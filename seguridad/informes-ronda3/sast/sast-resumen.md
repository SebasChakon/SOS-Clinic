# SAST / SCA — resumen

**Fecha:** 2026-10-22 · **Rama:** `pruebas-seguridad-3`

## Qué se hizo

- **Gitleaks** sobre el árbol y los 45 commits del historial, con `--redact`.
- **Semgrep** con `p/security-audit` y `p/secrets` sobre 302 archivos.
- **Trivy** sobre los 7 `pom.xml` (vulnerabilidades, secretos y configuración)
  y sobre `infra/`.
- Revisión manual de `docker-compose.yml` y de la configuración de logs.

## Resultados

| Escaneo | Resultado |
|---|---|
| Gitleaks árbol | 0 hallazgos |
| Gitleaks historial | 3 claves Grafana históricas (archivo eliminado) |
| Semgrep | 9 hallazgos, todos en scripts de prueba |
| Trivy dependencias | 0 vulnerabilidades, 0 secretos |
| Trivy configuración | 0 archivos detectados; revisión manual OK |

Los hallazgos de Semgrep son hashes BCrypt de fixture y `urllib` dinámico en
scripts locales: falsos positivos. No hay patrones inseguros en producción.

## Mejoras

1. Rotar la clave Grafana histórica si fue real.
2. Anotar los fixtures con `nosemgrep` o generarlos en ejecución.
3. Ampliar la CI con gitleaks del historial y artefactos de Semgrep/Trivy.

## Evidencia

- `seguridad/resultados/gitleaks-arbol-r3.json`
- `seguridad/resultados/gitleaks-historial-r3.json`
- `seguridad/resultados/semgrep-r3.json`
- `seguridad/resultados/trivy-r3.json` y `trivy-config-r3.json`

## Conclusión

Sin secretos vigentes ni vulnerabilidades conocidas en dependencias. Estado
apto para el pentest y el DAST.
