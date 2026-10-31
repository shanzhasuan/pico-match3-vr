package com.example.picomatch3vr.p3a7c9f1.ui.campaign

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.sin

/** Leaves the loading panel up until the actual board has been constructed. */
@Composable
fun StartupTransition() {
    var beginGame by remember { mutableStateOf(false) }
    var boardReady by remember { mutableStateOf(false) }
    var reveal by remember { mutableStateOf(0f) }
    var progress by remember { mutableStateOf(0f) }
    var elapsed by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        val started = SystemClock.uptimeMillis()
        // Give the lightweight loading panel a chance to render before SDK model creation.
        delay(100)
        beginGame = true
        while (isActive && reveal < 1f) {
            elapsed = (SystemClock.uptimeMillis() - started) / 1000f
            delay(16)
        }
    }
    LaunchedEffect(boardReady) {
        if (boardReady) {
            val started = SystemClock.uptimeMillis()
            while (reveal < 1f) {
                val t = ((SystemClock.uptimeMillis() - started) / 420f).coerceIn(0f, 1f)
                reveal = t * t * (3f - 2f * t)
                delay(16)
            }
        }
    }

    Box {
        if (beginGame) {
            GameplayStage(
                startupReveal = reveal,
                inputEnabled = boardReady && reveal >= 1f,
                onStartupProgress = { progress = it },
                onStartupReady = { boardReady = true },
            )
            // Both layers share the ViewModel created by GameplayStage.
            if (boardReady) {
                SnailClarityLayer()
                RedSpecialGlow()
            }
        }
        if (reveal < 1f) {
            SpatialView(
                modifier = Modifier,
                attachments = {
                    AttachmentPanel("startup_loading") {
                        LoadingPanel(progress, elapsed, 1f - reveal)
                    }
                },
                initial = { content, attachments ->
                    attachments.entity("startup_loading")?.let { panel ->
                        panel.components[TransformComponent::class.java]?.setPosition(
                            Vector3(0f, 1.55f, -1.35f),
                        )
                        content.addEntity(panel)
                    }
                },
                update = { _, attachments ->
                    attachments.entity("startup_loading")
                        ?.components?.get(TransformComponent::class.java)?.apply {
                            val size = (1f - reveal * 0.08f).coerceAtLeast(0.01f)
                            setScaleVector(Vector3(size, size, size))
                            setPosition(Vector3(0f, 1.55f + reveal * 0.04f, -1.35f))
                        }
                },
            )
        }
    }
}

@Composable
private fun LoadingPanel(progress: Float, elapsed: Float, opacity: Float) {
    val palette = listOf(
        Color(0xFFFF6939), Color(0xFFFFD34E), Color(0xFF63C9FF),
        Color(0xFF73DB83), Color(0xFFB78BFA),
    )
    Column(
        Modifier.width(480.dp)
            .graphicsLayer { alpha = opacity }
            .clip(RoundedCornerShape(44.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0xFF197CC8), Color(0xFF134983))),
            )
            .padding(horizontal = 42.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("空间消消乐", fontSize = 42.sp, color = Color.White)
        Spacer(Modifier.height(18.dp))
        Canvas(Modifier.size(310.dp, 70.dp)) {
            palette.forEachIndexed { index, color ->
                val bounce = sin(elapsed * 4.2f - index * 0.65f)
                val center = Offset(
                    size.width * (index + 0.5f) / palette.size,
                    size.height * 0.5f - bounce * size.height * 0.12f,
                )
                drawCircle(color, size.height * 0.23f, center)
                drawCircle(
                    Color.White.copy(alpha = 0.7f),
                    size.height * 0.055f,
                    center + Offset(-size.height * 0.07f, -size.height * 0.08f),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (opacity < 1f) "准备好了，出发！" else "正在唤醒小动物…",
            fontSize = 21.sp,
            color = Color(0xFFE7F4FF),
        )
        Spacer(Modifier.height(22.dp))
        Canvas(Modifier.fillMaxWidth().height(5.dp)) {
            drawRoundRect(Color.White.copy(alpha = 0.2f))
            drawRoundRect(
                Color(0xFFFFD34E),
                size = androidx.compose.ui.geometry.Size(size.width * progress.coerceIn(0f, 1f), size.height),
            )
        }
    }
}
