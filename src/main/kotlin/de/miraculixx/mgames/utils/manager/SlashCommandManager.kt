package de.miraculixx.mgames.utils.manager

import de.miraculixx.mgames.Main
import de.miraculixx.mgames.modules.games.connectFour.C4Command
import de.miraculixx.mgames.modules.games.quickMath.QuickMath
import de.miraculixx.mgames.modules.games.quickMath.QuickMath.MathDifficulty
import de.miraculixx.mgames.modules.games.tictactoe.TTTCommand
import de.miraculixx.mgames.modules.trivia.TriviaCategory
import de.miraculixx.mgames.modules.trivia.TriviaCommand
import de.miraculixx.mgames.modules.trivia.TriviaDifficulty
import de.miraculixx.mgames.modules.utils.commands.AdminCommand
import de.miraculixx.mgames.modules.utils.commands.StatsCommand
import de.miraculixx.mgames.modules.utils.commands.SetupCommand
import de.miraculixx.mgames.utils.log
import dev.minn.jda.ktx.events.listener
import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.interactions.commands.choice
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.subcommand
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType

object SlashCommandManager {
    private val commands = mapOf(
        "tictactoe" to TTTCommand(),
        "connect-4" to C4Command(),
        "quick-math" to QuickMath,
        "setup" to SetupCommand(),
        "admin" to AdminCommand(),
        "coins" to StatsCommand(),
        "trivia" to TriviaCommand()
    )

    fun startListen(jda: JDA) = jda.listener<SlashCommandInteractionEvent> {
        val commandClass = commands[it.name] ?: return@listener
        ">> ${it.user.asTag} -> /${it.name} ${it.subcommandName ?: ""}".log()
        commandClass.trigger(it)
    }

    init {
        //Implement all Commands into Discord
        val jda = Main.INSTANCE.jda
        val mainServer = jda.getGuildById(707925156919771158)
        jda.upsertCommand(Command("name", "desc"))
        jda.updateCommands().addCommands(
            Command("trivia", "Question your self some trivia!") {
                subcommand("play", "Answer a trivia question") {
                    option<String>("category", "Choose any category") {
                        TriviaCategory.entries.forEach { choice(it.title, it.name) }
                    }
                    option<String>("difficulty", "Choose any difficulty") {
                        TriviaDifficulty.entries.forEach { choice(it.title, it.name) }
                    }
                }
                subcommand("daily", "Answer today's daily trivia question")
            },

            Command("tictactoe", "Play Tic-Tac-Toe against others") {
                subcommand("user", "Play Tic-Tac-Toe against an other User") {
                    addOption(OptionType.USER, "request", "Send a game request to your selected User")
                }
                subcommand("bot", "Play Tic-Tac-Toe against our AI") {
                    option<String>("difficulty", "Choose a difficulty") {
                        MathDifficulty.entries.forEach { choice(it.title, it.name) }
                    }
                }
                subcommand("daily", "Play today's seeded medium Tic-Tac-Toe challenge")
            },

            Command("connect-4", "Play Connect-4 against others") {
                subcommand("user", "Play Connect 4 against an other User") {
                    addOption(OptionType.USER, "request", "Send a game request to your selected User")
                }
                subcommand("bot", "Play Connect 4 against our AI") {
                    option<String>("difficulty", "Choose a difficulty") {
                        MathDifficulty.entries.forEach { choice(it.title, it.name) }
                    }
                }
                subcommand("daily", "Play today's seeded medium Connect 4 challenge")
                subcommand("skin", "Choose a Skin for your Chip")
            },

            Command("quick-math", "Solve a quick math challenge") {
                subcommand("play", "Solve a quick math challenge") {
                    option<String>("difficulty", "Choose a difficulty") {
                        MathDifficulty.entries.forEach { choice(it.title, it.name) }
                    }
                }
                subcommand("daily", "Solve today's seeded medium quick math challenge")
            },

            Command("stats", "Inspect your personal stats") {
                addOption(OptionType.USER, "user", "Inspect the stats from an other User")
            },
            Command("setup", "Setup all bot settings to start gaming real quick") {
                subcommand("help", "All information about how to setup everything perfectly")
                subcommand("channel", "Create channels to play in with perfect settings") {
                    addOption(OptionType.CHANNEL, "stats-channel", "Setup current channel your stats channel? (PREMIUM ONLY)", false)
                    addOption(OptionType.CHANNEL, "game-channel", "Setup current channel to a Only-Gaming channel?", false)
                }
                subcommand("language", "Change the bot language for this guild") {
                    addOption(OptionType.STRING, "lang", "Choose your preferred bot language", true, true)
                }
            }
        ).queue()
        mainServer?.updateCommands()
            ?.addCommands(
                Command("admin", "Admin Command") {
                    defaultPermissions = DefaultMemberPermissions.DISABLED
                    subcommand("refresh-stats", "Erneuert die Stats")
                    subcommand("draw-image", "Draw Image")
                }
            )?.queue()
    }
}
