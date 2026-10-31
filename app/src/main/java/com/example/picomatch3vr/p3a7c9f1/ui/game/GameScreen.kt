package com.example.picomatch3vr.p3a7c9f1.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.picomatch3vr.p3a7c9f1.ui.game.components.GameBoard
import com.example.picomatch3vr.p3a7c9f1.ui.game.components.GameHud
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.TitleBar

@Composable
fun GameScreen() {
    val viewModel: GameViewModel = viewModel(factory = GameViewModelFactory())
    val state = viewModel.state.collectAsStateWithLifecycle()
    GameContent(
        state = state.value,
        onEvent = viewModel::onEvent,
    )
}

@Composable
internal fun GameContent(
    state: GameUiState,
    onEvent: (GameEvent) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        TitleBar(
            modifier = Modifier.fillMaxWidth(),
            title = {
                Column {
                    Text(
                        text = state.session.title,
                        style = PicoTheme.typography.titleLarge,
                        color = PicoTheme.colorScheme.labelPrimary,
                    )
                    Text(
                        text = "第 ${state.session.levelIndex} 关 · ${state.session.level.name}",
                        style = PicoTheme.typography.labelMedium,
                        color = PicoTheme.colorScheme.labelSecondary,
                    )
                }
            },
            trailingActions = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onEvent(GameEvent.ToggleHammer) }) {
                        Text(if (state.hammerArmed) "取消锤子" else "锤子")
                    }
                    Button(onClick = { onEvent(GameEvent.Restart) }) {
                        Text("重开本关")
                    }
                    if (state.isWin) {
                        Button(onClick = { onEvent(GameEvent.NextLevel) }) {
                            Text("下一关")
                        }
                    }
                }
            },
        )

        GameHud(
            levelName = state.session.level.name,
            score = state.session.score,
            targetScore = state.session.targetScore,
            movesRemaining = state.session.movesRemaining,
            hammerCount = state.session.inventory.hammerCount,
            starsEarned = state.session.starsEarned,
            progress = state.scoreProgress,
            collectedTargets =
                state.session.level.goal.collectTargets.mapValues { (color, target) ->
                    state.session.collectedTiles.getOrDefault(color, 0) to target
                },
            goalProgress = state.session.goalProgress,
            statusMessage = state.statusMessage,
            turnSummary = state.lastTurnSummary,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                modifier = Modifier.weight(1.45f),
                contentAlignment = Alignment.TopCenter,
            ) {
                GameBoard(
                    board = state.displayBoard,
                    selectedCell = state.selectedCell,
                    onCellClick = { point -> onEvent(GameEvent.SelectCell(point)) },
                    onCellDragSwap = { from, to -> onEvent(GameEvent.DragSwap(from, to)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column(
                modifier = Modifier.weight(0.85f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                GameInfoCard(
                    title = "关卡目标",
                    body = state.session.objectiveText,
                )
                GameInfoCard(
                    title = "道具说明",
                    body = if (state.hammerArmed) {
                        "锤子已激活，点击任意糖果可直接击碎，并消耗 1 步。"
                    } else {
                        "像手机三消一样，按住一块糖果朝相邻方向拖动即可交换；若不能消除，会短暂交换后弹回。"
                    },
                )
                GameInfoCard(
                    title = "头显版策略",
                    body = "当前版本先验证玩法主循环，再逐步增加关卡、道具和更强的空间化表现。",
                )
                if (state.isWin) {
                    GameInfoCard(
                        title = "当前状态",
                        body = "本关目标已达成，可以进入下一关继续验证进度系统。",
                    )
                } else if (state.isOutOfMoves) {
                    GameInfoCard(
                        title = "当前状态",
                        body = "步数已经耗尽，本关结束。可以重开本关继续尝试。",
                    )
                } else if (state.canRestart) {
                    GameInfoCard(
                        title = "当前状态",
                        body = "当前棋盘可能没有可行交换，建议重开本关。",
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun GameInfoCard(
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = PicoTheme.typography.titleLarge,
            color = PicoTheme.colorScheme.labelPrimary,
        )
        Text(
            text = body,
            style = PicoTheme.typography.bodyMedium,
            color = PicoTheme.colorScheme.labelSecondary,
        )
    }
}
