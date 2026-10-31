package com.example.picomatch3vr.p3a7c9f1.ui.campaign

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.picomatch3vr.p3a7c9f1.domain.campaign.CampaignMode
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.SpatialView
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/** Adds a readable route walker without changing board rules or collision targets. */
@Composable
fun SnailClarityLayer() {
    val model: GameplayViewModel = viewModel()
    val state by model.state.collectAsState()
    val anchor = remember { Entity().apply { setName("SnailAppearanceAnchor") } }
    SpatialView(modifier = Modifier.size(1.dp), initial = { content, _ ->
        content.addEntity(anchor)
    })
    LaunchedEffect(state.displayBoard, state.selectedCell, state.activeBurst, state.transientFallProgress) {
        if (state.session.level.mode != CampaignMode.Snails) return@LaunchedEffect
        // Wait for the existing board renderer to finish publishing its entities.
        withFrameNanos { }
        withFrameNanos { }
        repeat(8) {
            var top = anchor
            while (top.getParent() != null) top = top.getParent()!!
            val root = top.findEntity("Match3BoardRoot")
                ?: anchor.scene?.queryEntity()?.firstOrNull { it.getName() == "Match3BoardRoot" }
            if (root != null) {
                improveSnails(root)
                return@LaunchedEffect
            }
            delay(50)
        }
    }
}

private fun improveSnails(board: Entity) {
    board.getChildren().forEach { tile ->
        val marker = tile.getChildren().firstOrNull { it.getName() == "campaign_marker" }
        val snail = marker?.getChildren()?.firstOrNull { it.getName() == "snail" }
        val animal = tile.getChildren().firstOrNull { it.getName().endsWith("_prototype_container") }
        if (snail != null) {
            if (snail.findEntity("readable_snail") == null) {
                val mesh = snail.findEntity("snail_body")?.components?.get(ModelComponent::class.java)?.mesh
                    ?: return@forEach
                snail.getChildren().forEach { it.enabled = false }
                snail.components[TransformComponent::class.java]?.apply {
                    setPosition(Vector3(-0.07f, -0.22f, 0.65f))
                    setScaleVector(Vector3(1.02f, 1.02f, 1.02f))
                }
                snail.addChild(readableSnail(mesh))
            }
            // The animal remains visible and swappable above the walker.
            animal?.components?.get(TransformComponent::class.java)?.apply {
                val scale = scaleVector
                if (scale.x > 0.09f) {
                    setScaleVector(Vector3(scale.x * 0.56f, scale.y * 0.56f, scale.z * 0.56f))
                }
                if (scale.x > 0.001f) setPosition(Vector3(-0.031f, 0.055f, 0f))
            }
        }
        marker?.getChildren()?.firstOrNull { it.getName() == "route_line" }
            ?.components?.get(TransformComponent::class.java)?.apply {
                setScaleVector(Vector3(1.07f, 0.075f, 0.03f))
                setPosition(Vector3(0f, -0.52f, 0.12f))
            }
    }
}

private fun readableSnail(mesh: MeshResource): Entity {
    val peach = Color4(1f, 0.63f, 0.42f, 1f)
    val blue = Color4(0.09f, 0.67f, 1f, 1f)
    val red = Color4(0.95f, 0.12f, 0.20f, 1f)
    val pink = Color4(1f, 0.25f, 0.63f, 1f)
    val white = Color4(1f, 0.97f, 0.91f, 1f)
    val brown = Color4(0.24f, 0.065f, 0.025f, 1f)
    val root = Entity().apply {
        setName("readable_snail")
        components.set(TransformComponent())
    }
    // Materials are owned by the model components, and are released with this marker.
    val palette = listOf(peach, blue, red, pink, white, brown).associateWith {
        PhysicallyBasedMaterial.create().apply {
            setBaseColor(it)
            setMetallic(0f)
            setRoughness(0.38f)
        }
    }
    fun part(name: String, size: Vector3, color: Color4, p: Vector3, angle: Float = 0f) {
        root.addChild(ModelEntity(mesh, palette.getValue(color)).apply {
            setName(name)
            components.set(TransformComponent().apply {
                setPosition(p)
                setScaleVector(size)
                setEulerAngles(EulerAngles(0f, 0f, angle))
            })
        })
    }
    try {
        part("walker_foot", Vector3(0.96f, 0.24f, 0.30f), peach, Vector3(0f, -0.27f, 0.10f))
        part("walker_shell", Vector3(0.69f, 0.68f, 0.37f), blue, Vector3(-0.19f, 0.04f, 0.16f))
        part("walker_head", Vector3(0.30f, 0.37f, 0.29f), peach, Vector3(0.31f, -0.13f, 0.19f))
        // A continuous spiral reads clearly in stereo, unlike concentric flat discs.
        for (index in 0..32) {
            val angle = index * 0.235f
            val radius = 0.025f + angle * 0.031f
            part("walker_spiral_$index", Vector3(0.14f, 0.085f, 0.035f), red,
                Vector3(-0.19f + cos(angle) * radius, 0.04f + sin(angle) * radius, 0.354f),
                angle * 180f / kotlin.math.PI.toFloat() + 90f)
        }
        listOf(-1f, 1f).forEach { side ->
            val x = 0.30f + side * 0.105f
            part("walker_stalk_$side", Vector3(0.065f, 0.28f, 0.065f), pink, Vector3(x, 0.13f, 0.24f), -side * 15f)
            part("walker_eye_rim_$side", Vector3(0.22f, 0.23f, 0.13f), pink, Vector3(x + side * 0.025f, 0.29f, 0.26f))
            part("walker_eye_white_$side", Vector3(0.175f, 0.185f, 0.065f), white, Vector3(x + side * 0.025f, 0.29f, 0.335f))
            part("walker_pupil_$side", Vector3(0.10f, 0.12f, 0.035f), brown, Vector3(x + side * 0.025f + 0.016f, 0.29f, 0.375f))
            part("walker_glint_$side", Vector3(0.038f, 0.042f, 0.015f), white, Vector3(x + side * 0.025f, 0.32f, 0.398f))
        }
        part("walker_smile", Vector3(0.10f, 0.045f, 0.025f), brown, Vector3(0.33f, -0.16f, 0.35f))
        return root
    } finally {
        palette.values.forEach { it.close() }
    }
}
