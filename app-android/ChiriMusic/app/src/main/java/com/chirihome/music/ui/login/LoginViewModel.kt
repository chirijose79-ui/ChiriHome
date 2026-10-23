package com.chirihome.music.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chirihome.music.data.LoginGatewayPeticion
import com.chirihome.music.data.Sesion
import com.chirihome.music.data.SubsonicFabrica
import kotlinx.coroutines.launch
import retrofit2.HttpException

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
                val urlBase = SubsonicFabrica.normalizarUrl(url)
                val api = SubsonicFabrica.crearApi(urlBase)

                // 1) Login en el gateway: devuelve el token
                val login = api.loginGateway(LoginGatewayPeticion(usuario.trim(), clave))
                Sesion.iniciarConToken(urlBase, login.usuario, login.token)

                // 2) Comprobación: Navidrome debe aceptar al usuario vinculado
                val r = api.ping(Sesion.parametros()).respuesta
                if (r.status == "ok") {
                    EstadoLogin.Exito("${r.type ?: "Subsonic"} ${r.version ?: ""}".trim())
                } else {
                    Sesion.cerrar()
                    EstadoLogin.Fallo(r.error?.message ?: "Error desconocido")
                }
            } catch (e: HttpException) {
                Sesion.cerrar()
                EstadoLogin.Fallo(mensajeHttp(e.code()))
            } catch (e: Exception) {
                Sesion.cerrar()
                EstadoLogin.Fallo("No se pudo conectar: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun mensajeHttp(codigo: Int): String = when (codigo) {
        401 -> "Usuario o contraseña incorrectos"
        403 -> "Este usuario no tiene acceso a la música"
        404 -> "Esa dirección no es un servidor ChiriHome (revisa la URL)"
        429 -> "Demasiados intentos. Espera unos minutos"
        else -> "El servidor respondió con error $codigo"
    }
}