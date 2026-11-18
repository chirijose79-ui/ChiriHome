# Estado actual

## Infraestructura
- Raspberry Pi 4B (64 bits), hostname chiripi, IP fija 192.168.1.88 (configurada en la Pi y reservada en el router)
- Disco USB ext4 montado en /mnt/biblioteca (musica, video, respaldos)
- Docker en ~/ChiriHome/servidor/navidrome (docker-compose.yml con tres servicios: navidrome, gateway y jellyfin)
- Navidrome 0.64.2 en el puerto 4533 (sigue publicado: se usa para administrar en casa; por el túnel solo pasa el gateway)
- Red de Docker propia 10.77.0.0/24; el gateway tiene la IP fija 10.77.0.10
- Dominio chirihome.com en Cloudflare. Túnel con cloudflared (gestionado desde el panel web de Cloudflare): musica.chirihome.com → http://localhost:8000 (gateway)
- Jellyfin 10.11.11: puerto 8096 (y 7359/udp para que las apps lo encuentren en la red local), configuración y caché en /home/chiri/jellyfin (fuera del repo), videos en /mnt/biblioteca/video montados en solo lectura. Biblioteca "Westerns" (/media/Western). Solo reproducción directa: la Pi 4 no puede convertir video. Sus usuarios son propios, separados de los del gateway
- Tailscale instalado en la Pi (clave sin caducidad) y en el celular, plan personal gratuito. Es la vía para ver el video desde fuera: el video no pasa por Cloudflare. Jellyfin se publica con HTTPS solo dentro de la red de Tailscale (sudo tailscale serve --bg 8096). No usar tailscale funnel: abriría Jellyfin a todo internet. La dirección HTTPS contiene el nombre de la red de Tailscale y no se escribe en el repo, que es público (se ve con tailscale serve status). Otro dispositivo propio se añade iniciando sesión en Tailscale con la misma cuenta que la Pi. Para dar acceso a otra persona existe compartir solo la Pi con su cuenta, desde la página Machines del panel de Tailscale
- Copias de seguridad, todas en /mnt/biblioteca/respaldos y fuera del repo: Navidrome, cada día a las 3:00 UTC (las últimas 7) con su función incorporada (ND_BACKUP_PATH, ND_BACKUP_SCHEDULE y ND_BACKUP_COUNT); Jellyfin, cada domingo a las 4:00 con ~/respaldo-jellyfin.sh (para el contenedor, comprime /home/chiri/jellyfin/config y lo vuelve a arrancar; las últimas 4); gateway, diseñado cada día a las 4:30 con ~/respaldo-gateway.sh (API de copia de SQLite, sin parar nada; las últimas 8; ver Pendientes). Los scripts viven en /home/chiri y se programan con cron. El secreto ~/gateway.env no va en las copias: guardarlo aparte (hay una copia en C:\Respaldo-ChiriHome del PC). Las copias están en el mismo disco que la música: protegen de una falla de la tarjeta SD, no del disco, así que conviene copiar de vez en cuando las más recientes al PC

## Rutas
- Windows (PC y laptop): C:\ChiriHome
- Pi: /home/chiri/ChiriHome
- Flujo: editar en Windows → push → pull en la Pi → docker compose up -d --build
- Repo público: github.com/chirijose79-ui/ChiriHome (rama main)
- Fuera del repo, en la Pi: ~/gateway.env (secreto de los tokens), ~/gateway-datos (base de datos de usuarios del gateway) y /home/chiri/jellyfin (configuración de Jellyfin). Hacer copia de seguridad y no subirlos nunca a GitHub
- Fuera del repo, en Windows: C:\Llaves-ChiriHome\chirimusic.jks (llave de firma de la app, alias chirimusic, con dos copias de respaldo en otros sitios), C:\Respaldo-ChiriHome (copias del gateway) y las contraseñas de la llave en %USERPROFILE%\.gradle\gradle.properties

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
- Paquete: com.chirihome.music (no se puede cambiar una vez repartida la app)
- Estructura:
  - data/ (modelos, SubsonicApi, Sesion, Favoritos, Playlists, DescargasLocal, Gateway, Letras) y data/video/ (JellyfinModelos, JellyfinApi, SesionVideo, InformeVideo)
  - player/ (Reproductor, ServicioReproduccion, ServicioVideo, CacheAudio, EcualizadorAudio, Descargas, ServicioDescargas, BibliotecaAuto)
  - ui/ (login, albumes, album, buscar, favoritos, cola, playlists, playlist, descargas, ecualizador, letra, video, componentes, reproductor, theme)
