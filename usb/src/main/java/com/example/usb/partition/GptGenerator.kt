package com.example.usb.partition

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.zip.CRC32

object GptGenerator {

    // Microsoft Basic Data Partition GUID: EBD0A0A2-B9E5-4433-87C0-68B6B72699C7
    val BASIC_DATA_PARTITION_GUID = UUID.fromString("ebd0a0a2-b9e5-4433-87c0-68b6b72699c7")
    // EFI System Partition (ESP) GUID: C12A7328-F81F-11D2-BA4B-00A0C93EC93B
    val EFI_SYSTEM_PARTITION_GUID = UUID.fromString("c12a7328-f81f-11d2-ba4b-00a0c93ec93b")

    /**
     * Builds GPT Partition Array (32 sectors of 512 bytes = 128 entries of 128 bytes = 16,384 bytes)
     * and GPT Header (512 bytes for LBA 1).
     */
    fun createGptStructures(
        totalSectors: Long,
        volumeLabel: String = "RUFUS_USB",
        isEfiEsp: Boolean = false
    ): Pair<ByteArray, ByteArray> {
        val entryArray = ByteArray(32 * 512) // 16 KB for 128 partition entries
        val entryBuffer = ByteBuffer.wrap(entryArray).order(ByteOrder.LITTLE_ENDIAN)

        val firstUsableLba = 2048L // 1MB alignment
        val lastUsableLba = totalSectors - 34L

        // Write Partition Entry 1 (128 bytes)
        val typeGuid = if (isEfiEsp) EFI_SYSTEM_PARTITION_GUID else BASIC_DATA_PARTITION_GUID
        writeGuidToBuffer(entryBuffer, typeGuid)
        writeGuidToBuffer(entryBuffer, UUID.randomUUID()) // Unique Partition GUID
        entryBuffer.putLong(firstUsableLba) // Starting LBA
        entryBuffer.putLong(lastUsableLba)  // Ending LBA
        entryBuffer.putLong(0L) // Attributes

        // Partition Name (UTF-16LE, up to 36 chars = 72 bytes)
        val nameChars = volumeLabel.take(36).toCharArray()
        for (ch in nameChars) {
            entryBuffer.putChar(ch)
        }
        for (i in nameChars.size until 36) {
            entryBuffer.putChar('\u0000')
        }

        // Calculate CRC32 for Partition Entry Array
        val entryCrc = CRC32()
        entryCrc.update(entryArray)
        val entryArrayCrc32 = entryCrc.value.toInt()

        // Build GPT Header (LBA 1, 512 bytes)
        val header = ByteArray(512)
        val headerBuffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        headerBuffer.put("EFI PART".toByteArray(Charsets.US_ASCII)) // Signature (8 bytes)
        headerBuffer.putInt(0x00010000) // Revision 1.0 (4 bytes)
        headerBuffer.putInt(92) // Header Size (4 bytes)
        headerBuffer.putInt(0) // Header CRC32 placeholder (4 bytes)
        headerBuffer.putInt(0) // Reserved (4 bytes)
        headerBuffer.putLong(1L) // MyLBA (LBA 1) (8 bytes)
        headerBuffer.putLong(totalSectors - 1L) // AlternateLBA (8 bytes)
        headerBuffer.putLong(firstUsableLba) // FirstUsableLBA (8 bytes)
        headerBuffer.putLong(lastUsableLba) // LastUsableLBA (8 bytes)
        writeGuidToBuffer(headerBuffer, UUID.randomUUID()) // Disk GUID (16 bytes)
        headerBuffer.putLong(2L) // PartitionEntryLBA (LBA 2) (8 bytes)
        headerBuffer.putInt(128) // NumberOfPartitionEntries (4 bytes)
        headerBuffer.putInt(128) // SizeOfPartitionEntry (4 bytes)
        headerBuffer.putInt(entryArrayCrc32) // PartitionEntryArrayCRC32 (4 bytes)

        // Calculate CRC32 of GPT Header (with CRC field = 0)
        val headerCrc = CRC32()
        headerCrc.update(header, 0, 92)
        val headerCrc32 = headerCrc.value.toInt()

        // Write calculated Header CRC32 at offset 16 (0x10)
        headerBuffer.position(16)
        headerBuffer.putInt(headerCrc32)

        return Pair(header, entryArray)
    }

    private fun writeGuidToBuffer(buffer: ByteBuffer, uuid: UUID) {
        val mostSig = uuid.mostSignificantBits
        val leastSig = uuid.leastSignificantBits

        // Convert standard UUID to Mixed-Endian GUID (Windows / UEFI format)
        val data1 = (mostSig ushr 32).toInt()
        val data2 = ((mostSig ushr 16) and 0xFFFF).toShort()
        val data3 = (mostSig and 0xFFFF).toShort()

        buffer.putInt(data1)
        buffer.putShort(data2)
        buffer.putShort(data3)
        // Data4 (8 bytes in Big Endian order)
        for (i in 7 downTo 0) {
            buffer.put(((leastSig ushr (i * 8)) and 0xFF).toByte())
        }
    }
}
