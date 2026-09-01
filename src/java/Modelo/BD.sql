CREATE DATABASE SOSClinic;
USE SOSClinic;

CREATE TABLE perfil (
    id_perfil INT PRIMARY KEY AUTO_INCREMENT,
    perfil VARCHAR(30) NOT NULL UNIQUE,
    id_estado INT NOT NULL DEFAULT 1 CHECK (id_estado IN (0, 1))
);

CREATE TABLE personal (
    id_personal INT PRIMARY KEY AUTO_INCREMENT,
    nombre VARCHAR(30) NOT NULL,
    apellido VARCHAR(30) NOT NULL,
    email VARCHAR(60) NOT NULL UNIQUE,
    cedula VARCHAR(12) NOT NULL UNIQUE,
    telefono VARCHAR(20),
    usuario VARCHAR(20) NOT NULL UNIQUE,
    clave VARCHAR(255) NOT NULL,
    id_perfil INT NOT NULL,
    id_estado INT NOT NULL DEFAULT 1 CHECK (id_estado IN (0, 1)),
    FOREIGN KEY (id_perfil) REFERENCES perfil(id_perfil)
);

CREATE TABLE actividades (
    id_actividad INT PRIMARY KEY AUTO_INCREMENT,
    nombre_actividad VARCHAR(45) NOT NULL,
    enlace VARCHAR(100),
    id_estado INT NOT NULL DEFAULT 1 CHECK (id_estado IN (0, 1))
);

CREATE TABLE perfil_actividad (
    id_perfil_actividad INT PRIMARY KEY AUTO_INCREMENT,
    id_perfil INT NOT NULL,
    id_actividad INT NOT NULL,
    id_estado INT NOT NULL DEFAULT 1 CHECK (id_estado IN (0, 1)),
    UNIQUE (id_perfil, id_actividad),
    FOREIGN KEY (id_perfil) REFERENCES perfil(id_perfil),
    FOREIGN KEY (id_actividad) REFERENCES actividades(id_actividad)
);

CREATE TABLE paciente (
    id_paciente INT PRIMARY KEY AUTO_INCREMENT,
    nombre VARCHAR(50) NOT NULL,
    apellido VARCHAR(50) NOT NULL,
    cedula VARCHAR(12) NOT NULL UNIQUE,
    fecha_nacimiento DATE NOT NULL,
    id_estado INT NOT NULL DEFAULT 1 CHECK (id_estado IN (0, 1))
);

CREATE TABLE estado_triage (
    id_estado_triage INT PRIMARY KEY AUTO_INCREMENT,
    nombre_estado VARCHAR(30) NOT NULL UNIQUE,
    descripcion VARCHAR(100)
);

CREATE TABLE categoria_triage (
    id_categoria_triage INT PRIMARY KEY AUTO_INCREMENT,
    nombre_categoria VARCHAR(30) NOT NULL UNIQUE,
    descripcion VARCHAR(100),
    prioridad INT NOT NULL UNIQUE,
    id_estado INT NOT NULL DEFAULT 1 CHECK (id_estado IN (0, 1))
);

CREATE TABLE registro_triage (
    id_registro INT PRIMARY KEY AUTO_INCREMENT,
    id_paciente INT NOT NULL,
    id_estado_triage INT NOT NULL,
    id_categoria_triage INT NOT NULL,
    tiempo_espera_minutos INT,
    ubicacion VARCHAR(50),
    fecha_hora_llegada DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_estado INT NOT NULL DEFAULT 1 CHECK (id_estado IN (0, 1)),
    FOREIGN KEY (id_paciente) REFERENCES paciente(id_paciente),
    FOREIGN KEY (id_estado_triage) REFERENCES estado_triage(id_estado_triage),
    FOREIGN KEY (id_categoria_triage) REFERENCES categoria_triage(id_categoria_triage)
);

CREATE TABLE estado_paciente (
    id_registro_estado INT PRIMARY KEY AUTO_INCREMENT,
    id_registro INT NOT NULL,
    frecuencia_cardiaca INT NOT NULL,
    presion_sistolica INT NOT NULL,
    presion_diastolica INT NOT NULL,
    temperatura DECIMAL(4,1) NOT NULL,
    saturacion INT NOT NULL,
    frecuencia_respiratoria INT NOT NULL,
    glucemia DECIMAL(5,2),
    descripcion_sintoma VARCHAR(500),
    fecha_hora_toma DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_personal INT NOT NULL,
    id_estado INT NOT NULL DEFAULT 1 CHECK (id_estado IN (0, 1)),
    FOREIGN KEY (id_registro) REFERENCES registro_triage(id_registro),
    FOREIGN KEY (id_personal) REFERENCES personal(id_personal)
);

CREATE TABLE registro_auditoria (
    id_log INT PRIMARY KEY AUTO_INCREMENT,
    id_personal INT NOT NULL,
    id_paciente INT,
    accion VARCHAR(20) NOT NULL,
    detalle VARCHAR(100),
    fecha_hora DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_estado INT NOT NULL DEFAULT 1 CHECK (id_estado IN (0, 1)),
    FOREIGN KEY (id_personal) REFERENCES personal(id_personal),
    FOREIGN KEY (id_paciente) REFERENCES paciente(id_paciente)
);