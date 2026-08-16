package com.example.di

import android.content.Context
import com.example.data.engine.RealRufusWriteEngineImpl
import com.example.data.repository.LogRepositoryImpl
import com.example.data.repository.UsbRepositoryImpl
import com.example.domain.repository.LogRepository
import com.example.domain.repository.UsbRepository
import com.example.domain.repository.WriteEngine
import com.example.util.RufusNotificationManager

interface AppContainer {
    val logRepository: LogRepository
    val usbRepository: UsbRepository
    val writeEngine: WriteEngine
    val notificationManager: RufusNotificationManager
    val feedbackManager: com.example.util.RufusFeedbackManager
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val logRepository: LogRepository by lazy {
        LogRepositoryImpl()
    }

    override val usbRepository: UsbRepository by lazy {
        UsbRepositoryImpl(context, logRepository)
    }
    
    override val writeEngine: WriteEngine by lazy {
        RealRufusWriteEngineImpl(context, logRepository, usbRepository)
    }

    override val notificationManager: RufusNotificationManager by lazy {
        RufusNotificationManager(context)
    }

    override val feedbackManager: com.example.util.RufusFeedbackManager by lazy {
        com.example.util.RufusFeedbackManager(context)
    }
}
