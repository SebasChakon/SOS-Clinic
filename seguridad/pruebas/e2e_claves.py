#!/usr/bin/env python3
"""
Prueba E2E de los cambios de claves, alta aprobada, recuperacion y 2FA.

Requiere:
  - Eureka, ms-seguridad y ms-gateway levantados (o el stack completo).
  - PostgreSQL accesible por `docker exec` al contenedor de la BD.
  - Un perfil `administrador`, uno `seguridad` y uno `doctor_triage`.

Uso:
  python seguridad/pruebas/e2e_claves.py

Variables opcionales:
  E2E_BASE_URL       (default http://127.0.0.1:8080)
  E2E_DB_CONTAINER   (default sos-postgres)
  E2E_DB_USER        (default sos_admin)
  E2E_DB_NAME        (default sosclinic)

No imprime contrasenas ni tokens. Crea y elimina cuentas `e2e.*`.
"""

import hashlib
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request

BASE = os.environ.get("E2E_BASE_URL", "http://127.0.0.1:8080").rstrip("/")
DB_CONTAINER = os.environ.get("E2E_DB_CONTAINER", "sos-postgres")
DB_USER = os.environ.get("E2E_DB_USER", "sos_admin")
DB_NAME = os.environ.get("E2E_DB_NAME", "sosclinic")

CLAVE_INICIAL = "E2e#2026!"
CLAVE_NUEVA = "Nube#4821"
HASH_INICIAL = "$2b$12$.HBDqOrWfl0qvhgVMYvysuEuNzOeBwNwfBrfpjoMM8reQ7ZSlBCOK"
HASH_CODIGO = "$2b$12$mXs7qMWpNXq/epu2Tp7NfuiSDXkJ0bRTTTjukTsaW4ucFkZ65S1rq"

TOKEN_RECUPERACION = "e2e-recovery-token"
TOKEN_ACTIVACION = "e2e-activation-token"
TOKEN_CORREO = "e2e-email-token"
TOKEN_EXPIRADO = "e2e-expired-token"
TOKEN_USADO = "e2e-used-token"
CHALLENGE_2FA = "e2e-challenge"
CHALLENGE_OTRO_PROPOSITO = "e2e-off-challenge"

resultados = []


def check(nombre, condicion, detalle=""):
    resultados.append((nombre, bool(condicion), detalle))
    if condicion:
        print(f"PASS  {nombre}")
    else:
        print(f"FAIL  {nombre}  {detalle}")
        raise RuntimeError(nombre)


def http(metodo, ruta, cuerpo=None, token=None):
    datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
    peticion = urllib.request.Request(BASE + ruta, data=datos, method=metodo)
    peticion.add_header("Content-Type", "application/json")
    if token:
        peticion.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(peticion, timeout=40) as respuesta:
            texto = respuesta.read().decode()
            return respuesta.status, json.loads(texto) if texto else None
    except urllib.error.HTTPError as error:
        texto = error.read().decode()
        try:
            return error.code, json.loads(texto) if texto else None
        except Exception:
            return error.code, texto


def login(usuario, clave):
    return http("POST", "/ms-seguridad/auth/login", {"usuario": usuario, "clave": clave})


def token_de(estado, datos, nombre):
    check(nombre, estado == 200 and isinstance(datos, dict) and datos.get("token"),
          f"estado={estado} datos={datos if not isinstance(datos, dict) else list(datos)}")
    return datos["token"]


def psql_query(sql):
    proceso = subprocess.run(
        ["docker", "exec", "-i", DB_CONTAINER, "psql", "-U", DB_USER, "-d", DB_NAME,
         "-v", "ON_ERROR_STOP=1", "-tAc", sql],
        capture_output=True, text=True, timeout=60)
    if proceso.returncode != 0:
        raise RuntimeError(f"psql: {proceso.stderr.strip()}")
    return proceso.stdout.strip()


def psql_script(sql):
    proceso = subprocess.run(
        ["docker", "exec", "-i", DB_CONTAINER, "psql", "-U", DB_USER, "-d", DB_NAME,
         "-v", "ON_ERROR_STOP=1"],
        input=sql, capture_output=True, text=True, timeout=120)
    if proceso.returncode != 0:
        raise RuntimeError(f"psql: {proceso.stderr.strip()}")


def sha256(valor):
    return hashlib.sha256(valor.encode()).hexdigest()


