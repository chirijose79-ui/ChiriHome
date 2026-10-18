package com.chirihome.music.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.QueryMap
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

interface SubsonicApi {
    @GET("rest/ping")
    suspend fun ping(@QueryMap params: Map<String, String>): SubsonicEnvelope
}

object SubsonicAuth {
    // Autenticación Subsonic: token = md5(contraseña + sal). La contraseña no viaja en claro.
    fun parametros(usuario: String, clave: String): Map<String, String> {
        val sal = UUID.randomUUID().toString().replace("-", "").take(12)
        return mapOf(
            "u" to usuario,
            "t" to md5(clave + sal),
            "s" to sal,
            "v" to "1.16.1",
            "c" to "ChiriMusic",
            "f" to "json"
        )
    }

    private fun md5(texto: String): String =
        MessageDigest.getInstance("MD5")
            .digest(texto.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

object SubsonicFabrica {
    private val json = Json { ignoreUnknownKeys = true }

    private val cliente = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun normalizarUrl(texto: String): String {
        var url = texto.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
        if (!url.endsWith("/")) url += "/"
        return url
    }

    fun crearApi(urlBase: String): SubsonicApi =
        Retrofit.Builder()
            .baseUrl(urlBase)
            .client(cliente)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SubsonicApi::class.java)
}