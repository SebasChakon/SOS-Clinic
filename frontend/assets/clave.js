/* =====================================================================
   SOS · clave.js
   Reglas de clave compartidas por las pantallas de alta, activacion,
   recuperacion y cuenta. El backend sigue siendo la autoridad final.
   ===================================================================== */

const POLITICA_CLAVE = {
  longitudMinima: 8,
  longitudMaxima: 64,
  simbolos: '!?#$%.*@_+-'
};

const PALABRAS_COMUNES_CLAVE = [
  'password', 'contrasena', 'contrasenya', 'passw0rd', 'clave',
  'admin', 'administrador', 'usuario', 'user', 'login', 'root',
  'qwerty', 'asdf', 'zxcv', 'abc123', '123456', '12345678', '123456789',
  '111111', '000000', '654321', 'iloveyou', 'teamo', 'amor',
  'welcome', 'bienvenido', 'bienvenida', 'monkey', 'dragon', 'master',
  'sunshine', 'princess', 'shadow', 'superman', 'batman', 'football',
  'baseball', 'letmein', 'trustno1', 'hello', 'hola', 'secret', 'secreto',
  'sosclinic', 'hospital', 'clinica', 'clinic', 'medico', 'medica',
  'doctor', 'doctora', 'enfermera', 'enfermero', 'triage', 'urgencias',
  'urgencia', 'paciente', 'salud', 'colombia', 'bogota', 'sistema',
  'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio',
  'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'
];

const SECUENCIAS_TECLADO_CLAVE = [
  'qwerty', 'qwert', 'asdf', 'asdfg', 'zxcv', 'zxcvb',
  'qaz', 'wsx', 'edc', 'rfv', 'tgb', 'yhn', 'ujm',
  'qwe', 'wer', 'ert', 'rty', 'tyu', 'yui', 'uio', 'iop',
  'asd', 'sdf', 'dfg', 'fgh', 'ghj', 'hjk', 'jkl',
  'zxc', 'xcv', 'cvb', 'vbn', 'bnm'
];

