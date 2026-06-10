package de.miraculixx.mgames.modules.trivia

import de.miraculixx.mgames.config.Ansi
import de.miraculixx.mgames.config.msgAnsi
import de.miraculixx.mgames.modules.games.GoalManager
import de.miraculixx.mgames.modules.games.utils.coinGrantFooter
import de.miraculixx.mgames.modules.games.utils.enums.Game
import de.miraculixx.mgames.utils.Colors
import de.miraculixx.mgames.utils.Icons
import de.miraculixx.mgames.utils.api.callCustomAPI
import de.miraculixx.mgames.utils.api.SQL
import de.miraculixx.mgames.utils.entities.SlashCommandEvent
import de.miraculixx.mgames.utils.extensions.awaitV2
import de.miraculixx.mgames.utils.extensions.enumOf
import de.miraculixx.mgames.utils.serializer.json
import dev.minn.jda.ktx.interactions.components.Container
import dev.minn.jda.ktx.interactions.components.TextDisplay
import dev.minn.jda.ktx.interactions.components.button
import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.components.MessageTopLevelComponent
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.buttons.ButtonStyle
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.apache.commons.text.StringEscapeUtils
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class TriviaCommand : SlashCommandEvent {
    override suspend fun trigger(it: SlashCommandInteractionEvent) {
        val subcommand = it.subcommandName
        val user = it.user
        val userID = user.id
        val guildID = it.guild?.idLong ?: return

        if (subcommand == "daily" && GoalManager.hasCompletedDaily(Game.TRIVIA, user.idLong)) {
            it.reply("```diff\n- Daily Trivia wurde heute bereits abgeschlossen.```").setEphemeral(true).queue()
            return
        }

        it.deferReply().queue()
        val game = if (subcommand == "daily") {
            TriviaMessage.createDailyQuestion(userID)
        } else {
            val difficulty = it.getOption("difficulty")?.asString ?: "RANDOM"
            val diffEnum = enumOf(difficulty) ?: TriviaDifficulty.RANDOM
            val category = it.getOption("category")?.asString ?: "RANDOM"
            val catEnum = enumOf(category) ?: TriviaCategory.RANDOM
            TriviaMessage.createQuestion(catEnum, diffEnum, userID)
        }

        it.hook.editOriginalComponents(TriviaMessage.render(game)).awaitV2()
        TriviaMessage.remember(game)
    }

    @Serializable
    data class TriviaOutput(val results: List<TriviaQuestion>)

    @Serializable
    data class DailyTrivia(val output: TriviaOutput, val answerOrder: List<Int>)

    @Serializable
    data class TriviaQuestion(
        val category: String,
        val type: String,
        val difficulty: String,
        val question: String,
        val correct_answer: String,
        val incorrect_answers: List<String>
    )
}

object TriviaMessage {
    private val activeGames = ConcurrentHashMap<String, TriviaGameData>()
    private val gameIDs = AtomicLong()

    data class TriviaGameData(
        val userID: String,
        val gameID: String,
        val requestedCategory: TriviaCategory,
        val requestedDifficulty: TriviaDifficulty,
        val actualCategory: TriviaCategory,
        val actualDifficulty: TriviaDifficulty,
        val question: String,
        val answers: List<TriviaAnswer>,
        val booleanQuestion: Boolean,
        val daily: Boolean,
        val result: TriviaResult? = null
    )

    data class TriviaAnswer(val id: Int, val label: String)

    data class TriviaResult(val selectedAnswerID: Int, val reward: Int)

    fun remember(game: TriviaGameData) {
        activeGames[game.userID] = game
    }

    fun get(userID: String): TriviaGameData? {
        return activeGames[userID]
    }

    suspend fun createDailyQuestion(userID: String): TriviaGameData {
        val daily = ensureDailyTriviaQuestion()
        return createGame(daily.output, TriviaCategory.RANDOM, TriviaDifficulty.MEDIUM, userID, daily = true, answerOrder = daily.answerOrder)
    }

    suspend fun createQuestion(category: TriviaCategory, difficulty: TriviaDifficulty, userID: String): TriviaGameData {
        return createGame(requestTrivia(category, difficulty), category, difficulty, userID, daily = false, answerOrder = null)
    }

    suspend fun createReplayQuestion(game: TriviaGameData, userID: String): TriviaGameData {
        return createQuestion(game.actualCategory, game.actualDifficulty, userID)
    }

    fun reveal(game: TriviaGameData, selectedAnswerID: Int, reward: Int): TriviaGameData {
        return game.copy(result = TriviaResult(selectedAnswerID, reward))
    }

