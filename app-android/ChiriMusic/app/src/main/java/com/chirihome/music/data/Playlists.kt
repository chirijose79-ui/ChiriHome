package com.chirihome.music.data

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object Playlists {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val lista = mutableStateOf<List<Playlist>>(emptyList())

    fun cargar() {
        scope.launch { recargar() }
    }

    private suspend fun recargar() {
        val api = Sesion.api ?: return
        try {
            val r = api.playlists(Sesion.parametros()).respuesta
            if (r.status == "ok") lista.value = r.playlists?.playlist.orEmpty()
        } catch (e: Exception) {
            // Sin conexión: se deja lo que había
        }
    }

    // Ejecuta una acción, recarga la lista y avisa si salió bien
    private fun ejecutar(
        alTerminar: (Boolean) -> Unit,
        accion: suspend (SubsonicApi) -> SubsonicEnvelope
    ) {
        val api = Sesion.api
        if (api == null) {
            alTerminar(false)
            return
        }
        scope.launch {
            val ok = try {
                accion(api).respuesta.status == "ok"
            } catch (e: Exception) {
                false
            }
            recargar()
            alTerminar(ok)
        }
    }

    fun crear(nombre: String, cancion: Cancion? = null, alTerminar: (Boolean) -> Unit = {}) =
        ejecutar(alTerminar) { api ->
            val extra = if (cancion != null) mapOf("songId" to cancion.id) else emptyMap()
            api.crearPlaylist(Sesion.parametros() + mapOf("name" to nombre.trim()) + extra)
        }

    fun agregar(playlistId: String, cancion: Cancion, alTerminar: (Boolean) -> Unit = {}) =
        ejecutar(alTerminar) { api ->
            api.actualizarPlaylist(
                Sesion.parametros() + mapOf("playlistId" to playlistId, "songIdToAdd" to cancion.id)
            )
        }

    fun quitar(playlistId: String, indice: Int, alTerminar: (Boolean) -> Unit = {}) =
        ejecutar(alTerminar) { api ->
            api.actualizarPlaylist(
                Sesion.parametros() + mapOf(
                    "playlistId" to playlistId,
                    "songIndexToRemove" to indice.toString()
                )
            )
        }

    fun borrar(playlistId: String, alTerminar: (Boolean) -> Unit = {}) =
        ejecutar(alTerminar) { api ->
            api.borrarPlaylist(Sesion.parametros() + ("id" to playlistId))
        }

    fun limpiar() {
        lista.value = emptyList()
    }
}