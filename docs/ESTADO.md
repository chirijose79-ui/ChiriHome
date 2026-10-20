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
- Repo público: github.com/chirijose79-ui/ChiriHome (rama main)

## App Android (app-android/ChiriMusic)
- Kotlin, Jetpack Compose, Media3 (servicio MediaSessionService), Retrofit + kotlinx-serialization, Coil 3, Navigation Compose
- AGP 9.4.1, Kotlin 2.2.10, compileSdk 37, minSdk 26
- Sin Hilt ni Room todavía (necesitan KSP, que da problemas con AGP 9). Se usan ViewModel y objetos simples
- Paquete: com.chirihome.music
- Estructura: data/ (modelos, SubsonicApi, Sesion, Favoritos, Playlists), player/ (Reproductor, ServicioReproduccion), ui/ (login, albumes, album, buscar, favoritos, cola, playlists, playlist, componentes, reproductor, theme)
- API Subsonic de Navidrome con token (md5 de contraseña + sal). HTTP en claro permitido solo para 192.168.1.88
- La sesión guarda URL, usuario y token (no la contraseña) en almacenamiento privado, con allowBackup desactivado

## Decisiones
- Todo gratuito
- Música: Navidrome. Video: Jellyfin por Tailscale (Fase 7)
- Gateway propio con FastAPI para usuarios y permisos (Fase 6)

## Fase actual
- Fase 4 avanzada: descargas offline, caché, ecualizador.
- Siguiente: Fase 4 (Android Auto).

## Pendientes de la Fase 1
- Fijar IP en el router, crear usuario normal en Navidrome, corregir números de pista.