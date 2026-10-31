package com.example.picomatch3vr.p3a7c9f1.domain.model

import kotlin.math.abs

enum class TileColor(
    val symbol: String,
    val displayName: String,
) {
    Strawberry("S", "草莓"),
    Lemon("L", "柠檬"),
    Blueberry("B", "蓝莓"),
    Mint("M", "薄荷"),
    Peach("P", "蜜桃"),
    Grape("G", "葡萄"),
}

data class GridPoint(
    val row: Int,
    val column: Int,
) {
    fun isAdjacentTo(other: GridPoint): Boolean = abs(row - other.row) + abs(column - other.column) == 1
}

enum class SpecialTileKind {
    None,
    HorizontalLine,
    VerticalLine,
    Bomb,
    ColorBomb,
}

enum class GoalType {
    ReachScore,
    ClearTiles,
    CollectColor,
}

data class GoalObjective(
    val type: GoalType,
    val targetCount: Int,
    val color: TileColor? = null,
    val label: String,
)

data class Match3Tile(
    val id: Long,
    val color: TileColor,
    val special: SpecialTileKind = SpecialTileKind.None,
)

data class LevelGoal(
    val targetScore: Int = 0,
    val objectives: List<GoalObjective> = emptyList(),
) {
    val collectTargets: Map<TileColor, Int>
        get() =
            objectives
                .filter { it.type == GoalType.CollectColor && it.color != null }
                .associate { objective -> objective.color!! to objective.targetCount }
}

data class Match3Level(
    val index: Int,
    val name: String,
    val subtitle: String,
    val boardWidth: Int,
    val boardHeight: Int,
    val startingMoves: Int,
    val goal: LevelGoal,
    val starThresholds: List<Int>,
)

data class Match3Inventory(
    val hammerCount: Int = 1,
)

data class Match3Board(
    val width: Int,
    val height: Int,
    val cells: List<Match3Tile>,
) {
    init {
        require(width > 0) { "Board width must be positive." }
        require(height > 0) { "Board height must be positive." }
        require(cells.size == width * height) {
            "Board cell count ${cells.size} does not match ${width * height}."
        }
    }

    fun contains(point: GridPoint): Boolean = point.row in 0 until height && point.column in 0 until width

    fun indexOf(point: GridPoint): Int = point.row * width + point.column

    fun tileAt(point: GridPoint): Match3Tile {
        require(contains(point)) { "Point $point is outside the board." }
        return cells[indexOf(point)]
    }

    fun replace(point: GridPoint, tile: Match3Tile): Match3Board {
        val mutable = cells.toMutableList()
        mutable[indexOf(point)] = tile
        return copy(cells = mutable)
    }

    fun replaceAll(replacements: Map<GridPoint, Match3Tile>): Match3Board {
        if (replacements.isEmpty()) {
            return this
        }
        val mutable = cells.toMutableList()
        replacements.forEach { (point, tile) ->
            mutable[indexOf(point)] = tile
        }
        return copy(cells = mutable)
    }

    fun row(rowIndex: Int): List<Match3Tile> = cells.subList(rowIndex * width, (rowIndex + 1) * width)

    fun swap(first: GridPoint, second: GridPoint): Match3Board {
        val mutable = cells.toMutableList()
        val firstIndex = indexOf(first)
        val secondIndex = indexOf(second)
        val temp = mutable[firstIndex]
        mutable[firstIndex] = mutable[secondIndex]
        mutable[secondIndex] = temp
        return copy(cells = mutable)
    }
}

