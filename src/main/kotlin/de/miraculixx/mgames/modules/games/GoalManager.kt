package de.miraculixx.mgames.modules.games

import de.miraculixx.mgames.modules.games.utils.enums.Game
import de.miraculixx.mgames.modules.games.utils.enums.GameMode
import de.miraculixx.mgames.utils.cachedDailyDate
import de.miraculixx.mgames.utils.cachedDailySeed
import de.miraculixx.mgames.utils.api.SQL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object GoalManager {
    fun registerGameResult(
        game: Game,
        mode: GameMode,
        winnerSnowflake: Long?,
        loserSnowflake: Long?,
        guildSnowflake: Long,
        difficultyMultiplier: Int = 1
    ): Int {
        val coins = game.coinValue * difficultyMultiplier.coerceIn(1, 3)
        CoroutineScope(Dispatchers.Default).launch {
            registerGameHistory(game, listOfNotNull(winnerSnowflake, loserSnowflake))
            val difficulty = if (mode == GameMode.BOT) difficultyMultiplier.coerceIn(1, 3) else 0
            winnerSnowflake?.let {
                SQL.addGameStats(it, game, mode, difficulty, won = true)
                SQL.addCoins(it, guildSnowflake, coins)
            }
            loserSnowflake?.let {
                SQL.addGameStats(it, game, mode, difficulty, won = false)
            }
        }
        return coins
    }

    suspend fun registerGameHistory(game: Game, users: Collection<Long>) {
        SQL.addGameHistory(game, users)
    }

    suspend fun registerDailyCompletion(
        game: Game,
        userSnowflake: Long,
        guildSnowflake: Long,
        difficultyMultiplier: Int = 1
    ): SQL.DailyPlayResult {
        val date = currentDailyDate()
        val previousDate = date.minus(1, DateTimeUnit.DAY)
        val reward = game.coinValue * difficultyMultiplier.coerceIn(1, 3) * 10
        return SQL.completeDailyPlay(userSnowflake, guildSnowflake, game.name, date.toString(), previousDate.toString(), reward)
    }

    suspend fun hasCompletedDaily(game: Game, userSnowflake: Long): Boolean {
        return SQL.hasCompletedDailyPlay(userSnowflake, game.name, currentDailyDate().toString())
    }

    suspend fun getDailySeed(): Long {
        val date = currentDailyDate().toString()
        if (cachedDailyDate == date && cachedDailySeed != null) return cachedDailySeed!!

        val seed = SQL.getDailySeed(date)
        cachedDailyDate = date
        cachedDailySeed = seed
        return seed
    }

    fun currentDailyDate(): LocalDate {
        val current = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val date = current.date
        return if (current.hour < 1) date.minus(1, DateTimeUnit.DAY) else date
    }
}
