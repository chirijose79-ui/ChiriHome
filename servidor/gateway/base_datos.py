import os
import sqlite3
from contextlib import contextmanager

RUTA = os.environ.get("DB_PATH", "/data/gateway.db")

ESQUEMA = """
CREATE TABLE IF NOT EXISTS usuarios (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre TEXT NOT NULL UNIQUE COLLATE NOCASE,
    clave_hash TEXT NOT NULL,
    rol TEXT NOT NULL DEFAULT 'usuario' CHECK (rol IN ('admin', 'usuario')),
    navidrome_usuario TEXT,
    activo INTEGER NOT NULL DEFAULT 1,
    version_token INTEGER NOT NULL DEFAULT 1,
    creado TEXT NOT NULL DEFAULT (datetime('now'))
);
"""


@contextmanager
def _conexion():
    con = sqlite3.connect(RUTA, timeout=10)
    con.row_factory = sqlite3.Row
    try:
        yield con
        con.commit()
    finally:
        con.close()


def iniciar():
    carpeta = os.path.dirname(RUTA)
    if carpeta:
        os.makedirs(carpeta, exist_ok=True)
    with _conexion() as con:
        con.execute("PRAGMA journal_mode=WAL")
        con.executescript(ESQUEMA)


def _fila(fila):
    return dict(fila) if fila else None


def buscar_por_nombre(nombre):
    with _conexion() as con:
        return _fila(
            con.execute("SELECT * FROM usuarios WHERE nombre = ?", (nombre,)).fetchone()
        )


def buscar_por_id(id_usuario):
    with _conexion() as con:
        return _fila(
            con.execute("SELECT * FROM usuarios WHERE id = ?", (id_usuario,)).fetchone()
        )


def listar():
    with _conexion() as con:
        filas = con.execute(
            "SELECT id, nombre, rol, navidrome_usuario, activo, creado "
            "FROM usuarios ORDER BY id"
        ).fetchall()
        return [dict(f) for f in filas]


def crear(nombre, clave_hash, rol, navidrome_usuario):
    with _conexion() as con:
        con.execute(
            "INSERT INTO usuarios (nombre, clave_hash, rol, navidrome_usuario) "
            "VALUES (?, ?, ?, ?)",
            (nombre, clave_hash, rol, navidrome_usuario),
        )


def fijar_activo(nombre, activo):
    # Subir version_token invalida los tokens que ya se hubieran entregado
    with _conexion() as con:
        cur = con.execute(
            "UPDATE usuarios SET activo = ?, version_token = version_token + 1 "
            "WHERE nombre = ?",
            (1 if activo else 0, nombre),
        )
        return cur.rowcount > 0


def cambiar_clave(nombre, clave_hash):
    with _conexion() as con:
        cur = con.execute(
            "UPDATE usuarios SET clave_hash = ?, version_token = version_token + 1 "
            "WHERE nombre = ?",
            (clave_hash, nombre),
        )
        return cur.rowcount > 0
