package de.miraculixx.mgames.utils.entities

import net.dv8tion.jda.api.events.interaction.component.GenericSelectMenuInteractionEvent
import net.dv8tion.jda.api.components.selections.StringSelectMenu

interface DropDownEvent {
    suspend fun trigger(it: GenericSelectMenuInteractionEvent<String, StringSelectMenu>)
}