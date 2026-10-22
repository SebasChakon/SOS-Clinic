# DAST con ZAP — resumen

**Fecha:** 2026-10-22 · **Rama:** `pruebas-seguridad-3`

## Qué se hizo

Escaneo **baseline pasivo** con OWASP ZAP contra el frontend estático en
`http://host.docker.internal:5500`, usando la imagen oficial
`ghcr.io/zaproxy/zaproxy:stable`. Se analizaron 17 URLs sin autenticación.

## Resultado

| Métrica | Valor |
|---|---|
| URLs | 17 |
| PASS | **62** |
| FAIL | **0** |
| WARN | **6** |

Las 6 advertencias son de bajo impacto o intencionales:

1. Inclusión de JavaScript de YouTube (esperada).
2. `Cache-Control: no-store` (decisión de seguridad).
3. CSP con fuentes de esquema y `unsafe-inline` (residual F-03).
4. Detección de aplicación web moderna (informativo).
5. Falta de SRI en recursos externos (bajo).
6. Falta de COEP (opcional; COOP/CORP presentes).

## Controles correctos

Cabeceras anti-clickjacking, `nosniff`, CSP presente, sin cookies, sin mixed
content, sin divulgación de errores ni tecnologías vulnerables.

## Evidencia

- `seguridad/resultados/zap-baseline-r3.html`
- `seguridad/resultados/zap-baseline-r3.json`
- `seguridad/resultados/zap-baseline-r3.md`

## Conclusión

Sin fallos pasivos. La mejora prioritaria es endurecer la CSP, ya cubierta en el
plan de mejoras para `main`.
