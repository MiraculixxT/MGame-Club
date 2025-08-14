package de.miraculixx.mgames.config

import kotlinx.serialization.Serializable
import kotlin.io.path.Path
import kotlin.io.path.writeText

object ConfigManager {
    val coreFile = Path("config/core.json")
    val settingsFile = Path("config/settings.json")
    val gameSettingsFile = Path("config/game_settings.json")

    var coreConfig = coreFile.load<Core>(Core())
        private set
    var settingsConfig = settingsFile.load<Settings>(Settings())
        private set
    var gameSettingsConfig = gameSettingsFile.load<GameSettings>(GameSettings())
        private set


    fun reloadConfig() {
        coreConfig = coreFile.load<Core>(Core())
        settingsConfig = settingsFile.load<Settings>(Settings())
        gameSettingsConfig = gameSettingsFile.load<GameSettings>(GameSettings())
    }

    fun saveConfig() {
        coreFile.writeText(json.encodeToString(coreConfig))
        settingsFile.writeText(json.encodeToString(settingsConfig))
        gameSettingsFile.writeText(json.encodeToString(gameSettingsConfig))
    }

    @Serializable
    data class Core(
        val DISCORD_TOKEN: String = "",
        val SQL_TOKEN: String = ""
    )

    @Serializable
    data class Settings(
        val updater: Boolean = false
    )

    @Serializable
    data class GameSettings(
        val connect4: Connect4Settings = Connect4Settings()
    )
}