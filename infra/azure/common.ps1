# Funciones compartidas por los scripts de infra/azure.
# Compatible con Windows PowerShell 5.1 y PowerShell 7.

$ErrorActionPreference = 'Stop'
try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
} catch {
    # PowerShell 7 ya usa TLS moderno.
}

$script:RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$script:AzureDir = $PSScriptRoot
$script:StatePath = Join-Path $AzureDir '.deployment.json'
$script:AzureEnvPath = Join-Path $AzureDir '.env.azure'
$script:TemplatePath = Join-Path $AzureDir 'main.bicep'
$script:Modules = @(
    'eureka-server',
    'ms-gateway',
    'ms-seguridad',
    'ms-pacientes',
    'ms-triage',
    'ms-analitica',
    'ms-ia-agente'
)

function Write-Step {
    param([Parameter(Mandatory = $true)][string]$Message)
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Write-Ok {
    param([Parameter(Mandatory = $true)][string]$Message)
    Write-Host "OK  $Message" -ForegroundColor Green
}

function Write-Warn {
    param([Parameter(Mandatory = $true)][string]$Message)
    Write-Host "AVISO  $Message" -ForegroundColor Yellow
}

function Write-Err {
    param([Parameter(Mandatory = $true)][string]$Message)
    Write-Host "ERROR  $Message" -ForegroundColor Red
}

function Assert-Command {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [string]$Hint = ''
    )
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        $mensaje = "No se encontro '$Name' en PATH."
        if ($Hint) { $mensaje += " $Hint" }
        throw $mensaje
    }
}

function Invoke-Az {
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [switch]$AllowFailure
    )

    $anterior = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $salida = & az @Arguments 2>&1
        $codigo = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $anterior
    }

    $texto = ($salida | ForEach-Object { $_.ToString() }) -join "`n"
    if ($codigo -ne 0 -and -not $AllowFailure) {
        throw "az $($Arguments -join ' ') fallo (codigo $codigo).`n$texto"
    }

    return [pscustomobject]@{
        ExitCode = $codigo
        Output   = $texto
    }
}

function Get-AzJson {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $resultado = Invoke-Az -Arguments $Arguments
    if ([string]::IsNullOrWhiteSpace($resultado.Output)) {
        return $null
    }
    try {
        return $resultado.Output | ConvertFrom-Json
    } catch {
        throw "az devolvio JSON invalido para '$($Arguments -join ' ')'.`n$($resultado.Output)"
    }
}

function Get-AzAccount {
    $cuenta = Get-AzJson -Arguments @('account', 'show', '--only-show-errors', '-o', 'json')
    if ($null -eq $cuenta) {
        throw "No hay sesion de Azure. Ejecuta: az login"
    }
    return $cuenta
}

function Read-DotEnv {
    param([Parameter(Mandatory = $true)][string]$Path)

    $valores = @{}
    if (-not (Test-Path -LiteralPath $Path)) {
        return $valores
    }

    foreach ($linea in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $texto = $linea.Trim()
        if (-not $texto -or $texto.StartsWith('#') -or -not $texto.Contains('=')) {
            continue
        }
        $partes = $texto.Split('=', 2)
        $clave = $partes[0].Trim()
        $valor = $partes[1].Trim()
        if ($clave.StartsWith('export ')) {
            $clave = $clave.Substring(7).Trim()
        }
        if ($valor.Length -ge 2 -and (($valor[0] -eq "'" -and $valor[-1] -eq "'") -or ($valor[0] -eq '"' -and $valor[-1] -eq '"'))) {
            $valor = $valor.Substring(1, $valor.Length - 2)
        }
        $valores[$clave] = $valor
    }
    return $valores
}

function Write-DotEnv {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Values
    )

    $lineas = New-Object System.Collections.Generic.List[string]
    $lineas.Add('# Generado por infra/azure/deploy.ps1. No versionar ni compartir.')
    $lineas.Add('# Permisos esperados en la VM: 600, propietario root.')
    $lineas.Add('')
    foreach ($clave in $Values.Keys) {
        $valor = [string]$Values[$clave]
        if ($valor -match "[`r`n]") {
            throw "El valor de $clave contiene saltos de linea."
        }
        if ($valor.Contains("'")) {
            throw "El valor de $clave contiene una comilla simple; no se puede escribir en .env.azure sin escaping."
        }
        $lineas.Add("$clave='$valor'")
    }

    $utf8 = New-Object System.Text.UTF8Encoding -ArgumentList $false
    [System.IO.File]::WriteAllLines($Path, $lineas, $utf8)

    if ($env:OS -eq 'Windows_NT') {
        $identidad = if ($env:USERDOMAIN) { "$env:USERDOMAIN\$env:USERNAME" } else { $env:USERNAME }
        & icacls $Path /inheritance:r /grant:r "${identidad}:F" *> $null
    }
}

