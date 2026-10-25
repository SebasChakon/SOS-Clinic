#requires -Version 5.1
<#
.SYNOPSIS
    Despliega SOS Clinic completo en una VM Linux de Azure.

.DESCRIPTION
    Flujo:
      1. Valida az login, Docker, SSH y cuota de la VM.
      2. Despliega main.bicep (red, VM, ACR, Storage, identidad, auto-shutdown).
      3. Construye las 7 imagenes y las publica en ACR.
      4. Sube el commit actual y un .env nuevo a /opt/sos-clinic.
      5. Carga PostgreSQL y MySQL y arranca el stack con systemd.
      6. Verifica el seed y prueba HTTPS.

    Los secretos se generan en infra/azure/.env.azure (ignorado por Git).
    No se imprimen contrasenas ni tokens.

.EXAMPLE
    .\infra\azure\deploy.ps1

.EXAMPLE
    .\infra\azure\deploy.ps1 -Location brazilsouth -VmSize Standard_B2ms

.EXAMPLE
    # Reintentar solo build + subida + arranque sobre infraestructura existente
    .\infra\azure\deploy.ps1 -SkipInfra -SkipSeed
#>
[CmdletBinding()]
param(
    [string]$ResourceGroup = 'sos-clinic-rg',

    [string]$Location = '',

    [ValidateSet('Standard_B2ms', 'Standard_B4ms', 'Standard_D2as_v5', 'Standard_D2s_v5', 'Standard_D2as_v7', 'Standard_D2s_v7')]
    [string]$VmSize = 'Standard_B2ms',

    [string]$DnsLabel = '',

    [string]$AcrName = '',

    [string]$StorageName = '',

    [string]$AdminUsername = 'azureuser',

    [string]$SshKeyPath = (Join-Path $HOME '.ssh\sos_clinic_azure'),

    [string]$AcmeEmail = '',

    [string]$AlertEmail = '',

    [string]$AdminSourceIp = '',

    [string]$TrialEndDate = '',

    [int]$BudgetAmount = 60,

    [string]$AutoShutdownTime = '2200',

    [switch]$SkipInfra,

    [switch]$SkipBuild,

    [switch]$SkipSource,

    [switch]$SkipSeed,

    [switch]$Reseed,

    [switch]$ReseedAnalytics,

    [switch]$SkipVerify,

    [switch]$NoStart,

    [switch]$SyncLocalSecrets
)

. (Join-Path $PSScriptRoot 'common.ps1')

# ---------------------------------------------------------------------------
# Utilidades propias del despliegue
# ---------------------------------------------------------------------------

function New-RandomSuffix {
    param([int]$Length = 6)
    $alfabeto = 'abcdefghijklmnopqrstuvwxyz0123456789'
    $caracteres = for ($i = 0; $i -lt $Length; $i++) {
        $alfabeto[(Get-Random -Minimum 0 -Maximum $alfabeto.Length)]
    }
    return -join $caracteres
}

function Test-DnsLabelFree {
    param(
        [Parameter(Mandatory = $true)][string]$Label,
        [Parameter(Mandatory = $true)][string]$Region
    )
    $fqdn = "$Label.$Region.cloudapp.azure.com"
    try {
        if (Get-Command Resolve-DnsName -ErrorAction SilentlyContinue) {
            Resolve-DnsName -Name $fqdn -Type A -ErrorAction Stop | Out-Null
        } else {
            [System.Net.Dns]::GetHostEntry($fqdn) | Out-Null
        }
        return $false
    } catch {
        return $true
    }
}

function Test-AcrNameFree {
    param([Parameter(Mandatory = $true)][string]$Name)
    $resultado = Invoke-Az -Arguments @(
        'acr', 'check-name', '--name', $Name, '--only-show-errors', '-o', 'json'
    )
    $objeto = $resultado.Output | ConvertFrom-Json
    return [bool]$objeto.nameAvailable
}

function Test-StorageNameFree {
    param([Parameter(Mandatory = $true)][string]$Name)
    $resultado = Invoke-Az -Arguments @(
        'storage', 'account', 'check-name', '--name', $Name, '--only-show-errors', '-o', 'json'
    )
    $objeto = $resultado.Output | ConvertFrom-Json
    return [bool]$objeto.nameAvailable
}

