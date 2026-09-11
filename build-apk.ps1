$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

Write-Host "== 五条六眼 APK Build ==" -ForegroundColor Cyan

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    $studioJbr = @(
        "$env:ProgramFiles\Android\Android Studio\jbr\bin\java.exe",
        "$env:LOCALAPPDATA\Programs\Android Studio\jbr\bin\java.exe"
    ) | Where-Object { Test-Path $_ } | Select-Object -First 1
    if ($studioJbr) {
        $env:JAVA_HOME = Split-Path (Split-Path $studioJbr -Parent) -Parent
        $env:Path = "$env:JAVA_HOME\bin;$env:Path"
    } else {
        throw "未找到 Java。请安装 Android Studio（自带 JDK）或 JDK 17。"
    }
}

$javaVersion = (& java -version 2>&1 | Select-Object -First 1)
Write-Host "Java: $javaVersion"

if (-not $env:ANDROID_HOME -and -not $env:ANDROID_SDK_ROOT) {
    $sdkCandidates = @(
        "$env:LOCALAPPDATA\Android\Sdk",
        "$env:USERPROFILE\AppData\Local\Android\Sdk"
    ) | Where-Object { Test-Path $_ } | Select-Object -First 1
    if ($sdkCandidates) {
        $env:ANDROID_HOME = $sdkCandidates
        $env:ANDROID_SDK_ROOT = $sdkCandidates
    }
}

if (-not $env:ANDROID_HOME -and -not $env:ANDROID_SDK_ROOT) {
    throw "未找到 Android SDK。请先打开 Android Studio > SDK Manager 安装 Android SDK 37。"
}

$sdkPath = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
Write-Host "Android SDK: $sdkPath"

$androidJar = Join-Path $sdkPath "platforms\android-37\android.jar"
$buildTools = Join-Path $sdkPath "build-tools\36.0.0"
if (-not (Test-Path $androidJar) -or -not (Test-Path $buildTools)) {
    $sdkManagerCandidates = @(
        (Join-Path $sdkPath "cmdline-tools\latest\bin\sdkmanager.bat")
    )
    $sdkManagerCandidates += Get-ChildItem (Join-Path $sdkPath "cmdline-tools") -Filter sdkmanager.bat -Recurse -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty FullName
    $sdkManager = $sdkManagerCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $sdkManager) {
        throw "缺少 Android SDK 37 或 Build Tools 36.0.0，且未找到 sdkmanager。请在 Android Studio > SDK Manager 中安装 Android 17 (API 37) 与 Android SDK Build-Tools 36.0.0。"
    }
    Write-Host "正在安装 Android SDK 37 与 Build Tools 36.0.0..." -ForegroundColor Yellow
    & $sdkManager "platform-tools" "platforms;android-37" "build-tools;36.0.0"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

& .\gradlew.bat --no-daemon assembleDebug --stacktrace
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

New-Item -ItemType Directory -Force -Path .\dist | Out-Null
Copy-Item -Force .\app\build\outputs\apk\debug\app-debug.apk .\dist\WutiaoLiuyan-debug.apk
Write-Host "APK: $PSScriptRoot\dist\WutiaoLiuyan-debug.apk" -ForegroundColor Green
