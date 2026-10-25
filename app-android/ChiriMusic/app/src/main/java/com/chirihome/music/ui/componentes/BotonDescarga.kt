package com.chirihome.music.ui.componentes

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chirihome.music.data.Cancion
import com.chirihome.music.player.Descargas
import com.chirihome.music.player.EstadoDescarga

@Composable
fun BotonDescarga(cancion: Cancion) {
    val contexto = LocalContext.current
    val estado = Descargas.estadoDe(cancion.id)
    IconButton(
        onClick = {
            when (estado) {
                EstadoDescarga.NINGUNA, EstadoDescarga.FALLIDA ->
                    Descargas.descargar(contexto, cancion)
                EstadoDescarga.EN_CURSO, EstadoDescarga.LISTA ->
                    Descargas.quitar(contexto, cancion.id)
            }
        }
    ) {
        when (estado) {
            EstadoDescarga.NINGUNA -> Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = "Descargar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            EstadoDescarga.EN_CURSO -> {
                val progreso = Descargas.progresoDe(cancion.id)
                val modificador = Modifier
                    .size(22.dp)
                    .semantics { contentDescription = "Descargando, toca para cancelar" }
                if (progreso != null) {
                    CircularProgressIndicator(
                        progress = { progreso / 100f },
                        modifier = modificador,
                        strokeWidth = 2.dp
                    )
                } else {
                    CircularProgressIndicator(modifier = modificador, strokeWidth = 2.dp)
                }
            }

            EstadoDescarga.LISTA -> Icon(
                imageVector = Icons.Filled.DownloadDone,
                contentDescription = "Descargada, toca para quitar",
                tint = MaterialTheme.colorScheme.primary
            )

            EstadoDescarga.FALLIDA -> Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = "La descarga falló, toca para reintentar",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}