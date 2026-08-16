package com.example.usb.scsi

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * SCSI Command Block Wrapper (31 bytes) for USB Bulk-Only Transport
 */
class ScsiCbw(
    val tag: Int,
    val dataTransferLength: Int,
    val flags: Byte,
    val lun: Byte,
    val commandBlock: ByteArray
) {
    fun toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(ScsiConstants.CBW_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(ScsiConstants.CBW_SIGNATURE) // dCBWSignature
        buffer.putInt(tag)                        // dCBWTag
        buffer.putInt(dataTransferLength)         // dCBWDataTransferLength
        buffer.put(flags)                         // bmCBWFlags (0x80 = In, 0x00 = Out)
        buffer.put(lun)                           // bCBWLUN
        buffer.put(commandBlock.size.toByte())    // bCBWCBLength
        buffer.put(commandBlock)                  // CBWCB

        // Zero-pad remainder of 16-byte command block area
        for (i in commandBlock.size until 16) {
            buffer.put(0.toByte())
        }

        return buffer.array()
    }
}
