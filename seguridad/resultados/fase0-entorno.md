# Fase 0 — Preparación del entorno

**Fecha:** 2026-10-22
**Rama de trabajo:** `pruebas-seguridad-3` (creada desde `origin/main` en `7373082`)
**Alcance:** ronda 3 de pruebas funcionales y de seguridad. Esta rama solo
contiene pruebas, evidencias e informes; `main` no se modifica.

## Equipo y herramientas

| Componente | Versión / estado |
|---|---|
| Sistema | Windows 11, consola `cmd` |
| JDK | Eclipse Temurin 26.0.2 (`C:\Program Files\Apache NetBeans\jdk`) |
| Maven | Wrapper 3.9.16 (por módulo) |
| Docker Desktop | 29.9.2 (se levantó al inicio; estaba detenido) |
| Python | 3.12 (`python` en PATH) |
| Node.js | disponible (no se usó en la fase 1) |
| PostgreSQL | contenedor `sos-postgres`, puerto 5433 |
| MySQL | servicio local, puerto 3306 (`urgencias_hospital`) |

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
| Frontend (Python) | 5500 | UP |

Los servicios se levantaron con `04. levantar-sistema.bat`; Eureka reportó
`MS-SEGURIDAD`, `MS-PACIENTES`, `MS-TRIAGE`, `MS-ANALITICA` y `MS-IA-AGENTE`
en estado `UP`.

## Base de datos y seed

El seed no se regeneró: la base local ya contenía el conjunto canónico. Antes
de las pruebas se eliminaron dos residuos de uso manual (1 código 2FA y
1 registro de auditoría); no se borraron pacientes, episodios ni cuentas.

Verificación con `python infra/seed/verify_seed.py`:

- Login con 2FA y endpoint representativo: **9/9 cuentas PASS**.
- Conteos finales: 8 perfiles, 8 actividades, 22 asignaciones, 5 categorías,
  3 estados, 9 cuentas, 9 turnos, 24 pacientes, 30 episodios,
  0 códigos 2FA, 0 tokens de recuperación, 0 registros de auditoría.
- Resultado: `VERIFICACION COMPLETA OK`.

## Consideraciones

- El limitador de reenvíos 2FA permite 3 códigos por usuario cada 10 minutos.
  Repetir la suite E2E en ciclos muy cortos devuelve `429`; se debe esperar la
  ventana antes de otra corrida.
- MySQL y `ms-analitica` estaban disponibles, por lo que el tablero se probó
  con datos reales del generador sintético (50 000 llegadas).
- Las herramientas de análisis estático y DAST (gitleaks, semgrep, trivy, ZAP)
  no están instaladas localmente; en la fase 2 se ejecutarán vía Docker.
