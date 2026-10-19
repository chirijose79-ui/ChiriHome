package com.chirihome.music.data

import java.net.URLEncoder

object Sesion {
    var urlBase: String = ""
        private set
    var api: SubsonicApi? = null
        private set
    private var auth: Map<String, String> = emptyMap()

    fun iniciar(url: String, usuario: String, clave: String) {
        urlBase = url
        auth = SubsonicAuth.parametros(usuario, clave)
        api = SubsonicFabrica.crearApi(url)
    }

    fun parametros(): Map<String, String> = auth

    private fun consulta(extra: Map<String, String>): String =
        (auth + extra).entries.joinToString("&") {
            "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}"
        }

    // La sal es fija durante la sesión, así Coil puede guardar las carátulas en caché
    fun urlCaratula(id: String, tamano: Int = 300): String =
        "${urlBase}rest/getCoverArt?" + consulta(mapOf("id" to id, "size" to tamano.toString()))

    fun urlStream(id: String): String =
        "${urlBase}rest/stream?" + consulta(mapOf("id" to id))
}