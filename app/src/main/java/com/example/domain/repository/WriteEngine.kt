package com.example.domain.repository

import com.example.domain.models.WriteConfig
import com.example.domain.models.WriteProgress
import kotlinx.coroutines.flow.Flow

interface WriteEngine {
    fun startWriting(config: WriteConfig): Flow<WriteProgress>
    fun cancelWriting()
}
