package com.chirihome.music.data.video

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object InformeVideo {
    // Propio de la app, no de una pantalla: el aviso final debe salir aunque la pantalla ya se haya cerrado
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun inicio(idItem: String, sesionId: String, posicionMs: Long) = enviar { api ->
        api.reportarInicio(estado(idItem, sesionId, posicionMs, enPausa = false))
    }

    fun progreso(idItem: String, sesionId: String, posicionMs: Long, enPausa: Boolean) = enviar { api ->
        api.reportarProgreso(estado(idItem, sesionId, posicionMs, enPausa))
    }

    fun fin(idItem: String, sesionId: String, posicionMs: Long) = enviar { api ->
        api.reportarFin(JfFinReproduccion(idItem, aTicks(posicionMs), sesionId))
        // El servidor ya tiene la posición nueva: se avisa a las pantallas que la muestran
        SesionVideo.versionProgreso.intValue++
    }

    private fun estado(idItem: String, sesionId: String, posicionMs: Long, enPausa: Boolean) =
        JfEstadoReproduccion(
            itemId = idItem,
            posicionTicks = aTicks(posicionMs),
            enPausa = enPausa,
            metodo = "DirectPlay",
            sesionId = sesionId,
            puedeBuscar = true
        )

    // 1 milisegundo = 10.000 ticks
    private fun aTicks(milisegundos: Long): Long = milisegundos.coerceAtLeast(0) * 10_000L

    private fun enviar(accion: suspend (JellyfinApi) -> Unit) {
        val api = SesionVideo.api ?: return
        scope.launch {
            try {
                accion(api)
            } catch (_: Exception) {
                // Un aviso perdido no debe afectar a la reproducción
            }
        }
    }
}