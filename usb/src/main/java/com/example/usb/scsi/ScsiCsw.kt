package com.example.usb.scsi

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * SCSI Command Status Wrapper (13 bytes) for USB Bulk-Only Transport
 */
data class ScsiCsw(
    val signature: Int,
    val tag: Int,
    val dataResidue: Int,
    val status: Byte
) {
    val isPassed: Boolean get() = (status == ScsiConstants.CSW_STATUS_PASSED)

    companion object {
        fun fromByteArray(data: ByteArray): ScsiCsw? {
            if (data.size < ScsiConstants.CSW_SIZE) return null
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val sig = buffer.int
            if (sig != ScsiConstants.CSW_SIGNATURE) return null
            val tag = buffer.int
            val residue = buffer.int
            val status = buffer.get()
            return ScsiCsw(sig, tag, residue, status)
        }
    }
}
