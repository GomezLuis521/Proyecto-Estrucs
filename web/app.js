// Consola SavingCol: pinta el estado que entrega la API de Java y le envía las acciones.
// No guarda lógica de negocio: toda validación y cálculo ocurre en el servidor.
'use strict';

const VISTAS = [
  ['resumen', 'Resumen', 'M4 11l8-7 8 7v9h-5v-6H9v6H4z'],
  ['billeteras', 'Billeteras', 'M3 7h16a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2zM3 7l11-3v3M16 13.5h2'],
  ['movimientos', 'Movimientos', 'M4 6h16M4 12h16M4 18h10'],
  ['programadas', 'Programadas', 'M12 7v5l3 2M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z'],
  ['recompensas', 'Recompensas', 'M12 3l2.6 5.6 6 .7-4.5 4.1 1.2 6L12 16.5 6.7 19.4l1.2-6L3.4 9.3l6-.7z'],
  ['analitica', 'Analítica', 'M4 20V10M10 20V4M16 20v-7M22 20H2'],
  ['seguridad', 'Seguridad', 'M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6zM12 8v5M12 16v.5'],
  ['usuarios', 'Usuarios', 'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM2 21c0-4 3-6 7-6s7 2 7 6M17 4a4 4 0 0 1 0 7M19 15c2 .8 3 2.6 3 6'],
];

const SELLOS = {
  lista: 'Lista enlazada', pila: 'Pila', cola: 'Cola', prioridad: 'Cola de prioridad',
  arbol: 'Árbol AVL', hash: 'Tabla hash', grafo: 'Grafo',
};

let E = null;                       // último estado recibido del servidor
let operacion = 'recargar';         // pestaña del formulario Operar
let tipoProgramada = 'TRANSFERENCIA';
let editando = null;                // identificación del usuario en edición
let recien = false;               // la última acción cambió datos: se resalta lo nuevo una vez
const filtros = { billetera: '', tipo: '', estado: '', desde: '', hasta: '', texto: '', riesgo: '' };
const consulta = { desde: '', hasta: '', pmin: '', pmax: '' };

const medicion = { n: 20000, filas: null }; // última comparación de rendimiento pedida al servidor

// Agrupa las mediciones por operación y dibuja una barra relativa al más lento de cada grupo.
function tablaMediciones() {
  if (!medicion.filas) return '<p class="vacio">Pulsa Medir: el servidor ejecuta la misma tarea con lista, tabla hash, árbol AVL y cola de prioridad.</p>';
  const grupos = new Map();
  for (const m of medicion.filas) grupos.set(m.operacion, [...(grupos.get(m.operacion) ?? []), m]);
  return [...grupos].map(([op, ms]) => {
    const max = Math.max(...ms.map((m) => m.nanosegundos));
    return `<div class="panel-cuerpo"><h3 style="font-size:inherit;margin:0 0 .5rem">${h(op)}</h3><ul class="barras">${ms.map((m) =>
      `<li><span>${h(m.estructura)} <small class="tenue">${h(m.complejidad)}</small></span><span class="num">${(m.nanosegundos / 1e6).toLocaleString('es-CO', { maximumFractionDigits: 3 })} ms</span><span class="barra-valor"><i style="width:${Math.max(2, 100 * m.nanosegundos / max)}%"></i></span></li>`).join('')}</ul></div>`;
  }).join('');
}

const $ = (s) => document.querySelector(s);
const h = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const dinero = (n) => '$' + Number(n).toLocaleString('es-CO');
const numero = (n) => Number(n).toLocaleString('es-CO');
const fecha = (iso) => new Date(iso).toLocaleString('es-CO', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', hour12: false });
const fechaLarga = (iso) => new Date(iso).toLocaleString('es-CO', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false });
const vistaActual = () => (VISTAS.some((v) => v[0] === location.hash.slice(1)) ? location.hash.slice(1) : 'resumen');
let usuarioId = '';                 // usuario activo; se recuerda entre recargas si el navegador lo permite
try { usuarioId = localStorage.getItem('libreta.usuario') || ''; } catch { /* sin almacenamiento: el servidor elige el primero */ }

const sello = (clave, costo) => {
  const tono = clave === 'prioridad' ? 'cola' : clave;
  return `<span class="sello sello--${tono}"><b>${SELLOS[clave]}</b>${costo ? `<span>${costo}</span>` : ''}</span>`;
};
const panel = (titulo, selloHtml, cuerpo, extra = '') =>
  `<section class="panel"><header><h2>${titulo}</h2><span class="acciones">${extra}${selloHtml}</span></header>${cuerpo}</section>`;
const tabla = (cabeceras, filas, vacio, clase = '') => (filas.length
  ? `<div class="tabla-caja ${clase}"><table><thead><tr>${cabeceras.map((c) => `<th scope="col"${c.endsWith('#') ? ' class="num"' : ''}>${c.replace('#', '')}</th>`).join('')}</tr></thead><tbody>${filas.join('')}</tbody></table></div>`
  : `<p class="vacio">${vacio}</p>`);
const opciones = (lista, elegido) => lista.map(([v, t]) => `<option value="${h(v)}"${v === elegido ? ' selected' : ''}>${h(t)}</option>`).join('');
const campo = (rotulo, control) => `<label class="campo"><span>${rotulo}</span>${control}</label>`;

// ------------------------------------------------------------------ servidor

async function cargar() {
  const q = new URLSearchParams({ usuario: usuarioId });
  for (const [k, v] of Object.entries(consulta)) if (v !== '') q.set(k, v);
  try {
    const r = await fetch('/api/estado?' + q);
    const datos = await r.json();
    if (!r.ok) throw new Error(datos.error);
    E = datos;
    usuarioId = E.activo?.id ?? '';
    try { localStorage.setItem('libreta.usuario', usuarioId); } catch { /* sin almacenamiento: no se recuerda */ }
    pintar();
  } catch (e) {
    avisar(e.message || 'No se pudo conectar con el servidor. Verifica que el programa Java siga en ejecución.', true);
  }
}

