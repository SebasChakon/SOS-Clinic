#!/usr/bin/env python3
"""
Reset y seed canonico de la base PostgreSQL local de SOS Clinic.

Uso (desde la raiz del repo):
  python infra/seed/reset_seed.py
  python infra/seed/reset_seed.py --clean-volume --yes
  python infra/seed/reset_seed.py --regenerate-credentials

Los wrappers reset-seed.ps1 / reset-seed.sh llaman a este motor.
No imprime contrasenas ni tokens.
"""

from __future__ import annotations

import argparse
import os
import secrets
import string
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from usuarios import USERS  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
SEED_DIR = ROOT / "infra" / "seed"
ENV_FILE = ROOT / ".env"
CRED_ENV = SEED_DIR / "credenciales.local.env"
CRED_MD = SEED_DIR / "credenciales.local.md"
BACKUP_DIR = ROOT / "infra" / "backups"
COMPOSE_FILE = ROOT / "infra" / "docker-compose.yml"

ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!?#$%.*@_+-"
SIMBOLOS = set("!?#$%.*@_+-")


def load_env() -> dict[str, str]:
    if not ENV_FILE.exists():
        raise SystemExit(f"No existe {ENV_FILE}. Copia el archivo .env del paquete de preparacion en la raiz del repositorio.")
    valores: dict[str, str] = {}
    for linea in ENV_FILE.read_text(encoding="utf-8").splitlines():
        linea = linea.strip()
        if not linea or linea.startswith("#") or "=" not in linea:
            continue
        clave, valor = linea.split("=", 1)
        valor = valor.strip()
        if len(valor) >= 2 and valor[0] == valor[-1] and valor[0] in ("'", '"'):
            valor = valor[1:-1]
        valores[clave.strip()] = valor
    return valores


def read_credentials() -> dict[str, str]:
    valores: dict[str, str] = {}
    if not CRED_ENV.exists():
        return valores
    for linea in CRED_ENV.read_text(encoding="utf-8").splitlines():
        linea = linea.strip()
        if not linea or linea.startswith("#") or "=" not in linea:
            continue
        clave, valor = linea.split("=", 1)
        valor = valor.strip()
        if len(valor) >= 2 and valor[0] == valor[-1] and valor[0] in ("'", '"'):
            valor = valor[1:-1]
        valores[clave.strip()] = valor
    return valores


def valida_password(valor: str) -> bool:
    if not (8 <= len(valor) <= 64):
        return False
    if any(c not in ALFABETO for c in valor):
        return False
    return (
        any(c.isupper() for c in valor)
        and any(c.islower() for c in valor)
        and any(c.isdigit() for c in valor)
        and any(c in SIMBOLOS for c in valor)
    )


def genera_password(usadas: set[str]) -> str:
    while True:
        valor = "".join(secrets.choice(ALFABETO) for _ in range(24))
        if valor not in usadas and valida_password(valor):
            return valor


def asegura_credenciales(regenerar: bool = False) -> dict[str, str]:
    existentes = {} if regenerar else read_credentials()
    credenciales: dict[str, str] = {}
    usadas: set[str] = set()

    for usuario in USERS:
        clave = usuario["clave_env"]
        valor = existentes.get(clave, "").strip()
        if not valor or not valida_password(valor) or valor in usadas:
            valor = genera_password(usadas)
        credenciales[clave] = valor
        usadas.add(valor)

    CRED_ENV.parent.mkdir(parents=True, exist_ok=True)
    lineas = [
        "# Generado por infra/seed/reset_seed.py.",
        "# Archivo local: no se versiona. No lo compartas.",
    ]
    lineas += [f"{u['clave_env']}='{credenciales[u['clave_env']]}'" for u in USERS]
    CRED_ENV.write_text("\n".join(lineas) + "\n", encoding="utf-8")

    markdown = [
        "# Credenciales locales del seed",
        "",
        "Archivo generado por `infra/seed/reset_seed.py`. No se versiona.",
        "El 2FA esta activo: el codigo de cada cuenta llega a `pruebasosclinic@gmail.com`",
        "(las direcciones usan plus-addressing y todas caen en esa bandeja).",
        "",
        "| Usuario | Perfil | Correo | Contrasena | 2FA |",
        "|---|---|---|---|---|",
    ]
    for u in USERS:
        markdown.append(
            f"| `{u['usuario']}` | {u['perfil']} | `{u['email']}` | "
            f"`{credenciales[u['clave_env']]}` | Si, codigo en `pruebasosclinic@gmail.com` |"
        )
    CRED_MD.write_text("\n".join(markdown) + "\n", encoding="utf-8")

    for ruta in (CRED_ENV, CRED_MD):
        try:
            os.chmod(ruta, 0o600)
        except OSError:
            pass

    print(f"Credenciales generadas/reutilizadas en:\n  {CRED_ENV}\n  {CRED_MD}")
    return credenciales


