package com.michael.wifidrop.core.data

import android.content.Context
import android.os.Build
import com.michael.wifidrop.core.common.DispatcherProvider
import com.michael.wifidrop.core.common.SpeedMeter
import com.michael.wifidrop.core.domain.*
import com.michael.wifidrop.core.network.KtorLocalServer
import com.michael.wifidrop.core.network.NetworkUtils
import com.michael.wifidrop.core.storage.StorageMapper
import com.michael.wifidrop.core.storage.TransferDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class TransferRepositoryImpl(
    private val context: Context,
    private val ktorServer: KtorLocalServer,
    private val transferDao: TransferDao,
    private val dispatchers: DispatcherProvider
) : TransferRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val _activeSessions = MutableStateFlow<Map<UUID, TransferSession>>(emptyMap())

    @Volatile
    private var currentSessionId: UUID? = null

    // Track file progress and completions per session
    private val sessionProgressMap = java.util.concurrent.ConcurrentHashMap<UUID, MutableMap<String, Long>>()
    private val sessionCompletedItems = java.util.concurrent.ConcurrentHashMap<UUID, MutableSet<String>>()

    init {
        // Collect download events from Ktor and map them onto active transfer sessions dynamically
        repositoryScope.launch {
            val speedMeter = SpeedMeter()

            ktorServer.downloadEvents.collect { event ->
                val activeList = _activeSessions.value
                val activeId = currentSessionId ?: activeList.keys.firstOrNull() ?: return@collect
                val s = activeList[activeId] ?: return@collect

                when (event) {
                    is WebShareDownloadEvent.FileDownloaded -> {
                        val progressMap = sessionProgressMap.getOrPut(activeId) { java.util.concurrent.ConcurrentHashMap() }
                        val prevBytes = progressMap[event.fileName] ?: 0L
                        val delta = (event.bytesTransferred - prevBytes).coerceAtLeast(0L)
                        
                        speedMeter.record(delta)
                        progressMap[event.fileName] = event.bytesTransferred

                        val completedSet = sessionCompletedItems[activeId] ?: emptySet()
                        
                        val totalTransferred = s.items.sumOf { item ->
                            val eventName = when (item) {
                                is TransferItem.SingleFile -> item.name
                                is TransferItem.Folder -> "${item.name}.zip"
                            }
                            if (completedSet.contains(eventName)) {
                                item.sizeBytes
                            } else {
                                progressMap[eventName] ?: 0L
                            }
                        }

                        _activeSessions.update { sessions ->
                            val currentSession = sessions[activeId] ?: return@update sessions
                            val updated = currentSession.copy(
                                state = TransferState.TRANSFERRING,
                                bytesTransferred = totalTransferred,
                                speedBps = speedMeter.currentBps
                            )
                            sessions + (activeId to updated)
                        }
                    }
                    is WebShareDownloadEvent.Completed -> {
                        val completedSet = sessionCompletedItems.getOrPut(activeId) {
                            java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap())
                        }
                        completedSet.add(event.fileName)

                        val progressMap = sessionProgressMap.getOrPut(activeId) { java.util.concurrent.ConcurrentHashMap() }
                        
                        val totalTransferred = s.items.sumOf { item ->
                            val eventName = when (item) {
                                is TransferItem.SingleFile -> item.name
                                is TransferItem.Folder -> "${item.name}.zip"
                            }
                            if (completedSet.contains(eventName)) {
                                item.sizeBytes
                            } else {
                                progressMap[eventName] ?: 0L
                            }
                        }

                        val allCompleted = s.items.all { item ->
                            val eventName = when (item) {
                                is TransferItem.SingleFile -> item.name
                                is TransferItem.Folder -> "${item.name}.zip"
                            }
                            completedSet.contains(eventName)
                        }

                        _activeSessions.update { sessions ->
                            val currentSession = sessions[activeId] ?: return@update sessions
                            val updated = currentSession.copy(
                                state = if (allCompleted) TransferState.COMPLETED else TransferState.TRANSFERRING,
                                completedAt = if (allCompleted) System.currentTimeMillis() else null,
                                speedBps = 0L,
                                bytesTransferred = if (allCompleted) currentSession.bytesTotal else totalTransferred
                            )
                            if (allCompleted) {
                                // Save completed session to Room
                                repositoryScope.launch {
                                    transferDao.insertSession(StorageMapper.toEntity(updated))
                                }
                            }
                            sessions + (activeId to updated)
                        }

                        if (allCompleted) {
                            if (currentSessionId == activeId) {
                                currentSessionId = null
                            }
                            sessionProgressMap.remove(activeId)
                            sessionCompletedItems.remove(activeId)
                        }
                    }
                    is WebShareDownloadEvent.Error -> {
                        _activeSessions.update { sessions ->
                            val currentSession = sessions[activeId] ?: return@update sessions
                            val updated = currentSession.copy(
                                state = TransferState.FAILED,
                                completedAt = System.currentTimeMillis(),
                                speedBps = 0L
                            )
                            repositoryScope.launch {
                                transferDao.insertSession(StorageMapper.toEntity(updated))
                            }
                            sessions + (activeId to updated)
                        }
                        if (currentSessionId == activeId) {
                            currentSessionId = null
                        }
                        sessionProgressMap.remove(activeId)
                        sessionCompletedItems.remove(activeId)
                    }
                }
            }
        }
    }

    override fun observeActiveSessions(): Flow<List<TransferSession>> =
        _activeSessions.map { it.values.toList() }

    override suspend fun startSend(
        items: List<TransferItem>,
        target: DiscoveredDevice
    ): Result<UUID> = withContext(dispatchers.io) {
        runCatching {
            val id = UUID.randomUUID()
            val totalBytes = items.sumOf { it.sizeBytes }
            val session = TransferSession(
                id = id,
                deviceName = target.displayName,
                items = items,
                direction = TransferDirection.SEND,
                state = TransferState.CONNECTING,
                bytesTotal = totalBytes,
                bytesTransferred = 0,
                speedBps = 0L,
                startedAt = System.currentTimeMillis()
            )

            _activeSessions.update { it + (id to session) }

            // Start Ktor server
            val actualPort = ktorServer.start(port = 0, shareItems = items)

            val localIp = NetworkUtils.getActiveLocalIp()
            val itemsJson = StorageMapper.serializeItems(items)
            val encodedName = java.net.URLEncoder.encode(Build.MODEL, "UTF-8")
            val encodedJson = java.net.URLEncoder.encode(itemsJson, "UTF-8")
            val targetIp = target.localIp
            val targetPort = target.port
            val notifyUrl = "http://$targetIp:$targetPort/receive-request?senderIp=$localIp&senderPort=$actualPort&senderName=$encodedName&itemsJson=$encodedJson"

            repositoryScope.launch(dispatchers.io) {
                try {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    val request = okhttp3.Request.Builder().url(notifyUrl).build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            System.err.println("Failed to notify receiver: ${response.code}")
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Update session state as now sharing on the port
            _activeSessions.update { sessions ->
                val s = sessions[id] ?: return@update sessions
                sessions + (id to s.copy(state = TransferState.TRANSFERRING))
            }
            currentSessionId = id
            id
        }
    }

    override suspend fun cancelSession(id: UUID): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            if (currentSessionId == id) {
                currentSessionId = null
            }
            sessionProgressMap.remove(id)
            sessionCompletedItems.remove(id)
            _activeSessions.update { sessions ->
                val s = sessions[id] ?: return@update sessions
                val updated = s.copy(state = TransferState.CANCELLED, completedAt = System.currentTimeMillis(), speedBps = 0)
                repositoryScope.launch {
                    transferDao.insertSession(StorageMapper.toEntity(updated))
                }
                sessions - id
            }
            ktorServer.stop()
        }
    }

    override suspend fun getHistory(): List<TransferSession> = withContext(dispatchers.io) {
        // Query database history list
        val entities = transferDao.getAllSessions().first()
        entities.map { StorageMapper.toDomain(it) }
    }
}

class WebShareRepositoryImpl(
    private val ktorServer: KtorLocalServer,
    private val dispatchers: DispatcherProvider
) : WebShareRepository {

    override suspend fun startWebShare(items: List<TransferItem>): Result<WebShareSession> = withContext(dispatchers.io) {
        runCatching {
            val localIp = NetworkUtils.getActiveLocalIp()
            val port = ktorServer.start(port = 0, shareItems = items)
            val shareUrl = "http://$localIp:$port"
            WebShareSession(localIp, port, shareUrl)
        }
    }

    override suspend fun stopWebShare(): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            ktorServer.stop()
        }
    }

    override fun observeWebShareDownloads(): Flow<WebShareDownloadEvent> =
        ktorServer.downloadEvents
}
