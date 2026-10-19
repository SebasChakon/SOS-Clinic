<#
.SYNOPSIS
    Comprueba el acceso a endpoints GET de SOS Clinic con o sin token.

.DESCRIPTION
    Solo realiza peticiones GET de lectura. No modifica datos. Guarda un CSV
    con el codigo HTTP y el tamano de la respuesta; no guarda el cuerpo para no
    exponer datos personales.

.PARAMETER BaseUrl
    URL del gateway. Por defecto http://localhost:8080.

.PARAMETER Token
    JWT para enviar en la cabecera Authorization. Si se omite, prueba sin token.

.PARAMETER OutFile
    Ruta del CSV de salida. Se recomienda seguridad/resultados/.

.EXAMPLE
    .\smoke-auth.ps1 -OutFile ..\resultados\smoke-sin-token.csv

.EXAMPLE
    .\smoke-auth.ps1 -Token $env:SOS_TOKEN -OutFile ..\resultados\smoke-con-token.csv
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Token,
    [string]$OutFile
)

$ErrorActionPreference = "Stop"

$endpoints = @(
    @{ Nombre = "Pacientes: listar";              Ruta = "/ms-pacientes/pacientes" },
    @{ Nombre = "Pacientes: por cedula";          Ruta = "/ms-pacientes/pacientes/cedula/0000000000" },
    @{ Nombre = "Pacientes: buscar";              Ruta = "/ms-pacientes/pacientes/buscar?q=0" },
    @{ Nombre = "Pacientes: signos por registro"; Ruta = "/ms-pacientes/pacientes/signos-vitales/registro/1" },
    @{ Nombre = "Triage: prioridad";              Ruta = "/ms-triage/triage/prioridad" },
    @{ Nombre = "Triage: por id";                 Ruta = "/ms-triage/triage/1" },
    @{ Nombre = "Seguridad: admin personal";      Ruta = "/ms-seguridad/admin/personal" },
    @{ Nombre = "Seguridad: admin perfiles";      Ruta = "/ms-seguridad/admin/perfiles" },
    @{ Nombre = "Seguridad: admin actividades";   Ruta = "/ms-seguridad/admin/actividades" },
    @{ Nombre = "Seguridad: admin auditoria";     Ruta = "/ms-seguridad/admin/auditoria" },
    @{ Nombre = "Analitica: resumen";             Ruta = "/ms-analitica/dashboard/resumen?desde=2026-01-01&hasta=2026-01-31" },
    @{ Nombre = "Analitica: llegadas";            Ruta = "/ms-analitica/dashboard/llegadas?desde=2026-01-01&hasta=2026-01-31" },
    @{ Nombre = "Analitica: capacidad";           Ruta = "/ms-analitica/dashboard/capacidad?desde=2026-01-01&hasta=2026-01-31" }
)

$headers = @{}
if ($Token) {
    $headers["Authorization"] = "Bearer $Token"
}

$resultados = foreach ($endpoint in $endpoints) {
    $url = "$($BaseUrl.TrimEnd('/'))$($endpoint.Ruta)"
    try {
        $respuesta = Invoke-WebRequest -Uri $url -Method Get -Headers $headers `
            -TimeoutSec 10 -UseBasicParsing

        [pscustomobject]@{
            Prueba      = $endpoint.Nombre
            Metodo      = "GET"
            Ruta        = $endpoint.Ruta
            Status      = [int]$respuesta.StatusCode
            Bytes       = [int]$respuesta.RawContentLength
            ContentType = ($respuesta.Headers["Content-Type"] -join "; ")
        }
    }
    catch {
        $status = 0
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            $status = [int]$_.Exception.Response.StatusCode.value__
        }

        [pscustomobject]@{
            Prueba      = $endpoint.Nombre
            Metodo      = "GET"
            Ruta        = $endpoint.Ruta
            Status      = $status
            Bytes       = 0
            ContentType = ""
        }
    }

    Start-Sleep -Milliseconds 150
}

$resultados | Format-Table -AutoSize

if ($OutFile) {
    $directorio = Split-Path -Parent $OutFile
    if ($directorio -and -not (Test-Path $directorio)) {
        New-Item -ItemType Directory -Path $directorio -Force | Out-Null
    }
    $resultados | Export-Csv -Path $OutFile -NoTypeInformation -Encoding UTF8
    Write-Host "Resultados guardados en $OutFile"
}

Write-Host ""
Write-Host "Aviso: un 200 sin token es un hallazgo, no un exito." -ForegroundColor Yellow
