package de.miraculixx.mgames.modules.utils.commands

import de.miraculixx.mgames.modules.games.UpdaterGame
import de.miraculixx.mgames.utils.api.SQL
import de.miraculixx.mgames.utils.entities.SlashCommandEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

class AdminCommand : SlashCommandEvent {
    override suspend fun trigger(it: SlashCommandInteractionEvent) {
        when (it.subcommandName) {
            "refresh-stats" -> {
                val guild = it.guild ?: return
                val data = SQL.getGuild(guild.idLong)
                UpdaterGame.updateLeaderboardGuild(guild, guild.getTextChannelById(data.statsChannel))
                it.reply("Done").setEphemeral(true).queue()
            }

            "swap-daily" -> {
                UpdaterGame.updateDailyChallenges()
                it.reply("Done").setEphemeral(true).queue()
            }
        }
    }
}