# Estado actual

## Infraestructura
- Raspberry Pi 4B, hostname chiripi, IP 192.168.1.88
- Disco USB ext4 montado en /mnt/biblioteca (musica, video)
- Docker en ~/ChiriHome/servidor/navidrome (docker-compose.yml con dos servicios: navidrome y gateway)
- Navidrome 0.64.2 en el puerto 4533 (sigue publicado: se usa para administrar en casa y por el túnel solo pasa el gateway)
- Red de Docker propia 10.77.0.0/24; el gateway tiene la IP fija 10.77.0.10
- Dominio chirihome.com en Cloudflare. Túnel con cloudflared (gestionado desde el panel web de Cloudflare): musica.chirihome.com → http://localhost:8000 (gateway)
- Copias de seguridad: Navidrome guarda una copia de su base de datos cada día a las 3:00 UTC (las últimas 7) en /mnt/biblioteca/respaldos/navidrome, fuera del repo. El respaldo del gateway es manual (docker compose exec gateway con la API de copia de SQLite) y está en C:\Respaldo-ChiriHome en el PC

## Rutas
- Windows (PC y laptop): C:\ChiriHome
- Pi: /home/chiri/ChiriHome
- Flujo: editar en Windows → push → pull en la Pi → docker compose up -d --build
- Repo público: github.com/chirijose79-ui/ChiriHome (rama main)
- Fuera del repo, en la Pi: ~/gateway.env (secreto de los tokens) y ~/gateway-datos (base de datos de usuarios del gateway). Hacer copia de seguridad de ambos y no subirlos nunca a GitHub

## Gateway (servidor/gateway)
- Python 3.13 en Docker. Librerías con versión fijada: fastapi 0.136.3, uvicorn 0.52.4, httpx 0.28.1, argon2-cffi 25.1.0, PyJWT 2.13.0
- Archivos: main.py (proxy y rutas), base_datos.py (SQLite), seguridad.py (hash, tokens, límite de intentos, caducidad), cli.py (gestión de usuarios)
- Rutas propias: POST /_gateway/login (devuelve token, válido 30 días), GET /_gateway/yo, GET /_gateway/salud
- Toda otra ruta exige token (parámetro p de Subsonic o cabecera Authorization: Bearer). Sin token responde 401
- Al reenviar, el gateway quita los parámetros p, t, s y la cabecera Authorization, borra cualquier Remote-User del cliente y añade Remote-User con el usuario de Navidrome vinculado
- Navidrome solo confía en Remote-User si la petición viene de 10.77.0.10/32 (variables ND_EXTAUTH_TRUSTEDSOURCES y ND_REVERSEPROXYWHITELIST). Probado: desde otra IP la cabecera se ignora
- Usuarios: roles admin y usuario, activar/desactivar, cambio de contraseña, vínculo con el usuario de Navidrome y fecha de caducidad (hasta el final del día, UTC). Cambiar la contraseña o desactivar invalida los tokens ya entregados
- Límite de intentos de login: 5 fallidos por usuario y 20 por IP cada 15 minutos (429)
- Gestión: docker compose exec gateway python cli.py crear | listar | activar | desactivar | clave | vincular | expira | borrar
- La web de Navidrome ya no funciona a través del túnel (el navegador no manda el token). Para administrar: http://192.168.1.88:4533 en casa

## App Android (app-android/ChiriMusic)
- Kotlin, Jetpack Compose, Media3 1.10.0 (servicio de reproducción), Retrofit + kotlinx-serialization, Coil 3, Navigation Compose
- AGP 9.4.1, Kotlin 2.2.10, compileSdk 37, minSdk 26
- Sin Hilt ni Room (necesitan KSP, que da problemas con AGP 9). Se usan ViewModel y objetos simples
- Paquete: com.chirihome.music
- Estructura:
  - data/ (modelos, SubsonicApi, Sesion, Favoritos, Playlists, DescargasLocal, Gateway, Letras)
  - player/ (Reproductor, ServicioReproduccion, CacheAudio, EcualizadorAudio, Descargas, ServicioDescargas, BibliotecaAuto)
  - ui/ (login, albumes, album, buscar, favoritos, cola, playlists, playlist, descargas, ecualizador, letra, componentes, reproductor, theme)
