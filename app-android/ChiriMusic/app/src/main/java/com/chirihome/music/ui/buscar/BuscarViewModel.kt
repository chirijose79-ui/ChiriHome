package com.chirihome.music.ui.buscar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chirihome.music.data.Album
import com.chirihome.music.data.Cancion
import com.chirihome.music.data.Sesion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface EstadoBusqueda {
    data object Inactivo : EstadoBusqueda
    data object Cargando : EstadoBusqueda
    data class Listo(val albumes: List<Album>, val canciones: List<Cancion>) : EstadoBusqueda
    data class Fallo(val mensaje: String) : EstadoBusqueda
}

class BuscarViewModel : ViewModel() {
    var consulta by mutableStateOf("")
        private set
    var estado by mutableStateOf<EstadoBusqueda>(EstadoBusqueda.Inactivo)
        private set

    private var trabajo: Job? = null

    fun cambiarConsulta(texto: String) {
        consulta = texto
        trabajo?.cancel()
        if (texto.trim().length < 2) {
            estado = EstadoBusqueda.Inactivo
            return
        }
        trabajo = viewModelScope.launch {
            delay(350)
            estado = EstadoBusqueda.Cargando
            estado = try {
                val api = Sesion.api ?: error("No hay sesión iniciada")
                val params = Sesion.parametros() + mapOf(
                    "query" to texto.trim(),
                    "artistCount" to "0",
                    "albumCount" to "20",
                    "songCount" to "50"
                )
                val r = api.buscar(params).respuesta
                if (r.status == "ok") {
                    EstadoBusqueda.Listo(
                        r.searchResult3?.album.orEmpty(),
                        r.searchResult3?.song.orEmpty()
                    )
                } else {
                    EstadoBusqueda.Fallo(r.error?.message ?: "Error desconocido")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                EstadoBusqueda.Fallo("No se pudo buscar: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }
}