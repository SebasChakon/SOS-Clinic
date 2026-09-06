# SOS · Sistema Inteligente de Triage Hospitalario

Proyecto integrador — Aplicación Big Data, Ingeniería Web y Agente de IA (Corte II).
Arquitectura de microservicios en Java + Spring Boot, pensada para desplegarse en Azure.

## Índice

- [Arquitectura](#arquitectura)
- [Requisitos previos](#requisitos-previos)
- [Levantar el proyecto desde cero](#levantar-el-proyecto-desde-cero)
- [Microservicios existentes](#microservicios-existentes)
- [Endpoints disponibles](#endpoints-disponibles)
- [Pendiente por construir](#pendiente-por-construir)
- [Cómo agregar un microservicio nuevo](#cómo-agregar-un-microservicio-nuevo)
- [Problemas comunes](#problemas-comunes)

---

## Arquitectura

```
                        ┌─────────────────┐
                        │   ms-gateway     │  puerto 8080
                        │ (Spring Cloud    │  entrada unica al sistema
                        │  Gateway MVC)    │
                        └────────┬─────────┘
          ┌───────────┬─────────┼──────────┬────────────┬──────────────┐
          │            │         │          │            │              │
   ms-seguridad   ms-pacientes  ms-triage  ms-analitica  ms-riesgos   ms-ia-agente
    puerto 8081    puerto 8082  puerto 8083  (pendiente)  (pendiente)  (pendiente)
   (auth JWT,      (pacientes,  (registro y                 
    roles)         signos       priorizacion)
                   vitales)
          │            │         │
          └────────────┴─────────┴──────────────────────────────────────┘
                                    │
                          eureka-server (puerto 8761)
                          service discovery de todos los microservicios
                                    │
                              PostgreSQL (Docker, puerto 5432)
```

Cada microservicio es un proyecto Maven independiente. Todos corren sobre **Java 17** y **Spring Boot 4.0.8**, y se registran en Eureka para que el Gateway pueda ubicarlos por nombre (`lb://ms-nombre`) en vez de por IP/puerto fijo.

El repo también tiene una carpeta `legacy/` con el primer prototipo (Java EE / Ant / TomEE) que se descartó en favor de microservicios — se dejó como referencia del proceso, no se usa en el proyecto actual.

---

## Requisitos previos

- **JDK 17**
- **NetBeans 20+** (con el plugin "NetBeans Spring Boot" de Alex Falappa instalado vía Tools → Plugins, para crear proyectos Spring Initializr desde el IDE)
- **Docker Desktop**
- **Git** / Git Bash (recomendado sobre CMD en Windows para comandos con heredoc, `curl`, etc.)
- **Postman** (o similar) para probar los endpoints

---

## Levantar el proyecto desde cero

### 1. Clonar el repo

```bash
git clone <url-del-repo-en-gitlab>
cd sos-clinic
```

### 2. Levantar la base de datos (Docker)

```bash
cd infra
docker compose up -d
```

Esto levanta:
- **PostgreSQL 16** en el puerto `5432` (usuario `sos_admin`, clave `sos_pass_dev`, base de datos `sosclinic`), con el schema (`schema-postgres.sql`) cargado automáticamente la primera vez
- **pgAdmin** en `http://localhost:5050` (login: `admin@sosclinic.com` / `admin123`) — opcional, solo para inspeccionar visualmente las tablas

Verifica que las 10 tablas se crearon:
```bash
docker exec -it sos-postgres psql -U sos_admin -d sosclinic -c "\dt"
```

> **Nota:** si vuelves a levantar el proyecto en otra máquina y el volumen de Postgres ya existe pero está vacío/dañado, borra el volumen (`docker compose down -v`) y vuelve a correr `docker compose up -d` para que el schema se cargue de nuevo.

### 3. Correr los microservicios (en este orden)

El orden importa: `eureka-server` tiene que estar arriba antes que cualquier otro, porque los demás se registran contra él al arrancar.

1. **`eureka-server`** → abrir en NetBeans → Run. Verificar en `http://localhost:8761`
2. **`ms-gateway`** → Run. Verificar que aparezca `MS-GATEWAY` en el dashboard de Eureka
3. **`ms-seguridad`**, **`ms-pacientes`**, **`ms-triage`** → Run cada uno (no importa el orden entre ellos, solo que sea después del gateway)

Cada proyecto necesita, la primera vez que lo abras en NetBeans:
- **Properties → Run → Main Class**: escribir a mano la clase completa (ej. `com.sos.msseguridad.MsSeguridadApplication`) si el botón "Browse" no la lista
- **Properties → Actions → Run project → Execute Goals**: `spring-boot:run` (si no se configura esto, NetBeans intenta correr con `exec-maven-plugin` y falla con `Could not find or load main class ${start-class}`)

### 4. Insertar usuarios de prueba

La tabla `personal` empieza vacía. Para poder hacer login, hay que insertar al menos un usuario con la clave ya hasheada en BCrypt (nunca en texto plano). Ejemplo ya usado en pruebas:

```sql
-- usuario: ana.triage / clave en texto plano: Triage123*
INSERT INTO personal (nombre, apellido, email, cedula, telefono, usuario, clave, id_perfil)
VALUES ('Ana', 'Torres', 'ana.torres@sos.local', '1000111222', '3001234567',
        'ana.triage', '$2b$10$kxKo2nh3zuvoKsQS4JboeuSjy7uNABzBAr4FPhAe5Msmvj2AlXt8O', 1);
```

Para generar un hash nuevo con otra clave, cualquier generador de BCrypt online sirve, o un script corto en Python con la librería `bcrypt`.

### 5. Probar que todo funciona

Abre `frontend/index.html` directamente en el navegador (doble clic) y entra con `ana.triage` / `Triage123*`. Si te muestra el token JWT, todo el flujo (navegador → gateway → ms-seguridad → Postgres) está funcionando.

---

## Microservicios existentes

### `eureka-server` (puerto 8761)
Service discovery. No expone lógica de negocio, solo el dashboard en `/`.

### `ms-gateway` (puerto 8080)
Punto único de entrada. Usa **Spring Cloud Gateway Server MVC** (no la variante reactiva/WebFlux — importante porque cambia cómo se configuran las rutas). No tiene descubrimiento automático de rutas: cada microservicio nuevo necesita una ruta agregada a mano en `application.properties`, siguiendo este patrón:

```properties
spring.cloud.gateway.server.webmvc.routes[N].id=ms-nombre
spring.cloud.gateway.server.webmvc.routes[N].uri=lb://ms-nombre
spring.cloud.gateway.server.webmvc.routes[N].predicates[0]=Path=/ms-nombre/**
spring.cloud.gateway.server.webmvc.routes[N].filters[0]=StripPrefix=1
```

También tiene configurado CORS abierto (`CorsConfig.java`) para que el frontend pueda llamarlo desde el navegador en desarrollo. **Esto hay que restringirlo a un dominio específico antes de pasar a producción/Azure.**

### `ms-seguridad` (puerto 8081)
Autenticación y roles. Tablas: `personal`, `perfil`, `actividades`, `perfil_actividad`.

- Login con JWT (librería JJWT), contraseñas hasheadas con BCrypt
- Filtro `JwtAuthFilter` que valida el token en cada request y lo convierte en un `ROLE_<perfil>` de Spring Security
- Sesión completamente **stateless** (sin sesiones de servidor), como corresponde en microservicios

### `ms-pacientes` (puerto 8082)
Gestión de pacientes y signos vitales. Tablas: `paciente`, `estado_paciente`.

Nota de diseño: `estado_paciente` guarda `id_registro` e `id_personal` como enteros simples (no como relaciones JPA `@ManyToOne`), porque esas tablas pertenecen a otros microservicios (`ms-triage` y `ms-seguridad` respectivamente). En microservicios, cada base de datos lógica es dueña de sus propias tablas — los demás solo referencian el ID.

### `ms-triage` (puerto 8083)
El corazón del sistema: registro de llegada a triage y cálculo de la lista de priorización en tiempo real. Tablas: `registro_triage`, `categoria_triage`, `estado_triage`.

El endpoint `GET /triage/prioridad` devuelve la lista ordenada por prioridad de categoría (1 = más urgente) y luego por orden de llegada, con el tiempo de espera calculado al vuelo en cada consulta (no se guarda estático). Este endpoint es el que va a alimentar el dashboard y, más adelante, el agente de IA.

---

## Endpoints disponibles

Todos se acceden a través del gateway: `http://localhost:8080/<prefijo-servicio>/...`

| Método | Endpoint | Descripción |
|---|---|---|
| POST | `/ms-seguridad/auth/login` | Login, devuelve JWT |
| POST | `/ms-pacientes/pacientes` | Crear paciente |
| GET | `/ms-pacientes/pacientes` | Listar pacientes |
| GET | `/ms-pacientes/pacientes/{id}` | Obtener paciente por ID |
| GET | `/ms-pacientes/pacientes/cedula/{cedula}` | Obtener paciente por cédula |
| POST | `/ms-pacientes/pacientes/signos-vitales` | Registrar signos vitales |
| GET | `/ms-pacientes/pacientes/signos-vitales/registro/{idRegistro}` | Signos vitales de un registro de triage |
| POST | `/ms-triage/triage` | Registrar llegada a triage |
| GET | `/ms-triage/triage/prioridad` | Lista de priorización en tiempo real |
| PUT | `/ms-triage/triage/{id}/en-atencion` | Marcar paciente en atención |
| PUT | `/ms-triage/triage/{id}/atendido` | Cerrar el episodio de triage |

Ejemplo de flujo de prueba completo en Postman:
1. Login → copiar el token (para cuando protejamos estos endpoints con `@PreAuthorize` por rol)
2. Crear paciente → anotar `idPaciente`
3. Crear registro de triage con ese `idPaciente`
4. Consultar `/ms-triage/triage/prioridad` → confirmar que aparece con el tiempo de espera calculado

---

## Pendiente por construir

Cada uno de estos sigue exactamente el mismo patrón que `ms-pacientes` y `ms-triage`: generar en Spring Initializr (Spring Boot 4.0.8, Java 17), configurar `application.properties`, entidades JPA, repositorios, servicio, controller, y agregar la ruta en `ms-gateway`.

### `ms-analitica` (Big Data — puerto sugerido 8084)
Requisito de la guía del corte II. Debe:
- Ingerir un dataset histórico simulado (mínimo ~1000 registros de triage: signos vitales, categoría asignada, tiempo de espera, hora de llegada)
- Procesar ese histórico (Java Streams, o Spark en modo local con su API de Java si se quiere usar tecnología Big Data real, como sugiere la guía)
- Exponer endpoints con indicadores: tiempos de espera promedio por categoría, distribución de pacientes por hora/día, tendencias — datos que después consume el dashboard y el agente de IA

### `ms-riesgos` (ISO 27005 / STRIDE / DREAD — puerto sugerido 8085)
Corresponde al análisis de riesgos del anteproyecto SOS. Tablas sugeridas: activos de información, amenazas, vulnerabilidades, controles, con cálculo de nivel de riesgo.

### `ms-auditoria` (puerto sugerido 8086)
Tabla `registro_auditoria` (ya existe en el schema). Debe registrar acciones sensibles (quién accedió a qué paciente, cambios de estado, intentos de login fallidos) — corresponde al control de trazabilidad que exige el anteproyecto.

### `ms-ia-agente` (puerto sugerido 8087)
El agente de IA que exige la guía. Debe consumir los endpoints de `ms-triage` y `ms-analitica` para responder preguntas en lenguaje natural, interpretar indicadores y generar alertas/recomendaciones — no debe ser un chatbot aislado, tiene que usar datos reales del sistema.

### Dashboard
Ampliar `frontend/index.html` (que hoy es solo una pantalla de prueba de login) con: lista de priorización en vivo, KPIs de `ms-analitica`, y un panel de chat con `ms-ia-agente`.

---

## Cómo agregar un microservicio nuevo

1. **start.spring.io** (o el plugin de NetBeans): Group `com.sos`, Artifact `ms-nombre`, Package `com.sos.msnombre` (sin guiones), Spring Boot **4.0.8**, Java **17**
2. Descomprimir en `sos-clinic/ms-nombre/`
3. Abrir en NetBeans → **Properties → Run → Main Class**: escribir a mano `com.sos.msnombre.MsNombreApplication`
4. **Properties → Actions → Run project → Execute Goals**: `spring-boot:run`
5. `application.properties` con puerto propio, datos de conexión a Postgres, y `eureka.client.service-url.defaultZone=http://localhost:8761/eureka`
6. Entidades JPA → Repositorios → Servicio → Controller (mismo patrón de `ms-pacientes`/`ms-triage`)
7. Agregar la ruta correspondiente en `ms-gateway/application.properties`
8. `Clean and Build`, `Run`, confirmar que aparece en `http://localhost:8761`

---

## Problemas comunes

**`Could not find or load main class ${start-class}`**
Falta configurar `Properties → Actions → Run project → Execute Goals` a `spring-boot:run`. NetBeans por defecto intenta usar `exec-maven-plugin`, que no resuelve la clase principal automáticamente en este setup.

**`Web server failed to start. Port XXXX was already in use`**
Hay una instancia anterior del mismo microservicio corriendo. Encuentra el proceso con `netstat -ano | findstr :XXXX` y mátalo con `taskkill /PID <numero> /F`, o simplemente dale Stop a la pestaña de consola vieja en NetBeans.

**404 al pasar por el gateway**
Spring Cloud Gateway Server **MVC** (la variante que usamos, no la reactiva) no tiene descubrimiento automático de rutas. Cada servicio nuevo necesita su ruta explícita en `ms-gateway/application.properties` (ver sección de arriba).

**CORS bloqueado desde el navegador**
Confirmar que `CorsConfig.java` existe en `ms-gateway`, que se hizo `Clean and Build`, y que el proceso de `ms-gateway` se reinició (Stop + Run) después del cambio — un simple Run sin reiniciar no aplica cambios de código ya compilado antes.

**pgAdmin no arranca / bucle de reinicio con error de email**
El dominio del `PGADMIN_DEFAULT_EMAIL` no puede ser uno reservado (`.local`, `.test`, `.example`). Usar un dominio "normal" como `.com` aunque no exista de verdad — pgAdmin no envía correos, solo valida el formato.

**Login funciona en Postman pero el hash de la clave no hace match**
La clave en la base de datos tiene que estar hasheada con BCrypt, nunca en texto plano — `PasswordEncoder.matches()` siempre da `false` contra texto plano.