def limpiar():
    psql_script("""
UPDATE personal SET aprobado_por = NULL
WHERE aprobado_por IN (SELECT id_personal FROM personal WHERE usuario LIKE 'e2e.%');
DELETE FROM registro_auditoria
WHERE id_personal IN (SELECT id_personal FROM personal WHERE usuario LIKE 'e2e.%');
DELETE FROM turno
WHERE id_personal IN (SELECT id_personal FROM personal WHERE usuario LIKE 'e2e.%');
DELETE FROM codigo_verificacion
WHERE id_personal IN (SELECT id_personal FROM personal WHERE usuario LIKE 'e2e.%');
DELETE FROM token_recuperacion
WHERE id_personal IN (SELECT id_personal FROM personal WHERE usuario LIKE 'e2e.%');
DELETE FROM personal WHERE usuario LIKE 'e2e.%';
""")


def preparar():
    limpiar()
    perfil_admin = psql_query("SELECT id_perfil FROM perfil WHERE perfil='administrador'")
    perfil_seg = psql_query("SELECT id_perfil FROM perfil WHERE perfil='seguridad'")
    perfil_med = psql_query("SELECT id_perfil FROM perfil WHERE perfil='doctor_triage'")
    check("perfiles requeridos", bool(perfil_admin and perfil_seg and perfil_med),
          "faltan administrador, seguridad o doctor_triage")

    psql_script(f"""
INSERT INTO personal (nombre, apellido, email, cedula, telefono, usuario, clave, id_perfil, id_estado, requiere_2fa, debe_cambiar_clave, clave_expira, fecha_cambio_clave, estado_aprobacion)
VALUES
('E2E','Admin','e2e.admin@example.test','99100001',NULL,'e2e.admin','{HASH_INICIAL}',{perfil_admin},1,FALSE,FALSE,NULL,now(),'APROBADA'),
('E2E','Seguridad','e2e.seg@example.test','99100002',NULL,'e2e.seg','{HASH_INICIAL}',{perfil_seg},1,FALSE,FALSE,NULL,now(),'APROBADA'),
('E2E','Normal','e2e.normal@example.test','99100003',NULL,'e2e.normal','{HASH_INICIAL}',{perfil_med},1,FALSE,FALSE,NULL,now(),'APROBADA'),
('E2E','Temporal','e2e.temp@example.test','99100004',NULL,'e2e.temp','{HASH_INICIAL}',{perfil_med},1,FALSE,TRUE,now() + interval '1 hour',NULL,'APROBADA'),
('E2E','Recovery','e2e.recovery@example.test','99100005',NULL,'e2e.recovery','{HASH_INICIAL}',{perfil_med},1,FALSE,FALSE,NULL,now(),'APROBADA'),
('E2E','Activacion','e2e.act@example.test','99100006',NULL,'e2e.act','{HASH_INICIAL}',{perfil_med},1,FALSE,FALSE,NULL,NULL,'APROBADA'),
('E2E','Correo','e2e.mail@example.test','99100007',NULL,'e2e.mail','{HASH_INICIAL}',{perfil_med},1,FALSE,FALSE,NULL,now(),'APROBADA'),
('E2E','DosFactores','e2e.2fa@example.test','99100008',NULL,'e2e.2fa','{HASH_INICIAL}',{perfil_med},1,TRUE,FALSE,NULL,now(),'APROBADA');

UPDATE personal SET email_pendiente='nuevo.e2e.mail@example.test' WHERE usuario='e2e.mail';

INSERT INTO turno (id_personal, hora_inicio, hora_fin, id_estado)
SELECT id_personal, '00:00', '23:59', 1 FROM personal
WHERE usuario IN ('e2e.normal','e2e.temp','e2e.recovery','e2e.act','e2e.mail','e2e.2fa');

INSERT INTO token_recuperacion (id_personal, tipo, token_hash, fecha_creacion, fecha_expira, usado)
SELECT id_personal, 'RECUPERACION', '{sha256(TOKEN_RECUPERACION)}', now(), now() + interval '30 minutes', FALSE FROM personal WHERE usuario='e2e.recovery';
INSERT INTO token_recuperacion (id_personal, tipo, token_hash, fecha_creacion, fecha_expira, usado)
SELECT id_personal, 'RECUPERACION', '{sha256(TOKEN_EXPIRADO)}', now(), now() - interval '1 minute', FALSE FROM personal WHERE usuario='e2e.recovery';
INSERT INTO token_recuperacion (id_personal, tipo, token_hash, fecha_creacion, fecha_expira, usado)
SELECT id_personal, 'RECUPERACION', '{sha256(TOKEN_USADO)}', now(), now() + interval '30 minutes', TRUE FROM personal WHERE usuario='e2e.recovery';
INSERT INTO token_recuperacion (id_personal, tipo, token_hash, fecha_creacion, fecha_expira, usado)
SELECT id_personal, 'ACTIVACION', '{sha256(TOKEN_ACTIVACION)}', now(), now() + interval '72 hours', FALSE FROM personal WHERE usuario='e2e.act';
INSERT INTO token_recuperacion (id_personal, tipo, token_hash, fecha_creacion, fecha_expira, usado)
SELECT id_personal, 'CAMBIO_EMAIL', '{sha256(TOKEN_CORREO)}', now(), now() + interval '30 minutes', FALSE FROM personal WHERE usuario='e2e.mail';

INSERT INTO codigo_verificacion (id_personal, codigo, challenge_hash, proposito, fecha_expira, usado)
SELECT id_personal, '{HASH_CODIGO}', '{sha256(CHALLENGE_2FA)}', 'LOGIN', now() + interval '5 minutes', FALSE FROM personal WHERE usuario='e2e.2fa';
INSERT INTO codigo_verificacion (id_personal, codigo, challenge_hash, proposito, fecha_expira, usado)
SELECT id_personal, '{HASH_CODIGO}', '{sha256(CHALLENGE_OTRO_PROPOSITO)}', 'DESACTIVAR_2FA', now() + interval '5 minutes', FALSE FROM personal WHERE usuario='e2e.2fa';
""")


