package com.cunicella.fichaje.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Deslizar hasta el final para confirmar: evita terminar el turno por un toque sin querer. */
@Composable
internal fun SlideToConfirm(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onConfirm: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val thumb = 44.dp
    val pad = 4.dp
    BoxWithConstraints(modifier.height(52.dp).clip(RoundedCornerShape(12.dp)).background(cs.primary.copy(alpha = 0.22f))) {
        val maxPx = with(LocalDensity.current) { (maxWidth - thumb - pad * 2).toPx() }.coerceAtLeast(1f)
        val x = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val progress = (x.value / maxPx).coerceIn(0f, 1f)
        Text(
            text, Modifier.align(Alignment.Center).padding(start = thumb / 2).alpha(1f - progress),
            color = cs.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        )
        Box(
            Modifier
                .offset { IntOffset(x.value.roundToInt(), 0) }
                .padding(pad)
                .size(thumb)
                .clip(CircleShape)
                .background(if (enabled) cs.primary else cs.surfaceVariant)
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                    state = rememberDraggableState { delta -> scope.launch { x.snapTo((x.value + delta).coerceIn(0f, maxPx)) } },
                    onDragStopped = {
                        if (x.value >= maxPx * 0.85f) {
                            x.animateTo(maxPx)
                            onConfirm()
                            x.snapTo(0f)
                        } else x.animateTo(0f)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = text, tint = cs.onPrimary, modifier = Modifier.fillMaxSize().padding(8.dp))
        }
    }
}
