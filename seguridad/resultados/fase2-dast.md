# Fase 2 — DAST con OWASP ZAP

**Fecha:** 2026-10-22
**Objetivo:** frontend estático en `http://host.docker.internal:5500`
**Perfil:** ZAP Baseline (pasivo, sin ataques activos)
**Resultado:** 17 URLs analizadas, **62 PASS, 0 FAIL, 6 WARN**

## Advertencias y evaluación

| Regla | Ocurrencias | Evaluación |
|---|---|---|
| Cross-Domain JavaScript Source File Inclusion [10017] | 3 | Bajo. El landing incluye recursos de YouTube (iframe/script). Es una dependencia externa esperada, no una inclusión dinámica controlada por el usuario. |
| Non-Storable Content [10049] | 9 | Informativo. El servidor envía `Cache-Control: no-store` a propósito en páginas con sesión; es una decisión de seguridad, no un defecto. |
| CSP: Wildcard Directive [10055] | 12 | Bajo/medio. La CSP usa fuentes de esquema (`img-src https:`, `media-src blob:`) y `unsafe-inline`. Se relaciona con el hallazgo C-07 del pentest: una CSP estricta requiere retirar scripts y `onclick` inline. |
| Modern Web Application [10109] | 3 | Informativo. Detecta que es una SPA/HTML moderno; no es una vulnerabilidad. |
| Sub Resource Integrity Attribute Missing [90003] | 3 | Bajo. Los recursos locales son del mismo origen; el script de YouTube no lo controla la aplicación. |
| Cross-Origin-Embedder-Policy Header Missing or Invalid [90004] | 9 | Bajo. COEP es opcional y su ausencia no rompe el aislamiento actual (`COOP` y `CORP` sí están presentes). |

## Controles que pasaron

ZAP no encontró fallos en: cabeceras anti-clickjacking, `X-Content-Type-Options`,
CSP ausente, HSTS (el entorno local usa HTTP), divulgación de errores,
comentarios sospechosos, cookies (la app usa `sessionStorage`, no cookies),
enlaces a dominios maliciosos, mixed content, source code disclosure ni
tecnologías con versiones vulnerables.

Las cabeceras del frontend incluyen `X-Content-Type-Options: nosniff`,
`X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Permissions-Policy`,
`COOP`, `CORP` y una CSP con `unsafe-inline` (residual conocido).

## Evidencia

- `zap-baseline-r3.html` — informe navegable.
- `zap-baseline-r3.json` — resultados estructurados.
- `zap-baseline-r3.md` — resumen de ZAP.
