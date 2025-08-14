package de.miraculixx.mgames.config

import kotlinx.serialization.Serializable

@Serializable
data class Connect4Settings(
    val rawEmotes: List<Connect4Emotes> = emptyList(),
    val specialEmotes: List<Connect4Emotes> = emptyList()
)

@Serializable
data class Connect4Emotes(
    val emote: String,
    val price: Int,
)
