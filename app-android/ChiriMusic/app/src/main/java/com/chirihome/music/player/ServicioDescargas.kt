package com.chirihome.music.player

import android.app.Notification
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler

@OptIn(UnstableApi::class)
class ServicioDescargas : DownloadService(Descargas.ID_NOTIFICACION) {

    override fun getDownloadManager(): DownloadManager = Descargas.administrador(this)

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification = Descargas.notificacion(this, downloads, notMetRequirements)
}