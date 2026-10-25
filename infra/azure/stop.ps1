#requires -Version 5.1
<#
.SYNOPSIS
    Detiene el stack y desaloca la VM de Azure (deja de cobrar computo).

.DESCRIPTION
    Los discos, la IP publica, ACR y Storage siguen existiendo. Los volumenes
    Docker y las bases de datos se conservan. Antes de detener el stack se
    ejecuta un backup manual a Blob Storage; usa -SkipBackup para omitirlo.
    Usa -Force solo si el apagado ordenado por SSH no es posible.

.EXAMPLE
    .\infra\azure\stop.ps1

.EXAMPLE
    .\infra\azure\stop.ps1 -SkipBackup
#>
[CmdletBinding()]
param(
    [string]$SshKeyPath = '',
    [switch]$Force,
    [switch]$SkipBackup
)

. (Join-Path $PSScriptRoot 'common.ps1')

Assert-Command -Name 'az'

$estado = Get-DeploymentState
if (-not $estado) {
    throw "No existe $script:StatePath. No se que VM apagar."
}

$null = Get-AzAccount

if (-not $SshKeyPath) { $SshKeyPath = $estado.sshKeyPath }

$vista = Get-AzJson -Arguments @(
    'vm', 'get-instance-view',
    '--resource-group', $estado.resourceGroup,
    '--name', $estado.vmName,
    '--only-show-errors', '-o', 'json'
)
$energia = @($vista.statuses | Where-Object { $_.code -like 'PowerState/*' }) | Select-Object -First 1
$estadoEnergia = if ($energia) { $energia.code } else { 'PowerState/unknown' }

if ($estadoEnergia -eq 'PowerState/deallocated') {
    Write-Ok "$($estado.vmName) ya esta deallocated; no se cobra computo."
    exit 0
}

$detenido = $false
if ($estadoEnergia -eq 'PowerState/running' -and $SshKeyPath -and (Test-Path -LiteralPath $SshKeyPath)) {
    if ($estado.nsgName) {
        try {
            $ipActual = Get-PublicIp
            Set-AdminSshRule -ResourceGroup $estado.resourceGroup -NsgName $estado.nsgName -SourceIp $ipActual
        } catch {
            Write-Warn "No se pudo actualizar la regla SSH con la IP actual: $($_.Exception.Message)"
        }
    }

    if (-not $SkipBackup) {
        Write-Step 'Creando backup antes de apagar (PostgreSQL, MySQL y configuracion)'
        $respaldo = Invoke-Remote -KeyPath $SshKeyPath -User $estado.adminUsername -VmHost $estado.fqdn `
            -Command 'sudo /bin/bash /opt/sos-clinic/infra/azure/backup.sh' -AllowFailure
        if ($respaldo.ExitCode -eq 0) {
            Write-Ok 'Backup completado'
        } else {
            Write-Warn "El backup fallo; se continua con el apagado.`n$($respaldo.Output)"
        }
    }

    Write-Step 'Deteniendo el stack de forma ordenada'
    $resultado = Invoke-Remote -KeyPath $SshKeyPath -User $estado.adminUsername -VmHost $estado.fqdn `
        -Command 'sudo systemctl stop sos-clinic.service' -AllowFailure
    if ($resultado.ExitCode -eq 0) {
        $detenido = $true
        Write-Ok 'Contenedores detenidos y volumenes conservados'
    } else {
        Write-Warn "No se pudo detener el stack por SSH: $($resultado.Output)"
    }
}

if (-not $detenido -and $estadoEnergia -eq 'PowerState/running' -and -not $Force) {
    throw 'No se pudo confirmar el apagado ordenado. Usa -Force para desalocar de todas formas.'
}

Write-Step "Desalocando $($estado.vmName). Esto puede tardar un minuto"
Invoke-Az -Arguments @(
    'vm', 'deallocate',
    '--resource-group', $estado.resourceGroup,
    '--name', $estado.vmName,
    '--only-show-errors'
) | Out-Null

Write-Ok 'VM desalocada'
Write-Host "Para volver a levantarla: .\infra\azure\start.ps1" -ForegroundColor Gray
Write-Host 'Recuerda que disco, IP, ACR y Storage siguen generando un costo pequeno.' -ForegroundColor Yellow
exit 0
