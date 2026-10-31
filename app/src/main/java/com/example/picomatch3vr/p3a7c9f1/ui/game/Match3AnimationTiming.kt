package com.example.picomatch3vr.p3a7c9f1.ui.game

import com.example.picomatch3vr.p3a7c9f1.domain.model.GridPoint
import com.example.picomatch3vr.p3a7c9f1.domain.model.SpecialTileKind
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/** Keeps the turn timeline and visible clear animation on the same clock. */
internal object Match3AnimationTiming {
    const val SPRITE_FPS = 60f
    const val ANIMAL_CLEAR_SECONDS = 0.18f
    const val FALL_DURATION_NANOS = 180_000_000L
    const val UPDATE_INTERVAL_MS = 11L
    private const val NORMAL_FRAMES = 16
    private const val BOMB_FRAMES = 22
    private const val FRAME_MARGIN_MS = 34L

    fun clearDelay(point: GridPoint, burst: Match3FlashBurst, boardWidth: Int): Float {
        val delays = burst.activatedSpecials.mapNotNull { source ->
            val dx = abs(point.column - source.point.column)
            val dy = abs(point.row - source.point.row)
            when (source.kind) {
                SpecialTileKind.HorizontalLine -> if (dy == 0) dx * 0.022f else null
                SpecialTileKind.VerticalLine -> if (dx == 0) dy * 0.022f else null
                SpecialTileKind.Bomb -> if (max(dx, dy) <= 1) max(dx, dy) * 0.035f else null
                SpecialTileKind.ColorBomb -> ((point.row * boardWidth + point.column) % 4) * 0.025f
                SpecialTileKind.None -> null
            }
        }
        return delays.minOrNull() ?: 0f
    }

    fun clearDurationMs(burst: Match3FlashBurst, boardWidth: Int): Long {
        val lastDelay = burst.points.maxOfOrNull { clearDelay(it, burst, boardWidth) } ?: 0f
        val ordinaryEnd = lastDelay + max(ANIMAL_CLEAR_SECONDS, NORMAL_FRAMES / SPRITE_FPS)
        val hasBomb = burst.activatedSpecials.any {
            it.kind == SpecialTileKind.Bomb || it.kind == SpecialTileKind.ColorBomb
        }
        val specialEnd = if (hasBomb) BOMB_FRAMES / SPRITE_FPS else 0f
        return ceil(max(ordinaryEnd, specialEnd) * 1000.0).toLong() + FRAME_MARGIN_MS
    }
}
