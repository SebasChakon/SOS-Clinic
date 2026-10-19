# Fase 3 — DAST, frontend e infraestructura

| Campo | Valor |
|---|---|
| Fecha | 2026-10-19 |
| Rama | `pruebas-seguridad-2` |
| Herramienta | OWASP ZAP 2.17.0 (baseline) |
| Objetivo web | `http://host.docker.internal:5500` (frontend estático) |
| API | Pruebas manuales de la Fase 2 |

## 1. ZAP baseline

Comando:

```powershell
docker run --rm -t -v "E:\Program Files\GIT\sos-clinic\seguridad\resultados:/zap/wrk:rw" `
  zaproxy/zap-stable zap-baseline.py `
  -t http://host.docker.internal:5500/index.html `
  -r zap-baseline-frontend-2026-10-19.html `
  -J zap-baseline-frontend-2026-10-19.json -I
```

Resultado: **0 altas, 3 medias, 7 bajas, 1 informativa, 58 pruebas superadas**.

| Riesgo | Alerta | Instancias |
|---|---|---|
| Media | Content Security Policy (CSP) Header Not Set | 5 |
| Media | Missing Anti-clickjacking Header | 3 |
| Media | Sub Resource Integrity Attribute Missing | 2 |
| Baja | Cross-Domain JavaScript Source File Inclusion | 2 |
| Baja | Cross-Origin-Embedder-Policy Header Missing or Invalid | 2 |
| Baja | Cross-Origin-Opener-Policy Header Missing or Invalid | 2 |
| Baja | Cross-Origin-Resource-Policy Header Missing or Invalid | 3 |
| Baja | Permissions Policy Header Not Set | 5 |
| Baja | Server Leaks Version Information | 5 |
| Baja | X-Content-Type-Options Header Missing | 5 |
| Informativa | Storable and Cacheable Content | 5 |

**Nota:** el frontend se sirvió con `python -m http.server`; la fuga de versión
del servidor corresponde a ese servidor de pruebas, no a la aplicación. En un
despliegue real debe revisarse la cabecera `Server` del servidor definitivo.

Evidencia: `evidencias/21_zap_baseline.png`, reporte HTML y JSON en
`resultados/`.

## 2. Agente IA — endpoint de voz

`POST /ms-ia-agente/agente/transcribir` no exige autenticación. Se envió un WAV
silencioso de 0,2 s sin token:

```text
HTTP/1.1 500 Internal Server Error
```

El 500 proviene del procesamiento del audio (Groq), no de un rechazo de
autenticación: el endpoint acepta y procesa la solicitud sin credenciales. Esto
permite abuso de la cuota de Groq y consumo de recursos.

Evidencia: prueba ejecutada en la Fase 3; se documenta en el hallazgo P-17.

## 3. Infraestructura expuesta

| Servicio | Puerto | Acceso |
|---|---|---|
| Eureka | 8761 | HTTP 200 sin autenticación |
| pgAdmin | 5050 | HTTP 200, credenciales de desarrollo en Compose |
| PostgreSQL | 5433 | `sos_admin` / contraseña de desarrollo |
| Mailpit | 8025 | HTTP 200, correo de pruebas |
| Frontend | 5500 | HTTP sin TLS |

`infra/docker-compose.yml` publica PostgreSQL y pgAdmin en `0.0.0.0`. En una
red compartida quedarían accesibles desde otros equipos. Las credenciales
`admin123` y `sos_pass_dev` son de desarrollo y no deben reutilizarse.

## 4. Control de navegación en el frontend

`frontend/assets/api.js` implementa `exigirSesion()` en JavaScript. La
verificación se ejecuta en el navegador y puede omitirse navegando directamente
a la página; además, las APIs de pacientes y triage no validan el token. La
sesión se guarda en `sessionStorage`, por lo que un XSS puede extraer el JWT
(hallazgo P-05).

## 5. Hallazgos nuevos

| ID | Severidad | Hallazgo |
|---|---|---|
| P-17 | Media | `/agente/transcribir` sin autenticación y sin límite de tamaño |
| P-18 | Media | Cabeceras de seguridad ausentes y configuración web débil (ZAP) |
| P-19 | Media | Eureka, pgAdmin y PostgreSQL expuestos en la red local |
| P-20 | Baja | Control de navegación solo en el cliente |

## 6. Evidencia

| Archivo | Contenido |
|---|---|
| `evidencias/21_zap_baseline.png` | Resumen del reporte ZAP |
| `resultados/zap-baseline-frontend-2026-10-19.html` | Reporte ZAP completo |
| `resultados/zap-baseline-frontend-2026-10-19.json` | Datos estructurados de ZAP |
| `resultados/zap.yaml` | Configuración generada por el baseline |
