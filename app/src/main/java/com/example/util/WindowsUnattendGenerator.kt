package com.example.util

import com.example.domain.models.WindowsUserExperienceConfig

object WindowsUnattendGenerator {

    /**
     * Generates a fully compliant Microsoft Windows AutoUnattend.xml file
     * with TPM 2.0 / Secure Boot bypass registry keys, BypassNRO, and local account setup.
     */
    fun generateAutoUnattendXml(config: WindowsUserExperienceConfig): String {
        val bypassCommands = StringBuilder()
        var orderIndex = 1

        if (config.bypassTpmSecureBootRam) {
            bypassCommands.append("""
        <SynchronousCommand wcm:action="add">
          <Order>$orderIndex</Order>
          <CommandLine>reg add HKLM\SYSTEM\Setup\LabConfig /v BypassTPMCheck /t REG_DWORD /d 1 /f</CommandLine>
        </SynchronousCommand>
        <SynchronousCommand wcm:action="add">
          <Order>${orderIndex + 1}</Order>
          <CommandLine>reg add HKLM\SYSTEM\Setup\LabConfig /v BypassSecureBootCheck /t REG_DWORD /d 1 /f</CommandLine>
        </SynchronousCommand>
        <SynchronousCommand wcm:action="add">
          <Order>${orderIndex + 2}</Order>
          <CommandLine>reg add HKLM\SYSTEM\Setup\LabConfig /v BypassRAMCheck /t REG_DWORD /d 1 /f</CommandLine>
        </SynchronousCommand>
        <SynchronousCommand wcm:action="add">
          <Order>${orderIndex + 3}</Order>
          <CommandLine>reg add HKLM\SYSTEM\Setup\LabConfig /v BypassStorageCheck /t REG_DWORD /d 1 /f</CommandLine>
        </SynchronousCommand>
        <SynchronousCommand wcm:action="add">
          <Order>${orderIndex + 4}</Order>
          <CommandLine>reg add HKLM\SYSTEM\Setup\LabConfig /v BypassCPUCheck /t REG_DWORD /d 1 /f</CommandLine>
        </SynchronousCommand>
            """.trimIndent())
            orderIndex += 5
        }

        if (config.bypassOnlineAccount) {
            bypassCommands.append("""
        <SynchronousCommand wcm:action="add">
          <Order>$orderIndex</Order>
          <CommandLine>reg add HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\OOBE /v BypassNRO /t REG_DWORD /d 1 /f</CommandLine>
        </SynchronousCommand>
            """.trimIndent())
            orderIndex += 1
        }

        if (config.disableBitLocker) {
            bypassCommands.append("""
        <SynchronousCommand wcm:action="add">
          <Order>$orderIndex</Order>
          <CommandLine>reg add HKLM\SYSTEM\CurrentControlSet\Control\BitLocker /v PreventDeviceEncryption /t REG_DWORD /d 1 /f</CommandLine>
        </SynchronousCommand>
            """.trimIndent())
            orderIndex += 1
        }

        val localUserSection = if (config.createLocalAccount && config.localUsername.isNotEmpty()) {
            """
      <UserAccounts>
        <LocalAccounts>
          <LocalAccount wcm:action="add">
            <Password>
              <Value></Value>
              <PlainText>true</PlainText>
            </Password>
            <Description>Local Administrator Account</Description>
            <DisplayName>${config.localUsername}</DisplayName>
            <Group>Administrators</Group>
            <Name>${config.localUsername}</Name>
          </LocalAccount>
        </LocalAccounts>
      </UserAccounts>
            """.trimIndent()
        } else ""

        val privacySection = if (config.disableDataCollection) {
            """
      <OOBE>
        <HideEULAPage>true</HideEULAPage>
        <HideOEMRegistrationScreens>true</HideOEMRegistrationScreens>
        <HideOnlineAccountScreens>true</HideOnlineAccountScreens>
        <HideWirelessSetupInOOBE>false</HideWirelessSetupInOOBE>
        <ProtectYourPC>3</ProtectYourPC>
      </OOBE>
            """.trimIndent()
        } else ""

        return """<?xml version="1.0" encoding="utf-8"?>
<unattend xmlns="urn:schemas-microsoft-com:unattend">
  <settings pass="windowsPE">
    <component name="Microsoft-Windows-Setup" processorArchitecture="amd64" publicKeyToken="31bf3856ad364e35" language="neutral" versionScope="nonSxS" xmlns:wcm="http://schemas.microsoft.com/WMIConfig/2002/State">
      <RunSynchronous>
$bypassCommands
      </RunSynchronous>
    </component>
  </settings>
  <settings pass="oobeSystem">
    <component name="Microsoft-Windows-Shell-Setup" processorArchitecture="amd64" publicKeyToken="31bf3856ad364e35" language="neutral" versionScope="nonSxS" xmlns:wcm="http://schemas.microsoft.com/WMIConfig/2002/State">
$localUserSection
$privacySection
    </component>
  </settings>
</unattend>
        """.trimIndent()
    }
}