async function accion(nombre, datos, boton) {
  if (boton) boton.disabled = true;
  try {
    const cuerpo = new URLSearchParams(datos);
    if (!cuerpo.has('usuario') && E?.activo) cuerpo.set('usuario', E.activo.id);
    const r = await fetch('/api/accion/' + nombre, { method: 'POST', body: cuerpo });
    const res = await r.json();
    avisar(res.ok ? res.mensaje : res.error, !res.ok);
    if (res.ok) { editando = null; recien = true; }
    await cargar(); // un rechazo también cambia el historial y las alertas
    return res.ok;
  } catch {
    avisar('No se pudo conectar con el servidor. Verifica que el programa Java siga en ejecución.', true);
    return false;
  } finally {
    if (boton?.isConnected) boton.disabled = false;
  }
}

window.libreta = { datos: () => E, accion: (nombre, datos) => accion(nombre, datos) };

let temporizador;
function avisar(mensaje, error) {
  const caja = $('#aviso');
  caja.hidden = true; // reinicia la animación y vuelve a anunciar el mensaje
  caja.textContent = mensaje;
  caja.className = 'aviso' + (error ? ' aviso--error' : '');
  caja.setAttribute('role', error ? 'alert' : 'status');
  requestAnimationFrame(() => { caja.hidden = false; });
  clearTimeout(temporizador);
  temporizador = setTimeout(() => { caja.hidden = true; }, error ? 9000 : 5000);
}

// ------------------------------------------------------------------ marco

function pintar() {
  const vista = vistaActual();
  $('#nav').innerHTML = VISTAS.map(([id, nombre, trazo]) =>
    `<a href="#${id}"${id === vista ? ' aria-current="page"' : ''}><svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="${trazo}"/></svg>${nombre}</a>`).join('');
  $('#usuario-activo').innerHTML = opciones(E.usuarios.map((u) => [u.id, `${u.nombre} · ${u.nivel}`]), E.activo?.id);
  $('#reloj').textContent = fechaLarga(E.reloj);
  $('#reloj').dateTime = E.reloj;
  document.title = `${VISTAS.find((v) => v[0] === vista)[1]} · SavingCol`;
  const vitrina = vista === 'billeteras' && E.activo ? $('iframe.vitrina') : null;
  if (vitrina?.contentWindow.recibir) { vitrina.contentWindow.recibir(E.activo, E.billeteras); return; }
  $('#contenido').classList.toggle('recien', recien);
  recien = false;
  $('#contenido').innerHTML = E.activo || vista === 'usuarios'
    ? PINTORES[vista]()
    : `<div class="cabecera"><div><h1>Aún no hay usuarios</h1><p>Registra el primero en <a href="#usuarios">Usuarios</a> para empezar a operar.</p></div></div>`;
}

const cabecera = (titulo, texto, extra = '') => `<div class="cabecera"><div><h1>${titulo}</h1><p>${texto}</p></div>${extra}</div>`;

// ------------------------------------------------------------------ renglones

const ESTADOS = { EXITOSA: ['Aplicada', 'bien'], REVERTIDA: ['Revertida', 'aviso'], RECHAZADA: ['Rechazada', 'mal'] };
const etiquetaRiesgo = (r, prefijo = '') => `<span class="etiqueta${r === 'MEDIO' ? ' etiqueta--aviso' : r === 'ALTO' ? ' etiqueta--mal etiqueta--llena' : ''}">${prefijo}${prefijo ? r.toLowerCase() : r[0] + r.slice(1).toLowerCase()}</span>`;

function concepto(t, yo) {
  const lado = (nombre, dueno, duenoId) => (nombre ? h(nombre) + (duenoId !== yo ? ` <span class="tenue">(${h(dueno)})</span>` : '') : null);
  const o = lado(t.origenNombre, t.origenPropietario, t.origenPropietarioId) ?? (t.origen ? h(t.origen) : null);
  const d = lado(t.destinoNombre, t.destinoPropietario, t.destinoPropietarioId) ?? (t.destino ? h(t.destino) : null);
  const ruta = o && d ? `${o} → ${d}` : (o ?? d ?? '');
  const notas = [
    t.programada ? `Programada ${h(t.referencia)}` : '',
    t.tipo === 'REVERSION' ? `Deshace ${h(t.referencia)}` : '',
    t.tipo === 'BENEFICIO' ? `Canje ${h(t.referencia)}` : '',
    t.comision && t.usuarioId === yo ? `Comisión ${dinero(t.comision)}` : '',
    t.motivo ? h(t.motivo) : '',
  ].filter(Boolean).join(' · ');
  return `<span class="concepto">${h(t.tipoEtiqueta)}</span> <span class="tenue">${ruta}</span>${notas ? `<small>${notas}</small>` : ''}`;
}

// Renglón de libreta: débito, crédito y saldo de la billetera del usuario después del movimiento.
function renglon(t, yo, compacto) {
  const sale = t.origenPropietarioId === yo, entra = t.destinoPropietarioId === yo;
  const saldo = sale ? t.saldoOrigen : t.saldoDestino;
  const [estado, tono] = ESTADOS[t.estado];
  const riesgo = t.riesgo !== 'NINGUNO' ? ' ' + etiquetaRiesgo(t.riesgo, 'Riesgo ') : '';
  if (compacto) return `<tr class="${t.estado.toLowerCase()}">
    <td class="num" style="text-align:left">${fecha(t.fecha)}</td>
    <td>${concepto(t, yo)}${t.estado !== 'EXITOSA' ? ` <span class="etiqueta etiqueta--${tono}">${estado}</span>` : ''}${riesgo}</td>
    <td class="num debito">${sale ? dinero(t.valor + t.comision) : ''}</td>
    <td class="num credito">${entra ? dinero(t.valor) : ''}</td>
    <td class="num">${t.estado !== 'RECHAZADA' && saldo >= 0 ? dinero(saldo) : ''}</td></tr>`;
  return `<tr class="${t.estado.toLowerCase()}">
    <td class="num" style="text-align:left">${fecha(t.fecha)}</td>
    <td class="codigo">${h(t.id)}</td>
    <td>${concepto(t, yo)}</td>
    <td class="num debito">${sale ? dinero(t.valor + t.comision) : ''}</td>
    <td class="num credito">${entra ? dinero(t.valor) : ''}</td>
    <td class="num">${t.estado !== 'RECHAZADA' && saldo >= 0 ? dinero(saldo) : ''}</td>
    <td class="num">${t.puntos && t.usuarioId === yo ? (t.puntos > 0 ? '+' : '') + t.puntos : ''}</td>
    <td class="fijo"><span class="etiqueta etiqueta--${tono}">${estado}</span>${riesgo}</td></tr>`;
}
const CAB_RENGLON = ['Fecha', 'Código', 'Concepto', 'Débito#', 'Crédito#', 'Saldo#', 'Puntos#', 'Estado'];

