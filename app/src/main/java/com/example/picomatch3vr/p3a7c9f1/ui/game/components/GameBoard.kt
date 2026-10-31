package com.example.picomatch3vr.p3a7c9f1.ui.game.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import com.example.picomatch3vr.p3a7c9f1.domain.model.GridPoint
import com.example.picomatch3vr.p3a7c9f1.domain.model.Match3Board
import com.example.picomatch3vr.p3a7c9f1.domain.model.Match3Tile
import com.example.picomatch3vr.p3a7c9f1.domain.model.SpecialTileKind
import com.example.picomatch3vr.p3a7c9f1.domain.model.TileColor
import com.example.picomatch3vr.p3a7c9f1.ui.theme.Match3DesignTokens
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import kotlin.math.abs

@Composable
fun GameBoard(
    board: Match3Board,
    selectedCell: GridPoint?,
    onCellClick: (GridPoint) -> Unit,
    onCellDragSwap: (GridPoint, GridPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val neighborResolver = remember(board.width, board.height) {
        DragNeighborResolver(board.width, board.height)
    }
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(28.dp))
                .background(Match3DesignTokens.BoardShell)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (row in 0 until board.height) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (column in 0 until board.width) {
                    val point = GridPoint(row, column)
                    TileCell(
                        origin = point,
                        tile = board.tileAt(point),
                        isSelected = selectedCell == point,
                        canUseDrag = true,
                        neighborResolver = neighborResolver,
                        onClick = { onCellClick(point) },
                        onDragSwap = { from, to ->
                            if (board.contains(to)) {
                                onCellDragSwap(from, to)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TileCell(
    origin: GridPoint,
    tile: Match3Tile,
    isSelected: Boolean,
    canUseDrag: Boolean,
    neighborResolver: DragNeighborResolver,
    onClick: () -> Unit,
    onDragSwap: (GridPoint, GridPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (isSelected) Match3DesignTokens.BoardCellSelected else Match3DesignTokens.BoardCell
    val accent = colorFor(tile.color)
    val shape = RoundedCornerShape(18.dp)
    val symbol = specialSymbol(tile)

    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .clip(shape)
                .background(background)
                .spatialHoverEffect()
                .then(
                    if (canUseDrag) {
                        Modifier.pointerInput(origin) {
                            detectDragGestures(
                                onDragStart = { neighborResolver.start(origin) },
                                onDragEnd = { neighborResolver.reset() },
                                onDragCancel = { neighborResolver.reset() },
                            ) { change, dragAmount ->
                                val delta = change.positionChange()
                                change.consume()
                                neighborResolver
                                    .accumulate(if (delta == Offset.Zero) dragAmount else delta)
                                    ?.let { neighbor -> onDragSwap(origin, neighbor) }
                            }
                        }
                    } else {
                        Modifier
                    },
                )
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = symbol,
                color = PicoTheme.colorScheme.labelPrimaryLight,
                style = PicoTheme.typography.titleLarge,
            )
        }
    }
}

private class DragNeighborResolver(
    private val boardWidth: Int,
    private val boardHeight: Int,
) {
    private var origin: GridPoint? = null
    private var cumulativeOffset: Offset = Offset.Zero
    private var consumed = false

    fun start(origin: GridPoint) {
        this.origin = origin
        cumulativeOffset = Offset.Zero
        consumed = false
    }

    fun accumulate(dragAmount: Offset): GridPoint? {
        val currentOrigin = origin ?: return null
        if (consumed) {
            return null
        }

        cumulativeOffset += dragAmount
        val threshold = 18f
        val dx = cumulativeOffset.x
        val dy = cumulativeOffset.y
        if (abs(dx) < threshold && abs(dy) < threshold) {
            return null
        }

        val target =
            if (abs(dx) >= abs(dy)) {
                if (dx > 0) {
                    currentOrigin.copy(column = currentOrigin.column + 1)
                } else {
                    currentOrigin.copy(column = currentOrigin.column - 1)
                }
            } else {
                if (dy > 0) {
                    currentOrigin.copy(row = currentOrigin.row + 1)
                } else {
                    currentOrigin.copy(row = currentOrigin.row - 1)
                }
            }

        if (target.row !in 0 until boardHeight || target.column !in 0 until boardWidth) {
            consumed = true
            return null
        }

        consumed = true
        return target
    }

    fun reset() {
        origin = null
        cumulativeOffset = Offset.Zero
        consumed = false
    }
}

private fun colorFor(tileColor: TileColor) =
    when (tileColor) {
        TileColor.Strawberry -> Match3DesignTokens.AccentCoral
        TileColor.Lemon -> Match3DesignTokens.AccentGold
        TileColor.Blueberry -> Match3DesignTokens.AccentBerry
        TileColor.Mint -> Match3DesignTokens.AccentMint
        TileColor.Peach -> androidx.compose.ui.graphics.Color(0xFFFFB27D) // design-style: fixed-figma-color accentPeach
        TileColor.Grape -> androidx.compose.ui.graphics.Color(0xFF9E6EFF) // design-style: fixed-figma-color accentGrape
    }

private fun specialSymbol(tile: Match3Tile): String =
    when (tile.special) {
        SpecialTileKind.None -> tile.color.symbol
        SpecialTileKind.HorizontalLine -> "═"
        SpecialTileKind.VerticalLine -> "║"
        SpecialTileKind.Bomb -> "✹"
        SpecialTileKind.ColorBomb -> "★"
    }
