package com.chirihome.music.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.chirihome.music.data.Cancion
import com.chirihome.music.data.Sesion
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

enum class EstadoDescarga { NINGUNA, EN_CURSO, LISTA, FALLIDA }

@OptIn(UnstableApi::class)
object Descargas {
    const val CANAL = "descargas"
    const val ID_NOTIFICACION = 2

    private var proveedorBd: StandaloneDatabaseProvider? = null
    private var cache: SimpleCache? = null
    private var manager: DownloadManager? = null
    private var ayudanteNotificacion: DownloadNotificationHelper? = null

    // id de canción -> estado de Media3 (Download.STATE_*)
    private val estados = mutableStateOf<Map<String, Int>>(emptyMap())

    @Synchronized
    internal fun proveedorBd(contexto: Context): StandaloneDatabaseProvider {
        proveedorBd?.let { return it }
        val nuevo = StandaloneDatabaseProvider(contexto.applicationContext)
        proveedorBd = nuevo
        return nuevo
    }

    // Caché de descargas: NoOpCacheEvictor = nunca se borra nada solo
    @Synchronized
    internal fun cache(contexto: Context): SimpleCache {
        cache?.let { return it }
        val app = contexto.applicationContext
        val nueva = SimpleCache(
            File(app.filesDir, "descargas"),
            NoOpCacheEvictor(),
            proveedorBd(app)
        )
        cache = nueva
        return nueva
    }

    @Synchronized
    internal fun administrador(contexto: Context): DownloadManager {
        manager?.let { return it }
        val app = contexto.applicationContext
        crearCanal(app)
        val nuevo = DownloadManager(
            app,
            proveedorBd(app),
            cache(app),
            DefaultHttpDataSource.Factory(),
            Executors.newFixedThreadPool(2)
        )
        nuevo.maxParallelDownloads = 2
        nuevo.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?
            ) {
                estados.value = estados.value + (download.request.id to download.state)
            }

            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                estados.value = estados.value - download.request.id
            }
        })
        manager = nuevo
        cargarEstados(nuevo)
        return nuevo
    }

    private fun cargarEstados(m: DownloadManager) {
        try {
            m.downloadIndex.getDownloads().use { cursor ->
                val mapa = mutableMapOf<String, Int>()
                while (cursor.moveToNext()) {
                    val d = cursor.download
                    mapa[d.request.id] = d.state
                }
                estados.value = mapa
            }
        } catch (e: IOException) {
            android.util.Log.e("Descargas", "No se pudo leer el índice de descargas", e)
        }
    }

    private fun crearCanal(app: Context) {
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CANAL, "Descargas", NotificationManager.IMPORTANCE_LOW)
        )
    }

    internal fun notificacion(
        contexto: Context,
        descargas: List<Download>,
        noCumplidos: Int
    ): Notification {
        val app = contexto.applicationContext
        val ayudante = ayudanteNotificacion
            ?: DownloadNotificationHelper(app, CANAL).also { ayudanteNotificacion = it }
        return ayudante.buildProgressNotification(
            app,
            android.R.drawable.stat_sys_download,
            null,
            null,
            descargas,
            noCumplidos
        )
    }

    // Llamar desde la UI para que el estado de los botones esté al día
    fun iniciar(contexto: Context) {
        administrador(contexto)
    }

    fun estadoDe(id: String): EstadoDescarga = when (estados.value[id]) {
        Download.STATE_COMPLETED -> EstadoDescarga.LISTA
        Download.STATE_QUEUED,
        Download.STATE_DOWNLOADING,
        Download.STATE_RESTARTING,
        Download.STATE_STOPPED -> EstadoDescarga.EN_CURSO
        Download.STATE_FAILED -> EstadoDescarga.FALLIDA
        else -> EstadoDescarga.NINGUNA
    }

    fun descargar(contexto: Context, cancion: Cancion) {
        val app = contexto.applicationContext
        administrador(app)
        val peticion = DownloadRequest.Builder(cancion.id, Uri.parse(Sesion.urlStream(cancion.id)))
            .setCustomCacheKey(cancion.id)
            .build()
        DownloadService.sendAddDownload(app, ServicioDescargas::class.java, peticion, false)
    }

    fun quitar(contexto: Context, id: String) {
        val app = contexto.applicationContext
        administrador(app)
        DownloadService.sendRemoveDownload(app, ServicioDescargas::class.java, id, false)
    }
}