/*
 * Exporta el HTML del informe a PDF con Edge (Playwright).
 */
const { chromium } = require("playwright");
const path = require("path");

(async () => {
  const html = path.resolve("seguridad/informe/informe-seguridad.html");
  const pdf = path.resolve("seguridad/informe/informe-seguridad.pdf");
  const url = `file:///${html.replace(/\\/g, "/").replace(/ /g, "%20")}`;

  const navegador = await chromium.launch({ channel: "msedge", headless: true });
  const pagina = await navegador.newPage();
  await pagina.goto(url, { waitUntil: "networkidle", timeout: 60000 });
  await pagina.waitForTimeout(1000);
  await pagina.pdf({
    path: pdf,
    format: "A4",
    printBackground: true,
    margin: { top: "16mm", bottom: "18mm", left: "14mm", right: "14mm" },
    displayHeaderFooter: true,
    headerTemplate: "<div></div>",
    footerTemplate:
      '<div style="width:100%;font-size:8px;color:#64748b;text-align:center;">' +
      "SOS Clinic · Informe de pruebas de seguridad · " +
      '<span class="pageNumber"></span>/<span class="totalPages"></span></div>',
  });
  console.log("PDF:", pdf);
  await navegador.close();
})();
