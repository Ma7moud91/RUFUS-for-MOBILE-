package com.example.usb.partition

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.zip.CRC32

data class GptStructures(
    val primaryHeader: ByteArray,       // LBA 1 (512 bytes)
    val primaryEntryArray: ByteArray,   // LBA 2..33 (16,384 bytes)
    val backupEntryArray: ByteArray,    // LBA (totalSectors - 33) .. (totalSectors - 2) (16,384 bytes)
    val backupHeader: ByteArray         // LBA (totalSectors - 1) (512 bytes)
)

object GptGenerator {

    // Microsoft Basic Data Partition GUID: EBD0A0A2-B9E5-4433-87C0-68B6B72699C7
    val BASIC_DATA_PARTITION_GUID = UUID.fromString("ebd0a0a2-b9e5-4433-87c0-68b6b72699c7")
    // EFI System Partition (ESP) GUID: C12A7328-F81F-11D2-BA4B-00A0C93EC93B
    val EFI_SYSTEM_PARTITION_GUID = UUID.fromString("c12a7328-f81f-11d2-ba4b-00a0c93ec93b")

    /**
     * Builds Primary & Backup GPT Partition Arrays (32 sectors = 128 entries of 128 bytes = 16,384 bytes)
     * and Primary (LBA 1) + Backup GPT Header (LBA totalSectors - 1) in full compliance with UEFI specification.
     */
    fun createCompleteGptStructures(
        totalSectors: Long,
        volumeLabel: String = "RUFUS_USB",
        isEfiEsp: Boolean = false
    ): GptStructures {
        val entryArray = ByteArray(32 * 512) // 16 KB for 128 partition entries
        val entryBuffer = ByteBuffer.wrap(entryArray).order(ByteOrder.LITTLE_ENDIAN)

        val firstUsableLba = 2048L // 1MB alignment
        val lastUsableLba = (totalSectors - 34L).coerceAtLeast(firstUsableLba)

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

        val diskGuid = UUID.randomUUID()

        // 1. Build Primary GPT Header (LBA 1, 512 bytes)
        val primaryHeader = ByteArray(512)
        val pHeaderBuf = ByteBuffer.wrap(primaryHeader).order(ByteOrder.LITTLE_ENDIAN)
        pHeaderBuf.put("EFI PART".toByteArray(Charsets.US_ASCII)) // Signature (8 bytes)
        pHeaderBuf.putInt(0x00010000) // Revision 1.0 (4 bytes)
        pHeaderBuf.putInt(92) // Header Size (4 bytes)
        pHeaderBuf.putInt(0) // Header CRC32 placeholder (4 bytes)
        pHeaderBuf.putInt(0) // Reserved (4 bytes)
        pHeaderBuf.putLong(1L) // MyLBA (LBA 1) (8 bytes)
        pHeaderBuf.putLong(totalSectors - 1L) // AlternateLBA (8 bytes)
        pHeaderBuf.putLong(firstUsableLba) // FirstUsableLBA (8 bytes)
        pHeaderBuf.putLong(lastUsableLba) // LastUsableLBA (8 bytes)
        writeGuidToBuffer(pHeaderBuf, diskGuid) // Disk GUID (16 bytes)
        pHeaderBuf.putLong(2L) // PartitionEntryLBA (LBA 2) (8 bytes)
        pHeaderBuf.putInt(128) // NumberOfPartitionEntries (4 bytes)
        pHeaderBuf.putInt(128) // SizeOfPartitionEntry (4 bytes)
        pHeaderBuf.putInt(entryArrayCrc32) // PartitionEntryArrayCRC32 (4 bytes)

        val pHdrCrc = CRC32()
        pHdrCrc.update(primaryHeader, 0, 92)
        pHeaderBuf.position(16)
        pHeaderBuf.putInt(pHdrCrc.value.toInt())

        // 2. Build Backup GPT Header (LBA totalSectors - 1, 512 bytes)
        val backupHeader = ByteArray(512)
        val bHeaderBuf = ByteBuffer.wrap(backupHeader).order(ByteOrder.LITTLE_ENDIAN)
        bHeaderBuf.put("EFI PART".toByteArray(Charsets.US_ASCII)) // Signature (8 bytes)
        bHeaderBuf.putInt(0x00010000) // Revision 1.0 (4 bytes)
        bHeaderBuf.putInt(92) // Header Size (4 bytes)
        bHeaderBuf.putInt(0) // Header CRC32 placeholder (4 bytes)
        bHeaderBuf.putInt(0) // Reserved (4 bytes)
        bHeaderBuf.putLong(totalSectors - 1L) // MyLBA = last sector
        bHeaderBuf.putLong(1L) // AlternateLBA = LBA 1
        bHeaderBuf.putLong(firstUsableLba) // FirstUsableLBA
        bHeaderBuf.putLong(lastUsableLba) // LastUsableLBA
        writeGuidToBuffer(bHeaderBuf, diskGuid) // Disk GUID
        bHeaderBuf.putLong(totalSectors - 33L) // PartitionEntryLBA = totalSectors - 33
        bHeaderBuf.putInt(128) // NumberOfPartitionEntries
        bHeaderBuf.putInt(128) // SizeOfPartitionEntry
        bHeaderBuf.putInt(entryArrayCrc32) // PartitionEntryArrayCRC32

        val bHdrCrc = CRC32()
        bHdrCrc.update(backupHeader, 0, 92)
        bHeaderBuf.position(16)
        bHeaderBuf.putInt(bHdrCrc.value.toInt())

        val backupEntryArray = entryArray.copyOf()

        return GptStructures(
            primaryHeader = primaryHeader,
            primaryEntryArray = entryArray,
            backupEntryArray = backupEntryArray,
            backupHeader = backupHeader
        )
    }

    /**
     * Backward-compatible helper returning Pair(primaryHeader, primaryEntryArray).
     */
    fun createGptStructures(
        totalSectors: Long,
        volumeLabel: String = "RUFUS_USB",
        isEfiEsp: Boolean = false
    ): Pair<ByteArray, ByteArray> {
        val complete = createCompleteGptStructures(totalSectors, volumeLabel, isEfiEsp)
        return Pair(complete.primaryHeader, complete.primaryEntryArray)
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
