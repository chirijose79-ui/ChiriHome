package com.chirihome.music.ui.albumes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.chirihome.music.data.Album
import com.chirihome.music.data.Sesion
import com.chirihome.music.ui.reproductor.MiniReproductor

@Composable
fun AlbumesScreen(
    alAbrir: (String) -> Unit,
    alBuscar: () -> Unit,
    alFavoritos: () -> Unit,
    alPlaylists: () -> Unit,
    alDescargas: () -> Unit,
    alVideo: () -> Unit,
    alSalir: () -> Unit,
    modifier: Modifier = Modifier,
    vm: AlbumesViewModel = viewModel()
) {
    var confirmarSalir by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = alBuscar) {
                Icon(Icons.Filled.Search, contentDescription = "Buscar")
            }
            IconButton(onClick = alFavoritos) {
                Icon(Icons.Filled.Favorite, contentDescription = "Favoritos")
            }
            IconButton(onClick = alPlaylists) {
                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = "Playlists")
            }
            IconButton(onClick = alDescargas) {
                Icon(Icons.Filled.Download, contentDescription = "Descargas")
            }
            IconButton(onClick = alVideo) {
                Icon(Icons.Filled.Movie, contentDescription = "Video")
            }
            IconButton(onClick = { confirmarSalir = true }) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Cerrar sesión")
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val e = vm.estado) {
                EstadoAlbumes.Cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                is EstadoAlbumes.Fallo -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(e.mensaje, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.cargar() }) { Text("Reintentar") }
                }

                is EstadoAlbumes.Listo -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(e.albumes, key = { it.id }) { album -> TarjetaAlbum(album, alAbrir) }
                }
            }
        }

        MiniReproductor()
    }

    if (confirmarSalir) {
        AlertDialog(
            onDismissRequest = { confirmarSalir = false },
            title = { Text("Cerrar sesión") },
            text = { Text("Se detendrá la reproducción y tendrás que volver a entrar. También se cerrará la sesión de video.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmarSalir = false
                    alSalir()
                }) { Text("Cerrar sesión") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarSalir = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun TarjetaAlbum(album: Album, alAbrir: (String) -> Unit) {
    Column(Modifier.clickable { alAbrir(album.id) }) {
        Card {
            AsyncImage(
                model = album.coverArt?.let { Sesion.urlCaratula(it) },
                contentDescription = album.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            album.name ?: "Sin título",
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            album.artist ?: "",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}