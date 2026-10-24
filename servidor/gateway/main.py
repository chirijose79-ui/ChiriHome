import json
import os
import sqlite3
from contextlib import asynccontextmanager
from datetime import date
from typing import Literal

import anyio
import httpx
from anyio import to_thread
from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.responses import Response, StreamingResponse
from pydantic import BaseModel, Field

import base_datos
import seguridad

UPSTREAM = os.environ.get("UPSTREAM_URL", "http://navidrome:4533").rstrip("/")

# Cabecera con la que Navidrome recibe el usuario ya autenticado
HEADER_USUARIO = os.environ.get("NAVIDROME_USER_HEADER", "Remote-User").lower()

# La administración solo funciona desde la red local, salvo que se desactive a propósito
ADMIN_SOLO_LAN = os.environ.get("ADMIN_SOLO_LAN", "true").lower() != "false"

# Cloudflare añade siempre estas cabeceras a lo que pasa por el túnel
CABECERAS_CLOUDFLARE = ("cf-connecting-ip", "cf-ray")

# Cabeceras que son de una sola conexión y no se reenvían
HOP_BY_HOP = {
    "connection",
    "keep-alive",
    "proxy-authenticate",
    "proxy-authorization",
    "te",
    "trailers",
    "transfer-encoding",
    "upgrade",
}

# Nunca se reenvían tal cual: el host se recalcula, el token no debe llegar a
# Navidrome y la cabecera de usuario solo la pone el gateway
NO_REENVIAR = HOP_BY_HOP | {"host", "authorization", HEADER_USUARIO}

# Parámetros de autenticación de Subsonic: se quitan antes de reenviar
PARAMS_AUTH = {"p", "t", "s"}

MAX_FALLOS_POR_USUARIO = 5
MAX_FALLOS_POR_IP = 20

PATRON_NOMBRE = r"^[A-Za-z0-9_.-]{3,32}$"
PATRON_NAVIDROME = r"^[A-Za-z0-9_.@-]{1,64}$"

limitador = seguridad.LimitadorIntentos()


@asynccontextmanager
async def ciclo_de_vida(app: FastAPI):
    seguridad.secreto()  # falla al arrancar si falta JWT_SECRET
    base_datos.iniciar()
    app.state.cliente = httpx.AsyncClient(
        timeout=httpx.Timeout(connect=10.0, read=120.0, write=30.0, pool=10.0),
        limits=httpx.Limits(max_connections=50, max_keepalive_connections=10),
        follow_redirects=False,
    )
    yield
    await app.state.cliente.aclose()


app = FastAPI(
    lifespan=ciclo_de_vida,
    docs_url=None,
    redoc_url=None,
    openapi_url=None,
)


class DatosLogin(BaseModel):
    usuario: str = Field(min_length=1, max_length=64)
    clave: str = Field(min_length=1, max_length=256)


class UsuarioNuevo(BaseModel):
    nombre: str = Field(pattern=PATRON_NOMBRE)
    clave: str = Field(min_length=10, max_length=256)
    rol: Literal["admin", "usuario"] = "usuario"
    navidrome_usuario: str | None = Field(default=None, pattern=PATRON_NAVIDROME)
    expira: date | None = None


class UsuarioCambios(BaseModel):
    rol: Literal["admin", "usuario"] | None = None
    navidrome_usuario: str | None = Field(default=None, pattern=PATRON_NAVIDROME)
    activo: bool | None = None
    clave: str | None = Field(default=None, min_length=10, max_length=256)
    # Enviar "expira": null quita la caducidad
    expira: date | None = None


def _ip(request: Request) -> str:
    return request.headers.get("cf-connecting-ip") or (
        request.client.host if request.client else "desconocida"
    )


def _extraer_token(request: Request):
    # Cabecera Authorization: Bearer ..., o el parámetro p de Subsonic
    cabecera = request.headers.get("authorization", "")
    if cabecera.lower().startswith("bearer "):
        return cabecera[7:].strip()
    return request.query_params.get("p")


async def _usuario_actual(request: Request):
    token = _extraer_token(request)
    if not token:
        return None
    carga = seguridad.leer_token(token)
    if not carga:
        return None
    try:
        id_usuario = int(carga["sub"])
    except (KeyError, ValueError):
        return None
    usuario = await to_thread.run_sync(base_datos.buscar_por_id, id_usuario)
    if (
        not usuario
        or not usuario["activo"]
        or usuario["version_token"] != carga.get("ver")
        or seguridad.caducada(usuario.get("expira"))
    ):
        return None
    return usuario


async def _exigir_admin(request: Request) -> dict:
    # Por el túnel de Cloudflare la administración no existe, aunque el token sea válido
    if ADMIN_SOLO_LAN and any(request.headers.get(h) for h in CABECERAS_CLOUDFLARE):
        raise HTTPException(status_code=404, detail="No encontrado")
    usuario = await _usuario_actual(request)
    if not usuario:
        raise HTTPException(status_code=401, detail="Sesión no válida o caducada")
    if usuario["rol"] != "admin":
        raise HTTPException(status_code=403, detail="Solo para administradores")
    return usuario


