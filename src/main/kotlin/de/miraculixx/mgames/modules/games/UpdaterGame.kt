package de.miraculixx.mgames.modules.games

import de.miraculixx.mgames.modules.trivia.ensureDailyTriviaQuestion
import de.miraculixx.mgames.utils.Color as LogColor
import de.miraculixx.mgames.utils.Colors
import de.miraculixx.mgames.utils.Icons
import de.miraculixx.mgames.utils.api.SQL
import de.miraculixx.mgames.utils.log
import dev.minn.jda.ktx.interactions.components.Container
import kotlinx.coroutines.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import net.dv8tion.jda.api.components.MessageTopLevelComponent
import net.dv8tion.jda.api.components.mediagallery.MediaGallery
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.MessageHistory
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.utils.FileUpload
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalTime::class)
object UpdaterGame {
    private var JDA: JDA? = null

    fun start(jda: JDA): Job {
        JDA = jda
        return CoroutineScope(Dispatchers.Default).launch {
            delay(10.seconds) // Let the system slowly starts
            launch {
                GoalManager.getDailySeed()
                updateDailyTriviaQuestion()
                while (true) {
                    val current = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                    if (current.hour == 1) {
                        updateDailyPlays()
                        GameManager.cleanupOldInstances(1.hours.inWholeMilliseconds)
                        delay(23.hours)
                    }
                    delay(1.minutes)
                }
            }
            launch {
                while (true) {
                    updateLeaderboards()
                    delay(1.hours)
                }
            }
        }
    }

    suspend fun updateDailyPlays() {
        "---=---> DAILY UPDATE <---=---".log(LogColor.YELLOW)
        val seed = GoalManager.getDailySeed()
        updateDailyTriviaQuestion()
        " - Daily seed: $seed".log(LogColor.YELLOW)
    }

    private suspend fun updateDailyTriviaQuestion() {
        runCatching { ensureDailyTriviaQuestion() }
            .onFailure { " - Daily trivia preload failed: ${it.message}".log(LogColor.YELLOW) }
    }

    private fun updateLeaderboards() = runBlocking {
        "---=---> STATS UPDATE <---=---".log(LogColor.YELLOW)
        val call = SQL.call("SELECT Stats_Channel, Discord_ID FROM guildData WHERE Premium=1 && Stats_Channel!=0")

        var counter = 0
        while (call.next()) {
            counter++
            val guildID = call.getLong("Discord_ID")
            val statsChannelID = call.getLong("Stats_Channel")
            launch {
                val guild = JDA!!.getGuildById(guildID) ?: return@launch
                val channel = guild.getTextChannelById(statsChannelID)
                updateLeaderboardGuild(guild, channel)
            }
        }

        "Finished checking $counter Guilds".log(LogColor.YELLOW)
        "---=---=---=---=---=---=---=---".log(LogColor.YELLOW)
    }

    suspend fun updateLeaderboardGuild(guild: Guild, statsChannel: MessageChannel?) {
        val guildID = guild.idLong
        if (statsChannel == null) {
            SQL.update("UPDATE guildData SET Stats_Channel=0 WHERE Discord_ID=$guildID")
            " - GUILD REMOVE > $guildID deleted their stats channel".log(LogColor.YELLOW)
            return
        }
        val dailyDate = GoalManager.currentDailyDate()
        val topCoins = SQL.getTopTotalCoins(guildID)
        val topStreaks = SQL.getTopDailyStreaks(guildID, listOf(dailyDate.toString(), dailyDate.minus(1, DateTimeUnit.DAY).toString()))
        val since = Clock.System.now().minus(30.days).toEpochMilliseconds()
        val dailyStats = LeaderboardGraph.aggregate(SQL.getGuildHistory(guildID, since))
        val summary = LeaderboardGraph.summarize(dailyStats)
        val graph = LeaderboardGraph.renderPng(dailyStats)
        val nextUpdate = Clock.System.now().plus(1.hours).epochSeconds
        val components = renderLeaderboard(guildID, topStreaks, topCoins, summary, graph, nextUpdate)

        try {
            statsChannel.getHistoryFromBeginning(10).queue { history: MessageHistory ->
                val current = history.retrievedHistory.firstOrNull { it.author.id == JDA!!.selfUser.id }
                if (current == null) {
                    statsChannel.sendMessageComponents(components).useComponentsV2().queue()
                } else {
                    current.editMessageComponents(components)
                        .setReplace(true)
                        .useComponentsV2()
                        .queue()
                }
            }
        } catch (e: InsufficientPermissionException) {
            " - NO PERMISSION > Guild $guildID".log(LogColor.YELLOW)
        }
    }

    private fun renderLeaderboard(
        guildID: Long,
        topStreaks: List<SQL.LeaderboardEntry>,
        topCoins: List<SQL.LeaderboardEntry>,
        summary: LeaderboardGraph.Summary,
        graph: ByteArray,
        nextUpdate: Long
    ): List<MessageTopLevelComponent> {
        val graphUpload = FileUpload.fromData(graph, "mgames-leaderboard-$guildID.png")
            .setDescription("MGame Club activity graph for the last 30 days")

        return listOf(
            Container(accentColor = Colors.yellow) {
                text("## \uD83D\uDC51 || LEADERBOARD")
                text(
                    "> Updates <t:$nextUpdate:R>\n" +
                        "> ${summary.played} games | ${summary.wins} wins | ${summary.losses} losses | " +
                        "${summary.draws} draws | ${summary.winRate}% win rate"
                )
                separator()
                text("### \uD83D\uDD25 || Highest Streaks\n${formatLeaderboard(topStreaks, "days")}")
                text("### ${Icons.mCoins} || Highest Coins\n${formatLeaderboard(topCoins, "coins")}")
                separator()
                components += MediaGallery.of(
                    MediaGalleryItem.fromFile(graphUpload)
                        .withDescription("Game activity graph for the last 30 days")
                )
            }
        )
    }

    private fun formatLeaderboard(entries: List<SQL.LeaderboardEntry>, unit: String): String {
        if (entries.isEmpty()) return "> *Empty*\n> *Empty*\n> *Empty*"
        return buildString {
            repeat(3) { index ->
                val entry = entries.getOrNull(index)
                if (entry == null) append("> *Empty*\n")
                else append("> **#${index + 1}** <@${entry.discordID}> - ${entry.value} $unit\n")
            }
        }.trimEnd()
    }
}
