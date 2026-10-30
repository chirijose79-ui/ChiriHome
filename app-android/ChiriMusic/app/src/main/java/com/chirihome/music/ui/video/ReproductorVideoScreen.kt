package com.chirihome.music.ui.video

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.chirihome.music.data.video.InformeVideo
import com.chirihome.music.data.video.SesionVideo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import java.util.UUID

// El reproductor vive en el ViewModel para que no se reinicie al girar la pantalla
@OptIn(UnstableApi::class)
class ReproductorVideoViewModel(
    aplicacion: Application,
    handle: SavedStateHandle
) : AndroidViewModel(aplicacion) {
    private val id: String = checkNotNull(handle.get<String>("id"))

    // Desde qué milisegundo empezar (0 = desde el principio)
    private val desdeMs: Long = handle.get<Long>("desde") ?: 0L

    // Identifica esta reproducción ante Jellyfin
    private val sesionId: String = UUID.randomUUID().toString()
    private var inicioInformado = false

    val reproductor: ExoPlayer

    var error by mutableStateOf<String?>(null)
        private set

    init {
        // Jellyfin recibe la cabecera de autenticación en cada petición del video
        val origen = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("Authorization" to SesionVideo.cabeceraAuth()))
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)

        reproductor = ExoPlayer.Builder(aplicacion)
            .setMediaSourceFactory(DefaultMediaSourceFactory(aplicacion).setDataSourceFactory(origen))
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

        reproductor.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                this@ReproductorVideoViewModel.error = mensaje(error)
            }

            // La primera vez que de verdad suena, se avisa a Jellyfin
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && !inicioInformado) {
                    inicioInformado = true
                    InformeVideo.inicio(id, sesionId, reproductor.currentPosition)
                }
            }

            // Al pausar o reanudar se informa enseguida
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (inicioInformado) {
                    InformeVideo.progreso(
                        id, sesionId, reproductor.currentPosition, enPausa = !playWhenReady
                    )
                }
            }
        })

        reproductor.setMediaItem(MediaItem.fromUri(SesionVideo.urlVideo(id)), desdeMs)
        reproductor.prepare()
        reproductor.playWhenReady = true

        // Jellyfin recomienda informar del progreso cada 10 segundos
        viewModelScope.launch {
            while (true) {
                delay(10.seconds)
                if (inicioInformado && reproductor.isPlaying) {
                    InformeVideo.progreso(id, sesionId, reproductor.currentPosition, enPausa = false)
                }
            }
        }
    }

    fun reintentar() {
        error = null
        reproductor.prepare()
        reproductor.play()
    }

    private fun mensaje(e: PlaybackException): String {
        val causa = e.cause
        if (causa is HttpDataSource.InvalidResponseCodeException) {
            if (causa.responseCode == 401) {
                SesionVideo.sesionCaducada.value = true
                return "La sesión de video caducó. Vuelve atrás y entra de nuevo."
            }
            return "El servidor respondió con error ${causa.responseCode}."
        }
        return when (e.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "No se pudo conectar con el servidor. ¿Sigue activado Tailscale?"

            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
                "Tu celular no puede reproducir el formato de este video."

            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ->
                "El archivo está dañado o su formato no es compatible."

            else -> "No se pudo reproducir (${e.errorCodeName})."
        }
    }

    override fun onCleared() {
        // La posición se lee antes de liberar el reproductor
        val posicion = reproductor.currentPosition
        reproductor.release()
        if (inicioInformado) InformeVideo.fin(id, sesionId, posicion)
    }
}

private fun Context.buscarActividad(): ComponentActivity {
    var contexto = this
    while (contexto is ContextWrapper) {
        if (contexto is ComponentActivity) return contexto
        contexto = contexto.baseContext
    }
    error("No se encontró la actividad")
}

@OptIn(UnstableApi::class)
@Composable
fun ReproductorVideoScreen(
    alVolver: () -> Unit,
    vm: ReproductorVideoViewModel = viewModel()
) {
    val contexto = LocalContext.current
    val actividad = remember(contexto) { contexto.buscarActividad() }

    // Pantalla completa mientras se ve el video. La orientación sigue al celular
    // (y respeta el bloqueo de rotación de Android); al salir, todo vuelve a la normalidad
    DisposableEffect(actividad) {
        val ventana = actividad.window
        val controlador = WindowCompat.getInsetsController(ventana, ventana.decorView)
        actividad.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        controlador.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controlador.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controlador.show(WindowInsetsCompat.Type.systemBars())
            actividad.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Si sales de la app, el video se pausa
    DisposableEffect(actividad) {
        val observador = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_STOP) vm.reproductor.pause()
        }
        actividad.lifecycle.addObserver(observador)
        onDispose { actividad.lifecycle.removeObserver(observador) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = vm.reproductor
                    useController = true
                    setShowSubtitleButton(true)
                }
            },
            update = { it.player = vm.reproductor },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize()
        )

        val mensajeError = vm.error
        if (mensajeError != null) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(mensajeError, color = Color.White, textAlign = TextAlign.Center)
                Row(
                    Modifier.padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(onClick = { vm.reintentar() }) { Text("Reintentar") }
                    OutlinedButton(onClick = alVolver) { Text("Volver") }
                }
            }
        }
    }
}