function Test-VmSizeAvailable {
    param(
        [Parameter(Mandatory = $true)][string]$Region,
        [Parameter(Mandatory = $true)][string]$Size
    )

    $skus = Get-AzJson -Arguments @(
        'vm', 'list-skus', '--location', $Region, '--resource-type', 'virtualMachines',
        '--query', "[?name=='$Size']", '--only-show-errors', '-o', 'json'
    )
    if (-not $skus -or @($skus).Count -eq 0) {
        return [pscustomobject]@{ Available = $false; Reason = "SKU $Size no existe en $Region" }
    }

    $sku = @($skus)[0]
    $bloqueos = @($sku.restrictions | Where-Object { $_.reasonCode -and $_.reasonCode -ne 'None' })
    if ($bloqueos.Count -gt 0) {
        return [pscustomobject]@{ Available = $false; Reason = "restriccion: $($bloqueos[0].reasonCode)" }
    }

    $uso = Get-AzJson -Arguments @(
        'vm', 'list-usage', '--location', $Region, '--only-show-errors', '-o', 'json'
    )
    $entrada = @($uso | Where-Object { $_.name.value -eq $sku.family }) | Select-Object -First 1
    if ($entrada) {
        $vcpus = 2
        $capacidad = @($sku.capabilities | Where-Object { $_.name -eq 'vCPUs' }) | Select-Object -First 1
        if ($capacidad) { $vcpus = [int]$capacidad.value }
        if (($entrada.currentValue + $vcpus) -gt $entrada.limit) {
            return [pscustomobject]@{
                Available = $false
                Reason    = "cuota $($sku.family): $($entrada.currentValue)/$($entrada.limit) vCPU"
            }
        }
    }

    return [pscustomobject]@{ Available = $true; Reason = '' }
}

function Select-VmPlacement {
    param(
        [string]$RequestedLocation,
        [string]$RequestedSize
    )

    $regiones = if ($RequestedLocation) {
        @($RequestedLocation)
    } else {
        @('eastus2', 'brazilsouth')
    }
    $tamanos = if ($RequestedSize) {
        @($RequestedSize)
    } else {
        @('Standard_B2ms', 'Standard_D2as_v5', 'Standard_D2as_v7', 'Standard_D2s_v7')
    }

    $descartados = New-Object System.Collections.Generic.List[string]
    foreach ($region in $regiones) {
        foreach ($tamano in $tamanos) {
            Write-Host "  Probando $tamano en $region..." -ForegroundColor DarkGray
            $estado = Test-VmSizeAvailable -Region $region -Size $tamano
            if ($estado.Available) {
                return [pscustomobject]@{
                    Location = $region
                    VmSize   = $tamano
                    Notes    = ($descartados -join '; ')
                }
            }
            $descartados.Add("$region/$tamano -> $($estado.Reason)")
        }
    }

    throw "No hay cuota/SKU disponible. Detalle: $($descartados -join '; '). Prueba -Location brazilsouth, otro -VmSize o solicita cuota en Azure."
}

function Resolve-ResourceNames {
    param(
        [string]$Region,
        [string]$RequestedDnsLabel,
        [string]$RequestedAcr,
        [string]$RequestedStorage
    )

    if ($RequestedDnsLabel) {
        if ($RequestedDnsLabel -notmatch '^[a-z0-9]([a-z0-9-]{1,61}[a-z0-9])?$') {
            throw "DnsLabel invalido. Usa minusculas, numeros y guiones (3-63 caracteres)."
        }
        if (-not (Test-DnsLabelFree -Label $RequestedDnsLabel -Region $Region)) {
            throw "El DNS $RequestedDnsLabel.$Region.cloudapp.azure.com ya esta en uso. Elige otro -DnsLabel."
        }
        $dns = $RequestedDnsLabel
    } else {
        do {
            $dns = 'sos-clinic-' + (New-RandomSuffix -Length 5)
        } while (-not (Test-DnsLabelFree -Label $dns -Region $Region))
    }

    if ($RequestedAcr) {
        if ($RequestedAcr -notmatch '^[a-zA-Z0-9]{5,50}$') {
            throw "AcrName invalido. Solo letras y numeros, 5-50 caracteres."
        }
        if (-not (Test-AcrNameFree -Name $RequestedAcr)) {
            throw "El ACR $RequestedAcr ya existe globalmente. Elige otro -AcrName."
        }
        $acr = $RequestedAcr.ToLowerInvariant()
    } else {
        do {
            $acr = 'sosclinic' + (New-RandomSuffix -Length 6)
        } while (-not (Test-AcrNameFree -Name $acr))
    }

    if ($RequestedStorage) {
        if ($RequestedStorage -notmatch '^[a-z0-9]{3,24}$') {
            throw "StorageName invalido. Solo minusculas y numeros, 3-24 caracteres."
        }
        if (-not (Test-StorageNameFree -Name $RequestedStorage)) {
            throw "La Storage Account $RequestedStorage ya existe globalmente. Elige otro -StorageName."
        }
        $storage = $RequestedStorage
    } else {
        do {
            $storage = 'sosclinic' + (New-RandomSuffix -Length 6)
        } while (-not (Test-StorageNameFree -Name $storage))
    }

    return [pscustomobject]@{
        DnsLabel      = $dns
        AcrName       = $acr
        StorageName   = $storage
        BackupContainer = 'sos-clinic-backups'
    }
}

