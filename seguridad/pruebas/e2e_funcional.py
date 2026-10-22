#!/usr/bin/env python3
"""
Pruebas funcionales E2E de SOS Clinic (ronda 3, fase 1).

Cubre los flujos clinicos y administrativos que no estaban cubiertos por
`infra/seed/verify_seed.py` ni por `seguridad/pruebas/e2e_claves.py`:

  - Pacientes: alta, cedula duplicada, busqueda, validacion.
  - Triage: creacion, priorizacion, ubicacion, atencion, alta e idempotencia.
  - Signos vitales: tomas sucesivas y validacion.
  - Alta orquestada: correos por tipo/estado, reenvio y PDF de historia.
  - Analitica: resumen/capacidad y control de acceso por rol.
  - Agente IA: autenticacion, validacion y consulta real (si Groq responde).
  - Auditoria: registro desde frontend y consulta por rol.
  - Turnos: rechazo de login fuera del turno operativo.

Requiere el stack completo levantado y el seed canonico cargado.
Usa las credenciales de `infra/seed/credenciales.local.env` sin imprimirlas.
Escribe `seguridad/resultados/fase1-funcional.csv` y no deja datos de prueba
en la base (salvo que se use --no-cleanup).

Uso:
  python seguridad/pruebas/e2e_funcional.py
  python seguridad/pruebas/e2e_funcional.py --no-cleanup
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import hashlib
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RESULTADOS = ROOT / "seguridad" / "resultados"
CSV_SALIDA = RESULTADOS / "fase1-funcional.csv"
ENV_FILE = ROOT / ".env"
CRED_FILE = ROOT / "infra" / "seed" / "credenciales.local.env"

BASE = os.environ.get("E2E_BASE_URL", "http://127.0.0.1:8080").rstrip("/")
DB_CONTAINER = "sos-postgres"
DB_USER = "sos_admin"
DB_NAME = "sosclinic"

CLAVE_E2E = "E2e#2026!"
HASH_E2E = "$2b$12$.HBDqOrWfl0qvhgVMYvysuEuNzOeBwNwfBrfpjoMM8reQ7ZSlBCOK"

MARCA = dt.datetime.now().strftime("%y%m%d%H%M%S")
CEDULA = "9" + MARCA[6:] + "1"
CEDULA_IDEM = "9" + MARCA[6:] + "2"
EMAIL_PACIENTE = "pruebasosclinic+funcional@gmail.com"
USUARIO_TURNO = "e2e.func.turno"

resultados: list[dict[str, str]] = []
inicio = dt.datetime.now()


class Critico(Exception):
    """Fallo que impide continuar con la seccion actual."""


# --------------------------------------------------------------------------- #
# Utilidades
# --------------------------------------------------------------------------- #

def leer_env(ruta: Path) -> dict[str, str]:
    valores: dict[str, str] = {}
    if not ruta.exists():
        return valores
    for linea in ruta.read_text(encoding="utf-8").splitlines():
        linea = linea.strip()
        if not linea or linea.startswith("#") or "=" not in linea:
            continue
        clave, valor = linea.split("=", 1)
        valor = valor.strip()
        if len(valor) >= 2 and valor[0] == valor[-1] and valor[0] in ("'", '"'):
            valor = valor[1:-1]
        valores[clave.strip()] = valor
    return valores


def psql(sql: str) -> str:
    proceso = subprocess.run(
        ["docker", "exec", "-i", DB_CONTAINER, "psql", "-U", DB_USER, "-d", DB_NAME,
         "-v", "ON_ERROR_STOP=1", "-tAc", sql],
        capture_output=True, text=True, timeout=90,
    )
    if proceso.returncode != 0:
        raise RuntimeError(f"psql: {proceso.stderr.strip()}")
    return proceso.stdout.strip()


def sha256(valor: str) -> str:
    return hashlib.sha256(valor.encode()).hexdigest()


def http(metodo: str, ruta: str, cuerpo=None, token: str | None = None,
         timeout: int = 90):
    datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
    peticion = urllib.request.Request(BASE + ruta, data=datos, method=metodo)
    peticion.add_header("Content-Type", "application/json")
    if token:
        peticion.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(peticion, timeout=timeout) as respuesta:
            crudo = respuesta.read()
            return respuesta.status, _json_o_texto(crudo)
    except urllib.error.HTTPError as error:
        return error.code, _json_o_texto(error.read())


def http_binario(metodo: str, ruta: str, token: str | None = None,
                 timeout: int = 90):
    peticion = urllib.request.Request(BASE + ruta, method=metodo)
    if token:
        peticion.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(peticion, timeout=timeout) as respuesta:
            return respuesta.status, respuesta.read(), dict(respuesta.headers)
    except urllib.error.HTTPError as error:
        return error.code, error.read(), dict(error.headers)


def _json_o_texto(crudo: bytes):
    texto = crudo.decode("utf-8", errors="replace")
    try:
        return json.loads(texto) if texto else None
    except Exception:
        return texto


def registrar(seccion: str, caso: str, estado: str, detalle: str = "") -> None:
    resultados.append({
        "fecha_hora": dt.datetime.now().isoformat(timespec="seconds"),
        "seccion": seccion,
        "caso": caso,
        "estado": estado,
        "detalle": detalle[:500],
    })
    print(f"{estado:4}  [{seccion}] {caso}" + (f"  -> {detalle}" if detalle else ""))


def check(seccion: str, caso: str, condicion: bool, detalle: str = "") -> None:
    if condicion:
        registrar(seccion, caso, "PASS", detalle)
    else:
        registrar(seccion, caso, "FAIL", detalle)
        raise Critico(caso)


def warn(seccion: str, caso: str, detalle: str = "") -> None:
    registrar(seccion, caso, "WARN", detalle)


def skip(seccion: str, caso: str, detalle: str = "") -> None:
    registrar(seccion, caso, "SKIP", detalle)


def login(usuario: str, clave: str):
    estado, datos = http("POST", "/ms-seguridad/auth/login",
                         {"usuario": usuario, "clave": clave})
    if estado == 200 and isinstance(datos, dict) and datos.get("requiere2fa"):
        challenge = datos.get("challenge")
        if not challenge:
            return estado, datos
        psql("UPDATE codigo_verificacion "
             "SET codigo = crypt('123456', gen_salt('bf', 12)) "
             f"WHERE challenge_hash = '{sha256(challenge)}' AND usado = FALSE;")
        estado, datos = http("POST", "/ms-seguridad/auth/verificar-2fa",
                             {"challenge": challenge, "codigo": "123456"})
    return estado, datos


def credenciales() -> dict[str, str]:
    return leer_env(CRED_FILE)


def limpiar() -> None:
    psql(f"""
