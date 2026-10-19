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
- **Prompts.** When the crosshair targets a feature block within reach, show "Press [Use] to buy AK-47 [1000]" or the matching text for doors, perks, the box, PaP, traps, and the power switch. Show the refusal reason if the purchase would fail (not enough points, needs power).
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