// ------------------------------------------------------------------ vistas

const PINTORES = {
  resumen() {
    const u = E.activo;
    const meta = u.siguienteNivel ? `<small>faltan ${numero(u.puntosParaSiguiente)} para ${h(u.siguienteNivel)}</small>
      <progress max="${u.puntos + u.puntosParaSiguiente - u.nivelDetalle.desde}" value="${u.puntos - u.nivelDetalle.desde}" aria-label="Avance hacia ${h(u.siguienteNivel)}"></progress>` : '<small>nivel máximo</small>';
    return cabecera(h(u.nombre), `Identificación <span class="codigo">${h(u.id)}</span> · ${h(u.correo)} · registrado el ${fecha(u.fechaRegistro)}`) + `
      <dl class="cifras">
        <div><dt>Saldo total</dt><dd>${dinero(u.saldoTotal)} <small>en ${u.billeteras.length} billeteras</small></dd></div>
        <div><dt>Nivel ${h(u.nivel)}</dt><dd>${numero(u.puntos)} <small>puntos acumulados</small> ${meta}</dd></div>
        <div><dt>Puntos para canjear</dt><dd>${numero(u.puntosDisponibles)} <small><a href="#recompensas">ver beneficios</a></small></dd></div>
      </dl>
      <div class="rejilla rejilla--operar"><div class="columna">
        ${panel('Billeteras', sello('hash', 'O(1)'), tabla(['Billetera', 'Tipo', 'Código', 'Estado', 'Saldo#'], u.billeteras.map(filaBilletera), 'Este usuario aún no tiene billeteras. Créale una en la vista Billeteras.'), '<a class="btn btn--texto btn--chico" href="#billeteras">Administrar</a>')}
        ${panel('Últimos movimientos', sello('lista', 'O(1)'), tabla(['Fecha', 'Concepto', 'Débito#', 'Crédito#', 'Saldo#'], u.historial.slice(0, 5).map((t) => renglon(t, u.id, true)), 'Sin movimientos todavía. Haz una recarga con el formulario Operar.', 'historial'), '<a class="btn btn--texto btn--chico" href="#movimientos">Ver todos</a>')}
      </div><div class="columna">
        ${formOperar()}
        ${panel('Alertas recientes', sello('cola', 'FIFO'), listaAlertas(u.notificaciones.slice(0, 5)))}
      </div></div>`;
  },

  // La vitrina (vitrina.html) muestra y administra las billeteras del usuario activo; recibe los datos por window.libreta.
  billeteras() {
    return '<iframe class="vitrina" src="vitrina.html" title="Billeteras del usuario activo"></iframe>';
  },

  movimientos() {
    const u = E.activo;
    const pasa = (t) => (!filtros.billetera || t.origen === filtros.billetera || t.destino === filtros.billetera)
      && (!filtros.tipo || t.tipo === filtros.tipo) && (!filtros.estado || t.estado === filtros.estado)
      && (!filtros.desde || t.fecha.slice(0, 10) >= filtros.desde) && (!filtros.hasta || t.fecha.slice(0, 10) <= filtros.hasta);
    const lista = u.historial.filter(pasa);
    const cima = u.pila[0];
    return cabecera(`Movimientos de ${h(u.nombre)}`, 'Cada renglón muestra lo que salió, lo que entró y el saldo de la billetera después del movimiento.') + `
      <div class="columna">
        ${panel('Deshacer', sello('pila', 'LIFO'), `
          <div class="deshacer"><div class="panel-cuerpo"><p class="ayuda">Solo se puede revertir la operación de la cima. Al deshacer vuelve el dinero y se descuentan los puntos que había generado.</p>
          <button class="btn btn--primario" data-accion="operar.revertir"${cima ? ` data-confirmar="¿Deshacer ${h(cima.id)} (${h(cima.tipoEtiqueta).toLowerCase()} de ${dinero(cima.valor)})?"` : ' disabled'}>Deshacer la última operación</button></div>
          ${u.pila.length ? `<ol class="pila" aria-label="Pila de operaciones reversibles, de la cima al fondo">${u.pila.slice(0, 5).map((t, i) => `<li><span>${i === 0 ? '<span class="etiqueta etiqueta--llena pila-cima">Cima</span> ' : ''}<span class="codigo">${h(t.id)}</span> ${h(t.tipoEtiqueta)}</span><span class="num">${dinero(t.valor)}</span></li>`).join('')}${u.pila.length > 5 ? `<li class="tenue">y ${u.pila.length - 5} más debajo</li>` : ''}</ol>` : '<p class="vacio">La pila está vacía: no hay operaciones para deshacer.</p>'}</div>`)}
        ${panel(`${lista.length} de ${u.historial.length} movimientos`, sello('lista', 'O(n)'), `
          <div class="panel-cuerpo formulario formulario--fila">
            ${campo('Billetera', `<select data-filtro="billetera">${opciones([['', 'Todas'], ...u.billeteras.map((b) => [b.codigo, b.nombre])], filtros.billetera)}</select>`)}
            ${campo('Tipo', `<select data-filtro="tipo">${opciones([['', 'Todos'], ['RECARGA', 'Recarga'], ['RETIRO', 'Retiro'], ['TRANSFERENCIA', 'Transferencia'], ['REVERSION', 'Reversión'], ['BENEFICIO', 'Beneficio']], filtros.tipo)}</select>`)}
            ${campo('Estado', `<select data-filtro="estado">${opciones([['', 'Todos'], ['EXITOSA', 'Aplicada'], ['REVERTIDA', 'Revertida'], ['RECHAZADA', 'Rechazada']], filtros.estado)}</select>`)}
            ${campo('Desde', `<input type="date" data-filtro="desde" value="${h(filtros.desde)}">`)}
            ${campo('Hasta', `<input type="date" data-filtro="hasta" value="${h(filtros.hasta)}">`)}
          </div>
          ${tabla(CAB_RENGLON, lista.map((t) => renglon(t, u.id)), 'Ningún movimiento coincide con esos filtros. Quita alguno para ver más.', 'historial')}`)}
      </div>`;
  },

  programadas() {
    const u = E.activo;
    const filas = E.programadas.map((p) => `<tr><td class="num">${p.turno}</td><td class="num" style="text-align:left">${fecha(p.fecha)}</td>
      <td>${h(p.descripcion)}<small>${h(p.tipoEtiqueta)} · ${[p.origenNombre, p.destinoNombre].filter(Boolean).map(h).join(' → ')} · <span class="codigo">${h(p.id)}</span></small></td>
      <td>${h(p.usuario)}<small>Nivel ${h(p.nivel)}</small></td><td>${h(p.periodicidadEtiqueta)}${p.ejecuciones ? `<small>${p.ejecuciones} ejecutadas</small>` : ''}</td>
      <td class="num">${dinero(p.valor)}</td>
      <td><button class="btn btn--peligro btn--chico" data-accion="programada.cancelar" data-id="${h(p.id)}" data-confirmar="¿Cancelar «${h(p.descripcion)}»?">Cancelar</button></td></tr>`);
    const propias = u.billeteras.map((b) => [b.codigo, `${b.nombre} · ${dinero(b.saldo)}`]);
    const minimo = E.reloj.slice(0, 16);
    return cabecera('Operaciones programadas', 'Sale primero la de fecha más cercana; si dos coinciden, la del usuario de mayor nivel. Adelanta el reloj simulado para ejecutarlas.') + `
      <div class="rejilla"><div class="columna">
        ${panel(`Cola de ejecución · ${E.programadas.length}`, sello('prioridad', 'O(log n)'), tabla(['Turno#', 'Fecha', 'Operación', 'Usuario', 'Repite', 'Valor#', ''], filas, 'No hay operaciones programadas. Agenda una con el formulario.'))}
      </div><div class="columna">
        ${panel(`Programar para ${h(u.nombre.split(' ')[0])}`, sello('prioridad', 'O(log n)'), `<form class="panel-cuerpo formulario" data-accion="programada.crear">
          ${campo('Qué se programa', `<select name="tipo" data-estado="tipoProgramada">${opciones([['TRANSFERENCIA', 'Transferencia o ahorro automático'], ['RECARGA', 'Recarga automática'], ['RETIRO', 'Pago recurrente (retiro)']], tipoProgramada)}</select>`)}
          ${tipoProgramada !== 'RECARGA' ? campo('Desde', `<select name="origen" required>${opciones(propias)}</select>`) : ''}
          ${tipoProgramada !== 'RETIRO' ? campo('Hacia', `<select name="destino" required>${tipoProgramada === 'RECARGA' ? opciones(propias) : destinos()}</select>`) : ''}
          ${campo('Valor', '<input name="valor" inputmode="numeric" pattern="[0-9]+" required placeholder="0">')}
          ${campo('Primera ejecución', `<input type="datetime-local" name="fecha" required min="${minimo}">`)}
          ${campo('Se repite', `<select name="periodicidad">${opciones([['UNICA', 'Una vez'], ['DIARIA', 'Cada día'], ['SEMANAL', 'Cada semana'], ['MENSUAL', 'Cada mes']])}</select>`)}
          ${campo('Descripción', '<input name="descripcion" maxlength="60" placeholder="Ej.: Ahorro semanal">')}
          <button class="btn btn--primario">Programar operación</button></form>`)}
      </div></div>`;
  },

  recompensas() {
    const u = E.activo;
    const niveles = E.niveles.map((n) => `<tr><td>${n.nombre === u.nivel ? `<strong>${h(n.nombre)}</strong> <span class="etiqueta etiqueta--bien">Actual</span>` : h(n.nombre)}</td>
      <td class="num">${numero(n.desde)}${n.hasta ? ' – ' + numero(n.hasta) : ' o más'}</td><td class="num">${n.bonoPuntos ? '+' + n.bonoPuntos + ' %' : '—'}</td>
      <td class="num">${(n.comisionPuntosBasicos / 100).toLocaleString('es-CO')} %</td><td class="num">${dinero(n.limite)}</td></tr>`);
    const beneficios = E.beneficios.map((b) => {
      const falta = b.costoPuntos - u.puntosDisponibles;
      return `<tr><td>${h(b.nombre)}<small>${h(b.descripcion)}</small></td><td class="num">${numero(b.costoPuntos)}</td>
        <td><button class="btn btn--primario btn--chico" data-accion="beneficio.canjear" data-codigo="${h(b.codigo)}"${falta > 0 ? ' disabled' : ''}>Canjear</button>${falta > 0 ? `<small>Faltan ${numero(falta)} puntos</small>` : ''}</td></tr>`;
    });
    const fila = (x, i) => `<tr><td class="num">${i + 1}</td><td>${x.id === u.id ? `<strong>${h(x.nombre)}</strong>` : h(x.nombre)}<small class="codigo">${h(x.id)}</small></td><td>${h(x.nivel)}</td><td class="num">${numero(x.puntos)}</td></tr>`;
    return cabecera(`Recompensas de ${h(u.nombre)}`, `Nivel ${h(u.nivel)} con ${numero(u.puntos)} puntos acumulados y ${numero(u.puntosDisponibles)} disponibles para canjear.${u.transferenciasSinComision ? ` Le quedan ${u.transferenciasSinComision} transferencias sin comisión.` : ''} Recarga 1, retiro 2 y transferencia 3 puntos por cada $100.`) + `
      <div class="rejilla rejilla--mitad"><div class="columna">
        ${panel('Beneficios', sello('lista', 'O(1)'), tabla(['Beneficio', 'Puntos#', ''], beneficios, ''))}
        ${panel('Canjes realizados', sello('lista', 'O(1)'), tabla(['Beneficio', 'Puntos#', 'Fecha'], u.canjes.map((c) => `<tr><td>${h(c.nombre)}</td><td class="num">−${numero(c.costoPuntos)}</td><td class="num" style="text-align:left">${fecha(c.fecha)}</td></tr>`), 'Aún no ha canjeado beneficios. Los puntos se ganan con cada operación.'))}
        ${panel('Niveles y ventajas', sello('arbol', 'O(log n)'), tabla(['Nivel', 'Puntos#', 'Bono#', 'Comisión#', 'Límite#'], niveles, ''))}
      </div><div class="columna">
        ${panel('Clasificación por puntos', sello('arbol', 'O(n)'), tabla(['Puesto#', 'Usuario', 'Nivel', 'Puntos#'], E.ranking.map(fila), 'No hay usuarios.'))}
        ${panel('Buscar por rango de puntos', sello('arbol', 'O(log n + k)'), `<form class="panel-cuerpo formulario formulario--fila" data-consulta>
          ${campo('Desde', `<input name="pmin" inputmode="numeric" pattern="[0-9]+" required value="${h(consulta.pmin)}" placeholder="501">`)}
          ${campo('Hasta', `<input name="pmax" inputmode="numeric" pattern="[0-9]+" required value="${h(consulta.pmax)}" placeholder="1000">`)}
          <button class="btn btn--suave">Buscar</button></form>
          ${E.rangoPuntos ? tabla(['Puesto#', 'Usuario', 'Nivel', 'Puntos#'], E.rangoPuntos.map(fila), 'Ningún usuario tiene puntos en ese rango.') : ''}`)}
      </div></div>`;
  },

  analitica() {
    const a = E.analitica;
    // Anillos concéntricos: uno por elemento, lleno en proporción al mayor. El centro muestra el total.
    const tonos = ['hash', 'arbol', 'pila', 'grafo', 'lista', 'cola'];
    const anillos = (lista, unidad, rotulo, vacio) => {
      if (!lista.length) return `<p class="vacio">${vacio}</p>`;
      const grosor = 10, paso = 15, r0 = 38, lado = 2 * (r0 + paso * lista.length), c = lado / 2;
      const total = numero(lista.reduce((s, x) => s + x.veces, 0));
      const pct = (x) => Math.max(2, Math.round(100 * x.veces / lista[0].veces));
      const attrs = (x, i) => `data-anillo="${i}" data-valor="${numero(x.veces)}" style="--tono:var(--e-${tonos[i % tonos.length]})"`;
      return `<div class="anillos" data-total="${total}" data-rotulo="${rotulo}" data-unidad="${unidad}">
        <svg viewBox="0 0 ${lado} ${lado}" role="img" aria-label="${h(lista.map((x) => `${x.etiqueta}: ${x.veces} ${unidad}`).join('; '))}">
          <g transform="rotate(-90 ${c} ${c})">${lista.map((x, i) => `<circle class="pista" cx="${c}" cy="${c}" r="${r0 + paso * i + grosor / 2}"/><circle class="arco" ${attrs(x, i)} cx="${c}" cy="${c}" r="${r0 + paso * i + grosor / 2}" pathLength="100" stroke-dasharray="${pct(x)} 100"/>`).join('')}</g>
          <text class="anillo-valor" x="${c}" y="${c}">${total}</text><text class="anillo-rotulo" x="${c}" y="${c + 14}">${rotulo}</text>
        </svg>
        <ul>${lista.map((x, i) => `<li ${attrs(x, i)}><i></i><span title="${h(x.etiqueta)}">${h(x.etiqueta)}</span><span class="num">${numero(x.veces)} ${unidad} · ${dinero(x.monto)}</span><span class="barra-valor"><i style="width:${pct(x)}%"></i></span></li>`).join('')}</ul></div>`;
    };
    const rutas = (lista) => tabla(['Ruta', 'Veces#', 'Monto#'], lista.map((r) => `<tr><td>${h(r.origenNombre)} → ${h(r.destinoNombre)}</td><td class="num">${r.veces}</td><td class="num">${dinero(r.monto)}</td></tr>`), 'Aún no hay transferencias.');
    const sinDatos = 'Sin operaciones en ese período. Amplía el rango de fechas.';
    const masActivo = a.activos[0];
    return cabecera('Analítica de movimientos', `${numero(a.totalOperaciones)} operaciones aplicadas movilizaron ${dinero(a.montoTotal)}${consulta.desde || consulta.hasta ? ' en el período elegido' : ' desde el inicio'}.${masActivo ? ` Usuario más activo: ${h(masActivo.etiqueta)} (${masActivo.veces} operaciones).` : ''}`,
      `<form class="formulario formulario--fila" data-consulta style="grid-template-columns:auto auto auto">
        ${campo('Desde', `<input type="date" name="desde" value="${h(consulta.desde)}">`)}${campo('Hasta', `<input type="date" name="hasta" value="${h(consulta.hasta)}">`)}
        <button class="btn btn--suave">Aplicar período</button></form>`) + `
      <div class="rejilla rejilla--mitad"><div class="columna">
        ${panel('Billeteras con mayor uso', sello('hash', 'O(1)'), anillos(a.billeteras, 'mov.', 'movimientos', sinDatos))}
        ${panel('Usuarios con más transferencias', sello('prioridad', 'O(k log n)'), anillos(a.transferencias, 'transf.', 'transferencias', sinDatos))}
        ${panel('Categorías de billetera más activas', sello('hash', 'O(1)'), anillos(a.categorias, 'mov.', 'movimientos', sinDatos))}
        ${panel('Frecuencia por tipo de transacción', sello('lista', 'O(n)'), anillos(a.tipos, 'op.', 'operaciones', sinDatos))}
        ${panel('Transacciones de mayor valor', sello('arbol', 'O(log n + k)'), tabla(['Código', 'Operación', 'Fecha', 'Valor#'], a.mayores.map((t) => `<tr><td class="codigo">${h(t.id)}</td><td>${h(t.tipoEtiqueta)}<small>${h(t.usuario)}</small></td><td class="num" style="text-align:left">${fecha(t.fecha)}</td><td class="num">${dinero(t.valor)}</td></tr>`), sinDatos))}
      </div><div class="columna">
        ${panel('Transferencias entre usuarios', sello('grafo', 'O(V + A)'), grafo())}
        ${panel('Rutas más frecuentes entre usuarios', sello('grafo', 'O(A)'), rutas(a.rutasUsuarios))}
        ${panel('Rutas más frecuentes entre billeteras', sello('grafo', 'O(A)'), rutas(a.rutasBilleteras))}
        ${panel('Ciclos de dinero', sello('grafo', 'DFS'), a.ciclos.length ? `<ul class="alertas">${a.ciclos.map((c) => `<li><span class="etiqueta">${c.length} usuarios</span><span>${[...c, c[0]].map((n) => h(n.nombre.split(' ')[0])).join(' → ')}</span></li>`).join('')}</ul>` : '<p class="vacio">No hay ciclos: el dinero no ha vuelto a ningún usuario de origen.</p>')}
      </div></div>
      ${panel('Comparar rendimiento de estructuras', '', `<form class="panel-cuerpo formulario formulario--fila" data-medir style="grid-template-columns:auto auto">
        ${campo('Elementos (1.000 a 100.000)', `<input name="n" type="number" min="1000" max="100000" step="1000" required value="${medicion.n}">`)}
        <button class="btn btn--suave">Medir</button></form>${tablaMediciones()}`)}`;
  },

  seguridad() {
    const eventos = E.auditoria.filter((e) => !filtros.riesgo || e.riesgo === filtros.riesgo);
    const filas = eventos.map((e) => `<tr><td class="num" style="text-align:left">${fecha(e.fecha)}</td><td>${h(e.regla)}<small>${h(e.detalle)}</small></td>
      <td class="fijo">${h(e.usuario)}</td><td class="codigo">${h(e.transaccionId)}</td>
      <td>${etiquetaRiesgo(e.riesgo)}</td></tr>`);
    const reglas = [
      ['Ráfaga de transferencias', '4 o más transferencias del mismo usuario en 10 minutos.'],
      ['Monto inusualmente alto', 'Un retiro o envío de 3 veces o más su promedio de salidas.'],
      ['Destino repetido', '3 o más transferencias a la misma billetera en 30 minutos.'],
      ['Fragmentación de montos', 'Envíos a otros usuarios desde 3 o más billeteras propias en 15 minutos.'],
      ['Cambio brusco de frecuencia', 'Operaciones en 24 horas que triplican su ritmo de la semana anterior.'],
      ['Horario no habitual', 'Operación de madrugada cuando casi nunca opera a esa hora.'],
    ];
    return cabecera('Seguridad y auditoría', 'Cada retiro o transferencia se compara con el historial del usuario. Lo inusual queda aquí con su nivel de riesgo y genera una alerta de seguridad.') + `
      <div class="rejilla"><div class="columna">
        ${panel(`${eventos.length} eventos detectados`, sello('lista', 'O(n)'), `<div class="panel-cuerpo formulario formulario--fila">
          ${campo('Nivel de riesgo', `<select data-filtro="riesgo">${opciones([['', 'Todos'], ['ALTO', 'Alto'], ['MEDIO', 'Medio'], ['BAJO', 'Bajo']], filtros.riesgo)}</select>`)}</div>
          ${tabla(['Fecha', 'Patrón detectado', 'Usuario', 'Transacción', 'Riesgo'], filas, 'No hay eventos con ese nivel de riesgo.')}`)}
      </div><div class="columna">
        ${panel('Qué se vigila', sello('lista', 'O(n)'), `<dl class="panel-cuerpo definicion" style="grid-template-columns:1fr">${reglas.map(([r, d]) => `<div><dt style="color:var(--tinta);font-weight:600;font-size:inherit">${r}</dt><dd class="tenue">${d}</dd></div>`).join('')}</dl>`)}
      </div></div>`;
  },

  usuarios() {
    const texto = filtros.texto.trim().toLowerCase();
    const lista = E.usuarios.filter((u) => !texto || u.id.includes(texto) || u.nombre.toLowerCase().includes(texto));
    const filas = lista.map((u) => `<tr><td>${h(u.nombre)}<small>${h(u.correo)}${u.telefono ? ' · ' + h(u.telefono) : ''}</small></td><td class="codigo">${h(u.id)}</td>
      <td>${h(u.nivel)}<small>${numero(u.puntos)} puntos</small></td><td class="num">${u.billeteras}</td><td class="num">${dinero(u.saldoTotal)}</td>
      <td><span class="acciones"><button class="btn btn--suave btn--chico" data-editar="${h(u.id)}">Modificar</button>
      <button class="btn btn--peligro btn--chico" data-accion="usuario.eliminar" data-id="${h(u.id)}" data-confirmar="¿Eliminar a ${h(u.nombre)}? Se borran sus billeteras y operaciones programadas. Solo se puede si su saldo es cero.">Eliminar</button></span></td></tr>`);
    const e = E.usuarios.find((u) => u.id === editando);
    return cabecera('Usuarios', 'Registra, busca, modifica o elimina usuarios. La identificación es la clave con la que la plataforma los encuentra.') + `
      <div class="rejilla"><div class="columna">
        ${panel(`${lista.length} de ${E.usuarios.length} usuarios`, sello('hash', 'O(1)'), `<div class="panel-cuerpo">
          ${campo('Buscar por identificación o nombre', `<input type="search" data-filtro="texto" value="${h(filtros.texto)}" placeholder="Ej.: 1094900303">`)}</div>
          ${tabla(['Usuario', 'Identificación', 'Nivel', 'Billeteras#', 'Saldo#', ''], filas, 'Nadie coincide con esa búsqueda. Revisa la identificación o registra el usuario.')}`)}
      </div><div class="columna">
        ${panel(e ? `Modificar a ${h(e.nombre.split(' ')[0])}` : 'Registrar usuario', sello('hash', 'O(1)'), `<form class="panel-cuerpo formulario" data-accion="${e ? 'usuario.modificar' : 'usuario.registrar'}">
          ${campo('Identificación', `<input name="id" inputmode="numeric" pattern="[0-9]{5,12}" required value="${h(e?.id)}"${e ? ' readonly' : ''} placeholder="5 a 12 dígitos">`)}
          ${campo('Nombre completo', `<input name="nombre" required maxlength="60" value="${h(e?.nombre)}">`)}
          ${campo('Correo', `<input type="email" name="correo" required value="${h(e?.correo)}" placeholder="nombre@correo.example">`)}
          ${campo('Teléfono (opcional)', `<input name="telefono" inputmode="numeric" pattern="[0-9]{7,10}" value="${h(e?.telefono)}" placeholder="7 a 10 dígitos">`)}
          <span class="acciones" style="justify-content:flex-start"><button class="btn btn--primario">${e ? 'Guardar cambios' : 'Registrar usuario'}</button>
          ${e ? '<button type="button" class="btn btn--suave" data-editar="">Cancelar</button>' : ''}</span></form>`)}
      </div></div>`;
  },
};

