package com.michael.wifidrop.core.network

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.michael.wifidrop.core.common.DispatcherProvider
import com.michael.wifidrop.core.domain.TransferItem
import com.michael.wifidrop.core.domain.FolderEntry
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.michael.wifidrop.core.domain.WebShareDownloadEvent
import io.ktor.server.request.*

data class IncomingTransferEvent(
    val senderIp: String,
    val senderPort: Int,
    val senderName: String,
    val itemsJson: String
)

open class KtorLocalServer(
    private val context: Context,
    private val dispatchers: DispatcherProvider
) {
    private var server: NettyApplicationEngine? = null

    private val _downloadEvents = MutableSharedFlow<WebShareDownloadEvent>(extraBufferCapacity = 64)
    val downloadEvents: SharedFlow<WebShareDownloadEvent> = _downloadEvents

    private val _incomingTransfers = MutableSharedFlow<IncomingTransferEvent>(extraBufferCapacity = 16)
    val incomingTransfers: SharedFlow<IncomingTransferEvent> = _incomingTransfers.asSharedFlow()

    suspend fun emitDownloadEvent(event: WebShareDownloadEvent) {
        _downloadEvents.emit(event)
    }

    open suspend fun start(port: Int, shareItems: List<TransferItem>): Int {
        stop()
        val createdServer = embeddedServer(Netty, port = port) {
            routing {
                get("/receive-request") {
                    val senderIp = call.request.queryParameters["senderIp"] ?: call.request.local.remoteHost
                    val senderPort = call.request.queryParameters["senderPort"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val senderName = call.request.queryParameters["senderName"] ?: "Unknown"
                    val itemsJson = call.request.queryParameters["itemsJson"] ?: "[]"
                    
                    _incomingTransfers.emit(IncomingTransferEvent(senderIp, senderPort, senderName, itemsJson))
                    call.respond(HttpStatusCode.OK, "Request received")
                }

                get("/") {
                    val html = buildIndexPage(shareItems)
                    call.respondText(html, ContentType.Text.Html)
                }

                get("/file/{index}") {
                    val index = call.parameters["index"]?.toIntOrNull()
                    if (index == null || index < 0 || index >= shareItems.size) {
                        call.respond(HttpStatusCode.NotFound, "File not found")
                        return@get
                    }
                    val item = shareItems[index]
                    if (item is TransferItem.SingleFile) {
                        val resolver = this@KtorLocalServer.context.contentResolver
                        val uri = Uri.parse(item.uriString)
                        val inputStream = try {
                            resolver.openInputStream(uri)?.buffered(262144)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            null
                        }

                        if (inputStream == null) {
                            _downloadEvents.emit(WebShareDownloadEvent.Error(item.name, "Could not open file stream"))
                            call.respond(HttpStatusCode.InternalServerError, "Could not open file stream")
                            return@get
                        }

                        try {
                            call.response.header(
                                HttpHeaders.ContentDisposition,
                                ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, item.name).toString()
                            )
                            call.response.header(HttpHeaders.ContentLength, item.sizeBytes.toString())

                            _downloadEvents.emit(WebShareDownloadEvent.FileDownloaded(item.name, 0, item.sizeBytes))

                            inputStream.use { input ->
                                call.respondOutputStream(ContentType.Application.OctetStream) {
                                    pipeStream(input, this, item.name, item.sizeBytes)
                                }
                            }

                            _downloadEvents.emit(WebShareDownloadEvent.Completed(item.name))
                        } catch (e: Exception) {
                            e.printStackTrace()
                            _downloadEvents.emit(WebShareDownloadEvent.Error(item.name, e.localizedMessage ?: "Transmission failed"))
                        }
                    } else if (item is TransferItem.Folder) {
                        call.respondRedirect("/folder/$index")
                    }
                }

                get("/folder_entry/{index}/{entryIndex}") {
                    val index = call.parameters["index"]?.toIntOrNull()
                    val entryIndex = call.parameters["entryIndex"]?.toIntOrNull()
                    if (index == null || entryIndex == null || index < 0 || index >= shareItems.size) {
                        call.respond(HttpStatusCode.NotFound)
                        return@get
                    }
                    val folder = shareItems[index] as? TransferItem.Folder ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val entry = folder.entries.getOrNull(entryIndex) ?: return@get call.respond(HttpStatusCode.NotFound)

                    val resolver = this@KtorLocalServer.context.contentResolver
                    val uri = Uri.parse(entry.uriString)
                    val inputStream = try {
                        resolver.openInputStream(uri)?.buffered(262144)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }

                    if (inputStream == null) {
                        _downloadEvents.emit(WebShareDownloadEvent.Error(entry.relativePath, "Could not open entry stream"))
                        call.respond(HttpStatusCode.InternalServerError, "Could not open file entry stream")
                        return@get
                    }

                    try {
                        call.response.header(
                            HttpHeaders.ContentDisposition,
                            ContentDisposition.Attachment.withParameter(
                                ContentDisposition.Parameters.FileName,
                                entry.relativePath.substringAfterLast('/')
                            ).toString()
                        )
                        call.response.header(HttpHeaders.ContentLength, entry.sizeBytes.toString())

                        _downloadEvents.emit(WebShareDownloadEvent.FileDownloaded(entry.relativePath, 0, entry.sizeBytes))

                        inputStream.use { input ->
                            call.respondOutputStream(ContentType.Application.OctetStream) {
                                pipeStream(input, this, entry.relativePath, entry.sizeBytes)
                            }
                        }

                        _downloadEvents.emit(WebShareDownloadEvent.Completed(entry.relativePath))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        _downloadEvents.emit(WebShareDownloadEvent.Error(entry.relativePath, e.localizedMessage ?: "Transmission failed"))
                    }
                }

                get("/folder/{index}") {
                    val index = call.parameters["index"]?.toIntOrNull()
                    if (index == null || index < 0 || index >= shareItems.size) {
                        call.respond(HttpStatusCode.NotFound)
                        return@get
                    }
                    val folder = shareItems[index] as? TransferItem.Folder
                    if (folder == null) {
                        call.respond(HttpStatusCode.BadRequest, "Item is not a folder")
                        return@get
                    }

                    try {
                        call.response.header(
                            HttpHeaders.ContentDisposition,
                            ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "${folder.name}.zip").toString()
                        )

                        _downloadEvents.emit(WebShareDownloadEvent.FileDownloaded("${folder.name}.zip", 0, folder.sizeBytes))

                        call.respondOutputStream(ContentType.Application.Zip) {
                            buildZipStream(folder, this)
                        }

                        _downloadEvents.emit(WebShareDownloadEvent.Completed("${folder.name}.zip"))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        _downloadEvents.emit(WebShareDownloadEvent.Error("${folder.name}.zip", e.localizedMessage ?: "ZIP download failed"))
                    }
                }
            }
        }

        server = createdServer
        createdServer.start(wait = false)
        return createdServer.resolvedConnectors().first().port
    }

    private suspend fun pipeStream(input: InputStream, output: OutputStream, name: String, totalBytes: Long) = withContext(dispatchers.io) {
        val buffer = ByteArray(262144)
        var bytesRead: Int
        var totalRead = 0L
        var lastUpdateMillis = 0L
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            totalRead += bytesRead
            
            val now = System.currentTimeMillis()
            if (now - lastUpdateMillis > 200 || totalRead == totalBytes) {
                lastUpdateMillis = now
                _downloadEvents.emit(WebShareDownloadEvent.FileDownloaded(name, totalRead, totalBytes))
            }
        }
        // Ensure final update is emitted if not already
        _downloadEvents.emit(WebShareDownloadEvent.FileDownloaded(name, totalRead, totalBytes))
        output.flush()
    }

    private suspend fun buildZipStream(folder: TransferItem.Folder, out: java.io.OutputStream) = withContext(dispatchers.default) {
        ZipOutputStream(out).use { zip ->
            var writtenBytes = 0L
            folder.entries.forEach { entry ->
                zip.putNextEntry(ZipEntry(entry.relativePath))
                try {
                    val inputStream = this@KtorLocalServer.context.contentResolver.openInputStream(Uri.parse(entry.uriString))?.buffered(262144)
                        ?: throw java.io.IOException("Could not open input stream for entry: ${entry.relativePath}")
                    inputStream.use { input ->
                        val buffer = ByteArray(262144)
                        var bytesRead: Int
                        var lastUpdateMillis = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            zip.write(buffer, 0, bytesRead)
                            writtenBytes += bytesRead
                            
                            val now = System.currentTimeMillis()
                            if (now - lastUpdateMillis > 200 || writtenBytes == folder.sizeBytes) {
                                lastUpdateMillis = now
                                _downloadEvents.emit(WebShareDownloadEvent.FileDownloaded("${folder.name}.zip", writtenBytes, folder.sizeBytes))
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    throw e
                } finally {
                    zip.closeEntry()
                }
            }
        }
    }

    private fun buildIndexPage(shareItems: List<TransferItem>): String {
        val themeColor = "#1E293B"
        val accentColor = "#0F766E"
        val listItemsHtml = shareItems.mapIndexed { index, item ->
            val sizeFormatted = formatSize(item.sizeBytes)
            val iconSvg = when (item) {
                is TransferItem.SingleFile -> """<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline>"""
                is TransferItem.Folder -> """<path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path>"""
            }
            val downloadUrl = when (item) {
                is TransferItem.SingleFile -> "/file/$index"
                is TransferItem.Folder -> "/folder/$index"
            }
            """
            <div class="item-card">
                <div class="item-info">
                    <svg class="item-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        $iconSvg
                    </svg>
                    <div class="item-metadata">
                        <span class="item-name">${escapeHtml(item.name)}</span>
                        <span class="item-size">$sizeFormatted</span>
                    </div>
                </div>
                <a href="$downloadUrl" class="download-btn">
                    <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                        <polyline points="7 10 12 15 17 10"></polyline>
                        <line x1="12" y1="15" x2="12" y2="3"></line>
                    </svg>
                    Download
                </a>
            </div>
            """
        }.joinToString("\n")

        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Wi-Fi Drop Transfer</title>
            <style>
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
                    background-color: #F8FAFC;
                    color: #0F172A;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    min-height: 100vh;
                    padding: 32px 16px;
                }
                .container {
                    width: 100%;
                    max-width: 580px;
                    background: #FFFFFF;
                    border-radius: 20px;
                    box-shadow: 0 10px 25px -5px rgba(0,0,0,0.05), 0 8px 10px -6px rgba(0,0,0,0.05);
                    padding: 32px;
                    border: 1px solid #E2E8F0;
                }
                .header {
                    text-align: center;
                    margin-bottom: 32px;
                }
                .app-pill {
                    display: inline-block;
                    background-color: #CCFBF1;
                    color: #115E59;
                    font-size: 11px;
                    font-weight: 700;
                    letter-spacing: 0.05em;
                    text-transform: uppercase;
                    padding: 6px 14px;
                    border-radius: 9999px;
                    margin-bottom: 12px;
                }
                h1 {
                    font-size: 26px;
                    color: $themeColor;
                    font-weight: 800;
                    letter-spacing: -0.025em;
                }
                .subtitle {
                    font-size: 14px;
                    color: #64748B;
                    margin-top: 8px;
                }
                .item-list {
                    display: flex;
                    flex-direction: column;
                    gap: 16px;
                }
                .item-card {
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    padding: 16px 20px;
                    border: 1px solid #EDF2F7;
                    border-radius: 12px;
                    transition: all 0.2s ease;
                    background: #FAFAFA;
                }
                .item-card:hover {
                    border-color: #CBD5E1;
                    background: #F8FAFC;
                }
                .item-info {
                    display: flex;
                    align-items: center;
                    gap: 16px;
                    overflow: hidden;
                }
                .item-icon {
                    width: 26px;
                    height: 26px;
                    color: $accentColor;
                    flex-shrink: 0;
                }
                .item-metadata {
                    display: flex;
                    flex-direction: column;
                    overflow: hidden;
                }
                .item-name {
                    font-size: 15px;
                    font-weight: 700;
                    color: #1E293B;
                    white-space: nowrap;
                    overflow: hidden;
                    text-overflow: ellipsis;
                }
                .item-size {
                    font-size: 12px;
                    color: #64748B;
                    margin-top: 4px;
                    font-weight: 500;
                }
                .download-btn {
                    display: inline-flex;
                    align-items: center;
                    gap: 8px;
                    background-color: $accentColor;
                    color: #FFFFFF;
                    text-decoration: none;
                    font-weight: 700;
                    font-size: 13px;
                    padding: 10px 18px;
                    border-radius: 8px;
                    transition: background-color 0.2s;
                    flex-shrink: 0;
                }
                .download-btn:hover {
                    background-color: #115E59;
                }
                .footer {
                    margin-top: 40px;
                    text-align: center;
                    font-size: 12px;
                    font-weight: 500;
                    color: #94A3B8;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <span class="app-pill">⚡ Direct Local Link</span>
                    <h1>Wi-Fi Drop Download</h1>
                    <p class="subtitle">These files are ready for instant download on your local network.</p>
                </div>
                <div class="item-list">
                    $listItemsHtml
                </div>
                <div class="footer">
                    Powered by Wi-Fi Drop Web-Share Engine & Ktor.
                </div>
            </div>
        </body>
        </html>
        """.trimIndent()
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1] + ""
        return String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
    }

    private fun escapeHtml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
    }

    open fun stop() {
        server?.stop(500, 1000)
        server = null
    }
}
