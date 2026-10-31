package com.example.picomatch3vr.p3a7c9f1.ui.campaign

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.picomatch3vr.p3a7c9f1.domain.campaign.SpecialTileKind
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.sin

/** Replaces the old face stripes with a hollow blue-white outline and direction tips. */
@Composable
fun StraightSpecialPresentation() {
    val model: GameplayViewModel = viewModel()
    val state by model.state.collectAsState()
    val latest by rememberUpdatedState(state)
    val anchor = remember { Entity().apply { setName("StraightSpecialAppearanceAnchor") } }
    var assets by remember { mutableStateOf<StraightOutlineAssets?>(null) }
    var root by remember { mutableStateOf<Entity?>(null) }
    val lineKinds = setOf(SpecialTileKind.HorizontalLine, SpecialTileKind.VerticalLine)
    val hasLines = state.displayBoard.cells.any { it.special in lineKinds }
    val selected = state.selectedCell?.let { state.displayBoard.tileAt(it).special }
    val caption = when {
        state.hammerArmed -> "锤子已选中：点动物敲碎（消耗一次道具和一步）"
        selected == SpecialTileKind.HorizontalLine -> "横向直线特效：再次参与消除，清除整行"
        selected == SpecialTileKind.VerticalLine -> "竖向直线特效：再次参与消除，清除整列"
        hasLines -> "蓝白光圈是四连特效：再次消除时，横扫一行或竖扫一列"
        state.showGuide -> "锤子：点按钮后再点动物，可敲碎一格，消耗一步"
        else -> ""
    }
    SpatialView(
        modifier = Modifier,
        attachments = {
                AttachmentPanel("straight_help") {
                    Box(Modifier.background(Color.Transparent).padding(4.dp)) {
                        Text(caption, fontSize = 18.sp, color = Color(0xFF133E61))
                    }
                }
        },
        initial = { content, attachments ->
            content.addEntity(anchor)
            attachments.entity("straight_help")?.let {
                it.components[TransformComponent::class.java]?.setPosition(Vector3(0f, 1.89f, -1.79f))
                content.addEntity(it)
            }
        },
        update = { content, attachments ->
            attachments.entity("straight_help")?.let {
                it.components[TransformComponent::class.java]?.setPosition(Vector3(0f, 1.89f, -1.79f))
                if (it !in content.entities) content.addEntity(it)
            }
        },
    )
    LaunchedEffect(Unit) {
        // Mesh generation stays off the UI thread, including the first four-match.
        assets = withContext(Dispatchers.Default) { StraightOutlineAssets() }
    }
    DisposableEffect(Unit) {
        onDispose { assets?.close() }
    }
    LaunchedEffect(assets) {
        val palette = assets ?: return@LaunchedEffect
        while (isActive) {
            val board = root ?: run {
                var top = anchor
                while (top.getParent() != null) top = top.getParent()!!
                (top.findEntity("Match3BoardRoot")
                    ?: anchor.scene?.queryEntity()?.firstOrNull { it.getName() == "Match3BoardRoot" })
                    .also { root = it }
            }
            if (board != null && latest.displayBoard.cells.any { it.special in lineKinds }) {
                val phase = (System.nanoTime() / 1_000_000_000.0 * 3.0).toFloat()
                board.getChildren().forEach { tile ->
                    val container = tile.getChildren().firstOrNull { it.getName().endsWith("_prototype_container") }
                    val animal = container?.getChildren()?.firstOrNull() ?: return@forEach
                    val stripes = animal.getChildren().filter { it.getName().startsWith("stripe_") }
                    if (stripes.isEmpty()) return@forEach
                    stripes.forEach { it.enabled = false }
                    val horizontal = stripes.first().getName().contains("HorizontalLine")
                    val outline = animal.findEntity("straight_outline")
                        ?: palette.createOutline(horizontal).also { animal.addChild(it) }
                    val scale = 1f + sin(phase) * 0.015f
                    outline.components[TransformComponent::class.java]?.setScaleVector(Vector3(scale, scale, 1f))
                }
            }
            delay(50)
        }
    }
}

private class StraightOutlineAssets : AutoCloseable {
    private val outer = MeshResource.createTorus(0.49f, 0.466f)
    private val inner = MeshResource.createTorus(0.478f, 0.466f)
    private val tip = MeshResource.createSphere(0.5f)
    private val blue = UnlitMaterial.create().apply { setBaseColor(Color4(0.12f, 0.72f, 1f, 1f)) }
    private val white = UnlitMaterial.create().apply { setBaseColor(Color4(0.83f, 0.98f, 1f, 1f)) }

    fun createOutline(horizontal: Boolean): Entity = Entity().apply {
        setName("straight_outline")
        components.set(TransformComponent())
        listOf(outer to blue, inner to white).forEachIndexed { index, (mesh, material) ->
            addChild(ModelEntity(mesh, material).apply {
                setName("straight_rim_$index")
                components.set(TransformComponent().apply {
                    setPosition(Vector3(0f, -0.025f, 0.12f + index * 0.006f))
                    setScaleVector(Vector3(1f, 1f, 0.83f))
                    setEulerAngles(EulerAngles(90f, 0f, 0f))
                })
            })
        }
        // Two small chevrons sit outside the face and communicate the clearing axis.
        listOf(-1f, 1f).forEach { side ->
            listOf(-1f, 1f).forEach { wing ->
                addChild(ModelEntity(tip, white).apply {
                    setName("straight_direction_${side}_$wing")
                    components.set(TransformComponent().apply {
                        setScaleVector(Vector3(0.11f, 0.038f, 0.035f))
                        setPosition(if (horizontal) Vector3(side * 0.52f, -0.025f + wing * 0.024f, 0.15f)
                            else Vector3(wing * 0.024f, -0.025f + side * 0.43f, 0.15f))
                        setEulerAngles(EulerAngles(0f, 0f, if (horizontal) -side * wing * 38f else 90f - side * wing * 38f))
                    })
                })
            }
        }
    }

    override fun close() {
        outer.close()
        inner.close()
        tip.close()
        blue.close()
        white.close()
    }
}
