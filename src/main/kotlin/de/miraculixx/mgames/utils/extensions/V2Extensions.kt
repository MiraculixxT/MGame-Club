package de.miraculixx.mgames.utils.extensions

import kotlinx.coroutines.future.await
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.utils.messages.MessageRequest

suspend fun <T, R> R.awaitV2(): T
    where R : MessageRequest<R>,
          R : RestAction<T> =
    useComponentsV2().submit().await()

fun <R> R.queueV2()
    where R : MessageRequest<R>,
          R : RestAction<*> =
    useComponentsV2().queue()
