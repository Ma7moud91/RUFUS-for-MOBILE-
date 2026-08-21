package com.example.util

import android.content.Context
import com.example.usb.filesystem.Fat12FloppyParser
import com.example.usb.filesystem.InjectedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class BootloaderManager(private val context: Context) {

    companion object {
        // TODO: Maintainer: Update pinned download URLs and SHA-256 checksums per official upstream releases
        const val EDK2_SHELL_URL = "https://github.com/pbatard/UEFI-Shell/releases/download/24H2/Shell_Full.efi"
        const val EDK2_SHELL_SHA256 = "c081e69da5b34924775d71c828d119fae1ae8c9a33bb3571d7943c2cbe0a9058"

        const val FREEDOS_FDBOOT_URL = "https://www.ibiblio.org/pub/micro/pc-stuff/freedos/files/distributions/1.3/official/FD13-FloppyEdition/fdboot.img"
        const val FREEDOS_FDBOOT_SHA256 = "d6e6ea3dbb30fcb7bc1c9a622a59a43588970e5b565a587c48529f7cf479860b"
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
        val cachedFile = File(bootloaderDir, "uefishell.efi")

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
            context.assets.open("bootloaders/uefishell.efi").use { stream ->
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
     * Retrieves FreeDOS payload files (KERNEL.SYS, COMMAND.COM, and config files) by downloading
     * and parsing the official fdboot.img floppy image in memory.
     */
    suspend fun getFreeDosPayloadFiles(
        isCancelled: () -> Boolean,
        onProgress: suspend (Int, String) -> Unit
    ): Result<List<InjectedFile>> = withContext(Dispatchers.IO) {
        val cachedImageFile = File(bootloaderDir, "fdboot.img")
        var imageBytes: ByteArray? = null

        // 1. Check local cache
        if (cachedImageFile.exists() && cachedImageFile.length() > 0) {
            val cachedBytes = cachedImageFile.readBytes()
            val cachedHash = computeSha256(cachedBytes)
            if (cachedHash.equals(FREEDOS_FDBOOT_SHA256, ignoreCase = true)) {
                onProgress(100, "Loaded cached FreeDOS fdboot.img (SHA-256 verified)")
                imageBytes = cachedBytes
            }
        }

        // 2. Download if not cached
        if (imageBytes == null) {
            onProgress(0, "Downloading FreeDOS 1.3 floppy image (fdboot.img)...")
            val downloadResult = downloadWithHashPin(
                urlString = FREEDOS_FDBOOT_URL,
                expectedSha256 = FREEDOS_FDBOOT_SHA256,
                displayName = "FreeDOS fdboot.img",
                isCancelled = isCancelled,
                onProgress = onProgress
            )

            if (downloadResult.isFailure) {
                return@withContext Result.failure(downloadResult.exceptionOrNull() ?: Exception("Failed downloading FreeDOS image"))
            }
            val downloadedBytes = downloadResult.getOrThrow()
            try {
                cachedImageFile.writeBytes(downloadedBytes)
            } catch (e: Exception) {}
            imageBytes = downloadedBytes
        }

        onProgress(90, "Extracting FreeDOS kernel and system files from floppy image...")
        val extractedFiles = Fat12FloppyParser.extractAllFiles(imageBytes)

        val kernelSys = extractedFiles["KERNEL.SYS"]
        val commandCom = extractedFiles["COMMAND.COM"]

        if (kernelSys == null || commandCom == null) {
            return@withContext Result.failure(
                IllegalStateException("FreeDOS floppy image missing required KERNEL.SYS or COMMAND.COM files.")
            )
        }

        // Generate boot configuration files
        val autoexecBat = "@echo off\r\nSET DOSDIR=\\\r\nSET PATH=\\;\\BIN\r\necho Welcome to FreeDOS (Rufus Bootable USB)\r\n".toByteArray(Charsets.US_ASCII)
        val configSys = "SWITCHES=/F\r\nDOS=HIGH,UMB\r\nDOSDATA=UMB\r\nDEVICE=\\KERNEL.SYS\r\nSHELL=\\COMMAND.COM /E:1024 /P\r\n".toByteArray(Charsets.US_ASCII)
        val fdConfigSys = "SWITCHES=/F\r\nDOS=HIGH,UMB\r\nDOSDATA=UMB\r\nDEVICE=\\KERNEL.SYS\r\nSHELL=\\COMMAND.COM /E:1024 /P\r\n".toByteArray(Charsets.US_ASCII)

        val payloadList = listOf(
            InjectedFile(fileName83 = "KERNEL.SYS", content = kernelSys),
            InjectedFile(fileName83 = "COMMAND.COM", content = commandCom),
            InjectedFile(fileName83 = "AUTOEXEC.BAT", content = autoexecBat),
            InjectedFile(fileName83 = "CONFIG.SYS", content = configSys),
            InjectedFile(fileName83 = "FDCONFIG.SYS", content = fdConfigSys)
        )

        onProgress(100, "Extracted ${payloadList.size} FreeDOS files successfully")
        return@withContext Result.success(payloadList)
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
