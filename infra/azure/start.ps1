#requires -Version 5.1
<#
.SYNOPSIS
    Enciende la VM de SOS Clinic y arranca el stack.

.EXAMPLE
    .\infra\azure\start.ps1
#>
[CmdletBinding()]
param(
    [string]$SshKeyPath = '',
    [switch]$SkipSmoke
)

. (Join-Path $PSScriptRoot 'common.ps1')

Assert-Command -Name 'az'
Assert-Command -Name 'ssh'

$estado = Get-DeploymentState
if (-not $estado) {
    throw "No existe $script:StatePath. Ejecuta deploy.ps1 primero."
}

$null = Get-AzAccount

if (-not $SshKeyPath) { $SshKeyPath = $estado.sshKeyPath }
if (-not $SshKeyPath -or -not (Test-Path -LiteralPath $SshKeyPath)) {
    throw "No encuentro la llave SSH. Indica -SshKeyPath."
}

$ip = Get-PublicIp
Set-AdminSshRule -ResourceGroup $estado.resourceGroup -NsgName $estado.nsgName -SourceIp $ip

Write-Step "Arrancando $($estado.vmName) en $($estado.location)"
Invoke-Az -Arguments @(
    'vm', 'start',
    '--resource-group', $estado.resourceGroup,
    '--name', $estado.vmName,
    '--only-show-errors'
) | Out-Null

Wait-Ssh -KeyPath $SshKeyPath -User $estado.adminUsername -VmHost $estado.fqdn -TimeoutSeconds 600

Write-Step 'Asegurando que el stack este arriba'
Invoke-Remote -KeyPath $SshKeyPath -User $estado.adminUsername -VmHost $estado.fqdn `
    -Command 'sudo systemctl start sos-clinic.service' | Out-Null

Wait-RemoteHttp -KeyPath $SshKeyPath -User $estado.adminUsername -VmHost $estado.fqdn `
    -Url 'http://127.0.0.1:8080/ms-seguridad/auth/politica-clave' -TimeoutSeconds 900

Write-Step "Esperando HTTPS en https://$($estado.fqdn)"
Wait-PublicHttps -Fqdn $estado.fqdn -TimeoutSeconds 420
Write-Ok 'HTTPS responde'

if (-not $SkipSmoke) {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'smoke-test.ps1') -Domain $estado.fqdn
    if ($LASTEXITCODE -ne 0) {
        exit 1
    }
}

Write-Host ''
Write-Host "SOS Clinic arriba: https://$($estado.fqdn)" -ForegroundColor Green
Write-Host "SSH: ssh -i `"$SshKeyPath`" $($estado.adminUsername)@$($estado.fqdn)" -ForegroundColor Gray
Write-Host "Apagar: .\infra\azure\stop.ps1" -ForegroundColor Gray
exit 0
