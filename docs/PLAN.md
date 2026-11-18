# Plan ChiriHome

- [x] **Fase 1:** Servidor de música (Pi + disco USB + Docker + Navidrome)
  - [x] IP fija 192.168.1.88 (en la Pi y en el router)
  - [x] Usuario normal en Navidrome
  - [x] Corregir números de pista (Mp3tag)
- [x] **Fase 2:** App Android v0.1 (login, biblioteca, reproducción básica)
- [x] **Fase 3:** App Android v0.2 (segundo plano, cola, búsqueda, favoritos, playlists)
  - [x] Reproducción en segundo plano (servicio Media3 + notificación)
  - [x] Guardar la sesión y cerrar sesión
  - [x] Búsqueda de álbumes y canciones
  - [x] Favoritos
  - [x] Cola de reproducción
  - [x] Playlists
- [x] **Fase 4:** Offline y extras
  - [x] Caché de streaming
  - [x] Ecualizador
  - [x] Descargas offline (canciones, álbumes, playlists, pantalla Descargas)
  - [x] Android Auto
- [x] **Fase 5:** Acceso remoto (Cloudflare Tunnel, musica.chirihome.com)
  - [x] Túnel con cloudflared funcionando
  - [x] Usuario normal (no administrador) en la app
  - [x] El túnel apunta al gateway, no directo a Navidrome
- [x] **Fase 6:** Usuarios y permisos propios (gateway FastAPI)
  - [x] Gateway transparente en Docker (reenvía a Navidrome con streaming y rangos)
  - [x] Usuarios propios, login con token y límite de intentos
  - [x] Conexión con Navidrome por autenticación externa (cabecera Remote-User)
  - [x] App con login contra el gateway (el token va en el parámetro p)
  - [x] Cuentas con fecha de caducidad
  - [x] API y panel de administración (página web en /_gateway/admin, solo desde la red local)
  - [x] Bibliotecas por usuario: se usa el multi-biblioteca nativo de Navidrome (se configura desde su web cuando haga falta)
- [x] **Fase 7:** Servidor de video (Jellyfin + Tailscale + pantalla de video)
  - [x] Jellyfin 10.11.11 en Docker (biblioteca Westerns)
  - [x] Tailscale en la Pi y en el celular, con HTTPS solo dentro de la red (tailscale serve)
  - [x] Pantalla de video en la app: login de Jellyfin, biblioteca, detalle, reproductor
  - [x] Continuar viendo: la app recuerda por dónde ibas y avisa a Jellyfin
  - [x] El video gira junto con el celular
  - [x] Reproductor en ventana (vertical, con la ficha de la película debajo) y en pantalla completa, con botón para maximizar y minimizar
  - [x] El audio del video sigue con la pantalla bloqueada (servicio de video propio, con notificación y controles en la pantalla de bloqueo)

## Mejoras de la app
- [x] Aleatorio (interruptor y botón "Reproducir en aleatorio") y repetir (no / lista / una)
- [x] Letra de la canción (pantalla con letra sincronizada)
- [x] Iconos en lugar de texto en los botones
- [x] Mini reproductor con línea de progreso y fondo difuminado con la carátula del álbum
- [x] Pantalla de bloqueo y notificación: se abre la app al tocarlos y tienen botones de aleatorio y repetir (la letra no es posible ahí)
- [x] Icono propio de la app (doble nota sobre degradado índigo, con versión monocromática para iconos con tema)

## Distribución de la app
- [x] Camino elegido: APK firmado directo (gratis), no Play Store
- [x] Llave de firma creada, con dos copias, fuera del repo
- [x] Firma configurada en la compilación y APK de lanzamiento generado, verificado e instalado en el celular
- [ ] Subir versionCode a 2 y versionName a "1.1" y regenerar el APK (incluye el audio del video)
- [ ] Reparto por GitHub Releases (APK con su huella SHA-256 en las notas)
- [ ] Registro gratuito en Android Developer Console (distribución limitada) con el APK firmado
- [ ] Probar la app en la caja de TV

## Android TV
- [x] Manifest preparado: arranque en el inicio de la TV, sin pantalla táctil obligatoria y banner propio
- [ ] Probar el APK tal cual en la caja de TV
- [ ] Pantallas pensadas para TV (filas de carátulas, foco visible, controles manejables con el mando), con la librería de Google para TV

## Funciones propias (después de la fase 6)
- [ ] Playlists automáticas
- [ ] Bot de Telegram
- [x] Panel web de administración (hecho como parte de la Fase 6)
- [ ] Reportes de uso

## Ideas para más adelante
- [ ] Series en la pantalla de video (hoy solo películas)
- [ ] Marcar como vistas y mostrar el progreso en la cuadrícula de películas
- [ ] Carátula del reproductor de la pantalla de bloqueo desde la caché del celular (para verla sin conexión)
- [ ] Crear también el usuario en Navidrome desde el panel (hoy Navidrome lo crea solo la primera vez que entra)
- [ ] Controlar la música desde el reloj Xiaomi S5 por Bluetooth (sin probar)
- [ ] Probar la música y el video en un Echo Show (navegador y Bluetooth)