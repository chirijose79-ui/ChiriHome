import os
from contextlib import asynccontextmanager

import anyio
import httpx
from anyio import to_thread
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import Response, StreamingResponse
from pydantic import BaseModel, Field

import base_datos
import seguridad

UPSTREAM = os.environ.get("UPSTREAM_URL", "http://navidrome:4533").rstrip("/")

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

MAX_FALLOS_POR_USUARIO = 5
MAX_FALLOS_POR_IP = 20

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


def _ip(request: Request) -> str:
    return request.headers.get("cf-connecting-ip") or (
        request.client.host if request.client else "desconocida"
    )


async def _usuario_actual(request: Request):
    cabecera = request.headers.get("authorization", "")
    if not cabecera.lower().startswith("bearer "):
        return None
    carga = seguridad.leer_token(cabecera[7:].strip())
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
    ):
        return None
    return usuario


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

    limitador.limpiar(clave_usuario)
    return {
        "token": seguridad.crear_token(usuario),
        "usuario": usuario["nombre"],
        "rol": usuario["rol"],
        "expira_en_segundos": seguridad.DURACION_TOKEN,
    }


@app.get("/_gateway/yo")
async def yo(request: Request):
    usuario = await _usuario_actual(request)
    if not usuario:
        raise HTTPException(status_code=401, detail="Sesión no válida o caducada")
    return {"usuario": usuario["nombre"], "rol": usuario["rol"]}


@app.api_route(
    "/{ruta:path}",
    methods=["GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
)
async def reenviar(ruta: str, request: Request):
    cliente: httpx.AsyncClient = request.app.state.cliente

    # Se usa la ruta y la consulta tal como llegaron, sin decodificar
    ruta_cruda = request.scope.get("raw_path") or request.url.path.encode()
    consulta = request.scope.get("query_string", b"")
    destino = UPSTREAM + ruta_cruda.decode("latin-1")
    if consulta:
        destino += "?" + consulta.decode("latin-1")

    cabeceras = [
        (k, v)
        for k, v in request.headers.items()
        if k.lower() not in HOP_BY_HOP and k.lower() != "host"
    ]
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
