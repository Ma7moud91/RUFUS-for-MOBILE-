package com.example.domain.repository

import com.example.domain.models.UsbDeviceDomainModel
import kotlinx.coroutines.flow.StateFlow

interface UsbRepository {
    val connectedDevices: StateFlow<List<UsbDeviceDomainModel>>
    fun refreshDevices()
    fun requestPermission(deviceName: String)
    fun setSimulationMode(enabled: Boolean)
    fun addCustomSimulatedDevice(device: UsbDeviceDomainModel)
    fun benchmarkDevice(deviceName: String): FlowBenchmarkResult
}

data class FlowBenchmarkResult(
    val readSpeedMbPerSec: Double,
    val writeSpeedMbPerSec: Double,
    val accessTimeMs: Double,
    val status: String
)
