@echo off
setlocal EnableExtensions

set "BASE=%~dp0"
cd /d "%BASE%"
if not exist "%BASE%logs" mkdir "%BASE%logs"

rem ---------------------------------------------------------------------------
rem Carga .env (o env) sin imprimir valores.
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
  echo ADVERTENCIA: no existe .env ni env. Copia el archivo .env del
  echo              paquete de preparacion antes de levantar el sistema.
)

if not defined APP_JWT_SECRET (
  echo ERROR: APP_JWT_SECRET no esta definido. El sistema no arrancara sin el.
  echo        Agrega APP_JWT_SECRET a tu .env.
  pause
  exit /b 1
)

rem ---------------------------------------------------------------------------
rem Java: usa JAVA_HOME o busca un JDK en las rutas habituales.
rem ---------------------------------------------------------------------------
if defined JAVA_HOME if not exist "%JAVA_HOME%\bin\java.exe" set "JAVA_HOME="
if not defined JAVA_HOME if exist "C:\Program Files\Apache NetBeans\jdk\bin\java.exe" set "JAVA_HOME=C:\Program Files\Apache NetBeans\jdk"
if not defined JAVA_HOME for /d %%D in ("C:\Program Files\Java\jdk*") do if not defined JAVA_HOME set "JAVA_HOME=%%~fD"
if not defined JAVA_HOME for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk*") do if not defined JAVA_HOME set "JAVA_HOME=%%~fD"
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"

if not defined JAVA_HOME (
  where java >nul 2>nul
  if errorlevel 1 (
    echo ERROR: no se encontro Java. Instala un JDK 17+ o define JAVA_HOME.
    pause
    exit /b 1
  )
)

echo ===============================================
echo   Levantando infraestructura Docker...
echo ===============================================
docker info >nul 2>nul
if errorlevel 1 (
  echo ERROR: Docker Desktop no esta corriendo o Docker no esta disponible.
  pause
  exit /b 1
)

cd /d "%BASE%infra"
if defined ENV_FILE (
  docker compose --env-file "%ENV_FILE%" up -d
) else (
  docker compose up -d
)
if errorlevel 1 (
  echo ERROR: no se pudo levantar la infraestructura Docker.
  pause
  exit /b 1
)
cd /d "%BASE%"

echo Esperando a que PostgreSQL escuche en 5433...
set /a POSTGRES_WAIT=0
:wait_postgres
call :port_open 5433
if not errorlevel 1 goto postgres_ready
set /a POSTGRES_WAIT+=1
if %POSTGRES_WAIT% GEQ 30 (
  echo ADVERTENCIA: PostgreSQL no responde en 5433. Revisa Docker.
  goto postgres_ready
)
call :sleep 1
goto wait_postgres
:postgres_ready

echo ===============================================
echo   Iniciando microservicios
echo ===============================================
set "ANALITICA_STARTED=0"

call :start_service "eureka-server" 8761 "eureka-server"
call :start_service "ms-gateway" 8080 "ms-gateway"
call :start_service "ms-seguridad" 8081 "ms-seguridad"
call :start_service "ms-pacientes" 8082 "ms-pacientes"
call :start_service "ms-triage" 8083 "ms-triage"

call :port_open 3306
if errorlevel 1 (
  echo ADVERTENCIA: MySQL no responde en 3306; se omite ms-analitica.
) else (
  call :start_service "ms-analitica" 8084 "ms-analitica"
  set "ANALITICA_STARTED=1"
)

call :start_service "ms-ia-agente" 8088 "ms-ia-agente"

if not defined GROQ_API_KEY echo ADVERTENCIA: GROQ_API_KEY no esta definida; el agente respondera que no esta configurado.

echo Esperando a que los servicios esten listos...
call :wait_port 8761 60
call :wait_port 8080 90
call :wait_port 8081 90
call :wait_port 8082 90
call :wait_port 8083 90
call :wait_port 8088 90
if "%ANALITICA_STARTED%"=="1" call :wait_port 8084 60

rem ---------------------------------------------------------------------------
rem Frontend
rem ---------------------------------------------------------------------------
echo Abriendo el sistema en el navegador...
set "PY_FRONT="
where python >nul 2>nul && set "PY_FRONT=python"
if not defined PY_FRONT (
  where py >nul 2>nul
  if not errorlevel 1 set "PY_FRONT=py -3"
)

call :port_open 5500
if not errorlevel 1 (
  echo El frontend ya esta escuchando en 5500; se omite el servidor.
) else (
  if defined PY_FRONT (
    echo Sirviendo frontend en http://127.0.0.1:5500 ...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process -FilePath 'cmd.exe' -ArgumentList '/c %PY_FRONT% servidor.py 5500' -WorkingDirectory '%BASE%frontend' -WindowStyle Hidden -RedirectStandardOutput '%BASE%logs\frontend.out.log' -RedirectStandardError '%BASE%logs\frontend.err.log'"
    call :wait_port 5500 30
  ) else (
    echo ADVERTENCIA: Python no esta disponible; se abren los archivos directamente.
    echo              El video de la landing mostrara solo la miniatura hacia YouTube.
    start "" "%BASE%frontend\index.html"
    goto fin
  )
)

start "" "http://127.0.0.1:5500/index.html"

:fin
echo Listo. Los servicios siguen en segundo plano, sin ventanas visibles.
echo Logs: %BASE%logs
endlocal
exit /b 0

rem ---------------------------------------------------------------------------
rem Subrutinas
rem ---------------------------------------------------------------------------
:wait_port
set "W_PORT=%~1"
set /a W_MAX=%~2
set /a W_COUNT=0
:wait_port_loop
call :port_open %W_PORT%
if not errorlevel 1 exit /b 0
set /a W_COUNT+=1
if %W_COUNT% GEQ %W_MAX% (
  echo ADVERTENCIA: el puerto %W_PORT% no respondio en %W_MAX% segundos.
  exit /b 1
)
call :sleep 1
goto wait_port_loop

:sleep
set /a SLEEP_PINGS=%~1+1
ping 127.0.0.1 -n %SLEEP_PINGS% >nul
exit /b 0
:port_open
netstat -ano | findstr /r /c:":%~1 " | findstr LISTENING >nul
exit /b %errorlevel%

:start_service
set "S_NAME=%~1"
set "S_PORT=%~2"
set "S_DIR=%~3"
call :port_open %S_PORT%
if not errorlevel 1 (
  echo %S_NAME% ya esta escuchando en %S_PORT%; se omite.
  exit /b 0
)
echo Iniciando %S_NAME% (oculto)...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process -FilePath 'cmd.exe' -ArgumentList '/c mvnw.cmd spring-boot:run' -WorkingDirectory '%BASE%%S_DIR%' -WindowStyle Hidden -RedirectStandardOutput '%BASE%logs\%S_NAME%.out.log' -RedirectStandardError '%BASE%logs\%S_NAME%.err.log'"
exit /b 0
