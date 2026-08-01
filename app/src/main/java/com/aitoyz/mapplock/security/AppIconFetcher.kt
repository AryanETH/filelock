package com.aitoyz.mapplock.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options

/**
 * Marker data class for app icon loading.
 */
data class AppIcon(val packageName: String)

/**
 * Custom Coil Fetcher to load app icons directly from package names.
 */
class AppIconFetcher(
    private val data: AppIcon,
    private val context: Context
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        return try {
            val packageManager = context.packageManager
            val icon = packageManager.getApplicationIcon(data.packageName)
            
            // Convert to BitmapDrawable to ensure Coil can handle it consistently
            val bitmap = if (icon is BitmapDrawable) {
                icon.bitmap
            } else {
                val b = Bitmap.createBitmap(
                    icon.intrinsicWidth.coerceAtLeast(1),
                    icon.intrinsicHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(b)
                icon.setBounds(0, 0, canvas.width, canvas.height)
                icon.draw(canvas)
                b
            }
            
            DrawableResult(
                drawable = BitmapDrawable(context.resources, bitmap),
                isSampled = false,
                dataSource = DataSource.MEMORY
            )
        } catch (e: Exception) {
            Log.e("AppIconFetcher", "Failed to load icon for ${data.packageName}", e)
            null
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<AppIcon> {
        override fun create(data: AppIcon, options: Options, imageLoader: ImageLoader): Fetcher {
            return AppIconFetcher(data, context)
        }
    }
}

class AppIconKeyer : coil.key.Keyer<AppIcon> {
    override fun key(data: AppIcon, options: Options): String {
        return "app_icon_${data.packageName}"
    }
}