def ejecutar(comando: list[str], *, env: dict[str, str] | None = None,
             entrada: Path | None = None, mostrar: bool = False) -> subprocess.CompletedProcess:
    archivo = None
    try:
        if entrada is not None:
            archivo = open(entrada, "rb")
        resultado = subprocess.run(
            comando,
            stdin=archivo,
            capture_output=True,
            text=True,
            env=env,
            timeout=600,
        )
    finally:
        if archivo is not None:
            archivo.close()
    if resultado.returncode != 0:
        detalle = (resultado.stderr or resultado.stdout or "").strip()
        raise SystemExit(f"Fallo el comando: {' '.join(comando[:4])}\n{detalle}")
    if mostrar and resultado.stdout.strip():
        print(resultado.stdout.strip())
    return resultado


def contexto(env: dict[str, str]) -> tuple[str, str, str]:
    usuario = env.get("DB_USERNAME", "sos_admin") or "sos_admin"
    nombre = env.get("DB_NAME", "sosclinic") or "sosclinic"
    contenedor = env.get("DB_CONTAINER", "sos-postgres") or "sos-postgres"
    return usuario, nombre, contenedor


def psql(usuario: str, nombre: str, contenedor: str, archivo: Path,
         env: dict[str, str] | None = None) -> None:
    comando = [
        "docker", "exec", "-i", contenedor,
        "psql", "-U", usuario, "-d", nombre, "-v", "ON_ERROR_STOP=1",
    ]
    resultado = ejecutar(comando, env=env, entrada=archivo)
    if resultado.stdout.strip():
        print(resultado.stdout.strip())


def psql_query(usuario: str, nombre: str, contenedor: str, sql: str) -> str:
    resultado = subprocess.run(
        ["docker", "exec", contenedor, "psql", "-U", usuario, "-d", nombre, "-tAc", sql],
        capture_output=True, text=True, timeout=60,
    )
    return resultado.stdout.strip() if resultado.returncode == 0 else ""


def contenedor_arriba(contenedor: str) -> bool:
    resultado = subprocess.run(
        ["docker", "ps", "--filter", f"name=^{contenedor}$", "--format", "{{.Names}}"],
        capture_output=True, text=True, timeout=30,
    )
    return contenedor in resultado.stdout.splitlines()


def esperar_bd(usuario: str, nombre: str, contenedor: str, intentos: int = 90) -> None:
    for _ in range(intentos):
        if psql_query(usuario, nombre, contenedor,
                      "SELECT to_regclass('public.perfil') IS NOT NULL;") == "t":
            return
        time.sleep(1)
    raise SystemExit("La base no termino de inicializar (falta la tabla perfil).")


def backup(env: dict[str, str], usuario: str, nombre: str, contenedor: str) -> Path | None:
    BACKUP_DIR.mkdir(parents=True, exist_ok=True)
    sello = time.strftime("%Y%m%d-%H%M%S")
    temporal = f"/tmp/sosclinic-{sello}.dump"
    destino = BACKUP_DIR / f"sosclinic-{sello}.dump"
    try:
        ejecutar([
            "docker", "exec", contenedor, "sh", "-c",
            f"pg_dump -U '{usuario}' -d '{nombre}' -Fc -f '{temporal}'",
        ])
        ejecutar(["docker", "cp", f"{contenedor}:{temporal}", str(destino)])
        ejecutar(["docker", "exec", contenedor, "rm", "-f", temporal])
    except SystemExit as error:
        print(f"Aviso: no se pudo crear el backup antes del reset: {error}")
        return None
    print(f"Backup creado: {destino}")
    return destino


