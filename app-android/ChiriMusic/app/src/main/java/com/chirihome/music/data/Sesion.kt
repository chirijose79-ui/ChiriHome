package com.chirihome.music.data

import android.content.Context
import android.content.SharedPreferences
import java.net.URLEncoder

object Sesion {
    var urlBase: String = ""
        private set
    var api: SubsonicApi? = null
        private set
    private var auth: Map<String, String> = emptyMap()
    private var prefs: SharedPreferences? = null

    // Llamar una vez al arrancar la app
    fun init(contexto: Context) {
        prefs = contexto.applicationContext.getSharedPreferences("sesion", Context.MODE_PRIVATE)
    }

    fun iniciar(url: String, usuario: String, clave: String) {
        urlBase = url
        auth = SubsonicAuth.parametros(usuario, clave)
        api = SubsonicFabrica.crearApi(url)
        prefs?.edit()
            ?.putString("url", url)
            ?.putString("u", auth["u"])
            ?.putString("t", auth["t"])
            ?.putString("s", auth["s"])
            ?.apply()
    }

    // Devuelve true si había una sesión guardada y quedó lista para usar
    fun restaurar(): Boolean {
        val p = prefs ?: return false
        val url = p.getString("url", null)
        val u = p.getString("u", null)
        val t = p.getString("t", null)
        val s = p.getString("s", null)
        if (url == null || u == null || t == null || s == null) return false
        urlBase = url
        auth = mapOf("u" to u, "t" to t, "s" to s, "v" to "1.16.1", "c" to "ChiriMusic", "f" to "json")
        api = SubsonicFabrica.crearApi(url)
        return true
    }

    fun cerrar() {
        prefs?.edit()?.clear()?.apply()
        urlBase = ""
        auth = emptyMap()
        api = null
    }

    fun parametros(): Map<String, String> = auth

    private fun consulta(extra: Map<String, String>): String =
        (auth + extra).entries.joinToString("&") {
            "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}"
        }

    // La sal es fija, así Coil puede guardar las carátulas en caché
    fun urlCaratula(id: String, tamano: Int = 300): String =
        "${urlBase}rest/getCoverArt?" + consulta(mapOf("id" to id, "size" to tamano.toString()))

    fun urlStream(id: String): String =
        "${urlBase}rest/stream?" + consulta(mapOf("id" to id))
}