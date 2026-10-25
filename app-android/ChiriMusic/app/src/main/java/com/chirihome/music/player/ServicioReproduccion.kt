package com.chirihome.music.player

import android.app.PendingIntent
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
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.chirihome.music.MainActivity
import com.chirihome.music.data.Sesion
import com.google.common.collect.ImmutableList
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

        // Android Auto o la pantalla de bloqueo pueden arrancar este servicio sin abrir la app
        Sesion.init(this)
        if (Sesion.api == null) Sesion.restaurar()
        Descargas.iniciar(this)
        Reproductor.cargarAjustes(this)

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

        // Se aplica el modo de repetición guardado, aunque la app no esté abierta
        player.repeatMode = Reproductor.repetir.value

        EcualizadorAudio.conectar(this, player.audioSessionId)
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                EcualizadorAudio.conectar(this@ServicioReproduccion, audioSessionId)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                Reproductor.registrarRepetir(repeatMode)
            }
        })

        // Al tocar el reproductor de la pantalla de bloqueo o de la notificación, se abre la app
        val intentoApp = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        val abrirApp = PendingIntent.getActivity(
            this,
            0,
            intentoApp,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        sesion = MediaLibrarySession.Builder(this, player, BibliotecaAuto(scope))
            .setSessionActivity(abrirApp)
            .setMediaButtonPreferences(botones(player))
            .build()

        // Cuando cambia el aleatorio o la repetición, se actualizan los iconos
        Reproductor.alCambiarAjustes = { actualizarBotones() }
    }

    private fun botones(player: Player): ImmutableList<CommandButton> {
        val aleatorio = Reproductor.aleatorio.value
        val botonAleatorio = CommandButton.Builder(
            if (aleatorio) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF
        )
            .setDisplayName(if (aleatorio) "Aleatorio activado" else "Aleatorio desactivado")
            .setSessionCommand(BibliotecaAuto.COMANDO_ALEATORIO)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()

        val modo = player.repeatMode
        val botonRepetir = CommandButton.Builder(
            when (modo) {
                Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
                Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
                else -> CommandButton.ICON_REPEAT_OFF
            }
        )
            .setDisplayName(
                when (modo) {
                    Player.REPEAT_MODE_ALL -> "Repetir lista"
                    Player.REPEAT_MODE_ONE -> "Repetir una canción"
                    else -> "Repetir desactivado"
                }
            )
            .setSessionCommand(BibliotecaAuto.COMANDO_REPETIR)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()

        return ImmutableList.of(botonAleatorio, botonRepetir)
    }

    private fun actualizarBotones() {
        val s = sesion ?: return
        s.setMediaButtonPreferences(botones(s.player))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = sesion

    // Si cierras la app desde recientes: sigue sonando, pero si estaba en pausa el servicio se apaga
    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = sesion?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        Reproductor.alCambiarAjustes = {}
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