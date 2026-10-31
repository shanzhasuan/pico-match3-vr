package com.example.picomatch3vr.p3a7c9f1.ui.game

import com.example.picomatch3vr.p3a7c9f1.data.repository.FakeMatch3Repository
import com.example.picomatch3vr.p3a7c9f1.domain.model.GridPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay

class GameViewModelTest {
    @Test
    fun selectingTwoCells_resolvesTurn() {
        val repository = FakeMatch3Repository()
        val viewModel = GameViewModel(repository)
        val initial = viewModel.state.value
        val move = findFirstValidMove(repository, initial.session)

        viewModel.onEvent(GameEvent.SelectCell(move.first))
        assertEquals(move.first, viewModel.state.value.selectedCell)

        viewModel.onEvent(GameEvent.SelectCell(move.second))
        val resolved = viewModel.state.value

        assertTrue(resolved.selectedCell == null)
        assertEquals(initial.session.movesRemaining - 1, resolved.session.movesRemaining)
        assertTrue(resolved.session.score >= initial.session.score)
    }

    @Test
    fun dragSwap_resolvesTurn() {
        val repository = FakeMatch3Repository()
        val viewModel = GameViewModel(repository)
        val initial = viewModel.state.value
        val move = findFirstValidMove(repository, initial.session)

        viewModel.onEvent(GameEvent.DragSwap(move.first, move.second))

        val resolved = viewModel.state.value
        assertTrue(resolved.selectedCell == null)
        assertEquals(initial.session.movesRemaining - 1, resolved.session.movesRemaining)
        assertTrue(resolved.session.score >= initial.session.score)
    }

    @Test
    fun invalidDragSwap_showsTransientSwapThenResets() =
        runBlocking {
            val repository = FakeMatch3Repository()
            val viewModel = GameViewModel(repository)
            val initial = viewModel.state.value
            val move = findFirstInvalidAdjacentMove(repository, initial.session)

            viewModel.onEvent(GameEvent.DragSwap(move.first, move.second))

            val preview = viewModel.state.value
            assertNotNull(preview.transientBoard)
            assertEquals(initial.session.movesRemaining, preview.session.movesRemaining)
            assertTrue(preview.statusMessage.contains("弹回"))

            delay(240)

            assertNull(viewModel.state.value.transientBoard)
        }

    @Test
    fun toggleHammer_armsAndConsumesProp() {
        val repository = FakeMatch3Repository()
        val viewModel = GameViewModel(repository)
        val initial = viewModel.state.value

        viewModel.onEvent(GameEvent.ToggleHammer)
        assertTrue(viewModel.state.value.hammerArmed)

        viewModel.onEvent(GameEvent.SelectCell(GridPoint(0, 0)))
        val afterUse = viewModel.state.value

        assertFalse(afterUse.hammerArmed)
        assertEquals(initial.session.inventory.hammerCount - 1, afterUse.session.inventory.hammerCount)
        assertEquals(initial.session.movesRemaining - 1, afterUse.session.movesRemaining)
    }

    @Test
    fun nextLevel_cyclesForward() {
        val repository = FakeMatch3Repository()
        val viewModel = GameViewModel(repository)

        viewModel.onEvent(GameEvent.NextLevel)

        val state = viewModel.state.value
        assertEquals(2, state.session.levelIndex)
        assertTrue(state.statusMessage.contains("第 2 关"))
    }

    @Test
    fun restart_recreatesCurrentLevel() {
        val repository = FakeMatch3Repository()
        val viewModel = GameViewModel(repository)
        val initialLevel = viewModel.state.value.session.levelIndex

        viewModel.onEvent(GameEvent.Restart)

        val restarted = viewModel.state.value
        assertEquals(initialLevel, restarted.session.levelIndex)
        assertTrue(restarted.session.movesRemaining > 0)
    }

    private fun findFirstValidMove(
        repository: FakeMatch3Repository,
        session: com.example.picomatch3vr.p3a7c9f1.domain.model.Match3GameSession,
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
                    if (board.contains(neighbor)) {
                        val result = repository.playTurn(session, point, neighbor)
                        if (result.validMove) {
                            return point to neighbor
                        }
                    }
                }
            }
        }
        error("Expected at least one valid move.")
    }

    private fun findFirstInvalidAdjacentMove(
        repository: FakeMatch3Repository,
        session: com.example.picomatch3vr.p3a7c9f1.domain.model.Match3GameSession,
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
                    if (board.contains(neighbor)) {
                        val result = repository.playTurn(session, point, neighbor)
                        if (!result.validMove) {
                            return point to neighbor
                        }
                    }
                }
            }
        }
        error("Expected at least one invalid adjacent move.")
    }
}
