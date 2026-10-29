package com.chirihome.music.ui.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chirihome.music.data.video.JfCredenciales
import com.chirihome.music.data.video.JellyfinFabrica
import com.chirihome.music.data.video.SesionVideo
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed interface EstadoLoginVideo {
    data object Inactivo : EstadoLoginVideo
    data object Cargando : EstadoLoginVideo
    data object Exito : EstadoLoginVideo
    data class Fallo(val mensaje: String) : EstadoLoginVideo
}

class VideoLoginViewModel : ViewModel() {
    var estado by mutableStateOf<EstadoLoginVideo>(EstadoLoginVideo.Inactivo)
        private set

    fun conectar(url: String, usuario: String, clave: String) {
        if (url.isBlank() || usuario.isBlank() || clave.isBlank()) {
            estado = EstadoLoginVideo.Fallo("Escribe servidor, usuario y contraseña")
            return
        }
        estado = EstadoLoginVideo.Cargando
        viewModelScope.launch {
            estado = try {
                val urlBase = JellyfinFabrica.normalizarUrl(url)
                val api = JellyfinFabrica.crearApi(urlBase)
                val r = api.entrar(JfCredenciales(usuario.trim(), clave))
                SesionVideo.iniciar(
                    urlBase,
                    r.token,
                    r.usuario.id,
                    r.usuario.nombre ?: usuario.trim()
                )
                EstadoLoginVideo.Exito
            } catch (e: HttpException) {
                EstadoLoginVideo.Fallo(mensajeHttp(e.code()))
            } catch (e: Exception) {
                EstadoLoginVideo.Fallo(
                    "No se pudo conectar. ¿Está activado Tailscale? " +
                            "(${e.message ?: e.javaClass.simpleName})"
                )
            }
        }
    }

    private fun mensajeHttp(codigo: Int): String = when (codigo) {
        401 -> "Usuario o contraseña incorrectos"
        403 -> "Este usuario no tiene acceso"
        404 -> "Esa dirección no es un servidor Jellyfin (revisa la dirección)"
        else -> "El servidor respondió con error $codigo"
    }
}

@Composable
fun VideoLoginScreen(
    alConectar: () -> Unit,
    modifier: Modifier = Modifier,
    vm: VideoLoginViewModel = viewModel()
) {
    var url by remember { mutableStateOf(SesionVideo.ultimaUrl()) }
    var usuario by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    val estadoActual = vm.estado
    LaunchedEffect(estadoActual) { if (estadoActual is EstadoLoginVideo.Exito) alConectar() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Video", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Entra con tu usuario de Jellyfin.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Servidor de video") },
            placeholder = { Text("https://nombre.tu-red.ts.net") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = usuario,
            onValueChange = { usuario = it },
            label = { Text("Usuario") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = clave,
            onValueChange = { clave = it },
            label = { Text("Contraseña") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { vm.conectar(url, usuario, clave) },
            enabled = vm.estado !is EstadoLoginVideo.Cargando,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (vm.estado is EstadoLoginVideo.Cargando) "Conectando..." else "Conectar")
        }
        Spacer(Modifier.height(16.dp))

        val e = vm.estado
        if (e is EstadoLoginVideo.Fallo) {
            Text(e.mensaje, color = MaterialTheme.colorScheme.error)
        }
    }
}