import os
from contextlib import asynccontextmanager

import anyio
import httpx
from fastapi import FastAPI, Request
from fastapi.responses import Response, StreamingResponse

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


@asynccontextmanager
async def ciclo_de_vida(app: FastAPI):
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


@app.get("/_gateway/salud")
async def salud():
    return {"estado": "ok"}


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
