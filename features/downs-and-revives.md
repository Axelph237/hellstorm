# Downs and revives

## Goal

A player who takes lethal damage goes down instead of dying. They crawl with a pistol until a teammate revives them or they bleed out. Solo players can self-revive with Quick Revive.

## Data shape

`LifeState` from `game-loop-and-rounds.md`:
- `Downed(bleedTicks, reviver, reviveProgress)`
- `Spectating(respawnNextRound)`

`PlayerState.selfRevivesLeft` (solo Quick Revive, default 3).

## Implementation

- **Going down.** `LivingDamageEvent` on an in-game player: if the damage would kill, cancel it, set health to 1, and switch to `Downed`.
  - Remove all perks.
  - Force the crawl pose (phase 0 spike for the forced-pose API in 1.20.1).
  - Switch to the best pistol the player holds, or give the starting pistol. Only pistols can be used while downed (gun type from `TaczBridge.inspect`).
  - Emit `player_downed`.
- **Solo Quick Revive.** If the player is alone, has Quick Revive, and has self-revives left, start a self-revive timer instead of bleeding out.
- **Zombies** ignore downed players (`TargetGamePlayerGoal` skips them).
- **Reviving.** A teammate holding use within `downs.revive_range` adds progress each tick (faster with Quick Revive). Moving away or taking damage resets it. At full progress, the downed player returns to `Alive` with half health. Emit `player_revived`.
- **Bleeding out.** When `bleedTicks` reaches 0, switch to `Spectating(respawnNextRound = true)` and game mode spectator. Emit `player_bled_out`. At the next round start, respawn at the start zone with the starting loadout.
- **Game over.** When no player is `Alive`, the game moves to `GameOver`.
- **Regen.** Food is held at 17 so vanilla regen never runs. CoD-style regen heals after `players.regen_delay_ticks` with no damage.

## Config surface

- `downs.bleed_out_ticks` (600), `downs.revive_ticks` (60), `downs.quick_revive_multiplier` (0.5), `downs.revive_range` (2.5).
- `players.solo_self_revives` (3).
- `players.regen_delay_ticks` (80), `players.regen_per_tick`.

## Assets

- Revive progress bar and downed indicator on the HUD (see `hud-and-prompts.md`).
- **Needed from you:** a downed-player icon for the HUD (16×16). Listed in `TEXTURES_NEEDED.md`.

## Depends on

`game-loop-and-rounds.md`, `perk-machines.md`, `tacz-bridge.md`, `mob-profiles-and-ai-injection.md`.

## Verification

- GameTest: lethal damage downs the player instead of killing them, removes perks, and zombies stop targeting them.
- GameTest: a teammate reviving for `revive_ticks` restores `Alive`. Moving away resets progress.
- GameTest: bleeding out leads to spectating, and the player respawns at the next round start.
- GameTest: solo with Quick Revive self-revives 3 times, then bleeds out on the fourth down.

## Phase

6.
