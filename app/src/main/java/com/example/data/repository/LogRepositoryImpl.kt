package com.example.data.repository

import com.example.domain.models.LogEntry
import com.example.domain.models.LogLevel
import com.example.domain.repository.LogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogRepositoryImpl : LogRepository {
    private val _logs = MutableStateFlow<List<LogEntry>>(
        listOf(
            LogEntry(
                timestamp = System.currentTimeMillis() - 3000,
                level = LogLevel.INFO,
                tag = "RUFUS",
                message = "Rufus v4.5 (Android Edition) initialized."
            ),
            LogEntry(
                timestamp = System.currentTimeMillis() - 2000,
                level = LogLevel.INFO,
                tag = "USB",
                message = "USB Host subsystem ready. Listening for OTG storage events."
            ),
            LogEntry(
                timestamp = System.currentTimeMillis() - 1000,
                level = LogLevel.SUCCESS,
                tag = "CORE",
                message = "Storage Access Framework (SAF) ready for ISO/IMG selection."
            )
        )
    )
    override val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    override fun log(message: String, level: LogLevel, tag: String) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message
        )
        _logs.value = _logs.value + entry
    }

    override fun clearLogs() {
        _logs.value = listOf(
            LogEntry(
                timestamp = System.currentTimeMillis(),
                level = LogLevel.INFO,
                tag = "RUFUS",
                message = "Logs cleared by user."
            )
        )
    }

    override fun exportLogs(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        return _logs.value.joinToString("\n") { entry ->
            "[${sdf.format(Date(entry.timestamp))}] [${entry.tag}] [${entry.level}]: ${entry.message}"
        }
    }
}