def _publico(u: dict) -> dict:
    # Nunca incluye el hash de la contraseña
    return {
        "id": u["id"],
        "nombre": u["nombre"],
        "rol": u["rol"],
        "navidrome_usuario": u["navidrome_usuario"],
        "activo": bool(u["activo"]),
        "creado": u["creado"],
        "expira": u["expira"],
        "caducada": seguridad.caducada(u["expira"]),
    }


def _no_autorizado(ruta: str) -> Response:
    mensaje = "Sesión no válida o caducada"
    if ruta.startswith("/rest/"):
        cuerpo = {
            "subsonic-response": {
                "status": "failed",
                "version": "1.16.1",
                "error": {"code": 40, "message": mensaje},
            }
        }
    else:
        cuerpo = {"detail": mensaje}
    return Response(
        json.dumps(cuerpo), status_code=401, media_type="application/json"
    )


def _limpiar_consulta(consulta: bytes) -> bytes:
    partes = []
    for parte in consulta.split(b"&"):
        if not parte:
            continue
        clave = parte.split(b"=", 1)[0].decode("latin-1")
        if clave not in PARAMS_AUTH:
            partes.append(parte)
    return b"&".join(partes)


@app.get("/_gateway/salud")
async def salud():
    return {"estado": "ok"}


@app.post("/_gateway/login")
async def login(datos: DatosLogin, request: Request):
    nombre = datos.usuario.strip()
    clave_ip = f"ip:{_ip(request)}"
    clave_usuario = f"u:{nombre.lower()}"

    if limitador.bloqueado(clave_ip, MAX_FALLOS_POR_IP) or limitador.bloqueado(
        clave_usuario, MAX_FALLOS_POR_USUARIO
    ):
        raise HTTPException(
            status_code=429,
            detail="Demasiados intentos. Espera unos minutos.",
            headers={"Retry-After": "900"},
        )

    usuario = await to_thread.run_sync(base_datos.buscar_por_nombre, nombre)
    hash_guardado = usuario["clave_hash"] if usuario else seguridad.HASH_FALSO
    correcta = await to_thread.run_sync(seguridad.verificar, datos.clave, hash_guardado)

    if not usuario or not correcta or not usuario["activo"]:
        limitador.registrar_fallo(clave_ip)
        limitador.registrar_fallo(clave_usuario)
        raise HTTPException(status_code=401, detail="Usuario o contraseña incorrectos")

    # Solo se llega aquí con la contraseña correcta, así que avisar no delata nada
    if seguridad.caducada(usuario.get("expira")):
        raise HTTPException(status_code=403, detail="La cuenta ha caducado")

    limitador.limpiar(clave_usuario)
    return {
        "token": seguridad.crear_token(usuario),
        "usuario": usuario["nombre"],
        "rol": usuario["rol"],
        "expira_en_segundos": seguridad.DURACION_TOKEN,
        "cuenta_expira": usuario.get("expira"),
    }


@app.get("/_gateway/yo")
async def yo(request: Request):
    usuario = await _usuario_actual(request)
    if not usuario:
        raise HTTPException(status_code=401, detail="Sesión no válida o caducada")
    return {
        "usuario": usuario["nombre"],
        "rol": usuario["rol"],
        "cuenta_expira": usuario.get("expira"),
    }


# ---- Administración de usuarios (solo administradores, solo desde la red local) ----


@app.get("/_gateway/admin/usuarios")
async def admin_listar(admin: dict = Depends(_exigir_admin)):
    usuarios = await to_thread.run_sync(base_datos.listar)
    return [_publico(u) for u in usuarios]


@app.post("/_gateway/admin/usuarios", status_code=201)
async def admin_crear(datos: UsuarioNuevo, admin: dict = Depends(_exigir_admin)):
    clave_hash = await to_thread.run_sync(seguridad.hashear, datos.clave)
    expira = datos.expira.isoformat() if datos.expira else None
    try:
        await to_thread.run_sync(
            base_datos.crear,
            datos.nombre,
            clave_hash,
            datos.rol,
            datos.navidrome_usuario or datos.nombre,
            expira,
        )
    except sqlite3.IntegrityError:
        raise HTTPException(status_code=409, detail="Ya existe un usuario con ese nombre")
    usuario = await to_thread.run_sync(base_datos.buscar_por_nombre, datos.nombre)
    return _publico(usuario)


