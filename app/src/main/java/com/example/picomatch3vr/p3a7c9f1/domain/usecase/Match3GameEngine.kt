package com.example.picomatch3vr.p3a7c9f1.domain.usecase

import com.example.picomatch3vr.p3a7c9f1.domain.model.ActivatedSpecial
import com.example.picomatch3vr.p3a7c9f1.domain.model.GoalProgress
import com.example.picomatch3vr.p3a7c9f1.domain.model.GoalType
import com.example.picomatch3vr.p3a7c9f1.domain.model.GridPoint
import com.example.picomatch3vr.p3a7c9f1.domain.model.Match3Board
import com.example.picomatch3vr.p3a7c9f1.domain.model.Match3CascadeStep
import com.example.picomatch3vr.p3a7c9f1.domain.model.Match3GameConfig
import com.example.picomatch3vr.p3a7c9f1.domain.model.Match3GameSession
import com.example.picomatch3vr.p3a7c9f1.domain.model.Match3Inventory
import com.example.picomatch3vr.p3a7c9f1.domain.model.Match3Tile
import com.example.picomatch3vr.p3a7c9f1.domain.model.Match3TurnResult
import com.example.picomatch3vr.p3a7c9f1.domain.model.MatchGroup
import com.example.picomatch3vr.p3a7c9f1.domain.model.PropUseResult
import com.example.picomatch3vr.p3a7c9f1.domain.model.SpecialTileKind
import com.example.picomatch3vr.p3a7c9f1.domain.model.TileColor
import kotlin.random.Random

