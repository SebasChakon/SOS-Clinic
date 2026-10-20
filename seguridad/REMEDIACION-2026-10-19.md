# Remediacion de hallazgos de seguridad - 2026-10-19

Rama: `main`
Alcance: gateway, seguridad, pacientes, triage, analitica, agente IA, frontend e
infraestructura local.

Este documento resume las correcciones aplicadas despues del informe de la rama
`pruebas-seguridad-2`. No contiene tokens ni credenciales.

## Estado por hallazgo

| ID | Hallazgo | Estado | Evidencia / prueba |
|---|---|---|---|
| H-01 | Secreto JWT por defecto y forja de tokens | Remediado | Fallback eliminado; `APP_JWT_SECRET` obligatorio; token firmado con el secreto viejo -> 401; token nuevo -> 200. |
| H-02 | Lectura de pacientes y triage sin autenticacion | Remediado | Sin token -> 401 en gateway y en puertos directos. |
| H-03 | Escritura y altas sin autenticacion | Remediado | POST/PUT sin token -> 401; roles clinicos requeridos. |
| H-04 | Escalada de privilegios en `/admin/**` | Remediado | `doctor_triage` -> 403; `administrador` -> 200. |
| H-05 | Archivo `env` con credenciales reales | Mitigado | `env`/`.env` ignorados; `.env` local no versionado; rotar credenciales externas. |
| H-06 | Dependencias criticas vulnerables | Remediado | Tomcat 11.0.25, FreeMarker 2.3.35; Trivy sin criticas/altas. |
| H-07 | XSS almacenado | Remediado | Playwright: 0 dialogos y 0 `<img>` inyectadas en pacientes, seguimiento y lista de espera. |
| H-08 | Mass assignment en signos vitales | Remediado | `SignosVitalesRequest`; `idPersonal` sale del JWT; `idEstado`/fecha del servidor. |
| H-09 | IDOR en pacientes, triage e historias | Remediado (por rol) | Sin token -> 401; roles clinicos/lectura segun matriz. No hay propiedad por episodio. |
| H-10 | Agente IA sin autenticacion expone datos clinicos | Remediado | `/agente/**` exige JWT; contexto del cliente ignorado; nombres/cedulas no se envian a Groq. |
| H-11 | CORS permisivo con credenciales | Remediado | Origen `evil.local` -> 403; `localhost:5500` -> 200 con ACAO. |
| H-12 | 2FA sin limite de intentos | Remediado | Codigo BCrypt, `SecureRandom`, invalidacion de codigos previos, 5 fallos -> 429. |
| H-13 | Contrasena de Gmail en README | Remediado en el arbol | Valor reemplazado por placeholder; el historial conserva el valor antiguo, rotar. |
| H-14 | Dependencias altas vulnerables | Remediado | Jackson 2.21.7/3.1.7, HttpCore 5.4.3, HttpClient 4.5.13, HttpClient5 5.6.3; Trivy sin altas. |
| H-15 | Login sin bloqueo | Remediado | 5 fallos -> 429; el login correcto queda bloqueado hasta cumplir la ventana. |
| H-16 | Envio de correo sin autenticacion | Remediado | Endpoint exige JWT y rol clinico. |
| H-17 | Enumeracion de usuarios | Remediado | Mensajes genericos y BCrypt dummy para usuarios inexistentes/inactivos. |
| H-18 | Cabeceras de seguridad ausentes | Remediado parcial | Cabeceras en gateway y frontend; CSP con `unsafe-inline` por los scripts existentes. |
| H-19 | Infraestructura expuesta y credenciales de desarrollo | Remediado | Puertos en 127.0.0.1; credenciales por `.env`; sin root/root en scripts. |
| H-20 | `show-sql` y secretos en logs | Remediado | `show-sql=false`; sin codigo 2FA ni password generada en logs. |
| H-21 | Voz del agente sin autenticacion | Remediado | `/agente/transcribir` exige JWT; limite de 10 MB y rate limit. |
| H-22 | Errores 500 por referencias inexistentes | Remediado | Manejadores globales; JSON invalido -> 400; conflictos -> 409. |
| H-23 | Control de navegacion solo en el cliente | Remediado | Autorizacion real en gateway y servicios; el frontend queda como UX. |
| H-24 | Rol de analitica mal nombrado | Remediado | `coordinador_turnos` se normaliza a `ROLE_COORDINACION`. |
| H-25 | Falso positivo de Power BI en gitleaks | Documentado | `.gitleaks.toml` con allowlist del enlace publico. |

## Pruebas ejecutadas

- `mvn test` en los siete modulos: todos en verde.
- Trivy SCA (criticas/altas): 0 en los siete `pom.xml`.
- gitleaks sobre el arbol: sin hallazgos (el `.env` local esta excluido y no se versiona).
- E2E con servicios locales y PostgreSQL:
  - Sin token: 401 en pacientes, triage, admin y agente.
  - Admin: 200 en `/admin/personal`; doctor: 403.
  - Coordinacion: lectura 200, escritura 403; PDF 200.
  - 2FA: codigo correcto 200; cinco fallos -> 429; codigo correcto bloqueado -> 429.
  - Login: cinco fallos -> 429; mensajes genericos.
  - CORS: origen permitido 200; origen no permitido 403.
  - Mass assignment: `idPersonal` del cuerpo ignorado; se guarda el del JWT.
  - XSS: Playwright sin dialogos en pacientes, seguimiento y lista de espera.
  - Agente: respuesta sin nombres de pacientes; rate limit 20/min.

## Migracion necesaria en bases existentes

```powershell
docker exec -i sos-postgres psql -U sos_admin -d sosclinic `
  < infra/migraciones/2026-10-19-seguridad.sql
```

La migracion amplia `codigo_verificacion.codigo` a `VARCHAR(255)` para el hash
BCrypt y crea/actualiza `turno` y `personal.requiere_2fa` si faltan.

## Limpieza de actividades duplicadas

Se detecto que `pacientes.html` tenia dos actividades: `registrar_paciente` (id 1) y
`pacientes` (id 11). La segunda se creo durante las pruebas y estaba asignada a
`administrador`, `doctor_triage` y `coordinador_turnos`, produciendo dos entradas
"Pacientes" en el menu.

- Se conservo `registrar_paciente` como actividad canonica.
- Se eliminaron la actividad duplicada y sus asignaciones.
- Se quitaron asignaciones de prueba que no correspondian a la matriz de roles
  (auditoria/roles/analitica para `doctor_triage`; escritura clinica/auditoria/roles
  para `coordinador_turnos`).
- Se agregaron indices unicos por nombre y por enlace en `actividades` y
  validacion en `ActividadController` para responder 409 ante duplicados.
- La migracion reproducible es `infra/migraciones/2026-10-19-actividades.sql`.

Backup previo a la limpieza: `%TEMP%\opencode\backup-actividades-perfil-actividad.sql`
(solo local, no versionado).

## Riesgos residuales

- El logout sigue siendo local (JWT sin estado); no hay lista de revocacion.
- El limite de intentos es en memoria: en varias instancias debe moverse a Redis
  o a columnas de base de datos.
- H-09 queda cerrado por rol, no por propiedad de cada episodio.
- La CSP conserva `unsafe-inline`; una CSP estricta requiere retirar los scripts
  y `onclick` inline del frontend.
- El entorno local usa HTTP sin HSTS; en produccion debe ir detras de TLS.
- Los secretos antiguos siguen en el historial de Git. Deben rotarse; borrarlos
  del arbol no los invalida.
- El agente envia a Groq solo conteos y agregados; no nombres ni cedulas.
