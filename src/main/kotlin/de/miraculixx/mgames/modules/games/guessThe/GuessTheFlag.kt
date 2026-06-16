package de.miraculixx.mgames.modules.games.guessThe

import de.miraculixx.mgames.modules.games.GoalManager
import de.miraculixx.mgames.modules.games.utils.coinGrantFooter
import de.miraculixx.mgames.modules.games.utils.enums.Game
import de.miraculixx.mgames.modules.games.utils.enums.GameMode
import de.miraculixx.mgames.utils.Colors
import de.miraculixx.mgames.utils.Icons
import de.miraculixx.mgames.utils.api.callCustomAPI
import de.miraculixx.mgames.utils.api.callCustomAPIBytes
import de.miraculixx.mgames.utils.extensions.queueV2
import de.miraculixx.mgames.utils.serializer.json
import dev.minn.jda.ktx.interactions.components.Container
import dev.minn.jda.ktx.interactions.components.Modal
import dev.minn.jda.ktx.interactions.components.TextInput
import net.dv8tion.jda.api.components.MessageTopLevelComponent
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.mediagallery.MediaGallery
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem
import net.dv8tion.jda.api.components.section.Section
import net.dv8tion.jda.api.components.textdisplay.TextDisplay
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.utils.FileUpload
import java.text.Normalizer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object GuessTheFlag : GuessTheGame {
    override val subcommand = "flag"
    override val interactionKey = "FLAG"

    private const val ANSWER_INPUT_ID = "ANSWER"
    private const val EXPIRY_MILLIS = 15 * 60 * 1000L

    private val activeGames = ConcurrentHashMap<String, GuessTheFlagChallenge>()

    override suspend fun trigger(it: SlashCommandInteractionEvent) {
        it.deferReply().queue()
        startFlagChallenge(it.hook, it.user.idLong)
    }

    override suspend fun trigger(it: ButtonInteractionEvent) {
        val parts = it.componentId.split(":")
        if (parts.size < 3 || parts[0] != GUESS_THE_INTERACTION_PREFIX || parts[1] != interactionKey) return

        if (parts[2] == "PLAY") {
            it.deferReply().queue()
            startFlagChallenge(it.hook, it.user.idLong)
            return
        }

        if (parts.size < 5) return

        val gameID = parts[2]
        val ownerID = parts[3].toLongOrNull() ?: return
        val action = parts[4]
        val challenge = validateChallenge(gameID, ownerID, it.user.idLong)
            .validOrReply(it) { expired -> it.message.editMessageComponents(renderExpired(expired)).queueV2() }
            ?: return

        if (action == "NO_IDEA") {
            synchronized(challenge) {
                if (!challenge.completed) challenge.completed = true
            }
            activeGames.remove(gameID)
            val guildID = it.guild?.idLong ?: return
            GoalManager.registerGameResult(
                Game.GUESS_THE_FLAG,
                GameMode.SOLO,
                winnerSnowflake = null,
                loserSnowflake = it.user.idLong,
                guildSnowflake = guildID,
                coinOverride = 0
            )
            it.deferEdit()
                .setComponents(renderFailed(challenge))
                .useComponentsV2()
                .queue()
            return
        }

        if (action != "ANSWER") return

        it.replyModal(Modal("$GUESS_THE_INTERACTION_PREFIX:$interactionKey:$gameID:$ownerID", "Guess The Flag") {
            label("Country name", description = "Country name must be in english at the moment") {
                child = TextInput(ANSWER_INPUT_ID, TextInputStyle.SHORT, requiredLength = 1..80, placeholder = "Type the country name")
            }
        }).queue()
    }

    override suspend fun trigger(it: ModalInteractionEvent) {
        val parts = it.modalId.split(":")
        if (parts.size < 4 || parts[0] != GUESS_THE_INTERACTION_PREFIX || parts[1] != interactionKey) return

        val gameID = parts[2]
        val ownerID = parts[3].toLongOrNull() ?: return
        val challenge = validateChallenge(gameID, ownerID, it.user.idLong)
            .validOrReply(it) { expired -> it.message?.editMessageComponents(renderExpired(expired))?.queueV2() }
            ?: return

        val answer = it.getValue(ANSWER_INPUT_ID)?.asString.orEmpty()
        val result = synchronized(challenge) {
            when {
                challenge.completed -> GuessResult.Outdated
                challenge.matches(answer) -> {
                    challenge.completed = true
                    GuessResult.Correct(challenge.wrongTries.get(), rewardFor(challenge.wrongTries.get()))
                }
                else -> GuessResult.Wrong(challenge.wrongTries.incrementAndGet())
            }
        }

        when (result) {
            GuessResult.Outdated -> {
                it.reply("```diff\n- This guess-the-flag challenge is already completed.```")
                    .setEphemeral(true)
                    .queue()
            }
            is GuessResult.Wrong -> {
                it.reply("```diff\n- Wrong answer. Try again!\n- Wrong tries: ${result.wrongTries}```")
                    .setEphemeral(true)
                    .queue()
            }
            is GuessResult.Correct -> {
                val guildID = it.guild?.idLong ?: return
                val reward = GoalManager.registerGameResult(
                    Game.GUESS_THE_FLAG,
                    GameMode.SOLO,
                    winnerSnowflake = it.user.idLong,
                    loserSnowflake = null,
                    guildSnowflake = guildID,
                    coinOverride = result.reward
                )
                activeGames.remove(gameID)
                it.deferEdit()
                    .setComponents(renderResult(challenge, result.wrongTries, reward))
                    .useComponentsV2()
                    .queue()
            }
        }
    }

    private fun cleanupExpired() {
        activeGames.entries.removeIf { (_, challenge) -> challenge.isExpired() || challenge.completed }
    }

    private fun validateChallenge(gameID: String, ownerID: Long, userID: Long): ChallengeValidation {
        val challenge = activeGames[gameID]
            ?: return ChallengeValidation.Invalid(ChallengeValidation.Failure.OUTDATED)

        if (ownerID != userID || challenge.ownerID != userID) {
            return ChallengeValidation.Invalid(ChallengeValidation.Failure.NOT_OWNER)
        }

        if (challenge.isExpired()) {
            activeGames.remove(gameID)
            return ChallengeValidation.Invalid(ChallengeValidation.Failure.EXPIRED, challenge)
        }

        return ChallengeValidation.Valid(challenge)
    }

    private fun ChallengeValidation.validOrReply(
        event: IReplyCallback,
        onExpired: (GuessTheFlagChallenge) -> Unit
    ): GuessTheFlagChallenge? {
        return when (this) {
            is ChallengeValidation.Valid -> challenge
            is ChallengeValidation.Invalid -> {
                event.reply(reason.message).setEphemeral(true).queue()
                challenge?.let(onExpired)
                null
            }
        }
    }

    private fun renderActive(challenge: GuessTheFlagChallenge): List<MessageTopLevelComponent> {
        return listOf(
            Container(accentColor = null) {
                text("## ${Icons.goalFlag} || **Guess The Flag**")
                text("> Type your answer with the button below.")
                separator()
                components += flagGallery(challenge)
                separator()
                components += ActionRow.of(
                    Button.primary("$GUESS_THE_INTERACTION_PREFIX:$interactionKey:${challenge.gameID}:${challenge.ownerID}:ANSWER", "Answer"),
                    Button.secondary("$GUESS_THE_INTERACTION_PREFIX:$interactionKey:${challenge.gameID}:${challenge.ownerID}:NO_IDEA", "No Idea")
                )
            }
        )
    }

    private fun renderResult(challenge: GuessTheFlagChallenge, wrongTries: Int, reward: Int): List<MessageTopLevelComponent> {
        val firstTry = wrongTries == 0
        val attempts = wrongTries + 1
        return listOf(
            Container(accentColor = if (firstTry) Colors.yellow else Colors.green) {
                components += replayHeader()
                if (firstTry) text( "### ${Icons.star} First Try!")
                separator()
                components += flagGallery(challenge)
                separator()
                text(
                    "> Country >> `${challenge.countryName}`\n" +
                        "> Attempts >> `$attempts`\n" +
                        "-# Von <@${challenge.ownerID}>${coinGrantFooter(reward)}"
                )
            }
        )
    }

    private fun renderExpired(challenge: GuessTheFlagChallenge): List<MessageTopLevelComponent> {
        return listOf(
            Container(accentColor = Colors.red) {
                components += replayHeader()
                text("> This challenge expired. Start a new one with `/guess-the flag`.")
                separator()
                components += flagGallery(challenge)
            }
        )
    }

    private fun renderFailed(challenge: GuessTheFlagChallenge): List<MessageTopLevelComponent> {
        return listOf(
            Container(accentColor = Colors.red) {
                components += replayHeader()
                text("### Surrendered...")
                separator()
                components += flagGallery(challenge)
                separator()
                text(
                    "> Country >> `${challenge.countryName}`\n" +
                        "> Wrong tries >> `${challenge.wrongTries.get()}`\n" +
                        "-# Von <@${challenge.ownerID}>"
                )
            }
        )
    }

    private suspend fun startFlagChallenge(hook: InteractionHook, user: Long) {
        cleanupExpired()

        val challenge = runCatching {
            GuessTheFlagProvider.createChallenge(user)
        }.getOrElse { error ->
            hook.editOriginal("Could not load a flag challenge right now. `${error.message ?: "Unknown error"}`")
                .setComponents(emptyList())
                .queue()
            return
        }

        activeGames[challenge.gameID] = challenge
        hook.editOriginalComponents(renderActive(challenge))
            .useComponentsV2()
            .queue()
    }

    private fun replayHeader(): Section {
        return Section.of(
            Button.primary("$GUESS_THE_INTERACTION_PREFIX:$interactionKey:PLAY", Icons.play),
            TextDisplay.of("## ${Icons.goalFlag} || **Guess The Flag**")
        )
    }

    private fun flagGallery(challenge: GuessTheFlagChallenge): MediaGallery {
        val flagUpload = FileUpload.fromData(challenge.flagBytes.copyOf(), "guess-the-flag.png")
            .setDescription("Guess the flag image")
        return MediaGallery.of(
            MediaGalleryItem.fromFile(flagUpload)
                .withDescription("Guess the flag image")
        )
    }

    private fun rewardFor(wrongTries: Int): Int {
        return when (wrongTries) {
            0 -> 5
            1 -> 2
            2 -> 1
            else -> 0
        }
    }

    private sealed interface GuessResult {
        data object Outdated : GuessResult
        data class Wrong(val wrongTries: Int) : GuessResult
        data class Correct(val wrongTries: Int, val reward: Int) : GuessResult
    }

    private sealed interface ChallengeValidation {
        data class Valid(val challenge: GuessTheFlagChallenge) : ChallengeValidation
        data class Invalid(val reason: Failure, val challenge: GuessTheFlagChallenge? = null) : ChallengeValidation

        enum class Failure(val message: String) {
            OUTDATED("```diff\n- This guess-the-flag challenge is outdated.\n- Start a new one with /guess-the flag```"),
            NOT_OWNER("```diff\n- This is not your flag challenge.\n- Start your own with /guess-the flag```"),
            EXPIRED("```diff\n- This guess-the-flag challenge expired.\n- Start a new one with /guess-the flag```")
        }
    }

    private data class GuessTheFlagChallenge(
        val gameID: String,
        val ownerID: Long,
        val countryName: String,
        val acceptedAnswers: Set<String>,
        val flagBytes: ByteArray,
        val createdAt: Long = System.currentTimeMillis(),
        val wrongTries: AtomicInteger = AtomicInteger(0),
        @Volatile var completed: Boolean = false
    ) {
        fun matches(answer: String): Boolean = normalizeAnswer(answer) in acceptedAnswers
        fun isExpired(now: Long = System.currentTimeMillis()): Boolean = now - createdAt > EXPIRY_MILLIS

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as GuessTheFlagChallenge
            return gameID == other.gameID
        }

        override fun hashCode(): Int = gameID.hashCode()
    }

    private object GuessTheFlagProvider {
        private const val FLAG_URL_PREFIX = "https://flagcdn.com/w320"
        private val excludedCodes = setOf("un", "sj", "bv", "hm", "mf", "um")
        @Volatile private var cachedFlags: List<FlagOption>? = null

        suspend fun createChallenge(ownerID: Long): GuessTheFlagChallenge {
            val option = loadFlags().random()
            val flagBytes = callCustomAPIBytes("$FLAG_URL_PREFIX/${option.code}.png")
            return GuessTheFlagChallenge(
                gameID = UUID.randomUUID().toString(),
                ownerID = ownerID,
                countryName = option.name,
                acceptedAnswers = option.answers,
                flagBytes = flagBytes
            )
        }

        private suspend fun loadFlags(): List<FlagOption> {
            cachedFlags?.let { return it }
            val codes = json.decodeFromString<Map<String, String>>(callCustomAPI("https://flagcdn.com/en/codes.json"))
            val codesDE = json.decodeFromString<Map<String, String>>(callCustomAPI("https://flagcdn.com/de/codes.json"))
            val flags = codes
                .filterKeys { code -> code !in excludedCodes }
                .map { (code, name) -> FlagOption(code, name, aliasesFor(code, name, codesDE[code] ?: name)) }
                .filter { it.answers.isNotEmpty() }

            require(flags.isNotEmpty()) { "Flag list is empty" }
            cachedFlags = flags
            return flags
        }

        private fun aliasesFor(code: String, vararg names: String): Set<String> {
            val aliases = mutableSetOf<String>()
            names.forEach { name ->
                aliases += name
                aliases += name.replace(Regex("\\s*\\([^)]*\\)"), "").trim()

                Regex("\\(([^)]*)\\)").findAll(name).forEach { match ->
                    match.groupValues[1]
                        .split("/", ",", ";")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .forEach { aliases += it }
                }
            }

            hardcodedAliases[code]?.let { aliases += it }
            return aliases.map(::normalizeAnswer).filter { it.isNotBlank() }.toSet()
        }

        private val hardcodedAliases = mapOf(
            "us" to listOf("USA", "United States of America", "America"),
            "gb" to listOf("UK", "United Kingdom", "Great Britain", "Britain"),
            "cz" to listOf("Czech Republic"),
            "ci" to listOf("Ivory Coast", "Cote d'Ivoire"),
            "cd" to listOf("Democratic Republic of the Congo", "Congo Kinshasa"),
            "cg" to listOf("Congo", "Congo Brazzaville"),
            "kr" to listOf("Republic of Korea"),
            "kp" to listOf("DPRK", "Democratic People's Republic of Korea"),
            "ru" to listOf("Russian Federation"),
            "va" to listOf("Holy See"),
            "ps" to listOf("Palestine"),
            "sj" to listOf("Norway")
        )

        private data class FlagOption(val code: String, val name: String, val answers: Set<String>)
    }
}

private fun normalizeAnswer(input: String): String {
    val withoutAccents = Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return withoutAccents
        .lowercase()
        .replace("&", " and ")
        .replace(Regex("[^a-z0-9 ]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
