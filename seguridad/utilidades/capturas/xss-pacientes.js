/*
 * Captura la ejecucion de un XSS almacenado en la tabla de pacientes.
 *
 * Requiere:
 *   - Frontend en http://localhost:5500
 *   - Gateway en http://localhost:8080
 *   - Un paciente creado con nombre <img src=x onerror=alert(1)>
 *   - Variable SOS_TOKEN con un JWT valido (se usa solo en memoria)
 *
 * Salida:
 *   - seguridad/evidencias/15_xss_pacientes.png
 *   - JSON en consola con los dialogos detectados
 */
const { chromium } = require("playwright");

const token = process.env.SOS_TOKEN;
if (!token) {
  console.error("Falta la variable SOS_TOKEN");
  process.exit(2);
}

(async () => {
  const navegador = await chromium.launch({ channel: "msedge", headless: true });
  const pagina = await navegador.newPage({ viewport: { width: 1440, height: 900 } });
  const dialogos = [];

  pagina.on("dialog", async (dialogo) => {
    dialogos.push({ tipo: dialogo.type(), mensaje: dialogo.message() });
    await dialogo.dismiss();
  });

  await pagina.addInitScript((t) => {
    sessionStorage.setItem("sos_token", t);
    sessionStorage.setItem("sos_usuario", "forged.xss");
    sessionStorage.setItem("sos_perfil", "administrador");
    sessionStorage.setItem("sos_id_personal", "999");
    sessionStorage.setItem("sos_actividades", "[]");
  }, token);

  await pagina.goto("http://localhost:5500/pacientes.html", {
    waitUntil: "networkidle",
    timeout: 30000,
  });
  await pagina.waitForTimeout(2000);

  // Sanear la tabla: enmascarar nombres y cedulas, excepto el payload XSS.
  await pagina.evaluate(() => {
    document.querySelectorAll("table tbody tr").forEach((fila) => {
      const celdas = fila.querySelectorAll("td");
      if (celdas.length >= 4) {
        if (!celdas[1].innerHTML.includes("onerror")) {
          celdas[1].textContent = "****";
        }
        celdas[2].textContent = "****";
      }
    });
  });

  await pagina.screenshot({
    path: "seguridad/evidencias/15_xss_pacientes.png",
    fullPage: true,
  });

  const filas = await pagina.locator("table tbody tr").count();
  console.log(JSON.stringify({ dialogos, filas }, null, 2));
  await navegador.close();
})();
