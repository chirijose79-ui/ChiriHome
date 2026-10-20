package com.chirihome.music.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FavoritosEnvelope(
    @SerialName("subsonic-response") val respuesta: FavoritosRespuesta
)

@Serializable
data class FavoritosRespuesta(
    val status: String,
    val starred2: ListaFavoritos? = null,
    val error: SubsonicError? = null
)

@Serializable
data class ListaFavoritos(val song: List<Cancion> = emptyList())