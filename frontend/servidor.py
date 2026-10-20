#!/usr/bin/env python3
"""Servidor estatico de SOS Clinic con cabeceras de seguridad.

Uso:
    python servidor.py [puerto]

Escucha solo en 127.0.0.1 para no exponer el frontend en la red local.
La CSP permite los recursos externos que ya usa la interfaz (YouTube y el
reporte de Power BI) y mantiene bloqueado el resto.
"""

import os
import sys
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

CSP = (
    "default-src 'self'; "
    "script-src 'self' 'unsafe-inline' https://www.youtube.com; "
    "style-src 'self' 'unsafe-inline'; "
    "img-src 'self' data: https:; "
    "font-src 'self' data:; "
    "connect-src 'self' http://localhost:8080 http://127.0.0.1:8080; "
    "frame-src https://www.youtube.com https://app.powerbi.com; "
    "media-src 'self' blob:; "
    "object-src 'none'; "
    "base-uri 'self'; "
    "frame-ancestors 'none'; "
    "form-action 'self'"
)


class Handler(SimpleHTTPRequestHandler):
    server_version = "SOS-Clinic"
    sys_version = ""

    def end_headers(self):
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("X-Frame-Options", "DENY")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("Permissions-Policy", "camera=(), microphone=(self), geolocation=(self)")
        self.send_header("Content-Security-Policy", CSP)
        self.send_header("Cross-Origin-Opener-Policy", "same-origin")
        self.send_header("Cross-Origin-Resource-Policy", "same-site")
        if self.path.endswith(".html") or self.path == "/":
            self.send_header("Cache-Control", "no-store")
        super().end_headers()


def main() -> int:
    puerto = 5500
    if len(sys.argv) > 1:
        puerto = int(sys.argv[1])

    os.chdir(os.path.dirname(os.path.abspath(__file__)))
    servidor = ThreadingHTTPServer(("127.0.0.1", puerto), Handler)
    print(f"Frontend SOS Clinic en http://127.0.0.1:{puerto}")
    try:
        servidor.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        servidor.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
