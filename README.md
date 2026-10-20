# SOS Clinic

Sistema web de apoyo al registro y seguimiento de pacientes en el proceso de triage. El repositorio combina un frontend estático, servicios Java independientes, descubrimiento con Eureka y una base PostgreSQL local.

> **Estado:** prototipo academico en desarrollo. No debe tratarse como un sistema clinico de produccion. El esquema SQL inicial ya incluye turnos y codigos 2FA; en bases existentes aplica `infra/migraciones/2026-10-19-seguridad.sql`. La autenticacion y la autorizacion por rol estan implementadas en gateway, seguridad, pacientes, triage, analitica y agente. Revisa [Estado y limitaciones conocidas](#estado-y-limitaciones-conocidas) antes de levantarlo desde cero o exponerlo en una red.

## Contenido

- [Alcance actual](#alcance-actual)
- [Arquitectura](#arquitectura)
- [Mapa del repositorio](#mapa-del-repositorio)
- [Requisitos](#requisitos)
- [Puesta en marcha](#puesta-en-marcha)
- [Flujo de trabajo normal](#flujo-de-trabajo-normal)
- [Perfiles, actividades y turnos](#perfiles-actividades-y-turnos)
- [Datos y persistencia](#datos-y-persistencia)
- [API disponible](#api-disponible)
- [Pruebas y mantenimiento](#pruebas-y-mantenimiento)
- [Estado y limitaciones conocidas](#estado-y-limitaciones-conocidas)
- [Problemas frecuentes](#problemas-frecuentes)

## Alcance actual

Sujeto a que la base esté alineada con los modelos actuales, el sistema permite:

- Iniciar sesión con usuario y contraseña, emitir un JWT y solicitar un segundo factor por correo para cuentas configuradas con 2FA.
- Administrar perfiles, actividades, asignaciones a perfiles, usuarios y turnos desde la interfaz de administración.
- Registrar y buscar pacientes.
- Crear episodios de triage, asignar una categoría y ubicación, consultar la lista de espera y cambiar un episodio a atención o atendido.
- Guardar y consultar tomas de signos vitales asociadas a un episodio.
- Consultar eventos de auditoría y enviar algunos correos de historias clínicas/teleconsultas.

El tablero de analítica (`ms-analitica`, base MySQL sintética) y el agente conversacional (`ms-ia-agente`, API de Groq) existen como prototipos; no hay gestión de riesgos ni procesamiento Big Data real. La categoría de triage la selecciona el operador; no la calcula un modelo de IA. La lista de espera se consulta bajo demanda y no es un tablero con actualización automática en vivo.

## Arquitectura

```text
Navegador
  └── frontend (HTML, CSS y JavaScript sin framework)
        └── ms-gateway :8080
              ├── ms-seguridad :8081
              ├── ms-pacientes :8082
              ├── ms-triage :8083
              └── ms-analitica :8084 (local, fuera de Docker)
                    │
                    └── Eureka :8761 (registro y descubrimiento)

ms-analitica lee agregados del MySQL sintético `urgencias_hospital` en `localhost:3306`; no conecta el navegador directamente a la base.

Los servicios de negocio se conectan a PostgreSQL :5432.
pgAdmin, opcional, está publicado en :5050.
```

Los siete proyectos ejecutables son proyectos Maven independientes basados en Java 17, Spring Boot 4.0.8 y Spring Cloud 2025.1.3. Eureka permite que el gateway encuentre instancias por nombre. Las rutas del gateway son explícitas y están en `ms-gateway/src/main/resources/application.properties`; al agregar un servicio se debe definir allí una ruta `lb://...`.

Aunque conceptualmente cada microservicio tiene tablas bajo su responsabilidad, hoy todos usan el mismo esquema y las mismas credenciales de PostgreSQL (`sosclinic`). No hay una base de datos aislada por servicio ni mensajería entre servicios. Las referencias entre dominios se hacen principalmente mediante identificadores numéricos.

`legacy/` es un prototipo anterior Java EE/Ant/TomEE. No forma parte del arranque de los microservicios actuales.

## Mapa del repositorio

### Raíz

| Ruta | Responsabilidad |
|---|---|
| `README.md` | Guía de arquitectura, configuración, operación y limitaciones. |
| `levantar-sistema.bat` | En Windows, carga las credenciales del archivo `.env` (si existe) en el entorno, levanta Docker Compose, abre consolas para todos los servicios de aplicación (gateway, seguridad, pacientes, triage, analítica y agente), define credenciales por defecto de MySQL para analítica si faltan y abre el login. Usa el comando global `mvn`; requiere Maven en `PATH`. Sus pausas son fijas, no comprobaciones de disponibilidad. |
| `infra/` | Infraestructura local PostgreSQL/pgAdmin y SQL inicial. |
| `frontend/` | Interfaz estática que consume el gateway. |
| `eureka-server/` | Registro y descubrimiento de servicios. |
| `ms-gateway/` | Entrada HTTP y rutas hacia los servicios. |
| `ms-seguridad/` | Autenticación, JWT, identidades, perfiles, actividades, turnos y auditoría. |
| `ms-pacientes/` | Datos demográficos, signos vitales y correos relacionados con pacientes. |
| `ms-triage/` | Episodios, categorías, estados, ubicación y priorización. |
| `ms-analitica/` | Tablero de analítica sobre la base MySQL sintética `urgencias_hospital`. |
| `ms-ia-agente/` | Agente conversacional (chat y voz) con Groq y herramientas contra el gateway. |
| `legacy/` | Prototipo Java EE/Ant/TomEE, conservado como referencia; no ejecutarlo como parte del arranque actual. |
| `src/`, `web/`, `nbproject/` | Estructura NetBeans del proyecto antiguo en la raíz; no es uno de los microservicios Maven actuales. |
| `build/`, `legacy/build/` | Salidas generadas de compilaciones/prototipos; no son fuente y no deben editarse. |

Cada módulo Maven organiza normalmente su código en `src/main/java` así:

- `*Application.java`: punto de entrada Spring Boot; Eureka activa además la función de servidor Eureka.
- `controlador/`: endpoints HTTP y conversión de solicitudes/respuestas.
- `servicio/`: reglas de negocio y coordinación de repositorios.
- `repositorio/`: acceso a datos mediante Spring Data JPA.
- `modelo/`: entidades mapeadas a tablas PostgreSQL.
- `dto/`: objetos de entrada/salida de API.
- `seguridad/` en `ms-seguridad`: configuración, filtro JWT y flujo de autenticación.
- `src/main/resources/application.properties`: puerto, nombre Eureka, JDBC y configuración local.
- `pom.xml`: versión Java, dependencias y plugins Maven.
- `src/test/java/`: pruebas de contexto; no equivalen a cobertura funcional completa.
- `mvnw` / `mvnw.cmd`: Maven Wrapper del módulo.
- `target/`: salida generada por Maven; no editar ni usar como fuente.

### Infraestructura y frontend

| Ruta | Contenido |
|---|---|
| `infra/docker-compose.yml` | Contenedores `postgres` (PostgreSQL 16) y `pgadmin`, puertos, volumen y montaje del script inicial. |
| `infra/schema-postgres.sql` | Esquema, perfiles base, actividad CRUE y asignación inicial a todos los perfiles. Solo se ejecuta automáticamente al crear un volumen vacío. |
| `infra/activar-enfermedades-crue.sql` | Registra la actividad CRUE y la asigna idempotentemente a todos los perfiles de una base existente. |
| `infra/migraciones/2026-10-19-seguridad.sql` | Migración para bases existentes: amplía el código 2FA a hash BCrypt y añade `requiere_2fa`/`turno` si faltan. |
| `infra/migraciones/2026-10-19-actividades.sql` | Limpia actividades duplicadas por enlace y crea índices únicos para nombre y enlace. |
| `frontend/index.html` | Landing: presentación animada del proyecto, con el video y el acceso. |
| `frontend/login.html` | Login y formulario de verificación 2FA. |
| `frontend/pacientes.html` | Registro/listado de pacientes y entrada al flujo según actividades asignadas. |
| `frontend/triage-nuevo.html` | Búsqueda por cédula y registro de llegada con categoría y ubicación. |
| `frontend/enfermedades-crue.html` | Visualizador Power BI de enfermedades atendidas por el CRUE de Bogotá. |
| `frontend/lista-espera.html` | Consulta de priorización y acceso al seguimiento. |
| `frontend/seguimiento.html` | Detalle del episodio, ubicación, transición a atención, signos vitales e interfaz de alta. Requiere `?idRegistro=...`. |
| `frontend/auditoria.html` | Consulta de acciones registradas por backend y frontend. |
| `frontend/roles.html` | Administración de perfiles, actividades, asignaciones, usuarios y turnos. |
| `frontend/dashboard.html` | Tablero de analítica por rango de fechas, localidad y turno; consume `ms-analitica` vía gateway. |
| `frontend/assets/api.js` | URL base, sesión en `sessionStorage`, envío JWT, manejo de errores y funciones compartidas de API. |
| `frontend/assets/copiloto.js` | Widget flotante del agente IA (chat y voz) en las páginas con sesión activa. |
| `frontend/assets/estilos.css` | Estilos compartidos; algunas páginas añaden estilos locales en su HTML. |

## Requisitos

- JDK 17.
- Docker Desktop con Docker Compose.
- Maven 3.9 o superior si se usa `levantar-sistema.bat` o `mvn`. También se puede usar `mvnw.cmd` incluido en cada módulo.
- Navegador moderno.
- Para preparar los datos de analítica: MySQL Server iniciado, el cliente `mysql` en `PATH` y Python disponible como `python`.
- Opcional: NetBeans para abrir/ejecutar los proyectos y pgAdmin para inspeccionar PostgreSQL.

El IDE no es requisito para compilar o ejecutar. El plugin de Spring para NetBeans mencionado en documentación antigua no es necesario para el flujo normal.

## Puesta en marcha

### 0. Crear el archivo `.env`

Las credenciales (SMTP de correo, MySQL de analítica y API key de Groq) no están en el repositorio. Copia la plantilla y pide los valores al coordinador por un canal seguro (ver [Credenciales y secretos](#credenciales-y-secretos)):

```powershell
Copy-Item .env.example .env
notepad .env
```

`levantar-sistema.bat` lo carga automáticamente, y además cada servicio lo importa al arrancar con un pequeño cargador propio (`EnvEnvironmentPostProcessor`, que busca `.env` en el directorio del módulo y en la raíz del repo), así que **lanzar desde NetBeans o `mvn spring-boot:run` también funciona sin exportar nada**. Sin `.env` el sistema igual levanta, pero los correos fallarán (el código 2FA se muestra en la consola como respaldo) y el agente IA responderá que no está configurado.

### 1. Iniciar PostgreSQL

Desde PowerShell, CMD o una terminal abierta en el repositorio:

```powershell
cd infra
docker compose up -d
docker compose ps
```

Configuración local declarada en Compose:

| Servicio | Dirección | Credenciales locales |
|---|---|---|
| PostgreSQL | `localhost:5433`, base `sosclinic` | Usuario `DB_USERNAME` y contrasena en `.env`; solo escucha en 127.0.0.1. |
| pgAdmin | `http://localhost:5050` | Credenciales en `.env`; solo escucha en 127.0.0.1. |

Para comprobar las tablas:

```powershell
docker exec -it sos-postgres psql -U sos_admin -d sosclinic -c "\dt"
```

Para consultar datos base:

```powershell
docker exec -it sos-postgres psql -U sos_admin -d sosclinic -c "SELECT * FROM perfil; SELECT * FROM categoria_triage ORDER BY prioridad;"
```

**Persistencia:** los datos se conservan en el volumen `sos_pgdata` al detener contenedores. `docker compose down -v` elimina también el volumen y borra permanentemente los datos locales. Un cambio al SQL de inicialización no modifica un volumen existente.

> **Importante:** antes de iniciar las aplicaciones en una base nueva, revisa [Estado y limitaciones conocidas](#estado-y-limitaciones-conocidas). El SQL actual no cubre todas las entidades mapeadas por JPA y los servicios usan `ddl-auto=validate`.

### 2. Preparar la base MySQL de analítica

Este paso es independiente de PostgreSQL y es necesario para que `ms-analitica` encuentre su base de datos local. Con MySQL iniciado, el cliente `mysql` disponible en `PATH` y Python instalado, ejecuta desde la raíz del repositorio:

```powershell
.\levantar-bd-local.bat
```

El BAT ejecuta `bd_local.sql` con las credenciales `ANALITICA_DB_USERNAME`/`ANALITICA_DB_PASSWORD` de `.env` en `localhost:3306`. Ese SQL elimina y vuelve a crear `urgencias_hospital`. Después, el BAT verifica Python, instala `pandas`, `faker`, `sqlalchemy` y `pymysql`, y ejecuta `llenar_hospital.py`, que carga datos sintéticos de localidades, días, turnos y 50.000 llegadas.

> **Importante:** cada ejecución borra todos los datos previos de `urgencias_hospital` antes de volver a generarlos. El SQL se ejecuta antes de comprobar Python, por lo que la base también se reinicia si Python no está instalado y el BAT luego se detiene con el aviso correspondiente.

### 3. Iniciar servicios

Orden recomendado: Eureka, gateway y luego servicios de negocio:

1. `eureka-server`
2. `ms-gateway`
3. `ms-seguridad`, `ms-pacientes`, `ms-triage` y `ms-analitica`

Antes de arrancar, configura en el entorno local `ANALITICA_DB_USERNAME`, `ANALITICA_DB_PASSWORD` y, opcionalmente, `ANALITICA_DB_URL` para MySQL. Todos los servicios comparten el secreto HS256 `APP_JWT_SECRET`; no hay fallback versionado. Genera un valor aleatorio de al menos 32 bytes y guardalo en `.env`. El mismo valor debe estar en gateway, seguridad, pacientes, triage, analitica y agente.

Abre una terminal por servicio desde la raíz:

```powershell
cd eureka-server
.\mvnw.cmd spring-boot:run
```

Repite en terminales separadas desde las carpetas `ms-gateway`, `ms-seguridad`, `ms-pacientes`, `ms-triage`, `ms-analitica` y `ms-ia-agente`. También puedes ejecutar `mvn spring-boot:run` si Maven está instalado globalmente. Para usar el script de Windows desde la raíz:

```powershell
.\levantar-sistema.bat
```

El script abre las consolas en el orden básico, pero no valida que PostgreSQL o cada servicio estén listos; si hay demoras, revisa la salida de la consola correspondiente.

`ms-analitica` corre como proceso local fuera de Docker y requiere que el MySQL sintético ya esté disponible. Prepara esa base manualmente en el paso 2; el arranque de servicios no ejecuta el generador. `levantar-sistema.bat` carga `.env` y exige `APP_JWT_SECRET`; no define credenciales por defecto. `ms-ia-agente` necesita `GROQ_API_KEY` (definida en `.env`; `levantar-sistema.bat` la expone a las consolas) para responder; sin ella el asistente indica que no está configurado.

Comprobaciones:

- Eureka: `http://localhost:8761`
- Gateway: `http://localhost:8080`
- En Eureka deben aparecer `MS-GATEWAY`, `MS-SEGURIDAD`, `MS-PACIENTES`, `MS-TRIAGE`, `MS-ANALITICA` y `MS-IA-AGENTE` una vez registrados.

### 4. Preparar datos de aplicación

El SQL incluido crea cuatro perfiles base (`triage`, `medico`, `coordinacion`, `seguridad`), cinco categorias de triage, tres estados de episodio, la tabla `turno` y `codigo_verificacion`. **No crea administrador, usuarios, actividades ni asignaciones `perfil_actividad`.** En bases existentes aplica `infra/migraciones/2026-10-19-seguridad.sql` para ampliar el codigo 2FA y anadir las columnas/tablas nuevas.

Una vez actualizado el esquema, crea la primera cuenta de forma controlada. La clave debe guardarse como BCrypt, nunca en texto plano. La opción normal es crear usuarios desde `roles.html`, ya que el backend codifica la clave y exige asignar un turno. Para el primer acceso administrativo se necesita un proceso de bootstrap controlado (por ejemplo, una inserción revisada con un hash BCrypt generado localmente). No compartas cuentas o hashes de demostración.

Después del primer acceso, asigna actividades con enlaces válidos al perfil correspondiente. Sin actividades, el usuario puede autenticarse pero el frontend no tendrá una pantalla de destino.

Para registrar `dashboard_analitica` y asignarla al perfil `coordinacion`, ejecuta una sola vez `infra/analytics-dashboard-activity.sql` contra PostgreSQL `sosclinic`. La actividad controla navegacion; la API tambien valida el JWT compartido y exige el rol `COORDINACION` (incluye el perfil historico `coordinador_turnos`) o `ADMINISTRADOR`.

### 5. Abrir el frontend

El script de Windows sirve `frontend/` en `http://localhost:5500` con Python y abre la landing (`index.html`); si Python no está disponible, abre los archivos directamente (en ese caso el video de la landing muestra una miniatura con enlace a YouTube, porque el reproductor incrustado exige un referrer HTTP). La API apunta a `http://localhost:8080`, y el navegador guarda token y datos básicos de sesión en `sessionStorage`. Si el navegador bloquea las peticiones por CORS al abrir el archivo directamente, sirve `frontend/` desde un servidor estático local y permite ese origen en la configuración CORS del gateway.

## Flujo de trabajo normal

1. **Acceso del personal.** Se envían usuario y clave a `ms-seguridad`. El servicio comprueba que la cuenta esté activa, compara BCrypt y, excepto para el perfil llamado exactamente `administrador`, verifica que la hora esté dentro de un turno activo. Si se requiere 2FA, envía un código de seis dígitos al correo; vence en cinco minutos. Tras autenticar, devuelve JWT y actividades del perfil.
2. **Registro del paciente.** Se guardan nombre, apellido, cédula, fecha de nacimiento y, opcionalmente, correo. La cédula debe ser única. Un paciente existente puede localizarse por cédula desde el formulario de nuevo triage.
3. **L�mite de autorizaci�n:** las actividades siguen controlando el men� y la navegaci�n del navegador, pero la autorizaci�n real se aplica en el backend. El gateway valida el JWT y cada servicio vuelve a validarlo. `ms-seguridad` exige `ADMINISTRADOR` para `/admin/**` (salvo auditor�a, que tambi�n admite `SEGURIDAD`); `ms-pacientes` y `ms-triage` exigen roles cl�nicos; `ms-analitica` exige `COORDINACION` o `ADMINISTRADOR`; `ms-ia-agente` exige un usuario autenticado y deriva sus permisos del token. Las actividades ya no son la �nica barrera para los datos cl�nicos.
4. **Priorización.** `GET /ms-triage/triage/prioridad` devuelve la lista priorizada. La prioridad numérica 1 es la más urgente; el tiempo de espera en minutos se calcula al consultar y no se mantiene como contador en segundo plano.
5. **Atención y seguimiento.** Desde la lista se abre `seguimiento.html?idRegistro=...`. Se puede actualizar ubicación, marcar `En atencion` y guardar tomas sucesivas de signos vitales. Las tomas anteriores se conservan.
6. **Cierre y notificaciones.** La interfaz exige diagnóstico, tratamiento y la dirección del paciente; el alta se completa con **una sola llamada** a `POST /ms-pacientes/pacientes/atenciones/{idRegistro}/finalizar`. El backend persiste primero el alta en `ms-triage` (si eso falla, no se envía nada) y en seguida envía las notificaciones: confirmación de teleconsulta al profesional (`ms-seguridad`), cita de teleconsulta al paciente (con archivo de calendario `.ics`) e historia clínica en PDF (más farmacias cercanas a la dirección; si no se puede ubicar, farmacias de Bogotá en general). Cada correo se evalúa por separado y la respuesta informa ✅ enviado / ⚠️ fallido / ⏭️ omitido; la pantalla ofrece un botón de reintento que llama a `reenviar-correos` sin volver a tocar el alta.
7. **Trazabilidad.** Algunas operaciones administrativas se registran desde el backend; el frontend intenta registrar login, creación y cambios de triage. La auditoría es de mejor esfuerzo: un error al guardar el log no cancela la operación principal.

Las funciones de historia clínica y teleconsulta dependen de correo SMTP. No asumas que el correo se entregó porque el cambio de estado haya concluido correctamente: revisa el estado por correo que devuelve la operación de alta.

## Sistema de correos

Los correos se construyen y envían **siempre desde el backend** (el frontend ya no arma textos ni orquesta llamadas). Los componentes se repiten en `ms-pacientes` y `ms-seguridad`, porque no existe módulo compartido:

| Pieza | Ubicación | Qué hace |
|---|---|---|
| `EmailService` | ambos servicios | Renderiza plantillas Thymeleaf, genera texto alternativo y envía con adjuntos e imágenes embebidas (`cid:`). Tiempos de espera SMTP de 10 s. |
| Plantillas | `src/main/resources/templates/plantillas/` | HTML con tablas y CSS en línea (compatible con Gmail/Outlook) en la paleta teal de la app: cita de paciente, confirmación al profesional, resumen de historia clínica, código 2FA y PDF. |
| `HistoriaClinicaService` + `PdfService` | `ms-pacientes` | Consolidan paciente, episodio y signos vitales; generan el PDF con OpenHTMLtoPDF y las tipografías DejaVu embebidas en `resources/fonts/`. |
| `NotificacionesService` | `ms-pacientes` | Punto único de envío de cita e historia (lo usan el endpoint heredado y el orquestador). |
| `AtencionService` | `ms-pacientes` | Orquesta alta + correos con aislamiento de errores por correo. |
| `FarmaciasService` | `ms-pacientes` | Overpass API de OpenStreetMap (radio 3 km, 5 más cercanas) geocodificando la dirección del paciente con Nominatim; si la dirección falta o no se puede ubicar, usa el centro de Bogotá (radio 12 km) como respaldo. Mapa PNG compuesto a partir de los tiles de OSM. Cadena de fuentes: instancia principal de Overpass → `overpass.kumi.systems` → búsqueda por categoría en Nominatim, para que el correo salga con farmacias aunque las instancias públicas de Overpass estén caídas. Sin API key. |
| `TeleconsultaService` + `IcsService` | `ms-seguridad` | Cita determinista (+7 días, 10:00 `America/Bogota`, 30 min, sala Jitsi) y archivo `.ics` (RFC 5545) adjunto para ambos correos. |

Endpoints nuevos en `ms-pacientes`:

- `POST /pacientes/atenciones/{idRegistro}/finalizar` — alta + notificaciones en una sola llamada (recibe `diagnosticoFinal`, `tratamiento` y opcionalmente `latitud`, `longitud`, `direccion`).
- `POST /pacientes/atenciones/{idRegistro}/reenviar-correos` — reintento de notificaciones de un episodio ya `Atendido` (el alta no se vuelve a tocar; `TriageService.darDeAlta` tiene guard de idempotencia).
- `GET /pacientes/historias/{idRegistro}/pdf` — descarga del PDF oficial de la historia clínica (botón en `seguimiento.html`).

Los endpoints antiguos (`enviar-historia`, `enviar-cita-teleconsulta`, `PUT .../atendido`) se mantienen por compatibilidad.

### Probar los correos sin enviar mensajes reales

Para desarrollo usa **Mailpit**, que captura todo el SMTP en un contenedor y lo muestra en un navegador:

```bash
docker run -d --name mailpit -p 1025:1025 -p 8025:8025 axllent/mailpit
```

Luego exporta el SMTP de prueba antes de arrancar los servicios (o agrégalas a `.env`):

```bat
set MAIL_SMTP_HOST=localhost
set MAIL_SMTP_PORT=1025
set MAIL_USERNAME=
set MAIL_PASSWORD=
```

Abre `http://localhost:8025` para ver cada correo con sus adjuntos (PDF e `.ics`). Recuerda volver a las variables reales para enviar de verdad.

## Perfiles, actividades y turnos

El sistema separa estos conceptos:

- **Perfil (`perfil`):** etiqueta asignada a un usuario, por ejemplo `triage`, `medico`, `coordinacion` o `seguridad`. El SQL inicial crea estos cuatro nombres. `administrador` tiene comportamiento especial en algunas comprobaciones, pero no se crea automáticamente.
- **Actividad (`actividades`):** permiso de navegación del frontend, representado por nombre interno y archivo HTML, por ejemplo `crear_triage` → `triage-nuevo.html`.
- **Asignación (`perfil_actividad`):** relación que determina qué actividades recibe cada perfil al iniciar sesión.

`roles.html` permite crear/editar/eliminar perfiles y actividades, asignar o quitar actividades, y crear/editar usuarios. Al crear usuario se requiere clave y horario; la clave se codifica con BCrypt y se crea un turno activo. Al editar, dejar la clave vacía conserva la existente; también se puede cambiar perfil, 2FA y datos personales. Una cuenta inactiva no puede autenticarse.

Los turnos son intervalos horarios diarios, no calendarios por fecha. La comprobación admite intervalos que cruzan medianoche. El backend de seguridad omite la comprobación horaria para el perfil cuyo nombre sea exactamente `administrador`.

**Limite de autorizacion:** las actividades siguen controlando el menu y la navegacion del navegador, pero la autorizacion real se aplica en el backend. El gateway valida el JWT y cada servicio vuelve a validarlo. `ms-seguridad` exige `ADMINISTRADOR` para `/admin/**` (salvo auditoria, que tambien admite `SEGURIDAD`); `ms-pacientes` y `ms-triage` exigen roles clinicos; `ms-analitica` exige `COORDINACION` o `ADMINISTRADOR`; `ms-ia-agente` exige un usuario autenticado y deriva sus permisos del token. Las actividades ya no son la unica barrera para los datos clinicos.

## Datos y persistencia

El esquema de referencia está en `infra/schema-postgres.sql`. El SQL inicial crea diez tablas:

| Tabla | Uso y relaciones |
|---|---|
| `perfil` | Perfiles de personal; nombre único y estado activo/inactivo. |
| `personal` | Cuenta y datos del profesional; referencia a `perfil`. `clave` debe contener hash, nunca texto claro. |
| `actividades` | Nombre interno y enlace de pantalla. |
| `perfil_actividad` | Asignación única de actividad a perfil. |
| `paciente` | Datos demográficos, cédula única y estado. |
| `categoria_triage` | Catálogo de categorías con prioridad única; prioridad 1 es la mayor. |
| `estado_triage` | Catálogo de estados de episodios. |
| `registro_triage` | Episodio asociado a paciente, categoría, estado, ubicación y fecha/hora de llegada. |
| `estado_paciente` | Cada toma de signos vitales, asociada a episodio y al ID del personal que la registró. |
| `registro_auditoria` | Actor, paciente opcional, acción, detalle y fecha/hora. |

Los servicios necesitan además `turno` y `codigo_verificacion`, más columnas ausentes del SQL inicial como `personal.requiere_2fa`, `paciente.email`, `registro_triage.diagnostico_final` y `registro_triage.tratamiento`. La discrepancia está explicada en [Estado y limitaciones conocidas](#estado-y-limitaciones-conocidas).

### Reglas de datos

- Las identidades las genera PostgreSQL. No asumas que los IDs serán consecutivos después de borrar registros.
- Las claves foráneas relacionan paciente/episodio, episodio/catálogos de triage, personal/perfil y perfiles/actividades.
- Algunas referencias de otros dominios son identificadores numéricos en vez de relaciones JPA. Por ejemplo, los signos guardan `id_registro` e `id_personal`; esto no significa que haya bases físicas separadas por servicio.
- `id_estado` normalmente usa `1` activo y `0` inactivo. Los episodios usan el catálogo `estado_triage`.
- `spring.jpa.hibernate.ddl-auto=validate`: Hibernate valida tablas/columnas, pero no crea ni migra el esquema. El SQL inicial no está versionado como migraciones incrementales.
- `sos_pgdata` conserva la base entre reinicios. Los scripts de inicialización no se vuelven a ejecutar sobre un volumen existente.

## API disponible

Todas las rutas se exponen en `http://localhost:8080`. El gateway elimina el prefijo del servicio antes de reenviar; por ejemplo, `/ms-pacientes/pacientes` llega al controlador como `/pacientes`.

### Autenticación y seguridad

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/ms-seguridad/auth/login` | Valida usuario/clave y turno; puede requerir 2FA o devolver token y actividades. |
| `POST` | `/ms-seguridad/auth/verificar-2fa` | Valida código pendiente y devuelve token si es correcto. |
| `POST` | `/ms-seguridad/auth/programar-teleconsulta` | Solicita programación/confirmación usando el usuario autenticado. |
| `POST` | `/ms-seguridad/auditoria/registrar` | Registra una acción solicitada por el frontend; requiere JWT. |
| `GET` | `/ms-seguridad/admin/perfiles` | Lista perfiles. |
| `POST`, `PUT /{id}`, `DELETE /{id}` | `/ms-seguridad/admin/perfiles` | Crea, edita o elimina perfil. |
| `GET` | `/ms-seguridad/admin/actividades` | Lista actividades. |
| `POST`, `PUT /{id}`, `DELETE /{id}` | `/ms-seguridad/admin/actividades` | Crea, edita o elimina actividad. |
| `GET`, `POST` | `/ms-seguridad/admin/perfil-actividad` | Lista asignaciones o asigna actividad a perfil. |
| `DELETE` | `/ms-seguridad/admin/perfil-actividad/{id}` | Quita una asignación. |
| `GET`, `POST` | `/ms-seguridad/admin/personal` | Lista o crea personal; al crear exige clave y turno. |
| `PUT` | `/ms-seguridad/admin/personal/{id}` | Edita datos, perfil, 2FA y, opcionalmente, clave/turno. |
| `PUT` | `/ms-seguridad/admin/personal/{id}/estado?idEstado=0\|1` | Activa o desactiva usuario. |
| `GET`, `POST` | `/ms-seguridad/admin/turnos` | Lista o crea turnos. |
| `PUT` | `/ms-seguridad/admin/turnos/{id}` | Edita las horas. |
| `PUT` | `/ms-seguridad/admin/turnos/{id}/estado?idEstado=0\|1` | Activa o desactiva turno. |
| `GET` | `/ms-seguridad/admin/auditoria` | Lista registros de auditoría. |

Las operaciones administrativas de `ms-seguridad` requieren token, pero actualmente no validan que el token tenga un rol administrativo. No expongas estas rutas fuera de un entorno controlado.

### Pacientes

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/ms-pacientes/pacientes` | Registra paciente; responde conflicto si la cédula ya existe. |
| `GET` | `/ms-pacientes/pacientes` | Lista pacientes. |
| `GET` | `/ms-pacientes/pacientes/{id}` | Busca por ID. |
| `GET` | `/ms-pacientes/pacientes/cedula/{cedula}` | Busca por cédula exacta. |
| `GET` | `/ms-pacientes/pacientes/buscar?q={texto}` | Hasta diez coincidencias parciales de cédula. |
| `POST` | `/ms-pacientes/pacientes/signos-vitales` | Guarda una toma de signos vitales. |
| `GET` | `/ms-pacientes/pacientes/signos-vitales/registro/{idRegistro}` | Lista tomas del episodio. |
| `POST` | `/ms-pacientes/pacientes/{id}/enviar-historia` | Envía contenido por correo si el paciente tiene email. Con `idRegistro` en el cuerpo genera y adjunta el PDF oficial. |
| `POST` | `/ms-pacientes/pacientes/{id}/enviar-cita-teleconsulta` | Envía fecha/enlace recibidos en la solicitud (con `.ics` si llegan fechas ISO). |
| `POST` | `/ms-pacientes/pacientes/atenciones/{idRegistro}/finalizar` | Alta completa en una llamada: persiste diagnóstico/tratamiento en triage y envía teleconsulta + historia en PDF, devolviendo estado por correo. |
| `POST` | `/ms-pacientes/pacientes/atenciones/{idRegistro}/reenviar-correos` | Reintenta las notificaciones de un episodio ya `Atendido`, sin repetir el alta. |
| `GET` | `/ms-pacientes/pacientes/historias/{idRegistro}/pdf` | Descarga el PDF de la historia clínica del episodio. |

### Triage

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/ms-triage/triage` | Crea episodio `En espera`; recibe ID paciente, categoría y ubicación opcional. |
| `GET` | `/ms-triage/triage/{id}` | Obtiene episodio. |
| `GET` | `/ms-triage/triage/prioridad` | Lista priorizada y tiempo de espera calculado en la consulta. |
| `PUT` | `/ms-triage/triage/{id}/en-atencion` | Cambia estado a `En atencion`. |
| `PUT` | `/ms-triage/triage/{id}/ubicacion` | Cambia ubicación. |
| `PUT` | `/ms-triage/triage/{id}/atendido` | Da de alta: persiste `diagnosticoFinal` y `tratamiento` (obligatorios) y cambia el estado a `Atendido`. |

### Analítica sintética

Las rutas requieren un JWT firmado con `APP_JWT_SECRET` y perfil `coordinacion`, `coordinador_turnos` o `administrador`. Todas reciben fechas ISO inclusivas `desde=AAAA-MM-DD` y `hasta=AAAA-MM-DD`.

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/ms-analitica/dashboard/resumen` | Conteo de llegadas y espera media; filtros opcionales `localidad` y `turno`. |
| `GET` | `/ms-analitica/dashboard/llegadas` | Conteos por hora, prioridad y localidad; filtros opcionales `localidad` y `turno`. |
| `GET` | `/ms-analitica/dashboard/capacidad` | Llegadas, personal, puestos de triage y camas disponibles promedio por turno; filtro opcional `turno`. La localidad no aplica a capacidad global. |

El dashboard está en `frontend/dashboard.html`. Chart.js se distribuye como archivo local; los datos están marcados como sintéticos y no representan relaciones clínicas o causales.

El esquema MySQL observado es compatible con el seeder: `contexto_zona` contiene `localidad VARCHAR(100)` (clave), `poblacion INT`, `porcentaje_mayores_60 DECIMAL(5,2)` y `densidad_poblacional DECIMAL(10,2)`; `contexto_diario` contiene `fecha DATE` (clave), `es_festivo TINYINT(1)`, `lluvia_mm DECIMAL(6,2)`, `temperatura DECIMAL(5,2)`, `pm25 DECIMAL(6,2)` y `evento_masivo TINYINT(1)`; `capacidad_turno` contiene `(fecha DATE, turno VARCHAR(20))` como clave compuesta, más `personal_disponible`, `puestos_triage` y `camas_disponibles INT`; `llegadas` contiene `id_llegada INT AUTO_INCREMENT`, `fecha_hora DATETIME`, `fecha DATE`, `turno VARCHAR(20)`, `localidad VARCHAR(100)`, `prioridad VARCHAR(20)`, `estado VARCHAR(50)` y tiempos `INT`. Hay índices/FK para localidad y `(fecha, turno)`. Al verificarlo: 5 zonas, 365 días, 1.095 turnos y 50.000 llegadas.

`frontend/assets/api.js` incluye el token en las peticiones cuando hay sesión, pero eso no significa que todos los servicios verifiquen el token.

## Pruebas y mantenimiento

Cada módulo tiene su propio ciclo Maven. Desde una terminal ubicada en la carpeta del módulo:

```powershell
.\mvnw.cmd test
```

También se puede usar `mvn test`. Las pruebas actuales son principalmente de arranque/contexto; no cubren exhaustivamente negocio, autorización, persistencia ni flujos clínicos. Para empaquetar un módulo sin pruebas:

```powershell
.\mvnw.cmd package -DskipTests
```

Para detener infraestructura sin borrar los datos:

```powershell
cd infra
docker compose down
```

Añadir `-v` borra el volumen, por lo que debe hacerse solo de forma intencional.

## Estado y limitaciones conocidas

1. **SQL y entidades JPA no están sincronizados.** `schema-postgres.sql` define diez tablas, pero las entidades actuales también esperan `turno`, `codigo_verificacion` y columnas como `personal.requiere_2fa`, `paciente.email`, `registro_triage.diagnostico_final` y `registro_triage.tratamiento`. Con `ddl-auto=validate`, una base creada solo con ese SQL puede impedir el arranque de los servicios afectados. Actualiza el esquema mediante una migración controlada; no uses `ddl-auto=update` como sustituto de una migración revisada.
2. **Faltan datos iniciales de acceso.** No se inserta usuario ni turnos. El esquema inicial registra la actividad CRUE y la asigna a todos los perfiles base; en una base existente, ejecuta `infra/activar-enfermedades-crue.sql` una vez porque los scripts de inicialización no corren de nuevo sobre volúmenes existentes.
3. **Historia clínica y teleconsulta por correo.** El alta se hace en una sola llamada desde el backend, que persiste diagnóstico y tratamiento en `registro_triage` **antes** de notificar y devuelve el estado de cada correo por separado. Los envíos siguen siendo de mejor esfuerzo dependientes de SMTP: revisa el estado devuelto y usa el botón de reintento en lugar de asumir que la historia clínica llegó porque el alta concluyó. Si el PDF o el mapa de farmacias fallan, el correo se envía igual sin ese elemento.
4. **Autorización por rol incompleta.** JWT se valida dentro de `ms-seguridad`, pero las APIs de pacientes y triage no instalan ese filtro; el gateway tampoco valida token globalmente. Las rutas administrativas solo requieren autenticación, no un rol específico.
5. **Secretos fuera del repositorio.** El SMTP y la API key de Groq se leen desde el archivo `.env` (ignorado por git) o variables de entorno; `application.properties` solo conserva `${MAIL_USERNAME}` y similares. El secreto JWT se exige por la variable de entorno `APP_JWT_SECRET`; no hay fallback versionado. Genera uno nuevo, guardalo en `.env` y reinicia todos los servicios. Revisa el historial de git (los secretos antiguos ya estaban publicados) y sigue la seccion Credenciales y secretos para rotarlos. No los uses en produccion.
6. **Configuración de desarrollo sin endurecer.** PostgreSQL y pgAdmin publican puertos locales con credenciales de desarrollo; CORS debe limitarse al origen real del frontend. El repositorio no contiene un despliegue productivo completo para Azure.
7. **Auditoría de mejor esfuerzo.** Parte de los eventos se solicita desde JavaScript y los errores de auditoría se silencian. No es una bitácora inmutable ni registra automáticamente todos los accesos a datos clínicos.
8. **Analítica y agente IA en modo prototipo.** `ms-analitica` consulta una base MySQL sintética y `ms-ia-agente` usa la API de Groq para chat y voz; siguen sin existir módulos de Big Data, ISO 27005/STRIDE/DREAD o recomendaciones clínicas automáticas.
9. **No sustituye criterio clínico.** El personal ingresa la categoría. La aplicación no diagnostica, no detecta deterioro automáticamente y no debe ser el único mecanismo de priorización asistencial.

## Problemas frecuentes

**`Port XXXX was already in use`**
Ya existe un proceso usando el puerto. Detén la ejecución anterior o identifica el proceso con `netstat -ano | findstr :XXXX` antes de cerrarlo.

**Error de validación de Hibernate o columna/tabla faltante**
Compara las entidades con `infra/schema-postgres.sql`. El esquema inicial está incompleto para los modelos actuales. Añade una migración/esquema actualizado; los scripts de inicialización no corren otra vez sobre volúmenes existentes.

**404 a través del gateway**
Verifica que el servicio aparezca en Eureka y tenga ruta explícita en `ms-gateway/src/main/resources/application.properties`. Las rutas usan `StripPrefix=1`.

**No se puede contactar al servidor**
Comprueba Docker/PostgreSQL, Eureka, gateway y servicio destino, en ese orden. Revisa puertos y URL JDBC.

**L�mite de autorizaci�n:** las actividades siguen controlando el men� y la navegaci�n del navegador, pero la autorizaci�n real se aplica en el backend. El gateway valida el JWT y cada servicio vuelve a validarlo. `ms-seguridad` exige `ADMINISTRADOR` para `/admin/**` (salvo auditor�a, que tambi�n admite `SEGURIDAD`); `ms-pacientes` y `ms-triage` exigen roles cl�nicos; `ms-analitica` exige `COORDINACION` o `ADMINISTRADOR`; `ms-ia-agente` exige un usuario autenticado y deriva sus permisos del token. Las actividades ya no son la �nica barrera para los datos cl�nicos.
Asigna al perfil una actividad cuyo enlace exista. `seguimiento.html` además necesita `idRegistro` en la URL.

**L�mite de autorizaci�n:** las actividades siguen controlando el men� y la navegaci�n del navegador, pero la autorizaci�n real se aplica en el backend. El gateway valida el JWT y cada servicio vuelve a validarlo. `ms-seguridad` exige `ADMINISTRADOR` para `/admin/**` (salvo auditor�a, que tambi�n admite `SEGURIDAD`); `ms-pacientes` y `ms-triage` exigen roles cl�nicos; `ms-analitica` exige `COORDINACION` o `ADMINISTRADOR`; `ms-ia-agente` exige un usuario autenticado y deriva sus permisos del token. Las actividades ya no son la �nica barrera para los datos cl�nicos.
Comprueba que la cuenta esté activa, que tenga turno con `id_estado = 1` y que la hora local esté dentro del intervalo. El perfil `administrador` es una excepción del código.

**No llega el correo o código 2FA**
Verifica SMTP y acceso de red. Usa secretos locales no versionados; no publiques contraseñas SMTP ni códigos.

**No se puede eliminar una actividad**
La actividad está asignada a uno o más perfiles (tabla `perfil_actividad`). La API responde 409 e indica los perfiles involucrados; quita primero esas asignaciones en `roles.html` y vuelve a intentar.

**Cambiaste SQL pero la base conserva el esquema anterior**
El volumen conserva su estado. Aplica una migración explícita o, si los datos son descartables, elimina el volumen conscientemente. `docker compose down -v` es destructivo.

## Credenciales y secretos

**Regla de oro: nada de secretos en git.** Todo lo sensible se lee de variables de entorno; el archivo `.env` (en la raíz, ignorado por git) es solo el almacén local de desarrollo. `.env.example` documenta las variables esperadas sin valores reales.

### Cómo configurarlo

```bat
copy .env.example .env
notepad .env
```

Completa `APP_JWT_SECRET`, `DB_PASSWORD` (y `POSTGRES_PASSWORD`/`PGADMIN_DEFAULT_PASSWORD` si usas Docker), `MAIL_USERNAME`, `MAIL_PASSWORD` (contrasena de aplicacion de Gmail, no la clave normal), `ANALITICA_DB_*` y `GROQ_API_KEY`. `levantar-sistema.bat` carga `.env` automaticamente antes de abrir las consolas de cada servicio; si lo corres a mano, exporta las variables antes de arrancar.

### Cómo compartir las credenciales con el equipo (sin exponerlas)

1. **Gestor de contraseñas (recomendado):** crea una entrada compartida del proyecto (Bitwarden, 1Password, KeePassXC con archivo compartido, o el gestor de tu universidad) y añade allí el `.env` completo. Cada compañero copia de ahí a su `.env` local.
2. **Canal privado como alternativa:** envía el contenido por mensaje directo (nunca por grupos de WhatsApp/Telegram, Issues, Pull Requests, commits ni correos a listas). Borra el mensaje cuando el compañero ya tenga su copia.
3. **Nunca** pegues credenciales en: código, `application.properties` versionado, README, capturas de pantalla de la demo, documentos de la tarea ni respuestas del chat del curso.

### Rotación: si una credencial ya se filtra, cámbiala

Las credenciales antiguas de este proyecto **ya están en el historial de git** (una contraseña de aplicación de Gmail y una API key de Groq), así que deben considerarse comprometidas aunque las borres del código actual:

- **Gmail:** en [Cuenta de Google → Seguridad → Contraseñas de aplicaciones](https://myaccount.google.com/apppasswords), revoca la contraseña `TU_CONTRASENA_DE_APLICACION` y genera una nueva. Actualiza `MAIL_PASSWORD` en tu `.env`.
- **Groq:** en [console.groq.com/keys](https://console.groq.com/keys), elimina la key `gsk_...` y crea otra; actualiza `GROQ_API_KEY` en tu `.env`. La clave vieja sigue siendo válida hasta que la revokes: borrarla del README no la desactiva.
- **Clave JWT:** genera un valor aleatorio de al menos 32 bytes, guardalo como `APP_JWT_SECRET` en `.env` y reinicia todos los servicios. Al rotarlo se invalidan todas las sesiones activas.

### Comprobación y prevención

```bat
git log -p | findstr /i "gsk_ TU_CONTRASENA_DE_APLICACION"
```

Si algo aparece fuera de `.env`, la credencial estuvo versionada: rótala. Para evitar repeticiones, instala [gitleaks](https://github.com/gitleaks/gitleaks) como hook:

```bat
gitleaks protect --staged
```

