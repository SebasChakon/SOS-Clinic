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
| `pruebas/e2e_funcional.py` | Suite E2E de flujos clínicos y administrativos. |
| `pruebas/ejecutar-tests-maven.ps1` | Ejecuta las pruebas Maven de los 7 módulos. |
| `pruebas/e2e_claves.py` | Suite existente de claves, 2FA, recuperación y aprobaciones (heredada de `main`). |
| `evidencias/` | Capturas y artefactos de soporte. |

## Cómo reproducir la fase 1

1. Levantar el stack con `04. levantar-sistema.bat` y esperar los puertos.
2. Verificar el seed: `python infra/seed/verify_seed.py`.
3. Ejecutar la suite de claves: `python seguridad/pruebas/e2e_claves.py`.
4. Ejecutar la suite funcional: `python seguridad/pruebas/e2e_funcional.py`.
5. Pruebas Maven: `powershell -NoProfile -ExecutionPolicy Bypass -File seguridad/pruebas/ejecutar-tests-maven.ps1`.

La suite funcional crea un paciente y episodios temporales y los elimina al
finalizar. Con `--no-cleanup` se conservan para inspección manual. La suite
envía correos reales de 2FA, cita e historia clínica a la bandeja de pruebas.

## Reglas

- Solo se prueba contra `localhost`; no se ataca infraestructura externa.
- No se imprimen credenciales, tokens ni contenido de `.env`.
- Los datos del seed no se regeneran sin confirmación (el reset es destructivo).
- El push de esta rama requiere confirmación explícita.