// ------------------------------------------------------------------ piezas

const estadoBilletera = (b) => `<span class="etiqueta etiqueta--${b.activa ? 'bien' : 'aviso'}">${b.activa ? 'Activa' : 'Inactiva'}</span>`;
const filaBilletera = (b) => `<tr><td>${h(b.nombre)}</td><td>${h(b.tipoEtiqueta)}</td><td class="codigo">${h(b.codigo)}</td><td>${estadoBilletera(b)}</td><td class="num">${dinero(b.saldo)}</td></tr>`;

// Destinos de una transferencia: las propias primero y luego las de cada usuario.
function destinos() {
  const grupos = new Map();
  for (const b of E.billeteras) {
    if (!b.activa) continue;
    const clave = b.propietarioId === E.activo.id ? 'Mis billeteras' : b.propietario;
    grupos.set(clave, [...(grupos.get(clave) ?? []), b]);
  }
  const orden = [...grupos].sort(([a], [b]) => (a === 'Mis billeteras' ? -1 : b === 'Mis billeteras' ? 1 : 0));
  return orden.map(([g, bs]) => `<optgroup label="${h(g)}">${bs.map((b) => `<option value="${h(b.codigo)}">${h(b.nombre)} (${h(b.codigo)})</option>`).join('')}</optgroup>`).join('');
}

