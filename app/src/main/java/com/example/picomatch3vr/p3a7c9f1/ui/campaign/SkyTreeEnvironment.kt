package com.example.picomatch3vr.p3a7c9f1.ui.campaign

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

/** Original layered tree artwork, with texture uploads kept off the UI thread. */
internal class SkyTreeEnvironment {
    val root = Entity().apply {
        setName("SkyTreeEnvironment")
        components.set(TransformComponent())
    }
    private val textures = mutableListOf<TextureResource>()
    private val materials = mutableListOf<UnlitMaterial>()
    private var plane: MeshResource? = null
    private var closed = false

    suspend fun load() {
        val pendingTextures = mutableListOf<TextureResource>()
        val pendingMaterials = mutableListOf<UnlitMaterial>()
        var published = false
        try {
            val artwork = withContext(Dispatchers.IO) {
                listOf("sky", "forest-complete", "tree", "clouds").associateWith { name ->
                    val texture = TextureResource("sky-tree/$name.png")
                    pendingTextures += texture
                    texture.toGlobal()
                    UnlitMaterial.create().apply {
                        pendingMaterials += this
                        setBaseColorTexture(texture)
                        setCullingMode(MaterialCullingMode.NONE)
                        if (name != "sky") {
                            setBlendingMode(BlendingMode.TRANSPARENT)
                            setDepthWrite(false)
                        }
                        toGlobal()
                    }
                }
            }
            if (closed) return
            textures += pendingTextures
            materials += pendingMaterials
            published = true
            plane = MeshResource.createPlane(1f, 1f).apply { toGlobal() }
            // Equal-height gradient segments join seamlessly around the player.
            repeat(16) { index ->
                val angle = index * Math.PI * 2 / 16
                sprite(
                    "sky_$index", artwork.getValue("sky"),
                    Vector3((sin(angle) * 12).toFloat(), 3f, (-cos(angle) * 12).toFloat()),
                    4.79f, 24f, (-angle * 180 / Math.PI).toFloat(),
                )
            }
            cap("zenith", 15f, Color4(29f / 255f, 93f / 255f, 126f / 255f, 1f))
            cap("nadir", -9f, Color4(67f / 255f, 127f / 255f, 118f / 255f, 1f))
            // Preserve the village at its original height and extend its central trunk upward.
            sprite("forest_village_complete", artwork.getValue("forest-complete"), Vector3(0f, 4.472f, -6.4f), 13.2f, 19.36f)
            sprite("left_sky_tree", artwork.getValue("tree"), Vector3(-3.6f, 4.3f, -5.3f), 1.9f, 8.84f)
            sprite("right_sky_tree", artwork.getValue("tree"), Vector3(4.5f, 4.7f, -6.8f), 1.6f, 7.44f)
            sprite("cloud_sea", artwork.getValue("clouds"), Vector3(0f, -0.12f, -4.6f), 12.4f, 2.61f)
            sprite("high_cloud_left", artwork.getValue("clouds"), Vector3(-5.6f, 5.6f, -9f), 9.2f, 1.94f)
            sprite("high_cloud_right", artwork.getValue("clouds"), Vector3(5.6f, 6.2f, -10f), 8.4f, 1.77f)
        } finally {
            if (!published) withContext(NonCancellable + Dispatchers.Main) {
                pendingMaterials.forEach { it.close() }
                pendingTextures.forEach { it.close() }
            }
        }
    }

    private fun cap(name: String, y: Float, color: Color4) {
        val material = UnlitMaterial.create().apply {
            materials += this
            setBaseColor(color)
            setCullingMode(MaterialCullingMode.NONE)
            toGlobal()
        }
        root.addChild(ModelEntity(plane!!, material).apply {
            setName(name)
            components.set(TransformComponent().apply {
                setPosition(Vector3(0f, y, 0f))
                setScaleVector(Vector3(28f, 28f, 1f))
                setEulerAngles(EulerAngles(90f, 0f, 0f))
            })
        })
    }

    private fun sprite(name: String, material: UnlitMaterial, position: Vector3, width: Float, height: Float, yaw: Float = 0f) {
        root.addChild(ModelEntity(plane!!, material).apply {
            setName(name)
            components.set(TransformComponent().apply {
                setPosition(position)
                setScaleVector(Vector3(width, height, 1f))
                setEulerAngles(EulerAngles(0f, yaw, 0f))
            })
        })
    }

    fun close() {
        if (closed) return
        closed = true
        root.removeAllChildren()
        materials.forEach { it.close() }
        textures.forEach { it.close() }
        plane?.close()
        materials.clear()
        textures.clear()
        plane = null
    }
}
