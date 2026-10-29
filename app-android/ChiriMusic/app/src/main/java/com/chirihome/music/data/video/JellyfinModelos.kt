package com.chirihome.music.data.video

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JfCredenciales(
    @SerialName("Username") val usuario: String,
    @SerialName("Pw") val clave: String
)

@Serializable
data class JfAutenticacion(
    @SerialName("AccessToken") val token: String,
    @SerialName("User") val usuario: JfUsuario
)

@Serializable
data class JfUsuario(
    @SerialName("Id") val id: String,
    @SerialName("Name") val nombre: String? = null
)

@Serializable
data class JfItems(
    @SerialName("Items") val items: List<JfItem> = emptyList(),
    @SerialName("TotalRecordCount") val total: Int = 0
)

@Serializable
data class JfItem(
    @SerialName("Id") val id: String,
    @SerialName("Name") val nombre: String? = null,
    @SerialName("ProductionYear") val anio: Int? = null,
    @SerialName("Overview") val descripcion: String? = null,
    // 10.000.000 ticks = 1 segundo
    @SerialName("RunTimeTicks") val duracionTicks: Long? = null,
    @SerialName("ImageTags") val etiquetasImagen: Map<String, String>? = null,
    // Solo vienen cuando se piden en el detalle
    @SerialName("MediaSources") val fuentes: List<JfFuente>? = null
)

@Serializable
data class JfFuente(
    @SerialName("Id") val id: String? = null,
    @SerialName("Container") val contenedor: String? = null,
    @SerialName("MediaStreams") val pistas: List<JfPista>? = null
)

@Serializable
data class JfPista(
    // Video, Audio o Subtitle
    @SerialName("Type") val tipo: String? = null,
    @SerialName("Codec") val codec: String? = null,
    @SerialName("Language") val idioma: String? = null,
    @SerialName("Height") val alto: Int? = null
)