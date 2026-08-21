package com.example.usb.filesystem

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class Fat32FormattedStructures(
    val vbr: ByteArray,
    val fsInfo: ByteArray,
    val backupVbr: ByteArray,
    val backupFsInfo: ByteArray,
    val initialFatSector: ByteArray,
    val initialRootDirSector: ByteArray,
    val reservedSectors: Int = 32,
    val sectorsPerFat: Int,
    val sectorsPerCluster: Int
)

data class ExFatFormattedStructures(
    val mainBootRegion: ByteArray, // 12 sectors (6144 bytes)
    val backupBootRegion: ByteArray, // 12 sectors (6144 bytes)
    val initialFatSector: ByteArray, // 512 bytes
    val allocationBitmapClusters: ByteArray,
    val rootDirCluster: ByteArray,
    val fatOffsetSectors: Int,
    val fatLengthSectors: Int,
    val clusterHeapOffsetSectors: Int,
    val clusterCount: Int,
    val sectorsPerCluster: Int,
    val rootDirClusterNumber: Int,
    val allocationBitmapClusterNumber: Int = 2,
    val bitmapClustersNeeded: Int = 1
) {
    // Backwards compatibility getter
    val allocationBitmapCluster: ByteArray get() = allocationBitmapClusters
}

data class InjectedFile(
    val fileName83: String,
    val content: ByteArray
)

data class Fat32InjectedResult(
    val updatedRootDirSector: ByteArray,
    val updatedFatSectors: List<ByteArray>,
    val updatedFsInfoSector: ByteArray,
    val clustersAllocated: Int,
    val nextFreeCluster: Int
) {
    val updatedFatSector: ByteArray get() = updatedFatSectors.firstOrNull() ?: ByteArray(512)
}

data class EfiDirectoryStructures(
    val updatedRootDirSector: ByteArray,
    val efiDirClusterSector: ByteArray,
    val bootDirClusterSector: ByteArray,
    val updatedFatSectors: List<ByteArray>,
    val updatedFsInfoSector: ByteArray,
    val efiDirCluster: Int,
    val bootDirCluster: Int,
    val payloadStartCluster: Int,
    val totalClustersAllocated: Int
) {
    val updatedFatSector: ByteArray get() = updatedFatSectors.firstOrNull() ?: ByteArray(512)
}

object Fat32Formatter {

    /**
     * Calculates sectorsPerFat in compliance with the Microsoft FAT32 specification.
     */
    fun calculateSectorsPerFat(totalPartitionSectors: Long, sectorsPerCluster: Int, reservedSectors: Int = 32, numFats: Int = 2): Int {
        val bytesPerSector = 512L
        val bytesPerCluster = sectorsPerCluster.toLong() * bytesPerSector
        val numerator = (totalPartitionSectors - reservedSectors).coerceAtLeast(1024L)
        val denominator = (bytesPerCluster / 4L) + numFats
        val fatSize = ((numerator + denominator - 1L) / denominator).coerceAtLeast(512L)
        return (if (fatSize > 0xFFFFFFFFL) 0xFFFFFFFFL else fatSize).toInt()
    }

