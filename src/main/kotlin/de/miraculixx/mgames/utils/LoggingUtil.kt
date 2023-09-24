package de.miraculixx.mgames.utils

import de.miraculixx.mgames.Main
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.interactions.InteractionHook
import java.time.LocalDate
import java.time.LocalTime
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

var logger: Logger = LoggerFactory.getLogger(Main::class.java)

fun String.log(color: Color = Color.WHITE) {
    printToConsole(
        this, "\u001B[${color.code}m"
    )
}

fun String.error() {
    printToConsole(this, "\u001b[${Color.RED.code}m")
}

fun InsufficientPermissionException.notify(hook: InteractionHook) {
    hook.editOriginal("```diff\n- Missing Permission to perform this action!\n- Permission: ${this.permission.name}```").queue()
}

private fun printToConsole(input: String, color: String) {
    logger.info("$color$input\u001B[0m")
}

@Suppress("unused")
enum class Color(val code: Byte) {
    RED(31),
    GREEN(32),
    YELLOW(33),
    BLUE(34),
    MAGENTA(35),
    CYAN(36),
    GRAY(90),
    WHITE(97)
}