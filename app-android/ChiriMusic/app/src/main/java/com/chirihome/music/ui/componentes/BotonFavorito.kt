package com.chirihome.music.ui.componentes

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.chirihome.music.data.Cancion
import com.chirihome.music.data.Favoritos

@Composable
fun BotonFavorito(cancion: Cancion, modifier: Modifier = Modifier) {
    val esFavorita = cancion.id in Favoritos.ids.value
    IconButton(onClick = { Favoritos.alternar(cancion) }, modifier = modifier) {
        Icon(
            imageVector = if (esFavorita) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (esFavorita) "Quitar de favoritos" else "Marcar como favorita",
            tint = if (esFavorita) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}