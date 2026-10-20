package com.chirihome.music.player

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.Equalizer
import android.util.Log
import androidx.compose.runtime.mutableStateOf

object EcualizadorAudio {
    private const val ETIQUETA = "EcualizadorAudio"

    private var eq: Equalizer? = null
    private var sesionActual = 0
    private var prefs: SharedPreferences? = null

    val disponible = mutableStateOf(false)
    val activado = mutableStateOf(false)
    val frecuenciasHz = mutableStateOf<List<Int>>(emptyList())
    val niveles = mutableStateOf<List<Int>>(emptyList())
    val nivelMin = mutableStateOf(-1500)
    val nivelMax = mutableStateOf(1500)
    val presets = mutableStateOf<List<String>>(emptyList())
    val presetActual = mutableStateOf(-1)

    @Synchronized
    fun conectar(contexto: Context, sesionId: Int) {
        if (sesionId == 0) return
        if (eq != null && sesionId == sesionActual) return
        liberarInterno()

        val p = contexto.applicationContext.getSharedPreferences("ecualizador", Context.MODE_PRIVATE)
        prefs = p
        try {
            val e = Equalizer(0, sesionId)
            val bandas = e.numberOfBands.toInt()
            val rango = e.bandLevelRange
            nivelMin.value = rango[0].toInt()
            nivelMax.value = rango[1].toInt()
            frecuenciasHz.value = (0 until bandas).map { e.getCenterFreq(it.toShort()) / 1000 }
            presets.value = (0 until e.numberOfPresets.toInt()).map { e.getPresetName(it.toShort()) }

            val guardados = (0 until bandas).map { i ->
                p.getInt("banda_$i", e.getBandLevel(i.toShort()).toInt())
                    .coerceIn(nivelMin.value, nivelMax.value)
            }
            guardados.forEachIndexed { i, n -> e.setBandLevel(i.toShort(), n.toShort()) }
            val encendido = p.getBoolean("activado", false)
            e.enabled = encendido

            eq = e
            sesionActual = sesionId
            niveles.value = guardados
            activado.value = encendido
            presetActual.value = p.getInt("preset", -1)
            disponible.value = true
        } catch (e: Exception) {
            Log.e(ETIQUETA, "No se pudo crear el ecualizador", e)
            liberarInterno()
        }
    }

    @Synchronized
    fun liberar() {
        liberarInterno()
    }

    private fun liberarInterno() {
        try {
            eq?.release()
        } catch (e: Exception) {
            Log.e(ETIQUETA, "Error al liberar el ecualizador", e)
        }
        eq = null
        sesionActual = 0
        disponible.value = false
    }

    fun activar(valor: Boolean) {
        eq?.enabled = valor
        activado.value = valor
        prefs?.edit()?.putBoolean("activado", valor)?.apply()
    }

    fun fijarBanda(indice: Int, nivel: Int) {
        val e = eq ?: return
        val n = nivel.coerceIn(nivelMin.value, nivelMax.value)
        try {
            e.setBandLevel(indice.toShort(), n.toShort())
        } catch (ex: Exception) {
            Log.e(ETIQUETA, "No se pudo cambiar la banda", ex)
            return
        }
        niveles.value = niveles.value.toMutableList().also { it[indice] = n }
        presetActual.value = -1
    }

    fun usarPreset(indice: Int) {
        val e = eq ?: return
        try {
            e.usePreset(indice.toShort())
            niveles.value = (0 until niveles.value.size).map { e.getBandLevel(it.toShort()).toInt() }
            presetActual.value = indice
            guardar()
        } catch (ex: Exception) {
            Log.e(ETIQUETA, "No se pudo aplicar el preset", ex)
        }
    }

    fun restablecer() {
        val e = eq ?: return
        try {
            val cero = 0.coerceIn(nivelMin.value, nivelMax.value)
            for (i in niveles.value.indices) e.setBandLevel(i.toShort(), cero.toShort())
            niveles.value = niveles.value.map { cero }
            presetActual.value = -1
            guardar()
        } catch (ex: Exception) {
            Log.e(ETIQUETA, "No se pudo restablecer", ex)
        }
    }

    fun guardar() {
        val editor = prefs?.edit() ?: return
        niveles.value.forEachIndexed { i, n -> editor.putInt("banda_$i", n) }
        editor.putInt("preset", presetActual.value)
        editor.apply()
    }
}