# SOS Clinic

Sistema web de apoyo al registro y seguimiento de pacientes en el proceso de triage. El repositorio combina un frontend estático, servicios Java independientes, descubrimiento con Eureka y una base PostgreSQL local.

> **Estado:** prototipo académico en desarrollo. No debe tratarse como un sistema clínico de producción. El esquema SQL inicial no coincide con todos los modelos JPA, la autorización entre servicios está incompleta y hay secretos de desarrollo en configuración. Revisa [Estado y limitaciones conocidas](#estado-y-limitaciones-conocidas) antes de levantarlo desde cero o exponerlo en una red.

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
| `levantar-sistema.bat` | En Windows, levanta Docker Compose, abre consolas para todos los servicios de aplicación (gateway, seguridad, pacientes, triage, analítica y agente), define credenciales por defecto de MySQL para analítica si faltan y abre el login. Usa el comando global `mvn`; requiere Maven en `PATH`. Sus pausas son fijas, no comprobaciones de disponibilidad. |
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
| `frontend/index.html` | Login y formulario de verificación 2FA. |
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
| PostgreSQL | `localhost:5432`, base `sosclinic` | Usuario `sos_admin`; contraseña de desarrollo en `docker-compose.yml`. |
| pgAdmin | `http://localhost:5050` | Correo y contraseña de desarrollo en `docker-compose.yml`. |

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

El BAT ejecuta `bd_local.sql` con las credenciales locales `root:root` en `localhost:3306`. Ese SQL elimina y vuelve a crear `urgencias_hospital`. Después, el BAT verifica Python, instala `pandas`, `faker`, `sqlalchemy` y `pymysql`, y ejecuta `llenar_hospital.py`, que carga datos sintéticos de localidades, días, turnos y 50.000 llegadas.

> **Importante:** cada ejecución borra todos los datos previos de `urgencias_hospital` antes de volver a generarlos. El SQL se ejecuta antes de comprobar Python, por lo que la base también se reinicia si Python no está instalado y el BAT luego se detiene con el aviso correspondiente.

### 3. Iniciar servicios

Orden recomendado: Eureka, gateway y luego servicios de negocio:

1. `eureka-server`
2. `ms-gateway`
3. `ms-seguridad`, `ms-pacientes`, `ms-triage` y `ms-analitica`

Antes de arrancar, configura en el entorno local `ANALITICA_DB_USERNAME` y `ANALITICA_DB_PASSWORD` para MySQL; opcionalmente, `ANALITICA_DB_URL` (por defecto `jdbc:mysql://localhost:3306/urgencias_hospital`). `ms-seguridad` y `ms-analitica` comparten el fallback JWT local existente para desarrollo, por lo que no hace falta configurar una variable para el arranque actual. Puedes definir `APP_JWT_SECRET` para reemplazarlo, pero debe tener el mismo valor en ambos procesos. El fallback no es apropiado para producción.

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

`ms-analitica` corre como proceso local fuera de Docker y requiere que el MySQL sintético ya esté disponible. Prepara esa base manualmente en el paso 2; el arranque de servicios no ejecuta el generador. `levantar-sistema.bat` define `ANALITICA_DB_USERNAME` y `ANALITICA_DB_PASSWORD` en `root` solo si no están definidas; para otro usuario o clave, expórtalas antes de ejecutarlo. `ms-ia-agente` necesita `GROQ_API_KEY` para responder; sin ella el asistente indica que no está configurado.

Comprobaciones:

- Eureka: `http://localhost:8761`
- Gateway: `http://localhost:8080`
- En Eureka deben aparecer `MS-GATEWAY`, `MS-SEGURIDAD`, `MS-PACIENTES`, `MS-TRIAGE`, `MS-ANALITICA` y `MS-IA-AGENTE` una vez registrados.

### 4. Preparar datos de aplicación

El SQL incluido crea cuatro perfiles base (`triage`, `medico`, `coordinacion`, `seguridad`), cinco categorías de triage y tres estados de episodio. **No crea administrador, usuarios, actividades ni asignaciones `perfil_actividad`.** El esquema tampoco contiene hoy varias columnas/tablas que necesitan los modelos actuales; primero alinea la base de datos.

Una vez actualizado el esquema, crea la primera cuenta de forma controlada. La clave debe guardarse como BCrypt, nunca en texto plano. La opción normal es crear usuarios desde `roles.html`, ya que el backend codifica la clave y exige asignar un turno. Para el primer acceso administrativo se necesita un proceso de bootstrap controlado (por ejemplo, una inserción revisada con un hash BCrypt generado localmente). No compartas cuentas o hashes de demostración.

Después del primer acceso, asigna actividades con enlaces válidos al perfil correspondiente. Sin actividades, el usuario puede autenticarse pero el frontend no tendrá una pantalla de destino.

Para registrar `dashboard_analitica` y asignarla al perfil `coordinacion`, ejecuta una sola vez `infra/analytics-dashboard-activity.sql` contra PostgreSQL `sosclinic`. La actividad controla navegación; la API también valida el JWT compartido y exige el rol `COORDINACION` o `ADMINISTRADOR`.

### 5. Abrir el frontend

El script de Windows abre `frontend/index.html`; también puedes abrirlo directamente. La API apunta a `http://localhost:8080`, y el navegador guarda token y datos básicos de sesión en `sessionStorage`. Si el navegador bloquea las peticiones por CORS al abrir el archivo directamente, sirve `frontend/` desde un servidor estático local y permite ese origen en la configuración CORS del gateway.

## Flujo de trabajo normal

1. **Acceso del personal.** Se envían usuario y clave a `ms-seguridad`. El servicio comprueba que la cuenta esté activa, compara BCrypt y, excepto para el perfil llamado exactamente `administrador`, verifica que la hora esté dentro de un turno activo. Si se requiere 2FA, envía un código de seis dígitos al correo; vence en cinco minutos. Tras autenticar, devuelve JWT y actividades del perfil.
2. **Registro del paciente.** Se guardan nombre, apellido, cédula, fecha de nacimiento y, opcionalmente, correo. La cédula debe ser única. Un paciente existente puede localizarse por cédula desde el formulario de nuevo triage.
3. **Llegada a triage.** El operador selecciona paciente, categoría y, opcionalmente, ubicación. El backend crea el episodio en estado `En espera` y define la hora de llegada con el reloj del servidor. La categoría no se determina automáticamente.
4. **Priorización.** `GET /ms-triage/triage/prioridad` devuelve la lista priorizada. La prioridad numérica 1 es la más urgente; el tiempo de espera en minutos se calcula al consultar y no se mantiene como contador en segundo plano.
5. **Atención y seguimiento.** Desde la lista se abre `seguimiento.html?idRegistro=...`. Se puede actualizar ubicación, marcar `En atencion` y guardar tomas sucesivas de signos vitales. Las tomas anteriores se conservan.
6. **Cierre.** La interfaz exige diagnóstico y tratamiento antes del alta; `PUT /triage/{id}/atendido` recibe ambos campos (obligatorios), los persiste en `registro_triage` y cambia el estado a `Atendido`.
7. **Trazabilidad.** Algunas operaciones administrativas se registran desde el backend; el frontend intenta registrar login, creación y cambios de triage. La auditoría es de mejor esfuerzo: un error al guardar el log no cancela la operación principal.

Las funciones de historia clínica y teleconsulta dependen de correo SMTP. No asumas que el correo se entregó porque el cambio de estado haya concluido correctamente.

## Perfiles, actividades y turnos

El sistema separa estos conceptos:

- **Perfil (`perfil`):** etiqueta asignada a un usuario, por ejemplo `triage`, `medico`, `coordinacion` o `seguridad`. El SQL inicial crea estos cuatro nombres. `administrador` tiene comportamiento especial en algunas comprobaciones, pero no se crea automáticamente.
- **Actividad (`actividades`):** permiso de navegación del frontend, representado por nombre interno y archivo HTML, por ejemplo `crear_triage` → `triage-nuevo.html`.
- **Asignación (`perfil_actividad`):** relación que determina qué actividades recibe cada perfil al iniciar sesión.

`roles.html` permite crear/editar/eliminar perfiles y actividades, asignar o quitar actividades, y crear/editar usuarios. Al crear usuario se requiere clave y horario; la clave se codifica con BCrypt y se crea un turno activo. Al editar, dejar la clave vacía conserva la existente; también se puede cambiar perfil, 2FA y datos personales. Una cuenta inactiva no puede autenticarse.

Los turnos son intervalos horarios diarios, no calendarios por fecha. La comprobación admite intervalos que cruzan medianoche. El backend de seguridad omite la comprobación horaria para el perfil cuyo nombre sea exactamente `administrador`.

**Límite de autorización actual:** las actividades controlan principalmente el menú y navegación del navegador. `ms-seguridad` exige JWT para rutas protegidas, pero no restringe las operaciones administrativas a un perfil determinado. `ms-pacientes` y `ms-triage` no tienen filtros propios de autenticación y el gateway no valida globalmente el JWT. Por ello, las actividades asignadas no son autorización de API ni protección suficiente para datos clínicos.

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
| `POST` | `/ms-pacientes/pacientes/{id}/enviar-historia` | Envía contenido por correo si el paciente tiene email. |
| `POST` | `/ms-pacientes/pacientes/{id}/enviar-cita-teleconsulta` | Envía fecha/enlace recibidos en la solicitud. |

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

Las rutas requieren un JWT firmado con `APP_JWT_SECRET` y perfil `coordinacion` o `administrador`. Todas reciben fechas ISO inclusivas `desde=AAAA-MM-DD` y `hasta=AAAA-MM-DD`.

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
3. **Historia clínica y teleconsulta por correo.** El alta ya persiste diagnóstico y tratamiento en `registro_triage`, pero los correos dependen de SMTP y se registran como mejor esfuerzo: no asumas que la historia clínica llegó porque el alta concluya.
4. **Autorización por rol incompleta.** JWT se valida dentro de `ms-seguridad`, pero las APIs de pacientes y triage no instalan ese filtro; el gateway tampoco valida token globalmente. Las rutas administrativas solo requieren autenticación, no un rol específico.
5. **Secretos locales en configuración.** Hay secretos JWT y SMTP en archivos de propiedades versionados. Deben considerarse expuestos: rótalos, retíralos del repositorio y léelos desde variables de entorno o un gestor de secretos. No los uses en producción.
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

**Login correcto pero sin pantalla de destino**
Asigna al perfil una actividad cuyo enlace exista. `seguimiento.html` además necesita `idRegistro` en la URL.

**Login rechazado por horario**
Comprueba que la cuenta esté activa, que tenga turno con `id_estado = 1` y que la hora local esté dentro del intervalo. El perfil `administrador` es una excepción del código.

**No llega el correo o código 2FA**
Verifica SMTP y acceso de red. Usa secretos locales no versionados; no publiques contraseñas SMTP ni códigos.

**No se puede eliminar una actividad**
La actividad está asignada a uno o más perfiles (tabla `perfil_actividad`). La API responde 409 e indica los perfiles involucrados; quita primero esas asignaciones en `roles.html` y vuelve a intentar.

**Cambiaste SQL pero la base conserva el esquema anterior**
El volumen conserva su estado. Aplica una migración explícita o, si los datos son descartables, elimina el volumen conscientemente. `docker compose down -v` es destructivo.

## API KEY IA
setx GROQ_API_KEY "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"