function Ensure-SshKey {
    param([Parameter(Mandatory = $true)][string]$Path)

    $publica = "$Path.pub"
    if ((Test-Path -LiteralPath $Path) -and (Test-Path -LiteralPath $publica)) {
        $contenido = (Get-Content -LiteralPath $publica -Raw).Trim()
        if ($contenido -notmatch '^(ssh-ed25519|ssh-rsa|ecdsa-sha2-)') {
            throw "La llave publica $publica no tiene un formato OpenSSH reconocido."
        }
        return $contenido
    }
    if (Test-Path -LiteralPath $Path) {
        throw "Existe $Path pero falta $publica. Restaura la llave publica o mueve ambas."
    }

    $directorio = Split-Path -Parent $Path
    New-Item -ItemType Directory -Force -Path $directorio | Out-Null
    Write-Step "Generando llave SSH en $Path"
    $comando = 'ssh-keygen -t ed25519 -C "sos-clinic-azure" -f "{0}" -N "" -q' -f $Path
    & cmd.exe /c $comando
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $publica)) {
        throw "No se pudo generar la llave SSH. Ejecuta: ssh-keygen -t ed25519 -f $Path"
    }
    return (Get-Content -LiteralPath $publica -Raw).Trim()
}

function Ensure-Provider {
    param([Parameter(Mandatory = $true)][string]$Namespace)

    $estado = (Invoke-Az -Arguments @(
        'provider', 'show', '--namespace', $Namespace,
        '--query', 'registrationState', '-o', 'tsv', '--only-show-errors'
    )).Output.Trim()

    if ($estado -eq 'Registered') {
        return
    }

    Write-Host "  Registrando proveedor $Namespace ($estado)..." -ForegroundColor DarkGray
    Invoke-Az -Arguments @('provider', 'register', '--namespace', $Namespace, '--only-show-errors') | Out-Null
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 5
        $estado = (Invoke-Az -Arguments @(
            'provider', 'show', '--namespace', $Namespace,
            '--query', 'registrationState', '-o', 'tsv', '--only-show-errors'
        )).Output.Trim()
        if ($estado -eq 'Registered') {
            return
        }
    }
    throw "El proveedor $Namespace no llego a Registered."
}

function Write-JsonFile {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]$Object
    )
    $json = $Object | ConvertTo-Json -Depth 12
    $utf8 = New-Object System.Text.UTF8Encoding -ArgumentList $false
    [System.IO.File]::WriteAllText($Path, $json, $utf8)
}

function New-AzureEnvFile {
    param(
        [Parameter(Mandatory = $true)]$State,
        [Parameter(Mandatory = $true)][string]$ImageTag,
        [Parameter(Mandatory = $true)][string]$AcmeEmailAddress,
        [switch]$SyncLocalSecrets
    )

    $existente = Read-DotEnv -Path $script:AzureEnvPath
    $local = Read-DotEnv -Path (Join-Path $script:RepoRoot '.env')

    $jwt = $existente['APP_JWT_SECRET']
    if ([string]::IsNullOrWhiteSpace($jwt)) { $jwt = New-RandomHex -Bytes 64 }

    $dbPassword = $existente['DB_PASSWORD']
    $pgPassword = $existente['POSTGRES_PASSWORD']
    if ([string]::IsNullOrWhiteSpace($dbPassword) -and [string]::IsNullOrWhiteSpace($pgPassword)) {
        $dbPassword = New-RandomHex -Bytes 24
        $pgPassword = $dbPassword
    } elseif ([string]::IsNullOrWhiteSpace($dbPassword)) {
        $dbPassword = $pgPassword
    } elseif ([string]::IsNullOrWhiteSpace($pgPassword)) {
        $pgPassword = $dbPassword
    } elseif ($dbPassword -ne $pgPassword) {
        throw "DB_PASSWORD y POSTGRES_PASSWORD no coinciden en .env.azure. Corrige el archivo antes de continuar."
    }

    $mysqlPassword = $existente['ANALITICA_DB_PASSWORD']
    if ([string]::IsNullOrWhiteSpace($mysqlPassword)) { $mysqlPassword = New-RandomHex -Bytes 24 }

    $mailUser = $existente['MAIL_USERNAME']
    $mailPassword = $existente['MAIL_PASSWORD']
    $mailHost = $existente['MAIL_SMTP_HOST']
    $mailPort = $existente['MAIL_SMTP_PORT']
    $groqKey = $existente['GROQ_API_KEY']

    if ($SyncLocalSecrets) {
        if ($local['MAIL_USERNAME']) { $mailUser = $local['MAIL_USERNAME'] }
        if ($local['MAIL_PASSWORD']) { $mailPassword = $local['MAIL_PASSWORD'] }
        if ($local['MAIL_SMTP_HOST']) { $mailHost = $local['MAIL_SMTP_HOST'] }
        if ($local['MAIL_SMTP_PORT']) { $mailPort = $local['MAIL_SMTP_PORT'] }
        if ($local['GROQ_API_KEY']) { $groqKey = $local['GROQ_API_KEY'] }
    }

    if ([string]::IsNullOrWhiteSpace($mailUser) -and $local['MAIL_USERNAME']) { $mailUser = $local['MAIL_USERNAME'] }
    if ([string]::IsNullOrWhiteSpace($mailPassword) -and $local['MAIL_PASSWORD']) { $mailPassword = $local['MAIL_PASSWORD'] }
    if ([string]::IsNullOrWhiteSpace($mailHost)) { $mailHost = 'smtp.gmail.com' }
    if ([string]::IsNullOrWhiteSpace($mailPort)) { $mailPort = '587' }
    if ([string]::IsNullOrWhiteSpace($groqKey) -and $local['GROQ_API_KEY']) { $groqKey = $local['GROQ_API_KEY'] }

    if ([string]::IsNullOrWhiteSpace($mailUser) -or [string]::IsNullOrWhiteSpace($mailPassword)) {
        throw 'Faltan MAIL_USERNAME/MAIL_PASSWORD en .env local. Configura el correo antes de desplegar.'
    }
    if ([string]::IsNullOrWhiteSpace($groqKey)) {
        Write-Warn 'GROQ_API_KEY esta vacia: el agente IA no respondera hasta configurarla.'
    }

    $valores = [ordered]@{
        SOS_DOMAIN               = $State.fqdn
        ACME_EMAIL               = $AcmeEmailAddress
        ACR_NAME                 = $State.acrName
        ACR_LOGIN_SERVER         = $State.acrLoginServer
        IMAGE_TAG                = $ImageTag
        STORAGE_ACCOUNT          = $State.storageAccount
        BACKUP_CONTAINER         = $State.backupContainer
        DB_USERNAME              = $existente['DB_USERNAME']
        DB_PASSWORD              = $dbPassword
        POSTGRES_PASSWORD        = $pgPassword
        ANALITICA_DB_USERNAME    = 'root'
        ANALITICA_DB_PASSWORD    = $mysqlPassword
        APP_JWT_SECRET           = $jwt
        MAIL_SMTP_HOST           = $mailHost
        MAIL_SMTP_PORT           = $mailPort
        MAIL_USERNAME            = $mailUser
        MAIL_PASSWORD            = $mailPassword
        GROQ_API_KEY             = $groqKey
        CORS_ALLOWED_ORIGINS     = "https://$($State.fqdn)"
        FRONTEND_URL             = "https://$($State.fqdn)"
        TZ                       = 'America/Bogota'
        SOS_COMPOSE_FILE         = '/opt/sos-clinic/infra/azure/docker-compose.azure.yml'
        DB_CONTAINER             = 'sos-postgres'
    }
    if ([string]::IsNullOrWhiteSpace($valores['DB_USERNAME'])) {
        $valores['DB_USERNAME'] = 'sos_admin'
    }

    Write-DotEnv -Path $script:AzureEnvPath -Values $valores
    Write-Ok "Variables generadas en $script:AzureEnvPath"
    return $valores
}

