package com.chirihome.music.ui.cola

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chirihome.music.data.Cancion
import com.chirihome.music.player.Reproductor
import com.chirihome.music.ui.reproductor.MiniReproductor

@Composable
fun ColaScreen(modifier: Modifier = Modifier) {
    val cola = Reproductor.cola.value
    val actual = Reproductor.indiceActual.value

    Column(modifier.fillMaxSize()) {
        Text(
            "Cola de reproducción",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp)
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (cola.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("La cola está vacía. En una canción, toca el icono de tres puntos y elige «Agregar a la cola».")
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(cola, key = { i, c -> "${i}_${c.id}" }) { indice, cancion ->
                        FilaCola(
                            cancion = cancion,
                            esActual = indice == actual,
                            alTocar = { Reproductor.irA(indice) },
                            alQuitar = { Reproductor.quitar(indice) }
                        )
                    }
                }
            }
        }

        MiniReproductor(mostrarCola = false)
    }
}

@Composable
private fun FilaCola(
    cancion: Cancion,
    esActual: Boolean,
    alTocar: () -> Unit,
    alQuitar: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { alTocar() }
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Hueco fijo para que los títulos queden alineados, con o sin icono
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (esActual) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Suena ahora",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                cancion.title ?: "Sin título",
                fontWeight = if (esActual) FontWeight.Bold else FontWeight.Normal,
                color = if (esActual) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(cancion.artist, cancion.album).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = alQuitar) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Quitar de la cola",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}