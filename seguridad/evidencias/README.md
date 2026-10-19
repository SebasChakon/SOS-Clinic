# Evidencias

Capturas de pantalla y evidencia visual de cada caso. Una imagen por hallazgo o
por prueba relevante.

## Convencion de nombres

```text
NN_descripcion_corta.png
```

Ejemplos:

```text
01_login_credenciales_invalidas.png
02_pacientes_sin_token_200.png
07_jwt_forjado_admin_200.png
12_idor_historia_ajena.png
15_xss_auditoria.png
```

El prefijo `NN` debe coincidir con el ID del caso en la matriz de pruebas.

## Que debe mostrar cada captura

- **API:** metodo, URL, cabeceras relevantes, cuerpo de la solicitud y codigo
  de respuesta. Postman o DevTools son suficientes.
- **Escaneos:** comando ejecutado, version de la herramienta, fecha y resumen.
- **Frontend/XSS:** payload visible en el campo y ejecucion en el navegador.
- **Antes/Despues:** par de capturas con el mismo encuadre.

## Lista de verificacion antes de guardar

- [ ] Tokens y cookies difuminados.
- [ ] Contrasenas y codigos 2FA ocultos.
- [ ] Cedulas, nombres, correos y telefonos difuminados.
- [ ] Sin secretos en la barra de direccion ni en la consola.
- [ ] Fecha y commit visibles o anotados en el informe.
- [ ] Archivo en PNG o JPG, no capturas de pantalla completas del escritorio.

## Regla

Si una captura no se puede sanear, no se versiona: se describe el resultado en
el informe y se conserva la evidencia local fuera del repositorio.
