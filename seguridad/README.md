# Ronda 3 de pruebas — SOS Clinic

Esta rama (`pruebas-seguridad-3`) concentra las pruebas funcionales y de
seguridad de la ronda 3. **No se modifica `main`**: los resultados se guardan
aquí y las correcciones se propondrán después en ramas `fix/*` separadas.

## Estructura

| Ruta | Contenido |
|---|---|
| `resultados/fase0-entorno.md` | Equipo, servicios, seed y estado inicial. |
| `resultados/fase1-funcional.csv` | Casos funcionales ejecutados (PASS/FAIL/WARN/SKIP). |
| `resultados/fase1-funcional.md` | Resumen de la fase funcional y hallazgos. |
| `resultados/maven/` | Resumen de `mvnw test` por módulo. |
| `resultados/fase2-estatico.md` | Gitleaks, Semgrep y Trivy. |
| `resultados/fase2-pentest.md` / `.csv` | Pentest local y matriz de autorización. |
| `resultados/fase2-dast.md` | ZAP baseline y evaluación de advertencias. |
| `resultados/hallazgos-fase2.csv` | Hallazgos consolidados y recomendaciones. |
| `resultados/plan-mejoras-main.md` | Backlog priorizado para `main`. |
| `informes-ronda3/` | Documentación por prueba: 2 `.tex` (metodología y resultados), resumen `.md` y PDF. |
| `pruebas/e2e_funcional.py` | Suite E2E de flujos clínicos y administrativos. |
| `pruebas/pentest_local.py` | Matriz de roles, JWT, CORS, 2FA, IDOR y mass assignment. |
| `pruebas/pentest_xss.py` | Prepara/limpia el escenario de XSS almacenado. |
| `pruebas/ejecutar-tests-maven.ps1` | Ejecuta las pruebas Maven de los 7 módulos. |
| `pruebas/e2e_claves.py` | Suite existente de claves, 2FA, recuperación y aprobaciones (heredada de `main`). |
| `evidencias/` | Capturas XSS y artefactos de soporte. |

## Cómo reproducir la fase 1

1. Levantar el stack con `04. levantar-sistema.bat` y esperar los puertos.
2. Verificar el seed: `python infra/seed/verify_seed.py`.
3. Ejecutar la suite de claves: `python seguridad/pruebas/e2e_claves.py`.
4. Ejecutar la suite funcional: `python seguridad/pruebas/e2e_funcional.py`.
5. Pruebas Maven: `powershell -NoProfile -ExecutionPolicy Bypass -File seguridad/pruebas/ejecutar-tests-maven.ps1`.

La suite funcional crea un paciente y episodios temporales y los elimina al
finalizar. Con `--no-cleanup` se conservan para inspección manual. La suite
envía correos reales de 2FA, cita e historia clínica a la bandeja de pruebas.

## Cómo reproducir la fase 2

1. **SAST/SCA** (desde la raíz, con Docker):
   - `docker run --rm -v <repo>:/repo ghcr.io/gitleaks/gitleaks:latest detect --source /repo --config /repo/.gitleaks.toml --redact --no-git`
   - `docker run --rm -v <repo>:/src semgrep/semgrep semgrep scan --config p/security-audit --config p/secrets --metrics off /src`
   - `docker run --rm -v <repo>:/src -v ~/.m2:/root/.m2:ro aquasec/trivy fs --scanners vuln,secret,misconfig --offline-scan /src`
2. **Pentest local:** `python seguridad/pruebas/pentest_local.py` (requiere el stack y el seed).
3. **XSS con navegador:** `python seguridad/pruebas/pentest_xss.py prepare`, abrir
   `pacientes.html`/`lista-espera.html` con la cuenta temporal y ejecutar
   `python seguridad/pruebas/pentest_xss.py cleanup`.
4. **DAST:** `docker run --rm -v <repo>/seguridad/resultados:/zap/wrk:rw ghcr.io/zaproxy/zaproxy:stable zap-baseline.py -t http://host.docker.internal:5500 -r zap-baseline-r3.html -J zap-baseline-r3.json`

El limitador de login/2FA es en memoria: si se repiten las suites en menos de
10 minutos, reiniciar `ms-seguridad` limpia la ventana (solo en local).

## Reglas

- Solo se prueba contra `localhost`; no se ataca infraestructura externa.
- No se imprimen credenciales, tokens ni contenido de `.env`.
- Los datos del seed no se regeneran sin confirmación (el reset es destructivo).
- El push de esta rama requiere confirmación explícita.
