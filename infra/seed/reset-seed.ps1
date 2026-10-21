# Wrapper PowerShell para infra/seed/reset_seed.py
param(
    [switch]$CleanVolume,
    [switch]$SkipBackup,
    [switch]$Yes,
    [switch]$RegenerateCredentials
)

$ErrorActionPreference = "Stop"

$python = Get-Command python -ErrorAction SilentlyContinue
if (-not $python) {
    $python = Get-Command python3 -ErrorAction SilentlyContinue
}
if (-not $python) {
    Write-Error "No se encontro python ni python3 en PATH."
    exit 1
}

$argumentos = @()
if ($CleanVolume) { $argumentos += "--clean-volume" }
if ($SkipBackup) { $argumentos += "--skip-backup" }
if ($Yes) { $argumentos += "--yes" }
if ($RegenerateCredentials) { $argumentos += "--regenerate-credentials" }

& $python.Source (Join-Path $PSScriptRoot "reset_seed.py") @argumentos
exit $LASTEXITCODE
