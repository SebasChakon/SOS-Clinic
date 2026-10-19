# Resultados de herramientas

Salidas crudas de los analisis. Se versionan los reportes pequenos en JSON, CSV
o HTML; los binarios grandes y los dumps no se versionan.

## Nombres esperados

| Archivo | Origen |
|---|---|
| `trivy-sca-AAAA-MM-DD.json` | `trivy fs --scanners vuln` |
| `trivy-secretos-AAAA-MM-DD.json` | `trivy fs --scanners secret` |
| `semgrep-AAAA-MM-DD.json` | `semgrep scan --json` |
| `gitleaks-AAAA-MM-DD.json` | `gitleaks detect --report-format json` |
| `zap-baseline-AAAA-MM-DD.html` | ZAP baseline |
| `zap-autenticado-AAAA-MM-DD.html` | ZAP con sesion |
| `smoke-sin-token-AAAA-MM-DD.csv` | `smoke-auth.ps1` |
| `smoke-con-token-AAAA-MM-DD.csv` | `smoke-auth.ps1 -Token` |
| `idor-<recurso>-AAAA-MM-DD.csv` | `idor-enum.ps1` |

## Reglas

1. Registrar en el informe la version de la herramienta y la fecha.
2. No incluir tokens, secretos ni cuerpos de respuesta con datos personales.
3. Si un reporte contiene datos sensibles, guardarlo fuera del repositorio y
   dejar solo un resumen saneado.
