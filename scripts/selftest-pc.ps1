# SPDX-License-Identifier: GPL-3.0-or-later
<#
.SYNOPSIS
    End-to-end smoke test for the desktop (326) writer.

.DESCRIPTION
    Runs terraxaleid-cli --auto-test for one world / one character, which reads the .wld,
    writes Players/<character>/<world guid>.map, reads it back and verifies the cell count.
    Prints PASS / FAIL and exits with the CLI's exit code so CI can use it directly.

.EXAMPLE
    pwsh -File scripts\selftest-pc.ps1 -World 111test -Player koboshi
    pwsh -File scripts\selftest-pc.ps1 -World 111test -Player koboshi -DryRun
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$World,
    [Parameter(Mandatory = $true)][string]$Player,
    [switch]$DryRun,
    [string]$Configuration = 'Release'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$project = Join-Path $root 'windows\TerraXaleid.Cli\TerraXaleid.Cli.csproj'
$exe = Join-Path $root "windows\TerraXaleid.Cli\bin\$Configuration\net10.0\terraxaleid-cli.exe"

Write-Host "building  $project ($Configuration)"
& dotnet build $project -c $Configuration -v q --nologo | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host 'FAIL      build'
    exit 1
}

$cliArgs = @('--auto-test', '--world', $World, '--player', $Player)
if ($DryRun) { $cliArgs += '--dry-run' }

Write-Host "running   terraxaleid-cli $($cliArgs -join ' ')"
& $exe @cliArgs
$code = $LASTEXITCODE

if ($code -eq 0) {
    Write-Host 'PASS      auto-test'
} else {
    Write-Host "FAIL      auto-test (exit $code)"
}

exit $code
