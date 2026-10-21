#!/usr/bin/env python3
"""
Verifica el seed por API: login completo de las 9 cuentas (con un codigo 2FA
temporal), perfil en el token y un endpoint representativo por rol.

Requiere el stack levantado (Eureka, gateway, ms-seguridad, ms-pacientes,
ms-triage) y que el seed se haya ejecutado.

Uso:
  python infra/seed/verify_seed.py
  python infra/seed/verify_seed.py --base-url http://127.0.0.1:8080

No imprime contrasenas ni tokens.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from usuarios import USERS  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
ENV_FILE = ROOT / ".env"
CRED_ENV = ROOT / "infra" / "seed" / "credenciales.local.env"
VERIFY_SQL = ROOT / "infra" / "seed" / "verify.sql"


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


def http(metodo: str, base: str, ruta: str, cuerpo=None, token=None):
    datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
    peticion = urllib.request.Request(base.rstrip("/") + ruta, data=datos, method=metodo)
    peticion.add_header("Content-Type", "application/json")
    if token:
        peticion.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(peticion, timeout=60) as respuesta:
            texto = respuesta.read().decode()
            return respuesta.status, json.loads(texto) if texto else None
    except urllib.error.HTTPError as error:
        texto = error.read().decode()
        try:
            return error.code, json.loads(texto) if texto else None
        except Exception:
            return error.code, texto


def main() -> int:
    parser = argparse.ArgumentParser(description="Verificacion API del seed")
    parser.add_argument("--base-url", default=os.environ.get("E2E_BASE_URL", "http://127.0.0.1:8080"))
    args = parser.parse_args()

    env = leer_env(ENV_FILE)
    credenciales = leer_env(CRED_ENV)
    usuario_db = env.get("DB_USERNAME", "sos_admin") or "sos_admin"
    nombre_db = env.get("DB_NAME", "sosclinic") or "sosclinic"
    contenedor = env.get("DB_CONTAINER", "sos-postgres") or "sos-postgres"

    faltan = [u["clave_env"] for u in USERS if not credenciales.get(u["clave_env"])]
    if faltan:
        raise SystemExit("Faltan credenciales en infra/seed/credenciales.local.env: " + ", ".join(faltan))

    def psql(sql: str) -> str:
        resultado = subprocess.run(
            ["docker", "exec", contenedor, "psql", "-U", usuario_db, "-d", nombre_db,
             "-v", "ON_ERROR_STOP=1", "-tAc", sql],
            capture_output=True, text=True, timeout=60,
        )
        if resultado.returncode != 0:
            raise RuntimeError(resultado.stderr.strip())
        return resultado.stdout.strip()

    usuarios_sql = ",".join(repr(u["usuario"]) for u in USERS)

    def limpiar_temporales() -> None:
        print("Limpiando codigos temporales...")
        try:
            psql(f"DELETE FROM codigo_verificacion WHERE id_personal IN "
                 f"(SELECT id_personal FROM personal WHERE usuario IN ({usuarios_sql}));")
        except Exception as error:
            print(f"Aviso: no se pudo limpiar: {error}")

    try:
        for u in USERS:
            clave = credenciales[u["clave_env"]]
            estado, datos = http("POST", args.base_url, "/ms-seguridad/auth/login",
                                 {"usuario": u["usuario"], "clave": clave})
            if estado != 200 or not isinstance(datos, dict) or not datos.get("requiere2fa"):
                raise RuntimeError(f"{u['usuario']}: login inesperado ({estado})")
            challenge = datos.get("challenge")
            if not challenge:
                raise RuntimeError(f"{u['usuario']}: no se recibio challenge 2FA")

            challenge_hash = hashlib.sha256(challenge.encode()).hexdigest()
            actualizados = psql(
                "UPDATE codigo_verificacion "
                "SET codigo = crypt('123456', gen_salt('bf', 12)) "
                f"WHERE challenge_hash = '{challenge_hash}' AND usado = FALSE;"
            )
            if "UPDATE 1" not in actualizados:
                raise RuntimeError(f"{u['usuario']}: no se encontro el codigo 2FA pendiente")

            estado, sesion = http("POST", args.base_url, "/ms-seguridad/auth/verificar-2fa",
                                  {"challenge": challenge, "codigo": "123456"})
            if estado != 200 or not isinstance(sesion, dict) or not sesion.get("token"):
                raise RuntimeError(f"{u['usuario']}: fallo la verificacion 2FA ({estado})")
            if sesion.get("perfil") != u["perfil"]:
                raise RuntimeError(
                    f"{u['usuario']}: perfil {sesion.get('perfil')} != {u['perfil']}")
            token = sesion["token"]

            estado, cuenta = http("GET", args.base_url, "/ms-seguridad/auth/mi-cuenta", token=token)
            if estado != 200 or not isinstance(cuenta, dict) or cuenta.get("usuario") != u["usuario"]:
                raise RuntimeError(f"{u['usuario']}: mi-cuenta fallo ({estado})")

            estado, _ = http("GET", args.base_url, u["endpoint"], token=token)
            if estado != 200:
                raise RuntimeError(
                    f"{u['usuario']}: {u['endpoint']} devolvio {estado} (revisa roles)")

            print(f"PASS  {u['usuario']:16} perfil={u['perfil']:20} endpoint={u['endpoint']}")

        limpiar_temporales()

        print("\nVerificando conteos finales...")
        resultado = subprocess.run(
            ["docker", "exec", "-i", contenedor, "psql", "-U", usuario_db, "-d", nombre_db,
             "-v", "ON_ERROR_STOP=1"],
            stdin=open(VERIFY_SQL, "rb"), capture_output=True, text=True, timeout=60,
        )
        if resultado.returncode != 0:
            raise RuntimeError(resultado.stderr.strip())
        print(resultado.stdout.strip())
        print("\nVERIFICACION COMPLETA OK")
        return 0
    finally:
        limpiar_temporales()


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        raise SystemExit(130)
