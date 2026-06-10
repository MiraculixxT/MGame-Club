package de.miraculixx.mgames.utils.api

import de.miraculixx.mgames.config.ConfigManager
import de.miraculixx.mgames.config.LanguageManager
import de.miraculixx.mgames.modules.games.utils.enums.Game
import de.miraculixx.mgames.modules.games.utils.enums.GameMode
import de.miraculixx.mgames.utils.Color
import de.miraculixx.mgames.utils.error
import de.miraculixx.mgames.utils.log
import kotlinx.coroutines.delay
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.util.UUID
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

object SQL {
    private var connection: Connection

    private fun connect(): Connection {
        val con = DriverManager.getConnection(
            "jdbc:mariadb://miraculixx.de:3306/MGames",
            "MGamesBot",
            ConfigManager.coreConfig.SQL_TOKEN
        )
        if (con.isValid(0))
            ">> Connection established to MariaDB".log(Color.GREEN)
        else ">> ERROR > MariaDB refused the connection".error()
        return con
    }

    private fun ensureSchema() {
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
            connection.prepareStatement("DROP TEMPORARY TABLE IF EXISTS userDailyPlay_global").use { it.executeUpdate() }
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
            connection.prepareStatement("ALTER TABLE userDailyPlay ADD PRIMARY KEY (Discord_ID, Game)").use { it.executeUpdate() }
            connection.prepareStatement(
                "INSERT INTO userDailyPlay (Discord_ID, Game, Last_Play_Date, Streak) " +
                    "SELECT Discord_ID, Game, Last_Play_Date, Streak FROM userDailyPlay_global"
            ).use { it.executeUpdate() }
            connection.prepareStatement("DROP TEMPORARY TABLE IF EXISTS userDailyPlay_global").use { it.executeUpdate() }
        }
    }

    private fun columnExists(table: String, column: String): Boolean {
        connection.prepareStatement(
            "SELECT COUNT(*) AS Columns FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA=DATABASE() && TABLE_NAME='$table' && COLUMN_NAME='$column'"
        ).use { statement ->
            val result = statement.executeQuery()
            return result.next() && result.getInt("Columns") > 0
        }
    }

    suspend fun call(statement: String, resultSet: Int? = null): ResultSet {
        while (!connection.isValid(1)) {
            "ERROR >> SQL - No valid connection!".error()
            connection = connect()
            delay(1000.milliseconds)
        }

        val query = if (resultSet != null) connection.prepareStatement(statement, resultSet)
        else connection.prepareStatement(statement)
        return query.executeQuery()
    }

    suspend fun update(statement: String): Int {
        while (!connection.isValid(1)) {
            "ERROR >> SQL - No valid connection!".error()
            connection = connect()
            delay(1000.milliseconds)
        }

        return connection.prepareStatement(statement).use { it.executeUpdate() }
    }

    /*
    Interactions to the API
     */
    private suspend fun createUser(userSnowflake: Long, guildSnowflake: Long): UserData {
        // Generell User Account
        getGuild(guildSnowflake)
        update("INSERT INTO userData (Guild_ID, Discord_ID, Coins, Total_Coins) VALUES ($guildSnowflake, $userSnowflake, 0, 0)")
        val userData = call("SELECT * FROM userData WHERE Guild_ID=$guildSnowflake && Discord_ID=$userSnowflake")
        userData.next()
        val userID = userData.getInt("ID")

        // Create Empty Data Rows to simplify future calls
        update("INSERT INTO userEmotesActive VALUES ($userID, '\uD83D\uDD34', '\uD83D\uDFE1')")
        return UserData(
            userSnowflake, 0, 0,
            UserEmote(emptyMap(), "\uD83D\uDD34", "\uD83D\uDFE1"),
            emptyList()
        )
    }

    private suspend fun createGuild(guildSnowflake: Long): GuildData {
        val language = LanguageManager.Language.EN
        update("INSERT INTO guildData (Discord_ID, Premium, Stats_Channel, Language) VALUES ($guildSnowflake, false, 0, '${language.key}')")
        LanguageManager.cacheGuildLanguage(guildSnowflake, language.key)
        return GuildData(guildSnowflake, false, 0, language)
    }

    private suspend fun getUserID(userSnowflake: Long, guildSnowflake: Long): Int {
        val id = call("SELECT ID FROM userData WHERE Discord_ID=$userSnowflake && Guild_ID=$guildSnowflake")
        return if (id.next()) id.getInt("ID")
        else 0
    }

    suspend fun getUser(userSnowflake: Long, guildSnowflake: Long, emotes: Boolean = false, daily: Boolean = false): UserData {
        val result = call("SELECT * FROM userData WHERE Guild_ID=$guildSnowflake && Discord_ID=$userSnowflake")
        if (!result.next()) return createUser(userSnowflake, guildSnowflake)
        return UserData(
            userSnowflake,
            result.getInt("Coins"),
            result.getInt("Total_Coins"),
            if (emotes) {
                val allEmotes = call("SELECT Emote_Type, Emote FROM userEmotes, userData WHERE Guild_ID=$guildSnowflake && Discord_ID=$userSnowflake && userEmotes.ID=userData.ID")
                val activeEmotes = call("SELECT * FROM userEmotesActive, userData WHERE Guild_ID=$guildSnowflake && Discord_ID=$userSnowflake && userEmotesActive.ID=userData.ID")
                activeEmotes.next()
                val emoteMap = buildMap {
                    while (allEmotes.next()) {
                        try {
                            put(
                                allEmotes.getString("Emote_Type"),
                                allEmotes.getString("Emote")
                            )
                        } catch (e: Exception) {
                            put("1", "2")
                        }
                    }
                }
                UserEmote(
                    emoteMap,
                    activeEmotes.getString("C4_P"),
                    activeEmotes.getString("C4_S")
                )
            } else null,
            if (daily) getDailyPlays(userSnowflake) else null
        )
    }

    suspend fun getGuild(guildSnowflake: Long): GuildData {
        val result = call("SELECT * FROM guildData WHERE Discord_ID=$guildSnowflake")
        if (!result.next()) return createGuild(guildSnowflake)
        val language = LanguageManager.Language.from(result.getString("Language"))
        LanguageManager.cacheGuildLanguage(guildSnowflake, language.key)
        return GuildData(
            guildSnowflake,
            result.getBoolean("Premium"),
            result.getLong("Stats_Channel"),
            language
        )
    }

    suspend fun setGuildLanguage(guildSnowflake: Long, language: LanguageManager.Language) {
        getGuild(guildSnowflake)
        update("UPDATE guildData SET Language='${language.key}' WHERE Discord_ID=$guildSnowflake")
        LanguageManager.cacheGuildLanguage(guildSnowflake, language.key)
    }

    suspend fun setUserCoins(userSnowflake: Long, guildSnowflake: Long, amount: Int) {
        update("UPDATE userData SET Coins=$amount WHERE Discord_ID=$userSnowflake && Guild_ID=$guildSnowflake")
    }

    suspend fun addEmote(userSnowflake: Long, guildSnowflake: Long, type: String, emote: String) {
        val id = getUserID(userSnowflake, guildSnowflake)
        update("INSERT INTO userEmotes VALUES ($id, '$type', '$emote')")
    }

    suspend fun setActiveEmote(userSnowflake: Long, guildSnowflake: Long, type: String, newEmote: String) {
        val id = getUserID(userSnowflake, guildSnowflake)
        update("UPDATE userEmotesActive SET $type='$newEmote' WHERE ID=$id")
    }

    suspend fun addGameStats(userSnowflake: Long, game: Game, mode: GameMode, difficulty: Int, won: Boolean) {
        val safeDifficulty = difficulty.coerceIn(0, 3)
        val wins = if (won) 1 else 0
        val losses = if (won) 0 else 1
        update(
            "INSERT INTO userStats (Discord_ID, Game_ID, Mode_ID, Difficulty, Wins, Losses) " +
                "VALUES ($userSnowflake, ${game.id}, ${mode.id}, $safeDifficulty, $wins, $losses) " +
                "ON DUPLICATE KEY UPDATE Wins=Wins+$wins, Losses=Losses+$losses"
        )
    }

    suspend fun addGameHistory(
        guildSnowflake: Long,
        game: Game,
        results: Map<Long, GameResult>,
        timestamp: Long = System.currentTimeMillis(),
        matchID: String = UUID.randomUUID().toString()
    ) {
        val escapedMatchID = matchID.replace("'", "''")
        results.forEach { (userSnowflake, result) ->
            update(
                "INSERT INTO gameHistory (Played_At, Guild_ID, Game_ID, Discord_ID, Result, Match_ID) " +
                    "VALUES ($timestamp, $guildSnowflake, ${game.id}, $userSnowflake, ${result.id}, '$escapedMatchID')"
            )
        }
    }

    suspend fun getTopTotalCoins(guildSnowflake: Long, limit: Int = 3): List<LeaderboardEntry> {
        val safeLimit = limit.coerceIn(1, 10)
        val response = call(
            "SELECT Discord_ID, Total_Coins FROM userData " +
                "WHERE Guild_ID=$guildSnowflake ORDER BY Total_Coins DESC LIMIT $safeLimit"
        )
        return buildList {
            while (response.next()) {
                add(LeaderboardEntry(response.getLong("Discord_ID"), response.getInt("Total_Coins")))
            }
        }
    }

    suspend fun getTopDailyStreaks(guildSnowflake: Long, validDates: Collection<String>, limit: Int = 3): List<LeaderboardEntry> {
        val safeLimit = limit.coerceIn(1, 10)
        if (validDates.isEmpty()) return emptyList()
        val dates = validDates.joinToString(",") { "'${it.replace("'", "''")}'" }
        val response = call(
            "SELECT userDailyPlay.Discord_ID, MAX(userDailyPlay.Streak) AS Streak " +
                "FROM userDailyPlay INNER JOIN userData ON userData.Discord_ID=userDailyPlay.Discord_ID " +
                "WHERE userData.Guild_ID=$guildSnowflake && userDailyPlay.Last_Play_Date IN ($dates) " +
                "GROUP BY userDailyPlay.Discord_ID ORDER BY Streak DESC LIMIT $safeLimit"
        )
        return buildList {
            while (response.next()) {
                add(LeaderboardEntry(response.getLong("Discord_ID"), response.getInt("Streak")))
            }
        }
    }

    suspend fun getGuildHistory(guildSnowflake: Long, sinceMillis: Long): List<GameHistoryEntry> {
        val response = call(
            "SELECT Played_At, Match_ID, Result FROM gameHistory " +
                "WHERE Guild_ID=$guildSnowflake && Played_At >= $sinceMillis ORDER BY Played_At ASC"
        )
        return buildList {
            while (response.next()) {
                add(
                    GameHistoryEntry(
                        response.getLong("Played_At"),
                        response.getString("Match_ID"),
                        GameResult.from(response.getInt("Result"))
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
        update("UPDATE userData SET Coins=Coins+$amount, Total_Coins=Total_Coins+$amount WHERE ID=$id")
    }

    suspend fun getDailySeed(date: String): Long {
        val existing = call("SELECT Seed FROM globalDaily WHERE Date='$date'")
        if (existing.next()) return existing.getLong("Seed")

        val seed = abs(("MGame-Club:$date").hashCode().toLong()) + 1
        update("INSERT INTO globalDaily (Date, Seed, Trivia) VALUES ('$date', $seed, NULL)")
        return seed
    }

    suspend fun getDailyTrivia(date: String): String? {
        getDailySeed(date)
        val existing = call("SELECT Trivia FROM globalDaily WHERE Date='$date'")
        if (!existing.next()) return null
        return existing.getString("Trivia")
    }

    suspend fun setDailyTrivia(date: String, trivia: String) {
        getDailySeed(date)
        val escapedTrivia = trivia.replace("\\", "\\\\").replace("'", "''")
        update("UPDATE globalDaily SET Trivia='$escapedTrivia' WHERE Date='$date'")
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

        val escapedGame = game.replace("'", "''")
        val existing = call(
            "SELECT Last_Play_Date, Streak FROM userDailyPlay " +
                "WHERE Discord_ID=$userSnowflake && Game='$escapedGame'"
        )
        if (existing.next()) {
            val lastPlay = existing.getString("Last_Play_Date")
            if (lastPlay == date) {
                return DailyPlayResult(false, existing.getInt("Streak"), 0)
            }

            val previousStreak = existing.getInt("Streak")
            val nextStreak = if (lastPlay == previousDate) previousStreak + 1 else 1
            update(
                "UPDATE userDailyPlay SET Last_Play_Date='$date', Streak=$nextStreak " +
                    "WHERE Discord_ID=$userSnowflake && Game='$escapedGame'"
            )
            addCoins(userSnowflake, guildSnowflake, reward)
            return DailyPlayResult(true, nextStreak, reward)
        }

        update(
            "INSERT INTO userDailyPlay (Discord_ID, Game, Last_Play_Date, Streak) " +
                "VALUES ($userSnowflake, '$escapedGame', '$date', 1)"
        )
        addCoins(userSnowflake, guildSnowflake, reward)
        return DailyPlayResult(true, 1, reward)
    }

    suspend fun hasCompletedDailyPlay(userSnowflake: Long, game: String, date: String): Boolean {
        val escapedGame = game.replace("'", "''")
        val existing = call(
            "SELECT Last_Play_Date FROM userDailyPlay " +
                "WHERE Discord_ID=$userSnowflake && Game='$escapedGame'"
        )
        return existing.next() && existing.getString("Last_Play_Date") == date
    }

    private suspend fun getDailyPlays(userSnowflake: Long): List<UserDailyPlay> {
        val dailyData = call(
            "SELECT Game, Last_Play_Date, Streak FROM userDailyPlay " +
                "WHERE Discord_ID=$userSnowflake"
        )
        return buildList {
            while (dailyData.next()) {
                add(
                    UserDailyPlay(
                        dailyData.getString("Game"),
                        dailyData.getString("Last_Play_Date"),
                        dailyData.getInt("Streak")
                    )
                )
            }
        }
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
    data class UserData(val id: Long, val coins: Int, val totalCoins: Int, val emotes: UserEmote?, val daily: List<UserDailyPlay>?)

    /**
     * @param id Discord Guild ID
     * @param premium Does this Guild own Premium?
     * @param statsChannel Discord Channel ID (Statistics Channel)
     */
    data class GuildData(val id: Long, val premium: Boolean, val statsChannel: Long, val language: LanguageManager.Language)

    init {
        connection = connect()
        ensureSchema()
    }
}
