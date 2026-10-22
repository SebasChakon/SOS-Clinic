#!/usr/bin/env bash
# Compila los informes de la ronda 3 dentro de un contenedor TeX Live.
# Se ejecuta desde la raiz del repositorio montado en /work:
#   docker run --rm --entrypoint bash \
#     -v E:\PROGRA~1\GIT\SOS-CL~1:/work \
#     texlive/texlive:latest \
#     /work/seguridad/informes-ronda3/compilar-docker.sh
set -euo pipefail

BASE=/work/seguridad/informes-ronda3
SALIDA="$BASE/pdf"
mkdir -p "$SALIDA"

fallos=0
for dir in funcionales maven sast pentest dast; do
  for tex in "$BASE/$dir"/*.tex; do
    [ -e "$tex" ] || continue
    nombre=$(basename "$tex" .tex)
    build="/tmp/build/$dir/$nombre"
    mkdir -p "$build"
    echo "==> $dir/$nombre"

    if ! (cd "$BASE/$dir" && pdflatex -interaction=nonstopmode -halt-on-error \
            -output-directory="$build" "$nombre.tex" > "$build/pass1.log" 2>&1); then
      echo "    FALLO en la primera pasada:"
      tail -n 30 "$build/pass1.log" || true
      fallos=$((fallos + 1))
      continue
    fi

    if ! (cd "$BASE/$dir" && pdflatex -interaction=nonstopmode -halt-on-error \
            -output-directory="$build" "$nombre.tex" > "$build/pass2.log" 2>&1); then
      echo "    FALLO en la segunda pasada:"
      tail -n 30 "$build/pass2.log" || true
      fallos=$((fallos + 1))
      continue
    fi

    cp "$build/$nombre.pdf" "$SALIDA/$nombre.pdf"
    echo "    OK ($(stat -c%s "$SALIDA/$nombre.pdf") bytes)"
  done
done

echo ""
echo "PDF generados en $SALIDA:"
ls -1 "$SALIDA" || true
echo ""
if [ "$fallos" -ne 0 ]; then
  echo "Documentos con fallo: $fallos"
  exit 1
fi
echo "Compilacion completa."
