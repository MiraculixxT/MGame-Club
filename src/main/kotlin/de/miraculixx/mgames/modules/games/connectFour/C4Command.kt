package de.miraculixx.mgames.modules.games.connectFour

import de.miraculixx.mgames.config.ConfigManager
import de.miraculixx.mgames.config.Connect4Settings
import de.miraculixx.mgames.modules.games.utils.GameTools
import de.miraculixx.mgames.modules.games.utils.enums.Game
import de.miraculixx.mgames.modules.games.utils.enums.SkinType
import de.miraculixx.mgames.utils.Colors
import de.miraculixx.mgames.utils.Icons
import de.miraculixx.mgames.utils.api.SQL
import de.miraculixx.mgames.utils.entities.SlashCommandEvent
import de.miraculixx.mgames.utils.extensions.queueV2
import dev.minn.jda.ktx.interactions.components.Container
import dev.minn.jda.ktx.interactions.components.StringSelectMenu
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.selections.SelectOption

class C4Command : SlashCommandEvent {
    // Default chip skins everyone owns for free. Must match the defaults seeded in
    // SQL.createUser (userEmotesActive: C4_P='🔴', C4_S='🟡') so the active skin shows as selected.
    private val defaultPrimary = "🔴"
    private val defaultSecondary = "🟡"

    override suspend fun trigger(it: SlashCommandInteractionEvent) {
        val subcommand = it.subcommandName ?: return
        if (subcommand == "skin") {
            showSkinChanger(it)
            return
        }
        val tools = GameTools("4G", "4 Gewinnt", Game.CONNECT_4)
        tools.command(it)
    }

    private suspend fun showSkinChanger(it: SlashCommandInteractionEvent) {
        val member = it.member ?: return
        val guildID = it.guild?.idLong ?: return
        val user = SQL.getUser(member.idLong, guildID, emotes = true)
        val userEmotes = user.emotes!!
        val ownedPrimary = userEmotes.owned.filterKeys { it == "C4_P" }.values.toSet()
        val ownedSecondary = userEmotes.owned.filterKeys { it == "C4_S" }.values.toSet()
        val conf = ConfigManager.gameSettingsConfig.connect4

        val primary = StringSelectMenu("GAME_C4_SKIN_1") {
            placeholder = "Primary Chip Skin"
            minValues = 1
            maxValues = 1
            addOptions(defaultOption(defaultPrimary, userEmotes.c4))
            addOptions(skinOptions(conf, ownedPrimary, userEmotes.c4))
        }
        val secondary = StringSelectMenu("GAME_C4_SKIN_2") {
            placeholder = "Secondary Chip Skin"
            minValues = 1
            maxValues = 1
            addOptions(defaultOption(defaultSecondary, userEmotes.c42))
            addOptions(skinOptions(conf, ownedSecondary, userEmotes.c42))
        }

        it.replyComponents(
            listOf(
                Container(accentColor = Colors.yellow) {
                    text("## ${Icons.connect4} || **Skin Changer**")
                    text(
                        "> Set an exclusive chip skin for **Connect 4**!\n" +
                            "> - Primary `->` your main skin, used in most games\n" +
                            "> - Secondary `->` fallback skin, used when your opponent already picked your primary"
                    )
                    separator()
                    text("-# ${Icons.mCoins} Balance >> `${user.coins}`")
                    components += ActionRow.of(primary)
                    components += ActionRow.of(secondary)
                }
            )
        ).setEphemeral(true).queueV2()
    }

    private fun defaultOption(emote: String, current: String): SelectOption =
        skinOption(emote, if (current == emote) SkinType.SELECTED else SkinType.FREE)

    private fun skinOptions(config: Connect4Settings, owned: Set<String>, current: String): List<SelectOption> =
        config.emotes.map { (emote, price) ->
            when {
                current == emote -> skinOption(emote, SkinType.SELECTED)
                owned.contains(emote) -> skinOption(emote, SkinType.BOUGHT)
                else -> skinOption(emote, SkinType.COINS, price)
            }
        }

    private fun skinOption(emote: String, type: SkinType, price: Int = 0): SelectOption {
        val emoji = Emoji.fromFormatted(emote)
        // Value encodes the price tag the dropdown handler reads back:
        // _FREE (owned/free), _SELECTED (current), or _<price> (needs buying).
        return when (type) {
            SkinType.FREE, SkinType.BOUGHT -> SelectOption.of("Unlocked", "${emote}_FREE").withEmoji(emoji)
            SkinType.COINS -> SelectOption.of("Unlock >> $price Coins", "${emote}_$price").withEmoji(emoji)
            SkinType.SELECTED -> SelectOption.of(">> Current Skin <<", "${emote}_SELECTED").withEmoji(emoji)
        }
    }
}
