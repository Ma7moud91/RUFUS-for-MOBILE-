package com.example.domain.models

enum class BootSelectionType(val label: String, val description: String) {
    ISO_IMAGE("Disk or ISO image", "Select a bootable ISO, IMG, VHD, or compressed disk image"),
    FREEDOS("FreeDOS", "Create a DOS bootable USB using embedded FreeDOS 1.3"),
    MSDOS("MS-DOS", "Create a legacy MS-DOS bootable disk using standard system files"),
    UEFI_SHELL("UEFI Shell", "Deploy standalone UEFI Shell v2.2 with Secure Boot compatibility"),
    NON_BOOTABLE("Non-bootable", "Format storage drive without bootloader partitions"),
    WINDOWS_TO_GO("Windows To Go", "Deploy a fully portable, running Windows OS directly on USB")
}

data class WindowsUserExperienceConfig(
    val bypassTpmSecureBootRam: Boolean = true,
    val bypassOnlineAccount: Boolean = true,
    val createLocalAccount: Boolean = true,
    val localUsername: String = "User",
    val setRegionalOptions: Boolean = true,
    val disableDataCollection: Boolean = true,
    val disableBitLocker: Boolean = true
)

data class LinuxPersistenceConfig(
    val enabled: Boolean = false,
    val sizeGb: Float = 0f // 0 to max storage in GB
)

data class BadBlocksConfig(
    val enabled: Boolean = false,
    val passes: Int = 1,
    val detectFakeFlashDrives: Boolean = true
)

data class ImageDumpConfig(
    val targetDeviceName: String,
    val format: ImageFormat = ImageFormat.VHD,
    val fileName: String = "usb_backup_image",
    val compress: Boolean = true
)

enum class ImageFormat(val extension: String, val label: String) {
    VHD(".vhd", "Virtual Hard Disk (VHD)"),
    VHDX(".vhdx", "Hyper-V Virtual Disk (VHDX)"),
    DD_RAW(".img", "Raw Disk Image (DD / IMG)"),
    FFU(".ffu", "Full Flash Update (FFU)")
}

data class WriteConfig(
    val usbDeviceName: String,
    val rawDevicePath: String = "",
    val isSimulated: Boolean = false,
    val bootSelectionType: BootSelectionType = BootSelectionType.ISO_IMAGE,
    val imageUri: String = "",
    val volumeLabel: String = "BOOTABLE_USB",
    val partitionScheme: PartitionScheme = PartitionScheme.GPT,
    val targetSystem: TargetSystem = TargetSystem.UEFI_NON_CSM,
    val fileSystem: FileSystem = FileSystem.FAT32,
    val clusterSize: Int = 16384,
    val quickFormat: Boolean = true,
    val badBlocks: BadBlocksConfig = BadBlocksConfig(),
    val windowsUserExperience: WindowsUserExperienceConfig = WindowsUserExperienceConfig(),
    val linuxPersistence: LinuxPersistenceConfig = LinuxPersistenceConfig(),
    val isWindowsImage: Boolean = false,
    val isLinuxImage: Boolean = false,
    val sourceSha256: String = "",
    val verifySha256AfterBurn: Boolean = true,
    val imageSizeBytes: Long = 0L
)

enum class PartitionScheme(val label: String) {
    GPT("GPT"),
    MBR("MBR")
}

enum class TargetSystem(val label: String) {
    UEFI_NON_CSM("UEFI (non CSM)"),
    BIOS_OR_UEFI("BIOS (or UEFI-CSM)"),
    UEFI("UEFI (UEFI:NTFS Bootable)")
}

enum class FileSystem(val label: String, val isStandard: Boolean = true) {
    FAT("FAT (FAT16)"),
    FAT32("FAT32 (Default)"),
    NTFS("NTFS"),
    UDF("UDF"),
    EXFAT("exFAT"),
    REFS("ReFS (Resilient FS)"),
    EXT2("ext2 (Linux)"),
    EXT3("ext3 (Linux)"),
    EXT4("ext4 (Linux)")
}
