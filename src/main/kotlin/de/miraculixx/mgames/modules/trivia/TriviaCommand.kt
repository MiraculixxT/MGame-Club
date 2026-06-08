package de.miraculixx.mgames.modules.trivia

import de.miraculixx.mgames.modules.games.GoalManager
import de.miraculixx.mgames.modules.games.utils.enums.Game
import de.miraculixx.mgames.utils.api.callCustomAPI
import de.miraculixx.mgames.utils.api.SQL
import de.miraculixx.mgames.utils.entities.SlashCommandEvent
import de.miraculixx.mgames.utils.extensions.enumOf
import de.miraculixx.mgames.utils.serializer.json
import dev.minn.jda.ktx.interactions.components.button
import dev.minn.jda.ktx.messages.Embed
import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.apache.commons.text.StringEscapeUtils

class TriviaCommand : SlashCommandEvent {
    override suspend fun trigger(it: SlashCommandInteractionEvent) {
        val subcommand = it.subcommandName
        val user = it.user
        val userID = user.id
        val guildID = it.guild?.idLong ?: return

        if (subcommand == "daily" && GoalManager.hasCompletedDaily(Game.TRIVIA, user.idLong, guildID)) {
            it.reply("```diff\n- Daily Trivia wurde heute bereits abgeschlossen.```").setEphemeral(true).queue()
            return
        }

        it.deferReply().queue()
        val gen = if (subcommand == "daily") {
            generateDailyQuestion(userID)
        } else {
            val difficulty = it.getOption("difficulty")?.asString ?: "RANDOM"
            val diffEnum = enumOf(difficulty) ?: TriviaDifficulty.RANDOM
            val category = it.getOption("category")?.asString ?: "RANDOM"
            val catEnum = enumOf(category) ?: TriviaCategory.RANDOM
            generateQuestion(catEnum, diffEnum, userID)
        }

        it.hook.editOriginalEmbeds(listOf(
            gen.first
        )).setComponents(
            listOf(
                gen.second
            )
        ).queue()
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

suspend fun ensureDailyTriviaQuestion(): TriviaCommand.DailyTrivia {
    val date = GoalManager.currentDailyDate().toString()
    val existing = SQL.getDailyTrivia(date)
    if (existing != null) return json.decodeFromString<TriviaCommand.DailyTrivia>(existing)

    val output = requestTrivia(TriviaCategory.RANDOM, TriviaDifficulty.RANDOM)
    val question = output.results.first()
    val answers = if (question.type == "multiple") 4 else 2
    val daily = TriviaCommand.DailyTrivia(output, (1..answers).toList().shuffled())
    SQL.setDailyTrivia(date, json.encodeToString(daily))
    return daily
}

suspend fun generateDailyQuestion(userID: String): Pair<MessageEmbed, ActionRow> {
    val daily = ensureDailyTriviaQuestion()
    return buildQuestion(daily.output, TriviaCategory.RANDOM, TriviaDifficulty.RANDOM, userID, daily = true, answerOrder = daily.answerOrder)
}

suspend fun generateQuestion(category: TriviaCategory, difficulty: TriviaDifficulty, userID: String): Pair<MessageEmbed, ActionRow> {
    return buildQuestion(requestTrivia(category, difficulty), category, difficulty, userID, daily = false, answerOrder = null)
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

private fun buildQuestion(
    output: TriviaCommand.TriviaOutput,
    category: TriviaCategory,
    difficulty: TriviaDifficulty,
    userID: String,
    daily: Boolean,
    answerOrder: List<Int>?
): Pair<MessageEmbed, ActionRow> {
    val trivia = output.results.first()
    val answers = buildMap {
        put(1, StringEscapeUtils.unescapeHtml4(trivia.correct_answer))
        trivia.incorrect_answers.forEachIndexed { index, answer -> put(index + 2, StringEscapeUtils.unescapeHtml4(answer)) }
    }

    return Embed {
        val question = StringEscapeUtils.unescapeHtml4(trivia.question)
        val diff = enumOf<TriviaDifficulty>(trivia.difficulty.uppercase())
        val cat = TriviaCategory.getByTitle(trivia.category.replace(":", " -"))
        title = if (daily) "\uD83E\uDDE9  || **Daily Trivia**" else "\uD83E\uDDE9  || **Trivia Quiz**"
        description = "> Difficulty -> ``${diff?.title}${if (difficulty == TriviaDifficulty.RANDOM) " (random)" else ""}``\n" +
                "> Category -> ``${cat.title}${if (category == TriviaCategory.RANDOM) " (random)" else ""}``\n" +
                "\n" +
                "```fix\n" +
                "${question}```"
        color = 0xc29011
    } to ActionRow.of(
        (answerOrder ?: answers.keys.shuffled()).map { answerID ->
            val label = answers.getValue(answerID)
            val mode = if (daily) "DAILY" else "PLAY"
            if (trivia.type == "boolean") {
                val emoji = if (answerID == 1) Emoji.fromFormatted("<:yes:998195646467145751>")
                else Emoji.fromFormatted("<:no:998195603324551323>")
                button("TRIVIA:$userID:$mode:$answerID", label, emoji)
            } else {
                button("TRIVIA:$userID:$mode:$answerID", label)
            }
        }
    )
}
