# Downs and revives

## Goal

A player who takes lethal damage goes down instead of dying. They crawl with a pistol until a teammate revives them or they bleed out. Solo players can self-revive with Saint's Brew.

## Data shape

`LifeState` from `game-loop-and-rounds.md`:
- `Downed(bleedTicks, reviver, reviveProgress)`
- `Spectating(respawnNextRound)`

`PlayerState.selfRevivesLeft` (solo Saint's Brew, default 3).

## Implementation

- **Going down.** `LivingDamageEvent` on an in-game player: if the damage would kill, cancel it, set health to 1, and switch to `Downed`.
  - Remove every beneficial effect, perks included.
  - Force the crawl pose (phase 0 spike for the forced-pose API in 1.20.1).
  - Switch to the best pistol the player holds, or give the starting pistol. Only pistols can be used while downed (gun type from `TaczBridge.inspect`).
  - Emit `player_downed`.
- **Solo Saint's Brew.** If the player is alone, has Saint's Brew, and has self-revives left, start a self-revive timer instead of bleeding out.
- **Zombies** ignore downed players (`TargetGamePlayerGoal` skips them).
- **Reviving.** A teammate holding use within `downs.revive_range` adds progress each tick (faster with Saint's Brew). Moving away or taking damage resets it. At full progress, the downed player returns to `Alive` with half health. Emit `player_revived`.
- **Bleeding out.** When `bleedTicks` reaches 0, switch to `Spectating(respawnNextRound = true)` and game mode spectator. Emit `player_bled_out`. At the next round start, respawn at the start zone with the starting loadout.
- **Game over.** When no player is `Alive`, the game moves to `GameOver`.
- **Regen.** Food is held at 17 so vanilla regen never runs. CoD-style regen heals after `players.regen_delay_ticks` with no damage.

## Config surface

- `downs.bleed_out_ticks` (600), `downs.revive_ticks` (60), `downs.saints_brew_multiplier` (0.5), `downs.revive_range` (2.5).
- `players.solo_self_revives` (3).
- `players.regen_delay_ticks` (80), `players.regen_per_tick`.

## Assets

- Revive progress bar and downed indicator on the HUD (see `hud-and-prompts.md`).
- **Needed from you:** a downed-player icon for the HUD (16×16). Listed in `TEXTURES_NEEDED.md`.

## Depends on

`game-loop-and-rounds.md`, `perk-machines.md`, `tacz-bridge.md`, `mob-profiles-and-ai-injection.md`.

## Verification

- GameTest: lethal damage downs the player instead of killing them, removes their buffs, and zombies stop targeting them.
- GameTest: a teammate reviving for `revive_ticks` restores `Alive`. Moving away resets progress.
- GameTest: bleeding out leads to spectating, and the player respawns at the next round start.
- GameTest: solo with Saint's Brew self-revives 3 times, then bleeds out on the fourth down.

## Phase

6.

## As built

- **Rules** (pure, in `Game`, tested in `DownsTest`):
  - `onLethalDamage(player, world)`: an alive player goes `Downed(bleedTicksLeft, reviver, reviveTicks, reviveNeeded, selfReviving)` and the caller cancels the damage. More damage while down is absorbed. Emits `player_downed`.
  - **Solo Saint's Brew.** A player alone in the game who has an effect in the `hellstorm:self_revive` mob effect tag (Saint's Brew) and has used fewer than `downs.solo_self_revives` starts reviving themselves at once, taking `downs.revive_ticks`. `Game` asks `GameWorld.canSelfRevive` before the player goes down. Going down takes the effect, so each self-revive costs a fresh Saint's Brew, as in CoD.
  - **Reviving.** `holdRevive(reviver, downed)` marks a teammate holding use this tick. A hold carries over 5 ticks between presses, so right click's repeat rate counts as holding. Progress needs the reviver alive, within `downs.revive_range`, and the same reviver throughout. It takes `revive_ticks / revive speed` (the reviver's `hellstorm:revive_speed` attribute: base 1, Saint's Brew +1). Letting go, moving away, or the reviver taking damage (`onPlayerHurt`) resets it. Bleeding pauses while someone is reviving.
  - **Bleeding out.** When `bleedTicksLeft` runs out the player spectates (`player_bled_out`), and comes back at the next round start with a fresh starting loadout (`GameWorld.giveStartingLoadout`).
  - **Game over** when nobody is alive or self-reviving.
  - Downed players still earn points for hits and kills, but can't buy.
  - **Regen.** Alive players heal `downs.regen_per_second` once `downs.regen_delay_ticks` have passed since their last damage.
  - **HUD.** The downed player sees "Bleeding out" (red, draining), "Being revived", or "Reviving" for a self-revive. The reviver sees "Reviving". Downed players' points show in red for everyone.
- **On the player** (`game/Downs`, through `GameWorld.down` / `revive` / `spectate`):
  - Going down removes every beneficial effect (`Perks.clearBuffs`: perks, and any other buff such as Resistance; harmful effects stay), forces the crawl pose (`setForcedPose(SWIMMING)`), slows movement by 60%, sets health to 1, and selects a pistol (TacZ type `pistol`), swapping one into the hotbar if needed. A player with no pistol is lent the first starting pistol (else `tacz:m1911`), tagged `hellstorm_downed_pistol`.
  - Every tick while down they keep crawling and keep the pistol selected. Downed players take no damage at all.
  - **The crawl** (`Downs.crawl`) sets the forced pose and the pose itself after each player tick, on the server (`GameHooks.onPlayerTick`) and on every client (`client/DownedView`, for each player the HUD sync says is down, including you). Two things stopped a forced pose alone from working. It isn't synced, so each client recomputed every player as standing. And TacZ's own crawl (`LivingEntityCrawl.tickCrawling`, at the end of `LivingEntity.tick`) clears the forced pose of anyone holding a gun who isn't crawling by its key, which runs before vanilla applies the forced pose, so it never took on either side. Checked in a live client: pose `SWIMMING` on both sides, eye height 0.4.
  - **Your screen** goes black and white while you're down: the post effect `hellstorm:shaders/post/downed.json`, vanilla's `color_convolve` at saturation 0. It turns off when you get up or the game ends.
  - **Standing up on other clients.** A client never works out another player's pose again by itself, so `DownedView` remembers whom it put in the crawl and sets them back to standing once the HUD stops saying they're down. Without this, other players stayed crawling forever on everyone else's screen after a game.
  - **When the game is lost** (`Game.enterGameOver`), everyone still down is set back to alive and stood up at once (`GameWorld.standUp`, not a revive), so nobody crawls, glows, or sees in grey through the game-over pause. When the game ends, each player is sent an empty HUD (`HudSyncPacket.NONE`), which the client takes as "out of the game" at once rather than when its last HUD goes stale two seconds later.
  - Checked with two live clients over LAN (`runClient2` hosting, a second client joining): a teammate downed mid-round and the game stopped shows standing on both screens on the next tick.
  - **Red glow.** A downed player glows, through walls, for everyone. Glowing takes the team's color, so they join the red team `hellstorm_downed` and go back to their previous team (or none) when they get up, leave, or the game ends.
  - **Chat.** Everyone in the game sees "<name> is downed" in red.
  - Getting up (revive, bleed out, leaving, game end) undoes all of that and takes back the lent pistol. A revive leaves them at half health.
- **Food** is held at 17 for players in a game, so vanilla regen never runs and sprinting still works.
- **Input.** Hold the use key (O) or right click on a downed teammate. The use key sends `RevivePacket(entity)` every 2 ticks; right click goes through `EntityInteract`. The crosshair on a downed teammate shows "Revive <name>".
- **Tests.** JUnit `DownsTest`:
  - lethal damage downs, absorbs more, and zombies stop targeting
  - a teammate's hold revives in `revive_ticks`, and a revive speed of 2 halves it
  - letting go, damage, and range reset
  - bleeding out spectates, then respawns with a fresh loadout next round
  - everyone down ends the game
  - solo Saint's Brew works 3 times and the 4th down ends it (the fake world's `canSelfRevive`, which going down clears)
  - regen waits for the delay
  - downed players earn kill points

  GameTest `DownsGameTests`: real lethal damage downs a FakePlayer who bought and drank a Vigoraid (crawl pose, slowed, pistol held, Vigoraid's max health gone, not targetable), and a teammate's held revive stands them up at half health with the lent pistol gone. `PerkGameTests` checks which effects going down and dying take. `DownedGlowGameTests`: a downed player glows on a red team and gets their own team back when the game ends. The client crawl and the screen effect are checked in game, not by tests.

