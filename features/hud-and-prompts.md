# HUD and prompts

## Goal

Players always know the round, everyone's points, their perks, active power-ups, and what the thing they are looking at costs.

## Data shape

Client-side mirror, updated by packets:

```java
record HudState(int round, List<PlayerLine> players, List<ResourceLocation> myPerks,
                Map<ResourceLocation, Integer> activePowerUps, Optional<ReviveBar> revive) {}
record PlayerLine(UUID id, String name, int points, LifeKind life) {}
```

Look-at prompts read the targeted feature BlockEntity's synced data (`getUpdateTag`).

## Implementation

- **Overlays** registered with `RegisterGuiOverlaysEvent`:
  - Round counter (bottom left, large text).
  - Points list (bottom right, one line per player, own line highlighted, with "+N" popups on award).
  - Perk row (bottom left above the round).
  - Power-up timers (bottom center).
  - Revive bar and downed indicators.
- **Prompts.** When the crosshair targets a feature block within reach, show what it is and its cost in gold brackets, for example "AK-47 [1000]", "Juggernog [2500]", "Mystery Box [950]", "Pack-A-Punch [5000]", or "Door [750]". Show the cost in red when the player can't afford it, and "No power" in red instead when it needs the power on.
- **Sync.** The server sends `HudSyncPacket` deltas once per tick at most. Points popups are client-side animations from the delta.
- **Text only until icons exist.** Perks and power-ups render their name and color as text badges until you supply icons.

## Config surface

- Client config: `hud.scale`, `hud.show_popups`.

## Assets

- **Needed from you:** perk icons, power-up icons, a points icon, a downed icon (all 16×16). Listed in `TEXTURES_NEEDED.md`. The HUD falls back to text badges.

## Depends on

`economy-and-points.md`, `perk-machines.md`, `power-ups.md`, `downs-and-revives.md`.

## Verification

- In game with 2 clients: both see each other's points update within a tick of a kill.
- Looking at a door with too few points shows the refusal text.
- Missing icons fall back to text badges without errors in the log.

## Phase

5, extended in 6.

## As built (phase 5)

- **Sync.** `HudSyncPacket(round, phase, zombiesLeft, powerOn, points, players)` goes to each player every 4 ticks, built from `Game.status` (which now carries every player's standing). The client treats a HUD more than 2 seconds old as "not in a game". The action-bar status line is gone, which leaves the action bar for purchase refusals.
- **`client/GameHud`:**
  - The round number in red at three times text size, bottom left.
  - The phase under it while it isn't a plain round ("Starting in 3", "Game over"); otherwise how many zombies are left.
  - Every player's name and points, bottom right. Your own row is gold and downed or spectating players are grey.
  - Point changes rise off your row as gold "+N" or red "-N" and fade over 1.2 seconds.
- **`client/PromptOverlay`:** under the crosshair while in a game and looking at a door or a machine with something to sell, it shows "{label} [{cost}]" with gold brackets (just the label when it's free): the gun or item name for a wall-buy (buying, refilling ammo, or topping up), the perk name, "Mystery Box", "Pack-A-Punch", "Power", "Door", or "Debris". A Mystery Box or Pack-a-Punch result waiting to be taken shows its item name. The cost is red when you can't afford it. Anything that needs power shows "No power" in red instead while the power is off. Doors come from the synced map; machines from their block entity's `Prompted`.
- **Not built yet:** the perk row (with perks), power-up timers and revive bars (phase 6), and the `hud.scale` / `hud.show_popups` client config.
- **Text always fits.** `client/WrappedText` breaks any line wider than the screen (or 320 GUI pixels, whichever is smaller) onto more lines. Messages that used to go to the action bar (refusals, wand feedback) now go through `game/Notices` → `NoticePacket` → `client/NoticeOverlay`, drawn wrapped where the action bar sits and faded out after 3 seconds. Perk badges start a new row above when they'd reach the middle of the screen. `InteractionTextLintTest` fails the build on `displayClientMessage(..., true)` or `drawCenteredString` in the mod's code, so new text can't skip the wrapping.
