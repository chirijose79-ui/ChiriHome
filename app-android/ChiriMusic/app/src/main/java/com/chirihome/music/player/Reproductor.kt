package com.chirihome.music.player

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.chirihome.music.data.Cancion
import com.chirihome.music.data.Sesion

object Reproductor {
    private var player: ExoPlayer? = null

    val actual = mutableStateOf<Cancion?>(null)
    val sonando = mutableStateOf(false)

    fun iniciar(contexto: Context) {
        if (player != null) return
        player = ExoPlayer.Builder(contexto.applicationContext).build().also { p ->
            p.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    sonando.value = isPlaying
                }

                override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                    actual.value = item?.localConfiguration?.tag as? Cancion
                }
            })
        }
    }

    fun reproducir(canciones: List<Cancion>, indice: Int) {
        val p = player ?: return
        val items = canciones.map { c ->
            MediaItem.Builder()
                .setMediaId(c.id)
                .setUri(Sesion.urlStream(c.id))
                .setTag(c)
                .build()
        }
        p.setMediaItems(items, indice, 0L)
        p.prepare()
        p.play()
    }

    fun alternar() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun siguiente() { player?.seekToNextMediaItem() }

    fun anterior() { player?.seekToPreviousMediaItem() }
}