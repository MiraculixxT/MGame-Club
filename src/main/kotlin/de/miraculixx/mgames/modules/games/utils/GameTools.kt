package de.miraculixx.mgames.modules.games.utils

import de.miraculixx.mgames.config.msg
import de.miraculixx.mgames.config.msgDiff
import de.miraculixx.mgames.modules.games.GameManager
import de.miraculixx.mgames.modules.games.GoalManager
import de.miraculixx.mgames.modules.games.utils.enums.Game
import de.miraculixx.mgames.utils.botID
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.entities.emoji.Emoji
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
                    } else if (replyBlockedGame(it, discordID, member.id to opponent.id)) {
                        return
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
                if (replyBlockedGame(it, discordID, member.id to it.jda.selfUser.id)) return
                it.reply(msg("commandStartBotGame", discordID, mapOf("DIFF" to (option ?: "RANDOM")))).setEphemeral(true).queue()
                GameManager.newGameVersus(game, it.guild ?: return, member.id to it.jda.selfUser.id, it.channel.idLong, level)
            }
            "daily" -> {
                val option = "Medium"
                val level = 2
                if (GoalManager.hasCompletedDaily(game, member.idLong)) {
                    it.reply("```diff\n- Daily ${game.title} wurde heute bereits abgeschlossen.```").setEphemeral(true).queue()
                    return
                }
                if (replyBlockedGame(it, discordID, member.id to it.jda.selfUser.id)) return
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
                val member1 = options[1]
                val member2 = options[2]
                val botLevel = options.getOrNull(3)?.toIntOrNull() ?: 0
                if (member2 == botID || member1 == botID) { // start bot game with clicker
                    if (replyBlockedGame(it, guildID, member.id to botID)) return
                    it.deferEdit().queue()
                    GameManager.newGameVersus(game, guild, member.id to member2, it.channel.idLong, botLevel)
                } else if (member.id != member1 && member.id != member2) {
                    it.reply(msg("notPartOfMatch", guildID, mapOf("GAME" to gameName))).setEphemeral(true).queue()
                } else {
                    if (replyBlockedGame(it, guildID, member1 to member2)) return
                    it.deferEdit().queue()
                    GameManager.newGameVersus(game, guild, member1 to member2, it.channel.idLong, botLevel)
                }
            }

            "YES" -> {
                if (options[2] != member.id)
                    it.reply(msgDiff(msg("commandCannotAccept", guildID))).setEphemeral(true).queue()
                else {
                    if (replyBlockedGame(it, guildID, options[1] to options[2])) return
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
                if (replyBlockedGame(it, guildID, options[1] to member.id)) return
                it.message.delete().queue()
                it.reply(msg("commandStartGame", guildID)).setEphemeral(true).queue()
                GameManager.newGameVersus(game, guild, options[1] to member.id, it.channel.idLong)
            }

            "SURRENDER" -> {
                val uuid = options.getOrNull(1)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (uuid == null) {
                    it.reply(msgDiff("Invalid surrender request.")).setEphemeral(true).queue()
                    return
                }
                val surrendered = GameManager.surrenderGame(guildID, game, uuid, member)
                if (surrendered) {
                    it.editMessage("```diff\n+ Old ${game.title} game surrendered.\n+ You can start a new one now.```")
                        .setComponents(emptyList()).queue()
                } else {
                    it.reply(msgDiff("Could not surrender this game.")).setEphemeral(true).queue()
                }
            }

            "CANCEL" -> if (options[1] != member.id)
                it.reply(msgDiff(msg("commandCannotDeny", guildID))).setEphemeral(true).queue()
            else {
                it.message.delete().queue()
                it.reply(msg("commandQueueLeave", guildID)).setEphemeral(true).queue()
            }
        }
    }

    private fun replyBlockedGame(event: SlashCommandInteractionEvent, guildID: Long, members: Pair<String, String>): Boolean {
        val conflict = getConflict(guildID, members) ?: return false
        event.reply(blockedGameMessage())
            .setEphemeral(true)
            .setComponents(blockedGameComponents(conflict))
            .queue()
        return true
    }

    private fun replyBlockedGame(event: ButtonInteractionEvent, guildID: Long, members: Pair<String, String>): Boolean {
        val conflict = getConflict(guildID, members) ?: return false
        event.reply(blockedGameMessage())
            .setEphemeral(true)
            .setComponents(blockedGameComponents(conflict))
            .queue()
        return true
    }

    private fun getConflict(guildID: Long, members: Pair<String, String>): GameManager.ActiveGame? {
        val players = setOfNotNull(members.first.toLongOrNull(), members.second.toLongOrNull())
        if (players.size != 2) return null
        return GameManager.findSimilarGame(guildID, game, players)
    }

    private fun blockedGameMessage(): String {
        return "```diff\n" +
                "- You already have a similar ${game.title} game running.\n" +
                "- Surrender the old game first or finish it normally.```"
    }

    private fun blockedGameComponents(conflict: GameManager.ActiveGame): List<ActionRow> {
        return listOf(
            ActionRow.of(
                Button.danger("GAME_${gameTag}_SURRENDER_${conflict.uuid}", "Surrender old game")
                    .withEmoji(Emoji.fromUnicode("\uD83C\uDFF3\uFE0F"))
            )
        )
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
