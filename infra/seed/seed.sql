-- Seed canonico de SOS Clinic.
--
-- Las contrasenas se reciben por variables de entorno y se hashean aqui con
-- pgcrypto. No hay ninguna clave en este archivo.
--
-- Uso: el script infra/seed/reset-seed.ps1/.sh exporta las variables
-- SEED_PASSWORD_* y ejecuta este archivo con psql.

\getenv p_admin SEED_PASSWORD_ADMIN
\getenv p_seguridad SEED_PASSWORD_SEGURIDAD
\getenv p_coordinacion SEED_PASSWORD_COORDINACION
\getenv p_medico_dia SEED_PASSWORD_MEDICO_DIA
\getenv p_medico_noche SEED_PASSWORD_MEDICO_NOCHE
\getenv p_recepcion SEED_PASSWORD_RECEPCION
\getenv p_triage SEED_PASSWORD_TRIAGE
\getenv p_listas SEED_PASSWORD_LISTAS
\getenv p_auditoria SEED_PASSWORD_AUDITORIA

\if :{?p_admin}
\else
    \echo 'Falta SEED_PASSWORD_ADMIN'
    \quit 1
\endif
\if :{?p_seguridad}
\else
    \echo 'Falta SEED_PASSWORD_SEGURIDAD'
    \quit 1
\endif
\if :{?p_coordinacion}
\else
    \echo 'Falta SEED_PASSWORD_COORDINACION'
    \quit 1
\endif
\if :{?p_medico_dia}
\else
    \echo 'Falta SEED_PASSWORD_MEDICO_DIA'
    \quit 1
\endif
\if :{?p_medico_noche}
\else
    \echo 'Falta SEED_PASSWORD_MEDICO_NOCHE'
    \quit 1
\endif
\if :{?p_recepcion}
\else
    \echo 'Falta SEED_PASSWORD_RECEPCION'
    \quit 1
\endif
\if :{?p_triage}
\else
    \echo 'Falta SEED_PASSWORD_TRIAGE'
    \quit 1
\endif
\if :{?p_listas}
\else
    \echo 'Falta SEED_PASSWORD_LISTAS'
    \quit 1
\endif
\if :{?p_auditoria}
\else
    \echo 'Falta SEED_PASSWORD_AUDITORIA'
    \quit 1
\endif

CREATE EXTENSION IF NOT EXISTS pgcrypto;

BEGIN;

INSERT INTO perfil (perfil, id_estado) VALUES
    ('administrador', 1),
    ('seguridad', 1),
    ('coordinador_turnos', 1),
    ('doctor_triage', 1),
    ('triage', 1),
    ('recepcionista', 1),
    ('lista_espera', 1),
    ('auditor', 1);

INSERT INTO actividades (nombre_actividad, enlace, id_estado) VALUES
    ('registrar_paciente', 'pacientes.html', 1),
    ('crear_triage', 'triage-nuevo.html', 1),
    ('seguimiento_paciente', 'seguimiento.html', 1),
    ('lista_espera', 'lista-espera.html', 1),
    ('auditoria', 'auditoria.html', 1),
    ('gestion_roles', 'roles.html', 1),
    ('dashboard_analitica', 'dashboard.html', 1),
    ('aprobar_cuentas', 'aprobaciones.html', 1);

