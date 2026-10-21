[CmdletBinding()]
param(
    [switch]$Persist
)

$ErrorActionPreference = "Stop"

function Test-Jdk {
    param([string]$Ruta)
    if ([string]::IsNullOrWhiteSpace($Ruta)) { return $false }
    return Test-Path (Join-Path $Ruta "bin\java.exe")
}

$patrones = @(
    $env:JAVA_HOME,
    "C:\Program Files\Apache NetBeans\jdk",
    "C:\Program Files\Java\jdk*",
    "C:\Program Files\Eclipse Adoptium\jdk*",
    "C:\Program Files\Microsoft\jdk*",
    "C:\Program Files\Amazon Corretto\*",
    "C:\Program Files\BellSoft\LibericaJDK*",
    "C:\Program Files\Zulu\zulu-*"
)

$jdk = $null
foreach ($patron in $patrones) {
    if ([string]::IsNullOrWhiteSpace($patron)) { continue }
    $items = @(Get-Item $patron -ErrorAction SilentlyContinue)
    foreach ($item in $items) {
        if (Test-Jdk $item.FullName) {
            $jdk = $item.FullName
            break
        }
    }
    if ($jdk) { break }
}

if (-not $jdk) {
    $comando = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($comando) {
        $candidato = Split-Path (Split-Path $comando.Source -Parent) -Parent
        if (Test-Jdk $candidato) { $jdk = $candidato }
    }
}

if (-not $jdk) {
    Write-Host "No se encontro un JDK 17+ instalado." -ForegroundColor Red
    Write-Host "Instala Eclipse Temurin 17 o 21 desde https://adoptium.net/temurin/releases/"
    Write-Host "Luego vuelve a ejecutar este script o define JAVA_HOME manualmente."
    exit 1
}

$javaExe = Join-Path $jdk "bin\java.exe"
$anterior = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$salida = (& $javaExe -version 2>&1 | Out-String)
$ErrorActionPreference = $anterior
if ($salida -match 'version "(\d+)') {
    $mayor = [int]$Matches[1]
    if ($mayor -lt 17) {
        Write-Host "El JDK encontrado es demasiado antiguo (version mayor $mayor)." -ForegroundColor Red
        Write-Host "Instala un JDK 17 o superior."
        exit 1
    }
}

$env:JAVA_HOME = $jdk
$env:Path = (Join-Path $jdk "bin") + ";" + $env:Path

Write-Host "JDK encontrado: $jdk" -ForegroundColor Green
Write-Host "Version de Java comprobada."

if ($Persist) {
    [Environment]::SetEnvironmentVariable("JAVA_HOME", $jdk, "User")
    $rutaBin = Join-Path $jdk "bin"
    $pathUsuario = [Environment]::GetEnvironmentVariable("Path", "User")
    $entradas = @()
    if (-not [string]::IsNullOrWhiteSpace($pathUsuario)) {
        $entradas = @($pathUsuario -split ';' | Where-Object {
            $_ -and ($_.TrimEnd('\') -ine $rutaBin.TrimEnd('\'))
        })
    }
    [Environment]::SetEnvironmentVariable("Path", ((@($rutaBin) + $entradas) -join ';'), "User")
    Write-Host "JAVA_HOME y PATH guardados para el usuario actual." -ForegroundColor Green
    Write-Host "Cierra y abre una terminal nueva para usarlos en otros comandos."
} else {
    Write-Host "JAVA_HOME configurado solo en esta sesion." -ForegroundColor Yellow
    Write-Host "Para aplicarlo en la terminal actual, ejecuta:"
    Write-Host "  . .\configurar-java.ps1"
}
