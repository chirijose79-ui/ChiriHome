# Plan ChiriHome

- [x] **Fase 1:** Servidor de música (Pi + disco USB + Docker + Navidrome)
  - [x] Fijar IP 192.168.1.88 en el router
  - [x] Crear usuario normal en Navidrome
  - [x] Corregir números de pista (Picard / Mp3tag)
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
  - [x] Android Auto (código preparado, falta probarlo con el Desktop Head Unit)
- [x] **Fase 5:** Acceso remoto (Cloudflare Tunnel, musica.chirihome.com)
  - [x] Túnel con cloudflared funcionando
  - [x] Usar un usuario normal (no administrador) en la app
  - [x] El túnel apunta al gateway, no directo a Navidrome
- [ ] **Fase 6:** Usuarios y permisos propios (gateway FastAPI)
  - [x] Gateway transparente en Docker (reenvía a Navidrome con streaming y rangos)
  - [x] Usuarios propios, login con token y límite de intentos
  - [x] Conexión con Navidrome por autenticación externa (cabecera Remote-User)
  - [x] App con login contra el gateway (el token va en el parámetro p)
  - [x] Cuentas con fecha de caducidad
  - [ ] Bibliotecas por usuario (con el multi-biblioteca nativo de Navidrome, cuando haga falta)
  - [x] API y panel de administración (gestionar usuarios sin terminal)
- [ ] **Fase 7:** Servidor de video (Jellyfin + Tailscale + pantalla de video)

## Mejoras de la app
- [x] Aleatorio (interruptor y botón "Reproducir en aleatorio") y repetir (no / lista / una)
- [x] Letra de la canción (código preparado, falta probarlo; puede requerir añadir letras al servidor)

## Funciones propias (después de la fase 6)
- [ ] Playlists automáticas
- [ ] Bot de Telegram
- [ ] Panel web de administración
- [ ] Reportes de uso