function New-RandomHex {
    param([int]$Bytes = 32)
    $buffer = New-Object byte[] $Bytes
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($buffer)
    } finally {
        $rng.Dispose()
    }
    return ([BitConverter]::ToString($buffer) -replace '-', '').ToLowerInvariant()
}

function Get-PublicIp {
    $urls = @(
        'https://api.ipify.org?format=json',
        'https://ifconfig.me/ip',
        'https://icanhazip.com'
    )
    foreach ($url in $urls) {
        try {
            $respuesta = Invoke-RestMethod -Uri $url -TimeoutSec 15
            $candidato = if ($respuesta -is [string]) { $respuesta.Trim() } elseif ($respuesta.IP) { [string]$respuesta.IP } else { [string]$respuesta }
            $ip = $null
            if ([System.Net.IPAddress]::TryParse($candidato.Trim(), [ref]$ip) -and
                $ip.AddressFamily -eq [System.Net.Sockets.AddressFamily]::InterNetwork) {
                return $ip.ToString()
            }
        } catch {
            continue
        }
    }
    throw "No se pudo detectar la IP publica. Indica -AdminSourceIp manualmente."
}

function Get-RepoCommit {
    $commit = & git -C $script:RepoRoot rev-parse --short HEAD 2>$null
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($commit)) {
        return 'nogit'
    }
    return $commit.Trim()
}

function New-ImageTag {
    return ('{0}-{1}' -f (Get-Date -Format 'yyyyMMdd-HHmmss'), (Get-RepoCommit))
}

function Get-DeploymentState {
    if (-not (Test-Path -LiteralPath $script:StatePath)) {
        return $null
    }
    return Get-Content -LiteralPath $script:StatePath -Raw -Encoding UTF8 | ConvertFrom-Json
}

function Save-DeploymentState {
    param([Parameter(Mandatory = $true)]$State)
    $json = $State | ConvertTo-Json -Depth 8
    $utf8 = New-Object System.Text.UTF8Encoding -ArgumentList $false
    [System.IO.File]::WriteAllText($script:StatePath, $json, $utf8)
}

function Invoke-Remote {
    param(
        [Parameter(Mandatory = $true)][string]$KeyPath,
        [Parameter(Mandatory = $true)][string]$User,
        [Parameter(Mandatory = $true)][string]$VmHost,
        [Parameter(Mandatory = $true)][string]$Command,
        [switch]$AllowFailure
    )

    $destino = '{0}@{1}' -f $User, $VmHost
    $argumentos = @(
        '-i', $KeyPath,
        '-o', 'BatchMode=yes',
        '-o', 'StrictHostKeyChecking=accept-new',
        '-o', 'ConnectTimeout=15',
        '-o', 'ServerAliveInterval=30',
        '-o', 'ServerAliveCountMax=20',
        $destino,
        $Command
    )

    $anterior = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $salida = & ssh @argumentos 2>&1
        $codigo = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $anterior
    }

    $texto = ($salida | ForEach-Object { $_.ToString() }) -join "`n"
    if ($codigo -ne 0 -and -not $AllowFailure) {
        throw "Fallo SSH en $destino.`n$texto"
    }
    return [pscustomobject]@{
        ExitCode = $codigo
        Output   = $texto
    }
}

function Copy-Remote {
    param(
        [Parameter(Mandatory = $true)][string]$KeyPath,
        [Parameter(Mandatory = $true)][string]$User,
        [Parameter(Mandatory = $true)][string]$VmHost,
        [Parameter(Mandatory = $true)][string]$LocalPath,
        [Parameter(Mandatory = $true)][string]$RemotePath
    )

    $destino = '{0}@{1}:{2}' -f $User, $VmHost, $RemotePath
    $anterior = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $salida = & scp -q -i $KeyPath -o BatchMode=yes -o StrictHostKeyChecking=accept-new $LocalPath $destino 2>&1
        $codigo = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $anterior
    }
    if ($codigo -ne 0) {
        $texto = ($salida | ForEach-Object { $_.ToString() }) -join "`n"
        throw "Fallo scp hacia $destino.`n$texto"
    }
}

