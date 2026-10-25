package com.chirihome.music.ui.descargas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chirihome.music.data.DescargasLocal
import com.chirihome.music.player.Descargas
import com.chirihome.music.player.EstadoDescarga
import com.chirihome.music.player.Reproductor
import com.chirihome.music.ui.componentes.BotonDescarga
import com.chirihome.music.ui.reproductor.MiniReproductor

@Composable
fun DescargasScreen() {
    val contexto = LocalContext.current
    var confirmar by remember { mutableStateOf(false) }

    val todas = DescargasLocal.canciones.value
    val listas = todas.filter { Descargas.estadoDe(it.id) == EstadoDescarga.LISTA }
    val enCurso = todas.count { Descargas.estadoDe(it.id) == EstadoDescarga.EN_CURSO }
    val hayAlgo = listas.isNotEmpty() || enCurso > 0

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Descargas", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    val partes = mutableListOf(
                        "${listas.size} canciones",
                        formatoTamano(Descargas.espacioUsado(contexto))
                    )
                    if (enCurso > 0) partes.add("$enCurso descargando")
                    Text(partes.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { confirmar = true }, enabled = hayAlgo) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = "Borrar todas las descargas"
                    )
                }
            }

            if (listas.isEmpty()) {
                Text(
                    "Todavía no hay canciones descargadas.",
                    Modifier.padding(horizontal = 16.dp)
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(listas, key = { _, c -> c.id }) { indice, cancion ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { Reproductor.reproducir(listas, indice) }
                                .padding(start = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
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
                        }
                    }
                }
            }
        }
        MiniReproductor()
    }

    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("Borrar todas las descargas") },
            text = { Text("Se eliminarán las canciones descargadas de este celular. Seguirán en tu servidor.") },
            confirmButton = {
                TextButton(onClick = {
                    Descargas.borrarTodo(contexto)
                    confirmar = false
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmar = false }) { Text("Cancelar") }
            }
        )
    }
}

private fun formatoTamano(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "%.1f MB".format(mb)
}