package com.chirihome.music.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginGatewayPeticion(
    val usuario: String,
    val clave: String
)

@Serializable
data class LoginGatewayRespuesta(
    val token: String,
    val usuario: String,
    val rol: String,
    @SerialName("expira_en_segundos") val expiraEnSegundos: Long? = null
)