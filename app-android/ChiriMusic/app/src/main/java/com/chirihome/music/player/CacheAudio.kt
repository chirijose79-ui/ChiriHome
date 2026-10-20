package com.chirihome.music.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
object CacheAudio {
    private const val LIMITE_BYTES = 500L * 1024 * 1024

    private var cache: SimpleCache? = null

    @Synchronized
    fun obtener(contexto: Context): SimpleCache {
        cache?.let { return it }
        val app = contexto.applicationContext
        val nueva = SimpleCache(
            File(app.cacheDir, "audio"),
            LeastRecentlyUsedCacheEvictor(LIMITE_BYTES),
            StandaloneDatabaseProvider(app)
        )
        cache = nueva
        return nueva
    }
}