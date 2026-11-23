<#
.SYNOPSIS
    Builds the C# L1 core and runs its self test.

.DESCRIPTION
    Uses only the .NET SDK: no NuGet packages, no network. The self test works on synthetic
    bytes; pass -WithSamples to also check a real .wld/.map pair (read from disk, never written).

.EXAMPLE
    pwsh scripts/verify-dotnet.ps1

.EXAMPLE
    pwsh scripts/verify-dotnet.ps1 -WithSamples ..\live.wld ..\map1_orig_fromdevice.bin
#>
[CmdletBinding()]
param(
    [switch]$WithSamples,
    [string]$WorldPath,
    [string]$MapPath
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$project = Join-Path $root 'csharp\TerraMap.SelfTest\TerraMap.SelfTest.csproj'

if (-not (Get-Command dotnet -ErrorAction SilentlyContinue)) {
    Write-Host 'toolchain: unavailable (dotnet not found)'
    exit 2
}

Write-Host "dotnet: $(& dotnet --version)"

& dotnet build $project -v q --nologo
if ($LASTEXITCODE -ne 0) {
    Write-Host "build: FAILED ($LASTEXITCODE)"
    exit 1
}
Write-Host 'build: ok'

$runArgs = @('run', '--project', $project, '--no-build', '--')
if ($WithSamples) {
    if (-not $WorldPath -or -not $MapPath) {
        Write-Host 'samples: need both -WorldPath and -MapPath'
        exit 3
    }
    $runArgs += @('--with-samples', (Resolve-Path $WorldPath).Path, (Resolve-Path $MapPath).Path)
}

& dotnet @runArgs
exit $LASTEXITCODE
