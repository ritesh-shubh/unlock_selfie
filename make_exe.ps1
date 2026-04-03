$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$distDir = Join-Path $projectRoot "dist"
$outExe = Join-Path $distDir "UnlockSelfie.exe"
$source = Join-Path $projectRoot "README.md"
$icon = Join-Path $projectRoot "app\src\main\res\mipmap-xxxhdpi\ic_launcher.png"

if (-not (Get-Command iexpress -ErrorAction SilentlyContinue)) {
    throw "IExpress is required but was not found on this machine."
}

if (-not (Test-Path $source)) {
    throw "Source file not found: $source"
}

if (-not (Test-Path $icon)) {
    throw "Icon file not found: $icon"
}

if (-not (Test-Path $distDir)) {
    New-Item -ItemType Directory -Path $distDir | Out-Null
}

$tmpDir = Join-Path $env:TEMP ("unlock_selfie_exe_" + [Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $tmpDir | Out-Null

try {
    $tmpReadme = Join-Path $tmpDir "README.txt"
    Copy-Item -Path $source -Destination $tmpReadme -Force
    $tmpIcon = Join-Path $tmpDir "app_icon.png"
    Copy-Item -Path $icon -Destination $tmpIcon -Force

    $cmdFile = Join-Path $tmpDir "run.cmd"
    @'
@echo off
echo UnlockSelfie executable package
echo This repository contains an Android app. Build APK with Gradle.
echo.
type README.txt
pause
'@ | Set-Content -Path $cmdFile

    $sedFile = Join-Path $tmpDir "unlock_selfie.sed"
    @"
[Version]
Class=IEXPRESS
SEDVersion=3
[Options]
PackagePurpose=InstallApp
ShowInstallProgramWindow=1
HideExtractAnimation=0
UseLongFileName=1
InsideCompressed=0
CAB_FixedSize=0
CAB_ResvCodeSigning=0
RebootMode=N
InstallPrompt=
DisplayLicense=
FinishMessage=
TargetName=$outExe
FriendlyName=UnlockSelfie
AppLaunched=run.cmd
PostInstallCmd=<None>
AdminQuietInstCmd=
UserQuietInstCmd=
SourceFiles=SourceFiles
[SourceFiles]
SourceFiles0=$tmpDir
[SourceFiles0]
%FILE0%=README.txt
%FILE1%=run.cmd
%FILE2%=app_icon.png
[Strings]
FILE0=README.txt
FILE1=run.cmd
FILE2=app_icon.png
"@ | Set-Content -Path $sedFile

    & iexpress /N $sedFile

    if (-not (Test-Path $outExe)) {
        throw "EXE was not created: $outExe"
    }

    Write-Host "Created: $outExe"
}
finally {
    if (Test-Path $tmpDir) {
        Remove-Item -Path $tmpDir -Recurse -Force
    }
}
