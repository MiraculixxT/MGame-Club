package de.miraculixx.mgames.modules.trivia

import de.miraculixx.mgames.modules.games.GoalManager
import de.miraculixx.mgames.modules.games.utils.enums.Game
import de.miraculixx.mgames.modules.games.utils.enums.GameMode
import de.miraculixx.mgames.utils.entities.ButtonEvent
import de.miraculixx.mgames.utils.extensions.queueV2
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent

class TriviaButton : ButtonEvent {
    override suspend fun trigger(it: ButtonInteractionEvent) {
        val id = it.componentId
        val split = id.split(':')
        if (split.firstOrNull() != "TRIVIA") return
        val ownerID = split.getOrNull(1) ?: return
        val userID = it.user.id
        if (ownerID != userID) {
            it.reply_("```diff\n- This is not your Question!\n- Generate one with /trivia```", ephemeral = true).queue()
            return
        }

        val gameID = split.getOrNull(2) ?: return
        val game = TriviaMessage.get(userID)
        if (game == null || game.gameID != gameID) {
            it.reply_("```diff\n- This trivia question is outdated.\n- Generate a new one with /trivia```", ephemeral = true).queue()
            return
        }

        if (split.getOrNull(3) == "REPLAY") {
            it.deferEdit().queue()
            val newGame = TriviaMessage.createReplayQuestion(game, userID)
            TriviaMessage.remember(newGame)
            it.message.editMessageComponents(TriviaMessage.render(newGame)).queueV2()
            return
        }

        val answerID = split.getOrNull(4)?.toIntOrNull() ?: return
        if (game.answers.none { answer -> answer.id == answerID } || game.result != null) {
            it.reply_("```diff\n- This trivia question is outdated.\n- Generate a new one with /trivia```", ephemeral = true).queue()
            return
        }
        val success = answerID == 1
        val guildID = it.guild?.idLong ?: return
        if (game.daily && GoalManager.hasCompletedDaily(Game.TRIVIA, it.user.idLong)) {
            it.reply_("```diff\n- Daily Trivia wurde heute bereits abgeschlossen.```", ephemeral = true).queue()
            return
        }
        val dailyResult = if (success && game.daily) {
            GoalManager.registerDailyCompletion(Game.TRIVIA, it.user.idLong, guildID)
        } else null
        val coins = if (success && (!game.daily || dailyResult?.completed == true)) {
            GoalManager.registerGameResult(
                Game.TRIVIA,
                GameMode.SOLO,
                winnerSnowflake = it.user.idLong,
                loserSnowflake = null,
                guildSnowflake = guildID
            )
        } else if (!game.daily && !success) {
            GoalManager.registerGameResult(
                Game.TRIVIA,
                GameMode.SOLO,
                winnerSnowflake = null,
                loserSnowflake = it.user.idLong,
                guildSnowflake = guildID
            )
            0
        } else -1

        val revealedGame = TriviaMessage.reveal(game, answerID, dailyResult?.reward ?: coins)
        TriviaMessage.remember(revealedGame)
        it.editComponents(TriviaMessage.render(revealedGame)).queueV2()
    }
}
