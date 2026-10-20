package com.chirihome.music.data

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object Favoritos {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val ids = mutableStateOf<Set<String>>(emptySet())
    val canciones = mutableStateOf<List<Cancion>>(emptyList())

    fun cargar() {
        scope.launch { recargar() }
    }

    // Trae la lista real desde Navidrome
    private suspend fun recargar() {
        val api = Sesion.api ?: return
        try {
            val r = api.favoritos(Sesion.parametros()).respuesta
            if (r.status == "ok") {
                val lista = r.starred2?.song.orEmpty()
                canciones.value = lista
                ids.value = lista.map { it.id }.toSet()
            }
        } catch (e: Exception) {
            // Sin conexión: se deja lo que había
        }
    }

    fun alternar(cancion: Cancion) {
        val api = Sesion.api ?: return
        val eraFavorita = cancion.id in ids.value
        // El corazón cambia al instante; luego se confirma con el servidor
        ids.value = if (eraFavorita) ids.value - cancion.id else ids.value + cancion.id
        if (eraFavorita) canciones.value = canciones.value.filter { it.id != cancion.id }
        scope.launch {
            try {
                val p = Sesion.parametros() + ("id" to cancion.id)
                if (eraFavorita) api.quitarFavorito(p) else api.agregarFavorito(p)
            } catch (e: Exception) {
                // Si falla, la recarga siguiente devuelve el estado real
            }
            recargar()
        }
    }

    fun limpiar() {
        ids.value = emptySet()
        canciones.value = emptyList()
    }
}