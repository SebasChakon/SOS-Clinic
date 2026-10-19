/*
 * Captura el XSS almacenado en la descripcion de sintoma de una toma de
 * signos vitales (seguimiento.html?idRegistro=61).
 *
 * Requiere SOS_TOKEN con un JWT valido y el episodio 61 con el payload
 * <img src=x onerror=alert(1)> en descripcion_sintoma.
 */
const { chromium } = require("playwright");

const token = process.env.SOS_TOKEN;
if (!token) {
  console.error("Falta la variable SOS_TOKEN");
  process.exit(2);
}

(async () => {
  const navegador = await chromium.launch({ channel: "msedge", headless: true });
  const pagina = await navegador.newPage({ viewport: { width: 1100, height: 900 } });
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

  await pagina.goto("http://localhost:5500/seguimiento.html?idRegistro=61", {
    waitUntil: "networkidle",
    timeout: 30000,
  });
  await pagina.waitForTimeout(2500);

  const historial = pagina.locator("#lineaTiempo");
  if (await historial.count()) {
    await historial.screenshot({
      path: "seguridad/evidencias/15b_xss_seguimiento.png",
    });
  }

  console.log(JSON.stringify({ dialogos }, null, 2));
  await navegador.close();
})();
