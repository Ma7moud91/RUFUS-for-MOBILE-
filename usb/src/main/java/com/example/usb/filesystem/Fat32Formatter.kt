package com.example.usb.filesystem

import java.nio.ByteBuffer
import java.nio.ByteOrder

object Fat32Formatter {

    /**
     * Creates FAT32 Volume Boot Record (512 bytes) and FSInfo sector (512 bytes).
     */
    fun createFat32BootSectors(
        totalPartitionSectors: Long,
        volumeLabel: String = "RUFUS",
        sectorsPerCluster: Int = 8 // 4KB cluster with 512B sectors
    ): Pair<ByteArray, ByteArray> {
        val vbr = ByteArray(512)
        val buf = ByteBuffer.wrap(vbr).order(ByteOrder.LITTLE_ENDIAN)

        // Jump to boot code (EB 58 90)
        buf.put(byteArrayOf(0xEB.toByte(), 0x58.toByte(), 0x90.toByte()))
        // OEM Name (8 bytes)
        buf.put("MSDOS5.0".toByteArray(Charsets.US_ASCII))
        buf.putShort(512.toShort()) // Bytes per sector
        buf.put(sectorsPerCluster.toByte()) // Sectors per cluster
        buf.putShort(32.toShort()) // Reserved sector count (32 sectors)
        buf.put(2.toByte()) // Number of FATs (2)
        buf.putShort(0.toShort()) // Root directory entries (0 for FAT32)
        buf.putShort(0.toShort()) // Total sectors 16-bit (0 for FAT32)
        buf.put(0xF8.toByte()) // Media descriptor (Fixed disk)
        buf.putShort(0.toShort()) // Sectors per FAT 16-bit (0)
        buf.putShort(63.toShort()) // Sectors per track
        buf.putShort(255.toShort()) // Number of heads
        buf.putInt(2048) // Hidden sectors (Start LBA offset)
        buf.putInt(totalPartitionSectors.toInt()) // Total sectors 32-bit

        // FAT32 Extended BPB
        val sectorsPerFat = ((totalPartitionSectors / sectorsPerCluster) * 4 / 512).coerceAtLeast(1024L).toInt()
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

        // FSInfo Sector (512 bytes)
        val fsInfo = ByteArray(512)
        val fsBuf = ByteBuffer.wrap(fsInfo).order(ByteOrder.LITTLE_ENDIAN)
        fsBuf.putInt(0x41615252) // Lead signature ("RRaA")
        fsBuf.put(ByteArray(480)) // Reserved 480 bytes
        fsBuf.position(484)
        fsBuf.putInt(0x61417272) // Struct signature ("rrAa")
        val freeClusters = ((totalPartitionSectors - 32 - (sectorsPerFat * 2)) / sectorsPerCluster).toInt()
        fsBuf.putInt(freeClusters) // Free cluster count
        fsBuf.putInt(3) // Next free cluster
        fsInfo[510] = 0x55.toByte()
        fsInfo[511] = 0xAA.toByte()

        return Pair(vbr, fsInfo)
    }
}
