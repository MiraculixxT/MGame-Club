# CLAUDE.md

## Overview

MGame Club is a Discord minigame bot written in **Kotlin (JVM 17)**, built on a custom-modified **JDA 6** fork plus `jda-ktx`. Games (Tic-Tac-Toe, Connect-4, Quick Math, Trivia, Guess-the-Flag) are played inline in Discord via slash commands and message components (buttons/dropdowns/modals). State lives in a remote **MariaDB**; coins/streaks/leaderboards persist, active game instances are in-memory.

## Build & Run

```bash
./gradlew build              # compile + assemble
./gradlew run                # run the bot (reads ./config/*.json from CWD)
./gradlew installDist        # produce runnable dist in build/install/MCord-Games
./gradlew clean
```

- No test suite exists. There is no lint config beyond Kotlin compiler defaults.
- The running process reads stdin commands: `exit` (graceful shutdown) and `reload` (reload `config/*.json` via `ConfigManager`).
- Gradle root project name is `MCord-Games` (legacy); the artifact is `MCord-Games-1.0`.

## Configuration & Secrets

- `config/` is **gitignored** and holds live secrets — do not commit it or echo its contents.
  - `config/core.json` → `DISCORD_TOKEN`, `SQL_TOKEN` (deserialized into `ConfigManager.Core`).
  - `config/settings.json` → `updater` flag (toggles the `UpdaterGame` daily scheduler).
  - `config/game_settings.json` → Connect-4 emote shop definitions.
- The MariaDB host/user are **hardcoded** in `SQL.connect()` (`miraculixx.de:3306/MGames`, user `MGamesBot`); only the password comes from config.
- `database-scheme.sql` is the canonical schema. `SQL.ensureSchema()` runs idempotent `ALTER`/migration statements at startup — add new migrations there, not just to the `.sql` file.

## Architecture

`Main` (singleton via `Main.INSTANCE`) boots JDA, then starts five interaction routers and the `SQL` object (its `init` connects + migrates). Entry point: `de.miraculixx.mgames.MainKt`.

### Interaction routing (the core pattern)

Each interaction type has a `*Manager` object that registers one JDA listener and dispatches to a handler keyed by ID:

- `SlashCommandManager` — maps command name → handler, and **also registers all slash commands with Discord in its `init` block**. Adding/renaming a command means editing both the `commands` map and the `updateCommands()` builder here. Admin commands are guild-scoped to the main server (`707925156919771158`).
- `ButtonManager` / `DropDownManager` / `ModalManager` — route by `componentId` **prefix**. Component IDs encode state, e.g. `GAME_4G_YES_<member>_<opponent>` or `TRIVIA:...`. When emitting a component, its ID prefix must match a branch in the corresponding manager's `when`.
- `TabComplete` — autocomplete (e.g. language option).

Handlers implement the interfaces in `utils/entities/` (`SlashCommandEvent`, `ButtonEvent`, `DropDownEvent`, `ModalEvent`), each a single `suspend fun trigger(it)`. Handlers are registered as either fresh instances or `object` singletons in the manager maps.

### Games

- `Game` enum (`modules/games/utils/enums/Game.kt`) is the registry: each entry carries a stable numeric `id` (persisted in `userStats`/`gameHistory` — **never renumber**), `short` tag, `coinValue`, and `supportsDaily`.
- `GameManager` holds running two-player instances in a nested map `Guild → Game → UUID → SimpleGame`, handles matchmaking (`searchGame`/`requestGame`/`newGameVersus`), duplicate detection (bot games match on human players only), surrender, stale cleanup, and shutdown.
- Two-player games (TTT, C4) implement `SimpleGame`; single-player/quiz games (Quick Math, Trivia, Guess-the-Flag) are self-contained `object`s that handle their own button state and don't go through `GameManager`.
- Each game has its own subpackage under `modules/games/` (or `modules/trivia/`) splitting Command / Game-logic / Button / Bot-AI / DropDown.
- Daily challenges use a date-seeded RNG: `SQL.getDailySeed(date)` returns a deterministic seed per date so all players get the same daily puzzle; `userDailyPlay` tracks per-user streaks (global, not per-guild).

### Persistence (`utils/api/SQL.kt`)

- Single shared JDBC `Connection`; `call()`/`update()` are `suspend` and auto-reconnect if the connection drops.
- **Queries are built by string interpolation, not prepared-statement parameters.** Free-text values (trivia, match IDs, game keys) are manually escaped via `.replace("'", "''")`; numeric IDs are trusted as Longs. Follow this existing convention and escape any new string input the same way.
- `getUser`/`getGuild` lazily create rows on first access, so callers can assume a record exists after calling them.

### i18n & logging

- `msg(key, guildID, args)` (`config/MessageExtensions.kt`) resolves translations from `resources/lang/{de_DE,en_US}.yml` via `LanguageManager`, falling back EN → raw key. Args are `%KEY%` placeholders. Guild language is cached in memory and fetched async from SQL on first miss (defaulting EN until loaded) — user-facing strings should go through `msg`, not be hardcoded.
- Console output uses `String.log(Color)` / `String.error()` extensions (ANSI-colored via SLF4J), not raw `println`. Discord-facing colored text uses the separate `Ansi` object (code blocks) and `Colors`/`Icons` for embeds.

## Conventions

- Managers and stateless services are Kotlin `object` singletons.
- JDA calls are async: use `.queue()` for fire-and-forget or `jda-ktx`'s `.await()` inside `suspend` functions.
- Persisted enum IDs (`Game.id`, `GameResult.id`, `GameMode.id`) are part of the DB contract — append new values, don't reorder or reuse old IDs.
