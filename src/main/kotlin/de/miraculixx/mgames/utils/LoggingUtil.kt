package de.miraculixx.mgames.utils

import de.miraculixx.mgames.Main
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.interactions.InteractionHook
import org.slf4j.Logger
import org.slf4j.LoggerFactory


val logger: Logger = LoggerFactory.getLogger(Main::class.java)

fun InsufficientPermissionException.notify(hook: InteractionHook) {
    hook.editOriginal("```diff\n- Missing Permission to perform this action!\n- Permission: ${this.permission.name}```").queue()
}