function Deploy-Infrastructure {
    param(
        [string]$Region,
        [string]$Size,
        [string]$Dns,
        [string]$Acr,
        [string]$Storage,
        [string]$Container,
        [string]$SshPublicKey,
        [string]$AdminUser,
        [string]$SourcePrefix,
        [string]$BudgetEmail,
        [string]$TrialEnd,
        [string]$ShutdownTime
    )

    Write-Step "Preparando grupo de recursos $ResourceGroup en $Region"
    Invoke-Az -Arguments @('group', 'create', '--name', $ResourceGroup, '--location', $Region, '--only-show-errors') | Out-Null

    $parametros = [ordered]@{
        '$schema'      = 'https://schema.management.azure.com/schemas/2019-04-01/deploymentParameters.json#'
        contentVersion = '1.0.0.0'
        parameters     = [ordered]@{
            location           = @{ value = $Region }
            namePrefix         = @{ value = 'sos-clinic' }
            dnsLabel           = @{ value = $Dns }
            acrName            = @{ value = $Acr }
            storageName        = @{ value = $Storage }
            backupContainerName = @{ value = $Container }
            vmSize             = @{ value = $Size }
            adminUsername      = @{ value = $AdminUser }
            sshPublicKey       = @{ value = $SshPublicKey }
            adminSourcePrefix  = @{ value = $SourcePrefix }
            alertEmail         = @{ value = $BudgetEmail }
            budgetAmount       = @{ value = $BudgetAmount }
            autoShutdownTime   = @{ value = $ShutdownTime }
            trialEndDate       = @{ value = $TrialEnd }
        }
    }

    $temporal = Join-Path $script:AzureDir '.deploy-tmp'
    New-Item -ItemType Directory -Force -Path $temporal | Out-Null
    $archivoParametros = Join-Path $temporal 'parameters.json'
    Write-JsonFile -Path $archivoParametros -Object $parametros

    $nombreDespliegue = 'sos-clinic-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
    Write-Step "Desplegando Bicep ($nombreDespliegue). Esto puede tardar varios minutos"
    try {
        $resultado = Invoke-Az -Arguments @(
            'deployment', 'group', 'create',
            '--resource-group', $ResourceGroup,
            '--name', $nombreDespliegue,
            '--template-file', $script:TemplatePath,
            '--parameters', "@$archivoParametros",
            '--only-show-errors',
            '-o', 'json'
        )
        $despliegue = $resultado.Output | ConvertFrom-Json
        $salidas = $despliegue.properties.outputs
    } finally {
        Remove-Item -LiteralPath $archivoParametros -Force -ErrorAction SilentlyContinue
    }

    return [pscustomobject]@{
        Location       = $Region
        VmSize         = $Size
        Fqdn           = $salidas.fqdn.value
        PublicIp       = $salidas.publicIp.value
        VmName         = $salidas.vmName.value
        AcrName        = $salidas.acrName.value
        AcrLoginServer = $salidas.acrLoginServer.value
        StorageAccount = $salidas.storageAccount.value
        BackupContainer = $salidas.backupContainer.value
        NsgName        = $salidas.nsgName.value
        AdminUsername  = $salidas.adminUsername.value
        SshCommand     = $salidas.sshCommand.value
        DeploymentName = $nombreDespliegue
    }
}

