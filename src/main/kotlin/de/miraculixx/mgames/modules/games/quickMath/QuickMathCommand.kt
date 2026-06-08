package de.miraculixx.mgames.modules.games.quickMath

import de.miraculixx.mgames.modules.games.GoalManager
import de.miraculixx.mgames.modules.games.utils.enums.Game
import de.miraculixx.mgames.modules.games.utils.enums.GameMode
import de.miraculixx.mgames.utils.entities.ButtonEvent
import de.miraculixx.mgames.utils.entities.ModalEvent
import de.miraculixx.mgames.utils.entities.SlashCommandEvent
import dev.minn.jda.ktx.messages.Embed
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.callbacks.IModalCallback
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.buttons.ButtonStyle
import net.dv8tion.jda.api.components.label.Label
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.modals.Modal
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.random.Random

object QuickMathCommand : SlashCommandEvent, ModalEvent, ButtonEvent {
    private const val PLAY_BUTTON_ID = "22142abbf1c74da187fdabd4b59d4456"
    private const val ANSWER_INPUT_ID = "ANSWER"
    private const val MODAL_PREFIX = "QUICK-MATH"
    private val challenges = ConcurrentHashMap<String, MathChallenge>()
    private val invisibleChars = listOf("\u200B", "\u200C", "\u200D", "\u2060")

    override suspend fun trigger(it: SlashCommandInteractionEvent) {
        it.openChallenge(it.user.idLong, daily = it.subcommandName == "daily")
    }

    override suspend fun trigger(it: ButtonInteractionEvent) {
        it.openChallenge(it.user.idLong, daily = false)
    }

    private suspend fun IModalCallback.openChallenge(userId: Long, daily: Boolean) {
        val startedAt = System.currentTimeMillis()
        val modalId = "$MODAL_PREFIX:$startedAt:$userId:${Random.nextInt(100_000, 1_000_000)}:$daily"
        val challenge = if (daily) createChallenge(Random(GoalManager.getDailySeed())) else createChallenge(Random.Default)
        challenges[modalId] = challenge

        val answer = TextInput.create(ANSWER_INPUT_ID, TextInputStyle.SHORT)
            .setPlaceholder("Antwort als ganze Zahl")
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(20)
            .build()

        replyModal(
            Modal.create(modalId, "Quick Math")
                .addComponents(Label.of(challenge.hiddenQuestion, answer))
                .build()
        ).queue()
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

        val elapsedMs = System.currentTimeMillis() - startedAt
        val elapsed = "%.2f".format(elapsedMs / 1000.0)
        val success = answer == challenge.result
        val guildId = it.guild?.idLong ?: return
        GoalManager.registerGameResult(
            Game.QUICK_MATH,
            GameMode.SOLO,
            winnerSnowflake = if (success) targetUserId else null,
            loserSnowflake = if (success) null else targetUserId,
            guildSnowflake = guildId
        )
        val dailyResult = if (success && daily) {
            GoalManager.registerDailyCompletion(Game.QUICK_MATH, targetUserId, guildId, 1)
        } else null

        it.replyEmbeds(buildResultEmbed(challenge, answerRaw, elapsed, success, targetUserId, dailyResult?.reward ?: 0, dailyResult?.streak))
            .addComponents(ActionRow.of(Button.of(ButtonStyle.SUCCESS, PLAY_BUTTON_ID, "PLAY")))
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
    ) = Embed {
        title = "\uD83C\uDFB2 Quick Math"
        color = if (success) 0x2ECC71 else 0xE74C3C

        val result = challenge.result.toString()
        val answerLine = if (success) {
            "- Richtige Antwort  >> `$result`\n- Zeit                           >> `${elapsed}s`" +
                when {
                    reward > 0 -> "\n- Daily Reward          >> `$reward Coins`\n- Daily Streak            >> `$streak`"
                    streak != null -> "\n- Daily Reward          >> `already claimed`\n- Daily Streak            >> `$streak`"
                    else -> ""
                }
        } else {
            "- Falsche Antwort  >> `${submitted.ifBlank { "<leer>" }}` (`$result`)"
        }

        description = "- Challenge                  >> `${challenge.cleanQuestion}`\n" +
                "$answerLine\n" +
                "-# Beantwortet von <@$user>"
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
