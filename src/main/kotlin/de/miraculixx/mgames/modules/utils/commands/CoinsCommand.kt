package de.miraculixx.mgames.modules.utils.commands

import de.miraculixx.mgames.config.Ansi
import de.miraculixx.mgames.config.msgAnsi
import de.miraculixx.mgames.modules.games.GoalManager
import de.miraculixx.mgames.modules.games.utils.enums.Game
import de.miraculixx.mgames.utils.Icons
import de.miraculixx.mgames.utils.api.SQL
import de.miraculixx.mgames.utils.entities.SlashCommandEvent
import de.miraculixx.mgames.utils.extensions.queueV2
import dev.minn.jda.ktx.interactions.components.Container
import dev.minn.jda.ktx.interactions.components.Thumbnail
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class CoinsCommand : SlashCommandEvent {
    override suspend fun trigger(it: SlashCommandInteractionEvent) {
        val ownStats = it.getOption("user") == null
        val member = if (ownStats) it.member ?: return
            else it.getOption("user")!!.asMember ?: return
        val guild = it.guild ?: return
        val guildID = guild.idLong
        val userData = SQL.getUser(member.idLong, guildID, daily = true)
        val dailyData = userData.daily ?: emptyList()
        if (userData.id == 0L) {
            it.reply("```diff\n- Wir konnten leider keine Daten über den Account finden :(```").setEphemeral(true).queue()
            return
        }
        val date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val timestamp = Clock.System.now().minus(date.minute.minutes)
            .minus(date.second.seconds).plus((25 - date.hour).hours).epochSeconds

        it.replyComponents(listOf(
            Container {
                section {
                    text("## \uD83D\uDCCA || ${member.asMention} Statistics")
                    accessory = Thumbnail(member.user.avatarUrl ?: "https://imgur.com/gKjrvOA.png")
                    text("> ${Icons.mCoins} x ${userData.coins} (${userData.totalCoins})")
                }
                separator()
                text("### \uD83D\uDCC6 || Daily Games\n${msgAnsi(buildDailyOverview(dailyData))}\n> New Daily Plays <t:$timestamp:R>")
            }
        )).queueV2()
    }

    private fun buildDailyOverview(dailyData: List<SQL.UserDailyPlay>): String {
        val today = GoalManager.currentDailyDate()
        val todayString = today.toString()
        val yesterdayString = today.minus(1, DateTimeUnit.DAY).toString()
        val dailyDataByGame = dailyData.associateBy { it.game }
        val titleWidth = Game.entries.maxOf { it.title.length }

        return Game.entries
            .map { game ->
                val data = dailyDataByGame[game.name]
                val completedToday = data?.lastPlayDate == todayString
                val streak = when (data?.lastPlayDate) {
                    todayString, yesterdayString -> data.streak
                    else -> 0
                }
                DailyGameState(game, streak, completedToday)
            }
            .sortedWith(compareBy { it.streak == 0 })
            .joinToString(separator = "\n", postfix = "\n") { state ->
                val color = if (state.completedToday) Ansi.textGreen else Ansi.textRed
                val status = if (state.completedToday) "✓" else if (state.streak > 0) "Pending..." else "✗"
                "$color${Ansi.bold}${state.game.title.padEnd(titleWidth)}${Ansi.reset}" +
                    "$color | Streak ${state.streak} | $status${Ansi.reset}"
            }
    }

    private data class DailyGameState(val game: Game, val streak: Int, val completedToday: Boolean)
}
