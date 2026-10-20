# Informe ejecutivo de seguridad — SOS Clinic

Proyecto LaTeX autocontenido con el informe ejecutivo de las pruebas de seguridad
de SOS Clinic, rama `pruebas-seguridad-2`, y el estado de remediación en `main`.

Autores: Sebastian Chacón Mantilla, Oscar Castellanos López y Santiago Ramírez
Cubillos. Universidad de San Buenaventura, Ingeniería de Sistemas, Sistemas de
Gestión de la Seguridad.

## Contenido

- `main.tex`: documento principal.
- `preambulo.tex`: paquetes, colores, estilos y comandos.
- `secciones/`: capítulos del informe.
- `figuras/`: 16 capturas de evidencia descargadas de la rama.
- `main.pdf`: PDF compilado (se genera al compilar).

## Compilar con TeX Live en Docker

Desde esta carpeta:

```powershell
docker run --rm -v "${PWD}:/work" -w /work texlive/texlive:latest `
  latexmk -pdf -interaction=nonstopmode -halt-on-error main.tex
```

Si el equipo no tiene Docker, se puede subir la carpeta completa a Overleaf y
compilar con pdfLaTeX. No se requieren fuentes del sistema.

## Compilar en local

Con TeX Live o MiKTeX instalados:

```powershell
latexmk -pdf main.tex
```

## Notas

- El documento se basa en el informe y los resultados ya documentados en la rama
  `pruebas-seguridad-2`; no se ejecutó un pentest nuevo.
- Incluye la sección 7, con el estado de remediación tomado de
  `seguridad/REMEDIACION-2026-10-19.md`.
- Las capturas son evidencia saneada: no incluyen tokens, contraseñas ni datos
  personales reales.
- La portada ya está configurada con los autores, la institución, el programa, la
  asignatura y el docente; se puede editar en `main.tex`.
- Repositorio fuente: <https://gitlab.com/SebasChakon/sos-clinic/-/tree/pruebas-seguridad-2>
