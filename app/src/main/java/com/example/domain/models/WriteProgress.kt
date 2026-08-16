package com.example.domain.models

sealed class WriteProgress {
    object Idle : WriteProgress()
    
    data class Analyzing(
        val message: String = "Analyzing image structure..."
    ) : WriteProgress()
    
    data class Partitioning(
        val percentage: Int,
        val message: String = "Zeroing MBR & creating partition table..."
    ) : WriteProgress()
    
    data class Formatting(
        val percentage: Int,
        val message: String = "Formatting filesystem..."
    ) : WriteProgress()
    
    data class Writing(
        val percentage: Int,
        val currentFile: String,
        val speedMbPerSec: Double,
        val remainingTimeSec: Long,
        val bytesWritten: Long = 0L,
        val totalBytes: Long = 0L
    ) : WriteProgress()
    
    data class InstallingBootloader(
        val percentage: Int,
        val bootloaderType: String = "UEFI:NTFS / GRUB2"
    ) : WriteProgress()
    
    data class Verifying(
        val percentage: Int,
        val message: String = "Verifying data integrity..."
    ) : WriteProgress()
    
    data class Completed(
        val totalTimeSec: Long,
        val averageSpeedMbPerSec: Double
    ) : WriteProgress()
    
    data class Error(
        val message: String,
        val errorCode: Int = -1
    ) : WriteProgress()
}
