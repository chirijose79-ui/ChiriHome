package com.chirihome.music.player

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.Equalizer
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit

object EcualizadorAudio {

    private const val ETIQUETA = "EcualizadorAudio"
    private const val NOMBRE_PREFERENCIAS = "ecualizador"

    private const val CLAVE_ACTIVADO = "activado"
    private const val CLAVE_PRESET = "preset"
    private const val CLAVE_BANDA = "banda_"

    private var eq: Equalizer? = null
    private var sesionActual = 0
    private var prefs: SharedPreferences? = null

    val disponible = mutableStateOf(false)
    val activado = mutableStateOf(false)

    val frecuenciasHz = mutableStateOf<List<Int>>(emptyList())
    val niveles = mutableStateOf<List<Int>>(emptyList())
    val presets = mutableStateOf<List<String>>(emptyList())

    val nivelMin = mutableIntStateOf(-1500)
    val nivelMax = mutableIntStateOf(1500)
    val presetActual = mutableIntStateOf(-1)

    /**
     * Conecta el ecualizador a una sesión de audio.
     *
     * El identificador debe pertenecer al reproductor que
     * está reproduciendo el audio.
     */
    @Synchronized
    fun conectar(contexto: Context, sesionId: Int) {
        if (sesionId == 0) {
            Log.w(ETIQUETA, "No se puede conectar: sesión de audio no válida")
            return
        }

        if (eq != null && sesionId == sesionActual) {
            return
        }

        liberarInterno()

        val preferencias = contexto.applicationContext
            .getSharedPreferences(
                NOMBRE_PREFERENCIAS,
                Context.MODE_PRIVATE
            )

        prefs = preferencias

        var nuevoEcualizador: Equalizer? = null

        try {
            val e = Equalizer(0, sesionId)
            nuevoEcualizador = e

            val cantidadBandas = e.numberOfBands.toInt()
            val rango = e.bandLevelRange

            nivelMin.intValue = rango[0].toInt()
            nivelMax.intValue = rango[1].toInt()

            frecuenciasHz.value = (0 until cantidadBandas).map { indice ->
                e.getCenterFreq(indice.toShort()) / 1000
            }

            val cantidadPresets = e.numberOfPresets.toInt()

            presets.value = (0 until cantidadPresets).map { indice ->
                e.getPresetName(indice.toShort())
            }

            val nivelesGuardados = (0 until cantidadBandas).map { indice ->
                preferencias.getInt(
                    "$CLAVE_BANDA$indice",
                    e.getBandLevel(indice.toShort()).toInt()
                ).coerceIn(
                    nivelMin.intValue,
                    nivelMax.intValue
                )
            }

            nivelesGuardados.forEachIndexed { indice, nivel ->
                e.setBandLevel(
                    indice.toShort(),
                    nivel.toShort()
                )
            }

            val encendido = preferencias.getBoolean(
                CLAVE_ACTIVADO,
                false
            )

            e.enabled = encendido

            eq = e
            sesionActual = sesionId

            niveles.value = nivelesGuardados
            activado.value = e.enabled
            presetActual.intValue = preferencias.getInt(
                CLAVE_PRESET,
                -1
            ).takeIf { it in presets.value.indices } ?: -1

            disponible.value = true

        } catch (ex: Exception) {
            Log.e(
                ETIQUETA,
                "No se pudo crear o configurar el ecualizador",
                ex
            )

            try {
                nuevoEcualizador?.release()
            } catch (errorLiberacion: Exception) {
                Log.e(
                    ETIQUETA,
                    "Error al liberar el ecualizador después de un fallo",
                    errorLiberacion
                )
            }

            eq = null
            sesionActual = 0
            restablecerEstado()
        }
    }

    /**
     * Libera el recurso de audio.
     *
     * Las preferencias guardadas se conservan.
     */
    @Synchronized
    fun liberar() {
        liberarInterno()
    }

