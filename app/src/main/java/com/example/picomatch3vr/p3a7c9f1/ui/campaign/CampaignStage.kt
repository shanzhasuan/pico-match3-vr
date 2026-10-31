package com.example.picomatch3vr.p3a7c9f1.ui.campaign

import com.example.picomatch3vr.p3a7c9f1.ui.game.Match3EffectAudioPlayer

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.picomatch3vr.p3a7c9f1.domain.campaign.*
import com.example.picomatch3vr.p3a7c9f1.ui.theme.Match3DesignTokens
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.LinearProgressIndicator
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.gesture.SpatialDragValue
import com.pico.spatial.ui.foundation.gesture.SpatialPointerInfo
import com.pico.spatial.ui.foundation.gesture.SpatialTapValue
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.foundation.gesture.detectSpatialPointerEvent
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import com.pico.spatial.ui.geometry.Offset3D
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GameplayStage(
    startupReveal: Float = 1f,
    inputEnabled: Boolean = true,
    onStartupProgress: (Float) -> Unit = {},
    onStartupReady: () -> Unit = {},
) {
    Log.i(STAGE_TAG, "GameplayStage composition start")
    val context = LocalContext.current
    val viewModel: GameplayViewModel = viewModel(factory = CampaignViewModelFactory(context))
    val state by viewModel.state.collectAsState()
    val latestState by rememberUpdatedState(state)
    val latestInputEnabled by rememberUpdatedState(inputEnabled)
    val latestReveal by rememberUpdatedState(startupReveal)
    val reportProgress by rememberUpdatedState(onStartupProgress)
    val reportReady by rememberUpdatedState(onStartupReady)
    val startupScope = rememberCoroutineScope()
    var boardInitialized by remember { mutableStateOf(false) }
    var startupBoardRoot by remember { mutableStateOf<Entity?>(null) }
    val tileNodes = remember { mutableMapOf<GridPoint, TileNode>() }
    val entityToPoint = remember { mutableMapOf<Long, GridPoint>() }
    val dragTracker = remember { SpatialDragSwapTracker() }
    val pointerTracker = remember { SpatialPointerSwapTracker() }
    val renderKeyState = remember { mutableStateOf<BoardRenderKey?>(null) }
    LaunchedEffect(startupReveal, startupBoardRoot) {
        startupBoardRoot?.components?.get(TransformComponent::class.java)?.apply {
            val factor = if (startupReveal <= 0f) 0.0001f else 0.86f + 0.14f * startupReveal
            setScaleVector(Vector3(BOARD_WORLD_SCALE.x * factor, BOARD_WORLD_SCALE.y * factor, BOARD_WORLD_SCALE.z * factor))
        }
    }
    val effectAudioPlayer = remember(context) { Match3EffectAudioPlayer(context) }
    val clearEffectRenderer = remember { GameplayClearEffectRenderer() }
    val skyTreeEnvironment = remember { SkyTreeEnvironment() }
    val renderKey = remember(
        state.displayBoard,
        state.selectedCell,
        state.activeEffect,
        state.activeBurst,
        state.activeCelebration,
        state.celebrationProgress,
        state.transientFallOffsets,
        state.transientFallProgress,
    ) {
        BoardRenderKey.from(
            board = state.displayBoard,
            selectedCell = state.selectedCell,
            activeEffect = state.activeEffect,
            activeBurst = state.activeBurst,
            activeCelebration = state.activeCelebration,
            celebrationProgress = state.celebrationProgress,
            transientFallOffsets = state.transientFallOffsets,
            transientFallProgress = state.transientFallProgress,
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            effectAudioPlayer.release()
            clearEffectRenderer.close()
            skyTreeEnvironment.close()
        }
    }

    LaunchedEffect(state.activeBurst?.token) {
        state.activeBurst?.let { burst ->
            effectAudioPlayer.play(com.example.picomatch3vr.p3a7c9f1.ui.game.Match3EffectKind.valueOf(burst.kind.name), burst.cascadeCount)
        }
    }

    LaunchedEffect(state.levelClearCueToken) {
        if (state.levelClearCueToken != null) {
            effectAudioPlayer.playLevelComplete()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Transparent),
    ) {
        SpatialView(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .pointerInput(renderKey) {
                        detectSpatialPointerEvent(
                            context = context,
                        ) { events ->
                            if (boardInitialized && latestInputEnabled) handleSpatialPointerEvents(
                                events = events,
                                entityToPoint = entityToPoint,
                                state = latestState,
                                pointerTracker = pointerTracker,
                                onEvent = viewModel::onEvent,
                            )
                            false
                        }
                    }
                    .pointerInput(renderKey) {
                        detectSpatialTapGesture(
                            context = context,
                        ) { tapValue ->
                            if (boardInitialized && latestInputEnabled) handleSpatialTap(
                                tapValue = tapValue,
                                entityToPoint = entityToPoint,
                                board = latestState.displayBoard,
                                onEvent = viewModel::onEvent,
                            )
                        }
                    }
                    .pointerInput(renderKey) {
                        detectSpatialDragGesture(
                            context = context,
                            onDragStart = { _, dragValue ->
                                if (boardInitialized && latestInputEnabled) handleSpatialDragStart(
                                    dragValue = dragValue,
                                    entityToPoint = entityToPoint,
                                    state = latestState,
                                    dragTracker = dragTracker,
                                )
                            },
                            onDragEnd = { dragValue ->
                                Log.i(STAGE_TAG, "drag end target=${dragValue.targetEntity?.id} drag=${dragValue.dragAmount}")
                                dragTracker.reset()
                            },
                            onDragCancel = { dragValue ->
                                Log.i(STAGE_TAG, "drag cancel target=${dragValue.targetEntity?.id} drag=${dragValue.dragAmount}")
                                dragTracker.reset()
                            },
                        ) { dragValue ->
                            if (boardInitialized && latestInputEnabled) handleSpatialDrag(
                                dragValue = dragValue,
                                entityToPoint = entityToPoint,
                                state = latestState,
                                dragTracker = dragTracker,
                                onSwap = { from, to -> viewModel.onEvent(GameEvent.DragSwap(from, to)) },
                            )
                        }
                    },
            initial = { content, attachments ->
                Log.i(STAGE_TAG, "SpatialView initial start board=${state.displayBoard.width}x${state.displayBoard.height}")
                tileNodes.clear()
                entityToPoint.clear()

                val boardRoot =
                    Entity().apply {
                        setName("Match3BoardRoot")
                        components.set(
                            TransformComponent().apply {
                                // Keep the board larger and closer so controller rays can hit
                                // it reliably and the authored animal heads read clearly.
                                setPosition(BOARD_WORLD_POSITION)
                                setEulerAngles(EulerAngles(0f, 0f, 0f))
                                setScaleVector(BOARD_WORLD_SCALE)
                            },
                        )
                    }
                startupBoardRoot = boardRoot
                boardRoot.components[TransformComponent::class.java]?.setScaleVector(
                    Vector3(0.0001f, 0.0001f, 0.0001f),
                )
                content.addEntity(skyTreeEnvironment.root)
                content.addEntity(createAnimalStudioLights())
                content.addEntity(boardRoot)
                startupScope.launch {
                    skyTreeEnvironment.load()
                    val initialState = latestState
                    val board = initialState.displayBoard
                    // Keep native ownership and scene mutations on Main, but yield between cells.
                    for (index in board.cells.indices) {
                        createTileNodes(board, boardRoot, tileNodes, entityToPoint, index until index + 1)
                        val point = GridPoint(index / board.width, index % board.width)
                        val node = tileNodes.getValue(point)
                        ensureTilePrototype(node, board.tileAt(point).color, board.tileAt(point).special,
                            board.tileAt(point).cargo, board.gems[point] ?: 0)
                        reportProgress((index + 1f) / board.cells.size)
                        // Yield without adding a fixed one-second delay across all 64 cells.
                        delay(1)
                    }
                    clearEffectRenderer.prepare(initialState.activeBurst)
                    applyBoardToNodes(
                        board = board,
                        selectedCell = initialState.selectedCell,
                        activeBurst = initialState.activeBurst,
                        activeEffect = initialState.activeEffect,
                        celebration = initialState.activeCelebration,
                        celebrationProgress = initialState.celebrationProgress,
                        fallOffsets = initialState.transientFallOffsets,
                        fallProgress = initialState.transientFallProgress,
                        tileNodes = tileNodes,
                    )
                    updateClearEffects(clearEffectRenderer, initialState.activeBurst, tileNodes, board.width)
                    updateCampaignMarkers(board, tileNodes, initialState.session.level.mode)
                    boardInitialized = true
                    reportReady()
                    Log.i(STAGE_TAG, "Startup board ready tiles=${tileNodes.size}")
                    val factor = if (latestReveal <= 0f) 0.0001f else 0.86f + 0.14f * latestReveal
                    boardRoot.components[TransformComponent::class.java]?.setScaleVector(
                        Vector3(BOARD_WORLD_SCALE.x * factor, BOARD_WORLD_SCALE.y * factor, BOARD_WORLD_SCALE.z * factor),
                    )
                }

                attachments.entity("hud_panel")?.let { hud ->
                    Log.i(STAGE_TAG, "SpatialView initial attaching hud_panel")
                    hud.components.get(TransformComponent::class.java)?.apply {
                        setPosition(Vector3(0f, 2.02f, -1.84f))
                        setEulerAngles(EulerAngles(0f, 0f, 0f))
                    }
                    content.addEntity(hud)
                }
                attachments.entity("level_actions")?.let { actions ->
                    actions.components.get(TransformComponent::class.java)?.apply {
                        setPosition(LEVEL_ACTIONS_WORLD_POSITION)
                        setEulerAngles(EulerAngles(0f, 0f, 0f))
                    }
                    content.addEntity(actions)
                }
            },
            update = { _, attachments ->
                if (!boardInitialized) return@SpatialView
                Log.i(STAGE_TAG, "SpatialView update renderKeyChanged=${renderKeyState.value != renderKey}")
                attachments.entity("hud_panel")?.components?.get(TransformComponent::class.java)?.apply {
                    setPosition(Vector3(0f, 2.02f, -1.84f))
                    setEulerAngles(EulerAngles(0f, 0f, 0f))
                }
                attachments.entity("level_actions")?.components?.get(TransformComponent::class.java)?.apply {
                    setPosition(LEVEL_ACTIONS_WORLD_POSITION)
                    setEulerAngles(EulerAngles(0f, 0f, 0f))
                }
                if (renderKeyState.value == renderKey) {
                    return@SpatialView
                }
                clearEffectRenderer.prepare(state.activeBurst)
                applyBoardToNodes(
                    board = state.displayBoard,
                    selectedCell = state.selectedCell,
                    activeBurst = state.activeBurst,
                    activeEffect = state.activeEffect,
                    celebration = state.activeCelebration,
                    celebrationProgress = state.celebrationProgress,
                    fallOffsets = state.transientFallOffsets,
                    fallProgress = state.transientFallProgress,
                    tileNodes = tileNodes,
                )
                updateClearEffects(clearEffectRenderer, state.activeBurst, tileNodes, state.displayBoard.width)
                updateCampaignMarkers(state.displayBoard, tileNodes, state.session.level.mode)
                renderKeyState.value = renderKey
            },
            attachments = {
                AttachmentPanel("hud_panel") {
                    StageHudPanel(
                        state = state,
                    )
                }
                AttachmentPanel("level_actions") {
                    StageLevelActions(
                        state = state,
                        onEvent = { if (boardInitialized && latestInputEnabled) viewModel.onEvent(it) },
                    )
                }
            },
        )
    }
}

private fun handleSpatialTap(
    tapValue: SpatialTapValue,
    entityToPoint: Map<Long, GridPoint>,
    board: Match3Board,
    onEvent: (GameEvent) -> Unit,
) {
    val point =
        resolveBoardPoint(
            targetEntity = tapValue.targetEntity,
            entityToPoint = entityToPoint,
            board = board,
        )
    Log.i(STAGE_TAG, "tap point=$point entity=${tapValue.targetEntity?.id} kind=${tapValue.interactionKind} position=${tapValue.position}")
    point?.let { onEvent(GameEvent.SelectCell(it)) }
}

private fun handleSpatialDragStart(
    dragValue: SpatialDragValue,
    entityToPoint: Map<Long, GridPoint>,
    state: GameUiState,
    dragTracker: SpatialDragSwapTracker,
) {
    val point =
        resolveBoardPoint(
            targetEntity = dragValue.targetEntity,
            entityToPoint = entityToPoint,
            board = state.displayBoard,
            inputDevicePose = dragValue.inputDevicePose,
        )
    Log.i(
        STAGE_TAG,
        "drag start point=$point entity=${dragValue.targetEntity?.id} kind=${dragValue.interactionKind} rawPosition=${dragValue.inputDevicePose.rawPosition} drag=${dragValue.dragAmount}",
    )
    if (
        point == null ||
        state.isWin ||
        state.isOutOfMoves ||
        state.hammerArmed
    ) {
        dragTracker.reset()
    } else {
        dragTracker.start(point)
    }
}

private fun handleSpatialDrag(
    dragValue: SpatialDragValue,
    entityToPoint: Map<Long, GridPoint>,
    state: GameUiState,
    dragTracker: SpatialDragSwapTracker,
    onSwap: (GridPoint, GridPoint) -> Unit,
) {
    val hoveredPoint =
        resolveBoardPoint(
            targetEntity = dragValue.targetEntity,
            entityToPoint = entityToPoint,
            board = state.displayBoard,
            inputDevicePose = dragValue.inputDevicePose,
        )
    dragTracker.updateDrag(
        dragAmount = dragValue.dragAmount,
        hoveredPoint = hoveredPoint,
        board = state.displayBoard,
    )?.let { swap ->
        Log.i(
            STAGE_TAG,
            "drag swap from=${swap.from} to=${swap.to} drag=${dragValue.dragAmount} hovered=$hoveredPoint target=${dragValue.targetEntity?.id}",
        )
        onSwap(swap.from, swap.to)
    } ?: run {
        Log.i(
            STAGE_TAG,
            "drag update drag=${dragValue.dragAmount} hovered=$hoveredPoint target=${dragValue.targetEntity?.id}",
        )
    }
}

