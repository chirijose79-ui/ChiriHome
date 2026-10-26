package com.chirihome.music.player

import androidx.core.net.toUri
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.edit
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.chirihome.music.data.Cancion
import com.chirihome.music.data.Sesion

@OptIn(UnstableApi::class)
object Reproductor {
    private var controlador: MediaController? = null
    private var conectando = false
    private var prefs: SharedPreferences? = null

    val actual = mutableStateOf<Cancion?>(null)
    val sonando = mutableStateOf(false)
    val cola = mutableStateOf<List<Cancion>>(emptyList())
    val indiceActual = mutableIntStateOf(-1)

    // Aleatorio: se aplica a las listas que se empiezan y mezcla lo que falta por sonar
    val aleatorio = mutableStateOf(false)

    // Player.REPEAT_MODE_OFF, REPEAT_MODE_ALL o REPEAT_MODE_ONE
    val repetir = mutableIntStateOf(Player.REPEAT_MODE_OFF)

    // El servicio lo usa para actualizar los botones de la pantalla de bloqueo
    var alCambiarAjustes: () -> Unit = {}

    // Carga lo guardado una sola vez. Lo llaman la app y el servicio de reproducción
    fun cargarAjustes(contexto: Context) {
        if (prefs != null) return
        val p = contexto.applicationContext.getSharedPreferences("reproductor", Context.MODE_PRIVATE)
        prefs = p
        aleatorio.value = p.getBoolean("aleatorio", false)
        repetir.intValue = p.getInt("repetir", Player.REPEAT_MODE_OFF)
    }

    private fun fijarAleatorio(valor: Boolean) {
        aleatorio.value = valor
        prefs?.edit {
            putBoolean("aleatorio", valor)
        }
        alCambiarAjustes()
    }

    // Se llama cada vez que el modo de repetición cambia, venga de donde venga
    fun registrarRepetir(modo: Int) {
        repetir.intValue = modo
        prefs?.edit {
            putInt("repetir", modo)
        }
        alCambiarAjustes()
    }

