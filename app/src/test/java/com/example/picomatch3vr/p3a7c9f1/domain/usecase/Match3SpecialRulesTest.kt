package com.example.picomatch3vr.p3a7c9f1.domain.usecase

import com.example.picomatch3vr.p3a7c9f1.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class Match3SpecialRulesTest {
    private val engine = Match3GameEngine()
    private val first = GridPoint(2, 2)
    private val second = GridPoint(2, 3)

    private fun board(width: Int = 8, height: Int = 8): Match3Board =
        Match3Board(width, height, List(width * height) { index ->
            Match3Tile(1000L + index, TileColor.entries[(index / width + index % width * 2) % 6])
        })

    private fun session(board: Match3Board): Match3GameSession {
        val base = engine.createInitialSession()
        val objectives = TileColor.entries.map { GoalObjective(GoalType.CollectColor, 999, it, it.name) }
        return base.copy(
            board = board,
            level = base.level.copy(goal = LevelGoal(objectives = objectives)),
            collectedTiles = TileColor.entries.associateWith { 0 },
        )
    }

    private fun points(board: Match3Board): Set<GridPoint> =
        (0 until board.height).flatMap { row -> (0 until board.width).map { GridPoint(row, it) } }.toSet()

    private fun row(board: Match3Board, index: Int) = points(board).filter { it.row == index }.toSet()
    private fun column(board: Match3Board, index: Int) = points(board).filter { it.column == index }.toSet()
    private fun square(board: Match3Board, center: GridPoint, radius: Int) =
        points(board).filter { kotlin.math.abs(it.row - center.row) <= radius &&
            kotlin.math.abs(it.column - center.column) <= radius }.toSet()

    @Test
    fun specialChain_reachesEveryWave_andTriggersEachTileOnce() {
        var board = board()
        val third = GridPoint(2, 6)
        val fourth = GridPoint(6, 6)
        for ((point, kind) in listOf(first to SpecialTileKind.HorizontalLine,
            second to SpecialTileKind.HorizontalLine, third to SpecialTileKind.VerticalLine,
            fourth to SpecialTileKind.Bomb)) {
            board = board.replace(point, board.tileAt(point).copy(special = kind))
        }
        val step = engine.playTurn(session(board), first, second).cascadeSteps.first()
        val expected = row(board, 2) + column(board, 3) + column(board, 6) + square(board, fourth, 1)
        assertEquals(expected, step.removedPoints)
        assertEquals(4, step.activatedSpecials.size)
        assertEquals(4, step.activatedSpecials.map { it.point }.distinct().size)
    }

    @Test
    fun comboCollection_countsEachRemovedAnimalExactlyOnce() {
        val board = board().let { it.replace(first, it.tileAt(first).copy(special = SpecialTileKind.ColorBomb)) }
        val result = engine.playTurn(session(board), first, second)
        assertEquals(result.cascadeSteps.sumOf { it.removedPoints.size }, result.session.collectedTiles.values.sum())
    }

    @Test
    fun rainbowWithNormal_clearsOnlySwappedTargetColor_inBothOrdersAndEveryColor() {
        for (color in TileColor.entries) {
            for (reverse in listOf(false, true)) {
                val bird = if (reverse) second else first
                val other = if (reverse) first else second
                var board = board()
                board = board.replace(bird, board.tileAt(bird).copy(color = TileColor.Grape, special = SpecialTileKind.ColorBomb))
                board = board.replace(other, board.tileAt(other).copy(color = color))
                val swapped = board.swap(first, second)
                val birdAfter = other
                val expected = points(swapped).filter { it == birdAfter || swapped.tileAt(it).color == color }.toSet()
                val step = engine.playTurn(session(board), first, second).cascadeSteps.first()
                assertEquals("color=$color reverse=$reverse", expected, step.removedPoints)
                assertEquals(1, step.activatedSpecials.count { it.kind == SpecialTileKind.ColorBomb })
                assertTrue(step.activatedSpecials.none { it.kind == SpecialTileKind.None })
            }
        }
    }

    @Test
    fun rainbowWithSpecial_convertsAllTargetAnimals_andDetonatesEveryOne() {
        for (kind in listOf(SpecialTileKind.HorizontalLine, SpecialTileKind.VerticalLine, SpecialTileKind.Bomb)) {
            for (reverse in listOf(false, true)) {
                val bird = if (reverse) second else first
                val other = if (reverse) first else second
                var board = board()
                board = board.replace(bird, board.tileAt(bird).copy(color = TileColor.Grape, special = SpecialTileKind.ColorBomb))
                board = board.replace(other, board.tileAt(other).copy(color = TileColor.Lemon, special = kind))
                val swapped = board.swap(first, second)
                val targets = points(swapped).filter { it != other && swapped.tileAt(it).color == TileColor.Lemon }.toSet()
                val step = engine.playTurn(session(board), first, second).cascadeSteps.first()
                val converted = step.activatedSpecials.filter { it.kind != SpecialTileKind.ColorBomb }
                assertEquals(targets, converted.map { it.point }.toSet())
                assertEquals(targets.size, converted.size)
                val expected = targets.flatMap { point ->
                    if (kind == SpecialTileKind.Bomb) square(board, point, 1)
                    else if ((point.row + point.column) % 2 == 0) row(board, point.row)
                    else column(board, point.column)
                }.toSet() + other
                assertEquals(expected, step.removedPoints)
            }
        }
    }

    @Test
    fun twoRainbows_clearEveryCell_andCollectOnce() {
        var board = board()
        for (point in listOf(first, second)) {
            board = board.replace(point, board.tileAt(point).copy(special = SpecialTileKind.ColorBomb))
        }
        val result = engine.playTurn(session(board), first, second)
        assertEquals(points(board), result.cascadeSteps.first().removedPoints)
        assertEquals(2, result.cascadeSteps.first().activatedSpecials.size)
        assertEquals(result.clearedTiles, result.session.collectedTiles.values.sum())
    }

    @Test
    fun lineAndBomb_combinationsUseThreeRowsAndColumns_inEveryOrderAtAllEdges() {
        for (origin in listOf(GridPoint(0, 0), GridPoint(0, 6), GridPoint(7, 0), GridPoint(7, 6), first)) {
            val destination = GridPoint(origin.row, origin.column + 1)
            for (line in listOf(SpecialTileKind.HorizontalLine, SpecialTileKind.VerticalLine)) {
                for (reverse in listOf(false, true)) {
                    var board = board()
                    board = board.replace(origin, board.tileAt(origin).copy(special = if (reverse) SpecialTileKind.Bomb else line))
                    board = board.replace(destination, board.tileAt(destination).copy(special = if (reverse) line else SpecialTileKind.Bomb))
                    val step = engine.playTurn(session(board), origin, destination).cascadeSteps.first()
                    val expected = points(board).filter {
                        kotlin.math.abs(it.row - destination.row) <= 1 ||
                            kotlin.math.abs(it.column - destination.column) <= 1
                    }.toSet()
                    assertEquals("origin=$origin line=$line reverse=$reverse", expected, step.removedPoints)
                }
            }
        }
    }

    @Test
    fun matchingFourAndFive_produceCorrectOrientationAndRainbow_andKeepCreatedTile() {
        for (length in listOf(4, 5)) {
            for (vertical in listOf(false, true)) {
                var board = board()
                val target = if (vertical) GridPoint(2, 0) else GridPoint(0, 2)
                val source = if (vertical) GridPoint(2, 1) else GridPoint(1, 2)
                for (index in 0 until length) {
                    val point = if (vertical) GridPoint(index, 0) else GridPoint(0, index)
                    board = board.replace(point, board.tileAt(point).copy(color = TileColor.Strawberry))
                }
                board = board.replace(target, board.tileAt(target).copy(color = TileColor.Grape))
                board = board.replace(source, board.tileAt(source).copy(color = TileColor.Strawberry))
                val step = engine.playTurn(session(board), source, target).cascadeSteps.first()
                val expected = if (length == 5) SpecialTileKind.ColorBomb
                    else if (vertical) SpecialTileKind.VerticalLine else SpecialTileKind.HorizontalLine
                assertEquals(mapOf(target to expected), step.createdSpecials)
                assertFalse(step.removedPoints.contains(target))
                assertTrue(step.boardAfterCollapse.cells.any { it.id == board.tileAt(source).id && it.special == expected })
            }
        }
    }

    @Test
    fun bombMatch_shapesAndRotationsProduceBombAtIntersection() {
        val shapes = listOf(
            setOf(GridPoint(2, 2), GridPoint(2, 3), GridPoint(2, 4), GridPoint(3, 2), GridPoint(4, 2)),
            setOf(GridPoint(2, 2), GridPoint(2, 3), GridPoint(2, 4), GridPoint(3, 3), GridPoint(4, 3)),
        )
        for (shape in shapes) {
            for (rotation in 0..3) {
                fun rotate(p: GridPoint): GridPoint {
                    var value = p
                    repeat(rotation) { value = GridPoint(value.column, 7 - value.row) }
                    return value
                }
                val target = rotate(GridPoint(2, 4))
                val source = rotate(GridPoint(1, 4))
                var board = board()
                shape.map(::rotate).forEach { board = board.replace(it, board.tileAt(it).copy(color = TileColor.Strawberry)) }
                board = board.replace(target, board.tileAt(target).copy(color = TileColor.Grape))
                board = board.replace(source, board.tileAt(source).copy(color = TileColor.Strawberry))
                val step = engine.playTurn(session(board), source, target).cascadeSteps.first()
                assertEquals(listOf(SpecialTileKind.Bomb), step.createdSpecials.values.toList())
            }
        }
    }

    @Test
    fun standaloneBomb_matchesClearClippedNineCellsAtCenterAndCorners() {
        for (center in listOf(GridPoint(0, 0), GridPoint(0, 7), GridPoint(7, 0), GridPoint(7, 7), first)) {
            var board = board()
            board = board.replace(center, board.tileAt(center).copy(special = SpecialTileKind.Bomb))
            val result = engine.useHammer(session(board), center)
            assertTrue(result.used)
            assertEquals(square(board, center, 1).size, result.clearedTiles)
            assertEquals(result.clearedTiles, result.session.collectedTiles.values.sum())
        }
    }

    @Test
    fun hasAvailableMove_agreesWithLegalSwap_evenWhenOnlyOneStripedAnimalExists() {
        val colors = listOf(TileColor.Strawberry, TileColor.Lemon)
        var board = Match3Board(2, 2, List(4) { Match3Tile(2000L + it, colors[it % 2]) })
        board = board.replace(GridPoint(0, 0), board.tileAt(GridPoint(0, 0)).copy(special = SpecialTileKind.HorizontalLine))
        assertFalse(engine.hasAvailableMove(board))
        assertFalse(engine.playTurn(session(board), GridPoint(0, 0), GridPoint(0, 1)).validMove)
    }

    @Test
    fun invalidSwapsAndExhaustedMoves_leaveSessionUntouched() {
        val base = session(board())
        for ((from, to) in listOf(GridPoint(-1, 0) to first, first to GridPoint(8, 8),
            first to first, first to GridPoint(5, 5))) {
            val result = engine.playTurn(base, from, to)
            assertFalse(result.validMove)
            assertEquals(base, result.session)
        }
        val exhausted = base.copy(movesRemaining = 0)
        assertEquals(exhausted, engine.playTurn(exhausted, first, second).session)
    }
}
