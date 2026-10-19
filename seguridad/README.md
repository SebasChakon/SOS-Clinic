# Pruebas de seguridad — SOS Clinic

Rama de trabajo: `pruebas-seguridad-2`, creada desde `main` en
`50f8bea0969f0824d511d7cbc24de413ff74931d`.

Estado: **en ejecución**. La estructura, el plan y las utilidades están listos;
falta levantar el entorno local y ejecutar los casos para completar el informe
con evidencias.

## Objetivo

Documentar las pruebas de seguridad del prototipo SOS Clinic con evidencia
reproducible: capturas de pantalla, solicitudes y respuestas, y salidas crudas
de las herramientas. El marco de referencia es OWASP WSTG, OWASP API Security
Top 10 2023, OWASP ASVS 4.0, ISO/IEC 27002:2022 y CVSS 4.0.

## Alcance

Incluido:

- `ms-gateway`, `ms-seguridad`, `ms-pacientes`, `ms-triage`, `ms-analitica`
  y `ms-ia-agente`.
- Frontend estático (`frontend/`): sesión, control de navegación y XSS.
- Infraestructura local (`infra/docker-compose.yml`): puertos, credenciales y
  configuración de desarrollo.

Excluido:

- `legacy/` (prototipo Java EE que no forma parte del arranque actual).
- Datos clínicos reales.
- Cualquier entorno de producción o red externa al equipo local.

## Estructura

| Ruta | Contenido |
|---|---|
| `PLAN.md` | Plan de ejecución, fases, matriz de pruebas y criterios de evidencia. |
| `informe/informe-seguridad.md` | Informe narrativo; se completa con los resultados. |
| `evidencias/` | Capturas de pantalla y evidencia visual por caso. |
| `resultados/` | Salidas crudas de Trivy, Semgrep, gitleaks, ZAP y scripts. |
| `utilidades/` | Scripts reproducibles para las pruebas manuales. |

## Reglas de evidencia

1. Ejecutar solo contra el entorno local y con datos sintéticos.
2. Usar Mailpit (`http://localhost:8025`) para todo correo; nunca el SMTP real.
3. Difuminar tokens, contraseñas, cédulas, correos y cualquier dato personal
   antes de guardar una captura.
4. Nombrar las capturas con el patrón `NN_descripcion_corta.png`, donde `NN`
   coincide con el ID del caso.
5. No versionar `.env`, binarios de herramientas, dumps de base de datos ni
   respaldos `.bundle`.
6. Registrar en cada evidencia la fecha, la rama y el commit probado.

## Fases

| Fase | Contenido | Estado |
|---|---|---|
| 0 | Preparación del entorno, snapshot y usuarios de prueba | Completada |
| 1 | Análisis estático, secretos y dependencias (SCA/SAST) | Completada |
| 2 | Pentest manual de API y autenticación | Completada |
| 3 | DAST, frontend/XSS y agente IA | Completada |
| 4 | Re-ejecución de casos previos y contraste Antes/Después | Pendiente |
| 5 | Informe final, capturas y publicación | Pendiente |

## Documentos relacionados

- [Plan detallado](PLAN.md)
- [Informe de seguridad](informe/informe-seguridad.md)
- [Utilidades](utilidades/README.md)
- [Convenciones de evidencias](evidencias/README.md)
- [Resultados de herramientas](resultados/README.md)