    private fun liberarInterno() {
        try {
            eq?.release()
        } catch (ex: Exception) {
            Log.e(
                ETIQUETA,
                "Error al liberar el ecualizador",
                ex
            )
        } finally {
            eq = null
            sesionActual = 0
            restablecerEstado()
        }
    }

    private fun restablecerEstado() {
        disponible.value = false
        activado.value = false

        frecuenciasHz.value = emptyList()
        niveles.value = emptyList()
        presets.value = emptyList()

        presetActual.intValue = -1

        nivelMin.intValue = -1500
        nivelMax.intValue = 1500
    }

    /**
     * Activa o desactiva el ecualizador.
     */
    @Synchronized
    fun activar(valor: Boolean) {
        val e = eq ?: return

        try {
            e.enabled = valor

            val estadoReal = e.enabled
            activado.value = estadoReal

            prefs?.edit {
                putBoolean(CLAVE_ACTIVADO, estadoReal)
            }

        } catch (ex: Exception) {
            Log.e(
                ETIQUETA,
                "No se pudo cambiar el estado del ecualizador",
                ex
            )
        }
    }

    /**
     * Modifica una banda y guarda el cambio.
     *
     * Los niveles se expresan en milibelios (mB).
     */
    @Synchronized
    fun fijarBanda(indice: Int, nivel: Int) {
        val e = eq ?: return

        if (indice !in niveles.value.indices) {
            Log.w(
                ETIQUETA,
                "Índice de banda no válido: $indice"
            )
            return
        }

        val nivelSeguro = nivel.coerceIn(
            nivelMin.intValue,
            nivelMax.intValue
        )

        try {
            e.setBandLevel(
                indice.toShort(),
                nivelSeguro.toShort()
            )

            niveles.value = niveles.value.toMutableList().also {
                it[indice] = nivelSeguro
            }

            // La configuración deja de corresponder a un preset.
            presetActual.intValue = -1

            guardar()

        } catch (ex: Exception) {
            Log.e(
                ETIQUETA,
                "No se pudo cambiar la banda $indice",
                ex
            )
        }
    }

    /**
     * Aplica un preset disponible en el dispositivo.
     */
    @Synchronized
    fun usarPreset(indice: Int) {
        val e = eq ?: return

        if (indice !in presets.value.indices) {
            Log.w(
                ETIQUETA,
                "Índice de preset no válido: $indice"
            )
            return
        }

        try {
            e.usePreset(indice.toShort())

            niveles.value = niveles.value.indices.map { banda ->
                e.getBandLevel(banda.toShort()).toInt()
            }

            presetActual.intValue = indice

            guardar()

        } catch (ex: Exception) {
            Log.e(
                ETIQUETA,
                "No se pudo aplicar el preset $indice",
                ex
            )
        }
    }

    /**
     * Restablece todas las bandas a cero.
     */
    @Synchronized
    fun restablecer() {
        val e = eq ?: return

        try {
            val cero = 0.coerceIn(
                nivelMin.intValue,
                nivelMax.intValue
            )

            niveles.value.indices.forEach { indice ->
                e.setBandLevel(
                    indice.toShort(),
                    cero.toShort()
                )
            }

            niveles.value = niveles.value.map { cero }
            presetActual.intValue = -1

            guardar()

        } catch (ex: Exception) {
            Log.e(
                ETIQUETA,
                "No se pudo restablecer el ecualizador",
                ex
            )
        }
    }

    /**
     * Persiste los niveles, el preset y el estado observable.
     */
    @Synchronized
    fun guardar() {
        val preferencias = prefs ?: return

        preferencias.edit {
            niveles.value.forEachIndexed { indice, nivel ->
                putInt(
                    "$CLAVE_BANDA$indice",
                    nivel
                )
            }

            putInt(
                CLAVE_PRESET,
                presetActual.intValue
            )

            putBoolean(
                CLAVE_ACTIVADO,
                activado.value
            )
        }
    }
}
