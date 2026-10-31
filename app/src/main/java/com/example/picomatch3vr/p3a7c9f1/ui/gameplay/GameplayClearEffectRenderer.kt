package com.example.picomatch3vr.p3a7c9f1.ui.gameplay

import com.example.picomatch3vr.p3a7c9f1.ui.game.*

import android.os.Handler
import android.os.Looper
import com.example.picomatch3vr.p3a7c9f1.domain.model.GridPoint
import com.example.picomatch3vr.p3a7c9f1.domain.model.SpecialTileKind
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Reuses existing effect nodes; frame updates stay on the main thread. */
internal class GameplayClearEffectRenderer {
    internal data class Target(
        val point: GridPoint,
        val animal: Entity,
        val sparkle: ModelEntity,
        val specialEffect: ModelEntity,
        val auxiliaryEffect: ModelEntity,
    )

    private data class FadeBinding(
        val model: ModelComponent,
        val index: Int,
        val original: Material,
        val fade: PhysicallyBasedMaterial,
    )

    private data class ClearingAnimal(
        val target: Target,
        val scale: Vector3,
        val delay: Float,
        val materials: List<FadeBinding>,
    )

    private data class Sprite(
        val entity: ModelEntity,
        val frames: List<UnlitMaterial>,
        val size: Vector3,
        val delay: Float,
        val roll: Float = 0f,
        var frame: Int = -1,
    )

    // The flat launch Activity's Choreographer can be throttled in Full Space.
    private val frameHandler = Handler(Looper.getMainLooper())
    private val frameTick = Runnable { doFrame(System.nanoTime()) }
    private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val textures = mutableListOf<TextureResource>()
    private val sequences = mutableMapOf<String, List<UnlitMaterial>>()
    private var plane: MeshResource? = null
    private var token: Long? = null
    private var startedAt = 0L
    private var animals = emptyList<ClearingAnimal>()
    private var sprites = emptyList<Sprite>()
    private var closed = false

    init {
        preloadSequences()
    }

    fun prepare(burst: Match3FlashBurst?) {
        if (!closed && token != burst?.token) stop()
    }

    fun update(burst: Match3FlashBurst?, targets: List<Target>, boardWidth: Int) {
        if (closed || token == burst?.token) return
        stop()
        if (burst == null) return
        token = burst.token
        if (plane == null) plane = MeshResource.createPlane(1f, 1f).apply { toGlobal() }
        // Input and frame callbacks must never synchronously upload textures.
        val normalFrames = sequences["normal"].orEmpty()
        val byPoint = targets.associateBy(Target::point)
        animals = burst.points.mapNotNull { point ->
            val target = byPoint[point] ?: return@mapNotNull null
            val delay = GameplayAnimationTiming.clearDelay(point, burst, boardWidth)
            ClearingAnimal(
                target,
                target.animal.components[TransformComponent::class.java]!!.scaleVector,
                delay,
                fadeMaterials(target.animal),
            )
        }
        val ordinary = animals.map { animal ->
            Sprite(
                animal.target.sparkle, normalFrames,
                Vector3(TILE_SIZE * 90f / 70f, TILE_SIZE * 90f / 70f, 1f),
                animal.delay,
            )
        }
        val usedSlots = mutableMapOf<GridPoint, Int>()
        val special = burst.activatedSpecials.flatMap { activated ->
            val target = byPoint[activated.point] ?: return@flatMap emptyList()
            val delay = activated.delaySeconds

            fun line(point: GridPoint, vertical: Boolean): Sprite? {
                val slot = byPoint[point] ?: return null
                val count = usedSlots.getOrDefault(point, 0)
                if (count >= 2) return null
                usedSlots[point] = count + 1
                val entity = if (count == 0) slot.specialEffect else slot.auxiliaryEffect
                val width = TILE_SIZE * boardWidth * 1.2f
                return Sprite(entity, sequences["line"].orEmpty(),
                    Vector3(width, width * 160f / 1499f, 1f), delay,
                    if (vertical) 90f else 0f)
            }
            val immediate = burst.activatedSpecials.filter { it.delaySeconds == 0f }
            val cross = immediate.size == 2 && immediate.all {
                it.kind == SpecialTileKind.Bomb || it.kind == SpecialTileKind.HorizontalLine || it.kind == SpecialTileKind.VerticalLine
            } && immediate.any { it.kind != SpecialTileKind.Bomb }
            when {
                cross -> {
                    if (activated != immediate.first()) emptyList()
                    else {
                        val radius = if (immediate.any { it.kind == SpecialTileKind.Bomb }) 1 else 0
                        (-radius..radius).flatMap { offset ->
                            listOfNotNull(
                                line(GridPoint(activated.point.row + offset, activated.point.column), false),
                                line(GridPoint(activated.point.row, activated.point.column + offset), true),
                            )
                        }
                    }
                }
                activated.kind == SpecialTileKind.HorizontalLine || activated.kind == SpecialTileKind.VerticalLine ->
                    listOfNotNull(line(activated.point, activated.kind == SpecialTileKind.VerticalLine))
                activated.kind == SpecialTileKind.Bomb || activated.kind == SpecialTileKind.ColorBomb -> {
                    val count = usedSlots.getOrDefault(activated.point, 0)
                    if (count >= 2) emptyList() else {
                        usedSlots[activated.point] = count + 1
                        val entity = if (count == 0) target.specialEffect else target.auxiliaryEffect
                        val size = if (activated.kind == SpecialTileKind.ColorBomb) TILE_SIZE * boardWidth * 1.35f else TILE_SIZE * 3.6f
                        listOf(Sprite(entity, sequences["bomb"].orEmpty(),
                            Vector3(size, size * 356f / 339f, 1f), delay))
                    }
                }
                else -> emptyList()
            }
        }

        sprites = ordinary + special
        startedAt = 0L
        frameHandler.post(frameTick)
    }

