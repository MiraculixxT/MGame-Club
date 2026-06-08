package de.miraculixx.mgames.modules.utils.events

import dev.minn.jda.ktx.events.listener
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent

object TabComplete {
    fun startListen(jda: JDA) = jda.listener<CommandAutoCompleteInteractionEvent> {
        when ("${it.name}:${it.subcommandName}") {
            "setup:language" -> it.replyChoiceStrings("German", "English").queue()
        }
    }
}
