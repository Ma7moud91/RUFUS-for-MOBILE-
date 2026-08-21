package com.example.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

class BootloaderManager(private val context: Context) {

    companion object {
        // TODO: Maintainer: Update pinned download URLs and SHA-256 checksums per official upstream releases
        const val EDK2_SHELL_URL = "https://github.com/pbatard/UEFI-Shell/releases/download/26H1/shellx64.efi"
        const val EDK2_SHELL_SHA256 = "4ea080ddd576117cd04f5c02d16712ea5d9249c0752214d8e4055e460d7b11e0"

        const val FREEDOS_LITEUSB_URL = "https://www.ibiblio.org/pub/micro/pc-stuff/freedos/files/distributions/1.3/official/FD13-LiteUSB.zip"
        const val FREEDOS_LITEUSB_SHA256 = "64a934585087ccd91a18c55e20ee01f5f6762be712eeaa5f456be543778f9f7e"
    }

    private val bootloaderDir: File
        get() = File(context.filesDir, "bootloaders").apply { if (!exists()) mkdirs() }

    /**
     * Retrieves the official EDK2 UEFI Shell binary (x64), verifying SHA-256.
     */
    suspend fun getUefiShellBinary(
        isCancelled: () -> Boolean,
        onProgress: suspend (Int, String) -> Unit
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        val cachedFile = File(bootloaderDir, "shellx64.efi")

        // 1. Check local cache
        if (cachedFile.exists() && cachedFile.length() > 0) {
            val cachedBytes = cachedFile.readBytes()
            val cachedHash = computeSha256(cachedBytes)
            if (cachedHash.equals(EDK2_SHELL_SHA256, ignoreCase = true)) {
                onProgress(100, "Loaded cached UEFI Shell (SHA-256 verified)")
                return@withContext Result.success(cachedBytes)
            }
        }

        // 2. Check bundled asset fallback
        try {
            val assetStream = try {
                context.assets.open("bootloaders/shellx64.efi")
            } catch (e: Exception) {
                context.assets.open("bootloaders/uefishell.efi")
            }
            assetStream.use { stream ->
                val assetBytes = stream.readBytes()
                if (assetBytes.size > 512) {
                    val assetHash = computeSha256(assetBytes)
                    if (assetHash.equals(EDK2_SHELL_SHA256, ignoreCase = true)) {
                        cachedFile.writeBytes(assetBytes)
                        onProgress(100, "Loaded bundled UEFI Shell from assets (SHA-256 verified)")
                        return@withContext Result.success(assetBytes)
                    }
                }
            }
        } catch (e: Exception) {
            // Asset not bundled, proceed to download
        }

        // 3. Download from official repository
        onProgress(0, "Downloading UEFI Shell x64 release from official repository...")
        val downloadResult = downloadWithHashPin(
            urlString = EDK2_SHELL_URL,
            expectedSha256 = EDK2_SHELL_SHA256,
            displayName = "UEFI Shell",
            isCancelled = isCancelled,
            onProgress = onProgress
        )

        downloadResult.onSuccess { bytes ->
            try {
                cachedFile.writeBytes(bytes)
            } catch (e: Exception) {}
        }

        return@withContext downloadResult
    }