- Login de música: la app llama a /_gateway/login, recibe el token y lo manda en el parámetro p de cada URL. La sesión guarda URL, usuario y token (no la contraseña) en almacenamiento privado, con allowBackup desactivado. Si el servidor responde 401, la app cierra la sesión y vuelve al login
- Dirección del servidor de música: https://musica.chirihome.com (por defecto, también en casa). HTTP en claro solo para 192.168.1.88, y solo para pruebas, porque ahí el login viaja sin cifrar
- Manifest: MainActivity gestiona el giro de pantalla (configChanges) para que el video no se reinicie al girar; declara el lanzador de Android TV (LEANBACK_LAUNCHER), pantalla táctil y leanback no obligatorias, y un banner de TV (drawable/banner_tv.xml)
- Icono: icono adaptable con nombres propios (drawable/ic_chiri_fondo, ic_chiri_primer_plano e ic_chiri_monocromo, más mipmap-anydpi-v26/ic_launcher y ic_launcher_round). Diseño: doble nota blanca con barra ámbar sobre degradado índigo. El SVG de 512 píxeles para una ficha de tienda, si algún día hace falta, está en recursos-play/icono-512.svg

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
- Sesión de video aparte de la de música (SesionVideo): servidor, usuario de Jellyfin y token. Se guarda el token, no la contraseña. Se recuerda la última dirección del servidor y un identificador de la instalación. Cerrar sesión en la música también cierra la de video
- Autenticación con la cabecera Authorization: MediaBrowser Client=..., Device=..., DeviceId=..., Version=..., Token=... en todas las peticiones, incluidas las carátulas y el propio video. No se usa api_key: Jellyfin lo marca como obsoleto y no estará en la versión 12
- Pantallas: login de video, cuadrícula de películas, detalle (carátula, año, duración, formatos del archivo, descripción) y reproductor. En las pantallas de video no aparece el minireproductor de música
- El reproductor vive en ServicioVideo (MediaSessionService, sesión con identificador "video" y notificación propia con el número 1002). La pantalla controla el servicio con un MediaController y dibuja la imagen con PlayerView (media3-ui). Lección aprendida: Media3 exige un identificador único por sesión dentro de la app; la sesión de música usa el vacío, y sin identificador propio la de video cerraba la app al pulsar Play
- Reproducción directa desde /Videos/{id}/stream?static=true, con subtítulos y pistas de audio
- Con la pantalla bloqueada o la app en segundo plano, el audio sigue sonando; la pista de imagen se desactiva para ahorrar batería y vuelve al regresar. La pantalla de bloqueo y la notificación muestran el título, la carátula y el texto "Escuchando el audio del video", con pausa, avance y retroceso; al tocarlas se abre la app. Al salir de la película con el gesto de atrás, o al cerrar la app desde recientes, el audio se detiene y el servicio se apaga. Antes de empezar, la app pide a Jellyfin la ficha y la carátula (como máximo unos 4 segundos; si fallan, empieza igual con un título genérico)
- Si el audio se corta con la pantalla apagada mucho rato, suele ser el ahorro de batería del fabricante (más agresivo en Xiaomi, Huawei y a veces Samsung): poner ChiriMusic en "sin restricciones"
- Ventana y pantalla completa: la orientación sigue al celular y respeta el bloqueo de rotación de Android. En vertical, el video va arriba (16:9) con la ficha de la película debajo; en horizontal, a pantalla completa con las barras ocultas. El botón de pantalla completa de los controles maximiza (gira a horizontal) o minimiza (vuelve a vertical); tras usarlo, la orientación vuelve a seguir al celular cuando lo giras a esa misma posición, salvo que la rotación automática esté desactivada
- Al empezar, la música se pausa sola (foco de audio)
- Continuar viendo: el detalle ofrece continuar desde donde ibas. La app informa a Jellyfin al empezar (/Sessions/Playing), cada 10 segundos y al pausar o reanudar (/Sessions/Playing/Progress) y al salir (/Sessions/Playing/Stopped), también con la pantalla bloqueada. Es Jellyfin quien marca la película como vista
- Limitación conocida: muchos celulares no decodifican audio DTS. Si una película se ve pero sin sonido, hay que elegir otra pista de audio o convertir ese archivo una sola vez

