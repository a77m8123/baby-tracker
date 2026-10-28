$ErrorActionPreference = "Continue"

# JDK 17 и Android SDK. Поправь пути, если ставил в другое место.
$jdk = "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
if (-not (Test-Path $jdk)) {
    $jdk = (Get-ChildItem "C:\Program Files\Microsoft" -Filter "jdk-17*" -Directory |
            Select-Object -First 1).FullName
}
$env:JAVA_HOME = $jdk
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"

Set-Location $PSScriptRoot
& "$PSScriptRoot\gradlew.bat" --console=plain :app:assembleDebug
Write-Host ""
Write-Host "APK: $PSScriptRoot\app\build\outputs\apk\debug\app-debug.apk"
