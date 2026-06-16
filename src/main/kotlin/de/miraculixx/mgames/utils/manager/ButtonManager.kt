package de.miraculixx.mgames.utils.manager

import de.miraculixx.mgames.modules.games.connectFour.C4Button
import de.miraculixx.mgames.modules.games.guessThe.GuessTheCommand
import de.miraculixx.mgames.modules.games.quickMath.QuickMath
import de.miraculixx.mgames.modules.games.tictactoe.TTTListener
import de.miraculixx.mgames.modules.trivia.TriviaButton
import dev.minn.jda.ktx.events.listener
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent

object ButtonManager {
    private val buttons = mapOf(
        "GAME_TTT" to TTTListener(),
        "GAME_4G" to C4Button(),
        "QUICK-MATH" to QuickMath,
        "GUESS-THE" to GuessTheCommand,
        "TRIVIA" to TriviaButton()
    )

    fun startListen(jda: JDA) = jda.listener<ButtonInteractionEvent> {
        val id = it.componentId
        val commandClass = when {
            id.startsWith("GAME_TTT_") -> buttons["GAME_TTT"]
            id.startsWith("GAME_4G_") -> buttons["GAME_4G"]
            id.startsWith("TRIVIA:") -> buttons["TRIVIA"]
            id.startsWith("QUICK-MATH:") -> buttons["QUICK-MATH"]
            id.startsWith("GUESS-THE:") -> buttons["GUESS-THE"]
            else -> buttons[id]
        }
        commandClass?.trigger(it)
    }
}
