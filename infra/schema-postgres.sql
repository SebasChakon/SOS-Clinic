-- =====================================================================
-- SOS-Clinic — Esquema PostgreSQL
-- Migrado desde el BD.sql original (MySQL) de sos-clinic (rama Ant/TomEE)
-- Corresponde al diccionario de datos del anteproyecto SOS (cap. 2.1.2)
-- =====================================================================

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
    id_estado     INT NOT NULL DEFAULT 1 CHECK (id_estado IN (0, 1))
);

CREATE TABLE actividades (
    id_actividad      INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre_actividad  VARCHAR(45) NOT NULL,
    enlace            VARCHAR(100),
    id_estado         INT NOT NULL DEFAULT 1 CHECK (id_estado IN (0, 1))
);

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
    id_estado              INT NOT NULL DEFAULT 1 CHECK (id_estado IN (0, 1))
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

-- Datos base mínimos para poder probar los microservicios desde el día 1
INSERT INTO perfil (perfil) VALUES ('triage'), ('medico'), ('coordinacion'), ('seguridad');

INSERT INTO categoria_triage (nombre_categoria, descripcion, prioridad) VALUES
  ('Resucitacion', 'Riesgo vital inminente', 1),
  ('Emergencia', 'Riesgo vital potencial', 2),
  ('Urgencia', 'Requiere atencion pronta', 3),
  ('Urgencia menor', 'Puede esperar de forma segura', 4),
  ('Sin urgencia', 'No compromete la condicion del paciente', 5);

INSERT INTO estado_triage (nombre_estado, descripcion) VALUES
  ('En espera', 'Paciente registrado, pendiente de atencion'),
  ('En atencion', 'Paciente siendo atendido'),
  ('Atendido', 'Episodio de triage cerrado');
