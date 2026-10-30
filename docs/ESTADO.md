# Estado actual

## Infraestructura
- Raspberry Pi 4B (64 bits), hostname chiripi, IP fija 192.168.1.88 (configurada en la Pi y reservada en el router)
- Disco USB ext4 montado en /mnt/biblioteca (musica, video, respaldos)
- Docker en ~/ChiriHome/servidor/navidrome (docker-compose.yml con tres servicios: navidrome, gateway y jellyfin)
- Navidrome 0.64.2 en el puerto 4533 (sigue publicado: se usa para administrar en casa; por el túnel solo pasa el gateway)
- Red de Docker propia 10.77.0.0/24; el gateway tiene la IP fija 10.77.0.10
- Dominio chirihome.com en Cloudflare. Túnel con cloudflared (gestionado desde el panel web de Cloudflare): musica.chirihome.com → http://localhost:8000 (gateway)
- Jellyfin 10.11.11: puerto 8096 (y 7359/udp para que las apps lo encuentren en la red local), configuración y caché en /home/chiri/jellyfin (fuera del repo), videos en /mnt/biblioteca/video montados en solo lectura. Biblioteca "Westerns" (/media/Western). Solo reproducción directa: la Pi 4 no puede convertir video. Sus usuarios son propios, separados de los del gateway
- Tailscale instalado en la Pi (clave sin caducidad) y en el celular, plan personal gratuito. Es la vía para ver el video desde fuera: el video no pasa por Cloudflare. Jellyfin se publica con HTTPS solo dentro de la red de Tailscale (sudo tailscale serve --bg 8096). No usar tailscale funnel: abriría Jellyfin a todo internet. La dirección HTTPS contiene el nombre de la red de Tailscale y no se escribe en el repo, que es público (se ve con tailscale serve status)
- Copias de seguridad: Navidrome guarda una copia de su base de datos cada día a las 3:00 UTC (las últimas 7) en /mnt/biblioteca/respaldos/navidrome, fuera del repo (variables ND_BACKUP_PATH, ND_BACKUP_SCHEDULE y ND_BACKUP_COUNT). El respaldo del gateway es manual (docker compose exec gateway con la API de copia de SQLite) y está en C:\Respaldo-ChiriHome en el PC: repetirlo cuando se creen o cambien usuarios

## Rutas
- Windows (PC y laptop): C:\ChiriHome
- Pi: /home/chiri/ChiriHome
- Flujo: editar en Windows → push → pull en la Pi → docker compose up -d --build
- Repo público: github.com/chirijose79-ui/ChiriHome (rama main)
- Fuera del repo, en la Pi: ~/gateway.env (secreto de los tokens), ~/gateway-datos (base de datos de usuarios del gateway) y /home/chiri/jellyfin (configuración de Jellyfin). Hacer copia de seguridad y no subirlos nunca a GitHub

## Gateway (servidor/gateway)
- Python 3.13 en Docker. Librerías con versión fijada: fastapi 0.136.3, uvicorn 0.52.4, httpx 0.28.1, argon2-cffi 25.1.0, PyJWT 2.13.0
- Archivos: main.py (proxy y rutas), base_datos.py (SQLite), seguridad.py (hash, tokens, límite de intentos, caducidad), cli.py (gestión de usuarios por terminal), panel.html y panel.js (página de administración)
- Rutas propias: POST /_gateway/login (devuelve token, válido 30 días), GET /_gateway/yo, GET /_gateway/salud
- Toda otra ruta exige token (parámetro p de Subsonic o cabecera Authorization: Bearer). Sin token responde 401
- Al reenviar, el gateway quita los parámetros p, t, s y la cabecera Authorization, borra cualquier Remote-User del cliente y añade Remote-User con el usuario de Navidrome vinculado
- Navidrome solo confía en Remote-User si la petición viene de 10.77.0.10/32 (variable ND_EXTAUTH_TRUSTEDSOURCES). Probado: desde otra IP la cabecera se ignora
- Usuarios: roles admin y usuario, activar/desactivar, cambio de contraseña, vínculo con el usuario de Navidrome y fecha de caducidad (hasta el final del día, UTC). Cambiar la contraseña o desactivar invalida los tokens ya entregados
- Límite de intentos de login: 5 fallidos por usuario y 20 por IP cada 15 minutos (429)
- Gestión por terminal: docker compose exec gateway python cli.py crear | listar | activar | desactivar | clave | vincular | expira | borrar
- Administración por web: página en http://192.168.1.88:8000/_gateway/admin y API en /_gateway/admin/usuarios (listar, crear, modificar, borrar). Solo funciona desde la red local: si la petición llega por el túnel de Cloudflare responde 404 (variable ADMIN_SOLO_LAN, activa por defecto). Solo administradores; no puedes quitarte el rol, desactivarte ni borrarte a ti mismo, y siempre debe quedar un administrador activo. En la red local va por HTTP, así que la contraseña de administrador viaja sin cifrar: usarla solo en una red de confianza
- Cuentas nuevas: si un usuario del gateway se vincula a un nombre que no existe en Navidrome, Navidrome crea la cuenta sola la primera vez que entra, pero sin acceso a ninguna biblioteca hasta que se la des en su web (Settings → Users)
- La web de Navidrome ya no funciona a través del túnel (el navegador no manda el token). Para administrar Navidrome: http://192.168.1.88:4533 en casa