    /**
     * Creates complete FAT32 filesystem structures:
     * - VBR (Sector 0) & Backup VBR (Sector 6)
     * - FSInfo (Sector 1) & Backup FSInfo (Sector 7)
     * - Initial FAT table sector (FAT1 & FAT2)
     * - Initial Root Directory sector with Volume Label
     */
    fun createCompleteFat32Structures(
        totalPartitionSectors: Long,
        volumeLabel: String = "RUFUS",
        sectorsPerCluster: Int = 8, // 4KB clusters with 512B sectors
        startLbaOffset: Int = 2048
    ): Fat32FormattedStructures {
        val reservedSectors = 32
        val numFats = 2
        val sectorsPerFat = calculateSectorsPerFat(totalPartitionSectors, sectorsPerCluster, reservedSectors, numFats)

        val vbr = ByteArray(512)
        val buf = ByteBuffer.wrap(vbr).order(ByteOrder.LITTLE_ENDIAN)

        // Jump to boot code (EB 58 90)
        buf.put(byteArrayOf(0xEB.toByte(), 0x58.toByte(), 0x90.toByte()))
        // OEM Name (8 bytes)
        buf.put("MSDOS5.0".toByteArray(Charsets.US_ASCII))
        buf.putShort(512.toShort()) // Bytes per sector
        buf.put(sectorsPerCluster.toByte()) // Sectors per cluster
        buf.putShort(reservedSectors.toShort()) // Reserved sector count (32 sectors)
        buf.put(numFats.toByte()) // Number of FATs (2)
        buf.putShort(0.toShort()) // Root directory entries (0 for FAT32)
        buf.putShort(0.toShort()) // Total sectors 16-bit (0 for FAT32)
        buf.put(0xF8.toByte()) // Media descriptor (Fixed disk)
        buf.putShort(0.toShort()) // Sectors per FAT 16-bit (0)
        buf.putShort(63.toShort()) // Sectors per track
        buf.putShort(255.toShort()) // Number of heads
        buf.putInt(startLbaOffset) // Hidden sectors (Start LBA offset)
        val partSec32 = (if (totalPartitionSectors > 0xFFFFFFFFL) 0xFFFFFFFFL else totalPartitionSectors).toInt()
        buf.putInt(partSec32) // Total sectors 32-bit

        // FAT32 Extended BPB
        buf.putInt(sectorsPerFat) // Sectors per FAT 32-bit
        buf.putShort(0.toShort()) // Extended flags
        buf.putShort(0.toShort()) // Filesystem version (0.0)
        buf.putInt(2) // Root cluster (Cluster 2)
        buf.putShort(1.toShort()) // FSInfo sector (Sector 1)
        buf.putShort(6.toShort()) // Backup boot sector (Sector 6)
        buf.put(ByteArray(12)) // Reserved 12 bytes
        buf.put(0x80.toByte()) // Drive number
        buf.put(0.toByte()) // Reserved
        buf.put(0x29.toByte()) // Boot signature (0x29)
        buf.putInt(0x12345678) // Volume Serial Number

        // Volume Label (11 bytes, space-padded)
        val cleanLabel = volumeLabel.take(11).padEnd(11, ' ').toByteArray(Charsets.US_ASCII)
        buf.put(cleanLabel)
        // File system type string (8 bytes)
        buf.put("FAT32   ".toByteArray(Charsets.US_ASCII))

        // VBR Signature
        vbr[510] = 0x55.toByte()
        vbr[511] = 0xAA.toByte()

        // Backup VBR (Sector 6) is identical to VBR
        val backupVbr = vbr.copyOf()

        // FSInfo Sector (512 bytes)
        val fsInfo = ByteArray(512)
        val fsBuf = ByteBuffer.wrap(fsInfo).order(ByteOrder.LITTLE_ENDIAN)
        fsBuf.putInt(0x41615252) // Lead signature ("RRaA")
        fsBuf.put(ByteArray(480)) // Reserved 480 bytes
        fsBuf.position(484)
        fsBuf.putInt(0x61417272) // Struct signature ("rrAa")
        val dataSectors = totalPartitionSectors - reservedSectors - (sectorsPerFat.toLong() * numFats)
        val freeClusters = (dataSectors / sectorsPerCluster).coerceAtLeast(0L).toInt()
        fsBuf.putInt(freeClusters) // Free cluster count
        fsBuf.putInt(3) // Next free cluster (Root dir occupies cluster 2)
        fsInfo[510] = 0x55.toByte()
        fsInfo[511] = 0xAA.toByte()

        val backupFsInfo = fsInfo.copyOf()

        // Initial FAT Table Sector (First sector of FAT1 & FAT2)
        // Cluster 0: 0x0FFFFFF8, Cluster 1: 0xFFFFFFFF, Cluster 2 (Root directory EOF): 0x0FFFFFFF
        val initialFat = ByteArray(512)
        val fatBuf = ByteBuffer.wrap(initialFat).order(ByteOrder.LITTLE_ENDIAN)
        fatBuf.putInt(0x0FFFFFF8) // Media type in cluster 0
        fatBuf.putInt(0xFFFFFFFF.toInt()) // End of cluster chain marker in cluster 1
        fatBuf.putInt(0x0FFFFFFF) // Root directory EOF marker in cluster 2

        // Initial Root Directory Sector (Volume label entry)
        val rootDir = ByteArray(512)
        val rootBuf = ByteBuffer.wrap(rootDir).order(ByteOrder.LITTLE_ENDIAN)
        rootBuf.put(cleanLabel) // 11-byte volume label
        rootBuf.put(0x08.toByte()) // Attribute: Volume Label (0x08)

        return Fat32FormattedStructures(
            vbr = vbr,
            fsInfo = fsInfo,
            backupVbr = backupVbr,
            backupFsInfo = backupFsInfo,
            initialFatSector = initialFat,
            initialRootDirSector = rootDir,
            reservedSectors = reservedSectors,
            sectorsPerFat = sectorsPerFat,
            sectorsPerCluster = sectorsPerCluster
        )
    }

