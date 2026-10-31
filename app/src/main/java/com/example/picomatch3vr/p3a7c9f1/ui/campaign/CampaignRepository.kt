package com.example.picomatch3vr.p3a7c9f1.ui.campaign

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.picomatch3vr.p3a7c9f1.domain.campaign.*

class CampaignRepository(private val engine: CampaignGameEngine = CampaignGameEngine()) {
    fun createSession(levelIndex: Int) = engine.createInitialSession(levelIndex)
    fun playTurn(session: Match3GameSession, first: GridPoint, second: GridPoint) =
        engine.playTurn(session, first, second)
    fun useHammer(session: Match3GameSession, point: GridPoint) = engine.useHammer(session, point)
    fun hasAvailableMove(session: Match3GameSession) = engine.hasAvailableMove(session.board)
    fun hint(session: Match3GameSession): Pair<GridPoint, GridPoint>? = engine.availableMoves(session.board).firstOrNull()
}

class CampaignProgress(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("campaign_v1", Context.MODE_PRIVATE)
    var currentLevel: Int
        get() = prefs.getInt("current", 1).coerceIn(1, unlockedLevel)
        set(value) { prefs.edit().putInt("current", value.coerceIn(1, 25)).apply() }
    val unlockedLevel: Int get() = prefs.getInt("unlocked", 1).coerceIn(1, 25)
    fun unlock(level: Int) { prefs.edit().putInt("unlocked", maxOf(unlockedLevel, level.coerceIn(1, 25))).apply() }
}

class CampaignViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(GameplayViewModel::class.java))
        return GameplayViewModel(CampaignRepository(), CampaignProgress(context)) as T
    }
}
