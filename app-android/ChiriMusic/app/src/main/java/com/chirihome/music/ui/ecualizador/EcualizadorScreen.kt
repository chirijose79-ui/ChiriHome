package com.chirihome.music.ui.ecualizador

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chirihome.music.player.EcualizadorAudio
import com.chirihome.music.ui.reproductor.MiniReproductor
import kotlin.math.roundToInt

@Composable
fun EcualizadorScreen() {
    val disponible = EcualizadorAudio.disponible.value
    val activado = EcualizadorAudio.activado.value

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Ecualizador",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = activado,
                    onCheckedChange = { EcualizadorAudio.activar(it) },
                    enabled = disponible
                )
            }

            if (!disponible) {
                Spacer(Modifier.height(16.dp))
                Text("El ecualizador no está disponible todavía. Cierra la app y vuelve a abrirla.")
            } else {
                Spacer(Modifier.height(12.dp))
                Text("Presets", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EcualizadorAudio.presets.value.forEachIndexed { i, nombre ->
                        FilterChip(
                            selected = EcualizadorAudio.presetActual.value == i,
                            onClick = { EcualizadorAudio.usarPreset(i) },
                            label = { Text(nombre) },
                            enabled = activado
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                val min = EcualizadorAudio.nivelMin.value.toFloat()
                val max = EcualizadorAudio.nivelMax.value.toFloat()
                val frecuencias = EcualizadorAudio.frecuenciasHz.value
                EcualizadorAudio.niveles.value.forEachIndexed { i, nivel ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            etiquetaFrecuencia(frecuencias.getOrElse(i) { 0 }),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(64.dp)
                        )
                        Slider(
                            value = nivel.toFloat(),
                            onValueChange = { EcualizadorAudio.fijarBanda(i, it.roundToInt()) },
                            onValueChangeFinished = { EcualizadorAudio.guardar() },
                            valueRange = min..max,
                            enabled = activado,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "%+.1f dB".format(nivel / 100f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(64.dp)
                        )
                    }
                }

                TextButton(
                    onClick = { EcualizadorAudio.restablecer() },
                    enabled = activado
                ) { Text("Restablecer") }
            }
        }
        MiniReproductor(mostrarCola = false)
    }
}

private fun etiquetaFrecuencia(hz: Int): String {
    if (hz < 1000) return "$hz Hz"
    val k = hz / 1000f
    return if (k == k.toInt().toFloat()) "${k.toInt()} kHz" else "%.1f kHz".format(k)
}