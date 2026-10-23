import os
import time
from collections import defaultdict, deque
from datetime import date, datetime, timezone

import jwt
from argon2 import PasswordHasher
from argon2.exceptions import InvalidHashError, VerificationError

_hasher = PasswordHasher()

# Se verifica contra este hash cuando el usuario no existe, para que
# tardar lo mismo no delate qué nombres existen
HASH_FALSO = _hasher.hash("clave-falsa-para-igualar-tiempos")

DURACION_TOKEN = 30 * 24 * 3600


def secreto() -> str:
    valor = os.environ.get("JWT_SECRET", "")
    if len(valor) < 32:
        raise RuntimeError("JWT_SECRET debe tener al menos 32 caracteres")
    return valor


def hashear(clave: str) -> str:
    return _hasher.hash(clave)


def verificar(clave: str, hash_guardado: str) -> bool:
    try:
        return _hasher.verify(hash_guardado, clave)
    except (VerificationError, InvalidHashError):
        return False


def caducada(expira) -> bool:
    """La cuenta vale hasta el final del día indicado (hora UTC)."""
    if not expira:
        return False
    try:
        limite = date.fromisoformat(expira)
    except ValueError:
        return True  # una fecha ilegible se trata como caducada, por seguridad
    return datetime.now(timezone.utc).date() > limite


def crear_token(usuario: dict) -> str:
    ahora = int(time.time())
    carga = {
        "sub": str(usuario["id"]),
        "nombre": usuario["nombre"],
        "rol": usuario["rol"],
        "ver": usuario["version_token"],
        "iat": ahora,
        "exp": ahora + DURACION_TOKEN,
    }
    return jwt.encode(carga, secreto(), algorithm="HS256")


def leer_token(token: str):
    try:
        return jwt.decode(
            token,
            secreto(),
            algorithms=["HS256"],
            options={"require": ["exp", "sub"]},
        )
    except jwt.PyJWTError:
        return None


class LimitadorIntentos:
    """Cuenta intentos fallidos en una ventana de tiempo (en memoria)."""

    def __init__(self, ventana: int = 900):
        self.ventana = ventana
        self._fallos = defaultdict(deque)

    def _purgar(self, d: deque):
        ahora = time.monotonic()
        while d and ahora - d[0] > self.ventana:
            d.popleft()

    def bloqueado(self, clave: str, maximo: int) -> bool:
        d = self._fallos.get(clave)
        if not d:
            return False
        self._purgar(d)
        if not d:
            del self._fallos[clave]
            return False
        return len(d) >= maximo

    def registrar_fallo(self, clave: str):
        if len(self._fallos) > 5000:
            for k in list(self._fallos):
                d = self._fallos[k]
                self._purgar(d)
                if not d:
                    del self._fallos[k]
        d = self._fallos[clave]
        self._purgar(d)
        d.append(time.monotonic())

    def limpiar(self, clave: str):
        self._fallos.pop(clave, None)