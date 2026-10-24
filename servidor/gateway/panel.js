"use strict";

const RUTA = "/_gateway";

let token = sessionStorage.getItem("token_admin");
let yo = sessionStorage.getItem("usuario_admin") || "";

const $ = (id) => document.getElementById(id);

// ---------- Avisos y vistas ----------

function aviso(texto, esError) {
  const el = $("aviso");
  el.textContent = texto || "";
  el.className = texto ? (esError ? "error" : "ok") : "";
}

function mostrarVista(nombre) {
  $("vista-login").hidden = nombre !== "login";
  $("vista-panel").hidden = nombre !== "panel";
}

function cerrarSesion(mensaje) {
  token = null;
  yo = "";
  sessionStorage.removeItem("token_admin");
  sessionStorage.removeItem("usuario_admin");
  $("cuerpo-tabla").replaceChildren();
  mostrarVista("login");
  aviso(mensaje || "", false);
}

// ---------- Llamadas al gateway ----------

async function llamar(metodo, ruta, cuerpo) {
  const opciones = { method: metodo, headers: {} };
  if (token) opciones.headers["Authorization"] = "Bearer " + token;
  if (cuerpo !== undefined) {
    opciones.headers["Content-Type"] = "application/json";
    opciones.body = JSON.stringify(cuerpo);
  }
  const r = await fetch(RUTA + ruta, opciones);
  if (r.status === 401) {
    cerrarSesion("La sesión caducó. Entra de nuevo.");
    throw new Error("sesion");
  }
  if (!r.ok) {
    let detalle = "Error " + r.status;
    try {
      const j = await r.json();
      if (typeof j.detail === "string") {
        detalle = j.detail;
      } else if (Array.isArray(j.detail)) {
        detalle = j.detail.map((d) => d.msg).join(". ");
      }
    } catch (_) {
      // la respuesta no era JSON: se deja el mensaje genérico
    }
    throw new Error(detalle);
  }
  if (r.status === 204) return null;
  return r.json();
}

function mostrarError(e) {
  if (e && e.message !== "sesion") aviso(e.message, true);
}

// ---------- Cuadro de diálogo ----------

// Devuelve null si se cancela, o {accion: "aceptar", valor} / {accion: "extra"}
function pedir({ titulo, ayuda = "", etiqueta = "", tipo = "text", valor = "", extra = null }) {
  return new Promise((resolver) => {
    const d = $("dialogo");
    const entrada = $("dialogo-entrada");
    let resultado = null;

    $("dialogo-titulo").textContent = titulo;
    $("dialogo-ayuda").textContent = ayuda;
    $("dialogo-etiqueta").textContent = etiqueta;
    $("dialogo-etiqueta-contenedor").hidden = tipo === "ninguno";
    if (tipo !== "ninguno") {
      entrada.type = tipo;
      entrada.value = valor;
    }
    const botonExtra = $("dialogo-extra");
    botonExtra.hidden = !extra;
    if (extra) botonExtra.textContent = extra;

    $("dialogo-aceptar").onclick = () => {
      resultado = { accion: "aceptar", valor: entrada.value };
      d.close();
    };
    $("dialogo-cancelar").onclick = () => d.close();
    botonExtra.onclick = () => {
      resultado = { accion: "extra" };
      d.close();
    };
    entrada.onkeydown = (ev) => {
      if (ev.key === "Enter") $("dialogo-aceptar").click();
    };
    d.onclose = () => {
      entrada.value = ""; // por si era una contraseña
      resolver(resultado);
    };

    d.showModal();
    if (tipo !== "ninguno") entrada.focus();
  });
}

// ---------- Tabla ----------

function celda(texto, clase) {
  const td = document.createElement("td");
  td.textContent = texto;
  if (clase) td.className = clase;
  return td;
}

function boton(texto, alPulsar, { peligro = false, desactivado = false } = {}) {
  const b = document.createElement("button");
  b.type = "button";
  b.textContent = texto;
  if (peligro) b.classList.add("peligro");
  b.disabled = desactivado;
  b.addEventListener("click", alPulsar);
  return b;
}

function pintar(usuarios) {
  const cuerpo = $("cuerpo-tabla");
  cuerpo.replaceChildren();
  for (const u of usuarios) {
    const esYo = u.nombre.toLowerCase() === yo.toLowerCase();
    const tr = document.createElement("tr");

    let estado = "Activo";
    let claseEstado = "estado-ok";
    if (!u.activo) {
      estado = "Desactivado";
      claseEstado = "estado-mal";
    } else if (u.caducada) {
      estado = "Caducada";
      claseEstado = "estado-mal";
    }

    tr.append(
      celda(u.nombre + (esYo ? " (tú)" : "")),
      celda(u.rol),
      celda(u.navidrome_usuario || "—"),
      celda(estado, claseEstado),
      celda(u.expira ? u.expira + (u.caducada ? " (caducada)" : "") : "—")
    );

    const acciones = document.createElement("td");
    acciones.className = "acciones";
    acciones.append(
      boton(u.activo ? "Desactivar" : "Activar", () => alternarActivo(u), {
        desactivado: esYo && u.activo,
      }),
      boton(u.rol === "admin" ? "Quitar admin" : "Hacer admin", () => cambiarRol(u), {
        desactivado: esYo,
      }),
      boton("Caducidad", () => cambiarCaducidad(u)),
      boton("Contraseña", () => cambiarClave(u)),
      boton("Navidrome", () => cambiarNavidrome(u)),
      boton("Borrar", () => borrar(u), { peligro: true, desactivado: esYo })
    );
    tr.append(acciones);
    cuerpo.append(tr);
  }
}

