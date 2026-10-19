# Fase 1 — Análisis estático, secretos y dependencias

| Campo | Valor |
|---|---|
| Fecha | 2026-10-19 |
| Rama | `pruebas-seguridad-2` |
| Commit base | `d624c46` |
| Herramientas | gitleaks, Semgrep, Trivy 0.76.0 |
| Alcance | Código Java, frontend, configuración y `pom.xml` de los siete módulos |

## 1. Resumen

| Análisis | Resultado |
|---|---|
| Secretos (gitleaks, historial) | 2 hallazgos, ambos el mismo enlace de Power BI |
| Secretos (gitleaks, árbol de trabajo) | 3 hallazgos: 2 enlaces de Power BI y 1 archivo `env` con credenciales reales |
| SAST (Semgrep) | 0 hallazgos con 308 reglas (OWASP Top 10 + Java) y 159 reglas (security-audit) |
| SCA (Trivy) | 148 ocurrencias; **22 vulnerabilidades únicas**: 4 críticas, 12 altas, 6 medias |
| Configuración (Trivy config) | 0 archivos detectados; Docker Compose no es soportado por el escáner |
| Revisión manual | Secreto JWT versionado, contraseña en README, `show-sql` y credenciales de desarrollo |

## 2. Comandos ejecutados

```powershell
# Secretos en el historial
docker run --rm -e GIT_CONFIG_COUNT=1 -e GIT_CONFIG_KEY_0=safe.directory `
  -e GIT_CONFIG_VALUE_0=/repo -v "E:\Program Files\GIT\sos-clinic:/repo" `
  ghcr.io/gitleaks/gitleaks:latest detect --source=/repo --redact `
  --report-format=json `
  --report-path=/repo/seguridad/resultados/gitleaks-historial-2026-10-19.json

# Secretos en el arbol de trabajo
docker run --rm -v "E:\Program Files\GIT\sos-clinic:/repo" `
  ghcr.io/gitleaks/gitleaks:latest detect --source=/repo --no-git `
  --config=/repo/seguridad/utilidades/gitleaks.toml --redact `
  --report-format=json `
  --report-path=/repo/seguridad/resultados/gitleaks-arbol-2026-10-19.json

# SAST
docker run --rm -v "E:\Program Files\GIT\sos-clinic:/src" semgrep/semgrep `
  semgrep scan --config p/owasp-top-ten --config p/java --json `
  --output /src/seguridad/resultados/semgrep-2026-10-19.json `
  --exclude target --exclude build --exclude legacy --exclude .git /src

docker run --rm -v "E:\Program Files\GIT\sos-clinic:/src" semgrep/semgrep `
  semgrep scan --config p/security-audit --json `
  --output /src/seguridad/resultados/semgrep-security-audit-2026-10-19.json `
  --exclude target --exclude build --exclude legacy --exclude .git /src

# SCA con cache local de Maven para evitar el limite de Maven Central
docker run --rm -v "E:\Program Files\GIT\sos-clinic:/src" `
  -v "C:\Users\Oscar\.m2:/root/.m2" -v trivy-cache:/root/.cache/trivy `
  aquasec/trivy:latest fs --scanners vuln --offline-scan `
  --cache-dir /root/.cache/trivy --format json `
  --output /src/seguridad/resultados/trivy-sca-2026-10-19.json `
  --skip-dirs /src/.git --skip-dirs /src/legacy --skip-dirs /src/target `
  --skip-dirs /src/build --skip-dirs /src/logs /src

# Resumen del SCA
python seguridad\utilidades\resumir-trivy.py `
  seguridad\resultados\trivy-sca-2026-10-19.json `
  seguridad\resultados\trivy-sca-resumen-2026-10-19.csv
```

## 3. Secretos (gitleaks)

| Archivo | Línea | Regla | Evaluación |
|---|---|---|---|
| `frontend/enfermedades-crue.html` | 90, 97 | `grafana-api-key` | Probable falso positivo: es un enlace público de Power BI (`view?r=...`), no una clave de API. Confirmar con el propietario del reporte. |
| `env` | 16 | `generic-api-key` | **Real:** archivo con credenciales locales que no estaba en `.gitignore`. Se corrigió el `.gitignore`; el archivo sigue en disco y debe moverse fuera del repositorio. |
| Historial (`42f9d31`) | 77, 84 | `grafana-api-key` | Mismo enlace de Power BI en commits anteriores. |

Los reportes JSON están redactados (`Secret: REDACTED`); no contienen valores reales.

## 4. SAST (Semgrep)

- `p/owasp-top-ten` + `p/java`: 308 reglas, 226 archivos, **0 hallazgos**.
- `p/security-audit`: 159 reglas, 227 archivos, **0 hallazgos**.
- El escaneo se limitó a archivos rastreados por git y excluyó `target`, `build`, `legacy` y `.git`.

**Limitación:** la ausencia de hallazgos no demuestra ausencia de vulnerabilidades. La revisión manual identificó problemas que estos conjuntos de reglas no cubren (secreto JWT versionado, contraseña en README, CORS permisivo y falta de autorización).

