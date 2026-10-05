package com.example.theiptv

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.example.theiptv.data.CredentialsStore
import com.example.theiptv.data.FavoritesStore
import com.example.theiptv.data.XtreamRepository
import okio.Path.Companion.toOkioPath

class TheIptvApplication : Application(), SingletonImageLoader.Factory {
    val credentialsStore by lazy { CredentialsStore(this) }
    val favoritesStore by lazy { FavoritesStore(this) }
    val repository by lazy { XtreamRepository(this) }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}

fun Context.app(): TheIptvApplication = applicationContext as TheIptvApplication
