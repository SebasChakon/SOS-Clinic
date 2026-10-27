/* =====================================================================
   SOS · api.js
   Funciones compartidas para hablarle al backend a traves del gateway.
   Ninguna vista deberia usar fetch() directamente: todas pasan por aqui.
   ===================================================================== */

/* En local se conserva el gateway en 8080. En Azure el frontend y la API
   comparten origen: Caddy proxya /ms-* hacia 127.0.0.1:8080. */
const BASE_URL = (
  window.location.protocol === 'file:' ||
  window.location.hostname === 'localhost' ||
  window.location.hostname === '127.0.0.1'
)
  ? 'http://localhost:8080'
  : '';

/* El alta guarda el episodio y luego envia la cita, la historia en PDF y
   consulta farmacias. Es una operacion larga: no debe cortarse a los 20 s. */
const TIMEOUT_ALTA_MS = 120000;
const MENSAJE_TIMEOUT_ALTA = 'La operación está tardando más de lo esperado. Pudo haberse completado; actualiza la página de seguimiento antes de volver a intentarlo.';

/* ---------------------------------------------------------------------
   Sesion (token JWT guardado en el navegador)
   --------------------------------------------------------------------- */

function guardarSesion(datosLogin) {
  sessionStorage.setItem('sos_token', datosLogin.token);
  sessionStorage.setItem('sos_usuario', datosLogin.usuario);
  sessionStorage.setItem('sos_perfil', datosLogin.perfil);
  sessionStorage.setItem('sos_id_personal', datosLogin.idPersonal);
  sessionStorage.setItem('sos_actividades', JSON.stringify(datosLogin.actividades || []));
}

function obtenerToken() {
  return sessionStorage.getItem('sos_token');
}

function obtenerSesion() {
  const token = obtenerToken();
  if (!token) return null;
  return {
    token,
    usuario: sessionStorage.getItem('sos_usuario'),
    perfil: sessionStorage.getItem('sos_perfil'),
    idPersonal: sessionStorage.getItem('sos_id_personal'),
    actividades: JSON.parse(sessionStorage.getItem('sos_actividades') || '[]')
  };
}

function haySesion() {
  return obtenerToken() !== null;
}

/**
 * Codifica texto para insertarlo en HTML. Se usa en las plantillas que
 * construyen filas con innerHTML para que los datos del paciente o del
 * usuario nunca se interpreten como etiquetas.
 */
