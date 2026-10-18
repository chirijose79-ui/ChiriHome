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