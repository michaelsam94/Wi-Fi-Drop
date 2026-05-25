package com.example.feature.receive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.common.DispatcherProvider
import com.example.core.domain.DiscoveryRepository
import com.example.core.network.NetworkUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReceiveViewModel(
    private val discoveryRepository: DiscoveryRepository,
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
    }

    fun startAdvertising(deviceName: String) {
        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(isAdvertising = true, registeredName = deviceName) }
            discoveryRepository.startAdvertising(deviceName)
                .onFailure {
                    _state.update { it.copy(isAdvertising = false, registeredName = null) }
                    _events.emit(ReceiveUiEvent.ShowError("Could not publish receiver broadcast! Try re-connecting to Wi-Fi."))
                }
        }
    }

    fun stopAdvertising() {
        viewModelScope.launch(dispatchers.io) {
            discoveryRepository.stopAdvertising()
            _state.update { it.copy(isAdvertising = false, registeredName = null) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAdvertising()
    }

    class Factory(
        private val discoveryRepository: DiscoveryRepository,
        private val dispatchers: DispatcherProvider
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReceiveViewModel(discoveryRepository, dispatchers) as T
        }
    }
}

data class ReceiveUiState(
    val isAdvertising: Boolean = false,
    val registeredName: String? = null,
    val localIp: String = ""
)

sealed class ReceiveUiEvent {
    data class ShowError(val message: String) : ReceiveUiEvent()
}
