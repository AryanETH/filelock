package com.geovault

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.decode.VideoFrameDecoder
import android.os.Build
import com.geovault.security.LocaleManager
import org.maplibre.android.MapLibre

class GeoVaultApp : Application(), ImageLoaderFactory {

    override fun attachBaseContext(base: Context) {
        val lang = LocaleManager.getLanguage(base)
        super.attachBaseContext(LocaleManager.getLocaleContext(base, lang))
    }

    override fun onCreate() {
        super.onCreate()
        try {
            // Global initialization of MapLibre
            MapLibre.getInstance(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
                add(VideoFrameDecoder.Factory())
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
}
