package com.michael.wifidrop.playstore

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.michael.wifidrop.core.common.DispatcherProvider
import com.michael.wifidrop.core.domain.*
import com.michael.wifidrop.core.network.KtorLocalServer
import com.michael.wifidrop.core.storage.TransferDao
import com.michael.wifidrop.core.storage.TransferSessionEntity
import com.michael.wifidrop.feature.receive.ReceiveViewModel
import com.michael.wifidrop.feature.send.SendViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

private class PlayStoreDispatcherProvider : DispatcherProvider {
  override val main = kotlinx.coroutines.Dispatchers.Unconfined
  override val io = kotlinx.coroutines.Dispatchers.Unconfined
  override val default = kotlinx.coroutines.Dispatchers.Unconfined
}

private class NoOpDiscoveryRepository : DiscoveryRepository {
  override fun observeNearbyDevices(): Flow<List<DiscoveredDevice>> = emptyFlow()
  override suspend fun startAdvertising(deviceName: String): Result<Unit> = Result.success(Unit)
  override suspend fun stopAdvertising(): Result<Unit> = Result.success(Unit)
}

private class NoOpTransferRepository : TransferRepository {
  override fun observeActiveSessions(): Flow<List<TransferSession>> = emptyFlow()
  override suspend fun startSend(items: List<TransferItem>, target: DiscoveredDevice): Result<UUID> =
    Result.success(UUID.randomUUID())
  override suspend fun cancelSession(id: UUID): Result<Unit> = Result.success(Unit)
  override suspend fun getHistory(): List<TransferSession> = emptyList()
}

private class NoOpWebShareRepository : WebShareRepository {
  override suspend fun startWebShare(items: List<TransferItem>): Result<WebShareSession> =
    Result.success(PlayStoreTestFixtures.demoWebShare)
  override suspend fun stopWebShare(): Result<Unit> = Result.success(Unit)
  override fun observeWebShareDownloads(): Flow<WebShareDownloadEvent> = emptyFlow()
}

private class NoOpFileSystemHelper : FileSystemHelper {
  override suspend fun walkTree(rootUriString: String): Result<List<FolderEntry>> =
    Result.success(emptyList())
  override fun getFileName(uriString: String): String = "folder"
  override fun getFileSize(uriString: String): Long = 0L
}

private class NoOpTransferDao : TransferDao {
  override fun getAllSessions(): Flow<List<TransferSessionEntity>> = flowOf(emptyList())
  override suspend fun insertSession(session: TransferSessionEntity) = Unit
  override suspend fun deleteSessionById(id: String) = Unit
  override suspend fun getSessionById(id: String): TransferSessionEntity? = null
}

fun createPlayStoreSendViewModel(context: Context): SendViewModel {
  val dispatchers = PlayStoreDispatcherProvider()
  return SendViewModel(
    context = context,
    discoverDevices = DiscoverNearbyDevicesUseCase(NoOpDiscoveryRepository()),
    buildFolderTree = BuildFolderTreeUseCase(NoOpFileSystemHelper()),
    startSend = StartSendUseCase(NoOpTransferRepository(), FileValidator()),
    startWebShare = StartWebShareUseCase(NoOpWebShareRepository()),
    transferRepository = NoOpTransferRepository(),
    dispatchers = dispatchers,
  )
}

fun createPlayStoreReceiveViewModel(context: Context): ReceiveViewModel {
  val dispatchers = PlayStoreDispatcherProvider()
  return ReceiveViewModel(
    context = context,
    discoveryRepository = NoOpDiscoveryRepository(),
    ktorServer = KtorLocalServer(context, dispatchers),
    transferDao = NoOpTransferDao(),
    dispatchers = dispatchers,
  )
}

@Suppress("UNCHECKED_CAST")
fun playStoreViewModelFactory(context: Context): ViewModelProvider.Factory =
  object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return when {
        modelClass.isAssignableFrom(SendViewModel::class.java) ->
          createPlayStoreSendViewModel(context) as T
        modelClass.isAssignableFrom(ReceiveViewModel::class.java) ->
          createPlayStoreReceiveViewModel(context) as T
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
      }
    }
  }
