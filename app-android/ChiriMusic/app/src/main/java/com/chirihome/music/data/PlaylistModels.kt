package com.chirihome.music.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlaylistsEnvelope(
    @SerialName("subsonic-response") val respuesta: PlaylistsRespuesta
)

@Serializable
data class PlaylistsRespuesta(
    val status: String,
    val playlists: ListaPlaylists? = null,
    val error: SubsonicError? = null
)

@Serializable
data class ListaPlaylists(val playlist: List<Playlist> = emptyList())

@Serializable
data class Playlist(
    val id: String,
    val name: String? = null,
    val songCount: Int? = null,
    val owner: String? = null
)

@Serializable
data class PlaylistDetalleEnvelope(
    @SerialName("subsonic-response") val respuesta: PlaylistDetalleRespuesta
)

@Serializable
data class PlaylistDetalleRespuesta(
    val status: String,
    val playlist: PlaylistDetalle? = null,
    val error: SubsonicError? = null
)

@Serializable
data class PlaylistDetalle(
    val id: String,
    val name: String? = null,
    val entry: List<Cancion> = emptyList()
)