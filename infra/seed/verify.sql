-- Verificacion del seed. Falla con una excepcion si algun conteo no coincide.
\set ON_ERROR_STOP on

DO $$
DECLARE n integer;
BEGIN
    SELECT count(*) INTO n FROM perfil;
    IF n <> 8 THEN RAISE EXCEPTION 'perfil: se esperaban 8, hay %', n; END IF;

    SELECT count(*) INTO n FROM actividades;
    IF n <> 8 THEN RAISE EXCEPTION 'actividades: se esperaban 8, hay %', n; END IF;

    SELECT count(*) INTO n FROM actividades
    WHERE nombre_actividad NOT IN (
        'registrar_paciente', 'crear_triage', 'seguimiento_paciente',
        'lista_espera', 'auditoria', 'gestion_roles', 'dashboard_analitica',
        'aprobar_cuentas'
    );
    IF n <> 0 THEN RAISE EXCEPTION 'hay actividades inesperadas: %', n; END IF;

    SELECT count(*) INTO n FROM perfil_actividad;
    IF n <> 22 THEN RAISE EXCEPTION 'perfil_actividad: se esperaban 22, hay %', n; END IF;

    SELECT count(*) INTO n FROM categoria_triage;
    IF n <> 5 THEN RAISE EXCEPTION 'categoria_triage: se esperaban 5, hay %', n; END IF;

    SELECT count(*) INTO n FROM estado_triage;
    IF n <> 3 THEN RAISE EXCEPTION 'estado_triage: se esperaban 3, hay %', n; END IF;

    SELECT count(*) INTO n FROM personal;
    IF n <> 9 THEN RAISE EXCEPTION 'personal: se esperaban 9, hay %', n; END IF;

    SELECT count(*) INTO n FROM personal
    WHERE estado_aprobacion <> 'APROBADA' OR id_estado <> 1;
    IF n <> 0 THEN RAISE EXCEPTION 'hay cuentas no aprobadas o inactivas: %', n; END IF;

    SELECT count(*) INTO n FROM personal WHERE requiere_2fa IS NOT TRUE;
    IF n <> 0 THEN RAISE EXCEPTION 'todas las cuentas deben tener 2FA: % sin 2FA', n; END IF;

    SELECT count(*) INTO n FROM personal WHERE clave NOT LIKE '$2%';
    IF n <> 0 THEN RAISE EXCEPTION 'hay claves que no son BCrypt: %', n; END IF;

    SELECT count(*) INTO n FROM turno;
    IF n <> 9 THEN RAISE EXCEPTION 'turno: se esperaban 9, hay %', n; END IF;

    SELECT count(*) INTO n FROM turno t
    JOIN personal p ON p.id_personal = t.id_personal
    WHERE t.hora_inicio = '00:00' AND t.hora_fin = '00:00';
    IF n <> 9 THEN RAISE EXCEPTION 'las 9 cuentas deben tener turno 24h, hay %', n; END IF;

    SELECT count(*) INTO n FROM turno t
    JOIN personal p ON p.id_personal = t.id_personal
    WHERE p.usuario = 'admin.test' AND t.hora_inicio = '00:00' AND t.hora_fin = '00:00';
    IF n <> 1 THEN RAISE EXCEPTION 'admin.test debe conservar su turno 24h'; END IF;

    SELECT count(*) INTO n FROM paciente;
    IF n <> 24 THEN RAISE EXCEPTION 'paciente: se esperaban 24, hay %', n; END IF;

    SELECT count(*) INTO n FROM paciente WHERE email IS NULL OR btrim(email) = '';
    IF n <> 0 THEN RAISE EXCEPTION 'todos los pacientes deben tener email: % sin email', n; END IF;

    SELECT count(*) INTO n FROM registro_triage;
    IF n <> 30 THEN RAISE EXCEPTION 'registro_triage: se esperaban 30, hay %', n; END IF;

    SELECT count(*) INTO n FROM registro_triage
    WHERE diagnostico_final IS NOT NULL AND btrim(diagnostico_final) <> ''
      AND tratamiento IS NOT NULL AND btrim(tratamiento) <> '';
    IF n < 10 THEN RAISE EXCEPTION 'muy pocos episodios atendidos con diagnostico/tratamiento: %', n; END IF;

    SELECT count(*) INTO n FROM estado_paciente;
    IF n <> 0 THEN RAISE EXCEPTION 'no se siembran signos vitales: %', n; END IF;

    SELECT count(*) INTO n FROM registro_auditoria;
    IF n <> 0 THEN RAISE EXCEPTION 'no se siembra auditoria clinica: %', n; END IF;

    SELECT count(*) INTO n FROM codigo_verificacion;
    IF n <> 0 THEN RAISE EXCEPTION 'no se siembran codigos 2FA: %', n; END IF;

    SELECT count(*) INTO n FROM token_recuperacion;
    IF n <> 0 THEN RAISE EXCEPTION 'no se siembran tokens de recuperacion: %', n; END IF;

    SELECT count(*) INTO n FROM pg_extension WHERE extname = 'pgcrypto';
    IF n <> 1 THEN RAISE EXCEPTION 'pgcrypto no esta instalada'; END IF;

    SELECT count(*) INTO n FROM information_schema.columns
    WHERE table_name = 'paciente' AND column_name = 'email';
    IF n <> 1 THEN RAISE EXCEPTION 'falta paciente.email'; END IF;

    SELECT count(*) INTO n FROM information_schema.columns
    WHERE table_name = 'registro_triage'
      AND column_name IN ('diagnostico_final', 'tratamiento');
    IF n <> 2 THEN RAISE EXCEPTION 'faltan columnas de cierre en registro_triage'; END IF;
END $$;

SELECT 'perfil' AS tabla, count(*) AS filas FROM perfil
UNION ALL SELECT 'actividades', count(*) FROM actividades
UNION ALL SELECT 'perfil_actividad', count(*) FROM perfil_actividad
UNION ALL SELECT 'categoria_triage', count(*) FROM categoria_triage
UNION ALL SELECT 'estado_triage', count(*) FROM estado_triage
UNION ALL SELECT 'personal', count(*) FROM personal
UNION ALL SELECT 'turno', count(*) FROM turno
UNION ALL SELECT 'paciente', count(*) FROM paciente
UNION ALL SELECT 'registro_triage', count(*) FROM registro_triage
UNION ALL SELECT 'estado_paciente', count(*) FROM estado_paciente
UNION ALL SELECT 'registro_auditoria', count(*) FROM registro_auditoria
ORDER BY tabla;
