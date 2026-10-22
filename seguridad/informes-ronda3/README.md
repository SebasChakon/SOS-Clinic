# Documentación de la ronda 3 de pruebas — SOS Clinic

Esta carpeta reúne la documentación de las pruebas ejecutadas en la rama
`pruebas-seguridad-3` (22 de octubre de 2026). Cada prueba tiene **dos
documentos LaTeX** (metodología y resultados) y **un resumen Markdown** para
presentación.

## Índice

| Prueba | Metodología (LaTeX) | Resultados (LaTeX) | Resumen (MD) |
|---|---|---|---|
| Funcionales E2E | `funcionales/funcionales-metodologia.tex` | `funcionales/funcionales-resultados.tex` | `funcionales/funcionales-resumen.md` |
| Maven / unitarias | `maven/maven-metodologia.tex` | `maven/maven-resultados.tex` | `maven/maven-resumen.md` |
| SAST / SCA | `sast/sast-metodologia.tex` | `sast/sast-resultados.tex` | `sast/sast-resumen.md` |
| Pentest local | `pentest/pentest-metodologia.tex` | `pentest/pentest-resultados.tex` | `pentest/pentest-resumen.md` |
| DAST (ZAP) | `dast/dast-metodologia.tex` | `dast/dast-resultados.tex` | `dast/dast-resumen.md` |

## Resumen general

| Prueba | Resultado principal |
|---|---|
| Funcionales | 74 PASS, 0 FAIL, 1 WARN; 47/47 en claves; 9/9 cuentas del seed |
| Maven | 7/7 módulos, 49 pruebas, 0 fallos |
| SAST/SCA | 0 secretos vigentes, 0 vulnerabilidades en dependencias; 9 falsos positivos en scripts |
| Pentest | 62 PASS, 2 FAIL (validación 500), 3 WARN (PDF por rol y CSP) |
| DAST | 62 PASS, 0 FAIL, 6 WARN informativas |

## Compilación

Los archivos `.tex` usan un preámbulo común en `comun/preambulo.tex`. Los PDF ya
están generados en `pdf/` con la imagen Docker oficial de TeX Live:

```powershell
docker run --rm --entrypoint bash `
  -v E:\PROGRA~1\GIT\SOS-CL~1:/work `
  texlive/texlive:latest `
  /work/seguridad/informes-ronda3/compilar-docker.sh
```

También se puede compilar con una instalación local de TeX Live o MiKTeX:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File seguridad\informes-ronda3\compilar.ps1
```

`compilar-docker.sh` compila cada documento dos veces (para índices y
referencias) y copia los PDF a `pdf/`. `compilar.ps1` hace lo mismo usando el
`pdflatex` local.

## PDF generados

- `pdf/funcionales-metodologia.pdf` y `pdf/funcionales-resultados.pdf`
- `pdf/maven-metodologia.pdf` y `pdf/maven-resultados.pdf`
- `pdf/sast-metodologia.pdf` y `pdf/sast-resultados.pdf`
- `pdf/pentest-metodologia.pdf` y `pdf/pentest-resultados.pdf`
- `pdf/dast-metodologia.pdf` y `pdf/dast-resultados.pdf`

## Evidencia de resultados

Los datos citados provienen de:

- `../resultados/fase1-funcional.csv` y `fase1-claves.txt`
- `../resultados/maven/resumen.md`
- `../resultados/gitleaks-*.json`, `semgrep-r3.json`, `trivy-*.json`
- `../resultados/fase2-pentest.csv`
- `../resultados/zap-baseline-r3.json`
- `../evidencias/xss-*-r3.png`
