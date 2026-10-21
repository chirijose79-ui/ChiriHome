package com.chirihome.music.player

import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.chirihome.music.data.Cancion
import com.chirihome.music.data.DescargasLocal
import com.chirihome.music.data.Sesion
import com.chirihome.music.data.SubsonicApi
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class BibliotecaAuto(private val scope: CoroutineScope) : MediaLibrarySession.Callback {

    private companion object {
        const val RAIZ = "raiz"
        const val ALBUMES = "albumes"
        const val PLAYLISTS = "playlists"
        const val DESCARGAS = "descargas"
        const val PREFIJO_ALBUM = "album:"
        const val PREFIJO_PLAYLIST = "playlist:"
    }

    // Canciones vistas al navegar: sirven para completar los datos cuando el coche pide una por su id
    private val conocidas = HashMap<String, Cancion>()

    // Última lista de canciones abierta en el coche: al tocar una, se encola toda
    private var ultimaLista: List<Cancion> = emptyList()

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> =
        Futures.immediateFuture(LibraryResult.ofItem(carpeta(RAIZ, "ChiriMusic"), params))

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
        asincrono<LibraryResult<ImmutableList<MediaItem>>> {
            try {
                val hijos = hijos(parentId)
                if (hijos == null) {
                    LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                } else {
                    val desde = (page.toLong() * pageSize).coerceAtMost(hijos.size.toLong()).toInt()
                    LibraryResult.ofItemList(hijos.drop(desde).take(pageSize), params)
                }
            } catch (e: Exception) {
                LibraryResult.ofError(LibraryResult.RESULT_ERROR_IO)
            }
        }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val item = when (mediaId) {
            RAIZ -> carpeta(RAIZ, "ChiriMusic")
            ALBUMES -> carpeta(ALBUMES, "Álbumes")
            PLAYLISTS -> carpeta(PLAYLISTS, "Playlists")
            DESCARGAS -> carpeta(DESCARGAS, "Descargas")
            else -> conocidas[mediaId]?.let { aItem(it) }
        }
        val resultado: LibraryResult<MediaItem> =
            if (item != null) LibraryResult.ofItem(item, null)
            else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
        return Futures.immediateFuture(resultado)
    }

    // El coche manda solo el id de la canción: aquí se completa con su URL y sus datos
    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<MutableList<MediaItem>> =
        Futures.immediateFuture(mediaItems.map { resolver(it) }.toMutableList())

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        // Los items de la app ya traen URL; los del coche vienen solo con el id
        val unico = mediaItems.singleOrNull()
        val posicion = if (unico != null && unico.localConfiguration == null) {
            ultimaLista.indexOfFirst { it.id == unico.mediaId }
        } else {
            -1
        }
        val resultado = if (posicion >= 0) {
            MediaSession.MediaItemsWithStartPosition(
                ultimaLista.map { aItem(it) },
                posicion,
                startPositionMs
            )
        } else {
            MediaSession.MediaItemsWithStartPosition(
                mediaItems.map { resolver(it) },
                startIndex,
                startPositionMs
            )
        }
        return Futures.immediateFuture(resultado)
    }

    // ---- Catálogo ----

    private suspend fun hijos(padre: String): List<MediaItem>? = when {
        padre == RAIZ -> listOf(
            carpeta(ALBUMES, "Álbumes"),
            carpeta(PLAYLISTS, "Playlists"),
            carpeta(DESCARGAS, "Descargas")
        )
        padre == ALBUMES -> listarAlbumes()
        padre == PLAYLISTS -> listarPlaylists()
        padre == DESCARGAS -> canciones(descargadas())
        padre.startsWith(PREFIJO_ALBUM) ->
            canciones(cancionesDeAlbum(padre.removePrefix(PREFIJO_ALBUM)))
        padre.startsWith(PREFIJO_PLAYLIST) ->
            canciones(cancionesDePlaylist(padre.removePrefix(PREFIJO_PLAYLIST)))
        else -> null
    }

    private fun api(): SubsonicApi {
        if (Sesion.api == null) Sesion.restaurar()
        return Sesion.api ?: error("No hay sesión iniciada")
    }

    private suspend fun listarAlbumes(): List<MediaItem> {
        val r = api().albumes(
            Sesion.parametros() + mapOf("type" to "alphabeticalByName", "size" to "100")
        ).respuesta
        if (r.status != "ok") error("El servidor respondió con error")
        return r.albumList2?.album.orEmpty().map {
            carpeta(
                PREFIJO_ALBUM + it.id,
                it.name ?: "Sin título",
                it.artist,
                it.coverArt,
                MediaMetadata.MEDIA_TYPE_ALBUM
            )
        }
    }

    private suspend fun listarPlaylists(): List<MediaItem> {
        val r = api().playlists(Sesion.parametros()).respuesta
        if (r.status != "ok") error("El servidor respondió con error")
        return r.playlists?.playlist.orEmpty().map {
            carpeta(
                PREFIJO_PLAYLIST + it.id,
                it.name ?: "Playlist",
                it.songCount?.let { n -> "$n canciones" },
                null,
                MediaMetadata.MEDIA_TYPE_PLAYLIST
            )
        }
    }

    private suspend fun cancionesDeAlbum(id: String): List<Cancion> {
        val r = api().album(Sesion.parametros() + ("id" to id)).respuesta
        if (r.status != "ok") error("El servidor respondió con error")
        return r.album?.song.orEmpty()
    }

    private suspend fun cancionesDePlaylist(id: String): List<Cancion> {
        val r = api().playlist(Sesion.parametros() + ("id" to id)).respuesta
        if (r.status != "ok") error("El servidor respondió con error")
        return r.playlist?.entry.orEmpty()
    }

    private fun descargadas(): List<Cancion> =
        DescargasLocal.canciones.value.filter { Descargas.estadoDe(it.id) == EstadoDescarga.LISTA }

    private fun canciones(lista: List<Cancion>): List<MediaItem> {
        ultimaLista = lista
        lista.forEach { conocidas[it.id] = it }
        return lista.map { aItem(it) }
    }

    // ---- Items ----

    private fun carpeta(
        id: String,
        titulo: String,
        subtitulo: String? = null,
        caratula: String? = null,
        tipo: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
    ): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(titulo)
                    .setSubtitle(subtitulo)
                    .setArtworkUri(caratula?.let { Uri.parse(Sesion.urlCaratula(it, 300)) })
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(tipo)
                    .build()
            )
            .build()

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
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()
    }

    private fun resolver(item: MediaItem): MediaItem {
        if (item.localConfiguration != null) return item
        return aItem(conocidas[item.mediaId] ?: Cancion(id = item.mediaId))
    }

    private fun <T> asincrono(bloque: suspend () -> T): ListenableFuture<T> {
        val futuro = SettableFuture.create<T>()
        scope.launch {
            try {
                futuro.set(bloque())
            } catch (e: Exception) {
                futuro.setException(e)
            }
        }
        return futuro
    }
}