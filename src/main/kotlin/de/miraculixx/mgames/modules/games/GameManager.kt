package de.miraculixx.mgames.modules.games

import de.miraculixx.mgames.config.msg
import de.miraculixx.mgames.modules.games.connectFour.C4Game
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
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.components.buttons.Button
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.hashMapOf

object GameManager {
    //Running Games
    // HashMap<GuildID, Map<GameType, HashMap<GameID, GameInstance>>>
    private val guilds = HashMap<Long, MutableMap<Game, HashMap<UUID, SimpleGame>>>()

    fun searchGame(hook: InteractionHook, member: Member, gameTag: String, gameName: String) {
        hook.editOriginal(
            "\uD83C\uDFAE **|| ${gameName.uppercase()}**\n" +
                    "${member.asMention} ${msg("commandGameQueue", member.guild.idLong).replace("%GAME%", gameName)}"
        ).setComponents(
            ActionRow.of(
                Button.success("GAME_${gameTag}_ACCEPT_${member.id}", "Accept").withEmoji(Emoji.fromUnicode("✔️")),
                Button.danger("GAME_${gameTag}_CANCEL_${member.id}", "Cancel").withEmoji(Emoji.fromUnicode("✖️"))
            )
        ).queue()
    }

    fun requestGame(hook: InteractionHook, member: Member, opponent: Member, gameTag: String, gameName: String) {
        hook.editOriginal(
            "\uD83C\uDFAE **|| ${gameName.uppercase()}**\n" +
                    "${opponent.asMention} - ${msg("commandGameRequest", member.guild.idLong).replace("%GAME%", gameName).replace("%MEMBER%", member.asMention)}"
        ).setComponents(
            ActionRow.of(
                Button.success("GAME_${gameTag}_YES_${member.id}_${opponent.id}", "Accept").withEmoji(Emoji.fromUnicode("✔️")),
                Button.danger("GAME_${gameTag}_NO_${opponent.id}", "Deny").withEmoji(Emoji.fromUnicode("✖️"))
            )
        ).queue()
    }

    suspend fun newGameVersus(
        game: Game,
        guild: Guild,
        members: Pair<String, String>,
        channelID: Long,
        botLevel: Int = 0,
        daily: Boolean = false,
        seed: Long? = null
    ) {
        val gameMap = getGameMap(guild, game)
        val member1 = guild.retrieveMemberById(members.first).await() ?: return
        val member2 = guild.retrieveMemberById(members.second).await() ?: return
        val uuid = UUID.randomUUID()

        gameMap[uuid] = when (game) {
            Game.TIC_TAC_TOE -> TTTGame(member1, member2, uuid, channelID, guild, botLevel, daily, seed)
            Game.CONNECT_4 -> C4Game(member1, member2, uuid, guild, channelID, botLevel, daily, seed)
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
            val emptyGuild = Game.entries.associateWith { hashMapOf<UUID, SimpleGame>() }.toMutableMap()
            guilds[guild.idLong] = emptyGuild
            emptyGuild
        }
        return mGuild.getOrPut(game) { hashMapOf() }
    }

    fun cleanupOldInstances(maxAgeMillis: Long) {
        val now = System.currentTimeMillis()
        "---=---> GAME CLEANUP <---=---".log(Color.YELLOW)
        guilds.forEach { (guild, data) ->
            data.forEach { (type, games) ->
                val before = games.size
                games.entries.removeIf { (_, instance) -> now - instance.startedAt > maxAgeMillis }
                val removed = before - games.size
                if (removed > 0) " - Guild $guild removed $removed stale ${type.title} instance(s)".log(Color.YELLOW)
            }
        }
        "---=---=---=---=---=---=---=---".log(Color.YELLOW)
    }

    suspend fun shutdown() {
        "---=---> GAME MANAGER <---=---".log(Color.YELLOW)
        guilds.forEach { (guild, data) ->
            data.forEach { (type, games) ->
                games.toMap().forEach { (uuid, instance) ->
                    instance.setWinner(FieldsTwoPlayer.EMPTY)
                    removeGame(guild, type, uuid)
                }
            }
            " - Guild $guild offline".log(Color.YELLOW)
        }
        "---=---=---=---=---=---=---=---".log(Color.YELLOW)
    }
}
