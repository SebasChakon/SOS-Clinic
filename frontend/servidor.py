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
    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
    "img-src 'self' data: https:; "
    "font-src 'self' data: https://fonts.gstatic.com; "
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

    def do_GET(self):
        # La landing antigua redirige de forma permanente al índice actual.
        if self.path.split("?", 1)[0] == "/landing.html":
            self.send_response(301)
            self.send_header("Location", "/index.html")
            self.end_headers()
            return
        super().do_GET()

    def end_headers(self):
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("X-Frame-Options", "DENY")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("Permissions-Policy", "camera=(), microphone=(self), geolocation=(self)")
        self.send_header("Content-Security-Policy", CSP)
        self.send_header("Cross-Origin-Opener-Policy", "same-origin")
        self.send_header("Cross-Origin-Resource-Policy", "same-site")

        ruta = self.path.split("?", 1)[0]
        if ruta.endswith((".css", ".js", ".ico", ".png", ".jpg", ".jpeg", ".svg", ".woff2", ".mp4")):
            self.send_header("Cache-Control", "public, max-age=3600")
        elif ruta.endswith(".html") or ruta == "/":
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
