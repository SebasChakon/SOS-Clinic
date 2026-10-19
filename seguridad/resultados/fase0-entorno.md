# Fase 0 — Preparación del entorno

| Campo | Valor |
|---|---|
| Fecha | 2026-10-19 |
| Rama | `pruebas-seguridad-2` |
| Commit | `e7ae581` |
| Sistema | Windows 11 |
| Docker | 29.7.2 |
| PostgreSQL | 16.15 (contenedor `sos-postgres`) |
| JDK | Temurin 26.0.2 (incluido en NetBeans) |
| Maven | 3.9.16 (wrapper) |
| Frontend | `http://localhost:5500` (Python HTTP Server) |
| Mailpit | `http://localhost:8025` (SMTP 1025) |

## Servicios levantados

| Servicio | Puerto | Estado |
|---|---|---|
| Eureka | 8761 | UP |
| ms-gateway | 8080 | UP |
| ms-seguridad | 8081 | UP |
| ms-pacientes | 8082 | UP |
| ms-triage | 8083 | UP |
| ms-analitica | 8084 | UP |
| ms-ia-agente | 8088 | UP |
| PostgreSQL | 5433 | UP |
| pgAdmin | 5050 | UP |
| Frontend | 5500 | UP |

Las seis aplicaciones aparecen registradas en Eureka (`MS-GATEWAY`,
`MS-SEGURIDAD`, `MS-PACIENTES`, `MS-TRIAGE`, `MS-ANALITICA`, `MS-IA-AGENTE`).

## Usuarios de prueba

| Usuario | Perfil | 2FA | Turno |
|---|---|---|---|
| `tester.admin` | `administrador` | No | 00:00–23:59 |
| `tester.triage` | `doctor_triage` | No | 00:00–23:59 |
| `tester.coord` | `coordinador_turnos` | No | 00:00–23:59 |
| `tester.2fa` | `doctor_triage` | Sí | 00:00–23:59 |

La contraseña de estos usuarios es local y desechable; no se versiona en el
repositorio. Los correos usan el dominio `@example.local` y se capturan en
Mailpit.

## Snapshots de base de datos

| Momento | Archivo |
|---|---|
| Antes de crear usuarios | `%TEMP%\opencode\sosclinic-fase0.dump` |
| Después de crear usuarios | `%TEMP%\opencode\sosclinic-fase0-post.dump` |

Los snapshots no se versionan: contienen datos y hashes locales.

## Baseline ejecutado

| Archivo | Descripción |
|---|---|
| `smoke-sin-token-2026-10-19.csv` | Peticiones GET sin token. |
| `smoke-token-triage-2026-10-19.csv` | Peticiones GET con JWT de `tester.triage`. |

Resultados relevantes (se confirmarán con evidencia en la Fase 2):

| Caso | Resultado observado |
|---|---|
| `GET /ms-pacientes/pacientes` sin token | 200 |
| `GET /ms-pacientes/pacientes/buscar?q=0` sin token | 200 |
| `GET /ms-pacientes/pacientes/signos-vitales/registro/1` sin token | 200 |
| `GET /ms-triage/triage/prioridad` sin token | 200 |
| `GET /ms-triage/triage/1` sin token | 200 |
| `GET /ms-seguridad/admin/*` sin token | 401 |
| `GET /ms-seguridad/admin/*` con JWT de `doctor_triage` | 200 |
| `GET /ms-analitica/dashboard/*` con JWT de `doctor_triage` | 403 |
| `GET /ms-analitica/dashboard/*` con JWT de `administrador` | 200 |
| `POST /ms-ia-agente/agente/preguntar` sin token | 200 |

## Observaciones de la fase

1. **Datos previos:** el volumen `infra_sos_pgdata` ya existía con 8 usuarios,
   15 pacientes y 28 episodios de pruebas. Se conservaron y se añadieron los
   cuatro usuarios de esta fase.
2. **Esquema alineado:** la base contiene `turno`, `codigo_verificacion`,
   `personal.requiere_2fa`, `paciente.email` y las columnas de alta; no fue
   necesario restaurar `sosclinic.dump`.
3. **Rol de analítica:** el perfil `coordinador_turnos` no coincide con el rol
   `COORDINACION` que exige `ms-analitica`; solo `administrador` accede al
   dashboard. Se documentará como observación funcional.
4. **Agente IA:** responde sin token; queda habilitado para las pruebas de
   prompt injection de la Fase 3.
5. **Correo:** Mailpit recibió el código 2FA y la verificación se completó
   correctamente.
6. **Ruido esperado:** durante los primeros segundos el gateway devolvió 503/500
   porque Eureka aún no había propagado las instancias; tras el registro, las
   rutas responden con normalidad.