    /**
     * Creates a complete, spec-valid exFAT volume structures per Microsoft exFAT 1.00 specification.
     */
    fun createCompleteExFatStructures(
        totalPartitionSectors: Long,
        volumeLabel: String = "RUFUS",
        sectorsPerCluster: Int = 8,
        startLbaOffset: Int = 2048
    ): ExFatFormattedStructures {
        val bytesPerSectorShift = 9 // 2^9 = 512 bytes
        val sectorsPerClusterShift = Integer.numberOfTrailingZeros(sectorsPerCluster.coerceAtLeast(1)).coerceIn(0, 12)
        val actualSectorsPerCluster = 1 shl sectorsPerClusterShift
        val clusterBytes = actualSectorsPerCluster * 512

        val fatOffset = 128 // Aligned FAT offset (64 KB)
        val approxClusters = ((totalPartitionSectors - fatOffset) / actualSectorsPerCluster).coerceAtLeast(128L)
        val fatBytes = (approxClusters + 2) * 4L
        val fatLength = (((fatBytes + 511L) / 512L)).coerceAtLeast(128L).toInt()

        // Cluster heap offset aligned to cluster boundary
        val clusterHeapOffset = (((fatOffset + fatLength + actualSectorsPerCluster - 1) / actualSectorsPerCluster) * actualSectorsPerCluster)
        val clusterCount = ((totalPartitionSectors - clusterHeapOffset) / actualSectorsPerCluster).coerceAtLeast(1L).toInt()

        // Sector-aligned bitmap size and computed cluster requirement
        val rawBitmapBytes = (clusterCount + 7) / 8
        val bitmapSizeBytes = (((rawBitmapBytes + 511) / 512) * 512).coerceAtLeast(512)
        val bitmapClustersNeeded = ((bitmapSizeBytes + clusterBytes - 1) / clusterBytes).coerceAtLeast(1)

        val allocationBitmapClusterNumber = 2
        val rootDirClusterNumber = allocationBitmapClusterNumber + bitmapClustersNeeded

        // Main Boot Region (12 sectors: 0..11)
        val mainBootRegion = ByteArray(12 * 512)

        // Sector 0: Main VBR
        val vbrBuf = ByteBuffer.wrap(mainBootRegion, 0, 512).order(ByteOrder.LITTLE_ENDIAN)
        vbrBuf.put(byteArrayOf(0xEB.toByte(), 0x76.toByte(), 0x90.toByte())) // Jump (0..2)
        vbrBuf.put("EXFAT   ".toByteArray(Charsets.US_ASCII)) // OEM Name (3..10)
        vbrBuf.put(ByteArray(53)) // MustBeZero (11..63)
        vbrBuf.putLong(startLbaOffset.toLong()) // PartitionOffset in sectors (64..71)
        vbrBuf.putLong(totalPartitionSectors) // VolumeLength in sectors (72..79)
        vbrBuf.putInt(fatOffset) // FatOffset (80..83)
        vbrBuf.putInt(fatLength) // FatLength (84..87)
        vbrBuf.putInt(clusterHeapOffset) // ClusterHeapOffset (88..91)
        vbrBuf.putInt(clusterCount) // ClusterCount (92..95)
        vbrBuf.putInt(rootDirClusterNumber) // FirstClusterOfRootDirectory (96..99) - Computed BEFORE checksum
        vbrBuf.putInt(0x12345678) // VolumeSerialNumber (100..103)
        vbrBuf.putShort(0x0100.toShort()) // FileSystemRevision 1.00 (104..105)
        vbrBuf.putShort(0x0000.toShort()) // VolumeFlags (106..107)
        vbrBuf.put(bytesPerSectorShift.toByte()) // BytesPerSectorShift (108)
        vbrBuf.put(sectorsPerClusterShift.toByte()) // SectorsPerClusterShift (109)
        vbrBuf.put(1.toByte()) // NumberOfFats (110)
        vbrBuf.put(0x80.toByte()) // DriveSelect (111)
        vbrBuf.put(0.toByte()) // PercentInUse (112)
        vbrBuf.put(ByteArray(397)) // Reserved (113..509)
        mainBootRegion[510] = 0x55.toByte()
        mainBootRegion[511] = 0xAA.toByte()

        // Sectors 1..7: Extended Boot Sectors (zero-filled, no signature)
        // Sector 8: Extended Boot Sector (zero-filled, ends with 0x55AA at 510..511)
        mainBootRegion[8 * 512 + 510] = 0x55.toByte()
        mainBootRegion[8 * 512 + 511] = 0xAA.toByte()

        // Sector 9: OEM Parameter Sector (zero-filled)

        // Sector 10: Reserved Sector (zero-filled)

        // Sector 11: Boot Checksum Sector (exFAT spec 3.3.6)
        var checksum = 0 // Int used as UInt32; Kotlin arithmetic wraps naturally
        val numberOfBytes = 512 * 11 // covers sectors 0..10 inclusive
        for (index in 0 until numberOfBytes) {
            if (index == 106 || index == 107 || index == 112) continue // VolumeFlags + PercentInUse only
            val carry = if ((checksum and 1) != 0) 0x80000000.toInt() else 0
            checksum = carry + ((checksum ushr 1) and 0x7FFFFFFF) + (mainBootRegion[index].toInt() and 0xFF)
        }

        val checksumSecBuf = ByteBuffer.wrap(mainBootRegion, 11 * 512, 512).order(ByteOrder.LITTLE_ENDIAN)
        for (w in 0 until 128) {
            checksumSecBuf.putInt(checksum)
        }

        // Backup Boot Region (sectors 12..23) is identical to Main Boot Region
        val backupBootRegion = mainBootRegion.copyOf()

        // Initial FAT table sector (512 bytes)
        val initialFat = ByteArray(512)
        val fatBuf = ByteBuffer.wrap(initialFat).order(ByteOrder.LITTLE_ENDIAN)
        fatBuf.putInt(0xFFFFFFF8.toInt()) // Entry 0: Media type
        fatBuf.putInt(0xFFFFFFFF.toInt()) // Entry 1: End marker

        // Clusters 2 .. (1 + bitmapClustersNeeded): bitmap chain
        for (i in 0 until bitmapClustersNeeded) {
            val cluster = allocationBitmapClusterNumber + i
            if (cluster * 4 + 4 <= initialFat.size) {
                fatBuf.position(cluster * 4)
                if (i == bitmapClustersNeeded - 1) {
                    fatBuf.putInt(0xFFFFFFFF.toInt()) // EOC for bitmap
                } else {
                    fatBuf.putInt(cluster + 1) // Next cluster in bitmap chain
                }
            }
        }

        // Entry rootDirClusterNumber: Root dir EOF marker
        if (rootDirClusterNumber * 4 + 4 <= initialFat.size) {
            fatBuf.position(rootDirClusterNumber * 4)
            fatBuf.putInt(0xFFFFFFFF.toInt())
        }

        // Allocation Bitmap: Multi-cluster byte array
        val allocationBitmap = ByteArray(bitmapClustersNeeded * clusterBytes)
        // Mark bitmap clusters as allocated
        for (c in allocationBitmapClusterNumber until (allocationBitmapClusterNumber + bitmapClustersNeeded)) {
            val bitIndex = c - 2
            val byteIndex = bitIndex / 8
            val bitPos = bitIndex % 8
            allocationBitmap[byteIndex] = (allocationBitmap[byteIndex].toInt() or (1 shl bitPos)).toByte()
        }
        // Mark root directory cluster as allocated
        val rootBitIndex = rootDirClusterNumber - 2
        val rootByteIndex = rootBitIndex / 8
        val rootBitPos = rootBitIndex % 8
        allocationBitmap[rootByteIndex] = (allocationBitmap[rootByteIndex].toInt() or (1 shl rootBitPos)).toByte()

        // Root Directory (Cluster #rootDirClusterNumber)
        val rootDirCluster = ByteArray(clusterBytes)
        val rootBuf = ByteBuffer.wrap(rootDirCluster).order(ByteOrder.LITTLE_ENDIAN)

        // Entry 0: Volume Label Directory Entry (0x83)
        rootBuf.position(0)
        rootBuf.put(0x83.toByte()) // EntryType = Volume Label
        val cleanLabelStr = volumeLabel.take(11)
        rootBuf.put(cleanLabelStr.length.toByte()) // CharacterCount
        val labelChars = cleanLabelStr.toCharArray()
        for (i in 0 until 11) {
            if (i < labelChars.size) {
                rootBuf.putChar(labelChars[i])
            } else {
                rootBuf.putShort(0)
            }
        }
        rootBuf.put(ByteArray(8)) // Reserved 8 bytes (offset 24..31)

        // Entry 1: Allocation Bitmap Directory Entry (0x81)
        rootBuf.position(32)
        rootBuf.put(0x81.toByte()) // EntryType = Allocation Bitmap
        rootBuf.put(0x00.toByte()) // BitmapFlags = 0 (Active FAT allocation bitmap)
        rootBuf.put(ByteArray(18)) // Reserved 18 bytes (offset 2..19)
        rootBuf.putInt(allocationBitmapClusterNumber) // FirstCluster = 2 (offset 20..23)
        rootBuf.putLong(bitmapSizeBytes.toLong()) // DataLength in bytes (offset 24..31) - sector aligned

        return ExFatFormattedStructures(
            mainBootRegion = mainBootRegion,
            backupBootRegion = backupBootRegion,
            initialFatSector = initialFat,
            allocationBitmapClusters = allocationBitmap,
            rootDirCluster = rootDirCluster,
            fatOffsetSectors = fatOffset,
            fatLengthSectors = fatLength,
            clusterHeapOffsetSectors = clusterHeapOffset,
            clusterCount = clusterCount,
            sectorsPerCluster = actualSectorsPerCluster,
            rootDirClusterNumber = rootDirClusterNumber,
            allocationBitmapClusterNumber = allocationBitmapClusterNumber,
            bitmapClustersNeeded = bitmapClustersNeeded
        )
    }

