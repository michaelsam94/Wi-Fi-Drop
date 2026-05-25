package com.example.feature.send

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.common.DispatcherProvider
import com.example.core.domain.*
import com.example.core.network.NetworkUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class SendViewModel(
    private val context: Context,
    private val discoverDevices: DiscoverNearbyDevicesUseCase,
    private val buildFolderTree: BuildFolderTreeUseCase,
    private val startSend: StartSendUseCase,
    private val startWebShare: StartWebShareUseCase,
    private val transferRepository: TransferRepository,
    private val dispatchers: DispatcherProvider
) : ViewModel() {

    private val _state = MutableStateFlow(SendUiState())
    val state: StateFlow<SendUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SendUiEvent>()
    val events: SharedFlow<SendUiEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            discoverDevices().collect { devices ->
                _state.update { it.copy(nearbyDevices = devices) }
            }
        }

        viewModelScope.launch(dispatchers.io) {
            val ip = NetworkUtils.getActiveLocalIp()
            _state.update { it.copy(localIp = ip) }
        }
    }

    fun onFilesSelected(uris: List<String>) {
        viewModelScope.launch(dispatchers.default) {
            _state.update { it.copy(isProcessing = true) }
            val items = uris.mapNotNull { uriStr ->
                try {
                    val uri = Uri.parse(uriStr)
                    val isTreeUri = uriStr.contains("tree") || isUriDirectory(context, uri)
                    if (isTreeUri) {
                        buildFolderTree(uriStr).getOrNull()
                    } else {
                        val (name, size) = getFileNameAndSize(context, uri)
                        TransferItem.SingleFile(uriStr, name, size)
                    }
                } catch (e: Exception) {
                    null
                }
            }
            _state.update { it.copy(isProcessing = false, selectedItems = items) }
        }
    }

    private fun isUriDirectory(context: Context, uri: Uri): Boolean {
        return context.contentResolver.getType(uri) == "vnd.android.document/directory"
    }

    private fun getFileNameAndSize(context: Context, uri: Uri): Pair<String, Long> {
        var name = "file"
        var size = 0L
        try {
            if (uri.scheme == "content") {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx != -1) name = cursor.getString(nameIdx) ?: "file"
                        if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                    }
                }
            } else {
                name = uri.lastPathSegment ?: "file"
                val file = File(uri.path ?: "")
                if (file.exists()) {
                    size = file.length()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Pair(name, size)
    }

    fun onSendTapped(target: DiscoveredDevice) {
        viewModelScope.launch(dispatchers.io) {
            startSend(state.value.selectedItems, target)
                .onSuccess { sessionId ->
                    _events.emit(SendUiEvent.NavigateToProgress(sessionId))
                    observeActiveSession(sessionId)
                }
                .onFailure {
                    _events.emit(SendUiEvent.ShowError(it.message ?: "Send failed"))
                }
        }
    }

    fun onWebShareTapped() {
        viewModelScope.launch(dispatchers.io) {
            startWebShare(state.value.selectedItems)
                .onSuccess { session ->
                    _state.update { it.copy(webShareSession = session) }
                }
                .onFailure {
                    _events.emit(SendUiEvent.ShowError("Web share failed to initialize on this network."))
                }
        }
    }

    fun stopWebShare() {
        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(webShareSession = null) }
            transferRepository.cancelSession(UUID.randomUUID())
        }
    }

    fun cancelActiveSession(id: UUID) {
        viewModelScope.launch(dispatchers.io) {
            transferRepository.cancelSession(id)
            _state.update { it.copy(activeSession = null) }
        }
    }

    private fun observeActiveSession(id: UUID) {
        viewModelScope.launch {
            transferRepository.observeActiveSessions().collect { sessions ->
                val active = sessions.find { it.id == id }
                _state.update { it.copy(activeSession = active) }
            }
        }
    }

    class Factory(
        private val context: Context,
        private val discoverDevices: DiscoverNearbyDevicesUseCase,
        private val buildFolderTree: BuildFolderTreeUseCase,
        private val startSend: StartSendUseCase,
        private val startWebShare: StartWebShareUseCase,
        private val transferRepository: TransferRepository,
        private val dispatchers: DispatcherProvider
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SendViewModel(
                context,
                discoverDevices,
                buildFolderTree,
                startSend,
                startWebShare,
                transferRepository,
                dispatchers
            ) as T
        }
    }
}

data class SendUiState(
    val selectedItems: List<TransferItem> = emptyList(),
    val nearbyDevices: List<DiscoveredDevice> = emptyList(),
    val activeSession: TransferSession? = null,
    val webShareSession: WebShareSession? = null,
    val isProcessing: Boolean = false,
    val localIp: String = ""
)

sealed class SendUiEvent {
    data class ShowError(val message: String) : SendUiEvent()
    data class NavigateToProgress(val sessionId: UUID) : SendUiEvent()
    data object NavigateToHistory : SendUiEvent()
}
