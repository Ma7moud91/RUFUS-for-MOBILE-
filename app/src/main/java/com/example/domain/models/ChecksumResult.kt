package com.example.domain.models

data class ChecksumResult(
    val fileName: String,
    val fileSizeFormatted: String,
    val md5: String = "",
    val sha1: String = "",
    val sha256: String = "",
    val sha512: String = "",
    val isCalculating: Boolean = false,
    val progressPercent: Int = 0
)

data class UefiValidationCheck(
    val title: String,
    val detail: String,
    val passed: Boolean,
    val isCritical: Boolean = true
)

data class UefiValidationResult(
    val mediaLabel: String,
    val partitionScheme: String,
    val fileSystem: String,
    val isUefiBootable: Boolean,
    val secureBootCompliant: Boolean,
    val checks: List<UefiValidationCheck>,
    val timestamp: Long = System.currentTimeMillis()
)
