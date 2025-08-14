package de.miraculixx.mgames.config

fun msg(key: String?, id: Long): String {
    return key ?: "undefined"
}

fun msgDiff(text: String) = "```diff\n$text```"
