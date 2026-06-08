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
    private const val PLAY_BUTTON_ID = "QUICK-MATH"
    private const val ANSWER_INPUT_ID = "ANSWER"
    private const val MODAL_PREFIX = "QUICK-MATH"
    private val challenges = ConcurrentHashMap<String, MathChallenge>()
    private val invisibleChars = listOf("\u200B", "\u200C", "\u200D", "\u2060")

    override suspend fun trigger(it: SlashCommandInteractionEvent) {
        val daily = it.subcommandName == "daily"
        val guildId = it.guild?.idLong
        val difficulty = if (daily) MathDifficulty.MEDIUM else it.getOption("difficulty")?.asString.toMathDifficulty()
        if (daily && guildId != null && GoalManager.hasCompletedDaily(Game.QUICK_MATH, it.user.idLong, guildId)) {
            it.reply("```diff\n- Daily Quick Math wurde heute bereits abgeschlossen.```").setEphemeral(true).queue()
            return
        }
        it.openChallenge(it.user.idLong, daily, difficulty)
    }

    override suspend fun trigger(it: ButtonInteractionEvent) {
        val id = it.componentId
        val difficulty = id.substringAfter("$PLAY_BUTTON_ID:").toMathDifficulty()
        it.openChallenge(it.user.idLong, daily = false, difficulty = difficulty)
    }

    private suspend fun IModalCallback.openChallenge(userId: Long, daily: Boolean, difficulty: MathDifficulty) {
        val startedAt = System.currentTimeMillis()
        val modalId = "$MODAL_PREFIX:$startedAt:$userId:${Random.nextInt(100_000, 1_000_000)}:$daily:${difficulty.name}"
        val challenge = if (daily) createChallenge(Random(GoalManager.getDailySeed() + difficulty.seedOffset), difficulty)
        else createChallenge(Random.Default, difficulty)
        challenges[modalId] = challenge

        replyModal(Modal(modalId, "Quick Math - ${difficulty.title}") {
            label(challenge.hiddenQuestion) {
                child = TextInput(ANSWER_INPUT_ID, TextInputStyle.SHORT, requiredLength = 1..20, placeholder = "Antwort als ganze Zahl")
            }
            components += TextDisplay(difficulty.helpText)
        }).queue()
    }

    override suspend fun trigger(it: ModalInteractionEvent) {
        val parts = it.modalId.split(":")
        val startedAt = parts.getOrNull(1)?.toLongOrNull()
        val targetUserId = parts.getOrNull(2)?.toLongOrNull()
        val daily = parts.getOrNull(4)?.toBooleanStrictOrNull() ?: false
        val difficulty = parts.getOrNull(5).toMathDifficulty()
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
            GoalManager.registerDailyCompletion(Game.QUICK_MATH, targetUserId, guildId, difficulty.multiplier)
        } else null
        val coins = if (success && (!daily || dailyResult?.completed == true)) {
            GoalManager.registerGameResult(
                Game.QUICK_MATH,
                GameMode.SOLO,
                winnerSnowflake = targetUserId,
                loserSnowflake = null,
                guildSnowflake = guildId,
                difficultyMultiplier = difficulty.multiplier
            ) + (dailyResult?.reward ?: 0)
        } else if (!daily && !success) {
            GoalManager.registerGameResult(
                Game.QUICK_MATH,
                GameMode.SOLO,
                winnerSnowflake = null,
                loserSnowflake = targetUserId,
                guildSnowflake = guildId,
                difficultyMultiplier = difficulty.multiplier
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
            if (success) "- Daily Challenge Geschafft!"
            else "- Daily Challenge Falsch :/"
        } else {
            if (success) "- Richtig  >> `$result`\n- Zeit >> `${elapsed}s`"
            else "- Falsch  >> `${submitted.ifBlank { "<leer>" }}` (`$result`)"
        }

        return listOf(
            Container.of(
                Section.of(
                    Button.of(ButtonStyle.SUCCESS, "${PLAY_BUTTON_ID}:${challenge.difficulty}", "𝗣𝗟𝗔𝗬"),
                    TextDisplay.of("## 🎲 Quick Math")
                ),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(
                    if (streak == null) "- Challenge >> `${challenge.cleanQuestion}` (${challenge.difficulty.title})" else {""} +
                        "\n$answerLine" +
                        if (streak != null && success) "\n- Streak >> `${streak}`" else {""} +
                        "\n-# Von <@$user>${coinGrantFooter(reward)}"
                )
            ).withAccentColor(if (success) 0x2ECC71 else 0xE74C3C)
        )
    }

    private fun createChallenge(random: Random, difficulty: MathDifficulty): MathChallenge {
        repeat(200) {
            val expression = randomExpression(random, difficulty, difficulty.operationCount, topLevel = true)
            val question = expression.render()
            val hiddenQuestion = question.hideSpaces(random)
            if (question.length <= 34 && hiddenQuestion.length <= 45 && abs(expression.value) <= 100_000) {
                return MathChallenge(question, hiddenQuestion, expression.value, difficulty)
            }
        }

        val fallback = fallbackExpression(difficulty)
        val question = fallback.render()
        return MathChallenge(question, question.hideSpaces(random), fallback.value, difficulty)
    }

    private fun randomExpression(random: Random, difficulty: MathDifficulty, operations: Int, topLevel: Boolean = false): Expr {
        if (operations <= 0) return Const(difficulty.randomNumber(random).toLong())

        val op = randomOperator(random, difficulty)
        val grouped = !topLevel && random.nextBoolean()
        val leftOperations = random.nextInt(0, operations)
        val rightOperations = operations - 1 - leftOperations
        val left = randomExpression(random, difficulty, leftOperations)
        var right = randomExpression(random, difficulty, rightOperations)

        if (op == Operator.MODULO) {
            while (right.value == 0L) right = randomExpression(random, difficulty, rightOperations)
        }

        return Binary(left, op, right, grouped)
    }

    private fun randomOperator(random: Random, difficulty: MathDifficulty): Operator {
        val operators = if (difficulty.allowModulo) Operator.entries else Operator.entries.filter { it != Operator.MODULO }
        return operators[random.nextInt(operators.size)]
    }

    private fun String?.toMathDifficulty(): MathDifficulty {
        return MathDifficulty.entries.firstOrNull { difficulty ->
            equals(difficulty.name, ignoreCase = true) || equals(difficulty.title, ignoreCase = true)
        } ?: MathDifficulty.entries.random()
    }

    private fun fallbackExpression(difficulty: MathDifficulty): Expr {
        return when (difficulty) {
            MathDifficulty.EASY -> Binary(Const(12), Operator.ADD, Binary(Const(3), Operator.MULTIPLY, Const(2), grouped = false), grouped = false)
            MathDifficulty.MEDIUM -> Binary(
                Binary(Const(17), Operator.SUBTRACT, Const(-12), grouped = true),
                Operator.MULTIPLY,
                Binary(Const(8), Operator.ADD, Const(19), grouped = false),
                grouped = false
            )
            MathDifficulty.HARD -> Binary(
                Binary(Const(24), Operator.MODULO, Const(7), grouped = true),
                Operator.MULTIPLY,
                Binary(Const(-11), Operator.ADD, Const(5), grouped = false),
                grouped = false
            )
        }
    }

    enum class MathDifficulty(
        val title: String,
        val multiplier: Int,
        val operationCount: Int,
        private val minNumber: Int,
        private val maxNumber: Int,
        val allowModulo: Boolean,
        val seedOffset: Long
    ) {
        EASY("Easy", 1, 2, 1, 15, false, 10_000L),
        MEDIUM("Medium", 2, 3, -20, 20, false, 20_000L),
        HARD("Hard", 3, 3, -30, 30, true, 30_000L);

        val helpText: String
            get() = buildString {
                append("__Spickzettel__\n")
                append("- *")
                if (allowModulo) append(" %")
                append(" vor + - (Punkt vor Strich)\n")
                if (allowModulo) append("- % ist Rest einer Division (modulo)\n")
                append("- Nutze das Textfeld zum Zwischenspeichern")
            }

        fun randomNumber(random: Random): Int {
            return random.nextInt(minNumber, maxNumber + 1)
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
        val result: Long,
        val difficulty: MathDifficulty
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
