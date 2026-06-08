package de.miraculixx.mgames.modules.games

import de.miraculixx.mgames.config.msg
import de.miraculixx.mgames.modules.games.chess.ChessGame
import de.miraculixx.mgames.modules.games.connectFour.C4Game
import de.miraculixx.mgames.modules.games.quickMath.QuickMathCommand
import de.miraculixx.mgames.modules.games.tictactoe.TTTGame
import de.miraculixx.mgames.modules.games.utils.FieldsTwoPlayer
import de.miraculixx.mgames.modules.games.utils.enums.Game
import de.miraculixx.mgames.modules.games.utils.SimpleGame
import de.miraculixx.mgames.utils.Color
import de.miraculixx.mgames.utils.log
import dev.minn.jda.ktx.coroutines.await
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.components.buttons.Button
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.hashMapOf

object GameManager {
    //Running Games
    // HashMap<GuildID, Map<GameType, HashMap<GameID, GameInstance>>>
    private val guilds = HashMap<Long, Map<Game, HashMap<UUID, SimpleGame>>>()

    suspend fun searchGame(hook: InteractionHook, member: Member, gameTag: String, gameName: String) {
        hook.editOriginal(
            "\uD83C\uDFAE **|| ${gameName.uppercase()}**\n" +
                    "${member.asMention} ${msg("commandGameQueue", member.guild.idLong).replace("%GAME%", gameName)}"
        ).setActionRow(
            Button.success("GAME_${gameTag}_ACCEPT_${member.id}", "Accept").withEmoji(Emoji.fromUnicode("✔️")),
            Button.danger("GAME_${gameTag}_CANCEL_${member.id}", "Cancel").withEmoji(Emoji.fromUnicode("✖️"))
        ).queue()
    }

    suspend fun requestGame(hook: InteractionHook, member: Member, opponent: Member, gameTag: String, gameName: String) {
        hook.editOriginal(
            "\uD83C\uDFAE **|| ${gameName.uppercase()}**\n" +
                    "${opponent.asMention} - ${msg("commandGameRequest", member.guild.idLong).replace("%GAME%", gameName).replace("%MEMBER%", member.asMention)}"
        ).setActionRow(
            Button.success("GAME_${gameTag}_YES_${member.id}_${opponent.id}", "Accept").withEmoji(Emoji.fromUnicode("✔️")),
            Button.danger("GAME_${gameTag}_NO_${opponent.id}", "Deny").withEmoji(Emoji.fromUnicode("✖️"))
        ).queue()
    }

    suspend fun newGameVersus(game: Game, guild: Guild, members: Pair<String, String>, channelID: Long, botLevel: Int = 0) {
        val gameMap = getGameMap(guild, game)
        val member1 = guild.retrieveMemberById(members.first).await() ?: return
        val member2 = guild.retrieveMemberById(members.second).await() ?: return
        val uuid = UUID.randomUUID()
        GoalManager.registerNewGame(game, false, member1.idLong, guild.idLong)
        GoalManager.registerNewGame(game, false, member2.idLong, guild.idLong)

        gameMap[uuid] = when (game) {
            Game.TIC_TAC_TOE -> TTTGame(member1, member2, uuid, channelID, guild, botLevel)
            Game.CONNECT_4 -> C4Game(member1, member2, uuid, guild, channelID, botLevel)
            Game.CHESS -> ChessGame(member1, member2, uuid, guild, channelID)
            else -> return
        }
    }

    suspend fun newGameSolo(game: Game, guild: Guild, member: String, channelID: Long, botLevel: Int = 0) {
        val gameMap = getGameMap(guild, game)
        val member = guild.retrieveMemberById(member).await() ?: return
        val uuid = UUID.randomUUID()
        GoalManager.registerNewGame(game, false, member.idLong, guild.idLong)

        gameMap[uuid] = when (game) {
            Game.QUICK_MATH -> QuickMathCommand()
            else -> return
        }
    }

    fun getGame(guildID: Long, type: Game, uuid: UUID): SimpleGame? {
        return guilds[guildID]?.get(type)?.get(uuid)
    }

    fun removeGame(guildID: Long, type: Game, uuid: UUID): Boolean {
        return guilds[guildID]?.get(type)?.remove(uuid) != null
    }

    private fun getGameMap(guild: Guild, game: Game): HashMap<UUID, SimpleGame> {
        val mGuild = guilds[guild.idLong] ?: run {
            val emptyGuild = buildMap { Game.entries.forEach { put(it, hashMapOf<UUID, SimpleGame>()) } }
            guilds[guild.idLong] = emptyGuild
            emptyGuild
        }
        return mGuild[game] ?: hashMapOf()
    }

    suspend fun shutdown() {
        "---=---> GAME MANAGER <---=---".log(Color.YELLOW)
        guilds.forEach { (guild, data) ->
            data.forEach { (type, games) ->
                games.forEach { (uuid, instance) ->
                    instance.setWinner(FieldsTwoPlayer.EMPTY)
                    removeGame(guild, type, uuid)
                }
            }
            " - Guild $guild offline".log(Color.YELLOW)
        }
        "---=---=---=---=---=---=---=---".log(Color.YELLOW)
    }
}