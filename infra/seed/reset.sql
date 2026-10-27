-- Reset de datos de aplicacion. No elimina la base, el esquema ni los roles
-- de PostgreSQL. Solo deja vacias las tablas que el seed vuelve a poblar.
BEGIN;

TRUNCATE TABLE
    registro_auditoria,
    estado_paciente,
    registro_triage,
    paciente,
    turno,
    codigo_verificacion,
    token_recuperacion,
    sesion,
    personal,
    perfil_actividad,
    actividades,
    perfil,
    categoria_triage,
    estado_triage
RESTART IDENTITY CASCADE;

COMMIT;
