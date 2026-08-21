package com.example.usb.scsi

import android.hardware.usb.*
import android.util.Log
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

/**
 * High-performance, robust USB Mass Storage Driver implementing the USB Mass Storage
 * Class Bulk-Only Transport (BOT) Specification and SCSI Primary/Block Command Sets.
 *
 * Features:
 * - Robust BOT Error Recovery (ClearFeature ENDPOINT_HALT & Bulk-Only Mass Storage Reset)
 * - Automatic Request Sense on Unit Attention / Media Change conditions
 * - READ CAPACITY (10) and READ CAPACITY (16) support for large drives (> 2TB)
 * - Safe 32KB hardware packet chunking to prevent mobile USB Host Controller DMA drops
 * - Hardware cache synchronization to ensure all blocks are committed to NAND flash
 */
class UsbMassStorageDriver(
    private val usbManager: UsbManager,
    private val usbDevice: UsbDevice
) : Closeable {

    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var endpointIn: UsbEndpoint? = null
    private var endpointOut: UsbEndpoint? = null
    private val tagGenerator = AtomicInteger(1)

    var sectorSize: Int = ScsiConstants.DEFAULT_SECTOR_SIZE
        private set
    var totalSectors: Long = 0L
        private set
    var totalCapacityBytes: Long = 0L
        private set
    var vendorIdentification: String = ""
        private set
    var productIdentification: String = ""
        private set

    companion object {
        private const val TAG = "UsbMassStorageDriver"
        private const val TIMEOUT_MS = 6000
        private const val SAFE_CHUNK_BYTES = 32 * 1024 // 32 KB safe DMA chunk size
    }

    fun open(): Boolean {
        try {
            // Close any stale existing connection before opening anew
            if (connection != null) {
                close()
            }

            if (!usbManager.hasPermission(usbDevice)) {
                Log.e(TAG, "No USB Host permission granted for device: ${usbDevice.deviceName}")
                return false
            }

            // 1. Find Mass Storage Interface (Class 8)
            var targetInterface: UsbInterface? = null
            for (i in 0 until usbDevice.interfaceCount) {
                val iface = usbDevice.getInterface(i)
                if (iface.interfaceClass == 8) { // USB Mass Storage
                    targetInterface = iface
                    break
                }
            }

            if (targetInterface == null && usbDevice.interfaceCount > 0) {
                targetInterface = usbDevice.getInterface(0)
            }

            if (targetInterface == null) {
                Log.e(TAG, "No suitable USB Mass Storage interface found on ${usbDevice.deviceName}")
                return false
            }

            // 2. Locate Bulk IN and Bulk OUT endpoints
            var inEp: UsbEndpoint? = null
            var outEp: UsbEndpoint? = null
            for (i in 0 until targetInterface.endpointCount) {
                val ep = targetInterface.getEndpoint(i)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN) {
                        inEp = ep
                    } else {
                        outEp = ep
                    }
                }
            }

            if (inEp == null || outEp == null) {
                Log.e(TAG, "Could not find Bulk IN/OUT endpoint pair on interface ${targetInterface.id}")
                return false
            }

            val conn = usbManager.openDevice(usbDevice)
            if (conn == null) {
                Log.e(TAG, "Failed to open UsbDeviceConnection (returned null)")
                return false
            }

            // Claim interface; force=true detaches kernel storage driver cleanly
            if (!conn.claimInterface(targetInterface, true)) {
                Log.e(TAG, "Failed to claim USB interface ${targetInterface.id}")
                conn.close()
                return false
            }

            this.connection = conn
            this.usbInterface = targetInterface
            this.endpointIn = inEp
            this.endpointOut = outEp

            // 3. Perform BOT Reset & Clear Halt to ensure clean SCSI pipe state
            resetBotPipe()

            // 4. Send SCSI INQUIRY (0x12)
            executeInquiry()

            // 5. Test Unit Ready loop (handles spinning up / unit attention)
            var isReady = false
            for (attempt in 1..6) {
                if (testUnitReady()) {
                    isReady = true
                    break
                }
                requestSense()
                try { Thread.sleep(100) } catch (ignored: InterruptedException) {}
            }

            // 6. Read Drive Geometry (Read Capacity 10 / 16)
            var capRead = readCapacity()
            if (!capRead || totalSectors <= 0L) {
                Log.w(TAG, "Read capacity returned 0 or failed. Attempting secondary probe with REQUEST SENSE...")
                requestSense()
                capRead = readCapacity()
            }

            if (!capRead || totalSectors <= 0L) {
                Log.e(TAG, "Unable to determine storage media capacity for ${usbDevice.deviceName}. Aborting open to prevent data corruption.")
                close()
                return false
            }

            if (sectorSize !in 512..4096) sectorSize = 512
            totalCapacityBytes = totalSectors * sectorSize

            Log.i(TAG, "USB Mass Storage successfully opened: $totalSectors sectors ($sectorSize B/sec) = ${totalCapacityBytes / (1024 * 1024)} MB")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Exception opening USB Mass Storage: ${e.message}", e)
            close()
            return false
        }
    }

    /**
     * Issues a USB Bulk-Only Mass Storage Reset (BOT class-specific request 0xFF)
     * and clears the HALT feature on both bulk endpoints.
     */
    fun resetBotPipe() {
        val conn = connection ?: return
        val iface = usbInterface ?: return
        try {
            // Bulk-Only Mass Storage Reset (bmRequestType: 0x21, bRequest: 0xFF)
            conn.controlTransfer(0x21, 0xFF, 0, iface.id, null, 0, 1500)
            
            // Clear Halt Feature on Bulk IN and Bulk OUT endpoints
            endpointIn?.let { clearEndpointHalt(it) }
            endpointOut?.let { clearEndpointHalt(it) }
        } catch (e: Exception) {
            Log.w(TAG, "BOT pipe reset notice: ${e.message}")
        }
    }

    /**
     * Sends USB standard CLEAR_FEATURE ENDPOINT_HALT (0x01) to un-stall a pipe.
     */
    fun clearEndpointHalt(endpoint: UsbEndpoint) {
        val conn = connection ?: return
        try {
            conn.controlTransfer(
                0x02, // USB_RECIP_ENDPOINT | USB_REQ_STANDARD
                0x01, // USB_REQ_CLEAR_FEATURE
                0x00, // ENDPOINT_HALT
                endpoint.address,
                null,
                0,
                1500
            )
        } catch (e: Exception) {
            Log.w(TAG, "Clear halt failed for endpoint ${endpoint.address}: ${e.message}")
        }
    }

    /**
     * Executes SCSI INQUIRY (0x12) command to retrieve device strings.
     */
    fun executeInquiry(): Boolean {
        val cdb = byteArrayOf(ScsiConstants.INQUIRY, 0, 0, 0, 36, 0)
        val tag = tagGenerator.getAndIncrement()
        val cbw = ScsiCbw(tag, 36, ScsiConstants.DIRECTION_IN, 0, cdb)
        if (!sendCbw(cbw)) return false

        val buffer = ByteArray(36)
        val read = receiveData(buffer)
        val csw = receiveCsw(tag)

        if (csw?.isPassed == true && read >= 36) {
            vendorIdentification = String(buffer, 8, 8, Charsets.US_ASCII).trim()
            productIdentification = String(buffer, 16, 16, Charsets.US_ASCII).trim()
            Log.d(TAG, "SCSI INQUIRY: Vendor='$vendorIdentification', Product='$productIdentification'")
            return true
        }
        return false
    }

    /**
     * Executes SCSI TEST UNIT READY (0x00).
     */
    fun testUnitReady(): Boolean {
        val cdb = byteArrayOf(ScsiConstants.TEST_UNIT_READY, 0, 0, 0, 0, 0)
        val tag = tagGenerator.getAndIncrement()
        val cbw = ScsiCbw(tag, 0, ScsiConstants.DIRECTION_OUT, 0, cdb)
        if (!sendCbw(cbw)) {
            endpointOut?.let { clearEndpointHalt(it) }
            return false
        }
        val csw = receiveCsw(tag)
        return csw?.isPassed == true
    }

    /**
     * Executes SCSI REQUEST SENSE (0x03) to read and clear Sense Keys / Unit Attention.
     */
    fun requestSense(): ByteArray? {
        val cdb = byteArrayOf(ScsiConstants.REQUEST_SENSE, 0, 0, 0, 18, 0)
        val tag = tagGenerator.getAndIncrement()
        val cbw = ScsiCbw(tag, 18, ScsiConstants.DIRECTION_IN, 0, cdb)
        if (!sendCbw(cbw)) {
            endpointOut?.let { clearEndpointHalt(it) }
            return null
        }

        val buffer = ByteArray(18)
        val read = receiveData(buffer)
        val csw = receiveCsw(tag)

        if (read >= 18 && csw != null) {
            val senseKey = buffer[2].toInt() and 0x0F
            val asc = buffer[12].toInt() and 0xFF
            val ascq = buffer[13].toInt() and 0xFF
            Log.d(TAG, String.format("SCSI Sense: Key=0x%02X, ASC=0x%02X, ASCQ=0x%02X", senseKey, asc, ascq))
            return buffer
        }
        return null
    }

    /**
     * Executes SCSI READ CAPACITY (10) and (16) if needed.
     */
    fun readCapacity(): Boolean {
        val cdb = byteArrayOf(ScsiConstants.READ_CAPACITY_10, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val tag = tagGenerator.getAndIncrement()
        val cbw = ScsiCbw(tag, 8, ScsiConstants.DIRECTION_IN, 0, cdb)
        if (!sendCbw(cbw)) {
            endpointOut?.let { clearEndpointHalt(it) }
            return false
        }

        val buffer = ByteArray(8)
        val readBytes = receiveData(buffer)
        val csw = receiveCsw(tag)

        if (csw?.isPassed == true && readBytes >= 8) {
            val bb = ByteBuffer.wrap(buffer).order(ByteOrder.BIG_ENDIAN)
            val lastLba = bb.int.toLong() and 0xFFFFFFFFL
            val blockSize = bb.int

            if (lastLba == 0xFFFFFFFFL) {
                // Capacity exceeds 2TB; issue READ CAPACITY (16)
                return readCapacity16()
            }

            this.sectorSize = if (blockSize in 512..4096) blockSize else 512
            this.totalSectors = lastLba + 1
            this.totalCapacityBytes = this.totalSectors * this.sectorSize
            return true
        }
        return false
    }

    private fun readCapacity16(): Boolean {
        val cdb = ByteArray(16)
        cdb[0] = ScsiConstants.READ_CAPACITY_16
        cdb[1] = 0x10.toByte() // Service Action
        cdb[13] = 32.toByte()  // Allocation length (32 bytes)

        val tag = tagGenerator.getAndIncrement()
        val cbw = ScsiCbw(tag, 32, ScsiConstants.DIRECTION_IN, 0, cdb)
        if (!sendCbw(cbw)) return false

        val buffer = ByteArray(32)
        val readBytes = receiveData(buffer)
        val csw = receiveCsw(tag)

        if (csw?.isPassed == true && readBytes >= 32) {
            val bb = ByteBuffer.wrap(buffer).order(ByteOrder.BIG_ENDIAN)
            val lastLba = bb.long
            val blockSize = bb.int
            this.sectorSize = if (blockSize in 512..4096) blockSize else 512
            this.totalSectors = lastLba + 1
            this.totalCapacityBytes = this.totalSectors * this.sectorSize
            return true
        }
        return false
    }

    /**
     * Writes raw bytes to specified LBA sectors using SCSI WRITE (10).
     * Chunked into safe 32KB buffers to guarantee stability across all mobile host controllers.
     */
    fun writeSectors(startLba: Long, data: ByteArray): Boolean {
        val conn = connection ?: return false
        val epOut = endpointOut ?: return false
        val totalSectorsToWrite = (data.size + sectorSize - 1) / sectorSize

        var sectorOffset = 0
        while (sectorOffset < totalSectorsToWrite) {
            val sectorsThisBatch = Math.min(totalSectorsToWrite - sectorOffset, SAFE_CHUNK_BYTES / sectorSize)
            val byteOffset = sectorOffset * sectorSize
            val bytesThisBatch = Math.min(data.size - byteOffset, sectorsThisBatch * sectorSize)
            val currentLba = startLba + sectorOffset

            // Pad buffer to full sector boundary if needed
            val sendBuffer = if (bytesThisBatch == sectorsThisBatch * sectorSize && byteOffset == 0 && data.size == bytesThisBatch) {
                data
            } else {
                val padded = ByteArray(sectorsThisBatch * sectorSize)
                System.arraycopy(data, byteOffset, padded, 0, bytesThisBatch)
                padded
            }

            var success = false
            for (retry in 0..2) {
                val cdb = if (currentLba >= 0xFFFFFFFFL) {
                    val buf = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
                    buf.put(ScsiConstants.WRITE_16)
                    buf.put(0.toByte()) // WRPROTECT / DPO / FUA
                    buf.putLong(currentLba) // 64-bit LBA
                    buf.putInt(sectorsThisBatch) // 32-bit Transfer Length
                    buf.put(0.toByte()) // Group number
                    buf.put(0.toByte()) // Control
                    buf.array()
                } else {
                    val buf = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN)
                    buf.put(ScsiConstants.WRITE_10)
                    buf.put(0.toByte()) // Flags
                    buf.putInt(currentLba.toInt()) // LBA 32-bit
                    buf.put(0.toByte()) // Group number
                    buf.putShort(sectorsThisBatch.toShort()) // Transfer length in sectors
                    buf.put(0.toByte()) // Control
                    buf.array()
                }

                val tag = tagGenerator.getAndIncrement()
                val cbw = ScsiCbw(tag, sendBuffer.size, ScsiConstants.DIRECTION_OUT, 0, cdb)

                if (!sendCbw(cbw)) {
                    clearEndpointHalt(epOut)
                    continue
                }

                val transferred = conn.bulkTransfer(epOut, sendBuffer, sendBuffer.size, TIMEOUT_MS)
                if (transferred <= 0) {
                    clearEndpointHalt(epOut)
                    requestSense()
                    continue
                }

                val csw = receiveCsw(tag)
                if (csw?.isPassed == true) {
                    success = true
                    break
                } else {
                    requestSense()
                }
            }

            if (!success) {
                Log.e(TAG, "Failed writing sectors at LBA $currentLba after retries")
                return false
            }

            sectorOffset += sectorsThisBatch
        }

        return true
    }

    /**
     * Reads raw bytes from specified LBA sectors using SCSI READ (10).
     * Chunked into safe buffers with automated BOT retry and sense clearing for high reliability.
     */
    fun readSectors(startLba: Long, sectorCount: Int): ByteArray? {
        val totalBytes = sectorCount * sectorSize
        val epOut = endpointOut ?: return null
        val result = ByteArray(totalBytes)

        val maxSectorsPerBatch = SAFE_CHUNK_BYTES / sectorSize
        var sectorOffset = 0

        while (sectorOffset < sectorCount) {
            val sectorsThisBatch = Math.min(sectorCount - sectorOffset, maxSectorsPerBatch)
            val bytesThisBatch = sectorsThisBatch * sectorSize
            val currentLba = startLba + sectorOffset
            var batchSuccess = false

            for (retry in 0..2) {
                val cdb = if (currentLba >= 0xFFFFFFFFL) {
                    val buf = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
                    buf.put(ScsiConstants.READ_16)
                    buf.put(0.toByte()) // RDPROTECT / DPO / FUA
                    buf.putLong(currentLba) // 64-bit LBA
                    buf.putInt(sectorsThisBatch) // 32-bit Transfer Length
                    buf.put(0.toByte()) // Group number
                    buf.put(0.toByte()) // Control
                    buf.array()
                } else {
                    val buf = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN)
                    buf.put(ScsiConstants.READ_10)
                    buf.put(0.toByte())
                    buf.putInt(currentLba.toInt())
                    buf.put(0.toByte())
                    buf.putShort(sectorsThisBatch.toShort())
                    buf.put(0.toByte())
                    buf.array()
                }

                val tag = tagGenerator.getAndIncrement()
                val cbw = ScsiCbw(tag, bytesThisBatch, ScsiConstants.DIRECTION_IN, 0, cdb)
                if (!sendCbw(cbw)) {
                    clearEndpointHalt(epOut)
                    continue
                }

                val batchBuffer = ByteArray(bytesThisBatch)
                val readBytes = receiveData(batchBuffer)
                val csw = receiveCsw(tag)

                if (csw?.isPassed == true && readBytes == bytesThisBatch) {
                    System.arraycopy(batchBuffer, 0, result, sectorOffset * sectorSize, bytesThisBatch)
                    batchSuccess = true
                    break
                } else {
                    requestSense()
                }
            }

            if (!batchSuccess) {
                Log.e(TAG, "Failed reading sectors at LBA $currentLba after retries")
                return null
            }

            sectorOffset += sectorsThisBatch
        }

        return result
    }

    /**
     * Executes SCSI SYNCHRONIZE CACHE (10) to flush all flash controller caches to non-volatile media.
     */
    fun synchronizeCache(): Boolean {
        val cdb = byteArrayOf(ScsiConstants.SYNCHRONIZE_CACHE_10, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val tag = tagGenerator.getAndIncrement()
        val cbw = ScsiCbw(tag, 0, ScsiConstants.DIRECTION_OUT, 0, cdb)
        if (!sendCbw(cbw)) return false
        val csw = receiveCsw(tag)
        return csw?.isPassed == true
    }

    private fun sendCbw(cbw: ScsiCbw): Boolean {
        val conn = connection ?: return false
        val epOut = endpointOut ?: return false
        val bytes = cbw.toByteArray()
        val sent = conn.bulkTransfer(epOut, bytes, bytes.size, TIMEOUT_MS)
        return sent == bytes.size
    }

    private fun receiveData(buffer: ByteArray): Int {
        val conn = connection ?: return -1
        val epIn = endpointIn ?: return -1
        val read = conn.bulkTransfer(epIn, buffer, buffer.size, TIMEOUT_MS)
        if (read < 0) {
            clearEndpointHalt(epIn)
        }
        return read
    }

    private fun receiveCsw(expectedTag: Int? = null): ScsiCsw? {
        val conn = connection ?: return null
        val epIn = endpointIn ?: return null
        val buffer = ByteArray(ScsiConstants.CSW_SIZE)
        val read = conn.bulkTransfer(epIn, buffer, buffer.size, TIMEOUT_MS)
        if (read < ScsiConstants.CSW_SIZE) {
            clearEndpointHalt(epIn)
            return null
        }
        val csw = ScsiCsw.fromByteArray(buffer) ?: return null
        if (expectedTag != null && csw.tag != expectedTag) {
            Log.w(TAG, "CSW tag mismatch: expected $expectedTag, got ${csw.tag}")
            return null
        }
        return csw
    }

    override fun close() {
        try {
            synchronizeCache()
            usbInterface?.let { connection?.releaseInterface(it) }
            connection?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing UsbMassStorageDriver: ${e.message}", e)
        } finally {
            connection = null
            usbInterface = null
            endpointIn = null
            endpointOut = null
        }
    }
}
