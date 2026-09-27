package com.yaowanggu.trainer.refs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.InputStream

/**
 * 内置参考图浏览器：把长图切分成屏幕高度的分片用 LazyColumn 展示，
 * 用 BitmapRegionDecoder 按需解码，保证 3 万像素高的图也不会 OOM。
 */
class ChartViewer(private val asset: String) {
    private var decoder: BitmapRegionDecoder? = null
    var height: Int = 0; private set
    var width: Int = 0; private set

    fun open(context: android.content.Context) {
        if (decoder != null) return
        val ins: InputStream = context.assets.open(asset)
        decoder = BitmapRegionDecoder.newInstance(ins, false)
        width = decoder!!.width
        height = decoder!!.height
    }

    fun slice(context: android.content.Context, y0: Int, y1: Int): ImageBitmap? {
        if (decoder == null) open(context)
        val d = decoder ?: return null
        val top = y0.coerceIn(0, (height - 1).coerceAtLeast(0))
        val bottom = y1.coerceAtMost(height)
        if (bottom - top <= 0) return null
        return try {
            d.decodeRegion(Rect(0, top, width, bottom), BitmapFactory.Options()).asImageBitmap()
        } catch (e: Throwable) { null }
    }

    fun close() { decoder?.recycle(); decoder = null }
}

private const val SLICE_H = 1600

@Composable
fun ChartView(
    asset: String,
    scrollToY: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewer = remember(asset) { ChartViewer(asset) }
    DisposableEffect(asset) { onDispose { viewer.close() } }
    viewer.open(context)
    if (viewer.height == 0) {
        Box(modifier.fillMaxSize())
        return
    }
    val slices = remember(viewer.height) { (0 until viewer.height step SLICE_H).toList() }
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = (scrollToY / SLICE_H).coerceIn(0, (slices.size - 1).coerceAtLeast(0)),
    )
    LazyColumn(state = state, modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(slices.size) { i ->
            val y = slices[i]
            val bmp = remember(asset, y) {
                viewer.slice(context, y, minOf(y + SLICE_H, viewer.height))
            }
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth,
                )
            }
        }
    }
}


