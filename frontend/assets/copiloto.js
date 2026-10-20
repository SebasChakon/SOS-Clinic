/* SOS Clinic · copiloto.js — SosIA, asistente flotante: chat + voz + control de pantalla */
(function () {
  if (typeof haySesion !== 'function') { console.warn('copiloto: api.js no está cargado antes que copiloto.js'); return; }
  if (!haySesion()) return;

  const CLAVE_PLAN = 'sos_copiloto_plan';
  const CLAVE_HIST = 'sos_copiloto_hist';
  const CRITICO = /alta|eliminar|desactivar|quitar|borrar/i;

  let historial = JSON.parse(sessionStorage.getItem(CLAVE_HIST) || '[]');
  let cancelado = false;

  // estado de voz
  let grabando = false;
  let detener = null;
  let descartarAudio = false;
  let temporizadorEnvio = null;
  let vinoPorVoz = false;

  const norm = (s) => (s || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
  const esperar = (ms) => new Promise((r) => setTimeout(r, ms));

  /* ---------- interfaz ---------- */
  const estilo = document.createElement('style');
  estilo.textContent = `
    #sos-copiloto{position:fixed;right:20px;bottom:20px;z-index:9999}
    #sos-copiloto .cop-boton{min-height:44px;background:var(--acento);color:#fff;border-radius:999px;padding:10px 20px;font-family:var(--fuente);font-size:14px;letter-spacing:0;animation:copFlota 3.2s ease-in-out infinite alternate}
    #sos-copiloto .cop-boton:hover{background:var(--acento-hover)}
    #sos-copiloto .cop-panel{position:absolute;right:0;bottom:56px;width:min(360px,calc(100vw - 24px));height:min(460px,calc(100vh - 100px));background:#fff;border:1px solid var(--liquen);border-radius:16px;display:flex;flex-direction:column;overflow:hidden;box-shadow:0 18px 44px rgba(34,47,48,.22);animation:copAparece .3s cubic-bezier(.2,.7,.3,1) both}
    #sos-copiloto .cop-panel[hidden]{display:none}
    .cop-cabecera{background:var(--tinta);color:#fff;padding:12px 14px;display:flex;justify-content:space-between;align-items:center;gap:8px;font-family:var(--fuente);font-size:14px;letter-spacing:0}
    .cop-titulo{display:flex;align-items:center;gap:8px}
    .cop-luz{width:7px;height:7px;border-radius:50%;background:var(--acento-claro);animation:copPulso 1.8s ease-in-out infinite}
    .cop-cerrar,.cop-detener{min-height:32px;background:none;color:#fff;padding:4px 8px;font-size:13px}
    .cop-cerrar{font-size:20px;line-height:1}
    .cop-mensajes{flex:1;overflow-y:auto;padding:12px;display:flex;flex-direction:column;gap:8px;background:var(--hueso)}
    .cop-msg{max-width:85%;padding:8px 11px;border-radius:12px;font-size:14px;line-height:1.45;white-space:pre-wrap}
    .cop-msg.user{align-self:flex-end;background:var(--acento);color:#fff}
    .cop-msg.assistant{align-self:flex-start;background:#fff;border:1px solid var(--liquen)}
    .cop-msg.nota{align-self:center;color:var(--texto-suave);font-family:var(--mono);font-size:12px;text-align:center}
    .cop-form{display:flex;gap:6px;padding:10px;border-top:1px solid var(--liquen)}
    .cop-form input{flex:1;width:auto;padding:9px 11px;font-size:16px;min-height:44px}
    .cop-form button{padding:9px 14px;background:var(--acento);color:#fff;font-size:13px;min-height:44px}
    .cop-form button:hover{background:var(--acento-hover)}
    #sos-copiloto .cop-mic{background:var(--gris-fondo);color:var(--texto);padding:9px 10px;display:flex;align-items:center;min-width:44px}
    #sos-copiloto .cop-mic.grabando{background:var(--rojo-alerta);color:#fff;animation:copPulso 1s infinite}
    @media (max-width:720px){
      #sos-copiloto{right:12px;bottom:12px}
      #sos-copiloto .cop-boton{width:48px;height:48px;min-height:48px;padding:0;border-radius:50%;font-size:12px}
      #sos-copiloto .cop-panel{bottom:58px}
    }
    @keyframes copPulso{50%{opacity:.6}}
    @keyframes copAparece{from{opacity:0;transform:translateY(14px) scale(.97)}to{opacity:1;transform:none}}
    @keyframes copFlota{from{translate:0 0}to{translate:0 -4px}}
    @media (prefers-reduced-motion:reduce){html:not(.movimiento) #sos-copiloto .cop-boton,html:not(.movimiento) #sos-copiloto .cop-panel{animation:none}}`;
  document.head.appendChild(estilo);

  const raiz = document.createElement('div');
  raiz.id = 'sos-copiloto';
  raiz.innerHTML = `
    <div class="cop-panel" id="copPanelSosIA" role="region" aria-label="Asistente SosIA" hidden>
      <div class="cop-cabecera">
        <span class="cop-titulo"><span class="cop-luz" aria-hidden="true"></span>SosIA</span>
        <span>
          <button class="cop-detener" type="button" hidden>Detener</button>
          <button class="cop-cerrar" type="button" aria-label="Cerrar asistente">×</button>
        </span>
      </div>
      <div class="cop-mensajes" role="log" aria-live="polite" aria-relevant="additions"></div>
      <form class="cop-form">
        <button type="button" class="cop-mic" aria-label="Grabar mensaje de voz" aria-pressed="false" title="Hablar">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><rect x="9" y="2" width="6" height="12" rx="3"/><path d="M5 11a7 7 0 0 0 14 0M12 18v4"/></svg>
        </button>
        <input type="text" placeholder="Escribe o habla..." maxlength="600" autocomplete="off" aria-label="Mensaje para SosIA">
        <button type="submit">Enviar</button>
      </form>
    </div>
    <button class="cop-boton" type="button" aria-expanded="false" aria-controls="copPanelSosIA">SosIA</button>`;
  document.body.appendChild(raiz);

  const panel = raiz.querySelector('.cop-panel');
  const caja = raiz.querySelector('.cop-mensajes');
  const form = raiz.querySelector('.cop-form');
  const input = form.querySelector('input');
  const mic = form.querySelector('.cop-mic');
  const botonFlotante = raiz.querySelector('.cop-boton');
  const botonDetener = raiz.querySelector('.cop-detener');

  function abrirPanel() {
    panel.hidden = false;
    botonFlotante.setAttribute('aria-expanded', 'true');
    sessionStorage.setItem('sos_copiloto_abierto', '1');
    input.focus();
  }

  function cerrarPanel() {
    panel.hidden = true;
    botonFlotante.setAttribute('aria-expanded', 'false');
    sessionStorage.removeItem('sos_copiloto_abierto');
    botonFlotante.focus();
  }

  botonFlotante.onclick = () => (panel.hidden ? abrirPanel() : cerrarPanel());
  raiz.querySelector('.cop-cerrar').onclick = cerrarPanel;
  botonDetener.onclick = () => {
    cancelado = true;
    descartarAudio = true;
    sessionStorage.removeItem(CLAVE_PLAN);
    if ('speechSynthesis' in window) speechSynthesis.cancel();
    if (detener) detener();
    botonDetener.hidden = true;
    agregar('nota', 'Plan detenido.');
  };

  function pintar(rol, texto) {
    const d = document.createElement('div');
    d.className = 'cop-msg ' + rol;
    d.textContent = texto; // textContent: lo que diga el modelo nunca se interpreta como HTML
    caja.appendChild(d);
    caja.scrollTop = caja.scrollHeight;
    return d;
  }

  function agregar(rol, texto) {
    const d = pintar(rol, texto);
    if (rol !== 'nota') {
      historial.push({ role: rol, content: texto });
      historial = historial.slice(-20);
      sessionStorage.setItem(CLAVE_HIST, JSON.stringify(historial));
    }
    return d;
  }

  /* ---------- lectura de la pantalla ---------- */
  const visible = (el) => el.offsetParent !== null && !el.closest('#sos-copiloto');

  function elementosInteractivos() {
    return [...document.querySelectorAll('button, a[href], input:not([type=hidden]), select, textarea')]
      .filter((el) => visible(el)
        && el.type !== 'password'
        && el.type !== 'file'
        && !el.closest('[data-ia-privado]'))
      .slice(0, 70);
  }

  function etiqueta(el) {
    if (el.id) {
      const l = document.querySelector(`label[for="${el.id}"]`);
      if (l) return l.textContent.trim();
    }
    // Nunca se envía el valor escrito en un campo: puede contener datos clínicos.
    return (el.getAttribute('aria-label') || el.placeholder || el.innerText || el.title || '').trim().slice(0, 50);
  }

  function describirPantalla() {
    document.querySelectorAll('[data-ia-ref]').forEach((e) => e.removeAttribute('data-ia-ref'));
    return elementosInteractivos().map((el, i) => {
      const ref = 'e' + (i + 1);
      el.setAttribute('data-ia-ref', ref);
      const tag = el.tagName.toLowerCase();
      const tipo = tag === 'button' ? 'boton' : tag === 'a' ? 'enlace' : tag === 'select' ? 'lista' : 'campo';
      const extra = tag === 'select' ? ' [' + [...el.options].map((o) => o.text).join('|').slice(0, 120) + ']' : '';
      return `${ref} ${tipo} "${etiqueta(el)}"${el.id ? ' id=' + el.id : ''}${extra}`;
    });
  }

  function contexto() {
    const s = obtenerSesion();
    return {
      pagina: location.pathname.split('/').pop(),
      usuario: s.usuario,
      perfil: s.perfil,
      paginasPermitidas: s.actividades.map((a) => a.enlace),
      elementos: describirPantalla()
    };
  }

  /* ---------- motor de pasos ---------- */
  function buscarElemento(objetivo) {
    if (!objetivo) return null;
    let el = document.querySelector(`[data-ia-ref="${CSS.escape(objetivo)}"]`) || document.getElementById(objetivo);
    if (el) return el;
    const n = norm(objetivo);
    const candidatos = elementosInteractivos();
    return candidatos.find((c) => norm(etiqueta(c)) === n) || candidatos.find((c) => norm(etiqueta(c)).includes(n)) || null;
  }

  async function ejecutarPaso(p) {
    const el = buscarElemento(p.objetivo);
    if (!el) return { fallo: `No encontré «${p.objetivo}» en esta pantalla.` }; // nunca clic a ciegas

    const tag = el.tagName;
    const esCampoTexto = tag === 'INPUT' || tag === 'TEXTAREA';

    if (p.accion === 'escribir' || (p.accion === 'elegir' && esCampoTexto)) {
      el.focus();
      el.value = p.valor || '';
      el.dispatchEvent(new Event('input', { bubbles: true }));  // dispara el autocompletado de cedula, etc.
      el.dispatchEvent(new Event('change', { bubbles: true }));
      return { mensaje: `Escribí «${p.valor}» en ${etiqueta(el) || p.objetivo}` };
    }

    if (p.accion === 'elegir' && tag === 'SELECT') {
      const v = norm(p.valor);
      const op = [...el.options].find((o) => norm(o.text).endsWith(v) || norm(o.value) === v)
              || [...el.options].find((o) => norm(o.text).includes(v));
      if (!op) return { fallo: `No hay la opción «${p.valor}».` };
      el.value = op.value;
      el.dispatchEvent(new Event('change', { bubbles: true }));
      return { mensaje: `Elegí «${op.text}»` };
    }

    // clic (también "elegir" sobre pestañas o botones)
    const texto = etiqueta(el);
    if (/salir|cerrar sesi[oó]n|logout/i.test(texto)) {
      return { fallo: 'Por seguridad, SosIA no puede cerrar la sesión.' };
    }
    const critico = el.type === 'submit' || CRITICO.test(texto);
    if (critico) {
      const continuar = await confirmarAccion({
        titulo: 'Confirmar acción de SosIA',
        mensaje: `SosIA quiere pulsar «${texto || p.objetivo}». Revisa que sea la acción correcta.`,
        textoConfirmar: 'Continuar'
      });
      if (!continuar) return { fallo: 'Cancelado por ti.' };
    }
    el.click();
    return { mensaje: `Clic en «${texto || p.objetivo}»` };
  }

  async function ejecutarPlan(pasos) {
    cancelado = false;
    botonDetener.hidden = false;
    try {
      for (let i = 0; i < pasos.length; i++) {
        if (cancelado) { agregar('nota', 'Plan detenido.'); return; }
        const p = pasos[i];

        if (p.accion === 'navegar') {
          const resto = pasos.slice(i + 1);
          if (resto.length) sessionStorage.setItem(CLAVE_PLAN, JSON.stringify({ pasos: resto, expira: Date.now() + 60000 }));
          agregar('nota', 'Abriendo ' + p.objetivo + '...');
          location.href = p.objetivo;
          return;
        }

        const r = await ejecutarPaso(p);
        if (r.fallo) { agregar('nota', '⚠ ' + r.fallo); return; }
        agregar('nota', '✓ ' + r.mensaje);
        await esperar(350);
      }
    } finally {
      botonDetener.hidden = true;
    }
  }

  // Un plan que cruza paginas continua en la pagina nueva (caduca a los 60 s)
  async function reanudar() {
    const crudo = sessionStorage.getItem(CLAVE_PLAN);
    if (!crudo) return;
    sessionStorage.removeItem(CLAVE_PLAN);
    const plan = JSON.parse(crudo);
    if (Date.now() > plan.expira) return;
    abrirPanel();
    await esperar(700);
    const continuar = await confirmarAccion({
      titulo: 'Continuar plan de SosIA',
      mensaje: 'SosIA tiene pasos pendientes para esta pantalla. ¿Deseas continuar?',
      textoConfirmar: 'Continuar plan',
      peligro: false
    });
    if (!continuar) {
      agregar('nota', 'Plan cancelado.');
      return;
    }
    await ejecutarPlan(plan.pasos);
  }

  document.addEventListener('keydown', (e) => {
    if (e.key !== 'Escape') return;
    cancelado = true;
    descartarAudio = true;
    sessionStorage.removeItem(CLAVE_PLAN);
    clearTimeout(temporizadorEnvio);
    botonDetener.hidden = true;
    if ('speechSynthesis' in window) speechSynthesis.cancel();
    if (detener) detener();
  });

  /* ---------- voz: respuesta hablada (opcional) ---------- */
  function hablar(texto, porVoz) {
    if (!porVoz || !texto || !('speechSynthesis' in window)) return;
    speechSynthesis.cancel();
    const u = new SpeechSynthesisUtterance(texto);
    const voz = speechSynthesis.getVoices().find((v) => v.lang.toLowerCase().startsWith('es'));
    if (voz) { u.voice = voz; u.lang = voz.lang; } else { u.lang = 'es-ES'; }
    speechSynthesis.speak(u);
  }

  /* ---------- voz: grabacion con deteccion de silencio ---------- */
  async function grabar() {
    if (grabando) { if (detener) detener(); return; } // segundo clic = terminar ya

    if (!navigator.mediaDevices || !window.MediaRecorder) {
      agregar('nota', 'Tu navegador no permite grabar audio.');
      return;
    }

    let flujo;
    try {
      flujo = await navigator.mediaDevices.getUserMedia({ audio: true });
    } catch (e) {
      agregar('nota', 'No tengo permiso para usar el micrófono.');
      return;
    }

    if ('speechSynthesis' in window) speechSynthesis.cancel(); // que el asistente no se escuche a si mismo
    descartarAudio = false;

    const grabadora = new MediaRecorder(flujo);
    const trozos = [];
    grabadora.ondataavailable = (e) => { if (e.data.size) trozos.push(e.data); };

    const ctxAudio = new (window.AudioContext || window.webkitAudioContext)();
    const analizador = ctxAudio.createAnalyser();
    analizador.fftSize = 1024;
    ctxAudio.createMediaStreamSource(flujo).connect(analizador);
    const datos = new Uint8Array(analizador.fftSize);

    const nivel = () => {
      analizador.getByteTimeDomainData(datos);
      let suma = 0;
      for (const v of datos) { const x = (v - 128) / 128; suma += x * x; }
      return Math.sqrt(suma / datos.length);
    };

    let hablo = false, ruido = 0, muestras = 0, silencioDesde = null;
    const inicio = Date.now();

    const vigilar = setInterval(() => {
      const t = Date.now() - inicio;
      const n = nivel();

      if (t < 300) { ruido += n; muestras++; return; }          // mide el ruido del lugar
      const base = muestras ? ruido / muestras : 0;
      const umbral = Math.max(0.02, base * 1.7);                  // la voz debe superar 1,7 veces el ruido

      if (n > umbral) { hablo = true; silencioDesde = null; }
      else if (hablo) {
        silencioDesde = silencioDesde || Date.now();
        if (Date.now() - silencioDesde > 900) detener();         // corta tras 900 ms de silencio
      }

      if (!hablo && t > 5000) { descartarAudio = true; detener(); } // nadie hablo
      if (t > 10000) detener();                                     // tope por turno
    }, 100);

    detener = () => { if (grabadora.state !== 'inactive') grabadora.stop(); };

    grabadora.onstop = async () => {
      clearInterval(vigilar);
      flujo.getTracks().forEach((t) => t.stop());
      ctxAudio.close();
      grabando = false;
      detener = null;
      mic.classList.remove('grabando');
      mic.setAttribute('aria-pressed', 'false');
      mic.setAttribute('aria-label', 'Grabar mensaje de voz');

      if (descartarAudio || !hablo) { agregar('nota', 'No te escuché. Intenta de nuevo.'); return; }
      await transcribirYConfirmar(new Blob(trozos, { type: grabadora.mimeType || 'audio/webm' }));
    };

    grabando = true;
    mic.classList.add('grabando');
    mic.setAttribute('aria-pressed', 'true');
    mic.setAttribute('aria-label', 'Detener grabación de voz');
    grabadora.start();
  }

  async function transcribirYConfirmar(blob) {
    const espera = pintar('nota', 'Transcribiendo...');
    try {
      const r = await transcribirAudio(blob);
      espera.remove();
      const texto = (r.texto || '').trim();
      if (!texto) { agregar('nota', 'No logré entender el audio.'); return; }

      // Se muestra lo entendido y el usuario decide cuándo enviarlo.
      input.value = texto;
      vinoPorVoz = true;
      input.focus();
      pintar('nota', 'Entendí el mensaje. Revísalo y pulsa Enviar para confirmar.');
      clearTimeout(temporizadorEnvio);
    } catch (error) {
      espera.remove();
      agregar('nota', error.message);
    }
  }

  mic.onclick = grabar;
  input.addEventListener('input', () => clearTimeout(temporizadorEnvio)); // si escribes, se cancela el envio automatico

  /* ---------- envio ---------- */
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    clearTimeout(temporizadorEnvio);
    const texto = input.value.trim();
    if (!texto) return;
    input.value = '';

    const porVoz = vinoPorVoz;
    vinoPorVoz = false;

    const previo = historial.slice(-8);
    agregar('user', texto);
    const pensando = pintar('assistant', 'Pensando...');

    try {
      const resp = await preguntarAgente({ mensaje: texto, historial: previo, contexto: contexto() });
      pensando.remove();
      agregar('assistant', resp.texto);
      hablar(resp.texto, porVoz);

      if (resp.pasos && resp.pasos.length) {
        registrarAuditoria('ASISTENTE', `${resp.pasos.length} paso(s) en pantalla: ${texto}`);
        await ejecutarPlan(resp.pasos);
      }
    } catch (error) {
      pensando.remove();
      agregar('nota', error.message);
    }
  });

  /* ---------- arranque ---------- */
  historial.forEach((m) => pintar(m.role, m.content));
  if (sessionStorage.getItem('sos_copiloto_abierto')) {
    panel.hidden = false;
    botonFlotante.setAttribute('aria-expanded', 'true');
  }
  reanudar();
})();