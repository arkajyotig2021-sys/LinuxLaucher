package app.nova.desktop.files

import android.content.Context
import android.app.ActivityManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale

private sealed interface PreviewContent {
    data class Text(val value: String) : PreviewContent
    data class BitmapContent(val bitmap: Bitmap, val label: String) : PreviewContent
    data class Media(val rows: List<Pair<String, String>>) : PreviewContent
    data class Unsupported(val mime: String, val bytes: Long) : PreviewContent
    data class Failure(val message: String) : PreviewContent
}

@Composable
fun FilePreviewDialog(file: DocumentFile, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var preview by remember(file.uri) { mutableStateOf<PreviewContent?>(null) }
    LaunchedEffect(file.uri) {
        preview = withContext(Dispatchers.IO) { loadPreview(context, file.uri, context.contentResolver.getType(file.uri), file.length()) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(file.name ?: "Preview") },
        text = {
            Box(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                when (val item = preview) {
                    null -> CircularProgressIndicator()
                    is PreviewContent.Text -> Text(item.value, modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()))
                    is PreviewContent.BitmapContent -> Column {
                        Text(item.label, style = MaterialTheme.typography.labelSmall)
                        Image(item.bitmap.asImageBitmap(), null, modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp), contentScale = ContentScale.Fit)
                    }
                    is PreviewContent.Media -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        item.rows.forEach { (label, value) ->
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    is PreviewContent.Unsupported -> Column {
                        Text("No built-in preview for this file type.")
                        Text("MIME: " + item.mime)
                        Text("Size: " + item.bytes + " bytes")
                    }
                    is PreviewContent.Failure -> Text(item.message, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private fun loadPreview(context: Context, uri: Uri, mime: String?, bytes: Long): PreviewContent {
    val type = mime ?: "application/octet-stream"
    return try {
        when {
            type.startsWith("text/") || type in setOf("application/json", "application/xml", "application/javascript") -> {
                val data = context.contentResolver.openInputStream(uri)?.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var remaining = 96 * 1024
                    while (remaining > 0) {
                        val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        remaining -= count
                    }
                    output.toByteArray()
                } ?: error("Cannot open this document.")
                PreviewContent.Text(data.toString(Charsets.UTF_8) + if (bytes > data.size) "\n\n— Preview truncated —" else "")
            }
            type.startsWith("image/") -> {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val memory = ActivityManager.MemoryInfo().also { info -> (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info) }
                val maxDimension = when { memory.availMem < 600L * 1024 * 1024 -> 800; memory.availMem < 1000L * 1024 * 1024 -> 1200; else -> 1600 }
                var sample = 1
                while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
                val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                    ?: error("Image decoding failed.")
                PreviewContent.BitmapContent(bitmap, "Image preview · ${bitmap.width} × ${bitmap.height}")
            }
            type == "application/pdf" -> {
                val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: error("Cannot open PDF.")
                descriptor.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (renderer.pageCount == 0) error("PDF has no pages.")
                        renderer.openPage(0).use { page ->
                            val width = 1200
                            val height = (width.toFloat() * page.height / page.width).toInt().coerceAtMost(1800)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            PreviewContent.BitmapContent(bitmap, "PDF · page 1 of ${renderer.pageCount}")
                        }
                    }
                }
            }
            type.startsWith("audio/") || type.startsWith("video/") -> {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    val rows = mutableListOf("Type" to type, "Size" to "${bytes} bytes")
                    duration?.let { rows += "Duration" to String.format(Locale.getDefault(), "%d:%02d", it / 60000, (it / 1000) % 60) }
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.let { rows += "Width" to it }
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.let { rows += "Height" to it }
                    PreviewContent.Media(rows)
                } finally { retriever.release() }
            }
            else -> PreviewContent.Unsupported(type, bytes)
        }
    } catch (error: Exception) {
        PreviewContent.Failure(error.message ?: "Preview could not be loaded.")
    }
}