@app.patch("/_gateway/admin/usuarios/{nombre}")
async def admin_cambiar(
    nombre: str, datos: UsuarioCambios, admin: dict = Depends(_exigir_admin)
):
    objetivo = await to_thread.run_sync(base_datos.buscar_por_nombre, nombre)
    if not objetivo:
        raise HTTPException(status_code=404, detail="No existe ese usuario")

    enviados = datos.model_fields_set
    campos = {}
    if "rol" in enviados and datos.rol is not None:
        campos["rol"] = datos.rol
    if "navidrome_usuario" in enviados and datos.navidrome_usuario is not None:
        campos["navidrome_usuario"] = datos.navidrome_usuario
    if "activo" in enviados and datos.activo is not None:
        campos["activo"] = 1 if datos.activo else 0
    if "clave" in enviados and datos.clave is not None:
        campos["clave_hash"] = await to_thread.run_sync(seguridad.hashear, datos.clave)
    if "expira" in enviados:
        campos["expira"] = datos.expira.isoformat() if datos.expira else None

    # Protecciones para no quedarse sin administradores
    deja_de_ser_admin_activo = objetivo["rol"] == "admin" and (
        campos.get("rol") == "usuario" or campos.get("activo") == 0
    )
    if deja_de_ser_admin_activo:
        if objetivo["id"] == admin["id"]:
            raise HTTPException(
                status_code=400,
                detail="No puedes quitarte el rol de administrador ni desactivarte a ti mismo",
            )
        restantes = await to_thread.run_sync(
            base_datos.contar_admins_activos, objetivo["nombre"]
        )
        if restantes == 0:
            raise HTTPException(
                status_code=400,
                detail="Debe quedar al menos un administrador activo",
            )

    await to_thread.run_sync(base_datos.actualizar, objetivo["nombre"], campos)
    actualizado = await to_thread.run_sync(base_datos.buscar_por_id, objetivo["id"])
    return _publico(actualizado)


@app.delete("/_gateway/admin/usuarios/{nombre}", status_code=204)
async def admin_borrar(nombre: str, admin: dict = Depends(_exigir_admin)):
    objetivo = await to_thread.run_sync(base_datos.buscar_por_nombre, nombre)
    if not objetivo:
        raise HTTPException(status_code=404, detail="No existe ese usuario")
    if objetivo["id"] == admin["id"]:
        raise HTTPException(status_code=400, detail="No puedes borrarte a ti mismo")
    if objetivo["rol"] == "admin" and objetivo["activo"]:
        restantes = await to_thread.run_sync(
            base_datos.contar_admins_activos, objetivo["nombre"]
        )
        if restantes == 0:
            raise HTTPException(
                status_code=400,
                detail="Debe quedar al menos un administrador activo",
            )
    await to_thread.run_sync(base_datos.borrar, objetivo["nombre"])
    return Response(status_code=204)


# ---- Reenvío a Navidrome ----


@app.api_route(
    "/{ruta:path}",
    methods=["GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
)
async def reenviar(ruta: str, request: Request):
    usuario = await _usuario_actual(request)
    if not usuario:
        return _no_autorizado(request.url.path)
    if not usuario["navidrome_usuario"]:
        return Response(
            json.dumps({"detail": "Este usuario no tiene un usuario de Navidrome vinculado"}),
            status_code=403,
            media_type="application/json",
        )

    cliente: httpx.AsyncClient = request.app.state.cliente

    # Se usa la ruta y la consulta tal como llegaron, sin decodificar
    ruta_cruda = request.scope.get("raw_path") or request.url.path.encode()
    consulta = _limpiar_consulta(request.scope.get("query_string", b""))
    destino = UPSTREAM + ruta_cruda.decode("latin-1")
    if consulta:
        destino += "?" + consulta.decode("latin-1")

    cabeceras = [
        (k, v) for k, v in request.headers.items() if k.lower() not in NO_REENVIAR
    ]
    cabeceras.append((HEADER_USUARIO, usuario["navidrome_usuario"]))
    cuerpo = None if request.method in ("GET", "HEAD", "OPTIONS") else request.stream()

    peticion = cliente.build_request(
        request.method, destino, headers=cabeceras, content=cuerpo
    )
    try:
        respuesta = await cliente.send(peticion, stream=True)
    except httpx.TimeoutException:
        return Response("Navidrome tardó demasiado en responder", status_code=504)
    except httpx.HTTPError:
        return Response("No se pudo conectar con Navidrome", status_code=502)

    async def contenido():
        try:
            async for trozo in respuesta.aiter_raw():
                yield trozo
        finally:
            # Si el cliente se desconecta, igual se cierra la conexión con Navidrome
            with anyio.CancelScope(shield=True):
                await respuesta.aclose()

    salida = StreamingResponse(contenido(), status_code=respuesta.status_code)
    salida.raw_headers = [
        (k.lower().encode("latin-1"), v.encode("latin-1"))
        for k, v in respuesta.headers.multi_items()
        if k.lower() not in HOP_BY_HOP
    ]
    return salida