    private fun doFrame(frameTimeNanos: Long) {
        if (closed || token == null) return
        if (startedAt == 0L) startedAt = frameTimeNanos
        val age = (frameTimeNanos - startedAt) / 1_000_000_000f
        for (animal in animals) {
            val progress = ((age - animal.delay) / GameplayAnimationTiming.ANIMAL_CLEAR_SECONDS).coerceIn(0f, 1f)
            val eased = progress * progress * (3f - 2f * progress)
            val factor = (1f - eased).coerceAtLeast(0.0001f)
            animal.target.animal.components[TransformComponent::class.java]?.apply {
                setScaleVector(Vector3(animal.scale.x * factor, animal.scale.y * factor, animal.scale.z * factor))
            }
            animal.materials.forEach { it.fade.setOpacity(1f - eased) }
        }
        var active = animals.any { age < it.delay + GameplayAnimationTiming.ANIMAL_CLEAR_SECONDS }
        for (sprite in sprites) {
            val frame = ((age - sprite.delay) * GameplayAnimationTiming.SPRITE_FPS).toInt()
            val visible = age >= sprite.delay && frame in sprite.frames.indices
            val model = sprite.entity.components[ModelComponent::class.java] ?: continue
            model.isVisible = visible
            if (!visible) {
                if (age < sprite.delay) active = true
                continue
            }
            active = true
            if (sprite.frame != frame) {
                model.mesh = plane!!
                if (model.materials.size == 0) model.materials.add(sprite.frames[frame])
                else model.materials[0] = sprite.frames[frame]
                sprite.frame = frame
                sprite.entity.components[TransformComponent::class.java]?.apply {
                    setPosition(Vector3(0f, 0f, 0.09f))
                    setScaleVector(sprite.size)
                    setEulerAngles(EulerAngles(0f, 0f, sprite.roll))
                }
            }
        }
        if (active) frameHandler.postDelayed(frameTick, GameplayAnimationTiming.UPDATE_INTERVAL_MS)
    }

    private fun preloadSequences() {
        loadScope.launch {
            for ((name, count) in listOf("normal" to 16, "line" to 10, "bomb" to 22)) {
                val loadedTextures = mutableListOf<TextureResource>()
                val loadedMaterials = mutableListOf<UnlitMaterial>()
                var published = false
                try {
                    repeat(count) { index ->
                        coroutineContext.ensureActive()
                        val texture = TextureResource("match3-effects/$name/${index.toString().padStart(2, '0')}.png")
                        loadedTextures += texture
                        texture.toGlobal()
                        val material = UnlitMaterial.create()
                        loadedMaterials += material
                        material.apply {
                            setBlendingMode(BlendingMode.TRANSPARENT)
                            setBaseColor(Color4(1f, 1f, 1f, 1f))
                            setBaseColorTexture(texture)
                            setCullingMode(MaterialCullingMode.NONE)
                            setDepthWrite(false)
                            toGlobal()
                        }
                    }
                    // Publish or release even if disposal races a native upload.
                    withContext(NonCancellable + Dispatchers.Main.immediate) {
                        if (!closed) {
                            textures += loadedTextures
                            sequences[name] = loadedMaterials
                            published = true
                        }
                    }
                } finally {
                    if (!published) {
                        loadedMaterials.forEach { it.close() }
                        loadedTextures.forEach { it.close() }
                    }
                }
            }
        }
    }

    private fun fadeMaterials(root: Entity): List<FadeBinding> {
        val result = mutableListOf<FadeBinding>()
        fun visit(entity: Entity) {
            entity.components[ModelComponent::class.java]?.let { model ->
                for (index in 0 until model.materials.size) {
                    val original = model.materials[index]
                    if (original is PhysicallyBasedMaterial) {
                        val fade = PhysicallyBasedMaterial.create().apply {
                            setBaseColor(original.getBaseColor())
                            setMetallic(original.getMetallic())
                            setRoughness(original.getRoughness())
                            setBlendingMode(BlendingMode.FADE)
                            setDepthWrite(false)
                            toGlobal()
                        }
                        model.materials[index] = fade
                        result += FadeBinding(model, index, original, fade)
                    }
                }
            }
            entity.getChildren().forEach(::visit)
        }
        visit(root)
        return result
    }

    private fun stop() {
        frameHandler.removeCallbacks(frameTick)
        sprites.forEach { it.entity.components[ModelComponent::class.java]?.isVisible = false }
        animals.forEach { animal ->
            // Restore borrowed originals before closing the per-clear fade materials.
            animal.materials.forEach { binding ->
                if (binding.index < binding.model.materials.size && binding.model.materials[binding.index] == binding.fade) {
                    binding.model.materials[binding.index] = binding.original
                }
                binding.fade.close()
            }
        }
        sprites = emptyList()
        animals = emptyList()
        token = null
    }

    fun close() {
        if (closed) return
        closed = true
        loadScope.cancel()
        stop()
        sequences.values.flatten().forEach { it.close() }
        textures.forEach { it.close() }
        plane?.close()
        sequences.clear()
        textures.clear()
    }

    private companion object {
        const val TILE_SIZE = 0.13f
    }
}
