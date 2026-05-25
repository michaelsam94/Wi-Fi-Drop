package com.michael.wifidrop.core.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.michael.wifidrop.core.common.DispatcherProvider
import com.michael.wifidrop.core.domain.FileSystemHelper
import com.michael.wifidrop.core.domain.FolderEntry
import kotlinx.coroutines.withContext

class FileSystemHelperImpl(
    private val context: Context,
    private val dispatchers: DispatcherProvider
) : FileSystemHelper {

    override fun getFileName(uriString: String): String {
        try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "content") {
                val queryUri = if (DocumentsContract.isTreeUri(uri)) {
                    DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
                } else {
                    uri
                }
                context.contentResolver.query(queryUri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        return cursor.getString(nameIndex) ?: "file"
                    }
                }
            }
            return uri.lastPathSegment ?: "file"
        } catch (e: Exception) {
            return "file"
        }
    }

    override fun getFileSize(uriString: String): Long {
        try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "content") {
                val queryUri = if (DocumentsContract.isTreeUri(uri)) {
                    DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
                } else {
                    uri
                }
                context.contentResolver.query(queryUri, null, null, null, null)?.use { cursor ->
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1 && cursor.moveToFirst()) {
                        return cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 0L
    }

    override suspend fun walkTree(rootUriString: String): Result<List<FolderEntry>> = withContext(dispatchers.io) {
        runCatching {
            val entries = mutableListOf<FolderEntry>()
            val rootUri = Uri.parse(rootUriString)

            val docId = if (DocumentsContract.isTreeUri(rootUri)) {
                DocumentsContract.getTreeDocumentId(rootUri)
            } else {
                DocumentsContract.getDocumentId(rootUri)
            }

            traverseDirectory(rootUri, docId, "", entries)
            entries
        }
    }

    private fun traverseDirectory(
        treeUri: Uri,
        parentId: String,
        currentRelativePath: String,
        outEntries: MutableList<FolderEntry>
    ) {
        val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )

        try {
            context.contentResolver.query(childUri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(0) ?: continue
                    val displayName = cursor.getString(1) ?: "file"
                    val mimeType = cursor.getString(2) ?: ""
                    val size = cursor.getLong(3)

                    val path = if (currentRelativePath.isEmpty()) displayName else "$currentRelativePath/$displayName"
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        traverseDirectory(treeUri, docId, path, outEntries)
                    } else {
                        outEntries.add(
                            FolderEntry(
                                relativePath = path,
                                sizeBytes = size,
                                uriString = fileUri.toString()
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
