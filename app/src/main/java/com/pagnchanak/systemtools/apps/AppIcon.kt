package com.pagnchanak.systemtools.apps

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val iconCache = LruCache<String, ImageBitmap>(300)

@Composable
fun AppIcon(app: InstalledApp, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val context = LocalContext.current
    val icon by produceState(iconCache.get(app.packageName), app.packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    app.info.loadIcon(context.packageManager).toBitmap(128, 128).asImageBitmap()
                }.getOrNull()?.also { iconCache.put(app.packageName, it) }
            }
        }
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = modifier.size(size))
    } else {
        Box(modifier.size(size))
    }
}
