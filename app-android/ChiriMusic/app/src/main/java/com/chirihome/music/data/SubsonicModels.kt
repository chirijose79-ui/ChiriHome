package com.chirihome.music.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubsonicEnvelope(
    @SerialName("subsonic-response") val respuesta: SubsonicRespuesta
)

@Serializable
data class SubsonicRespuesta(
    val status: String,
    val version: String? = null,
    val type: String? = null,
    val error: SubsonicError? = null
)

@Serializable
data class SubsonicError(
    val code: Int,
    val message: String? = null
)