@echo off
setlocal

set BASE=%~dp0

rem ---------------------------------------------------------------------------
rem Carga las credenciales de desarrollo desde el archivo .env (no versionado).
rem Cada linea debe tener formato NOMBRE=VALOR. Las lineas vacias o que
rem empiecen con # se ignoran. Si .env no existe, sigue con el entorno actual.
rem ---------------------------------------------------------------------------
if exist "%BASE%.env" (
  echo Cargando credenciales desde .env ...
  for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%BASE%.env") do (
    if not "%%A"=="" if not "%%B"=="" set "%%A=%%B"
  )
) else (
  echo ADVERTENCIA: no existe el archivo .env. Copia .env.example a .env y
  echo              completa las credenciales antes de levantar el sistema.
)

if not defined ANALITICA_DB_USERNAME set ANALITICA_DB_USERNAME=root
if not defined ANALITICA_DB_PASSWORD set ANALITICA_DB_PASSWORD=root

echo ===============================================
echo   Levantando infraestructura Docker...
echo ===============================================
cd /d "%BASE%infra"
docker compose up -d

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
start "" "%BASE%frontend\index.html"

echo Listo. Cierra esta ventana cuando quieras (los microservicios siguen en sus propias ventanas).
endlocal