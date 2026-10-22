# Compila los informes LaTeX de la ronda 3 a PDF.
# Requiere pdflatex (TeX Live o MiKTeX) en PATH.
$ErrorActionPreference = 'Stop'
$Raiz = Split-Path -Parent $PSScriptRoot
$Salida = Join-Path $Raiz 'pdf'
New-Item -ItemType Directory -Force -Path $Salida | Out-Null

$pdfLatex = Get-Command pdflatex -ErrorAction SilentlyContinue
if (-not $pdfLatex) {
    Write-Host "No se encontro pdflatex. Instala TeX Live o MiKTeX y vuelve a intentarlo." -ForegroundColor Red
    exit 1
}

$documentos = Get-ChildItem -Path $Raiz -Recurse -Filter '*.tex' |
    Where-Object { $_.DirectoryName -notlike '*\comun' }

$fallos = 0
foreach ($documento in $documentos) {
    Write-Host "==> $($documento.Name)"
    Push-Location $documento.DirectoryName
    try {
        & pdflatex -interaction=nonstopmode -halt-on-error $documento.Name | Out-Null
        & pdflatex -interaction=nonstopmode -halt-on-error $documento.Name | Out-Null
        if ($LASTEXITCODE -ne 0) {
            $fallos++
            Write-Host "    Fallo la compilacion" -ForegroundColor Red
        } else {
            $pdf = [System.IO.Path]::ChangeExtension($documento.FullName, '.pdf')
            if (Test-Path $pdf) {
                Copy-Item -Path $pdf -Destination $Salida -Force
                Write-Host "    PDF generado" -ForegroundColor Green
            }
        }
    } finally {
        Pop-Location
    }
}

Write-Host ""
if ($fallos -eq 0) {
    Write-Host "Todos los documentos se compilaron. PDF en $Salida" -ForegroundColor Green
} else {
    Write-Host "$fallos documento(s) fallaron." -ForegroundColor Red
}
exit $fallos