INSERT INTO perfil_actividad (id_perfil, id_actividad)
SELECT p.id_perfil, a.id_actividad
FROM (VALUES
    ('administrador', 'registrar_paciente'),
    ('administrador', 'crear_triage'),
    ('administrador', 'seguimiento_paciente'),
    ('administrador', 'lista_espera'),
    ('administrador', 'auditoria'),
    ('administrador', 'gestion_roles'),
    ('administrador', 'dashboard_analitica'),
    ('seguridad', 'auditoria'),
    ('seguridad', 'aprobar_cuentas'),
    ('coordinador_turnos', 'lista_espera'),
    ('coordinador_turnos', 'dashboard_analitica'),
    ('doctor_triage', 'registrar_paciente'),
    ('doctor_triage', 'crear_triage'),
    ('doctor_triage', 'seguimiento_paciente'),
    ('doctor_triage', 'lista_espera'),
    ('recepcionista', 'registrar_paciente'),
    ('triage', 'crear_triage'),
    ('triage', 'seguimiento_paciente'),
    ('triage', 'lista_espera'),
    ('lista_espera', 'seguimiento_paciente'),
    ('lista_espera', 'lista_espera'),
    ('auditor', 'auditoria')
) AS matriz(perfil, actividad)
JOIN perfil p ON p.perfil = matriz.perfil
JOIN actividades a ON a.nombre_actividad = matriz.actividad;

-- admin.test es la cuenta maestra; todas las cuentas tendran turno 24h mas abajo.
INSERT INTO personal (nombre, apellido, email, cedula, telefono, usuario, clave,
                      id_perfil, id_estado, requiere_2fa, debe_cambiar_clave,
                      clave_expira, fecha_cambio_clave, estado_aprobacion,
                      aprobado_por, fecha_aprobacion, email_pendiente)
VALUES (
    'Laura', 'Martínez', 'pruebasosclinic+admin@gmail.com', '90000001', '3000000001',
    'admin.test', crypt(:'p_admin', gen_salt('bf', 12)),
    (SELECT id_perfil FROM perfil WHERE perfil = 'administrador'),
    1, TRUE, FALSE, NULL, now(), 'APROBADA', NULL, now(), NULL
);

INSERT INTO personal (nombre, apellido, email, cedula, telefono, usuario, clave,
                      id_perfil, id_estado, requiere_2fa, debe_cambiar_clave,
                      clave_expira, fecha_cambio_clave, estado_aprobacion,
                      aprobado_por, fecha_aprobacion, email_pendiente)
SELECT datos.nombre, datos.apellido, datos.email, datos.cedula, datos.telefono,
       datos.usuario, crypt(datos.clave, gen_salt('bf', 12)),
       p.id_perfil, 1, TRUE, FALSE, NULL, now(), 'APROBADA',
       (SELECT id_personal FROM personal WHERE usuario = 'admin.test'), now(), NULL
FROM (VALUES
    ('Carlos', 'Rojas', 'pruebasosclinic+seguridad@gmail.com', '90000002', '3000000002',
     'seguridad.sos', :'p_seguridad', 'seguridad'),
    ('Ana', 'Gómez', 'pruebasosclinic+coordinacion@gmail.com', '90000003', '3000000003',
     'coordinacion.sos', :'p_coordinacion', 'coordinador_turnos'),
    ('Diego', 'Ramírez', 'pruebasosclinic+medico1@gmail.com', '90000004', '3000000004',
     'medico.dia', :'p_medico_dia', 'doctor_triage'),
    ('Paula', 'Vargas', 'pruebasosclinic+medico2@gmail.com', '90000005', '3000000005',
     'medico.noche', :'p_medico_noche', 'doctor_triage'),
    ('Sandra', 'Ortiz', 'pruebasosclinic+recepcion@gmail.com', '90000006', '3000000006',
     'recepcion.sos', :'p_recepcion', 'recepcionista'),
    ('Jorge', 'Castro', 'pruebasosclinic+triage@gmail.com', '90000007', '3000000007',
     'triage.sos', :'p_triage', 'triage'),
    ('Natalia', 'Rojas', 'pruebasosclinic+listas@gmail.com', '90000008', '3000000008',
     'listas.sos', :'p_listas', 'lista_espera'),
    ('Héctor', 'Acosta', 'pruebasosclinic+auditoria@gmail.com', '90000009', '3000000009',
     'auditoria.sos', :'p_auditoria', 'auditor')
) AS datos(nombre, apellido, email, cedula, telefono, usuario, clave, perfil)
JOIN perfil p ON p.perfil = datos.perfil;

