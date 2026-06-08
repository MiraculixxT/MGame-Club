package de.miraculixx.mgames.modules.games.quickMath

import de.miraculixx.mgames.modules.games.GoalManager
import de.miraculixx.mgames.modules.games.utils.coinGrantFooter
import de.miraculixx.mgames.modules.games.utils.enums.Game
import de.miraculixx.mgames.modules.games.utils.enums.GameMode
import de.miraculixx.mgames.utils.entities.ButtonEvent
import de.miraculixx.mgames.utils.entities.ModalEvent
import de.miraculixx.mgames.utils.entities.SlashCommandEvent
import dev.minn.jda.ktx.interactions.components.Modal
import dev.minn.jda.ktx.interactions.components.TextDisplay
import dev.minn.jda.ktx.interactions.components.TextInput
import net.dv8tion.jda.api.components.MessageTopLevelComponent
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.callbacks.IModalCallback
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.buttons.ButtonStyle
import net.dv8tion.jda.api.components.container.Container
import net.dv8tion.jda.api.components.section.Section
import net.dv8tion.jda.api.components.separator.Separator
import net.dv8tion.jda.api.components.textdisplay.TextDisplay
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.random.Random

object QuickMath : SlashCommandEvent, ModalEvent, ButtonEvent {
    private const val PLAY_BUTTON_ID = "22142abbf1c74da187fdabd4b59d4456"
    private const val ANSWER_INPUT_ID = "ANSWER"
    private const val MODAL_PREFIX = "QUICK-MATH"
    private val challenges = ConcurrentHashMap<String, MathChallenge>()
    private val invisibleChars = listOf("\u200B", "\u200C", "\u200D", "\u2060")

    override suspend fun trigger(it: SlashCommandInteractionEvent) {
        val daily = it.subcommandName == "daily"
        val guildId = it.guild?.idLong
        if (daily && guildId != null && GoalManager.hasCompletedDaily(Game.QUICK_MATH, it.user.idLong, guildId)) {
            it.reply("```diff\n- Daily Quick Math wurde heute bereits abgeschlossen.```").setEphemeral(true).queue()
            return
        }
        it.openChallenge(it.user.idLong, daily)
    }

    override suspend fun trigger(it: ButtonInteractionEvent) {
        it.openChallenge(it.user.idLong, daily = false)
    }

    private suspend fun IModalCallback.openChallenge(userId: Long, daily: Boolean) {
        val startedAt = System.currentTimeMillis()
        val modalId = "$MODAL_PREFIX:$startedAt:$userId:${Random.nextInt(100_000, 1_000_000)}:$daily"
        val challenge = if (daily) createChallenge(Random(GoalManager.getDailySeed())) else createChallenge(Random.Default)
        challenges[modalId] = challenge

        replyModal(Modal(modalId, "Quick Math") {
            label(challenge.hiddenQuestion) {
                child = TextInput(ANSWER_INPUT_ID, TextInputStyle.SHORT, requiredLength = 1..20, placeholder = "Antwort als ganze Zahl")
            }
            components += TextDisplay("__Spickzettel__\n- * % vor + - (Punkt vor Strich)\n- % ist Rest einer Division (modulo)\n- Nutze das Textfeld zum Zwischenspeichern")
        }).queue()
    }

    override suspend fun trigger(it: ModalInteractionEvent) {
        val parts = it.modalId.split(":")
        val startedAt = parts.getOrNull(1)?.toLongOrNull()
        val targetUserId = parts.getOrNull(2)?.toLongOrNull()
        val daily = parts.getOrNull(4)?.toBooleanStrictOrNull() ?: false
        val answerRaw = it.getValue(ANSWER_INPUT_ID)?.asString?.trim().orEmpty()
        val answer = answerRaw.toLongOrNull()

        if (startedAt == null || targetUserId == null) {
            it.reply("Dieses Quick-Math Rätsel ist ungültig. Bitte starte `/quick-math` erneut.").queue()
            return
        }

        if (targetUserId != it.user.idLong) {
            it.reply("Dieses Quick-Math Rätsel gehört nicht dir.").setEphemeral(true).queue()
            return
        }

        val challenge = challenges.remove(it.modalId)
        if (challenge == null) {
            it.reply("Dieses Quick-Math Rätsel ist abgelaufen. Bitte starte `/quick-math` erneut.").queue()
            return
        }
        val guildId = it.guild?.idLong ?: return
        if (daily && GoalManager.hasCompletedDaily(Game.QUICK_MATH, targetUserId, guildId)) {
            it.reply("```diff\n- Daily Quick Math wurde heute bereits abgeschlossen.```").setEphemeral(true).queue()
            return
        }
        it.deferReply().queue()

        val elapsedMs = System.currentTimeMillis() - startedAt
        val elapsed = "%.2f".format(elapsedMs / 1000.0)
        val success = answer == challenge.result
        val dailyResult = if (success && daily) {
            GoalManager.registerDailyCompletion(Game.QUICK_MATH, targetUserId, guildId, 1)
        } else null
        val coins = if (success && (!daily || dailyResult?.completed == true)) {
            GoalManager.registerGameResult(
                Game.QUICK_MATH,
                GameMode.SOLO,
                winnerSnowflake = targetUserId,
                loserSnowflake = null,
                guildSnowflake = guildId
            ) + (dailyResult?.reward ?: 0)
        } else if (!daily && !success) {
            GoalManager.registerGameResult(
                Game.QUICK_MATH,
                GameMode.SOLO,
                winnerSnowflake = null,
                loserSnowflake = targetUserId,
                guildSnowflake = guildId
            )
            0
        } else 0
        val streak = if (daily) dailyResult?.streak ?: -1 else null

        it.hook.editOriginalComponents(buildResultEmbed(challenge, answerRaw, elapsed, success, targetUserId, coins, streak))
            .useComponentsV2()
            .queue()
    }

