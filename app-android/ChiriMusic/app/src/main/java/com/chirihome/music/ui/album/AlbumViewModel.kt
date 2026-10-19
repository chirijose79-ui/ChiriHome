package com.chirihome.music.ui.album

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chirihome.music.data.AlbumDetalle
import com.chirihome.music.data.Sesion
import kotlinx.coroutines.launch

sealed interface EstadoAlbum {
    data object Cargando : EstadoAlbum
    data class Listo(val album: AlbumDetalle) : EstadoAlbum
    data class Fallo(val mensaje: String) : EstadoAlbum
}

class AlbumViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val id: String = checkNotNull(savedStateHandle["id"])

    var estado by mutableStateOf<EstadoAlbum>(EstadoAlbum.Cargando)
        private set

    init { cargar() }

    fun cargar() {
        estado = EstadoAlbum.Cargando
        viewModelScope.launch {
            estado = try {
                val api = Sesion.api ?: error("No hay sesión iniciada")
                val r = api.album(Sesion.parametros() + ("id" to id)).respuesta
                val album = r.album
                if (r.status == "ok" && album != null) {
                    EstadoAlbum.Listo(album)
                } else {
                    EstadoAlbum.Fallo(r.error?.message ?: "Álbum no encontrado")
                }
            } catch (e: Exception) {
                EstadoAlbum.Fallo("No se pudo cargar: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }
}