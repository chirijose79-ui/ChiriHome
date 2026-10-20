package com.chirihome.music.player

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@OptIn(UnstableApi::class)
class ServicioReproduccion : MediaSessionService() {
    private var sesion: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val origen = DefaultDataSource.Factory(this, DefaultHttpDataSource.Factory())
        val conCache = CacheDataSource.Factory()
            .setCache(CacheAudio.obtener(this))
            .setUpstreamDataSourceFactory(origen)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(conCache))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        EcualizadorAudio.conectar(this, player.audioSessionId)
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                EcualizadorAudio.conectar(this@ServicioReproduccion, audioSessionId)
            }
        })

        sesion = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = sesion

    // Si cierras la app desde recientes: sigue sonando, pero si estaba en pausa el servicio se apaga
    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = sesion?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        EcualizadorAudio.liberar()
        sesion?.run {
            player.release()
            release()
        }
        sesion = null
        super.onDestroy()
    }
}