    /**
     * Retrieves official FreeDOS 1.3 LiteUSB disk image (FD13LITE.img) by downloading and extracting
     * the official FD13-LiteUSB.zip archive in memory, verifying size (32MB) and SHA-256 integrity.
     */
    suspend fun getFreeDosUsbImage(
        isCancelled: () -> Boolean,
        onProgress: suspend (Int, String) -> Unit
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        val cachedImageFile = File(bootloaderDir, "FD13LITE.img")
        val expectedExtractedSha256 = "e6b9a2e7694d92209ea3ab2a99ca820de1bc9fe3dd3360350b6a0103967bf58b"
        val expectedExtractedSize = 33554432L // Exactly 32 MB

        // 1. Check local cache
        if (cachedImageFile.exists() && cachedImageFile.length() == expectedExtractedSize) {
            val cachedBytes = cachedImageFile.readBytes()
            val cachedHash = computeSha256(cachedBytes)
            if (cachedHash.equals(expectedExtractedSha256, ignoreCase = true)) {
                onProgress(100, "Loaded cached FreeDOS 1.3 LiteUSB image (SHA-256 verified)")
                return@withContext Result.success(cachedBytes)
            }
        }

        // 2. Download zip via downloadWithHashPin
        onProgress(0, "Downloading FreeDOS 1.3 LiteUSB distribution archive...")
        val zipResult = downloadWithHashPin(
            urlString = FREEDOS_LITEUSB_URL,
            expectedSha256 = FREEDOS_LITEUSB_SHA256,
            displayName = "FreeDOS 1.3 LiteUSB zip",
            isCancelled = isCancelled,
            onProgress = onProgress
        )

        if (zipResult.isFailure) {
            return@withContext Result.failure(zipResult.exceptionOrNull() ?: Exception("Failed downloading FreeDOS archive"))
        }

        if (isCancelled()) {
            return@withContext Result.failure(IllegalStateException("Operation cancelled by user."))
        }

        onProgress(90, "Extracting FD13LITE.img from archive...")
        val zipBytes = zipResult.getOrThrow()
        var extractedImgBytes: ByteArray? = null

        try {
            ZipInputStream(zipBytes.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name.equals("FD13LITE.img", ignoreCase = true) ||
                        name.endsWith("/FD13LITE.img", ignoreCase = true) ||
                        name.endsWith("\\FD13LITE.img", ignoreCase = true)
                    ) {
                        val baos = ByteArrayOutputStream()
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        while (zis.read(buf).also { read = it } > 0) {
                            if (isCancelled()) {
                                return@withContext Result.failure(IllegalStateException("Operation cancelled by user."))
                            }
                            baos.write(buf, 0, read)
                        }
                        extractedImgBytes = baos.toByteArray()
                        break
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            return@withContext Result.failure(IllegalStateException("Failed extracting FD13LITE.img: ${e.message}", e))
        }

        if (extractedImgBytes == null) {
            return@withContext Result.failure(IllegalStateException("Archive does not contain FD13LITE.img"))
        }

        val extracted = extractedImgBytes!!
        if (extracted.size.toLong() != expectedExtractedSize) {
            return@withContext Result.failure(
                IllegalStateException("FD13LITE.img size mismatch: expected $expectedExtractedSize bytes, got ${extracted.size} bytes")
            )
        }

        val imgHash = computeSha256(extracted)
        if (!imgHash.equals(expectedExtractedSha256, ignoreCase = true)) {
            return@withContext Result.failure(
                SecurityException("FD13LITE.img SHA-256 verification failed! Expected: $expectedExtractedSha256, got: $imgHash")
            )
        }

        try {
            cachedImageFile.writeBytes(extracted)
        } catch (e: Exception) {}

        onProgress(100, "FreeDOS 1.3 LiteUSB image verified successfully (${extracted.size} bytes)")
        Result.success(extracted)
    }

    private suspend fun downloadWithHashPin(
        urlString: String,
        expectedSha256: String,
        displayName: String,
        isCancelled: () -> Boolean,
        onProgress: suspend (Int, String) -> Unit
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        try {
            var currentUrl = urlString
            var redirectCount = 0
            while (redirectCount < 5) {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 15000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Rufus-Android-Engine/4.5")
                }

                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == 307 || status == 308
                ) {
                    val newUrl = connection.getHeaderField("Location") ?: break
                    connection.disconnect()
                    currentUrl = newUrl
                    redirectCount++
                    continue
                }
                break
            }

            if (connection == null || connection.responseCode != HttpURLConnection.HTTP_OK) {
                val code = connection?.responseCode ?: -1
                return@withContext Result.failure(
                    IllegalStateException("Failed to download $displayName (HTTP response code: $code). Please check network connection.")
                )
            }

            val totalBytes = connection.contentLength.toLong()
            inputStream = connection.inputStream
            val outputStream = ByteArrayOutputStream()
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(32 * 1024)
            var bytesReadTotal = 0L

            var lastReportTime = System.currentTimeMillis()

            while (!isCancelled()) {
                val read = inputStream.read(buffer)
                if (read <= 0) break

                outputStream.write(buffer, 0, read)
                digest.update(buffer, 0, read)
                bytesReadTotal += read

                val now = System.currentTimeMillis()
                if (now - lastReportTime >= 200) {
                    lastReportTime = now
                    val pct = if (totalBytes > 0) {
                        ((bytesReadTotal.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 99)
                    } else {
                        50
                    }
                    val mbRead = bytesReadTotal / (1024.0 * 1024.0)
                    onProgress(pct, "Downloading $displayName ($pct% - ${String.format("%.1f", mbRead)} MB)...")
                }
            }

            if (isCancelled()) {
                return@withContext Result.failure(IllegalStateException("Download cancelled by user."))
            }

            val downloadedBytes = outputStream.toByteArray()
            val calculatedSha256 = digest.digest().joinToString("") { "%02x".format(it) }

            if (!expectedSha256.equals(calculatedSha256, ignoreCase = true)) {
                return@withContext Result.failure(
                    SecurityException(
                        "SHA-256 verification failed for $displayName! Expected: $expectedSha256, Got: $calculatedSha256. Downloaded data rejected."
                    )
                )
            }

            onProgress(100, "$displayName downloaded and verified successfully (${downloadedBytes.size} bytes).")
            return@withContext Result.success(downloadedBytes)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        } finally {
            try { inputStream?.close() } catch (e: Exception) {}
            try { connection?.disconnect() } catch (e: Exception) {}
        }
    }

    private fun computeSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
