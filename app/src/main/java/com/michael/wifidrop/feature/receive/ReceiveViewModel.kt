package com.michael.wifidrop.feature.receive

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.michael.wifidrop.core.common.DispatcherProvider
import com.michael.wifidrop.core.domain.*
import com.michael.wifidrop.core.network.KtorLocalServer
import com.michael.wifidrop.core.network.NetworkUtils
import com.michael.wifidrop.core.storage.StorageMapper
import com.michael.wifidrop.core.storage.TransferDao
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.UUID

class ReceiveViewModel(
    private val context: Context,
    private val discoveryRepository: DiscoveryRepository,
    private val ktorServer: KtorLocalServer,
    private val transferDao: TransferDao,
    private val dispatchers: DispatcherProvider
) : ViewModel() {

    private val _state = MutableStateFlow(ReceiveUiState())
    val state: StateFlow<ReceiveUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ReceiveUiEvent>()
    val events: SharedFlow<ReceiveUiEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch(dispatchers.io) {
            val ip = NetworkUtils.getActiveLocalIp()
            _state.update { it.copy(localIp = ip) }
        }

        // Collect incoming transfer notifications from the local server
        viewModelScope.launch(dispatchers.io) {
            ktorServer.incomingTransfers.collect { event ->
                startDownload(event.senderIp, event.senderPort, event.senderName, event.itemsJson)
            }
        }
    }

    fun startAdvertising(deviceName: String) {
        viewModelScope.launch(dispatchers.io) {
            _state.update {
                it.copy(
                    isStartingAdvertising = true,
                    processingMessage = "Starting receiver…",
                )
            }
            try {
                _state.update { it.copy(isAdvertising = true, registeredName = deviceName) }

                ktorServer.start(port = 33445, shareItems = emptyList())

                discoveryRepository.startAdvertising(deviceName)
                    .onFailure {
                        ktorServer.stop()
                        _state.update {
                            it.copy(isAdvertising = false, registeredName = null)
                        }
                        _events.emit(ReceiveUiEvent.ShowError("Could not publish receiver broadcast! Try re-connecting to Wi-Fi."))
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                _state.update {
                    it.copy(isAdvertising = false, registeredName = null)
                }
                _events.emit(ReceiveUiEvent.ShowError("Could not start receiver: ${e.localizedMessage}"))
            } finally {
                _state.update {
                    it.copy(isStartingAdvertising = false, processingMessage = "")
                }
            }
        }
    }

    fun stopAdvertising() {
        viewModelScope.launch(dispatchers.io) {
            discoveryRepository.stopAdvertising()
            ktorServer.stop()
            _state.update {
                it.copy(
                    isAdvertising = false,
                    registeredName = null,
                    isDownloading = false,
                    currentFileName = ""
                )
            }
        }
    }

    private fun startDownload(senderIp: String, senderPort: Int, senderName: String, itemsJson: String) {
        viewModelScope.launch(dispatchers.io) {
            _state.update {
                it.copy(
                    isDownloading = true,
                    senderName = senderName,
                    bytesTransferred = 0,
                    bytesTotal = 0,
                    progress = 0f,
                    currentFileName = "Preparing download…",
                    processingMessage = "Receiving from $senderName…",
                )
            }

            try {
                val items = StorageMapper.deserializeItems(itemsJson)
                val totalBytes = items.sumOf { it.sizeBytes }
                _state.update { it.copy(bytesTotal = totalBytes) }

                var overallTransferred = 0L
                var lastUpdateMillis = 0L
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val sessionId = UUID.randomUUID()

                // Target directory: public Downloads/Wifi-Drop folder
                val publicDownloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val downloadDir = File(publicDownloadDir, "Wifi-Drop")
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs()
                }

                items.forEachIndexed { index, item ->
                    _state.update { it.copy(currentFileName = item.name) }

                    when (item) {
                        is TransferItem.SingleFile -> {
                            val url = "http://$senderIp:$senderPort/file/$index"
                            val request = okhttp3.Request.Builder().url(url).build()
                            val destFile = File(downloadDir, item.name)

                            client.newCall(request).execute().use { response ->
                                if (!response.isSuccessful) throw IOException("Failed to download file ${item.name}: $response")
                                val body = response.body ?: throw IOException("Empty body for ${item.name}")

                                body.byteStream().use { input ->
                                    destFile.outputStream().buffered(262144).use { output ->
                                        val buffer = ByteArray(262144)
                                        var bytesRead: Int
                                        while (input.read(buffer).also { bytesRead = it } != -1) {
                                            output.write(buffer, 0, bytesRead)
                                            overallTransferred += bytesRead
                                            
                                            val now = System.currentTimeMillis()
                                            if (now - lastUpdateMillis > 200 || overallTransferred == totalBytes) {
                                                lastUpdateMillis = now
                                                _state.update { s ->
                                                    s.copy(
                                                        bytesTransferred = overallTransferred,
                                                        progress = if (totalBytes > 0) overallTransferred.toFloat() / totalBytes.toFloat() else 0f
                                                    )
                                                }
                                            }
                                        }
                                        output.flush()
                                    }
                                }
                            }
                        }
                        is TransferItem.Folder -> {
                            val url = "http://$senderIp:$senderPort/folder/$index"
                            val request = okhttp3.Request.Builder().url(url).build()
                            val destFile = File(downloadDir, "${item.name}.zip")

                            client.newCall(request).execute().use { response ->
                                if (!response.isSuccessful) throw IOException("Failed to download folder ${item.name}: $response")
                                val body = response.body ?: throw IOException("Empty body for folder ${item.name}")

                                body.byteStream().use { input ->
                                    destFile.outputStream().buffered(262144).use { output ->
                                        val buffer = ByteArray(262144)
                                        var bytesRead: Int
                                        while (input.read(buffer).also { bytesRead = it } != -1) {
                                            output.write(buffer, 0, bytesRead)
                                            overallTransferred += bytesRead
                                            
                                            val now = System.currentTimeMillis()
                                            if (now - lastUpdateMillis > 200 || overallTransferred == totalBytes) {
                                                lastUpdateMillis = now
                                                _state.update { s ->
                                                    s.copy(
                                                        bytesTransferred = overallTransferred,
                                                        progress = if (totalBytes > 0) overallTransferred.toFloat() / totalBytes.toFloat() else 0f
                                                    )
                                                }
                                            }
                                        }
                                        output.flush()
                                    }
                                }
                            }

                            // Extract the downloaded ZIP file and clean it up
                            try {
                                unzip(destFile, downloadDir)
                            } finally {
                                if (destFile.exists()) {
                                    destFile.delete()
                                }
                            }
                        }
                    }
                }

                // Final state update to ensure 100% completion is reflected accurately in UI
                _state.update { s ->
                    s.copy(
                        bytesTransferred = overallTransferred,
                        progress = 1.0f
                    )
                }

                // Log successfully completed transfer to Room history database
                val session = TransferSession(
                    id = sessionId,
                    deviceName = senderName,
                    items = items,
                    direction = TransferDirection.RECEIVE,
                    state = TransferState.COMPLETED,
                    bytesTotal = totalBytes,
                    bytesTransferred = totalBytes,
                    speedBps = 0L,
                    startedAt = System.currentTimeMillis(),
                    completedAt = System.currentTimeMillis()
                )
                transferDao.insertSession(StorageMapper.toEntity(session))

                _state.update {
                    it.copy(
                        isDownloading = false,
                        processingMessage = "",
                        currentFileName = "Download completed! Saved in Downloads/Wifi-Drop directory.",
                        progress = 1.0f
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _state.update {
                    it.copy(
                        isDownloading = false,
                        processingMessage = "",
                        currentFileName = "Download failed: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    private fun unzip(zipFile: File, destDir: File) {
        java.util.zip.ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val destFile = File(destDir, entry.name)
                // Prevent Zip Slip vulnerability
                val canonicalDestDir = destDir.canonicalPath
                val canonicalDestFile = destFile.canonicalPath
                if (!canonicalDestFile.startsWith(canonicalDestDir + File.separator)) {
                    throw SecurityException("Illegal zip entry path: ${entry.name}")
                }
                
                if (entry.isDirectory) {
                    destFile.mkdirs()
                } else {
                    destFile.parentFile?.mkdirs()
                    destFile.outputStream().buffered().use { out ->
                        zis.copyTo(out)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    override fun onCleared() {
        viewModelScope.launch(dispatchers.io) {
            discoveryRepository.stopAdvertising()
            ktorServer.stop()
        }
        super.onCleared()
    }

    class Factory(
        private val context: Context,
        private val discoveryRepository: DiscoveryRepository,
        private val ktorServer: KtorLocalServer,
        private val transferDao: TransferDao,
        private val dispatchers: DispatcherProvider
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReceiveViewModel(context, discoveryRepository, ktorServer, transferDao, dispatchers) as T
        }
    }
}

data class ReceiveUiState(
    val isAdvertising: Boolean = false,
    val isStartingAdvertising: Boolean = false,
    val registeredName: String? = null,
    val localIp: String = "",
    val isDownloading: Boolean = false,
    val currentFileName: String = "",
    val bytesTransferred: Long = 0,
    val bytesTotal: Long = 0,
    val senderName: String = "",
    val progress: Float = 0f,
    val processingMessage: String = "",
) {
    val isBusy: Boolean
        get() = isStartingAdvertising
}

sealed class ReceiveUiEvent {
    data class ShowError(val message: String) : ReceiveUiEvent()
}
