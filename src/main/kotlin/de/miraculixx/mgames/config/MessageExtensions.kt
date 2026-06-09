package de.miraculixx.mgames.config

import de.miraculixx.mgames.utils.api.SQL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.yaml.snakeyaml.Yaml
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

fun msg(key: String?, id: Long, args: Map<String, String> = emptyMap()): String {
    return LanguageManager.message(key, id, args)
}

fun msgDiff(text: String) = "```diff\n$text```"

object LanguageManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val guildLanguages = ConcurrentHashMap<Long, Language>()
    private val pendingFetches = ConcurrentHashMap.newKeySet<Long>()
    private val messages = Language.entries.associateWith { loadMessages(it) }

    fun cacheGuildLanguage(guildID: Long, rawLanguage: String?) {
        guildLanguages[guildID] = Language.from(rawLanguage)
    }

    fun message(key: String?, guildID: Long, args: Map<String, String> = emptyMap()): String {
        if (key == null) return "undefined"

        val language = guildLanguages[guildID] ?: run {
            fetchGuildLanguage(guildID)
            Language.EN
        }
        val message = messages[language]?.get(key)
            ?: messages[Language.EN]?.get(key)
            ?: key
        return applyArgs(message, args)
    }

    private fun applyArgs(message: String, args: Map<String, String>): String {
        return args.entries.fold(message) { text, (key, value) ->
            text.replace("%$key%", value)
        }
    }

    private fun fetchGuildLanguage(guildID: Long) {
        if (!pendingFetches.add(guildID)) return

        scope.launch {
            try {
                SQL.getGuild(guildID)
            } finally {
                pendingFetches.remove(guildID)
            }
        }
    }

    private fun loadMessages(language: Language): Map<String, String> {
        val input = LanguageManager::class.java.classLoader.getResourceAsStream("lang/${language.fileName}") ?: return emptyMap()
        return input.use {
            val yaml = Yaml().load<Map<String, Any?>>(it) ?: return emptyMap()
            yaml.mapValues { (_, value) -> value?.toString().orEmpty() }
        }
    }

    enum class Language(val key: String, val fileName: String) {
        DE("DE_DE", "de_DE.yml"),
        EN("EN_US", "en_US.yml");

        companion object {
            fun from(raw: String?): Language {
                return when (raw?.replace('-', '_')?.uppercase(Locale.ROOT)) {
                    "DE", "DE_DE", "GERMAN", "DEUTSCH" -> DE
                    else -> EN
                }
            }
        }
    }
}
