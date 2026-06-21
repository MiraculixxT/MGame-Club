package de.miraculixx.mgames.utils.api

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import de.miraculixx.mgames.config.ConfigManager
import de.miraculixx.mgames.config.LanguageManager
import de.miraculixx.mgames.modules.games.utils.enums.Game
import de.miraculixx.mgames.modules.games.utils.enums.GameMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.ResultSet
import java.util.*
import kotlin.math.abs

object SQL {
    /**
     * HikariCP pool. Each DB call borrows its own [Connection] (`use { }` returns it to the pool).
     * The driver/pool handle validation, reconnects and leak detection.
     */
    private val dataSource: HikariDataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = "jdbc:mariadb://miraculixx.de:3306/MGames"
            username = "MGamesBot"
            password = ConfigManager.coreConfig.SQL_TOKEN
            maximumPoolSize = 10
            poolName = "MGames-Pool"
        }
    )

    private fun ensureSchema() {
        dataSource.connection.use { connection ->
            val statements = listOf(
                "ALTER TABLE gameHistory ADD COLUMN IF NOT EXISTS Guild_ID BIGINT NOT NULL DEFAULT 0 AFTER Played_At",
                "ALTER TABLE gameHistory ADD COLUMN IF NOT EXISTS Result TINYINT UNSIGNED NOT NULL DEFAULT 4 AFTER Discord_ID",
                "ALTER TABLE gameHistory ADD COLUMN IF NOT EXISTS Match_ID VARCHAR(36) NOT NULL DEFAULT '' AFTER Result",
                "CREATE INDEX IF NOT EXISTS idx_history_guild_time ON gameHistory(Guild_ID, Played_At)",
                "CREATE INDEX IF NOT EXISTS idx_history_match ON gameHistory(Guild_ID, Match_ID)"
            )
            statements.forEach { statement ->
                connection.prepareStatement(statement).use { it.executeUpdate() }
            }

            if (columnExists("userDailyPlay", "Guild_ID")) {
                connection.prepareStatement("DROP TEMPORARY TABLE IF EXISTS userDailyPlay_global")
                    .use { it.executeUpdate() }
                connection.prepareStatement(
                    "CREATE TEMPORARY TABLE userDailyPlay_global AS " +
                            "SELECT daily.Discord_ID, daily.Game, daily.Last_Play_Date, MAX(daily.Streak) AS Streak " +
                            "FROM userDailyPlay daily " +
                            "INNER JOIN (" +
                            "SELECT Discord_ID, Game, MAX(Last_Play_Date) AS Last_Play_Date FROM userDailyPlay GROUP BY Discord_ID, Game" +
                            ") latest ON latest.Discord_ID=daily.Discord_ID && latest.Game=daily.Game && latest.Last_Play_Date=daily.Last_Play_Date " +
                            "GROUP BY daily.Discord_ID, daily.Game, daily.Last_Play_Date"
                ).use { it.executeUpdate() }
                connection.prepareStatement("DELETE FROM userDailyPlay").use { it.executeUpdate() }
                connection.prepareStatement("ALTER TABLE userDailyPlay DROP PRIMARY KEY").use { it.executeUpdate() }
                connection.prepareStatement("ALTER TABLE userDailyPlay DROP COLUMN Guild_ID").use { it.executeUpdate() }
                connection.prepareStatement("ALTER TABLE userDailyPlay ADD PRIMARY KEY (Discord_ID, Game)")
                    .use { it.executeUpdate() }
                connection.prepareStatement(
                    "INSERT INTO userDailyPlay (Discord_ID, Game, Last_Play_Date, Streak) " +
                            "SELECT Discord_ID, Game, Last_Play_Date, Streak FROM userDailyPlay_global"
                ).use { it.executeUpdate() }
                connection.prepareStatement("DROP TEMPORARY TABLE IF EXISTS userDailyPlay_global")
                    .use { it.executeUpdate() }
            }
        }
    }

    private fun columnExists(table: String, column: String): Boolean {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT COUNT(*) AS Columns FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA=DATABASE() && TABLE_NAME=? && COLUMN_NAME=?"
            ).use { statement ->
                statement.setString(1, table)
                statement.setString(2, column)
                val result = statement.executeQuery()
                return result.next() && result.getInt("Columns") > 0
            }
        }
    }


    //
    // Main DB interactions
    //

    /** Runs [sql] as a query, binds [params] positionally, and maps the ResultSet on an IO thread. */
    private suspend fun <T> query(sql: String, vararg params: Any?, mapper: (ResultSet) -> T): T =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    params.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                    statement.executeQuery().use(mapper)
                }
            }
        }

    /** Runs [sql] as an update, binds [params] positionally. Returns affected rows. */
    private suspend fun update(sql: String, vararg params: Any?): Int =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    params.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                    statement.executeUpdate()
                }
            }
        }


    //
    // API exposure, sandboxed from DB details and schema management
    //

    private suspend fun createUser(userSnowflake: Long, guildSnowflake: Long): UserData {
        // Generell User Account
        getGuild(guildSnowflake)
        update(
            "INSERT INTO userData (Guild_ID, Discord_ID, Coins, Total_Coins) VALUES (?, ?, 0, 0)",
            guildSnowflake,
            userSnowflake
        )
        val userID =
            query("SELECT ID FROM userData WHERE Guild_ID=? && Discord_ID=?", guildSnowflake, userSnowflake) { rs ->
                if (rs.next()) rs.getInt("ID") else 0
            }

        // Create Empty Data Rows to simplify future calls
        update("INSERT INTO userEmotesActive VALUES (?, '🔴', '🟡')", userID)
        return UserData(
            userSnowflake, 0, 0,
            UserEmote(emptyMap(), "🔴", "🟡"),
            emptyList()
        )
    }

    private suspend fun createGuild(guildSnowflake: Long): GuildData {
        val language = LanguageManager.Language.EN
        update(
            "INSERT INTO guildData (Discord_ID, Premium, Stats_Channel, Language) VALUES (?, false, 0, ?)",
            guildSnowflake,
            language.key
        )
        LanguageManager.cacheGuildLanguage(guildSnowflake, language.key)
        return GuildData(guildSnowflake, false, 0, language)
    }

    private suspend fun getUserID(userSnowflake: Long, guildSnowflake: Long): Int =
        query("SELECT ID FROM userData WHERE Discord_ID=? && Guild_ID=?", userSnowflake, guildSnowflake) { rs ->
            if (rs.next()) rs.getInt("ID") else 0
        }

    suspend fun getUser(
        userSnowflake: Long,
        guildSnowflake: Long,
        emotes: Boolean = false,
        daily: Boolean = false
    ): UserData {
        val base = query(
            "SELECT Coins, Total_Coins FROM userData WHERE Guild_ID=? && Discord_ID=?",
            guildSnowflake,
            userSnowflake
        ) { rs ->
            if (rs.next()) rs.getInt("Coins") to rs.getInt("Total_Coins") else null
        } ?: return createUser(userSnowflake, guildSnowflake)

        return UserData(
            userSnowflake,
            base.first,
            base.second,
            if (emotes) getUserEmotes(userSnowflake, guildSnowflake) else null,
            if (daily) getDailyPlays(userSnowflake) else null
        )
    }

    private suspend fun getUserEmotes(userSnowflake: Long, guildSnowflake: Long): UserEmote {
        val owned = query(
            "SELECT Emote_Type, Emote FROM userEmotes, userData WHERE Guild_ID=? && Discord_ID=? && userEmotes.ID=userData.ID",
            guildSnowflake, userSnowflake
        ) { rs ->
            buildMap {
                while (rs.next()) {
                    try {
                        put(rs.getString("Emote_Type"), rs.getString("Emote"))
                    } catch (e: Exception) {
                        put("1", "2")
                    }
                }
            }
        }
        return query(
            "SELECT C4_P, C4_S FROM userEmotesActive, userData WHERE Guild_ID=? && Discord_ID=? && userEmotesActive.ID=userData.ID",
            guildSnowflake, userSnowflake
        ) { rs ->
            if (rs.next()) UserEmote(owned, rs.getString("C4_P"), rs.getString("C4_S"))
            else UserEmote(owned, "🔴", "🟡")
        }
    }

    suspend fun getGuild(guildSnowflake: Long): GuildData {
        val data =
            query("SELECT Premium, Stats_Channel, Language FROM guildData WHERE Discord_ID=?", guildSnowflake) { rs ->
                if (!rs.next()) null
                else GuildData(
                    guildSnowflake,
                    rs.getBoolean("Premium"),
                    rs.getLong("Stats_Channel"),
                    LanguageManager.Language.from(rs.getString("Language"))
                )
            } ?: return createGuild(guildSnowflake)
        LanguageManager.cacheGuildLanguage(guildSnowflake, data.language.key)
        return data
    }

    suspend fun setGuildLanguage(guildSnowflake: Long, language: LanguageManager.Language) {
        getGuild(guildSnowflake)
        update("UPDATE guildData SET Language=? WHERE Discord_ID=?", language.key, guildSnowflake)
        LanguageManager.cacheGuildLanguage(guildSnowflake, language.key)
    }

    suspend fun setUserCoins(userSnowflake: Long, guildSnowflake: Long, amount: Int) {
        update("UPDATE userData SET Coins=? WHERE Discord_ID=? && Guild_ID=?", amount, userSnowflake, guildSnowflake)
    }

    suspend fun addEmote(userSnowflake: Long, guildSnowflake: Long, type: String, emote: String) {
        val id = getUserID(userSnowflake, guildSnowflake)
        update("INSERT INTO userEmotes VALUES (?, ?, ?)", id, type, emote)
    }

    suspend fun setActiveEmote(userSnowflake: Long, guildSnowflake: Long, type: String, newEmote: String) {
        // `type` is a column identifier, ensure its correct
        require(type == "C4_P" || type == "C4_S") { "Invalid active emote column: $type" }
        val id = getUserID(userSnowflake, guildSnowflake)
        update("UPDATE userEmotesActive SET $type=? WHERE ID=?", newEmote, id)
    }

    suspend fun addGameStats(userSnowflake: Long, game: Game, mode: GameMode, difficulty: Int, won: Boolean) {
        val safeDifficulty = difficulty.coerceIn(0, 3)
        val wins = if (won) 1 else 0
        val losses = if (won) 0 else 1
        update(
            "INSERT INTO userStats (Discord_ID, Game_ID, Mode_ID, Difficulty, Wins, Losses) " +
                    "VALUES (?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE Wins=Wins+?, Losses=Losses+?",
            userSnowflake, game.id, mode.id, safeDifficulty, wins, losses, wins, losses
        )
    }

    suspend fun addGameHistory(
        guildSnowflake: Long,
        game: Game,
        results: Map<Long, GameResult>,
        timestamp: Long = System.currentTimeMillis(),
        matchID: String = UUID.randomUUID().toString()
    ) {
        results.forEach { (userSnowflake, result) ->
            update(
                "INSERT INTO gameHistory (Played_At, Guild_ID, Game_ID, Discord_ID, Result, Match_ID) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                timestamp, guildSnowflake, game.id, userSnowflake, result.id, matchID
            )
        }
    }

    suspend fun getTopTotalCoins(guildSnowflake: Long, limit: Int = 3): List<LeaderboardEntry> {
        val safeLimit = limit.coerceIn(1, 10)
        return query(
            "SELECT Discord_ID, Total_Coins FROM userData WHERE Guild_ID=? ORDER BY Total_Coins DESC LIMIT ?",
            guildSnowflake, safeLimit
        ) { rs ->
            buildList {
                while (rs.next()) add(LeaderboardEntry(rs.getLong("Discord_ID"), rs.getInt("Total_Coins")))
            }
        }
    }

    suspend fun getTopDailyStreaks(
        guildSnowflake: Long,
        validDates: Collection<String>,
        limit: Int = 3
    ): List<LeaderboardEntry> {
        val safeLimit = limit.coerceIn(1, 10)
        if (validDates.isEmpty()) return emptyList()
        val placeholders = validDates.joinToString(",") { "?" }
        val params = buildList<Any?> {
            add(guildSnowflake)
            addAll(validDates)
            add(safeLimit)
        }
        return query(
            "SELECT userDailyPlay.Discord_ID, MAX(userDailyPlay.Streak) AS Streak " +
                    "FROM userDailyPlay INNER JOIN userData ON userData.Discord_ID=userDailyPlay.Discord_ID " +
                    "WHERE userData.Guild_ID=? && userDailyPlay.Last_Play_Date IN ($placeholders) " +
                    "GROUP BY userDailyPlay.Discord_ID ORDER BY Streak DESC LIMIT ?",
            *params.toTypedArray()
        ) { rs ->
            buildList {
                while (rs.next()) add(LeaderboardEntry(rs.getLong("Discord_ID"), rs.getInt("Streak")))
            }
        }
    }

    suspend fun getGuildHistory(guildSnowflake: Long, sinceMillis: Long): List<GameHistoryEntry> =
        query(
            "SELECT Played_At, Match_ID, Result FROM gameHistory " +
                    "WHERE Guild_ID=? && Played_At >= ? ORDER BY Played_At ASC",
            guildSnowflake, sinceMillis
        ) { rs ->
            buildList {
                while (rs.next()) {
                    add(
                        GameHistoryEntry(
                            rs.getLong("Played_At"),
                            rs.getString("Match_ID"),
                            GameResult.from(rs.getInt("Result"))
                        )
                    )
                }
            }
        }

    suspend fun addCoins(userSnowflake: Long, guildSnowflake: Long, amount: Int) {
        if (amount <= 0) return
        var id = getUserID(userSnowflake, guildSnowflake)
        if (id == 0) {
            createUser(userSnowflake, guildSnowflake)
            id = getUserID(userSnowflake, guildSnowflake)
        }
        update("UPDATE userData SET Coins=Coins+?, Total_Coins=Total_Coins+? WHERE ID=?", amount, amount, id)
    }

    suspend fun getDailySeed(date: String): Long {
        val existing = query("SELECT Seed FROM globalDaily WHERE Date=?", date) { rs ->
            if (rs.next()) rs.getLong("Seed") else null
        }
        if (existing != null) return existing

        val seed = abs(("MGame-Club:$date").hashCode().toLong()) + 1
        update("INSERT INTO globalDaily (Date, Seed, Trivia) VALUES (?, ?, NULL)", date, seed)
        return seed
    }

    suspend fun getDailyTrivia(date: String): String? {
        getDailySeed(date)
        return query("SELECT Trivia FROM globalDaily WHERE Date=?", date) { rs ->
            if (rs.next()) rs.getString("Trivia") else null
        }
    }

    suspend fun setDailyTrivia(date: String, trivia: String) {
        getDailySeed(date)
        update("UPDATE globalDaily SET Trivia=? WHERE Date=?", trivia, date)
    }

    suspend fun completeDailyPlay(
        userSnowflake: Long,
        guildSnowflake: Long,
        game: String,
        date: String,
        previousDate: String,
        reward: Int
    ): DailyPlayResult {
        if (getUserID(userSnowflake, guildSnowflake) == 0) {
            createUser(userSnowflake, guildSnowflake)
        }

        val existing = query(
            "SELECT Last_Play_Date, Streak FROM userDailyPlay WHERE Discord_ID=? && Game=?",
            userSnowflake, game
        ) { rs ->
            if (rs.next()) rs.getString("Last_Play_Date") to rs.getInt("Streak") else null
        }
        if (existing != null) {
            val (lastPlay, previousStreak) = existing
            if (lastPlay == date) {
                return DailyPlayResult(false, previousStreak, 0)
            }

            val nextStreak = if (lastPlay == previousDate) previousStreak + 1 else 1
            update(
                "UPDATE userDailyPlay SET Last_Play_Date=?, Streak=? WHERE Discord_ID=? && Game=?",
                date, nextStreak, userSnowflake, game
            )
            addCoins(userSnowflake, guildSnowflake, reward)
            return DailyPlayResult(true, nextStreak, reward)
        }

        update(
            "INSERT INTO userDailyPlay (Discord_ID, Game, Last_Play_Date, Streak) VALUES (?, ?, ?, 1)",
            userSnowflake, game, date
        )
        addCoins(userSnowflake, guildSnowflake, reward)
        return DailyPlayResult(true, 1, reward)
    }

    suspend fun hasCompletedDailyPlay(userSnowflake: Long, game: String, date: String): Boolean =
        query("SELECT Last_Play_Date FROM userDailyPlay WHERE Discord_ID=? && Game=?", userSnowflake, game) { rs ->
            rs.next() && rs.getString("Last_Play_Date") == date
        }

    private suspend fun getDailyPlays(userSnowflake: Long): List<UserDailyPlay> =
        query("SELECT Game, Last_Play_Date, Streak FROM userDailyPlay WHERE Discord_ID=?", userSnowflake) { rs ->
            buildList {
                while (rs.next()) {
                    add(
                        UserDailyPlay(
                            rs.getString("Game"),
                            rs.getString("Last_Play_Date"),
                            rs.getInt("Streak")
                        )
                    )
                }
            }
        }

    suspend fun getPremiumStatsGuilds(): List<GuildStatsChannel> =
        query("SELECT Discord_ID, Stats_Channel FROM guildData WHERE Premium=1 && Stats_Channel!=0") { rs ->
            buildList {
                while (rs.next()) add(GuildStatsChannel(rs.getLong("Discord_ID"), rs.getLong("Stats_Channel")))
            }
        }

    suspend fun setStatsChannel(guildSnowflake: Long, channelID: Long) {
        update("UPDATE guildData SET Stats_Channel=? WHERE Discord_ID=?", channelID, guildSnowflake)
    }


    /**
     * @param owned All bought Emotes -> Emote_ID - Emote
     * @param c4 Connect 4 Primary Emote
     * @param c42 Connect 4 Secondary Emote
     */
    data class UserEmote(val owned: Map<String, String>, val c4: String, val c42: String)

    data class UserDailyPlay(val game: String, val lastPlayDate: String, val streak: Int)

    data class DailyPlayResult(val completed: Boolean, val streak: Int, val reward: Int)

    data class LeaderboardEntry(val discordID: Long, val value: Int)

    data class GameHistoryEntry(val playedAt: Long, val matchID: String, val result: GameResult)

    data class GuildStatsChannel(val guildID: Long, val statsChannelID: Long)

    enum class GameResult(val id: Int) {
        WIN(1),
        LOSS(2),
        DRAW(3),
        PLAYED(4);

        companion object {
            fun from(id: Int): GameResult = entries.firstOrNull { it.id == id } ?: PLAYED
        }
    }

    /**
     * @param id Discord User ID
     * @param coins Amount of spendable Coins
     * @param totalCoins Total earned Coins
     * @param emotes All Emote Information
     */
    data class UserData(
        val id: Long,
        val coins: Int,
        val totalCoins: Int,
        val emotes: UserEmote?,
        val daily: List<UserDailyPlay>?
    )

    /**
     * @param id Discord Guild ID
     * @param premium Does this Guild own Premium?
     * @param statsChannel Discord Channel ID (Statistics Channel)
     */
    data class GuildData(
        val id: Long,
        val premium: Boolean,
        val statsChannel: Long,
        val language: LanguageManager.Language
    )

    init {
        ensureSchema()
    }
}
