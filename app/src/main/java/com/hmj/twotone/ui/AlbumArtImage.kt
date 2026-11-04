package com.hmj.twotone.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hmj.twotone.music.AlbumArt
import com.hmj.twotone.music.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The album cover as a 1-bit dithered picture in the two UI colours, drawn
 * with hard pixel edges. Without a cover, a plain dither pattern stands in.
 */
@Composable
fun AlbumArtImage(track: Track?, size: Dp, dots: Int = 64) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val bitmap by produceState<ImageBitmap?>(null, track?.albumId, p, dots) {
        value = if (track == null) null else withContext(Dispatchers.IO) {
            AlbumArt.dithered(ctx, track, dots, p.ink.toArgb(), p.paper.toArgb())?.asImageBitmap()
        }
    }
    Box(Modifier.size(size).border(2.dp, p.ink)) {
        val b = bitmap
        if (b != null) {
            Image(
                bitmap = b,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.None
            )
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val c = 3.dp.toPx()
                var y = 0f; var r = 0
                while (y < this.size.height) {
                    var x = if (r % 2 == 0) 0f else c
                    while (x < this.size.width) { drawRect(p.ink, Offset(x, y), Size(c / 2, c / 2)); x += 2 * c }
                    y += c; r++
                }
            }
        }
    }
}


/** Any picture (another app's cover art) dithered to the two UI colours. */
@Composable
fun DitheredPicture(src: android.graphics.Bitmap?, size: Dp, dots: Int, key: Any?) {
    val p = LocalPalette.current
    val bitmap by produceState<ImageBitmap?>(null, key, p, dots) {
        value = if (src == null) null else withContext(Dispatchers.Default) {
            try { AlbumArt.dither(src, dots, p.ink.toArgb(), p.paper.toArgb()).asImageBitmap() } catch (e: Exception) { null }
        }
    }
    Box(Modifier.size(size).border(2.dp, p.ink)) {
        val b = bitmap
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.None)
    }
}