def main() -> int:
    parser = argparse.ArgumentParser(description="Reset y seed canonico de SOS Clinic")
    parser.add_argument("--clean-volume", action="store_true",
                        help="Elimina el volumen de PostgreSQL y reconstruye el esquema")
    parser.add_argument("--skip-backup", action="store_true", help="No crea backup (no recomendado)")
    parser.add_argument("--yes", action="store_true", help="No pide confirmacion")
    parser.add_argument("--regenerate-credentials", action="store_true",
                        help="Genera nuevas contrasenas para las 9 cuentas")
    args = parser.parse_args()

    env = load_env()
    usuario, nombre, contenedor = contexto(env)
    credenciales = asegura_credenciales(args.regenerate_credentials)

    if args.clean_volume:
        if not args.yes:
            respuesta = input(
                "Esto eliminara el volumen de PostgreSQL y TODOS sus datos. "
                "Se conservara un backup. Escribe 's' para continuar: "
            ).strip().lower()
            if respuesta not in ("s", "si", "sí", "y", "yes"):
                raise SystemExit("Cancelado.")
        if not args.skip_backup and contenedor_arriba(contenedor):
            backup(env, usuario, nombre, contenedor)
        print("Reconstruyendo el volumen de PostgreSQL...")
        ejecutar(["docker", "compose", "--env-file", str(ENV_FILE),
                  "-f", str(COMPOSE_FILE), "down", "-v"])
        ejecutar(["docker", "compose", "--env-file", str(ENV_FILE),
                  "-f", str(COMPOSE_FILE), "up", "-d", "postgres"])
    else:
        if not args.yes:
            respuesta = input(
                "Esto reemplazara los datos de aplicacion de la base local. "
                "Se conservara un backup. Escribe 's' para continuar: "
            ).strip().lower()
            if respuesta not in ("s", "si", "sí", "y", "yes"):
                raise SystemExit("Cancelado.")
        if not contenedor_arriba(contenedor):
            print("Levantando PostgreSQL...")
            ejecutar(["docker", "compose", "--env-file", str(ENV_FILE),
                      "-f", str(COMPOSE_FILE), "up", "-d", "postgres"])
        if not args.skip_backup:
            backup(env, usuario, nombre, contenedor)

    esperar_bd(usuario, nombre, contenedor)

    print("Aplicando migraciones...")
    for migracion in (
        "2026-10-19-seguridad.sql",
        "2026-10-19-actividades.sql",
        "2026-10-21-claves.sql",
        "2026-10-22-seed-drift.sql",
    ):
        ruta = ROOT / "infra" / "migraciones" / migracion
        if not ruta.exists():
            raise SystemExit(f"Falta la migracion {ruta}")
        print(f"  - {migracion}")
        psql(usuario, nombre, contenedor, ruta)

    print("Limpiando datos de aplicacion...")
    psql(usuario, nombre, contenedor, SEED_DIR / "reset.sql")

    print("Cargando seed canonico...")
    entorno = os.environ.copy()
    entorno["PGCLIENTENCODING"] = "UTF8"
    comando = ["docker", "exec", "-i", "-e", "PGCLIENTENCODING=UTF8"]
    for u in USERS:
        entorno[u["clave_env"]] = credenciales[u["clave_env"]]
        comando += ["-e", u["clave_env"]]
    comando += [contenedor, "psql", "-U", usuario, "-d", nombre, "-v", "ON_ERROR_STOP=1"]
    resultado = ejecutar(comando, env=entorno, entrada=SEED_DIR / "seed.sql")
    if resultado.stdout.strip():
        print(resultado.stdout.strip())

    print("Verificando conteos...")
    psql(usuario, nombre, contenedor, SEED_DIR / "verify.sql")

    print("\nSeed completado.")
    print(f"Credenciales: {CRED_MD}")
    print(f"Backups:      {BACKUP_DIR}")
    print("Turnos 24h: las 9 cuentas (maestra: admin.test)")
    print("2FA activo: los codigos llegan a pruebasosclinic@gmail.com")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        raise SystemExit(130)
