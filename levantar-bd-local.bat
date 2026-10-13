@echo off
setlocal
set "EXIT_CODE=1"

cd /d "%~dp0"

echo ADVERTENCIA: bd_local.sql elimina y vuelve a crear urgencias_hospital.
echo Los datos anteriores de esa base se perderan.
echo.

echo [1/4] Ejecutando bd_local.sql en MySQL...
set "MYSQL_EXE="
for /f "delims=" %%I in ('where mysql 2^>nul') do if not defined MYSQL_EXE set "MYSQL_EXE=%%I"
if not defined MYSQL_EXE if exist "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" set "MYSQL_EXE=C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
if not defined MYSQL_EXE goto mysql_no_disponible

"%MYSQL_EXE%" --user=root --password=root < "%~dp0bd_local.sql"
if errorlevel 1 goto sql_error

echo.
echo [2/4] Comprobando Python...
python --version
if errorlevel 1 goto python_no_disponible

echo.
echo [3/4] Instalando dependencias de Python...
python -m pip install pandas faker sqlalchemy pymysql
if errorlevel 1 goto pip_error

echo.
echo [4/4] Ejecutando llenar_hospital.py...
python "%~dp0llenar_hospital.py"
if errorlevel 1 goto script_error

echo.
echo Proceso finalizado correctamente.
set "EXIT_CODE=0"
goto finalizar

:mysql_no_disponible
echo ERROR: No se encontro el comando mysql. Instala MySQL o agrega su carpeta bin al PATH.
goto finalizar

:sql_error
echo ERROR: No se pudo ejecutar bd_local.sql. Verifica que MySQL este iniciado y que root:root sea correcto.
goto finalizar

:python_no_disponible
echo ERROR: Python no esta instalado o no esta disponible en PATH. Instala Python y vuelve a ejecutar este archivo.
goto finalizar

:pip_error
echo ERROR: No se pudieron instalar las dependencias de Python.
goto finalizar

:script_error
echo ERROR: Fallo la ejecucion de llenar_hospital.py.
goto finalizar

:finalizar
echo.
pause
exit /b %EXIT_CODE%
