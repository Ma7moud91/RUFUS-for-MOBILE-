package com.example.domain.models

data class ImageFile(
    val uriString: String,
    val fileName: String,
    val sizeBytes: Long,
    val hashSha256: String? = null,
    val hashSha512: String? = null,
    val hashSha1: String? = null,
    val hashMd5: String? = null,
    val osDetection: String? = null,
    val architecture: String? = "x86_64",
    val isWindows: Boolean = false,
    val isLinux: Boolean = false,
    val isDos: Boolean = false,
    val isUefiShell: Boolean = false,
    val isCompressed: Boolean = false,
    val recommendedPartitionScheme: PartitionScheme = PartitionScheme.GPT,
    val recommendedFileSystem: FileSystem = FileSystem.FAT32,
    val isPreset: Boolean = false
) {
    val sizeFormatted: String
        get() {
            val gb = sizeBytes / (1024.0 * 1024.0 * 1024.0)
            if (gb >= 1.0) {
                return String.format("%.2f GB", gb)
            }
            val mb = sizeBytes / (1024.0 * 1024.0)
            if (mb >= 1.0) {
                return String.format("%.1f MB", mb)
            }
            val kb = sizeBytes / 1024.0
            return String.format("%.1f KB", kb)
        }

    companion object {
        val PRESETS = listOf(
            ImageFile(
                uriString = "preset://Win11_23H2_English_x64v2.iso",
                fileName = "Win11_23H2_English_x64v2.iso",
                sizeBytes = 6_871_947_673L, // ~6.4 GB
                hashSha256 = "36ae2572b89f81d119e74d1a084c818fae473e6daef8ef23758b29ff07b4618e",
                hashSha1 = "b24d77cb306a4b4bb2d24268e3efad832dfb1192",
                hashMd5 = "7d4d423cbcfd46ea3be4fe56aa447d95",
                hashSha512 = "51a44e6ef1b569da8915494d4d3d810a9ef2236ca91f1ad689725fba09689886a117b4ca21855a9b71a2a118837a7fe61e0bb50d53ba514867ae68e8e77a16df",
                osDetection = "Windows 11 23H2 / 24H2 (Retail ISO)",
                architecture = "x64 (UEFI Only)",
                isWindows = true,
                recommendedPartitionScheme = PartitionScheme.GPT,
                recommendedFileSystem = FileSystem.NTFS,
                isPreset = true
            ),
            ImageFile(
                uriString = "preset://Win10_22H2_English_x64.iso",
                fileName = "Win10_22H2_English_x64.iso",
                sizeBytes = 6_120_349_696L, // ~5.7 GB
                hashSha256 = "a6f470ca6d331eb353b815c961e0d100882704b7537161700f56f43ff58b0d3e",
                hashSha1 = "a438f7125345d378dfb9fcf822cbdd18f888f4e9",
                hashMd5 = "56eb80a424267b2d56e2eb275f63d09e",
                hashSha512 = "66f7caeb14522964e526c8b939f1c7d23d8c119e12da6d9255653b6f001948ae7015eb6f5e7146cefa7c5ea2d87e07661b6c7a6e11894d0fbcd87e14bc5409a2",
                osDetection = "Windows 10 22H2 (Retail ISO)",
                architecture = "x64 (BIOS or UEFI)",
                isWindows = true,
                recommendedPartitionScheme = PartitionScheme.GPT,
                recommendedFileSystem = FileSystem.NTFS,
                isPreset = true
            ),
            ImageFile(
                uriString = "preset://Win8.1_English_x64.iso",
                fileName = "Win8.1_English_x64.iso",
                sizeBytes = 4_357_627_904L, // ~4.0 GB
                hashSha256 = "c0868f763ee3f668705b0c95bb0e34c9c2ff14ffbbdd2cc46123497d398f6d33",
                hashSha1 = "1e812d8a23075b89a80e6e7368a44b1c7d67f139",
                hashMd5 = "34f195dcfdf82fa56efb925bca04bb77",
                hashSha512 = "68c92a912bb09c84918e697bc8a1dfec854b7324564c79899f8d55a6d36e2fba4f74d0a927d6d5386db4b6e5117ee2764b8a24aa7be2904b79148d42d38561d5",
                osDetection = "Windows 8.1 with Update (Retail)",
                architecture = "x64",
                isWindows = true,
                recommendedPartitionScheme = PartitionScheme.MBR,
                recommendedFileSystem = FileSystem.NTFS,
                isPreset = true
            ),
            ImageFile(
                uriString = "preset://ubuntu-24.04-desktop-amd64.iso",
                fileName = "ubuntu-24.04-desktop-amd64.iso",
                sizeBytes = 6_012_954_112L, // ~5.6 GB
                hashSha256 = "81fae9cc21e2b1b3a9a5230463a80f7380f284e1a4bf008f514d506d3b707258",
                hashSha1 = "d376510d54020a5628b07e597dbd4be7fa3065b2",
                hashMd5 = "e102f831341c2c31766a506140ad8bf1",
                hashSha512 = "446ad031c26176395b060f643e26bb2a563f17d3d1912a2080a221f7c3dc0ff5dfbd73cb439bb101d2d3a77197b102ce08fbf793c52e46b3281aa03a62ea65a1",
                osDetection = "Ubuntu 24.04 LTS (Noble Numbat)",
                architecture = "x86_64",
                isLinux = true,
                recommendedPartitionScheme = PartitionScheme.GPT,
                recommendedFileSystem = FileSystem.FAT32,
                isPreset = true
            ),
            ImageFile(
                uriString = "preset://debian-12.5.0-amd64-netinst.iso",
                fileName = "debian-12.5.0-amd64-netinst.iso",
                sizeBytes = 658_505_728L, // ~628 MB
                hashSha256 = "1f33f11bc052dfaeaf960e6f6630f9a26569103f62294e77353f40f09a80e149",
                hashSha1 = "f2a893699c27ee982755e100f91e9f45ba6b8969",
                hashMd5 = "2668b0c441b80c57176189ef999bb3c8",
                hashSha512 = "087c5a0bd0b1df6fef88ffbeab3737b8d4c94f573efb7c7b8979b90875e53ee28e6c7eb2212959828e83348d423fcb5be7376c66cf1c572cf93b054238e8ec43",
                osDetection = "Debian GNU/Linux 12 (Bookworm)",
                architecture = "x86_64 / i386",
                isLinux = true,
                recommendedPartitionScheme = PartitionScheme.MBR,
                recommendedFileSystem = FileSystem.FAT32,
                isPreset = true
            ),
            ImageFile(
                uriString = "preset://FD13-FullUSB.img",
                fileName = "FD13-FullUSB.img",
                sizeBytes = 536_870_912L, // 512 MB
                hashSha256 = "e2c366ff173d3a04e578cda7bc9242d544f07a78a7efda43e36e3c0ea410aa6a",
                hashSha1 = "772f4f2c0023a45610bcdeea42890538a7c2937e",
                hashMd5 = "12cb59f0f9b69b61bcbe920956ba75d3",
                hashSha512 = "3998b3c66f7f2b1d3d63ba4ca32d67c4856f4d25167f139589d8164005ae65ee6c191a27e7f7b2c0f2ee8d0cba6783d964f4347713dcf68c07c4bc0c6ba8a911",
                osDetection = "FreeDOS 1.3 (Full USB)",
                architecture = "x86 (16/32-bit)",
                isDos = true,
                recommendedPartitionScheme = PartitionScheme.MBR,
                recommendedFileSystem = FileSystem.FAT,
                isPreset = true
            ),
            ImageFile(
                uriString = "preset://uefi-shell-2.2.iso",
                fileName = "uefi-shell-v2.2-signed.iso",
                sizeBytes = 4_194_304L, // 4 MB
                hashSha256 = "56a93b45a4980bbd978a7d11df2e5bfae792c0d8329b3cae3135c9118e95ea64",
                hashSha1 = "88bdfa279090b8417c88b029ff85a5e31707ef41",
                hashMd5 = "b845fe92a1017efad8d1bb4546419794",
                hashSha512 = "460d3d3a1fca94d4d1ba873673f40f28e679a9523f2fca2ee8f1e56b4618e4768393e82d02c91834162e24749f7b11d94fca240fbe4b46c0507a216440e2cf7d",
                osDetection = "UEFI Shell v2.2 (EDK II TianoCore)",
                architecture = "x64 / AArch64 (UEFI)",
                isUefiShell = true,
                recommendedPartitionScheme = PartitionScheme.GPT,
                recommendedFileSystem = FileSystem.FAT32,
                isPreset = true
            ),
            ImageFile(
                uriString = "preset://archlinux-2024.08.01-x86_64.iso",
                fileName = "archlinux-2024.08.01-x86_64.iso",
                sizeBytes = 1_181_116_416L, // ~1.1 GB
                hashSha256 = "f80998a44b82d49ef554a9d7dc32a818c392473ff7c3ecdfd90bb002fbe5bb83",
                hashSha1 = "0946b528b9c4fae0a2da1be1350a80e1819d44cf",
                hashMd5 = "3e66014529f7cf7c08a46b3fcf5868ab",
                hashSha512 = "399434857b6f38efcd814120f2dbf88c691349f7c78cb3a277704df39c1b72e022f6745ea98442e652a233b2b8004f2d7a2fa2b8da46e7f86f784e1b858e2d42",
                osDetection = "Arch Linux Rolling (2024.08)",
                architecture = "x86_64",
                isLinux = true,
                recommendedPartitionScheme = PartitionScheme.GPT,
                recommendedFileSystem = FileSystem.FAT32,
                isPreset = true
            )
        )
    }
}
