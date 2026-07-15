/**
 * AmiDog Veterinaria — Lógica del Calendario de Citas
 * Maneja la navegación por meses, selección de fecha y horarios.
 */

// =============================================
// Configuración
// =============================================

const HORARIOS_DISPONIBLES = [
  '09:00', '09:30', '10:00', '10:30',
  '11:00', '11:30', '12:00', '14:00',
  '14:30', '15:00', '15:30', '16:00',
  '16:30', '17:00', '17:30', '18:00',
];

// Días no disponibles: 0 = Domingo, 1 = Lunes (sin atención lunes en este ejemplo)
const DIAS_NO_DISPONIBLES = [0]; // Solo domingos bloqueados

// =============================================
// Estado
// =============================================
let fechaActual = new Date();
let mesVisto = new Date(fechaActual.getFullYear(), fechaActual.getMonth(), 1);
let fechaSeleccionada = null;
let horaSeleccionada = null;

// =============================================
// Referencias DOM
// =============================================
const mesTexto = document.getElementById('mes-texto');
const diasCalendario = document.getElementById('dias-calendario');
const horariosContainer = document.getElementById('horarios-container');
const btnMesAnterior = document.getElementById('btn-mes-anterior');
const btnMesSiguiente = document.getElementById('btn-mes-siguiente');
const btnContinuar = document.getElementById('btn-continuar');
const inputFecha = document.getElementById('input-fecha');
const inputHora = document.getElementById('input-hora');

// =============================================
// Helpers
// =============================================

const MESES = [
  'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
  'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'
];

function formatearFecha(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function esPasado(year, month, day) {
  const hoy = new Date();
  hoy.setHours(0, 0, 0, 0);
  return new Date(year, month, day) < hoy;
}

function esDiaNoDisponible(year, month, day) {
  const diaSemana = new Date(year, month, day).getDay();
  return DIAS_NO_DISPONIBLES.includes(diaSemana);
}

// =============================================
// Renderizar calendario
// =============================================

function renderCalendario() {
  const year = mesVisto.getFullYear();
  const month = mesVisto.getMonth();

  mesTexto.textContent = `${MESES[month]} ${year}`;

  const primerDia = new Date(year, month, 1).getDay(); // 0=Dom
  // Convertir de Domingo-primero a Lunes-primero
  const offset = (primerDia === 0) ? 6 : primerDia - 1;
  const diasEnMes = new Date(year, month + 1, 0).getDate();

  diasCalendario.innerHTML = '';

  // Celdas vacías al inicio
  for (let i = 0; i < offset; i++) {
    const celda = document.createElement('div');
    celda.className = 'col';
    diasCalendario.appendChild(celda);
  }

  // Días del mes
  for (let d = 1; d <= diasEnMes; d++) {
    const celda = document.createElement('div');
    celda.className = 'col text-center';

    const btn = document.createElement('button');
    btn.className = 'dia-btn';
    btn.textContent = d;
    btn.type = 'button';

    const pasado = esPasado(year, month, d);
    const noDisponible = esDiaNoDisponible(year, month, d);

    if (pasado || noDisponible) {
      btn.disabled = true;
      if (noDisponible && !pasado) btn.title = 'Sin atención este día';
    } else {
      const fechaStr = formatearFecha(new Date(year, month, d));
      btn.setAttribute('data-fecha', fechaStr);

      if (fechaSeleccionada === fechaStr) {
        btn.classList.add('seleccionado');
      }

      btn.addEventListener('click', () => seleccionarFecha(fechaStr, btn));
    }

    celda.appendChild(btn);
    diasCalendario.appendChild(celda);
  }

  // Deshabilitar botón de mes anterior si ya estamos en el mes actual
  const hoy = new Date();
  btnMesAnterior.disabled = (mesVisto.getFullYear() === hoy.getFullYear() && mesVisto.getMonth() === hoy.getMonth());
}

// =============================================
// Selección de fecha
// =============================================

function seleccionarFecha(fechaStr, btnClickeado) {
  fechaSeleccionada = fechaStr;
  horaSeleccionada = null;
  inputFecha.value = fechaStr;
  inputHora.value = '';

  // Resaltar botón seleccionado
  document.querySelectorAll('.dia-btn').forEach(b => b.classList.remove('seleccionado'));
  btnClickeado.classList.add('seleccionado');

  renderHorarios();
  actualizarBotonContinuar();
}

// =============================================
// Renderizar horarios
// =============================================

function renderHorarios() {
  horariosContainer.innerHTML = '';

  if (!fechaSeleccionada) return;

  HORARIOS_DISPONIBLES.forEach(hora => {
    const celda = document.createElement('div');
    celda.className = 'col';

    const btn = document.createElement('button');
    btn.className = 'horario-btn';
    btn.textContent = hora;
    btn.type = 'button';

    if (horaSeleccionada === hora) {
      btn.classList.add('seleccionado');
    }

    btn.addEventListener('click', () => seleccionarHora(hora, btn));
    celda.appendChild(btn);
    horariosContainer.appendChild(celda);
  });
}

// =============================================
// Selección de hora
// =============================================

function seleccionarHora(hora, btnClickeado) {
  horaSeleccionada = hora;
  inputHora.value = hora;

  document.querySelectorAll('.horario-btn').forEach(b => b.classList.remove('seleccionado'));
  btnClickeado.classList.add('seleccionado');

  actualizarBotonContinuar();
}

// =============================================
// Botón Continuar
// =============================================

function actualizarBotonContinuar() {
  btnContinuar.disabled = !(fechaSeleccionada && horaSeleccionada);
}

// =============================================
// Navegación de meses
// =============================================

btnMesAnterior.addEventListener('click', () => {
  mesVisto.setMonth(mesVisto.getMonth() - 1);
  fechaSeleccionada = null;
  horaSeleccionada = null;
  inputFecha.value = '';
  inputHora.value = '';
  horariosContainer.innerHTML = '';
  actualizarBotonContinuar();
  renderCalendario();
});

btnMesSiguiente.addEventListener('click', () => {
  mesVisto.setMonth(mesVisto.getMonth() + 1);
  fechaSeleccionada = null;
  horaSeleccionada = null;
  inputFecha.value = '';
  inputHora.value = '';
  horariosContainer.innerHTML = '';
  actualizarBotonContinuar();
  renderCalendario();
});

// =============================================
// Acción del botón Continuar
// =============================================

btnContinuar.addEventListener('click', () => {
  if (fechaSeleccionada && horaSeleccionada) {
    // Guardar en sessionStorage para la página de confirmación
    sessionStorage.setItem('cita_fecha', fechaSeleccionada);
    sessionStorage.setItem('cita_hora', horaSeleccionada);
    // Navegar a confirmación
    window.location.href = 'confirmacion.html';
  }
});

// =============================================
// Inicializar
// =============================================
renderCalendario();
actualizarBotonContinuar();