function Copy-FromRemote {
    param(
        [Parameter(Mandatory = $true)][string]$KeyPath,
        [Parameter(Mandatory = $true)][string]$User,
        [Parameter(Mandatory = $true)][string]$VmHost,
        [Parameter(Mandatory = $true)][string]$RemotePath,
        [Parameter(Mandatory = $true)][string]$LocalPath
    )

    $origen = '{0}@{1}:{2}' -f $User, $VmHost, $RemotePath
    $directorio = Split-Path -Parent $LocalPath
    if ($directorio) {
        New-Item -ItemType Directory -Force -Path $directorio | Out-Null
    }

    $anterior = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $salida = & scp -q -i $KeyPath -o BatchMode=yes -o StrictHostKeyChecking=accept-new $origen $LocalPath 2>&1
        $codigo = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $anterior
    }
    if ($codigo -ne 0) {
        $texto = ($salida | ForEach-Object { $_.ToString() }) -join "`n"
        throw "Fallo scp desde $origen.`n$texto"
    }
}

function Wait-Ssh {
    param(
        [Parameter(Mandatory = $true)][string]$KeyPath,
        [Parameter(Mandatory = $true)][string]$User,
        [Parameter(Mandatory = $true)][string]$VmHost,
        [int]$TimeoutSeconds = 600
    )

    $limite = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $limite) {
        $resultado = Invoke-Remote -KeyPath $KeyPath -User $User -VmHost $VmHost -Command 'true' -AllowFailure
        if ($resultado.ExitCode -eq 0) {
            return
        }
        Start-Sleep -Seconds 10
    }
    throw "La VM no acepto SSH en $TimeoutSeconds segundos ($VmHost)."
}

function Wait-CloudInit {
    param(
        [Parameter(Mandatory = $true)][string]$KeyPath,
        [Parameter(Mandatory = $true)][string]$User,
        [Parameter(Mandatory = $true)][string]$VmHost,
        [int]$TimeoutSeconds = 900
    )

    Write-Step 'Esperando a que cloud-init termine (Docker, Azure CLI y swap)'
    $limite = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $limite) {
        $estado = Invoke-Remote -KeyPath $KeyPath -User $User -VmHost $VmHost `
            -Command 'test -f /var/lib/cloud/instance/boot-finished && command -v docker >/dev/null && command -v az >/dev/null && docker compose version >/dev/null 2>&1' `
            -AllowFailure
        if ($estado.ExitCode -eq 0) {
            Write-Ok 'cloud-init termino y Docker Compose esta disponible'
            return
        }
        Start-Sleep -Seconds 15
    }
    throw "cloud-init no termino en $TimeoutSeconds segundos. Revisa /var/log/cloud-init-output.log en la VM."
}

