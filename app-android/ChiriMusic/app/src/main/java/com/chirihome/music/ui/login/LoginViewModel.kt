package com.chirihome.music.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chirihome.music.data.SubsonicAuth
import com.chirihome.music.data.SubsonicFabrica
import kotlinx.coroutines.launch

sealed interface EstadoLogin {
    data object Inactivo : EstadoLogin
    data object Cargando : EstadoLogin
    data class Exito(val servidor: String) : EstadoLogin
    data class Fallo(val mensaje: String) : EstadoLogin
}

class LoginViewModel : ViewModel() {
    var estado by mutableStateOf<EstadoLogin>(EstadoLogin.Inactivo)
        private set

    fun conectar(url: String, usuario: String, clave: String) {
        if (usuario.isBlank() || clave.isBlank()) {
            estado = EstadoLogin.Fallo("Escribe usuario y contraseña")
            return
        }
        estado = EstadoLogin.Cargando
        viewModelScope.launch {
            estado = try {
                val api = SubsonicFabrica.crearApi(SubsonicFabrica.normalizarUrl(url))
                val r = api.ping(SubsonicAuth.parametros(usuario.trim(), clave)).respuesta
                if (r.status == "ok") {
                    EstadoLogin.Exito("${r.type ?: "Subsonic"} ${r.version ?: ""}".trim())
                } else {
                    EstadoLogin.Fallo(r.error?.message ?: "Error desconocido")
                }
            } catch (e: Exception) {
                EstadoLogin.Fallo("No se pudo conectar: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }
}