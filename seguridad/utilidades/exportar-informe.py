#!/usr/bin/env python3
"""
Convierte el informe Markdown en HTML listo para imprimir a PDF.
No requiere dependencias fuera de `markdown` (pip install markdown).
"""

import pathlib
import sys

import markdown


CSS = """
@page { size: A4; margin: 16mm 14mm 18mm 14mm; }
* { box-sizing: border-box; }
body {
  font-family: "Segoe UI", Arial, sans-serif;
  font-size: 10.5pt;
  line-height: 1.5;
  color: #1e293b;
  max-width: 100%;
}
h1 { font-size: 24pt; color: #0f766e; border-bottom: 3px solid #0f766e; padding-bottom: 8px; }
h2 { font-size: 16pt; color: #0f172a; margin-top: 26px; border-bottom: 1px solid #cbd5e1; padding-bottom: 4px; }
h3 { font-size: 13pt; color: #0f766e; margin-top: 20px; }
table { border-collapse: collapse; width: 100%; margin: 12px 0; font-size: 9pt; }
th { background: #0f766e; color: #fff; text-align: left; padding: 7px 8px; }
td { border: 1px solid #cbd5e1; padding: 6px 8px; vertical-align: top; }
tr:nth-child(even) td { background: #f8fafc; }
code { font-family: Consolas, monospace; background: #f1f5f9; padding: 1px 4px; border-radius: 3px; font-size: 9.5pt; }
pre { background: #0f172a; color: #e2e8f0; padding: 12px 14px; border-radius: 6px; overflow-x: auto; font-size: 8.5pt; }
pre code { background: transparent; color: inherit; padding: 0; }
blockquote { border-left: 4px solid #0f766e; background: #f0fdfa; margin: 14px 0; padding: 10px 14px; color: #134e4a; }
img { max-width: 100%; border: 1px solid #cbd5e1; border-radius: 5px; margin: 10px 0; }
hr { border: none; border-top: 1px solid #cbd5e1; margin: 22px 0; }
ul, ol { padding-left: 22px; }
li { margin: 4px 0; }
"""


def main() -> int:
    base = pathlib.Path(__file__).resolve().parents[2]
    origen = base / "seguridad" / "informe" / "informe-seguridad.md"
    destino = base / "seguridad" / "informe" / "informe-seguridad.html"

    if not origen.exists():
        print(f"No existe {origen}", file=sys.stderr)
        return 2

    texto = origen.read_text(encoding="utf-8")
    cuerpo = markdown.markdown(
        texto,
        extensions=["tables", "fenced_code", "sane_lists", "toc"],
    )

    html = f"""<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<title>Informe de pruebas de seguridad — SOS Clinic</title>
<style>{CSS}</style>
</head>
<body>
{cuerpo}
</body>
</html>
"""

    destino.write_text(html, encoding="utf-8")
    print(f"HTML: {destino}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
