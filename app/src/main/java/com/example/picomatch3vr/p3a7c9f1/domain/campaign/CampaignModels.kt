package com.example.picomatch3vr.p3a7c9f1.domain.campaign

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
    ClearIce,
    DropPods,
    CollectGems,
    DeliverSnails,
}

enum class CargoKind { None, GoldenPod }
enum class CampaignMode { Score, Ice, Pods, Gems, Snails }

data class SnailJourney(val route: List<GridPoint>, val position: Int = 0) {
    val completed: Boolean get() = position >= route.lastIndex
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
    val cargo: CargoKind = CargoKind.None,
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
    val mode: CampaignMode = CampaignMode.Score,
    val difficulty: Int = 1,
)

data class Match3Inventory(
    val hammerCount: Int = 3,
)

data class Match3Board(
    val width: Int,
    val height: Int,
    val cells: List<Match3Tile>,
    val ice: Map<GridPoint, Int> = emptyMap(),
    val gems: Map<GridPoint, Int> = emptyMap(),
    val snails: List<SnailJourney> = emptyList(),
    val clearedRoute: Set<GridPoint> = emptySet(),
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
    val useValidatedOpenings: Boolean = true,
) {
    val levels: List<Match3Level> = (1..25).map { index ->
        val tier = (index - 1) % 5 + 1
        val mode = CampaignMode.entries[(index - 1) / 5]
        val score = listOf(11000, 14000, 18000, 22000, 27000)[tier - 1]
        val objective = when (mode) {
            CampaignMode.Score -> GoalObjective(GoalType.ReachScore, score, label = "目标分数")
            CampaignMode.Ice -> GoalObjective(GoalType.ClearIce, 0, label = "冰层")
            CampaignMode.Pods -> GoalObjective(GoalType.DropPods, listOf(2, 3, 3, 4, 4)[tier - 1], label = "金豆荚")
            CampaignMode.Gems -> GoalObjective(GoalType.CollectGems, tier + 3, label = "宝石")
            CampaignMode.Snails -> GoalObjective(GoalType.DeliverSnails, if (tier <= 2) 1 else 2, label = "蜗牛")
        }
        val instructions = when (mode) {
            CampaignMode.Score -> "直线4连造条纹，直线5连造彩虹鸟；交换两个特效可增强。"
            CampaignMode.Ice -> "消除蓝色冰格上的动物，每次减一层；清空所有冰层。"
            CampaignMode.Pods -> "消除豆荚下方动物让它掉落，也能滑动豆荚；送到最底行出口。"
            CampaignMode.Gems -> "消除宝石旁边的动物，或用特效命中；剥开薄荷云收集宝石。"
            CampaignMode.Snails -> "消除路线下一格上的动物，铺好路；蜗牛会沿已清路线走到终点。"
        }
        Match3Level(
            index = index,
            name = listOf("彩虹启程", "冰晶花园", "金豆荚之旅", "云端寻宝", "蜗牛快递")[(index - 1) / 5],
            subtitle = instructions,
            boardWidth = boardWidth, boardHeight = boardHeight,
            startingMoves = when (mode) {
                CampaignMode.Score -> listOf(25, 25, 24, 24, 23)[tier - 1]
                CampaignMode.Ice -> 32 - tier
                CampaignMode.Pods -> 33 - tier
                CampaignMode.Gems -> listOf(28, 28, 27, 26, 25)[tier - 1]
                CampaignMode.Snails -> listOf(31, 30, 30, 29, 29)[tier - 1]
            },
            goal = LevelGoal(if (mode == CampaignMode.Score) score else 6000 + tier * 1500, listOf(objective)),
            starThresholds = listOf(score, score * 3 / 2, score * 2),
            mode = mode,
            difficulty = tier,
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
    val iceCleared: Int = 0,
    val podsDelivered: Int = 0,
    val gemsCollected: Int = 0,
) {
    val goalProgress: List<GoalProgress>
        get() =
            level.goal.objectives.map { objective ->
                val current =
                    when (objective.type) {
                        GoalType.ReachScore -> score
                        GoalType.CollectColor -> collectedTiles.getOrDefault(objective.color, 0)
                        GoalType.ClearTiles -> totalClearedTiles
                        GoalType.ClearIce -> iceCleared
                        GoalType.DropPods -> podsDelivered
                        GoalType.CollectGems -> gemsCollected
                        GoalType.DeliverSnails -> board.snails.count { it.completed }
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