function formOperar() {
  const u = E.activo;
  const propias = u.billeteras.filter((b) => b.activa).map((b) => [b.codigo, `${b.nombre} · ${dinero(b.saldo)}`]);
  const n = u.nivelDetalle;
  const boton = { recargar: 'Recargar', retirar: 'Retirar', transferir: 'Transferir' }[operacion];
  return panel('Operar', sello('hash', 'O(1)'), `<form class="panel-cuerpo formulario" data-accion="operar.${operacion}">
    <div class="segmento" role="radiogroup" aria-label="Tipo de operación">
      ${['recargar', 'retirar', 'transferir'].map((o) => `<label><input type="radio" name="_op" value="${o}" data-estado="operacion"${o === operacion ? ' checked' : ''}>${o[0].toUpperCase() + o.slice(1)}</label>`).join('')}
    </div>
    ${operacion !== 'recargar' ? campo('Desde', `<select name="origen" required>${opciones(propias)}</select>`) : ''}
    ${operacion !== 'retirar' ? campo('Hacia', `<select name="destino" required>${operacion === 'recargar' ? opciones(propias) : destinos()}</select>`) : ''}
    ${campo('Valor', '<input name="valor" inputmode="numeric" pattern="[0-9]+" required placeholder="0" autocomplete="off">')}
    <p class="ayuda">Nivel ${h(n.nombre)}: hasta ${dinero(n.limite)} por operación${operacion === 'transferir' ? `; comisión de ${(n.comisionPuntosBasicos / 100).toLocaleString('es-CO')} % al enviar a otro usuario` : ''}.</p>
    <button class="btn btn--primario"${propias.length ? '' : ' disabled'}>${boton}</button></form>`);
}

