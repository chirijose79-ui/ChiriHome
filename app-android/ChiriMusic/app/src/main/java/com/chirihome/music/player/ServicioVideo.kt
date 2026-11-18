package com.chirihome.music.player

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.chirihome.music.MainActivity
import com.chirihome.music.data.video.SesionVideo

// Contiene el reproductor de la película, para que el audio siga sonando con la pantalla bloqueada
@OptIn(UnstableApi::class)
class ServicioVideo : MediaSessionService() {
    private var sesion: MediaSession? = null

    private val callback = object : MediaSession.Callback {
        // Si la pantalla de video ya no está conectada, el audio no debe quedarse sonando solo
        override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
            val hayPantalla = session.connectedControllers.any {
                !session.isMediaNotificationController(it)
            }
            if (!hayPantalla) {
                session.player.stop()
                session.player.clearMediaItems()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // El servicio puede arrancar sin que la app esté abierta
        SesionVideo.init(this)
        if (SesionVideo.api == null) SesionVideo.restaurar()

        // Cada petición lleva la cabecera de autenticación de Jellyfin vigente en ese momento
        val origen = DataSource.Factory {
            DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(30_000)
                .setDefaultRequestProperties(mapOf("Authorization" to SesionVideo.cabeceraAuth()))
                .createDataSource()
        }

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(origen))
            // Al pedir el foco de audio, la música se pausa sola
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // Un 401 significa que el token de Jellyfin ya no vale
                val causa = error.cause
                if (causa is HttpDataSource.InvalidResponseCodeException && causa.responseCode == 401) {
                    SesionVideo.sesionCaducada.value = true
                }
            }

            // Cuando ya no hay nada que reproducir, el servicio se apaga
            override fun onPlaybackStateChanged(estado: Int) {
                if (estado == Player.STATE_IDLE && player.mediaItemCount == 0) stopSelf()
            }
        })

        // Al tocar la notificación o el reproductor de la pantalla de bloqueo, se abre la app
        val intentoApp = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        val abrirApp = PendingIntent.getActivity(
            this,
            1,
            intentoApp,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        sesion = MediaSession.Builder(this, player)
            // Media3 exige un identificador distinto para cada sesión de la app:
            // la de música ya usa el vacío, así que esta lleva el suyo
            .setId("video")
            .setCallback(callback)
            .setSessionActivity(abrirApp)
            .build()

        // Notificación propia, para no pisar la de la música (que usa el número por defecto)
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setNotificationId(1002)
                .build()
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = sesion

    // Si cierras la app desde recientes, el video se detiene
    override fun onTaskRemoved(rootIntent: Intent?) {
        sesion?.player?.pause()
        stopSelf()
    }

    override fun onDestroy() {
        sesion?.run {
            player.release()
            release()
        }
        sesion = null
        super.onDestroy()
    }
}