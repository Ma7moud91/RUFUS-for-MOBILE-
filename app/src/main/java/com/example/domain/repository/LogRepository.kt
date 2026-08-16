package com.example.domain.repository

import com.example.domain.models.LogEntry
import com.example.domain.models.LogLevel
import kotlinx.coroutines.flow.StateFlow

interface LogRepository {
    val logs: StateFlow<List<LogEntry>>
    fun log(message: String, level: LogLevel = LogLevel.INFO, tag: String = "RUFUS")
    fun clearLogs()
    fun exportLogs(): String
}
