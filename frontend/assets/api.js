/* =====================================================================
   SOS · api.js
   Funciones compartidas para hablarle al backend a traves del gateway.
   Ninguna vista deberia usar fetch() directamente: todas pasan por aqui.
   ===================================================================== */

const BASE_URL = 'http://localhost:8080';

/* ---------------------------------------------------------------------
   Sesion (token JWT guardado en el navegador)
   --------------------------------------------------------------------- */

function guardarSesion(datosLogin) {
  sessionStorage.setItem('sos_token', datosLogin.token);
  sessionStorage.setItem('sos_usuario', datosLogin.usuario);
  sessionStorage.setItem('sos_perfil', datosLogin.perfil);
  sessionStorage.setItem('sos_id_personal', datosLogin.idPersonal);
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
    idPersonal: sessionStorage.getItem('sos_id_personal')
  };
}

function haySesion() {
  return obtenerToken() !== null;
}

function cerrarSesion() {
  sessionStorage.removeItem('sos_token');
  sessionStorage.removeItem('sos_usuario');
  sessionStorage.removeItem('sos_perfil');
  sessionStorage.removeItem('sos_id_personal');
  window.location.href = 'index.html';
}

/**
 * Llama esto al cargar cualquier vista que NO sea el login.
 * Si no hay sesion activa, redirige automaticamente a index.html.
 */
function exigirSesion() {
  if (!haySesion()) {
    window.location.href = 'index.html';
  }
}

/* ---------------------------------------------------------------------
   Funcion base: todas las llamadas al backend pasan por aqui
   --------------------------------------------------------------------- */

async function llamarApi(ruta, opciones = {}) {
  const token = obtenerToken();

  const headers = {
    'Content-Type': 'application/json',
    ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
    ...opciones.headers
  };

  let respuesta;
  try {
    respuesta = await fetch(`${BASE_URL}${ruta}`, { ...opciones, headers });
  } catch (error) {
    throw new Error('No se pudo contactar al servidor. Verifica que eureka-server, ms-gateway y los microservicios esten corriendo.');
  }

  if (respuesta.status === 401) {
    cerrarSesion();
    throw new Error('Sesion expirada, vuelve a iniciar sesion');
  }

  const datos = await respuesta.json().catch(() => null);

  if (!respuesta.ok) {
    const mensaje = (datos && typeof datos === 'string')
      ? datos
      : (datos && datos.message) || 'Error en la solicitud';
    throw new Error(mensaje);
  }

  return datos;
}

/* ---------------------------------------------------------------------
   ms-seguridad
   --------------------------------------------------------------------- */

async function login(usuario, clave) {
  const datos = await llamarApi('/ms-seguridad/auth/login', {
    method: 'POST',
    body: JSON.stringify({ usuario, clave })
  });
  guardarSesion(datos);
  return datos;
}

/* ---------------------------------------------------------------------
   ms-pacientes
   --------------------------------------------------------------------- */

async function crearPaciente(paciente) {
  return llamarApi('/ms-pacientes/pacientes', {
    method: 'POST',
    body: JSON.stringify(paciente)
  });
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

/* ---------------------------------------------------------------------
   ms-triage
   --------------------------------------------------------------------- */

async function registrarTriage(registro) {
  return llamarApi('/ms-triage/triage', {
    method: 'POST',
    body: JSON.stringify(registro)
  });
}

async function listaPriorizacion() {
  return llamarApi('/ms-triage/triage/prioridad');
}

async function marcarEnAtencion(idRegistro) {
  return llamarApi(`/ms-triage/triage/${idRegistro}/en-atencion`, { method: 'PUT' });
}

async function marcarAtendido(idRegistro) {
  return llamarApi(`/ms-triage/triage/${idRegistro}/atendido`, { method: 'PUT' });
}

async function obtenerRegistroTriage(idRegistro) {
  return llamarApi(`/ms-triage/triage/${idRegistro}`);
}
/* ---------------------------------------------------------------------
   Utilidad comun para mostrar mensajes de exito/error en cualquier vista
   Requiere un elemento <div class="mensaje" id="mensaje"></div> en el HTML
   --------------------------------------------------------------------- */

function mostrarMensaje(idElemento, texto, tipo) {
  const el = document.getElementById(idElemento);
  el.className = `mensaje ${tipo}`;
  el.textContent = texto;
}

function limpiarMensaje(idElemento) {
  const el = document.getElementById(idElemento);
  el.className = 'mensaje';
  el.textContent = '';
}