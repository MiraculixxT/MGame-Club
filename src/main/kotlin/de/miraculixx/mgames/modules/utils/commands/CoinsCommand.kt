package de.miraculixx.mgames.modules.utils.commands

import de.miraculixx.mgames.modules.games.GoalManager
import de.miraculixx.mgames.modules.games.utils.enums.Game
import de.miraculixx.mgames.utils.api.SQL
import de.miraculixx.mgames.utils.entities.SlashCommandEvent
import dev.minn.jda.ktx.messages.Embed
import kotlinx.datetime.TimeZone
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
        val userData = SQL.getUser(member.idLong, guild.idLong, daily = true)
        val dailyData = userData.daily ?: emptyList()
        if (userData.id == 0L) {
            it.reply("```diff\n- Wir konnten leider keine Daten über den Account finden :(```").setEphemeral(true).queue()
            return
        }
        val date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val timestamp = Clock.System.now().minus(date.minute.minutes)
            .minus(date.second.seconds).plus((25 - date.hour).hours).epochSeconds

        val defaultEmbed = Embed {
            color = 0xd39526
            title = "<:mcoin:996386525208117258> ~~M~~-**COINS** - `${userData.coins}`"
            description = if (ownStats) "<:blanc:784059217890770964> <:blanc:784059217890770964>" else "<:blanc:784059217890770964> **↳** `Miraculixx#1234` (<@341998118574751745>)\n" +
                    "<:blanc:784059217890770964> **↳** `Booster Rank` ${if (member.isBoosting) "<:yes:998195646467145751>" else "<:no:998195603324551323>"}"
            if (ownStats) {
                field {
                    name = "\uD83C\uDFAF  ||  DAILY PLAYS"
                    value = buildString {
                        append("```diff\n")
                        val today = GoalManager.currentDailyDate().toString()
                        Game.entries.forEach { game ->
                            val data = dailyData.firstOrNull { it.game == game.name }
                            val claimed = data?.lastClaimDate == today
                            append("${if (claimed) "+" else "-"} ${game.title} | Streak ${data?.streak ?: 0}\n")
                        }
                        append("```\n")
                        append("> New Daily Plays <t:$timestamp:R>")
                    }
                }
                field {
                    name = "**DAILY REWARDS**"
                    value = buildString {
                        append("```ini\n")
                        Game.entries.forEach { game -> append("${game.title} => ${game.coinValue * 10}\n") }
                        append("```")
                    }
                }
            }
        }

        it.replyEmbeds(defaultEmbed).queue()
    }
}
