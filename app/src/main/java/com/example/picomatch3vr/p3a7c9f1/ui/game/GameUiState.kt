package com.example.picomatch3vr.p3a7c9f1.ui.game

import com.example.picomatch3vr.p3a7c9f1.domain.model.GridPoint
import com.example.picomatch3vr.p3a7c9f1.domain.model.ActivatedSpecial
import com.example.picomatch3vr.p3a7c9f1.domain.model.Match3Board
import com.example.picomatch3vr.p3a7c9f1.domain.model.Match3GameSession

data class GameUiState(
    val session: Match3GameSession,
    val selectedCell: GridPoint? = null,
    val hammerArmed: Boolean = false,
    val transientBoard: Match3Board? = null,
    val transientFallOffsets: Map<GridPoint, Float> = emptyMap(),
    val transientFallProgress: Float = 0f,
    val isResolvingTurn: Boolean = false,
    val activeBurst: Match3FlashBurst? = null,
    val activeEffect: Match3EffectCue? = null,
    val levelClearCueToken: Long? = null,
    val activeCelebration: LevelClearCelebration? = null,
    val celebrationProgress: Float = 0f,
    val statusMessage: String = "选择相邻糖果进行交换。",
    val lastTurnSummary: String = "第一版目标：先把核心三消循环跑通。",
    val canRestart: Boolean = false,
) {
    val displayBoard: Match3Board = transientBoard ?: session.board

    val scoreProgress: Float =
        if (session.targetScore == 0) 0f else session.score.toFloat() / session.targetScore.toFloat()

    val isWin: Boolean = session.goalProgress.all { it.isCompleted }
    val isOutOfMoves: Boolean = session.movesRemaining <= 0
}

enum class Match3EffectKind {
    Normal,
    Line,
    Bomb,
    ColorBomb,
    Combo,
}

data class Match3EffectCue(
    val token: Long,
    val kind: Match3EffectKind,
    val summary: String,
    val highlightedPoints: Set<GridPoint>,
)

data class Match3FlashBurst(
    val token: Long,
    val kind: Match3EffectKind,
    val points: Set<GridPoint>,
    val cascadeCount: Int = 1,
    val activatedSpecials: List<ActivatedSpecial> = emptyList(),
)

data class LevelClearCelebration(
    val token: Long,
)

sealed interface GameEvent {
    data class SelectCell(val point: GridPoint) : GameEvent

    data class DragSwap(
        val from: GridPoint,
        val to: GridPoint,
    ) : GameEvent

    data object ToggleHammer : GameEvent

    data object NextLevel : GameEvent

    data object Restart : GameEvent
}
