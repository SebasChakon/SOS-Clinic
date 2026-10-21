-- =====================================================================
-- SOS-Clinic — Esquema PostgreSQL
-- Migrado desde el BD.sql original (MySQL) de sos-clinic (rama Ant/TomEE)
-- Corresponde al diccionario de datos del anteproyecto SOS (cap. 2.1.2)
-- =====================================================================

-- pgcrypto se usa para hashear las claves del seed con BCrypt.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE perfil (
    id_perfil     INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    perfil        VARCHAR(30) NOT NULL UNIQUE,
    id_estado     INT NOT NULL DEFAULT 1 CHECK (id_estado IN (0, 1))
);

CREATE TABLE personal (
    id_personal   INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre        VARCHAR(30) NOT NULL,
    apellido      VARCHAR(30) NOT NULL,
    email         VARCHAR(60) NOT NULL UNIQUE,
    cedula        VARCHAR(12) NOT NULL UNIQUE,
    telefono      VARCHAR(20),
    usuario       VARCHAR(20) NOT NULL UNIQUE,
    clave         VARCHAR(255) NOT NULL,           -- hash BCrypt, nunca texto plano
    id_perfil     INT NOT NULL REFERENCES perfil(id_perfil),
    id_estado     INT NOT NULL DEFAULT 1 CHECK (id_estado IN (0, 1)),
    requiere_2fa  BOOLEAN NOT NULL DEFAULT FALSE,
    debe_cambiar_clave BOOLEAN NOT NULL DEFAULT FALSE,
    clave_expira       TIMESTAMP NULL,
    fecha_cambio_clave TIMESTAMP NULL,
    estado_aprobacion  VARCHAR(20) NOT NULL DEFAULT 'APROBADA'
                       CHECK (estado_aprobacion IN ('PENDIENTE', 'APROBADA', 'RECHAZADA')),
    aprobado_por       INT NULL REFERENCES personal(id_personal) ON DELETE SET NULL,
    fecha_aprobacion   TIMESTAMP NULL,
    email_pendiente    VARCHAR(60) NULL
);

CREATE TABLE turno (
    id_turno     INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_personal  INT NOT NULL REFERENCES personal(id_personal) ON DELETE CASCADE,
    hora_inicio  TIME NOT NULL,
    hora_fin     TIME NOT NULL,
    id_estado    INT NOT NULL DEFAULT 1 CHECK (id_estado IN (0, 1))
);

