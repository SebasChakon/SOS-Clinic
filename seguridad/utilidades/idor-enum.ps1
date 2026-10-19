<#
.SYNOPSIS
    Recorre IDs secuenciales para detectar IDOR en endpoints de lectura.

.DESCRIPTION
    Solo realiza peticiones GET y muestra el codigo HTTP y el tamano de la
    respuesta. No guarda ni imprime el cuerpo para no exponer datos clinicos.

.PARAMETER Recurso
    pacientes | triage | historias.

.PARAMETER Desde
    Primer ID del rango (por defecto 1).

.PARAMETER Hasta
    Ultimo ID del rango (por defecto 20). Mantener rangos pequenos.

.PARAMETER BaseUrl
    URL del gateway.

.PARAMETER Token
    JWT opcional. Si se omite, la prueba es sin autenticacion.

.PARAMETER OutFile
    CSV de salida.

.EXAMPLE
    .\idor-enum.ps1 -Recurso pacientes -Desde 1 -Hasta 20 -OutFile ..\resultados\idor-pacientes.csv
#>
[CmdletBinding()]
param(
    [ValidateSet("pacientes", "triage", "historias")]
    [string]$Recurso = "pacientes",

    [int]$Desde = 1,

    [int]$Hasta = 20,

    [string]$BaseUrl = "http://localhost:8080",

    [string]$Token,

    [string]$OutFile
)

$ErrorActionPreference = "Stop"

if ($Hasta -lt $Desde) {
    throw "El rango es invalido: Hasta debe ser mayor o igual que Desde."
}

if (($Hasta - $Desde) -gt 200) {
    throw "Rango demasiado grande. Usa como maximo 200 IDs por ejecucion."
}

$plantilla = switch ($Recurso) {
    "pacientes" { "/ms-pacientes/pacientes/{0}" }
    "triage"    { "/ms-triage/triage/{0}" }
    "historias" { "/ms-pacientes/pacientes/historias/{0}/pdf" }
}

$headers = @{}
if ($Token) {
    $headers["Authorization"] = "Bearer $Token"
}

$resultados = for ($id = $Desde; $id -le $Hasta; $id++) {
    $ruta = $plantilla -f $id
    $url = "$($BaseUrl.TrimEnd('/'))$ruta"

    try {
        $respuesta = Invoke-WebRequest -Uri $url -Method Get -Headers $headers `
            -TimeoutSec 10 -UseBasicParsing

        [pscustomobject]@{
            Recurso = $Recurso
            Id      = $id
            Status  = [int]$respuesta.StatusCode
            Bytes   = [int]$respuesta.RawContentLength
            Tipo    = ($respuesta.Headers["Content-Type"] -join "; ")
        }
    }
    catch {
        $status = 0
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            $status = [int]$_.Exception.Response.StatusCode.value__
        }

        [pscustomobject]@{
            Recurso = $Recurso
            Id      = $id
            Status  = $status
            Bytes   = 0
            Tipo    = ""
        }
    }

    Start-Sleep -Milliseconds 200
}

$resultados | Format-Table -AutoSize

$encontrados = @($resultados | Where-Object { $_.Status -eq 200 }).Count
Write-Host "IDs con respuesta 200: $encontrados de $($Hasta - $Desde + 1)" -ForegroundColor Yellow

if ($OutFile) {
    $directorio = Split-Path -Parent $OutFile
    if ($directorio -and -not (Test-Path $directorio)) {
        New-Item -ItemType Directory -Path $directorio -Force | Out-Null
    }
    $resultados | Export-Csv -Path $OutFile -NoTypeInformation -Encoding UTF8
    Write-Host "Resultados guardados en $OutFile"
}
