package com.chirihome.music.data.video

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import java.util.UUID

object SesionVideo {
    var urlBase: String = ""
        private set
    var api: JellyfinApi? = null
        private set
    var token: String? = null
        private set
    var usuarioId: String = ""
        private set
    var nombreUsuario: String = ""
        private set

    // Identifica esta instalación ante Jellyfin; se guarda para que no cambie en cada inicio de sesión
    private var deviceId: String = ""
    private var prefs: SharedPreferences? = null

    // Pasa a true cuando Jellyfin rechaza el token
    val sesionCaducada = mutableStateOf(false)

    // Llamar una vez al arrancar la app
    fun init(contexto: Context) {
        val p = contexto.applicationContext.getSharedPreferences("sesion_video", Context.MODE_PRIVATE)
        prefs = p
        deviceId = p.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            p.edit().putString("device_id", it).apply()
        }
    }

    fun estaActiva(): Boolean = api != null && token != null

    // El servidor que se usó la última vez, para no tener que escribirlo de nuevo
    fun ultimaUrl(): String = prefs?.getString("url", "") ?: ""

    fun iniciar(url: String, tokenNuevo: String, idUsuario: String, nombre: String) {
        urlBase = url
        token = tokenNuevo
        usuarioId = idUsuario
        nombreUsuario = nombre
        api = JellyfinFabrica.crearApi(url)
        sesionCaducada.value = false
        prefs?.edit()
            ?.putString("url", url)
            ?.putString("token", tokenNuevo)
            ?.putString("usuario_id", idUsuario)
            ?.putString("usuario_nombre", nombre)
            ?.apply()
    }

    // Devuelve true si había una sesión guardada y quedó lista para usar
    fun restaurar(): Boolean {
        val p = prefs ?: return false
        val url = p.getString("url", null)
        val t = p.getString("token", null)
        val id = p.getString("usuario_id", null)
        if (url == null || t == null || id == null) return false
        urlBase = url
        token = t
        usuarioId = id
        nombreUsuario = p.getString("usuario_nombre", "") ?: ""
        api = JellyfinFabrica.crearApi(url)
        return true
    }

    // Se conserva la dirección del servidor y el identificador de la instalación
    fun cerrar() {
        prefs?.edit()
            ?.remove("token")
            ?.remove("usuario_id")
            ?.remove("usuario_nombre")
            ?.apply()
        token = null
        usuarioId = ""
        nombreUsuario = ""
        api = null
        sesionCaducada.value = false
    }

    // Formato que exige Jellyfin: valores entre comillas dobles
    fun cabeceraAuth(): String {
        val base = "MediaBrowser Client=\"ChiriMusic\", Device=\"Android\", " +
                "DeviceId=\"$deviceId\", Version=\"1.0\""
        val t = token
        return if (t != null) "$base, Token=\"$t\"" else base
    }

    fun urlCaratula(idItem: String, etiqueta: String, altura: Int = 450): String =
        "${urlBase}Items/$idItem/Images/Primary?maxHeight=$altura&quality=90&tag=$etiqueta"

    // Reproducción directa: el archivo se envía tal cual, sin convertirlo
    fun urlVideo(idItem: String): String =
        "${urlBase}Videos/$idItem/stream?static=true"
}