package de.miraculixx.mgames.utils.manager

import de.miraculixx.mgames.modules.games.guessThe.GuessTheCommand
import de.miraculixx.mgames.modules.games.quickMath.QuickMath
import de.miraculixx.mgames.utils.entities.ModalEvent
import dev.minn.jda.ktx.events.listener
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent

object ModalManager {
    private val modals: Map<String, ModalEvent> = mapOf(
        "guess-the" to GuessTheCommand,
        "quick-math" to QuickMath
    )

    fun startListen(jda: JDA) = jda.listener<ModalInteractionEvent> {
        val id = it.modalId
        val commandClass = when {
            id.startsWith("QUICK-MATH:") -> modals["quick-math"]
            id.startsWith("GUESS-THE:") -> modals["guess-the"]
            else -> modals[id]
        }
        commandClass?.trigger(it)
    }
}
