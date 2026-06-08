package de.miraculixx.mgames.modules.trivia

import de.miraculixx.mgames.modules.games.GoalManager
import de.miraculixx.mgames.modules.games.utils.enums.Game
import de.miraculixx.mgames.modules.games.utils.enums.GameMode
import de.miraculixx.mgames.utils.entities.ButtonEvent
import dev.minn.jda.ktx.messages.Embed
import dev.minn.jda.ktx.messages.editMessage_
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.buttons.ButtonStyle

class TriviaButton : ButtonEvent {
    override suspend fun trigger(it: ButtonInteractionEvent) {
        val id = it.componentId
        val split = id.split(':')
        if (split.firstOrNull() != "TRIVIA") return
        val userID = it.user.id
        if (split[1] != userID) {
            it.reply_("```diff\n- This is not your Question!\n- Generate one with /trivia```", ephemeral = true).queue()
            return
        }
        if (split[2] == "REPLAY") {
            it.editButton(it.button.asDisabled()).queue()
            val message = it.message
            val embed = message.embeds.firstOrNull()
            val gen = if (embed == null || embed.description == null) {
                generateQuestion(TriviaCategory.RANDOM, TriviaDifficulty.RANDOM, userID)
            } else {
                val description = embed.description
                val category =
                    TriviaCategory.entries.firstOrNull { i -> description?.contains("``${i.title}``") == true }
                val difficulty =
                    TriviaDifficulty.entries.firstOrNull { i -> description?.contains("``${i.title}``") == true }

                generateQuestion(
                    category ?: TriviaCategory.RANDOM,
                    difficulty ?: TriviaDifficulty.RANDOM,
                    userID
                )
            }
            message.editMessageEmbeds(listOf(gen.first)).setComponents(listOf(gen.second)).queue()
            return
        }
        val mode = split.getOrNull(2) ?: return
        val answer = split.getOrNull(3) ?: split.getOrNull(2) ?: return
        val daily = mode == "DAILY"
        val success = answer == "1"
        val isFalse = !success
        val guildID = it.guild?.idLong ?: return
        if (daily && GoalManager.hasCompletedDaily(Game.TRIVIA, it.user.idLong, guildID)) {
            it.reply_("```diff\n- Daily Trivia wurde heute bereits abgeschlossen.```", ephemeral = true).queue()
            return
        }
        val message = it.message
        val components = message.components.first().asActionRow().buttons
        val embed = message.embeds.first()
        val replay = ActionRow.of(Button.primary("TRIVIA:$userID:REPLAY", "Replay").withEmoji(Emoji.fromUnicode("\uD83D\uDD01")))
        val dailyDifficultyMultiplier = 2
        val dailyResult = if (success && daily) {
            GoalManager.registerDailyCompletion(Game.TRIVIA, it.user.idLong, guildID, dailyDifficultyMultiplier)
        } else null
        if (success && (!daily || dailyResult?.completed == true)) {
            GoalManager.registerGameResult(
                Game.TRIVIA,
                GameMode.SOLO,
                winnerSnowflake = it.user.idLong,
                loserSnowflake = null,
                guildSnowflake = guildID,
                difficultyMultiplier = if (daily) dailyDifficultyMultiplier else 1
            )
        } else if (!daily && !success) {
            GoalManager.registerGameResult(
                Game.TRIVIA,
                GameMode.SOLO,
                winnerSnowflake = null,
                loserSnowflake = it.user.idLong,
                guildSnowflake = guildID
            )
        }
        if (isFalse) {

            it.editMessage_(null, listOf(Embed {
                title = embed.title
                description = embed.description?.replace("```fix\n", "```diff\n- ")
                color = 0xc21111
            }), listOf(ActionRow.of(buildList {
                components.forEach { com ->
                    if (id == com.customId) add(com.asDisabled().withStyle(ButtonStyle.DANGER))
                    else if (com.customId?.endsWith('1') == true) add(com.asDisabled().withStyle(ButtonStyle.PRIMARY))
                    else add(com.asDisabled())
                }
            }), replay
            )).queue()

        } else {

            it.editMessage_(null, listOf(Embed {
                title = embed.title
                description = embed.description?.replace(
                    "```fix\n",
                    "```diff\n+ "
                ) + if (dailyResult != null) "\n> Daily Reward -> `${dailyResult.reward.takeIf { reward -> reward > 0 } ?: "already completed"}`" else ""
                color = 0x00800f
            }), listOf(ActionRow.of(buildList {
                components.forEach { com ->
                    if (id == com.customId) add(com.asDisabled().withStyle(ButtonStyle.SUCCESS))
                    else add(com.asDisabled())
                }
            }), replay
            )).queue()

        }
    }
}
