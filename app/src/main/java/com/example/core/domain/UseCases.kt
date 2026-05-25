package com.example.core.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

interface FileSystemHelper {
    suspend fun walkTree(rootUriString: String): Result<List<FolderEntry>>
    fun getFileName(uriString: String): String
    fun getFileSize(uriString: String): Long
}

class FileValidator {
    fun validate(item: TransferItem): Boolean {
        return when (item) {
            is TransferItem.SingleFile -> item.sizeBytes >= 0 && item.name.isNotEmpty()
            is TransferItem.Folder -> item.entries.isNotEmpty() && item.name.isNotEmpty()
        }
    }
}

class StartSendUseCase(
    private val transferRepo: TransferRepository,
    private val fileValidator: FileValidator
) {
    suspend operator fun invoke(items: List<TransferItem>, target: DiscoveredDevice): Result<UUID> {
        val allValid = items.all { fileValidator.validate(it) }
        if (!allValid) return Result.failure(IllegalArgumentException("Some selected transfer items are invalid or empty."))
        return transferRepo.startSend(items, target)
    }
}

class ObserveLiveTransferUseCase(private val transferRepo: TransferRepository) {
    operator fun invoke(sessionId: UUID): Flow<TransferSession?> {
        return transferRepo.observeActiveSessions().map { sessions ->
            sessions.find { it.id == sessionId }
        }
    }
}

class BuildFolderTreeUseCase(private val fileSystem: FileSystemHelper) {
    suspend operator fun invoke(rootUriString: String): Result<TransferItem.Folder> {
        return fileSystem.walkTree(rootUriString).map { entries ->
            val name = fileSystem.getFileName(rootUriString)
            TransferItem.Folder(rootUriString, name, entries)
        }
    }
}

class StartWebShareUseCase(private val webShareRepo: WebShareRepository) {
    suspend operator fun invoke(items: List<TransferItem>): Result<WebShareSession> {
        return webShareRepo.startWebShare(items)
    }
}

class DiscoverNearbyDevicesUseCase(private val discoveryRepo: DiscoveryRepository) {
    operator fun invoke(): Flow<List<DiscoveredDevice>> {
        return discoveryRepo.observeNearbyDevices()
    }
}
