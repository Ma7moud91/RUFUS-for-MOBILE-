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

data class Fat32InjectedResult(
    val updatedRootDirSector: ByteArray,
    val updatedFatSector: ByteArray,
    val updatedFsInfoSector: ByteArray,
    val clustersAllocated: Int,
    val nextFreeCluster: Int
)

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
     * Injects a real file (e.g. AUTOUNAT.XML) into FAT32 root directory and updates the FAT allocation table and FSInfo.
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
        val updatedRootDir = initialRootDirSector.copyOf()
        val updatedFat = initialFatSector.copyOf()
        val updatedFsInfo = initialFsInfoSector.copyOf()

        val clusterBytes = (sectorsPerCluster * 512).coerceAtLeast(512)
        val clustersNeeded = ((fileContent.size + clusterBytes - 1) / clusterBytes).coerceAtLeast(1)

        // Write 32-byte SFN directory record at offset 32 (Entry 1, following Volume Label at Entry 0)
        val dirBuf = ByteBuffer.wrap(updatedRootDir).order(ByteOrder.LITTLE_ENDIAN)
        dirBuf.position(32)

        val sfnBytes = fileName83.take(11).padEnd(11, ' ').uppercase().toByteArray(Charsets.US_ASCII)
        dirBuf.put(sfnBytes)
        dirBuf.put(0x20.toByte()) // Attribute: Archive (0x20)
        dirBuf.put(0.toByte()) // NT reserved
        dirBuf.put(0.toByte()) // Creation time tenths
        dirBuf.putShort(0.toShort()) // Creation time
        dirBuf.putShort(0x5295.toShort()) // Creation date
        dirBuf.putShort(0x5295.toShort()) // Last access date
        dirBuf.putShort(((startCluster shr 16) and 0xFFFF).toShort()) // High cluster
        dirBuf.putShort(0.toShort()) // Write time
        dirBuf.putShort(0x5295.toShort()) // Write date
        dirBuf.putShort((startCluster and 0xFFFF).toShort()) // Low cluster
        dirBuf.putInt(fileContent.size) // File size

        // Update FAT1/FAT2 sector chain across all clusters
        val fatBuf = ByteBuffer.wrap(updatedFat).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until clustersNeeded) {
            val currentCluster = startCluster + i
            val fatOffset = currentCluster * 4
            if (fatOffset + 4 <= updatedFat.size) {
                fatBuf.position(fatOffset)
                if (i == clustersNeeded - 1) {
                    fatBuf.putInt(0x0FFFFFFF) // End of cluster chain (EOC)
                } else {
                    fatBuf.putInt(currentCluster + 1) // Link to next cluster
                }
            }
        }

        // Update FSInfo free cluster count and next free cluster pointer
        val nextFree = startCluster + clustersNeeded
        val fsBuf = ByteBuffer.wrap(updatedFsInfo).order(ByteOrder.LITTLE_ENDIAN)
        fsBuf.position(488)
        val currentFree = fsBuf.getInt(488)
        if (currentFree > 0) {
            fsBuf.putInt((currentFree - clustersNeeded).coerceAtLeast(0))
        }
        fsBuf.position(492)
        fsBuf.putInt(nextFree)

        return Fat32InjectedResult(
            updatedRootDirSector = updatedRootDir,
            updatedFatSector = updatedFat,
            updatedFsInfoSector = updatedFsInfo,
            clustersAllocated = clustersNeeded,
            nextFreeCluster = nextFree
        )
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
        val vbr = ByteArray(512)
        val buf = ByteBuffer.wrap(vbr).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(byteArrayOf(0xEB.toByte(), 0x76.toByte(), 0x90.toByte())) // Jump
        buf.put("EXFAT   ".toByteArray(Charsets.US_ASCII)) // OEM ID
        buf.put(ByteArray(53)) // Must be 0
        buf.putLong(startLbaOffset.toLong()) // Partition offset
        buf.putLong(totalPartitionSectors) // Volume length
        buf.putInt(128) // FAT offset in sectors
        val fatLen = ((totalPartitionSectors / sectorsPerCluster) * 4 / 512).coerceAtLeast(128L).toInt()
        buf.putInt(fatLen) // FAT length
        val clusterHeapOffset = 128 + (fatLen * 1)
        buf.putInt(clusterHeapOffset) // Cluster heap offset
        val clusterCount = ((totalPartitionSectors - clusterHeapOffset) / sectorsPerCluster).toInt()
        buf.putInt(clusterCount) // Cluster count
        buf.putInt(2) // First cluster of root dir
        buf.putInt(0x12345678) // Volume Serial Number
        buf.putShort(0x0100.toShort()) // File system revision
        buf.putShort(0.toShort()) // Volume flags
        buf.put(9.toByte()) // Bytes per sector shift (2^9 = 512)
        buf.put(3.toByte()) // Sectors per cluster shift (2^3 = 8)
        buf.put(1.toByte()) // Number of FATs
        buf.put(0x80.toByte()) // Drive select
        vbr[510] = 0x55.toByte()
        vbr[511] = 0xAA.toByte()
        return vbr
    }
}
