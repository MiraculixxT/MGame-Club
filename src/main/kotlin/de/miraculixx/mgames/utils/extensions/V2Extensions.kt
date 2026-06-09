package de.miraculixx.mgames.utils.extensions

import kotlinx.coroutines.future.await
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import net.dv8tion.jda.api.requests.restaction.MessageEditAction

suspend fun MessageEditAction.awaitV2(): Message = useComponentsV2().submit().await()
fun MessageEditAction.queueV2() = useComponentsV2().queue()

suspend fun MessageCreateAction.awaitV2(): Message = useComponentsV2().submit().await()
fun MessageCreateAction.queueV2() = useComponentsV2().queue()
