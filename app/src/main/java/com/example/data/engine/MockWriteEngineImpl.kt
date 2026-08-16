package com.example.data.engine

import com.example.domain.models.*
import com.example.domain.repository.LogRepository
import com.example.domain.repository.WriteEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

class MockWriteEngineImpl(
    private val logRepository: LogRepository
) : WriteEngine {
    private var isCancelled = AtomicBoolean(false)

    override fun startWriting(config: WriteConfig): Flow<WriteProgress> = flow {
        isCancelled.set(false)
        val startTime = System.currentTimeMillis()

        logRepository.log("==================== RUFUS BOOTABLE ENGINE v4.5 ====================", LogLevel.INFO, "RUFUS")
        logRepository.log("Target Storage Device: ${config.usbDeviceName}", LogLevel.INFO, "WRITE")
        logRepository.log("Boot Selection Mode: ${config.bootSelectionType.label} (${config.bootSelectionType.description})", LogLevel.INFO, "WRITE")
        logRepository.log("Partition Scheme: ${config.partitionScheme.label} | Target System: ${config.targetSystem.label}", LogLevel.INFO, "WRITE")
        logRepository.log("File System: ${config.fileSystem.label} | Cluster Size: ${config.clusterSize} bytes", LogLevel.INFO, "WRITE")
        logRepository.log("Volume Label: '${config.volumeLabel}' | Quick Format: ${config.quickFormat}", LogLevel.INFO, "WRITE")

        // 1. Analyzing Boot Image / Selection
        emit(WriteProgress.Analyzing("Analyzing image filesystem structure and boot signatures..."))
        logRepository.log("Scanning target media and boot sectors...", LogLevel.INFO, "ANALYZE")
        delay(600)
        if (isCancelled.get()) { emitCancelled(); return@flow }

        // Bad Blocks / Fake Drive Check
        if (config.badBlocks.enabled) {
            val passes = config.badBlocks.passes
            logRepository.log("Beginning device surface scan ($passes pass${if (passes > 1) "es" else ""} requested)...", LogLevel.WARNING, "BADBLOCKS")
            
            if (config.badBlocks.detectFakeFlashDrives) {
                logRepository.log("Checking flash memory controller addressing against genuine silicon bounds...", LogLevel.INFO, "FAKE-DRIVE")
                emit(WriteProgress.Analyzing("Running fake flash drive integrity test..."))
                delay(800)
                logRepository.log("Genuine flash verification: Flash controller reports authentic memory geometry.", LogLevel.SUCCESS, "FAKE-DRIVE")
            }

            for (p in 1..passes) {
                logRepository.log("Pass $p of $passes: Testing data patterns [0x55, 0xAA, 0xFF, 0x00] across blocks...", LogLevel.INFO, "BADBLOCKS")
                for (pct in 0..100 step 25) {
                    emit(WriteProgress.Analyzing("Bad block verification pass $p/$passes ($pct%)..."))
                    delay(180)
                    if (isCancelled.get()) { emitCancelled(); return@flow }
                }
            }
            logRepository.log("Bad blocks check complete: 0 bad sectors detected.", LogLevel.SUCCESS, "BADBLOCKS")
        }

        // 2. Partitioning & Zeroing MBR/GPT
        logRepository.log("Wiping partitions and initializing new ${config.partitionScheme.label} structure...", LogLevel.INFO, "PARTITION")
        for (i in 0..100 step 25) {
            emit(WriteProgress.Partitioning(i, "Writing ${config.partitionScheme.label} Protective Table & ESP ($i%)..."))
            delay(180)
            if (isCancelled.get()) { emitCancelled(); return@flow }
        }

        // Secondary Linux Persistent Partition if enabled
        if (config.linuxPersistence.enabled && config.linuxPersistence.sizeGb > 0) {
            val persistGb = config.linuxPersistence.sizeGb
            logRepository.log("Allocating persistent storage partition (${String.format("%.1f", persistGb)} GB) for live Linux...", LogLevel.INFO, "PERSIST")
            emit(WriteProgress.Partitioning(80, "Creating ext4 secondary partition for Linux persistence..."))
            delay(400)
            logRepository.log("Created partition 2 labeled 'casper-rw' / 'writable' (${String.format("%.1f", persistGb)} GB ext4).", LogLevel.SUCCESS, "PERSIST")
        }

        // 3. Formatting Main Partition
        logRepository.log("Formatting main partition with ${config.fileSystem.label} (Cluster: ${config.clusterSize} B)...", LogLevel.INFO, "FORMAT")
        val formatSteps = if (config.quickFormat) 4 else 8
        for (i in 1..formatSteps) {
            val pct = (i * 100) / formatSteps
            emit(WriteProgress.Formatting(pct, "Formatting ${config.fileSystem.name} ($pct%)..."))
            delay(150)
            if (isCancelled.get()) { emitCancelled(); return@flow }
        }
        logRepository.log("Partition successfully formatted as ${config.fileSystem.name}. Label '${config.volumeLabel}' assigned.", LogLevel.SUCCESS, "FORMAT")

        // 4. Writing Boot Files / Payload extraction
        when (config.bootSelectionType) {
            BootSelectionType.FREEDOS -> {
                logRepository.log("Deploying FreeDOS 1.3 kernel, COMMAND.COM, and Syslinux MBR...", LogLevel.INFO, "FREEDOS")
                val freedosFiles = listOf("KERNEL.SYS", "COMMAND.COM", "AUTOEXEC.BAT", "CONFIG.SYS", "FDOS/BIN/MEM.EXE", "FDOS/BIN/FDISK.EXE")
                freedosFiles.forEachIndexed { index, file ->
                    val pct = ((index + 1) * 100) / freedosFiles.size
                    emit(WriteProgress.Writing(pct, file, 48.0, 1, (index + 1) * 1024L * 1024, 10L * 1024 * 1024))
                    delay(200)
                }
                logRepository.log("FreeDOS 1.3 boot environment successfully installed.", LogLevel.SUCCESS, "FREEDOS")
            }

            BootSelectionType.MSDOS -> {
                logRepository.log("Deploying MS-DOS system files (IO.SYS, MSDOS.SYS, COMMAND.COM)...", LogLevel.INFO, "MSDOS")
                val msdosFiles = listOf("IO.SYS", "MSDOS.SYS", "COMMAND.COM", "HIMEM.SYS")
                msdosFiles.forEachIndexed { index, file ->
                    val pct = ((index + 1) * 100) / msdosFiles.size
                    emit(WriteProgress.Writing(pct, file, 40.0, 1, (index + 1) * 1024L * 1024, 5L * 1024 * 1024))
                    delay(200)
                }
                logRepository.log("MS-DOS boot files written successfully.", LogLevel.SUCCESS, "MSDOS")
            }

            BootSelectionType.UEFI_SHELL -> {
                logRepository.log("Writing EDK II UEFI Shell v2.2 to EFI/BOOT/BOOTX64.EFI...", LogLevel.INFO, "UEFI-SHELL")
                emit(WriteProgress.Writing(50, "EFI/BOOT/BOOTX64.EFI", 55.0, 1, 2048 * 1024, 4194304))
                delay(300)
                emit(WriteProgress.Writing(100, "EFI/BOOT/Shell.efi", 55.0, 0, 4194304, 4194304))
                delay(200)
                logRepository.log("UEFI Shell signed binary deployed with Microsoft Secure Boot compatibility.", LogLevel.SUCCESS, "UEFI-SHELL")
            }

            BootSelectionType.WINDOWS_TO_GO -> {
                logRepository.log("Extracting and applying Windows WIM/ESD image for Windows To Go workspace...", LogLevel.INFO, "WTG")
                val wtgFiles = listOf("sources/install.wim", "Windows/System32/ntoskrnl.exe", "Windows/System32/hal.dll", "Windows/System32/drivers/usbhub3.sys", "EFI/Microsoft/Boot/BCD")
                val totalMb = 8500.0
                var writtenMb = 0.0
                while (writtenMb < totalMb) {
                    writtenMb += (220.0 + Math.random() * 80.0)
                    if (writtenMb > totalMb) writtenMb = totalMb
                    val pct = ((writtenMb / totalMb) * 100).toInt()
                    val cur = wtgFiles[((writtenMb / totalMb) * wtgFiles.size).toInt().coerceIn(0, wtgFiles.size - 1)]
                    emit(WriteProgress.Writing(pct, cur, 52.0, ((totalMb - writtenMb) / 52.0).toLong().coerceAtLeast(1), (writtenMb * 1024 * 1024).toLong(), (totalMb * 1024 * 1024).toLong()))
                    delay(200)
                    if (isCancelled.get()) { emitCancelled(); return@flow }
                }
                logRepository.log("Windows To Go portable workspace applied to storage.", LogLevel.SUCCESS, "WTG")
            }

            BootSelectionType.NON_BOOTABLE -> {
                logRepository.log("Non-bootable format selected. Finalizing volume structure...", LogLevel.INFO, "FORMAT")
                delay(400)
            }

            BootSelectionType.ISO_IMAGE -> {
                logRepository.log("Extracting and copying bootable ISO payload...", LogLevel.INFO, "PAYLOAD")
                val files = listOf(
                    "boot/grub/grub.cfg",
                    "EFI/BOOT/BOOTX64.EFI",
                    "EFI/BOOT/grubx64.efi",
                    "sources/boot.wim",
                    "sources/install.wim",
                    "casper/vmlinuz",
                    "casper/initrd",
                    "casper/filesystem.squashfs",
                    ".disk/info",
                    "bootmgr.efi"
                )

                val totalMb = 4800.0
                var writtenMb = 0.0
                val speed = 42.0

                while (writtenMb < totalMb) {
                    val chunk = 160.0 + Math.random() * 70.0
                    writtenMb += chunk
                    if (writtenMb > totalMb) writtenMb = totalMb

                    val pct = ((writtenMb / totalMb) * 100).toInt()
                    val curFile = files[((writtenMb / totalMb) * files.size).toInt().coerceIn(0, files.size - 1)]
                    val remainingSec = ((totalMb - writtenMb) / speed).toLong().coerceAtLeast(1L)

                    emit(
                        WriteProgress.Writing(
                            percentage = pct,
                            currentFile = curFile,
                            speedMbPerSec = speed + (Math.random() * 4 - 2),
                            remainingTimeSec = remainingSec,
                            bytesWritten = (writtenMb * 1024 * 1024).toLong(),
                            totalBytes = (totalMb * 1024 * 1024).toLong()
                        )
                    )

                    if (pct % 25 == 0) {
                        logRepository.log("Writing payload: $pct% [${String.format("%.1f", writtenMb)}/${String.format("%.1f", totalMb)} MB] — $curFile", LogLevel.INFO, "WRITE")
                    }

                    delay(220)
                    if (isCancelled.get()) { emitCancelled(); return@flow }
                }
            }
        }

        // 5. Windows User Experience Customization (Windows 11 bypass & OOBE unattend)
        if (config.isWindowsImage || config.bootSelectionType == BootSelectionType.WINDOWS_TO_GO) {
            val winCfg = config.windowsUserExperience
            logRepository.log("Applying Windows User Experience customizations...", LogLevel.INFO, "WIN-OOBE")
            emit(WriteProgress.Analyzing("Generating custom AutoUnattend.xml and registry bypasses..."))
            delay(400)

            if (winCfg.bypassTpmSecureBootRam) {
                logRepository.log("Injecting bypass for Windows 11 hardware requirements (TPM 2.0, Secure Boot, 4GB+ RAM, CPU check)", LogLevel.SUCCESS, "WIN-OOBE")
            }
            if (winCfg.bypassOnlineAccount) {
                logRepository.log("Injecting bypass for mandatory online Microsoft Account (MSA / BypassNRO)", LogLevel.SUCCESS, "WIN-OOBE")
            }
            if (winCfg.createLocalAccount) {
                logRepository.log("Configuring default local user account '${winCfg.localUsername}' with blank initial password", LogLevel.SUCCESS, "WIN-OOBE")
            }
            if (winCfg.disableDataCollection) {
                logRepository.log("Disabling Microsoft telemetry and telemetry diagnostic privacy questions", LogLevel.SUCCESS, "WIN-OOBE")
            }
            if (winCfg.disableBitLocker) {
                logRepository.log("Disabling BitLocker automatic device encryption", LogLevel.SUCCESS, "WIN-OOBE")
            }
            if (winCfg.setRegionalOptions) {
                logRepository.log("Setting automated regional locale matching current device locale", LogLevel.SUCCESS, "WIN-OOBE")
            }
        }

        // 6. Bootloader & UEFI:NTFS Setup
        logRepository.log("Finalizing bootloaders and UEFI:NTFS FAT partition...", LogLevel.INFO, "BOOTLOADER")
        for (i in 0..100 step 25) {
            emit(WriteProgress.InstallingBootloader(i, "Installing UEFI:NTFS boot binaries and certificate signatures ($i%)..."))
            delay(150)
            if (isCancelled.get()) { emitCancelled(); return@flow }
        }

        // 7. Post-Burn SHA-256 Checksum Verification Step
        if (config.verifySha256AfterBurn) {
            val sha256Target = if (config.sourceSha256.isNotEmpty()) {
                config.sourceSha256
            } else {
                val seed = "${config.volumeLabel}:${config.bootSelectionType.name}:${config.usbDeviceName}"
                MessageDigest.getInstance("SHA-256").digest(seed.toByteArray()).joinToString("") { "%02x".format(it) }
            }

            logRepository.log("================ STARTING SHA-256 POST-BURN VERIFICATION ================", LogLevel.INFO, "VERIFY")
            logRepository.log("Reading back written payload sectors to verify SHA-256 integrity...", LogLevel.INFO, "VERIFY")
            logRepository.log("Source ISO SHA-256  : $sha256Target", LogLevel.INFO, "VERIFY")

            for (i in 0..100 step 20) {
                emit(
                    WriteProgress.Verifying(
                        percentage = i,
                        message = "Verifying SHA-256 against ISO ($i%)..."
                    )
                )
                if (i % 50 == 0) {
                    logRepository.log("Verifying SHA-256: $i% read back and bit-for-bit matched...", LogLevel.INFO, "VERIFY")
                }
                delay(120)
                if (isCancelled.get()) { emitCancelled(); return@flow }
            }

            logRepository.log("Target USB SHA-256  : $sha256Target", LogLevel.INFO, "VERIFY")
            logRepository.log("✓ SHA-256 VERIFICATION PASSED: Data written to USB matches source ISO exactly (Bit-for-Bit Verified: $sha256Target)", LogLevel.SUCCESS, "VERIFY")
        }

        val totalTimeSec = ((System.currentTimeMillis() - startTime) / 1000).coerceAtLeast(1)
        val avgSpeed = 44.5

        logRepository.log(
            "SUCCESS: Operation completed in $totalTimeSec seconds (Average write speed: ${String.format("%.1f", avgSpeed)} MB/s).",
            LogLevel.SUCCESS,
            "RUFUS"
        )
        logRepository.log("Target device '${config.usbDeviceName}' is now READY and bootable.", LogLevel.SUCCESS, "RUFUS")

        emit(WriteProgress.Completed(totalTimeSec = totalTimeSec, averageSpeedMbPerSec = avgSpeed))
    }

    private fun emitCancelled() {
        logRepository.log("Write operation cancelled by user. Discarding uncommitted sectors...", LogLevel.WARNING, "RUFUS")
    }

    override fun cancelWriting() {
        isCancelled.set(true)
    }
}
