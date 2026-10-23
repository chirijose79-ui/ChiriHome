package com.chirihome.music.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.QueryMap
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

interface SubsonicApi {
    // Login propio del gateway ChiriHome: devuelve el token que se usa como "p"
    @POST("_gateway/login")
    suspend fun loginGateway(@Body datos: LoginGatewayPeticion): LoginGatewayRespuesta

    @GET("rest/ping")
    suspend fun ping(@QueryMap params: Map<String, String>): SubsonicEnvelope

    @GET("rest/getAlbumList2")
    suspend fun albumes(@QueryMap params: Map<String, String>): AlbumListEnvelope

    @GET("rest/getAlbum")
    suspend fun album(@QueryMap params: Map<String, String>): AlbumDetalleEnvelope

    @GET("rest/search3")
    suspend fun buscar(@QueryMap params: Map<String, String>): BusquedaEnvelope

    @GET("rest/getStarred2")
    suspend fun favoritos(@QueryMap params: Map<String, String>): FavoritosEnvelope

    @GET("rest/star")
    suspend fun agregarFavorito(@QueryMap params: Map<String, String>): SubsonicEnvelope

    @GET("rest/unstar")
    suspend fun quitarFavorito(@QueryMap params: Map<String, String>): SubsonicEnvelope

    @GET("rest/getPlaylists")
    suspend fun playlists(@QueryMap params: Map<String, String>): PlaylistsEnvelope

    @GET("rest/getPlaylist")
    suspend fun playlist(@QueryMap params: Map<String, String>): PlaylistDetalleEnvelope

    @GET("rest/createPlaylist")
    suspend fun crearPlaylist(@QueryMap params: Map<String, String>): SubsonicEnvelope

    @GET("rest/updatePlaylist")
    suspend fun actualizarPlaylist(@QueryMap params: Map<String, String>): SubsonicEnvelope

    @GET("rest/deletePlaylist")
    suspend fun borrarPlaylist(@QueryMap params: Map<String, String>): SubsonicEnvelope
}

object SubsonicAuth {
    // Autenticación Subsonic: token = md5(contraseña + sal). La contraseña no viaja en claro.
    // Ya no se usa con el gateway (que usa su propio token); se deja por si se conecta a un Navidrome directo.
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
        .addInterceptor { chain ->
            val respuesta = chain.proceed(chain.request())
            // Un 401 en la API significa que el token ya no vale
            if (respuesta.code == 401 && chain.request().url.encodedPath.contains("/rest/")) {
                Sesion.sesionCaducada.value = true
            }
            respuesta
        }
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