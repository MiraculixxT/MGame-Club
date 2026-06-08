package de.miraculixx.mgames.modules.games.connectFour

import de.miraculixx.mgames.modules.games.utils.FieldsTwoPlayer
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class C4Bot(val level: Int, private val random: Random = Random.Default) {
    private val botPlayer = FieldsTwoPlayer.PLAYER_2
    private val humanPlayer = FieldsTwoPlayer.PLAYER_1

    // Returns Column
    fun getNextMove(array: Array<Array<FieldsTwoPlayer>>): Int {
        val validColumns = orderedColumns(array)
        if (validColumns.isEmpty()) return 0

        return when (level.coerceIn(1, 3)) {
            1 -> getEasyMove(array, validColumns)
            2 -> getMediumMove(array, validColumns)
            else -> getHardMove(array, validColumns)
        }
    }

    private fun getEasyMove(board: Array<Array<FieldsTwoPlayer>>, validColumns: List<Int>): Int {
        if (random.nextDouble() < 0.45) return validColumns.random(random)

        findImmediateWin(board, botPlayer, validColumns)?.let { return it }
        if (random.nextDouble() < 0.55) {
            findImmediateWin(board, humanPlayer, validColumns)?.let { return it }
        }

        return getBestMove(board, depth = 2, randomizeNearBest = true)
    }

    private fun getMediumMove(board: Array<Array<FieldsTwoPlayer>>, validColumns: List<Int>): Int {
        findImmediateWin(board, botPlayer, validColumns)?.let { return it }
        findImmediateWin(board, humanPlayer, validColumns)?.let { return it }
        return getBestMove(board, depth = 5, randomizeNearBest = true)
    }

    private fun getHardMove(board: Array<Array<FieldsTwoPlayer>>, validColumns: List<Int>): Int {
        findImmediateWin(board, botPlayer, validColumns)?.let { return it }
        findImmediateWin(board, humanPlayer, validColumns)?.let { return it }
        return getBestMove(board, depth = 8, randomizeNearBest = false)
    }

    private fun getBestMove(
        board: Array<Array<FieldsTwoPlayer>>,
        depth: Int,
        randomizeNearBest: Boolean
    ): Int {
        val cache = HashMap<String, CacheEntry>()
        var bestScore = -INFINITY
        val scoredColumns = mutableListOf<Pair<Int, Int>>()
        val playable = orderedColumns(board)

        playable.forEach { column ->
            val row = place(board, column, botPlayer) ?: return@forEach
            val score = minimax(board, depth - 1, maximizing = false, alpha = -INFINITY, beta = INFINITY, cache = cache)
            board[row][column] = FieldsTwoPlayer.EMPTY
            scoredColumns.add(column to score)
            if (score > bestScore) bestScore = score
        }

        val bestColumns = scoredColumns
            .filter { (_, score) -> score == bestScore }
            .map { (column, _) -> column }
        if (bestColumns.isEmpty()) return playable.firstOrNull() ?: 0
        if (!randomizeNearBest) return bestColumns.random(random)

        val nearBest = scoredColumns
            .filter { (_, score) -> bestScore - score <= MEDIUM_MISTAKE_RANGE }
            .map { (column, _) -> column }

        return (nearBest.ifEmpty { bestColumns }).random(random)
    }

    private fun minimax(
        board: Array<Array<FieldsTwoPlayer>>,
        depth: Int,
        maximizing: Boolean,
        alpha: Int,
        beta: Int,
        cache: MutableMap<String, CacheEntry>
    ): Int {
        if (hasWon(board, botPlayer)) return WIN_SCORE + depth
        if (hasWon(board, humanPlayer)) return -WIN_SCORE - depth
        if (depth == 0 || orderedColumns(board).isEmpty()) return scoreBoard(board)

        var currentAlpha = alpha
        var currentBeta = beta
        val alphaStart = currentAlpha
        val betaStart = currentBeta
        val key = boardKey(board, maximizing)

        cache[key]?.takeIf { it.depth >= depth }?.let { entry ->
            when (entry.flag) {
                CacheFlag.EXACT -> return entry.score
                CacheFlag.LOWER -> currentAlpha = max(currentAlpha, entry.score)
                CacheFlag.UPPER -> currentBeta = min(currentBeta, entry.score)
            }
            if (currentAlpha >= currentBeta) return entry.score
        }

        val score = if (maximizing) {
            var value = -INFINITY
            for (column in orderedColumns(board)) {
                val row = place(board, column, botPlayer) ?: continue
                value = max(value, minimax(board, depth - 1, maximizing = false, alpha = currentAlpha, beta = currentBeta, cache = cache))
                board[row][column] = FieldsTwoPlayer.EMPTY
                currentAlpha = max(currentAlpha, value)
                if (currentAlpha >= currentBeta) break
            }
            value
        } else {
            var value = INFINITY
            for (column in orderedColumns(board)) {
                val row = place(board, column, humanPlayer) ?: continue
                value = min(value, minimax(board, depth - 1, maximizing = true, alpha = currentAlpha, beta = currentBeta, cache = cache))
                board[row][column] = FieldsTwoPlayer.EMPTY
                currentBeta = min(currentBeta, value)
                if (currentAlpha >= currentBeta) break
            }
            value
        }

        val flag = when {
            score <= alphaStart -> CacheFlag.UPPER
            score >= betaStart -> CacheFlag.LOWER
            else -> CacheFlag.EXACT
        }
        cache[key] = CacheEntry(depth, score, flag)
        return score
    }

    private fun scoreBoard(board: Array<Array<FieldsTwoPlayer>>): Int {
        var score = 0

        repeat(ROWS) { row ->
            if (board[row][CENTER_COLUMN] == botPlayer) score += 6
            if (board[row][CENTER_COLUMN] == humanPlayer) score -= 6
        }

        repeat(ROWS) { row ->
            repeat(COLUMNS - 3) { column ->
                score += scoreWindow(
                    board[row][column],
                    board[row][column + 1],
                    board[row][column + 2],
                    board[row][column + 3]
                )
            }
        }

        repeat(COLUMNS) { column ->
            repeat(ROWS - 3) { row ->
                score += scoreWindow(
                    board[row][column],
                    board[row + 1][column],
                    board[row + 2][column],
                    board[row + 3][column]
                )
            }
        }

        repeat(ROWS - 3) { row ->
            repeat(COLUMNS - 3) { column ->
                score += scoreWindow(
                    board[row][column],
                    board[row + 1][column + 1],
                    board[row + 2][column + 2],
                    board[row + 3][column + 3]
                )
                score += scoreWindow(
                    board[row + 3][column],
                    board[row + 2][column + 1],
                    board[row + 1][column + 2],
                    board[row][column + 3]
                )
            }
        }

        return score
    }

    private fun scoreWindow(
        first: FieldsTwoPlayer,
        second: FieldsTwoPlayer,
        third: FieldsTwoPlayer,
        fourth: FieldsTwoPlayer
    ): Int {
        val window = arrayOf(first, second, third, fourth)
        val botCount = window.count { it == botPlayer }
        val humanCount = window.count { it == humanPlayer }
        val emptyCount = window.count { it == FieldsTwoPlayer.EMPTY }

        return when {
            botCount == 4 -> WIN_SCORE
            humanCount == 4 -> -WIN_SCORE
            botCount == 3 && emptyCount == 1 -> 90
            humanCount == 3 && emptyCount == 1 -> -120
            botCount == 2 && emptyCount == 2 -> 12
            humanCount == 2 && emptyCount == 2 -> -16
            botCount == 1 && emptyCount == 3 -> 1
            humanCount == 1 && emptyCount == 3 -> -1
            else -> 0
        }
    }

    private fun findImmediateWin(
        board: Array<Array<FieldsTwoPlayer>>,
        player: FieldsTwoPlayer,
        validColumns: List<Int>
    ): Int? {
        validColumns.forEach { column ->
            val row = place(board, column, player) ?: return@forEach
            val wins = hasWon(board, player)
            board[row][column] = FieldsTwoPlayer.EMPTY
            if (wins) return column
        }
        return null
    }

    private fun hasWon(board: Array<Array<FieldsTwoPlayer>>, player: FieldsTwoPlayer): Boolean {
        repeat(ROWS) { row ->
            repeat(COLUMNS - 3) { column ->
                if (
                    board[row][column] == player &&
                    board[row][column + 1] == player &&
                    board[row][column + 2] == player &&
                    board[row][column + 3] == player
                ) return true
            }
        }

        repeat(COLUMNS) { column ->
            repeat(ROWS - 3) { row ->
                if (
                    board[row][column] == player &&
                    board[row + 1][column] == player &&
                    board[row + 2][column] == player &&
                    board[row + 3][column] == player
                ) return true
            }
        }

        repeat(ROWS - 3) { row ->
            repeat(COLUMNS - 3) { column ->
                if (
                    board[row][column] == player &&
                    board[row + 1][column + 1] == player &&
                    board[row + 2][column + 2] == player &&
                    board[row + 3][column + 3] == player
                ) return true
                if (
                    board[row + 3][column] == player &&
                    board[row + 2][column + 1] == player &&
                    board[row + 1][column + 2] == player &&
                    board[row][column + 3] == player
                ) return true
            }
        }

        return false
    }

    private fun orderedColumns(board: Array<Array<FieldsTwoPlayer>>): List<Int> {
        return COLUMN_ORDER.filter { column -> board[0][column] == FieldsTwoPlayer.EMPTY }
    }

    private fun place(board: Array<Array<FieldsTwoPlayer>>, column: Int, player: FieldsTwoPlayer): Int? {
        for (row in ROWS - 1 downTo 0) {
            if (board[row][column] == FieldsTwoPlayer.EMPTY) {
                board[row][column] = player
                return row
            }
        }
        return null
    }

    private fun boardKey(board: Array<Array<FieldsTwoPlayer>>, maximizing: Boolean): String {
        val builder = StringBuilder(ROWS * COLUMNS + 1)
        builder.append(if (maximizing) '2' else '1')
        board.forEach { row ->
            row.forEach { field ->
                builder.append(
                    when (field) {
                        FieldsTwoPlayer.EMPTY -> '0'
                        FieldsTwoPlayer.PLAYER_1 -> '1'
                        FieldsTwoPlayer.PLAYER_2 -> '2'
                    }
                )
            }
        }
        return builder.toString()
    }

    private data class CacheEntry(val depth: Int, val score: Int, val flag: CacheFlag)

    private enum class CacheFlag {
        EXACT,
        LOWER,
        UPPER
    }

    private companion object {
        private const val ROWS = 6
        private const val COLUMNS = 7
        private const val CENTER_COLUMN = 3
        private const val WIN_SCORE = 1_000_000
        private const val INFINITY = 1_100_000
        private const val MEDIUM_MISTAKE_RANGE = 80
        private val COLUMN_ORDER = listOf(3, 2, 4, 1, 5, 0, 6)
    }
}
