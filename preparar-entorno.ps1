[CmdletBinding()]
param(
    [string]$Repo = "",
    [switch]$SkipSeed
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($Repo)) {
    $Repo = $PSScriptRoot
}

function New-Secreto {
    param([int]$Bytes = 24)
    $buffer = [byte[]]::new($Bytes)
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($buffer)
    $rng.Dispose()
    return [Convert]::ToBase64String($buffer).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function Set-EnvValue {
    param(
        [string]$Texto,
        [string]$Clave,
        [string]$Valor
    )
    $patron = "(?m)^" + [regex]::Escape($Clave) + "=.*$"
    return [regex]::Replace($Texto, $patron, "$Clave=$Valor")
}

$seedScript = Join-Path $Repo "infra\seed\reset-seed.ps1"
$envExample = Join-Path $Repo ".env.example"
$envFile = Join-Path $Repo ".env"

if (-not (Test-Path $seedScript)) {
    Write-Host "No se encontro el seed en: $seedScript" -ForegroundColor Red
    Write-Host "Verifica -Repo o clona el repositorio completo."
    exit 1
}
if (-not (Test-Path $envExample)) {
    Write-Host "No se encontro .env.example en: $envExample" -ForegroundColor Red
    exit 1
}

$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) {
    Write-Host "Docker no esta disponible. Instala/abre Docker Desktop." -ForegroundColor Red
    exit 1
}
& docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Docker no responde. Abre Docker Desktop y vuelve a intentar." -ForegroundColor Red
    exit 1
}

if (-not (Get-Command python -ErrorAction SilentlyContinue)) {
    Write-Host "Python 3 no esta disponible." -ForegroundColor Red
    exit 1
}

if (-not (Test-Path $envFile)) {
    Copy-Item $envExample $envFile
    $texto = Get-Content $envFile -Raw
    $db = New-Secreto 24
    $jwt = New-Secreto 64
    $pgadmin = New-Secreto 24
    $texto = Set-EnvValue $texto "DB_PASSWORD" $db
    $texto = Set-EnvValue $texto "POSTGRES_PASSWORD" $db
    $texto = Set-EnvValue $texto "PGADMIN_DEFAULT_PASSWORD" $pgadmin
    $texto = Set-EnvValue $texto "APP_JWT_SECRET" $jwt
    Set-Content -Path $envFile -Value $texto -Encoding UTF8
    Write-Host ".env creado con secretos locales nuevos." -ForegroundColor Green
} else {
    Write-Host ".env ya existe; no se sobrescribe." -ForegroundColor Yellow
}

$contenido = Get-Content $envFile -Raw
if ($contenido -match "(?m)^MAIL_USERNAME=\s*$" -or $contenido -match "(?m)^MAIL_PASSWORD=\s*$") {
    Write-Host "AVISO: MAIL_USERNAME y MAIL_PASSWORD estan vacios." -ForegroundColor Yellow
    Write-Host "Sin SMTP el login no puede entregar el codigo 2FA."
}

if ($SkipSeed) {
    Write-Host "Seed omitido por -SkipSeed." -ForegroundColor Yellow
} else {
    Write-Host "Ejecutando seed canonico..." -ForegroundColor Cyan
    & $seedScript -Yes
    if ($LASTEXITCODE -ne 0) {
        Write-Host "El seed fallo. Revisa el mensaje anterior." -ForegroundColor Red
        exit $LASTEXITCODE
    }
}

Write-Host ""
Write-Host "Preparacion terminada." -ForegroundColor Green
if ($contenido -match "(?m)^MAIL_USERNAME=\s*$" -or $contenido -match "(?m)^MAIL_PASSWORD=\s*$") {
    Write-Host "Completa MAIL_USERNAME/MAIL_PASSWORD en $envFile." -ForegroundColor Yellow
}
Write-Host "Inicia el sistema con:"
Write-Host "  $Repo\levantar-sistema.bat"
Write-Host "Para analitica, ejecuta tambien: $Repo\levantar-bd-local.bat"
