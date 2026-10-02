@echo off
setlocal

set BASE=%~dp0

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

echo Esperando a que todos terminen de arrancar...
timeout /t 20 /nobreak >nul

echo Abriendo el sistema en el navegador...
start "" "%BASE%frontend\index.html"

echo Listo. Cierra esta ventana cuando quieras (los microservicios siguen en sus propias ventanas).
endlocal