private fun createStageEnvironment(): Entity =
    Entity().apply {
        setName("Match3StageEnvironment")
        components.set(TransformComponent())

        addChild(
            environmentPanel(
                name = "sky_top",
                size = Vector3(8.4f, 4.4f, 0.04f),
                color = Color4(0.13f, 0.62f, 0.94f, 1f),
                position = Vector3(0f, 2.7f, -4.8f),
            ),
        )
        addChild(
            environmentPanel(
                name = "sky_mid",
                size = Vector3(8.8f, 3.2f, 0.04f),
                color = Color4(0.28f, 0.75f, 0.96f, 1f),
                position = Vector3(0f, 1.2f, -4.78f),
            ),
        )
        addChild(
            environmentPanel(
                name = "meadow",
                size = Vector3(9.2f, 2.4f, 0.04f),
                color = Color4(0.48f, 0.74f, 0.16f, 1f),
                position = Vector3(0f, -0.58f, -4.75f),
            ),
        )
        addChild(
            environmentPanel(
                name = "left_wing",
                size = Vector3(4.8f, 4.6f, 0.04f),
                color = Color4(0.2f, 0.68f, 0.95f, 0.98f),
                position = Vector3(-4.05f, 1.34f, -2.8f),
                rotation = EulerAngles(0f, 42f, 0f),
            ),
        )
        addChild(
            environmentPanel(
                name = "right_wing",
                size = Vector3(4.8f, 4.6f, 0.04f),
                color = Color4(0.2f, 0.68f, 0.95f, 0.98f),
                position = Vector3(4.05f, 1.34f, -2.8f),
                rotation = EulerAngles(0f, -42f, 0f),
            ),
        )
        addChild(
            environmentPanel(
                name = "ceiling_glow",
                size = Vector3(7.8f, 3.6f, 0.04f),
                color = Color4(0.54f, 0.84f, 1f, 0.92f),
                position = Vector3(0f, 4.2f, -2.9f),
                rotation = EulerAngles(68f, 0f, 0f),
            ),
        )

        addChild(cloudCluster("cloud_left", Vector3(-1.85f, 2.34f, -3.7f), 1.1f))
        addChild(cloudCluster("cloud_center", Vector3(0.08f, 2.82f, -4.05f), 1.36f))
        addChild(cloudCluster("cloud_right", Vector3(1.96f, 2.16f, -3.56f), 1f))

        addChild(hillBand("hill_far_left", Vector3(-2.3f, 0.16f, -4.34f), Vector3(2.5f, 1.18f, 0.28f), Color4(0.27f, 0.61f, 0.22f, 1f)))
        addChild(hillBand("hill_far_center", Vector3(0.1f, 0.26f, -4.18f), Vector3(3.44f, 1.42f, 0.3f), Color4(0.19f, 0.52f, 0.27f, 1f)))
        addChild(hillBand("hill_far_right", Vector3(2.52f, 0.12f, -4.32f), Vector3(2.46f, 1.14f, 0.28f), Color4(0.37f, 0.67f, 0.19f, 1f)))

        addChild(candyColumn("candy_left", Vector3(-2.72f, 0.18f, -3.36f), Color4(1f, 0.3f, 0.5f, 1f), Color4(1f, 0.86f, 0.92f, 1f)))
        addChild(candyColumn("candy_right", Vector3(2.74f, 0.18f, -3.34f), Color4(0.12f, 0.61f, 0.94f, 1f), Color4(0.92f, 0.98f, 1f, 1f)))

        addChild(
            environmentPanel(
                name = "floor_glow",
                size = Vector3(4.9f, 1.4f, 0.04f),
                color = Color4(0.65f, 0.86f, 0.35f, 0.35f),
                position = Vector3(0f, 0.74f, -2.34f),
                rotation = EulerAngles(86f, 0f, 0f),
            ),
        )
    }

private fun environmentPanel(
    name: String,
    size: Vector3,
    color: Color4,
    position: Vector3,
    rotation: EulerAngles = EulerAngles(0f, 0f, 0f),
): ModelEntity =
    ModelEntity(
        MeshCatalog.environmentMesh(size, 0.18f),
        MaterialCatalog.material(color),
    ).apply {
        setName(name)
        components.set(
            TransformComponent().apply {
                setPosition(position)
                setScaleVector(Vector3(1f, 1f, 1f))
                setEulerAngles(rotation)
            },
        )
    }

private fun cloudCluster(
    name: String,
    position: Vector3,
    scale: Float,
): Entity =
    Entity().apply {
        setName(name)
        components.set(
            TransformComponent().apply {
                setPosition(position)
                setScaleVector(Vector3(scale, scale, scale))
            },
        )
        addChild(
            environmentPanel(
                name = "${name}_core",
                size = Vector3(0.94f, 0.38f, 0.18f),
                color = Color4(0.98f, 1f, 1f, 0.96f),
                position = Vector3(0f, 0f, 0f),
            ),
        )
        addChild(
            environmentPanel(
                name = "${name}_left",
                size = Vector3(0.52f, 0.3f, 0.16f),
                color = Color4(0.98f, 1f, 1f, 0.94f),
                position = Vector3(-0.42f, 0.08f, -0.02f),
            ),
        )
        addChild(
            environmentPanel(
                name = "${name}_right",
                size = Vector3(0.56f, 0.32f, 0.16f),
                color = Color4(0.98f, 1f, 1f, 0.94f),
                position = Vector3(0.44f, 0.07f, -0.02f),
            ),
        )
        addChild(
            environmentPanel(
                name = "${name}_top",
                size = Vector3(0.48f, 0.24f, 0.14f),
                color = Color4(1f, 1f, 1f, 0.96f),
                position = Vector3(0f, 0.18f, 0.02f),
            ),
        )
    }

private fun hillBand(
    name: String,
    position: Vector3,
    size: Vector3,
    color: Color4,
): ModelEntity =
    environmentPanel(
        name = name,
        size = size,
        color = color,
        position = position,
    )

private fun candyColumn(
    name: String,
    position: Vector3,
    candyColor: Color4,
    stripeColor: Color4,
): Entity =
    Entity().apply {
        setName(name)
        components.set(
            TransformComponent().apply {
                setPosition(position)
                setScaleVector(Vector3(1f, 1f, 1f))
            },
        )
        addChild(
            environmentPanel(
                name = "${name}_stick",
                size = Vector3(0.12f, 1.16f, 0.08f),
                color = Color4(0.98f, 0.9f, 0.76f, 1f),
                position = Vector3(0f, -0.3f, -0.02f),
            ),
        )
        addChild(
            environmentPanel(
                name = "${name}_head",
                size = Vector3(0.58f, 0.58f, 0.14f),
                color = candyColor,
                position = Vector3(0f, 0.34f, 0f),
            ),
        )
        addChild(
            environmentPanel(
                name = "${name}_stripe_h",
                size = Vector3(0.48f, 0.1f, 0.06f),
                color = stripeColor,
                position = Vector3(0f, 0.34f, 0.03f),
                rotation = EulerAngles(0f, 0f, 24f),
            ),
        )
        addChild(
            environmentPanel(
                name = "${name}_stripe_v",
                size = Vector3(0.1f, 0.48f, 0.06f),
                color = stripeColor,
                position = Vector3(0f, 0.34f, 0.03f),
                rotation = EulerAngles(0f, 0f, 24f),
            ),
        )
    }

private fun createTileNodes(
    board: Match3Board,
    root: Entity,
    tileNodes: MutableMap<GridPoint, TileNode>,
    entityToPoint: MutableMap<Long, GridPoint>,
    indices: IntRange = board.cells.indices,
) {
    val centerX = (board.width - 1) / 2f
    val centerY = (board.height - 1) / 2f
    for (row in 0 until board.height) {
        for (column in 0 until board.width) {
            if (row * board.width + column !in indices) continue
            val point = GridPoint(row, column)
            val node = createTileNode(point)
            val basePosition =
                Vector3(
                    (column - centerX) * TILE_SPACING,
                    (centerY - row) * TILE_SPACING,
                    0f,
                )
            node.entity.components.get(TransformComponent::class.java)?.apply {
                setPosition(basePosition)
            }
            entityToPoint[node.entity.id] = point
            entityToPoint[node.prototypeContainer.id] = point
            entityToPoint[node.glow.id] = point
            entityToPoint[node.sparkle.id] = point
            entityToPoint[node.flareVertical.id] = point
            entityToPoint[node.flareDiagonal.id] = point
            node.basePosition = basePosition
            tileNodes[point] = node
            root.addChild(node.entity)
        }
    }
}

private fun createTileNode(point: GridPoint): TileNode {
    val entity =
        ModelEntity(
            MeshCatalog.gemShellMesh,
            MaterialCatalog.material(Color4(0.8f, 0.84f, 0.9f, 0.84f)),
        ).apply {
            setName("tile_${point.row}_${point.column}")
            components.set(
                TransformComponent().apply {
                    setScaleVector(Vector3(1f, 1f, 1f))
                },
            )
            components.set(InteractableComponent())
            components.set(HoverEffectComponent())
            components.set(
                CollisionComponent(
                    collisionShape = listOf(MeshCatalog.tileCollisionShape),
                    physicsMaterial = PhysicsCatalog.defaultMaterial,
                ),
            )
        }
    val prototypeContainer =
        Entity().apply {
            setName("tile_${point.row}_${point.column}_prototype_container")
            components.set(
                TransformComponent().apply {
                    setPosition(Vector3(0f, 0f, 0f))
                    setScaleVector(Vector3(1f, 1f, 1f))
                },
            )
        }
    val glow =
        ModelEntity(
            MeshCatalog.glowMesh,
            MaterialCatalog.material(Color4(1f, 1f, 1f, 0.18f)),
        ).apply {
            setName("tile_${point.row}_${point.column}_glow")
            components.set(
                TransformComponent().apply {
                    setPosition(Vector3(0f, 0f, -0.02f))
                    setScaleVector(Vector3(1.12f, 1.12f, 1f))
                },
            )
        }
    val sparkle =
        ModelEntity(
            MeshCatalog.sparkleMesh,
            MaterialCatalog.material(Color4(1f, 1f, 1f, 0.85f)),
        ).apply {
            setName("tile_${point.row}_${point.column}_sparkle")
            components.set(
                TransformComponent().apply {
                    setPosition(Vector3(-0.024f, 0.028f, 0.046f))
                    setScaleVector(Vector3(0.024f, 0.024f, 0.02f))
                    setEulerAngles(EulerAngles(0f, 0f, 24f))
                },
            )
        }
    val flareVertical =
        ModelEntity(
            MeshCatalog.flareVerticalMesh,
            MaterialCatalog.material(Color4(1f, 1f, 1f, 0.52f)),
        ).apply {
            setName("tile_${point.row}_${point.column}_flare_vertical")
            components.set(
                TransformComponent().apply {
                    setPosition(Vector3(0f, 0f, 0.032f))
                    setScaleVector(Vector3(0.03f, 0.03f, 0.02f))
                    setEulerAngles(EulerAngles(0f, 0f, 0f))
                },
            )
        }
    val flareDiagonal =
        ModelEntity(
            MeshCatalog.flareHorizontalMesh,
            MaterialCatalog.material(Color4(1f, 1f, 1f, 0.44f)),
        ).apply {
            setName("tile_${point.row}_${point.column}_flare_diagonal")
            components.set(
                TransformComponent().apply {
                    setPosition(Vector3(0f, 0f, 0.034f))
                    setScaleVector(Vector3(0.03f, 0.03f, 0.02f))
                    setEulerAngles(EulerAngles(0f, 0f, 45f))
                },
            )
        }
    val burstSparkA =
        ModelEntity(
            MeshCatalog.sparkleMesh,
            MaterialCatalog.material(Color4(1f, 0.98f, 0.84f, 0.9f)),
        ).apply {
            setName("tile_${point.row}_${point.column}_burst_spark_a")
            components.set(
                TransformComponent().apply {
                    setPosition(Vector3(0f, 0f, 0.04f))
                    setScaleVector(Vector3(0.014f, 0.032f, 0.02f))
                    setEulerAngles(EulerAngles(0f, 0f, 18f))
                },
            )
        }
    val burstSparkB =
        ModelEntity(
            MeshCatalog.sparkleMesh,
            MaterialCatalog.material(Color4(1f, 0.94f, 0.7f, 0.82f)),
        ).apply {
            setName("tile_${point.row}_${point.column}_burst_spark_b")
            components.set(
                TransformComponent().apply {
                    setPosition(Vector3(0f, 0f, 0.04f))
                    setScaleVector(Vector3(0.014f, 0.032f, 0.02f))
                    setEulerAngles(EulerAngles(0f, 0f, -22f))
                },
            )
        }
    val burstSparkC =
        ModelEntity(
            MeshCatalog.sparkleMesh,
            MaterialCatalog.material(Color4(1f, 0.9f, 0.56f, 0.74f)),
        ).apply {
            setName("tile_${point.row}_${point.column}_burst_spark_c")
            components.set(
                TransformComponent().apply {
                    setPosition(Vector3(0f, 0f, 0.04f))
                    setScaleVector(Vector3(0.012f, 0.028f, 0.02f))
                    setEulerAngles(EulerAngles(0f, 0f, 54f))
                },
            )
        }
    val burstSparkD =
        ModelEntity(
            MeshCatalog.sparkleMesh,
            MaterialCatalog.material(Color4(1f, 0.99f, 0.9f, 0.68f)),
        ).apply {
            setName("tile_${point.row}_${point.column}_burst_spark_d")
            components.set(
                TransformComponent().apply {
                    setPosition(Vector3(0f, 0f, 0.04f))
                    setScaleVector(Vector3(0.012f, 0.028f, 0.02f))
                    setEulerAngles(EulerAngles(0f, 0f, -58f))
                },
            )
        }
    entity.addChild(prototypeContainer)
    entity.addChild(glow)
    entity.addChild(sparkle)
    entity.addChild(flareVertical)
    entity.addChild(flareDiagonal)
    entity.addChild(burstSparkA)
    entity.addChild(burstSparkB)
    entity.addChild(burstSparkC)
    entity.addChild(burstSparkD)
    return TileNode(
        entity = entity,
        prototypeContainer = prototypeContainer,
        glow = glow,
        sparkle = sparkle,
        flareVertical = flareVertical,
        flareDiagonal = flareDiagonal,
        burstSparkA = burstSparkA,
        burstSparkB = burstSparkB,
        burstSparkC = burstSparkC,
        burstSparkD = burstSparkD,
    )
}

private fun updateClearEffects(
    renderer: GameplayClearEffectRenderer,
    burst: Match3FlashBurst?,
    tileNodes: Map<GridPoint, TileNode>,
    boardWidth: Int,
) {
    renderer.update(
        burst,
        tileNodes.map { (point, node) ->
            GameplayClearEffectRenderer.Target(point, node.prototypeContainer, node.sparkle, node.flareVertical, node.flareDiagonal)
        },
        boardWidth,
    )
}

private fun applyBoardToNodes(
    board: Match3Board,
    selectedCell: GridPoint?,
    activeBurst: Match3FlashBurst?,
    activeEffect: Match3EffectCue?,
    celebration: LevelClearCelebration?,
    celebrationProgress: Float,
    fallOffsets: Map<GridPoint, Float>,
    fallProgress: Float,
    tileNodes: Map<GridPoint, TileNode>,
) {
    tileNodes.forEach { (point, node) ->
        applyTileAppearance(
            node = node,
            tile = board.tileAt(point),
            gemLayers = board.gems[point] ?: 0,
            selected = selectedCell == point,
            bursting = activeBurst?.points?.contains(point) == true,
            burstKind = activeBurst?.kind,
            burstCascadeCount = activeBurst?.cascadeCount ?: 1,
            highlighted = activeEffect?.highlightedPoints?.contains(point) == true,
            effectKind = activeEffect?.kind,
            celebrating = celebration != null,
            celebrationProgress = celebrationProgress,
            fallOffsetCells = fallOffsets[point] ?: 0f,
            fallProgress = fallProgress,
        )
    }
}

