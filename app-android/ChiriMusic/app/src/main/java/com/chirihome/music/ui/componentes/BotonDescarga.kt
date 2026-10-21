package com.chirihome.music.ui.componentes

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chirihome.music.data.Cancion
import com.chirihome.music.player.Descargas
import com.chirihome.music.player.EstadoDescarga

@Composable
fun BotonDescarga(cancion: Cancion) {
    val contexto = LocalContext.current
    val estado = Descargas.estadoDe(cancion.id)
    TextButton(
        onClick = {
            when (estado) {
                EstadoDescarga.NINGUNA, EstadoDescarga.FALLIDA ->
                    Descargas.descargar(contexto, cancion)
                EstadoDescarga.EN_CURSO, EstadoDescarga.LISTA ->
                    Descargas.quitar(contexto, cancion.id)
            }
        },
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Text(
            when (estado) {
                EstadoDescarga.NINGUNA -> "↓"
                EstadoDescarga.EN_CURSO -> "…"
                EstadoDescarga.LISTA -> "✓"
                EstadoDescarga.FALLIDA -> "!"
            }
        )
    }
}