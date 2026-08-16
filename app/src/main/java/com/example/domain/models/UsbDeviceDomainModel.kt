package com.example.domain.models

enum class DeviceType(val label: String, val badge: String) {
    USB_FLASH("USB Flash Drive", "USB"),
    SD_CARD("SD / Flash Memory Card", "SD"),
    VIRTUAL_DISK("Virtual Drive (VHD/VHDX)", "VHD"),
    USB_HDD("USB External HDD / SSD", "HDD")
}

data class UsbDeviceDomainModel(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val productName: String,
    val manufacturerName: String,
    val capacityBytes: Long,
    val isGranted: Boolean,
    val isMounted: Boolean = true,
    val fileSystemType: String = "FAT32",
    val isSimulated: Boolean = false,
    val deviceType: DeviceType = DeviceType.USB_FLASH,
    val serialNumber: String = "SN-${(100000..999999).random()}",
    val speedUsbVersion: String = "USB 3.0 (OTG)",
    val isFakeDriveSimulated: Boolean = false
) {
    val capacityGb: Double
        get() = capacityBytes / (1024.0 * 1024.0 * 1024.0)

    val formattedCapacity: String
        get() = String.format("%.1f GB", capacityGb)

    val displayName: String
        get() = "$productName ($formattedCapacity)"
}

