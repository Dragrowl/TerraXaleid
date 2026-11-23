<#
.SYNOPSIS
    Runs every L1 self test: Python, C#, Kotlin.

.DESCRIPTION
    Each language runs in its own child process so a failure in one does not stop the others.
    A missing toolchain is reported as "unavailable" (exit code 2 from the child) and is counted
    as a failure: the repository is only green when all three actually ran.

    Nothing is downloaded and nothing under version control is modified; the Kotlin step writes
    only inside kotlin/.verify (ignored by git).

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\verify-all.ps1

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\verify-all.ps1 -WithSamples `
        -WorldPath C:\saves\world.wld -MapPath C:\saves\template.map
#>
[CmdletBinding()]
param(
    [switch]$WithSamples,
    [string]$WorldPath,
    [string]$MapPath
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$results = New-Object System.Collections.ArrayList

function Invoke-Step {
    param(
        [string]$Name,
        [scriptblock]$Body
    )
    Write-Host ''
    Write-Host "=== $Name ===" -ForegroundColor Cyan
    $sw = [Diagnostics.Stopwatch]::StartNew()
    $code = 0
    try {
        & $Body
        $code = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
    } catch {
        Write-Host "error: $($_.Exception.Message)" -ForegroundColor Red
        $code = 1
    }
    $sw.Stop()
    $null = $results.Add([pscustomobject]@{
        Step     = $Name
        Exit     = $code
        Seconds  = [math]::Round($sw.Elapsed.TotalSeconds, 1)
        Verdict  = if ($code -eq 0) { 'PASS' } elseif ($code -eq 2) { 'UNAVAILABLE' } else { 'FAIL' }
    })
}

$sample = @{}
if ($WithSamples) {
    if (-not $WorldPath -or -not $MapPath) {
        Write-Host 'samples: need both -WorldPath and -MapPath' -ForegroundColor Red
        exit 3
    }
    $sample = @{ WithSamples = $true; WorldPath = $WorldPath; MapPath = $MapPath }
}

Invoke-Step 'python selftest' {
    Push-Location $root
    try {
        if ($WithSamples) { & python .\selftest.py $WorldPath $MapPath } else { & python .\selftest.py }
    } finally { Pop-Location }
}

Invoke-Step 'csharp selftest' {
    & powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'verify-dotnet.ps1') @sample
}

Invoke-Step 'kotlin selftest' {
    & powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'verify-kotlin.ps1') @sample
}

Write-Host ''
Write-Host '=== summary ===' -ForegroundColor Cyan
$results | Format-Table -AutoSize | Out-String | Write-Host

$failed = @($results | Where-Object { $_.Exit -ne 0 })
if ($failed.Count -gt 0) {
    Write-Host ("FAILED: " + (($failed | ForEach-Object { "$($_.Step)=$($_.Verdict)" }) -join ', ')) -ForegroundColor Red
    exit 1
}

Write-Host 'RESULT: PASS' -ForegroundColor Green
exit 0
