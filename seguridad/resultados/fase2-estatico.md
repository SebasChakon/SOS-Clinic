# Fase 2 — Análisis estático (SAST/SCA)

**Fecha:** 2026-10-22
**Herramientas:** gitleaks, Semgrep y Trivy, todas vía Docker.

## 1. Gitleaks (secretos)

| Escaneo | Resultado | Evidencia |
|---|---|---|
| Árbol de trabajo (`--no-git`) | **0 hallazgos** (15,72 MB) | `gitleaks-arbol-r3.json` |
| Historial de Git (45 commits) | **3 hallazgos históricos** | `gitleaks-historial-r3.json` |

Los 3 hallazgos del historial son de la regla `grafana-api-key` en
`frontend/enfermedades-crue.html` (commits `d0fb56f` y `42f9d31`). Ese archivo
**ya no existe** en el árbol actual. No es una exposición vigente, pero si el
valor fue una clave real debe rotarse; borrarlo del árbol no lo elimina del
historial.

El escaneo del árbol no encuentra `.env` ni credenciales actuales: el archivo
local está ignorado por Git (`git check-ignore .env` lo confirma) y no está
versionado.

## 2. Semgrep (código)

Se ejecutaron las reglas `p/security-audit` y `p/secrets` sobre 302 archivos
versionados (200 reglas): **9 hallazgos, todos en scripts de prueba locales**,
ninguno en código de producción.

| Regla | Archivo | Evaluación |
|---|---|---|
| `detected-bcrypt-hash` (x3) | `seguridad/pruebas/e2e_claves.py`, `e2e_funcional.py`, `pentest_local.py`, `pentest_xss.py` | **Falso positivo**: hashes de la clave de fixture `E2e#2026!`, usada solo en cuentas temporales de pruebas. |
| `dynamic-urllib-use-detected` (x6) | `infra/seed/verify_seed.py`, scripts E2E | **Falso positivo**: las URLs son fijas (`localhost`) o vienen de configuración controlada. |

Evidencia: `semgrep-r3.json`.

## 3. Trivy (dependencias, secretos y configuración)

| Escaneo | Resultado | Evidencia |
|---|---|---|
| `fs` sobre los 7 `pom.xml` | **0 vulnerabilidades, 0 secretos, 0 misconfiguraciones** | `trivy-r3.json` |
| `config` sobre `infra/` | 0 archivos de configuración detectados | `trivy-config-r3.json` |

La primera ejecución falló porque Maven Central respondió `429 Too Many
Requests` al resolver los BOM. Se repitió con la caché local `~/.m2` montada y
modo offline; el resultado es el de la tabla.

Trivy no detectó `docker-compose.yml` como archivo de configuración (limitación
de la versión de la imagen). La revisión manual confirma:

- PostgreSQL y pgAdmin publican solo en `127.0.0.1` (puertos 5433 y 5050).
- `POSTGRES_PASSWORD` y `PGADMIN_DEFAULT_PASSWORD` son obligatorios vía `.env`;
  no hay credenciales por defecto.
- `spring.jpa.show-sql=false` y los secretos (`DB_PASSWORD`, `APP_JWT_SECRET`,
  `MAIL_PASSWORD`) se leen de variables de entorno.

## Conclusión

No hay secretos vigentes ni vulnerabilidades conocidas en dependencias. Los
únicos hallazgos del análisis estático están en artefactos de prueba y en el
historial de Git (clave antigua ya retirada del árbol).
