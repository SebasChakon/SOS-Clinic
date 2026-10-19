# Utilidades de pruebas

Scripts para reproducir las pruebas manuales sobre el entorno local. No
ejecutar contra producción ni contra datos reales.

| Archivo | Uso |
|---|---|
| `jwt-forge.py` | Genera un JWT HS256 firmado para probar la validación de tokens. |
| `smoke-auth.ps1` | Comprueba acceso GET a endpoints con y sin token. |
| `idor-enum.ps1` | Recorre IDs de pacientes, triage o historias PDF (solo lectura). |
| `payloads-xss.txt` | Payloads inofensivos para pruebas de XSS almacenado. |

## Requisitos

- Python 3.8 o superior.
- PowerShell 5.1 o superior.
- El sistema levantado en `http://localhost:8080`.

## Ejemplos

```powershell
# Endpoints sin token (línea base)
.\smoke-auth.ps1 -OutFile ..\resultados\smoke-sin-token.csv

# Endpoints con un token válido
.\smoke-auth.ps1 -Token $env:SOS_TOKEN -OutFile ..\resultados\smoke-con-token.csv

# Enumeración de IDs de pacientes
.\idor-enum.ps1 -Recurso pacientes -Desde 1 -Hasta 20 -OutFile ..\resultados\idor-pacientes.csv

# JWT con el perfil administrador
$env:APP_JWT_SECRET = "pega-aqui-el-secreto-local"
python .\jwt-forge.py --usuario admin --perfil administrador --id-personal 1
```

## Reglas de uso

1. Los scripts de humo e IDOR solo hacen `GET`; no modifican datos.
2. No guardar el cuerpo de las respuestas: puede contener datos personales.
3. Esperar al menos 150–200 ms entre peticiones.
4. Guardar los CSV en `seguridad/resultados/`.
5. No commitear tokens, secretos ni capturas sin difuminar.
