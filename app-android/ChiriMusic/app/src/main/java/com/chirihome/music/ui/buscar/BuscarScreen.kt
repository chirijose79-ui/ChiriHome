package com.chirihome.music.ui.buscar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.chirihome.music.data.Album
import com.chirihome.music.data.Cancion
import com.chirihome.music.data.Sesion
import com.chirihome.music.player.Reproductor
import com.chirihome.music.ui.reproductor.MiniReproductor

@Composable
fun BuscarScreen(
    alAbrirAlbum: (String) -> Unit,
    modifier: Modifier = Modifier,
    vm: BuscarViewModel = viewModel()
) {
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { foco.requestFocus() }

    Column(modifier.fillMaxSize().imePadding()) {
        OutlinedTextField(
            value = vm.consulta,
            onValueChange = { vm.cambiarConsulta(it) },
            label = { Text("Buscar álbumes y canciones") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(12.dp).focusRequester(foco)
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val e = vm.estado) {
                EstadoBusqueda.Inactivo -> Centrado { Text("Escribe al menos 2 letras") }
                EstadoBusqueda.Cargando -> Centrado { CircularProgressIndicator() }
                is EstadoBusqueda.Fallo -> Centrado {
                    Text(e.mensaje, color = MaterialTheme.colorScheme.error)
                }
                is EstadoBusqueda.Listo ->
                    if (e.albumes.isEmpty() && e.canciones.isEmpty()) {
                        Centrado { Text("Sin resultados") }
                    } else {
                        Resultados(e.albumes, e.canciones, alAbrirAlbum)
                    }
            }
        }

        MiniReproductor()
    }
}

@Composable
private fun Centrado(contenido: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        contenido()
    }
}

@Composable
private fun Resultados(
    albumes: List<Album>,
    canciones: List<Cancion>,
    alAbrirAlbum: (String) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (albumes.isNotEmpty()) {
            item { Titulo("Álbumes") }
            items(albumes, key = { "a${it.id}" }) { album ->
                Fila(
                    caratula = album.coverArt,
                    titulo = album.name ?: "Sin título",
                    subtitulo = album.artist ?: ""
                ) { alAbrirAlbum(album.id) }
            }
        }
        if (canciones.isNotEmpty()) {
            item { Titulo("Canciones") }
            itemsIndexed(canciones, key = { _, c -> "c${c.id}" }) { indice, cancion ->
                Fila(
                    caratula = cancion.coverArt,
                    titulo = cancion.title ?: "Sin título",
                    subtitulo = listOfNotNull(cancion.artist, cancion.album).joinToString(" · ")
                ) { Reproductor.reproducir(canciones, indice) }
            }
        }
    }
}

@Composable
private fun Titulo(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun Fila(caratula: String?, titulo: String, subtitulo: String, alTocar: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { alTocar() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = caratula?.let { Sesion.urlCaratula(it, 120) },
            contentDescription = titulo,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitulo,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}