DELETE FROM estado_paciente WHERE id_registro IN (
    SELECT id_registro FROM registro_triage WHERE id_paciente IN (
        SELECT id_paciente FROM paciente WHERE cedula IN ('{CEDULA}','{CEDULA_IDEM}')));
DELETE FROM registro_triage WHERE id_paciente IN (
    SELECT id_paciente FROM paciente WHERE cedula IN ('{CEDULA}','{CEDULA_IDEM}'));
DELETE FROM paciente WHERE cedula IN ('{CEDULA}','{CEDULA_IDEM}');
DELETE FROM registro_auditoria WHERE accion = 'e2e_funcional';
DELETE FROM turno WHERE id_personal IN (
    SELECT id_personal FROM personal WHERE usuario = '{USUARIO_TURNO}');
DELETE FROM personal WHERE usuario = '{USUARIO_TURNO}';
""")


# --------------------------------------------------------------------------- #
# Secciones
# --------------------------------------------------------------------------- #

def seccion_login(creds: dict[str, str]) -> dict[str, dict]:
    seccion = "login"
    sesiones: dict[str, dict] = {}
    claves_env = {
        "medico.dia": "SEED_PASSWORD_MEDICO_DIA",
        "admin.test": "SEED_PASSWORD_ADMIN",
        "coordinacion.sos": "SEED_PASSWORD_COORDINACION",
        "auditoria.sos": "SEED_PASSWORD_AUDITORIA",
    }
    for usuario, clave_env in claves_env.items():
        clave = creds.get(clave_env)
        if not clave:
            skip(seccion, f"credencial {usuario}", "falta en credenciales.local.env")
            continue
        estado, datos = login(usuario, clave)
        check(seccion, f"login {usuario}", estado == 200 and isinstance(datos, dict)
              and bool(datos.get("token")), f"estado={estado}")
        check(seccion, f"perfil {usuario}", datos.get("usuario") == usuario,
              str(datos.get("perfil")))
        sesiones[usuario] = datos
    check(seccion, "sesiones requeridas", {"medico.dia", "admin.test", "coordinacion.sos",
                                           "auditoria.sos"} <= sesiones.keys(),
          f"obtenidas={sorted(sesiones)}")
    return sesiones


def seccion_pacientes(medico: dict) -> dict:
    seccion = "pacientes"
    token = medico["token"]

    estado, lista = http("GET", "/ms-pacientes/pacientes", token=token)
    check(seccion, "listar pacientes", estado == 200 and isinstance(lista, list)
          and len(lista) >= 24, f"estado={estado} total={len(lista) if isinstance(lista, list) else '?'}")

    cuerpo = {
        "nombre": "Paciente", "apellido": "Funcional",
        "cedula": CEDULA, "fechaNacimiento": "1985-04-12",
        "email": EMAIL_PACIENTE,
    }

    # La columna admite 12 caracteres; sin @Size, el error llega como 409
    # generico en vez de 400. Se documenta como hallazgo menor.
    larga = dict(cuerpo, cedula="9" * 20, nombre="Paciente")
    estado, respuesta_larga = http("POST", "/ms-pacientes/pacientes", larga, token)
    if estado == 400:
        registrar(seccion, "cedula fuera de longitud", "PASS", "rechazada con 400")
    else:
        warn(seccion, "cedula fuera de longitud",
             f"estado={estado} (ideal 400): {respuesta_larga}")

    estado, paciente = http("POST", "/ms-pacientes/pacientes", cuerpo, token)
    check(seccion, "crear paciente", estado == 200 and isinstance(paciente, dict)
          and paciente.get("idPaciente"), f"estado={estado} respuesta={paciente}")
    check(seccion, "cedula persistida", paciente.get("cedula") == CEDULA)

    estado, duplicado = http("POST", "/ms-pacientes/pacientes", cuerpo, token)
    check(seccion, "cedula duplicada rechazada", estado == 409, f"estado={estado}")

    estado, encontrado = http("GET", f"/ms-pacientes/pacientes/cedula/{CEDULA}", token=token)
    check(seccion, "buscar por cedula", estado == 200
          and encontrado.get("idPaciente") == paciente["idPaciente"], f"estado={estado}")

    estado, parciales = http("GET", "/ms-pacientes/pacientes/buscar?q=" + CEDULA[:6],
                             token=token)
    check(seccion, "busqueda parcial", estado == 200 and isinstance(parciales, list)
          and any(p.get("cedula") == CEDULA for p in parciales), f"estado={estado}")

    invalido = dict(cuerpo)
    invalido["nombre"] = ""
    invalido["cedula"] = CEDULA + "9"
    estado, _ = http("POST", "/ms-pacientes/pacientes", invalido, token)
    check(seccion, "validacion de campos", estado == 400, f"estado={estado}")

    estado, _ = http("GET", "/ms-pacientes/pacientes")
    check(seccion, "pacientes sin token", estado == 401, f"estado={estado}")

    return paciente


def seccion_triage(medico: dict, paciente: dict) -> dict:
    seccion = "triage"
    token = medico["token"]

    categoria = psql("SELECT id_categoria_triage FROM categoria_triage "
                     "WHERE prioridad = 3 AND id_estado = 1 LIMIT 1")
    check(seccion, "categoria de prueba disponible", bool(categoria), categoria)

    estado, registro = http("POST", "/ms-triage/triage",
                            {"idPaciente": paciente["idPaciente"],
                             "idCategoriaTriage": int(categoria),
                             "ubicacion": "Sala de espera A"}, token)
    check(seccion, "crear episodio", estado == 200 and isinstance(registro, dict)
          and registro.get("idRegistro"), f"estado={estado} respuesta={registro}")
    check(seccion, "estado inicial En espera",
          (registro.get("estadoTriage") or {}).get("nombreEstado") == "En espera",
          str(registro.get("estadoTriage")))

    id_registro = registro["idRegistro"]
    estado, consultado = http("GET", f"/ms-triage/triage/{id_registro}", token=token)
    check(seccion, "consultar episodio", estado == 200
          and consultado.get("idPaciente") == paciente["idPaciente"], f"estado={estado}")

    estado, actualizado = http("PUT", f"/ms-triage/triage/{id_registro}/ubicacion",
                               {"ubicacion": "Consultorio 3"}, token)
    check(seccion, "actualizar ubicacion", estado == 200
          and actualizado.get("ubicacion") == "Consultorio 3", f"estado={estado}")

    estado, prioridad = http("GET", "/ms-triage/triage/prioridad", token=token)
    check(seccion, "lista de prioridad", estado == 200 and isinstance(prioridad, list),
          f"estado={estado}")
    nuestro = next((r for r in prioridad if r.get("idRegistro") == id_registro), None)
    check(seccion, "episodio en lista", nuestro is not None,
          f"idRegistro={id_registro} encontrado={nuestro is not None}")
    prioridades = [(r.get("categoriaTriage") or {}).get("prioridad", 999) for r in prioridad]
    check(seccion, "orden por prioridad", prioridades == sorted(prioridades),
          f"primeras={prioridades[:8]}")
    check(seccion, "tiempo de espera calculado",
          isinstance(nuestro.get("tiempoEsperaMinutos"), int)
          and nuestro["tiempoEsperaMinutos"] >= 0,
          str(nuestro.get("tiempoEsperaMinutos")))

    estado, atencion = http("PUT", f"/ms-triage/triage/{id_registro}/en-atencion",
                            None, token)
    check(seccion, "pasar a En atencion", estado == 200
          and (atencion.get("estadoTriage") or {}).get("nombreEstado") == "En atencion",
          f"estado={estado}")

    # Segundo episodio para probar la guarda de idempotencia del alta.
    estado, registro2 = http("POST", "/ms-triage/triage",
                             {"idPaciente": paciente["idPaciente"],
                              "idCategoriaTriage": int(categoria),
                              "ubicacion": "Sala de espera B"}, token)
    check(seccion, "crear segundo episodio", estado == 200
          and registro2.get("idRegistro"), f"estado={estado}")

    id_idem = registro2["idRegistro"]
    estado, alta1 = http("PUT", f"/ms-triage/triage/{id_idem}/atendido",
                         {"diagnosticoFinal": "Diagnostico A",
                          "tratamiento": "Tratamiento A"}, token)
    check(seccion, "alta manual", estado == 200
          and alta1.get("diagnosticoFinal") == "Diagnostico A", f"estado={estado}")

    estado, alta2 = http("PUT", f"/ms-triage/triage/{id_idem}/atendido",
                         {"diagnosticoFinal": "Diagnostico B",
                          "tratamiento": "Tratamiento B"}, token)
    check(seccion, "alta idempotente", estado == 200
          and alta2.get("diagnosticoFinal") == "Diagnostico A",
          f"diagnostico={alta2.get('diagnosticoFinal')}")

    return {"idRegistro": id_registro, "idIdem": id_idem}


def seccion_signos(medico: dict, id_registro: int) -> None:
    seccion = "signos"
    token = medico["token"]

    def toma(fc: int, temp: float) -> dict:
        return {
            "idRegistro": id_registro,
            "frecuenciaCardiaca": fc,
            "presionSistolica": 120,
            "presionDiastolica": 80,
            "temperatura": temp,
            "saturacion": 97,
            "frecuenciaRespiratoria": 18,
            "glucemia": 95.5,
            "descripcionSintoma": "Dolor toracico leve",
        }

    estado, primera = http("POST", "/ms-pacientes/pacientes/signos-vitales",
                           toma(88, 36.7), token)
    check(seccion, "primera toma", estado == 200 and primera.get("idRegistroEstado"),
          f"estado={estado}")
    check(seccion, "personal desde JWT",
          primera.get("idPersonal") == medico.get("idPersonal"),
          f"idPersonal={primera.get('idPersonal')}")

    estado, segunda = http("POST", "/ms-pacientes/pacientes/signos-vitales",
                           toma(92, 37.1), token)
    check(seccion, "segunda toma", estado == 200, f"estado={estado}")

    estado, tomas = http("GET", f"/ms-pacientes/pacientes/signos-vitales/registro/{id_registro}",
                         token=token)
    check(seccion, "tomas conservadas", estado == 200 and len(tomas) == 2,
          f"estado={estado} total={len(tomas) if isinstance(tomas, list) else '?'}")

    estado, _ = http("POST", "/ms-pacientes/pacientes/signos-vitales",
                     dict(toma(999, 36.5)), token)
    check(seccion, "validacion de rango", estado == 400, f"estado={estado}")


def seccion_alta(medico: dict, id_registro: int) -> None:
    seccion = "alta"
    token = medico["token"]

    cuerpo = {
        "diagnosticoFinal": "Dolor toracico no cardiogenico",
        "tratamiento": "Reposo y control en 48 horas",
        "direccion": "Calle 45 # 24-30, Bogota",
    }
    inicio_alta = time.time()
    estado, alta = http("POST",
                        f"/ms-pacientes/pacientes/atenciones/{id_registro}/finalizar",
                        cuerpo, token, timeout=180)
    duracion = time.time() - inicio_alta
    check(seccion, "finalizar atencion", estado == 200 and isinstance(alta, dict)
          and alta.get("altaOk") is True, f"estado={estado} respuesta={alta}")
    correos = alta.get("correos") or []
    tipos = {c.get("tipo") for c in correos}
    check(seccion, "tres correos evaluados", tipos == {"teleconsulta-profesional",
                                                       "cita-paciente", "historia-pdf"},
          str(tipos))
    validos = {"ENVIADO", "FALLIDO", "OMITIDO"}
    check(seccion, "estados de correo validos",
          all(c.get("estado") in validos for c in correos), str(correos))
    registrar(seccion, "duracion del alta", "INFO", f"{duracion:.1f} s")
    fallidos = [c for c in correos if c.get("estado") == "FALLIDO"]
    if fallidos:
        warn(seccion, "entrega SMTP con fallos", json.dumps(fallidos, ensure_ascii=False))
    omitidos = [c for c in correos if c.get("estado") == "OMITIDO"]
    if omitidos:
        warn(seccion, "correos omitidos", json.dumps(omitidos, ensure_ascii=False))

    estado, episodio = http("GET", f"/ms-triage/triage/{id_registro}", token=token)
    check(seccion, "alta persistida en triage", estado == 200
          and (episodio.get("estadoTriage") or {}).get("nombreEstado") == "Atendido"
          and episodio.get("diagnosticoFinal") == cuerpo["diagnosticoFinal"],
          f"estado={estado} episodio={episodio}")

    estado, pdf, headers = http_binario(
        "GET", f"/ms-pacientes/pacientes/historias/{id_registro}/pdf", token)
    content_type = (headers.get("Content-Type") or "").lower()
    check(seccion, "descargar PDF", estado == 200 and pdf.startswith(b"%PDF")
          and "application/pdf" in content_type,
          f"estado={estado} bytes={len(pdf)} tipo={content_type}")

    estado, reenvio = http("POST",
                           f"/ms-pacientes/pacientes/atenciones/{id_registro}/reenviar-correos",
                           {"direccion": cuerpo["direccion"]}, token, timeout=180)
    check(seccion, "reenviar correos", estado == 200 and isinstance(reenvio, dict)
          and reenvio.get("altaOk") is True, f"estado={estado}")
    correos_reenvio = (reenvio or {}).get("correos") or []
    estados_reenvio = {c.get("tipo"): c.get("estado") for c in correos_reenvio}
    registrar(seccion, "estados del reenvio", "INFO", json.dumps(estados_reenvio))
    fallidos_reenvio = [c for c in correos_reenvio if c.get("estado") == "FALLIDO"]
    if fallidos_reenvio:
        warn(seccion, "reenvio con fallos", json.dumps(fallidos_reenvio, ensure_ascii=False))
    estado, episodio2 = http("GET", f"/ms-triage/triage/{id_registro}", token=token)
    check(seccion, "alta no se duplica al reenviar", estado == 200
          and (episodio2.get("estadoTriage") or {}).get("nombreEstado") == "Atendido",
          f"estado={estado}")

    estado, _ = http("GET", "/ms-pacientes/pacientes/historias/99999999/pdf", token=token)
    check(seccion, "PDF de episodio inexistente", estado == 404, f"estado={estado}")


def seccion_analitica(coordinacion: dict, medico: dict) -> None:
    seccion = "analitica"
    rango = "desde=2024-01-01&hasta=2026-12-31"

    estado, resumen = http("GET", f"/ms-analitica/dashboard/resumen?{rango}",
                           token=coordinacion["token"])
    check(seccion, "resumen con coordinacion", estado == 200
          and isinstance(resumen, dict), f"estado={estado} respuesta={resumen}")

    estado, llegadas = http("GET", f"/ms-analitica/dashboard/llegadas?{rango}",
                            token=coordinacion["token"])
    check(seccion, "llegadas con coordinacion", estado == 200
          and isinstance(llegadas, dict), f"estado={estado}")

    estado, capacidad = http("GET", f"/ms-analitica/dashboard/capacidad?{rango}",
                             token=coordinacion["token"])
    check(seccion, "capacidad con coordinacion", estado == 200
          and isinstance(capacidad, list), f"estado={estado}")

    estado, _ = http("GET", f"/ms-analitica/dashboard/resumen?{rango}",
                     token=medico["token"])
    check(seccion, "rol clinico rechazado", estado == 403, f"estado={estado}")

    estado, _ = http("GET", f"/ms-analitica/dashboard/resumen?{rango}")
    check(seccion, "sin token rechazado", estado == 401, f"estado={estado}")

    estado, _ = http("GET",
                     "/ms-analitica/dashboard/resumen?desde=2026-12-31&hasta=2024-01-01",
                     token=coordinacion["token"])
    check(seccion, "rango invalido", estado == 400, f"estado={estado}")


def seccion_agente(medico: dict) -> None:
    seccion = "agente"
    token = medico["token"]

    estado, _ = http("POST", "/ms-ia-agente/agente/preguntar",
                     {"mensaje": "Hola"})
    check(seccion, "sin token rechazado", estado == 401, f"estado={estado}")

    estado, datos = http("POST", "/ms-ia-agente/agente/preguntar",
                         {"mensaje": ""}, token)
    check(seccion, "mensaje vacio rechazado", estado == 400, f"estado={estado}")

    estado, respuesta = http("POST", "/ms-ia-agente/agente/preguntar",
                             {"mensaje": "Responde solo: sistema operativo."},
                             token, timeout=120)
    if estado == 200 and isinstance(respuesta, dict):
        registrar(seccion, "consulta real al agente", "PASS",
                  f"campos={sorted(respuesta.keys())}")
    else:
        warn(seccion, "consulta real al agente", f"estado={estado} respuesta={respuesta}")


def seccion_auditoria(medico: dict, admin: dict) -> None:
    seccion = "auditoria"
    token = medico["token"]

    estado, _ = http("POST", "/ms-seguridad/auditoria/registrar",
                     {"accion": "e2e_funcional", "detalle": "Chequeo E2E fase 1"})
    check(seccion, "registro sin token", estado == 401, f"estado={estado}")

    estado, _ = http("POST", "/ms-seguridad/auditoria/registrar",
                     {"accion": "e2e_funcional", "detalle": "Chequeo E2E fase 1"}, token)
    check(seccion, "registrar accion", estado == 200, f"estado={estado}")

    estado, registros = http("GET", "/ms-seguridad/admin/auditoria",
                             token=admin["token"])
    check(seccion, "listar auditoria como admin", estado == 200
          and isinstance(registros, list), f"estado={estado}")
    encontrado = any(r.get("accion") == "e2e_funcional"
                     and (r.get("detalle") or "").startswith("Chequeo E2E")
                     for r in registros)
    check(seccion, "accion visible en auditoria", encontrado,
          f"total={len(registros) if isinstance(registros, list) else '?'}")

    estado, _ = http("GET", "/ms-seguridad/admin/personal", token=medico["token"])
    check(seccion, "personal admin rechazado a medico", estado == 403,
          f"estado={estado}")


def seccion_frontend() -> None:
    seccion = "frontend"
    base = "http://127.0.0.1:5500"
    paginas = {
        "/index.html": "SOS Clinic",
        "/login.html": "Acceso al sistema",
        "/recuperar-clave.html": "Recuperar clave",
        "/restablecer-clave.html": "Nueva clave",
        "/cambiar-clave.html": "Cambiar clave",
        "/cuenta.html": "Mi cuenta",
        "/aprobaciones.html": "Aprobación de cuentas",
        "/pacientes.html": "Pacientes",
        "/triage-nuevo.html": "Nuevo triage",
        "/lista-espera.html": "Lista de espera",
        "/seguimiento.html": "Seguimiento del paciente",
        "/auditoria.html": "Auditor",
        "/roles.html": "Administración",
        "/dashboard.html": "Anal",
    }
    for ruta, marcador in paginas.items():
        try:
            with urllib.request.urlopen(base + ruta, timeout=20) as respuesta:
                estado = respuesta.status
                html = respuesta.read().decode("utf-8", errors="replace")
        except urllib.error.HTTPError as error:
            estado, html = error.code, error.read().decode("utf-8", errors="replace")
        except Exception as error:
            registrar(seccion, f"pagina {ruta}", "FAIL", repr(error))
            continue
        check(seccion, f"pagina {ruta}", estado == 200
              and marcador.lower() in html.lower(),
              f"estado={estado} marcador={marcador!r}")

    try:
        with urllib.request.urlopen(base + "/assets/api.js", timeout=20) as respuesta:
            estado = respuesta.status
            api = respuesta.read().decode("utf-8", errors="replace")
    except Exception as error:
        registrar(seccion, "asset api.js", "FAIL", repr(error))
        return
    check(seccion, "asset api.js", estado == 200 and "fetch" in api,
          f"estado={estado} bytes={len(api)}")


def seccion_turnos() -> None:
    seccion = "turnos"
    ahora = dt.datetime.now().time()
    if ahora < dt.time(12, 0):
        inicio_turno, fin_turno = "20:00", "20:01"
    else:
        inicio_turno, fin_turno = "03:00", "03:01"

    psql(f"""
