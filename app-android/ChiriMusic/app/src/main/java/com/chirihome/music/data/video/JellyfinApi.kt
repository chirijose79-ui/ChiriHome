package com.chirihome.music.data.video

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface JellyfinApi {
    @POST("Users/AuthenticateByName")
    suspend fun entrar(@Body datos: JfCredenciales): JfAutenticacion

    @GET("Items")
    suspend fun peliculas(
        @Query("userId") usuarioId: String,
        @Query("includeItemTypes") tipos: String = "Movie",
        @Query("recursive") recursivo: Boolean = true,
        @Query("sortBy") ordenarPor: String = "SortName",
        @Query("sortOrder") sentido: String = "Ascending",
        @Query("fields") campos: String = "Overview"
    ): JfItems

    // Una película concreta, con los formatos de su archivo
    @GET("Items")
    suspend fun detalle(
        @Query("userId") usuarioId: String,
        @Query("ids") ids: String,
        @Query("fields") campos: String = "Overview,MediaSources"
    ): JfItems
}

object JellyfinFabrica {
    private val json = Json { ignoreUnknownKeys = true }

    private val cliente = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            // Todas las peticiones llevan la cabecera de autenticación de Jellyfin
            val peticion = chain.request().newBuilder()
                .header("Authorization", SesionVideo.cabeceraAuth())
                .build()
            val respuesta = chain.proceed(peticion)
            // Un 401 con sesión iniciada significa que el token ya no vale
            if (respuesta.code == 401 && SesionVideo.token != null) {
                SesionVideo.sesionCaducada.value = true
            }
            respuesta
        }
        .build()

    // Sin http ni https: se usa https, salvo que empiece por un número (una IP de la red local)
    fun normalizarUrl(texto: String): String {
        var url = texto.trim()
        if (url.isEmpty()) return url
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = (if (url.first().isDigit()) "http://" else "https://") + url
        }
        if (!url.endsWith("/")) url += "/"
        return url
    }

    fun crearApi(urlBase: String): JellyfinApi =
        Retrofit.Builder()
            .baseUrl(urlBase)
            .client(cliente)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(JellyfinApi::class.java)
}