package com.example.picomatch3vr.p3a7c9f1.domain.campaign

import kotlin.random.Random

class CampaignGameEngine(
    private val config: Match3GameConfig = Match3GameConfig(),
) {
    private var nextTileId: Long = 1L
    private var sessionSeed: Int = config.startingSeed

    fun createInitialSession(levelIndex: Int = 1): Match3GameSession {
        nextTileId = 1L
        var level = config.levels.firstOrNull { it.index == levelIndex } ?: config.levels.first()
        val variation = sessionSeed++
        val seed = if (config.useValidatedOpenings && level.boardWidth == 8 && level.boardHeight == 8 &&
            config.availableColors == TileColor.entries) {
            val pool = validatedSeeds(level.index)
            pool[Math.floorMod(variation, pool.size)]
        } else variation
        val random = Random(seed)
        val board = addCampaignTargets(createStableBoard(level.boardWidth, level.boardHeight, random), level, random)
        if (level.mode == CampaignMode.Ice) {
            level = level.copy(goal = level.goal.copy(objectives = listOf(
                GoalObjective(GoalType.ClearIce, board.ice.values.sum(), label = "冰层"),
            )))
        }
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
        if (session.movesRemaining <= 0 || isLevelCompleted(session)) {
            return invalidTurn(session, "步数已经用完，请重新开始。")
        }

        val firstTile = session.board.tileAt(first)
        val secondTile = session.board.tileAt(second)
        if (!canSwap(session.board, first, second)) return invalidTurn(session, "宝石不能交换，请消除周围动物。")
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
        if (groups.isEmpty() && firstTile.cargo == CargoKind.None && secondTile.cargo == CargoKind.None) {
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
        if (isLevelCompleted(session) || session.board.tileAt(target).cargo != CargoKind.None) {
            return invalidPropUse(session, "请选择未结束关卡中的动物或宝石。")
        }
        val result = settleTurn(session, session.board, target, target, setOf(target), emptyList(), emptyMap())
        return PropUseResult(
            result.session.copy(inventory = session.inventory.copy(hammerCount = session.inventory.hammerCount - 1)),
            true, result.scoreDelta, result.clearedTiles, "锤子已使用，并消耗一步。",
        )
    }

    private fun matchesAsAnimal(board: Match3Board, point: GridPoint): Boolean =
        point !in board.gems && board.tileAt(point).cargo == CargoKind.None &&
            board.tileAt(point).special != SpecialTileKind.ColorBomb

    // Each retained default opening has a verified legal winning trajectory.
    // Changing rules, target counts or move budgets requires revalidating this pool.
    private fun validatedSeeds(level: Int): List<Int> {
        val excluded = when (level) {
            9 -> setOf(1776)
            10 -> setOf(1000, 1097)
            18 -> setOf(1582)
            19 -> setOf(1873)
            20 -> setOf(1000, 1679)
            23 -> setOf(1000, 1582)
            24 -> setOf(1097, 1291, 1776)
            25 -> setOf(1000, 1097, 1291, 1582, 1776)
            else -> emptySet()
        }
        return (0 until 12).map { 1000 + it * 97 }.filter { it !in excluded }
    }

    private fun canSwap(board: Match3Board, a: GridPoint, b: GridPoint): Boolean =
        a !in board.gems && b !in board.gems &&
            !(board.tileAt(a).cargo != CargoKind.None && board.tileAt(b).cargo != CargoKind.None)

    private fun addCampaignTargets(board: Match3Board, level: Match3Level, random: Random): Match3Board {
        val tier = level.difficulty
        return when (level.mode) {
            CampaignMode.Score -> board
            CampaignMode.Ice -> {
                val points = allPoints(board).sortedBy {
                    kotlin.math.abs(it.row - 3.5f) + kotlin.math.abs(it.column - 3.5f)
                }.take(10 + tier * 4)
                board.copy(ice = points.associateWith { point ->
                    if (tier >= 4 && (point.row + point.column) % 3 == 0) 3
                    else if (tier >= 2 && (point.row + point.column) % 2 == 0) 2 else 1
                })
            }
            CampaignMode.Pods -> {
                val columns = (0 until board.width).shuffled(random).take(listOf(2, 3, 3, 4, 4)[tier - 1])
                board.replaceAll(columns.mapIndexed { index, column ->
                    GridPoint(if (tier == 1) 2 + index % 2 else if (tier <= 3) 1 + index % 2 else index % 2, column) to
                        newTile(random).copy(cargo = CargoKind.GoldenPod)
                }.toMap())
            }
            CampaignMode.Gems -> {
                val positions = listOf(GridPoint(2, 2), GridPoint(5, 5), GridPoint(2, 5), GridPoint(5, 2),
                    GridPoint(0, 3), GridPoint(7, 4), GridPoint(3, 0), GridPoint(4, 7))
                board.copy(gems = positions.take(tier + 3).associateWith { if (tier == 1) 2 else 3 })
            }
            CampaignMode.Snails -> {
                val route = buildList {
                    val length = 6 + tier * 2
                    for (row in 0 until board.height) {
                        val columns = if (row % 2 == 0) 0 until board.width else board.width - 1 downTo 0
                        columns.forEach { column -> if (size < length) add(GridPoint(row, column)) }
                    }
                }
                val journeys = mutableListOf(SnailJourney(route))
                if (tier >= 3) journeys += SnailJourney(route.map { GridPoint(board.height - 1 - it.row, board.width - 1 - it.column) })
                board.copy(snails = journeys, clearedRoute = journeys.mapTo(mutableSetOf()) { it.route.first() })
            }
        }.let { ensureAvailableMove(it, random) }
    }

    private data class TargetDamage(
        val board: Match3Board,
        val iceCleared: Int,
        val gemsCollected: Int,
        val destroyedGems: Set<GridPoint>,
    )

    private fun damageTargets(board: Match3Board, attacked: Set<GridPoint>): TargetDamage {
        val ice = board.ice.toMutableMap()
        var iceCleared = 0
        attacked.forEach { point ->
            val layers = ice[point] ?: return@forEach
            iceCleared++
            if (layers <= 1) ice.remove(point) else ice[point] = layers - 1
        }
        val gems = board.gems.toMutableMap()
        val destroyed = mutableSetOf<GridPoint>()
        board.gems.forEach { (point, layers) ->
            if (point in attacked || attacked.any { it.isAdjacentTo(point) }) {
                if (layers <= 1) {
                    gems.remove(point)
                    destroyed += point
                } else gems[point] = layers - 1
            }
        }
        val routeCells = board.snails.flatMapTo(mutableSetOf()) { it.route }
        val cleared = board.clearedRoute + attacked.intersect(routeCells)
        val snails = board.snails.map { snail ->
            var position = snail.position
            while (position < snail.route.lastIndex && snail.route[position + 1] in cleared) position++
            snail.copy(position = position)
        }
        return TargetDamage(board.copy(ice = ice, gems = gems, snails = snails, clearedRoute = cleared),
            iceCleared, destroyed.size, destroyed)
    }

    private fun deliverPods(board: Match3Board, random: Random): Pair<Match3Board, Int> {
        var current = board
        var count = 0
        while (true) {
            val bottom = (0 until current.width).map { GridPoint(current.height - 1, it) }
                .filterTo(mutableSetOf()) { current.tileAt(it).cargo == CargoKind.GoldenPod }
            if (bottom.isEmpty()) return current to count
            count += bottom.size
            current = collapseBoard(current, bottom, emptyMap(), random).board
        }
    }

    private fun ensureAvailableMove(board: Match3Board, random: Random): Match3Board {
        if (findMatchGroups(board).isEmpty() && hasAvailableMove(board)) return board
        val points = allPoints(board).filter { it !in board.gems && board.tileAt(it).cargo == CargoKind.None }
        val tiles = points.map(board::tileAt)
        repeat(2000) {
            val candidate = board.replaceAll(points.zip(tiles.shuffled(random)).toMap())
            if (findMatchGroups(candidate).isEmpty() && hasAvailableMove(candidate)) return candidate
        }
        error("Could not reshuffle a playable campaign board.")
    }

    private fun collapseAroundGems(
        board: Match3Board, removed: Set<GridPoint>, random: Random,
    ): CollapseResult {
        val cells = board.cells.toMutableList()
        val distances = mutableMapOf<GridPoint, Int>()
        for (column in 0 until board.width) {
            var bottom = board.height - 1
            while (bottom >= 0) {
                if (GridPoint(bottom, column) in board.gems) { bottom--; continue }
                var top = bottom
                while (top > 0 && GridPoint(top - 1, column) !in board.gems) top--
                val survivors = (bottom downTo top).filter { GridPoint(it, column) !in removed }
                    .map { it to board.tileAt(GridPoint(it, column)) }.toMutableList()
                while (survivors.size < bottom - top + 1) survivors += (top - 1) to newTile(random)
                for ((index, pair) in survivors.withIndex()) {
                    val row = bottom - index
                    cells[row * board.width + column] = pair.second
                    if (row > pair.first) distances[GridPoint(row, column)] = row - pair.first
                }
                bottom = top - 1
            }
        }
        return CollapseResult(board.copy(cells = cells), distances)
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

    fun availableMoves(board: Match3Board): List<Pair<GridPoint, GridPoint>> = buildList {
        allPoints(board).forEach { point ->
            listOf(GridPoint(point.row + 1, point.column), GridPoint(point.row, point.column + 1)).forEach { other ->
                if (board.contains(other) && isPotentialMove(board, point, other)) add(point to other)
            }
        }
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
        var iceCleared = 0
        var gemsCollected = 0
        var podsDelivered = 0
        var firstPass = true

        while (firstPass || removed.isNotEmpty() || createdSpecials.isNotEmpty()) {
            firstPass = false
            check(cascadeIndex < 100) { "Cascade did not settle." }
            cascadeIndex += 1
            val expansion = expandSpecialChain(board, removed, activatedSpecials, createdSpecials.keys)
            val attacked = expansion.first - createdSpecials.keys
            val expandedRemoved = attacked.filterTo(linkedSetOf()) {
                board.tileAt(it).cargo == CargoKind.None && it !in board.gems
            }
            val expandedActivated = expansion.second
            val survivingCreated = createdSpecials.filterKeys { it !in expandedRemoved }

            expandedRemoved.forEach { point ->
                val tile = board.tileAt(point)
                collected.merge(tile.color, 1, Int::plus)
            }

            val scoreDelta = calculateCascadeScore(expandedRemoved.size, survivingCreated.values, cascadeIndex, expandedActivated)
            totalCleared += expandedRemoved.size
            totalScore += scoreDelta
            val beforeClear = board
            val damage = damageTargets(board, attacked + createdSpecials.keys)
            iceCleared += damage.iceCleared
            gemsCollected += damage.gemsCollected
            board = damage.board
            val collapse = collapseBoard(board, expandedRemoved + damage.destroyedGems, survivingCreated, random)
            val delivery = deliverPods(collapse.board, random)
            podsDelivered += delivery.second
            val settledBoard = delivery.first

            cascadeSteps +=
                Match3CascadeStep(
                    stepIndex = cascadeIndex,
                    removedPoints = expandedRemoved + damage.destroyedGems,
                    createdSpecials = survivingCreated,
                    activatedSpecials = expandedActivated,
                    scoreDelta = scoreDelta,
                    boardAfterCollapse = settledBoard,
                    fallDistances = collapse.fallDistances,
                    boardBeforeClear = beforeClear,
                )

            board = settledBoard

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
                board = ensureAvailableMove(board, random),
                score = updatedScore,
                movesRemaining = session.movesRemaining - 1,
                collectedTiles = updatedCollected,
                totalClearedTiles = session.totalClearedTiles + totalCleared,
                starsEarned = calculateStars(session.level.starThresholds, updatedScore),
                iceCleared = session.iceCleared + iceCleared,
                gemsCollected = session.gemsCollected + gemsCollected,
                podsDelivered = session.podsDelivered + podsDelivered,
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
        if (board.gems.isNotEmpty()) return collapseAroundGems(boardWithSpecials, removedPoints, random)
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
                while (end < board.width && matchesAsAnimal(board, GridPoint(row, start)) &&
                    matchesAsAnimal(board, GridPoint(row, end)) && board.tileAt(GridPoint(row, end)).color == color) {
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
                while (end < board.height && matchesAsAnimal(board, GridPoint(start, column)) &&
                    matchesAsAnimal(board, GridPoint(end, column)) && board.tileAt(GridPoint(end, column)).color == color) {
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
                listOfNotNull(second, first)
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
        return (removed - newlyCreated) to activated
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
        if (!canSwap(board, first, second)) return false
        val firstTile = board.tileAt(first)
        val secondTile = board.tileAt(second)
        if (firstTile.cargo != CargoKind.None || secondTile.cargo != CargoKind.None) return true
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

    private fun buildObjectiveText(objectives: List<GoalObjective>): String =
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
