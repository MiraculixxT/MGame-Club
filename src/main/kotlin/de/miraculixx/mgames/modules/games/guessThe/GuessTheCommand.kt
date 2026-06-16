package de.miraculixx.mgames.modules.games.guessThe

import de.miraculixx.mgames.utils.entities.ButtonEvent
import de.miraculixx.mgames.utils.entities.ModalEvent
import de.miraculixx.mgames.utils.entities.SlashCommandEvent
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent

const val GUESS_THE_INTERACTION_PREFIX = "GUESS-THE"

object GuessTheCommand : SlashCommandEvent, ButtonEvent, ModalEvent {
    private val subGames = listOf<GuessTheGame>(
        GuessTheFlag
    )
    private val subGamesByCommand = subGames.associateBy { it.subcommand }
    private val subGamesByInteraction = subGames.associateBy { it.interactionKey }

    override suspend fun trigger(it: SlashCommandInteractionEvent) {
        subGamesByCommand[it.subcommandName]?.trigger(it)
    }

    override suspend fun trigger(it: ButtonInteractionEvent) {
        val interactionKey = it.componentId.split(":").getOrNull(1) ?: return
        subGamesByInteraction[interactionKey]?.trigger(it)
    }

    override suspend fun trigger(it: ModalInteractionEvent) {
        val interactionKey = it.modalId.split(":").getOrNull(1) ?: return
        subGamesByInteraction[interactionKey]?.trigger(it)
    }
}

interface GuessTheGame : SlashCommandEvent, ButtonEvent, ModalEvent {
    val subcommand: String
    val interactionKey: String
}