CREATE TABLE codigo_verificacion (
    id_codigo      INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_personal    INT NOT NULL REFERENCES personal(id_personal) ON DELETE CASCADE,
    codigo         VARCHAR(255) NOT NULL,          -- hash BCrypt del codigo
    challenge_hash VARCHAR(64),                    -- SHA-256 del challenge de pre-autenticacion
    proposito      VARCHAR(20) NOT NULL DEFAULT 'LOGIN',
    fecha_expira   TIMESTAMP NOT NULL,
    usado          BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX ux_codigo_challenge
    ON codigo_verificacion (challenge_hash)
    WHERE challenge_hash IS NOT NULL;

CREATE TABLE token_recuperacion (
    id_token       INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_personal    INT NOT NULL REFERENCES personal(id_personal) ON DELETE CASCADE,
    tipo           VARCHAR(20) NOT NULL
                   CHECK (tipo IN ('RECUPERACION', 'ACTIVACION', 'CAMBIO_EMAIL')),
    token_hash     VARCHAR(64) NOT NULL,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT now(),
    fecha_expira   TIMESTAMP NOT NULL,
    usado          BOOLEAN NOT NULL DEFAULT FALSE,
    ip_solicitud   VARCHAR(45),
    user_agent     VARCHAR(200)
);

CREATE UNIQUE INDEX ux_token_recuperacion_hash ON token_recuperacion (token_hash);
CREATE INDEX ix_token_recuperacion_personal
    ON token_recuperacion (id_personal, tipo) WHERE usado = FALSE;

CREATE TABLE actividades (
    id_actividad      INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre_actividad  VARCHAR(45) NOT NULL,
    enlace            VARCHAR(100),
    id_estado         INT NOT NULL DEFAULT 1 CHECK (id_estado IN (0, 1))
);

CREATE UNIQUE INDEX ux_actividades_nombre_lower
    ON actividades (LOWER(btrim(nombre_actividad)));
CREATE UNIQUE INDEX ux_actividades_enlace_lower
    ON actividades (LOWER(btrim(enlace)))
    WHERE enlace IS NOT NULL AND btrim(enlace) <> '';

CREATE TABLE perfil_actividad (
    id_perfil_actividad  INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_perfil            INT NOT NULL REFERENCES perfil(id_perfil),
    id_actividad         INT NOT NULL REFERENCES actividades(id_actividad),
    id_estado            INT NOT NULL DEFAULT 1 CHECK (id_estado IN (0, 1)),
    UNIQUE (id_perfil, id_actividad)
);

CREATE TABLE paciente (
    id_paciente        INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre             VARCHAR(50) NOT NULL,
    apellido           VARCHAR(50) NOT NULL,
    cedula             VARCHAR(12) NOT NULL UNIQUE,
    fecha_nacimiento   DATE NOT NULL,
    email              VARCHAR(80),
    id_estado          INT NOT NULL DEFAULT 1 CHECK (id_estado IN (0, 1))
);

CREATE TABLE estado_triage (
    id_estado_triage  INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre_estado     VARCHAR(30) NOT NULL UNIQUE,
    descripcion       VARCHAR(100)
);

CREATE TABLE categoria_triage (
    id_categoria_triage  INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre_categoria     VARCHAR(30) NOT NULL UNIQUE,
    descripcion          VARCHAR(100),
    prioridad            INT NOT NULL UNIQUE,
    id_estado            INT NOT NULL DEFAULT 1 CHECK (id_estado IN (0, 1))
);

CREATE TABLE registro_triage (
    id_registro            INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_paciente            INT NOT NULL REFERENCES paciente(id_paciente),
    id_estado_triage       INT NOT NULL REFERENCES estado_triage(id_estado_triage),
    id_categoria_triage    INT NOT NULL REFERENCES categoria_triage(id_categoria_triage),
    tiempo_espera_minutos  INT,
    ubicacion              VARCHAR(50),
    fecha_hora_llegada     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_estado              INT NOT NULL DEFAULT 1 CHECK (id_estado IN (0, 1)),
    diagnostico_final      VARCHAR(1000),
    tratamiento            VARCHAR(1000)
);

CREATE TABLE estado_paciente (
    id_registro_estado       INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_registro              INT NOT NULL REFERENCES registro_triage(id_registro),
    frecuencia_cardiaca      INT NOT NULL,
    presion_sistolica        INT NOT NULL,
    presion_diastolica       INT NOT NULL,
    temperatura              DECIMAL(4,1) NOT NULL,
    saturacion               INT NOT NULL,
    frecuencia_respiratoria  INT NOT NULL,
    glucemia                 DECIMAL(5,2),
    descripcion_sintoma      VARCHAR(500),
    fecha_hora_toma          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_personal              INT NOT NULL REFERENCES personal(id_personal),
    id_estado                INT NOT NULL DEFAULT 1 CHECK (id_estado IN (0, 1))
);

CREATE TABLE registro_auditoria (
    id_log       INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_personal  INT NOT NULL REFERENCES personal(id_personal),
    id_paciente  INT REFERENCES paciente(id_paciente),
    accion       VARCHAR(20) NOT NULL,
    detalle      VARCHAR(100),
    fecha_hora   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_estado    INT NOT NULL DEFAULT 1 CHECK (id_estado IN (0, 1))
);

-- Los datos de aplicacion (perfiles, actividades, cuentas, pacientes y
-- episodios) se cargan con infra/seed/seed.sql. El esquema solo crea tablas
-- e indices para que una base nueva quede siempre alineada con JPA.
