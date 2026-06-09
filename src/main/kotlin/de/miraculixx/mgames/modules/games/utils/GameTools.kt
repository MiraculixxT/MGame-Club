package de.miraculixx.mgames.modules.games.utils

import de.miraculixx.mgames.config.msg
import de.miraculixx.mgames.config.msgDiff
import de.miraculixx.mgames.modules.games.GameManager
import de.miraculixx.mgames.modules.games.GoalManager
import de.miraculixx.mgames.modules.games.utils.enums.Game
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import java.util.*

class GameTools(private val gameTag: String, private val gameName: String, private val game: Game) {
    suspend fun command(it: SlashCommandInteractionEvent) {
        val subcommand = it.subcommandName ?: return
        val member = it.member ?: return
        val hook = it.hook
        val discordID = it.guild?.idLong ?: return

        when (subcommand) {
            "user" -> {
                val opponent = it.getOption("request")?.asMember
                if (opponent != null) {
                    if (opponent.id == member.id)
                        it.reply(msgDiff(msgDiff(msg("commandSamePlayer", discordID)))).setEphemeral(true).queue()
                    else if (opponent.user.isBot) {
                        it.reply(msgDiff(msg("commandNotHuman", discordID))).setEphemeral(true).queue()
                    } else {
                        it.deferReply().queue()
                        GameManager.requestGame(hook, member, opponent, gameTag, gameName)
                    }
                } else {
                    it.deferReply().queue()
                    GameManager.searchGame(hook, member, gameTag, gameName)
                }
            }
            "bot" -> {
                val option = it.getOption("difficulty")?.asString?.uppercase()
                val level = option.toDifficultyLevel()
                it.reply(msg("commandStartBotGame", discordID, mapOf("DIFF" to (option ?: "RANDOM")))).setEphemeral(true).queue()
                GameManager.newGameVersus(game, it.guild ?: return, member.id to it.jda.selfUser.id, it.channel.idLong, level)
            }
            "daily" -> {
                val option = "Medium"
                val level = 2
                if (GoalManager.hasCompletedDaily(game, member.idLong, discordID)) {
                    it.reply("```diff\n- Daily ${game.title} wurde heute bereits abgeschlossen.```").setEphemeral(true).queue()
                    return
                }
                it.reply("```diff\n+ Daily ${game.title} wird gestartet!\n+ Difficulty: $option```").setEphemeral(true).queue()
                GameManager.newGameVersus(
                    game,
                    it.guild ?: return,
                    member.id to it.jda.selfUser.id,
                    it.channel.idLong,
                    level,
                    daily = true,
                    seed = GoalManager.getDailySeed()
                )
            }
        }
    }

    suspend fun buttons(it: ButtonInteractionEvent) {
        val id = it.componentId.removePrefix("GAME_${gameTag}_")
        val member = it.member ?: return
        val guild = it.guild ?: return
        val options = id.split('_')
        val guildID = guild.idLong

        // GAME_TTT_ (first snippet)
        // P_<DATA> (options)
        when (options[0]) {
            "P" -> GameManager.getGame(guild.idLong, game, UUID.fromString(options[1]))
                    ?.interact(options.subList(2, options.size), member, it)
            "R" -> { // <id1>_<id2>_<difficulty>
                it.deferEdit().queue()
                val botLevel = options.getOrNull(3)?.toIntOrNull() ?: 0
                GameManager.newGameVersus(game, guild, options[1] to options[2], it.channel.idLong, botLevel)
            }
            "YES" -> {
                if (options[2] != member.id)
                    it.reply(msgDiff(msg("commandCannotAccept", guildID))).setEphemeral(true).queue()
                else {
                    it.message.delete().queue()
                    GameManager.newGameVersus(game, guild, options[1] to options[2], it.channel.idLong)
                }
            }
            "NO" -> {
                if (options[1] != member.id)
                    it.reply(msgDiff(msg("commandCannotDeny", guildID))).setEphemeral(true).queue()
                else it.editMessage(
                    "\uD83C\uDFAE || ${gameName.uppercase()}\n" +
                            "❌ ${member.asMention} ${msg("commandDeclineRequest", guildID)}"
                ).setComponents(ActionRow.of(it.message.components.first().asActionRow().buttons.map { it.asDisabled() })).queue()
            }
            "ACCEPT" -> if (options[1] == member.id)
                it.reply(msgDiff(msg("commandSelfPlay", guildID))).setEphemeral(true).queue()
            else {
                it.message.delete().queue()
                it.reply(msg("commandStartGame", guildID)).setEphemeral(true).queue()
                GameManager.newGameVersus(game, guild, options[1] to member.id, it.channel.idLong)
            }
            "CANCEL" -> if (options[1] != member.id)
                it.reply(msgDiff(msg("commandCannotDeny", guildID))).setEphemeral(true).queue()
            else {
                it.message.delete().queue()
                it.reply(msg("commandQueueLeave", guildID)).setEphemeral(true).queue()
            }
        }
    }

    private fun String?.toDifficultyLevel(): Int {
        return when (this) {
            "HARD" -> 3
            "MEDIUM" -> 2
            "EASY" -> 1
            else -> (1..3).random()
        }
    }
}