private fun applyTileAppearance(
    node: TileNode,
    tile: Match3Tile,
    gemLayers: Int,
    selected: Boolean,
    bursting: Boolean,
    burstKind: Match3EffectKind?,
    burstCascadeCount: Int,
    highlighted: Boolean,
    effectKind: Match3EffectKind?,
    celebrating: Boolean,
    celebrationProgress: Float,
    fallOffsetCells: Float,
    fallProgress: Float,
) {
    ensureTilePrototype(node, tile.color, tile.special, tile.cargo, gemLayers)
    val usingRuntimePrototype = node.currentPrototype != null
    // The renderer owns shrink/fade and sprite playback during every clear.
    val suppressNormalClearVisual = false
    val celebrationWave = celebrationEnvelope(celebrationProgress)

    updateRenderable(
        entity = node.entity,
        mesh = headMeshFor(tile.color),
        material =
            MaterialCatalog.material(
                gemShellColor(
                    color = tile.color,
                    special = tile.special,
                    selected = selected,
                    bursting = bursting,
                    burstKind = burstKind,
                    highlighted = highlighted,
                    effectKind = effectKind,
                ),
            ),
        visible = !usingRuntimePrototype && !suppressNormalClearVisual,
    )
    updateRenderable(
        entity = node.glow,
        mesh = MeshCatalog.glowMesh,
        material = MaterialCatalog.material(
            glowColor(
                color = tile.color,
                special = tile.special,
                selected = selected,
                bursting = bursting,
                burstKind = burstKind,
                highlighted = highlighted,
                effectKind = effectKind,
            ),
        ),
        visible = false,
    )
    updateRenderable(
        entity = node.sparkle,
        mesh = MeshCatalog.sparkleMesh,
        material = MaterialCatalog.material(sparkleColor(tile.color, selected, bursting, highlighted)),
        visible = false,
    )
    updateRenderable(
        entity = node.flareVertical,
        mesh = MeshCatalog.flareVerticalMesh,
        material = MaterialCatalog.material(
            flareColor(
                color = tile.color,
                special = tile.special,
                selected = selected,
                bursting = bursting,
                burstKind = burstKind,
                highlighted = highlighted,
                effectKind = effectKind,
                primary = true,
            ),
        ),
        visible = false,
    )
    updateRenderable(
        entity = node.flareDiagonal,
        mesh = MeshCatalog.flareHorizontalMesh,
        material = MaterialCatalog.material(
            flareColor(
                color = tile.color,
                special = tile.special,
                selected = selected,
                bursting = bursting,
                burstKind = burstKind,
                highlighted = highlighted,
                effectKind = effectKind,
                primary = false,
            ),
        ),
        visible = false,
    )
    if (!celebrating) {
        val burstSparkColorA =
            MaterialCatalog.material(
                fireworkSparkColor(
                    color = tile.color,
                    bursting = bursting,
                    burstKind = burstKind,
                    intensity = 1f,
                ),
            )
        val burstSparkColorB =
            MaterialCatalog.material(
                fireworkSparkColor(
                    color = tile.color,
                    bursting = bursting,
                    burstKind = burstKind,
                    intensity = 0.92f,
                ),
            )
        val burstSparkColorC =
            MaterialCatalog.material(
                fireworkSparkColor(
                    color = tile.color,
                    bursting = bursting,
                    burstKind = burstKind,
                    intensity = 0.84f,
                ),
            )
        val burstSparkColorD =
            MaterialCatalog.material(
                fireworkSparkColor(
                    color = tile.color,
                    bursting = bursting,
                    burstKind = burstKind,
                    intensity = 0.78f,
                ),
            )
        updateRenderable(
            entity = node.burstSparkA,
            mesh = MeshCatalog.sparkleMesh,
            material = burstSparkColorA,
            visible = false,
        )
        updateRenderable(
            entity = node.burstSparkB,
            mesh = MeshCatalog.sparkleMesh,
            material = burstSparkColorB,
            visible = false,
        )
        updateRenderable(
            entity = node.burstSparkC,
            mesh = MeshCatalog.sparkleMesh,
            material = burstSparkColorC,
            visible = false,
        )
        updateRenderable(
            entity = node.burstSparkD,
            mesh = MeshCatalog.sparkleMesh,
            material = burstSparkColorD,
            visible = false,
        )
    }
    node.entity.components.get(TransformComponent::class.java)?.apply {
        val fallOffsetY = TILE_SPACING * fallOffsetCells * (1f - fallProgress).coerceIn(0f, 1f)
        val scaleBoost =
            when {
                celebrating -> 1.08f + celebrationWave * 0.08f
                bursting -> 1.18f
                selected -> 1.1f
                highlighted -> 1.14f
                else -> 1f
            }
        val zOffset =
            when {
                celebrating -> specialLift(tile.special) + 0.02f + celebrationWave * 0.03f
                bursting -> specialLift(tile.special) + 0.07f
                selected -> 0.04f
                highlighted -> specialLift(tile.special) + 0.05f
                else -> specialLift(tile.special)
            }
        val basePosition = node.basePosition
        setPosition(Vector3(basePosition.x, basePosition.y + fallOffsetY, zOffset))
        if (usingRuntimePrototype) {
            setScaleVector(Vector3(1f, 1f, 1f))
            setEulerAngles(EulerAngles(0f, 0f, 0f))
        } else {
            setScaleVector(scaleFor(tile.color, scaleBoost))
            setEulerAngles(rotationFor(tile.color, tile.special, highlighted))
        }
    }
    val prototypeScaleBoost =
        when {
            celebrating -> 1f + celebrationWave * 0.06f
            bursting -> 1.14f
            highlighted -> 1.08f
            selected -> 1.05f
            else -> 1f
        }
    node.prototypeContainer.components.get(TransformComponent::class.java)?.apply {
        setPosition(prototypeAnchorFor(tile.color, selected, bursting, highlighted))
        setScaleVector(
            if (suppressNormalClearVisual) {
                Vector3(0.0001f, 0.0001f, 0.0001f)
            } else if (usingRuntimePrototype) {
                animalScaleFor(tile.color, prototypeScaleBoost)
            } else {
                Vector3(0.0001f, 0.0001f, 0.0001f)
            },
        )
        setEulerAngles(rotationFor(tile.color, tile.special, highlighted))
    }
    node.glow.components.get(TransformComponent::class.java)?.apply {
        val cascadeBoost = 1f + ((burstCascadeCount - 1).coerceAtLeast(0) * 0.06f)
        val glowScale =
            when {
                bursting -> Vector3(0.132f * cascadeBoost, 0.132f * cascadeBoost, 0.02f)
                highlighted -> Vector3(0.11f, 0.11f, 0.02f)
                selected -> Vector3(0.098f, 0.098f, 0.02f)
                tile.special != SpecialTileKind.None -> Vector3(0.088f, 0.088f, 0.02f)
                else -> Vector3(0.08f, 0.08f, 0.02f)
            }
        setPosition(Vector3(0f, 0f, if (bursting) -0.006f else if (highlighted) -0.012f else -0.02f))
        setScaleVector(glowScale)
    }
    node.sparkle.components.get(TransformComponent::class.java)?.apply {
        setPosition(
            Vector3(
                sparkleOffsetX(tile.color),
                sparkleOffsetY(tile.color),
                if (bursting) 0.062f else if (highlighted || selected) 0.052f else 0.046f,
            ),
        )
        setScaleVector(
            when {
                bursting -> {
                    val burstScale = 1f + ((burstCascadeCount - 1).coerceAtLeast(0) * 0.08f)
                    Vector3(0.042f * burstScale, 0.042f * burstScale, 0.02f)
                }
                highlighted -> Vector3(0.034f, 0.034f, 0.02f)
                selected -> Vector3(0.03f, 0.03f, 0.02f)
                else -> Vector3(0.024f, 0.024f, 0.02f)
            },
        )
        setEulerAngles(EulerAngles(0f, 0f, sparkleRoll(tile.color, tile.special, bursting, highlighted)))
    }
    val flareScale =
        when {
            bursting -> Vector3(0.108f, 0.108f, 0.02f)
            highlighted -> Vector3(0.082f, 0.082f, 0.02f)
            selected -> Vector3(0.066f, 0.066f, 0.02f)
            tile.special != SpecialTileKind.None -> Vector3(0.054f, 0.054f, 0.02f)
            else -> Vector3(0.042f, 0.042f, 0.02f)
        }
    val burstProfile = burstProfileFor(burstKind)
    node.flareVertical.components.get(TransformComponent::class.java)?.apply {
        val scale =
            if (bursting && burstProfile != null) {
                Vector3(
                    flareScale.x * burstProfile.primaryScale.x,
                    flareScale.y * burstProfile.primaryScale.y,
                    flareScale.z,
                )
            } else {
                flareScale
            }
        setPosition(Vector3(0f, 0f, if (bursting) 0.056f else if (highlighted) 0.044f else 0.034f))
        setScaleVector(scale)
        setEulerAngles(
            EulerAngles(
                0f,
                0f,
                if (bursting && burstProfile != null) {
                    burstProfile.primaryRoll
                } else if (highlighted) {
                    8f
                } else {
                    0f
                },
            ),
        )
    }
    node.flareDiagonal.components.get(TransformComponent::class.java)?.apply {
        setPosition(Vector3(0f, 0f, if (bursting) 0.058f else if (highlighted) 0.046f else 0.036f))
        setScaleVector(
            if (bursting && burstProfile != null) {
                Vector3(
                    flareScale.x * burstProfile.secondaryScale.x,
                    flareScale.y * burstProfile.secondaryScale.y,
                    flareScale.z,
                )
            } else {
                Vector3(
                    flareScale.x * 0.84f,
                    flareScale.y * 0.84f,
                    flareScale.z,
                )
            },
        )
        setEulerAngles(
            EulerAngles(
                0f,
                0f,
                if (bursting && burstProfile != null) {
                    burstProfile.secondaryRoll
                } else if (highlighted) {
                    36f
                } else {
                    45f
                },
            ),
        )
    }
    val fireworkSpread =
        when (burstKind) {
            Match3EffectKind.Normal -> 0.042f
            Match3EffectKind.Line -> 0.054f
            Match3EffectKind.Bomb -> 0.07f
            Match3EffectKind.ColorBomb -> 0.082f
            Match3EffectKind.Combo -> 0.094f
            null -> 0.034f
        } * (1f + ((burstCascadeCount - 1).coerceAtLeast(0) * 0.08f)) * if (celebrating) (1.6f + celebrationWave * 0.6f) else 1f
    val fireworkScale =
        when (burstKind) {
            Match3EffectKind.Normal -> Vector3(0.014f, 0.034f, 0.02f)
            Match3EffectKind.Line -> Vector3(0.012f, 0.04f, 0.02f)
            Match3EffectKind.Bomb -> Vector3(0.016f, 0.048f, 0.02f)
            Match3EffectKind.ColorBomb -> Vector3(0.018f, 0.054f, 0.02f)
            Match3EffectKind.Combo -> Vector3(0.02f, 0.058f, 0.02f)
            null -> Vector3(0.012f, 0.028f, 0.02f)
        }.let { base ->
            if (celebrating) {
                Vector3(base.x * (1.6f + celebrationWave * 0.8f), base.y * (1.9f + celebrationWave), base.z)
            } else {
                base
            }
        }
    node.burstSparkA.components.get(TransformComponent::class.java)?.apply {
        setPosition(Vector3(-fireworkSpread, fireworkSpread * 0.46f, 0.072f))
        setScaleVector(fireworkScale)
        setEulerAngles(EulerAngles(0f, 0f, -32f))
    }
    node.burstSparkB.components.get(TransformComponent::class.java)?.apply {
        setPosition(Vector3(fireworkSpread, fireworkSpread * 0.4f, 0.076f))
        setScaleVector(fireworkScale)
        setEulerAngles(EulerAngles(0f, 0f, 28f))
    }
    node.burstSparkC.components.get(TransformComponent::class.java)?.apply {
        setPosition(Vector3(-fireworkSpread * 0.38f, -fireworkSpread, 0.068f))
        setScaleVector(Vector3(fireworkScale.x * 0.92f, fireworkScale.y * 0.92f, fireworkScale.z))
        setEulerAngles(EulerAngles(0f, 0f, -118f))
    }
    node.burstSparkD.components.get(TransformComponent::class.java)?.apply {
        setPosition(Vector3(fireworkSpread * 0.34f, -fireworkSpread, 0.07f))
        setScaleVector(Vector3(fireworkScale.x * 0.88f, fireworkScale.y * 0.88f, fireworkScale.z))
        setEulerAngles(EulerAngles(0f, 0f, 122f))
    }
    if (celebrating) {
        val seed =
            (node.basePosition.x / TILE_SPACING).roundToInt() * 13 +
                (node.basePosition.y / TILE_SPACING).roundToInt() * 7 + tile.color.ordinal * 3
        updateCelebrationParticle(node.burstSparkA, seed, 0, celebrationProgress)
        updateCelebrationParticle(node.burstSparkB, seed, 1, celebrationProgress)
        updateCelebrationParticle(node.burstSparkC, seed, 2, celebrationProgress)
        updateCelebrationParticle(node.burstSparkD, seed, 3, celebrationProgress)
    }
}

// Reuse the four clear-effect entities and a fixed palette of cached materials.
private fun updateCelebrationParticle(
    entity: ModelEntity,
    seed: Int,
    index: Int,
    progress: Float,
) {
    val variation = Math.floorMod(seed + index * 11, 17) / 16f
    val delay = Math.floorMod(seed, 7) * 0.026f + index * 0.026f
    val age = (progress.coerceIn(0f, 1f) - delay) / 0.72f
    val visible = age > 0f && age < 1f
    val confetti = index >= 2
    updateRenderable(
        entity = entity,
        mesh = MeshCatalog.sparkleMesh,
        material = MaterialCatalog.material(
            CelebrationPalette.colors[Math.floorMod(seed + index * 2, CelebrationPalette.colors.size)],
        ),
        visible = visible,
    )
    if (!visible) return

    val direction = if (index % 2 == 0) -1f else 1f
    val flutter = sin(age * 15f + variation * 6f)
    val spread = direction * (0.025f + (0.08f + variation * 0.09f) * age)
    val rise = (0.38f + variation * 0.18f) * age
    val gravity = if (confetti) 0.62f else 0.46f
    // Shrink at the end rather than creating hundreds of translucent fade materials.
    val envelope = (age / 0.08f).coerceAtMost(1f) * ((1f - age) / 0.24f).coerceAtMost(1f)
    val size = (0.009f + variation * 0.004f) * envelope
    entity.components.get(TransformComponent::class.java)?.apply {
        setPosition(
            Vector3(
                spread + if (confetti) flutter * 0.018f * age else 0f,
                0.035f + rise - gravity * age * age,
                0.09f + variation * 0.035f,
            ),
        )
        setScaleVector(
            if (confetti) {
                Vector3(size * (0.35f + abs(flutter) * 0.65f), size * 1.8f, 0.006f * envelope)
            } else {
                Vector3(size * 0.65f, size * (2.8f - age * 1.5f), 0.006f * envelope)
            },
        )
        setEulerAngles(
            EulerAngles(
                0f,
                0f,
                if (confetti) variation * 180f + direction * age * 540f else direction * (30f + age * 100f),
            ),
        )
    }
}

private object CelebrationPalette {
    val colors = listOf(
        Color4(1f, 0.78f, 0.12f, 1f),
        Color4(1f, 0.25f, 0.52f, 1f),
        Color4(0.16f, 0.86f, 1f, 1f),
        Color4(0.55f, 1f, 0.26f, 1f),
        Color4(0.75f, 0.38f, 1f, 1f),
        Color4(1f, 0.93f, 0.62f, 1f),
    )
}

