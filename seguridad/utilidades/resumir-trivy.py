#!/usr/bin/env python3
"""
Resume un reporte JSON de Trivy (scanner vuln) en un CSV y cuenta hallazgos
por severidad. No imprime secretos: solo IDs de CVE, paquetes y versiones.

Uso:
    python resumir-trivy.py entrada.json salida.csv
"""

import collections
import csv
import json
import sys


def main() -> int:
    if len(sys.argv) != 3:
        print("Uso: python resumir-trivy.py <entrada.json> <salida.csv>",
              file=sys.stderr)
        return 2

    entrada, salida = sys.argv[1], sys.argv[2]
    with open(entrada, encoding="utf-8") as fh:
        datos = json.load(fh)

    filas = []
    for resultado in datos.get("Results", []):
        objetivo = resultado.get("Target", "")
        for vuln in resultado.get("Vulnerabilities") or []:
            filas.append({
                "Severidad": vuln.get("Severity", ""),
                "CVE": vuln.get("VulnerabilityID", ""),
                "Paquete": vuln.get("PkgName", ""),
                "Instalada": vuln.get("InstalledVersion", ""),
                "Corregida": vuln.get("FixedVersion", ""),
                "Modulo": objetivo,
                "Titulo": (vuln.get("Title") or "").replace("\n", " "),
            })

    # Deduplicar por CVE + paquete + version instalada (Trivy repite por modulo).
    vistas = set()
    unicas = []
    for fila in filas:
        clave = (fila["CVE"], fila["Paquete"], fila["Instalada"])
        if clave in vistas:
            continue
        vistas.add(clave)
        unicas.append(fila)

    orden = {"CRITICAL": 0, "HIGH": 1, "MEDIUM": 2, "LOW": 3, "UNKNOWN": 4}
    unicas.sort(key=lambda f: (orden.get(f["Severidad"], 9), f["CVE"],
                               f["Paquete"]))

    with open(salida, "w", newline="", encoding="utf-8") as fh:
        escritor = csv.DictWriter(
            fh,
            fieldnames=["Severidad", "CVE", "Paquete", "Instalada",
                        "Corregida", "Modulo", "Titulo"],
        )
        escritor.writeheader()
        escritor.writerows(unicas)

    conteo = collections.Counter(f["Severidad"] for f in unicas)
    print(f"Hallazgos unicos: {len(unicas)}")
    for severidad in ("CRITICAL", "HIGH", "MEDIUM", "LOW", "UNKNOWN"):
        print(f"  {severidad}: {conteo.get(severidad, 0)}")
    print(f"CSV: {salida}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
