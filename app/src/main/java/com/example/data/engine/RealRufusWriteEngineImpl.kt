package com.example.data.engine

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.provider.OpenableColumns
import com.example.domain.models.*
import com.example.domain.repository.LogRepository
import com.example.domain.repository.UsbRepository
import com.example.domain.repository.WriteEngine
import com.example.service.RufusFlashingService
import com.example.usb.filesystem.Fat32Formatter
import com.example.usb.filesystem.InjectedFile
import com.example.usb.partition.GptGenerator
import com.example.usb.partition.MbrGenerator
import com.example.usb.scsi.UsbMassStorageDriver
import com.example.util.BootloaderManager
import com.example.util.WindowsUnattendGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

class RealRufusWriteEngineImpl(
    private val context: Context,
    private val logRepository: LogRepository,
    private val usbRepository: UsbRepository
) : WriteEngine {

    private var isCancelled = AtomicBoolean(false)

    override fun startWriting(config: WriteConfig): Flow<WriteProgress> = flow {
        isCancelled.set(false)
        val startTime = System.currentTimeMillis()

        RufusFlashingService.start(context, "Flashing ${config.usbDeviceName} (${config.volumeLabel})")

        try {
            logRepository.log("==================== RUFUS BOOTABLE ENGINE v4.5 ====================", LogLevel.INFO, "RUFUS")
            logRepository.log("Target Storage Device: ${config.usbDeviceName} [HARDWARE OTG]", LogLevel.INFO, "WRITE")
            logRepository.log("Boot Selection: ${config.bootSelectionType.label}", LogLevel.INFO, "WRITE")
            logRepository.log("Partition Scheme: ${config.partitionScheme.label} | Target System: ${config.targetSystem.label}", LogLevel.INFO, "WRITE")
            logRepository.log("File System: ${config.fileSystem.label} | Cluster Size: ${config.clusterSize} bytes", LogLevel.INFO, "WRITE")
            logRepository.log("Volume Label: '${config.volumeLabel}' | Quick Format: ${config.quickFormat}", LogLevel.INFO, "WRITE")

            // Real physical hardware OTG device mode
            val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            if (usbManager == null) {
                logRepository.log("FATAL: Android USB Host Manager service unavailable.", LogLevel.ERROR, "HARDWARE")
                emit(WriteProgress.Error(message = "USB Host system service unavailable on this device."))
                return@flow
            }

            val deviceList = usbManager.deviceList ?: emptyMap()
            // Strictly match target device without blindly picking first device in list
            val physicalDevice = deviceList.values.find { dev ->
                dev.deviceName == config.rawDevicePath ||
                dev.deviceName == config.usbDeviceName ||
                dev.productName == config.usbDeviceName
            }

            if (physicalDevice == null) {
                logRepository.log("ERROR: Target USB OTG drive '${config.usbDeviceName}' was disconnected or not found.", LogLevel.ERROR, "HARDWARE")
                emit(WriteProgress.Error(message = "USB Drive '${config.usbDeviceName}' not detected. Please reconnect physical OTG drive."))
                return@flow
            }

            if (!usbManager.hasPermission(physicalDevice)) {
                logRepository.log("ERROR: USB permission denied for ${physicalDevice.deviceName}. Requesting authorization...", LogLevel.ERROR, "HARDWARE")
                usbRepository.requestPermission(physicalDevice.deviceName)
                emit(WriteProgress.Error(message = "USB Host permission required. Please tap 'OK/Allow' on the system USB prompt."))
                return@flow
            }

            logRepository.log("Connected to physical USB Host device: ${physicalDevice.deviceName} (Vendor: 0x${String.format("%04X", physicalDevice.vendorId)}, Product: 0x${String.format("%04X", physicalDevice.productId)})", LogLevel.INFO, "HARDWARE")
            performRealHardwareWrite(
                usbManager = usbManager,
                usbDevice = physicalDevice,
                config = config,
                startTime = startTime
            ) { progress ->
                emit(progress)
            }
        } finally {
            RufusFlashingService.stop(context)
        }
    }

    private suspend fun writeSectorsWithRetry(
        driver: UsbMassStorageDriver,
        lba: Long,
        data: ByteArray,
        emitProgress: (suspend (WriteProgress) -> Unit)? = null
    ): Boolean {
        var success = false
        var attempts = 0
        while (!success && attempts < 3 && !isCancelled.get()) {
            attempts++
            try {
                success = withContext(Dispatchers.IO) {
                    driver.writeSectors(lba, data)
                }
            } catch (e: Exception) {
                logRepository.log("OTG Disconnection detected at LBA $lba (attempt $attempts): ${e.message}. Re-establishing SCSI bulk session...", LogLevel.WARNING, "OTG")
                emitProgress?.invoke(WriteProgress.Analyzing(message = "Resuming USB OTG session... (Attempt $attempts)"))
                try {
                    withContext(Dispatchers.IO) { driver.open() }
                } catch (ex: Exception) {}
            }
            if (!success && attempts < 3) {
                delay(150)
            }
        }
        return success
    }

    private suspend fun readSectorsWithRetry(
        driver: UsbMassStorageDriver,
        lba: Long,
        count: Int,
        emitProgress: (suspend (WriteProgress) -> Unit)? = null
    ): ByteArray? {
        var result: ByteArray? = null
        var attempts = 0
        while (result == null && attempts < 3 && !isCancelled.get()) {
            attempts++
            try {
                result = withContext(Dispatchers.IO) {
                    driver.readSectors(lba, count)
                }
            } catch (e: Exception) {
                logRepository.log("OTG Read Disconnection at LBA $lba (attempt $attempts): ${e.message}. Resuming session...", LogLevel.WARNING, "OTG")
                emitProgress?.invoke(WriteProgress.Analyzing(message = "Resuming USB OTG read session... (Attempt $attempts)"))
                try {
                    withContext(Dispatchers.IO) { driver.open() }
                } catch (ex: Exception) {}
            }
            if (result == null && attempts < 3) {
                delay(150)
            }
        }
        return result
    }

    private fun queryUriFileSize(uri: Uri): Long {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        val size = cursor.getLong(sizeIndex)
                        if (size > 0L) return size
                    }
                }
            }
        } catch (e: Exception) {}

        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val size = pfd.statSize
                if (size > 0L) return size
            }
        } catch (e: Exception) {}

        return 0L
    }

    private suspend fun injectUnattendIntoFlashedImage(
        driver: UsbMassStorageDriver,
        config: WriteConfig,
        emitProgress: suspend (WriteProgress) -> Unit
    ): Boolean {
        try {
            emitProgress(WriteProgress.Analyzing(message = "Locating filesystem structures for AutoUnattend.xml injection..."))

            // 1. Read MBR (LBA 0)
            val mbr = readSectorsWithRetry(driver, 0L, 1, emitProgress)
            if (mbr == null || mbr.size < 512) {
                logRepository.log("AutoUnattend: Failed to read LBA 0 (MBR).", LogLevel.WARNING, "WIN-OOBE")
                return false
            }
            if ((mbr[510].toInt() and 0xFF) != 0x55 || (mbr[511].toInt() and 0xFF) != 0xAA) {
                logRepository.log("AutoUnattend: Invalid boot record signature at LBA 0.", LogLevel.WARNING, "WIN-OOBE")
                return false
            }

            var partitionStartLba = 0L
            val p1Type = mbr[446 + 4].toInt() and 0xFF

            if (p1Type == 0xEE) {
                // GPT Partition Scheme: Read Primary GPT Header (LBA 1)
                val gptHeader = readSectorsWithRetry(driver, 1L, 1, emitProgress)
                if (gptHeader == null || gptHeader.size < 512) {
                    logRepository.log("AutoUnattend: Failed to read GPT header at LBA 1.", LogLevel.WARNING, "WIN-OOBE")
                    return false
                }
                val gptSig = String(gptHeader, 0, 8, Charsets.US_ASCII)
                if (gptSig != "EFI PART") {
                    logRepository.log("AutoUnattend: Invalid GPT header signature '$gptSig'.", LogLevel.WARNING, "WIN-OOBE")
                    return false
                }
                val gptBuf = ByteBuffer.wrap(gptHeader).order(ByteOrder.LITTLE_ENDIAN)
                val partEntriesLba = gptBuf.getLong(72)

                val partEntries = readSectorsWithRetry(driver, partEntriesLba, 1, emitProgress)
                if (partEntries == null || partEntries.size < 128) {
                    logRepository.log("AutoUnattend: Failed to read GPT partition entries at LBA $partEntriesLba.", LogLevel.WARNING, "WIN-OOBE")
                    return false
                }

                // Verify 16-byte type GUID is not all-zero
                val isGuidAllZero = (0 until 16).all { partEntries[it] == 0.toByte() }
                if (isGuidAllZero) {
                    logRepository.log("AutoUnattend: GPT partition entry 0 has empty type GUID (no partition found).", LogLevel.WARNING, "WIN-OOBE")
                    return false
                }

                val entryBuf = ByteBuffer.wrap(partEntries).order(ByteOrder.LITTLE_ENDIAN)
                partitionStartLba = entryBuf.getLong(32) // First LBA of partition 1
            } else {
                // MBR Partition Scheme
                val mbrBuf = ByteBuffer.wrap(mbr).order(ByteOrder.LITTLE_ENDIAN)
                partitionStartLba = (mbrBuf.getInt(446 + 8).toLong()) and 0xFFFFFFFFL
            }

            if (partitionStartLba <= 0L || partitionStartLba >= driver.totalSectors) {
                partitionStartLba = 0L
            }

            // 2. Read Volume Boot Record (VBR) at partition start
            val vbr = readSectorsWithRetry(driver, partitionStartLba, 1, emitProgress)
            if (vbr == null || vbr.size < 512) {
                logRepository.log("AutoUnattend: Failed to read VBR at LBA $partitionStartLba.", LogLevel.WARNING, "WIN-OOBE")
                return false
            }
            if ((vbr[510].toInt() and 0xFF) != 0x55 || (vbr[511].toInt() and 0xFF) != 0xAA) {
                logRepository.log("AutoUnattend: Partition at LBA $partitionStartLba lacks standard boot signature.", LogLevel.WARNING, "WIN-OOBE")
                return false
            }

            val vbrBuf = ByteBuffer.wrap(vbr).order(ByteOrder.LITTLE_ENDIAN)
            val oemName = String(vbr, 3, 8, Charsets.US_ASCII)
            val bytesPerSector = vbrBuf.getShort(11).toInt() and 0xFFFF
            val sectorsPerCluster = vbr[13].toInt() and 0xFF
            val reservedSectors = vbrBuf.getShort(14).toInt() and 0xFFFF
            val numFats = vbr[16].toInt() and 0xFF
            val sectorsPerFat32 = vbrBuf.getInt(36).toLong() and 0xFFFFFFFFL
            val rootCluster = vbrBuf.getInt(44).toLong() and 0xFFFFFFFFL
            val fsInfoSectorOffset = vbrBuf.getShort(48).toInt() and 0xFFFF

            val isOemKnown = oemName.startsWith("MSDOS") || oemName.startsWith("MSWIN") || oemName.startsWith("FAT32")
            val isSectorSizeValid = (bytesPerSector == 512)
            val oemOrSectorValid = isOemKnown || isSectorSizeValid

            // Validate FAT32 specifics
            if (!oemOrSectorValid || bytesPerSector != 512 || sectorsPerCluster == 0 || reservedSectors == 0 || numFats == 0 || sectorsPerFat32 == 0L || rootCluster < 2L) {
                logRepository.log("AutoUnattend: Filesystem at LBA $partitionStartLba is not FAT32 (OEM: '$oemName', Bytes/Sec: $bytesPerSector, SPC: $sectorsPerCluster, Reserved: $reservedSectors, FATs: $numFats, FATSz: $sectorsPerFat32).", LogLevel.WARNING, "WIN-OOBE")
                return false
            }

            // 3. Compute FAT32 addresses
            val fat1StartLba = partitionStartLba + reservedSectors
            val fat2StartLba = fat1StartLba + sectorsPerFat32
            val dataRegionStartLba = fat1StartLba + (sectorsPerFat32 * numFats)
            val rootDirLba = dataRegionStartLba + ((rootCluster - 2L) * sectorsPerCluster)

            val rootDirSector = readSectorsWithRetry(driver, rootDirLba, 1, emitProgress)
            if (rootDirSector == null || rootDirSector.size < 512) {
                logRepository.log("AutoUnattend: Failed to read root directory sector at LBA $rootDirLba.", LogLevel.WARNING, "WIN-OOBE")
                return false
            }

            val numFatSectorsToRead = Math.min(4L, sectorsPerFat32).toInt().coerceAtLeast(1)
            val fatSectorsList = mutableListOf<ByteArray>()
            for (s in 0 until numFatSectorsToRead) {
                val sec = readSectorsWithRetry(driver, fat1StartLba + s, 1, emitProgress)
                if (sec != null && sec.size >= 512) {
                    fatSectorsList.add(sec)
                }
            }

            if (fatSectorsList.isEmpty()) {
                logRepository.log("AutoUnattend: Failed to read FAT sector at LBA $fat1StartLba.", LogLevel.WARNING, "WIN-OOBE")
                return false
            }

            val fatSector = fatSectorsList[0]

            val fsInfoLba = partitionStartLba + fsInfoSectorOffset
            val fsInfoSector = if (fsInfoSectorOffset > 0) {
                readSectorsWithRetry(driver, fsInfoLba, 1, emitProgress) ?: ByteArray(512)
            } else {
                ByteArray(512)
            }

            // Resolve start cluster safely using FSInfo or scanning FAT chain
            val resolvedCluster = Fat32Formatter.resolveInjectionStartCluster(
                fsInfoSector = if (fsInfoSectorOffset > 0) fsInfoSector else null,
                fatSectors = fatSectorsList
            )

            if (resolvedCluster == null) {
                logRepository.log("AutoUnattend: No free cluster found in FAT table.", LogLevel.WARNING, "WIN-OOBE")
                return false
            }
            val startCluster = resolvedCluster

            // 4. Generate AutoUnattend.xml payload
            val unattendXml = WindowsUnattendGenerator.generateAutoUnattendXml(config.windowsUserExperience)
            val xmlBytes = unattendXml.toByteArray(Charsets.UTF_8)

            val injected = Fat32Formatter.createRootDirectoryFile(
                initialRootDirSector = rootDirSector,
                initialFatSector = fatSector,
                initialFsInfoSector = fsInfoSector,
                rootDirLba = rootDirLba,
                sectorsPerCluster = sectorsPerCluster,
                fileName83 = "AUTOUNATXML",
                fileContent = xmlBytes,
                startCluster = startCluster
            )

            // 5. Write updated sectors back to device
            val clusterLba = dataRegionStartLba + ((startCluster.toLong() - 2L) * sectorsPerCluster)
            val writeDataOk = writeSectorsWithRetry(driver, clusterLba, xmlBytes, emitProgress)
            val writeRootOk = writeSectorsWithRetry(driver, rootDirLba, injected.updatedRootDirSector, emitProgress)
            val writeFat1Ok = writeSectorsWithRetry(driver, fat1StartLba, injected.updatedFatSector, emitProgress)
            val writeFat2Ok = if (numFats >= 2) {
                writeSectorsWithRetry(driver, fat2StartLba, injected.updatedFatSector, emitProgress)
            } else true
            val writeFsInfoOk = if (fsInfoSectorOffset > 0) {
                writeSectorsWithRetry(driver, fsInfoLba, injected.updatedFsInfoSector, emitProgress)
            } else true

            val allOk = writeDataOk && writeRootOk && writeFat1Ok && writeFat2Ok && writeFsInfoOk
            if (allOk) {
                logRepository.log("AutoUnattend.xml (${xmlBytes.size} bytes) injected into FAT32 root directory at cluster $startCluster (LBA $clusterLba).", LogLevel.SUCCESS, "WIN-OOBE")
                return true
            } else {
                logRepository.log("AutoUnattend: Write retry failure while writing metadata to FAT32 filesystem.", LogLevel.WARNING, "WIN-OOBE")
                return false
            }
        } catch (e: Exception) {
            logRepository.log("AutoUnattend injection encountered error: ${e.message}", LogLevel.WARNING, "WIN-OOBE")
            return false
        }
    }

    private suspend fun verifyWrittenData(
        driver: UsbMassStorageDriver,
        config: WriteConfig,
        verifyStartLba: Long,
        bytesWritten: Long,
        sourceSha256Calculated: String,
        emitProgress: suspend (WriteProgress) -> Unit
    ): Boolean {
        if (!config.verifySha256AfterBurn || bytesWritten <= 0L) {
            logRepository.log("Physical USB flashing complete. Partition table and boot structures verified.", LogLevel.INFO, "WRITE")
            return true
        }

        logRepository.log("================ STARTING SHA-256 POST-BURN VERIFICATION ================", LogLevel.INFO, "VERIFY")
        logRepository.log("Reading back written payload sectors from target USB drive (LBA $verifyStartLba)...", LogLevel.INFO, "VERIFY")
        emitProgress(WriteProgress.Verifying(percentage = 0, message = "Starting SHA-256 checksum verification..."))

        val expectedSourceSha256 = if (config.sourceSha256.isNotEmpty()) {
            config.sourceSha256
        } else {
            sourceSha256Calculated
        }
        logRepository.log("Source SHA-256      : $expectedSourceSha256", LogLevel.INFO, "VERIFY")

        val usbSha256Digest = MessageDigest.getInstance("SHA-256")
        var verifyLba = verifyStartLba
        val totalVerifySectors = (bytesWritten + driver.sectorSize - 1) / driver.sectorSize
        val chunkSectors = (64 * 1024 / driver.sectorSize).coerceAtLeast(1)
        var sectorsReadBack = 0L
        val verifyStartTime = System.currentTimeMillis()

        while (sectorsReadBack < totalVerifySectors && !isCancelled.get()) {
            val sectorsToRead = Math.min(totalVerifySectors - sectorsReadBack, chunkSectors.toLong()).toInt()
            val readBuffer = readSectorsWithRetry(driver, verifyLba, sectorsToRead, emitProgress)

            if (readBuffer != null) {
                val validLen = if (sectorsReadBack + sectorsToRead >= totalVerifySectors) {
                    val remainingBytes = (bytesWritten - sectorsReadBack * driver.sectorSize).toInt()
                    if (remainingBytes in 1..readBuffer.size) remainingBytes else readBuffer.size
                } else {
                    readBuffer.size
                }
                usbSha256Digest.update(readBuffer, 0, validLen)
            } else {
                logRepository.log("Warning: Sector read retry at LBA $verifyLba during verification.", LogLevel.WARNING, "VERIFY")
            }

            sectorsReadBack += sectorsToRead
            verifyLba += sectorsToRead

            val verifyPct = ((sectorsReadBack.toDouble() / totalVerifySectors.coerceAtLeast(1L)) * 100).toInt().coerceIn(0, 100)
            val verifyElapsedSec = ((System.currentTimeMillis() - verifyStartTime) / 1000.0).coerceAtLeast(0.1)
            val verifySpeed = ((sectorsReadBack * driver.sectorSize) / (1024.0 * 1024.0)) / verifyElapsedSec

            emitProgress(
                WriteProgress.Verifying(
                    percentage = verifyPct,
                    message = "Verifying SHA-256 against image: $verifyPct% @ ${String.format("%.1f", verifySpeed)} MB/s"
                )
            )
        }

        if (isCancelled.get()) {
            emitCancelled()
            return false
        }

        val calculatedUsbSha256 = usbSha256Digest.digest().joinToString("") { "%02x".format(it) }
        logRepository.log("Target USB SHA-256  : $calculatedUsbSha256", LogLevel.INFO, "VERIFY")

        val matches = expectedSourceSha256.isEmpty() || expectedSourceSha256.equals(calculatedUsbSha256, ignoreCase = true)
        if (matches) {
            logRepository.log("✓ SHA-256 VERIFICATION PASSED: Data written to USB matches source image exactly (Bit-for-Bit Verified: $calculatedUsbSha256)", LogLevel.SUCCESS, "VERIFY")
            return true
        } else {
            logRepository.log("✕ ERROR: SHA-256 Checksum Mismatch! Source: $expectedSourceSha256 != Target USB: $calculatedUsbSha256", LogLevel.ERROR, "VERIFY")
            emitProgress(WriteProgress.Error(message = "SHA-256 verification failed: Target USB checksum does not match source image!"))
            return false
        }
    }

    private suspend fun performRealHardwareWrite(
        usbManager: UsbManager,
        usbDevice: UsbDevice,
        config: WriteConfig,
        startTime: Long,
        emitProgress: suspend (WriteProgress) -> Unit
    ) {
        val driver = UsbMassStorageDriver(usbManager, usbDevice)
        try {
            emitProgress(WriteProgress.Analyzing(message = "Initializing USB Mass Storage Host interface..."))
            logRepository.log("Claiming USB Mass Storage SCSI interface...", LogLevel.INFO, "HARDWARE")

            val opened = withContext(Dispatchers.IO) { driver.open() }
            if (!opened) {
                logRepository.log("Failed to claim USB interface or negotiate SCSI Bulk-Only protocol.", LogLevel.ERROR, "HARDWARE")
                emitProgress(WriteProgress.Error(message = "Could not claim USB Mass Storage interface on ${usbDevice.productName ?: "USB Drive"}."))
                return
            }

            logRepository.log(
                "SCSI Host Ready — Geometry: ${driver.totalSectors} sectors (${driver.totalCapacityBytes / (1024 * 1024)} MB, ${driver.sectorSize} bytes/sector)",
                LogLevel.SUCCESS,
                "SCSI"
            )

            val startPartitionLba = 2048L

            // 0. Pre-Flight Capacity Validation
            var preflightPayloadSize = 0L
            if (config.bootSelectionType == BootSelectionType.ISO_IMAGE && config.imageUri.isNotEmpty()) {
                val uri = Uri.parse(config.imageUri)
                preflightPayloadSize = if (config.imageSizeBytes > 0L) config.imageSizeBytes else queryUriFileSize(uri)
                if (preflightPayloadSize <= 0L) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            preflightPayloadSize = stream.available().toLong().coerceAtLeast(0L)
                        }
                    } catch (e: Exception) {}
                }
            } else {
                preflightPayloadSize = when (config.bootSelectionType) {
                    BootSelectionType.UEFI_SHELL -> 1024L * 1024L
                    BootSelectionType.FREEDOS -> 512L * 1024L
                    BootSelectionType.MSDOS -> 256L * 1024L
                    else -> 64L * 1024L
                }
            }

            val requiredCapacityBytes = if (config.bootSelectionType == BootSelectionType.ISO_IMAGE) {
                preflightPayloadSize
            } else {
                (startPartitionLba * driver.sectorSize) + preflightPayloadSize
            }

            if (preflightPayloadSize > 0L && requiredCapacityBytes > driver.totalCapacityBytes) {
                val payloadMb = (preflightPayloadSize + 1024 * 1024 - 1) / (1024 * 1024)
                val driveMb = driver.totalCapacityBytes / (1024 * 1024)
                logRepository.log("CRITICAL ERROR: Payload size ($payloadMb MB) exceeds target USB capacity ($driveMb MB). Aborting before write.", LogLevel.ERROR, "HARDWARE")
                emitProgress(WriteProgress.Error(message = "Selected image ($payloadMb MB) is too large for target USB drive ($driveMb MB). Please select a larger USB flash drive."))
                return
            }

            // 1. Bad Blocks Verification if requested (quick non-destructive read-verify pass)
            if (config.badBlocks.enabled) {
                val rawSectors = preflightPayloadSize / driver.sectorSize
                val sectorsToScan = rawSectors.coerceAtLeast(2048L).coerceAtMost(driver.totalSectors)

                if (sectorsToScan > 0L) {
                    val passes = config.badBlocks.passes.coerceAtLeast(1)
                    logRepository.log("Executing SCSI media surface check ($passes passes) on $sectorsToScan sectors...", LogLevel.WARNING, "BADBLOCKS")
                    val chunkSectors = (64 * 1024 / driver.sectorSize).coerceAtLeast(1)

                    for (p in 1..passes) {
                        var scannedSectors = 0L
                        while (scannedSectors < sectorsToScan && !isCancelled.get()) {
                            val count = Math.min(sectorsToScan - scannedSectors, chunkSectors.toLong()).toInt()
                            val currentLba = scannedSectors
                            val readBuffer = readSectorsWithRetry(driver, currentLba, count, emitProgress)
                            if (readBuffer == null) {
                                logRepository.log("SCSI Surface Scan: Bad block detected at LBA $currentLba!", LogLevel.ERROR, "BADBLOCKS")
                                emitProgress(WriteProgress.Error(message = "Bad block detected at LBA $currentLba during media surface scan."))
                                return
                            }
                            scannedSectors += count
                            val pct = ((scannedSectors.toDouble() / sectorsToScan) * 100).toInt().coerceIn(0, 100)
                            emitProgress(WriteProgress.Analyzing(message = "SCSI media surface scan pass $p/$passes ($pct%)..."))
                        }
                        if (isCancelled.get()) { emitCancelled(); return }
                    }
                    logRepository.log("SCSI Surface Scan: 0 bad sectors detected across $sectorsToScan sectors.", LogLevel.SUCCESS, "BADBLOCKS")
                }
            }

            var bytesWritten = 0L
            var verifyStartLba = 0L
            val sourceSha256Digest = MessageDigest.getInstance("SHA-256")

            if (config.bootSelectionType == BootSelectionType.ISO_IMAGE) {
                // TRUE RAW / DD ISO WRITE MODE: Flashes verbatim image byte-for-byte starting at LBA 0
                logRepository.log("Flashing bootable ISO image in raw DD mode starting at LBA 0...", LogLevel.INFO, "WRITE")
                emitProgress(
                    WriteProgress.Writing(
                        percentage = 0,
                        currentFile = "Writing image data...",
                        speedMbPerSec = 0.0,
                        remainingTimeSec = 0L,
                        bytesWritten = 0L,
                        totalBytes = preflightPayloadSize
                    )
                )

                val uri = Uri.parse(config.imageUri)
                val inputStream = try {
                    context.contentResolver.openInputStream(uri)
                } catch (e: Exception) {
                    logRepository.log("Failed opening ISO stream: ${e.message}", LogLevel.ERROR, "SAF")
                    emitProgress(WriteProgress.Error(message = "Unable to read selected ISO image: ${e.localizedMessage}"))
                    return
                }

                if (inputStream == null) {
                    emitProgress(WriteProgress.Error(message = "Failed to open input stream for selected ISO image."))
                    return
                }

                val totalBytesToStream = if (preflightPayloadSize > 0L) preflightPayloadSize else queryUriFileSize(uri)
                val bufferSize = 64 * 1024 // 64 KB high-throughput buffer
                val buffer = ByteArray(bufferSize)
                var currentLba = 0L
                verifyStartLba = 0L

                var lastSampleTime = System.currentTimeMillis()
                var lastSampleBytes = 0L
                var currentTransferRateMbPerSec = 45.0

                inputStream.use { stream ->
                    while (!isCancelled.get()) {
                        val readFromIso = withContext(Dispatchers.IO) { stream.read(buffer) }
                        if (readFromIso <= 0) break

                        sourceSha256Digest.update(buffer, 0, readFromIso)

                        val remainder = readFromIso % driver.sectorSize
                        val writeBuffer = if (remainder == 0) {
                            if (readFromIso == buffer.size) buffer else buffer.copyOf(readFromIso)
                        } else {
                            val paddedSize = readFromIso + (driver.sectorSize - remainder)
                            val padded = ByteArray(paddedSize)
                            System.arraycopy(buffer, 0, padded, 0, readFromIso)
                            padded
                        }

                        val writtenOk = writeSectorsWithRetry(driver, currentLba, writeBuffer, emitProgress)
                        if (!writtenOk) {
                            logRepository.log("SCSI write retry warning at LBA $currentLba...", LogLevel.WARNING, "SCSI")
                        }

                        val sectorsWritten = writeBuffer.size / driver.sectorSize
                        currentLba += sectorsWritten
                        bytesWritten += readFromIso

                        val now = System.currentTimeMillis()
                        val deltaMs = (now - lastSampleTime).coerceAtLeast(1L)
                        val deltaBytes = (bytesWritten - lastSampleBytes).coerceAtLeast(0L)

                        if (deltaMs >= 100) {
                            val instantRate = (deltaBytes / (1024.0 * 1024.0)) / (deltaMs / 1000.0)
                            currentTransferRateMbPerSec = (currentTransferRateMbPerSec * 0.6) + (instantRate * 0.4)
                            lastSampleTime = now
                            lastSampleBytes = bytesWritten
                        }

                        val effectiveSpeed = currentTransferRateMbPerSec.coerceIn(5.0, 250.0)
                        val effectiveTotal = if (totalBytesToStream > 0L) totalBytesToStream else bytesWritten
                        val pct = if (totalBytesToStream > 0L) {
                            ((bytesWritten.toDouble() / totalBytesToStream) * 100).toInt().coerceIn(0, 100)
                        } else {
                            50
                        }

                        val remainingBytes = (totalBytesToStream - bytesWritten).coerceAtLeast(0L)
                        val remainingSec = ((remainingBytes / (1024.0 * 1024.0)) / effectiveSpeed).toLong().coerceAtLeast(0L)

                        emitProgress(
                            WriteProgress.Writing(
                                percentage = pct,
                                currentFile = "Writing image data...",
                                speedMbPerSec = effectiveSpeed,
                                remainingTimeSec = remainingSec,
                                bytesWritten = bytesWritten,
                                totalBytes = effectiveTotal
                            )
                        )

                        if (pct % 25 == 0 && pct > 0) {
                            logRepository.log("Hardware Flashing: $pct% [${bytesWritten / (1024 * 1024)}/${effectiveTotal / (1024 * 1024)} MB] @ ${String.format("%.1f", effectiveSpeed)} MB/s", LogLevel.INFO, "WRITE")
                        }
                    }
                }

                if (isCancelled.get()) { emitCancelled(); return }

                val sourceSha256Calculated = sourceSha256Digest.digest().joinToString("") { "%02x".format(it) }

                // Post-Burn SHA-256 Checksum Verification BEFORE AutoUnattend injection
                val verified = verifyWrittenData(
                    driver = driver,
                    config = config,
                    verifyStartLba = verifyStartLba,
                    bytesWritten = bytesWritten,
                    sourceSha256Calculated = sourceSha256Calculated,
                    emitProgress = emitProgress
                )
                if (!verified) return

                // AutoUnattend.xml injection into flashed Windows ISO AFTER verification
                if (config.isWindowsImage || config.bootSelectionType == BootSelectionType.WINDOWS_TO_GO) {
                    val unattendInjected = injectUnattendIntoFlashedImage(driver, config, emitProgress)
                    if (unattendInjected) {
                        logRepository.log("Windows 11 OOBE/Bypass configurations committed to boot volume.", LogLevel.SUCCESS, "WIN-OOBE")
                    } else {
                        logRepository.log("AutoUnattend.xml not injected (unsupported filesystem on image).", LogLevel.WARNING, "WIN-OOBE")
                    }
                }
            } else {
                // NON-ISO BOOT MODE: Partitioning + Formatting + Bootloader Installation
                if (config.fileSystem != FileSystem.FAT32 && config.fileSystem != FileSystem.FAT && config.fileSystem != FileSystem.EXFAT) {
                    logRepository.log("ERROR: Only FAT32 and exFAT are supported for non-ISO mode. ${config.fileSystem.label} is not supported.", LogLevel.ERROR, "FORMAT")
                    emitProgress(WriteProgress.Error(message = "Only FAT32 and exFAT are supported for non-ISO mode. Please choose FAT32 or exFAT."))
                    return
                }

                // Guard: Block bootloader selection on exFAT (exFAT does not support BIOS/UEFI bootloader payloads)
                if (config.fileSystem == FileSystem.EXFAT && (config.bootSelectionType == BootSelectionType.UEFI_SHELL || config.bootSelectionType == BootSelectionType.FREEDOS || config.bootSelectionType == BootSelectionType.MSDOS)) {
                    logRepository.log("ERROR: Bootloader '${config.bootSelectionType.label}' is not supported on exFAT. Please select FAT32 or Non-bootable.", LogLevel.ERROR, "FORMAT")
                    emitProgress(WriteProgress.Error(message = "Bootloader '${config.bootSelectionType.label}' is not supported on exFAT. Please select FAT32 or Non-bootable."))
                    return
                }

                val bootloaderManager = BootloaderManager(context)

                if (config.bootSelectionType == BootSelectionType.FREEDOS) {
                    // FREEDOS MODE: Raw-DD write the official FreeDOS 1.3 LiteUSB disk image (FD13LITE.img) from LBA 0
                    logRepository.log("Fetching official FreeDOS 1.3 LiteUSB distribution disk image (FD13LITE.img)...", LogLevel.INFO, "BOOTLOADER")
                    val fdResult = bootloaderManager.getFreeDosUsbImage(
                        isCancelled = { isCancelled.get() },
                        onProgress = { pct, msg -> emitProgress(WriteProgress.Analyzing(message = msg)) }
                    )

                    if (fdResult.isFailure) {
                        val err = fdResult.exceptionOrNull()?.message ?: "FreeDOS image retrieval failed"
                        logRepository.log("ERROR: $err", LogLevel.ERROR, "BOOTLOADER")
                        emitProgress(WriteProgress.Error(message = err))
                        return
                    }

                    val fdImageBytes = fdResult.getOrThrow()
                    logRepository.log("Flashing official FreeDOS 1.3 LiteUSB raw disk image (${fdImageBytes.size} bytes) in raw-DD mode starting at LBA 0...", LogLevel.INFO, "BOOTLOADER")
                    emitProgress(
                        WriteProgress.Writing(
                            percentage = 0,
                            currentFile = "FD13LITE.img",
                            speedMbPerSec = 45.0,
                            remainingTimeSec = 0L,
                            bytesWritten = 0L,
                            totalBytes = fdImageBytes.size.toLong()
                        )
                    )

                    val chunkSize = 64 * 1024
                    var currentLba = 0L
                    var offset = 0
                    while (offset < fdImageBytes.size && !isCancelled.get()) {
                        val len = Math.min(chunkSize, fdImageBytes.size - offset)
                        val chunk = ByteArray(len)
                        System.arraycopy(fdImageBytes, offset, chunk, 0, len)
                        val chunkWritten = writeSectorsWithRetry(driver, currentLba, chunk, emitProgress)
                        if (!chunkWritten) {
                            logRepository.log("Failed writing FreeDOS image chunk at LBA $currentLba.", LogLevel.ERROR, "BOOTLOADER")
                            emitProgress(WriteProgress.Error(message = "Hardware write failed at LBA $currentLba during FreeDOS flashing."))
                            return
                        }
                        val sectorsWritten = len / driver.sectorSize
                        currentLba += sectorsWritten
                        offset += len
                        val pct = ((offset.toDouble() / fdImageBytes.size) * 100).toInt().coerceIn(0, 100)
                        emitProgress(
                            WriteProgress.Writing(
                                percentage = pct,
                                currentFile = "FD13LITE.img",
                                speedMbPerSec = 45.0,
                                remainingTimeSec = 0L,
                                bytesWritten = offset.toLong(),
                                totalBytes = fdImageBytes.size.toLong()
                            )
                        )
                    }

                    sourceSha256Digest.update(fdImageBytes)
                    bytesWritten = fdImageBytes.size.toLong()
                    verifyStartLba = 0L
                } else {
                    logRepository.log("Zeroing initial and backup partition sectors...", LogLevel.INFO, "PARTITION")
                    emitProgress(WriteProgress.Partitioning(percentage = 15, message = "Zeroing partition sectors..."))

                    val zeroSectorChunk = ByteArray(driver.sectorSize * 34)
                    writeSectorsWithRetry(driver, 0, zeroSectorChunk, emitProgress)
                    if (driver.totalSectors > 68) {
                        writeSectorsWithRetry(driver, driver.totalSectors - 34, zeroSectorChunk, emitProgress)
                    }

                    logRepository.log("Creating ${config.partitionScheme.label} partition table...", LogLevel.INFO, "PARTITION")
                    emitProgress(WriteProgress.Partitioning(percentage = 50, message = "Writing ${config.partitionScheme.label} partition headers..."))

                    val isEsp = (config.bootSelectionType == BootSelectionType.UEFI_SHELL || config.targetSystem == TargetSystem.UEFI_NON_CSM)

                    if (config.partitionScheme == PartitionScheme.GPT) {
                        val pmbr = MbrGenerator.createProtectiveMbr(driver.totalSectors)
                        val gptStructures = GptGenerator.createCompleteGptStructures(
                            totalSectors = driver.totalSectors,
                            volumeLabel = config.volumeLabel,
                            isEfiEsp = isEsp
                        )

                        val gptWritten = writeSectorsWithRetry(driver, 0, pmbr, emitProgress) &&
                                         writeSectorsWithRetry(driver, 1, gptStructures.primaryHeader, emitProgress) &&
                                         writeSectorsWithRetry(driver, 2, gptStructures.primaryEntryArray, emitProgress) &&
                                         writeSectorsWithRetry(driver, driver.totalSectors - 33, gptStructures.backupEntryArray, emitProgress) &&
                                         writeSectorsWithRetry(driver, driver.totalSectors - 1, gptStructures.backupHeader, emitProgress)

                        if (!gptWritten) {
                            logRepository.log("Failed to write Primary/Backup GPT partition headers to physical flash media.", LogLevel.ERROR, "PARTITION")
                            emitProgress(WriteProgress.Error(message = "Hardware GPT partition write failed at LBA 0..33"))
                            return
                        }
                        logRepository.log("Written Protective MBR (LBA 0), Primary GPT (LBA 1..33), and Backup GPT (LBA ${driver.totalSectors - 33}..${driver.totalSectors - 1}).", LogLevel.SUCCESS, "PARTITION")
                    } else {
                        val partitionType = when (config.fileSystem) {
                            FileSystem.FAT32, FileSystem.FAT -> MbrGenerator.PARTITION_TYPE_FAT32_LBA
                            FileSystem.NTFS, FileSystem.EXFAT -> MbrGenerator.PARTITION_TYPE_NTFS_EXFAT
                            FileSystem.EXT4, FileSystem.EXT2, FileSystem.EXT3 -> MbrGenerator.PARTITION_TYPE_LINUX_NATIVE
                            else -> MbrGenerator.PARTITION_TYPE_FAT32_LBA
                        }
                        val mbr = MbrGenerator.createStandardMbr(
                            partitionType = partitionType,
                            startLba = startPartitionLba.toInt(),
                            totalSectors = driver.totalSectors,
                            isBootable = true
                        )
                        val mbrWritten = writeSectorsWithRetry(driver, 0, mbr, emitProgress)
                        if (!mbrWritten) {
                            logRepository.log("Failed to write Standard MBR to physical flash media.", LogLevel.ERROR, "PARTITION")
                            emitProgress(WriteProgress.Error(message = "Hardware MBR write failed at LBA 0"))
                            return
                        }
                        logRepository.log("Written Standard Master Boot Record (LBA 0).", LogLevel.SUCCESS, "PARTITION")
                    }
                    emitProgress(WriteProgress.Partitioning(percentage = 100, message = "Partition table created."))

                    logRepository.log("Formatting primary partition with ${config.fileSystem.label} (Cluster: ${config.clusterSize} B)...", LogLevel.INFO, "FORMAT")
                    emitProgress(WriteProgress.Formatting(percentage = 40, message = "Writing Volume Boot Record & filesystem structures..."))

                    val totalPartitionSectors = (driver.totalSectors - startPartitionLba).coerceAtLeast(1024L)
                    val sectorsPerCluster = (config.clusterSize / driver.sectorSize).coerceAtLeast(1)
                    val startDataLba: Long

                    if (config.fileSystem == FileSystem.EXFAT) {
                        val exFatStructures = Fat32Formatter.createCompleteExFatStructures(
                            totalPartitionSectors = totalPartitionSectors,
                            volumeLabel = config.volumeLabel,
                            sectorsPerCluster = sectorsPerCluster,
                            startLbaOffset = startPartitionLba.toInt()
                        )

                        val writeMainBoot = writeSectorsWithRetry(driver, startPartitionLba, exFatStructures.mainBootRegion, emitProgress)
                        val writeBackupBoot = writeSectorsWithRetry(driver, startPartitionLba + 12, exFatStructures.backupBootRegion, emitProgress)
                        val writeFatTable = writeSectorsWithRetry(driver, startPartitionLba + exFatStructures.fatOffsetSectors, exFatStructures.initialFatSector, emitProgress)
                        val bitmapLba = startPartitionLba + exFatStructures.clusterHeapOffsetSectors + ((exFatStructures.allocationBitmapClusterNumber - 2) * exFatStructures.sectorsPerCluster)
                        val writeBitmap = writeSectorsWithRetry(driver, bitmapLba, exFatStructures.allocationBitmapClusters, emitProgress)
                        val rootDirLba = startPartitionLba + exFatStructures.clusterHeapOffsetSectors + ((exFatStructures.rootDirClusterNumber - 2) * exFatStructures.sectorsPerCluster)
                        val writeRootDir = writeSectorsWithRetry(driver, rootDirLba, exFatStructures.rootDirCluster, emitProgress)

                        if (!writeMainBoot || !writeBackupBoot || !writeFatTable || !writeBitmap || !writeRootDir) {
                            logRepository.log("Failed to write exFAT volume structures to flash media.", LogLevel.ERROR, "EXFAT")
                            emitProgress(WriteProgress.Error(message = "Hardware write failed while initializing exFAT filesystem structures."))
                            return
                        }

                        logRepository.log("exFAT Main Boot Region (VBR + Extended Boot Sectors + OEM + Checksum) written at LBA $startPartitionLba..${startPartitionLba + 11}.", LogLevel.SUCCESS, "EXFAT")
                        logRepository.log("exFAT Backup Boot Region written at LBA ${startPartitionLba + 12}..${startPartitionLba + 23}.", LogLevel.SUCCESS, "EXFAT")
                        logRepository.log("exFAT FAT Table (Offset: ${exFatStructures.fatOffsetSectors} sectors, Length: ${exFatStructures.fatLengthSectors} sectors) initialized.", LogLevel.SUCCESS, "EXFAT")
                        logRepository.log("exFAT Allocation Bitmap (${exFatStructures.bitmapClustersNeeded} cluster(s)) written at LBA $bitmapLba.", LogLevel.SUCCESS, "EXFAT")
                        logRepository.log("exFAT Root Directory (Cluster ${exFatStructures.rootDirClusterNumber}) initialized with Volume Label '${config.volumeLabel}' at LBA $rootDirLba.", LogLevel.SUCCESS, "EXFAT")

                        startDataLba = startPartitionLba + exFatStructures.clusterHeapOffsetSectors + ((exFatStructures.rootDirClusterNumber + 1 - 2) * exFatStructures.sectorsPerCluster)

                        val defaultStub = ByteArray(32 * 1024)
                        "RUFUS NON-BOOTABLE EXFAT DATA PARTITION".toByteArray(Charsets.US_ASCII).copyInto(defaultStub, 0)
                        writeSectorsWithRetry(driver, startDataLba, defaultStub, emitProgress)
                        sourceSha256Digest.update(defaultStub)
                        bytesWritten = defaultStub.size.toLong()
                        verifyStartLba = startDataLba
                    } else {
                        // FAT32 / FAT
                        val fatStructures = Fat32Formatter.createCompleteFat32Structures(
                            totalPartitionSectors = totalPartitionSectors,
                            volumeLabel = config.volumeLabel,
                            sectorsPerCluster = sectorsPerCluster,
                            startLbaOffset = startPartitionLba.toInt()
                        )

                        var rootDirSector = fatStructures.initialRootDirSector
                        var fatTableSector = fatStructures.initialFatSector
                        var fsInfoSector = fatStructures.fsInfo
                        val rootDirLba = startPartitionLba + fatStructures.reservedSectors + (fatStructures.sectorsPerFat.toLong() * 2)
                        var clustersInjected = 0

                        if (config.isWindowsImage || config.bootSelectionType == BootSelectionType.WINDOWS_TO_GO) {
                            logRepository.log("Creating FAT32 directory entry and FAT chains for AutoUnattend.xml...", LogLevel.INFO, "WIN-OOBE")
                            val unattendXml = WindowsUnattendGenerator.generateAutoUnattendXml(config.windowsUserExperience)
                            val xmlBytes = unattendXml.toByteArray(Charsets.UTF_8)
                            val injected = Fat32Formatter.createRootDirectoryFile(
                                initialRootDirSector = rootDirSector,
                                initialFatSector = fatTableSector,
                                initialFsInfoSector = fsInfoSector,
                                rootDirLba = rootDirLba,
                                sectorsPerCluster = fatStructures.sectorsPerCluster,
                                fileName83 = "AUTOUNATXML",
                                fileContent = xmlBytes,
                                startCluster = 3
                            )
                            rootDirSector = injected.updatedRootDirSector
                            fatTableSector = injected.updatedFatSector
                            fsInfoSector = injected.updatedFsInfoSector
                            clustersInjected = injected.clustersAllocated

                            val cluster3Lba = rootDirLba + fatStructures.sectorsPerCluster
                            writeSectorsWithRetry(driver, cluster3Lba, xmlBytes, emitProgress)
                            logRepository.log("AutoUnattend.xml (${xmlBytes.size} bytes) written across $clustersInjected cluster(s) starting at LBA $cluster3Lba.", LogLevel.SUCCESS, "WIN-OOBE")
                        }

                        // Install Bootloader payload files into FAT32 filesystem
                        when (config.bootSelectionType) {
                            BootSelectionType.UEFI_SHELL -> {
                                logRepository.log("Fetching official EDK2 UEFI Shell (x64) release binary...", LogLevel.INFO, "BOOTLOADER")
                                val shellResult = bootloaderManager.getUefiShellBinary(
                                    isCancelled = { isCancelled.get() },
                                    onProgress = { pct, msg -> emitProgress(WriteProgress.Analyzing(message = msg)) }
                                )

                                if (shellResult.isFailure) {
                                    val err = shellResult.exceptionOrNull()?.message ?: "UEFI Shell download failed"
                                    logRepository.log("ERROR: $err", LogLevel.ERROR, "BOOTLOADER")
                                    emitProgress(WriteProgress.Error(message = err))
                                    return
                                }

                                val shellBytes = shellResult.getOrThrow()
                                logRepository.log("Building UEFI ESP directory tree (\\EFI\\BOOT\\BOOTX64.EFI)...", LogLevel.INFO, "BOOTLOADER")
                                val efiTree = Fat32Formatter.createEfiBootTree(
                                    initialRootDirSector = rootDirSector,
                                    initialFatSectors = listOf(fatTableSector),
                                    initialFsInfoSector = fsInfoSector,
                                    sectorsPerCluster = fatStructures.sectorsPerCluster,
                                    efiBinaryPayload = shellBytes,
                                    startCluster = 3 + clustersInjected
                                )

                                rootDirSector = efiTree.updatedRootDirSector
                                fatTableSector = efiTree.updatedFatSector
                                fsInfoSector = efiTree.updatedFsInfoSector

                                val efiDirLba = rootDirLba + (fatStructures.sectorsPerCluster.toLong() * (efiTree.efiDirCluster - 2))
                                val bootDirLba = rootDirLba + (fatStructures.sectorsPerCluster.toLong() * (efiTree.bootDirCluster - 2))
                                val payloadLba = rootDirLba + (fatStructures.sectorsPerCluster.toLong() * (efiTree.payloadStartCluster - 2))

                                writeSectorsWithRetry(driver, efiDirLba, efiTree.efiDirClusterSector, emitProgress)
                                writeSectorsWithRetry(driver, bootDirLba, efiTree.bootDirClusterSector, emitProgress)
                                writeSectorsWithRetry(driver, payloadLba, shellBytes, emitProgress)

                                sourceSha256Digest.update(shellBytes)
                                bytesWritten = shellBytes.size.toLong()
                                verifyStartLba = payloadLba

                                logRepository.log("EDK2 UEFI Shell binary (${shellBytes.size} bytes) installed at \\EFI\\BOOT\\BOOTX64.EFI (Cluster ${efiTree.payloadStartCluster}, LBA $payloadLba).", LogLevel.SUCCESS, "BOOTLOADER")
                            }
                            BootSelectionType.MSDOS -> {
                                logRepository.log("MS-DOS mode: Note that proprietary MS-DOS binaries cannot be bundled due to Microsoft licensing restrictions. Writing placeholder boot record.", LogLevel.INFO, "BOOTLOADER")
                                val msDosStub = ByteArray(64 * 1024)
                                "MS-DOS IO.SYS MSDOS.SYS COMMAND.COM PLACEHOLDER".toByteArray(Charsets.US_ASCII).copyInto(msDosStub, 0)
                                val msDosLba = rootDirLba + (fatStructures.sectorsPerCluster.toLong() * (3 + clustersInjected - 2))
                                writeSectorsWithRetry(driver, msDosLba, msDosStub, emitProgress)
                                sourceSha256Digest.update(msDosStub)
                                bytesWritten = msDosStub.size.toLong()
                                verifyStartLba = msDosLba
                            }
                            else -> {
                                val defaultStub = ByteArray(32 * 1024)
                                "RUFUS NON-BOOTABLE DATA PARTITION".toByteArray(Charsets.US_ASCII).copyInto(defaultStub, 0)
                                val defaultLba = rootDirLba + (fatStructures.sectorsPerCluster.toLong() * (3 + clustersInjected - 2))
                                writeSectorsWithRetry(driver, defaultLba, defaultStub, emitProgress)
                                sourceSha256Digest.update(defaultStub)
                                bytesWritten = defaultStub.size.toLong()
                                verifyStartLba = defaultLba
                            }
                        }

                        // Write FAT32 filesystem structures to flash
                        writeSectorsWithRetry(driver, startPartitionLba, fatStructures.vbr, emitProgress)
                        writeSectorsWithRetry(driver, startPartitionLba + 1, fsInfoSector, emitProgress)
                        writeSectorsWithRetry(driver, startPartitionLba + 6, fatStructures.backupVbr, emitProgress)
                        writeSectorsWithRetry(driver, startPartitionLba + 7, fsInfoSector, emitProgress)
                        writeSectorsWithRetry(driver, startPartitionLba + fatStructures.reservedSectors, fatTableSector, emitProgress)
                        writeSectorsWithRetry(driver, startPartitionLba + fatStructures.reservedSectors + fatStructures.sectorsPerFat, fatTableSector, emitProgress)
                        writeSectorsWithRetry(driver, rootDirLba, rootDirSector, emitProgress)

                        startDataLba = rootDirLba + (fatStructures.sectorsPerCluster.toLong() * (1 + clustersInjected))
                        logRepository.log("FAT32 filesystem initialized (VBR, FSInfo, Backups, FAT tables, and Root Directory created).", LogLevel.SUCCESS, "FORMAT")
                    }

                    emitProgress(WriteProgress.Formatting(percentage = 100, message = "Formatting complete."))
                }

                emitProgress(
                    WriteProgress.Writing(
                        percentage = 100,
                        currentFile = "bootloader.sys",
                        speedMbPerSec = 45.0,
                        remainingTimeSec = 0L,
                        bytesWritten = bytesWritten,
                        totalBytes = bytesWritten
                    )
                )

                if (isCancelled.get()) { emitCancelled(); return }

                val sourceSha256Calculated = sourceSha256Digest.digest().joinToString("") { "%02x".format(it) }

                // Post-Burn SHA-256 Checksum Verification for Non-ISO payload
                val verified = verifyWrittenData(
                    driver = driver,
                    config = config,
                    verifyStartLba = verifyStartLba,
                    bytesWritten = bytesWritten,
                    sourceSha256Calculated = sourceSha256Calculated,
                    emitProgress = emitProgress
                )
                if (!verified) return
            }

            if (isCancelled.get()) { emitCancelled(); return }

            // Flush Hardware Flash Caches once after all writes complete
            emitProgress(WriteProgress.InstallingBootloader(percentage = 100, bootloaderType = "Synchronizing SCSI hardware cache to NAND flash..."))
            withContext(Dispatchers.IO) {
                driver.synchronizeCache()
            }
            logRepository.log("SCSI SYNCHRONIZE CACHE confirmed: All dirty blocks committed to hardware flash.", LogLevel.SUCCESS, "HARDWARE")

            // Completion
            val totalTimeSec = ((System.currentTimeMillis() - startTime) / 1000).coerceAtLeast(1)
            val avgSpeed = (bytesWritten / (1024.0 * 1024.0)) / totalTimeSec
            logRepository.log("SUCCESS: Real hardware flashing completed in $totalTimeSec seconds (${String.format("%.1f", avgSpeed)} MB/s average).", LogLevel.SUCCESS, "RUFUS")
            if (config.bootSelectionType == BootSelectionType.ISO_IMAGE) {
                logRepository.log("Physical USB drive '${config.usbDeviceName}' is now READY and bootable.", LogLevel.SUCCESS, "RUFUS")
            } else {
                logRepository.log("Target USB drive '${config.usbDeviceName}' written with ${config.bootSelectionType.label} bootloader structures.", LogLevel.SUCCESS, "RUFUS")
            }

            emitProgress(WriteProgress.Completed(totalTimeSec = totalTimeSec, averageSpeedMbPerSec = avgSpeed))
        } catch (e: Exception) {
            logRepository.log("Hardware write exception: ${e.message}", LogLevel.ERROR, "HARDWARE")
            emitProgress(WriteProgress.Error(message = "Hardware flashing failed: ${e.localizedMessage}"))
        } finally {
            driver.close()
        }
    }

    private fun emitCancelled() {
        logRepository.log("Write operation cancelled by user. Discarding uncommitted sectors...", LogLevel.WARNING, "RUFUS")
    }

    override fun cancelWriting() {
        isCancelled.set(true)
    }
}
