package com.chirihome.music.ui.reproductor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.chirihome.music.data.Sesion
import com.chirihome.music.player.Reproductor
import com.chirihome.music.ui.Navegacion
import com.chirihome.music.ui.componentes.BotonFavorito
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

// estado: null = botón normal, true = activado (de color), false = apagado (atenuado)
@Composable
private fun BotonIcono(
    icono: ImageVector,
    descripcion: String,
    alPulsar: () -> Unit,
    estado: Boolean? = null,
    tamano: Dp = 48.dp,
    tamanoIcono: Dp = 24.dp
) {
    val color = when (estado) {
        true -> MaterialTheme.colorScheme.primary
        false -> LocalContentColor.current.copy(alpha = 0.5f)
        null -> LocalContentColor.current
    }
    IconButton(onClick = alPulsar, modifier = Modifier.size(tamano)) {
        Icon(
            imageVector = icono,
            contentDescription = descripcion,
            modifier = Modifier.size(tamanoIcono),
            tint = color
        )
    }
}

private fun formatoTiempo(ms: Long): String {
    val segundos = (ms / 1000).coerceAtLeast(0L)
    val minutos = segundos / 60
    val segundosRestantes = segundos % 60
    return "%d:%02d".format(minutos, segundosRestantes)
}

// Línea de progreso: se puede tocar o arrastrar para saltar a otro momento
@Composable
private fun BarraProgreso(duracionNavidrome: Int?, idCancion: String) {
    var posicion by remember { mutableLongStateOf(0L) }
    var duracion by remember { mutableLongStateOf(0L) }
    var arrastrando by remember { mutableStateOf(false) }
    var valorArrastre by remember { mutableFloatStateOf(0f) }

    // Se consulta cuatro veces por segundo; al cambiar de canción se reinicia
    LaunchedEffect(idCancion) {
        while (true) {
            if (!arrastrando) posicion = Reproductor.posicionMs()
            val real = Reproductor.duracionMs()
            duracion = if (real > 0) real else (duracionNavidrome ?: 0) * 1000L
            delay(250.milliseconds)
        }
    }

    val maximo = duracion.coerceAtLeast(1L).toFloat()
    val valor = (if (arrastrando) valorArrastre else posicion.toFloat()).coerceIn(0f, maximo)

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            formatoTiempo(valor.toLong()),
            style = MaterialTheme.typography.bodySmall
        )
        Slider(
            value = valor,
            onValueChange = {
                arrastrando = true
                valorArrastre = it
            },
            onValueChangeFinished = {
                val destino = valorArrastre.toLong()
                Reproductor.irAPosicion(destino)
                posicion = destino
                arrastrando = false
            },
            valueRange = 0f..maximo,
            enabled = duracion > 0,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        Text(
            formatoTiempo(duracion),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun MiniReproductor(modifier: Modifier = Modifier, mostrarCola: Boolean = true) {
    val cancion = Reproductor.actual.value ?: return
    val aleatorio = Reproductor.aleatorio.value
    val modoRepetir = Reproductor.repetir.intValue
    val sonando = Reproductor.sonando.value

    Box(modifier.fillMaxWidth()) {
        // Fondo: la carátula en tamaño diminuto, ampliada y difuminada
        cancion.coverArt?.let { caratula ->
            AsyncImage(
                model = Sesion.urlCaratula(caratula, 64),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().blur(16.dp)
            )
        }
        // Velo del color del tema para que los textos y los iconos se lean bien
        Box(
            Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
        )

        Column(Modifier.fillMaxWidth()) {
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        cancion.title ?: "Sin título",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        cancion.artist ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BotonFavorito(cancion)
            }
            BarraProgreso(duracionNavidrome = cancion.duration, idCancion = cancion.id)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BotonIcono(
                    icono = Icons.Filled.Shuffle,
                    descripcion = if (aleatorio) "Aleatorio activado" else "Aleatorio desactivado",
                    alPulsar = { Reproductor.alternarAleatorio() },
                    estado = aleatorio
                )
                BotonIcono(Icons.Filled.SkipPrevious, "Anterior", { Reproductor.anterior() })
                BotonIcono(
                    icono = if (sonando) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    descripcion = if (sonando) "Pausa" else "Reproducir",
                    alPulsar = { Reproductor.alternar() },
                    tamano = 56.dp,
                    tamanoIcono = 36.dp
                )
                BotonIcono(Icons.Filled.SkipNext, "Siguiente", { Reproductor.siguiente() })
                BotonIcono(
                    icono = if (modoRepetir == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    descripcion = when (modoRepetir) {
                        Player.REPEAT_MODE_ALL -> "Repetir lista"
                        Player.REPEAT_MODE_ONE -> "Repetir una canción"
                        else -> "Repetir desactivado"
                    },
                    alPulsar = { Reproductor.alternarRepetir() },
                    estado = modoRepetir != Player.REPEAT_MODE_OFF
                )
            }
            if (mostrarCola) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BotonIcono(Icons.AutoMirrored.Filled.QueueMusic, "Cola", { Navegacion.abrirCola() })
                    BotonIcono(Icons.Filled.Lyrics, "Letra", { Navegacion.abrirLetra() })
                    BotonIcono(Icons.Filled.Equalizer, "Ecualizador", { Navegacion.abrirEcualizador() })
                }
            }
        }
    }
}