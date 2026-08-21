package com.example.usb.filesystem

import java.nio.ByteBuffer
import java.nio.ByteOrder

object Fat12FloppyParser {

    /**
     * Parses a raw FAT12 disk/floppy image and extracts all files found in the root directory.
     */
    fun extractAllFiles(imageBytes: ByteArray): Map<String, ByteArray> {
        if (imageBytes.size < 512) return emptyMap()

        val buf = ByteBuffer.wrap(imageBytes).order(ByteOrder.LITTLE_ENDIAN)
        val bytesPerSector = (buf.getShort(11).toInt() and 0xFFFF).coerceAtLeast(512)
        val sectorsPerCluster = (imageBytes[13].toInt() and 0xFF).coerceAtLeast(1)
        val reservedSectors = (buf.getShort(14).toInt() and 0xFFFF).coerceAtLeast(1)
        val numFats = (imageBytes[16].toInt() and 0xFF).coerceAtLeast(1)
        val rootEntries = (buf.getShort(17).toInt() and 0xFFFF).coerceAtLeast(1)
        val sectorsPerFat = (buf.getShort(22).toInt() and 0xFFFF).coerceAtLeast(1)

        val fatStartOffset = reservedSectors * bytesPerSector
        val fatSizeBytes = sectorsPerFat * bytesPerSector
        if (fatStartOffset + fatSizeBytes > imageBytes.size) return emptyMap()

        val fatBytes = ByteArray(fatSizeBytes)
        System.arraycopy(imageBytes, fatStartOffset, fatBytes, 0, fatSizeBytes)

        val rootDirStartOffset = fatStartOffset + (numFats * fatSizeBytes)
        val rootDirSizeBytes = rootEntries * 32
        val dataStartOffset = rootDirStartOffset + rootDirSizeBytes
        val clusterBytes = sectorsPerCluster * bytesPerSector

        val result = mutableMapOf<String, ByteArray>()

        for (entryIdx in 0 until rootEntries) {
            val entryOffset = rootDirStartOffset + (entryIdx * 32)
            if (entryOffset + 32 > imageBytes.size) break

            val firstByte = imageBytes[entryOffset].toInt() and 0xFF
            if (firstByte == 0x00) break // No more entries
            if (firstByte == 0xE5) continue // Deleted entry

            val attr = imageBytes[entryOffset + 11].toInt() and 0xFF
            // Explicitly skip Long File Name (LFN) directory entries
            if ((attr and 0x3F) == 0x0F) continue
            if ((attr and 0x08) != 0) continue // Volume label entry
            if ((attr and 0x10) != 0) continue // Subdirectory entry

            val namePart = String(imageBytes, entryOffset, 8, Charsets.US_ASCII).trim()
            val extPart = String(imageBytes, entryOffset + 8, 3, Charsets.US_ASCII).trim()
            val fullFileName = if (extPart.isNotEmpty()) "$namePart.$extPart" else namePart

            val startCluster = (imageBytes[entryOffset + 26].toInt() and 0xFF) or
                    ((imageBytes[entryOffset + 27].toInt() and 0xFF) shl 8)
            val fileSize = (imageBytes[entryOffset + 28].toInt() and 0xFF) or
                    ((imageBytes[entryOffset + 29].toInt() and 0xFF) shl 8) or
                    ((imageBytes[entryOffset + 30].toInt() and 0xFF) shl 16) or
                    ((imageBytes[entryOffset + 31].toInt() and 0xFF) shl 24)

            if (fileSize <= 0 || startCluster < 2) continue

            // Read cluster chain
            val fileData = ByteArray(fileSize)
            var bytesCopied = 0
            var currentCluster = startCluster
            val visitedClusters = mutableSetOf<Int>()

            while (bytesCopied < fileSize && currentCluster in 2..0x0FF6) {
                if (!visitedClusters.add(currentCluster)) break // Prevent circular reference

                val clusterOffset = dataStartOffset + ((currentCluster - 2) * clusterBytes)
                if (clusterOffset >= imageBytes.size) break

                val toCopy = Math.min(fileSize - bytesCopied, clusterBytes)
                val availableInImage = Math.min(toCopy, imageBytes.size - clusterOffset)
                if (availableInImage > 0) {
                    System.arraycopy(imageBytes, clusterOffset, fileData, bytesCopied, availableInImage)
                    bytesCopied += availableInImage
                }

                currentCluster = getNextFat12Cluster(fatBytes, currentCluster)
            }

            result[fullFileName.uppercase()] = fileData
        }

        return result
    }

    private fun getNextFat12Cluster(fatBytes: ByteArray, cluster: Int): Int {
        val fatOffset = cluster + (cluster / 2)
        if (fatOffset + 1 >= fatBytes.size) return 0x0FFF

        val b0 = fatBytes[fatOffset].toInt() and 0xFF
        val b1 = fatBytes[fatOffset + 1].toInt() and 0xFF
        val raw = b0 or (b1 shl 8)

        return if (cluster % 2 == 0) {
            raw and 0x0FFF
        } else {
            raw ushr 4
        }
    }
}
