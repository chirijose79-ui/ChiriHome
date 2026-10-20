package com.chirihome.music.ui.album

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.chirihome.music.data.AlbumDetalle
import com.chirihome.music.data.Cancion
import com.chirihome.music.data.Sesion
import com.chirihome.music.player.Reproductor
import com.chirihome.music.ui.reproductor.MiniReproductor
import com.chirihome.music.ui.componentes.BotonFavorito
import com.chirihome.music.ui.componentes.BotonMas

@Composable
fun AlbumScreen(modifier: Modifier = Modifier, vm: AlbumViewModel = viewModel()) {
    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val e = vm.estado) {
                EstadoAlbum.Cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                is EstadoAlbum.Fallo -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(e.mensaje, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.cargar() }) { Text("Reintentar") }
                }

                is EstadoAlbum.Listo -> ListaCanciones(e.album)
            }
        }
        MiniReproductor()
    }
}

@Composable
private fun ListaCanciones(album: AlbumDetalle) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { Cabecera(album) }
        itemsIndexed(album.song, key = { _, c -> c.id }) { indice, cancion ->
            FilaCancion(cancion, indice + 1) { Reproductor.reproducir(album.song, indice) }
        }
    }
}

@Composable
private fun Cabecera(album: AlbumDetalle) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = album.coverArt?.let { Sesion.urlCaratula(it) },
            contentDescription = album.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(120.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(album.name ?: "Sin título", style = MaterialTheme.typography.titleLarge)
            Text(album.artist ?: "", style = MaterialTheme.typography.bodyMedium)
            val detalle = listOfNotNull(album.year?.toString(), "${album.song.size} canciones")
            Text(detalle.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FilaCancion(cancion: Cancion, numero: Int, alTocar: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { alTocar() }
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${cancion.track ?: numero}", Modifier.width(32.dp), style = MaterialTheme.typography.bodySmall)
        Text(
            cancion.title ?: "Sin título",
            Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        cancion.duration?.let {
            Text("%d:%02d".format(it / 60, it % 60), style = MaterialTheme.typography.bodySmall)
        }
        BotonMas(cancion)
        BotonFavorito(cancion)
    }
}