const TONO_ALERTA = { SALDO_BAJO: ['Saldo bajo', 'aviso'], PROGRAMADA_PROXIMA: ['Próxima', ''], PROGRAMADA_EJECUTADA: ['Programada', 'bien'], OPERACION_RECHAZADA: ['Rechazo', 'mal'], CAMBIO_NIVEL: ['Nivel', 'bien'], CANJE: ['Canje', 'bien'], REVERSION: ['Reversión', 'aviso'], SEGURIDAD: ['Seguridad', 'mal'] };
function listaAlertas(lista) {
  if (!lista.length) return '<p class="vacio">Sin alertas. Aquí aparecen saldos bajos, rechazos, cambios de nivel y avisos de seguridad.</p>';
  return `<ul class="alertas">${lista.map((n) => { const [rotulo, tono] = TONO_ALERTA[n.tipo]; return `<li><span class="etiqueta${tono ? ' etiqueta--' + tono : ''}">${rotulo}</span><span>${h(n.mensaje)}</span><time datetime="${h(n.fecha)}">${fecha(n.fecha)}</time></li>`; }).join('')}</ul>`;
}

// Grafo de transferencias entre usuarios: nodos en círculo, grosor de la flecha según las veces.
function grafo() {
  const { nodos, aristas } = E.grafo;
  if (!aristas.length) return '<p class="vacio">Aún no hay transferencias entre usuarios.</p>';
  const W = 520, H = 400, R = 140, pos = {};
  const orden = [...nodos].sort((a, b) => a.nombre.localeCompare(b.nombre));
  orden.forEach((n, i) => { const a = (2 * Math.PI * i) / orden.length - Math.PI / 2; pos[n.id] = [W / 2 + R * Math.cos(a), H / 2 + R * Math.sin(a), a]; });
  const maxVeces = Math.max(...aristas.map((a) => a.veces));
  const lineas = aristas.filter((a) => pos[a.origen] && pos[a.destino]).map((a) => {
    const [x1, y1] = pos[a.origen], [x2, y2] = pos[a.destino];
    const dx = x2 - x1, dy = y2 - y1, d = Math.hypot(dx, dy), nx = -dy / d, ny = dx / d;
    // La curva se desvía a un lado para que ida y vuelta no se tapen; se recorta antes del nodo.
    const cx = (x1 + x2) / 2 + nx * 26, cy = (y1 + y2) / 2 + ny * 26, r = 13;
    return `<path class="arista" marker-end="url(#punta)" opacity="${(0.2 + 0.7 * a.veces / maxVeces).toFixed(2)}" stroke-width="${(1 + 3.5 * a.veces / maxVeces).toFixed(1)}" d="M${(x1 + dx / d * r).toFixed(1)} ${(y1 + dy / d * r).toFixed(1)} Q${cx.toFixed(1)} ${cy.toFixed(1)} ${(x2 - dx / d * (r + 6)).toFixed(1)} ${(y2 - dy / d * (r + 6)).toFixed(1)}"><title>${h(a.origenNombre)} → ${h(a.destinoNombre)}: ${a.veces} veces, ${dinero(a.monto)}</title></path>`;
  }).join('');
  const puntos = orden.map((n) => { const [x, y, a] = pos[n.id]; const lx = x + 24 * Math.cos(a), ly = y + 24 * Math.sin(a) + 4;
    return `<circle class="nodo" cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="9"/><text x="${lx.toFixed(1)}" y="${ly.toFixed(1)}" text-anchor="${Math.cos(a) > 0.3 ? 'start' : Math.cos(a) < -0.3 ? 'end' : 'middle'}">${h(n.nombre.split(' ')[0])}</text>`; }).join('');
  return `<div class="panel-cuerpo"><svg class="grafo" viewBox="0 0 ${W} ${H}" role="img" aria-label="Grafo de transferencias entre ${orden.length} usuarios con ${aristas.length} rutas. El detalle está en la tabla de rutas más frecuentes.">
    <defs><marker id="punta" viewBox="0 0 10 10" refX="5" refY="5" markerWidth="5" markerHeight="5" orient="auto-start-reverse"><path d="M0 0L10 5L0 10z" fill="var(--e-grafo)"/></marker></defs>${lineas}${puntos}</svg>
    <p class="ayuda">El grosor de cada flecha indica cuántas veces se usó la ruta. Pasa el cursor sobre una flecha para ver el monto.</p></div>`;
}

