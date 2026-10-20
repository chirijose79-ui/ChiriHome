package com.chirihome.music.ui.playlist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chirihome.music.data.PlaylistDetalle
import com.chirihome.music.data.Playlists
import com.chirihome.music.data.Sesion
import kotlinx.coroutines.launch

sealed interface EstadoPlaylist {
    data object Cargando : EstadoPlaylist
    data class Listo(val playlist: PlaylistDetalle) : EstadoPlaylist
    data class Fallo(val mensaje: String) : EstadoPlaylist
}

class PlaylistViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val id: String = checkNotNull(savedStateHandle["id"])

    var estado by mutableStateOf<EstadoPlaylist>(EstadoPlaylist.Cargando)
        private set

    init { cargar() }

    fun cargar() {
        viewModelScope.launch {
            estado = try {
                val api = Sesion.api ?: error("No hay sesión iniciada")
                val r = api.playlist(Sesion.parametros() + ("id" to id)).respuesta
                val playlist = r.playlist
                if (r.status == "ok" && playlist != null) {
                    EstadoPlaylist.Listo(playlist)
                } else {
                    EstadoPlaylist.Fallo(r.error?.message ?: "Playlist no encontrada")
                }
            } catch (e: Exception) {
                EstadoPlaylist.Fallo("No se pudo cargar: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun quitar(indice: Int) {
        Playlists.quitar(id, indice) { cargar() }
    }
}