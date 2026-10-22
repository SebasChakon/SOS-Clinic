# Plan de mejoras para `main` (a partir de la ronda 3)

Orden sugerido por impacto y esfuerzo. Cada punto puede ir en una rama `fix/*`
independiente con su prueba.

## 1. Validación de entrada en `ms-seguridad` (F-01) — alta prioridad

**Problema:** `POST /auth/login {}` y `POST /auth/recuperar-clave {}` devuelven
500 porque `ApiExceptionHandler` no maneja `MethodArgumentNotValidException`.
**Cambio:** copiar el manejador de `ms-pacientes`/`ms-triage` que devuelve 400
con el mensaje del primer campo inválido. Añadir pruebas de controller.
**Evidencia:** A-15/A-16 de `fase2-pentest.csv`.

## 2. Historia clínica PDF por rol (F-02) — alta prioridad

**Problema:** `recepcionista` y `lista_espera` descargan el PDF completo de
cualquier episodio.
**Cambio:** restringir `/pacientes/historias/**` a `ADMINISTRADOR` y `MEDICO`;
si se quiere más granularidad, validar que el episodio esté asignado al
profesional. Agregar el caso a la matriz de autorización.
**Evidencia:** I-04-recepcion / I-04-listas.

## 3. Validación de longitud de paciente (fase 1) — media

**Problema:** una cédula de más de 12 caracteres devuelve 409 genérico.
**Cambio:** `@Size(max = 12)` en `PacienteRequest.cedula` y límites para
nombre/apellido/email; probar 400.

## 4. Envío de correos del alta (F-06) — media

**Problema:** el alta espera 3 envíos SMTP (16–24 s) y el PDF puede agotar el
timeout; el reintento funciona pero no es determinista.
**Cambio:** outbox/cola con reintentos, o envío asíncrono con estado
persistido; aumentar el timeout solo para adjuntos grandes.

## 5. CSP estricta (F-03) — media

**Problema:** `script-src 'unsafe-inline'` y `style-src 'unsafe-inline'`.
**Cambio:** mover scripts y `onclick` inline a `assets/*.js`, eliminar
`unsafe-inline` y ajustar `connect-src`/`img-src` a orígenes concretos.

## 6. Propiedad de episodios/pacientes (F-04) — baja / evolución

**Problema:** cualquier rol clínico accede a cualquier episodio (IDOR
horizontal aceptado).
**Cambio:** asociar episodio a profesional/asignación y validar en cada lectura
y en el PDF. Requiere decisión funcional.

## 7. Secreto histórico de Grafana (F-05) — baja

**Problema:** gitleaks encuentra `grafana-api-key` en el historial
(`frontend/enfermedades-crue.html`, ya eliminado).
**Cambio:** rotar el valor si fue real; opcionalmente documentar la limpieza
del historial (requiere reescribir, no se recomienda sin coordinación).

## 8. CI — media

El `.gitlab-ci.yml` actual solo ejecuta gitleaks sobre el árbol y 6 pruebas de
`ms-seguridad`. Sugerido:

- Ejecutar `mvnw -B test` en los 7 módulos (49 pruebas actuales).
- Añadir gitleaks sobre el historial (permitiendo fallos conocidos y
  documentados).
- Programar (no en cada push) el pentest local y el E2E contra un entorno
  efímero, o convertirlo en un job manual.
- Publicar los reportes como artefactos.

## 9. Limitador de intentos en memoria — media / producción

Login y 2FA usan `ConcurrentHashMap`. Para más de una instancia hace falta
Redis o columnas en base de datos; ya estaba identificado en la ronda 2.

## 10. Pruebas que conviene conservar

- `seguridad/pruebas/e2e_funcional.py` (74 comprobaciones).
- `seguridad/pruebas/pentest_local.py` (matriz de 8 roles y 11 endpoints).
- `seguridad/pruebas/pentest_xss.py` (escenario de XSS con navegador).
- `seguridad/pruebas/ejecutar-tests-maven.ps1`.

No hace falta mover a `main` las capturas ni los JSON de evidencia; sí los
scripts y un resumen de resultados.