// ------------------------------------------------------------------ eventos

document.addEventListener('submit', async (ev) => {
  ev.preventDefault();
  const f = ev.target, boton = f.querySelector('button:not([type=button])');
  const datos = Object.fromEntries([...new FormData(f)].filter(([k]) => !k.startsWith('_')));
  if (f.dataset.accion) {
    await accion(f.dataset.accion, datos, boton);
    if (f.dataset.accion.startsWith('operar.')) $('form[data-accion^="operar."] [name=valor]')?.focus();
  } else if ('medir' in f.dataset) {
    if (boton) boton.disabled = true;
    try {
      medicion.n = Number(datos.n);
      medicion.filas = await (await fetch('/api/rendimiento?n=' + medicion.n)).json();
      pintar();
    } catch {
      avisar('No se pudo medir el rendimiento. Verifica que el programa Java siga en ejecución.', true);
    } finally { if (boton?.isConnected) boton.disabled = false; }
  } else if ('consulta' in f.dataset) {
    Object.assign(consulta, datos);
    await cargar();
  }
});

document.addEventListener('click', async (ev) => {
  const b = ev.target.closest('button');
  if (!b) return;
  if ('editar' in b.dataset) { editando = b.dataset.editar || null; pintar(); $('form[data-accion^="usuario."] [name=nombre]')?.focus(); return; }
  if (!b.dataset.accion || b.form?.dataset.accion) return; // los botones de formulario van por submit
  if (b.dataset.confirmar && !confirm(b.dataset.confirmar)) return;
  const { accion: nombre, confirmar, ...datos } = b.dataset;
  await accion(nombre, datos, b);
});

