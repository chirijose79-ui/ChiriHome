package com.chirihome.music.ui.video

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.provider.Settings
import android.view.OrientationEventListener
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.chirihome.music.data.video.InformeVideo
import com.chirihome.music.data.video.JfItem
import com.chirihome.music.data.video.SesionVideo
import com.chirihome.music.player.ServicioVideo
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID

// La película se reproduce en el servicio de video; aquí solo hay un mando que la controla
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

    @Volatile
    private var liberado = false
    private var conexion: ListenableFuture<MediaController>? = null

    // Null mientras se conecta con el servicio de video
    var reproductor by mutableStateOf<Player?>(null)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    // La ficha de la película, para mostrarla debajo del video cuando está en vertical
    var pelicula by mutableStateOf<JfItem?>(null)
        private set

    private val escucha = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            this@ReproductorVideoViewModel.error = mensaje(error)
        }

        // La primera vez que de verdad suena, se avisa a Jellyfin
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying && !inicioInformado) {
                inicioInformado = true
                InformeVideo.inicio(id, sesionId, reproductor?.currentPosition ?: 0L)
            }
        }

        // Al pausar o reanudar se informa enseguida
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (inicioInformado) {
                InformeVideo.progreso(
                    id, sesionId, reproductor?.currentPosition ?: 0L, enPausa = !playWhenReady
                )
            }
        }
    }

    init {
        // Primero se piden los datos de la película (título y carátula) y luego se empieza
        viewModelScope.launch {
            val ficha = withTimeoutOrNull(4_000) { pedirFicha() }
            val caratula = withTimeoutOrNull(2_500) { pedirCaratula(ficha) }
            pelicula = ficha
            conectar(ficha, caratula)
        }

        // Jellyfin recomienda informar del progreso cada 10 segundos, también con la pantalla bloqueada
        viewModelScope.launch {
            while (true) {
                delay(10_000)
                val p = reproductor
                if (inicioInformado && p != null && p.isPlaying) {
                    InformeVideo.progreso(id, sesionId, p.currentPosition, enPausa = false)
                }
            }
        }
    }

    private suspend fun pedirFicha(): JfItem? {
        val api = SesionVideo.api ?: return null
        return try {
            api.detalle(SesionVideo.usuarioId, id).items.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    // La carátula viaja como bytes: el sistema la usa en la pantalla de bloqueo
    private suspend fun pedirCaratula(ficha: JfItem?): ByteArray? {
        val etiqueta = ficha?.etiquetasImagen?.get("Primary") ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val peticion = Request.Builder()
                    .url(SesionVideo.urlCaratula(ficha.id, etiqueta, 400))
                    .header("Authorization", SesionVideo.cabeceraAuth())
                    .build()
                OkHttpClient().newCall(peticion).execute().use { respuesta ->
                    if (respuesta.isSuccessful) respuesta.body?.bytes() else null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun conectar(ficha: JfItem?, caratula: ByteArray?) {
        if (liberado) return
        val app = getApplication<Application>()
        val token = SessionToken(app, ComponentName(app, ServicioVideo::class.java))
        val futuro = MediaController.Builder(app, token).buildAsync()
        conexion = futuro
        futuro.addListener({
            if (liberado) {
                MediaController.releaseFuture(futuro)
                return@addListener
            }
            try {
                val mando = futuro.get()
                mando.addListener(escucha)
                mando.setMediaItem(itemVideo(ficha, caratula), desdeMs)
                mando.prepare()
                mando.playWhenReady = true
                reproductor = mando
            } catch (e: Exception) {
                error = "No se pudo iniciar la reproducción de video."
            }
        }, ContextCompat.getMainExecutor(app))
    }

    // Lo que dice el reproductor de la pantalla de bloqueo y la notificación
    private fun itemVideo(ficha: JfItem?, caratula: ByteArray?): MediaItem {
        val metadatos = MediaMetadata.Builder()
            .setTitle(ficha?.nombre ?: "Película")
            .setArtist("Escuchando el audio del video")
            .setAlbumTitle("ChiriMusic · Video")
        if (caratula != null) {
            metadatos.setArtworkData(caratula, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(SesionVideo.urlVideo(id))
            .setMediaMetadata(metadatos.build())
            .build()
    }

    fun reintentar() {
        error = null
        reproductor?.prepare()
        reproductor?.play()
    }

    private fun mensaje(e: PlaybackException): String =
        when (e.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "No se pudo conectar con el servidor. ¿Sigue activado Tailscale?"

            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                "El servidor rechazó la petición. Si el problema sigue, vuelve atrás y entra de nuevo."

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

    override fun onCleared() {
        liberado = true
        val p = reproductor
        val posicion = p?.currentPosition ?: 0L
        // Al salir de la película, el audio se detiene y el servicio se apaga
        if (p != null) {
            p.stop()
            p.clearMediaItems()
        }
        conexion?.let { MediaController.releaseFuture(it) }
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

    // En horizontal: pantalla completa. En vertical: video arriba y la ficha de la película debajo
    val horizontal = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Orientación fijada con el botón de maximizar o minimizar (null = sigue al celular)
    val bloqueo = remember { mutableStateOf<Int?>(null) }
    val maximizar = remember(actividad) {
        {
            bloqueo.value = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            actividad.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
    val minimizar = remember(actividad) {
        {
            bloqueo.value = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            actividad.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    // Al salir del reproductor, las barras y la orientación vuelven a la normalidad
    DisposableEffect(actividad) {
        onDispose {
            val ventana = actividad.window
            WindowCompat.getInsetsController(ventana, ventana.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
            actividad.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Las barras del sistema se ocultan solo en pantalla completa
    LaunchedEffect(horizontal) {
        val ventana = actividad.window
        val controlador = WindowCompat.getInsetsController(ventana, ventana.decorView)
        if (horizontal) {
            controlador.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controlador.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controlador.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Cuando giras el celular a la posición que forzaste con el botón, vuelve a seguirlo con normalidad
    DisposableEffect(actividad) {
        val escucha = object : OrientationEventListener(actividad) {
            override fun onOrientationChanged(grados: Int) {
                if (grados == OrientationEventListener.ORIENTATION_UNKNOWN) return
                val forzada = bloqueo.value ?: return
                // Con la rotación automática desactivada no se suelta: volvería a la posición fija del sistema
                val rotacionAuto = Settings.System.getInt(
                    actividad.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0
                ) == 1
                if (!rotacionAuto) return
                val vertical = grados <= 30 || grados >= 330 || grados in 150..210
                val apaisado = grados in 60..120 || grados in 240..300
                val coincide =
                    (forzada == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT && vertical) ||
                            (forzada == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE && apaisado)
                if (coincide) {
                    bloqueo.value = null
                    actividad.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }
        if (escucha.canDetectOrientation()) escucha.enable()
        onDispose { escucha.disable() }
    }

    // Con la app fuera de vista (pantalla bloqueada o Inicio), el audio sigue pero la imagen se desactiva
    DisposableEffect(actividad) {
        val observador = LifecycleEventObserver { _, evento ->
            val p = vm.reproductor ?: return@LifecycleEventObserver
            when (evento) {
                Lifecycle.Event.ON_STOP ->
                    p.trackSelectionParameters = p.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                        .build()

                Lifecycle.Event.ON_START ->
                    p.trackSelectionParameters = p.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                        .build()

                else -> Unit
            }
        }
        actividad.lifecycle.addObserver(observador)
        onDispose { actividad.lifecycle.removeObserver(observador) }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        // El video está siempre en el mismo sitio del árbol, para que no se recree al cambiar de modo
        Box(
            modifier = if (horizontal) {
                Modifier.fillMaxSize()
            } else {
                Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = vm.reproductor
                        useController = true
                        setShowSubtitleButton(true)
                        // El botón de los controles maximiza o minimiza
                        setFullscreenButtonClickListener { quiereCompleta ->
                            if (quiereCompleta) maximizar() else minimizar()
                        }
                    }
                },
                update = {
                    it.player = vm.reproductor
                    // Así el icono del botón coincide con el modo actual
                    it.setFullscreenButtonState(horizontal)
                },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize()
            )

            val mensajeError = vm.error
            if (vm.reproductor == null && mensajeError == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }
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

        if (!horizontal) FichaPelicula(vm.pelicula)
    }
}

// Lo que se ve debajo del video cuando el celular está en vertical
@Composable
private fun ColumnScope.FichaPelicula(pelicula: JfItem?) {
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (pelicula == null) {
            Text("Reproduciendo…", style = MaterialTheme.typography.titleMedium)
        } else {
            Text(pelicula.nombre ?: "Sin título", style = MaterialTheme.typography.titleLarge)
            val datos = listOfNotNull(pelicula.anio?.toString(), duracionTexto(pelicula))
            if (datos.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(datos.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            }
            val descripcion = pelicula.descripcion
            if (!descripcion.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(descripcion, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}