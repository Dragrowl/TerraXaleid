<#
.SYNOPSIS
    Compiles the Kotlin L1 port and runs its self test.

.DESCRIPTION
    Tries, in order:
      1. kotlinc on PATH
      2. kotlin/kotlin-compiler-embeddable-*.jar (offline copy under .tools)
      3. the project's own Gradle wrapper / a Gradle on PATH, via the `selftest` task

    Nothing is downloaded. If no toolchain is present the script prints
    "toolchain: unavailable" and exits with code 2 instead of pretending to pass.

.EXAMPLE
    pwsh scripts/verify-kotlin.ps1
#>
[CmdletBinding()]
param(
    [switch]$WithSamples,
    [string]$WorldPath,
    [string]$MapPath
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$kotlinDir = Join-Path $root 'kotlin'
$srcDir = Join-Path $kotlinDir 'src\main\kotlin'
$toolsDir = Join-Path $kotlinDir '.tools'

function Get-JavaExe {
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME 'bin\java.exe'
        if (Test-Path $candidate) { return $candidate }
    }
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}

function Get-StdlibJar {
    $local = Get-ChildItem $toolsDir -Filter 'kotlin-stdlib-*.jar' -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($local) { return $local.FullName }
    $cached = Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\org.jetbrains.kotlin\kotlin-stdlib" `
        -Recurse -Filter 'kotlin-stdlib-*.jar' -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($cached) { return $cached.FullName }
    return $null
}

$java = Get-JavaExe
$stdlib = Get-StdlibJar
$sampleArgs = @()
if ($WithSamples) {
    if (-not $WorldPath -or -not $MapPath) {
        Write-Host 'samples: need both -WorldPath and -MapPath'
        exit 3
    }
    $sampleArgs = @('--with-samples', (Resolve-Path $WorldPath).Path, (Resolve-Path $MapPath).Path)
}

# 1) kotlinc on PATH
$kotlinc = Get-Command kotlinc -ErrorAction SilentlyContinue
if ($kotlinc) {
    Write-Host "toolchain: kotlinc ($($kotlinc.Source))"
    & $kotlinc.Source -version
    & $kotlinc.Source -include-runtime -jvm-target 17 -d (Join-Path $kotlinDir '.verify\terramap.jar') $srcDir
    if ($LASTEXITCODE -ne 0) { Write-Host "compile: FAILED ($LASTEXITCODE)"; exit 1 }
    Write-Host 'compile: ok'
    & $java -jar (Join-Path $kotlinDir '.verify\terramap.jar') @sampleArgs
    exit $LASTEXITCODE
}

# 2) offline kotlin-compiler-embeddable
$compilerJar = Get-ChildItem $toolsDir -Filter 'kotlin-compiler-embeddable-*.jar' -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending | Select-Object -First 1
if ($compilerJar -and $java -and $stdlib) {
    Write-Host "toolchain: kotlin-compiler-embeddable ($($compilerJar.Name))"
    $classes = Join-Path $kotlinDir '.verify\classes'
    $outJar = Join-Path $kotlinDir '.verify\terramap.jar'
    Remove-Item $classes -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $classes | Out-Null

    $extraJars = @($compilerJar.FullName) + @(
        Get-ChildItem $toolsDir -Filter '*.jar' -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -ne $compilerJar.Name } |
            Select-Object -ExpandProperty FullName
    )
    $cp = $extraJars -join ';'

    & $java -cp $cp org.jetbrains.kotlin.cli.jvm.K2JVMCompiler `
        -no-stdlib -no-reflect -classpath $stdlib -jvm-target 17 -d $classes $srcDir
    if ($LASTEXITCODE -ne 0) { Write-Host "compile: FAILED ($LASTEXITCODE)"; exit 1 }
    Write-Host 'compile: ok'

    Push-Location $classes
    try { & jar cf $outJar . } finally { Pop-Location }
    if (-not (Test-Path $outJar)) { Write-Host 'jar: FAILED'; exit 1 }

    & $java -cp "$outJar;$stdlib" io.terraxaleid.core.SelfTestKt @sampleArgs
    exit $LASTEXITCODE
}

# 3) Gradle
$gradlew = Join-Path $kotlinDir 'gradlew.bat'
$gradleCmd = if (Test-Path $gradlew) { $gradlew } elseif (Get-Command gradle -ErrorAction SilentlyContinue) { 'gradle' } else { $null }
if ($gradleCmd) {
    Write-Host "toolchain: gradle ($gradleCmd)"
    Push-Location $kotlinDir
    try {
        if ($WithSamples) {
            & $gradleCmd selftest -Pworld=$WorldPath -Pmap=$MapPath
        } else {
            & $gradleCmd selftest
        }
        exit $LASTEXITCODE
    } finally { Pop-Location }
}

Write-Host 'toolchain: unavailable (no kotlinc, no kotlin-compiler-embeddable jar, no gradle)'
exit 2
