[CmdletBinding()]
param(
    [string]$Repo = "",
    [switch]$SkipSeed
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($Repo)) {
    $Repo = $PSScriptRoot
}

$seedScript = Join-Path $Repo "infra\seed\reset-seed.ps1"
$envFile = Join-Path $Repo ".env"

if (-not (Test-Path $seedScript)) {
    Write-Host "No se encontro el seed en: $seedScript" -ForegroundColor Red
    Write-Host "Verifica -Repo o clona el repositorio completo."
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
    Write-Host "No existe .env en: $Repo" -ForegroundColor Red
    Write-Host "Copia el archivo .env del paquete de preparacion en la raiz del repositorio."
    exit 1
}
Write-Host ".env encontrado; no se sobrescribe." -ForegroundColor Green

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
Write-Host "  $Repo\04. levantar-sistema.bat"
Write-Host "Para analitica, ejecuta tambien: $Repo\03. levantar-bd-local.bat"
