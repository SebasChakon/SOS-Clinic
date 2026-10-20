@echo off
setlocal

echo ===============================================
echo   Deteniendo SOS Clinic...
echo ===============================================

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0detener-sistema.ps1"

echo.
echo Proceso completado. Los datos de PostgreSQL se conservan.
pause
endlocal
