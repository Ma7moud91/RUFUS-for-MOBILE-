package com.example.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RufusUsbManager(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
    private val _connectedDevices = MutableStateFlow<List<UsbDevice>>(emptyList())
    val connectedDevices: StateFlow<List<UsbDevice>> = _connectedDevices.asStateFlow()

    companion object {
        const val ACTION_USB_PERMISSION = "com.example.usb.USB_PERMISSION"
        private const val TAG = "RufusUsbManager"
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                when (intent.action) {
                    ACTION_USB_PERMISSION -> {
                        synchronized(this) {
                            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                            }
                            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                            Log.d(TAG, "USB Permission result: granted=$granted, device=${device?.deviceName}")
                            refreshDevices()
                        }
                    }
                    UsbManager.ACTION_USB_DEVICE_ATTACHED,
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        refreshDevices()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in usbReceiver onReceive: ${e.message}", e)
            }
        }
    }

    init {
        try {
            val filter = IntentFilter(ACTION_USB_PERMISSION).apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(usbReceiver, filter)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register usbReceiver: ${e.message}", e)
        }
        refreshDevices()
    }

    fun refreshDevices() {
        try {
            val manager = usbManager ?: return
            val deviceList = manager.deviceList ?: emptyMap()
            val devices = deviceList.values.filter { isPotentialStorageDevice(it) }
            _connectedDevices.value = devices.toList()
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing USB devices: ${e.message}", e)
            _connectedDevices.value = emptyList()
        }
    }

    private fun isPotentialStorageDevice(device: UsbDevice): Boolean {
        try {
            // Direct device class check (8 = USB Mass Storage)
            if (device.deviceClass == 8) {
                return true
            }

            // Inspect all interfaces for USB Mass Storage class (8)
            for (i in 0 until device.interfaceCount) {
                try {
                    val usbInterface = device.getInterface(i)
                    if (usbInterface.interfaceClass == 8) {
                        return true
                    }
                } catch (e: Exception) {
                    // Ignore interface access errors
                }
            }

            // Non-storage composite devices (e.g. keyboards/mice/audio with deviceClass == 0) return false
            return false
        } catch (e: Exception) {
            return false
        }
    }

    fun requestPermission(device: UsbDevice) {
        val manager = usbManager ?: return
        try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION).apply { setPackage(context.packageName) },
                flags
            )
            manager.requestPermission(device, permissionIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting USB permission: ${e.message}", e)
        }
    }

    fun hasPermission(device: UsbDevice): Boolean {
        return try {
            usbManager?.hasPermission(device) == true
        } catch (e: Exception) {
            false
        }
    }

    fun cleanup() {
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
    }
}
