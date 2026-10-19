package com.chirihome.music.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AlbumListEnvelope(
    @SerialName("subsonic-response") val respuesta: AlbumListRespuesta
)

@Serializable
data class AlbumListRespuesta(
    val status: String,
    val albumList2: AlbumList2? = null,
    val error: SubsonicError? = null
)

@Serializable
data class AlbumList2(val album: List<Album> = emptyList())

@Serializable
data class Album(
    val id: String,
    val name: String? = null,
    val artist: String? = null,
    val coverArt: String? = null,
    val year: Int? = null,
    val songCount: Int? = null
)

@Serializable
data class AlbumDetalleEnvelope(
    @SerialName("subsonic-response") val respuesta: AlbumDetalleRespuesta
)

@Serializable
data class AlbumDetalleRespuesta(
    val status: String,
    val album: AlbumDetalle? = null,
    val error: SubsonicError? = null
)

@Serializable
data class AlbumDetalle(
    val id: String,
    val name: String? = null,
    val artist: String? = null,
    val coverArt: String? = null,
    val year: Int? = null,
    val song: List<Cancion> = emptyList()
)

@Serializable
data class Cancion(
    val id: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val track: Int? = null,
    val duration: Int? = null
)