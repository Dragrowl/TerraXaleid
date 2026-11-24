# Push this repository to GitHub without ever writing the token to disk or to .git/config.
#
#   $env:GITHUB_TOKEN = 'github_pat_...'      # fine-grained: Contents: Read and write
#   powershell -ExecutionPolicy Bypass -File .\scripts\push-github.ps1
#   powershell -ExecutionPolicy Bypass -File .\scripts\push-github.ps1 -Owner someone -Repo terramap
#
# The token travels in an http.extraheader for the duration of one git invocation only.

[CmdletBinding()]
param(
    [string] $Owner = 'Dragrowl',
    [string] $Repo  = 'terramap',
    [string] $Branch = 'main',
    [switch] $TagsOnly
)

$ErrorActionPreference = 'Stop'

$token = $env:GITHUB_TOKEN
if ([string]::IsNullOrWhiteSpace($token)) {
    Write-Host 'GITHUB_TOKEN is not set.' -ForegroundColor Red
    Write-Host '  $env:GITHUB_TOKEN = ''github_pat_...''' -ForegroundColor Yellow
    exit 2
}

$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    $url = "https://github.com/$Owner/$Repo.git"

    $existing = (git remote 2>$null) -contains 'origin'
    if (-not $existing) {
        git remote add origin $url
        Write-Host "remote origin -> $url"
    } else {
        git remote set-url origin $url
        Write-Host "remote origin updated -> $url"
    }

    # basic auth header: user name is irrelevant for PATs, any non-empty value works
    $pair = "x-access-token:$token"
    $b64 = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
    $header = "http.https://github.com/.extraheader=AUTHORIZATION: basic $b64"

    # direct HTTPS to github.com gets reset on some networks; use the local proxy when it is up
    $gitArgs = @('-c', $header)
    $proxyUp = $false
    try {
        $proxyUp = (Test-NetConnection -ComputerName 127.0.0.1 -Port 7890 -InformationLevel Quiet -WarningAction SilentlyContinue)
    } catch { $proxyUp = $false }
    if ($proxyUp) {
        $gitArgs += @('-c', 'http.proxy=http://127.0.0.1:7890')
        Write-Host 'using proxy 127.0.0.1:7890'
    } else {
        Write-Host 'no proxy on 127.0.0.1:7890, going direct'
    }

    if (-not $TagsOnly) {
        Write-Host "pushing $Branch ..."
        & git @gitArgs push -u origin $Branch
        if ($LASTEXITCODE -ne 0) { throw "push of $Branch failed (exit $LASTEXITCODE)" }
    }

    Write-Host 'pushing tags ...'
    & git @gitArgs push origin --tags
    if ($LASTEXITCODE -ne 0) { Write-Host 'tag push failed (tags may already exist remotely)' -ForegroundColor Yellow }

    Write-Host ''
    Write-Host "done: https://github.com/$Owner/$Repo" -ForegroundColor Green
    git log --oneline -1
    git tag --list 'v0.1.0*'
} finally {
    Pop-Location
}