function Invoke-DatabaseSetup {
    param(
        [string]$KeyPath,
        [string]$User,
        [string]$VmHost,
        [bool]$FirstDeploy,
        [bool]$ForcePostgres,
        [bool]$ForceAnalytics
    )

    $compose = 'sudo docker compose --env-file /opt/sos-clinic/.env -f /opt/sos-clinic/infra/azure/docker-compose.azure.yml'

    Write-Step 'Levantando PostgreSQL y MySQL'
    Invoke-Remote -KeyPath $KeyPath -User $User -VmHost $VmHost -Command "$compose up -d --wait --wait-timeout 240 postgres mysql" | Out-Null
    Write-Ok 'Bases de datos healthy'

    if ($FirstDeploy -or $ForcePostgres) {
        Write-Step 'Aplicando migraciones y seed canonico de PostgreSQL'
        Invoke-Remote -KeyPath $KeyPath -User $User -VmHost $VmHost -Command @'
set -e
cd /opt/sos-clinic
sudo env SOS_COMPOSE_FILE=/opt/sos-clinic/infra/azure/docker-compose.azure.yml \
  python3 infra/seed/reset_seed.py --yes --skip-backup
'@ | Out-Null
        Write-Ok 'PostgreSQL migrado y sembrado'
    }

    if ($FirstDeploy -or $ForceAnalytics) {
        Write-Step 'Recreando MySQL de analitica y cargando 50.000 llegadas'
        Invoke-Remote -KeyPath $KeyPath -User $User -VmHost $VmHost -Command @'
set -e
cd /opt/sos-clinic
# .env es 600 root; leer la clave con sudo en vez de hacer source como azureuser.
# Sin comillas dobles: PowerShell 5.1 las elimina al pasar el comando a ssh.
ANALITICA_DB_PASSWORD=$(sudo grep '^ANALITICA_DB_PASSWORD=' /opt/sos-clinic/.env | head -n1 | cut -d= -f2- | tr -d \' | tr -d '\r')
test -n "$ANALITICA_DB_PASSWORD"
sudo docker exec -e MYSQL_PWD=$ANALITICA_DB_PASSWORD sos-mysql \
  mysql -uroot -e 'DROP DATABASE IF EXISTS urgencias_hospital;'
sudo docker exec -i -e MYSQL_PWD=$ANALITICA_DB_PASSWORD sos-mysql \
  mysql -uroot < bd_local.sql
'@ | Out-Null
        Invoke-Remote -KeyPath $KeyPath -User $User -VmHost $VmHost -Command "$compose --profile seed run --rm analitica-seed" | Out-Null
        Write-Ok 'MySQL de analitica sembrado'
    }
}

function Start-SosStack {
    param(
        [string]$KeyPath,
        [string]$User,
        [string]$VmHost
    )
    Write-Step 'Arrancando el stack con systemd'
    Invoke-Remote -KeyPath $KeyPath -User $User -VmHost $VmHost -Command @'
set -e
sudo systemctl enable sos-clinic.service
sudo systemctl restart sos-clinic.service
# Releer Caddyfile tras un update. Los archivos estaticos ya se sirven por bind mount.
sudo docker restart sos-caddy >/dev/null
'@ | Out-Null
    Wait-RemoteHttp -KeyPath $KeyPath -User $User -VmHost $VmHost `
        -Url 'http://127.0.0.1:8080/ms-seguridad/auth/politica-clave' -TimeoutSeconds 600
    Write-Ok 'Gateway y ms-seguridad responden'
}

function Download-SeedCredentials {
    param(
        [string]$KeyPath,
        [string]$User,
        [string]$VmHost
    )
    $destino = Join-Path $script:AzureDir '.credenciales.azure.md'
    try {
        # Las credenciales quedan en 600 root en la VM; scp como azureuser no
        # puede leerlas. sudo cat evita cambiar el propietario del archivo.
        $resultado = Invoke-Remote -KeyPath $KeyPath -User $User -VmHost $VmHost `
            -Command 'sudo cat /opt/sos-clinic/infra/seed/credenciales.local.md' -AllowFailure
        if ($resultado.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($resultado.Output)) {
            throw "El archivo de credenciales no existe o esta vacio.`n$($resultado.Output)"
        }
        $contenido = $resultado.Output.TrimEnd() + "`n"
        $utf8 = New-Object System.Text.UTF8Encoding -ArgumentList $false
        [System.IO.File]::WriteAllText($destino, $contenido, $utf8)
        if ($env:OS -eq 'Windows_NT') {
            $identidad = if ($env:USERDOMAIN) { "$env:USERDOMAIN\$env:USERNAME" } else { $env:USERNAME }
            & icacls $destino /inheritance:r /grant:r "${identidad}:F" *> $null
        }
        Write-Ok "Credenciales del seed descargadas en $destino"
    } catch {
        Write-Warn "No se pudieron descargar las credenciales del seed: $($_.Exception.Message)"
    }
}