async function cargar() {
  try {
    const usuarios = await llamar("GET", "/admin/usuarios");
    pintar(usuarios);
  } catch (e) {
    mostrarError(e);
  }
}

// ---------- Acciones ----------

async function aplicar(nombre, cambios, mensaje) {
  try {
    await llamar("PATCH", "/admin/usuarios/" + encodeURIComponent(nombre), cambios);
    aviso(mensaje, false);
    await cargar();
  } catch (e) {
    mostrarError(e);
  }
}

async function alternarActivo(u) {
  if (u.activo) {
    const r = await pedir({
      titulo: "Desactivar a " + u.nombre,
      ayuda: "Sus sesiones se cerrarán y no podrá entrar hasta que lo actives otra vez.",
      tipo: "ninguno",
    });
    if (!r) return;
    aplicar(u.nombre, { activo: false }, "Usuario desactivado.");
  } else {
    aplicar(u.nombre, { activo: true }, "Usuario activado.");
  }
}

async function cambiarRol(u) {
  const nuevo = u.rol === "admin" ? "usuario" : "admin";
  const r = await pedir({
    titulo: "Cambiar rol de " + u.nombre,
    ayuda:
      nuevo === "admin"
        ? "Podrá administrar a todos los usuarios."
        : "Dejará de poder administrar usuarios.",
    tipo: "ninguno",
  });
  if (!r) return;
  aplicar(u.nombre, { rol: nuevo }, "Rol cambiado a " + nuevo + ".");
}

async function cambiarCaducidad(u) {
  const r = await pedir({
    titulo: "Caducidad de " + u.nombre,
    ayuda: "La cuenta vale hasta el final de ese día (hora UTC).",
    etiqueta: "Fecha",
    tipo: "date",
    valor: u.expira || "",
    extra: "Sin caducidad",
  });
  if (!r) return;
  if (r.accion === "extra") {
    aplicar(u.nombre, { expira: null }, "Caducidad quitada.");
    return;
  }
  if (!r.valor) {
    aviso("Elige una fecha.", true);
    return;
  }
  aplicar(u.nombre, { expira: r.valor }, "Caducidad actualizada.");
}

async function cambiarClave(u) {
  const r = await pedir({
    titulo: "Nueva contraseña para " + u.nombre,
    ayuda: "Mínimo 10 caracteres. Sus sesiones actuales se cerrarán.",
    etiqueta: "Contraseña",
    tipo: "password",
  });
  if (!r) return;
  if (r.valor.length < 10) {
    aviso("La contraseña debe tener al menos 10 caracteres.", true);
    return;
  }
  aplicar(u.nombre, { clave: r.valor }, "Contraseña cambiada.");
}

async function cambiarNavidrome(u) {
  const r = await pedir({
    titulo: "Usuario de Navidrome de " + u.nombre,
    ayuda:
      "Nombre exacto del usuario en Navidrome. Si no existe, Navidrome crea uno nuevo la primera vez que entre.",
    etiqueta: "Usuario de Navidrome",
    valor: u.navidrome_usuario || "",
  });
  if (!r) return;
  const nombre = r.valor.trim();
  if (!nombre) {
    aviso("Escribe el nombre del usuario de Navidrome.", true);
    return;
  }
  aplicar(u.nombre, { navidrome_usuario: nombre }, "Vínculo con Navidrome actualizado.");
}

async function borrar(u) {
  const r = await pedir({
    titulo: "Borrar a " + u.nombre,
    ayuda: "Se borra del gateway. Su usuario de Navidrome no se toca.",
    tipo: "ninguno",
  });
  if (!r) return;
  try {
    await llamar("DELETE", "/admin/usuarios/" + encodeURIComponent(u.nombre));
    aviso("Usuario borrado.", false);
    await cargar();
  } catch (e) {
    mostrarError(e);
  }
}

// ---------- Formularios ----------

async function entrar(ev) {
  ev.preventDefault();
  aviso("", false);
  try {
    const r = await fetch(RUTA + "/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        usuario: $("login-usuario").value.trim(),
        clave: $("login-clave").value,
      }),
    });
    const datos = await r.json().catch(() => ({}));
    if (!r.ok) throw new Error(typeof datos.detail === "string" ? datos.detail : "No se pudo entrar");
    if (datos.rol !== "admin") throw new Error("Este usuario no es administrador");
    token = datos.token;
    yo = datos.usuario;
    sessionStorage.setItem("token_admin", token);
    sessionStorage.setItem("usuario_admin", yo);
    $("login-clave").value = "";
    abrirPanel();
  } catch (e) {
    aviso(e.message, true);
  }
}

async function crear(ev) {
  ev.preventDefault();
  const cuerpo = {
    nombre: $("nuevo-nombre").value.trim(),
    clave: $("nuevo-clave").value,
    rol: $("nuevo-rol").value,
  };
  const navidrome = $("nuevo-navidrome").value.trim();
  if (navidrome) cuerpo.navidrome_usuario = navidrome;
  const expira = $("nuevo-expira").value;
  if (expira) cuerpo.expira = expira;
  try {
    await llamar("POST", "/admin/usuarios", cuerpo);
    $("form-crear").reset();
    aviso("Usuario creado.", false);
    await cargar();
  } catch (e) {
    mostrarError(e);
  }
}

function abrirPanel() {
  $("sesion-usuario").textContent = yo;
  mostrarVista("panel");
  cargar();
}

// ---------- Arranque ----------

$("form-login").addEventListener("submit", entrar);
$("form-crear").addEventListener("submit", crear);
$("boton-salir").addEventListener("click", () => cerrarSesion(""));

if (token) {
  abrirPanel();
} else {
  mostrarVista("login");
}