function normalizarClave(valor) {
  const sinTildes = String(valor ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase();
  let salida = '';
  for (const c of sinTildes) {
    if (c === '@') salida += 'a';
    else if (c === '0') salida += 'o';
    else if (c === '1') salida += 'i';
    else if (c === '3') salida += 'e';
    else if (c === '$') salida += 's';
    else if (c === '#') salida += 'h';
    else if (c === '!') salida += 'i';
    else if (c === '+') salida += 't';
    else if (/[a-z0-9]/.test(c)) salida += c;
  }
  return salida;
}

function tieneSecuenciaClave(clave) {
  const texto = String(clave ?? '').toLowerCase();
  for (let i = 0; i + 2 < texto.length; i++) {
    const a = texto.charCodeAt(i);
    const b = texto.charCodeAt(i + 1);
    const c = texto.charCodeAt(i + 2);
    const digito = a >= 48 && a <= 57 && b >= 48 && b <= 57 && c >= 48 && c <= 57;
    const letra = a >= 97 && a <= 122 && b >= 97 && b <= 122 && c >= 97 && c <= 122;
    if (!digito && !letra) continue;
    if ((b - a === 1 && c - b === 1) || (b - a === -1 && c - b === -1)) return true;
  }
  return false;
}

function contieneDatoPersonalClave(normalizada, contexto) {
  if (!contexto) return false;
  const datos = new Set();
  const agregar = (valor) => {
    if (!valor) return;
    const normal = normalizarClave(valor);
    if (normal.length >= 3) datos.add(normal);
    normal.split(/[^a-z0-9]+/).forEach((parte) => {
      if (parte.length >= 3) {
        datos.add(parte);
        datos.add(parte.split('').reverse().join(''));
      }
    });
  };
  agregar(contexto.nombre);
  agregar(contexto.apellido);
  agregar(contexto.usuario);
  agregar(contexto.email);
  if (contexto.email && contexto.email.includes('@')) {
    agregar(contexto.email.split('@')[0]);
  }
  agregar(`${contexto.nombre || ''}${contexto.apellido || ''}`);
  agregar(`${contexto.apellido || ''}${contexto.nombre || ''}`);
  agregar(contexto.cedula);
  agregar(contexto.telefono);
  const ultimosCuatro = (valor) => {
    if (!valor) return;
    const digitos = String(valor).replace(/\D/g, '');
    if (digitos.length >= 4) datos.add(digitos.slice(-4));
  };
  ultimosCuatro(contexto.cedula);
  ultimosCuatro(contexto.telefono);
  return [...datos].some((dato) => dato && normalizada.includes(dato));
}

/**
 * Reglas visibles del checklist. `ok === null` significa que la regla
 * no se puede comprobar en el navegador y queda a cargo del backend.
 */
function reglasClave(clave, contexto = {}) {
  const valor = String(clave ?? '');
  const normalizada = normalizarClave(valor);
  const simbolos = POLITICA_CLAVE.simbolos;
  const coincideSimbolo = [...simbolos].some((s) => valor.includes(s));

  const reglas = [
    { texto: `Minimo ${POLITICA_CLAVE.longitudMinima} caracteres`, ok: valor.length >= POLITICA_CLAVE.longitudMinima },
    { texto: 'Una letra mayuscula', ok: /[A-Z]/.test(valor) },
    { texto: 'Una letra minuscula', ok: /[a-z]/.test(valor) },
    { texto: 'Un numero', ok: /[0-9]/.test(valor) },
    { texto: `Un simbolo permitido (${simbolos})`, ok: coincideSimbolo },
    { texto: 'Sin tres caracteres iguales seguidos', ok: !/(.)\1\1/.test(valor) },
    { texto: 'Sin secuencias (abc, 123, cba, 321)', ok: !tieneSecuenciaClave(valor) },
    { texto: 'Sin secuencias de teclado (qwerty, asdf)', ok: !SECUENCIAS_TECLADO_CLAVE.some((s) => valor.toLowerCase().includes(s)) },
    { texto: 'Sin palabras comunes ni el nombre del sistema', ok: !PALABRAS_COMUNES_CLAVE.some((p) => normalizada.includes(p)) },
    { texto: 'Sin tus datos personales', ok: contexto && Object.keys(contexto).length ? !contieneDatoPersonalClave(normalizada, contexto) : null },
    { texto: `Solo letras sin tilde, numeros y ${simbolos}`, ok: valor === '' ? null : /^[A-Za-z0-9!?#$%.*@_+-]+$/.test(valor) },
    { texto: `Maximo ${POLITICA_CLAVE.longitudMaxima} caracteres`, ok: valor.length <= POLITICA_CLAVE.longitudMaxima }
  ];
  return reglas;
}

function erroresClave(clave, contexto = {}) {
  const valor = String(clave ?? '');
  const errores = [];
  if (!valor) return ['La clave es obligatoria.'];
  if (valor.length < POLITICA_CLAVE.longitudMinima) {
    errores.push(`Debe tener al menos ${POLITICA_CLAVE.longitudMinima} caracteres.`);
  }
  if (valor.length > POLITICA_CLAVE.longitudMaxima) {
    errores.push(`Debe tener como maximo ${POLITICA_CLAVE.longitudMaxima} caracteres.`);
  }
  if (!/^[A-Za-z0-9!?#$%.*@_+-]+$/.test(valor)) {
    errores.push(`Solo se permiten letras sin tilde, numeros y los simbolos ${POLITICA_CLAVE.simbolos}.`);
  }
  if (!/[A-Z]/.test(valor)) errores.push('Debe incluir al menos una letra mayuscula.');
  if (!/[a-z]/.test(valor)) errores.push('Debe incluir al menos una letra minuscula.');
  if (!/[0-9]/.test(valor)) errores.push('Debe incluir al menos un numero.');
  if (![...POLITICA_CLAVE.simbolos].some((s) => valor.includes(s))) {
    errores.push(`Debe incluir al menos un simbolo de ${POLITICA_CLAVE.simbolos}.`);
  }
  if (/(.)\1\1/.test(valor)) errores.push('No repitas el mismo caracter tres veces seguidas.');
  if (tieneSecuenciaClave(valor)) errores.push('No uses secuencias como abc, 123, cba o 321.');
  if (SECUENCIAS_TECLADO_CLAVE.some((s) => valor.toLowerCase().includes(s))) {
    errores.push('No uses secuencias de teclado como qwerty o asdf.');
  }
  const normalizada = normalizarClave(valor);
  if (PALABRAS_COMUNES_CLAVE.some((p) => normalizada.includes(p))) {
    errores.push('La clave contiene una palabra comun o el nombre del sistema.');
  }
  if (contexto && Object.keys(contexto).length && contieneDatoPersonalClave(normalizada, contexto)) {
    errores.push('La clave no puede contener tus datos personales.');
  }
  return errores;
}

function pintarReglasClave(idContenedor, clave, contexto = {}) {
  const contenedor = document.getElementById(idContenedor);
  if (!contenedor) return;
  const valor = String(clave ?? '');
  contenedor.innerHTML = reglasClave(valor, contexto).map((regla) => {
    const estado = valor === '' || regla.ok === null ? '' : (regla.ok === false ? 'pendiente' : 'ok');
    const icono = valor === '' || regla.ok === null ? '•' : (regla.ok === false ? '✗' : '✓');
    return `<li class="requisito ${estado}"><span aria-hidden="true">${icono}</span><span>${regla.texto}</span></li>`;
  }).join('');
}

function pintarFuerzaClave(idContenedor, clave) {
  const contenedor = document.getElementById(idContenedor);
  if (!contenedor) return;
  const valor = String(clave ?? '');
  let nivel = 0;
  if (valor.length >= POLITICA_CLAVE.longitudMinima) nivel++;
  if (valor.length >= 12) nivel++;
  if (/[A-Z]/.test(valor) && /[a-z]/.test(valor) && /[0-9]/.test(valor) && /[^A-Za-z0-9]/.test(valor)) nivel++;
  if (valor.length >= 16) nivel++;
  nivel = Math.min(4, nivel);
  const etiquetas = ['', 'Debil', 'Media', 'Fuerte', 'Muy fuerte'];
  contenedor.className = `fuerza nivel-${nivel}`;
  contenedor.innerHTML = '<span></span><span></span><span></span><span></span>';
  contenedor.setAttribute('aria-label', valor ? `Fuerza de la clave: ${etiquetas[nivel]}` : 'Fuerza de la clave');
  const salida = document.getElementById(`${idContenedor}-texto`);
  if (salida) salida.textContent = valor ? etiquetas[nivel] : '';
}

function conectarClave(inputId, reglasId, fuerzaId, contextoFn) {
  const input = document.getElementById(inputId);
  if (!input) return;
  const actualizar = () => {
    const contexto = typeof contextoFn === 'function' ? contextoFn() : (contextoFn || {});
    if (reglasId) pintarReglasClave(reglasId, input.value, contexto);
    if (fuerzaId) pintarFuerzaClave(fuerzaId, input.value);
  };
  input.addEventListener('input', actualizar);
  actualizar();
}