# ---------------------------------------------------------------------------
# Validaciones iniciales
# ---------------------------------------------------------------------------

Write-Host '============================================================' -ForegroundColor Cyan
Write-Host ' SOS Clinic - despliegue Azure (stack completo)' -ForegroundColor Cyan
Write-Host '============================================================' -ForegroundColor Cyan

Assert-Command -Name 'az' -Hint 'Instala Azure CLI: winget install Microsoft.AzureCLI'
Assert-Command -Name 'docker' -Hint 'Abre Docker Desktop.'
Assert-Command -Name 'ssh'
Assert-Command -Name 'scp'
Assert-Command -Name 'git'

$cuenta = Get-AzAccount
Write-Host ""
Write-Host "Suscripcion: $($cuenta.name)" -ForegroundColor Gray
Write-Host "Usuario:     $($cuenta.user.name)" -ForegroundColor Gray
Write-Host "Tenant:      $($cuenta.tenantId)" -ForegroundColor Gray

if (-not $TrialEndDate) {
    $TrialEndDate = (Get-Date).AddDays(30).ToString('yyyy-MM-dd')
}

if (-not $AlertEmail) {
    if ($cuenta.user.name -match '@') { $AlertEmail = $cuenta.user.name }
}

$estado = Get-DeploymentState
$primerDespliegue = $true
$Region = $Location
$Size = $VmSize
$Dns = $DnsLabel
$Acr = $AcrName
$Storage = $StorageName
$Fqdn = ''
$PublicIp = ''
$VmName = ''
$AcrLoginServer = ''
$BackupContainer = 'sos-clinic-backups'
$NsgName = ''

if ($SkipInfra) {
    if (-not $estado) {
        throw "No existe $script:StatePath. No puedo usar -SkipInfra sin un despliegue previo."
    }
    $ResourceGroup = $estado.resourceGroup
    $Region = $estado.location
    $Size = $estado.vmSize
    $Dns = $estado.dnsLabel
    $Acr = $estado.acrName
    $AcrLoginServer = $estado.acrLoginServer
    $Storage = $estado.storageAccount
    $BackupContainer = $estado.backupContainer
    $NsgName = $estado.nsgName
    $Fqdn = $estado.fqdn
    $PublicIp = $estado.publicIp
    $VmName = $estado.vmName
    $AdminUsername = $estado.adminUsername
    if (-not $PSBoundParameters.ContainsKey('SshKeyPath') -and $estado.sshKeyPath) {
        $SshKeyPath = $estado.sshKeyPath
    }
    $primerDespliegue = -not [bool]$estado.seeded
    Write-Step "Reutilizando despliegue existente: $Fqdn ($Region/$Size)"
} else {
    if ($estado) {
        Write-Warn "Ya existe un estado local ($script:StatePath). Si quieres reutilizarlo usa -SkipInfra; si quieres crear otro, mueve ese archivo."
        throw 'Despliegue detenido para no duplicar recursos. Usa -SkipInfra o elimina/mueve .deployment.json.'
    }

    $argumentosPlacement = @{ RequestedLocation = $Location }
    if ($PSBoundParameters.ContainsKey('VmSize')) {
        $argumentosPlacement.RequestedSize = $VmSize
    }
    Write-Step 'Verificando proveedores de Azure necesarios'
    foreach ($proveedor in @(
            'Microsoft.Compute',
            'Microsoft.Network',
            'Microsoft.Storage',
            'Microsoft.ContainerRegistry',
            'Microsoft.DevTestLab',
            'Microsoft.Consumption'
        )) {
        Ensure-Provider -Namespace $proveedor
    }
    Write-Ok 'Proveedores listos'

    $colocacion = Select-VmPlacement @argumentosPlacement
    $Region = $colocacion.Location
    $Size = $colocacion.VmSize
    if ($colocacion.Notes) { Write-Warn $colocacion.Notes }
    Write-Ok "Placement: $Size en $Region"

    $nombres = Resolve-ResourceNames -Region $Region -RequestedDnsLabel $DnsLabel -RequestedAcr $AcrName -RequestedStorage $StorageName
    $Dns = $nombres.DnsLabel
    $Acr = $nombres.AcrName
    $Storage = $nombres.StorageName
    $BackupContainer = $nombres.BackupContainer
    $Fqdn = "$Dns.$Region.cloudapp.azure.com"
    Write-Ok "DNS: $Fqdn"
}

$AdminIp = if ($AdminSourceIp) { $AdminSourceIp } else { Get-PublicIp }
$null = [System.Net.IPAddress]::Parse($AdminIp)
$adminCidr = "$AdminIp/32"

