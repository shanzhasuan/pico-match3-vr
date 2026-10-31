package com.example.picomatch3vr.p3a7c9f1.data.repository

import com.example.picomatch3vr.p3a7c9f1.domain.model.GridPoint
import com.example.picomatch3vr.p3a7c9f1.domain.model.Match3GameSession
import com.example.picomatch3vr.p3a7c9f1.domain.model.Match3TurnResult
import com.example.picomatch3vr.p3a7c9f1.domain.model.PropUseResult
import com.example.picomatch3vr.p3a7c9f1.domain.usecase.Match3GameEngine

interface Match3Repository {
    fun createSession(levelIndex: Int = 1): Match3GameSession

    fun playTurn(
        session: Match3GameSession,
        first: GridPoint,
        second: GridPoint,
    ): Match3TurnResult

    fun useHammer(
        session: Match3GameSession,
        target: GridPoint,
    ): PropUseResult

    fun hasAvailableMove(session: Match3GameSession): Boolean
}

class FakeMatch3Repository(
    private val engine: Match3GameEngine = Match3GameEngine(),
) : Match3Repository {
    override fun createSession(levelIndex: Int): Match3GameSession = engine.createInitialSession(levelIndex)

    override fun playTurn(
        session: Match3GameSession,
        first: GridPoint,
        second: GridPoint,
    ): Match3TurnResult = engine.playTurn(session, first, second)

    override fun useHammer(
        session: Match3GameSession,
        target: GridPoint,
    ): PropUseResult = engine.useHammer(session, target)

    override fun hasAvailableMove(session: Match3GameSession): Boolean = engine.hasAvailableMove(session.board)
}
