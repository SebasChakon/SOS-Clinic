# Fase 1 — Pruebas funcionales (resultado)

**Fecha:** 2026-10-22
**Rama:** `pruebas-seguridad-3`
**Alcance:** flujos funcionales por API, frontend estático y pruebas Maven.
**Estado:** completada sin fallos bloqueantes.

## Resumen de ejecución

| Suite | Resultado | Evidencia |
|---|---|---|
| Smoke del seed (`infra/seed/verify_seed.py`) | 9/9 cuentas, conteos correctos | `fase0-entorno.md` |
| Claves, 2FA, recuperación y aprobaciones (`e2e_claves.py`) | **47/47 PASS** | `fase1-claves.txt` |
| Funcional E2E (`e2e_funcional.py`) | **74 PASS, 0 FAIL, 1 WARN** | `fase1-funcional.csv` |
| Maven por módulo (`ejecutar-tests-maven.ps1`) | **7/7 PASS, 49 tests** | `maven/resumen.md` |

La suite funcional se ejecutó en 53,4 s y dejó la base sin datos de prueba
(paciente, episodios, tomas, auditoría y cuenta de turno eliminados).

## Cobertura funcional

- **Frontend:** 14 páginas y `assets/api.js` responden 200 con su contenido
  esperado (login, recuperación, cambio de clave, cuenta, aprobaciones,
  pacientes, triage, lista de espera, seguimiento, auditoría, roles, dashboard).
- **Autenticación:** login de administrador, médico, coordinación y auditor con
  2FA; perfil y sesión correctos.
- **Pacientes:** listado, alta, cédula duplicada (409), búsqueda exacta y
  parcial, validación de campos (400), acceso sin token (401).
- **Triage:** creación, estado inicial, consulta, cambio de ubicación, lista
  priorizada y ordenada, cálculo de espera, paso a atención y alta.
- **Signos vitales:** dos tomas conservadas, autor tomado del JWT, rango
  inválido rechazado (400).
- **Alta orquestada:** diagnóstico/tratamiento persistidos antes de notificar,
  tres correos evaluados (`teleconsulta-profesional`, `cita-paciente`,
  `historia-pdf`), PDF válido (26 KB), reintento de correos sin duplicar el alta
  y 404 para episodio inexistente.
- **Analítica:** resumen, llegadas y capacidad con rol coordinación; 403 para
  rol clínico, 401 sin token y 400 con rango invertido.
- **Agente IA:** 401 sin token, 400 con mensaje vacío y respuesta real de Groq.
- **Auditoría:** registro autenticado, visibilidad para administrador y 403 para
  médico en rutas de administración.
- **Turnos:** login rechazado (403) fuera del turno configurado.

## Hallazgos

### 1. Cédula fuera de longitud devuelve 409 genérico (menor)

`PacienteRequest` no limita la longitud de la cédula. La columna admite 12
caracteres, así que una cédula más larga provoca `DataIntegrityViolationException`
y `ApiExceptionHandler` la traduce a **409** con el mensaje genérico de datos
inválidos, en lugar de un **400** de validación.

- Evidencia: `fase1-funcional.csv`, caso `pacientes / cedula fuera de longitud`.
- Recomendación: agregar `@Size(max = 12)` (y límites equivalentes para nombre,
  apellido y email) en `PacienteRequest`, o mapear ese caso a 400.

### 2. El correo con PDF puede agotar el tiempo de escritura SMTP (transitorio)

En una corrida preliminar, `historia-pdf` quedó en `FALLIDO` con
`java.io.IOException: Write timed out`, mientras los otros dos correos se
enviaron. En la corrida limpia y en el reintento, los tres correos quedaron en
`ENVIADO`. El alta se persistió correctamente en todos los casos.

- Impacto: el flujo es tolerante (estado por correo + reintento), pero la
  entrega del PDF no es determinista con SMTP lento.
- Recomendación: mover el envío a un proceso asíncrono/outbox y aumentar el
  timeout solo para adjuntos grandes, sin bloquear la respuesta del alta.

### 3. El alta tarda 16–24 s por el envío síncrono de correos (observación)

La llamada `finalizar` espera los tres envíos SMTP. Es funcional, pero afecta la
experiencia en la pantalla de seguimiento. Se relaciona con el hallazgo 2.

### 4. Limitador de reenvíos 2FA (observación, no es defecto)

Se permiten 3 códigos por usuario cada 10 minutos. Repetir la suite E2E en
ciclos cortos devuelve 429; hay que esperar la ventana. El comportamiento es el
esperado según `LoginAttemptService`.

### 5. Datos sintéticos de analítica (observación)

El tablero reporta 50 000 llegadas y una espera promedio de ~102 minutos sobre
la base sintética. Es el comportamiento esperado del generador, no un defecto
de la aplicación.

## Conclusión

Los flujos funcionales cubiertos pasan en su totalidad. No hay fallos
bloqueantes; los dos primeros hallazgos son mejoras acotadas para `main`.
La fase 2 (seguridad) todavía no se ejecutó en esta rama.