- Login: la app llama a /_gateway/login, recibe el token y lo manda en el parámetro p de cada URL. La sesión guarda URL, usuario y token (no la contraseña) en almacenamiento privado, con allowBackup desactivado. Si el servidor responde 401, la app cierra la sesión y vuelve al login
- Dirección del servidor: https://musica.chirihome.com (por defecto, también en casa). HTTP en claro solo para 192.168.1.88, y solo para pruebas, porque ahí el login viaja sin cifrar

## Fase 4: cómo funciona
- Caché de streaming: SimpleCache de 500 MB (cache/audio), clave de caché = id de la canción
- Descargas offline: DownloadManager de Media3, caché aparte en files/descargas sin borrado automático; datos de cada canción en files/descargas.json (DescargasLocal). Botón en cada canción, en álbum y en playlist; pantalla Descargas
- Ecualizador: android.media.audiofx.Equalizer enganchado al audioSessionId; ajustes guardados
- Android Auto: ServicioReproduccion es un MediaLibraryService y BibliotecaAuto expone Álbumes, Playlists y Descargas. Código preparado, SIN PROBAR (falta el Desktop Head Unit)

## Reproducción: aleatorio, repetir y letra
- Repetir: no / lista / una (modo de repetición de Media3). Se guarda
- Aleatorio: hecho a mano, no con el modo de Media3. El interruptor mezcla lo que falta por sonar y las listas nuevas empiezan por la canción tocada con el resto mezclado; el botón "Reproducir en aleatorio" (álbum y playlist) mezcla todo. Se guarda
- Letra: pantalla que usa getLyricsBySongId de Navidrome (OpenSubsonic). Resalta la línea actual si la letra está sincronizada y salta al tocar una línea. Necesita conexión. Código preparado, Probado y funcionando

## Decisiones
- Todo gratuito
- Música: Navidrome. Video: Jellyfin por Tailscale (Fase 7)
- Gateway propio con FastAPI para usuarios y permisos (Fase 6), con la autenticación externa de Navidrome (Remote-User)
- Bibliotecas por usuario: se usa el multi-biblioteca nativo de Navidrome (Settings → Libraries y Users). Cada biblioteca necesita su propia carpeta, sin solaparse con otra
- Sin permiso de descargas offline en el gateway: las descargas usan la misma ruta que el streaming, así que no se puede imponer de verdad
- Cloudflare: los términos del CDN restringen servir vídeo y archivos grandes alojados fuera de Cloudflare; para audio personal el riesgo es bajo pero sin garantía, por eso el vídeo irá por Tailscale

## Fase actual
- Fases 1 a 3 y 5 terminadas.
- Fase 4: caché, ecualizador y descargas terminados y probados; Android Auto pendiente de probar.
- Fase 6: gateway funcionando con login, vínculo con Navidrome y cuentas con caducidad; faltan las bibliotecas por usuario (nativas de Navidrome) y el panel de administración.
- App: aleatorio y repetir terminados y probados; letra terminada y probada.

## Pendientes
- Fijar IP en el router
- Corregir números de pista (Picard / Mp3tag)
- Probar Android Auto con el Desktop Head Unit (las carátulas pueden no verse en el coche porque el servidor usaba HTTP; ahora el túnel es HTTPS)
- Probar la pantalla de letra; si las canciones no tienen letra, añadirlas (archivos .lrc junto a la canción, etiquetas incrustadas o un plugin de Navidrome con LRCLIB)
- Revisar si la imagen de Docker deluan/navidrome sigue actualizándose o hay que pasar a navidrome/navidrome
- Panel de administración de usuarios (sin terminal)