    /**
     * Injects multiple files into FAT32 root directory and updates the multi-sector FAT allocation table and FSInfo.
     */
    fun createRootDirectoryFiles(
        initialRootDirSector: ByteArray,
        initialFatSectors: List<ByteArray>,
        initialFsInfoSector: ByteArray,
        rootDirLba: Long,
        sectorsPerCluster: Int,
        files: List<InjectedFile>,
        startCluster: Int = 3
    ): Fat32InjectedResult {
        val updatedRootDir = initialRootDirSector.copyOf()
        val totalFatBytes = initialFatSectors.sumOf { it.size }
        val combinedFat = ByteArray(totalFatBytes)
        var fatOffset = 0
        for (sec in initialFatSectors) {
            System.arraycopy(sec, 0, combinedFat, fatOffset, sec.size)
            fatOffset += sec.size
        }
        val updatedFsInfo = initialFsInfoSector.copyOf()

        val clusterBytes = (sectorsPerCluster * 512).coerceAtLeast(512)
        val dirBuf = ByteBuffer.wrap(updatedRootDir).order(ByteOrder.LITTLE_ENDIAN)
        val fatBuf = ByteBuffer.wrap(combinedFat).order(ByteOrder.LITTLE_ENDIAN)

        var currentCluster = startCluster
        var totalClustersAllocated = 0

        var entryIndex = 0
        val maxEntries = updatedRootDir.size / 32

        for (file in files) {
            val clustersNeeded = ((file.content.size + clusterBytes - 1) / clusterBytes).coerceAtLeast(1)

            // Find the next free 32-byte directory entry slot
            while (entryIndex < maxEntries) {
                val firstByte = updatedRootDir[entryIndex * 32].toInt() and 0xFF
                if (firstByte == 0x00 || firstByte == 0xE5) {
                    break
                }
                entryIndex++
            }

            if (entryIndex >= maxEntries) {
                break // Root directory sector full
            }

            // Write 32-byte SFN directory record
            dirBuf.position(entryIndex * 32)
            val sfnBytes = file.fileName83.take(11).padEnd(11, ' ').uppercase().toByteArray(Charsets.US_ASCII)
            dirBuf.put(sfnBytes)
            dirBuf.put(0x20.toByte()) // Attribute: Archive (0x20)
            dirBuf.put(0.toByte()) // NT reserved
            dirBuf.put(0.toByte()) // Creation time tenths
            dirBuf.putShort(0.toShort()) // Creation time
            dirBuf.putShort(0x5295.toShort()) // Creation date
            dirBuf.putShort(0x5295.toShort()) // Last access date
            dirBuf.putShort(((currentCluster shr 16) and 0xFFFF).toShort()) // High cluster
            dirBuf.putShort(0.toShort()) // Write time
            dirBuf.putShort(0x5295.toShort()) // Write date
            dirBuf.putShort((currentCluster and 0xFFFF).toShort()) // Low cluster
            dirBuf.putInt(file.content.size) // File size

            entryIndex++

            // Update FAT chain for this file
            for (i in 0 until clustersNeeded) {
                val c = currentCluster + i
                val entryFatOffset = c * 4
                if (entryFatOffset + 4 <= combinedFat.size) {
                    fatBuf.position(entryFatOffset)
                    if (i == clustersNeeded - 1) {
                        fatBuf.putInt(0x0FFFFFFF) // End of chain (EOC)
                    } else {
                        fatBuf.putInt(c + 1) // Link to next cluster
                    }
                }
            }

            currentCluster += clustersNeeded
            totalClustersAllocated += clustersNeeded
        }

        // Update FSInfo free cluster count and next free cluster pointer
        val nextFree = currentCluster
        if (updatedFsInfo.size >= 512) {
            val fsBuf = ByteBuffer.wrap(updatedFsInfo).order(ByteOrder.LITTLE_ENDIAN)
            val currentFree = fsBuf.getInt(488)
            if (currentFree > 0) {
                fsBuf.putInt(488, (currentFree - totalClustersAllocated).coerceAtLeast(0))
            }
            fsBuf.putInt(492, nextFree)
        }

        val updatedFatSectors = mutableListOf<ByteArray>()
        var splitOffset = 0
        for (sec in initialFatSectors) {
            val copy = ByteArray(sec.size)
            System.arraycopy(combinedFat, splitOffset, copy, 0, sec.size)
            updatedFatSectors.add(copy)
            splitOffset += sec.size
        }

        return Fat32InjectedResult(
            updatedRootDirSector = updatedRootDir,
            updatedFatSectors = updatedFatSectors,
            updatedFsInfoSector = updatedFsInfo,
            clustersAllocated = totalClustersAllocated,
            nextFreeCluster = nextFree
        )
    }

