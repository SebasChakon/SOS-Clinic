#requires -Version 5.1
<#
.SYNOPSIS
    Elimina el grupo de recursos de SOS Clinic en Azure.

.DESCRIPTION
    Borra VM, discos, IP, ACR, Storage y backups. No toca el repositorio local
    ni la llave SSH. Por defecto tambien elimina .env.azure, .deployment.json y
    .credenciales.azure.md; usa -KeepLocalSecrets para conservarlos.

.EXAMPLE
    .\infra\azure\destroy.ps1

.EXAMPLE
    .\infra\azure\destroy.ps1 -Force
#>
[CmdletBinding()]
param(
    [string]$ResourceGroup = '',
    [switch]$Force,
    [switch]$KeepLocalSecrets
)

. (Join-Path $PSScriptRoot 'common.ps1')

Assert-Command -Name 'az'
$null = Get-AzAccount

$estado = Get-DeploymentState
if (-not $ResourceGroup) {
    if (-not $estado) {
        throw 'No hay .deployment.json. Indica -ResourceGroup manualmente.'
    }
    $ResourceGroup = $estado.resourceGroup
}

if (-not $Force) {
    Write-Host ''
    Write-Host "Se eliminara el grupo de recursos '$ResourceGroup' y TODOS sus recursos." -ForegroundColor Yellow
    Write-Host 'Incluye la VM, discos, bases de datos en contenedores, ACR, Storage y backups.' -ForegroundColor Yellow
    $confirmacion = Read-Host "Escribe el nombre exacto del grupo para confirmar"
    if ($confirmacion -ne $ResourceGroup) {
        throw 'Cancelado.'
    }
}

Write-Step "Eliminando $ResourceGroup"
$resultado = Invoke-Az -Arguments @(
    'group', 'delete',
    '--name', $ResourceGroup,
    '--yes',
    '--only-show-errors'
) -AllowFailure
if ($resultado.ExitCode -ne 0) {
    Write-Warn "az group delete devolvio un error: $($resultado.Output)"
    Write-Warn 'Se conservan .deployment.json y los secretos locales para que puedas reintentar.'
    exit 1
}
Write-Ok 'Grupo de recursos eliminado'

Remove-Item -LiteralPath $script:StatePath -Force -ErrorAction SilentlyContinue
if (-not $KeepLocalSecrets) {
    Remove-Item -LiteralPath $script:AzureEnvPath -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath (Join-Path $script:AzureDir '.credenciales.azure.md') -Force -ErrorAction SilentlyContinue
    Write-Ok 'Secretos locales del despliegue eliminados'
} else {
    Write-Warn "Se conservaron $script:AzureEnvPath y .credenciales.azure.md"
}

Write-Host ''
Write-Host 'Si vas a conservar el proyecto, guarda antes cualquier backup que necesites.' -ForegroundColor Gray
exit 0
