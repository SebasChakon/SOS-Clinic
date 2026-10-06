(function(d, s, id) {
  var js, fjs = d.getElementsByTagName(s)[0];
  if (d.getElementById(id)) return;
  js = d.createElement(s); js.id = id;
  js.src = 'http://localhost:3010/embed/dograh-widget.js?token=emb_Xky_0uaZq-XSRwpIjXCGHEO-ufw3g0-zPO1MJSJrLwI&environment=local&apiEndpoint=http://localhost:8000';

  // Contexto que el agente puede leer en sus prompts como {{initial_context.XXX}}
  var sesion = (typeof obtenerSesion === 'function') ? obtenerSesion() : null;

  js.setAttribute('data-dograh-context', JSON.stringify({
    page_url: window.location.href,
    today: new Date().toISOString().slice(0, 10),
    pagina_actual: window.location.pathname.split('/').pop(),
    usuario: sesion ? sesion.usuario : 'no autenticado',
    perfil: sesion ? sesion.perfil : null
  }));

  js.async = true;
  fjs.parentNode.insertBefore(js, fjs);
}(document, 'script', 'dograh-widget'));