-- Todas las cuentas quedan disponibles 24/7 para pruebas funcionales.
INSERT INTO turno (id_personal, hora_inicio, hora_fin, id_estado)
SELECT p.id_personal, t.inicio::time, t.fin::time, 1
FROM (VALUES
    ('admin.test', '00:00', '00:00'),
    ('seguridad.sos', '00:00', '00:00'),
    ('coordinacion.sos', '00:00', '00:00'),
    ('medico.dia', '00:00', '00:00'),
    ('medico.noche', '00:00', '00:00'),
    ('recepcion.sos', '00:00', '00:00'),
    ('triage.sos', '00:00', '00:00'),
    ('listas.sos', '00:00', '00:00'),
    ('auditoria.sos', '00:00', '00:00')
) AS t(usuario, inicio, fin)
JOIN personal p ON p.usuario = t.usuario;

INSERT INTO categoria_triage (nombre_categoria, descripcion, prioridad, id_estado) VALUES
    ('Resucitacion', 'Riesgo vital inminente', 1, 1),
    ('Emergencia', 'Riesgo vital potencial', 2, 1),
    ('Urgencia', 'Requiere atencion pronta', 3, 1),
    ('Urgencia menor', 'Puede esperar de forma segura', 4, 1),
    ('Sin urgencia', 'No compromete la condicion del paciente', 5, 1);

INSERT INTO estado_triage (nombre_estado, descripcion) VALUES
    ('En espera', 'Paciente registrado, pendiente de atencion'),
    ('En atencion', 'Paciente siendo atendido'),
    ('Atendido', 'Episodio de triage cerrado');

INSERT INTO paciente (nombre, apellido, cedula, fecha_nacimiento, email, id_estado) VALUES
    ('María Fernanda', 'Gómez', '1000000001', DATE '1985-03-12', 'pruebasosclinic+paciente01@gmail.com', 1),
    ('Carlos Andrés', 'Rodríguez', '1000000002', DATE '1978-07-25', 'pruebasosclinic+paciente02@gmail.com', 1),
    ('Luz Marina', 'Martínez', '1000000003', DATE '1992-11-05', 'pruebasosclinic+paciente03@gmail.com', 1),
    ('Jorge Iván', 'Pérez', '1000000004', DATE '1965-01-18', 'pruebasosclinic+paciente04@gmail.com', 1),
    ('Ana Lucía', 'Torres', '1000000005', DATE '2001-09-30', 'pruebasosclinic+paciente05@gmail.com', 1),
    ('Diego Alejandro', 'Ramírez', '1000000006', DATE '1988-05-14', 'pruebasosclinic+paciente06@gmail.com', 1),
    ('Paula Andrea', 'Vargas', '1000000007', DATE '1995-12-22', 'pruebasosclinic+paciente07@gmail.com', 1),
    ('Luis Fernando', 'Castro', '1000000008', DATE '1970-04-08', 'pruebasosclinic+paciente08@gmail.com', 1),
    ('Sandra Milena', 'Ortiz', '1000000009', DATE '1983-08-17', 'pruebasosclinic+paciente09@gmail.com', 1),
    ('Andrés Felipe', 'Moreno', '1000000010', DATE '1999-02-11', 'pruebasosclinic+paciente10@gmail.com', 1),
    ('Claudia Patricia', 'Jiménez', '1000000011', DATE '1975-10-03', 'pruebasosclinic+paciente11@gmail.com', 1),
    ('Juan Sebastián', 'Herrera', '1000000012', DATE '2004-06-27', 'pruebasosclinic+paciente12@gmail.com', 1),
    ('Mónica', 'Salazar', '1000000013', DATE '1981-09-09', 'pruebasosclinic+paciente13@gmail.com', 1),
    ('Ricardo', 'Guzmán', '1000000014', DATE '1959-03-21', 'pruebasosclinic+paciente14@gmail.com', 1),
    ('Natalia', 'Rojas', '1000000015', DATE '1997-01-14', 'pruebasosclinic+paciente15@gmail.com', 1),
    ('Óscar Javier', 'Medina', '1000000016', DATE '1986-07-07', 'pruebasosclinic+paciente16@gmail.com', 1),
    ('Valentina', 'Cárdenas', '1000000017', DATE '2003-11-19', 'pruebasosclinic+paciente17@gmail.com', 1),
    ('Héctor', 'Acosta', '1000000018', DATE '1968-12-01', 'pruebasosclinic+paciente18@gmail.com', 1),
    ('Laura Camila', 'Peña', '1000000019', DATE '1993-04-23', 'pruebasosclinic+paciente19@gmail.com', 1),
    ('Sergio', 'León', '1000000020', DATE '1979-08-05', 'pruebasosclinic+paciente20@gmail.com', 1),
    ('Diana Carolina', 'Suárez', '1000000021', DATE '1990-02-28', 'pruebasosclinic+paciente21@gmail.com', 1),
    ('Miguel Ángel', 'Benítez', '1000000022', DATE '1962-06-15', 'pruebasosclinic+paciente22@gmail.com', 1),
    ('Adriana', 'Molina', '1000000023', DATE '1987-10-30', 'pruebasosclinic+paciente23@gmail.com', 1),
    ('Felipe', 'Navarro', '1000000024', DATE '2000-03-09', 'pruebasosclinic+paciente24@gmail.com', 1);

