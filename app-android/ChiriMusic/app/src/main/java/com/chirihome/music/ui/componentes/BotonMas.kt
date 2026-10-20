package com.chirihome.music.ui.componentes

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chirihome.music.data.Cancion
import com.chirihome.music.data.Playlists
import com.chirihome.music.player.Reproductor

@Composable
fun BotonMas(cancion: Cancion, modifier: Modifier = Modifier) {
    val contexto = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var dialogo by remember { mutableStateOf(false) }

    Box(modifier) {
        IconButton(onClick = { menu = true }) {
            Text(
                "⋮",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Agregar a la cola") },
                onClick = {
                    menu = false
                    Reproductor.agregarAlFinal(cancion)
                    Toast.makeText(contexto, "Agregada a la cola", Toast.LENGTH_SHORT).show()
                }
            )
            DropdownMenuItem(
                text = { Text("Agregar a playlist") },
                onClick = {
                    menu = false
                    Playlists.cargar()
                    dialogo = true
                }
            )
        }
    }

    if (dialogo) DialogoPlaylist(cancion) { dialogo = false }
}

@Composable
private fun DialogoPlaylist(cancion: Cancion, alCerrar: () -> Unit) {
    val contexto = LocalContext.current
    var nombre by remember { mutableStateOf("") }
    val resultado = { ok: Boolean ->
        Toast.makeText(
            contexto,
            if (ok) "Listo" else "No se pudo completar",
            Toast.LENGTH_SHORT
        ).show()
        alCerrar()
    }

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Agregar a playlist") },
        text = {
            androidx.compose.foundation.layout.Column {
                LazyColumn(Modifier.heightIn(max = 220.dp)) {
                    items(Playlists.lista.value, key = { it.id }) { p ->
                        TextButton(
                            onClick = { Playlists.agregar(p.id, cancion, resultado) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(p.name ?: "Sin nombre") }
                    }
                }
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nueva playlist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = nombre.isNotBlank(),
                onClick = { Playlists.crear(nombre, cancion, resultado) }
            ) { Text("Crear y agregar") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } }
    )
}