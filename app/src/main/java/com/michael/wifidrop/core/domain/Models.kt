package com.michael.wifidrop.core.domain

import java.util.UUID

enum class TransferDirection {
    SEND, RECEIVE
}

enum class TransferState {
    QUEUED, CONNECTING, TRANSFERRING, PAUSED, COMPLETED, FAILED, CANCELLED
}

sealed class TransferItem {
    abstract val name: String
    abstract val sizeBytes: Long
    abstract val uriString: String

    data class SingleFile(
        override val uriString: String,
        override val name: String,
        override val sizeBytes: Long
    ) : TransferItem()

    data class Folder(
        override val uriString: String,
        override val name: String,
        val entries: List<FolderEntry>
    ) : TransferItem() {
        override val sizeBytes: Long get() = entries.sumOf { it.sizeBytes }
    }
}

data class FolderEntry(
    val relativePath: String,
    val sizeBytes: Long,
    val uriString: String = ""
)

data class TransferSession(
    val id: UUID,
    val deviceName: String,
    val items: List<TransferItem>,
    val direction: TransferDirection,
    val state: TransferState,
    val bytesTotal: Long,
    val bytesTransferred: Long,
    val speedBps: Long,                 // rolling 1-second window
    val startedAt: Long,                // Using epoch milliseconds for easy DB storage & pure Kotlin compatibility
    val completedAt: Long? = null
)

data class DiscoveredDevice(
    val id: String,
    val displayName: String,
    val localIp: String,
    val port: Int,
    val sharePort: Int,                 // Ktor HTTP port for web share mode
    val supportsClient: Boolean
)
