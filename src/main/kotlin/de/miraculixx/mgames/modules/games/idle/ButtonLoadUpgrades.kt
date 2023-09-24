package de.miraculixx.mgames.modules.games.idle

import de.miraculixx.mgames.utils.entities.ButtonEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent

class ButtonLoadUpgrades: ButtonEvent {
    override suspend fun trigger(it: ButtonInteractionEvent) {
        it.deferReply(true).queue()
    }
}