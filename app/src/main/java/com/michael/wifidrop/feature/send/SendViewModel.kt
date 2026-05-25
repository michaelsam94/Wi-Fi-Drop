package com.michael.wifidrop.feature.send

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.michael.wifidrop.core.common.DispatcherProvider
import com.michael.wifidrop.core.domain.*
import com.michael.wifidrop.core.network.NetworkUtils
import kotlinx.coroutines.Job
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

    private var discoveryJob: Job? = null

    init {
        viewModelScope.launch(dispatchers.io) {
            val ip = NetworkUtils.getActiveLocalIp()
            _state.update { it.copy(localIp = ip) }
        }
    }

    private fun refreshDiscovery() {
        val shouldDiscover =
            _state.value.selectedItems.isNotEmpty() && _state.value.webShareSession == null
        discoveryJob?.cancel()
        discoveryJob = null
        if (shouldDiscover) {
            discoveryJob = viewModelScope.launch(dispatchers.io) {
                discoverDevices().collect { devices ->
                    _state.update { it.copy(nearbyDevices = devices) }
                }
            }
        } else {
            _state.update { it.copy(nearbyDevices = emptyList()) }
        }
    }

    fun onFilesSelected(uris: List<String>) {
        viewModelScope.launch(dispatchers.io) {
            _state.update {
                it.copy(isProcessing = true, processingMessage = "Reading selected files…")
            }
            val newItems = uris.mapNotNull { uriStr ->
                try {
                    val uri = Uri.parse(uriStr)
                    val isTreeUri = uriStr.contains("tree") || isUriDirectory(context, uri)
                    if (isTreeUri) {
                        _state.update {
                            it.copy(processingMessage = "Scanning folder contents…")
                        }
                        buildFolderTree(uriStr).getOrNull()
                    } else {
                        val (name, size) = getFileNameAndSize(context, uri)
                        TransferItem.SingleFile(uriStr, name, size)
                    }
                } catch (e: Exception) {
                    null
                }
            }
            _state.update { s ->
                val existingUris = s.selectedItems.map { it.uriString }.toSet()
                val uniqueNewItems = newItems.filter { it.uriString !in existingUris }
                s.copy(
                    isProcessing = false,
                    processingMessage = "",
                    selectedItems = s.selectedItems + uniqueNewItems,
                )
            }
            refreshDiscovery()
        }
    }

    fun removeSelectedItem(item: TransferItem) {
        _state.update { s ->
            s.copy(selectedItems = s.selectedItems.filter { it.uriString != item.uriString })
        }
        refreshDiscovery()
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
            _state.update { it.copy(isConnecting = true, processingMessage = "Connecting to ${target.displayName}…") }
            try {
                startSend(state.value.selectedItems, target)
                    .onSuccess { sessionId ->
                        _events.emit(SendUiEvent.NavigateToProgress(sessionId))
                        observeActiveSession(sessionId)
                    }
                    .onFailure {
                        _events.emit(SendUiEvent.ShowError(it.message ?: "Send failed"))
                    }
            } finally {
                _state.update { it.copy(isConnecting = false, processingMessage = "") }
            }
        }
    }

    fun onWebShareTapped() {
        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(isStartingWebShare = true, processingMessage = "Starting web share…") }
            try {
                startWebShare(state.value.selectedItems)
                    .onSuccess { session ->
                        _state.update { it.copy(webShareSession = session) }
                        refreshDiscovery()
                    }
                    .onFailure {
                        _events.emit(SendUiEvent.ShowError("Web share failed to initialize on this network."))
                    }
            } finally {
                _state.update { it.copy(isStartingWebShare = false, processingMessage = "") }
            }
        }
    }

    fun stopWebShare() {
        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(webShareSession = null) }
            transferRepository.cancelSession(UUID.randomUUID())
            refreshDiscovery()
        }
    }

    fun cancelActiveSession(id: UUID) {
        viewModelScope.launch(dispatchers.io) {
            transferRepository.cancelSession(id)
            _state.update { it.copy(activeSession = null) }
        }
    }

    private fun observeActiveSession(id: UUID) {
        viewModelScope.launch(dispatchers.io) {
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
    val isConnecting: Boolean = false,
    val isStartingWebShare: Boolean = false,
    val processingMessage: String = "",
    val localIp: String = "",
) {
    val isBusy: Boolean
        get() = isProcessing || isConnecting || isStartingWebShare
}

sealed class SendUiEvent {
    data class ShowError(val message: String) : SendUiEvent()
    data class NavigateToProgress(val sessionId: UUID) : SendUiEvent()
    data object NavigateToHistory : SendUiEvent()
}
