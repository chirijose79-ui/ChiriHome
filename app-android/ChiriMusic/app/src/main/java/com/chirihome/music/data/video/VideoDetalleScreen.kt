package com.chirihome.music.ui.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chirihome.music.data.video.JfItem
import com.chirihome.music.data.video.SesionVideo
import com.chirihome.music.ui.reproductor.MiniReproductor
import kotlinx.coroutines.launch

sealed interface EstadoDetalle {
    data object Cargando : EstadoDetalle
    data class Fallo(val mensaje: String) : EstadoDetalle
    data class Listo(val pelicula: JfItem) : EstadoDetalle
}

class VideoDetalleViewModel(handle: SavedStateHandle) : ViewModel() {
    val id: String = checkNotNull(handle.get<String>("id"))

    var estado by mutableStateOf<EstadoDetalle>(EstadoDetalle.Cargando)
        private set

    // La primera vez muestra la rueda de carga; las siguientes recargan sin parpadeo
    fun cargar() {
        if (estado !is EstadoDetalle.Listo) estado = EstadoDetalle.Cargando
        viewModelScope.launch {
            try {
                val api = SesionVideo.api ?: error("No hay sesión de video iniciada")
                val pelicula = api.detalle(SesionVideo.usuarioId, id).items.firstOrNull()
                    ?: error("No se encontró la película")
                estado = EstadoDetalle.Listo(pelicula)
            } catch (e: Exception) {
                if (estado !is EstadoDetalle.Listo) {
                    estado = EstadoDetalle.Fallo(
                        "No se pudo cargar: ${e.message ?: e.javaClass.simpleName}"
                    )
                }
            }
        }
    }
}

@Composable
fun VideoDetalleScreen(
    // Recibe el id de la película y desde qué milisegundo empezar
    alReproducir: (String, Long) -> Unit,
    modifier: Modifier = Modifier,
    vm: VideoDetalleViewModel = viewModel()
) {
    // Se carga al entrar y cada vez que el servidor guarda una posición nueva (al volver del reproductor)
    LaunchedEffect(SesionVideo.versionProgreso.intValue) { vm.cargar() }

    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val e = vm.estado) {
                EstadoDetalle.Cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                is EstadoDetalle.Fallo -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(e.mensaje, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.cargar() }) { Text("Reintentar") }
                }

                is EstadoDetalle.Listo -> Detalle(e.pelicula, alReproducir)
            }
        }
        MiniReproductor()
    }
}

@Composable
private fun Detalle(pelicula: JfItem, alReproducir: (String, Long) -> Unit) {
    val continuarMs = posicionParaContinuar(pelicula)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row {
            Card(Modifier.width(140.dp)) {
                CaratulaJellyfin(
                    idItem = pelicula.id,
                    etiqueta = pelicula.etiquetasImagen?.get("Primary"),
                    descripcion = pelicula.nombre,
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(pelicula.nombre ?: "Sin título", style = MaterialTheme.typography.titleLarge)
                val datos = listOfNotNull(
                    pelicula.anio?.toString(),
                    duracionTexto(pelicula),
                    if (pelicula.datosUsuario?.vista == true) "Vista" else null
                )
                if (datos.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(datos.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                }
                infoTecnica(pelicula).forEach {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        if (continuarMs != null) {
            Button(
                onClick = { alReproducir(pelicula.id, continuarMs) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Continuar desde ${formatoTiempo(continuarMs)}")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { alReproducir(pelicula.id, 0L) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Empezar desde el principio")
            }
        } else {
            Button(
                onClick = { alReproducir(pelicula.id, 0L) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Reproducir")
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(pelicula.descripcion ?: "Sin descripción", style = MaterialTheme.typography.bodyMedium)
    }
}

// Solo se ofrece continuar si dejaste la película empezada (más de 30 segundos) y no la viste entera
private fun posicionParaContinuar(p: JfItem): Long? {
    val datos = p.datosUsuario ?: return null
    if (datos.vista == true) return null
    // 10.000 ticks = 1 milisegundo
    val ms = (datos.posicionTicks ?: 0L) / 10_000L
    return if (ms >= 30_000L) ms else null
}

private fun formatoTiempo(ms: Long): String {
    val total = ms / 1000
    val horas = total / 3600
    val minutos = (total % 3600) / 60
    val segundos = total % 60
    return if (horas > 0) {
        "%d:%02d:%02d".format(horas, minutos, segundos)
    } else {
        "%d:%02d".format(minutos, segundos)
    }
}

private fun duracionTexto(p: JfItem): String? =
    p.duracionTicks?.let { ticks ->
        // 600.000.000 ticks = 1 minuto
        val minutos = ticks / 600_000_000L
        if (minutos <= 0) null else "$minutos min"
    }

// Líneas con los formatos del archivo: ayudan a saber si el celular podrá reproducirlo
private fun infoTecnica(p: JfItem): List<String> {
    val fuente = p.fuentes?.firstOrNull() ?: return emptyList()
    val pistas = fuente.pistas.orEmpty()
    val video = pistas.firstOrNull { it.tipo == "Video" }
    val audios = pistas.filter { it.tipo == "Audio" }
    val subtitulos = pistas.count { it.tipo == "Subtitle" }

    val lineas = mutableListOf<String>()
    val formato = listOfNotNull(
        fuente.contenedor?.uppercase(),
        video?.codec?.uppercase(),
        video?.alto?.let { "${it}p" }
    )
    if (formato.isNotEmpty()) lineas.add(formato.joinToString(" · "))

    val codecsAudio = audios.mapNotNull { it.codec?.uppercase() }.distinct()
    if (audios.isNotEmpty()) {
        val cuantos = if (audios.size == 1) "1 audio" else "${audios.size} audios"
        lineas.add(
            if (codecsAudio.isNotEmpty()) "$cuantos (${codecsAudio.joinToString(", ")})" else cuantos
        )
    }
    lineas.add(
        when (subtitulos) {
            0 -> "Sin subtítulos"
            1 -> "1 subtítulo"
            else -> "$subtitulos subtítulos"
        }
    )
    return lineas
}