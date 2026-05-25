package com.example.core.data

import android.content.Context
import android.os.Build
import com.example.core.common.DispatcherProvider
import com.example.core.common.SpeedMeter
import com.example.core.domain.*
import com.example.core.network.KtorLocalServer
import com.example.core.network.NetworkUtils
import com.example.core.storage.StorageMapper
import com.example.core.storage.TransferDao
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

    init {
        // Collect download events from Ktor and map them onto active transfer sessions dynamically
        repositoryScope.launch {
            val speedMeter = SpeedMeter()
            var currentSessionId: UUID? = null

            ktorServer.downloadEvents.collect { event ->
                val activeList = _activeSessions.value
                val activeId = currentSessionId ?: activeList.keys.firstOrNull() ?: return@collect

                when (event) {
                    is WebShareDownloadEvent.FileDownloaded -> {
                        speedMeter.record(event.bytesTransferred)
                        _activeSessions.update { sessions ->
                            val s = sessions[activeId] ?: return@update sessions
                            val updated = s.copy(
                                state = TransferState.TRANSFERRING,
                                bytesTransferred = event.bytesTransferred,
                                speedBps = speedMeter.currentBps
                            )
                            sessions + (activeId to updated)
                        }
                    }
                    is WebShareDownloadEvent.Completed -> {
                        _activeSessions.update { sessions ->
                            val s = sessions[activeId] ?: return@update sessions
                            val updated = s.copy(
                                state = TransferState.COMPLETED,
                                completedAt = System.currentTimeMillis(),
                                speedBps = 0L,
                                bytesTransferred = s.bytesTotal
                            )
                            // Save completed session to Room
                            repositoryScope.launch {
                                transferDao.insertSession(StorageMapper.toEntity(updated))
                            }
                            sessions + (activeId to updated)
                        }
                    }
                    is WebShareDownloadEvent.Error -> {
                        _activeSessions.update { sessions ->
                            val s = sessions[activeId] ?: return@update sessions
                            val updated = s.copy(
                                state = TransferState.FAILED,
                                completedAt = System.currentTimeMillis(),
                                speedBps = 0L
                            )
                            repositoryScope.launch {
                                transferDao.insertSession(StorageMapper.toEntity(updated))
                            }
                            sessions + (activeId to updated)
                        }
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

            // Update session state as now sharing on the port
            _activeSessions.update { sessions ->
                val s = sessions[id] ?: return@update sessions
                sessions + (id to s.copy(state = TransferState.TRANSFERRING))
            }
            id
        }
    }

    override suspend fun cancelSession(id: UUID): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
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
