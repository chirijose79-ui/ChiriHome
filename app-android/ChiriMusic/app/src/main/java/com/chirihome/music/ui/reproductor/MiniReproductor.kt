package com.chirihome.music.ui.reproductor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chirihome.music.player.Reproductor
import com.chirihome.music.ui.componentes.BotonFavorito

@Composable
fun MiniReproductor(modifier: Modifier = Modifier) {
    val cancion = Reproductor.actual.value ?: return
    Column(modifier.fillMaxWidth()) {
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
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = { Reproductor.anterior() }) { Text("Anterior") }
            TextButton(onClick = { Reproductor.alternar() }) {
                Text(if (Reproductor.sonando.value) "Pausa" else "Play")
            }
            TextButton(onClick = { Reproductor.siguiente() }) { Text("Siguiente") }
        }
    }
}