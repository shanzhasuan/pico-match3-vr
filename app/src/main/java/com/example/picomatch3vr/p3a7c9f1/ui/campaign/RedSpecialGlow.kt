package com.example.picomatch3vr.p3a7c9f1.ui.campaign

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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.picomatch3vr.p3a7c9f1.domain.campaign.SpecialTileKind
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.SpatialView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

/** Saturated red light, soft additive halos and a bounded number of orbiting sparks. */
@Composable
fun RedSpecialGlow() {
    val game: GameplayViewModel = viewModel()
    val state by game.state.collectAsState()
    val current by rememberUpdatedState(state)
    val anchor = remember { Entity().apply { setName("RedSpecialGlowAnchor") } }
    var resources by remember { mutableStateOf<RedGlowResources?>(null) }
    SpatialView(
        modifier = Modifier,
        initial = { content, _ ->
            content.addEntity(anchor)
        },
    )
    LaunchedEffect(Unit) {
        resources = withContext(Dispatchers.Default) { RedGlowResources() }
    }
    DisposableEffect(Unit) { onDispose { resources?.close() } }
    LaunchedEffect(resources) {
        val assets = resources ?: return@LaunchedEffect
        var board: Entity? = null
        while (isActive) {
            if (current.displayBoard.cells.any { it.special.isStraight() }) {
                if (board == null) {
                    var top = anchor
                    while (top.getParent() != null) top = top.getParent()!!
                    board = top.findEntity("Match3BoardRoot")
                        ?: anchor.scene?.queryEntity()?.firstOrNull { it.getName() == "Match3BoardRoot" }
                }
                val time = (System.nanoTime() / 1_000_000_000.0 % 1000).toFloat()
                var animatedCount = 0
                board?.getChildren()?.forEach { tile ->
                    val container = tile.getChildren().firstOrNull {
                        it.getName().endsWith("_prototype_container")
                    }
                    val animal = container?.getChildren()?.firstOrNull() ?: return@forEach
                    val marks = animal.getChildren().filter { it.getName().startsWith("stripe_") }
                    if (marks.isEmpty()) return@forEach
                    marks.filter { it.enabled }.forEach { it.enabled = false }
                    val horizontal = marks.first().getName().contains("HorizontalLine")
                    val glow = animal.findEntity("red_line_glow")
                        ?: assets.makeGlow(horizontal).also { animal.addChild(it) }
                    assets.animate(glow, time + animatedCount * 0.37f, animatedCount++ < 8)
                }
            }
            delay(25)
        }
    }
}

private fun SpecialTileKind.isStraight() =
    this == SpecialTileKind.HorizontalLine || this == SpecialTileKind.VerticalLine

private class RedGlowResources : AutoCloseable {
    private val coreMesh = MeshResource.createTorus(0.5f, 0.43f).apply { toGlobal() }
    private val haloMesh = MeshResource.createTorus(0.53f, 0.414f).apply { toGlobal() }
    private val sparkMesh = MeshResource.createSphere(0.5f).apply { toGlobal() }
    private val red = UnlitMaterial.create().apply {
        setBaseColor(Color4(1f, 0f, 0f, 1f))
        toGlobal()
    }
    private val hot = UnlitMaterial.create().apply {
        setBaseColor(Color4(1f, 0.37f, 0.08f, 1f))
        toGlobal()
    }
    private val nearHalo = UnlitMaterial.create(BlendingMode.ADD).apply {
        setBaseColor(Color4(2f, 0.025f, 0.045f, 0.25f))
        toGlobal()
    }
    private val farHalo = UnlitMaterial.create(BlendingMode.ADD).apply {
        setBaseColor(Color4(1.7f, 0.025f, 0.045f, 0.1f))
        toGlobal()
    }

    fun makeGlow(horizontal: Boolean): Entity = Entity().apply {
        setName("red_line_glow")
        components.set(TransformComponent())
        listOf(coreMesh to red, haloMesh to nearHalo, haloMesh to farHalo)
            .forEachIndexed { index, (mesh, material) ->
                addChild(ModelEntity(mesh, material).apply {
                    setName("red_glow_band_$index")
                    components.set(TransformComponent().apply {
                        setPosition(Vector3(0f, -0.025f, 0.13f - index * 0.04f))
                        val size = if (index == 2) 1.035f else 1f
                        setScaleVector(Vector3(size, 1f, size * 0.83f))
                        setEulerAngles(EulerAngles(90f, 0f, 0f))
                    })
                })
            }
        listOf(-1f, 1f).forEach { side ->
            listOf(-1f, 1f).forEach { wing ->
                addChild(ModelEntity(sparkMesh, hot).apply {
                    setName("red_direction_${side}_$wing")
                    components.set(TransformComponent().apply {
                        setScaleVector(Vector3(0.12f, 0.045f, 0.04f))
                        setPosition(if (horizontal) Vector3(side * 0.525f, -0.025f + wing * 0.026f, 0.18f)
                            else Vector3(wing * 0.026f, -0.025f + side * 0.44f, 0.18f))
                        setEulerAngles(EulerAngles(0f, 0f,
                            if (horizontal) -side * wing * 38f else 90f - side * wing * 38f))
                    })
                })
            }
        }
        repeat(6) { index ->
            addChild(ModelEntity(sparkMesh, if (index % 3 == 0) hot else red).apply {
                setName("red_orbit_spark_$index")
                components.set(TransformComponent().apply {
                    val angle = index * 1.0472f
                    setPosition(Vector3(cos(angle) * 0.535f, -0.025f + sin(angle) * 0.535f * 0.83f, 0.2f))
                    setScaleVector(Vector3(0.04f, 0.04f, 0.04f))
                })
            })
        }
    }

    fun animate(glow: Entity, time: Float, animateParticles: Boolean) {
        val breath = sin(time * 2.8f)
        glow.getChildren().forEach { child ->
            val name = child.getName()
            val transform = child.components[TransformComponent::class.java] ?: return@forEach
            if (name.startsWith("red_glow_band_")) {
                val band = name.last().digitToInt()
                val size = when (band) {
                    0 -> 1f + breath * 0.012f
                    1 -> 1f + breath * 0.025f
                    else -> 1.035f + breath * 0.025f
                }
                transform.setScaleVector(Vector3(size, 1f, size * 0.83f))
            } else if (name.startsWith("red_orbit_spark_")) {
                if (child.enabled != animateParticles) child.enabled = animateParticles
                if (!animateParticles) return@forEach
                val index = name.last().digitToInt()
                val angle = time * 0.9f + index * 1.0472f
                val radius = 0.535f + sin(time * 1.7f + index) * 0.009f
                val size = 0.035f + (sin(time * 2.4f + index) + 1f) * 0.009f
                transform.setPosition(Vector3(cos(angle) * radius, -0.025f + sin(angle) * radius * 0.83f, 0.2f))
                transform.setScaleVector(Vector3(size, size, size))
            }
        }
    }

    override fun close() {
        coreMesh.close()
        haloMesh.close()
        sparkMesh.close()
        red.close()
        hot.close()
        nearHalo.close()
        farHalo.close()
    }
}