function Wait-RemoteHttp {
    param(
        [Parameter(Mandatory = $true)][string]$KeyPath,
        [Parameter(Mandatory = $true)][string]$User,
        [Parameter(Mandatory = $true)][string]$VmHost,
        [Parameter(Mandatory = $true)][string]$Url,
        [int]$TimeoutSeconds = 600
    )

    $limite = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $limite) {
        $resultado = Invoke-Remote -KeyPath $KeyPath -User $User -VmHost $VmHost `
            -Command ("curl -fsS -o /dev/null --max-time 10 '{0}'" -f $Url) -AllowFailure
        if ($resultado.ExitCode -eq 0) {
            return
        }
        Start-Sleep -Seconds 10
    }
    throw "El endpoint $Url no respondio en $TimeoutSeconds segundos."
}

function Wait-PublicHttps {
    param(
        [Parameter(Mandatory = $true)][string]$Fqdn,
        [int]$TimeoutSeconds = 420
    )

    $limite = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $limite) {
        try {
            $respuesta = Invoke-WebRequest -Uri "https://$Fqdn/" -UseBasicParsing -TimeoutSec 20
            if ($respuesta.StatusCode -eq 200) {
                return
            }
        } catch {
            # Caddy puede estar emitiendo el certificado o esperando DNS.
        }
        Start-Sleep -Seconds 10
    }
    throw "HTTPS no respondio en $Fqdn. Revisa el DNS y: ssh ... 'sudo docker logs --tail 100 sos-caddy'"
}

function Set-AdminSshRule {
    param(
        [Parameter(Mandatory = $true)][string]$ResourceGroup,
        [Parameter(Mandatory = $true)][string]$NsgName,
        [Parameter(Mandatory = $true)][string]$SourceIp
    )

    $cidr = "$SourceIp/32"
    Invoke-Az -Arguments @(
        'network', 'nsg', 'rule', 'update',
        '--resource-group', $ResourceGroup,
        '--nsg-name', $NsgName,
        '--name', 'AllowSSH',
        '--source-address-prefixes', $cidr,
        '--only-show-errors'
    ) | Out-Null
    Write-Ok "Regla SSH actualizada para $cidr"
}

function Build-AndPushImages {
    param(
        [Parameter(Mandatory = $true)][string]$AcrLoginServer,
        [Parameter(Mandatory = $true)][string]$Tag,
        [string[]]$Modules = $script:Modules
    )

    Assert-Command -Name 'docker' -Hint 'Abre Docker Desktop.'
    $dockerfile = Join-Path $script:AzureDir 'Dockerfile.service'
    if (-not (Test-Path -LiteralPath $dockerfile)) {
        throw "No existe $dockerfile"
    }

    $anterior = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & docker buildx version *> $null
        $tieneBuildx = ($LASTEXITCODE -eq 0)
    } finally {
        $ErrorActionPreference = $anterior
    }

    foreach ($modulo in $Modules) {
        $imagen = '{0}/sos-clinic/{1}:{2}' -f $AcrLoginServer, $modulo, $Tag
        Write-Step "Construyendo y publicando $modulo"
        Write-Host "  $imagen" -ForegroundColor DarkGray

        if ($tieneBuildx) {
            & docker buildx build `
                --platform linux/amd64 `
                --provenance=false `
                --sbom=false `
                --build-arg "MODULE=$modulo" `
                -f $dockerfile `
                -t $imagen `
                --push `
                $script:RepoRoot
            $codigo = $LASTEXITCODE
        } else {
            Write-Warn 'buildx no esta disponible; se usa docker build + docker push.'
            & docker build --build-arg "MODULE=$modulo" -f $dockerfile -t $imagen $script:RepoRoot
            $codigo = $LASTEXITCODE
            if ($codigo -eq 0) {
                & docker push $imagen
                $codigo = $LASTEXITCODE
            }
        }

        if ($codigo -ne 0) {
            throw "Fallo la construccion/publicacion de $modulo."
        }
        Write-Ok "$modulo publicado"
    }
}

function Send-DeploymentFiles {
    param(
        [Parameter(Mandatory = $true)][string]$KeyPath,
        [Parameter(Mandatory = $true)][string]$User,
        [Parameter(Mandatory = $true)][string]$VmHost,
        [Parameter(Mandatory = $true)][string]$EnvFile,
        [switch]$SkipSource
    )

    if (-not $SkipSource) {
        Write-Step 'Empaquetando el commit actual y subiendolo a la VM'
        Assert-Command -Name 'git'
        $temporal = Join-Path $env:TEMP 'sos-clinic-deploy'
        New-Item -ItemType Directory -Force -Path $temporal | Out-Null
        $archivo = Join-Path $temporal 'sos-clinic-src.tar.gz'
        if (Test-Path -LiteralPath $archivo) {
            Remove-Item -LiteralPath $archivo -Force
        }

        & git -C $script:RepoRoot archive --format=tar.gz --output=$archivo HEAD
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $archivo)) {
            throw 'No se pudo crear el archivo con git archive. Haz commit de los cambios antes de desplegar.'
        }

        Copy-Remote -KeyPath $KeyPath -User $User -VmHost $VmHost -LocalPath $archivo -RemotePath '/tmp/sos-clinic-src.tar.gz'
        $resultado = Invoke-Remote -KeyPath $KeyPath -User $User -VmHost $VmHost -Command @'
set -e
sudo mkdir -p /opt/sos-clinic
sudo tar -xzf /tmp/sos-clinic-src.tar.gz -C /opt/sos-clinic
sudo rm -f /tmp/sos-clinic-src.tar.gz
sudo chown -R azureuser:azureuser /opt/sos-clinic
'@
        if ($resultado.ExitCode -ne 0) {
            throw "No se pudo extraer el codigo en la VM.`n$($resultado.Output)"
        }
        Write-Ok 'Codigo actualizado en /opt/sos-clinic'
    }

    Write-Step 'Instalando .env con permisos 600 en la VM'
    Copy-Remote -KeyPath $KeyPath -User $User -VmHost $VmHost -LocalPath $EnvFile -RemotePath '/tmp/sos-clinic.env'
    $resultado = Invoke-Remote -KeyPath $KeyPath -User $User -VmHost $VmHost -Command @'
set -e
sudo install -m 600 -o root -g root /tmp/sos-clinic.env /opt/sos-clinic/.env
sudo rm -f /tmp/sos-clinic.env
test -s /opt/sos-clinic/.env
'@
    if ($resultado.ExitCode -ne 0) {
        throw "No se pudo instalar .env en la VM.`n$($resultado.Output)"
    }
    Write-Ok '.env instalado'
}
