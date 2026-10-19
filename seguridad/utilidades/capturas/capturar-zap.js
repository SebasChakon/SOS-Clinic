/*
 * Captura la parte superior del reporte HTML de ZAP para anexarlo al informe.
 */
const { chromium } = require("playwright");
const path = require("path");

(async () => {
  const archivo = path.resolve(
    "seguridad/resultados/zap-baseline-frontend-2026-10-19.html"
  );
  const navegador = await chromium.launch({ channel: "msedge", headless: true });
  const pagina = await navegador.newPage({ viewport: { width: 1400, height: 1000 } });
  await pagina.goto(`file:///${archivo.replace(/\\/g, "/").replace(/ /g, "%20")}`, {
    waitUntil: "load",
    timeout: 30000,
  });
  await pagina.waitForTimeout(1000);
  await pagina.screenshot({
    path: "seguridad/evidencias/21_zap_baseline.png",
    fullPage: false,
  });
  console.log("OK seguridad/evidencias/21_zap_baseline.png");
  await navegador.close();
})();
