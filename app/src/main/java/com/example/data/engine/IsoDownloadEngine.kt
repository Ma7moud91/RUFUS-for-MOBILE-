package com.example.data.engine

import android.content.Context
import android.os.Environment
import com.example.domain.models.FileSystem
import com.example.domain.models.ImageFile
import com.example.domain.models.IsoDownloadItem
import com.example.domain.models.PartitionScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed class IsoDownloadState {
    object Idle : IsoDownloadState()
    data class Connecting(val item: IsoDownloadItem) : IsoDownloadState()
    data class Progress(
        val item: IsoDownloadItem,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val percent: Int,
        val speedBytesPerSec: Long,
        val speedFormatted: String,
        val etaSeconds: Long,
        val tempFilePath: String
    ) : IsoDownloadState()
    data class Completed(
        val item: IsoDownloadItem,
        val downloadedImage: ImageFile,
        val file: File,
        val sha256Calculated: String,
        val totalBytes: Long
    ) : IsoDownloadState()
    data class Error(val item: IsoDownloadItem?, val message: String) : IsoDownloadState()
    object Cancelled : IsoDownloadState()
}

class IsoDownloadEngine(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {

    private var activeCall: okhttp3.Call? = null
    private var isCancelled = false

    fun cancelDownload() {
        isCancelled = true
        activeCall?.cancel()
    }

    fun downloadIso(item: IsoDownloadItem, customUrl: String? = null): Flow<IsoDownloadState> = flow {
        isCancelled = false
        emit(IsoDownloadState.Connecting(item))

        val targetUrl = customUrl?.takeIf { it.isNotBlank() } ?: item.downloadUrl

        // Determine destination file
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }

        val sanitizedName = item.id.replace(Regex("[^a-zA-Z0-9._-]"), "_") + ".iso"
        val destinationFile = File(downloadsDir, sanitizedName)
        val tempFile = File(downloadsDir, "$sanitizedName.download")

        if (tempFile.exists()) {
            tempFile.delete()
        }

        try {
            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "Rufus-Android/1.0 (Linux; Android) DownloadManager")
                .header("Accept", "*/*")
                .build()

            val call = client.newCall(request)
            activeCall = call

            val response = call.execute()
            if (!response.isSuccessful) {
                emit(IsoDownloadState.Error(item, "HTTP ${response.code}: ${response.message}"))
                return@flow
            }

            val body = response.body
            if (body == null) {
                emit(IsoDownloadState.Error(item, "Server returned an empty response body."))
                return@flow
            }

            val totalBytes = body.contentLength().takeIf { it > 0 } ?: (item.approximateSize.let {
                // Approximate fallback if chunked transfer
                when {
                    it.contains("GB", ignoreCase = true) -> (it.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 1.0) * 1024 * 1024 * 1024
                    it.contains("MB", ignoreCase = true) -> (it.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 500.0) * 1024 * 1024
                    else -> 100 * 1024 * 1024.0
                }.toLong()
            })

            val inputStream: InputStream = body.byteStream()
            val outputStream = FileOutputStream(tempFile)
            val digest = MessageDigest.getInstance("SHA-256")

            val buffer = ByteArray(64 * 1024) // 64 KB buffer
            var bytesReadTotal = 0L
            var lastUpdateTime = System.currentTimeMillis()
            var bytesSinceLastUpdate = 0L
            var currentSpeed = 0L

            inputStream.use { input ->
                outputStream.use { output ->
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        if (isCancelled || !currentCoroutineContext().isActive) {
                            tempFile.delete()
                            emit(IsoDownloadState.Cancelled)
                            return@flow
                        }

                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)

                        bytesReadTotal += read
                        bytesSinceLastUpdate += read

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastUpdateTime
                        if (elapsed >= 400) {
                            currentSpeed = (bytesSinceLastUpdate * 1000L) / elapsed
                            val speedFormatted = formatSpeed(currentSpeed)
                            val remainingBytes = (totalBytes - bytesReadTotal).coerceAtLeast(0)
                            val eta = if (currentSpeed > 0) remainingBytes / currentSpeed else 0L
                            val percent = if (totalBytes > 0) {
                                ((bytesReadTotal * 100) / totalBytes).toInt().coerceIn(0, 99)
                            } else {
                                50
                            }

                            emit(
                                IsoDownloadState.Progress(
                                    item = item,
                                    downloadedBytes = bytesReadTotal,
                                    totalBytes = totalBytes,
                                    percent = percent,
                                    speedBytesPerSec = currentSpeed,
                                    speedFormatted = speedFormatted,
                                    etaSeconds = eta,
                                    tempFilePath = tempFile.absolutePath
                                )
                            )

                            lastUpdateTime = now
                            bytesSinceLastUpdate = 0L
                        }
                    }
                }
            }

            // Rename temp to target
            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            tempFile.renameTo(destinationFile)

            val calculatedHash = digest.digest().joinToString("") { "%02x".format(it) }

            val imageFile = ImageFile(
                uriString = destinationFile.toURI().toString(),
                fileName = destinationFile.name,
                sizeBytes = destinationFile.length(),
                hashSha256 = calculatedHash,
                osDetection = "${item.title} (${item.release})",
                architecture = item.architecture,
                isWindows = item.osFamily.equals("Windows", ignoreCase = true),
                isLinux = item.osFamily.equals("Linux", ignoreCase = true),
                isDos = item.osFamily.equals("DOS/UEFI", ignoreCase = true) || item.osFamily.equals("DOS", ignoreCase = true),
                isUefiShell = item.osFamily.equals("UEFI", ignoreCase = true),
                recommendedPartitionScheme = if (item.osFamily.equals("Windows", ignoreCase = true) || item.osFamily.equals("Linux", ignoreCase = true)) PartitionScheme.GPT else PartitionScheme.MBR,
                recommendedFileSystem = if (item.osFamily.equals("Windows", ignoreCase = true)) FileSystem.NTFS else FileSystem.FAT32,
                isPreset = false
            )

            emit(
                IsoDownloadState.Completed(
                    item = item,
                    downloadedImage = imageFile,
                    file = destinationFile,
                    sha256Calculated = calculatedHash,
                    totalBytes = destinationFile.length()
                )
            )

        } catch (e: Exception) {
            if (isCancelled) {
                tempFile.delete()
                emit(IsoDownloadState.Cancelled)
            } else {
                emit(IsoDownloadState.Error(item, "Download failed: ${e.localizedMessage ?: e.message}"))
            }
        }
    }.flowOn(Dispatchers.IO)

    fun scanLocalDownloadedIsos(): List<ImageFile> {
        val results = mutableListOf<ImageFile>()
        val dirsToScan = listOfNotNull(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        )

        for (dir in dirsToScan) {
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles { f ->
                    val name = f.name.lowercase()
                    f.isFile && (name.endsWith(".iso") || name.endsWith(".img") || name.endsWith(".raw") || name.endsWith(".vhd"))
                } ?: emptyArray()

                for (f in files) {
                    val name = f.name
                    val lower = name.lowercase()
                    val isWin = lower.contains("win") || lower.contains("windows")
                    val isLinux = lower.contains("ubuntu") || lower.contains("debian") || lower.contains("arch") || lower.contains("linux") || lower.contains("fedora")
                    val isDos = lower.contains("freedos") || lower.contains("msdos") || lower.contains("fd13")

                    results.add(
                        ImageFile(
                            uriString = f.toURI().toString(),
                            fileName = f.name,
                            sizeBytes = f.length(),
                            osDetection = when {
                                isWin -> "Windows Installation Media"
                                isLinux -> "Linux Distribution Image"
                                isDos -> "DOS / Utilities Image"
                                else -> "Bootable Disk Image"
                            },
                            architecture = if (lower.contains("x86") || lower.contains("32")) "x86" else "x86_64",
                            isWindows = isWin,
                            isLinux = isLinux,
                            isDos = isDos,
                            recommendedPartitionScheme = if (isWin || isLinux) PartitionScheme.GPT else PartitionScheme.MBR,
                            recommendedFileSystem = if (isWin) FileSystem.NTFS else FileSystem.FAT32,
                            isPreset = false
                        )
                    )
                }
            }
        }
        return results.distinctBy { it.fileName }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        val mb = bytesPerSec / (1024.0 * 1024.0)
        if (mb >= 1.0) {
            return String.format("%.2f MB/s", mb)
        }
        val kb = bytesPerSec / 1024.0
        if (kb >= 1.0) {
            return String.format("%.1f KB/s", kb)
        }
        return "$bytesPerSec B/s"
    }
}
