package com.example.picomatch3vr.p3a7c9f1.ui.game.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.picomatch3vr.p3a7c9f1.domain.model.GoalProgress
import com.example.picomatch3vr.p3a7c9f1.domain.model.TileColor
import com.example.picomatch3vr.p3a7c9f1.ui.theme.Match3DesignTokens
import com.pico.spatial.ui.design.LinearProgressIndicator
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text

@androidx.compose.runtime.Composable
fun GameHud(
    levelName: String,
    score: Int,
    targetScore: Int,
    movesRemaining: Int,
    hammerCount: Int,
    starsEarned: Int,
    progress: Float,
    collectedTargets: Map<TileColor, Pair<Int, Int>>,
    goalProgress: List<GoalProgress>,
    statusMessage: String,
    turnSummary: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(24.dp))
                .background(PicoTheme.colorScheme.fillSecondary)
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatBlock(label = "关卡", value = levelName)
            StatBlock(label = "分数", value = score.toString())
            StatBlock(label = "目标", value = targetScore.toString())
            StatBlock(label = "步数", value = movesRemaining.toString())
            StatBlock(
                label = "星级",
                value = if (starsEarned > 0) "★".repeat(starsEarned) else "☆",
            )
            StatBlock(label = "锤子", value = hammerCount.toString())
        }
        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) })
        if (goalProgress.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                goalProgress.forEach { progressItem ->
                    Text(
                        text = "${if (progressItem.isCompleted) "已完成" else "进行中"} · ${progressItem.objective.label} · ${progressItem.currentCount}/${progressItem.objective.targetCount}",
                        style = PicoTheme.typography.labelLarge,
                        color = if (progressItem.isCompleted) Match3DesignTokens.AccentMint else PicoTheme.colorScheme.labelSecondary,
                    )
                }
            }
        }
        if (collectedTargets.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                collectedTargets.forEach { (color, progressPair) ->
                    GoalChip(
                        label = color.displayName,
                        current = progressPair.first,
                        target = progressPair.second,
                    )
                }
            }
        }
        Text(
            text = statusMessage,
            style = PicoTheme.typography.bodyMedium,
            color = PicoTheme.colorScheme.labelPrimary,
        )
        Text(
            text = turnSummary,
            style = PicoTheme.typography.labelLarge,
            color = Match3DesignTokens.AccentGold,
        )
    }
}

@androidx.compose.runtime.Composable
private fun StatBlock(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = PicoTheme.typography.labelMedium,
            color = PicoTheme.colorScheme.labelSecondary,
        )
        Text(
            text = value,
            style = PicoTheme.typography.titleLarge,
            color = PicoTheme.colorScheme.labelPrimary,
        )
    }
}

@androidx.compose.runtime.Composable
private fun GoalChip(
    label: String,
    current: Int,
    target: Int,
) {
    Column(
        modifier =
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(PicoTheme.colorScheme.fillPrimary)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = PicoTheme.typography.labelMedium,
            color = PicoTheme.colorScheme.labelSecondary,
        )
        Text(
            text = "$current / $target",
            style = PicoTheme.typography.titleLarge,
            color = PicoTheme.colorScheme.labelPrimary,
        )
    }
}