    /**
     * Backward-compatible single FAT sector overload for createRootDirectoryFiles.
     */
    fun createRootDirectoryFiles(
        initialRootDirSector: ByteArray,
        initialFatSector: ByteArray,
        initialFsInfoSector: ByteArray,
        rootDirLba: Long,
        sectorsPerCluster: Int,
        files: List<InjectedFile>,
        startCluster: Int = 3
    ): Fat32InjectedResult {
        return createRootDirectoryFiles(
            initialRootDirSector = initialRootDirSector,
            initialFatSectors = listOf(initialFatSector),
            initialFsInfoSector = initialFsInfoSector,
            rootDirLba = rootDirLba,
            sectorsPerCluster = sectorsPerCluster,
            files = files,
            startCluster = startCluster
        )
    }

    /**
     * Single-file SFN injection helper delegating to createRootDirectoryFiles.
     */
    fun createRootDirectoryFile(
        initialRootDirSector: ByteArray,
        initialFatSector: ByteArray,
        initialFsInfoSector: ByteArray,
        rootDirLba: Long,
        sectorsPerCluster: Int,
        fileName83: String = "AUTOUNATXML",
        fileContent: ByteArray,
        startCluster: Int = 3
    ): Fat32InjectedResult {
        return createRootDirectoryFiles(
            initialRootDirSector = initialRootDirSector,
            initialFatSectors = listOf(initialFatSector),
            initialFsInfoSector = initialFsInfoSector,
            rootDirLba = rootDirLba,
            sectorsPerCluster = sectorsPerCluster,
            files = listOf(InjectedFile(fileName83 = fileName83, content = fileContent)),
            startCluster = startCluster
        )
    }

    /**
     * Multi-sector FAT overload for createRootDirectoryFile.
     */
    fun createRootDirectoryFile(
        initialRootDirSector: ByteArray,
        initialFatSectors: List<ByteArray>,
        initialFsInfoSector: ByteArray,
        rootDirLba: Long,
        sectorsPerCluster: Int,
        fileName83: String = "AUTOUNATXML",
        fileContent: ByteArray,
        startCluster: Int = 3
    ): Fat32InjectedResult {
        return createRootDirectoryFiles(
            initialRootDirSector = initialRootDirSector,
            initialFatSectors = initialFatSectors,
            initialFsInfoSector = initialFsInfoSector,
            rootDirLba = rootDirLba,
            sectorsPerCluster = sectorsPerCluster,
            files = listOf(InjectedFile(fileName83 = fileName83, content = fileContent)),
            startCluster = startCluster
        )
    }