function escaparHtml(valor) {
  return String(valor ?? '').replace(/[&<>"']/g, (c) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  }[c]));
}

/** Solo acepta enlaces relativos a paginas .html del propio frontend. */
function enlaceSeguro(enlace) {
  const valor = String(enlace ?? '');
  return /^[A-Za-z0-9_-]+\.html(?:\?[^#]*)?$/.test(valor) ? valor : '#';
}

/** Solo permite enlaces externos HTTP(S), nunca javascript: ni data:. */
function urlExternaSegura(valor) {
  try {
    const url = new URL(String(valor));
    return ['http:', 'https:'].includes(url.protocol) ? url.href : '#';
  } catch (error) {
    return '#';
  }
}

function cerrarSesion(destino = 'index.html') {
  sessionStorage.clear();
  window.location.href = destino;
}

/**
 * Elige la primera pantalla "navegable" de una lista de actividades,
 * saltandose 'seguimiento_paciente' porque esa nunca es un destino valido
 * por si sola (necesita un idRegistro en la URL para funcionar).
 */
function primerDestinoValido(actividades) {
  const lista = Array.isArray(actividades) ? actividades : [];
  const candidato = lista.find((a) => a.nombreActividad !== 'seguimiento_paciente' && enlaceSeguro(a.enlace) !== '#');
  if (candidato) return enlaceSeguro(candidato.enlace);
  const primero = lista.find((a) => enlaceSeguro(a.enlace) !== '#');
  return primero ? enlaceSeguro(primero.enlace) : null;
}

/**
 * Llama esto al cargar cualquier vista que NO sea el login.
 * Redirige a login.html si no hay sesion, o a la pantalla correcta
 * si el usuario no tiene permiso para ver esta pagina.
 */
function exigirSesion() {
  const sesion = obtenerSesion();

  if (!sesion) {
    window.location.href = 'login.html';
    return;
  }

  if (sesion.perfil === 'administrador') return;

  const paginaActual = window.location.pathname.split('/').pop();
  if (paginaActual === 'cuenta.html') return;
  const tienePermiso = sesion.actividades.some((a) => a.enlace === paginaActual);

  if (!tienePermiso) {
    if (paginaActual === 'login.html' || paginaActual === '') return; // evita loop con el login
    const destino = primerDestinoValido(sesion.actividades);
    if (destino) {
      window.location.href = destino;
    } else {
      cerrarSesion('login.html?motivo=sin-actividades');
    }
  }
}

/* ---------------------------------------------------------------------
   Funcion base: todas las llamadas al backend pasan por aqui
   --------------------------------------------------------------------- */

function mensajeDeError(datos, respuesta) {
  const bruto = (datos && typeof datos === 'string')
    ? datos.trim()
    : String((datos && (datos.message || datos.detail || datos.error)) || '').trim();

  // Los mensajes de validación del backend son útiles; los técnicos no.
  if ([400, 401, 409, 422, 429].includes(respuesta.status) && bruto && bruto.length <= 240) {
    return bruto;
  }

  const mensajes = {
    400: 'Revisa los datos del formulario e inténtalo de nuevo.',
    403: 'No tienes permisos para realizar esta acción.',
    404: 'No encontramos el registro solicitado.',
    409: 'El registro ya existe o entra en conflicto con otro dato.',
    422: 'Hay datos que no cumplen las reglas del sistema.',
    429: 'Demasiados intentos. Espera un momento y vuelve a intentarlo.',
    500: 'El servidor tuvo un problema. Inténtalo de nuevo en unos segundos.',
    502: 'El servicio no está disponible en este momento.',
    503: 'El servicio no está disponible en este momento.',
    504: 'El servidor tardó demasiado en responder.'
  };

  return mensajes[respuesta.status] || bruto || 'No se pudo completar la solicitud.';
}

async function llamarApi(ruta, opciones = {}) {
  const token = obtenerToken();

  const headers = {
    'Content-Type': 'application/json',
    ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
    ...opciones.headers
  };

  const controlador = new AbortController();
  const senalExterna = opciones.signal;
  const alCancelar = () => controlador.abort();
  if (senalExterna) {
    if (senalExterna.aborted) controlador.abort();
    else senalExterna.addEventListener('abort', alCancelar, { once: true });
  }
  const temporizador = setTimeout(() => controlador.abort(), opciones.timeout || 20000);

  let respuesta;
  try {
    respuesta = await fetch(`${BASE_URL}${ruta}`, {
      ...opciones,
      headers,
      signal: controlador.signal
    });
  } catch (error) {
    if (error.name === 'AbortError') {
      if (senalExterna && senalExterna.aborted) throw new Error('Solicitud cancelada.');
      throw new Error(opciones.mensajeTimeout || 'El servidor tardó demasiado en responder. Inténtalo de nuevo.');
    }
    throw new Error('No se pudo contactar al servidor. Verifica que eureka-server, ms-gateway y los microservicios estén corriendo.');
  } finally {
    clearTimeout(temporizador);
    if (senalExterna) senalExterna.removeEventListener('abort', alCancelar);
  }

  if (respuesta.status === 401) {
    if (token) {
      // Ya teníamos una sesión activa y el token dejó de ser válido.
      cerrarSesion('login.html?motivo=sesion-expirada');
      throw new Error('Sesión expirada. Vuelve a iniciar sesión.');
    }
    // Sin token (por ejemplo, login fallido): mostramos el mensaje del backend.
  }

  const textoRespuesta = await respuesta.text();
  let datos = null;
  try { datos = textoRespuesta ? JSON.parse(textoRespuesta) : null; } catch (e) { datos = textoRespuesta; }

  if (!respuesta.ok) {
    if (respuesta.status === 403 && datos && datos.code === 'CAMBIO_CLAVE_REQUERIDO') {
      window.location.href = 'cambiar-clave.html?obligatorio=1';
      throw new Error('Debes cambiar la clave temporal antes de continuar.');
    }
    throw new Error(mensajeDeError(datos, respuesta));
  }

  return datos;
}

/* ---------------------------------------------------------------------
   ms-seguridad
   --------------------------------------------------------------------- */

  async function login(usuario, clave) {
    const datos = await llamarApi('/ms-seguridad/auth/login', {
      method: 'POST', body: JSON.stringify({ usuario, clave })
    });
    if (!datos.requiere2fa) {
      guardarSesion(datos);
      if (!datos.debeCambiarClave) registrarAuditoria('LOGIN', 'Inicio de sesion');
    }
    return datos;
  }

  async function verificarCodigo2fa(challenge, codigo) {
    const datos = await llamarApi('/ms-seguridad/auth/verificar-2fa', {
      method: 'POST', body: JSON.stringify({ challenge, codigo })
    });
    guardarSesion(datos);
    if (!datos.debeCambiarClave) registrarAuditoria('LOGIN', 'Inicio de sesion (2FA)');
    return datos;
  }

  async function reenviarCodigo2fa(challenge) {
    return llamarApi('/ms-seguridad/auth/reenviar-2fa', {
      method: 'POST', body: JSON.stringify({ challenge })
    });
  }

  async function obtenerPoliticaClave() {
    return llamarApi('/ms-seguridad/auth/politica-clave');
  }

  async function recuperarClave(identificador) {
    return llamarApi('/ms-seguridad/auth/recuperar-clave', {
      method: 'POST', body: JSON.stringify({ identificador })
    });
  }

  async function validarTokenRecuperacion(token) {
    return llamarApi('/ms-seguridad/auth/validar-token-recuperacion', {
      method: 'POST', body: JSON.stringify({ token })
    });
  }

  async function restablecerClave(token, clave) {
    return llamarApi('/ms-seguridad/auth/restablecer-clave', {
      method: 'POST', body: JSON.stringify({ token, clave })
    });
  }

  async function confirmarCambioEmail(token) {
    return llamarApi('/ms-seguridad/auth/confirmar-email', {
      method: 'POST', body: JSON.stringify({ token })
    });
  }

  async function cambiarClave(claveActual, claveNueva) {
    const datos = await llamarApi('/ms-seguridad/auth/cambiar-clave', {
      method: 'POST', body: JSON.stringify({ claveActual, claveNueva })
    });
    guardarSesion(datos);
    registrarAuditoria('ACTUALIZAR', 'Cambio de clave propio');
    return datos;
  }

  async function obtenerMiCuenta() {
    return llamarApi('/ms-seguridad/auth/mi-cuenta');
  }

  async function activar2fa() {
    return llamarApi('/ms-seguridad/auth/2fa/activar', { method: 'POST' });
  }

  async function solicitarDesactivar2fa(claveActual) {
    return llamarApi('/ms-seguridad/auth/2fa/desactivar/solicitar', {
      method: 'POST', body: JSON.stringify({ claveActual })
    });
  }

  async function desactivar2fa(challenge, codigo) {
    return llamarApi('/ms-seguridad/auth/2fa/desactivar', {
      method: 'POST', body: JSON.stringify({ challenge, codigo })
    });
  }

  async function solicitarCambioEmail(claveActual, emailNuevo) {
    return llamarApi('/ms-seguridad/auth/solicitar-cambio-email', {
      method: 'POST', body: JSON.stringify({ claveActual, emailNuevo })
    });
  }

/* ---------------------------------------------------------------------
   ms-pacientes
   --------------------------------------------------------------------- */

async function crearPaciente(paciente) {
  const resultado = await llamarApi('/ms-pacientes/pacientes', {
    method: 'POST',
    body: JSON.stringify(paciente)
  });
  registrarAuditoria('CREAR', `Paciente: ${resultado.nombre} ${resultado.apellido} (CC ${resultado.cedula})`);
  return resultado;
}

async function listarPacientes() {
  return llamarApi('/ms-pacientes/pacientes');
}

async function obtenerPacientePorCedula(cedula) {
  return llamarApi(`/ms-pacientes/pacientes/cedula/${encodeURIComponent(cedula)}`);
}

async function obtenerPacientePorId(idPaciente) {
  return llamarApi(`/ms-pacientes/pacientes/${idPaciente}`);
}

async function registrarSignosVitales(signos) {
  return llamarApi('/ms-pacientes/pacientes/signos-vitales', {
    method: 'POST',
    body: JSON.stringify(signos)
  });
}

async function listarSignosVitalesPorRegistro(idRegistro) {
  return llamarApi(`/ms-pacientes/pacientes/signos-vitales/registro/${idRegistro}`);
}

async function enviarHistoriaClinica(idPaciente, contenido) {
  return llamarApi(`/ms-pacientes/pacientes/${idPaciente}/enviar-historia`, {
    method: 'POST', body: JSON.stringify({ contenido })
  });
}

async function programarTeleconsulta(nombrePaciente) {
  return llamarApi('/ms-seguridad/auth/programar-teleconsulta', {
    method: 'POST', body: JSON.stringify({ nombrePaciente })
  });
}

async function enviarCitaTeleconsulta(idPaciente, cita) {
  return llamarApi(`/ms-pacientes/pacientes/${idPaciente}/enviar-cita-teleconsulta`, {
    method: 'POST', body: JSON.stringify(cita)
  });
}

/* ---------------------------------------------------------------------
   Alta completa (una sola llamada): el backend persiste el alta en
   ms-triage, genera la cita de teleconsulta, envia la historia clinica
   en PDF y las farmacias cercanas, y devuelve estado por correo.
   --------------------------------------------------------------------- */

async function finalizarAtencion(idRegistro, datos) {
  const resultado = await llamarApi(`/ms-pacientes/pacientes/atenciones/${idRegistro}/finalizar`, {
    method: 'POST',
    body: JSON.stringify(datos),
    timeout: TIMEOUT_ALTA_MS,
    mensajeTimeout: MENSAJE_TIMEOUT_ALTA
  });
  registrarAuditoria('ACTUALIZAR', `Triage #${idRegistro} dado de alta (alta + notificaciones en backend)`);
  return resultado;
}

async function reenviarCorreosAtencion(idRegistro, datos) {
  return llamarApi(`/ms-pacientes/pacientes/atenciones/${idRegistro}/reenviar-correos`, {
    method: 'POST',
    body: JSON.stringify(datos || {}),
    timeout: TIMEOUT_ALTA_MS,
    mensajeTimeout: MENSAJE_TIMEOUT_ALTA
  });
}

/* Descarga el PDF oficial de la historia clinica (lo genera ms-pacientes). */
async function descargarHistoriaPdf(idRegistro) {
  const token = obtenerToken();
  let respuesta;
  try {
    respuesta = await fetch(`${BASE_URL}/ms-pacientes/pacientes/historias/${idRegistro}/pdf`, {
      headers: token ? { 'Authorization': `Bearer ${token}` } : {}
    });
  } catch (error) {
    throw new Error('No se pudo contactar al servidor para generar el PDF.');
  }

  // El cuerpo de una respuesta solo puede leerse UNA vez.
  if (!respuesta.ok) {
    const textoError = await respuesta.text().catch(() => '');
    let mensaje = 'No se pudo generar el PDF de la historia clínica';
    try {
      const datos = textoError ? JSON.parse(textoError) : null;
      if (typeof datos === 'string' && datos) mensaje = datos;
      else if (datos && datos.message) mensaje = datos.message;
    } catch (e) {
      if (textoError) mensaje = textoError;
    }
    throw new Error(mensaje);
  }

  const blob = await respuesta.blob();
  const disposicion = respuesta.headers.get('Content-Disposition') || '';
  const coincide = disposicion.match(/filename="?([^";]+)"?/);
  const url = URL.createObjectURL(blob);
  const enlace = document.createElement('a');
  enlace.href = url;
  enlace.download = coincide ? coincide[1].trim() : `historia_clinica_${idRegistro}.pdf`;
  document.body.appendChild(enlace);
  enlace.click();
  enlace.remove();
  URL.revokeObjectURL(url);
}

/* ---------------------------------------------------------------------
   ms-triage
   --------------------------------------------------------------------- */

async function registrarTriage(registro) {
  const resultado = await llamarApi('/ms-triage/triage', {
    method: 'POST',
    body: JSON.stringify(registro)
  });
  registrarAuditoria('CREAR', `Triage, paciente #${registro.idPaciente}`);
  return resultado;
}

async function listaPriorizacion() {
  return llamarApi('/ms-triage/triage/prioridad');
}

async function marcarEnAtencion(idRegistro) {
  const resultado = await llamarApi(`/ms-triage/triage/${idRegistro}/en-atencion`, { method: 'PUT' });
  registrarAuditoria('ACTUALIZAR', `Triage #${idRegistro} -> En atencion`);
  return resultado;
}

async function marcarAtendido(idRegistro, diagnosticoFinal, tratamiento) {
  const resultado = await llamarApi(`/ms-triage/triage/${idRegistro}/atendido`, {
    method: 'PUT', body: JSON.stringify({ diagnosticoFinal, tratamiento })
  });
  registrarAuditoria('ACTUALIZAR', `Triage #${idRegistro} dado de alta. Diagnostico: ${diagnosticoFinal}`);
  return resultado;
}

async function actualizarUbicacionTriage(idRegistro, ubicacion) {
  const resultado = await llamarApi(`/ms-triage/triage/${idRegistro}/ubicacion`, {
    method: 'PUT', body: JSON.stringify({ ubicacion })
  });
  registrarAuditoria('ACTUALIZAR', `Triage #${idRegistro}, ubicacion -> ${ubicacion}`);
  return resultado;
}

async function buscarPacientesPorCedula(q) {
  return llamarApi(`/ms-pacientes/pacientes/buscar?q=${encodeURIComponent(q)}`);
}

const UBICACIONES_DISPONIBLES = [
  'Consultorio 1', 'Consultorio 2', 'Consultorio 3', 'Consultorio 4', 'Consultorio 5',
  'Sala de espera', 'Rayos X', 'Laboratorio', 'Ginecología', 'Pediatría', 'Cirugía', 'UCI', 'Urgencias', 'Observación', 'Farmacia', 'Otro'
];

async function obtenerRegistroTriage(idRegistro) {
  return llamarApi(`/ms-triage/triage/${idRegistro}`);
}

/* ---------------------------------------------------------------------
   Utilidad comun para mostrar mensajes de exito/error en cualquier vista
   Requiere un elemento <div class="mensaje" id="mensaje"></div> en el HTML
   --------------------------------------------------------------------- */

function mostrarMensaje(idElemento, texto, tipo = 'info') {
  const el = document.getElementById(idElemento);
  if (!el) return;
  el.className = `mensaje ${tipo}`;
  el.textContent = texto;
  el.setAttribute('role', tipo === 'error' ? 'alert' : 'status');
  el.setAttribute('aria-live', tipo === 'error' ? 'assertive' : 'polite');
  el.setAttribute('aria-atomic', 'true');
}

function limpiarMensaje(idElemento) {
  const el = document.getElementById(idElemento);
  if (!el) return;
  el.className = 'mensaje';
  el.textContent = '';
  el.removeAttribute('role');
  el.removeAttribute('aria-live');
  el.removeAttribute('aria-atomic');
}

function avisar(texto, tipo = 'exito') {
  let contenedor = document.getElementById('sos-avisos');
  if (!contenedor) {
    contenedor = document.createElement('div');
    contenedor.id = 'sos-avisos';
    contenedor.setAttribute('role', 'status');
    contenedor.setAttribute('aria-live', 'polite');
    document.body.appendChild(contenedor);
  }
  const aviso = document.createElement('div');
  aviso.className = `aviso-toast ${tipo}`;
  aviso.setAttribute('role', tipo === 'error' ? 'alert' : 'status');
  aviso.textContent = texto;
  contenedor.appendChild(aviso);
  setTimeout(() => aviso.remove(), 6000);
}

function confirmarAccion(opciones = {}) {
  const titulo = opciones.titulo || 'Confirmar acción';
  const mensaje = opciones.mensaje || '¿Deseas continuar?';
  const textoConfirmar = opciones.textoConfirmar || 'Confirmar';
  const peligro = opciones.peligro !== false;

  return new Promise((resolver) => {
    const dialogo = document.createElement('dialog');
    dialogo.className = 'dialogo';

    const cabecera = document.createElement('div');
    cabecera.className = 'dialogo-cabecera';
    const h2 = document.createElement('h2');
    h2.textContent = titulo;
    cabecera.appendChild(h2);

    const cuerpo = document.createElement('div');
    cuerpo.className = 'dialogo-cuerpo';
    const parrafo = document.createElement('p');
    parrafo.textContent = mensaje;
    cuerpo.appendChild(parrafo);

    const acciones = document.createElement('div');
    acciones.className = 'dialogo-acciones';
    const cancelar = document.createElement('button');
    cancelar.type = 'button';
    cancelar.className = 'boton-secundario';
    cancelar.textContent = 'Cancelar';
    const confirmar = document.createElement('button');
    confirmar.type = 'button';
    confirmar.className = peligro ? 'boton-peligro' : 'boton-primario';
    confirmar.textContent = textoConfirmar;
    acciones.append(cancelar, confirmar);
    dialogo.append(cabecera, cuerpo, acciones);

    const cerrar = (valor) => {
      if (dialogo.open) dialogo.close();
      dialogo.remove();
      resolver(valor);
    };

    cancelar.addEventListener('click', () => cerrar(false));
    confirmar.addEventListener('click', () => cerrar(true));
    dialogo.addEventListener('cancel', (evento) => {
      evento.preventDefault();
      cerrar(false);
    });

    document.body.appendChild(dialogo);
    dialogo.showModal();
    cancelar.focus();
  });
}

const ETIQUETAS_ESTADO = {
  'En espera': 'En espera',
  'En atencion': 'En atención',
  'Atendido': 'Atendido'
};

function formatearEstado(estado) {
  return ETIQUETAS_ESTADO[estado] || estado || '—';
}

function formatearFechaHora(iso) {
  if (!iso) return '—';
  const fecha = new Date(iso);
  if (Number.isNaN(fecha.getTime())) return '—';
  return fecha.toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short', hour12: false });
}

function formatearFechaSolo(iso) {
  if (!iso) return '—';
  const fecha = new Date(`${String(iso).slice(0, 10)}T00:00:00`);
  if (Number.isNaN(fecha.getTime())) return '—';
  return fecha.toLocaleDateString('es-CO', { dateStyle: 'medium' });
}

function calcularEdad(fechaNacimiento) {
  if (!fechaNacimiento) return null;
  const nacimiento = new Date(`${String(fechaNacimiento).slice(0, 10)}T00:00:00`);
  if (Number.isNaN(nacimiento.getTime())) return null;
  const hoy = new Date();
  let edad = hoy.getFullYear() - nacimiento.getFullYear();
  const mes = hoy.getMonth() - nacimiento.getMonth();
  if (mes < 0 || (mes === 0 && hoy.getDate() < nacimiento.getDate())) edad--;
  return edad >= 0 && edad < 130 ? edad : null;
}

/* ---------------------------------------------------------------------
   Menu dinamico segun las actividades permitidas del usuario
   --------------------------------------------------------------------- */

const ETIQUETAS_ACTIVIDAD = {
  registrar_paciente: 'Pacientes',
  crear_triage: 'Nuevo triage',
  seguimiento_paciente: 'Seguimiento',
  lista_espera: 'Lista de espera',
  auditoria: 'Auditoría',
  gestion_roles: 'Administración',
  aprobar_cuentas: 'Aprobaciones',
  dashboard_analitica: 'Analítica',
  dashboard: 'Analítica'
};

function pintarNav(idContenedor, enlaceActual) {
  const sesion = obtenerSesion();
  const contenedor = document.getElementById(idContenedor);
  if (!sesion || !contenedor) return;

  // 'Seguimiento' no se muestra en el menú: no es un destino válido por sí solo,
  // necesita idRegistro en la URL. Se llega desde la lista de espera.
  const visibles = sesion.actividades.filter((a) => a.nombreActividad !== 'seguimiento_paciente');

  const enlaces = visibles.map((a) => {
    const etiqueta = ETIQUETAS_ACTIVIDAD[a.nombreActividad] || a.nombreActividad;
    const activo = a.enlace === enlaceActual;
    return `<a href="${escaparHtml(enlaceSeguro(a.enlace))}"${activo ? ' class="activo" aria-current="page"' : ''}>${escaparHtml(etiqueta)}</a>`;
  }).join('') + `<a href="cuenta.html"${enlaceActual === 'cuenta.html' ? ' class="activo" aria-current="page"' : ''}>Mi cuenta</a>`;

  contenedor.innerHTML = `
    <button type="button" class="menu-toggle" aria-expanded="false" aria-controls="navEnlaces">
      <span aria-hidden="true">☰</span> Menú
    </button>
    <div class="nav-enlaces" id="navEnlaces">${enlaces}</div>`;

  const boton = contenedor.querySelector('.menu-toggle');
  const panel = contenedor.querySelector('.nav-enlaces');
  if (!boton || !panel) return;

  const cerrar = () => {
    panel.classList.remove('abierto');
    boton.setAttribute('aria-expanded', 'false');
  };

  boton.addEventListener('click', () => {
    const abierto = panel.classList.toggle('abierto');
    boton.setAttribute('aria-expanded', String(abierto));
    if (abierto) panel.querySelector('a')?.focus();
  });

  panel.addEventListener('click', (evento) => {
    if (evento.target.closest('a')) cerrar();
  });

  document.addEventListener('keydown', (evento) => {
    if (evento.key === 'Escape') cerrar();
  });

  document.addEventListener('click', (evento) => {
    if (!contenedor.contains(evento.target)) cerrar();
  });
}

/* ---------------------------------------------------------------------
   ms-analitica
   --------------------------------------------------------------------- */

function parametrosDashboard(filtros) {
  const parametros = new URLSearchParams({ desde: filtros.desde, hasta: filtros.hasta });
  if (filtros.localidad) parametros.set('localidad', filtros.localidad);
  if (filtros.turno) parametros.set('turno', filtros.turno);
  return parametros.toString();
}

async function obtenerResumenDashboard(filtros, signal) {
  return llamarApi(`/ms-analitica/dashboard/resumen?${parametrosDashboard(filtros)}`, { signal });
}

async function obtenerLlegadasDashboard(filtros, signal) {
  return llamarApi(`/ms-analitica/dashboard/llegadas?${parametrosDashboard(filtros)}`, { signal });
}

async function obtenerCapacidadDashboard(filtros, signal) {
  return llamarApi(`/ms-analitica/dashboard/capacidad?${parametrosDashboard(filtros)}`, { signal });
}

/* ---------------------------------------------------------------------
   ms-seguridad — administracion (perfiles, actividades, usuarios)
   --------------------------------------------------------------------- */

async function listarPerfiles() { return llamarApi('/ms-seguridad/admin/perfiles'); }
async function crearPerfil(datos) { return llamarApi('/ms-seguridad/admin/perfiles', { method: 'POST', body: JSON.stringify(datos) }); }
async function actualizarPerfil(id, datos) { return llamarApi(`/ms-seguridad/admin/perfiles/${id}`, { method: 'PUT', body: JSON.stringify(datos) }); }
async function eliminarPerfil(id) { return llamarApi(`/ms-seguridad/admin/perfiles/${id}`, { method: 'DELETE' }); }

async function listarActividades() { return llamarApi('/ms-seguridad/admin/actividades'); }
async function crearActividad(datos) { return llamarApi('/ms-seguridad/admin/actividades', { method: 'POST', body: JSON.stringify(datos) }); }
async function actualizarActividad(id, datos) { return llamarApi(`/ms-seguridad/admin/actividades/${id}`, { method: 'PUT', body: JSON.stringify(datos) }); }
async function eliminarActividad(id) { return llamarApi(`/ms-seguridad/admin/actividades/${id}`, { method: 'DELETE' }); }

async function listarPerfilActividad() { return llamarApi('/ms-seguridad/admin/perfil-actividad'); }
async function asignarActividad(idPerfil, idActividad) {
  return llamarApi('/ms-seguridad/admin/perfil-actividad', { method: 'POST', body: JSON.stringify({ idPerfil, idActividad }) });
}
async function quitarActividad(id) { return llamarApi(`/ms-seguridad/admin/perfil-actividad/${id}`, { method: 'DELETE' }); }

async function listarPersonal() { return llamarApi('/ms-seguridad/admin/personal'); }
async function crearPersonal(datos) { return llamarApi('/ms-seguridad/admin/personal', { method: 'POST', body: JSON.stringify(datos) }); }
async function actualizarPersonal(id, datos) { return llamarApi(`/ms-seguridad/admin/personal/${id}`, { method: 'PUT', body: JSON.stringify(datos) }); }
async function cambiarEstadoPersonal(id, idEstado) {
  return llamarApi(`/ms-seguridad/admin/personal/${id}/estado?idEstado=${idEstado}`, { method: 'PUT' });
}
async function listarPendientes() { return llamarApi('/ms-seguridad/admin/personal/pendientes'); }
async function aprobarPersonal(id) {
  return llamarApi(`/ms-seguridad/admin/personal/${id}/aprobar`, { method: 'POST' });
}
async function rechazarPersonal(id, motivo) {
  return llamarApi(`/ms-seguridad/admin/personal/${id}/rechazar`, {
    method: 'POST', body: JSON.stringify({ motivo: motivo || null })
  });
}
async function reenviarActivacion(id) {
  return llamarApi(`/ms-seguridad/admin/personal/${id}/activacion`, { method: 'POST' });
}
async function restablecerPersonal(id) {
  return llamarApi(`/ms-seguridad/admin/personal/${id}/restablecer`, { method: 'POST' });
}
async function generarClaveTemporal(id) {
  return llamarApi(`/ms-seguridad/admin/personal/${id}/clave-temporal`, { method: 'POST' });
}
async function listarTurnos() { return llamarApi('/ms-seguridad/admin/turnos'); }
async function crearTurno(datos) { return llamarApi('/ms-seguridad/admin/turnos', { method: 'POST', body: JSON.stringify(datos) }); }
async function actualizarTurno(id, datos) { return llamarApi(`/ms-seguridad/admin/turnos/${id}`, { method: 'PUT', body: JSON.stringify(datos) }); }
async function cambiarEstadoTurno(id, idEstado) { return llamarApi(`/ms-seguridad/admin/turnos/${id}/estado?idEstado=${idEstado}`, { method: 'PUT' }); }

async function listarAuditoria() { return llamarApi('/ms-seguridad/admin/auditoria'); }
async function registrarAuditoria(accion, detalle) {
  try {
    await llamarApi('/ms-seguridad/auditoria/registrar', {
      method: 'POST',
      body: JSON.stringify({ accion, detalle })
    });
  } catch (error) {
    console.error('No se pudo registrar en auditoria:', error.message);
    // No relanzamos el error: que falle el log de auditoria no debe tumbar la accion principal del usuario
  }
}


async function preguntarAgente(payload) {
  return llamarApi('/ms-ia-agente/agente/preguntar', { method: 'POST', body: JSON.stringify(payload) });
}

async function transcribirAudio(blob) {
  return llamarApi('/ms-ia-agente/agente/transcribir', {
    method: 'POST',
    headers: { 'Content-Type': blob.type || 'audio/webm' },
    body: blob
  });
}