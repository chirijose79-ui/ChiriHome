package com.chirihome.music.ui.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chirihome.music.data.PlaylistDetalle
import com.chirihome.music.player.Descargas
import com.chirihome.music.player.EstadoDescarga
import com.chirihome.music.player.Reproductor
import com.chirihome.music.ui.componentes.BotonDescarga
import com.chirihome.music.ui.componentes.BotonFavorito
import com.chirihome.music.ui.reproductor.MiniReproductor

@Composable
fun PlaylistScreen(modifier: Modifier = Modifier, vm: PlaylistViewModel = viewModel()) {
    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val e = vm.estado) {
                EstadoPlaylist.Cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                is EstadoPlaylist.Fallo -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(e.mensaje, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.cargar() }) { Text("Reintentar") }
                }

                is EstadoPlaylist.Listo -> Contenido(e.playlist) { indice -> vm.quitar(indice) }
            }
        }
        MiniReproductor()
    }
}

@Composable
private fun Contenido(playlist: PlaylistDetalle, alQuitar: (Int) -> Unit) {
    val contexto = LocalContext.current
    // Una canción repetida en la playlist se descarga una sola vez
    val unicas = playlist.entry.distinctBy { it.id }
    val total = unicas.size
    val listas = unicas.count { Descargas.estadoDe(it.id) == EstadoDescarga.LISTA }
    val enCurso = unicas.filter { Descargas.estadoDe(it.id) == EstadoDescarga.EN_CURSO }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text(playlist.name ?: "Playlist", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${playlist.entry.size} canciones",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    enabled = playlist.entry.isNotEmpty(),
                    onClick = { Reproductor.reproducir(playlist.entry, 0) }
                ) { Text("Reproducir todo") }
                if (total > 0) {
                    TextButton(
                        onClick = {
                            when {
                                listas == total ->
                                    Descargas.quitarVarias(contexto, unicas.map { it.id })
                                enCurso.isNotEmpty() ->
                                    Descargas.quitarVarias(contexto, enCurso.map { it.id })
                                else -> Descargas.descargarTodas(contexto, unicas)
                            }
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            when {
                                listas == total -> "Quitar descarga de la playlist"
                                enCurso.isNotEmpty() -> "Cancelar descarga ($listas/$total)"
                                listas > 0 -> "Descargar lo que falta"
                                else -> "Descargar playlist"
                            }
                        )
                    }
                }
            }
        }
        itemsIndexed(playlist.entry, key = { i, c -> "${i}_${c.id}" }) { indice, cancion ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { Reproductor.reproducir(playlist.entry, indice) }
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(
                        cancion.title ?: "Sin título",
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
                BotonDescarga(cancion)
                TextButton(onClick = { alQuitar(indice) }) { Text("Quitar") }
                BotonFavorito(cancion)
            }
        }
    }
}