    /**
     * Builds a full UEFI ESP directory tree: \EFI\BOOT\BOOTX64.EFI
     * Accepts multi-sector initialFatSectors to remove the 128-cluster ceiling.
     */
    fun createEfiBootTree(
        initialRootDirSector: ByteArray,
        initialFatSectors: List<ByteArray>,
        initialFsInfoSector: ByteArray,
        sectorsPerCluster: Int,
        efiBinaryPayload: ByteArray,
        startCluster: Int = 3
    ): EfiDirectoryStructures {
        val updatedRootDir = initialRootDirSector.copyOf()
        val totalFatBytes = initialFatSectors.sumOf { it.size }
        val combinedFat = ByteArray(totalFatBytes)
        var fatOffset = 0
        for (sec in initialFatSectors) {
            System.arraycopy(sec, 0, combinedFat, fatOffset, sec.size)
            fatOffset += sec.size
        }
        val updatedFsInfo = initialFsInfoSector.copyOf()

        val clusterBytes = (sectorsPerCluster * 512).coerceAtLeast(512)
        val payloadClusters = ((efiBinaryPayload.size + clusterBytes - 1) / clusterBytes).coerceAtLeast(1)

        val efiCluster = startCluster
        val bootCluster = startCluster + 1
        val payloadStartCluster = startCluster + 2
        val totalClustersAllocated = 2 + payloadClusters

        val dirBuf = ByteBuffer.wrap(updatedRootDir).order(ByteOrder.LITTLE_ENDIAN)
        val fatBuf = ByteBuffer.wrap(combinedFat).order(ByteOrder.LITTLE_ENDIAN)

        // 1. Root directory entry for EFI (Directory, cluster 3)
        var rootEntryIdx = 0
        while (rootEntryIdx < updatedRootDir.size / 32) {
            val fb = updatedRootDir[rootEntryIdx * 32].toInt() and 0xFF
            if (fb == 0x00 || fb == 0xE5) break
            rootEntryIdx++
        }
        if (rootEntryIdx < updatedRootDir.size / 32) {
            dirBuf.position(rootEntryIdx * 32)
            dirBuf.put("EFI        ".toByteArray(Charsets.US_ASCII))
            dirBuf.put(0x10.toByte()) // Attribute: Subdirectory
            dirBuf.put(ByteArray(8)) // Reserved
            dirBuf.putShort(((efiCluster shr 16) and 0xFFFF).toShort())
            dirBuf.putShort(0.toShort())
            dirBuf.putShort(0x5295.toShort())
            dirBuf.putShort((efiCluster and 0xFFFF).toShort())
            dirBuf.putInt(0) // Size 0 for directory
        }

        // 2. EFI Directory Cluster (Cluster 3)
        val efiDirSector = ByteArray(clusterBytes)
        val efiBuf = ByteBuffer.wrap(efiDirSector).order(ByteOrder.LITTLE_ENDIAN)
        // "." entry
        efiBuf.put(".          ".toByteArray(Charsets.US_ASCII))
        efiBuf.put(0x10.toByte())
        efiBuf.put(ByteArray(8))
        efiBuf.putShort(((efiCluster shr 16) and 0xFFFF).toShort())
        efiBuf.putShort(0.toShort())
        efiBuf.putShort(0x5295.toShort())
        efiBuf.putShort((efiCluster and 0xFFFF).toShort())
        efiBuf.putInt(0)
        // ".." entry (points to root = cluster 0)
        efiBuf.put("..         ".toByteArray(Charsets.US_ASCII))
        efiBuf.put(0x10.toByte())
        efiBuf.put(ByteArray(8))
        efiBuf.putShort(0.toShort())
        efiBuf.putShort(0.toShort())
        efiBuf.putShort(0x5295.toShort())
        efiBuf.putShort(0.toShort())
        efiBuf.putInt(0)
        // "BOOT" directory entry (points to Cluster 4)
        efiBuf.put("BOOT       ".toByteArray(Charsets.US_ASCII))
        efiBuf.put(0x10.toByte())
        efiBuf.put(ByteArray(8))
        efiBuf.putShort(((bootCluster shr 16) and 0xFFFF).toShort())
        efiBuf.putShort(0.toShort())
        efiBuf.putShort(0x5295.toShort())
        efiBuf.putShort((bootCluster and 0xFFFF).toShort())
        efiBuf.putInt(0)

        // 3. BOOT Directory Cluster (Cluster 4)
        val bootDirSector = ByteArray(clusterBytes)
        val bootBuf = ByteBuffer.wrap(bootDirSector).order(ByteOrder.LITTLE_ENDIAN)
        // "." entry
        bootBuf.put(".          ".toByteArray(Charsets.US_ASCII))
        bootBuf.put(0x10.toByte())
        bootBuf.put(ByteArray(8))
        bootBuf.putShort(((bootCluster shr 16) and 0xFFFF).toShort())
        bootBuf.putShort(0.toShort())
        bootBuf.putShort(0x5295.toShort())
        bootBuf.putShort((bootCluster and 0xFFFF).toShort())
        bootBuf.putInt(0)
        // ".." entry (points to EFI = cluster 3)
        bootBuf.put("..         ".toByteArray(Charsets.US_ASCII))
        bootBuf.put(0x10.toByte())
        bootBuf.put(ByteArray(8))
        bootBuf.putShort(((efiCluster shr 16) and 0xFFFF).toShort())
        bootBuf.putShort(0.toShort())
        bootBuf.putShort(0x5295.toShort())
        bootBuf.putShort((efiCluster and 0xFFFF).toShort())
        bootBuf.putInt(0)
        // "BOOTX64.EFI" file entry
        bootBuf.put("BOOTX64 EFI".toByteArray(Charsets.US_ASCII))
        bootBuf.put(0x20.toByte()) // Attribute: Archive
        bootBuf.put(ByteArray(8))
        bootBuf.putShort(((payloadStartCluster shr 16) and 0xFFFF).toShort())
        bootBuf.putShort(0.toShort())
        bootBuf.putShort(0x5295.toShort())
        bootBuf.putShort((payloadStartCluster and 0xFFFF).toShort())
        bootBuf.putInt(efiBinaryPayload.size)

        // 4. Update FAT Table
        if (efiCluster * 4 + 4 <= combinedFat.size) {
            fatBuf.position(efiCluster * 4)
            fatBuf.putInt(0x0FFFFFFF) // EOC for EFI dir
        }
        if (bootCluster * 4 + 4 <= combinedFat.size) {
            fatBuf.position(bootCluster * 4)
            fatBuf.putInt(0x0FFFFFFF) // EOC for BOOT dir
        }
        for (i in 0 until payloadClusters) {
            val c = payloadStartCluster + i
            if (c * 4 + 4 <= combinedFat.size) {
                fatBuf.position(c * 4)
                if (i == payloadClusters - 1) {
                    fatBuf.putInt(0x0FFFFFFF) // EOC
                } else {
                    fatBuf.putInt(c + 1)
                }
            }
        }

        // 5. Update FSInfo
        val nextFree = payloadStartCluster + payloadClusters
        if (updatedFsInfo.size >= 512) {
            val fsBuf = ByteBuffer.wrap(updatedFsInfo).order(ByteOrder.LITTLE_ENDIAN)
            val currentFree = fsBuf.getInt(488)
            if (currentFree > 0) {
                fsBuf.putInt(488, (currentFree - totalClustersAllocated).coerceAtLeast(0))
            }
            fsBuf.putInt(492, nextFree)
        }

        val updatedFatSectors = mutableListOf<ByteArray>()
        var splitOffset = 0
        for (sec in initialFatSectors) {
            val copy = ByteArray(sec.size)
            System.arraycopy(combinedFat, splitOffset, copy, 0, sec.size)
            updatedFatSectors.add(copy)
            splitOffset += sec.size
        }

        return EfiDirectoryStructures(
            updatedRootDirSector = updatedRootDir,
            efiDirClusterSector = efiDirSector,
            bootDirClusterSector = bootDirSector,
            updatedFatSectors = updatedFatSectors,
            updatedFsInfoSector = updatedFsInfo,
            efiDirCluster = efiCluster,
            bootDirCluster = bootCluster,
            payloadStartCluster = payloadStartCluster,
            totalClustersAllocated = totalClustersAllocated
        )
    }

