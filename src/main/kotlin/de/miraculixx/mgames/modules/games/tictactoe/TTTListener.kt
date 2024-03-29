package de.miraculixx.mgames.modules.games.tictactoe

import de.miraculixx.mgames.modules.games.utils.GameTools
import de.miraculixx.mgames.modules.games.utils.enums.Game
import de.miraculixx.mgames.utils.entities.ButtonEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent

/**
 * System integration des Spiels Tic Tac Toe
 * @author Julius
 */
class TTTListener : ButtonEvent {
    override suspend fun trigger(it: ButtonInteractionEvent) {
        val tools = GameTools("TTT", "Tic Tac Toe", Game.TIC_TAC_TOE)
        tools.buttons(it)
    }
}