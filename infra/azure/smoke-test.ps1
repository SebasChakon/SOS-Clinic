#requires -Version 5.1
<#
.SYNOPSIS
    Prueba HTTPS, cabeceras y endpoints publicos de SOS Clinic en Azure.

.EXAMPLE
    .\infra\azure\smoke-test.ps1 -Domain sos-clinic-demo.eastus2.cloudapp.azure.com
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Domain
)

$ErrorActionPreference = 'Stop'
try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
} catch {
}

$fallos = New-Object System.Collections.Generic.List[string]
$base = "https://$Domain"

function Probar {
    param(
        [string]$Nombre,
        [scriptblock]$Accion
    )
    try {
        & $Accion
        Write-Host "PASS  $Nombre" -ForegroundColor Green
    } catch {
        Write-Host "FAIL  $Nombre -> $($_.Exception.Message)" -ForegroundColor Red
        $fallos.Add($Nombre)
    }
}

Write-Host ""
Write-Host "Smoke test de $base" -ForegroundColor Cyan
Write-Host ""

Probar 'Frontend / responde 200' {
    $r = Invoke-WebRequest -Uri "$base/" -UseBasicParsing -TimeoutSec 30
    if ($r.StatusCode -ne 200) { throw "HTTP $($r.StatusCode)" }
    if ($r.Content -notmatch 'SOS') { throw 'La pagina no parece ser SOS Clinic' }
}

Probar 'login.html responde 200' {
    $r = Invoke-WebRequest -Uri "$base/login.html" -UseBasicParsing -TimeoutSec 30
    if ($r.StatusCode -ne 200) { throw "HTTP $($r.StatusCode)" }
}

Probar 'landing.html redirige a /index.html' {
    $peticion = [System.Net.HttpWebRequest]::Create("$base/landing.html")
    $peticion.AllowAutoRedirect = $false
    $peticion.Method = 'GET'
    $peticion.Timeout = 30000
    $respuesta = $null
    try {
        $respuesta = $peticion.GetResponse()
    } catch [System.Net.WebException] {
        $respuesta = $_.Exception.Response
    }
    if ($null -eq $respuesta) { throw 'Sin respuesta del servidor' }
    try {
        $codigo = [int]$respuesta.StatusCode
        $ubicacion = [string]$respuesta.Headers['Location']
        if ($codigo -ne 301) { throw "HTTP $codigo" }
        if ($ubicacion -notmatch '/index\.html') { throw "Location: $ubicacion" }
    } finally {
        $respuesta.Close()
    }
}

Probar 'cabecera HSTS presente' {
    $r = Invoke-WebRequest -Uri "$base/" -UseBasicParsing -TimeoutSec 30
    if (-not $r.Headers['Strict-Transport-Security']) { throw 'Falta Strict-Transport-Security' }
}

Probar 'CSP same-origin presente' {
    $r = Invoke-WebRequest -Uri "$base/" -UseBasicParsing -TimeoutSec 30
    $csp = [string]$r.Headers['Content-Security-Policy']
    if ($csp -notmatch "connect-src 'self'") { throw 'CSP no contiene connect-src self' }
}

Probar 'gateway/ms-seguridad responde por Caddy' {
    $r = Invoke-WebRequest -Uri "$base/ms-seguridad/auth/politica-clave" -UseBasicParsing -TimeoutSec 30
    if ($r.StatusCode -ne 200) { throw "HTTP $($r.StatusCode)" }
    $null = $r.Content | ConvertFrom-Json
}

Probar 'gateway rechaza sin JWT' {
    try {
        $null = Invoke-WebRequest -Uri "$base/ms-pacientes/pacientes" -UseBasicParsing -TimeoutSec 30
        throw 'Respondio 200 sin token'
    } catch {
        $codigo = $null
        if ($_.Exception.Response) {
            $codigo = [int]$_.Exception.Response.StatusCode
        }
        if ($codigo -ne 401) {
            throw "Se esperaba 401 y llego $codigo"
        }
    }
}

Probar 'certificado TLS de confianza' {
    $null = Invoke-WebRequest -Uri "$base/" -UseBasicParsing -TimeoutSec 30
}

Write-Host ""
if ($fallos.Count -gt 0) {
    Write-Host "$($fallos.Count) prueba(s) fallaron." -ForegroundColor Red
    exit 1
}
Write-Host 'Todas las pruebas pasaron.' -ForegroundColor Green
exit 0