data class Match3GameConfig(
    val title: String = "空间消消乐",
    val subtitle: String = "把快乐留在每一次消除里",
    val objectiveText: String = "在步数用尽前完成关卡目标",
    val boardWidth: Int = 8,
    val boardHeight: Int = 8,
    val availableColors: List<TileColor> = TileColor.entries,
    val startingSeed: Int = 7,
) {
    val levels: List<Match3Level> = (1..20).map { index ->
        val score = 2400 + (index - 1) * 450
        val targetColor = availableColors[(index - 1) % availableColors.size]
        val objectives = when {
            index <= 4 -> listOf(GoalObjective(GoalType.ReachScore, score, label = "目标分数"))
            index % 3 == 0 -> listOf(
                GoalObjective(GoalType.CollectColor, 15 + index, targetColor, animalName(targetColor)),
            )
            index % 3 == 1 -> listOf(
                GoalObjective(GoalType.ReachScore, score, label = "分数"),
                GoalObjective(GoalType.CollectColor, 10 + index, targetColor, animalName(targetColor)),
            )
            else -> listOf(GoalObjective(GoalType.ClearTiles, 50 + index * 5, label = "消除动物"))
        }
        Match3Level(
            index = index,
            name = when { index <= 4 -> "特效启程"; index <= 10 -> "彩虹收集"; index <= 15 -> "连锁挑战"; else -> "特效大师" },
            subtitle = "合成特效，组合引爆，在步数内完成目标",
            boardWidth = boardWidth, boardHeight = boardHeight,
            startingMoves = (24 - (index - 1) / 3).coerceAtLeast(17),
            goal = LevelGoal(score, objectives),
            starThresholds = listOf(score, score + 1600, score + 3200),
        )
    }

    private fun animalName(color: TileColor): String = when (color) {
        TileColor.Strawberry -> "狐狸"
        TileColor.Lemon -> "小鸡"
        TileColor.Blueberry -> "猫头鹰"
        TileColor.Mint -> "青蛙"
        TileColor.Peach -> "小熊"
        TileColor.Grape -> "河马"
    }

}

data class GoalProgress(
    val objective: GoalObjective,
    val currentCount: Int,
) {
    val isCompleted: Boolean = currentCount >= objective.targetCount
}

data class ActivatedSpecial(
    val point: GridPoint,
    val kind: SpecialTileKind,
    val sourceColor: TileColor?,
    val delaySeconds: Float = 0f,
)

data class MatchGroup(
    val color: TileColor,
    val points: Set<GridPoint>,
    val horizontal: Int,
    val vertical: Int,
) {
    val size: Int = points.size
}

data class Match3CascadeStep(
    val stepIndex: Int,
    val removedPoints: Set<GridPoint>,
    val createdSpecials: Map<GridPoint, SpecialTileKind>,
    val activatedSpecials: List<ActivatedSpecial>,
    val scoreDelta: Int,
    val boardAfterCollapse: Match3Board,
    val fallDistances: Map<GridPoint, Int>,
    val boardBeforeClear: Match3Board? = null,
)

data class Match3GameSession(
    val level: Match3Level,
    val board: Match3Board,
    val score: Int,
    val movesRemaining: Int,
    val targetScore: Int,
    val title: String,
    val subtitle: String,
    val objectiveText: String,
    val seed: Int,
    val levelIndex: Int,
    val collectedTiles: Map<TileColor, Int>,
    val totalClearedTiles: Int = 0,
    val inventory: Match3Inventory,
    val starsEarned: Int = 0,
) {
    val goalProgress: List<GoalProgress>
        get() =
            level.goal.objectives.map { objective ->
                val current =
                    when (objective.type) {
                        GoalType.ReachScore -> score
                        GoalType.CollectColor -> collectedTiles.getOrDefault(objective.color, 0)
                        GoalType.ClearTiles -> totalClearedTiles
                    }
                GoalProgress(objective = objective, currentCount = current)
            }
}

data class Match3TurnResult(
    val session: Match3GameSession,
    val validMove: Boolean,
    val clearedTiles: Int,
    val cascadeCount: Int,
    val scoreDelta: Int,
    val statusMessage: String,
    val cascadeSteps: List<Match3CascadeStep> = emptyList(),
    val goalsProgress: List<GoalProgress> = emptyList(),
)

data class PropUseResult(
    val session: Match3GameSession,
    val used: Boolean,
    val scoreDelta: Int,
    val clearedTiles: Int,
    val statusMessage: String,
)
