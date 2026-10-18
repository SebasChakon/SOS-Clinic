/* ------------------------------------------------------------------
   Interruptor de animaciones.
   Si el sistema operativo pide movimiento reducido, el CSS apaga las
   animaciones. Este script ofrece activarlas de forma explicita y
   recuerda la eleccion en localStorage (clave: sos_movimiento).

   Se carga en el <head> para aplicar la clase `movimiento` antes del
   primer render y evitar parpadeos.
   ------------------------------------------------------------------ */
(function () {
  try {
    if (localStorage.getItem('sos_movimiento') === '1') {
      document.documentElement.classList.add('movimiento');
    }
  } catch (error) { }

  function crearPregunta() {
    if (!window.matchMedia || !window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    if (document.documentElement.classList.contains('movimiento')) return;
    try {
      if (localStorage.getItem('sos_movimiento') !== null) return;
    } catch (error) { }

    const caja = document.createElement('div');
    caja.className = 'interruptor-movimiento';
    caja.innerHTML =
      '<span>Tu equipo tiene las animaciones reducidas.</span>' +
      '<button type="button" class="boton-activar">Activar animaciones</button>' +
      '<button type="button" class="boton-descartar" aria-label="No volver a preguntar">&times;</button>';

    caja.querySelector('.boton-activar').addEventListener('click', () => {
      let persistido = false;
      try {
        localStorage.setItem('sos_movimiento', '1');
        persistido = true;
      } catch (error) { }
      document.documentElement.classList.add('movimiento');
      if (persistido) {
        location.reload();
      } else {
        caja.remove();
      }
    });

    caja.querySelector('.boton-descartar').addEventListener('click', () => {
      try { localStorage.setItem('sos_movimiento', '0'); } catch (error) { }
      caja.remove();
    });

    document.body.appendChild(caja);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', crearPregunta);
  } else {
    crearPregunta();
  }
})();
