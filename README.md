# SOS Clinic

Sistema web de apoyo al registro y seguimiento de pacientes en el proceso de triage. El repositorio combina un frontend estático, servicios Java independientes, descubrimiento con Eureka y una base PostgreSQL local.

> **Estado:** prototipo academico en desarrollo. No debe tratarse como un sistema clinico de produccion. El esquema SQL inicial ya incluye turnos, codigos 2FA, aprobacion de cuentas y tokens de recuperacion; en bases existentes aplica `infra/migraciones/2026-10-19-seguridad.sql` y `infra/migraciones/2026-10-21-claves.sql`. La autenticacion y la autorizacion por rol estan implementadas en gateway, seguridad, pacientes, triage, analitica y agente. Revisa [Estado y limitaciones conocidas](#estado-y-limitaciones-conocidas) antes de levantarlo desde cero o exponerlo en una red.

## Contenido

- [Alcance actual](#alcance-actual)
- [Arquitectura](#arquitectura)
- [Mapa del repositorio](#mapa-del-repositorio)
- [Requisitos](#requisitos)
- [Puesta en marcha](#puesta-en-marcha)
- [Flujo de trabajo normal](#flujo-de-trabajo-normal)
- [Política de contraseñas y recuperación](#política-de-contraseñas-y-recuperación)
- [Perfiles, actividades y turnos](#perfiles-actividades-y-turnos)
- [Datos y persistencia](#datos-y-persistencia)
- [API disponible](#api-disponible)
- [Pruebas y mantenimiento](#pruebas-y-mantenimiento)
- [Estado y limitaciones conocidas](#estado-y-limitaciones-conocidas)
- [Problemas frecuentes](#problemas-frecuentes)
- [Credenciales y secretos](#credenciales-y-secretos)
- [Despliegue en Azure](#despliegue-en-azure)

## Alcance actual

Sujeto a que la base esté alineada con los modelos actuales, el sistema permite:

- Iniciar sesión con usuario y contraseña, emitir un JWT y completar un segundo factor por correo (2FA, obligatorio para `administrador` y `seguridad`), con recuperación de clave desde el login.
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

## Mapa del repositorio

### Raíz

| Ruta | Responsabilidad |
|---|---|
| `README.md` | Guía de arquitectura, configuración, operación y limitaciones. |
| `00. configurar-java.cmd` y `configurar-java.ps1` | Detecta un JDK 17+ instalado y configura `JAVA_HOME`/`PATH`; no instala Java. |
| `01. levantar-docker.bat` | Levanta PostgreSQL y pgAdmin con `docker compose` y muestra su estado. |
| `02. preparar-entorno.cmd` y `preparar-entorno.ps1` | Verifica Docker, exige el `.env` entregado y ejecuta el seed canónico; recomendado en el primer arranque. |
| `03. levantar-bd-local.bat` | Recrea y puebla la base MySQL sintética de analítica. |
| `04. levantar-sistema.bat` | En Windows, carga `.env`, levanta Docker Compose, inicia Eureka, gateway, seguridad, pacientes, triage, analitica y agente con `mvnw.cmd`, detecta Java, omite servicios ya activos y espera los puertos antes de abrir el login. |
| `05. detener-sistema.bat` | Detiene frontend, microservicios y contenedores del proyecto; conserva los datos de PostgreSQL. |
| `infra/` | Infraestructura local PostgreSQL/pgAdmin y SQL inicial. |
| `frontend/` | Interfaz estática que consume el gateway. |
| `eureka-server/` | Registro y descubrimiento de servicios. |
| `ms-gateway/` | Entrada HTTP y rutas hacia los servicios. |
| `ms-seguridad/` | Autenticación, JWT, identidades, perfiles, actividades, turnos y auditoría. |
| `ms-pacientes/` | Datos demográficos, signos vitales y correos relacionados con pacientes. |
| `ms-triage/` | Episodios, categorías, estados, ubicación y priorización. |
| `ms-analitica/` | Tablero de analítica sobre la base MySQL sintética `urgencias_hospital`. |
| `ms-ia-agente/` | Agente conversacional (chat y voz) con Groq y herramientas contra el gateway. |
| `build/` | Salidas generadas de compilaciones locales; no son fuente y no deben editarse. |

El prototipo Java EE anterior se retiró del árbol actual. Los informes de seguridad y de pruebas se conservan únicamente en la rama remota `pruebas-seguridad-2`; `main` mantiene el código y los scripts de prueba.

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
| `infra/schema-postgres.sql` | Esquema DDL (tablas e índices), `pgcrypto` y columnas que validan las entidades. No siembra datos: el seed canónico vive en `infra/seed/`. |
| `infra/seed/` | Seed canónico: perfiles, actividades, cuentas, pacientes y episodios sintéticos. `reset_seed.py` es el motor y `reset-seed.ps1`/`reset-seed.sh` los wrappers. |
| `infra/seed/credenciales.local.env` y `.md` | Generados localmente, ignorados por Git. Contienen las contraseñas únicas de las 9 cuentas. |
| `infra/backups/` | Backups automáticos previos a cada reset; ignorado por Git. |
| `infra/migraciones/2026-10-19-seguridad.sql` | Migración para bases existentes: amplía el código 2FA a hash BCrypt y añade `requiere_2fa`/`turno` si faltan. |
| `infra/migraciones/2026-10-21-claves.sql` | Migración para bases existentes: aprobación de cuentas, activación/recuperación/cambio de correo por token, cambio obligatorio de clave, `challenge` 2FA y actividad `aprobar_cuentas`. |
| `infra/migraciones/2026-10-19-actividades.sql` | Limpia actividades duplicadas por enlace y crea índices únicos para nombre y enlace. |
| `infra/migraciones/2026-10-22-seed-drift.sql` | Añade `paciente.email` y las columnas de cierre de triage, e instala `pgcrypto` si faltan. |
| `frontend/index.html` | Landing: presentación animada del proyecto, con el video y el acceso. |
| `frontend/login.html` | Login, enlace de recuperación y formulario de verificación 2FA con `challenge`. |
| `frontend/recuperar-clave.html` | Solicitud de enlace de recuperación por usuario o correo. |
| `frontend/restablecer-clave.html` | Define la clave nueva desde un enlace de activación o recuperación; también confirma cambios de correo. |
| `frontend/cambiar-clave.html` | Cambio de clave autenticado y cambio obligatorio de una clave temporal. |
| `frontend/cuenta.html` | Datos de la cuenta, cambio de clave, alta/baja de 2FA y cambio de correo verificado. |
| `frontend/aprobaciones.html` | Cola de cuentas pendientes para el perfil `seguridad`. |
| `frontend/pacientes.html` | Registro/listado de pacientes y entrada al flujo según actividades asignadas. |
| `frontend/triage-nuevo.html` | Búsqueda por cédula y registro de llegada con categoría y ubicación. |
| `frontend/lista-espera.html` | Consulta de priorización y acceso al seguimiento. |
| `frontend/seguimiento.html` | Detalle del episodio, ubicación, transición a atención, signos vitales e interfaz de alta. Requiere `?idRegistro=...`. |
| `frontend/auditoria.html` | Consulta de acciones registradas por backend y frontend. |
| `frontend/roles.html` | Administración de perfiles, actividades, asignaciones, usuarios y turnos. |
| `frontend/dashboard.html` | Tablero de analítica por rango de fechas, localidad y turno; consume `ms-analitica` vía gateway. |
| `frontend/assets/api.js` | URL base, sesión en `sessionStorage`, envío JWT, manejo de errores y funciones compartidas de API. |
| `frontend/assets/clave.js` | Checklist y medidor de fuerza de la política de claves; el backend sigue siendo la autoridad. |
| `frontend/assets/copiloto.js` | Widget flotante del agente IA (chat y voz) en las páginas con sesión activa. |
| `frontend/assets/estilos.css` | Estilos compartidos; algunas páginas añaden estilos locales en su HTML. |

## Requisitos

- JDK 17.
- Docker Desktop con Docker Compose.
- Java 17+; el arranque usa el wrapper incluido (`mvnw.cmd`), no requiere Maven en `PATH`.
- Navegador moderno.
- Python 3 para servir el frontend (`frontend/servidor.py`) y ejecutar el motor del seed (`infra/seed/reset_seed.py`).
- Para preparar los datos de analítica: MySQL Server iniciado, el cliente `mysql` en `PATH` y Python disponible como `python`.
- Opcional: NetBeans para abrir/ejecutar los proyectos y pgAdmin para inspeccionar PostgreSQL.

El IDE no es requisito para compilar o ejecutar. El plugin de Spring para NetBeans mencionado en documentación antigua no es necesario para el flujo normal.

## Puesta en marcha

### Primer arranque desde un clon nuevo

El repositorio no versiona `.env` ni una base de datos ya poblada. El paquete de preparación (`SOS-Clinic-Preparacion`) contiene:

```text
.env
credenciales.local.env
credenciales.local.md
```

El `.env` ya trae PostgreSQL, JWT, SMTP, MySQL y Groq. Las credenciales del seed son las cuentas actuales. El clon solo aporta el código.

#### Requisitos

- Docker Desktop iniciado.
- Python 3 disponible como `python`.
- JDK 17+. Si ya existe uno instalado, `00. configurar-java.cmd` lo detecta; no es necesario instalar otro. Si no hay ninguno, instala Eclipse Temurin 17 o 21.

#### Pasos

1. **Clonar el repositorio** y entrar a su raíz.

2. **Copiar los archivos de preparación**, usando la ruta real de la carpeta:

   ```powershell
   $prep = "C:\ruta\a\SOS-Clinic-Preparacion"
   Copy-Item "$prep\.env" ".env"
   Copy-Item "$prep\credenciales.local.env" "infra\seed\"
   Copy-Item "$prep\credenciales.local.md" "infra\seed\"
   ```

   Las credenciales van dentro de `infra\seed\`, no en la raíz. No subas ninguno de estos archivos a Git.

3. **Configurar Java**:

   ```powershell
   & ".\00. configurar-java.cmd" -Persist
   ```

   Cierra y abre una terminal nueva. Si no encuentra ningún JDK, el script indica qué falta; no instala Java por su cuenta.

3.1. **Levantar la infraestructura Docker (recomendado)**: si quieres arrancar y revisar PostgreSQL/pgAdmin antes del seed:

   ```powershell
   & ".\01. levantar-docker.bat"
   ```

   El paso siguiente también levanta PostgreSQL automáticamente.

4. **Preparar PostgreSQL y cargar el seed**:

   ```powershell
   & ".\02. preparar-entorno.cmd"
   ```

   Verifica Docker y Python, exige que exista `.env`, levanta PostgreSQL, aplica las migraciones y carga el seed. Debe dejar **9 cuentas, 24 pacientes y 30 episodios**. Este paso reemplaza los datos de aplicación: no lo ejecutes sobre una base cuyos datos quieras conservar.

5. **Preparar analítica (solo si se va a usar)**:

   ```powershell
   net start MySQL80
   & ".\03. levantar-bd-local.bat"
   ```

   El script elimina y recrea `urgencias_hospital`, y carga 50.000 registros. Si el MySQL local no usa las credenciales del `.env`, ajusta `ANALITICA_DB_USERNAME` y `ANALITICA_DB_PASSWORD` antes de ejecutarlo.

6. **Arrancar el sistema**:

   ```powershell
   & ".\04. levantar-sistema.bat"
   ```

   Abre Eureka, gateway, seguridad, pacientes, triage, analítica, agente y el frontend. Comprueba:

   ```text
   Eureka:   http://localhost:8761
   Gateway:  http://localhost:8080
   Frontend: http://127.0.0.1:5500
   pgAdmin:  http://localhost:5050
   ```

7. **Iniciar sesión** con un usuario de `infra\seed\credenciales.local.md`. El 2FA llega a `pruebasosclinic@gmail.com`; el asunto incluye el usuario, por ejemplo `[admin.test] Codigo de verificacion - SOS Clinic`. El código vence en 5 minutos.

8. **Apagar**:

   ```powershell
   & ".\05. detener-sistema.bat"
   ```

   Detiene frontend, microservicios y contenedores. Conserva el volumen de PostgreSQL y no detiene MySQL.

#### Notas rápidas

- PowerShell con política `Restricted` no es problema si usas los `.cmd`: invocan PowerShell con `-ExecutionPolicy Bypass`.
- Si `02. preparar-entorno.cmd` falla por contraseña de PostgreSQL, hay un volumen anterior con otra clave. Ajusta `DB_PASSWORD`/`POSTGRES_PASSWORD` o reconstruye el volumen conscientemente con `.\infra\seed\reset-seed.ps1 -CleanVolume -Yes`; esto último borra la base local.
- El primer build de Maven puede tardar: descarga dependencias.
- No abras `frontend\index.html` directamente; usa el servidor Python del puerto 5500.
- La carpeta de preparación contiene secretos. No la publiques fuera del equipo.

### 0. Copiar el archivo `.env`

Las credenciales (SMTP de correo, MySQL de analítica y API key de Groq) no están en el repositorio. El paquete de preparación entrega el `.env` completo; cópialo a la raíz del clon:

```powershell
$prep = "C:\ruta\a\SOS-Clinic-Preparacion"
Copy-Item "$prep\.env" .env
```

No hay una plantilla de `.env` versionada ni debes crear un `.env` vacío: el paquete ya trae los valores locales. No subas el `.env` a Git.

`04. levantar-sistema.bat` lo carga automaticamente, y ademas cada servicio lo importa al arrancar con un pequeno cargador propio (`EnvEnvironmentPostProcessor`, que busca `.env` en el directorio del modulo y en la raiz del repo), asi que **lanzar desde NetBeans o `mvn spring-boot:run` tambien funciona sin exportar nada**. Sin `.env` el sistema no arranca: copia el archivo del paquete de preparación.

### 1. Iniciar PostgreSQL

Desde PowerShell, CMD o una terminal abierta en el repositorio:

```powershell
& ".\01. levantar-docker.bat"
```

Equivalente manual, teniendo en cuenta que el `.env` está en la raíz:

```powershell
cd infra
docker compose --env-file ..\.env up -d
docker compose --env-file ..\.env ps
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
& ".\03. levantar-bd-local.bat"
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
& ".\04. levantar-sistema.bat"
```

El script arranca los servicios, espera a que los puertos esten listos y abre el login. Si un puerto ya esta ocupado, omite ese servicio.

Para detener todo:

```powershell
& ".\05. detener-sistema.bat"
```

Detiene microservicios, frontend y contenedores del proyecto; no borra el volumen de PostgreSQL.

`ms-analitica` corre como proceso local fuera de Docker y requiere que el MySQL sintético ya esté disponible. Prepara esa base manualmente en el paso 2; el arranque de servicios no ejecuta el generador. `04. levantar-sistema.bat` carga `.env` y exige `APP_JWT_SECRET`; no define credenciales por defecto. `ms-ia-agente` necesita `GROQ_API_KEY` (definida en `.env`; `04. levantar-sistema.bat` la expone a las consolas) para responder; sin ella el asistente indica que no está configurado.

Comprobaciones:

- Eureka: `http://localhost:8761`
- Gateway: `http://localhost:8080`
- En Eureka deben aparecer `MS-GATEWAY`, `MS-SEGURIDAD`, `MS-PACIENTES`, `MS-TRIAGE`, `MS-ANALITICA` y `MS-IA-AGENTE` una vez registrados.

### 4. Cargar el seed canónico

El seed reemplaza los datos de aplicación por un conjunto reproducible: **8 perfiles, 8 actividades, 22 asignaciones, 5 categorías, 3 estados, 9 cuentas, 9 turnos, 24 pacientes y 30 episodios**. No crea usuarios de demostración: las cuentas son institucionales, con contraseñas únicas, 2FA activo y correos que llegan a `pruebasosclinic@gmail.com`.

Desde la raíz del repositorio, con `.env` completo:

```powershell
.\infra\seed\reset-seed.ps1
```

```bash
bash infra/seed/reset-seed.sh
```

Opciones:

- `-CleanVolume` / `--clean-volume`: elimina el volumen y reconstruye el esquema. Es destructivo y pide confirmación.
- `-Yes` / `--yes`: no pide confirmación.
- `-SkipBackup` / `--skip-backup`: no hace backup (no recomendado).
- `-RegenerateCredentials` / `--regenerate-credentials`: genera nuevas contraseñas para las 9 cuentas.

El script:

1. Genera o reutiliza `infra/seed/credenciales.local.env` y `credenciales.local.md`, ambos **ignorados por Git**. El `.md` es la lista de usuarios, perfiles, correos y contraseñas.
2. Hace un backup `pg_dump -Fc` en `infra/backups/` antes de tocar datos.
3. Aplica las migraciones, trunca solo las tablas de aplicación y carga el seed con `pgcrypto`/BCrypt. No borra la base, el esquema ni los roles de PostgreSQL.
4. Ejecuta `infra/seed/verify.sql` y muestra los conteos.

Cuentas del seed:

| Usuario | Perfil | Turno |
|---|---|---|
| `admin.test` | administrador | **00:00–00:00 (24 h, cuenta maestra)** |
| `seguridad.sos` | seguridad | **00:00–00:00 (24 h)** |
| `coordinacion.sos` | coordinador_turnos | **00:00–00:00 (24 h)** |
| `medico.dia` | doctor_triage | **00:00–00:00 (24 h)** |
| `medico.noche` | doctor_triage | **00:00–00:00 (24 h)** |
| `recepcion.sos` | recepcionista | **00:00–00:00 (24 h)** |
| `triage.sos` | triage | **00:00–00:00 (24 h)** |
| `listas.sos` | lista_espera | **00:00–00:00 (24 h)** |
| `auditoria.sos` | auditor | **00:00–00:00 (24 h)** |

Todas tienen 2FA activo. Los códigos y enlaces de confirmación se envían a las direcciones `pruebasosclinic+...@gmail.com`, que Gmail entrega en la bandeja `pruebasosclinic@gmail.com`.

Para verificar el seed con el stack levantado:

```powershell
python infra/seed/verify_seed.py
```

El verificador completa el 2FA con códigos temporales, comprueba el perfil de las 9 cuentas y un endpoint por rol, y limpia los códigos temporales. El seed vuelve a quedar con 0 códigos y las 9 cuentas conservan su turno de 24 h.

Las cuentas del seed ya están aprobadas. El flujo de aprobación sigue vigente para cuentas nuevas creadas desde `roles.html`: el administrador las deja pendientes y un usuario `seguridad` las aprueba.

### 5. Abrir el frontend

El script de Windows sirve `frontend/` en `http://localhost:5500` con Python y abre la landing (`index.html`); si Python no está disponible, abre los archivos directamente (en ese caso el video de la landing muestra una miniatura con enlace a YouTube, porque el reproductor incrustado exige un referrer HTTP). La API apunta a `http://localhost:8080`, y el navegador guarda token y datos básicos de sesión en `sessionStorage`. Si el navegador bloquea las peticiones por CORS al abrir el archivo directamente, sirve `frontend/` desde un servidor estático local y permite ese origen en la configuración CORS del gateway.

## Flujo de trabajo normal

1. **Acceso del personal.** Se envían usuario y clave a `ms-seguridad`. El servicio comprueba que la cuenta esté aprobada y activa, compara BCrypt y, excepto para los perfiles `administrador` y `seguridad`, verifica que la hora esté dentro de un turno activo. Si se requiere 2FA, envía un código de seis dígitos al correo; vence en cinco minutos y el segundo paso exige un *challenge* emitido tras validar la clave. Tras autenticar, devuelve JWT y actividades del perfil. Las cuentas nuevas se crean **pendientes de aprobación** y se activan por correo; el administrador nunca define la clave. La política de claves se valida siempre en el backend.
2. **Registro del paciente.** Se guardan nombre, apellido, cédula, fecha de nacimiento y, opcionalmente, correo. La cédula debe ser única. Un paciente existente puede localizarse por cédula desde el formulario de nuevo triage.
3. **Limite de autorizacion:** las actividades siguen controlando el menu y la navegacion del navegador, pero la autorizacion real se aplica en el backend. El gateway valida el JWT y cada servicio vuelve a validarlo. `ms-seguridad` exige `ADMINISTRADOR` para `/admin/**` (salvo auditoria, que admite `ADMINISTRADOR`, `SEGURIDAD` y `AUDITOR`, y las rutas de aprobacion, que exigen `SEGURIDAD`); `ms-pacientes` y `ms-triage` admiten roles clinicos y de recepcion/lista de espera; `ms-analitica` exige `COORDINACION` o `ADMINISTRADOR`; `ms-ia-agente` exige un usuario autenticado.
4. **Priorización.** `GET /ms-triage/triage/prioridad` devuelve la lista priorizada. La prioridad numérica 1 es la más urgente; el tiempo de espera en minutos se calcula al consultar y no se mantiene como contador en segundo plano.
5. **Atención y seguimiento.** Desde la lista se abre `seguimiento.html?idRegistro=...`. Se puede actualizar ubicación, marcar `En atencion` y guardar tomas sucesivas de signos vitales. Las tomas anteriores se conservan.
6. **Cierre y notificaciones.** La interfaz exige diagnóstico, tratamiento y la dirección del paciente; el alta se completa con **una sola llamada** a `POST /ms-pacientes/pacientes/atenciones/{idRegistro}/finalizar`. El backend persiste primero el alta en `ms-triage` (si eso falla, no se envía nada) y en seguida envía las notificaciones: confirmación de teleconsulta al profesional (`ms-seguridad`), cita de teleconsulta al paciente (con archivo de calendario `.ics`) e historia clínica en PDF (más farmacias cercanas a la dirección; si no se puede ubicar, farmacias de Bogotá en general). Cada correo se evalúa por separado y la respuesta informa ✅ enviado / ⚠️ fallido / ⏭️ omitido; la pantalla ofrece un botón de reintento que llama a `reenviar-correos` sin volver a tocar el alta.
7. **Trazabilidad.** Algunas operaciones administrativas se registran desde el backend; el frontend intenta registrar login, creación y cambios de triage. La auditoría es de mejor esfuerzo: un error al guardar el log no cancela la operación principal.

Las funciones de historia clínica y teleconsulta dependen de correo SMTP. No asumas que el correo se entregó porque el cambio de estado haya concluido correctamente: revisa el estado por correo que devuelve la operación de alta.

## Política de contraseñas y recuperación

La política se aplica en el backend (`PoliticaClaves`) y el frontend solo muestra el checklist. Reglas:

- Longitud entre 8 y 64 caracteres.
- Solo letras ASCII sin tilde, números y los símbolos `! ? # $ % . * @ _ - +`.
- Al menos una mayúscula, una minúscula, un número y un símbolo.
- Sin tres caracteres iguales seguidos, secuencias (`abc`, `123`, `cba`, `321`) ni patrones de teclado (`qwerty`, `asdf`).
- Sin palabras comunes ni el nombre del sistema (se normalizan tildes y sustituciones tipo *leetspeak*).
- Sin datos del propio perfil: nombre, apellido, usuario, correo (completo y parte local), cédula o teléfono.
- Sin reutilizar la clave actual.

**Alta con doble control.** El administrador crea la cuenta sin clave; queda `PENDIENTE` e inactiva. Un usuario con perfil `seguridad` la aprueba desde `aprobaciones.html` y solo entonces se envía el enlace de activación al correo del usuario. El enlace vence en 72 h y es de un solo uso. Así el administrador no conoce la clave y la aprobación queda en manos de otra persona. El aprobador debe verificar que el correo y el nombre correspondan al empleado: el administrador controla el correo en el alta y podría poner una dirección propia. Para que este flujo funcione deben existir al menos **dos cuentas iniciales** creadas fuera del flujo normal: un `administrador` y un `seguridad` con hashes BCrypt generados localmente. No compartas esas cuentas.

**Recuperación desde el login.** `login.html` enlaza a `recuperar-clave.html`. El usuario ingresa su usuario o correo y recibe un enlace de 30 minutos al correo registrado (el mismo que usa el 2FA). La respuesta es siempre genérica para no revelar si la cuenta existe. El enlace es de un solo uso y, al restablecer, invalida los códigos 2FA y los tokens pendientes.

**Segundo factor.** El segundo paso usa un *challenge* opaco de 32 bytes que se emite solo después de validar la clave. Se guarda su SHA-256 en `codigo_verificacion.challenge_hash`, dura 5 minutos, es de un solo uso y `verificar-2fa` deriva el usuario de él, no del cuerpo de la petición. Reenviar el código usa el mismo challenge y ya no requiere conservar la contraseña en JavaScript. El 2FA es obligatorio para `administrador` y `seguridad`; el resto de cuentas lo reciben activado y solo el dueño puede desactivarlo, con clave actual y un código nuevo. El administrador no puede cambiar el 2FA de otra persona.

**Sesiones.** El JWT dura 2 horas. Al cambiar o restablecer la clave se invalidan los tokens de recuperación y los códigos 2FA pendientes, pero los JWT ya emitidos siguen siendo válidos hasta que expiran; no hay revocación centralizada. No uses el sistema con datos reales sin resolver esa limitación.

## Sistema de correos

Los correos se construyen y envían **siempre desde el backend** (el frontend ya no arma textos ni orquesta llamadas). Los componentes se repiten en `ms-pacientes` y `ms-seguridad`, porque no existe módulo compartido:

| Pieza | Ubicación | Qué hace |
|---|---|---|
| `EmailService` | ambos servicios | Renderiza plantillas Thymeleaf, genera texto alternativo y envía con adjuntos e imágenes embebidas (`cid:`). Tiempos de espera SMTP de 10 s. |
| Plantillas | `src/main/resources/templates/plantillas/` | HTML con tablas y CSS en línea (compatible con Gmail/Outlook) en la paleta teal de la app: cita de paciente, confirmación al profesional, resumen de historia clínica, código 2FA, activación de cuenta, recuperación de clave, cambio de correo, avisos de seguridad y PDF. |
| `HistoriaClinicaService` + `PdfService` | `ms-pacientes` | Consolidan paciente, episodio y signos vitales; generan el PDF con OpenHTMLtoPDF y las tipografías DejaVu embebidas en `resources/fonts/`. |
| `NotificacionesService` | `ms-pacientes` | Punto único de envío de cita e historia (lo usan el endpoint heredado y el orquestador). |
| `AtencionService` | `ms-pacientes` | Orquesta alta + correos con aislamiento de errores por correo. |
| `FarmaciasService` | `ms-pacientes` | Overpass API de OpenStreetMap (radio 3 km, 5 más cercanas) geocodificando la dirección del paciente con Nominatim; si la dirección falta o no se puede ubicar, usa el centro de Bogotá (radio 12 km) como respaldo. Mapa PNG compuesto a partir de los tiles de OSM. Cadena de fuentes: instancia principal de Overpass → `overpass.kumi.systems` → búsqueda por categoría en Nominatim, para que el correo salga con farmacias aunque las instancias públicas de Overpass estén caídas. Sin API key. |
| `TeleconsultaService` + `IcsService` | `ms-seguridad` | Cita determinista (+7 días, 10:00 `America/Bogota`, 30 min, sala Jitsi) y archivo `.ics` (RFC 5545) adjunto para ambos correos. |

Los correos de seguridad (2FA, activación, recuperación, cambio de correo y avisos) incluyen el usuario entre corchetes en el asunto y, en el cuerpo, nombre, usuario y perfil. Así se distingue a quién va dirigido cada mensaje cuando todos llegan a la misma bandeja de prueba.

Endpoints nuevos en `ms-pacientes`:

- `POST /pacientes/atenciones/{idRegistro}/finalizar` — alta + notificaciones en una sola llamada (recibe `diagnosticoFinal`, `tratamiento` y opcionalmente `latitud`, `longitud`, `direccion`).
- `POST /pacientes/atenciones/{idRegistro}/reenviar-correos` — reintento de notificaciones de un episodio ya `Atendido` (el alta no se vuelve a tocar; `TriageService.darDeAlta` tiene guard de idempotencia).
- `GET /pacientes/historias/{idRegistro}/pdf` — descarga del PDF oficial de la historia clínica (botón en `seguimiento.html`).

Los endpoints antiguos (`enviar-historia`, `enviar-cita-teleconsulta`, `PUT .../atendido`) se mantienen por compatibilidad.

### Probar los correos con Gmail

El sistema usa SMTP real. Configura en `.env`:

```properties
MAIL_SMTP_HOST=smtp.gmail.com
MAIL_SMTP_PORT=587
MAIL_USERNAME=tu-cuenta@gmail.com
MAIL_PASSWORD=contrasena-de-aplicacion
```

Gmail exige verificacion en dos pasos y una contrasena de aplicacion; no uses la clave normal de la cuenta. Para pruebas usa una cuenta y destinatarios de prueba, y no envies datos clinicos reales. La entrega queda sujeta a los limites y filtros de Gmail; revisa la bandeja de enviados o la del destinatario. No hay captura local de correo.

## Perfiles, actividades y turnos

El sistema separa estos conceptos:

- **Perfil (`perfil`):** etiqueta asignada a un usuario. El seed crea **8**: `administrador`, `seguridad`, `coordinador_turnos`, `doctor_triage`, `triage`, `recepcionista`, `lista_espera` y `auditor`. `administrador` y `seguridad` no tienen restricción de turno; `seguridad` aprueba cuentas.
- **Actividad (`actividades`):** permiso de navegación del frontend, representado por nombre interno y archivo HTML, por ejemplo `crear_triage` → `triage-nuevo.html`. El seed crea 8; la pantalla de analítica (`dashboard_analitica`) reemplazó a la antigua pantalla de enfermedades.
- **Asignación (`perfil_actividad`):** relación que determina qué actividades recibe cada perfil al iniciar sesión.

`roles.html` permite crear/editar/eliminar perfiles y actividades, asignar o quitar actividades, y crear/editar usuarios. Al crear un usuario **no se define clave**: la cuenta queda `PENDIENTE`, un usuario con perfil `seguridad` la aprueba desde `aprobaciones.html` y el sistema envía al correo del usuario un enlace de activación de 72 h. Al activarlo, el usuario define su propia clave. El administrador puede reenviar la activación o, solo antes de la primera activación, generar una clave temporal de emergencia (se muestra una vez, vence en 72 h y obliga a cambiarla al primer ingreso). Una cuenta inactiva o pendiente no puede autenticarse. El correo y la clave de una cuenta ya activada solo los cambia el propio usuario desde `cuenta.html`.

**Cuentas del seed y cuentas nuevas.** El seed deja las 9 cuentas ya aprobadas y activas. Para una cuenta nueva, el administrador la crea desde `roles.html` sin clave; queda `PENDIENTE` y un usuario `seguridad` (`seguridad.sos` en el seed) la aprueba desde `aprobaciones.html`. Después se envía el enlace de activación. El bootstrap manual de una primera cuenta `seguridad` ya no es necesario: el seed incluye `seguridad.sos`.

Los turnos son intervalos horarios diarios, no calendarios por fecha. La comprobación admite intervalos que cruzan medianoche. El backend de seguridad omite la comprobación horaria para los perfiles `administrador` y `seguridad`. En el seed, **las 9 cuentas tienen turno de 00:00 a 00:00 (24 h)** para que las pruebas funcionales no dependan de la hora; `admin.test` sigue siendo la cuenta maestra. Las cuentas adicionales con turno 24 h no reciben privilegios extra: cada una conserva su perfil y sus actividades.

**Limite de autorizacion:** las actividades siguen controlando el menu y la navegacion del navegador, pero la autorizacion real se aplica en el backend. El gateway valida el JWT y cada servicio vuelve a validarlo. `ms-seguridad` exige `ADMINISTRADOR` para `/admin/**` (salvo auditoria, que admite `ADMINISTRADOR`, `SEGURIDAD` y `AUDITOR`, y las rutas de aprobacion, que exigen `SEGURIDAD`); `ms-pacientes` y `ms-triage` admiten `ADMINISTRADOR`, `MEDICO`, `TRIAGE`, `COORDINACION`, `RECEPCIONISTA` y `LISTA_ESPERA` segun el endpoint; `ms-analitica` exige `COORDINACION` o `ADMINISTRADOR`; `ms-ia-agente` exige un usuario autenticado y deriva sus permisos del token. Las actividades ya no son la unica barrera para los datos clinicos.

## Datos y persistencia

El esquema de referencia está en `infra/schema-postgres.sql`. El SQL inicial crea las tablas siguientes:

| Tabla | Uso y relaciones |
|---|---|
| `perfil` | Perfiles de personal; nombre único y estado activo/inactivo. |
| `personal` | Cuenta y datos del profesional; referencia a `perfil`. Incluye estado de aprobación, cambio obligatorio de clave y 2FA. `clave` debe contener hash, nunca texto claro. |
| `codigo_verificacion` | Códigos 2FA hasheados, su `challenge` de pre-autenticación, propósito y expiración. |
| `token_recuperacion` | Tokens de activación, recuperación y cambio de correo: SHA-256, tipo, expiración, un solo uso e IP/User-Agent de la solicitud. |
| `actividades` | Nombre interno y enlace de pantalla. |
| `perfil_actividad` | Asignación única de actividad a perfil. |
| `paciente` | Datos demográficos, cédula única, email y estado. |
| `categoria_triage` | Catálogo de categorías con prioridad única; prioridad 1 es la mayor. |
| `estado_triage` | Catálogo de estados de episodios. |
| `registro_triage` | Episodio asociado a paciente, categoría, estado, ubicación, fecha/hora de llegada y cierre (`diagnostico_final`, `tratamiento`). |
| `estado_paciente` | Cada toma de signos vitales, asociada a episodio y al ID del personal que la registró. |
| `registro_auditoria` | Actor, paciente opcional, acción, detalle y fecha/hora. |

El esquema ya incluye `turno`, `codigo_verificacion`, `token_recuperacion`, las columnas de aprobación/cambio obligatorio/2FA, `paciente.email` y las columnas de cierre de triage. En bases existentes aplica las migraciones de `infra/migraciones/`; para bases nuevas, ejecuta el seed después de crear el volumen.

### Reglas de datos

- Las identidades las genera PostgreSQL. No asumas que los IDs serán consecutivos después de borrar registros.
- Las claves foráneas relacionan paciente/episodio, episodio/catálogos de triage, personal/perfil y perfiles/actividades.
- Algunas referencias de otros dominios son identificadores numéricos en vez de relaciones JPA. Por ejemplo, los signos guardan `id_registro` e `id_personal`; esto no significa que haya bases físicas separadas por servicio.
- `id_estado` normalmente usa `1` activo y `0` inactivo. Los episodios usan el catálogo `estado_triage`.
- `spring.jpa.hibernate.ddl-auto=validate`: Hibernate valida tablas/columnas, pero no crea ni migra el esquema. El esquema y el seed son pasos explícitos.
- `sos_pgdata` conserva la base entre reinicios. Los scripts de inicialización no se vuelven a ejecutar sobre un volumen existente; para eso está `reset_seed.py --clean-volume`.

## API disponible

Todas las rutas se exponen en `http://localhost:8080`. El gateway elimina el prefijo del servicio antes de reenviar; por ejemplo, `/ms-pacientes/pacientes` llega al controlador como `/pacientes`.

### Autenticación y seguridad

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/ms-seguridad/auth/login` | Valida usuario/clave, aprobación y turno; puede devolver un `challenge` para 2FA o el token y las actividades. |
| `POST` | `/ms-seguridad/auth/verificar-2fa` | Valida `challenge` + código y devuelve el token si es correcto. |
| `POST` | `/ms-seguridad/auth/reenviar-2fa` | Reenvía el código usando el `challenge`, sin pedir la clave. |
| `GET` | `/ms-seguridad/auth/politica-clave` | Publica las reglas de clave que usa el checklist del frontend. |
| `POST` | `/ms-seguridad/auth/recuperar-clave` | Envía un enlace de recuperación (respuesta genérica, 30 min). |
| `POST` | `/ms-seguridad/auth/validar-token-recuperacion` | Valida un enlace e informa su tipo (`RECUPERACION`, `ACTIVACION` o `CAMBIO_EMAIL`). |
| `POST` | `/ms-seguridad/auth/restablecer-clave` | Define la clave nueva desde un enlace de recuperación o activación. |
| `POST` | `/ms-seguridad/auth/confirmar-email` | Confirma un cambio de correo desde el enlace enviado a la dirección nueva. |
| `POST` | `/ms-seguridad/auth/cambiar-clave` | Cambio de clave autenticado (clave actual + nueva); devuelve un token nuevo. |
| `GET` | `/ms-seguridad/auth/mi-cuenta` | Datos de la cuenta autenticada, sin hash ni secretos. |
| `POST` | `/ms-seguridad/auth/2fa/activar` | Activa la verificación en dos pasos. |
| `POST` | `/ms-seguridad/auth/2fa/desactivar/solicitar` | Verifica la clave actual y envía un código para desactivar 2FA. |
| `POST` | `/ms-seguridad/auth/2fa/desactivar` | Desactiva 2FA con el código enviado. Prohibido para perfiles privilegiados. |
| `POST` | `/ms-seguridad/auth/solicitar-cambio-email` | Envía la verificación al correo nuevo; solo el dueño puede hacerlo. |
| `POST` | `/ms-seguridad/auth/programar-teleconsulta` | Solicita programación/confirmación usando el usuario autenticado. |
| `POST` | `/ms-seguridad/auditoria/registrar` | Registra una acción solicitada por el frontend; requiere JWT. |
| `GET` | `/ms-seguridad/admin/perfiles` | Lista perfiles. |
| `POST`, `PUT /{id}`, `DELETE /{id}` | `/ms-seguridad/admin/perfiles` | Crea, edita o elimina perfil. |
| `GET` | `/ms-seguridad/admin/actividades` | Lista actividades. |
| `POST`, `PUT /{id}`, `DELETE /{id}` | `/ms-seguridad/admin/actividades` | Crea, edita o elimina actividad. |
| `GET`, `POST` | `/ms-seguridad/admin/perfil-actividad` | Lista asignaciones o asigna actividad a perfil. |
| `DELETE` | `/ms-seguridad/admin/perfil-actividad/{id}` | Quita una asignación. |
| `GET`, `POST` | `/ms-seguridad/admin/personal` | Lista o crea personal. Al crear solo exige datos y turno: la cuenta queda pendiente de aprobación. |
| `PUT` | `/ms-seguridad/admin/personal/{id}` | Edita datos, perfil y turno. No cambia clave ni 2FA; el correo de una cuenta activada solo lo cambia el usuario. |
| `PUT` | `/ms-seguridad/admin/personal/{id}/estado?idEstado=0\|1` | Activa o desactiva un usuario ya aprobado. |
| `GET` | `/ms-seguridad/admin/personal/pendientes` | Lista cuentas pendientes de aprobación (`SEGURIDAD`). |
| `POST` | `/ms-seguridad/admin/personal/{id}/aprobar` | Aprueba la cuenta y envía el enlace de activación (`SEGURIDAD`). |
| `POST` | `/ms-seguridad/admin/personal/{id}/rechazar` | Rechaza la solicitud (`SEGURIDAD`). |
| `POST` | `/ms-seguridad/admin/personal/{id}/activacion` | Reenvía el enlace de activación (`ADMINISTRADOR`). |
| `POST` | `/ms-seguridad/admin/personal/{id}/restablecer` | Envía un enlace de recuperación a una cuenta activada (`ADMINISTRADOR`). |
| `POST` | `/ms-seguridad/admin/personal/{id}/clave-temporal` | Genera una clave temporal antes de la primera activación; se muestra una vez (`ADMINISTRADOR`). |
| `GET`, `POST` | `/ms-seguridad/admin/turnos` | Lista o crea turnos. |
| `PUT` | `/ms-seguridad/admin/turnos/{id}` | Edita las horas. |
| `PUT` | `/ms-seguridad/admin/turnos/{id}/estado?idEstado=0\|1` | Activa o desactiva turno. |
| `GET` | `/ms-seguridad/admin/auditoria` | Lista registros de auditoría. |

`/admin/**` exige `ADMINISTRADOR`; `/admin/auditoria/**` admite `ADMINISTRADOR`, `SEGURIDAD` y `AUDITOR`; las rutas de aprobación exigen `SEGURIDAD` y se evalúan antes del matcher general. El gateway valida el JWT y bloquea cualquier token emitido con cambio de clave pendiente (`ccp=true`) salvo el endpoint de cambio de clave. Las rutas públicas nuevas están en `SecurityConfig` y en el `shouldNotFilter` del gateway; si agregas otra, debes declararla en ambos sitios.

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

También se puede usar `mvn test`. Las pruebas de seguridad (`PoliticaClavesTest`, `JwtServiceTest`, `LoginAttemptServiceTest`, `PlantillasCorreoTest`) corren sin base de datos. `MsSeguridadApplicationTests` y `EnvResolucionTest` cargan el contexto completo y necesitan `.env` y PostgreSQL. Para empaquetar un módulo sin pruebas:

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

1. **Esquema y migraciones.** `schema-postgres.sql` incluye el esquema completo, `pgcrypto`, `paciente.email` y las columnas de cierre de triage. En una base existente aplica, en este orden, `infra/migraciones/2026-10-19-seguridad.sql`, `2026-10-19-actividades.sql`, `2026-10-21-claves.sql` y `2026-10-22-seed-drift.sql`. `reset_seed.py` lo hace automáticamente antes del seed. `ddl-auto=validate` no crea ni migra nada.
2. **Seed y credenciales locales.** El seed reemplaza los datos de aplicación; las contraseñas únicas de las 9 cuentas viven en `infra/seed/credenciales.local.env`/`.md`, ignorados por Git. No se versionan ni se comparten. Las cuentas tienen 2FA activo y los códigos llegan a `pruebasosclinic@gmail.com`. Las 9 cuentas tienen turno de 24 h para pruebas funcionales; `admin.test` sigue siendo la cuenta maestra.
3. **Historia clínica y teleconsulta por correo.** El alta se hace en una sola llamada desde el backend, que persiste diagnóstico y tratamiento en `registro_triage` **antes** de notificar y devuelve el estado de cada correo por separado. Los envíos siguen siendo de mejor esfuerzo dependientes de SMTP: revisa el estado devuelto y usa el botón de reintento en lugar de asumir que la historia clínica llegó porque el alta concluyó. Si el PDF o el mapa de farmacias fallan, el correo se envía igual sin ese elemento.
4. **Autorizacion por rol.** El gateway valida el JWT y cada servicio vuelve a validarlo; `/admin/**` exige `ADMINISTRADOR`, salvo `/admin/auditoria/**` (`ADMINISTRADOR`, `SEGURIDAD`, `AUDITOR`) y las rutas de aprobación de cuentas (`pendientes`, `aprobar`, `rechazar`), que exigen `SEGURIDAD`; pacientes y triage admiten roles clinicos y de recepcion/lista de espera segun el endpoint. Queda pendiente la autorizacion por propietario de cada episodio y el doble control para cambios de perfil.
5. **Secretos fuera del repositorio.** El SMTP y la API key de Groq se leen desde el archivo `.env` (ignorado por git) o variables de entorno; `application.properties` solo conserva `${MAIL_USERNAME}` y similares. El secreto JWT se exige por la variable de entorno `APP_JWT_SECRET`; no hay fallback versionado. Genera uno nuevo, guardalo en `.env` y reinicia todos los servicios. Revisa el historial de git (los secretos antiguos ya estaban publicados) y sigue la seccion Credenciales y secretos para rotarlos. No los uses en produccion.
6. **Configuración de desarrollo sin endurecer.** PostgreSQL y pgAdmin publican puertos locales con credenciales de desarrollo; CORS debe limitarse al origen real del frontend. El repositorio no contiene un despliegue productivo completo para Azure.
7. **Auditoría de mejor esfuerzo.** Las operaciones de seguridad (alta, aprobación, activación, reset, cambio de clave, 2FA, login fallido) se registran desde el backend, pero la bitácora no es inmutable y parte de los eventos del frontend se siguen enviando desde JavaScript. No registra automáticamente todos los accesos a datos clínicos.
8. **Analítica y agente IA en modo prototipo.** `ms-analitica` consulta una base MySQL sintética y `ms-ia-agente` usa la API de Groq para chat y voz; siguen sin existir módulos de Big Data, ISO 27005/STRIDE/DREAD o recomendaciones clínicas automáticas.
9. **No sustituye criterio clínico.** El personal ingresa la categoría. La aplicación no diagnostica, no detecta deterioro automáticamente y no debe ser el único mecanismo de priorización asistencial.
10. **Sesiones y dependencia de correo.** El JWT dura 2 horas y no hay revocación centralizada: un token robado sigue siendo válido hasta que expira, incluso después de cambiar la clave. La activación, la recuperación y el 2FA dependen de SMTP; si el correo falla, la cuenta queda inutilizable hasta reenviar el enlace. El *challenge* 2FA evita que el código por sí solo sea una credencial, pero el correo sigue siendo el único segundo factor.

## Problemas frecuentes

**`Port XXXX was already in use`**
Ya existe un proceso usando el puerto. Detén la ejecución anterior o identifica el proceso con `netstat -ano | findstr :XXXX` antes de cerrarlo.

**Error de validación de Hibernate o columna/tabla faltante**
El esquema de referencia ya incluye las columnas de paciente y cierre de triage. Si una base antigua falla, aplica `infra/migraciones/2026-10-22-seed-drift.sql` o ejecuta `reset-seed` con `--clean-volume`. Los scripts de inicialización no corren otra vez sobre volúmenes existentes.

**404 a través del gateway**
Verifica que el servicio aparezca en Eureka y tenga ruta explícita en `ms-gateway/src/main/resources/application.properties`. Las rutas usan `StripPrefix=1`.

**No se puede contactar al servidor**
Comprueba Docker/PostgreSQL, Eureka, gateway y servicio destino, en ese orden. Revisa puertos y URL JDBC.

**No aparece una pantalla en el menú**
Asigna al perfil una actividad cuyo enlace exista. `seguimiento.html` además necesita `idRegistro` en la URL.

**No puedo iniciar sesión fuera de mi turno**
Comprueba que la cuenta esté activa y que tenga un turno con `id_estado = 1`. Las cuentas del seed tienen turno 24 h; los perfiles `administrador` y `seguridad` además están exentos de la comprobación horaria en el código.

**No llega el correo o código 2FA**
Verifica SMTP y acceso de red. Usa secretos locales no versionados; no publiques contraseñas SMTP ni códigos. El código 2FA vence en 5 minutos y el enlace de activación en 72 horas; si expiraron, pide un reenvío. En el seed, todas las cuentas reciben en `pruebasosclinic+...@gmail.com`, que llega a la bandeja `pruebasosclinic@gmail.com`; revisa esa bandeja y spam. Si una cuenta tiene un correo inválido, corrige el correo en la base o usa `cuenta.html` desde una sesión válida.

**Mi cuenta nueva no permite iniciar sesión**
La cuenta nace `PENDIENTE` y el administrador no puede aprobarla. Un usuario con perfil `seguridad` debe aprobarla en `aprobaciones.html`; después llega el enlace de activación al correo. Hasta que el usuario defina su clave, no hay forma de iniciar sesión. Si el correo no llega, el administrador puede reenviar la activación o, solo antes de la primera activación, generar una clave temporal.

**El enlace de activación o recuperación dice que no es válido**
Los enlaces son de un solo uso y vencen (30 minutos para recuperación, 72 horas para activación). Solicita uno nuevo. Si cambiaste el correo del usuario antes de la activación, el enlace anterior se invalida.

**No se puede eliminar una actividad**
La actividad está asignada a uno o más perfiles (tabla `perfil_actividad`). La API responde 409 e indica los perfiles involucrados; quita primero esas asignaciones en `roles.html` y vuelve a intentar.

**Cambiaste SQL pero la base conserva el esquema anterior**
El volumen conserva su estado. Aplica una migración explícita o, si los datos son descartables, elimina el volumen conscientemente. `docker compose down -v` es destructivo.

## Credenciales y secretos

**Regla de oro: nada de secretos en git.** Todo lo sensible se lee de variables de entorno; el archivo `.env` (en la raíz, ignorado por git) es solo el almacén local de desarrollo. El paquete de preparación entrega el `.env` completo; no hay una plantilla versionada.

### Cómo configurarlo

Copia el `.env` del paquete de preparación a la raíz del clon:

```powershell
$prep = "C:\ruta\a\SOS-Clinic-Preparacion"
Copy-Item "$prep\.env" .env
```

El `.env` ya incluye `APP_JWT_SECRET`, `DB_PASSWORD`/`POSTGRES_PASSWORD`/`PGADMIN_DEFAULT_PASSWORD`, `MAIL_USERNAME`, `MAIL_PASSWORD` (contrasena de aplicacion de Gmail, no la clave normal), `ANALITICA_DB_*` y `GROQ_API_KEY`. `FRONTEND_URL` debe apuntar al servidor estatico del frontend (`http://127.0.0.1:5500` en local) para que los enlaces de activacion y recuperacion funcionen. `04. levantar-sistema.bat` carga `.env` automaticamente antes de abrir las consolas de cada servicio; si lo corres a mano, exporta las variables antes de arrancar.

Las contraseñas del seed **no** van en `.env`: se generan una vez en `infra/seed/credenciales.local.env` y `credenciales.local.md`, que están en `.gitignore`. El `.md` es la lista que debes usar para iniciar sesión. Las 9 cuentas tienen 2FA activo y los códigos llegan a `pruebasosclinic@gmail.com`.

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

## Despliegue en Azure

La guia operativa esta en [`infra/azure/README.md`](infra/azure/README.md) y el registro completo de la implementacion (recursos, decisiones, validaciones, problemas corregidos y pendientes) en [`infra/azure/README-IMPLEMENTACION.md`](infra/azure/README-IMPLEMENTACION.md). El flujo local de Windows (`00` a `05`, `infra/docker-compose.yml` y los `application.properties`) no cambia.

Resumen:

```powershell
az login
.\infra\azure\deploy.ps1
```

El script crea un grupo de recursos con una VM Ubuntu 24.04, PostgreSQL/MySQL en contenedores, las 7 imagenes en ACR, Caddy con HTTPS (`<label>.<region>.cloudapp.azure.com`), backups a Blob Storage y auto-shutdown. Antes de ejecutarlo:

1. **Decide qué hacer con los secretos SMTP/Groq.** El despliegue reutiliza `MAIL_PASSWORD` y `GROQ_API_KEY` del `.env` local; en esta implementación se decidió no rotarlas y aceptar el riesgo. Para reducirlo, rótalas antes de desplegar o actualízalas después con `update.ps1 -SyncLocalSecrets`.
2. Verifica que tienes acceso a `pruebasosclinic@gmail.com` para los codigos 2FA.
3. Haz commit de los cambios: el script sube a la VM el ultimo commit, no el arbol de trabajo sucio.
4. Ten Docker Desktop y Azure CLI instalados, y `az login` activo.

Operacion diaria:

```powershell
.\infra\azure\start.ps1
.\infra\azure\update.ps1
.\infra\azure\stop.ps1
.\infra\azure\smoke-test.ps1 -Domain <label>.<region>.cloudapp.azure.com
```

`stop.ps1` desaloca la VM para no consumir credito de computo; los volumenes, el disco, la IP y los backups se conservan. No uses esta infraestructura con datos clinicos reales: sigue siendo un prototipo academico de una sola VM, sin alta disponibilidad ni WAF.

