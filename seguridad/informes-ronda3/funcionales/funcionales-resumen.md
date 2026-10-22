# Pruebas funcionales E2E — resumen

**Fecha:** 2026-10-22 · **Rama:** `pruebas-seguridad-3`

## Qué se probó

Flujos clínicos y administrativos de punta a punta por API, más un smoke test
del frontend estático, sobre el stack local completo (Eureka, gateway, seis
servicios, PostgreSQL, MySQL sintético y frontend).

## Resultados

| Suite | Resultado |
|---|---|
| Seed (`verify_seed.py`) | 9/9 cuentas, conteos correctos |
| Claves y cuentas (`e2e_claves.py`) | **47/47 PASS** |
| Funcional E2E (`e2e_funcional.py`) | **74 PASS, 0 FAIL, 1 WARN** |
| Frontend | 14 páginas + `api.js`, todas 200 |

Cobertura: login con 2FA por rol, cambio/recuperación de clave, aprobación de
cuentas, pacientes, triage, signos vitales, alta con 3 correos y PDF,
reintento sin duplicar el alta, analítica, agente IA, auditoría y turnos.

## Hallazgos

1. **Menor:** cédula de más de 12 caracteres devuelve 409 genérico en vez de 400.
2. **Transitorio:** el correo con PDF puede agotar el timeout SMTP; el reintento
   funciona y las corridas posteriores enviaron los 3 correos.
3. **Observación:** el alta tarda 16–24 s por el envío síncrono.
4. **Observación:** el limitador de 2FA (3 códigos/10 min) obliga a esperar
   entre corridas; es comportamiento esperado.

## Evidencia

- `seguridad/resultados/fase1-funcional.csv`
- `seguridad/resultados/fase1-funcional.md`
- `seguridad/resultados/fase1-claves.txt`
- `seguridad/resultados/fase0-entorno.md`

## Conclusión

Sin fallos bloqueantes. Mejoras acotadas para `main`: validación de longitud y
envío de correos asíncrono/outbox.
