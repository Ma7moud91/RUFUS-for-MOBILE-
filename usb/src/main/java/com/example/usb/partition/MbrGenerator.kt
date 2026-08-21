package com.example.usb.partition

import java.nio.ByteBuffer
import java.nio.ByteOrder

object MbrGenerator {

    const val PARTITION_TYPE_FAT32_LBA: Byte = 0x0C
    const val PARTITION_TYPE_NTFS_EXFAT: Byte = 0x07
    const val PARTITION_TYPE_LINUX_NATIVE: Byte = 0x83.toByte()
    const val PARTITION_TYPE_GPT_PROTECTIVE: Byte = 0xEE.toByte()

    fun createProtectiveMbr(totalSectors: Long): ByteArray {
        val mbr = ByteArray(512)
        val buffer = ByteBuffer.wrap(mbr).order(ByteOrder.LITTLE_ENDIAN)

        // Partition 1 entry starts at offset 446 (0x1BE)
        buffer.position(446)
        buffer.put(0x00.toByte()) // Boot indicator (Non-bootable)
        buffer.put(byteArrayOf(0x00, 0x02, 0x00)) // Starting CHS (0/0/2)
        buffer.put(PARTITION_TYPE_GPT_PROTECTIVE) // Type 0xEE (GPT Protective)
        buffer.put(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())) // Ending CHS
        buffer.putInt(1) // Starting LBA = 1
        val size = if (totalSectors > 0xFFFFFFFFL) 0xFFFFFFFF.toInt() else (totalSectors - 1).toInt()
        buffer.putInt(size) // Size in LBA sectors

        // Boot signature 0x55, 0xAA at 510-511
        mbr[510] = 0x55.toByte()
        mbr[511] = 0xAA.toByte()

        return mbr
    }

    fun createStandardMbr(
        partitionType: Byte,
        startLba: Int = 2048, // 1MB standard alignment
        totalSectors: Long,
        isBootable: Boolean = true
    ): ByteArray {
        val mbr = ByteArray(512)
        val buffer = ByteBuffer.wrap(mbr).order(ByteOrder.LITTLE_ENDIAN)

        // Partition 1 entry at offset 446
        buffer.position(446)
        buffer.put(if (isBootable) 0x80.toByte() else 0x00.toByte()) // Boot indicator
        buffer.put(byteArrayOf(0x00, 0x20, 0x21)) // Start CHS approx
        buffer.put(partitionType) // Partition type
        buffer.put(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())) // End CHS
        buffer.putInt(startLba) // Start LBA = 2048
        val rawSize = totalSectors - startLba
        val partitionSize = (if (rawSize > 0xFFFFFFFFL) 0xFFFFFFFFL else rawSize.coerceAtLeast(1L)).toInt()
        buffer.putInt(partitionSize) // Size in sectors

        // Boot signature 0x55, 0xAA
        mbr[510] = 0x55.toByte()
        mbr[511] = 0xAA.toByte()

        return mbr
    }
}
