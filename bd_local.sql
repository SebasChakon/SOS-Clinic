DROP DATABASE IF EXISTS urgencias_hospital;
CREATE DATABASE IF NOT EXISTS urgencias_hospital;
USE urgencias_hospital;

-- 1. Dimensión: Contexto de Zona
-- Define las características fijas de cada localidad.
CREATE TABLE contexto_zona (
    localidad VARCHAR(100) PRIMARY KEY,
    poblacion INT NOT NULL,
    porcentaje_mayores_60 DECIMAL(5,2),
    densidad_poblacional DECIMAL(10,2)
);

-- 2. Dimensión: Contexto Diario
-- Almacena las condiciones específicas de un día en particular.
CREATE TABLE contexto_diario (
    fecha DATE PRIMARY KEY,
    es_festivo BOOLEAN DEFAULT FALSE,
    lluvia_mm DECIMAL(6,2),
    temperatura DECIMAL(5,2),
    pm25 DECIMAL(6,2),
    evento_masivo BOOLEAN DEFAULT FALSE
);

-- 3. Sub-dimensión: Capacidad del Turno
-- Depende del día. La llave primaria es compuesta: la fecha + el nombre del turno.
CREATE TABLE capacidad_turno (
    fecha DATE NOT NULL,
    turno VARCHAR(20) NOT NULL, -- Ej: 'Mañana', 'Tarde', 'Noche'
    personal_disponible INT NOT NULL,
    puestos_triage INT NOT NULL,
    camas_disponibles INT NOT NULL,
    PRIMARY KEY (fecha, turno),
    FOREIGN KEY (fecha) REFERENCES contexto_diario(fecha) ON DELETE CASCADE
);

-- 4. Tabla de Hechos: Llegadas
-- Registra cada evento individual. Se añaden 'fecha' y 'turno' para 
-- vincular el evento con las capacidades y el clima de ese momento exacto.
CREATE TABLE llegadas (
    id_llegada INT AUTO_INCREMENT PRIMARY KEY,
    fecha_hora DATETIME NOT NULL,
    fecha DATE NOT NULL,          -- Extraído de fecha_hora para la relación
    turno VARCHAR(20) NOT NULL,   -- Calculado según la hora para la relación
    localidad VARCHAR(100) NOT NULL,
    prioridad VARCHAR(20),        -- Ej: 'Triage 1', 'Triage 2', etc.
    estado VARCHAR(50),           -- Ej: 'Atendido', 'Abandono', 'En Espera'
    tiempo_espera_min INT,
    tiempo_atencion_min INT,
    
    -- Relaciones lógicas (Llaves foráneas)
    FOREIGN KEY (localidad) REFERENCES contexto_zona(localidad) ON DELETE CASCADE,
    FOREIGN KEY (fecha) REFERENCES contexto_diario(fecha) ON DELETE CASCADE,
    FOREIGN KEY (fecha, turno) REFERENCES capacidad_turno(fecha, turno) ON DELETE CASCADE
);