private fun ensureTilePrototype(
    node: TileNode,
    color: TileColor,
    special: SpecialTileKind,
    cargo: CargoKind,
    gemLayers: Int,
) {
    if (node.currentPrototypeColor == color && node.currentPrototypeSpecial == special &&
        node.currentCargo == cargo && node.currentGemLayers == gemLayers && node.currentPrototype != null) {
        return
    }
    node.currentPrototype?.destroy(true)
    val prototype = when {
        gemLayers > 0 -> createGemPrototype(gemLayers)
        cargo == CargoKind.GoldenPod -> createPodPrototype()
        special == SpecialTileKind.ColorBomb -> createRainbowBirdPrototype()
        else -> createRuntimeAnimalPrototype(color)
    }
    addSpecialDecoration(prototype, special)
    prototype.components.get(TransformComponent::class.java)?.apply {
        setPosition(Vector3(0f, 0f, 0f))
        setScaleVector(Vector3(1f, 1f, 1f))
        setEulerAngles(EulerAngles(0f, 0f, 0f))
    }
    node.prototypeContainer.removeAllChildren()
    node.prototypeContainer.addChild(prototype)
    node.currentPrototype = prototype
    node.currentPrototypeColor = color
    node.currentPrototypeSpecial = special
    node.currentCargo = cargo
    node.currentGemLayers = gemLayers
}

private fun prototypeAnchorFor(
    color: TileColor,
    selected: Boolean,
    bursting: Boolean,
    highlighted: Boolean,
): Vector3 {
    val base = Vector3(0f, 0f, 0f)
    val yLift =
        when {
            bursting -> 0.018f
            highlighted -> 0.012f
            selected -> 0.008f
            else -> 0f
        }
    val zLift =
        when {
            bursting -> 0.012f
            highlighted -> 0.008f
            selected -> 0.006f
            else -> 0f
        }
    return Vector3(base.x, base.y + yLift, base.z + zLift)
}

private fun animalScaleFor(
    color: TileColor,
    boost: Float,
): Vector3 {
    val base =
        when (color) {
            TileColor.Strawberry -> 0.116f
            TileColor.Lemon -> 0.108f
            TileColor.Blueberry -> 0.112f
            TileColor.Mint -> 0.118f
            TileColor.Peach -> 0.114f
            TileColor.Grape -> 0.11f
        }
    return Vector3(base * boost, base * boost, base * boost)
}

private fun createRuntimeAnimalPrototype(color: TileColor): Entity =
    when (color) {
        TileColor.Strawberry -> createFoxPrototype()
        TileColor.Lemon -> createChickPrototype()
        TileColor.Blueberry -> createOwlPrototype()
        TileColor.Mint -> createFrogPrototype()
        TileColor.Peach -> createBearPrototype()
        TileColor.Grape -> createHippoPrototype()
    }

private fun createAnimalStudioLights(): Entity =
    prototypeRoot("AnimalStudioLights").apply {
        listOf(
            Triple(EulerAngles(-28f, -24f, 0f), 1800f, Color4(1f, 0.96f, 0.9f, 1f)),
            Triple(EulerAngles(12f, 35f, 0f), 900f, Color4(0.86f, 0.94f, 1f, 1f)),
        ).forEachIndexed { index, (rotation, strength, tint) ->
            addChild(
                prototypeRoot("AnimalStudioLight_$index").apply {
                    components[TransformComponent::class.java]?.setEulerAngles(rotation)
                    components.set(
                        DirectionalLightComponent(tint, strength, false),
                    )
                },
            )
        }
    }

private fun createFoxPrototype(): Entity =
    prototypeRoot("fox_piece").apply {
        addChild(
            prototypePart(
                name = "fox_head",
                size = Vector3(0.82f, 0.74f, 0.5f),
                color = Color4(0.98f, 0.12f, 0.018f, 1f),
                cornerRadius = 0.2f,
            ),
        )
        addChild(
            prototypePart(
                name = "fox_ear_left",
                size = Vector3(0.27f, 0.27f, 0.16f),
                color = Color4(0.9f, 0.09f, 0.012f, 1f),
                position = Vector3(-0.27f, 0.43f, -0.02f),
                rotation = EulerAngles(0f, 0f, -45f),
                cornerRadius = 0.025f,
            ),
        )
        addChild(
            prototypePart(
                name = "fox_ear_right",
                size = Vector3(0.27f, 0.27f, 0.16f),
                color = Color4(0.9f, 0.09f, 0.012f, 1f),
                position = Vector3(0.27f, 0.43f, -0.02f),
                rotation = EulerAngles(0f, 0f, 45f),
                cornerRadius = 0.025f,
            ),
        )
        addChild(
            prototypePart(
                name = "fox_muzzle",
                size = Vector3(0.42f, 0.22f, 0.14f),
                color = Color4(1f, 0.92f, 0.84f, 1f),
                position = Vector3(0f, -0.14f, 0.16f),
                cornerRadius = 0.1f,
            ),
        )
        listOf(-1f, 1f).forEach { side ->
            addChild(
                prototypePart(
                    name = "fox_inner_ear_$side",
                    size = Vector3(0.15f, 0.15f, 0.07f),
                    color = Color4(1f, 0.84f, 0.67f, 1f),
                    position = Vector3(side * 0.27f, 0.45f, 0.08f),
                    rotation = EulerAngles(0f, 0f, side * 45f),
                    cornerRadius = 0.018f,
                ),
            )
            addChild(
                prototypePart(
                    name = "fox_cream_cheek_$side",
                    size = Vector3(0.34f, 0.25f, 0.13f),
                    color = Color4(1f, 0.95f, 0.83f, 1f),
                    position = Vector3(side * 0.18f, -0.14f, 0.24f),
                    rotation = EulerAngles(0f, 0f, side * 18f),
                    cornerRadius = 0.07f,
                ),
            )
        }
        addChild(faceEyes("fox_eyes", eyeY = 0.08f))
        addChild(candyNose("fox_nose", Vector3(0f, -0.04f, 0.34f)))
        addChild(candySmile("fox_smile", -0.21f, 0.33f))
    }

private fun createBearPrototype(): Entity =
    prototypeRoot("bear_piece").apply {
        addChild(
            prototypePart(
                name = "bear_head",
                size = Vector3(0.98f, 0.78f, 0.52f),
                color = Color4(0.52f, 0.23f, 0.065f, 1f),
                cornerRadius = 0.34f,
            ),
        )
        addChild(
            prototypePart(
                name = "bear_ear_left",
                size = Vector3(0.29f, 0.29f, 0.14f),
                color = Color4(0.38f, 0.14f, 0.035f, 1f),
                position = Vector3(-0.35f, 0.36f, -0.03f),
                cornerRadius = 0.145f,
            ),
        )
        addChild(
            prototypePart(
                name = "bear_ear_right",
                size = Vector3(0.29f, 0.29f, 0.14f),
                color = Color4(0.38f, 0.14f, 0.035f, 1f),
                position = Vector3(0.35f, 0.36f, -0.03f),
                cornerRadius = 0.145f,
            ),
        )
        addChild(
            prototypePart(
                name = "bear_muzzle",
                size = Vector3(0.52f, 0.32f, 0.16f),
                color = Color4(1f, 0.88f, 0.65f, 1f),
                position = Vector3(0f, -0.14f, 0.29f),
                cornerRadius = 0.15f,
            ),
        )
        listOf(-1f, 1f).forEach { side ->
            addChild(
                prototypePart(
                    name = "bear_inner_ear_$side",
                    size = Vector3(0.16f, 0.16f, 0.05f),
                    color = Color4(0.82f, 0.52f, 0.25f, 1f),
                    position = Vector3(side * 0.35f, 0.36f, 0.065f),
                    cornerRadius = 0.08f,
                ),
            )
        }
        addChild(faceEyes("bear_eyes", eyeY = 0.1f))
        addChild(candyNose("bear_nose", Vector3(0f, -0.06f, 0.4f)))
        addChild(candySmile("bear_smile", -0.22f, 0.4f))
    }

private fun createFrogPrototype(): Entity =
    prototypeRoot("frog_piece").apply {
        addChild(
            prototypePart(
                name = "frog_head",
                size = Vector3(1f, 0.72f, 0.5f),
                color = Color4(0.1f, 0.91f, 0.08f, 1f),
                cornerRadius = 0.24f,
            ),
        )
        addChild(
            prototypePart(
                name = "frog_eye_left",
                size = Vector3(0.34f, 0.34f, 0.26f),
                color = Color4(0.15f, 0.94f, 0.12f, 1f),
                position = Vector3(-0.28f, 0.34f, 0.08f),
                cornerRadius = 0.16f,
            ),
        )
        addChild(
            prototypePart(
                name = "frog_eye_right",
                size = Vector3(0.34f, 0.34f, 0.26f),
                color = Color4(0.15f, 0.94f, 0.12f, 1f),
                position = Vector3(0.28f, 0.34f, 0.08f),
                cornerRadius = 0.16f,
            ),
        )
        addChild(candyEyePair("frog_eyes", 0.34f, 0.24f, 0.28f, 0.26f))
        addChild(candySmile("frog_smile", -0.16f, 0.27f))
        addChild(candyCheeks("frog_cheeks", -0.08f, 0.22f))
    }

private fun createChickPrototype(): Entity =
    prototypeRoot("chick_piece").apply {
        addChild(
            prototypePart(
                name = "chick_head",
                size = Vector3(0.78f, 0.78f, 0.48f),
                color = Color4(1f, 0.84f, 0.025f, 1f),
                cornerRadius = 0.26f,
            ),
        )
        addChild(
            prototypePart(
                name = "chick_beak",
                size = Vector3(0.24f, 0.14f, 0.14f),
                color = Color4(1f, 0.56f, 0.12f, 1f),
                position = Vector3(0f, -0.09f, 0.32f),
                cornerRadius = 0.06f,
            ),
        )
        addChild(
            prototypePart(
                name = "chick_crest_left",
                size = Vector3(0.12f, 0.2f, 0.08f),
                color = Color4(1f, 0.72f, 0.1f, 1f),
                position = Vector3(-0.12f, 0.48f, 0.04f),
                rotation = EulerAngles(0f, 0f, -18f),
                cornerRadius = 0.05f,
            ),
        )
        addChild(
            prototypePart(
                name = "chick_crest_center",
                size = Vector3(0.12f, 0.22f, 0.08f),
                color = Color4(1f, 0.72f, 0.1f, 1f),
                position = Vector3(0f, 0.5f, 0.04f),
                cornerRadius = 0.05f,
            ),
        )
        addChild(
            prototypePart(
                name = "chick_crest_right",
                size = Vector3(0.12f, 0.2f, 0.08f),
                color = Color4(1f, 0.72f, 0.1f, 1f),
                position = Vector3(0.12f, 0.48f, 0.04f),
                rotation = EulerAngles(0f, 0f, 18f),
                cornerRadius = 0.05f,
            ),
        )
        addChild(faceEyes("chick_eyes", eyeY = 0.1f))
        addChild(candyCheeks("chick_cheeks", -0.1f, 0.22f))
    }

private fun createOwlPrototype(): Entity =
    prototypeRoot("owl_piece").apply {
        addChild(
            prototypePart(
                name = "owl_head",
                size = Vector3(0.86f, 0.8f, 0.54f),
                color = Color4(0.53f, 0.09f, 0.94f, 1f),
                cornerRadius = 0.18f,
            ),
        )
        addChild(
            prototypePart(
                name = "owl_ear_left",
                size = Vector3(0.18f, 0.24f, 0.12f),
                color = Color4(0.38f, 0.42f, 0.82f, 1f),
                position = Vector3(-0.27f, 0.37f, -0.02f),
                rotation = EulerAngles(0f, 0f, -14f),
                cornerRadius = 0.06f,
            ),
        )
        addChild(
            prototypePart(
                name = "owl_ear_right",
                size = Vector3(0.18f, 0.24f, 0.12f),
                color = Color4(0.38f, 0.42f, 0.82f, 1f),
                position = Vector3(0.27f, 0.37f, -0.02f),
                rotation = EulerAngles(0f, 0f, 14f),
                cornerRadius = 0.06f,
            ),
        )
        addChild(
            prototypePart(
                name = "owl_eye_left",
                size = Vector3(0.38f, 0.43f, 0.14f),
                color = Color4(0.98f, 0.98f, 1f, 1f),
                position = Vector3(-0.2f, 0.07f, 0.25f),
                cornerRadius = 0.16f,
            ),
        )
        addChild(
            prototypePart(
                name = "owl_eye_right",
                size = Vector3(0.38f, 0.43f, 0.14f),
                color = Color4(0.98f, 0.98f, 1f, 1f),
                position = Vector3(0.2f, 0.07f, 0.25f),
                cornerRadius = 0.16f,
            ),
        )
        addChild(candyEyePair("owl_eyes", 0.09f, 0.34f, 0.2f, 0.22f))
        addChild(
            prototypePart(
                name = "owl_beak",
                size = Vector3(0.15f, 0.2f, 0.16f),
                color = Color4(1f, 0.68f, 0.18f, 1f),
                position = Vector3(0f, -0.14f, 0.35f),
                cornerRadius = 0.05f,
            ),
        )
    }

private fun createHippoPrototype(): Entity =
    prototypeRoot("hippo_piece").apply {
        addChild(
            prototypePart(
                name = "hippo_head",
                size = Vector3(0.9f, 0.82f, 0.54f),
                color = Color4(0.035f, 0.35f, 1f, 1f),
                cornerRadius = 0.24f,
            ),
        )
        addChild(
            prototypePart(
                name = "hippo_ear_left",
                size = Vector3(0.25f, 0.25f, 0.14f),
                color = Color4(0.06f, 0.43f, 1f, 1f),
                position = Vector3(-0.3f, 0.37f, -0.03f),
                cornerRadius = 0.16f,
            ),
        )
        addChild(
            prototypePart(
                name = "hippo_ear_right",
                size = Vector3(0.25f, 0.25f, 0.14f),
                color = Color4(0.06f, 0.43f, 1f, 1f),
                position = Vector3(0.3f, 0.37f, -0.03f),
                cornerRadius = 0.16f,
            ),
        )
        addChild(candyEyePair("hippo_eyes", 0.17f, 0.29f, 0.19f, 0.22f))
        addChild(
            prototypePart(
                name = "hippo_muzzle",
                size = Vector3(0.7f, 0.36f, 0.24f),
                color = Color4(0.23f, 0.64f, 1f, 1f),
                position = Vector3(0f, -0.13f, 0.32f),
                cornerRadius = 0.14f,
            ),
        )
        listOf(-1f, 1f).forEach { side ->
            addChild(
                prototypePart(
                    name = "hippo_nostril_$side",
                    size = Vector3(0.065f, 0.045f, 0.025f),
                    color = Color4(0.03f, 0.2f, 0.58f, 1f),
                    position = Vector3(side * 0.2f, -0.055f, 0.425f),
                    cornerRadius = 0.02f,
                ),
            )
            addChild(
                prototypePart(
                    name = "hippo_tooth_$side",
                    size = Vector3(0.09f, 0.13f, 0.065f),
                    color = Color4(1f, 0.94f, 0.82f, 1f),
                    position = Vector3(side * 0.14f, -0.3f, 0.33f),
                    cornerRadius = 0.035f,
                ),
            )
        }
    }

private fun prototypeRoot(name: String): Entity =
    Entity().apply {
        setName(name)
        components.set(TransformComponent())
    }

private fun faceEyes(
    name: String,
    eyeY: Float,
): Entity = candyEyePair(name, eyeY, 0.29f, 0.18f, 0.23f)