    fun render(game: TriviaGameData): List<MessageTopLevelComponent> {
        val result = game.result
        val success = result?.selectedAnswerID == 1

        return listOf(
            Container(accentColor = when {
                result == null -> null
                success -> Colors.green
                else -> Colors.red
            }) {
                val title = if (game.daily) "## ${Icons.trivia}  || **Daily Trivia**" else "## ${Icons.trivia}  || **Trivia Quiz**"
                if (result == null) text(title)
                else {
                    section(Button.primary("TRIVIA:${game.userID}:${game.gameID}:REPLAY", Icons.replay), TextDisplay(title))
                }
                separator()
                text(buildDescription(game))

                components += ActionRow.of(
                    game.answers.map { answer ->
                        val button = if (game.booleanQuestion) {
                            val emoji = if (answer.id == 1) Emoji.fromFormatted(Icons.yes)
                            else Emoji.fromFormatted(Icons.no)
                            button("TRIVIA:${game.userID}:${game.gameID}:${if (game.daily) "DAILY" else "PLAY"}:${answer.id}", answer.label, emoji)
                        } else {
                            button("TRIVIA:${game.userID}:${game.gameID}:${if (game.daily) "DAILY" else "PLAY"}:${answer.id}", answer.label)
                        }

                        when {
                            result == null -> button
                            answer.id == result.selectedAnswerID -> button.asDisabled()
                                .withStyle(if (success) ButtonStyle.SUCCESS else ButtonStyle.DANGER)
                            !success && answer.id == 1 -> button.asDisabled().withStyle(ButtonStyle.PRIMARY)
                            else -> button.asDisabled()
                        }
                    }
                )

                game.result?.reward?.let {
                    text("-# Von <@${game.userID}>${coinGrantFooter(it)}")
                }
            }
        )
    }

    suspend fun ensureDailyTriviaQuestion(): TriviaCommand.DailyTrivia {
        val date = GoalManager.currentDailyDate().toString()
        val existing = SQL.getDailyTrivia(date)
        if (existing != null) return json.decodeFromString<TriviaCommand.DailyTrivia>(existing)

        val output = requestTrivia(TriviaCategory.RANDOM, TriviaDifficulty.MEDIUM)
        val question = output.results.first()
        val answers = if (question.type == "multiple") 4 else 2
        val daily = TriviaCommand.DailyTrivia(output, (1..answers).toList().shuffled())
        SQL.setDailyTrivia(date, json.encodeToString(daily))
        return daily
    }

    private suspend fun requestTrivia(category: TriviaCategory, difficulty: TriviaDifficulty): TriviaCommand.TriviaOutput {
        val url = buildString {
            append("https://opentdb.com/api.php?amount=1")
            if (category != TriviaCategory.RANDOM) append("&category=${category.id}")
            if (difficulty != TriviaDifficulty.RANDOM) append("&difficulty=${difficulty.name.lowercase()}")
        }
        val response = callCustomAPI(url)
        return json.decodeFromString<TriviaCommand.TriviaOutput>(response)
    }

    private fun createGame(
        output: TriviaCommand.TriviaOutput,
        category: TriviaCategory,
        difficulty: TriviaDifficulty,
        userID: String,
        daily: Boolean,
        answerOrder: List<Int>?
    ): TriviaGameData {
        val trivia = output.results.first()
        val answerMap = buildMap {
            put(1, StringEscapeUtils.unescapeHtml4(trivia.correct_answer))
            trivia.incorrect_answers.forEachIndexed { index, answer -> put(index + 2, StringEscapeUtils.unescapeHtml4(answer)) }
        }
        val order = answerOrder ?: answerMap.keys.shuffled()
        return TriviaGameData(
            userID = userID,
            gameID = gameIDs.incrementAndGet().toString(36),
            requestedCategory = category,
            requestedDifficulty = difficulty,
            actualCategory = TriviaCategory.getByTitle(trivia.category.replace(":", " -")),
            actualDifficulty = enumOf<TriviaDifficulty>(trivia.difficulty.uppercase()) ?: difficulty,
            question = StringEscapeUtils.unescapeHtml4(trivia.question),
            answers = order.map { answerID -> TriviaAnswer(answerID, answerMap.getValue(answerID)) },
            booleanQuestion = trivia.type == "boolean",
            daily = daily
        )
    }

    private fun buildDescription(game: TriviaGameData): String {
        val result = game.result
        val color = when (result?.selectedAnswerID) {
            1 -> Ansi.textGreen + Ansi.bold + "✓ " + Ansi.reset + Ansi.textGreen
            null -> Ansi.textWhite
            else -> Ansi.textRed + Ansi.bold + "✗ " + Ansi.reset + Ansi.textRed
        }

        return "> Difficulty >> ``${game.actualDifficulty.title}${if (game.requestedDifficulty == TriviaDifficulty.RANDOM) " (random)" else ""}``\n" +
                "> Category >> ``${game.actualCategory.title}${if (game.requestedCategory == TriviaCategory.RANDOM) " (random)" else ""}``\n" +
                "\n${msgAnsi("$color${game.question}")}"
    }
}

suspend fun ensureDailyTriviaQuestion(): TriviaCommand.DailyTrivia {
    return TriviaMessage.ensureDailyTriviaQuestion()
}
