@echo off
setlocal
cd /d "%~dp0infra"

if not exist "%~dp0.env" (
  echo ERROR: no existe .env en la raiz del repositorio.
  echo Copia el archivo .env del paquete de preparacion.
  pause
  exit /b 1
)

echo Levantando PostgreSQL y pgAdmin...
docker compose --env-file "%~dp0.env" up -d
if errorlevel 1 (
  echo ERROR: no se pudo levantar la infraestructura Docker.
  pause
  exit /b 1
)

docker compose --env-file "%~dp0.env" ps
pause
endlocal