def main():
    preparar()

    estado, datos = http("GET", "/ms-seguridad/auth/politica-clave")
    check("politica publica por gateway", estado == 200 and datos.get("simbolosPermitidos") == "!?#$%.*@_+-", str(estado))

    estado, datos = login("e2e.admin", CLAVE_INICIAL)
    token_admin = token_de(estado, datos, "login admin")

    estado, datos = login("e2e.seg", CLAVE_INICIAL)
    token_seg = token_de(estado, datos, "login seguridad")

    estado, datos = login("e2e.normal", CLAVE_INICIAL)
    token_normal = token_de(estado, datos, "login usuario normal")
    check("perfil normal", datos.get("perfil") == "doctor_triage", str(datos.get("perfil")))

    estado, cuenta = http("GET", "/ms-seguridad/auth/mi-cuenta", token=token_normal)
    check("mi cuenta", estado == 200 and cuenta.get("usuario") == "e2e.normal", str(estado))

    estado, datos = http("POST", "/ms-seguridad/auth/cambiar-clave",
                         {"claveActual": CLAVE_INICIAL, "claveNueva": CLAVE_NUEVA}, token_normal)
    check("cambio de clave voluntario", estado == 200 and datos.get("debeCambiarClave") is False, str(estado))
    token_normal_nuevo = datos["token"]

    estado, _ = login("e2e.normal", CLAVE_INICIAL)
    check("clave anterior rechazada", estado == 401, str(estado))
    estado, datos = login("e2e.normal", CLAVE_NUEVA)
    check("clave nueva aceptada", estado == 200 and datos.get("token"), str(estado))

    estado, datos = http("POST", "/ms-seguridad/auth/cambiar-clave",
                         {"claveActual": CLAVE_NUEVA, "claveNueva": "Password1!"}, token_normal_nuevo)
    check("politica rechaza Password1!", estado == 400 and datos.get("errores"), str(estado))

    estado, datos = login("e2e.temp", CLAVE_INICIAL)
    check("login con clave temporal", estado == 200 and datos.get("debeCambiarClave") is True, str(estado))
    token_ccp = datos["token"]

    estado, datos = http("GET", "/ms-seguridad/auth/mi-cuenta", token=token_ccp)
    check("gateway bloquea ccp", estado == 403 and isinstance(datos, dict)
          and datos.get("code") == "CAMBIO_CLAVE_REQUERIDO", str(estado))

    estado, datos = http("POST", "/ms-seguridad/auth/cambiar-clave",
                         {"claveActual": CLAVE_INICIAL, "claveNueva": CLAVE_NUEVA}, token_ccp)
    check("cambio obligatorio", estado == 200 and datos.get("debeCambiarClave") is False, str(estado))
    estado, _ = http("GET", "/ms-seguridad/auth/mi-cuenta", token=datos["token"])
    check("token posterior al cambio obligatorio", estado == 200, str(estado))

    estado, datos = http("POST", "/ms-seguridad/auth/verificar-2fa",
                         {"challenge": CHALLENGE_2FA, "codigo": "123456"})
    check("2FA challenge valido", estado == 200 and datos.get("token"), str(estado))
    estado, _ = http("POST", "/ms-seguridad/auth/verificar-2fa",
                     {"challenge": CHALLENGE_2FA, "codigo": "123456"})
    check("2FA challenge de un solo uso", estado == 401, str(estado))
    estado, _ = http("POST", "/ms-seguridad/auth/verificar-2fa",
                     {"challenge": "no-existe", "codigo": "123456"})
    check("2FA challenge invalido", estado == 401, str(estado))
    estado, _ = http("POST", "/ms-seguridad/auth/verificar-2fa",
                     {"challenge": CHALLENGE_OTRO_PROPOSITO, "codigo": "123456"})
    check("2FA no acepta codigo de otro proposito", estado == 401, str(estado))

    estado, datos = http("POST", "/ms-seguridad/auth/validar-token-recuperacion",
                         {"token": TOKEN_RECUPERACION})
    check("validar enlace recuperacion", estado == 200 and datos.get("tipo") == "RECUPERACION", str(estado))
    estado, datos = http("POST", "/ms-seguridad/auth/restablecer-clave",
                         {"token": TOKEN_RECUPERACION, "clave": CLAVE_NUEVA})
    check("restablecer clave", estado == 200, str(estado))
    estado, datos = login("e2e.recovery", CLAVE_NUEVA)
    check("login tras recuperacion", estado == 200 and datos.get("token"), str(estado))
    estado, _ = login("e2e.recovery", CLAVE_INICIAL)
    check("clave anterior tras recuperacion rechazada", estado == 401, str(estado))

    estado, _ = http("POST", "/ms-seguridad/auth/restablecer-clave",
                     {"token": TOKEN_RECUPERACION, "clave": CLAVE_NUEVA})
    check("token de recuperacion reutilizado", estado == 400, str(estado))
    estado, _ = http("POST", "/ms-seguridad/auth/restablecer-clave",
                     {"token": TOKEN_EXPIRADO, "clave": CLAVE_NUEVA})
    check("token de recuperacion expirado", estado == 400, str(estado))
    estado, _ = http("POST", "/ms-seguridad/auth/restablecer-clave",
                     {"token": TOKEN_USADO, "clave": CLAVE_NUEVA})
    check("token de recuperacion usado", estado == 400, str(estado))

    estado, datos = http("POST", "/ms-seguridad/auth/validar-token-recuperacion",
                         {"token": TOKEN_ACTIVACION})
    check("validar enlace activacion", estado == 200 and datos.get("tipo") == "ACTIVACION", str(estado))
    estado, datos = http("POST", "/ms-seguridad/auth/restablecer-clave",
                         {"token": TOKEN_ACTIVACION, "clave": "Password1!"})
    check("activacion rechaza politica", estado == 400 and datos.get("errores"), str(estado))
    estado, _ = http("POST", "/ms-seguridad/auth/restablecer-clave",
                     {"token": TOKEN_ACTIVACION, "clave": CLAVE_NUEVA})
    check("activacion con clave valida", estado == 200, str(estado))
    estado, datos = login("e2e.act", CLAVE_NUEVA)
    check("login tras activacion", estado == 200 and datos.get("token"), str(estado))

    estado, _ = http("POST", "/ms-seguridad/auth/confirmar-email", {"token": TOKEN_CORREO})
    check("confirmar cambio de correo", estado == 200, str(estado))
    correo = psql_query("SELECT email FROM personal WHERE usuario='e2e.mail'")
    pendiente = psql_query("SELECT email_pendiente IS NULL FROM personal WHERE usuario='e2e.mail'")
    check("correo actualizado en BD", correo == "nuevo.e2e.mail@example.test" and pendiente == "t", correo)

    pendiente_payload = {
        "nombre": "E2E", "apellido": "Pendiente", "email": "e2e.pendnew@example.test",
        "cedula": "99100009", "telefono": None, "usuario": "e2e.pendnew",
        "idPerfil": int(psql_query("SELECT id_perfil FROM perfil WHERE perfil='doctor_triage'")),
        "horaInicio": "00:00", "horaFin": "23:59",
    }
    estado, datos = http("POST", "/ms-seguridad/admin/personal", pendiente_payload, token_admin)
    check("admin crea cuenta pendiente", estado == 200 and datos.get("estadoAprobacion") == "PENDIENTE",
          str(estado))
    id_pend = datos["idPersonal"]

    estado, _ = http("POST", f"/ms-seguridad/admin/personal/{id_pend}/aprobar", token=token_admin)
    check("admin no puede aprobar", estado == 403, str(estado))
    estado, _ = http("GET", "/ms-seguridad/admin/personal", token=token_seg)
    check("seguridad no puede listar todo el personal", estado == 403, str(estado))
    estado, pendientes = http("GET", "/ms-seguridad/admin/personal/pendientes", token=token_seg)
    check("seguridad ve pendientes", estado == 200 and any(
        p.get("idPersonal") == id_pend for p in pendientes), str(estado))
    estado, datos = http("POST", f"/ms-seguridad/admin/personal/{id_pend}/aprobar", token=token_seg)
    check("seguridad aprueba", estado == 200 and "correoEnviado" in datos, str(estado))

    estado, temporal = http("POST", f"/ms-seguridad/admin/personal/{id_pend}/clave-temporal",
                            token=token_admin)
    check("admin genera clave temporal", estado == 200 and temporal.get("claveTemporal"), str(estado))
    clave_temporal = temporal["claveTemporal"]
    listado = http("GET", "/ms-seguridad/admin/personal", token=token_admin)[1]
    check("clave temporal no aparece en el listado",
          clave_temporal not in json.dumps(listado), "fuga en listado")

    psql_query(f"UPDATE personal SET requiere_2fa=false WHERE usuario='e2e.pendnew'")
    estado, datos = login("e2e.pendnew", clave_temporal)
    check("login con clave temporal del admin", estado == 200 and datos.get("debeCambiarClave") is True,
          str(estado))
    estado, datos = http("POST", "/ms-seguridad/auth/cambiar-clave",
                         {"claveActual": clave_temporal, "claveNueva": CLAVE_NUEVA}, datos["token"])
    check("cambio de clave temporal del admin", estado == 200, str(estado))

    rechazo_payload = dict(pendiente_payload)
    rechazo_payload.update({"usuario": "e2e.reject", "email": "e2e.reject@example.test",
                            "cedula": "99100010", "nombre": "E2E", "apellido": "Rechazo"})
    estado, datos = http("POST", "/ms-seguridad/admin/personal", rechazo_payload, token_admin)
    check("admin crea cuenta para rechazo", estado == 200, str(estado))
    id_rech = datos["idPersonal"]
    estado, _ = http("POST", f"/ms-seguridad/admin/personal/{id_rech}/rechazar",
                     {"motivo": "prueba E2E"}, token_seg)
    check("seguridad rechaza", estado == 200, str(estado))
    estado_bd = psql_query(f"SELECT estado_aprobacion FROM personal WHERE id_personal={id_rech}")
    check("estado rechazada en BD", estado_bd == "RECHAZADA", estado_bd)

    estado, _ = http("GET", "/ms-seguridad/auth/mi-cuenta")
    check("sin token devuelve 401", estado == 401, str(estado))

    auditorias = psql_query("""
SELECT count(*) FROM registro_auditoria
WHERE id_personal IN (SELECT id_personal FROM personal WHERE usuario LIKE 'e2e.%');
""")
    check("auditoria de acciones de prueba", int(auditorias) > 0, auditorias)

    estado, _ = login("e2e.pendnew", CLAVE_NUEVA)
    check("login final de cuenta aprobada", estado == 200, str(estado))


if __name__ == "__main__":
    try:
        main()
        print(f"\nRESULTADO: {len(resultados)} comprobaciones, todas correctas")
    except Exception as error:
        print(f"\nRESULTADO: FALLO -> {error}")
        sys.exit(1)
    finally:
        try:
            limpiar()
            print("Limpieza de cuentas e2e completada")
        except Exception as limpieza:
            print(f"Aviso: no se pudo limpiar: {limpieza}")
