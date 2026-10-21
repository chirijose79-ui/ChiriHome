package com.chirihome.music.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

object DescargasLocal {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializador = ListSerializer(Cancion.serializer())
    private var archivo: File? = null

    val canciones = mutableStateOf<List<Cancion>>(emptyList())

    fun init(contexto: Context) {
        if (archivo != null) return
        val f = File(contexto.applicationContext.filesDir, "descargas.json")
        archivo = f
        canciones.value = try {
            if (f.exists()) json.decodeFromString(serializador, f.readText()) else emptyList()
        } catch (e: Exception) {
            Log.e("DescargasLocal", "No se pudo leer descargas.json", e)
            emptyList()
        }
    }

    fun agregar(cancion: Cancion) {
        if (canciones.value.any { it.id == cancion.id }) return
        canciones.value = canciones.value + cancion
        guardar()
    }

    fun quitar(id: String) {
        if (canciones.value.none { it.id == id }) return
        canciones.value = canciones.value.filter { it.id != id }
        guardar()
    }

    private fun guardar() {
        try {
            archivo?.writeText(json.encodeToString(serializador, canciones.value))
        } catch (e: Exception) {
            Log.e("DescargasLocal", "No se pudo guardar descargas.json", e)
        }
    }
}