if ($SkipInfra) {
    $publica = "$SshKeyPath.pub"
    if (-not (Test-Path -LiteralPath $SshKeyPath) -or -not (Test-Path -LiteralPath $publica)) {
        throw "No encuentro la llave SSH del despliegue: $SshKeyPath(.pub). Restaurala antes de continuar."
    }
    $sshPublicKey = (Get-Content -LiteralPath $publica -Raw).Trim()
} else {
    $sshPublicKey = Ensure-SshKey -Path $SshKeyPath
}

# ---------------------------------------------------------------------------
# Infraestructura
# ---------------------------------------------------------------------------

if (-not $SkipInfra) {
    $infra = Deploy-Infrastructure `
        -Region $Region `
        -Size $Size `
        -Dns $Dns `
        -Acr $Acr `
        -Storage $Storage `
        -Container $BackupContainer `
        -SshPublicKey $sshPublicKey `
        -AdminUser $AdminUsername `
        -SourcePrefix $adminCidr `
        -BudgetEmail $AlertEmail `
        -TrialEnd $TrialEndDate `
        -ShutdownTime $AutoShutdownTime

    $Fqdn = $infra.Fqdn
    $PublicIp = $infra.PublicIp
    $VmName = $infra.VmName
    $Acr = $infra.AcrName
    $AcrLoginServer = $infra.AcrLoginServer
    $Storage = $infra.StorageAccount
    $BackupContainer = $infra.BackupContainer
    $NsgName = $infra.NsgName
    $AdminUsername = $infra.AdminUsername
    $primerDespliegue = $true

    $estado = [ordered]@{
        resourceGroup   = $ResourceGroup
        location        = $Region
        dnsLabel        = $Dns
        fqdn            = $Fqdn
        publicIp        = $PublicIp
        vmName          = $VmName
        vmSize          = $Size
        acrName         = $Acr
        acrLoginServer  = $AcrLoginServer
        storageAccount  = $Storage
        backupContainer = $BackupContainer
        nsgName         = $NsgName
        adminUsername   = $AdminUsername
        sshKeyPath      = $SshKeyPath
        adminSourceIp   = $AdminIp
        trialEndDate    = $TrialEndDate
        autoShutdown    = $AutoShutdownTime
        deployedAt      = (Get-Date).ToString('o')
        imageTag        = ''
        seeded          = $false
    }
    Save-DeploymentState -State $estado
    Write-Ok "Estado guardado en $script:StatePath"
}

if (-not $VmName) {
    throw 'No se pudo determinar el nombre de la VM. Revisa .deployment.json.'
}
if ($AdminUsername -ne 'azureuser') {
    throw "cloud-init esta preparado para el usuario 'azureuser'; no uses -AdminUsername $AdminUsername."
}

Wait-Ssh -KeyPath $SshKeyPath -User $AdminUsername -VmHost $Fqdn -TimeoutSeconds 900
Wait-CloudInit -KeyPath $SshKeyPath -User $AdminUsername -VmHost $Fqdn -TimeoutSeconds 1200
Set-AdminSshRule -ResourceGroup $ResourceGroup -NsgName $NsgName -SourceIp $AdminIp

# ---------------------------------------------------------------------------
# Imagenes
# ---------------------------------------------------------------------------

$envExistente = Read-DotEnv -Path $script:AzureEnvPath
if ($SkipBuild) {
    $ImageTag = if ($envExistente['IMAGE_TAG']) { $envExistente['IMAGE_TAG'] } elseif ($estado.imageTag) { $estado.imageTag } else { '' }
    if (-not $ImageTag) {
        throw 'No hay IMAGE_TAG en .env.azure ni en .deployment.json. Ejecuta sin -SkipBuild.'
    }
    Write-Warn "Se reutiliza IMAGE_TAG=$ImageTag; no se reconstruyen imagenes."
} else {
    $ImageTag = New-ImageTag
}

if (-not $SkipBuild) {
    Write-Step "Autenticando Docker contra $Acr"
    Invoke-Az -Arguments @('acr', 'login', '--name', $Acr, '--only-show-errors') | Out-Null
    Write-Ok 'Docker autenticado contra ACR'
    Build-AndPushImages -AcrLoginServer $AcrLoginServer -Tag $ImageTag
}

if (-not $AcmeEmail) {
    $AcmeEmail = if ($envExistente['ACME_EMAIL']) { $envExistente['ACME_EMAIL'] } else { $envExistente['MAIL_USERNAME'] }
}
if (-not $AcmeEmail) {
    $localEnv = Read-DotEnv -Path (Join-Path $script:RepoRoot '.env')
    $AcmeEmail = $localEnv['MAIL_USERNAME']
}
if (-not $AcmeEmail -or $AcmeEmail -notmatch '@') {
    throw 'Indica -AcmeEmail con un correo valido para Let''s Encrypt.'
}

New-AzureEnvFile -State ([pscustomobject]@{
        fqdn            = $Fqdn
        acrName         = $Acr
        acrLoginServer  = $AcrLoginServer
        storageAccount  = $Storage
        backupContainer = $BackupContainer
    }) -ImageTag $ImageTag -AcmeEmailAddress $AcmeEmail -SyncLocalSecrets:$SyncLocalSecrets | Out-Null