    private fun buildResultEmbed(
        challenge: MathChallenge,
        submitted: String,
        elapsed: String,
        success: Boolean,
        user: Long,
        reward: Int,
        streak: Int?
    ): List<MessageTopLevelComponent> {
        val result = challenge.result.toString()
        val answerLine = if (streak != null) {
            if (success) "- Richtig Beantwortet!"
            else "- Falsch Beantwortet :/"
        } else {
            if (success) "- Richtig  >> `$result`\n- Zeit                           >> `${elapsed}s`"
            else "- Falsch  >> `${submitted.ifBlank { "<leer>" }}` (`$result`)"
        }

        return listOf(
            Container.of(
                Section.of(
                    Button.of(ButtonStyle.SUCCESS, PLAY_BUTTON_ID, "𝗣𝗟𝗔𝗬"),
                    TextDisplay.of("## 🎲 Quick Math")
                ),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(
                    if (streak == null) "- Challenge >> `${challenge.cleanQuestion}`" else {"- Daily Challenge"} +
                        "\n$answerLine" +
                        if (streak != null && streak > 0) "\n- Streak >> `${streak}`" else {""} +
                        "\n-# Von <@$user>${coinGrantFooter(reward)}"
                )
            ).withAccentColor(if (success) 0x2ECC71 else 0xE74C3C)
        )
    }

    private fun createChallenge(random: Random): MathChallenge {
        repeat(50) {
            val expression = randomExpression(random, depth = random.nextInt(2, 4), topLevel = true)
            val question = expression.render()
            val hiddenQuestion = question.hideSpaces(random)
            if (question.length <= 34 && hiddenQuestion.length <= 45 && abs(expression.value) <= 100_000) {
                return MathChallenge(question, hiddenQuestion, expression.value)
            }
        }

        val fallback = Binary(Const(38), Operator.ADD, Binary(Const(-8), Operator.MULTIPLY, Const(3), grouped = false), grouped = false)
        val question = fallback.render()
        return MathChallenge(question, question.hideSpaces(random), fallback.value)
    }

    private fun randomExpression(random: Random, depth: Int, topLevel: Boolean = false): Expr {
        if (depth <= 0) return Const(random.nextInt(-12, 51).let { if (it == 0) 1 else it }.toLong())

        val op = randomOperator(random)
        val grouped = !topLevel && random.nextBoolean()
        val left = randomExpression(random, depth - 1)
        var right = randomExpression(random, depth - 1)

        if (op == Operator.MODULO) {
            while (right.value == 0L) right = randomExpression(random, depth - 1)
        }

        return Binary(left, op, right, grouped)
    }

    private fun randomOperator(random: Random): Operator {
        return when (random.nextInt(100)) {
            in 0..6 -> Operator.MODULO
            in 7..29 -> Operator.MULTIPLY
            in 30..61 -> Operator.SUBTRACT
            else -> Operator.ADD
        }
    }

    private fun String.hideSpaces(random: Random): String {
        return Regex(" ").replace(this) {
            " " + invisibleChars.shuffled(random).take(random.nextInt(1, 4)).joinToString("")
        }
    }

    private data class MathChallenge(
        val cleanQuestion: String,
        val hiddenQuestion: String,
        val result: Long
    )

    private sealed interface Expr {
        val value: Long
        val precedence: Int
        fun render(parentPrecedence: Int = 0): String
    }

    private data class Const(override val value: Long) : Expr {
        override val precedence = 3
        override fun render(parentPrecedence: Int): String = value.toString()
    }

    private data class Binary(
        val left: Expr,
        val operator: Operator,
        val right: Expr,
        val grouped: Boolean
    ) : Expr {
        override val value: Long = when (operator) {
            Operator.ADD -> left.value + right.value
            Operator.SUBTRACT -> left.value - right.value
            Operator.MULTIPLY -> left.value * right.value
            Operator.MODULO -> left.value % right.value
        }
        override val precedence = operator.precedence

        override fun render(parentPrecedence: Int): String {
            val leftRendered = left.render(precedence)
            val rightRendered = right.render(precedence + 1)
            val rendered = "$leftRendered ${operator.symbol} $rightRendered"
            return if (grouped || precedence < parentPrecedence) "($rendered)" else rendered
        }
    }

    private enum class Operator(val symbol: String, val precedence: Int) {
        ADD("+", 1),
        SUBTRACT("-", 1),
        MULTIPLY("*", 2),
        MODULO("%", 2)
    }
}