private fun candyEyePair(
    name: String,
    eyeY: Float,
    eyeZ: Float,
    spacing: Float,
    diameter: Float,
): Entity =
    prototypeRoot(name).apply {
        listOf(-1f, 1f).forEach { side ->
            val x = side * spacing
            addChild(
                prototypePart(
                    "${name}_white_$side", Vector3(diameter, diameter * 1.12f, 0.09f),
                    Color4(1f, 0.97f, 0.9f, 1f), Vector3(x, eyeY, eyeZ),
                    cornerRadius = 0.04f,
                ),
            )
            addChild(
                prototypePart(
                    "${name}_iris_$side", Vector3(diameter * 0.73f, diameter * 0.83f, 0.055f),
                    Color4(0.14f, 0.055f, 0.025f, 1f), Vector3(x, eyeY, eyeZ + 0.052f),
                    cornerRadius = 0.02f,
                ),
            )
            addChild(
                prototypePart(
                    "${name}_pupil_$side", Vector3(diameter * 0.51f, diameter * 0.6f, 0.03f),
                    Color4(0.015f, 0.008f, 0.025f, 1f), Vector3(x, eyeY, eyeZ + 0.082f),
                    cornerRadius = 0.015f,
                ),
            )
            addChild(
                prototypePart(
                    "${name}_glint_$side", Vector3(diameter * 0.24f, diameter * 0.24f, 0.025f),
                    Color4(1f, 1f, 1f, 1f),
                    Vector3(x - diameter * 0.15f, eyeY + diameter * 0.18f, eyeZ + 0.101f),
                    cornerRadius = 0.01f,
                ),
            )
        }
    }

private fun candyNose(name: String, position: Vector3): Entity =
    prototypePart(
        name, Vector3(0.14f, 0.09f, 0.08f), Color4(0.12f, 0.035f, 0.018f, 1f),
        position, cornerRadius = 0.035f,
    )

private fun candySmile(name: String, y: Float, z: Float): Entity =
    prototypeRoot(name).apply {
        for (index in 0..6) {
            val x = (index - 3) * 0.035f
            addChild(
                prototypePart(
                    "${name}_$index", Vector3(0.055f, 0.032f, 0.025f),
                    Color4(0.15f, 0.05f, 0.025f, 1f),
                    Vector3(x, y + 2.6f * x * x, z),
                    cornerRadius = 0.01f,
                ),
            )
        }
    }

private fun candyCheeks(name: String, y: Float, z: Float): Entity =
    prototypeRoot(name).apply {
        listOf(-1f, 1f).forEach { side ->
            addChild(
                prototypePart(
                    "${name}_$side", Vector3(0.12f, 0.08f, 0.025f),
                    Color4(1f, 0.4f, 0.22f, 1f), Vector3(side * 0.27f, y, z),
                    cornerRadius = 0.03f,
                ),
            )
        }
    }

private fun prototypePart(
    name: String,
    size: Vector3,
    color: Color4,
    position: Vector3 = Vector3(0f, 0f, 0f),
    rotation: EulerAngles = EulerAngles(0f, 0f, 0f),
    cornerRadius: Float,
): ModelEntity =
    ModelEntity(
        if (name.startsWith("ice_") || name.startsWith("route_") || name.startsWith("pod_exit") ||
            name.startsWith("gem_") || name == "pink_gem" || name.startsWith("bird_brow")) {
            MeshCatalog.campaignCube
        } else if (name.startsWith("fox_ear_") || name.startsWith("fox_inner_ear_")) {
            MeshCatalog.animalEar
        } else {
            MeshCatalog.animalSphere
        },
        AnimalMaterialCatalog.material(color),
    ).apply {
        setName(name)
        components.set(
            TransformComponent().apply {
                setPosition(position)
                setScaleVector(size)
                setEulerAngles(rotation)
            },
        )
    }

private fun updateRenderable(
    entity: ModelEntity,
    mesh: MeshResource,
    material: UnlitMaterial,
    visible: Boolean,
) {
    entity.components[ModelComponent::class.java]?.apply {
        isVisible = visible
        if (this.mesh != mesh) {
            this.mesh = mesh
        }
        if (materials.size == 0) {
            materials.add(material)
        } else if (materials[0] != material) {
            materials[0] = material
        }
    }
}

private fun headMeshFor(color: TileColor): MeshResource =
    MeshCatalog.gemShellMesh

private fun scaleFor(
    color: TileColor,
    boost: Float,
): Vector3 {
    val base =
        when (color) {
            TileColor.Strawberry -> Vector3(0.112f, 0.102f, 0.064f)
            TileColor.Lemon -> Vector3(0.102f, 0.096f, 0.062f)
            TileColor.Blueberry -> Vector3(0.098f, 0.116f, 0.064f)
            TileColor.Mint -> Vector3(0.108f, 0.108f, 0.062f)
            TileColor.Peach -> Vector3(0.114f, 0.098f, 0.064f)
            TileColor.Grape -> Vector3(0.104f, 0.116f, 0.064f)
        }
    return Vector3(base.x * boost, base.y * boost, base.z * boost)
}

private fun rotationFor(
    color: TileColor,
    special: SpecialTileKind,
    highlighted: Boolean,
): EulerAngles {
    val yaw =
        when (color) {
            TileColor.Strawberry -> -8f
            TileColor.Lemon -> 0f
            TileColor.Blueberry -> 4f
            TileColor.Mint -> 0f
            TileColor.Peach -> 8f
            TileColor.Grape -> -4f
        }
    val roll =
        when (special) {
            SpecialTileKind.HorizontalLine -> baseRollFor(color) + 10f
            SpecialTileKind.VerticalLine -> baseRollFor(color) - 10f
            SpecialTileKind.Bomb -> baseRollFor(color) + 18f
            SpecialTileKind.ColorBomb -> baseRollFor(color) + 24f
            SpecialTileKind.None -> baseRollFor(color)
        }
    val pitch = if (highlighted) -9f else 0f
    return EulerAngles(pitch, yaw, roll)
}

private fun specialLift(special: SpecialTileKind): Float =
    when (special) {
        SpecialTileKind.None -> 0f
        SpecialTileKind.HorizontalLine,
        SpecialTileKind.VerticalLine -> 0.014f
        SpecialTileKind.Bomb -> 0.02f
        SpecialTileKind.ColorBomb -> 0.028f
    }

private fun tileColor(
    color: TileColor,
    special: SpecialTileKind,
    selected: Boolean,
    bursting: Boolean,
    burstKind: Match3EffectKind?,
    highlighted: Boolean,
    effectKind: Match3EffectKind?,
): Color4 {
    val base =
        when (color) {
            TileColor.Strawberry -> Color4(0.98f, 0.18f, 0.18f, 1f)
            TileColor.Lemon -> Color4(1f, 0.87f, 0.14f, 1f)
            TileColor.Blueberry -> Color4(0.14f, 0.58f, 1f, 1f)
            TileColor.Mint -> Color4(0.19f, 0.94f, 0.52f, 1f)
            TileColor.Peach -> Color4(1f, 0.61f, 0.18f, 1f)
            TileColor.Grape -> Color4(0.88f, 0.16f, 0.96f, 1f)
        }
    val specialTint =
        when (special) {
            SpecialTileKind.None -> base
            SpecialTileKind.HorizontalLine,
            SpecialTileKind.VerticalLine -> Color4(base.red, base.green, 1f, 1f)
            SpecialTileKind.Bomb -> Color4(1f, 0.38f, 0.35f, 1f)
            SpecialTileKind.ColorBomb -> Color4(0.98f, 0.96f, 0.72f, 1f)
        }
    val effectTint =
        if (!highlighted || effectKind == null) {
            specialTint
        } else {
            when (effectKind) {
                Match3EffectKind.Normal -> Color4(1f, 0.94f, 0.7f, 1f)
                Match3EffectKind.Line -> Color4(0.78f, 0.92f, 1f, 1f)
                Match3EffectKind.Bomb -> Color4(1f, 0.62f, 0.38f, 1f)
                Match3EffectKind.ColorBomb -> Color4(1f, 0.96f, 0.54f, 1f)
                Match3EffectKind.Combo -> Color4(1f, 0.82f, 0.62f, 1f)
            }
        }
    val burstTint =
        if (!bursting || burstKind == null) {
            effectTint
        } else {
            when (burstKind) {
                Match3EffectKind.Normal -> Color4(1f, 0.98f, 0.86f, 1f)
                Match3EffectKind.Line -> Color4(0.94f, 0.99f, 1f, 1f)
                Match3EffectKind.Bomb -> Color4(1f, 0.88f, 0.76f, 1f)
                Match3EffectKind.ColorBomb -> Color4(1f, 0.99f, 0.82f, 1f)
                Match3EffectKind.Combo -> Color4(1f, 0.95f, 0.84f, 1f)
            }
        }
    return if (selected || highlighted || bursting) {
        Color4(
            (burstTint.red + if (bursting) 0.18f else 0.12f).coerceIn(0f, 1f),
            (burstTint.green + if (bursting) 0.18f else 0.12f).coerceIn(0f, 1f),
            (burstTint.blue + if (bursting) 0.18f else 0.12f).coerceIn(0f, 1f),
            1f,
        )
    } else {
        burstTint
    }
}

private fun gemShellColor(
    color: TileColor,
    special: SpecialTileKind,
    selected: Boolean,
    bursting: Boolean,
    burstKind: Match3EffectKind?,
    highlighted: Boolean,
    effectKind: Match3EffectKind?,
): Color4 {
    val base = tileColor(color, special, false, bursting, burstKind, highlighted, effectKind)
    val alpha =
        when {
            bursting -> 0.92f
            highlighted -> 0.88f
            selected -> 0.84f
            special != SpecialTileKind.None -> 0.8f
            else -> 0.78f
        }
    return Color4(
        (base.red * 0.86f + 0.14f).coerceIn(0f, 1f),
        (base.green * 0.86f + 0.14f).coerceIn(0f, 1f),
        (base.blue * 0.9f + 0.16f).coerceIn(0f, 1f),
        alpha,
    )
}

private fun fireworkSparkColor(
    color: TileColor,
    bursting: Boolean,
    burstKind: Match3EffectKind?,
    intensity: Float,
): Color4 {
    val base =
        when (burstKind) {
            Match3EffectKind.Line -> Color4(0.8f, 0.96f, 1f, 1f)
            Match3EffectKind.Bomb -> Color4(1f, 0.78f, 0.46f, 1f)
            Match3EffectKind.ColorBomb -> Color4(1f, 0.98f, 0.8f, 1f)
            Match3EffectKind.Combo -> Color4(1f, 0.9f, 0.72f, 1f)
            Match3EffectKind.Normal,
            null -> tileColor(color, SpecialTileKind.None, false, bursting, null, false, null)
        }
    return Color4(
        (base.red * intensity + 0.16f).coerceIn(0f, 1f),
        (base.green * intensity + 0.16f).coerceIn(0f, 1f),
        (base.blue * intensity + 0.16f).coerceIn(0f, 1f),
        (0.92f - (1f - intensity) * 0.24f).coerceIn(0.4f, 1f),
    )
}

private fun glowColor(
    color: TileColor,
    special: SpecialTileKind,
    selected: Boolean,
    bursting: Boolean,
    burstKind: Match3EffectKind?,
    highlighted: Boolean,
    effectKind: Match3EffectKind?,
): Color4 {
    if (bursting && burstKind != null) {
        return when (burstKind) {
            Match3EffectKind.Normal -> Color4(1f, 0.98f, 0.88f, 0.82f)
            Match3EffectKind.Line -> Color4(0.9f, 0.99f, 1f, 0.88f)
            Match3EffectKind.Bomb -> Color4(1f, 0.84f, 0.68f, 0.9f)
            Match3EffectKind.ColorBomb -> Color4(1f, 0.99f, 0.76f, 0.96f)
            Match3EffectKind.Combo -> Color4(1f, 0.95f, 0.82f, 1f)
        }
    }
    val base =
        tileColor(
            color = color,
            special = special,
            selected = false,
            bursting = false,
            burstKind = null,
            highlighted = false,
            effectKind = null,
        )
    val boosted =
        if (highlighted && effectKind != null) {
            when (effectKind) {
                Match3EffectKind.Normal -> Color4(1f, 0.96f, 0.76f, 0.42f)
                Match3EffectKind.Line -> Color4(0.72f, 0.92f, 1f, 0.48f)
                Match3EffectKind.Bomb -> Color4(1f, 0.62f, 0.32f, 0.5f)
                Match3EffectKind.ColorBomb -> Color4(1f, 0.96f, 0.54f, 0.56f)
                Match3EffectKind.Combo -> Color4(1f, 0.84f, 0.6f, 0.6f)
            }
        } else {
            Color4(
                (base.red + 0.08f).coerceIn(0f, 1f),
                (base.green + 0.08f).coerceIn(0f, 1f),
                (base.blue + 0.1f).coerceIn(0f, 1f),
                if (selected || special != SpecialTileKind.None) 0.5f else 0.3f,
            )
        }
    return boosted
}

private fun sparkleColor(
    color: TileColor,
    selected: Boolean,
    bursting: Boolean,
    highlighted: Boolean,
): Color4 {
    val alpha =
        when {
            bursting -> 1f
            highlighted -> 0.98f
            selected -> 0.92f
            else -> 0.8f
        }
    return when (color) {
        TileColor.Strawberry -> Color4(1f, 0.92f, 0.92f, alpha)
        TileColor.Lemon -> Color4(1f, 0.99f, 0.9f, alpha)
        TileColor.Blueberry -> Color4(0.9f, 0.97f, 1f, alpha)
        TileColor.Mint -> Color4(0.92f, 1f, 0.94f, alpha)
        TileColor.Peach -> Color4(1f, 0.95f, 0.88f, alpha)
        TileColor.Grape -> Color4(1f, 0.92f, 1f, alpha)
    }
}

private fun gemCoreColor(
    color: TileColor,
    bursting: Boolean,
    burstKind: Match3EffectKind?,
    highlighted: Boolean,
    effectKind: Match3EffectKind?,
): Color4 {
    val base = tileColor(color, SpecialTileKind.None, false, bursting, burstKind, highlighted, effectKind)
    return Color4(
        (base.red * 0.98f + 0.1f).coerceIn(0f, 1f),
        (base.green * 0.98f + 0.1f).coerceIn(0f, 1f),
        (base.blue * 1f + 0.12f).coerceIn(0f, 1f),
        1f,
    )
}

private fun gemCrownColor(
    color: TileColor,
    bursting: Boolean,
    burstKind: Match3EffectKind?,
    highlighted: Boolean,
    effectKind: Match3EffectKind?,
): Color4 {
    val base = tileColor(color, SpecialTileKind.None, false, bursting, burstKind, highlighted, effectKind)
    return Color4(
        (base.red + 0.28f).coerceIn(0f, 1f),
        (base.green + 0.28f).coerceIn(0f, 1f),
        (base.blue + 0.28f).coerceIn(0f, 1f),
        0.98f,
    )
}

private fun gemPavilionColor(
    color: TileColor,
    bursting: Boolean,
    burstKind: Match3EffectKind?,
    highlighted: Boolean,
    effectKind: Match3EffectKind?,
): Color4 {
    val base = tileColor(color, SpecialTileKind.None, false, bursting, burstKind, highlighted, effectKind)
    return Color4(
        (base.red * 0.9f + 0.05f).coerceIn(0f, 1f),
        (base.green * 0.9f + 0.05f).coerceIn(0f, 1f),
        (base.blue * 0.94f + 0.06f).coerceIn(0f, 1f),
        0.94f,
    )
}

