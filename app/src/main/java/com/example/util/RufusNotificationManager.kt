package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.domain.models.WriteProgress

class RufusNotificationManager(private val context: Context) {

    private val notificationManager = NotificationManagerCompat.from(context)

    companion object {
        const val CHANNEL_ID_PROGRESS = "rufus_flash_channel"
        const val CHANNEL_NAME_PROGRESS = "Rufus Flash Operations"
        const val NOTIFICATION_ID_PROGRESS = 1001
        const val NOTIFICATION_ID_COMPLETE = 1002
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_PROGRESS,
                CHANNEL_NAME_PROGRESS,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live USB creation and ISO flashing progress"
                setShowBadge(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showWriteProgressNotification(progress: WriteProgress, deviceName: String, label: String) {
        if (!hasNotificationPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        when (progress) {
            is WriteProgress.Writing -> {
                val notification = NotificationCompat.Builder(context, CHANNEL_ID_PROGRESS)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle("Flashing to $deviceName: ${progress.percentage}%")
                    .setContentText("${String.format("%.1f", progress.speedMbPerSec)} MB/s • ${progress.remainingTimeSec}s remaining")
                    .setSubText(progress.currentFile)
                    .setProgress(100, progress.percentage, false)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setContentIntent(pendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()

                try {
                    notificationManager.notify(NOTIFICATION_ID_PROGRESS, notification)
                } catch (e: SecurityException) {
                    // Ignored if permission was revoked
                }
            }
            is WriteProgress.Formatting -> {
                val notification = NotificationCompat.Builder(context, CHANNEL_ID_PROGRESS)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle("Formatting $deviceName")
                    .setContentText(progress.message)
                    .setProgress(100, progress.percentage, false)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setContentIntent(pendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()

                try {
                    notificationManager.notify(NOTIFICATION_ID_PROGRESS, notification)
                } catch (e: SecurityException) {
                    // Ignored
                }
            }
            is WriteProgress.Partitioning -> {
                val notification = NotificationCompat.Builder(context, CHANNEL_ID_PROGRESS)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle("Partitioning $deviceName")
                    .setContentText(progress.message)
                    .setProgress(100, progress.percentage, false)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setContentIntent(pendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()

                try {
                    notificationManager.notify(NOTIFICATION_ID_PROGRESS, notification)
                } catch (e: SecurityException) {
                    // Ignored
                }
            }
            is WriteProgress.Completed -> {
                dismissProgressNotification()
                val notification = NotificationCompat.Builder(context, CHANNEL_ID_PROGRESS)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("Bootable Media Ready! ✅")
                    .setContentText("Created '$label' on $deviceName in ${progress.totalTimeSec}s (${String.format("%.1f", progress.averageSpeedMbPerSec)} MB/s)")
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()

                try {
                    notificationManager.notify(NOTIFICATION_ID_COMPLETE, notification)
                } catch (e: SecurityException) {
                    // Ignored
                }
            }
            is WriteProgress.Error -> {
                dismissProgressNotification()
                val notification = NotificationCompat.Builder(context, CHANNEL_ID_PROGRESS)
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setContentTitle("Flashing Error ⚠️")
                    .setContentText(progress.message)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()

                try {
                    notificationManager.notify(NOTIFICATION_ID_COMPLETE, notification)
                } catch (e: SecurityException) {
                    // Ignored
                }
            }
            is WriteProgress.Idle -> {
                dismissProgressNotification()
            }
            else -> {}
        }
    }

    fun dismissProgressNotification() {
        try {
            notificationManager.cancel(NOTIFICATION_ID_PROGRESS)
        } catch (e: Exception) {
            // Ignored
        }
    }
}
