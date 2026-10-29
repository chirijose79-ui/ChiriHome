package com.chirihome.music.ui.video

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.chirihome.music.data.video.JfItem
import com.chirihome.music.data.video.SesionVideo
import com.chirihome.music.ui.reproductor.MiniReproductor
import kotlinx.coroutines.launch

sealed interface EstadoVideo {
    data object Cargando : EstadoVideo
    data class Fallo(val mensaje: String) : EstadoVideo
    data class Listo(val peliculas: List<JfItem>) : EstadoVideo
}

class VideoViewModel : ViewModel() {
    var estado by mutableStateOf<EstadoVideo>(EstadoVideo.Cargando)
        private set

    init {
        cargar()
    }

    fun cargar() {
        estado = EstadoVideo.Cargando
        viewModelScope.launch {
            estado = try {
                val api = SesionVideo.api ?: error("No hay sesión de video iniciada")
                val r = api.peliculas(SesionVideo.usuarioId)
                EstadoVideo.Listo(r.items)
            } catch (e: Exception) {
                EstadoVideo.Fallo("No se pudo cargar: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }
}

@Composable
fun VideoScreen(
    alAbrirPelicula: (String) -> Unit,
    alCerrarSesion: () -> Unit,
    modifier: Modifier = Modifier,
    vm: VideoViewModel = viewModel()
) {
    var confirmarSalir by remember { mutableStateOf(false) }

    // Si Jellyfin rechaza el token, se vuelve al login de video
    val caducada = SesionVideo.sesionCaducada.value
    LaunchedEffect(caducada) { if (caducada) alCerrarSesion() }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Video", style = MaterialTheme.typography.titleLarge)
                val e = vm.estado
                if (e is EstadoVideo.Listo) {
                    Text(
                        "${e.peliculas.size} películas",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            IconButton(onClick = { confirmarSalir = true }) {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Cerrar sesión de video"
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val e = vm.estado) {
                EstadoVideo.Cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                is EstadoVideo.Fallo -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(e.mensaje, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.cargar() }) { Text("Reintentar") }
                }

                is EstadoVideo.Listo ->
                    if (e.peliculas.isEmpty()) {
                        Box(
                            Modifier.fillMaxSize().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Este usuario no tiene películas disponibles.")
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(140.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(e.peliculas, key = { it.id }) { pelicula ->
                                TarjetaPelicula(pelicula) { alAbrirPelicula(pelicula.id) }
                            }
                        }
                    }
            }
        }

        MiniReproductor()
    }

    if (confirmarSalir) {
        AlertDialog(
            onDismissRequest = { confirmarSalir = false },
            title = { Text("Cerrar sesión de video") },
            text = { Text("Tendrás que volver a entrar con tu usuario de Jellyfin.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmarSalir = false
                    alCerrarSesion()
                }) { Text("Cerrar sesión") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarSalir = false }) { Text("Cancelar") }
            }
        )
    }
}

// Carátula de Jellyfin: la imagen se pide con la cabecera de autenticación
@Composable
internal fun CaratulaJellyfin(
    idItem: String,
    etiqueta: String?,
    descripcion: String?,
    modifier: Modifier = Modifier
) {
    val contexto = LocalContext.current
    if (etiqueta != null) {
        val peticion = remember(idItem, etiqueta) {
            ImageRequest.Builder(contexto)
                .data(SesionVideo.urlCaratula(idItem, etiqueta))
                .httpHeaders(
                    NetworkHeaders.Builder()
                        .set("Authorization", SesionVideo.cabeceraAuth())
                        .build()
                )
                .build()
        }
        AsyncImage(
            model = peticion,
            contentDescription = descripcion,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                descripcion ?: "Sin título",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
private fun TarjetaPelicula(pelicula: JfItem, alTocar: () -> Unit) {
    Column(Modifier.clickable { alTocar() }) {
        Card {
            CaratulaJellyfin(
                idItem = pelicula.id,
                etiqueta = pelicula.etiquetasImagen?.get("Primary"),
                descripcion = pelicula.nombre,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            pelicula.nombre ?: "Sin título",
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        pelicula.anio?.let {
            Text(it.toString(), style = MaterialTheme.typography.bodySmall)
        }
    }
}