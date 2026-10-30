# Hellstorm: notes for agents

Hellstorm is a Forge 1.20.1 mod (mod id `hellstorm`, package `dev.candycanecazoo.hellstorm`) for Call of Duty: Zombies-style games on top of the TacZ gun mod. Map makers build arenas with tools and commands; players fight rounds of zombies. `README.md` is the user-facing overview, `PLAN.md` the original design and the roadmap, and `features/*.md` one file per feature.

## Build and test

- **Toolchain.** Forge 47.4.10, Parchment mappings, ForgeGradle 6. The Gradle wrapper must stay at **8.8**: ForgeGradle 6 doesn't run on Gradle 9. Run Gradle with JDK 17: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew ...` (the default `java` is newer). The Gradle daemon is on (`gradle.properties`), which saves about 8 s a run; `./gradlew --stop` frees its memory.
- **Unit tests:** `./gradlew test`. Pure logic (map model, validator, settings, formulas, codecs) and lints over the sources.
- **GameTests:** `./gradlew runGameTestServer`, a real dedicated server running every `@GameTest` in `gametest/`, in a fresh flat world in `run-gametest/` each run (`prepareRunGameTestServer` deletes the world and sets `level-type` in its `server.properties`). Not `run/world`: on generated terrain, caves and lava beside the tests break them. The game spawns nothing in a chunk that isn't ticking entities, so a test that spawns zombies on its first tick needs `setupTicks = 5`, and one whose arena reaches past its 9x9 floor must wait for those chunks (`TestLayout.onceTicking`). **Its exit code lies:** it exits 0 when the server fails to start. After every run, grep the log for `All N required tests passed`, and for `Failed to start` or `invalid dist`. Run GameTests, not only unit tests, after anything that touches rendering from a common class.
- **Flaky by nature:** tests that drive TacZ guns run on wall-clock timers. If one times out once, rerun before digging.
- **Client:** `./gradlew runClient`. The tests can't see the screen, and screenshots aren't allowed without asking. Hand visual checks to the user with exact steps and add them to `TO_TEST.md`.
- **Jar:** `./gradlew build` gives `build/libs/hellstorm-<version>.jar` (ship this one, not `-slim`). GeckoLib and LuaJ are bundled with jarJar.

## Rules that aren't obvious from the code

- **Never add texture files.** A feature that needs one leaves it missing or uses a vanilla texture, and gets a row in `NEEDED/TEXTURES.md`. Models may reference vanilla textures. In-memory `DynamicTexture`s are fine. The same goes for sounds: list what's needed in `NEEDED/SOUNDS.md`. Files the user supplies are copied in, converted only as they ask (sounds to Ogg Vorbis: mono if played at a place in the world, stereo if played to a player).
- **Third-party assets.** Every sound is from Pixabay and every Minecraft-derived texture is Mojang's. Each is listed by path in `LICENSE`, `THIRD_PARTY_NOTICES` and `README.md`; a new one is added to all three.
- **Dedicated servers.** Common classes must never name client classes, not even as a lambda's return type: the server's verifier loads them and the server crashes. Build client objects inside `client/`, and reach them from packets through `DistExecutor`. `config/ClientConfig` is registered on the client only, because its HUD sections are built from `client/hud`, so common code never reads it.
- **TacZ boundary.** Only the `tacz/` package imports `com.tacz.*`. Mixins (`tacz/mixin/`, `hellstorm.mixins.json`) have **no refmap**: they may only name TacZ's own methods and fields, never Minecraft's, or they break in production. Minecraft class names in descriptors are fine. TacZ's models draw into Minecraft's shared buffer source and flush it, whatever buffers they're handed: to read a gun's geometry, draw it through `tacz/GunModelCapture`.
- **Commands.** Everything lives under `/hs`. Register every `.executes(...)` through `CommandErrors.titled("Unable to ...", ...)` and fail by throwing `CommandErrors.fail("... {} ...", details)`, never `sendFailure` or your own exception type. `CommandErrorsLintTest` enforces it.
- **Player-facing text.** Messages to players go through `Notices.show`. Names shown to players (perks, the Mystery Crate, the upgrader) come from lang keys in `assets/hellstorm/lang/en_us.json`; lint tests enforce this. The mod uses its own names, not Call of Duty trademarks.
- **Event listeners.** Forge names a generated listener after its package, class, method and event, and silently merges two with the same name. `EventSubscriberLintTest` catches it; give a nested `Registration` class's methods distinct names.
- **Settings.** Game rules live in `config/GameSettings` (a record with a codec), and are exposed in `config/GameSettingsConfig` (per-world `serverconfig/hellstorm-server.toml`). Per-map overrides are designed (`config/SettingsResolver` merges one over the server's) but not wired up: nothing stores or passes a map's override yet. `config/CommonConfig` holds what both sides read at startup. Forge registers renderers before the common config loads, so code that needs a setting then reads the toml itself (`client/ZombieLimbs.dismembermentAtStartup`).
- **HUD.** Anything drawn on the game HUD is a `client/hud/HudElement` listed in `HudElements.ALL`: that gives it a client config section with `x`, `y` and `hidden`, and settings of its own go in `defineMore`.
- **Stay compatible** with NeoForge for 1.20.1.

## Working agreements

- **Docs.** Keep `features/*.md` current as you build, especially each one's "As built" section, which records what really exists and why. The game's user docs live in the wiki, in the sibling folder `../hellstorm-docs`; update the page a change affects.
- **`TO_TEST.md`** lists what's built but not yet checked in game. Add an item with every change that needs a visual or play check; remove items once the user confirms them.
- **Commits.** Commit on `main` as work completes, with messages that say what changed for players or map makers. Author every commit as `candycanecazoo <aiden.j.king004@gmail.com>`: `git -c user.name=candycanecazoo -c user.email=aiden.j.king004@gmail.com commit`. Don't put the user's personal name or local username in any file or commit. The user pushes; suggest `! git push`.
- **Never commit `src/main/java/dev/candycanecazoo/hellstorm/DevLan.java`.** It's local-only, gitignored and kept out of jars.
- **Releases and anything published** (a GitHub release, a pushed tag, a wiki push) need the user's go-ahead first.
- **Scope.** `PLAN.md`'s "Scope after v0.1.0" says what's in the first release, what comes after, and what goes to addons. Stronger zombie AI is deliberately put off until after v0.1.0.
