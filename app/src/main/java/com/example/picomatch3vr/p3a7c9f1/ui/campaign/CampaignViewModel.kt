package com.example.picomatch3vr.p3a7c9f1.ui.campaign

import androidx.lifecycle.ViewModel
import com.example.picomatch3vr.p3a7c9f1.domain.campaign.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GameplayViewModel(
    private val repository: CampaignRepository,
    private val progress: CampaignProgress,
) : ViewModel() {
    private val previewScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val resolveScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var transientBoardJob: Job? = null
    private var clearEffectJob: Job? = null
    private var clearBurstJob: Job? = null
    private var clearCelebrationJob: Job? = null
    private var resolveTurnJob: Job? = null
    private var effectToken: Long = 1L
    private var levelClearCueToken: Long = 1L
    private var celebrationToken: Long = 1L

    private val _state = MutableStateFlow(
        GameUiState(
            session = repository.createSession(levelIndex = progress.currentLevel),
            unlockedLevel = progress.unlockedLevel,
        ),
    )
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    fun onEvent(event: GameEvent) {
        when (event) {
            is GameEvent.SelectCell -> handleCellSelection(event.point)
            is GameEvent.DragSwap -> handleDragSwap(event.from, event.to)
            GameEvent.ToggleHammer -> toggleHammer()
            GameEvent.NextLevel -> nextLevel()
            GameEvent.Restart -> restartCurrentLevel()
            GameEvent.Hint -> showHint()
            GameEvent.ToggleGuide -> _state.update { it.copy(showGuide = !it.showGuide) }
            is GameEvent.ChooseLevel -> chooseLevel(event.index)
        }
    }

    private fun handleCellSelection(point: GridPoint) {
        val currentState = state.value
        if (currentState.isResolvingTurn) {
            return
        }
        if (currentState.hammerArmed) {
            handleHammerUse(point)
            return
        }

        if (currentState.isWin || currentState.isOutOfMoves) {
            _state.update {
                it.copy(
                    statusMessage = "本关已结束，请进入下一关或重新开始。",
                    canRestart = true,
                )
            }
            return
        }

        val selected = currentState.selectedCell
        if (selected == null) {
            _state.update {
                it.copy(
                    selectedCell = point,
                    statusMessage = "已按住 ${point.row + 1} 行 ${point.column + 1} 列，可继续拖向相邻格交换。",
                )
            }
            return
        }

        if (selected == point) {
            _state.update {
                it.copy(
                    selectedCell = null,
                    statusMessage = "已取消当前选择。",
                )
            }
            return
        }

        if (!selected.isAdjacentTo(point)) {
            _state.update {
                it.copy(
                    selectedCell = point,
                    statusMessage = "请继续拖到上下左右相邻位置交换。",
                    lastTurnSummary = "点按目标不相邻，已切换到新的起点。",
                )
            }
            return
        }

        previewAndResolveSwap(currentState.session, selected, point)
    }

    private fun handleDragSwap(
        from: GridPoint,
        to: GridPoint,
    ) {
        val currentState = state.value
        if (currentState.isResolvingTurn) {
            return
        }
        if (!currentState.session.board.contains(from) || !currentState.session.board.contains(to)) {
            _state.update {
                it.copy(
                    selectedCell = null,
                    transientBoard = null,
                    statusMessage = "拖拽落点超出棋盘范围，未执行交换。",
                    lastTurnSummary = "已拦截越界交换：from=$from to=$to",
                )
            }
            return
        }
        if (currentState.hammerArmed) {
            handleHammerUse(to)
            return
        }
        if (currentState.isWin || currentState.isOutOfMoves) {
            _state.update {
                it.copy(
                    selectedCell = null,
                    statusMessage = "本关已结束，请进入下一关或重新开始。",
                    canRestart = true,
                )
            }
            return
        }
        if (!from.isAdjacentTo(to)) {
            _state.update {
                it.copy(
                    selectedCell = null,
                    statusMessage = "拖动方向无效，请拖向相邻糖果。",
                    lastTurnSummary = "拖拽未形成相邻交换。",
                )
            }
            return
        }

        previewAndResolveSwap(currentState.session, from, to)
    }

    private fun handleHammerUse(point: GridPoint) {
        val currentState = state.value
        if (currentState.isResolvingTurn) {
            return
        }
        val result = repository.useHammer(currentState.session, point)
        if (!result.used) {
            _state.update {
                it.copy(
                    hammerArmed = false,
                    statusMessage = result.statusMessage,
                    lastTurnSummary = "道具未生效。",
                )
            }
            return
        }

        applySessionUpdate(
            nextState = result.session,
            activeEffect = null,
            statusMessage = result.statusMessage,
            turnSummary = "道具回合：击碎 ${result.clearedTiles} 块，得分 +${result.scoreDelta}。",
            hammerArmed = false,
        )
    }

    private fun toggleHammer() {
        val currentState = state.value
        if (currentState.isResolvingTurn) {
            return
        }
        if (currentState.session.inventory.hammerCount <= 0) {
            _state.update {
                it.copy(
                    hammerArmed = false,
                    statusMessage = "锤子已经用完了。",
                )
            }
            return
        }
        _state.update {
            val armed = !it.hammerArmed
            it.copy(
                selectedCell = null,
                hammerArmed = armed,
                statusMessage = if (armed) "锤子已就绪，点击任意糖果即可击碎。" else "已取消锤子道具。",
            )
        }
    }

    private fun nextLevel() {
        val currentLevel = state.value.session.levelIndex
        if (!state.value.isWin || state.value.isResolvingTurn) return
        val nextLevel = if (currentLevel >= 25) 1 else currentLevel + 1
        progress.currentLevel = nextLevel
        transientBoardJob?.cancel()
        resolveTurnJob?.cancel()
        clearCelebrationJob?.cancel()
        _state.update {
            GameUiState(
                session = repository.createSession(levelIndex = nextLevel),
                unlockedLevel = progress.unlockedLevel,
                statusMessage = "已进入第 $nextLevel 关。",
                lastTurnSummary = "新关卡已载入，目标和步数已更新。",
                transientFallOffsets = emptyMap(),
                transientFallProgress = 0f,
                activeCelebration = null,
                celebrationProgress = 0f,
            )
        }
    }

    private fun restartCurrentLevel() {
        val currentLevel = state.value.session.levelIndex
        transientBoardJob?.cancel()
        resolveTurnJob?.cancel()
        clearCelebrationJob?.cancel()
        _state.update {
            GameUiState(
                session = repository.createSession(levelIndex = currentLevel),
                unlockedLevel = progress.unlockedLevel,
                statusMessage = "已重新开始第 $currentLevel 关。",
                lastTurnSummary = "当前关卡的棋盘、步数和目标已重置。",
                transientFallOffsets = emptyMap(),
                transientFallProgress = 0f,
                activeCelebration = null,
                celebrationProgress = 0f,
            )
        }
    }

    private fun applySessionUpdate(
        nextState: Match3GameSession,
        activeEffect: Match3EffectCue?,
        statusMessage: String,
        turnSummary: String,
        hammerArmed: Boolean,
    ) {
        val wasWin = state.value.isWin
        val outOfMoves = nextState.movesRemaining <= 0
        val canContinue = repository.hasAvailableMove(nextState)
        val levelCompleted = nextState.goalProgress.all { it.isCompleted }
        if (levelCompleted) progress.unlock((nextState.levelIndex + 1).coerceAtMost(25))
        val newLevelClearCueToken = if (!wasWin && levelCompleted) levelClearCueToken++ else null
        val newCelebration = if (!wasWin && levelCompleted) LevelClearCelebration(token = celebrationToken++) else null
        val appendedMessage = when {
            levelCompleted -> "$statusMessage 当前关卡完成，可以进入下一关。"
            !canContinue -> "$statusMessage 当前棋盘无可用步，建议重开。"
            outOfMoves -> "$statusMessage 步数耗尽，本关结束。"
            else -> statusMessage
        }

        transientBoardJob?.cancel()
        clearEffectJob?.cancel()
        clearBurstJob?.cancel()
        clearCelebrationJob?.cancel()
        _state.update {
            it.copy(
                session = nextState,
                unlockedLevel = progress.unlockedLevel,
                selectedCell = null,
                hammerArmed = hammerArmed,
                transientBoard = null,
                transientFallOffsets = emptyMap(),
                transientFallProgress = 0f,
                isResolvingTurn = false,
                activeBurst = activeEffect?.let { effect ->
                    Match3FlashBurst(
                        token = effect.token,
                        kind = effect.kind,
                        points = effect.highlightedPoints,
                        cascadeCount = resultCascadeCount(turnSummary),
                    )
                },
                activeEffect = activeEffect,
                levelClearCueToken = newLevelClearCueToken,
                activeCelebration = newCelebration,
                celebrationProgress = 0f,
                statusMessage = appendedMessage,
                lastTurnSummary = turnSummary,
                canRestart = outOfMoves || levelCompleted || !canContinue,
            )
        }
        if (activeEffect != null) {
            scheduleBurstClear(
                token = activeEffect.token,
                durationMs = state.value.activeBurst?.let {
                    GameplayAnimationTiming.clearDurationMs(it, nextState.board.width)
                } ?: 301L,
            )
        }
        if (activeEffect != null) {
            scheduleEffectClear(activeEffect.token)
        }
        if (newCelebration != null) {
            scheduleCelebration(newCelebration.token)
        }
    }

    private fun chooseLevel(index: Int) {
        if (state.value.isResolvingTurn || index !in 1..progress.unlockedLevel) return
        transientBoardJob?.cancel()
        resolveTurnJob?.cancel()
        clearCelebrationJob?.cancel()
        clearBurstJob?.cancel()
        clearEffectJob?.cancel()
        progress.currentLevel = index
        _state.value = GameUiState(repository.createSession(index), unlockedLevel = progress.unlockedLevel)
    }

    private fun showHint() {
        if (state.value.isResolvingTurn || state.value.isWin || state.value.isOutOfMoves) return
        val pair = repository.hint(state.value.session) ?: return
        _state.update { it.copy(
            activeEffect = Match3EffectCue(effectToken++, Match3EffectKind.Normal, "提示交换", setOf(pair.first, pair.second)),
            statusMessage = "交换闪亮的两个动物；提示不消耗步数。",
        ) }
        clearEffectJob?.cancel()
        clearEffectJob = previewScope.launch {
            delay(2500)
            _state.update { it.copy(activeEffect = null) }
        }
    }

    private fun previewAndResolveSwap(
        session: Match3GameSession,
        from: GridPoint,
        to: GridPoint,
    ) {
        transientBoardJob?.cancel()
        resolveTurnJob?.cancel()
        clearEffectJob?.cancel()
        clearBurstJob?.cancel()
        clearCelebrationJob?.cancel()

        val previewBoard = session.board.swap(from, to)
        _state.update {
            it.copy(
                selectedCell = null,
                hammerArmed = false,
                transientBoard = previewBoard,
                isResolvingTurn = true,
                activeBurst = null,
                activeEffect = null,
                activeCelebration = null,
                celebrationProgress = 0f,
                transientFallOffsets = emptyMap(),
                transientFallProgress = 0f,
                statusMessage = "交换中...",
                lastTurnSummary = "已执行交换预览，正在结算消除。",
            )
        }

        resolveTurnJob =
            resolveScope.launch {
                val result = repository.playTurn(session, from, to)
                if (!result.validMove) {
                    previewRejectedSwap(from, to, result.statusMessage)
                    return@launch
                }
                delay(VALID_SWAP_PREVIEW_MS)
                playResolvedTurnAnimation(
                    initialBoard = previewBoard,
                    result = result,
                )
            }
    }

    private fun previewRejectedSwap(
        from: GridPoint,
        to: GridPoint,
        statusMessage: String,
    ) {
        val board = state.value.session.board
        if (!board.contains(from) || !board.contains(to)) {
            _state.update {
                it.copy(
                    selectedCell = null,
                    transientBoard = null,
                    statusMessage = "无效交换已被忽略。",
                    lastTurnSummary = "回弹预览跳过越界坐标：from=$from to=$to",
                )
            }
            return
        }
        transientBoardJob?.cancel()
        val previewBoard = board.swap(from, to)
        _state.update {
            it.copy(
                selectedCell = null,
                transientBoard = previewBoard,
                transientFallOffsets = emptyMap(),
                transientFallProgress = 0f,
                isResolvingTurn = true,
                activeBurst = null,
                activeEffect = null,
                activeCelebration = null,
                celebrationProgress = 0f,
                statusMessage = "这次交换没有形成消除，糖果会弹回原位。",
                lastTurnSummary = "尝试交换 ${from.row + 1}-${from.column + 1} 与 ${to.row + 1}-${to.column + 1}：$statusMessage",
            )
        }
        transientBoardJob =
            previewScope.launch {
                delay(REJECTED_SWAP_PREVIEW_MS)
                _state.update { current ->
                    if (current.transientBoard == previewBoard) {
                        current.copy(
                            transientBoard = null,
                            transientFallOffsets = emptyMap(),
                            transientFallProgress = 0f,
                            activeCelebration = null,
                            celebrationProgress = 0f,
                            isResolvingTurn = false,
                        )
                    } else {
                        current
                    }
                }
            }
    }

    override fun onCleared() {
        transientBoardJob?.cancel()
        clearEffectJob?.cancel()
        clearBurstJob?.cancel()
        clearCelebrationJob?.cancel()
        resolveTurnJob?.cancel()
        previewScope.coroutineContext.cancel()
        resolveScope.coroutineContext.cancel()
        super.onCleared()
    }

    private fun buildTurnSummary(result: Match3TurnResult): String {
        if (!result.validMove) {
            return "无效操作：${result.statusMessage}"
        }
        return "本回合消除 ${result.clearedTiles} 块，连锁 ${result.cascadeCount} 次，得分 +${result.scoreDelta}。"
    }

    private suspend fun playResolvedTurnAnimation(
        initialBoard: Match3Board,
        result: Match3TurnResult,
    ) {
        var currentBoard = initialBoard
        result.cascadeSteps.forEach { step ->
            currentBoard = step.boardBeforeClear ?: currentBoard
            if (step.activatedSpecials.any { it.delaySeconds > 0f } &&
                step.activatedSpecials.any { it.kind == SpecialTileKind.ColorBomb }) {
                _state.update {
                    it.copy(transientBoard = currentBoard, activeBurst = null, activeEffect = null)
                }
                delay(140L)
            }
            val effectCue = buildEffectCue(step)
            val burst = effectCue?.let { effect ->
                Match3FlashBurst(
                    token = effect.token,
                    kind = effect.kind,
                    points = effect.highlightedPoints,
                    cascadeCount = step.stepIndex,
                    activatedSpecials = step.activatedSpecials,
                )
            }
            _state.update {
                it.copy(
                    transientBoard = currentBoard,
                    transientFallOffsets = emptyMap(),
                    transientFallProgress = 0f,
                    activeBurst = burst,
                    activeEffect = effectCue,
                    statusMessage = result.statusMessage,
                    lastTurnSummary = buildTurnSummary(result),
                )
            }
            if (burst != null) {
                delay(GameplayAnimationTiming.clearDurationMs(burst, currentBoard.width))
            } else {
                delay(96L)
            }
            _state.update {
                it.copy(
                    activeBurst = null,
                    activeEffect = null,
                    transientBoard = step.boardAfterCollapse,
                    transientFallOffsets = step.fallDistances.mapValues { (_, distance) -> distance.toFloat() },
                    transientFallProgress = 0f,
                )
            }
            animateFallProgress()
            currentBoard = step.boardAfterCollapse
        }
        applySessionUpdate(
            nextState = result.session,
            activeEffect = null,
            statusMessage = result.statusMessage,
            turnSummary = buildTurnSummary(result),
            hammerArmed = false,
        )
    }

    private suspend fun animateFallProgress() {
        val startedAt = System.nanoTime()
        while (true) {
            delay(GameplayAnimationTiming.UPDATE_INTERVAL_MS)
            val progress = ((System.nanoTime() - startedAt).toFloat() /
                GameplayAnimationTiming.FALL_DURATION_NANOS).coerceIn(0f, 1f)
            // Elapsed time avoids slow-motion drift when an update is delayed.
            val eased = progress * progress * (3f - 2f * progress)
            _state.update { it.copy(transientFallProgress = eased) }
            if (progress >= 1f) break
        }
    }

    private fun scheduleEffectClear(token: Long) {
        clearEffectJob?.cancel()
        clearEffectJob =
            previewScope.launch {
                delay(EFFECT_CUE_DURATION_MS)
                _state.update { current ->
                    if (current.activeEffect?.token == token) {
                        current.copy(activeEffect = null)
                    } else {
                        current
                    }
                }
            }
    }

    private fun scheduleBurstClear(
        token: Long,
        durationMs: Long,
    ) {
        clearBurstJob?.cancel()
        clearBurstJob =
            previewScope.launch {
                delay(durationMs)
                _state.update { current ->
                    if (current.activeBurst?.token == token) {
                        current.copy(activeBurst = null)
                    } else {
                        current
                    }
                }
            }
    }

    private fun scheduleCelebration(token: Long) {
        clearCelebrationJob?.cancel()
        clearCelebrationJob =
            previewScope.launch {
                for (frame in 1..CELEBRATION_ANIMATION_STEPS) {
                    delay(CELEBRATION_FRAME_MS)
                    val progress = frame.toFloat() / CELEBRATION_ANIMATION_STEPS.toFloat()
                    _state.update { current ->
                        if (current.activeCelebration?.token == token) {
                            current.copy(celebrationProgress = progress)
                        } else {
                            current
                        }
                    }
                }
                _state.update { current ->
                    if (current.activeCelebration?.token == token) {
                        current.copy(
                            activeCelebration = null,
                            celebrationProgress = 0f,
                        )
                    } else {
                        current
                    }
                }
            }
    }

    private fun buildEffectCue(result: Match3TurnResult): Match3EffectCue? {
        if (!result.validMove || result.cascadeSteps.isEmpty()) {
            return null
        }
        val activated = result.cascadeSteps.flatMap(Match3CascadeStep::activatedSpecials)
        val created = result.cascadeSteps.flatMap { step -> step.createdSpecials.values }
        val points = result.cascadeSteps.flatMapTo(linkedSetOf()) { it.removedPoints }
        val kind =
            when {
                activated.size >= 2 -> Match3EffectKind.Combo
                activated.any { it.kind == SpecialTileKind.ColorBomb } || created.any { it == SpecialTileKind.ColorBomb } -> Match3EffectKind.ColorBomb
                activated.any { it.kind == SpecialTileKind.Bomb } || created.any { it == SpecialTileKind.Bomb } -> Match3EffectKind.Bomb
                activated.any { it.kind == SpecialTileKind.HorizontalLine || it.kind == SpecialTileKind.VerticalLine } ||
                    created.any { it == SpecialTileKind.HorizontalLine || it == SpecialTileKind.VerticalLine } -> Match3EffectKind.Line
                points.isNotEmpty() -> Match3EffectKind.Normal
                else -> return null
            }
        val summary =
            when (kind) {
                Match3EffectKind.Normal -> "普通消除触发"
                Match3EffectKind.Line -> "直线特效触发"
                Match3EffectKind.Bomb -> "爆炸特效触发"
                Match3EffectKind.ColorBomb -> "魔力鸟特效触发"
                Match3EffectKind.Combo -> "特效组合触发"
            }
        return Match3EffectCue(
            token = effectToken++,
            kind = kind,
            summary = summary,
            highlightedPoints = points,
        )
    }

    private fun buildEffectCue(step: Match3CascadeStep): Match3EffectCue? {
        val activated = step.activatedSpecials
        val created = step.createdSpecials.values
        val points = step.removedPoints
        val kind =
            when {
                activated.size >= 2 -> Match3EffectKind.Combo
                activated.any { it.kind == SpecialTileKind.ColorBomb } || created.any { it == SpecialTileKind.ColorBomb } -> Match3EffectKind.ColorBomb
                activated.any { it.kind == SpecialTileKind.Bomb } || created.any { it == SpecialTileKind.Bomb } -> Match3EffectKind.Bomb
                activated.any { it.kind == SpecialTileKind.HorizontalLine || it.kind == SpecialTileKind.VerticalLine } ||
                    created.any { it == SpecialTileKind.HorizontalLine || it == SpecialTileKind.VerticalLine } -> Match3EffectKind.Line
                points.isNotEmpty() -> Match3EffectKind.Normal
                else -> return null
            }
        val summary =
            when (kind) {
                Match3EffectKind.Normal -> "普通消除触发"
                Match3EffectKind.Line -> "直线特效触发"
                Match3EffectKind.Bomb -> "爆炸特效触发"
                Match3EffectKind.ColorBomb -> "魔力鸟特效触发"
                Match3EffectKind.Combo -> "特效组合触发"
            }
        return Match3EffectCue(
            token = effectToken++,
            kind = kind,
            summary = summary,
            highlightedPoints = points,
        )
    }

    private fun resultCascadeCount(turnSummary: String): Int =
        when {
            "连锁 " in turnSummary -> turnSummary.substringAfter("连锁 ").substringBefore(" 次").toIntOrNull() ?: 1
            else -> 1
        }.coerceAtLeast(1)

    private companion object {
        const val REJECTED_SWAP_PREVIEW_MS = 180L
        const val VALID_SWAP_PREVIEW_MS = 72L
        const val EFFECT_CUE_DURATION_MS = 1200L
        const val CELEBRATION_ANIMATION_STEPS = 100
        const val CELEBRATION_FRAME_MS = 32L
    }
}