document.addEventListener('change', (ev) => {
  const c = ev.target;
  if (c.id === 'usuario-activo') { usuarioId = c.value; filtros.billetera = ''; cargar(); return; }
  if (c.dataset.estado === 'operacion') { operacion = c.value; pintar(); $(`[name=_op][value=${operacion}]`)?.focus(); }
  if (c.dataset.estado === 'tipoProgramada') { tipoProgramada = c.value; pintar(); $('[data-estado=tipoProgramada]')?.focus(); }
  if (c.dataset.filtro && c.type !== 'search') { filtros[c.dataset.filtro] = c.value; pintar(); $(`[data-filtro=${c.dataset.filtro}]`)?.focus(); }
});

document.addEventListener('input', (ev) => {
  const c = ev.target;
  if (c.dataset.filtro !== 'texto') return;
  filtros.texto = c.value;
  const cursor = c.selectionStart;
  pintar();
  const nuevo = $('[data-filtro=texto]');
  nuevo.focus();
  nuevo.setSelectionRange(cursor, cursor);
});

// Al pasar por un anillo o su renglón, los demás se atenúan y el centro muestra ese valor.
document.addEventListener('mouseover', (ev) => {
  const item = ev.target.closest?.('[data-anillo]');
  for (const caja of document.querySelectorAll('.anillos')) {
    const i = caja.contains(item) ? item.dataset.anillo : '';
    caja.querySelectorAll('[data-anillo]').forEach((x) => x.classList.toggle('apagado', i !== '' && x.dataset.anillo !== i));
    caja.querySelector('.anillo-valor').textContent = i === '' ? caja.dataset.total : item.dataset.valor;
    caja.querySelector('.anillo-rotulo').textContent = i === '' ? caja.dataset.rotulo : caja.dataset.unidad;
  }
});
addEventListener('hashchange', () => { if (E) { pintar(); $('#contenido').focus({ preventScroll: true }); scrollTo(0, 0); } });
cargar();