## App Android (app-android/ChiriMusic)
- Kotlin, Jetpack Compose, Media3 1.10.0 (exoplayer, session, datasource-okhttp y ui), Retrofit + kotlinx-serialization, Coil 3.3.0, Navigation Compose, iconos Material (material-icons-extended por el BOM de Compose: Google ya no la actualiza, pero sigue funcionando)
- AGP 9.4.1, Kotlin 2.2.10, compileSdk 37, minSdk 26
- Sin Hilt ni Room (necesitan KSP, que da problemas con AGP 9). Se usan ViewModel y objetos simples
- Paquete: com.chirihome.music
- Estructura:
  - data/ (modelos, SubsonicApi, Sesion, Favoritos, Playlists, DescargasLocal, Gateway, Letras) y data/video/ (JellyfinModelos, JellyfinApi, SesionVideo, InformeVideo)
  - player/ (Reproductor, ServicioReproduccion, CacheAudio, EcualizadorAudio, Descargas, ServicioDescargas, BibliotecaAuto)
  - ui/ (login, albumes, album, buscar, favoritos, cola, playlists, playlist, descargas, ecualizador, letra, video, componentes, reproductor, theme)
- Login de música: la app llama a /_gateway/login, recibe el token y lo manda en el parámetro p de cada URL. La sesión guarda URL, usuario y token (no la contraseña) en almacenamiento privado, con allowBackup desactivado. Si el servidor responde 401, la app cierra la sesión y vuelve al login
- Dirección del servidor de música: https://musica.chirihome.com (por defecto, también en casa). HTTP en claro solo para 192.168.1.88, y solo para pruebas, porque ahí el login viaja sin cifrar
- MainActivity declara en el Manifest que gestiona el giro de pantalla (configChanges), para que el video no se reinicie al girar

## Música: cómo funciona
- Caché de streaming: SimpleCache de 500 MB (cache/audio), clave de caché = id de la canción
- Descargas offline: DownloadManager de Media3, caché aparte en files/descargas sin borrado automático; datos de cada canción en files/descargas.json (DescargasLocal). Botón en cada canción, en álbum y en playlist; pantalla Descargas
- Ecualizador: android.media.audiofx.Equalizer enganchado al audioSessionId; ajustes guardados
- Android Auto: ServicioReproduccion es un MediaLibraryService y BibliotecaAuto expone Álbumes, Playlists y Descargas. Probado y funcionando
- Repetir: no / lista / una (modo de repetición de Media3). Se guarda
- Aleatorio: hecho a mano, no con el modo de Media3. El interruptor mezcla lo que falta por sonar y las listas nuevas empiezan por la canción tocada con el resto mezclado; el botón "Reproducir en aleatorio" (álbum y playlist) mezcla todo. Se guarda
- Letra: pantalla que usa getLyricsBySongId de Navidrome (OpenSubsonic). Resalta la línea actual si la letra está sincronizada y salta al tocar una línea. Necesita conexión
- Mini reproductor: botones con iconos, línea de progreso que se puede arrastrar y fondo difuminado con la carátula del álbum (carátula diminuta ampliada; el desenfoque nativo solo actúa desde Android 12)
- Pantalla de bloqueo y notificación: al tocar el reproductor se abre la app (setSessionActivity); tiene botones de aleatorio y repetir (comandos personalizados de la sesión, que también aparecen en Android Auto). El botón de letra no es posible ahí: Android no deja abrir pantallas desde esos botones. El fondo de ese reproductor lo dibuja el sistema

## Video: cómo funciona
- Sesión de video aparte de la de música (SesionVideo): servidor, usuario de Jellyfin y token. Se guarda el token, no la contraseña. Se recuerda la última dirección del servidor y un identificador de la instalación
- Autenticación con la cabecera Authorization: MediaBrowser Client=..., Device=..., DeviceId=..., Version=..., Token=... en todas las peticiones, incluidas las carátulas y el propio video. No se usa api_key: Jellyfin lo marca como obsoleto y no estará en la versión 12
- Pantallas: login de video, cuadrícula de películas, detalle (carátula, año, duración, formatos del archivo, descripción) y reproductor
- Reproductor: ExoPlayer con PlayerView (media3-ui), pantalla completa, subtítulos y pistas de audio, reproducción directa desde /Videos/{id}/stream?static=true. La orientación sigue al celular y respeta el bloqueo de rotación de Android. Al empezar, la música se pausa sola (foco de audio). Si sales de la app, se pausa
- Continuar viendo: el detalle ofrece continuar desde donde ibas. La app informa a Jellyfin al empezar (/Sessions/Playing), cada 10 segundos y al pausar o reanudar (/Sessions/Playing/Progress) y al salir (/Sessions/Playing/Stopped). Es Jellyfin quien marca la película como vista
- Cerrar sesión en la música también cierra la de video
- Limitación conocida: muchos celulares no decodifican audio DTS. Si una película