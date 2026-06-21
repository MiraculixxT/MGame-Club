package de.miraculixx.mgames.config

import kotlinx.serialization.Serializable

@Serializable
data class Connect4Settings(
    // Every buyable chip skin. `emote` is either a unicode emoji or a Discord-formatted
    // custom emote (e.g. "<:name:id>"); both render the same way in selects and the board.
    val emotes: List<Connect4Emotes> = emptyList()
)

@Serializable
data class Connect4Emotes(
    val emote: String,
    val price: Int,
)
