package com.chirihome.music.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.net.toUri
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
import com.chirihome.music.data.DescargasLocal
import com.chirihome.music.data.Sesion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

enum class EstadoDescarga { NINGUNA, EN_CURSO, LISTA, FALLIDA }

@OptIn(UnstableApi::class)
object Descargas {
    const val CANAL = "descargas"
    const val ID_NOTIFICACION = 2

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var sondeo: Job? = null

    private var proveedorBd: StandaloneDatabaseProvider? = null
    private var cache: SimpleCache? = null
    private var manager: DownloadManager? = null
    private var ayudanteNotificacion: DownloadNotificationHelper? = null

    // id de canción -> estado de Media3 (Download.STATE_*)
    private val estados = mutableStateOf<Map<String, Int>>(emptyMap())

    // id de canción -> porcentaje descargado (solo mientras descarga)
    private val progreso = mutableStateOf<Map<String, Int>>(emptyMap())

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
        DescargasLocal.init(app)
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
                estados.value += download.request.id to download.state
                if (download.state == Download.STATE_DOWNLOADING ||
                    download.state == Download.STATE_QUEUED
                ) {
                    asegurarSondeo(downloadManager)
                }
            }

            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                val id = download.request.id
                estados.value -= id
                progreso.value -= id
                DescargasLocal.quitar(id)
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

    // Media3 no avisa del avance, así que se consulta cada segundo mientras haya descargas activas
    private fun asegurarSondeo(m: DownloadManager) {
        if (sondeo?.isActive == true) return
        sondeo = scope.launch {
            while (true) {
                val actuales = m.currentDownloads
                progreso.value = actuales
                    .filter { it.state == Download.STATE_DOWNLOADING }
                    .associate { it.request.id to it.percentDownloaded.toInt().coerceAtLeast(0) }
                val activas = actuales.any {
                    it.state == Download.STATE_DOWNLOADING ||
                            it.state == Download.STATE_QUEUED ||
                            it.state == Download.STATE_RESTARTING
                }
                if (!activas) break
                delay(1000.milliseconds)
            }
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

    // Llamar al arrancar la app para que todo esté al día, incluso sin red
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

    fun progresoDe(id: String): Int? = progreso.value[id]

    fun espacioUsado(contexto: Context): Long = cache(contexto).cacheSpace

    fun descargar(contexto: Context, cancion: Cancion) {
        val app = contexto.applicationContext
        administrador(app)
        DescargasLocal.agregar(cancion)
        val peticion = DownloadRequest.Builder(cancion.id, Sesion.urlStream(cancion.id).toUri())
            .setCustomCacheKey(cancion.id)
            .build()
        DownloadService.sendAddDownload(app, ServicioDescargas::class.java, peticion, false)
    }

    // Encola las que no estén ya descargadas o en curso
    fun descargarTodas(contexto: Context, canciones: List<Cancion>) {
        canciones.forEach {
            val estado = estadoDe(it.id)
            if (estado != EstadoDescarga.LISTA && estado != EstadoDescarga.EN_CURSO) {
                descargar(contexto, it)
            }
        }
    }

    fun quitar(contexto: Context, id: String) {
        val app = contexto.applicationContext
        administrador(app)
        DownloadService.sendRemoveDownload(app, ServicioDescargas::class.java, id, false)
    }

    fun quitarVarias(contexto: Context, ids: List<String>) {
        ids.forEach { quitar(contexto, it) }
    }

    fun borrarTodo(contexto: Context) {
        val app = contexto.applicationContext
        administrador(app)
        DownloadService.sendRemoveAllDownloads(app, ServicioDescargas::class.java, false)
    }
}