private fun gemFacetColor(
    color: TileColor,
    selected: Boolean,
    bursting: Boolean,
    burstKind: Match3EffectKind?,
    highlighted: Boolean,
    effectKind: Match3EffectKind?,
): Color4 {
    val base = tileColor(color, SpecialTileKind.None, selected, bursting, burstKind, highlighted, effectKind)
    return Color4(
        (base.red + 0.34f).coerceIn(0f, 1f),
        (base.green + 0.34f).coerceIn(0f, 1f),
        (base.blue + 0.34f).coerceIn(0f, 1f),
        1f,
    )
}

private fun gemGirdleColor(
    color: TileColor,
    selected: Boolean,
    bursting: Boolean,
    burstKind: Match3EffectKind?,
    highlighted: Boolean,
    effectKind: Match3EffectKind?,
): Color4 {
    val base = tileColor(color, SpecialTileKind.None, selected, bursting, burstKind, highlighted, effectKind)
    return Color4(
        (base.red + 0.4f).coerceIn(0f, 1f),
        (base.green + 0.4f).coerceIn(0f, 1f),
        (base.blue + 0.38f).coerceIn(0f, 1f),
        1f,
    )
}

private fun gemCornerFacetColor(
    color: TileColor,
    selected: Boolean,
    bursting: Boolean,
    burstKind: Match3EffectKind?,
    highlighted: Boolean,
    effectKind: Match3EffectKind?,
    topFacet: Boolean,
): Color4 {
    val base = tileColor(color, SpecialTileKind.None, selected, bursting, burstKind, highlighted, effectKind)
    val lift = if (topFacet) 0.3f else 0.2f
    val alpha = if (topFacet) 0.96f else 0.88f
    return Color4(
        (base.red + lift).coerceIn(0f, 1f),
        (base.green + lift).coerceIn(0f, 1f),
        (base.blue + lift).coerceIn(0f, 1f),
        alpha,
    )
}

private fun gemHighlightColor(
    color: TileColor,
    selected: Boolean,
    bursting: Boolean,
    highlighted: Boolean,
): Color4 {
    val alpha =
        when {
            bursting -> 1f
            highlighted -> 0.96f
            selected -> 0.92f
            else -> 0.84f
        }
    return when (color) {
        TileColor.Strawberry -> Color4(1f, 0.98f, 0.98f, alpha)
        TileColor.Lemon -> Color4(1f, 1f, 0.96f, alpha)
        TileColor.Blueberry -> Color4(0.95f, 0.99f, 1f, alpha)
        TileColor.Mint -> Color4(0.96f, 1f, 0.98f, alpha)
        TileColor.Peach -> Color4(1f, 0.98f, 0.94f, alpha)
        TileColor.Grape -> Color4(1f, 0.96f, 1f, alpha)
    }
}

private fun gemCrownRoll(
    color: TileColor,
    bursting: Boolean,
): Float =
    when (color) {
        TileColor.Strawberry -> if (bursting) 8f else 0f
        TileColor.Lemon -> 45f
        TileColor.Blueberry -> if (bursting) 18f else 12f
        TileColor.Mint -> if (bursting) 12f else 6f
        TileColor.Peach -> if (bursting) 24f else 18f
        TileColor.Grape -> if (bursting) 36f else 30f
    }

private fun gemPavilionRoll(
    color: TileColor,
    bursting: Boolean,
): Float =
    when (color) {
        TileColor.Strawberry -> if (bursting) -10f else -6f
        TileColor.Lemon -> if (bursting) 42f else 45f
        TileColor.Blueberry -> if (bursting) -18f else -12f
        TileColor.Mint -> if (bursting) 10f else 4f
        TileColor.Peach -> if (bursting) 16f else 10f
        TileColor.Grape -> if (bursting) 28f else 22f
    }

private fun shouldShowPrimaryFlare(
    bursting: Boolean,
    burstKind: Match3EffectKind?,
    highlighted: Boolean,
): Boolean {
    if (bursting) {
        return when (burstKind) {
            Match3EffectKind.Normal -> false
            Match3EffectKind.Bomb -> false
            else -> true
        }
    }
    return highlighted
}

private fun shouldShowSecondaryFlare(
    bursting: Boolean,
    burstKind: Match3EffectKind?,
    highlighted: Boolean,
): Boolean {
    if (bursting) {
        return when (burstKind) {
            Match3EffectKind.Normal -> false
            Match3EffectKind.Line -> false
            else -> true
        }
    }
    return highlighted
}

private fun flareColor(
    color: TileColor,
    special: SpecialTileKind,
    selected: Boolean,
    bursting: Boolean,
    burstKind: Match3EffectKind?,
    highlighted: Boolean,
    effectKind: Match3EffectKind?,
    primary: Boolean,
): Color4 {
    if (bursting && burstKind != null) {
        return when (burstKind) {
            Match3EffectKind.Normal -> Color4(1f, 0.98f, 0.92f, if (primary) 0.74f else 0.46f)
            Match3EffectKind.Line -> Color4(0.92f, 0.98f, 1f, if (primary) 1f else 0.32f)
            Match3EffectKind.Bomb -> Color4(1f, 0.96f, 0.9f, if (primary) 0.22f else 1f)
            Match3EffectKind.ColorBomb -> Color4(1f, 1f, 0.98f, if (primary) 1f else 0.92f)
            Match3EffectKind.Combo -> Color4(1f, 0.98f, 0.94f, if (primary) 1f else 1f)
        }
    }
    if (highlighted && effectKind != null) {
        return when (effectKind) {
            Match3EffectKind.Normal -> Color4(1f, 0.96f, 0.84f, if (primary) 0.54f else 0.32f)
            Match3EffectKind.Line -> Color4(0.9f, 0.98f, 1f, if (primary) 0.72f else 0.5f)
            Match3EffectKind.Bomb -> Color4(1f, 0.82f, 0.68f, if (primary) 0.78f else 0.56f)
            Match3EffectKind.ColorBomb -> Color4(1f, 0.98f, 0.78f, if (primary) 0.84f else 0.62f)
            Match3EffectKind.Combo -> Color4(1f, 0.92f, 0.78f, if (primary) 0.88f else 0.66f)
        }
    }
    val tint =
        tileColor(
            color = color,
            special = special,
            selected = false,
            bursting = false,
            burstKind = null,
            highlighted = false,
            effectKind = null,
        )
    val alpha =
        when {
            highlighted -> if (primary) 0.7f else 0.5f
            selected -> if (primary) 0.52f else 0.36f
            special != SpecialTileKind.None -> if (primary) 0.42f else 0.28f
            else -> 0.22f
        }
    return Color4(
        (tint.red + 0.18f).coerceIn(0f, 1f),
        (tint.green + 0.18f).coerceIn(0f, 1f),
        (tint.blue + 0.18f).coerceIn(0f, 1f),
        alpha,
    )
}

private fun burstProfileFor(kind: Match3EffectKind?): BurstProfile? =
    when (kind) {
        Match3EffectKind.Normal ->
            BurstProfile(
                primaryScale = Vector2Like(0.01f, 0.01f),
                secondaryScale = Vector2Like(1.02f, 1.02f),
                primaryRoll = 0f,
                secondaryRoll = 45f,
            )
        Match3EffectKind.Line ->
            BurstProfile(
                primaryScale = Vector2Like(0.28f, 2.45f),
                secondaryScale = Vector2Like(0.18f, 0.52f),
                primaryRoll = 0f,
                secondaryRoll = 0f,
            )
        Match3EffectKind.Bomb ->
            BurstProfile(
                primaryScale = Vector2Like(0.16f, 0.16f),
                secondaryScale = Vector2Like(2.05f, 2.05f),
                primaryRoll = 0f,
                secondaryRoll = 0f,
            )
        Match3EffectKind.ColorBomb ->
            BurstProfile(
                primaryScale = Vector2Like(2.05f, 2.05f),
                secondaryScale = Vector2Like(1.7f, 1.7f),
                primaryRoll = 0f,
                secondaryRoll = 45f,
            )
        Match3EffectKind.Combo ->
            BurstProfile(
                primaryScale = Vector2Like(1.34f, 2.35f),
                secondaryScale = Vector2Like(1.88f, 1.88f),
                primaryRoll = 0f,
                secondaryRoll = 45f,
            )
        null -> null
    }

private fun sparkleOffsetX(color: TileColor): Float =
    when (color) {
        TileColor.Strawberry -> -0.02f
        TileColor.Lemon -> -0.014f
        TileColor.Blueberry -> -0.01f
        TileColor.Mint -> -0.018f
        TileColor.Peach -> -0.012f
        TileColor.Grape -> -0.016f
    }

private fun sparkleOffsetY(color: TileColor): Float =
    when (color) {
        TileColor.Strawberry -> 0.026f
        TileColor.Lemon -> 0.03f
        TileColor.Blueberry -> 0.038f
        TileColor.Mint -> 0.022f
        TileColor.Peach -> 0.028f
        TileColor.Grape -> 0.036f
    }

private fun sparkleRoll(
    color: TileColor,
    special: SpecialTileKind,
    bursting: Boolean,
    highlighted: Boolean,
): Float {
    val base =
        when (color) {
            TileColor.Strawberry -> 12f
            TileColor.Lemon -> 45f
            TileColor.Blueberry -> 30f
            TileColor.Mint -> 18f
            TileColor.Peach -> 38f
            TileColor.Grape -> 24f
        }
    val specialBoost =
        when (special) {
            SpecialTileKind.None -> 0f
            SpecialTileKind.HorizontalLine,
            SpecialTileKind.VerticalLine -> 10f
            SpecialTileKind.Bomb -> 18f
            SpecialTileKind.ColorBomb -> 26f
        }
    return base + specialBoost + if (bursting) 40f else if (highlighted) 20f else 0f
}

private fun baseRollFor(color: TileColor): Float =
    when (color) {
        TileColor.Strawberry -> -3f
        TileColor.Lemon -> 2f
        TileColor.Blueberry -> -3f
        TileColor.Mint -> 0f
        TileColor.Peach -> 3f
        TileColor.Grape -> -2f
    }

@Composable
private fun StageHudPanel(
    state: GameUiState,
) {
    Column(
        modifier = Modifier.width(560.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF194F54), Color(0xFF102F38))))
            .border(1.dp, Color(0xFFBFA66B), RoundedCornerShape(28.dp))
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "第 ${state.session.levelIndex} 关",
                style = PicoTheme.typography.titleLarge,
                fontSize = 32.sp,
                color = Color(0xFFFFE2A2),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.session.goalProgress.joinToString(" · ") { "${it.currentCount.coerceAtMost(it.objective.targetCount)}/${it.objective.targetCount}" },
                    style = PicoTheme.typography.titleMedium,
                    fontSize = 32.sp,
                    color = Color(0xFFFFFAED),
                )
                Text(
                    text = if (state.isWin) "目标达成" else state.session.goalProgress.joinToString(" · ") { it.objective.label },
                    style = PicoTheme.typography.labelMedium,
                    fontSize = 20.sp,
                    color = Color(0xFFB8D9D5),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.session.movesRemaining.toString(),
                    style = PicoTheme.typography.titleLarge,
                    fontSize = 36.sp,
                    color = if (state.session.movesRemaining <= 5) Color(0xFFFFB793) else Color(0xFFFFFAED),
                )
                Text(
                    text = "剩余步数",
                    style = PicoTheme.typography.labelMedium,
                    fontSize = 20.sp,
                    color = Color(0xFFB8D9D5),
                )
            }
        }
        Canvas(Modifier.fillMaxWidth().height(5.dp)) {
            val progress = state.session.goalProgress.map {
                (it.currentCount.toFloat() / it.objective.targetCount).coerceIn(0f, 1f)
            }.average().toFloat().coerceIn(0f, 1f)
            drawRoundRect(Color(0xFF376067), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height))
            drawRoundRect(
                Brush.horizontalGradient(listOf(Color(0xFF73D9BB), Color(0xFFE8CD88))),
                size = androidx.compose.ui.geometry.Size(size.width * progress, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height),
            )
        }
        Text(
            text = "${state.session.level.name} · ${"★".repeat(state.session.level.difficulty)}" +
                if (state.session.level.mode == CampaignMode.Snails) {
                    " · 路线 ${state.displayBoard.snails.sumOf { it.position }}/${state.displayBoard.snails.sumOf { it.route.lastIndex }}"
                } else "",
            fontSize = 18.sp,
            color = Color(0xFFB8D9D5),
        )
    }
}

