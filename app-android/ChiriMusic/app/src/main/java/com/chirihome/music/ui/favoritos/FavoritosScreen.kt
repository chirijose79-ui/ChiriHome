package com.chirihome.music.ui.favoritos

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.chirihome.music.data.Cancion
import com.chirihome.music.data.Favoritos
import com.chirihome.music.data.Sesion
import com.chirihome.music.player.Reproductor
import com.chirihome.music.ui.componentes.BotonFavorito
import com.chirihome.music.ui.reproductor.MiniReproductor

@Composable
fun FavoritosScreen(modifier: Modifier = Modifier) {
    val lista = Favoritos.canciones.value

    Column(modifier.fillMaxSize()) {
        Text(
            "Favoritos",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp)
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (lista.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Aún no tienes canciones favoritas. Toca el corazón en una canción para agregarla.")
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(lista, key = { _, c -> c.id }) { indice, cancion ->
                        FilaFavorita(cancion) { Reproductor.reproducir(lista, indice) }
                    }
                }
            }
        }

        MiniReproductor()
    }
}

@Composable
private fun FilaFavorita(cancion: Cancion, alTocar: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { alTocar() }
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = cancion.coverArt?.let { Sesion.urlCaratula(it, 120) },
            contentDescription = cancion.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(cancion.title ?: "Sin título", maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(cancion.artist, cancion.album).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        BotonFavorito(cancion)
    }
}