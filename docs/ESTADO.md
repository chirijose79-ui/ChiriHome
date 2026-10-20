# Estado actual

## Infraestructura
- Raspberry Pi 4B, hostname chiripi, IP 192.168.1.88
- Disco USB ext4 montado en /mnt/biblioteca (musica, video)
- Docker + Navidrome en ~/ChiriHome/servidor/navidrome (puerto 4533)
- Dominio chirihome.com en Cloudflare (túnel pendiente, Fase 5)

## Rutas
- Windows (PC y laptop): C:\ChiriHome
- Pi: /home/chiri/ChiriHome
- Flujo: editar en Windows → push → pull en la Pi

## Decisiones
- Todo gratuito
- App Android propia (ChiriMusic): Kotlin, Jetpack Compose, Media3, Retrofit con kotlinx-serialization, Coil, Navigation Compose
- Proyecto con AGP 9.4.1 y Kotlin 2.2.10. Hilt y Room (necesitan KSP) quedan para más adelante; por ahora ViewModel y objetos simples (Sesion, Reproductor)
- Música: Navidrome (API Subsonic). Video: Jellyfin por Tailscale (Fase 7)
- Gateway propio con FastAPI para usuarios y permisos (Fase 6)
- HTTP en claro permitido solo para 192.168.1.88 (network_security_config)

## Fase actual
- Fase 3: app Android v0.2.
- Hecho: login, álbumes con carátulas, canciones del álbum, reproducción en segundo plano con notificación, sesión guardada, cerrar sesión y búsqueda.
- Pendiente: favoritos, cola, playlists.

## Notas
- La sesión guarda URL, usuario y token Subsonic (no la contraseña) en almacenamiento privado, con allowBackup desactivado. Se reemplaza por JWT en la Fase 6.