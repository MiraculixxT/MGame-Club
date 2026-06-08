package de.miraculixx.mgames.utils.manager

import de.miraculixx.mgames.modules.games.quickMath.QuickMath
import dev.minn.jda.ktx.events.listener
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent

object ModalManager {
    private val modals = mapOf(
        "quick-math" to QuickMath
    )

    fun startListen(jda: JDA) = jda.listener<ModalInteractionEvent> {
        val id = it.modalId
        val commandClass = when {
            id.startsWith("QUICK-MATH:") -> modals["quick-math"]
            else -> modals[id]
        }
        commandClass?.trigger(it)
    }
}
