# Ejecuta las pruebas Maven de cada modulo y deja un resumen en
# seguridad/resultados/maven/. Los logs completos van a la carpeta temporal
# para no inflar el repositorio; si un modulo falla se guarda el final del log.
#
# Uso:
#   powershell -NoProfile -ExecutionPolicy Bypass -File seguridad\pruebas\ejecutar-tests-maven.ps1
param(
    [string[]]$Modules = @(
        'eureka-server', 'ms-gateway', 'ms-seguridad', 'ms-pacientes',
        'ms-triage', 'ms-analitica', 'ms-ia-agente'
    )
)

$ErrorActionPreference = 'Continue'
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$Out = Join-Path $Root 'seguridad\resultados\maven'
$Logs = Join-Path $env:TEMP 'sos-clinic-maven'
New-Item -ItemType Directory -Force -Path $Out, $Logs | Out-Null

$env:JAVA_HOME = 'C:\Progra~1\Apache~1\jdk'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

$resultados = @()
foreach ($modulo in $Modules) {
    $directorio = Join-Path $Root $modulo
    if (-not (Test-Path (Join-Path $directorio 'mvnw.cmd'))) {
        Write-Host "OMITIDO: $modulo no tiene mvnw.cmd"
        continue
    }

    Write-Host "==> $modulo"
    $log = Join-Path $Logs "$modulo.log"
    Push-Location $directorio
    & .\mvnw.cmd -B test *> $log
    $codigo = $LASTEXITCODE
    Pop-Location

    $resumen = (Select-String -Path $log -Pattern 'Tests run:.*Failures' |
                Select-Object -Last 1).Line
    if ($resumen) {
        $resumen = ($resumen -replace '^\[INFO\]\s*', '' -replace '\s+', ' ').Trim()
    } else {
        $resumen = 'sin resumen de surefire'
    }

    $estado = if ($codigo -eq 0) { 'PASS' } else { 'FAIL' }
    if ($codigo -ne 0) {
        $cola = Join-Path $Out "$modulo-fallo.log"
        Get-Content -Path $log -Tail 120 | Set-Content -Path $cola -Encoding UTF8
    }

    $resultados += [pscustomobject]@{
        modulo  = $modulo
        estado  = $estado
        codigo  = $codigo
        resumen = $resumen
    }
    Write-Host "    $estado - $resumen"
}

$resultados | Export-Csv -Path (Join-Path $Out 'resumen.csv') -NoTypeInformation -Encoding UTF8

$lineas = @('# Pruebas Maven por modulo', '',
            "Fecha: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')", '',
            '| Modulo | Estado | Codigo | Resumen |',
            '|---|---|---|---|')
foreach ($r in $resultados) {
    $lineas += "| $($r.modulo) | $($r.estado) | $($r.codigo) | $($r.resumen) |"
}
$lineas | Set-Content -Path (Join-Path $Out 'resumen.md') -Encoding UTF8

$fallos = @($resultados | Where-Object { $_.estado -eq 'FAIL' }).Count
Write-Host ""
Write-Host "Resultado: $($resultados.Count - $fallos) PASS, $fallos FAIL"
Write-Host "Resumen: $(Join-Path $Out 'resumen.md')"
exit $fallos
