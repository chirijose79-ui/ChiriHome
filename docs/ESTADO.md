# Estado actual

## Infraestructura
- Raspberry Pi 4B, hostname chiripi, IP fija 192.168.1.88 (configurada en la Pi y reservada en el router)
- Disco USB ext4 montado en /mnt/biblioteca (musica, video, respaldos)
- Docker en ~/ChiriHome/servidor/navidrome (docker-compose.yml con dos servicios: navidrome y gateway)
- Navidrome 0.64.2 en el puerto 4533 (sigue publicado: se usa para administrar en casa y por el túnel solo pasa el gateway)
- Red de Docker propia 10.77.0.0/24; el gateway tiene la IP fija 10.77.0.10
- Dominio chirihome.com en Cloudflare. Túnel con cloudflared (gestionado desde el panel web de Cloudflare): musica.chirihome.com → http://localhost:8000 (gateway)
- Copias de seguridad: Navidrome guarda una copia de su base de datos cada día a las 3:00 UTC (las últimas 7) en /mnt/biblioteca/respaldos/navidrome, fuera del repo (variables ND_BACKUP_PATH, ND_BACKUP_SCHEDULE y ND_BACKUP_COUNT). El respaldo del gateway es manual (docker compose exec gateway con la API de copia de SQLite) y está en C:\Respaldo-ChiriHome en el PC: repetirlo cuando se creen o cambien usuarios

## Rutas
- Windows (PC y laptop): C:\ChiriHome
- Pi: /home/chiri/ChiriHome
- Flujo: editar en Windows → push → pull en la Pi → docker compose up -d --build
- Repo público: github.com/chirijose79-ui/ChiriHome (rama main)
- Fuera del repo, en la Pi: ~/gateway.env (secreto de los tokens) y ~/gateway-datos (base de datos de usuarios del gateway). Hacer copia de seguridad de ambos y no subirlos nunca a GitHub

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
- La web de Navidrome ya no funciona a través del túnel (el navegador no manda el token). Para administrar Navidrome: http://192.168.1.88:4533 en casa

## App Android (app-android/ChiriMusic)
- Kotlin, Jetpack Compose, Media3 1.10.0 (servicio de reproducción), Retrofit + kotlinx-serialization, Coil 3, Navigation Compose
- AGP 9.4.1, Kotlin 2.2.10, compileSdk 37, minSdk 26
- Sin Hilt ni Room (necesitan KSP, que da problemas con AGP 9). Se usan ViewModel y objetos simples
- Paquete: com.chirihome.music
- Estructura:
  - data/ (modelos, SubsonicApi, Sesion, Favoritos, Playlists, DescargasLocal, Gateway, Letras)
  - player/ (Reproductor, ServicioReproduccion, CacheAudio, EcualizadorAudio, Descargas, ServicioDescargas, BibliotecaAuto)
  - ui/ (login, albumes, album, buscar, favoritos, cola, playlists, playlist, descargas, ecualizador, letra, componentes, reproductor, theme)
- Login: la app llama a /_gateway/login, recibe el token y lo manda en el parámetro p de cada URL. La sesión guarda URL, usuario y token (no la contraseña) en almacenamiento privado, con allowBackup