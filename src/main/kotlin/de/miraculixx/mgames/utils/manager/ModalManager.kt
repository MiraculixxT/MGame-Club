package de.miraculixx.mgames.utils.manager

import de.miraculixx.mgames.modules.games.chess.ChessModal
import de.miraculixx.mgames.modules.games.quickMath.QuickMathCommand
import dev.minn.jda.ktx.events.listener
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent

object ModalManager {
    private val modals = mapOf(
        "chess" to ChessModal(),
        "quick-math" to QuickMathCommand
    )

    fun startListen(jda: JDA) = jda.listener<ModalInteractionEvent> {
        val id = it.modalId
        val commandClass = when {
            id.startsWith("GAME_CHESS_") -> modals["chess"]
            id.startsWith("QUICK-MATH:") -> modals["quick-math"]
            else -> modals[id]
        }
        commandClass?.trigger(it)
    }
}
