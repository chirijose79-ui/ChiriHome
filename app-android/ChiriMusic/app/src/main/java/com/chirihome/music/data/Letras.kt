package com.chirihome.music.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LetraEnvelope(
    @SerialName("subsonic-response") val respuesta: LetraRespuesta
)

@Serializable
data class LetraRespuesta(
    val status: String,
    val lyricsList: ListaLetras? = null,
    val error: SubsonicError? = null
)

@Serializable
data class ListaLetras(val structuredLyrics: List<LetraEstructurada> = emptyList())

@Serializable
data class LetraEstructurada(
    val lang: String? = null,
    val synced: Boolean = false,
    val line: List<LineaLetra> = emptyList()
)

@Serializable
data class LineaLetra(
    val value: String = "",
    // Milisegundos desde el inicio de la canción; solo viene en letras sincronizadas
    val start: Double? = null
)