## Distribución de la app (APK firmado directo)
- Camino elegido: APK firmado, gratis. No se usa Play Store: la cuenta de desarrollador cuesta 25 USD una vez y una cuenta personal nueva exige 12 probadores durante 14 días antes de publicar en producción; además, la app solo funciona contra este servidor privado, lo que puede hacer que una revisión pública la rechace
- Firma: la llave está en C:\Llaves-ChiriHome\chirimusic.jks (alias chirimusic). Las contraseñas están solo en %USERPROFILE%\.gradle\gradle.properties (CHIRI_STORE_FILE, CHIRI_STORE_PASSWORD, CHIRI_KEY_ALIAS y CHIRI_KEY_PASSWORD), nunca en el proyecto ni en el repo. app/build.gradle.kts las lee y, si faltan, el APK sale sin firmar (app-release-unsigned.apk). Si se pierde la llave no se puede volver a firmar como la misma app
- Generar el APK: en C:\ChiriHome\app-android\ChiriMusic ejecutar .\gradlew assembleRelease; sale en app\build\outputs\apk\release\app-release.apk. Comprobar la firma con apksigner verify --print-certs y que la huella SHA-256 coincide con la de la llave. Antes de instalar una versión de lanzamiento hay que desinstalar la de depuración (misma app, distinta firma)
- La versión de lanzamiento recorta y optimiza el código: probar todo (login, música, descargas, bloqueo, ecualizador, video, Android Auto). Plan B si algo falla solo ahí: cambiar enable = true por false en el bloque optimization, a costa de un APK más grande
- Cada versión repartida necesita un versionCode mayor que la anterior (hoy 1) y un nombre de archivo con su versión (ChiriMusic-1.1.apk)
- Verificación de desarrolladores de Google: desde septiembre de 2026, en Brasil, Indonesia, Singapur y Tailandia, una app solo se instala en dispositivos certificados si la registró un desarrollador verificado; el resto del mundo va después (2027 en adelante). Perú no está en la primera tanda. Hay una cuenta gratuita de distribución limitada (hasta 20 dispositivos autorizados por sus usuarios) y un flujo avanzado para instalar apps no verificadas. Registrar la app pide identidad (nombre legal, dirección, correo, teléfono y a veces documento oficial) y el nombre del paquete con un APK firmado con esta llave

## Android TV y otros aparatos
- Un solo proyecto y un solo APK sirven en celular y TV. El Manifest ya está preparado; las pantallas siguen siendo las del celular, manejables con el control remoto pero sin diseño pensado para TV
- Cajas Android TV o Google TV: la app aparece en el inicio con su banner. Cajas Android genéricas y Fire TV Stick (Fire OS): instalan el APK, pero la app se abre desde el cajón de aplicaciones
- Instalar el APK en la caja: con un USB y un administrador de archivos, con la app gratuita Downloader, o con adb por la red (opciones de desarrollador y depuración por red). Hay que permitir orígenes desconocidos
- En casa, la caja no necesita Tailscale: música por https://musica.chirihome.com y video escribiendo 192.168.1.88:8096 en el login de video (empieza por número, así que la app usa http, que es la única dirección en claro que permite). Fuera de casa, el video necesitaría Tailscale en la caja
- Echo Show (sin probar): no se puede instalar la app. Se pueden abrir en su navegador Silk las webs de Navidrome (http://192.168.1.88:4533) y Jellyfin (http://192.168.1.88:8096) solo dentro de casa, y reproducir lo que Silk soporte, porque la Pi no puede convertir video. La música también se puede enviar por Bluetooth desde el celular. Controlarlo por voz con Alexa exige skills de terceros con direcciones públicas, lo que choca con tener Navidrome cerrado a internet: no recomendado

## Decisiones
- Todo gratuito
- Música: Navidrome. Video: Jellyfin por Tailscale, nunca por Cloudflare
- Cloudflare: los términos del CDN restringen servir vídeo y archivos grandes alojados fuera de Cloudflare; para audio personal el riesgo es bajo pero sin garantía, por eso el vídeo va por Tailscale
- Gateway propio con FastAPI para usuarios y permisos, con la autenticación externa de Navidrome (Remote-User)
- La administración de usuarios solo es accesible desde la red local, nunca por el túnel de Cloudflare
- Bibliotecas por usuario: se usa el multi-biblioteca nativo de Navidrome (Settings → Libraries y Users). Cada biblioteca necesita su propia carpeta, sin solaparse con otra
- Sin permiso de descargas offline en el gateway: las descargas usan la misma ruta que el streaming, así que no se puede imponer de verdad
- Los usuarios de Jellyfin son independientes de los del gateway y de Navidrome
- La app se reparte como APK firmado directo y no por Play Store

## Fase actual
- Fases 1 a 7 terminadas. La app de música y video está completa según el plan original, con icono propio, APK firmado y audio del video en segundo plano.
- En marcha: reparto del APK y adaptación a Android TV.

## Pendientes
- Subir versionCode a 2 y versionName a "1.1" en app/build.gradle.kts, regenerar el APK de lanzamiento (incluye el audio del video) y probarlo entero
- Repartir el APK por GitHub Releases, con su huella SHA-256 en las notas
- Registrar la app en Android Developer Console con la cuenta gratuita de distribución limitada
- Probar el APK en la caja de TV y decidir qué pantallas necesitan diseño para TV
- Confirmar que el respaldo automático del gateway (~/respaldo-gateway.sh, cron diario a las 4:30) está activo; si no, repetirlo a mano cuando cambien los usuarios. Comprobar también con ls que el respaldo semanal de Jellyfin genera copias
- Confirmar que el usuario normal recibe 403 en la API de administración
- En Navidrome (Settings → Libraries), marcar la biblioteca como predeterminada para usuarios nuevos, si existe esa opción en su versión
- Revisar si la imagen de Docker deluan/navidrome sigue actualizándose o hay que pasar a navidrome/navidrome (la documentación oficial ya usa este último nombre)
- Probar el Echo Show (navegador y Bluetooth) y, si se quiere, el reloj Xiaomi S5