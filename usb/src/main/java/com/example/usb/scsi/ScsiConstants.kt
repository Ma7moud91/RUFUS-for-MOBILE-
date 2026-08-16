package com.example.usb.scsi

object ScsiConstants {
    // CBW / CSW Signatures
    const val CBW_SIGNATURE = 0x43425355 // 'USBC' in Little Endian
    const val CSW_SIGNATURE = 0x53425355 // 'USBS' in Little Endian

    const val CBW_SIZE = 31
    const val CSW_SIZE = 13

    // SCSI Command Opcodes
    const val TEST_UNIT_READY: Byte = 0x00
    const val REQUEST_SENSE: Byte = 0x03
    const val INQUIRY: Byte = 0x12
    const val MODE_SENSE_6: Byte = 0x1A
    const val START_STOP_UNIT: Byte = 0x1B
    const val READ_CAPACITY_10: Byte = 0x25
    const val READ_10: Byte = 0x28
    const val WRITE_10: Byte = 0x2A
    const val SYNCHRONIZE_CACHE_10: Byte = 0x35
    const val READ_CAPACITY_16: Byte = 0x9E.toByte()
    const val READ_16: Byte = 0x88.toByte()
    const val WRITE_16: Byte = 0x8A.toByte()

    // Direction Flags
    const val DIRECTION_OUT: Byte = 0x00 // Host to Device
    const val DIRECTION_IN: Byte = 0x80.toByte() // Device to Host

    // CSW Status
    const val CSW_STATUS_PASSED: Byte = 0x00
    const val CSW_STATUS_FAILED: Byte = 0x01
    const val CSW_STATUS_PHASE_ERROR: Byte = 0x02

    // Sector & Buffer Sizes
    const val DEFAULT_SECTOR_SIZE = 512
    const val MAX_BLOCK_TRANSFER_SIZE = 64 * 1024 // 64 KB (128 sectors of 512 bytes)
}
