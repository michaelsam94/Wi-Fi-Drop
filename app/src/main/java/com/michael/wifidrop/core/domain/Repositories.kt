package com.michael.wifidrop.core.domain

import kotlinx.coroutines.flow.Flow
import java.util.UUID

sealed class WebShareDownloadEvent {
    data class FileDownloaded(val fileName: String, val bytesTransferred: Long, val totalBytes: Long) : WebShareDownloadEvent()
    data class Completed(val fileName: String) : WebShareDownloadEvent()
    data class Error(val fileName: String, val error: String) : WebShareDownloadEvent()
}

data class WebShareSession(
    val localIp: String,
    val port: Int,
    val shareUrl: String
)

interface TransferRepository {
    fun observeActiveSessions(): Flow<List<TransferSession>>
    suspend fun startSend(items: List<TransferItem>, target: DiscoveredDevice): Result<UUID>
    suspend fun cancelSession(id: UUID): Result<Unit>
    suspend fun getHistory(): List<TransferSession>
}

interface DiscoveryRepository {
    fun observeNearbyDevices(): Flow<List<DiscoveredDevice>>
    suspend fun startAdvertising(deviceName: String): Result<Unit>
    suspend fun stopAdvertising(): Result<Unit>
}

interface WebShareRepository {
    suspend fun startWebShare(items: List<TransferItem>): Result<WebShareSession>
    suspend fun stopWebShare(): Result<Unit>
    fun observeWebShareDownloads(): Flow<WebShareDownloadEvent>
}