WITH datos (cedula, categoria, estado, espera, ubicacion, llegada, diagnostico, tratamiento) AS (
    VALUES
    ('1000000001', 'Urgencia', 'Atendido', 25, 'Consultorio 1', TIMESTAMP '2026-10-15 08:30:00', 'Dolor abdominal agudo', 'Analgesia y observacion'),
    ('1000000002', 'Emergencia', 'Atendido', 10, 'Urgencias', TIMESTAMP '2026-10-15 09:10:00', 'Dificultad respiratoria', 'Oxigeno y broncodilatadores'),
    ('1000000003', 'Urgencia menor', 'En espera', 40, 'Sala de espera', TIMESTAMP '2026-10-15 10:00:00', NULL, NULL),
    ('1000000004', 'Resucitacion', 'Atendido', 5, 'UCI', TIMESTAMP '2026-10-15 10:20:00', 'Paro cardiorrespiratorio', 'RCP avanzada'),
    ('1000000005', 'Sin urgencia', 'En espera', 60, 'Sala de espera', TIMESTAMP '2026-10-15 11:05:00', NULL, NULL),
    ('1000000006', 'Urgencia', 'En atencion', 20, 'Observación', TIMESTAMP '2026-10-15 11:40:00', NULL, NULL),
    ('1000000007', 'Emergencia', 'Atendido', 8, 'Urgencias', TIMESTAMP '2026-10-16 08:15:00', 'Crisis asmatica', 'Nebulizaciones'),
    ('1000000008', 'Urgencia menor', 'Atendido', 35, 'Consultorio 2', TIMESTAMP '2026-10-16 09:00:00', 'Lumbalgia', 'Analgesicos'),
    ('1000000009', 'Urgencia', 'En espera', 45, 'Sala de espera', TIMESTAMP '2026-10-16 09:45:00', NULL, NULL),
    ('1000000010', 'Sin urgencia', 'En espera', 70, 'Sala de espera', TIMESTAMP '2026-10-16 10:30:00', NULL, NULL),
    ('1000000011', 'Emergencia', 'En atencion', 12, 'Urgencias', TIMESTAMP '2026-10-16 11:10:00', NULL, NULL),
    ('1000000012', 'Urgencia', 'Atendido', 30, 'Consultorio 3', TIMESTAMP '2026-10-17 08:20:00', 'Migrana', 'Triptanes'),
    ('1000000013', 'Urgencia menor', 'En espera', 50, 'Sala de espera', TIMESTAMP '2026-10-17 09:05:00', NULL, NULL),
    ('1000000014', 'Resucitacion', 'Atendido', 3, 'UCI', TIMESTAMP '2026-10-17 09:40:00', 'Shock septico', 'Reanimacion y antibioticos'),
    ('1000000015', 'Sin urgencia', 'Atendido', 65, 'Consultorio 4', TIMESTAMP '2026-10-17 10:25:00', 'Control de hipertension', 'Ajuste de medicacion'),
    ('1000000016', 'Urgencia', 'En espera', 35, 'Sala de espera', TIMESTAMP '2026-10-18 08:10:00', NULL, NULL),
    ('1000000017', 'Emergencia', 'Atendido', 9, 'Urgencias', TIMESTAMP '2026-10-18 09:00:00', 'Trauma craneoencefalico', 'Tomografia y observacion'),
    ('1000000018', 'Urgencia menor', 'En atencion', 28, 'Observación', TIMESTAMP '2026-10-18 09:50:00', NULL, NULL),
    ('1000000019', 'Sin urgencia', 'En espera', 55, 'Sala de espera', TIMESTAMP '2026-10-18 10:35:00', NULL, NULL),
    ('1000000020', 'Urgencia', 'Atendido', 22, 'Consultorio 5', TIMESTAMP '2026-10-19 08:30:00', 'Infeccion urinaria', 'Antibiotico oral'),
    ('1000000021', 'Emergencia', 'En espera', 15, 'Urgencias', TIMESTAMP '2026-10-19 09:15:00', NULL, NULL),
    ('1000000022', 'Urgencia menor', 'Atendido', 38, 'Consultorio 1', TIMESTAMP '2026-10-19 10:05:00', 'Herida superficial', 'Sutura y curacion'),
    ('1000000023', 'Sin urgencia', 'En espera', 60, 'Sala de espera', TIMESTAMP '2026-10-19 10:50:00', NULL, NULL),
    ('1000000024', 'Urgencia', 'Atendido', 26, 'Consultorio 2', TIMESTAMP '2026-10-20 08:40:00', 'Gastroenteritis', 'Hidratacion oral'),
    ('1000000001', 'Urgencia', 'Atendido', 18, 'Consultorio 3', TIMESTAMP '2026-10-20 09:25:00', 'Recurrencia de dolor abdominal', 'Observacion y control'),
    ('1000000002', 'Emergencia', 'En atencion', 7, 'Urgencias', TIMESTAMP '2026-10-20 10:10:00', NULL, NULL),
    ('1000000003', 'Urgencia menor', 'Atendido', 33, 'Consultorio 4', TIMESTAMP '2026-10-20 10:55:00', 'Esguince de tobillo', 'Inmovilizacion'),
    ('1000000004', 'Resucitacion', 'En atencion', 2, 'UCI', TIMESTAMP '2026-10-21 08:05:00', NULL, NULL),
    ('1000000005', 'Sin urgencia', 'Atendido', 58, 'Consultorio 5', TIMESTAMP '2026-10-21 08:50:00', 'Certificado de salud', 'Educacion y egreso'),
    ('1000000006', 'Urgencia', 'En espera', 42, 'Sala de espera', TIMESTAMP '2026-10-21 09:30:00', NULL, NULL)
)
INSERT INTO registro_triage (id_paciente, id_estado_triage, id_categoria_triage,
                             tiempo_espera_minutos, ubicacion, fecha_hora_llegada,
                             id_estado, diagnostico_final, tratamiento)
SELECT p.id_paciente, e.id_estado_triage, c.id_categoria_triage,
       d.espera, d.ubicacion, d.llegada, 1, d.diagnostico, d.tratamiento
FROM datos d
JOIN paciente p ON p.cedula = d.cedula
JOIN estado_triage e ON e.nombre_estado = d.estado
JOIN categoria_triage c ON c.nombre_categoria = d.categoria;

COMMIT;
