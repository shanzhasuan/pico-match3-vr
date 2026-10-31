package com.example.picomatch3vr.p3a7c9f1.domain.usecase

import com.example.picomatch3vr.p3a7c9f1.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class Match3LevelProgressionTest {
    @Test
    fun allTwentyLevels_haveStablePlayableBoards_andResetObjectives() {
        val engine = Match3GameEngine()
        assertEquals((1..20).toList(), Match3GameConfig().levels.map { it.index })
        for (index in 1..20) {
            val session = engine.createInitialSession(index)
            assertEquals(index, session.levelIndex)
            assertEquals(64, session.board.cells.size)
            assertEquals(64, session.board.cells.map { it.id }.distinct().size)
            assertTrue(engine.hasAvailableMove(session.board))
            assertEquals(0, session.totalClearedTiles)
            assertFalse(engine.isLevelCompleted(session))
            assertTrue(session.goalProgress.all { it.currentCount == 0 && it.objective.targetCount > 0 })
            for (row in 0..7) {
                for (column in 0..5) {
                    assertFalse((column..column + 2).map { session.board.tileAt(GridPoint(row, it)).color }.distinct().size == 1)
                    assertFalse((column..column + 2).map { session.board.tileAt(GridPoint(it, row)).color }.distinct().size == 1)
                }
            }
        }
        for (invalid in listOf(-1, 0, 21, Int.MAX_VALUE)) {
            assertEquals(1, engine.createInitialSession(invalid).levelIndex)
        }
    }

    @Test
    fun everyGoal_requiresAllObjectives_atExactThreshold() {
        val engine = Match3GameEngine()
        for (index in 1..20) {
            val base = engine.createInitialSession(index)
            val goals = base.level.goal.objectives
            val completed = base.copy(
                score = goals.firstOrNull { it.type == GoalType.ReachScore }?.targetCount ?: 0,
                totalClearedTiles = goals.firstOrNull { it.type == GoalType.ClearTiles }?.targetCount ?: 0,
                collectedTiles = goals.filter { it.type == GoalType.CollectColor }.associate { it.color!! to it.targetCount },
            )
            assertTrue(engine.isLevelCompleted(completed))
            for (goal in goals) {
                val incomplete = when (goal.type) {
                    GoalType.ReachScore -> completed.copy(score = goal.targetCount - 1)
                    GoalType.ClearTiles -> completed.copy(totalClearedTiles = goal.targetCount - 1)
                    GoalType.CollectColor -> completed.copy(collectedTiles = completed.collectedTiles + (goal.color!! to goal.targetCount - 1))
                }
                assertFalse("level=$index goal=$goal", engine.isLevelCompleted(incomplete))
            }
        }
    }

    @Test
    fun clearCount_accumulatesAcrossTurnsAndHammer_withoutDoubleCounting() {
        val engine = Match3GameEngine()
        val base = engine.createInitialSession(5)
        val cells = List(64) { Match3Tile(1000L + it, TileColor.entries[(it / 8 + it % 8 * 2) % 6]) }
        val board = Match3Board(8, 8, cells).let {
            it.replace(GridPoint(2, 2), it.tileAt(GridPoint(2, 2)).copy(special = SpecialTileKind.ColorBomb))
        }
        val result = engine.playTurn(base.copy(board = board, totalClearedTiles = 7), GridPoint(2, 2), GridPoint(2, 3))
        assertTrue(result.validMove)
        assertEquals(7 + result.clearedTiles, result.session.totalClearedTiles)
        assertEquals(result.clearedTiles, result.cascadeSteps.sumOf { it.removedPoints.size })
        val hammer = engine.useHammer(result.session, GridPoint(0, 0))
        assertTrue(hammer.used)
        assertEquals(result.session.totalClearedTiles + hammer.clearedTiles, hammer.session.totalClearedTiles)
    }

    @Test
    fun convertedBoard_exposesEveryNewSpecialBeforeClear_andKeepsWaveDelays() {
        val engine = Match3GameEngine()
        for (kind in listOf(SpecialTileKind.HorizontalLine, SpecialTileKind.VerticalLine, SpecialTileKind.Bomb)) {
            val base = engine.createInitialSession()
            var board = Match3Board(8, 8, List(64) {
                Match3Tile(1000L + it, TileColor.entries[(it / 8 + it % 8 * 2) % 6])
            })
            val first = GridPoint(2, 2)
            val second = GridPoint(2, 3)
            board = board.replace(first, board.tileAt(first).copy(special = SpecialTileKind.ColorBomb))
            board = board.replace(second, board.tileAt(second).copy(special = kind))
            val step = engine.playTurn(base.copy(board = board), first, second).cascadeSteps.first()
            val converted = step.activatedSpecials.filter { it.delaySeconds > 0f }
            assertTrue(converted.isNotEmpty())
            for (activation in converted) {
                assertEquals(activation.kind, step.boardBeforeClear!!.tileAt(activation.point).special)
            }
            assertEquals(converted.size, converted.map { it.point }.distinct().size)
            assertTrue(converted.zipWithNext().all { (a, b) -> b.delaySeconds > a.delaySeconds })
        }
    }
}
