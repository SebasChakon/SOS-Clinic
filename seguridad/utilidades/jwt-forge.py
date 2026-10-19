#!/usr/bin/env python3
"""
Genera un JWT HS256 para pruebas de seguridad autorizadas en SOS Clinic.

No requiere dependencias externas. El secreto se pasa por argumento o por la
variable de entorno APP_JWT_SECRET; nunca se escribe en el repositorio.

Uso:
    python jwt-forge.py --usuario admin --perfil administrador --id-personal 1 \
        --secret "secreto-local" --exp-minutes 60

Salida:
    El token en stdout (listo para usar en la cabecera Authorization).
"""

import argparse
import base64
import hashlib
import hmac
import json
import os
import sys
import time


def base64url(datos: bytes) -> str:
    return base64.urlsafe_b64encode(datos).rstrip(b"=").decode("ascii")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Genera un JWT HS256 para pruebas locales de SOS Clinic."
    )
    parser.add_argument("--usuario", default="usuario-prueba", help="Claim sub")
    parser.add_argument("--perfil", default="administrador", help="Claim perfil")
    parser.add_argument(
        "--id-personal", type=int, default=1, help="Claim idPersonal"
    )
    parser.add_argument(
        "--secret",
        default=os.environ.get("APP_JWT_SECRET", ""),
        help="Secreto HS256 (o variable APP_JWT_SECRET)",
    )
    parser.add_argument(
        "--exp-minutes",
        type=int,
        default=60,
        help="Minutos de vigencia del token (por defecto 60)",
    )
    parser.add_argument(
        "--sin-exp",
        action="store_true",
        help="Genera el token sin claim exp (para probar validacion)",
    )
    args = parser.parse_args()

    if not args.secret:
        print(
            "ERROR: falta --secret o la variable APP_JWT_SECRET.",
            file=sys.stderr,
        )
        return 2

    ahora = int(time.time())
    header = {"alg": "HS256", "typ": "JWT"}
    payload = {
        "sub": args.usuario,
        "idPersonal": args.id_personal,
        "perfil": args.perfil,
        "iat": ahora,
    }
    if not args.sin_exp:
        payload["exp"] = ahora + args.exp_minutes * 60

    header_b64 = base64url(
        json.dumps(header, separators=(",", ":")).encode("utf-8")
    )
    payload_b64 = base64url(
        json.dumps(payload, separators=(",", ":")).encode("utf-8")
    )
    firma = hmac.new(
        args.secret.encode("utf-8"),
        f"{header_b64}.{payload_b64}".encode("ascii"),
        hashlib.sha256,
    ).digest()

    print(f"{header_b64}.{payload_b64}.{base64url(firma)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
