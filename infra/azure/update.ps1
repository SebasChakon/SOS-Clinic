#requires -Version 5.1
<#
.SYNOPSIS
    Actualiza codigo/imagenes en el despliegue Azure existente.

.DESCRIPTION
    Es un envoltorio de deploy.ps1 -SkipInfra:
      * construye un tag nuevo y publica las 7 imagenes en ACR;
      * sube el commit actual y regenera .env.azure;
      * reinicia el stack y ejecuta el smoke test.
    Los datos no se tocan salvo que uses -Reseed o -ReseedAnalytics.

.EXAMPLE
    .\infra\azure\update.ps1

.EXAMPLE
    .\infra\azure\update.ps1 -Reseed
#>
[CmdletBinding()]
param(
    [string]$SshKeyPath = '',
    [switch]$SkipBuild,
    [switch]$SkipSource,
    [switch]$Reseed,
    [switch]$ReseedAnalytics,
    [switch]$SyncLocalSecrets,
    [switch]$SkipVerify
)

$argumentos = @('-SkipInfra')
if ($SshKeyPath) { $argumentos += @('-SshKeyPath', $SshKeyPath) }
if ($SkipBuild) { $argumentos += '-SkipBuild' }
if ($SkipSource) { $argumentos += '-SkipSource' }
if ($Reseed) { $argumentos += '-Reseed' }
if ($ReseedAnalytics) { $argumentos += '-ReseedAnalytics' }
if ($SyncLocalSecrets) { $argumentos += '-SyncLocalSecrets' }
if ($SkipVerify) { $argumentos += '-SkipVerify' }

if (-not ($Reseed -or $ReseedAnalytics)) {
    $argumentos += '-SkipSeed'
}

& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'deploy.ps1') @argumentos
exit $LASTEXITCODE
