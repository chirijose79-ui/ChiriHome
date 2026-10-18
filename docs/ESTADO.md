# Estado actual

## Infraestructura
- Raspberry Pi 4B, hostname chiripi, IP 192.168.1.88, usuario chiri
- Disco USB ext4 montado en /mnt/biblioteca (musica, video)
- Docker + Navidrome en ~/ChiriHome/servidor/navidrome (puerto 4533)
- Dominio chirihome.com en Cloudflare (túnel pendiente, Fase 5)

## Rutas
- Windows (PC y laptop): C:\ChiriHome
- Pi: /home/chiri/ChiriHome
- Flujo: editar en Windows → push → pull en la Pi

## Decisiones
- Todo gratuito
- App Android propia: Kotlin, Jetpack Compose, Media3, Retrofit, Room, Hilt
- Música: Navidrome (API Subsonic). Video: Jellyfin por Tailscale (Fase 7)
- Gateway propio con FastAPI para usuarios y permisos (Fase 6)

## Fase actual
Fase 2: app Android v0.1 (ChiriMusic en app-android/)