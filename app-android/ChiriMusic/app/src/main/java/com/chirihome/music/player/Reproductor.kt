package com.chirihome.music.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.chirihome.music.data.Cancion
import com.chirihome.music.data.Sesion

object Reproductor {
    private var controlador: MediaController? = null
    private var conectando = false

    val actual = mutableStateOf<Cancion?>(null)
    val sonando = mutableStateOf(false)

    fun iniciar(contexto: Context) {
        if (controlador != null || conectando) return
        conectando = true
        val app = contexto.applicationContext
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
                        actual.value = item?.aCancion()
                    }
                })
                sonando.value = c.isPlaying
                actual.value = c.currentMediaItem?.aCancion()
            } catch (e: Exception) {
                Log.e("Reproductor", "No se pudo conectar con el servicio", e)
            }
        }, ContextCompat.getMainExecutor(app))
    }

    fun reproducir(canciones: List<Cancion>, indice: Int) {
        val c = controlador ?: return
        val items = canciones.map { cancion ->
            MediaItem.Builder()
                .setMediaId(cancion.id)
                .setUri(Sesion.urlStream(cancion.id))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(cancion.title)
                        .setArtist(cancion.artist)
                        .setAlbumTitle(cancion.album)
                        .setArtworkUri(cancion.coverArt?.let { Uri.parse(Sesion.urlCaratula(it, 512)) })
                        .build()
                )
                .build()
        }
        c.setMediaItems(items, indice, 0L)
        c.prepare()
        c.play()
    }

    fun alternar() {
        val c = controlador ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun siguiente() { controlador?.seekToNextMediaItem() }

    fun anterior() { controlador?.seekToPreviousMediaItem() }

    private fun MediaItem.aCancion() = Cancion(
        id = mediaId,
        title = mediaMetadata.title?.toString(),
        artist = mediaMetadata.artist?.toString(),
        album = mediaMetadata.albumTitle?.toString()
    )
}