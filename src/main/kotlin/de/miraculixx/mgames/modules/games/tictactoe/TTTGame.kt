package de.miraculixx.mgames.modules.games.tictactoe

import de.miraculixx.mgames.config.msg
import de.miraculixx.mgames.config.msgDiff
import de.miraculixx.mgames.modules.games.GameManager
import de.miraculixx.mgames.modules.games.GoalManager
import de.miraculixx.mgames.modules.games.utils.FieldsTwoPlayer
import de.miraculixx.mgames.modules.games.utils.SimpleGame
import de.miraculixx.mgames.modules.games.utils.enums.Game
import de.miraculixx.mgames.modules.games.utils.enums.GameMode
import de.miraculixx.mgames.utils.Colors
import de.miraculixx.mgames.utils.Icons
import de.miraculixx.mgames.utils.extensions.awaitV2
import de.miraculixx.mgames.utils.extensions.queueV2
import de.miraculixx.mgames.utils.log
import dev.minn.jda.ktx.events.getDefaultScope
import dev.minn.jda.ktx.interactions.components.Container
import dev.minn.jda.ktx.interactions.components.button
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.components.MessageTopLevelComponent
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.buttons.ButtonStyle
import java.util.*
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class TTTGame(
    private val member1: Member, private val member2: Member,
    private val uuid: UUID,
    channelID: Long,
    guild: Guild,
    botLevel: Int,
    private val daily: Boolean = false,
    seed: Long? = null
) : SimpleGame {
    override val startedAt: Long = System.currentTimeMillis()

    // Who is playing the next step
    // True - P1 (red) || False - P2 (green)
    private val bot: TTTBot?
    private var guildID: Long
    private val random = seed?.let { Random(it) } ?: Random.Default
    private val difficultyMultiplier = botLevel.coerceIn(1, 3)
    private var whoPlays = random.nextBoolean()
    private var winner: FieldsTwoPlayer? = null
    private lateinit var message: Message
    private val fields = Array(3) {
        (1..3).map { FieldsTwoPlayer.EMPTY }.toTypedArray()
    }

    private fun calcButtons(): List<ActionRow> {
        val rows = ArrayList<ActionRow>()
        val blancEmote = Emoji.fromFormatted("<:blanc:784059217890770964>")
        val xEmote = Emoji.fromFormatted("<:xx:988156472020066324>")
        val oEmote = Emoji.fromFormatted("<:oo:988156473274163200>")

        var rowI = 0
        fields.forEach { row ->
            var columnI = 0
            val list = ArrayList<Button>()
            row.forEach { field ->
                val button = when (field) {
                    FieldsTwoPlayer.EMPTY -> if (winner == null) Button.secondary("GAME_TTT_P_${uuid}_${rowI}_$columnI", blancEmote)
                    else Button.secondary("GAME_TTT_$rowI-$columnI", blancEmote).asDisabled()
                    FieldsTwoPlayer.PLAYER_1 -> Button.danger("GAME_TTT_${rowI}_$columnI", xEmote).asDisabled()
                    FieldsTwoPlayer.PLAYER_2 -> Button.success("GAME_TTT_${rowI}_$columnI", oEmote).asDisabled()
                }
                list.add(button)
                columnI++
            }
            rows.add(ActionRow.of(list))
            rowI++
        }
        return rows
    }

    private fun calcEmbed(buttons: List<ActionRow>): List<MessageTopLevelComponent> {
        return listOf(
            Container {
                text("## ${Icons.tictactoe} || TIC TAC TOE")
                separator()
                text("${Icons.x} - Red ${member1.asMention}\n" +
                        "${Icons.o} - Green " +
                        if (daily) "`Daily Challenge`"
                        else if (bot != null) "`Bot Level ${bot.level}`"
                        else member2.asMention)
                separator()
                val message = when (winner) {
                    FieldsTwoPlayer.EMPTY -> "Draw"
                    FieldsTwoPlayer.PLAYER_1 -> "${member1.asMention} ${msg("win", guildID)}"
                    FieldsTwoPlayer.PLAYER_2 -> "${member2.asMention} ${msg("win", guildID)}"
                    null -> {
                        if (whoPlays) {
                            accentColorRaw = Colors.buttonRed
                            "${member1.asMention} ${msg("onMove", guildID)}"
                        } else {
                            accentColorRaw = Colors.buttonGreen
                            " ${member2.asMention} ${msg("onMove", guildID)}"
                        }
                    }
                }
                if (winner != null) {
                    accentColorRaw = null
                    section {
                        accessory = button("GAME_TTT_R_${member1.id}_${member2.id}_${bot?.level ?: 0}", Icons.play, style = ButtonStyle.PRIMARY)
                        text("${Icons.goalFlag} $message")
                    }
                } else text("> $message")
                components += buttons
            }
        )
    }

    private suspend fun checkWin() {
        winner = getWinner() ?: return
        when (winner ?: return) {
            FieldsTwoPlayer.EMPTY -> if (!daily) {
                GoalManager.registerGameHistory(
                    Game.TIC_TAC_TOE,
                    if (bot != null) listOf(member1.idLong) else listOf(member1.idLong, member2.idLong)
                )
            }
            FieldsTwoPlayer.PLAYER_1 -> {
                val dailyResult = if (daily) {
                    GoalManager.registerDailyCompletion(Game.TIC_TAC_TOE, member1.idLong, guildID, difficultyMultiplier)
                } else null
                if (!daily || dailyResult?.completed == true) {
                    GoalManager.registerGameResult(
                        Game.TIC_TAC_TOE,
                        if (bot != null) GameMode.BOT else GameMode.USER,
                        winnerSnowflake = member1.idLong,
                        loserSnowflake = if (bot == null) member2.idLong else null,
                        guildSnowflake = guildID,
                        difficultyMultiplier = difficultyMultiplier
                    )
                }
            }
            FieldsTwoPlayer.PLAYER_2 -> {
                if (bot == null) {
                    GoalManager.registerGameResult(
                        Game.TIC_TAC_TOE,
                        GameMode.USER,
                        winnerSnowflake = member2.idLong,
                        loserSnowflake = member1.idLong,
                        guildSnowflake = guildID,
                        difficultyMultiplier = difficultyMultiplier
                    )
                } else if (!daily) {
                    GoalManager.registerGameResult(
                        Game.TIC_TAC_TOE,
                        GameMode.BOT,
                        winnerSnowflake = null,
                        loserSnowflake = member1.idLong,
                        guildSnowflake = guildID,
                        difficultyMultiplier = difficultyMultiplier
                    )
                }
            }
        }
        GameManager.removeGame(guildID, Game.TIC_TAC_TOE, uuid)
    }

    private fun getWinner(): FieldsTwoPlayer? {
        //Check rows
        repeat(3) { row ->
            val s = fields[row][0]
            if (s != FieldsTwoPlayer.EMPTY && s == fields[row][1] && s == fields[row][2])
                return s
        }

        //Check columns
        repeat(3) { col ->
            val s = fields[0][col]
            if (s != FieldsTwoPlayer.EMPTY && s == fields[1][col] && s == fields[2][col])
                return s
        }

        //Check diagonals
        val s = fields[1][1] //middle piece
        if (s != FieldsTwoPlayer.EMPTY) {
            if (s == fields[0][0] && s == fields[2][2])
                return s
            if (s == fields[2][0] && s == fields[0][2])
                return s
        }

        //Check if tie
        fields.forEach { row ->
            if (row.contains(FieldsTwoPlayer.EMPTY))
                return null //No tie
        }
        //If code reaches here - TIE
        return FieldsTwoPlayer.EMPTY
    }

    override suspend fun interact(options: List<String>, interactor: Member, event: GenericComponentInteractionCreateEvent?) {
        val memberID = interactor.idLong
        if (memberID != member1.idLong && memberID != member2.idLong) {
            event?.reply(msgDiff(msg("notPartOfMatch", guildID, mapOf("GAME" to "tictactoe"))))?.setEphemeral(true)?.queue()
            return
        }
        val row = options[0].toInt()
        val column = options[1].toInt()
        if (memberID == member1.idLong) {
            if (whoPlays) {
                fields[row][column] = FieldsTwoPlayer.PLAYER_1
                whoPlays = false
            } else {
                event?.reply(msgDiff(msg("notYourMove", guildID)))?.setEphemeral(true)?.queue()
                return
            }
        } else {
            if (!whoPlays) {
                fields[row][column] = FieldsTwoPlayer.PLAYER_2
                whoPlays = true
            } else {
                event?.reply(msgDiff(msg("notYourMove", guildID)))?.setEphemeral(true)?.queue()
                return
            }
        }
        event?.deferEdit()?.queue()
        checkWin()
        val buttons = calcButtons()
        message.editMessageComponents(calcEmbed(buttons)).awaitV2()
        if (winner == null && bot != null && !whoPlays) botMove()
    }

    private suspend fun botMove() {
        delay(1.seconds)
        val pos = bot?.getMove(fields)!!
        fields[pos.first][pos.second] = FieldsTwoPlayer.PLAYER_2
        interact(listOf(pos.first.toString(), pos.second.toString()), member2, null)
    }

    override suspend fun setWinner(win: FieldsTwoPlayer) {
        winner = win
        message.editMessageComponents(calcEmbed(calcButtons())).queueV2()
    }

    init {
        bot = if (member2.user.isBot) {
            "GAME > Start TTT Bot Game".log()
            TTTBot(botLevel, FieldsTwoPlayer.PLAYER_2, random)
        } else null
        guildID = guild.idLong

        getDefaultScope().launch {
            val channel = guild.getTextChannelById(channelID)!!
            message = channel.sendMessageComponents(calcEmbed(calcButtons())).awaitV2()
            if (bot != null && !whoPlays)
                botMove()
        }
    }
}
