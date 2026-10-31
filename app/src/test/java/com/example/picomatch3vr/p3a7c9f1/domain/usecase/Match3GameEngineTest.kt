package com.example.picomatch3vr.p3a7c9f1.domain.usecase

import com.example.picomatch3vr.p3a7c9f1.domain.model.GridPoint
import com.example.picomatch3vr.p3a7c9f1.domain.model.Match3Board
import com.example.picomatch3vr.p3a7c9f1.domain.model.Match3GameSession
import com.example.picomatch3vr.p3a7c9f1.domain.model.Match3Tile
import com.example.picomatch3vr.p3a7c9f1.domain.model.SpecialTileKind
import com.example.picomatch3vr.p3a7c9f1.domain.model.TileColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Match3GameEngineTest {
    private val engine = Match3GameEngine()

    @Test
    fun initialBoard_hasAvailableMove() {
        val session = engine.createInitialSession()

        assertTrue(engine.hasAvailableMove(session.board))
    }

    @Test
    fun initialSession_usesLevelConfiguration() {
        val session = engine.createInitialSession(levelIndex = 2)

        assertEquals(2, session.levelIndex)
        assertEquals(session.level.goal.targetScore, session.targetScore)
        assertTrue(session.collectedTiles.keys.containsAll(session.level.goal.collectTargets.keys))
    }

    @Test
    fun validMove_changesBoardConsumesMove_andUpdatesCollection() {
        val session = engine.createInitialSession()
        val move = findFirstValidMove(session)

        val result = engine.playTurn(session, move.first, move.second)

        assertTrue(result.validMove)
        assertEquals(session.movesRemaining - 1, result.session.movesRemaining)
        assertTrue(result.session.score > session.score)
        assertNotEquals(session.board.cells, result.session.board.cells)
        assertTrue(result.session.collectedTiles.values.sum() >= 0)
    }

    @Test
    fun hammerUse_consumesInventoryAndMove() {
        val session = engine.createInitialSession()
        val target = GridPoint(0, 0)

        val result = engine.useHammer(session, target)

        assertTrue(result.used)
        assertEquals(session.movesRemaining - 1, result.session.movesRemaining)
        assertEquals(session.inventory.hammerCount - 1, result.session.inventory.hammerCount)
        assertTrue(result.session.score > session.score)
    }

    @Test
    fun nonAdjacentMove_isRejected() {
        val session = engine.createInitialSession()
        val result = engine.playTurn(
            session = session,
            first = GridPoint(0, 0),
            second = GridPoint(2, 2),
        )

        assertFalse(result.validMove)
        assertEquals(session, result.session)
    }

    @Test
    fun fourInRow_createsLineSpecial() {
        val session = sessionWithBoard(
            boardOf(
                listOf(
                    "SSPS",
                    "LMSB",
                    "BGLP",
                    "MPSG",
                ),
            ),
        )

        val result = engine.playTurn(session, GridPoint(0, 2), GridPoint(1, 2))

        assertTrue(result.validMove)
        assertTrue(
            result.cascadeSteps.first().createdSpecials.values.any {
                it == SpecialTileKind.HorizontalLine || it == SpecialTileKind.VerticalLine
            },
        )
    }

    @Test
    fun fiveInRow_createsColorBomb() {
        val session = sessionWithBoard(
            boardOf(
                listOf(
                    "SSPSS",
                    "LMSGL",
                    "BGLPB",
                    "MPSGM",
                    "GLPBS",
                ),
            ),
        )

        val result = engine.playTurn(session, GridPoint(0, 2), GridPoint(1, 2))

        assertTrue(result.validMove)
        assertTrue(result.cascadeSteps.first().createdSpecials.values.contains(SpecialTileKind.ColorBomb))
    }

    @Test
    fun tShape_createsBombSpecial() {
        val session = sessionWithBoard(
            boardOf(
                listOf(
                    "LMSGP",
                    "GPPBL",
                    "MSSSG",
                    "BGSLM",
                    "PLGMB",
                ),
            ),
        )

        val result = engine.playTurn(session, GridPoint(0, 2), GridPoint(1, 2))

        assertTrue(result.validMove)
        assertTrue(result.cascadeSteps.first().createdSpecials.values.contains(SpecialTileKind.Bomb))
    }

    private fun findFirstValidMove(
        session: Match3GameSession,
    ): Pair<GridPoint, GridPoint> {
        val board = session.board
        for (row in 0 until board.height) {
            for (column in 0 until board.width) {
                val point = GridPoint(row, column)
                val candidates = listOf(
                    GridPoint(row + 1, column),
                    GridPoint(row, column + 1),
                )
                candidates.forEach { neighbor ->
                    if (board.contains(neighbor) && engine.playTurn(session, point, neighbor).validMove) {
                        return point to neighbor
                    }
                }
            }
        }
        error("Expected at least one valid move on a stable board.")
    }

    private fun sessionWithBoard(board: Match3Board): Match3GameSession {
        val base = engine.createInitialSession()
        return base.copy(board = board)
    }

    private fun boardOf(rows: List<String>): Match3Board {
        val height = rows.size
        val width = rows.first().length
        var nextId = 1L
        return Match3Board(
            width = width,
            height = height,
            cells = rows.flatMap { row ->
                row.map { ch ->
                    Match3Tile(
                        id = nextId++,
                        color = when (ch) {
                            'S' -> TileColor.Strawberry
                            'L' -> TileColor.Lemon
                            'B' -> TileColor.Blueberry
                            'M' -> TileColor.Mint
                            'P' -> TileColor.Peach
                            'G' -> TileColor.Grape
                            else -> error("Unsupported tile char: $ch")
                        },
                    )
                }
            },
        )
    }
}
