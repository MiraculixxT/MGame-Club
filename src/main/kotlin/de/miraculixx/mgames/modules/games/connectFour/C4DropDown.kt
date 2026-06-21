package de.miraculixx.mgames.modules.games.connectFour

import de.miraculixx.mgames.utils.Colors
import de.miraculixx.mgames.utils.Icons
import de.miraculixx.mgames.utils.api.SQL
import de.miraculixx.mgames.utils.entities.DropDownEvent
import de.miraculixx.mgames.utils.extensions.queueV2
import dev.minn.jda.ktx.interactions.components.Container
import net.dv8tion.jda.api.components.MessageTopLevelComponent
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.interaction.component.GenericSelectMenuInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.components.selections.StringSelectMenu

class C4DropDown : DropDownEvent {
    override suspend fun trigger(it: GenericSelectMenuInteractionEvent<String, StringSelectMenu>) {
        val member = it.member ?: return
        val guildID = it.guild?.idLong ?: return
        val secondary = it.componentId.split('_').getOrNull(3) == "2"
        val data = it.values.first().split('_')
        val emote = data[0]
        val priceTag = data[1]

        it.deferReply(true).queue()
        val hook = it.hook

        val user = SQL.getUser(member.idLong, guildID, emotes = true)
        val column = if (secondary) "C4_S" else "C4_P"

        when (priceTag) {
            "FREE" -> applyEmote(emote, member, guildID, secondary, hook, user.emotes)

            "SELECTED" -> hook.feedback(Colors.yellow, "You already use this skin!")

            else -> {
                val alreadyOwned = user.emotes!!.owned.filterKeys { it == column }.containsValue(emote)
                if (alreadyOwned) {
                    applyEmote(emote, member, guildID, secondary, hook, user.emotes)
                    return
                }

                // User does not own the chosen skin yet -> try to buy it.
                val price = priceTag.toIntOrNull() ?: 0
                if (user.coins < price) {
                    hook.feedback(
                        Colors.red,
                        "You don't have enough Coins to unlock $emote!\n> Missing >> `${price - user.coins}` ${Icons.mCoins}"
                    )
                    return
                }
                SQL.setUserCoins(member.idLong, guildID, user.coins - price)
                SQL.addEmote(member.idLong, guildID, column, emote)
                applyEmote(emote, member, guildID, secondary, hook, user.emotes)
            }
        }
    }

    private suspend fun applyEmote(
        emote: String,
        member: Member,
        guildID: Long,
        secondary: Boolean,
        hook: InteractionHook,
        emoteData: SQL.UserEmote?
    ) {
        val other = if (!secondary) emoteData?.c42 else emoteData?.c4
        if (other == emote) {
            hook.feedback(Colors.red, "You can't use the same skin for both slots!")
            return
        }
        SQL.setActiveEmote(member.idLong, guildID, if (secondary) "C4_S" else "C4_P", emote)
        hook.feedback(Colors.green, "$emote is now your Connect 4 chip skin!", "Skin Changed")
    }

    private fun InteractionHook.feedback(color: Int, message: String, title: String = "Skin Changer") {
        editOriginalComponents(skinFeedback(color, message, title)).queueV2()
    }

    private fun skinFeedback(color: Int, message: String, title: String): List<MessageTopLevelComponent> =
        listOf(
            Container(accentColor = color) {
                text("## ${Icons.connect4} || **$title**")
                text("> $message")
            }
        )
}
