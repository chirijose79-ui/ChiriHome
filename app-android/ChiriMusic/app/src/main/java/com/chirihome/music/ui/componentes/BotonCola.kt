package com.chirihome.music.ui.componentes

import android.widget.Toast
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.chirihome.music.data.Cancion
import com.chirihome.music.player.Reproductor

@Composable
fun BotonCola(cancion: Cancion, modifier: Modifier = Modifier) {
    val contexto = LocalContext.current
    IconButton(
        onClick = {
            Reproductor.agregarAlFinal(cancion)
            Toast.makeText(contexto, "Agregada a la cola", Toast.LENGTH_SHORT).show()
        },
        modifier = modifier
    ) {
        Text(
            "+",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}