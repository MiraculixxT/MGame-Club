package de.miraculixx.mgames.modules.games.tictactoe

import de.miraculixx.mgames.modules.games.utils.GameTools
import de.miraculixx.mgames.modules.games.utils.enums.Game
import de.miraculixx.mgames.utils.entities.SlashCommandEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

/**
 * System integration des Spiels Tic Tac Toe
 * @author Julius
 */
class TTTCommand : SlashCommandEvent {
    override suspend fun trigger(it: SlashCommandInteractionEvent) {
        val tools = GameTools("TTT", "Tic Tac Toe", Game.TIC_TAC_TOE)
        tools.command(it)
    }
}