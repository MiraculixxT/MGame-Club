package de.miraculixx.mgames.modules.games.chess

import de.miraculixx.mgames.modules.games.utils.GameTools
import de.miraculixx.mgames.modules.games.utils.enums.Game
import de.miraculixx.mgames.utils.entities.SlashCommandEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

class ChessCommand: SlashCommandEvent {
    override suspend fun trigger(it: SlashCommandInteractionEvent) {
        val tools = GameTools("CHESS", "Schach", Game.CHESS)
        tools.command(it)
    }
}