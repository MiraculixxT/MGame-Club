package de.miraculixx.mgames.modules.utils.commands

import de.miraculixx.mgames.config.LanguageManager
import de.miraculixx.mgames.config.msg
import de.miraculixx.mgames.modules.games.UpdaterGame
import de.miraculixx.mgames.utils.api.SQL
import de.miraculixx.mgames.utils.entities.SlashCommandEvent
import de.miraculixx.mgames.utils.notify
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.messages.Embed
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.components.buttons.Button

class SetupCommand : SlashCommandEvent {
    override suspend fun trigger(it: SlashCommandInteractionEvent) {
        when (it.subcommandName) {
            "help" -> it.reply("HELP")
            "channel" -> {
                val guild = it.guild ?: return
                it.deferReply().queue()
                val hook = it.hook

                if (it.getOption("stats-channel") != null) {
                    val g = SQL.getGuild(guild.idLong)
                    if (g.premium) {
                        val target = it.getOption("stats-channel")?.asChannel as? MessageChannel
                        if (target == null) {
                            hook.editOriginal("```diff\n- Please select a valid channel. NOT a category```")
                            return
                        }
                        try {
                            if (target.getHistoryFromBeginning(10).await().size() != 0) {
                                hook.editOriginal("```diff\n- This Channel has to much traffic! Please choose an empty Channel to setup```").queue()
                                return
                            }

                            UpdaterGame.updateLeaderboardGuild(guild, target)
                            hook.editOriginal("**>> ERFOLG**\n${target.asMention} ist nun der Game Stats Channel!").queue()

                            SQL.update("UPDATE guildData SET Stats_Channel=${target.id} WHERE Discord_ID=${guild.id}")
                        } catch (e: InsufficientPermissionException) {
                            e.notify(hook)
                        }
                    } else {
                        hook.editOriginal("```diff\n- Your Guild does not own Premium!\n- Activate it in MCreate (Bots Master-Guild) or get it on our Webshop!```")
                            .setComponents(
                                ActionRow.of(
                                    Button.link("https://discord.gg/VEcR8RbnSH", "MCreate").withEmoji(Emoji.fromFormatted("<:mutils:975780449903341579>")),
                                    Button.link("https://miraculixx.de/mcreate/shop", "Webshop").withEmoji(Emoji.fromUnicode("\uD83D\uDED2"))
                                )
                            )
                            .queue()
                        return
                    }
                }
            }
            "language" -> {
                val guildID = it.guild?.idLong ?: return
                val language = it.getOption("lang")!!.asString
                val selectedLanguage = LanguageManager.Language.from(language)
                SQL.setGuildLanguage(guildID, selectedLanguage)

                it.replyEmbeds(
                    Embed {
                        title = "\uD83C\uDF0D  **||  LANGUAGE SWITCHER**"
                        description = "```diff\n" +
                                "+ ${msg("systemLanguageSwitch", guildID)}```\n" +
                                "**New Language ~~⠀⠀>~~** `$language (${selectedLanguage.key})`\n" +
                                "<:blanc:784059217890770964>\n" +
                                msg("systemLanguageInfo", guildID)
                        color = 0xc99d11
                        footer {
                            name = "MGame-Club - Play games inside of Discord everywhere!"
                            iconUrl = "https://i.imgur.com/Im1QNQ9.png"
                        }
                    }
                )
            }
        }
    }
}
