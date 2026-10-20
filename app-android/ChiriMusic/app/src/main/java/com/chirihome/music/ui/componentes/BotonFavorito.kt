package com.chirihome.music.ui.componentes

import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.chirihome.music.data.Cancion
import com.chirihome.music.data.Favoritos

@Composable
fun BotonFavorito(cancion: Cancion, modifier: Modifier = Modifier) {
    val esFavorita = cancion.id in Favoritos.ids.value
    IconButton(onClick = { Favoritos.alternar(cancion) }, modifier = modifier) {
        Text(
            if (esFavorita) "♥" else "♡",
            style = MaterialTheme.typography.titleLarge,
            color = if (esFavorita) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}