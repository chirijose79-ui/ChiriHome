# Estado actual

## Infraestructura
- Raspberry Pi 4B, hostname chiripi, IP 192.168.1.88
- Disco USB ext4 montado en /mnt/biblioteca (musica, video)
- Docker + Navidrome en ~/ChiriHome/servidor/navidrome (puerto 4533)
- Dominio chirihome.com en Cloudflare. Túnel con cloudflared: musica.chirihome.com → Navidrome (probado desde datos móviles, funciona)

## Rutas
- Windows (PC y laptop): C:\ChiriHome
- Pi: /home/chiri/ChiriHome
- Flujo: editar en Windows → push → pull en la Pi
- Repo público: github.com/chirijose79-ui/ChiriHome (rama main)

## App Android (app-android/ChiriMusic)
- Kotlin, Jetpack Compose, Media3 1.10.0 (servicio de reproducción), Retrofit + kotlinx-serialization, Coil 3, Navigation Compose
- AGP 9.4.1, Kotlin 2.2.10, compileSdk 37, minSdk 26
- Sin Hilt ni Room (necesitan KSP, que da problemas con AGP 9). Se usan ViewModel y objetos simples
- Paquete: com.chirihome.music
- Estructura:
  - data/ (modelos, SubsonicApi, Sesion, Favoritos, Playlists, DescargasLocal)
  - player/ (Reproductor, ServicioReproduccion, CacheAudio, EcualizadorAudio, Descargas, ServicioDescargas, BibliotecaAuto)
  - ui/ (login, albumes, album, buscar, favoritos, cola, playlists, playlist, descargas, ecualizador, componentes, reproductor, theme)
- API Subsonic de Navidrome con token (md5 de contraseña + sal). HTTP en claro permitido solo para 192.168.1.88; fuera de casa se usa https://musica.chirihome.com (pantalla de login lo trae por defecto)
- La sesión guarda URL, usuario y token (no la contraseña) en almacenamiento privado, con allowBackup desactivado

## Fase 4: cómo funciona
- Caché de streaming: SimpleCache de 500 MB (carpeta cache/audio), clave de caché = id de la canción
- Descargas offline: DownloadManager de Media3, caché aparte en files/descargas sin borrado automático; datos de cada canción en files/descargas.json (DescargasLocal). Botón en cada canción, en álbum y en playlist; pantalla Descargas
- Ecualizador: android.media.audiofx.Equalizer enganchado al audioSessionId; ajustes guardados
- Android Auto: ServicioReproduccion es un MediaLibraryService y BibliotecaAuto expone Álbumes, Playlists y Descargas. Código preparado, SIN PROBAR (falta el Desktop Head Unit)

## Decisiones
- Todo gratuito
- Música: Navidrome. Video: Jellyfin por Tailscale (Fase 7)
- Cloudflare: los términos del CDN restringen servir vídeo y archivos grandes alojados fuera de Cloudflare; para audio personal el riesgo es bajo pero sin garantía, por eso el vídeo irá por Tailscale
- Gateway propio con FastAPI para usuarios y permisos (Fase 6)

## Fase actual
- Fases 1 a 3 terminadas.
- Fase 4: caché, ecualizador y descargas terminados y probados; Android Auto pendiente de probar.
- Fase 5: túnel de Cloudflare funcionando; falta el usuario normal en Navidrome.

## Pendientes
- Fijar IP en el router
- Crear usuario normal (no administrador) en Navidrome y usarlo en la app
- Corregir números de pista (Picard / Mp3tag)
- Probar Android Auto con el Desktop Head Unit
- Carátulas en el coche: pueden no verse porque el servidor usa HTTP (a revisar al probar)