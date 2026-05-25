package com.michael.wifidrop.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.michael.wifidrop.core.common.DispatcherProvider
import com.michael.wifidrop.core.domain.TransferSession
import com.michael.wifidrop.core.domain.TransferRepository
import com.michael.wifidrop.core.storage.TransferDao
import com.michael.wifidrop.core.storage.StorageMapper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(
    private val transferRepository: TransferRepository,
    private val transferDao: TransferDao,
    private val dispatchers: DispatcherProvider
) : ViewModel() {

    val historyUiState: StateFlow<HistoryUiState> = combine(
        transferRepository.observeActiveSessions(),
        transferDao.getAllSessions().map { entities ->
            entities.map { StorageMapper.toDomain(it) }
        }
    ) { active, historical ->
        HistoryUiState(active, historical)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState()
    )

    class Factory(
        private val transferRepository: TransferRepository,
        private val transferDao: TransferDao,
        private val dispatchers: DispatcherProvider
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(transferRepository, transferDao, dispatchers) as T
        }
    }
}

data class HistoryUiState(
    val activeTransfers: List<TransferSession> = emptyList(),
    val historicalTransfers: List<TransferSession> = emptyList()
)
