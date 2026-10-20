package com.chirihome.music.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.chirihome.music.data.Cancion
import com.chirihome.music.data.Sesion

object Reproductor {
    private var controlador: MediaController? = null
    private var conectando = false

    val actual = mutableStateOf<Cancion?>(null)
    val sonando = mutableStateOf(false)
    val cola = mutableStateOf<List<Cancion>>(emptyList())
    val indiceActual = mutableStateOf(-1)

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
                        refrescarCola()
                    }

                    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                        refrescarCola()
                    }
                })
                sonando.value = c.isPlaying
                refrescarCola()
            } catch (e: Exception) {
                Log.e("Reproductor", "No se pudo conectar con el servicio", e)
            }
        }, ContextCompat.getMainExecutor(app))
    }

    fun reproducir(canciones: List<Cancion>, indice: Int) {
        val c = controlador ?: return
        c.setMediaItems(canciones.map { aItem(it) }, indice, 0L)
        c.prepare()
        c.play()
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

    fun detener() {
        controlador?.run {
            stop()
            clearMediaItems()
        }
        actual.value = null
        cola.value = emptyList()
        indiceActual.value = -1
    }

    private fun refrescarCola() {
        val c = controlador ?: return
        cola.value = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).aCancion() }
        indiceActual.value = if (c.mediaItemCount == 0) -1 else c.currentMediaItemIndex
        actual.value = c.currentMediaItem?.aCancion()
    }

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
                    .setArtworkUri(cancion.coverArt?.let { Uri.parse(Sesion.urlCaratula(it, 512)) })
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