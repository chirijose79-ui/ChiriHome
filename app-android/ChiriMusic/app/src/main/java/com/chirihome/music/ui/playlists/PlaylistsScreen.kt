package com.chirihome.music.ui.playlists

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chirihome.music.data.Playlist
import com.chirihome.music.data.Playlists
import com.chirihome.music.ui.reproductor.MiniReproductor

@Composable
fun PlaylistsScreen(alAbrir: (String) -> Unit, modifier: Modifier = Modifier) {
    val contexto = LocalContext.current
    var creando by remember { mutableStateOf(false) }
    var aBorrar by remember { mutableStateOf<Playlist?>(null) }
    val lista = Playlists.lista.value

    LaunchedEffect(Unit) { Playlists.cargar() }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Playlists",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { creando = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Nueva playlist")
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (lista.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Aún no tienes playlists. Toca el + de arriba para crear una.")
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(lista, key = { it.id }) { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { alAbrir(p.id) }
                                .padding(start = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                                Text(
                                    p.name ?: "Sin nombre",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${p.songCount ?: 0} canciones",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = { aBorrar = p }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Borrar playlist",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        MiniReproductor()
    }

    if (creando) {
        var nombre by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creando = false },
            title = { Text("Nueva playlist") },
            text = {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = nombre.isNotBlank(),
                    onClick = {
                        Playlists.crear(nombre) { ok ->
                            if (!ok) Toast.makeText(contexto, "No se pudo crear", Toast.LENGTH_SHORT).show()
                        }
                        creando = false
                    }
                ) { Text("Crear") }
            },
            dismissButton = { TextButton(onClick = { creando = false }) { Text("Cancelar") } }
        )
    }

    aBorrar?.let { p ->
        AlertDialog(
            onDismissRequest = { aBorrar = null },
            title = { Text("Borrar playlist") },
            text = { Text("¿Borrar \"${p.name ?: "Sin nombre"}\"? Las canciones no se borran de tu biblioteca.") },
            confirmButton = {
                TextButton(onClick = {
                    Playlists.borrar(p.id) { ok ->
                        if (!ok) Toast.makeText(contexto, "No se pudo borrar", Toast.LENGTH_SHORT).show()
                    }
                    aBorrar = null
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { aBorrar = null }) { Text("Cancelar") } }
        )
    }
}