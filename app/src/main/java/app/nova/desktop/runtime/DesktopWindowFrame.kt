package app.nova.desktop.runtime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

data class DesktopWindowState(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val x: Float = 18f,
    val y: Float = 48f,
    val width: Float = 340f,
    val height: Float = 420f,
    val minimized: Boolean = false,
    val maximized: Boolean = false
)

/** NOVA-owned windows only. External Android Activities remain under Android task management. */
@Composable
fun DesktopWindowFrame(
    state: DesktopWindowState,
    layer: Float,
    onChange: (DesktopWindowState) -> Unit,
    onFocus: () -> Unit,
    onMinimize: () -> Unit,
    onMaximize: () -> Unit,
    onClose: () -> Unit,
    content: @Composable () -> Unit
) {
    if (state.minimized) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current.density
        val width = if (state.maximized) maxWidth else state.width.dp.coerceAtMost(maxWidth).coerceAtLeast(240.dp)
        val height = if (state.maximized) maxHeight else state.height.dp.coerceAtMost(maxHeight).coerceAtLeast(180.dp)
        val x = if (state.maximized) 0.dp else state.x.dp.coerceIn(0.dp, (maxWidth - width).coerceAtLeast(0.dp))
        val y = if (state.maximized) 0.dp else state.y.dp.coerceIn(0.dp, (maxHeight - height).coerceAtLeast(0.dp))
        Column(
            Modifier.offset(x, y).width(width).height(height).zIndex(layer)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Row(
                Modifier.fillMaxWidth().height(46.dp).background(Color(0xFF18233C))
                    .pointerInput(state.id, state.maximized) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            onFocus()
                            if (!state.maximized) onChange(state.copy(
                                x = (state.x + drag.x / density).coerceAtLeast(0f),
                                y = (state.y + drag.y / density).coerceAtLeast(0f)
                            ))
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(state.title, color = Color.White, style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).padding(start = 14.dp))
                TextButton(onClick = onMinimize) { Text("—", color = Color.White) }
                TextButton(onClick = onMaximize) { Text(if (state.maximized) "❐" else "□", color = Color.White) }
                TextButton(onClick = onClose) { Text("×", color = Color.White) }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(10.dp)) { content() }
            Box(
                Modifier.align(Alignment.End).size(24.dp)
                    .pointerInput(state.id, state.maximized) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            onFocus()
                            if (!state.maximized) onChange(state.copy(
                                width = (state.width + drag.x / density).coerceIn(240f, 900f),
                                height = (state.height + drag.y / density).coerceIn(180f, 1000f)
                            ))
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("◢", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