class Match3GameEngine(
    private val config: Match3GameConfig = Match3GameConfig(),
) {
    private var nextTileId: Long = 1L
    private var sessionSeed: Int = config.startingSeed

    fun createInitialSession(levelIndex: Int = 1): Match3GameSession {
        nextTileId = 1L
        val level = config.levels.firstOrNull { it.index == levelIndex } ?: config.levels.first()
        val seed = sessionSeed++
        val random = Random(seed)
        val board = createStableBoard(level.boardWidth, level.boardHeight, random)
        return Match3GameSession(
            level = level,
            board = board,
            score = 0,
            movesRemaining = level.startingMoves,
            targetScore = level.goal.targetScore,
            title = config.title,
            subtitle = level.subtitle,
            objectiveText = buildObjectiveText(level.goal.objectives),
            seed = seed,
            levelIndex = level.index,
            collectedTiles = level.goal.collectTargets.mapValues { 0 },
            inventory = Match3Inventory(),
            starsEarned = 0,
        )
    }

    fun playTurn(
        session: Match3GameSession,
        first: GridPoint,
        second: GridPoint,
    ): Match3TurnResult {
        if (!session.board.contains(first) || !session.board.contains(second)) {
            return invalidTurn(session, "选择超出了棋盘范围。")
        }
        if (!first.isAdjacentTo(second)) {
            return invalidTurn(session, "只能交换上下左右相邻的小动物。")
        }
        if (session.movesRemaining <= 0) {
            return invalidTurn(session, "步数已经用完，请重新开始。")
        }

        val firstTile = session.board.tileAt(first)
        val secondTile = session.board.tileAt(second)
        val swappedBoard = session.board.swap(first, second)
        val comboResult = resolveSpecialCombo(swappedBoard, second, first, firstTile, secondTile)
        if (comboResult != null) {
            return settleTurn(
                session = session,
                swappedBoard = comboResult.board,
                first = first,
                second = second,
                initialRemoved = comboResult.removed,
                initialActivated = comboResult.activatedSpecials,
                initialCreatedSpecials = emptyMap(),
            )
        }

        val groups = findMatchGroups(swappedBoard)
        if (groups.isEmpty()) {
            return invalidTurn(session, "这次交换不能形成消除。")
        }

        val createdSpecials = determineCreatedSpecials(groups, swappedBoard, first, second)
        val removedPoints =
            groups
                .flatMapTo(linkedSetOf()) { it.points }
                .minus(createdSpecials.keys)

        return settleTurn(
            session = session,
            swappedBoard = swappedBoard,
            first = first,
            second = second,
            initialRemoved = removedPoints,
            initialActivated = emptyList(),
            initialCreatedSpecials = createdSpecials,
        )
    }

    fun useHammer(
        session: Match3GameSession,
        target: GridPoint,
    ): PropUseResult {
        if (!session.board.contains(target)) {
            return invalidPropUse(session, "锤子目标超出了棋盘范围。")
        }
        if (session.inventory.hammerCount <= 0) {
            return invalidPropUse(session, "没有可用的锤子道具。")
        }
        if (session.movesRemaining <= 0) {
            return invalidPropUse(session, "步数已经用完，无法继续使用道具。")
        }

        val expansion = expandSpecialChain(session.board, setOf(target), emptyList(), emptySet())
        val removed = expansion.first
        val collected = removed.associateByColor(session.board)

        val random = Random(session.seed + session.score + session.movesRemaining + target.row * 31 + target.column)
        val collapsedBoard = collapseBoard(session.board, removed, emptyMap(), random).board
        val scoreDelta = removed.size * 120
        val updatedSession =
            session.copy(
                board = collapsedBoard,
                score = session.score + scoreDelta,
                movesRemaining = session.movesRemaining - 1,
                collectedTiles = mergeCollectedTiles(session.collectedTiles, collected),
                totalClearedTiles = session.totalClearedTiles + removed.size,
                inventory = session.inventory.copy(hammerCount = session.inventory.hammerCount - 1),
                starsEarned = calculateStars(session.level.starThresholds, session.score + scoreDelta),
            )

        return PropUseResult(
            session = updatedSession,
            used = true,
            scoreDelta = scoreDelta,
            clearedTiles = removed.size,
            statusMessage = "锤子已击碎 ${removed.size} 块，并消耗 1 步。",
        )
    }

    fun hasAvailableMove(board: Match3Board): Boolean {
        for (row in 0 until board.height) {
            for (column in 0 until board.width) {
                val point = GridPoint(row, column)
                val neighbors =
                    listOf(
                        GridPoint(row + 1, column),
                        GridPoint(row, column + 1),
                    )
                if (neighbors.any { neighbor ->
                        board.contains(neighbor) &&
                            isPotentialMove(board, point, neighbor)
                    }
                ) {
                    return true
                }
            }
        }
        return false
    }

    fun isLevelCompleted(session: Match3GameSession): Boolean =
        session.goalProgress.all { it.isCompleted }

    private fun settleTurn(
        session: Match3GameSession,
        swappedBoard: Match3Board,
        first: GridPoint,
        second: GridPoint,
        initialRemoved: Set<GridPoint>,
        initialActivated: List<ActivatedSpecial>,
        initialCreatedSpecials: Map<GridPoint, SpecialTileKind>,
    ): Match3TurnResult {
        nextTileId = maxOf(nextTileId, swappedBoard.cells.maxOf { it.id } + 1)
        val random = Random(session.score + session.movesRemaining + session.seed + first.row * 13 + second.column * 17)
        var board = swappedBoard
        var removed = initialRemoved.toMutableSet()
        var createdSpecials = initialCreatedSpecials
        var activatedSpecials = initialActivated
        val collected = mutableMapOf<TileColor, Int>()
        val cascadeSteps = mutableListOf<Match3CascadeStep>()
        var totalCleared = 0
        var totalScore = 0
        var cascadeIndex = 0

        while (removed.isNotEmpty() || createdSpecials.isNotEmpty()) {
            cascadeIndex += 1
            val expansion = expandSpecialChain(board, removed, activatedSpecials, createdSpecials.keys)
            val expandedRemoved = expansion.first
            val expandedActivated = expansion.second
            val survivingCreated = createdSpecials.filterKeys { it !in expandedRemoved }

            expandedRemoved.forEach { point ->
                val tile = board.tileAt(point)
                collected.merge(tile.color, 1, Int::plus)
            }

            val scoreDelta = calculateCascadeScore(expandedRemoved.size, survivingCreated.values, cascadeIndex, expandedActivated)
            totalCleared += expandedRemoved.size
            totalScore += scoreDelta
            val collapse = collapseBoard(board, expandedRemoved, survivingCreated, random)

            cascadeSteps +=
                Match3CascadeStep(
                    stepIndex = cascadeIndex,
                    removedPoints = expandedRemoved.toSet(),
                    createdSpecials = survivingCreated,
                    activatedSpecials = expandedActivated,
                    scoreDelta = scoreDelta,
                    boardAfterCollapse = collapse.board,
                    fallDistances = collapse.fallDistances,
                    boardBeforeClear = board,
                )

            board = collapse.board

            val groups = findMatchGroups(board)
            if (groups.isEmpty()) {
                removed = mutableSetOf()
                createdSpecials = emptyMap()
                activatedSpecials = emptyList()
            } else {
                createdSpecials = determineCreatedSpecials(groups, board, null, null)
                removed =
                    groups
                        .flatMapTo(linkedSetOf()) { it.points }
                        .minus(createdSpecials.keys)
                        .toMutableSet()
                activatedSpecials = emptyList()
            }
        }

        val updatedScore = session.score + totalScore
        val updatedCollected = mergeCollectedTiles(session.collectedTiles, collected)
        val updatedSession =
            session.copy(
                board = board,
                score = updatedScore,
                movesRemaining = session.movesRemaining - 1,
                collectedTiles = updatedCollected,
                totalClearedTiles = session.totalClearedTiles + totalCleared,
                starsEarned = calculateStars(session.level.starThresholds, updatedScore),
            )

        val progress = buildGoalProgress(updatedSession)
        val statusMessage =
            when {
                isLevelCompleted(updatedSession) ->
                    "当前关卡目标已达成！本回合触发 $cascadeIndex 次结算，得分 +$totalScore。"
                cascadeIndex > 1 ->
                    "触发 $cascadeIndex 次连锁，共消除 $totalCleared 块，得分 +$totalScore。"
                createdAnySpecial(cascadeSteps) ->
                    "成功消除 $totalCleared 块，并合成了特效，得分 +$totalScore。"
                else ->
                    "成功消除 $totalCleared 块，得分 +$totalScore。"
            }

        return Match3TurnResult(
            session = updatedSession,
            validMove = true,
            clearedTiles = totalCleared,
            cascadeCount = cascadeIndex,
            scoreDelta = totalScore,
            statusMessage = statusMessage,
            cascadeSteps = cascadeSteps,
            goalsProgress = progress,
        )
    }

    private fun resolveSpecialCombo(
        board: Match3Board,
        first: GridPoint,
        second: GridPoint,
        firstTile: Match3Tile,
        secondTile: Match3Tile,
    ): SpecialComboResolution? {
        if (firstTile.special == SpecialTileKind.ColorBomb && secondTile.special == SpecialTileKind.ColorBomb) {
            val removed = allPoints(board)
            return SpecialComboResolution(
                board = board,
                removed = removed,
                activatedSpecials =
                    listOf(
                        ActivatedSpecial(first, firstTile.special, firstTile.color),
                        ActivatedSpecial(second, secondTile.special, secondTile.color),
                    ),
            )
        }

        if (firstTile.special == SpecialTileKind.ColorBomb || secondTile.special == SpecialTileKind.ColorBomb) {
            val colorBombPoint = if (firstTile.special == SpecialTileKind.ColorBomb) first else second
            val otherPoint = if (colorBombPoint == first) second else first
            val otherTile = if (colorBombPoint == first) secondTile else firstTile
            val removed =
                allPoints(board)
                    .filterTo(linkedSetOf()) { point ->
                        point == colorBombPoint || point == otherPoint || board.tileAt(point).color == otherTile.color
                    }
            if (otherTile.special == SpecialTileKind.None) {
                return SpecialComboResolution(
                    board = board,
                    removed = removed,
                    activatedSpecials =
                        listOf(
                            ActivatedSpecial(colorBombPoint, SpecialTileKind.ColorBomb, otherTile.color),
                        ),
                )
            }

            val expandedRemoved = removed.toMutableSet()
            val replacements = mutableMapOf<GridPoint, Match3Tile>()
            val activated =
                mutableListOf(
                    ActivatedSpecial(colorBombPoint, SpecialTileKind.ColorBomb, otherTile.color),
                )
            boardPointsWithColor(board, otherTile.color).filter { it != colorBombPoint }.forEachIndexed { index, point ->
                val kind =
                    when (otherTile.special) {
                        SpecialTileKind.Bomb -> SpecialTileKind.Bomb
                        SpecialTileKind.HorizontalLine,
                        SpecialTileKind.VerticalLine -> if ((point.row + point.column) % 2 == 0) SpecialTileKind.HorizontalLine else SpecialTileKind.VerticalLine
                        else -> otherTile.special
                    }
                replacements[point] = board.tileAt(point).copy(special = kind)
                activated += ActivatedSpecial(point, kind, otherTile.color, 0.12f + index * 0.018f)
                expandedRemoved += triggerSpecialFromKind(board, point, kind)
            }
            return SpecialComboResolution(
                board = board.replaceAll(replacements),
                removed = expandedRemoved,
                activatedSpecials = activated,
            )
        }

        if (firstTile.special != SpecialTileKind.None && secondTile.special != SpecialTileKind.None) {
            val activated =
                listOf(
                    ActivatedSpecial(first, firstTile.special, firstTile.color),
                    ActivatedSpecial(second, secondTile.special, secondTile.color),
                )
            val removed = mutableSetOf(first, second)
            removed += combineSpecials(board, first, firstTile.special, second, secondTile.special)
            return SpecialComboResolution(
                board = board,
                removed = removed,
                activatedSpecials = activated,
            )
        }

        return null
    }

    private fun combineSpecials(
        board: Match3Board,
        first: GridPoint,
        firstKind: SpecialTileKind,
        second: GridPoint,
        secondKind: SpecialTileKind,
    ): Set<GridPoint> {
        return when {
            isLine(firstKind) && isLine(secondKind) -> {
                rowPoints(board, first.row) + columnPoints(board, first.column)
            }
            (isLine(firstKind) && secondKind == SpecialTileKind.Bomb) ||
                (firstKind == SpecialTileKind.Bomb && isLine(secondKind)) -> {
                val center = first
                val removed = mutableSetOf<GridPoint>()
                for (row in center.row - 1..center.row + 1) {
                    removed += rowPoints(board, row)
                }
                for (column in center.column - 1..center.column + 1) {
                    removed += columnPoints(board, column)
                }
                removed
            }
            firstKind == SpecialTileKind.Bomb && secondKind == SpecialTileKind.Bomb -> {
                squarePoints(board, first, radius = 2) + squarePoints(board, second, radius = 2)
            }
            else -> {
                triggerSpecialFromKind(board, first, firstKind) + triggerSpecialFromKind(board, second, secondKind)
            }
        }
    }

    private fun createStableBoard(width: Int, height: Int, random: Random): Match3Board {
        var attempts = 0
        while (true) {
            attempts += 1
            val board =
                Match3Board(
                    width = width,
                    height = height,
                    cells = List(width * height) { newTile(random) },
                )
            if (findMatchGroups(board).isEmpty() && hasAvailableMove(board)) {
                return board
            }
            require(attempts < 500) { "Failed to generate a stable opening board." }
        }
    }

    private fun collapseBoard(
        board: Match3Board,
        removedPoints: Set<GridPoint>,
        createdSpecials: Map<GridPoint, SpecialTileKind>,
        random: Random,
    ): CollapseResult {
        val replacements =
            createdSpecials.mapValues { (point, special) ->
                val tile = board.tileAt(point)
                tile.copy(special = special)
            }
        val boardWithSpecials = board.replaceAll(replacements)
        val columns = MutableList(board.width) { mutableListOf<Match3Tile>() }
        val fallDistances = mutableMapOf<GridPoint, Int>()

        for (column in 0 until board.width) {
            val sourceRowsByTileId = mutableMapOf<Long, Int>()
            for (row in board.height - 1 downTo 0) {
                val point = GridPoint(row, column)
                if (point !in removedPoints) {
                    val tile = boardWithSpecials.tileAt(point)
                    columns[column].add(tile)
                    sourceRowsByTileId[tile.id] = row
                }
            }
            while (columns[column].size < board.height) {
                columns[column].add(newTile(random))
            }

            val sourceColumn = columns[column]
            for (visualRow in board.height - 1 downTo 0) {
                val indexFromBottom = board.height - 1 - visualRow
                val tile = sourceColumn[indexFromBottom]
                val destinationPoint = GridPoint(visualRow, column)
                val sourceRow = sourceRowsByTileId[tile.id]
                val fallDistance =
                    if (sourceRow == null) {
                        sourceColumn.size - indexFromBottom
                    } else {
                        visualRow - sourceRow
                    }
                if (fallDistance > 0) {
                    fallDistances[destinationPoint] = fallDistance
                }
            }
        }

        val rebuiltCells = MutableList(board.width * board.height) { columns[0][0] }
        for (column in 0 until board.width) {
            val sourceColumn = columns[column]
            for (visualRow in board.height - 1 downTo 0) {
                val indexFromBottom = board.height - 1 - visualRow
                rebuiltCells[visualRow * board.width + column] = sourceColumn[indexFromBottom]
            }
        }
        return CollapseResult(
            board = board.copy(cells = rebuiltCells),
            fallDistances = fallDistances,
        )
    }

    private fun findMatchGroups(board: Match3Board): List<MatchGroup> {
        val horizontalRuns = mutableListOf<List<GridPoint>>()
        val verticalRuns = mutableListOf<List<GridPoint>>()

        for (row in 0 until board.height) {
            var start = 0
            while (start < board.width) {
                val color = board.tileAt(GridPoint(row, start)).color
                var end = start + 1
                while (end < board.width && board.tileAt(GridPoint(row, end)).color == color) {
                    end += 1
                }
                if (end - start >= 3) {
                    horizontalRuns += (start until end).map { column -> GridPoint(row, column) }
                }
                start = end
            }
        }

        for (column in 0 until board.width) {
            var start = 0
            while (start < board.height) {
                val color = board.tileAt(GridPoint(start, column)).color
                var end = start + 1
                while (end < board.height && board.tileAt(GridPoint(end, column)).color == color) {
                    end += 1
                }
                if (end - start >= 3) {
                    verticalRuns += (start until end).map { row -> GridPoint(row, column) }
                }
                start = end
            }
        }

        val runsByPoint = mutableMapOf<GridPoint, MutableList<List<GridPoint>>>()
        (horizontalRuns + verticalRuns).forEach { run ->
            run.forEach { point ->
                runsByPoint.getOrPut(point) { mutableListOf() }.add(run)
            }
        }

        val visited = mutableSetOf<GridPoint>()
        val groups = mutableListOf<MatchGroup>()
        for (point in runsByPoint.keys) {
            if (point in visited) {
                continue
            }
            val queue = ArrayDeque<GridPoint>()
            val points = linkedSetOf<GridPoint>()
            queue += point
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                if (!visited.add(current)) {
                    continue
                }
                points += current
                runsByPoint[current].orEmpty().flatten().forEach { neighbor ->
                    if (neighbor !in visited) {
                        queue += neighbor
                    }
                }
            }
            val horizontal = horizontalRuns.filter { run -> run.any { it in points } }.maxOfOrNull { it.size } ?: 0
            val vertical = verticalRuns.filter { run -> run.any { it in points } }.maxOfOrNull { it.size } ?: 0
            groups += MatchGroup(color = board.tileAt(point).color, points = points, horizontal = horizontal, vertical = vertical)
        }
        return groups
    }

    private fun determineCreatedSpecials(
        groups: List<MatchGroup>,
        board: Match3Board,
        first: GridPoint?,
        second: GridPoint?,
    ): Map<GridPoint, SpecialTileKind> {
        val created = mutableMapOf<GridPoint, SpecialTileKind>()
        groups.forEach { group ->
            val special =
                when {
                    group.horizontal >= 5 || group.vertical >= 5 -> SpecialTileKind.ColorBomb
                    group.horizontal >= 3 && group.vertical >= 3 -> SpecialTileKind.Bomb
                    group.horizontal == 4 -> SpecialTileKind.HorizontalLine
                    group.vertical == 4 -> SpecialTileKind.VerticalLine
                    else -> SpecialTileKind.None
                }
            if (special == SpecialTileKind.None) {
                return@forEach
            }
            val preferred =
                listOfNotNull(first, second)
                    .firstOrNull { it in group.points }
                    ?: chooseCenterPoint(group.points)
            created[preferred] = special
        }
        return created
    }

    private fun expandSpecialChain(
        board: Match3Board,
        initial: Set<GridPoint>,
        alreadyActivated: List<ActivatedSpecial>,
        newlyCreated: Set<GridPoint>,
    ): Pair<Set<GridPoint>, List<ActivatedSpecial>> {
        val removed = initial.toMutableSet()
        val activated = alreadyActivated.distinctBy { it.point }.toMutableList()
        val triggered = activated.mapTo(mutableSetOf()) { it.point }
        val queue = ArrayDeque(initial)
        while (queue.isNotEmpty()) {
            val point = queue.removeFirst()
            val tile = board.tileAt(point)
            if (tile.special == SpecialTileKind.None || point in newlyCreated || !triggered.add(point)) continue
            val delay = if (activated.isEmpty()) 0f else activated.maxOf { it.delaySeconds } + 0.035f
            activated += ActivatedSpecial(point, tile.special, tile.color, delay)
            val extra = if (tile.special == SpecialTileKind.ColorBomb) boardPointsWithColor(board, tile.color) + point
                else triggerSpecialFromKind(board, point, tile.special)
            extra.forEach {
                if (removed.add(it)) queue.addLast(it)
            }
        }
        return removed to activated
    }

    private fun triggerSpecialFromKind(
        board: Match3Board,
        point: GridPoint,
        kind: SpecialTileKind,
    ): Set<GridPoint> {
        return when (kind) {
            SpecialTileKind.None -> emptySet()
            SpecialTileKind.HorizontalLine -> rowPoints(board, point.row)
            SpecialTileKind.VerticalLine -> columnPoints(board, point.column)
            SpecialTileKind.Bomb -> squarePoints(board, point, radius = 1)
            SpecialTileKind.ColorBomb -> allPoints(board)
        }
    }

    private fun isPotentialMove(
        board: Match3Board,
        first: GridPoint,
        second: GridPoint,
    ): Boolean {
        val firstTile = board.tileAt(first)
        val secondTile = board.tileAt(second)
        if (firstTile.special == SpecialTileKind.ColorBomb || secondTile.special == SpecialTileKind.ColorBomb ||
            (firstTile.special != SpecialTileKind.None && secondTile.special != SpecialTileKind.None)) {
            return true
        }
        return findMatchGroups(board.swap(first, second)).isNotEmpty()
    }

    private fun buildGoalProgress(session: Match3GameSession): List<GoalProgress> = session.goalProgress

    private fun mergeCollectedTiles(
        current: Map<TileColor, Int>,
        delta: Map<TileColor, Int>,
    ): Map<TileColor, Int> {
        if (delta.isEmpty()) {
            return current
        }
        val updated = current.toMutableMap()
        delta.forEach { (color, count) ->
            if (color in updated) {
                updated[color] = updated.getValue(color) + count
            }
        }
        return updated
    }

    private fun calculateStars(
        thresholds: List<Int>,
        score: Int,
    ): Int = thresholds.count { score >= it }.coerceAtMost(3)

    private fun calculateCascadeScore(
        removedCount: Int,
        createdSpecials: Collection<SpecialTileKind>,
        cascadeIndex: Int,
        activatedSpecials: List<ActivatedSpecial>,
    ): Int {
        val base = removedCount * 100 * cascadeIndex
        val creationBonus =
            createdSpecials.sumOf { special ->
                when (special) {
                    SpecialTileKind.HorizontalLine,
                    SpecialTileKind.VerticalLine -> 200
                    SpecialTileKind.Bomb -> 300
                    SpecialTileKind.ColorBomb -> 500
                    SpecialTileKind.None -> 0
                }.toInt()
            }
        val activatedBonus = activatedSpecials.size * 150
        return base + creationBonus + activatedBonus
    }

    private fun buildObjectiveText(objectives: List<com.example.picomatch3vr.p3a7c9f1.domain.model.GoalObjective>): String =
        objectives.joinToString(separator = "；") { it.label }

    private fun invalidTurn(
        session: Match3GameSession,
        message: String,
    ): Match3TurnResult =
        Match3TurnResult(
            session = session,
            validMove = false,
            clearedTiles = 0,
            cascadeCount = 0,
            scoreDelta = 0,
            statusMessage = message,
            goalsProgress = buildGoalProgress(session),
        )

    private fun invalidPropUse(
        session: Match3GameSession,
        message: String,
    ): PropUseResult =
        PropUseResult(
            session = session,
            used = false,
            scoreDelta = 0,
            clearedTiles = 0,
            statusMessage = message,
        )

    private fun createdAnySpecial(steps: List<Match3CascadeStep>): Boolean =
        steps.any { it.createdSpecials.isNotEmpty() }

    private fun chooseCenterPoint(points: Set<GridPoint>): GridPoint =
        points.sortedWith(compareBy<GridPoint> { it.row }.thenBy { it.column })[points.size / 2]

    private fun rowPoints(board: Match3Board, row: Int): Set<GridPoint> =
        if (row !in 0 until board.height) emptySet() else (0 until board.width).mapTo(linkedSetOf()) { column -> GridPoint(row, column) }

    private fun columnPoints(board: Match3Board, column: Int): Set<GridPoint> =
        if (column !in 0 until board.width) emptySet() else (0 until board.height).mapTo(linkedSetOf()) { row -> GridPoint(row, column) }

    private fun squarePoints(
        board: Match3Board,
        center: GridPoint,
        radius: Int,
    ): Set<GridPoint> {
        val points = linkedSetOf<GridPoint>()
        for (row in center.row - radius..center.row + radius) {
            for (column in center.column - radius..center.column + radius) {
                val point = GridPoint(row, column)
                if (board.contains(point)) {
                    points += point
                }
            }
        }
        return points
    }

    private fun boardPointsWithColor(
        board: Match3Board,
        color: TileColor,
    ): Set<GridPoint> {
        val points = linkedSetOf<GridPoint>()
        for (row in 0 until board.height) {
            for (column in 0 until board.width) {
                val point = GridPoint(row, column)
                if (board.tileAt(point).color == color) {
                    points += point
                }
            }
        }
        return points
    }

    private fun allPoints(board: Match3Board): Set<GridPoint> {
        val points = linkedSetOf<GridPoint>()
        for (row in 0 until board.height) {
            for (column in 0 until board.width) {
                points += GridPoint(row, column)
            }
        }
        return points
    }

    private fun newTile(random: Random): Match3Tile =
        Match3Tile(
            id = nextTileId++,
            color = config.availableColors[random.nextInt(config.availableColors.size)],
        )

    private fun Set<GridPoint>.associateByColor(board: Match3Board): Map<TileColor, Int> {
        val result = mutableMapOf<TileColor, Int>()
        forEach { point ->
            result.merge(board.tileAt(point).color, 1, Int::plus)
        }
        return result
    }

    private fun isLine(kind: SpecialTileKind): Boolean =
        kind == SpecialTileKind.HorizontalLine || kind == SpecialTileKind.VerticalLine

    private data class SpecialComboResolution(
        val board: Match3Board,
        val removed: Set<GridPoint>,
        val activatedSpecials: List<ActivatedSpecial>,
    )

    private data class CollapseResult(
        val board: Match3Board,
        val fallDistances: Map<GridPoint, Int>,
    )
}
