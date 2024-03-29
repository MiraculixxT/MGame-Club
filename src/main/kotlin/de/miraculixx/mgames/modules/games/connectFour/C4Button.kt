@file:Suppress("BooleanLiteralArgument")

package de.miraculixx.mgames.modules.games.connectFour

import de.miraculixx.mgames.modules.games.utils.enums.Game
import de.miraculixx.mgames.modules.games.utils.GameTools
import de.miraculixx.mgames.utils.entities.ButtonEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent

class C4Button : ButtonEvent {
    override suspend fun trigger(it: ButtonInteractionEvent) {
        val tools = GameTools("4G", "4 Gewinnt", Game.CONNECT_4)
        tools.buttons(it)
    }
}