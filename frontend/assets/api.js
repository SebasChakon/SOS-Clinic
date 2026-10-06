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

function cerrarSesion() {
  sessionStorage.clear();
  window.location.href = 'index.html';
}

/**
 * Elige la primera pantalla "navegable" de una lista de actividades,
 * saltandose 'seguimiento_paciente' porque esa nunca es un destino valido
 * por si sola (necesita un idRegistro en la URL para funcionar).
 */
function primerDestinoValido(actividades) {
  const candidato = actividades.find((a) => a.nombreActividad !== 'seguimiento_paciente');
  return candidato ? candidato.enlace : (actividades[0] ? actividades[0].enlace : null);
}

/**
 * Llama esto al cargar cualquier vista que NO sea el login.
 * Redirige a index.html si no hay sesion, o a la pantalla correcta
 * si el usuario no tiene permiso para ver esta pagina.
 */
function exigirSesion() {
  const sesion = obtenerSesion();

  if (!sesion) {
    window.location.href = 'index.html';
    return;
  }

  if (sesion.perfil === 'administrador') return;

  const paginaActual = window.location.pathname.split('/').pop();
  const tienePermiso = sesion.actividades.some((a) => a.enlace === paginaActual);

  if (!tienePermiso) {
    if (paginaActual === 'index.html' || paginaActual === '') return; // evita loop con el login
    const destino = primerDestinoValido(sesion.actividades);
    if (destino) {
      window.location.href = destino;
    } else {
      alert('Tu usuario no tiene ninguna pantalla asignada. Contacta al coordinador de turnos.');
      cerrarSesion();
    }
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
    if (token) {
      // Ya teniamos una sesion activa y el token dejo de ser valido: si expira de verdad
      cerrarSesion();
      throw new Error('Sesion expirada, vuelve a iniciar sesion');
    }
    // No habia token (ej. intento de login fallido): dejamos que el flujo normal de abajo
    // lea el mensaje real del backend ("Fuera de tu horario...", "Usuario o clave incorrectos", etc.)
  }

  const datos = await respuesta.json().catch(() => null);

  if (!respuesta.ok) {
    const mensaje = (datos && typeof datos === 'string')
      ? datos
      : (datos && (datos.message || datos.detail)) || 'Error en la solicitud';
    throw new Error(mensaje);
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
      registrarAuditoria('LOGIN', 'Inicio de sesion');
    }
    return datos;
  }

  async function verificarCodigo2fa(usuario, codigo) {
    const datos = await llamarApi('/ms-seguridad/auth/verificar-2fa', {
      method: 'POST', body: JSON.stringify({ usuario, codigo })
    });
    guardarSesion(datos);
    registrarAuditoria('LOGIN', 'Inicio de sesion (2FA)');
    return datos;
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
  const resultado = await llamarApi('/ms-pacientes/pacientes/signos-vitales', {
    method: 'POST',
    body: JSON.stringify(signos)
  });
  registrarAuditoria('CREAR', `Signos vitales, registro #${signos.idRegistro}`);
  return resultado;
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
  'Sala de espera', 'Rayos X', 'Laboratorio', 'Ginecología', 'Pediatría', 'Cirugía', 'UCI', 'Urgencias', 'Observación', 'Farmacia', 'Sala de espera', 'Otro'
];

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

/* ---------------------------------------------------------------------
   Menu dinamico segun las actividades permitidas del usuario
   --------------------------------------------------------------------- */

const ETIQUETAS_ACTIVIDAD = {
  registrar_paciente: 'Pacientes',
  crear_triage: 'Nuevo triage',
  seguimiento_paciente: 'Seguimiento',
  lista_espera: 'Lista de espera',
  auditoria: 'Auditoría',
  gestion_roles: 'Roles',
  enfermedades_crue: 'Enfermedades CRUE'
};

function pintarNav(idContenedor, enlaceActual) {
  const sesion = obtenerSesion();
  const contenedor = document.getElementById(idContenedor);
  if (!sesion || !contenedor) return;

  // 'Seguimiento' solo se muestra en el menu si es la pagina actual,
  // porque no es un destino valido por si sola (necesita idRegistro en la URL)
  const visibles = sesion.actividades.filter((a) => {
    if (a.nombreActividad === 'seguimiento_paciente') {
      return a.enlace === enlaceActual;
    }
    return true;
  });

  contenedor.innerHTML = visibles.map((a) => {
    const etiqueta = ETIQUETAS_ACTIVIDAD[a.nombreActividad] || a.nombreActividad;
    const clase = a.enlace === enlaceActual ? 'activo' : '';
    return `<a href="${a.enlace}" class="${clase}">${etiqueta}</a>`;
  }).join('');
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