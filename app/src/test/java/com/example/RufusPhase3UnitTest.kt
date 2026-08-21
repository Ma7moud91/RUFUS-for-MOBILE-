package com.example

import com.example.usb.filesystem.Fat12FloppyParser
import com.example.usb.filesystem.Fat32Formatter
import com.example.usb.filesystem.InjectedFile
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RufusPhase3UnitTest {

    @Test
    fun testExFatCompleteStructures() {
        val totalSectors = 204800L // 100 MB partition
        val exFat = Fat32Formatter.createCompleteExFatStructures(
            totalPartitionSectors = totalSectors,
            volumeLabel = "MYEXFAT",
            sectorsPerCluster = 8,
            startLbaOffset = 2048
        )

        // 1. Verify Main Boot Region size (12 sectors = 6144 bytes)
        assertEquals(12 * 512, exFat.mainBootRegion.size)
        assertEquals(12 * 512, exFat.backupBootRegion.size)
        assertArrayEquals(exFat.mainBootRegion, exFat.backupBootRegion)

        // 2. Verify VBR (Sector 0)
        val vbr = exFat.mainBootRegion.copyOfRange(0, 512)
        assertEquals(0xEB.toByte(), vbr[0])
        assertEquals(0x76.toByte(), vbr[1])
        assertEquals(0x90.toByte(), vbr[2])
        val oemName = String(vbr, 3, 8, Charsets.US_ASCII)
        assertEquals("EXFAT   ", oemName)
        assertEquals(0x55.toByte(), vbr[510])
        assertEquals(0xAA.toByte(), vbr[511])

        val vbrBuf = ByteBuffer.wrap(vbr).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(2048L, vbrBuf.getLong(64)) // PartitionOffset
        assertEquals(totalSectors, vbrBuf.getLong(72)) // VolumeLength
        assertEquals(4, vbrBuf.getInt(96)) // FirstClusterOfRootDirectory
        assertEquals(9.toByte(), vbr[108]) // BytesPerSectorShift (2^9 = 512)
        assertEquals(3.toByte(), vbr[109]) // SectorsPerClusterShift (2^3 = 8)

        // 3. Verify Sector 8 has 0x55AA signature
        assertEquals(0x55.toByte(), exFat.mainBootRegion[8 * 512 + 510])
        assertEquals(0xAA.toByte(), exFat.mainBootRegion[8 * 512 + 511])

        // 4. Verify Sector 9 (OEM parameter) and Sector 10 (Reserved) are zero-filled
        for (b in 9 * 512 until 11 * 512) {
            assertEquals(0.toByte(), exFat.mainBootRegion[b])
        }

        // 5. Verify Checksum sector (Sector 11)
        val checksumSec = exFat.mainBootRegion.copyOfRange(11 * 512, 12 * 512)
        val checksumBuf = ByteBuffer.wrap(checksumSec).order(ByteOrder.LITTLE_ENDIAN)
        val firstWord = checksumBuf.getInt(0)
        assertNotEquals(0, firstWord)
        for (i in 0 until 128) {
            assertEquals("Checksum word $i must match", firstWord, checksumBuf.getInt(i * 4))
        }

        // 6. Verify Allocation Bitmap cluster
        assertEquals(0x05.toByte(), exFat.allocationBitmapCluster[0]) // Bits 0 and 2 set

        // 7. Verify Root Directory cluster (Cluster 4)
        val rootDir = exFat.rootDirCluster
        assertEquals(0x83.toByte(), rootDir[0]) // Volume label entry
        assertEquals(7.toByte(), rootDir[1]) // Length of "MYEXFAT"
        assertEquals(0x81.toByte(), rootDir[32]) // Allocation bitmap entry
        val rootBuf = ByteBuffer.wrap(rootDir).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(2, rootBuf.getInt(32 + 20)) // FirstCluster = 2
    }

    @Test
    fun testMultiFileFat32Injection() {
        val fatStructures = Fat32Formatter.createCompleteFat32Structures(
            totalPartitionSectors = 65536L,
            volumeLabel = "TESTFAT",
            sectorsPerCluster = 8
        )

        val file1 = InjectedFile("FILE1   TXT", "Content of file 1".toByteArray(Charsets.US_ASCII))
        val file2 = InjectedFile("FILE2   DAT", ByteArray(5000) { 0x42.toByte() }) // > 4096 bytes = 2 clusters

        val result = Fat32Formatter.createRootDirectoryFiles(
            initialRootDirSector = fatStructures.initialRootDirSector,
            initialFatSector = fatStructures.initialFatSector,
            initialFsInfoSector = fatStructures.fsInfo,
            rootDirLba = 2048L,
            sectorsPerCluster = 8,
            files = listOf(file1, file2),
            startCluster = 3
        )

        // file1 needs 1 cluster (cluster 3), file2 needs 2 clusters (clusters 4, 5) -> total 3 clusters
        assertEquals(3, result.clustersAllocated)
        assertEquals(6, result.nextFreeCluster)

        val fatBuf = ByteBuffer.wrap(result.updatedFatSector).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x0FFFFFFF, fatBuf.getInt(3 * 4)) // Cluster 3 EOC
        assertEquals(5, fatBuf.getInt(4 * 4)) // Cluster 4 links to 5
        assertEquals(0x0FFFFFFF, fatBuf.getInt(5 * 4)) // Cluster 5 EOC

        // Check root dir entries
        val dirBuf = ByteBuffer.wrap(result.updatedRootDirSector).order(ByteOrder.LITTLE_ENDIAN)
        // Entry 0 is volume label
        assertEquals(0x08.toByte(), result.updatedRootDirSector[11])
        // Entry 1 is file 1 (start cluster 3)
        assertEquals(3.toShort(), dirBuf.getShort(32 + 26))
        assertEquals(file1.content.size, dirBuf.getInt(32 + 28))
        // Entry 2 is file 2 (start cluster 4)
        assertEquals(4.toShort(), dirBuf.getShort(64 + 26))
        assertEquals(file2.content.size, dirBuf.getInt(64 + 28))
    }

    @Test
    fun testEfiBootTreeGeneration() {
        val fatStructures = Fat32Formatter.createCompleteFat32Structures(
            totalPartitionSectors = 65536L,
            volumeLabel = "UEFITEST",
            sectorsPerCluster = 8
        )

        val shellPayload = ByteArray(10000) { 0x90.toByte() } // ~3 clusters

        val efiTree = Fat32Formatter.createEfiBootTree(
            initialRootDirSector = fatStructures.initialRootDirSector,
            initialFatSector = fatStructures.initialFatSector,
            initialFsInfoSector = fatStructures.fsInfo,
            sectorsPerCluster = 8,
            efiBinaryPayload = shellPayload,
            startCluster = 3
        )

        assertEquals(3, efiTree.efiDirCluster)
        assertEquals(4, efiTree.bootDirCluster)
        assertEquals(5, efiTree.payloadStartCluster)
        assertEquals(5, efiTree.totalClustersAllocated) // 1 for EFI + 1 for BOOT + 3 for payload

        // Verify root dir entry 1 is "EFI        "
        val rootDirStr = String(efiTree.updatedRootDirSector, 32, 11, Charsets.US_ASCII)
        assertEquals("EFI        ", rootDirStr)

        // Verify EFI dir entry 2 is "BOOT       "
        val efiDirStr = String(efiTree.efiDirClusterSector, 64, 11, Charsets.US_ASCII)
        assertEquals("BOOT       ", efiDirStr)

        // Verify BOOT dir entry 2 is "BOOTX64 EFI"
        val bootDirStr = String(efiTree.bootDirClusterSector, 64, 11, Charsets.US_ASCII)
        assertEquals("BOOTX64 EFI", bootDirStr)
    }

    @Test
    fun testFat12FloppyParser() {
        // Construct a synthetic 1.44MB FAT12 floppy disk image
        val image = ByteArray(2880 * 512)
        val buf = ByteBuffer.wrap(image).order(ByteOrder.LITTLE_ENDIAN)

        // BPB
        buf.putShort(11, 512.toShort()) // Bytes per sector
        image[13] = 1.toByte() // Sectors per cluster
        buf.putShort(14, 1.toShort()) // Reserved sectors (1)
        image[16] = 2.toByte() // Number of FATs (2)
        buf.putShort(17, 224.toShort()) // Root directory entries
        buf.putShort(22, 9.toShort()) // Sectors per FAT (9)

        // FAT start = 1 * 512 = 512
        // Root dir start = (1 + 2*9) * 512 = 19 * 512 = 9728
        // Data start = 9728 + 224 * 32 = 9728 + 7168 = 16896 (Sector 33)

        // Set FAT entries for cluster 2 (KERNEL.SYS, 1 cluster) and cluster 3->4 (COMMAND.COM, 2 clusters)
        // FAT12 entries:
        // Cluster 0: 0xFF0
        // Cluster 1: 0xFFF
        // Cluster 2: 0xFFF (EOF)
        // Cluster 3: 0x004
        // Cluster 4: 0xFFF (EOF)

        // Pack clusters 0, 1: 0xFF0, 0xFFF -> bytes: F0 FF FF
        image[512] = 0xF0.toByte(); image[513] = 0xFF.toByte(); image[514] = 0xFF.toByte()
        // Pack clusters 2, 3: cluster 2 = 0xFFF, cluster 3 = 0x004 -> bytes: FF 4F 00
        image[515] = 0xFF.toByte(); image[516] = 0x4F.toByte(); image[517] = 0x00.toByte()
        // Pack clusters 4, 5: cluster 4 = 0xFFF, cluster 5 = 0x000 -> bytes: FF 0F 00
        image[518] = 0xFF.toByte(); image[519] = 0x0F.toByte(); image[520] = 0x00.toByte()

        // Root dir entry 0: KERNEL.SYS
        val rootDirOffset = 19 * 512
        System.arraycopy("KERNEL  SYS".toByteArray(Charsets.US_ASCII), 0, image, rootDirOffset, 11)
        image[rootDirOffset + 11] = 0x20.toByte() // Archive
        image[rootDirOffset + 26] = 2.toByte()
        image[rootDirOffset + 27] = 0.toByte()
        image[rootDirOffset + 28] = 100.toByte()
        image[rootDirOffset + 29] = 0.toByte()
        image[rootDirOffset + 30] = 0.toByte()
        image[rootDirOffset + 31] = 0.toByte()

        // Root dir entry 1: COMMAND.COM
        val entry1Offset = rootDirOffset + 32
        System.arraycopy("COMMAND COM".toByteArray(Charsets.US_ASCII), 0, image, entry1Offset, 11)
        image[entry1Offset + 11] = 0x20.toByte()
        image[entry1Offset + 26] = 3.toByte()
        image[entry1Offset + 27] = 0.toByte()
        image[entry1Offset + 28] = (800 and 0xFF).toByte()
        image[entry1Offset + 29] = ((800 ushr 8) and 0xFF).toByte()
        image[entry1Offset + 30] = 0.toByte()
        image[entry1Offset + 31] = 0.toByte()

        // Write file payload data
        val dataOffset = 33 * 512
        val cluster2Offset = dataOffset + (2 - 2) * 512
        val kernelData = "KERNEL DATA 123456789".toByteArray(Charsets.US_ASCII)
        System.arraycopy(kernelData, 0, image, cluster2Offset, kernelData.size)

        val cluster3Offset = dataOffset + (3 - 2) * 512
        val cmdDataPart1 = ByteArray(512) { 'A'.code.toByte() }
        System.arraycopy(cmdDataPart1, 0, image, cluster3Offset, 512)

        val cluster4Offset = dataOffset + (4 - 2) * 512
        val cmdDataPart2 = ByteArray(288) { 'B'.code.toByte() }
        System.arraycopy(cmdDataPart2, 0, image, cluster4Offset, 288)

        // Parse floppy image
        val extracted = Fat12FloppyParser.extractAllFiles(image)
        assertTrue(extracted.containsKey("KERNEL.SYS"))
        assertTrue(extracted.containsKey("COMMAND.COM"))

        val extractedKernel = extracted["KERNEL.SYS"]!!
        assertEquals(100, extractedKernel.size)
        assertEquals("KERNEL DATA 123456789", String(extractedKernel, 0, kernelData.size, Charsets.US_ASCII))

        val extractedCmd = extracted["COMMAND.COM"]!!
        assertEquals(800, extractedCmd.size)
        assertEquals('A'.code.toByte(), extractedCmd[0])
        assertEquals('B'.code.toByte(), extractedCmd[512])
    }
}
