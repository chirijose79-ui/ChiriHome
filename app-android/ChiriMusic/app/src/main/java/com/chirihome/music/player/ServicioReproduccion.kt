package com.chirihome.music.player

import android.content.Intent
import android.app.PendingIntent
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
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.chirihome.music.data.Sesion
import com.chirihome.music.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@OptIn(UnstableApi::class)
class ServicioReproduccion : MediaLibraryService() {
    private var sesion: MediaLibrarySession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        // Android Auto puede arrancar este servicio sin abrir la app
        Sesion.init(this)
        if (Sesion.api == null) Sesion.restaurar()
        Descargas.iniciar(this)

        val origen = DefaultDataSource.Factory(this, DefaultHttpDataSource.Factory())
        val conCache = CacheDataSource.Factory()
            .setCache(CacheAudio.obtener(this))
            .setUpstreamDataSourceFactory(origen)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val conDescargas = CacheDataSource.Factory()
            .setCache(Descargas.cache(this))
            .setCacheWriteDataSinkFactory(null)
            .setUpstreamDataSourceFactory(conCache)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(conDescargas))
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

        val intentActividad = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntentActividad = PendingIntent.getActivity(
            this,
            0,
            intentActividad,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        sesion = MediaLibrarySession.Builder(
            this,
            player,
            BibliotecaAuto(scope)
        )
            .setSessionActivity(pendingIntentActividad)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = sesion

    // Si cierras la app desde recientes: sigue sonando, pero si estaba en pausa el servicio se apaga
    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = sesion?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        EcualizadorAudio.liberar()
        scope.cancel()
        sesion?.run {
            player.release()
            release()
        }
        sesion = null
        super.onDestroy()
    }
}