    /**
     * Backward-compatible single FAT sector overload for createEfiBootTree.
     */
    fun createEfiBootTree(
        initialRootDirSector: ByteArray,
        initialFatSector: ByteArray,
        initialFsInfoSector: ByteArray,
        sectorsPerCluster: Int,
        efiBinaryPayload: ByteArray,
        startCluster: Int = 3
    ): EfiDirectoryStructures {
        return createEfiBootTree(
            initialRootDirSector = initialRootDirSector,
            initialFatSectors = listOf(initialFatSector),
            initialFsInfoSector = initialFsInfoSector,
            sectorsPerCluster = sectorsPerCluster,
            efiBinaryPayload = efiBinaryPayload,
            startCluster = startCluster
        )
    }

    /**
     * Resolves the start cluster for file injection in FAT32:
     * 1. Validates FSInfo sector signatures (0x41615252 at 0, 0x61417272 at 484, 0x55AA at 510).
     *    If valid and offset 492 has a valid cluster pointer (>= 3 and != 0xFFFFFFFF), returns it.
     * 2. If FSInfo is missing/invalid, scans the provided FAT sector(s) for the first free cluster
     *    (entry == 0x00000000) starting at cluster index 3.
     * 3. Returns null if no free cluster is found.
     */
    fun resolveInjectionStartCluster(
        fsInfoSector: ByteArray?,
        fatSectors: List<ByteArray>
    ): Int? {
        if (fsInfoSector != null && fsInfoSector.size >= 512) {
            val fsBuf = ByteBuffer.wrap(fsInfoSector).order(ByteOrder.LITTLE_ENDIAN)
            val leadSig = fsBuf.getInt(0)
            val structSig = fsBuf.getInt(484)
            val trailSig0 = fsInfoSector[510].toInt() and 0xFF
            val trailSig1 = fsInfoSector[511].toInt() and 0xFF
            val isTrailValid = (trailSig0 == 0x55 && trailSig1 == 0xAA)
            if (leadSig == 0x41615252 && structSig == 0x61417272 && isTrailValid) {
                val nextFree = fsBuf.getInt(492)
                if (nextFree >= 3 && nextFree != -1 && nextFree != 0xFFFFFFFF.toInt()) {
                    return nextFree
                }
            }
        }

        // Fallback: Scan FAT sector(s) for the first free cluster entry (0x00000000) starting at cluster 3
        var clusterIndex = 0
        for (fatSector in fatSectors) {
            val fatBuf = ByteBuffer.wrap(fatSector).order(ByteOrder.LITTLE_ENDIAN)
            val entriesInSector = fatSector.size / 4
            for (i in 0 until entriesInSector) {
                val currentCluster = clusterIndex + i
                val entryValue = fatBuf.getInt(i * 4) and 0x0FFFFFFF
                if (currentCluster >= 3 && entryValue == 0) {
                    return currentCluster
                }
            }
            clusterIndex += entriesInSector
        }

        return null
    }

