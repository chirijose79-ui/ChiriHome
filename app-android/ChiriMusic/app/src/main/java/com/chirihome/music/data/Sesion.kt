package com.chirihome.music.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import java.net.URLEncoder

object Sesion {
    var urlBase: String = ""
        private set
    var api: SubsonicApi? = null
        private set
    private var auth: Map<String, String> = emptyMap()
    private var prefs: SharedPreferences? = null

    // Pasa a true cuando el servidor responde 401 a una llamada de la API
    val sesionCaducada = mutableStateOf(false)

    // Llamar una vez al arrancar la app
    fun init(contexto: Context) {
        prefs = contexto.applicationContext.getSharedPreferences("sesion", Context.MODE_PRIVATE)
    }

    // El usuario y el token vienen del login del gateway; la contraseña no se guarda
    fun iniciarConToken(url: String, usuario: String, token: String) {
        urlBase = url
        auth = parametrosBase(usuario, token)
        api = SubsonicFabrica.crearApi(url)
        sesionCaducada.value = false
        prefs?.edit()
            ?.clear()
            ?.putString("url", url)
            ?.putString("u", usuario)
            ?.putString("p", token)
            ?.apply()
    }

    // Devuelve true si había una sesión guardada y quedó lista para usar
    fun restaurar(): Boolean {
        val p = prefs ?: return false
        val url = p.getString("url", null)
        val u = p.getString("u", null)
        val token = p.getString("p", null)
        if (url == null || u == null || token == null) return false
        urlBase = url
        auth = parametrosBase(u, token)
        api = SubsonicFabrica.crearApi(url)
        return true
    }

    fun cerrar() {
        prefs?.edit()?.clear()?.apply()
        urlBase = ""
        auth = emptyMap()
        api = null
        sesionCaducada.value = false
    }

    fun parametros(): Map<String, String> = auth

    private fun parametrosBase(usuario: String, token: String): Map<String, String> =
        mapOf(
            "u" to usuario,
            "p" to token,
            "v" to "1.16.1",
            "c" to "ChiriMusic",
            "f" to "json"
        )

    private fun consulta(extra: Map<String, String>): String =
        (auth + extra).entries.joinToString("&") {
            "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}"
        }

    // El token es fijo durante la sesión, así Coil puede guardar las carátulas en caché
    fun urlCaratula(id: String, tamano: Int = 300): String =
        "${urlBase}rest/getCoverArt?" + consulta(mapOf("id" to id, "size" to tamano.toString()))

    fun urlStream(id: String): String =
        "${urlBase}rest/stream?" + consulta(mapOf("id" to id))
}