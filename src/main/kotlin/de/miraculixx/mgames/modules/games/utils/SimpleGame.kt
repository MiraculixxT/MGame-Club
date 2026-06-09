package de.miraculixx.mgames.modules.games.utils

import kotlinx.coroutines.coroutineScope
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent

interface SimpleGame {
    val startedAt: Long
    val playerIds: Set<Long>

    suspend fun interact(options: List<String>, interactor: Member, event: GenericComponentInteractionCreateEvent?) = coroutineScope {}

    suspend fun setWinner(win: FieldsTwoPlayer)

    suspend fun surrender(surrenderer: Member) = setWinner(FieldsTwoPlayer.EMPTY)
}
