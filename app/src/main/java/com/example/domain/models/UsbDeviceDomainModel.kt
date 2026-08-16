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
    val speedUsbVersion: String = "USB 3.1 Gen 1 (5 Gbps)",
    val isFakeDriveSimulated: Boolean = false
) {
    val capacityGb: Double
        get() = capacityBytes / (1024.0 * 1024.0 * 1024.0)

    val formattedCapacity: String
        get() = String.format("%.1f GB", capacityGb)

    val displayName: String
        get() = "$productName ($formattedCapacity)"

    companion object {
        val SIMULATED_DEVICES = listOf(
            UsbDeviceDomainModel(
                deviceName = "/dev/bus/usb/001/004",
                vendorId = 0x04E8, // Samsung
                productId = 0x7001,
                productName = "Samsung Bar Plus USB 3.1",
                manufacturerName = "Samsung Electronics",
                capacityBytes = 64L * 1024 * 1024 * 1024, // 64 GB
                isGranted = true,
                isMounted = true,
                fileSystemType = "exFAT",
                isSimulated = true,
                deviceType = DeviceType.USB_FLASH,
                serialNumber = "SAM-BAR-64GB-9921",
                speedUsbVersion = "USB 3.1 Gen 1 (5 Gbps)"
            ),
            UsbDeviceDomainModel(
                deviceName = "/dev/bus/usb/001/005",
                vendorId = 0x0781, // SanDisk
                productId = 0x5581,
                productName = "SanDisk Ultra Dual Drive Luxe",
                manufacturerName = "SanDisk Corp.",
                capacityBytes = 32L * 1024 * 1024 * 1024, // 32 GB
                isGranted = true,
                isMounted = true,
                fileSystemType = "FAT32",
                isSimulated = true,
                deviceType = DeviceType.USB_FLASH,
                serialNumber = "SD-LUXE-32G-7714",
                speedUsbVersion = "USB 3.0 (5 Gbps)"
            ),
            UsbDeviceDomainModel(
                deviceName = "/dev/block/mmcblk0",
                vendorId = 0x0001,
                productId = 0x5344,
                productName = "SanDisk Extreme PRO SDXC UHS-I",
                manufacturerName = "SanDisk / MMC",
                capacityBytes = 128L * 1024 * 1024 * 1024, // 128 GB
                isGranted = true,
                isMounted = true,
                fileSystemType = "exFAT",
                isSimulated = true,
                deviceType = DeviceType.SD_CARD,
                serialNumber = "SD-EXTPRO-128G-3391",
                speedUsbVersion = "UHS-I (170 MB/s)"
            ),
            UsbDeviceDomainModel(
                deviceName = "/dev/loop0",
                vendorId = 0x0000,
                productId = 0x0000,
                productName = "Virtual Disk Workspace.vhdx",
                manufacturerName = "Microsoft VHDX Loopback",
                capacityBytes = 64L * 1024 * 1024 * 1024, // 64 GB
                isGranted = true,
                isMounted = true,
                fileSystemType = "NTFS",
                isSimulated = true,
                deviceType = DeviceType.VIRTUAL_DISK,
                serialNumber = "VHDX-VIRTUAL-LUN-0",
                speedUsbVersion = "Direct NVMe Loopback"
            ),
            UsbDeviceDomainModel(
                deviceName = "/dev/bus/usb/002/002",
                vendorId = 0x0951, // Kingston
                productId = 0x1666,
                productName = "Kingston DataTraveler Exodia",
                manufacturerName = "Kingston Technology",
                capacityBytes = 16L * 1024 * 1024 * 1024, // 16 GB
                isGranted = true,
                isMounted = true,
                fileSystemType = "FAT32",
                isSimulated = true,
                deviceType = DeviceType.USB_FLASH,
                serialNumber = "KNG-DTX-16G-4402",
                speedUsbVersion = "USB 3.2 Gen 1"
            )
        )
    }
}
