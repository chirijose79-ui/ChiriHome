import argparse
import getpass
import re
import sqlite3
import sys

import base_datos
import seguridad

PATRON_NOMBRE = re.compile(r"^[A-Za-z0-9_.-]{3,32}$")
PATRON_NAVIDROME = re.compile(r"^[A-Za-z0-9_.@-]{1,64}$")


def pedir_clave() -> str:
    clave = getpass.getpass("Contraseña (mínimo 10 caracteres): ")
    if len(clave) < 10:
        sys.exit("La contraseña debe tener al menos 10 caracteres.")
    if getpass.getpass("Repite la contraseña: ") != clave:
        sys.exit("Las contraseñas no coinciden.")
    return clave


def validar_navidrome(valor: str) -> str:
    if not PATRON_NAVIDROME.match(valor):
        sys.exit(
            "Usuario de Navidrome no válido: usa letras, números, punto, guion, "
            "guion bajo o arroba, sin espacios."
        )
    return valor


def crear(args):
    if not PATRON_NOMBRE.match(args.nombre):
        sys.exit("Nombre no válido: usa de 3 a 32 letras, números, punto, guion o guion bajo.")
    navidrome = validar_navidrome(args.navidrome or args.nombre)
    clave = pedir_clave()
    try:
        base_datos.crear(args.nombre, seguridad.hashear(clave), args.rol, navidrome)
    except sqlite3.IntegrityError:
        sys.exit("Ya existe un usuario con ese nombre.")
    print(f"Usuario '{args.nombre}' creado con rol {args.rol}.")


def listar(args):
    usuarios = base_datos.listar()
    if not usuarios:
        print("No hay usuarios.")
        return
    print(f"{'ID':<4} {'NOMBRE':<20} {'ROL':<8} {'NAVIDROME':<20} {'ACTIVO':<7} CREADO")
    for u in usuarios:
        print(
            f"{u['id']:<4} {u['nombre']:<20} {u['rol']:<8} "
            f"{(u['navidrome_usuario'] or '-'):<20} "
            f"{'sí' if u['activo'] else 'no':<7} {u['creado']}"
        )


def activar(args):
    if not base_datos.fijar_activo(args.nombre, True):
        sys.exit("No existe ese usuario.")
    print(f"Usuario '{args.nombre}' activado.")


def desactivar(args):
    if not base_datos.fijar_activo(args.nombre, False):
        sys.exit("No existe ese usuario.")
    print(f"Usuario '{args.nombre}' desactivado. Sus sesiones dejan de valer.")


def clave(args):
    nueva = pedir_clave()
    if not base_datos.cambiar_clave(args.nombre, seguridad.hashear(nueva)):
        sys.exit("No existe ese usuario.")
    print(f"Contraseña de '{args.nombre}' cambiada. Sus sesiones anteriores dejan de valer.")


def vincular(args):
    navidrome = validar_navidrome(args.navidrome)
    if not base_datos.fijar_navidrome(args.nombre, navidrome):
        sys.exit("No existe ese usuario.")
    print(f"Usuario '{args.nombre}' vinculado al usuario de Navidrome '{navidrome}'.")


def main():
    base_datos.iniciar()
    parser = argparse.ArgumentParser(description="Gestión de usuarios del gateway ChiriHome")
    sub = parser.add_subparsers(dest="orden", required=True)

    p = sub.add_parser("crear", help="Crea un usuario")
    p.add_argument("nombre")
    p.add_argument("--rol", choices=["admin", "usuario"], default="usuario")
    p.add_argument("--navidrome", help="Usuario de Navidrome vinculado (por defecto, el mismo nombre)")
    p.set_defaults(func=crear)

    p = sub.add_parser("listar", help="Lista los usuarios")
    p.set_defaults(func=listar)

    p = sub.add_parser("activar", help="Reactiva un usuario")
    p.add_argument("nombre")
    p.set_defaults(func=activar)

    p = sub.add_parser("desactivar", help="Desactiva un usuario")
    p.add_argument("nombre")
    p.set_defaults(func=desactivar)

    p = sub.add_parser("clave", help="Cambia la contraseña de un usuario")
    p.add_argument("nombre")
    p.set_defaults(func=clave)

    p = sub.add_parser("vincular", help="Cambia el usuario de Navidrome vinculado")
    p.add_argument("nombre")
    p.add_argument("navidrome", help="Nombre exacto del usuario en Navidrome")
    p.set_defaults(func=vincular)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()