    /**
     * Backward-compatible Pair helper.
     */
    fun createRootDirectoryFile(
        initialRootDirSector: ByteArray,
        initialFatSector: ByteArray,
        rootDirLba: Long,
        sectorsPerCluster: Int,
        fileName83: String = "AUTOUNATXML",
        fileContent: ByteArray,
        startCluster: Int = 3
    ): Pair<ByteArray, ByteArray> {
        val dummyFsInfo = ByteArray(512)
        val res = createRootDirectoryFile(
            initialRootDirSector = initialRootDirSector,
            initialFatSector = initialFatSector,
            initialFsInfoSector = dummyFsInfo,
            rootDirLba = rootDirLba,
            sectorsPerCluster = sectorsPerCluster,
            fileName83 = fileName83,
            fileContent = fileContent,
            startCluster = startCluster
        )
        return Pair(res.updatedRootDirSector, res.updatedFatSector)
    }

    /**
     * Backward-compatible helper returning Pair(vbr, fsInfo).
     */
    fun createFat32BootSectors(
        totalPartitionSectors: Long,
        volumeLabel: String = "RUFUS",
        sectorsPerCluster: Int = 8
    ): Pair<ByteArray, ByteArray> {
        val structures = createCompleteFat32Structures(totalPartitionSectors, volumeLabel, sectorsPerCluster)
        return Pair(structures.vbr, structures.fsInfo)
    }

    /**
     * Creates NTFS Volume Boot Record (512 bytes).
     */
    fun createNtfsBootSector(
        totalPartitionSectors: Long,
        volumeLabel: String = "RUFUS",
        sectorsPerCluster: Int = 8,
        startLbaOffset: Int = 2048
    ): ByteArray {
        val vbr = ByteArray(512)
        val buf = ByteBuffer.wrap(vbr).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(byteArrayOf(0xEB.toByte(), 0x52.toByte(), 0x90.toByte())) // Jump
        buf.put("NTFS    ".toByteArray(Charsets.US_ASCII)) // OEM ID
        buf.putShort(512.toShort()) // Bytes per sector
        buf.put(sectorsPerCluster.toByte()) // Sectors per cluster
        buf.putShort(0.toShort()) // Reserved sectors
        buf.put(0.toByte()) // Number of FATs
        buf.putShort(0.toShort()) // Root dir entries
        buf.putShort(0.toShort()) // Total sectors 16-bit
        buf.put(0xF8.toByte()) // Media descriptor
        buf.putShort(0.toShort()) // Sectors per FAT 16-bit
        buf.putShort(63.toShort()) // Sectors per track
        buf.putShort(255.toShort()) // Heads
        buf.putInt(startLbaOffset) // Hidden sectors
        buf.putInt(0) // Unused
        buf.putInt(0x80.toInt()) // Unused
        buf.putLong(totalPartitionSectors - 1L) // Total sectors 64-bit
        buf.putLong(4L) // Start cluster for $MFT
        buf.putLong(totalPartitionSectors / (2 * sectorsPerCluster)) // Start cluster for $MFTMirr
        buf.put(0xF6.toByte()) // Clusters per file record segment
        buf.put(ByteArray(3)) // Reserved
        buf.put(0xF6.toByte()) // Clusters per index buffer
        buf.put(ByteArray(3)) // Reserved
        buf.putLong(0x1234567887654321L) // Volume Serial Number
        vbr[510] = 0x55.toByte()
        vbr[511] = 0xAA.toByte()
        return vbr
    }

    /**
     * Creates exFAT Volume Boot Record (512 bytes).
     */
    fun createExFatBootSector(
        totalPartitionSectors: Long,
        volumeLabel: String = "RUFUS",
        sectorsPerCluster: Int = 8,
        startLbaOffset: Int = 2048
    ): ByteArray {
        val structures = createCompleteExFatStructures(
            totalPartitionSectors = totalPartitionSectors,
            volumeLabel = volumeLabel,
            sectorsPerCluster = sectorsPerCluster,
            startLbaOffset = startLbaOffset
        )
        return structures.mainBootRegion.copyOfRange(0, 512)
    }
}
