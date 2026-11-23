# SPDX-License-Identifier: GPL-3.0-or-later
# Builds the release APK and drops it in dist/ under a name that carries nothing but the version.
#
#   powershell -ExecutionPolicy Bypass -File scripts\package-apk.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\package-apk.ps1 -SkipBuild
#
# Naming rule: dist/<artifact>-<versionName>.apk. Build flavour, feature or signing state belong in
# the commit message, the git tag and CHANGELOG - never in the file name: two files that differ only by
# a suffix invite installing the wrong one.

param(
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$android = Join-Path $root 'android'
$gradle = 'F:\Android\gradle\gradle-8.9\bin\gradle.bat'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'

$buildFile = Join-Path $android 'app\build.gradle.kts'
$versionName = (Select-String -Path $buildFile -Pattern 'versionName\s*=\s*"([^"]+)"').Matches[0].Groups[1].Value
$versionCode = (Select-String -Path $buildFile -Pattern 'versionCode\s*=\s*(\d+)').Matches[0].Groups[1].Value
if (-not $versionName) { throw "cannot read versionName from $buildFile" }

if (-not $SkipBuild) {
    Push-Location $android
    try {
        & $gradle ':app:assembleRelease' --no-daemon --console=plain
        if ($LASTEXITCODE -ne 0) { throw "gradle failed with exit code $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
}

$built = Join-Path $android 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $built)) { throw "missing build output: $built" }

$dist = Join-Path $root 'dist'
New-Item -ItemType Directory -Force -Path $dist | Out-Null
$target = Join-Path $dist ('terramap-{0}.apk' -f $versionName)
Copy-Item $built $target -Force

$hash = (Get-FileHash $target -Algorithm SHA256).Hash.ToLower()
$size = (Get-Item $target).Length
"artifact : $target"
"version  : $versionName (versionCode $versionCode)"
"size     : {0:N0} bytes" -f $size
"sha256   : $hash"