$estado.imageTag = $ImageTag
$estado.adminSourceIp = $AdminIp
Save-DeploymentState -State $estado

if ($SkipSource -and $primerDespliegue) {
    throw '-SkipSource no es valido en el primer despliegue: la VM todavia no tiene /opt/sos-clinic.'
}

Send-DeploymentFiles -KeyPath $SshKeyPath -User $AdminUsername -VmHost $Fqdn `
    -EnvFile $script:AzureEnvPath -SkipSource:$SkipSource

# ---------------------------------------------------------------------------
# Datos y arranque
# ---------------------------------------------------------------------------

if (($Reseed -or $ReseedAnalytics) -and -not $NoStart) {
    Write-Step 'Deteniendo el stack antes de recargar datos'
    Invoke-Remote -KeyPath $SshKeyPath -User $AdminUsername -VmHost $Fqdn `
        -Command 'sudo systemctl stop sos-clinic.service || true' -AllowFailure | Out-Null
}

if (-not $SkipSeed) {
    Invoke-DatabaseSetup -KeyPath $SshKeyPath -User $AdminUsername -VmHost $Fqdn `
        -FirstDeploy $primerDespliegue -ForcePostgres:$Reseed -ForceAnalytics:($ReseedAnalytics -or $primerDespliegue)
    $estado.seeded = $true
    Save-DeploymentState -State $estado
} else {
    Write-Warn 'Seed omitido por -SkipSeed. La base puede quedar vacia.'
}

$smokeOk = $true
if (-not $NoStart) {
    Start-SosStack -KeyPath $SshKeyPath -User $AdminUsername -VmHost $Fqdn

    if (-not $SkipVerify) {
        Write-Step 'Verificando el seed por API (9 cuentas, 2FA y roles)'
        $verificacion = Invoke-Remote -KeyPath $SshKeyPath -User $AdminUsername -VmHost $Fqdn -Command @'
set -e
cd /opt/sos-clinic
sudo python3 infra/seed/verify_seed.py --base-url http://127.0.0.1:8080
'@ -AllowFailure
        if ($verificacion.ExitCode -ne 0) {
            $smokeOk = $false
            Write-Warn "La verificacion del seed fallo:`n$($verificacion.Output)"
        } else {
            Write-Host $verificacion.Output
            Write-Ok 'Seed verificado'
        }
    }

    Download-SeedCredentials -KeyPath $SshKeyPath -User $AdminUsername -VmHost $Fqdn

    try {
        Write-Step "Esperando HTTPS y certificado de Let's Encrypt en https://$Fqdn"
        Wait-PublicHttps -Fqdn $Fqdn -TimeoutSeconds 420
        Write-Ok 'HTTPS responde'
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'smoke-test.ps1') -Domain $Fqdn
        if ($LASTEXITCODE -ne 0) { $smokeOk = $false }
    } catch {
        $smokeOk = $false
        Write-Warn $_.Exception.Message
        Write-Host "  Revisa: ssh -i `"$SshKeyPath`" $AdminUsername@$Fqdn 'sudo docker logs --tail 120 sos-caddy'" -ForegroundColor DarkGray
    }
} else {
    Write-Warn 'Stack no arrancado por -NoStart.'
}

Write-Host ''
Write-Host '============================================================' -ForegroundColor Cyan
Write-Host ' Despliegue terminado' -ForegroundColor Cyan
Write-Host '============================================================' -ForegroundColor Cyan
Write-Host "URL:            https://$Fqdn"
Write-Host "SSH:            ssh -i `"$SshKeyPath`" $AdminUsername@$Fqdn"
Write-Host "VM:             $VmName ($Size, $Region)"
Write-Host "ACR:            $AcrLoginServer"
Write-Host "Storage:        $Storage / $BackupContainer"
Write-Host "Auto-shutdown:  $AutoShutdownTime hora Bogota"
Write-Host "Trial hasta:    $TrialEndDate"
Write-Host "Estado local:   $script:StatePath"
Write-Host "Credenciales:   $script:AzureEnvPath"
if (Test-Path -LiteralPath (Join-Path $script:AzureDir '.credenciales.azure.md')) {
    Write-Host "Seed:           $(Join-Path $script:AzureDir '.credenciales.azure.md')"
}
Write-Host ''
Write-Host 'Operacion:' -ForegroundColor Gray
Write-Host '  .\infra\azure\start.ps1' -ForegroundColor Gray
Write-Host '  .\infra\azure\stop.ps1' -ForegroundColor Gray
Write-Host '  .\infra\azure\update.ps1' -ForegroundColor Gray
Write-Host '  .\infra\azure\smoke-test.ps1 -Domain ' + $Fqdn -ForegroundColor Gray
Write-Host ''
Write-Host 'Aviso: apaga la VM fuera de la demo y exporta backups antes del fin del trial.' -ForegroundColor Yellow

if (-not $smokeOk) {
    exit 1
}
exit 0
