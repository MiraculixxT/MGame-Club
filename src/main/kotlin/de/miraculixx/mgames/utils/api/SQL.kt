package de.miraculixx.mgames.utils.api

import de.miraculixx.mgames.config.ConfigManager
import de.miraculixx.mgames.modules.games.utils.enums.Game
import de.miraculixx.mgames.modules.games.utils.enums.GameMode
import de.miraculixx.mgames.utils.Color
import de.miraculixx.mgames.utils.error
import de.miraculixx.mgames.utils.log
import kotlinx.coroutines.delay
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import kotlin.math.abs

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

    suspend fun call(statement: String, resultSet: Int? = null): ResultSet {
        while (!connection.isValid(1)) {
            "ERROR >> SQL - No valid connection!".error()
            connection = connect()
            delay(1000)
        }

        val query = if (resultSet != null) connection.prepareStatement(statement, resultSet)
        else connection.prepareStatement(statement)
        return query.executeQuery()
    }

    suspend fun update(statement: String): Int {
        while (!connection.isValid(1)) {
            "ERROR >> SQL - No valid connection!".error()
            connection = connect()
            delay(1000)
        }

        return connection.prepareStatement(statement).use { it.executeUpdate() }
    }

    /*
    Interactions to the API
     */
    private suspend fun createUser(userSnowflake: Long, guildSnowflake: Long): UserData {
        // Generell User Account
        getGuild(guildSnowflake)
        update("INSERT INTO userData (Guild_ID, Discord_ID, Coins) VALUES ($guildSnowflake, $userSnowflake, 0)")
        val userData = call("SELECT * FROM userData WHERE Guild_ID=$guildSnowflake && Discord_ID=$userSnowflake")
        userData.next()
        val userID = userData.getInt("ID")

        // Create Empty Data Rows to simplify future calls
        update("INSERT INTO userEmotesActive VALUES ($userID, '\uD83D\uDD34', '\uD83D\uDFE1')")
        return UserData(
            userSnowflake, 0,
            UserEmote(emptyMap(), "\uD83D\uDD34", "\uD83D\uDFE1"),
            emptyList()
        )
    }

    private suspend fun createGuild(guildSnowflake: Long): GuildData {
        update("INSERT INTO guildData (Discord_ID, Premium, Stats_Channel, Language) VALUES ($guildSnowflake, false, 0, 'EN_US')")
        return GuildData(guildSnowflake, false, 0)
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
            if (daily) getDailyPlays(result.getInt("ID")) else null
        )
    }

    suspend fun getGuild(guildSnowflake: Long): GuildData {
        val result = call("SELECT * FROM guildData WHERE Discord_ID=$guildSnowflake")
        if (!result.next()) return createGuild(guildSnowflake)
        return GuildData(
            guildSnowflake,
            result.getBoolean("Premium"),
            result.getLong("Stats_Channel")
        )
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

    suspend fun addCoins(userSnowflake: Long, guildSnowflake: Long, amount: Int) {
        if (amount <= 0) return
        var id = getUserID(userSnowflake, guildSnowflake)
        if (id == 0) {
            createUser(userSnowflake, guildSnowflake)
            id = getUserID(userSnowflake, guildSnowflake)
        }
        update("UPDATE userData SET Coins=Coins+$amount WHERE ID=$id")
    }

    suspend fun getDailySeed(date: String): Long {
        val existing = call("SELECT Seed FROM globalDaily WHERE Date='$date'")
        if (existing.next()) return existing.getLong("Seed")

        val seed = abs(("MGame-Club:$date").hashCode().toLong()) + 1
        update("INSERT INTO globalDaily VALUES ('$date', $seed)")
        return seed
    }

    suspend fun completeDailyPlay(
        userSnowflake: Long,
        guildSnowflake: Long,
        game: String,
        date: String,
        previousDate: String,
        reward: Int
    ): DailyPlayResult {
        var id = getUserID(userSnowflake, guildSnowflake)
        if (id == 0) {
            createUser(userSnowflake, guildSnowflake)
            id = getUserID(userSnowflake, guildSnowflake)
        }

        val escapedGame = game.replace("'", "''")
        val existing = call("SELECT Last_Play_Date, Streak, Last_Claim_Date FROM userDailyPlay WHERE ID=$id && Game='$escapedGame'")
        if (existing.next()) {
            val lastClaim = existing.getString("Last_Claim_Date")
            if (lastClaim == date) {
                return DailyPlayResult(false, existing.getInt("Streak"), 0)
            }

            val previousStreak = existing.getInt("Streak")
            val lastPlay = existing.getString("Last_Play_Date")
            val nextStreak = if (lastPlay == previousDate) previousStreak + 1 else 1
            update(
                "UPDATE userDailyPlay SET Last_Play_Date='$date', Streak=$nextStreak, Last_Claim_Date='$date' " +
                    "WHERE ID=$id && Game='$escapedGame'"
            )
            addCoins(userSnowflake, guildSnowflake, reward)
            return DailyPlayResult(true, nextStreak, reward)
        }

        update("INSERT INTO userDailyPlay VALUES ($id, '$escapedGame', '$date', 1, '$date')")
        addCoins(userSnowflake, guildSnowflake, reward)
        return DailyPlayResult(true, 1, reward)
    }

    private suspend fun getDailyPlays(userID: Int): List<UserDailyPlay> {
        val dailyData = call("SELECT Game, Last_Play_Date, Streak, Last_Claim_Date FROM userDailyPlay WHERE ID=$userID")
        return buildList {
            while (dailyData.next()) {
                add(
                    UserDailyPlay(
                        dailyData.getString("Game"),
                        dailyData.getString("Last_Play_Date"),
                        dailyData.getInt("Streak"),
                        dailyData.getString("Last_Claim_Date")
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

    data class UserDailyPlay(val game: String, val lastPlayDate: String, val streak: Int, val lastClaimDate: String)

    data class DailyPlayResult(val claimed: Boolean, val streak: Int, val reward: Int)

    /**
     * @param id Discord User ID
     * @param coins Amount of Coins
     * @param emotes All Emote Information
     */
    data class UserData(val id: Long, val coins: Int, val emotes: UserEmote?, val daily: List<UserDailyPlay>?)

    /**
     * @param id Discord Guild ID
     * @param premium Does this Guild own Premium?
     * @param statsChannel Discord Channel ID (Statistics Channel)
     */
    data class GuildData(val id: Long, val premium: Boolean, val statsChannel: Long)

    init {
        connection = connect()
    }
}
