package com.example.core.storage

import androidx.room.*
import com.example.core.domain.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity(tableName = "transfer_history")
data class TransferSessionEntity(
    @PrimaryKey val id: String,
    val deviceName: String,
    val itemsJson: String,
    val direction: String,
    val state: String,
    val bytesTotal: Long,
    val bytesTransferred: Long,
    val speedBps: Long,
    val startedAt: Long,
    val completedAt: Long?
)

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfer_history ORDER BY startedAt DESC")
    fun getAllSessions(): Flow<List<TransferSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: TransferSessionEntity)

    @Query("DELETE FROM transfer_history WHERE id = :id")
    suspend fun deleteSessionById(id: String)

    @Query("SELECT * FROM transfer_history WHERE id = :id")
    suspend fun getSessionById(id: String): TransferSessionEntity?
}

@Database(entities = [TransferSessionEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transferDao(): TransferDao
}

object StorageMapper {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    // Simplified DTO for serializing TransferItem poly-hierarchy
    internal class TransferItemDto(
        val type: String, // "FILE" or "FOLDER"
        val rootUriString: String?,
        val name: String,
        val sizeBytes: Long,
        val entries: List<FolderEntryDto>?
    )

    internal class FolderEntryDto(
        val relativePath: String,
        val sizeBytes: Long,
        val uriString: String
    )

    private val listType = Types.newParameterizedType(List::class.java, TransferItemDto::class.java)
    private val adapter = moshi.adapter<List<TransferItemDto>>(listType)

    fun serializeItems(items: List<TransferItem>): String {
        val dtos = items.map { item ->
            when (item) {
                is TransferItem.SingleFile -> TransferItemDto(
                    type = "FILE",
                    rootUriString = item.uriString,
                    name = item.name,
                    sizeBytes = item.sizeBytes,
                    entries = null
                )
                is TransferItem.Folder -> TransferItemDto(
                    type = "FOLDER",
                    rootUriString = item.rootUriString,
                    name = item.name,
                    sizeBytes = item.sizeBytes,
                    entries = item.entries.map { FolderEntryDto(it.relativePath, it.sizeBytes, it.uriString) }
                )
            }
        }
        return adapter.toJson(dtos) ?: "[]"
    }

    fun deserializeItems(json: String): List<TransferItem> {
        return try {
            val dtos = adapter.fromJson(json) ?: emptyList()
            dtos.map { dto ->
                if (dto.type == "FILE") {
                    TransferItem.SingleFile(dto.rootUriString ?: "", dto.name, dto.sizeBytes)
                } else {
                    val entries = dto.entries?.map { FolderEntry(it.relativePath, it.sizeBytes, it.uriString) } ?: emptyList()
                    TransferItem.Folder(dto.rootUriString ?: "", dto.name, entries)
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun toEntity(session: TransferSession): TransferSessionEntity {
        return TransferSessionEntity(
            id = session.id.toString(),
            deviceName = session.deviceName,
            itemsJson = serializeItems(session.items),
            direction = session.direction.name,
            state = session.state.name,
            bytesTotal = session.bytesTotal,
            bytesTransferred = session.bytesTransferred,
            speedBps = session.speedBps,
            startedAt = session.startedAt,
            completedAt = session.completedAt
        )
    }

    fun toDomain(entity: TransferSessionEntity): TransferSession {
        return TransferSession(
            id = UUID.fromString(entity.id),
            deviceName = entity.deviceName,
            items = deserializeItems(entity.itemsJson),
            direction = TransferDirection.valueOf(entity.direction),
            state = TransferState.valueOf(entity.state),
            bytesTotal = entity.bytesTotal,
            bytesTransferred = entity.bytesTransferred,
            speedBps = entity.speedBps,
            startedAt = entity.startedAt,
            completedAt = entity.completedAt
        )
    }
}
