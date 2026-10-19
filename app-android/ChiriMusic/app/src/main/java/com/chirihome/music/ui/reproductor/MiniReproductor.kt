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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chirihome.music.player.Reproductor

@Composable
fun MiniReproductor(modifier: Modifier = Modifier) {
    val cancion = Reproductor.actual.value ?: return
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider()
        Text(
            cancion.title ?: "Sin título",
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
        )
        Text(
            cancion.artist ?: "",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
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