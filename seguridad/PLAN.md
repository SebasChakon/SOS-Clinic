# Plan de pruebas de seguridad — SOS Clinic

## 1. Objetivo

Evaluar la seguridad del prototipo SOS Clinic, documentar cada hallazgo con
evidencia reproducible y proponer una remediación verificable. El resultado se
publica en la rama `pruebas-seguridad-2` y se resume en
`informe/informe-seguridad.md`.

## 2. Alcance y autorización

- **Sistemas:** gateway, seguridad, pacientes, triage, analítica, agente IA y
  frontend estático.
- **Infraestructura:** Docker Compose local (PostgreSQL, pgAdmin) y puertos de
  los servicios.
- **Datos:** exclusivamente sintéticos. No se usan historias clínicas reales.
- **Correo:** Mailpit local. No se envía correo a direcciones reales.
- **Ambiente:** local. Ninguna prueba se dirige a producción ni a redes
  externas.
- **Autorización:** el responsable del repositorio autoriza la ejecución sobre
  su entorno local. Cualquier prueba fuera de ese alcance requiere autorización
  escrita adicional.

## 3. Metodología

| Marco | Uso |
|---|---|
| PTES | Estructura general de las fases. |
| OWASP WSTG v4.2 | Casos de prueba web (autenticación, sesión, entrada, errores). |
| OWASP API Security Top 10 2023 | BOLA, autenticación, mass assignment, BFLA, consumo. |
| OWASP ASVS 4.0 | Verificación de requisitos por capítulo. |
| ISO/IEC 27002:2022 | Controles de referencia para la matriz académica. |
| CVSS 4.0 | Severidad de cada hallazgo. |
| STRIDE/DREAD | Modelado de amenazas por componente. |

Cada hallazgo se registra con: ID, título, severidad, CWE, referencia OWASP,
control ISO, evidencia, causa raíz, impacto, remediación y estado de
verificación (`Antes` / `Después`).

## 4. Fases

### Fase 0 — Preparación (0,5 día)

1. Iniciar Docker Desktop y `infra/docker-compose.yml`.
2. Restaurar la base alineada desde `sosclinic.dump` si el esquema no coincide
   con las entidades JPA.
3. Levantar Mailpit y configurar SMTP local.
4. Arrancar Eureka, gateway y servicios de negocio.
5. Crear usuarios de prueba: `administrador`, `coordinacion`, `medico`,
   `triage`, uno con 2FA activo.
6. Tomar snapshot de la base antes de las pruebas.
7. Verificar login, una petición autenticada y una petición sin token.

**Criterio de salida:** el sistema responde en `http://localhost:8080` y los
usuarios de prueba pueden autenticarse.

### Fase 1 — Estático, secretos y dependencias (0,5 día)

- `gitleaks` sobre el historial y el árbol de trabajo.
- `semgrep` con reglas de OWASP Top 10 y Java.
- `trivy fs` para dependencias, secretos y configuración.
- Revisión manual de `application.properties`, `CorsConfig`,
  `SecurityConfig`, `JwtService`, `AuthService` y `docker-compose.yml`.

### Fase 2 — Pentest manual de API (2 días)

Matriz principal:

| ID | Prueba | Referencia |
|---|---|---|
| A-01 | Pacientes sin token | API1/API2, CWE-306 |
| A-02 | Triage sin token | CWE-306 |
| A-03 | Enumeración de usuarios en login | WSTG-IDNT |
| A-04 | Fuerza bruta de login sin bloqueo | CWE-307 |
| A-05 | Fuerza bruta y reutilización del código 2FA | CWE-307/798 |
| A-06 | Forja de JWT con el secreto por defecto | CWE-798/347 |
| A-07 | Token expirado, firma alterada y `alg:none` | WSTG-SESS |
| A-08 | Token válido después de logout | CWE-613 |
| A-09 | Token de perfil ordinario contra `/admin/**` | API5, CWE-862 |
| A-10 | Creación de usuario administrador y cambio de claves | API5 |
| A-11 | IDOR en pacientes, triage e historias PDF | API1, CWE-639 |
| A-12 | Mass assignment en signos vitales | API3, CWE-915 |
| A-13 | CORS con origen arbitrario y credenciales | CWE-942 |
| A-14 | Payloads de gran tamaño sin límites | CWE-770/400 |
| A-15 | Inyección SQL en búsquedas y filtros | WSTG-INPV |
| A-16 | Abuso de endpoints de correo | API4 |
| A-17 | Analítica con token de rol incorrecto | API5 |
| A-18 | Errores 500, stacktraces y `show-sql` | WSTG-ERRH |
| A-19 | Cabeceras de seguridad ausentes | WSTG-CONF |
| A-20 | Acceso directo a páginas sin actividad asignada | WSTG-ATHZ |

### Fase 3 — DAST, frontend y agente IA (1 día)

- ZAP baseline y escaneo autenticado con cabecera `Authorization`.
- XSS almacenado en nombre, apellido, email, diagnóstico, tratamiento y
  auditoría; revisar los usos de `innerHTML`.
- Prompt injection y abuso de herramientas del agente; `/agente/preguntar` y
  `/agente/transcribir` sin autenticación; costo y límite de tamaño de audio.
- Puertos y credenciales de infraestructura.

### Fase 4 — Contraste y regresión (0,5 día)

- Re-ejecutar los casos ya documentados en la matriz existente.
- Completar la columna `Después` solo cuando exista una corrección aplicada.
- Opcional: convertir los hallazgos críticos en pruebas `MockMvc`.

### Fase 5 — Informe (1 día)

- Completar `informe/informe-seguridad.md`.
- Anexar capturas, resultados crudos y la matriz Excel actualizada.
- Verificar que no haya secretos ni datos personales en la evidencia.
- Commit final y push a la rama.

## 5. Herramientas

| Tipo | Herramienta | Ejecución |
|---|---|---|
| SCA | Trivy | Docker |
| SAST | Semgrep | Docker |
| Secretos | gitleaks | Docker |
| DAST | OWASP ZAP | Docker |
| API manual | PowerShell, Python, Postman | Local |
| JWT | `utilidades/jwt-forge.py` | Python local |
| Evidencia | Postman, DevTools, capturas de pantalla | Local |

## 6. Estructura de resultados

```text
seguridad/
├── informe/          informe narrativo y matriz
├── evidencias/       capturas por caso
├── resultados/       JSON/HTML/CSV crudos
└── utilidades/       scripts reproducibles
```

## 7. Riesgos y controles

| Riesgo | Control |
|---|---|
| Enviar correos reales | Mailpit obligatorio; SMTP local. |
| Exponer datos personales en capturas | Difuminar antes de guardar. |
| Romper datos locales | Snapshot previo y restauración del dump. |
| Saturar servicios con escaneo activo | Limitar alcance a localhost y espaciar peticiones. |
| Publicar secretos | Revisar `.gitignore` y sanear la evidencia. |
| Confundir hallazgo con explotación | Reproducir cada hallazgo con una petición concreta. |
