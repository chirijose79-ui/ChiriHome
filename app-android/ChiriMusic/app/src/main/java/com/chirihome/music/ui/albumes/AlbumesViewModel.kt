package com.chirihome.music.ui.albumes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chirihome.music.data.Album
import com.chirihome.music.data.Sesion
import com.chirihome.music.data.Favoritos
import kotlinx.coroutines.launch

sealed interface EstadoAlbumes {
    data object Cargando : EstadoAlbumes
    data class Listo(val albumes: List<Album>) : EstadoAlbumes
    data class Fallo(val mensaje: String) : EstadoAlbumes
}

class AlbumesViewModel : ViewModel() {
    var estado by mutableStateOf<EstadoAlbumes>(EstadoAlbumes.Cargando)
        private set

    init {
        cargar()
        Favoritos.cargar()
    }

    fun cargar() {
        estado = EstadoAlbumes.Cargando
        viewModelScope.launch {
            estado = try {
                val api = Sesion.api ?: error("No hay sesión iniciada")
                val params = Sesion.parametros() + mapOf(
                    "type" to "alphabeticalByName",
                    "size" to "100"
                )
                val r = api.albumes(params).respuesta
                if (r.status == "ok") {
                    EstadoAlbumes.Listo(r.albumList2?.album.orEmpty())
                } else {
                    EstadoAlbumes.Fallo(r.error?.message ?: "Error desconocido")
                }
            } catch (e: Exception) {
                EstadoAlbumes.Fallo("No se pudo cargar: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }
}