@Composable
private fun StageLevelActions(
    state: GameUiState,
    onEvent: (GameEvent) -> Unit,
) {
    val settled = !state.isResolvingTurn
    val celebrationFinished = state.activeCelebration == null
    // Keep attachment bounds stable while the visible card fits its content.
    Box(Modifier.width(620.dp).height(360.dp), contentAlignment = Alignment.TopCenter) {
    Column(
        // Reserve the completed-level layout from the first attachment frame so
        // chapter and level buttons remain inside the panel after its content changes.
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF194F54), Color(0xFF102F38))))
            .border(1.dp, Color(0xFFBFA66B), RoundedCornerShape(28.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.isWin && settled && celebrationFinished) {
            Text(
                text = "关卡完成",
                style = PicoTheme.typography.titleLarge,
                fontSize = 32.sp,
                color = Color(0xFFFFFAED),
            )
            Text(
                text = "★".repeat(state.session.starsEarned.coerceIn(1, 3)),
                style = PicoTheme.typography.titleMedium,
                fontSize = 28.sp,
                color = Match3DesignTokens.AccentGold,
            )
            StageActionButton(
                onClick = { onEvent(GameEvent.NextLevel) },
                modifier = Modifier.width(240.dp).height(64.dp),
            ) {
                Text(
                    text = if (state.session.levelIndex < 25) "下一关" else "再玩一轮",
                    fontSize = 28.sp,
                )
            }
        } else if ((state.isOutOfMoves || state.canRestart) && !state.isWin && settled) {
            Text(
                text = if (state.isOutOfMoves) "差一点点，再试一次" else "换个棋盘，再来一次",
                style = PicoTheme.typography.titleMedium,
                fontSize = 28.sp,
                color = Color(0xFFFFFAED),
            )
            StageActionButton(
                onClick = { onEvent(GameEvent.Restart) },
                modifier = Modifier.width(240.dp).height(64.dp),
            ) {
                Text("重新挑战", fontSize = 28.sp)
            }
        }
        if (settled) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!state.isWin && !state.isOutOfMoves) {
                    StageActionButton(onClick = { onEvent(GameEvent.Hint) }) { Text("提示", fontSize = 20.sp) }
                    StageActionButton(onClick = { onEvent(GameEvent.ToggleHammer) }, selected = state.hammerArmed) {
                        Text(if (state.hammerArmed) "取消锤子" else "锤子 ${state.session.inventory.hammerCount}", fontSize = 20.sp)
                    }
                }
                if (!state.isWin && !state.isOutOfMoves) {
                    StageActionButton(onClick = { onEvent(GameEvent.Restart) }) { Text("重试", fontSize = 20.sp) }
                }
            }
            run {
                val currentChapter = (state.session.levelIndex - 1) / 5
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..4).forEach { chapter ->
                        val start = chapter * 5 + 1
                        if (start <= state.unlockedLevel) {
                            StageActionButton(onClick = { onEvent(GameEvent.ChooseLevel(start)) }, selected = chapter == currentChapter) {
                                Text(listOf("分数", "冰块", "豆荚", "宝石", "蜗牛")[chapter], fontSize = 18.sp)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (currentChapter * 5 + 1..currentChapter * 5 + 5).forEach { index ->
                        StageActionButton(
                            onClick = { onEvent(GameEvent.ChooseLevel(index)) },
                            enabled = index <= state.unlockedLevel,
                            selected = index == state.session.levelIndex,
                        ) {
                            Text(if (index <= state.unlockedLevel) "$index" else "锁", fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun StageActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Button(
        onClick = onClick,
        modifier = modifier.border(
            1.dp,
            if (!enabled) Color(0xFF376067) else if (selected) Color(0xFFFFE2A2) else Color(0xFF8A936E),
            shape,
        ),
        enabled = enabled,
        size = ButtonDefaults.Small,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Color(0xFF376067) else Color(0xFF194F54),
            contentColor = if (selected) Color(0xFFFFE2A2) else Color(0xFFFFFAED),
        ),
        content = content,
    )
}

private class TileNode(
    val entity: ModelEntity,
    val prototypeContainer: Entity,
    val glow: ModelEntity,
    val sparkle: ModelEntity,
    val flareVertical: ModelEntity,
    val flareDiagonal: ModelEntity,
    val burstSparkA: ModelEntity,
    val burstSparkB: ModelEntity,
    val burstSparkC: ModelEntity,
    val burstSparkD: ModelEntity,
) {
    var currentPrototype: Entity? = null
    var currentPrototypeColor: TileColor? = null
    var currentPrototypeSpecial: SpecialTileKind = SpecialTileKind.None
    var currentCargo: CargoKind = CargoKind.None
    var currentGemLayers: Int = 0
    var campaignMarker: Entity? = null
    var campaignMarkerKey: String = ""
    var basePosition: Vector3 = Vector3(0f, 0f, 0f)
}

private data class BurstProfile(
    val primaryScale: Vector2Like,
    val secondaryScale: Vector2Like,
    val primaryRoll: Float,
    val secondaryRoll: Float,
)

private data class Vector2Like(
    val x: Float,
    val y: Float,
)

private fun celebrationEnvelope(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return if (p < 0.5f) {
        p / 0.5f
    } else {
        (1f - p) / 0.5f
    }.coerceIn(0f, 1f)
}

private fun handleSpatialPointerEvents(
    events: List<SpatialPointerInfo>,
    entityToPoint: Map<Long, GridPoint>,
    state: GameUiState,
    pointerTracker: SpatialPointerSwapTracker,
    onEvent: (GameEvent) -> Unit,
) {
    events.forEach { event ->
        val point =
            resolveBoardPoint(
                targetEntity = event.targetedEntity,
                entityToPoint = entityToPoint,
                board = state.displayBoard,
                inputDevicePose = event.inputDevicePose,
            )
        Log.i(
            STAGE_TAG,
            "pointer kind=${event.kind} pressed=${event.pressed} down=${event.isDownEvent()} up=${event.isUpEvent()} point=$point entity=${event.targetedEntity?.id} position=${event.position3D} rawPosition=${event.inputDevicePose.rawPosition}",
        )
        if (
            state.isWin ||
            state.isOutOfMoves ||
            state.hammerArmed
        ) {
            pointerTracker.reset()
            return@forEach
        }
        when {
            event.isDownEvent() && point != null -> {
                    pointerTracker.start(event.pointerId, point)
                onEvent(GameEvent.SelectCell(point))
            }
            event.pressed -> {
                pointerTracker.update(
                        pointerId = event.pointerId,
                    hoveredPoint = point,
                    board = state.displayBoard,
                )?.let { swap ->
                    Log.i(STAGE_TAG, "pointer swap from=${swap.from} to=${swap.to} kind=${event.kind}")
                    onEvent(GameEvent.DragSwap(swap.from, swap.to))
                }
            }
            event.isUpEvent() -> {
                    pointerTracker.end(event.pointerId)
            }
        }
    }
}

private fun resolveBoardPoint(
    targetEntity: Entity?,
    entityToPoint: Map<Long, GridPoint>,
    board: Match3Board,
    inputDevicePose: com.pico.spatial.ui.foundation.gesture.data.InputDevicePose? = null,
): GridPoint? {
    var current = targetEntity
    while (current != null) {
        entityToPoint[current.id]?.let { return it }
        current = current.getParent()
    }
    return inputDevicePose?.let { pose ->
        projectPoseToBoardPoint(pose, board)
    }
}

private fun projectPoseToBoardPoint(
    pose: com.pico.spatial.ui.foundation.gesture.data.InputDevicePose,
    board: Match3Board,
): GridPoint? {
    val origin =
        Vector3(
            pose.rawPosition.x,
            pose.rawPosition.y,
            pose.rawPosition.z,
        )
    val rotation = pose.rawRotation.toQuaternion()
    val primaryDirection = rotation.rotateVector(Vector3(0f, 0f, -1f)).normalizeOrNull()
    val secondaryDirection = rotation.rotateVector(Vector3(0f, 0f, 1f)).normalizeOrNull()
    val intersection =
        listOfNotNull(
            primaryDirection?.let { rayPlaneIntersection(origin, it, BOARD_WORLD_Z) },
            secondaryDirection?.let { rayPlaneIntersection(origin, it, BOARD_WORLD_Z) },
        ).minByOrNull { point ->
            distanceSquaredToBoardCenter(point)
        } ?: return null
    return boardPointFromWorld(intersection, board)
}

private fun rayPlaneIntersection(
    origin: Vector3,
    direction: Vector3,
    planeZ: Float,
): Vector3? {
    if (abs(direction.z) < 1e-4f) {
        return null
    }
    val distance = (planeZ - origin.z) / direction.z
    if (distance <= 0f) {
        return null
    }
    return origin + direction.times(distance)
}

private fun boardPointFromWorld(
    worldPoint: Vector3,
    board: Match3Board,
): GridPoint? {
    val localX = (worldPoint.x - BOARD_WORLD_POSITION.x) / BOARD_WORLD_SCALE.x
    val localY = (worldPoint.y - BOARD_WORLD_POSITION.y) / BOARD_WORLD_SCALE.y
    val columnCenter = localX / TILE_SPACING + (board.width - 1) / 2f
    val rowCenter = (board.height - 1) / 2f - localY / TILE_SPACING
    val column = columnCenter.roundToInt()
    val row = rowCenter.roundToInt()
    val point = GridPoint(row, column)
    if (!board.contains(point)) {
        return null
    }
    if (abs(columnCenter - column) > BOARD_POINT_CAPTURE_RADIUS_CELLS) {
        return null
    }
    if (abs(rowCenter - row) > BOARD_POINT_CAPTURE_RADIUS_CELLS) {
        return null
    }
    return point
}

private fun distanceSquaredToBoardCenter(point: Vector3): Float {
    val dx = point.x - BOARD_WORLD_POSITION.x
    val dy = point.y - BOARD_WORLD_POSITION.y
    return dx * dx + dy * dy
}

private fun Vector3.normalizeOrNull(): Vector3? {
    val length = length()
    if (length <= 1e-4f) {
        return null
    }
    return times(1f / length)
}

private class SpatialPointerSwapTracker {
    private var pointerId: PointerId? = null
    private var startPoint: GridPoint? = null
    private var swapEmitted = false

    fun start(
        pointerId: PointerId,
        point: GridPoint,
    ) {
        this.pointerId = pointerId
        startPoint = point
        swapEmitted = false
    }

    fun update(
        pointerId: PointerId,
        hoveredPoint: GridPoint?,
        board: Match3Board,
    ): DragSwapResolution? {
        if (this.pointerId != pointerId || swapEmitted) {
            return null
        }
        val from = startPoint ?: return null
        val target = hoveredPoint ?: return null
        val resolved =
            when {
                from == target -> null
                from.isAdjacentTo(target) -> target
                else -> directionProjectedPoint(from, target)?.takeIf(board::contains)
            } ?: return null
        swapEmitted = true
        return DragSwapResolution(from, resolved)
    }

    fun end(pointerId: PointerId) {
        if (this.pointerId == pointerId) {
            reset()
        }
    }

    fun reset() {
        pointerId = null
        startPoint = null
        swapEmitted = false
    }
}

private class SpatialDragSwapTracker {
    private var startPoint: GridPoint? = null
    private var cumulativeX: Float = 0f
    private var cumulativeY: Float = 0f
    private var swapEmitted = false

    fun start(point: GridPoint) {
        startPoint = point
        cumulativeX = 0f
        cumulativeY = 0f
        swapEmitted = false
    }

    fun isTracking(): Boolean = startPoint != null

    fun updateDrag(
        dragAmount: Offset3D,
        hoveredPoint: GridPoint?,
        board: Match3Board,
    ): DragSwapResolution? {
        if (swapEmitted) {
            return null
        }
        cumulativeX += dragAmount.x
        cumulativeY += dragAmount.y
        return resolve(hoveredPoint = hoveredPoint, board = board)
    }

    private fun resolve(
        hoveredPoint: GridPoint?,
        board: Match3Board,
    ): DragSwapResolution? {
        val from = startPoint ?: return null
        if (hoveredPoint != null && hoveredPoint != from && from.isAdjacentTo(hoveredPoint)) {
            swapEmitted = true
            return DragSwapResolution(from, hoveredPoint)
        }
        val projected = directionProjectedPoint(from, cumulativeX, cumulativeY, board) ?: return null
        swapEmitted = true
        return DragSwapResolution(from, projected)
    }

    fun reset() {
        startPoint = null
        cumulativeX = 0f
        cumulativeY = 0f
        swapEmitted = false
    }
}

private fun directionProjectedPoint(
    from: GridPoint,
    dx: Float,
    dy: Float,
    board: Match3Board,
): GridPoint? {
    val threshold = POINTER_SWAP_DRAG_THRESHOLD
    if (abs(dx) < threshold && abs(dy) < threshold) {
        return null
    }
    val candidate =
        if (abs(dx) >= abs(dy)) {
            GridPoint(from.row, from.column + dx.directionSign())
        } else {
            GridPoint(from.row + dy.directionSign(), from.column)
        }
    return candidate.takeIf(board::contains)
}

private fun Float.directionSign(): Int =
    when {
        this > 0f -> 1
        this < 0f -> -1
        else -> 0
    }

private fun directionProjectedPoint(
    from: GridPoint,
    target: GridPoint,
): GridPoint? {
    val rowDelta = target.row - from.row
    val columnDelta = target.column - from.column
    if (rowDelta == 0 && columnDelta == 0) {
        return null
    }
    return if (abs(columnDelta) >= abs(rowDelta)) {
        GridPoint(from.row, from.column + columnDelta.sign())
    } else {
        GridPoint(from.row + rowDelta.sign(), from.column)
    }
}

private fun Int.sign(): Int =
    when {
        this > 0 -> 1
        this < 0 -> -1
        else -> 0
    }

private data class DragSwapResolution(
    val from: GridPoint,
    val to: GridPoint,
)

private data class BoardRenderKey(
    val selectedCell: GridPoint?,
    val activeBurstToken: Long?,
    val burstSignature: List<Int>,
    val activeEffectToken: Long?,
    val highlightedSignature: List<Int>,
    val celebrationToken: Long?,
    val celebrationProgressBucket: Int,
    val fallProgressBucket: Int,
    val fallSignature: List<Int>,
    val tileSignature: List<Long>,
    val campaignSignature: Int,
) {
    companion object {
        fun from(
            board: Match3Board,
            selectedCell: GridPoint?,
            activeEffect: Match3EffectCue?,
            activeBurst: Match3FlashBurst?,
            activeCelebration: LevelClearCelebration?,
            celebrationProgress: Float,
            transientFallOffsets: Map<GridPoint, Float>,
            transientFallProgress: Float,
        ): BoardRenderKey =
            BoardRenderKey(
                selectedCell = selectedCell,
                activeBurstToken = activeBurst?.token,
                burstSignature =
                    activeBurst
                        ?.points
                        ?.map { it.row * board.width + it.column }
                        ?.sorted()
                        ?: emptyList(),
                activeEffectToken = activeEffect?.token,
                highlightedSignature =
                    activeEffect
                        ?.highlightedPoints
                        ?.map { it.row * board.width + it.column }
                        ?.sorted()
                        ?: emptyList(),
                celebrationToken = activeCelebration?.token,
                celebrationProgressBucket = (celebrationProgress * 120f).roundToInt(),
                fallProgressBucket = (transientFallProgress * 100f).roundToInt(),
                fallSignature =
                    transientFallOffsets
                        .toList()
                        .sortedWith(compareBy({ it.first.row }, { it.first.column }))
                        .map { (point, offset) ->
                            point.row * 10_000 + point.column * 100 + (offset * 10f).roundToInt()
                        },
                tileSignature =
                    board.cells.map { tile ->
                        tile.id xor
                            (tile.color.ordinal.toLong() shl 32) xor
                            (tile.special.ordinal.toLong() shl 40) xor
                            (tile.cargo.ordinal.toLong() shl 48)
                    },
                campaignSignature = listOf(board.ice, board.gems, board.snails, board.clearedRoute).hashCode(),
            )
    }
}

private object MeshCatalog {
    val animalSphere: MeshResource by lazy { MeshResource.createSphere(0.5f).apply { toGlobal() } }
    val animalEar: MeshResource by lazy { MeshResource.createCone(1f, 0.5f).apply { toGlobal() } }
    val campaignCube: MeshResource by lazy { MeshResource.createBox(Vector3(1f, 1f, 1f), 0.04f).apply { toGlobal() } }
    val tileCollisionShape: ShapeResource by lazy {
        ShapeResource.createBox(Vector3(0.14f, 0.14f, 0.18f))
    }
    val bearMesh: MeshResource by lazy { MeshResource.createBox(Vector3(0.92f, 0.72f, 0.56f), 0.2f) }
    val frogMesh: MeshResource by lazy { MeshResource.createBox(Vector3(0.82f, 0.82f, 0.58f), 0.24f) }
    val chickMesh: MeshResource by lazy { MeshResource.createBox(Vector3(0.72f, 0.72f, 0.52f), 0.18f) }
    val owlMesh: MeshResource by lazy { MeshResource.createBox(Vector3(0.68f, 0.86f, 0.54f), 0.12f) }
    val catMesh: MeshResource by lazy { MeshResource.createBox(Vector3(0.72f, 0.88f, 0.54f), 0.12f) }
    val foxMesh: MeshResource by lazy { MeshResource.createBox(Vector3(0.82f, 0.82f, 0.56f), 0.12f) }
    val headMesh: MeshResource by lazy { MeshResource.createBox(Vector3(0.86f, 0.86f, 0.56f), 0.1f) }
    val gemShellMesh: MeshResource by lazy { MeshResource.createBox(Vector3(1f, 1f, 0.82f), 0.22f) }
    val gemCoreMesh: MeshResource by lazy { MeshResource.createBox(Vector3(1f, 1f, 1f), 0.1f) }
    val gemCrownMesh: MeshResource by lazy { MeshResource.createBox(Vector3(1f, 1f, 1f), 0.06f) }
    val gemPavilionMesh: MeshResource by lazy { MeshResource.createBox(Vector3(1f, 1f, 1f), 0.06f) }
    val gemFacetMesh: MeshResource by lazy { MeshResource.createBox(Vector3(1f, 1f, 1f), 0.04f) }
    val gemGirdleMesh: MeshResource by lazy { MeshResource.createBox(Vector3(1f, 1f, 1f), 0.02f) }
    val gemCornerFacetMesh: MeshResource by lazy { MeshResource.createBox(Vector3(1f, 1f, 1f), 0.03f) }
    val gemHighlightMesh: MeshResource by lazy { MeshResource.createBox(Vector3(1f, 1f, 1f), 0.18f) }
    val glowMesh: MeshResource by lazy { MeshResource.createBox(Vector3(1f, 1f, 0.08f), 0.28f) }
    val environmentPanelMesh: MeshResource by lazy { MeshResource.createBox(Vector3(1f, 1f, 1f), 0.18f) }
    val sparkleMesh: MeshResource by lazy { MeshResource.createBox(Vector3(1f, 1f, 0.3f), 0.08f) }
    val flareVerticalMesh: MeshResource by lazy { MeshResource.createBox(Vector3(0.16f, 1f, 0.08f), 0.06f) }
    val flareHorizontalMesh: MeshResource by lazy { MeshResource.createBox(Vector3(1f, 0.16f, 0.08f), 0.06f) }
    private val boxMeshes = mutableMapOf<String, MeshResource>()

    fun environmentMesh(
        size: Vector3,
        cornerRadius: Float,
    ): MeshResource = boxMesh(size.x, size.y, size.z, cornerRadius)

    private fun boxMesh(
        x: Float,
        y: Float,
        z: Float,
        cornerRadius: Float,
    ): MeshResource {
        val key = "${x}_${y}_${z}_$cornerRadius"
        return boxMeshes.getOrPut(key) {
            MeshResource.createBox(Vector3(x, y, z), cornerRadius)
        }
    }
}

private object MaterialCatalog {
    private val cache = mutableMapOf<Color4, UnlitMaterial>()

    fun material(color: Color4): UnlitMaterial =
        cache.getOrPut(color) {
            UnlitMaterial.create().apply {
                setBaseColor(color)
                toGlobal()
            }
        }
}

private object AnimalMaterialCatalog {
    private val cache = mutableMapOf<Color4, PhysicallyBasedMaterial>()

    fun material(color: Color4): PhysicallyBasedMaterial =
        cache.getOrPut(color) {
            PhysicallyBasedMaterial.create().apply {
                setBaseColor(color)
                setMetallic(0f)
                setRoughness(0.3f)
                if (color.alpha < 1f) setBlendingMode(com.pico.spatial.core.ecs.resource.BlendingMode.TRANSPARENT)
                toGlobal()
            }
        }
}

private object PhysicsCatalog {
    val defaultMaterial: PhysicsMaterialResource by lazy { PhysicsMaterialResource() }
}

private const val TILE_SPACING = 0.13f
private const val POINTER_SWAP_DRAG_THRESHOLD = 0.03f
private const val BOARD_POINT_CAPTURE_RADIUS_CELLS = 0.62f
private const val STAGE_TAG = "GameplayStage"
private val BOARD_WORLD_POSITION = Vector3(0f, 1.46f, -1.92f)
// The tilted floor decoration reaches z=-1.64; keep every action in front of it.
private val LEVEL_ACTIONS_WORLD_POSITION = Vector3(0f, 0.80f, -1.35f)
private val BOARD_WORLD_SCALE = Vector3(0.74f, 0.74f, 0.74f)
private const val BOARD_WORLD_Z = -1.92f


private fun addSpecialDecoration(root: Entity, kind: SpecialTileKind) {
    if (kind == SpecialTileKind.HorizontalLine || kind == SpecialTileKind.VerticalLine) {
        val horizontal = kind == SpecialTileKind.HorizontalLine
        listOf(-1f, 0f, 1f).forEachIndexed { index, offset ->
            root.addChild(prototypePart(
                name = "stripe_${kind}_$index",
                size = if (horizontal) Vector3(0.62f, 0.045f, 0.035f) else Vector3(0.045f, 0.52f, 0.035f),
                color = Color4(1f, 0.91f, 0.24f, 1f),
                position = if (horizontal) Vector3(0f, offset * 0.095f - 0.12f, 0.39f) else Vector3(offset * 0.10f, -0.07f, 0.39f),
                cornerRadius = 0.018f,
            ))
        }
    } else if (kind == SpecialTileKind.Bomb) {
        root.addChild(prototypePart(
            name = "bomb_badge", size = Vector3(0.28f, 0.28f, 0.20f),
            color = Color4(0.43f, 0.06f, 0.72f, 1f),
            position = Vector3(0.26f, -0.24f, 0.40f), cornerRadius = 0.13f,
        ))
        root.addChild(prototypePart(
            name = "bomb_fuse", size = Vector3(0.045f, 0.14f, 0.04f),
            color = Color4(1f, 0.79f, 0.08f, 1f),
            position = Vector3(0.28f, -0.06f, 0.44f),
            rotation = EulerAngles(0f, 0f, -25f), cornerRadius = 0.015f,
        ))
        root.addChild(prototypePart(
            name = "bomb_spark", size = Vector3(0.10f, 0.10f, 0.05f),
            color = Color4(1f, 0.37f, 0.025f, 1f),
            position = Vector3(0.31f, 0.01f, 0.44f), cornerRadius = 0.045f,
        ))
    }
}

private fun createRainbowBirdPrototype(): Entity = prototypeRoot("rainbow_bird").apply {
    addChild(prototypePart(
        name = "rainbow_body", size = Vector3(0.86f, 0.70f, 0.52f),
        color = Color4(0.76f, 0.025f, 0.57f, 1f), cornerRadius = 0.32f,
    ))
    val colors = listOf(
        Color4(1f, 0.15f, 0.12f, 1f), Color4(1f, 0.65f, 0.025f, 1f),
        Color4(0.25f, 0.9f, 0.12f, 1f), Color4(0.04f, 0.66f, 1f, 1f),
        Color4(0.65f, 0.10f, 0.96f, 1f),
    )
    colors.forEachIndexed { index, tint ->
        addChild(prototypePart(
            name = "rainbow_crest_$index", size = Vector3(0.13f, 0.39f, 0.12f),
            color = tint, position = Vector3((index - 2f) * 0.115f, 0.43f + (2f - abs(index - 2f)) * 0.045f, 0f),
            rotation = EulerAngles(0f, 0f, (index - 2f) * -18f), cornerRadius = 0.06f,
        ))
    }
    listOf(-1f, 1f).forEach { side ->
        addChild(prototypePart("bird_eye_white_$side", Vector3(0.28f, 0.30f, 0.06f),
            Color4(1f, 0.96f, 1f, 1f), Vector3(side * 0.19f, 0.03f, 0.285f), cornerRadius = 0.12f))
        addChild(prototypePart("bird_pupil_$side", Vector3(0.14f, 0.19f, 0.05f),
            Color4(0.23f, 0.03f, 0.40f, 1f), Vector3(side * 0.16f, 0.02f, 0.326f), cornerRadius = 0.07f))
        addChild(prototypePart("bird_eye_glint_$side", Vector3(0.045f, 0.06f, 0.02f),
            Color4(1f, 1f, 1f, 1f), Vector3(side * 0.16f - 0.025f, 0.065f, 0.36f), cornerRadius = 0.02f))
        addChild(prototypePart("bird_brow_$side", Vector3(0.32f, 0.09f, 0.05f),
            Color4(1f, 0.98f, 1f, 1f), Vector3(side * 0.19f, 0.215f, 0.31f),
            rotation = EulerAngles(0f, 0f, side * 16f), cornerRadius = 0.035f))
    }
    addChild(prototypePart(
        name = "rainbow_beak", size = Vector3(0.22f, 0.18f, 0.18f),
        color = Color4(1f, 0.72f, 0.03f, 1f), position = Vector3(0f, -0.11f, 0.33f),
        rotation = EulerAngles(0f, 0f, 45f), cornerRadius = 0.025f,
    ))
}

private fun createPodPrototype(): Entity = prototypeRoot("golden_pod").apply {
    addChild(prototypePart("pod_husk", Vector3(0.42f, 1.08f, 0.28f),
        Color4(1f, 0.64f, 0.015f, 1f), rotation = EulerAngles(0f, 0f, -18f), cornerRadius = 0.19f))
    for (index in 0..2) {
        addChild(prototypePart("gold_bean_$index", Vector3(0.31f, 0.29f, 0.22f),
            Color4(1f, 0.91f, 0.18f, 1f), Vector3((index - 1) * 0.10f, (index - 1) * 0.29f, 0.19f), cornerRadius = 0.14f))
        addChild(prototypePart("bean_glint_$index", Vector3(0.065f, 0.055f, 0.02f),
            Color4(1f, 1f, 0.81f, 1f), Vector3((index - 1) * 0.10f - 0.065f, (index - 1) * 0.29f + 0.055f, 0.31f), cornerRadius = 0.02f))
    }
    addChild(prototypePart("pod_stem", Vector3(0.11f, 0.23f, 0.08f),
        Color4(0.24f, 0.65f, 0.08f, 1f), Vector3(0.17f, 0.58f, 0f),
        rotation = EulerAngles(0f, 0f, -30f), cornerRadius = 0.035f))
}

private fun createGemPrototype(layers: Int): Entity = prototypeRoot("gem_cloud").apply {
    for (index in 0 until layers + 1) {
        val x = (index - layers / 2f) * 0.20f
        addChild(prototypePart("mint_cloud_$index", Vector3(0.44f, 0.41f, 0.22f),
            Color4(0.42f, 0.96f, 0.75f, 1f), Vector3(x, -0.23f, 0f), cornerRadius = 0.19f))
    }
    addChild(prototypePart("pink_gem", Vector3(0.49f, 0.49f, 0.32f),
        Color4(1f, 0.10f, 0.58f, 1f), Vector3(0f, 0.10f, 0.14f),
        rotation = EulerAngles(0f, 0f, 45f), cornerRadius = 0.025f))
    addChild(prototypePart("gem_facet", Vector3(0.23f, 0.23f, 0.025f),
        Color4(1f, 0.70f, 0.90f, 1f), Vector3(-0.06f, 0.17f, 0.325f),
        rotation = EulerAngles(0f, 0f, 45f), cornerRadius = 0.012f))
    for (index in 0 until layers) {
        addChild(prototypePart("cloud_layer_$index", Vector3(0.065f, 0.065f, 0.03f),
            Color4(0.02f, 0.52f, 0.29f, 1f), Vector3((index - (layers - 1) / 2f) * 0.12f, -0.33f, 0.15f),
            cornerRadius = 0.025f))
    }
}

private fun createSnailPrototype(): Entity = prototypeRoot("snail").apply {
    addChild(prototypePart("snail_body", Vector3(0.80f, 0.22f, 0.22f),
        Color4(0.48f, 0.92f, 0.17f, 1f), Vector3(0f, -0.24f, 0.13f), cornerRadius = 0.10f))
    addChild(prototypePart("snail_shell", Vector3(0.53f, 0.53f, 0.26f),
        Color4(1f, 0.48f, 0.06f, 1f), Vector3(-0.11f, 0.03f, 0.10f), cornerRadius = 0.24f))
    addChild(prototypePart("snail_spiral", Vector3(0.29f, 0.29f, 0.025f),
        Color4(0.61f, 0.20f, 0.045f, 1f), Vector3(-0.11f, 0.03f, 0.245f), cornerRadius = 0.14f))
    addChild(prototypePart("snail_spiral_center", Vector3(0.15f, 0.15f, 0.025f),
        Color4(1f, 0.73f, 0.19f, 1f), Vector3(-0.09f, 0.03f, 0.267f), cornerRadius = 0.07f))
    listOf(-1f, 1f).forEach { side ->
        addChild(prototypePart("snail_stalk_$side", Vector3(0.045f, 0.27f, 0.045f),
            Color4(0.39f, 0.75f, 0.12f, 1f), Vector3(0.29f + side * 0.065f, -0.01f, 0.22f), cornerRadius = 0.02f))
        addChild(prototypePart("snail_eye_$side", Vector3(0.065f, 0.075f, 0.04f),
            Color4(0.06f, 0.13f, 0.01f, 1f), Vector3(0.29f + side * 0.065f, 0.14f, 0.245f), cornerRadius = 0.03f))
    }
}

private fun updateCampaignMarkers(board: Match3Board, nodes: Map<GridPoint, TileNode>, mode: CampaignMode) {
    val routes = board.snails.flatMapTo(mutableSetOf()) { it.route }
    nodes.forEach { (point, node) ->
        val ice = board.ice[point] ?: 0
        val route = point in routes
        val next = board.snails.any { !it.completed && it.route[it.position + 1] == point }
        val snail = board.snails.any { !it.completed && it.route[it.position] == point }
        val end = board.snails.any { it.route.last() == point }
        val exit = mode == CampaignMode.Pods && point.row == board.height - 1
        val cleared = point in board.clearedRoute
        val key = "$ice/$route/$next/$snail/$end/$exit/$cleared"
        if (node.campaignMarkerKey == key) return@forEach
        node.campaignMarker?.destroy(true)
        val marker = prototypeRoot("campaign_marker").apply {
            components[TransformComponent::class.java]!!.setScaleVector(Vector3(0.11f, 0.11f, 0.11f))
            if (ice > 0) {
                addChild(prototypePart("ice_glass", Vector3(1.10f, 1.10f, 0.15f),
                    Color4(0.12f, 0.68f, 1f, 0.32f), Vector3(0f, 0f, -0.45f), cornerRadius = 0.12f))
                for (layer in 0 until ice) {
                    addChild(prototypePart("ice_layer_$layer", Vector3(0.95f - layer * 0.12f, 0.035f, 0.03f),
                        Color4(0.62f, 0.95f, 1f, 1f), Vector3(0f, -0.47f + layer * 0.08f, 0.12f), cornerRadius = 0.015f))
                }
                listOf(-1f, 1f).forEach { side ->
                    addChild(prototypePart("ice_edge_$side", Vector3(0.045f, 1.02f, 0.04f),
                        Color4(0.38f, 0.91f, 1f, 0.85f), Vector3(side * 0.51f, 0f, -0.31f), cornerRadius = 0.02f))
                }
            }
            if (route) {
                val tint = if (next) Color4(1f, 0.76f, 0.04f, 1f)
                    else if (cleared) Color4(0.44f, 0.96f, 0.16f, 1f) else Color4(0.22f, 0.65f, 0.40f, 1f)
                addChild(prototypePart("route_line", Vector3(1.07f, 0.045f, 0.03f), tint, Vector3(0f, -0.45f, 0.03f), cornerRadius = 0.02f))
                if (next || end) {
                    addChild(prototypePart("route_flag", Vector3(0.20f, 0.20f, 0.03f),
                        tint, Vector3(0.40f, -0.28f, 0.1f), rotation = EulerAngles(0f, 0f, 45f), cornerRadius = 0.01f))
                }
            }
            if (snail) {
                addChild(createSnailPrototype().apply {
                    components[TransformComponent::class.java]!!.apply {
                        setPosition(Vector3(0f, -0.28f, 0.46f))
                        setScaleVector(Vector3(0.72f, 0.72f, 0.72f))
                    }
                })
            }
            if (exit) {
                listOf(-1f, 1f).forEach { side ->
                    addChild(prototypePart("pod_exit_$side", Vector3(0.26f, 0.065f, 0.04f),
                        Color4(1f, 0.85f, 0.07f, 1f), Vector3(side * 0.09f, -0.62f, 0.13f),
                        rotation = EulerAngles(0f, 0f, side * 35f), cornerRadius = 0.02f))
                }
            }
        }
        node.entity.addChild(marker)
        node.campaignMarker = marker
        node.campaignMarkerKey = key
    }
}
