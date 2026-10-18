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

    // La sal es fija durante la sesión, así Coil puede guardar las carátulas en caché
    fun urlCaratula(id: String, tamano: Int = 300): String {
        val q = (auth + mapOf("id" to id, "size" to tamano.toString()))
            .entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
        return "${urlBase}rest/getCoverArt?$q"
    }
}