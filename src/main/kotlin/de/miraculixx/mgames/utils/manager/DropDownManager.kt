package de.miraculixx.mgames.utils.manager

import de.miraculixx.mgames.modules.games.connectFour.C4DropDown
import dev.minn.jda.ktx.events.listener
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.component.GenericSelectMenuInteractionEvent
import net.dv8tion.jda.api.components.selections.StringSelectMenu

object DropDownManager {
    private val dropdowns = mapOf(
        "GAME_C4" to C4DropDown()
    )

    fun startListen(jda: JDA) = jda.listener<GenericSelectMenuInteractionEvent<String, StringSelectMenu>> {
        val id = it.componentId
        val commandClass = when {
            id.startsWith("GAME_C4_") -> dropdowns["GAME_C4"]

            else -> dropdowns[id]
        }
        commandClass?.trigger(it)
    }
}
