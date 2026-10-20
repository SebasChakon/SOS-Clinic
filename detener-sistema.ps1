$ErrorActionPreference = 'SilentlyContinue'

$projectRoot = $PSScriptRoot
$ports = [ordered]@{
    5500 = 'Frontend'
    8088 = 'ms-ia-agente'
    8084 = 'ms-analitica'
    8083 = 'ms-triage'
    8082 = 'ms-pacientes'
    8081 = 'ms-seguridad'
    8080 = 'ms-gateway'
    8761 = 'Eureka'
}

function Get-ProcessInfo([int]$id) {
    if (-not $id) { return $null }
    Get-CimInstance Win32_Process -Filter "ProcessId=$id" -ErrorAction SilentlyContinue
}

function Test-Proyecto([string]$cmd) {
    if ([string]::IsNullOrWhiteSpace($cmd)) { return $false }
    return ($cmd -match 'com\.sos\.' -or
            $cmd -match 'sos-clinic' -or
            $cmd -match 'eureka-server' -or
            $cmd -match 'servidor\.py' -or
            $cmd -match 'mvnw' -or
            $cmd -match 'mvn\s+spring-boot' -or
            $cmd -match 'spring-boot:run')
}

foreach ($entry in $ports.GetEnumerator()) {
    $port = $entry.Key
    $name = $entry.Value
    $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue

    if (-not $conns) {
        Write-Host "$name ($port): no estaba corriendo."
        continue
    }

    foreach ($conn in $conns) {
        $proc = Get-ProcessInfo $conn.OwningProcess
        if (-not $proc) { continue }

        if (-not (Test-Proyecto $proc.CommandLine)) {
            Write-Host "$name ($port): puerto ocupado por otro proceso (PID $($proc.ProcessId)); no se toca." -ForegroundColor Yellow
            continue
        }

        Write-Host "Deteniendo $name (PID $($proc.ProcessId))..." -ForegroundColor Cyan
        $actual = $proc

        for ($i = 0; $i -lt 4 -and $actual; $i++) {
            Stop-Process -Id $actual.ProcessId -Force -ErrorAction SilentlyContinue
            $parent = Get-ProcessInfo $actual.ParentProcessId
            if (-not $parent) { break }

            if ($parent.Name -eq 'java.exe' -and $parent.CommandLine -match 'maven|mvnw|plexus|classworlds') {
                $actual = $parent
                continue
            }

            if ($parent.Name -eq 'cmd.exe' -and (Test-Proyecto $parent.CommandLine)) {
                Stop-Process -Id $parent.ProcessId -Force -ErrorAction SilentlyContinue
            }
            break
        }
    }
}

# Cerrar ventanas de Maven/frontend que hayan quedado abiertas por el launcher.
Get-CimInstance Win32_Process -Filter "Name='cmd.exe'" |
    Where-Object { $_.CommandLine -match 'SOS-' } |
    ForEach-Object {
        taskkill /PID $_.ProcessId /T /F *> $null
    }

$envFile = Join-Path $projectRoot '.env'
$composeDir = Join-Path $projectRoot 'infra'

if (Test-Path $composeDir) {
    if (Get-Command docker -ErrorAction SilentlyContinue) {
        docker info *> $null
        if ($LASTEXITCODE -eq 0) {
            Push-Location $composeDir
            if (Test-Path $envFile) {
                docker compose --env-file $envFile stop
            } else {
                docker compose stop
            }
            Pop-Location
            Write-Host 'Contenedores del proyecto detenidos.' -ForegroundColor Green
        } else {
            Write-Host 'Docker no esta corriendo; se omiten los contenedores.' -ForegroundColor Yellow
        }
    } else {
        Write-Host 'Docker no esta disponible; se omiten los contenedores.' -ForegroundColor Yellow
    }
}

Write-Host 'SOS Clinic detenido.' -ForegroundColor Green