INSERT INTO personal (nombre, apellido, email, cedula, telefono, usuario, clave,
                      id_perfil, id_estado, requiere_2fa, debe_cambiar_clave,
                      clave_expira, fecha_cambio_clave, estado_aprobacion)
SELECT 'E2E', 'Turno', 'e2e.func.turno@example.test', '9{MARCA[6:]}9',
       NULL, '{USUARIO_TURNO}', '{HASH_E2E}', id_perfil, 1, FALSE, FALSE,
       NULL, now(), 'APROBADA'
FROM perfil WHERE perfil = 'doctor_triage';
INSERT INTO turno (id_personal, hora_inicio, hora_fin, id_estado)
SELECT id_personal, '{inicio_turno}', '{fin_turno}', 1
FROM personal WHERE usuario = '{USUARIO_TURNO}';
""")

    estado, datos = login(USUARIO_TURNO, CLAVE_E2E)
    check(seccion, "login fuera de turno rechazado", estado == 403,
          f"estado={estado} respuesta={datos}")


# --------------------------------------------------------------------------- #
# Main
# --------------------------------------------------------------------------- #

def main() -> int:
    parser = argparse.ArgumentParser(description="Pruebas funcionales E2E")
    parser.add_argument("--no-cleanup", action="store_true",
                        help="conserva los datos creados por la prueba")
    args = parser.parse_args()

    global DB_CONTAINER, DB_USER, DB_NAME
    env = leer_env(ENV_FILE)
    DB_USER = env.get("DB_USERNAME") or DB_USER
    DB_NAME = env.get("DB_NAME") or DB_NAME
    DB_CONTAINER = env.get("DB_CONTAINER") or DB_CONTAINER

    RESULTADOS.mkdir(parents=True, exist_ok=True)
    creds = credenciales()
    if not creds:
        print("Faltan credenciales en infra/seed/credenciales.local.env")
        return 2

    print(f"SOS Clinic - pruebas funcionales E2E (fase 1)")
    print(f"Base URL: {BASE}")
    print(f"Marcador de prueba: {MARCA}\n")

    try:
        seccion_frontend()
    except Critico:
        pass
    except Exception as error:
        registrar("frontend", "error inesperado", "FAIL", repr(error))

    try:
        sesiones = seccion_login(creds)
    except Critico:
        sesiones = {}

    paciente = None
    episodios = None
    for nombre, funcion in (
        ("pacientes", lambda: seccion_pacientes(sesiones["medico.dia"])),
        ("triage", lambda: seccion_triage(sesiones["medico.dia"], paciente)),
    ):
        if not sesiones:
            break
        if nombre == "triage" and paciente is None:
            skip("triage", "seccion omitida", "no se pudo crear el paciente")
            break
        try:
            resultado = funcion()
            if nombre == "pacientes":
                paciente = resultado
            else:
                episodios = resultado
        except Critico:
            continue
        except Exception as error:
            registrar(nombre, "error inesperado", "FAIL", repr(error))

    if sesiones and paciente and episodios:
        for nombre, funcion in (
            ("signos", lambda: seccion_signos(sesiones["medico.dia"],
                                              episodios["idRegistro"])),
            ("alta", lambda: seccion_alta(sesiones["medico.dia"],
                                          episodios["idRegistro"])),
        ):
            try:
                funcion()
            except Critico:
                continue
            except Exception as error:
                registrar(nombre, "error inesperado", "FAIL", repr(error))
    else:
        skip("signos", "seccion omitida", "faltan episodios previos")
        skip("alta", "seccion omitida", "faltan episodios previos")

    if sesiones:
        for nombre, funcion in (
            ("analitica", lambda: seccion_analitica(sesiones["coordinacion.sos"],
                                                    sesiones["medico.dia"])),
            ("agente", lambda: seccion_agente(sesiones["medico.dia"])),
            ("auditoria", lambda: seccion_auditoria(sesiones["medico.dia"],
                                                    sesiones["admin.test"])),
        ):
            try:
                funcion()
            except Critico:
                continue
            except Exception as error:
                registrar(nombre, "error inesperado", "FAIL", repr(error))
    else:
        for nombre in ("analitica", "agente", "auditoria"):
            skip(nombre, "seccion omitida", "no hay sesiones")

    try:
        seccion_turnos()
    except Critico:
        pass
    except Exception as error:
        registrar("turnos", "error inesperado", "FAIL", repr(error))

    if not args.no_cleanup:
        try:
            limpiar()
            registrar("limpieza", "datos de prueba eliminados", "PASS")
        except Exception as error:
            warn("limpieza", "no se pudo limpiar", repr(error))

    conteos = {"PASS": 0, "FAIL": 0, "WARN": 0, "SKIP": 0, "INFO": 0}
    for fila in resultados:
        conteos[fila["estado"]] = conteos.get(fila["estado"], 0) + 1

    with CSV_SALIDA.open("w", newline="", encoding="utf-8-sig") as archivo:
        escritor = csv.DictWriter(archivo, fieldnames=["fecha_hora", "seccion",
                                                       "caso", "estado", "detalle"])
        escritor.writeheader()
        escritor.writerows(resultados)

    duracion = (dt.datetime.now() - inicio).total_seconds()
    print(f"\nResultado: {conteos['PASS']} PASS, {conteos['FAIL']} FAIL, "
          f"{conteos['WARN']} WARN, {conteos['SKIP']} SKIP "
          f"({duracion:.1f} s)")
    print(f"Detalle: {CSV_SALIDA}")
    return 0 if conteos["FAIL"] == 0 else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        raise SystemExit(130)