    fun iniciar(contexto: Context) {
        val app = contexto.applicationContext
        cargarAjustes(app)
        if (controlador != null || conectando) return
        conectando = true
        val token = SessionToken(app, ComponentName(app, ServicioReproduccion::class.java))
        val futuro = MediaController.Builder(app, token).buildAsync()
        futuro.addListener({
            conectando = false
            try {
                val c = futuro.get()
                controlador = c
                c.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        sonando.value = isPlaying
                    }

                    override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                        refrescarCola()
                    }

                    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                        refrescarCola()
                    }

                    override fun onRepeatModeChanged(repeatMode: Int) {
                        registrarRepetir(repeatMode)
                    }
                })
                // El servicio puede haberse reiniciado: se vuelve a aplicar lo guardado
                c.repeatMode = repetir.intValue
                sonando.value = c.isPlaying
                refrescarCola()
            } catch (e: Exception) {
                Log.e("Reproductor", "No se pudo conectar con el servicio", e)
            }
        }, ContextCompat.getMainExecutor(app))
    }

    fun reproducir(canciones: List<Cancion>, indice: Int) {
        val c = controlador ?: return
        if (canciones.isEmpty()) return
        if (aleatorio.value) {
            // La canción elegida suena primero y el resto va mezclado
            val posicion = indice.coerceIn(canciones.indices)
            val resto = (canciones.take(posicion) + canciones.drop(posicion + 1)).shuffled()
            c.setMediaItems((listOf(canciones[posicion]) + resto).map { aItem(it) }, 0, 0L)
        } else {
            c.setMediaItems(canciones.map { aItem(it) }, indice, 0L)
        }
        c.prepare()
        c.play()
    }

    // Mezcla toda la lista y empieza, sin importar el interruptor de aleatorio
    fun reproducirAleatorio(canciones: List<Cancion>) {
        val c = controlador ?: return
        if (canciones.isEmpty()) return
        c.setMediaItems(canciones.shuffled().map { aItem(it) }, 0, 0L)
        c.prepare()
        c.play()
    }

    // Desde la app
    fun alternarAleatorio() {
        val nuevo = !aleatorio.value
        fijarAleatorio(nuevo)
        if (nuevo) mezclarPendientes()
    }

    // Desde el botón de la pantalla de bloqueo: se trabaja directamente sobre el reproductor del servicio
    fun alternarAleatorioEnSesion(reproductor: Player) {
        val nuevo = !aleatorio.value
        fijarAleatorio(nuevo)
        if (nuevo) mezclarPendientesEn(reproductor)
    }

    fun alternarRepetir() {
        val c = controlador ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    // Mezcla las canciones que faltan por sonar; la actual no se toca
    private fun mezclarPendientes() {
        val c = controlador ?: return
        val actualIndice = c.currentMediaItemIndex
        val lista = cola.value
        if (actualIndice < 0 || lista.size - actualIndice <= 2) return
        val pendientes = lista.drop(actualIndice + 1).shuffled()
        c.removeMediaItems(actualIndice + 1, lista.size)
        c.addMediaItems(pendientes.map { aItem(it) })
    }

    // Lo mismo, pero sobre el reproductor del servicio, que conserva las direcciones de cada canción
    private fun mezclarPendientesEn(reproductor: Player) {
        val actualIndice = reproductor.currentMediaItemIndex
        val total = reproductor.mediaItemCount
        if (actualIndice < 0 || total - actualIndice <= 2) return
        val pendientes = (actualIndice + 1 until total)
            .map { reproductor.getMediaItemAt(it) }
            .shuffled()
        reproductor.removeMediaItems(actualIndice + 1, total)
        reproductor.addMediaItems(pendientes)
    }

    fun agregarAlFinal(cancion: Cancion) {
        val c = controlador ?: return
        if (c.mediaItemCount == 0) {
            reproducir(listOf(cancion), 0)
        } else {
            c.addMediaItem(aItem(cancion))
        }
    }

    fun irA(indice: Int) {
        val c = controlador ?: return
        if (indice !in 0 until c.mediaItemCount) return
        c.seekTo(indice, 0L)
        c.play()
    }

    fun quitar(indice: Int) {
        val c = controlador ?: return
        if (indice in 0 until c.mediaItemCount) c.removeMediaItem(indice)
    }

    fun alternar() {
        val c = controlador ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun siguiente() { controlador?.seekToNextMediaItem() }

    fun anterior() { controlador?.seekToPreviousMediaItem() }

    // Posición actual dentro de la canción, para la barra de progreso y la letra
    fun posicionMs(): Long = controlador?.currentPosition ?: 0L

    // Duración de la canción actual en milisegundos; 0 si todavía no se conoce
    fun duracionMs(): Long {
        val d = controlador?.duration ?: return 0L
        return if (d > 0) d else 0L
    }

    // Salta a un momento de la canción actual
    fun irAPosicion(ms: Long) { controlador?.seekTo(ms) }

    fun detener() {
        controlador?.run {
            stop()
            clearMediaItems()
        }
        actual.value = null
        cola.value = emptyList()
        indiceActual.intValue = -1
    }

    private fun refrescarCola() {
        val c = controlador ?: return
        cola.value = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).aCancion() }
        indiceActual.intValue = if (c.mediaItemCount == 0) -1 else c.currentMediaItemIndex
        actual.value = c.currentMediaItem?.aCancion()
    }

    @OptIn(UnstableApi::class)
    private fun aItem(cancion: Cancion): MediaItem {
        val extras = Bundle().apply { putString("coverArt", cancion.coverArt) }
        return MediaItem.Builder()
            .setMediaId(cancion.id)
            .setUri(Sesion.urlStream(cancion.id))
            .setCustomCacheKey(cancion.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(cancion.title)
                    .setArtist(cancion.artist)
                    .setAlbumTitle(cancion.album)
                    .setArtworkUri(cancion.coverArt?.let { Sesion.urlCaratula(it, 512).toUri() })
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private fun MediaItem.aCancion() = Cancion(
        id = mediaId,
        title = mediaMetadata.title?.toString(),
        artist = mediaMetadata.artist?.toString(),
        album = mediaMetadata.albumTitle?.toString(),
        coverArt = mediaMetadata.extras?.getString("coverArt")
    )
}