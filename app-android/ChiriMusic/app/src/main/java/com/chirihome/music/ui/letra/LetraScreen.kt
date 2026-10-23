package com.chirihome.music.ui.letra

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chirihome.music.data.LineaLetra
import com.chirihome.music.data.Sesion
import com.chirihome.music.player.Reproductor
import com.chirihome.music.ui.reproductor.MiniReproductor
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException

private sealed interface EstadoLetra {
    data object Cargando : EstadoLetra
    data object SinLetra : EstadoLetra
    data class Fallo(val mensaje: String) : EstadoLetra
    data class Listo(val lineas: List<LineaLetra>, val sincronizada: Boolean) : EstadoLetra
}

@Composable
fun LetraScreen() {
    val cancion = Reproductor.actual.value
    var estado by remember { mutableStateOf<EstadoLetra>(EstadoLetra.Cargando) }
    var intento by remember { mutableIntStateOf(0) }

    // Se vuelve a pedir la letra cuando cambia la canción o se pulsa Reintentar
    LaunchedEffect(cancion?.id, intento) {
        val id = cancion?.id
        if (id == null) {
            estado = EstadoLetra.SinLetra
            return@LaunchedEffect
        }
        estado = EstadoLetra.Cargando
        estado = cargarLetra(id)
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(cancion?.title ?: "Sin canción", style = MaterialTheme.typography.titleLarge)
                Text(cancion?.artist ?: "", style = MaterialTheme.typography.bodyMedium)
            }

            when (val e = estado) {
                EstadoLetra.Cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                EstadoLetra.SinLetra -> Text(
                    "Esta canción no tiene letra en el servidor.",
                    Modifier.padding(horizontal = 16.dp)
                )

                is EstadoLetra.Fallo -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Top
                ) {
                    Text(e.mensaje, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { intento++ }) { Text("Reintentar") }
                }

                is EstadoLetra.Listo -> ListaLetra(e.lineas, e.sincronizada)
            }
        }
        MiniReproductor(mostrarCola = false)
    }
}

@Composable
private fun ListaLetra(lineas: List<LineaLetra>, sincronizada: Boolean) {
    val lista = rememberLazyListState()
    var posicion by remember { mutableLongStateOf(0L) }

    // Solo se consulta la posición si la letra está sincronizada
    if (sincronizada) {
        LaunchedEffect(Unit) {
            while (true) {
                posicion = Reproductor.posicionMs()
                delay(250)
            }
        }
    }

    val indiceActual = if (sincronizada) {
        lineas.indexOfLast { (it.start ?: 0.0) <= posicion.toDouble() }
    } else {
        -1
    }

    // Mantiene la línea actual a la vista
    LaunchedEffect(indiceActual) {
        if (indiceActual >= 0) lista.animateScrollToItem((indiceActual - 2).coerceAtLeast(0))
    }

    LazyColumn(
        state = lista,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
    ) {
        itemsIndexed(lineas) { indice, linea ->
            if (linea.value.isBlank()) {
                Spacer(Modifier.height(16.dp))
            } else {
                val activa = indice == indiceActual
                val tocar = if (sincronizada) {
                    Modifier.clickable { linea.start?.let { Reproductor.irAPosicion(it.toLong()) } }
                } else {
                    Modifier
                }
                Text(
                    linea.value,
                    style = if (activa) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                    fontWeight = if (activa) FontWeight.Bold else FontWeight.Normal,
                    color = if (activa) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().then(tocar).padding(vertical = 6.dp)
                )
            }
        }
    }
}

private suspend fun cargarLetra(id: String): EstadoLetra {
    val api = Sesion.api ?: return EstadoLetra.Fallo("No hay sesión iniciada")
    return try {
        val r = api.letra(Sesion.parametros() + ("id" to id)).respuesta
        if (r.status != "ok") {
            EstadoLetra.Fallo(r.error?.message ?: "Error desconocido")
        } else {
            val letras = r.lyricsList?.structuredLyrics.orEmpty()
                .filter { l -> l.line.any { it.value.isNotBlank() } }
            // Se prefiere una versión sincronizada si el servidor la tiene
            val elegida = letras.firstOrNull { l -> l.synced && l.line.any { it.start != null } }
                ?: letras.firstOrNull()
            if (elegida == null) {
                EstadoLetra.SinLetra
            } else {
                EstadoLetra.Listo(
                    elegida.line,
                    elegida.synced && elegida.line.any { it.start != null }
                )
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        EstadoLetra.Fallo("No se pudo cargar la letra: ${e.message ?: e.javaClass.simpleName}")
    }
}