## 5. SCA (Trivy)

Ocurrencias totales por módulo (incluye repeticiones entre módulos):

| Módulo | Ocurrencias |
|---|---|
| `eureka-server/pom.xml` | 22 |
| `ms-analitica/pom.xml` | 21 |
| `ms-gateway/pom.xml` | 21 |
| `ms-ia-agente/pom.xml` | 21 |
| `ms-pacientes/pom.xml` | 21 |
| `ms-seguridad/pom.xml` | 21 |
| `ms-triage/pom.xml` | 21 |

Tras deduplicar por CVE + paquete + versión: **22 únicas** (4 críticas, 12 altas, 6 medias).

### Críticas

| CVE | Paquete | Instalada | Corregida | Descripción |
|---|---|---|---|---|
| CVE-2026-65182 | `tomcat-embed-core` | 11.0.24 | 11.0.25 | Bypass de restricciones de acceso |
| CVE-2026-65905 | `tomcat-embed-core` | 11.0.24 | 11.0.25 | Bypass de autenticación DIGEST |
| CVE-2026-68525 | `tomcat-embed-core` | 11.0.24 | 11.0.25 | Acceso no autorizado vía FORM |
| CVE-2026-84939 | `freemarker` | 2.3.34 | 2.3.35 | Path traversal por locale malformado |

### Altas (agrupadas)

| Paquete | CVEs | Corrección |
|---|---|---|
| `jackson-databind` (2.21.5 y 3.1.5) | CVE-2026-68497, CVE-2026-91776, CVE-2026-91777 | 2.21.6/2.22.2 y 3.1.6/3.2.2 |
| `jackson-core` (2.21.5 y 3.1.5) | CVE-2026-89407, CVE-2026-89425 | 2.21.7/2.22.3 y 3.1.7/3.2.3 |
| `httpcore5` / `httpcore5-h2` | CVE-2026-54399, CVE-2026-54428 | 5.4.3 |

El detalle completo está en `trivy-sca-resumen-2026-10-19.csv`.

## 6. Configuración

`trivy config infra/` detectó **0 archivos de configuración**. Trivy 0.76 no evalúa
`docker-compose.yml`, por lo que el resultado no equivale a “configuración limpia”.

Revisión manual de `infra/docker-compose.yml`:

- PostgreSQL publicado en `5433` con usuario `sos_admin` y contraseña de desarrollo.
- pgAdmin publicado en `5050` con `admin123`.
- Ambos servicios escuchan en `0.0.0.0`; en una red compartida quedarían expuestos.
- No hay TLS local (aceptable en desarrollo, no en producción).

## 7. Hallazgos manuales

| ID | Severidad | Hallazgo | Evidencia |
|---|---|---|---|
| S-01 | Crítica | Secreto JWT por defecto versionado, compartido por `ms-seguridad` y `ms-analitica` | `application.properties:19` y `:16` |
| S-02 | Crítica | Archivo `env` con credenciales reales fuera de `.gitignore` | `env:16` (ahora ignorado) |
| S-03 | Alta | Contraseña de aplicación de Gmail en el README | `README.md:487` |
| S-04 | Media | Contraseña generada por Spring Security escrita en el log | `logs/seguridad.log:124` |
| S-05 | Media | Credenciales de desarrollo y puertos expuestos | `infra/docker-compose.yml` |
| S-06 | Media | `show-sql=true` en tres servicios | `ms-seguridad`, `ms-pacientes`, `ms-triage` |
| S-07 | Baja | Enlaces de Power BI marcados como `grafana-api-key` | `frontend/enfermedades-crue.html` |

## 8. Recomendaciones

1. **Rotar el secreto JWT** y definirlo solo por `APP_JWT_SECRET`; eliminar el valor por defecto de ambos `application.properties`.
2. **Mover `env` fuera del repositorio** (gestor de contraseñas) y rotar la contraseña de aplicación de Gmail y la clave de Groq si estuvieron expuestas.
3. **Actualizar dependencias**: Tomcat 11.0.25+, FreeMarker 2.3.35, Jackson y HttpComponents a las versiones corregidas; repetir Trivy.
4. **Desactivar `show-sql`** y no registrar secretos ni códigos 2FA en logs.
5. **Endurecer Compose**: no publicar PostgreSQL/pgAdmin fuera de localhost y usar credenciales no triviales.
6. **Mantener gitleaks** como hook/pre-commit y conservar la allowlist de artefactos.

## 9. Evidencia

| Archivo | Contenido |
|---|---|
| `gitleaks-historial-2026-10-19.json` | Hallazgos en el historial (redactado) |
| `gitleaks-arbol-2026-10-19.json` | Hallazgos en el árbol de trabajo (redactado) |
| `semgrep-2026-10-19.json` | SAST OWASP/Java |
| `semgrep-security-audit-2026-10-19.json` | SAST security-audit |
| `trivy-sca-2026-10-19.json` | Reporte SCA completo |
| `trivy-sca-resumen-2026-10-19.csv` | Resumen deduplicado |
| `trivy-config-2026-10-19.json` | Configuración (sin archivos detectados) |
