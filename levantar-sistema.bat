@echo off
setlocal

set BASE=%~dp0

rem ---------------------------------------------------------------------------
rem Carga las credenciales de desarrollo desde el archivo .env (no versionado).
rem Cada linea debe tener formato NOMBRE=VALOR. Las lineas vacias o que
rem empiecen con # se ignoran. Si .env no existe, sigue con el entorno actual.
rem ---------------------------------------------------------------------------
set "ENV_FILE="
if exist "%BASE%.env" set "ENV_FILE=%BASE%.env"
if not defined ENV_FILE if exist "%BASE%env" set "ENV_FILE=%BASE%env"

if defined ENV_FILE (
  echo Cargando credenciales desde %ENV_FILE% ...
  for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%ENV_FILE%") do (
    if not "%%A"=="" if not "%%B"=="" set "%%A=%%B"
  )
) else (
  echo ADVERTENCIA: no existe .env ni env. Copia .env.example a .env y
  echo              completa las credenciales antes de levantar el sistema.
)

if not defined APP_JWT_SECRET (
  echo ERROR: APP_JWT_SECRET no esta definido. El sistema no arrancara sin el.
  echo        Agrega APP_JWT_SECRET a tu .env.
  pause
  exit /b 1
)

echo ===============================================
echo   Levantando infraestructura Docker...
echo ===============================================
cd /d "%BASE%infra"
if defined ENV_FILE (
  docker compose --env-file "%ENV_FILE%" up -d
) else (
  docker compose up -d
)

echo Esperando a que Postgres este listo...
timeout /t 8 /nobreak >nul

echo ===============================================
echo   Iniciando eureka-server...
echo ===============================================
start "eureka-server" cmd /k "cd /d %BASE%eureka-server && mvn spring-boot:run"
timeout /t 20 /nobreak >nul

echo Iniciando ms-gateway...
start "ms-gateway" cmd /k "cd /d %BASE%ms-gateway && mvn spring-boot:run"
timeout /t 10 /nobreak >nul

echo Iniciando ms-seguridad...
start "ms-seguridad" cmd /k "cd /d %BASE%ms-seguridad && mvn spring-boot:run"
timeout /t 6 /nobreak >nul

echo Iniciando ms-pacientes...
start "ms-pacientes" cmd /k "cd /d %BASE%ms-pacientes && mvn spring-boot:run"
timeout /t 6 /nobreak >nul

echo Iniciando ms-triage...
start "ms-triage" cmd /k "cd /d %BASE%ms-triage && mvn spring-boot:run"
timeout /t 6 /nobreak >nul

echo Iniciando ms-analitica (requiere MySQL local)...
start "ms-analitica" cmd /k "cd /d %BASE%ms-analitica && mvn spring-boot:run"
timeout /t 6 /nobreak >nul

echo Iniciando ms-ia-agente...
start "ms-ia-agente" cmd /k "cd /d %BASE%ms-ia-agente && mvn spring-boot:run"

if not defined GROQ_API_KEY echo ADVERTENCIA: GROQ_API_KEY no esta definida; el agente respondera que no esta configurado.

echo Esperando a que todos terminen de arrancar...
timeout /t 20 /nobreak >nul

echo Abriendo el sistema en el navegador...
set "PY_FRONT="
where python >nul 2>nul && set "PY_FRONT=python"
if not defined PY_FRONT (
  where py >nul 2>nul
  if not errorlevel 1 set "PY_FRONT=py -3"
)
if defined PY_FRONT (
  echo Sirviendo frontend en http://127.0.0.1:5500 ...
  start "frontend-sos" cmd /k "cd /d %BASE%frontend && %PY_FRONT% servidor.py 5500"
  timeout /t 4 /nobreak >nul
  start "" "http://127.0.0.1:5500/index.html"
) else (
  echo ADVERTENCIA: Python no esta disponible; se abren los archivos directamente.
  echo              El video de la landing mostrara solo la miniatura hacia YouTube.
  start "" "%BASE%frontend\index.html"
)

echo Listo. Cierra esta ventana cuando quieras (los microservicios siguen en sus propias ventanas).
endlocal