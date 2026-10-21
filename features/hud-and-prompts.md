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
- **Prompts.** When the crosshair targets a feature block within reach, show what it is and its cost in gold brackets, for example "AK-47 [1000]", "Vigorade [2500]", "Mystery Crate [950]", "Dante's Toil [5000]", or "Door [750]". Show the cost in red when the player can't afford it, and "No power" in red instead when it needs the power on.
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
- **`client/PromptOverlay`:** under the crosshair while in a game and looking at a door or a machine with something to sell, it shows "{label} [{cost}]" with gold brackets (just the label when it's free): the gun or item name for a wall-buy (buying, refilling ammo, or topping up), the perk name, "Mystery Crate", "Dante's Toil", "Power", "Door", or "Debris". A Mystery Crate or upgrader result waiting to be taken shows its item name. The cost is red when you can't afford it. Anything that needs power shows "No power" in red instead while the power is off. Doors come from the synced map; machines from their block entity's `Prompted`.
- **World prompts** (`client/WorldPrompt`, the default). The same text floats at the thing itself instead of under the crosshair, turned to face you. `[hud] prompt_style` in the client config picks `WORLD` or `HUD` (the old crosshair prompt).
  - **Where.** Machines: 0.35 above the top of every block the machine covers (`FeatureBlock.blocks`; both halves of the upgrader), pulled 0.4 toward you so a wall-buy's text clears the wall. A machine with a block above it (a trap set into a wall) keeps that height but moves out half a block in front of the face you're looking at first, so the block above doesn't hide it (see `traps.md`). Doors: the face your crosshair hit, out at the door's edge on that side and centered on the whole door; looking at its top or bottom, the side you're nearer. Windows the same, 0.35 out so it clears the boards. A downed teammate: above their head ("Revive <name>"). The revive bar stays on the HUD.
  - **Windows.** "Rebuild barricade" while you look at a window with boards missing, worded like "Revive <name>": no key, no price, since rebuilding is free and pays. Read from the synced map's window and the barricade's `boards` blockstate. Nothing once the window is full.
  - **Per player.** Each client builds the text from its own points, power, inventory, and effects, so the red price and "No power" are yours alone. That is why it isn't a vanilla text display entity, which every player would see the same.
  - **Look.** Only for what you're looking at. Hidden by walls like any block. Name-tag size at 4 blocks or closer, and never smaller on screen beyond that. A faint dark background behind it.
- **Progress bar.** One bar over the crosshair, 100 by 5 GUI pixels on a dark frame, its label above it: "Reviving", "Being revived", "Bleeding out" (red), and "Rebuilding" while you hold use on a window. `Game.status` builds it, as `GameWorld.Bar(label, progress, color, perTick)`. The HUD only arrives every 4 ticks, so `perTick` (how far the bar moves a tick) lets `GameHud` move it on smoothly between updates, from how long ago the last one came, capped at full. Without it a board's bar (15 ticks) moved in four jumps and never showed full before the board went on. A rebuilding bar fills over `windows.repair_ticks`, and the board goes on as it fills (see `windows-and-barricades.md`).
- **Client config** (`hellstorm-client.toml`, `[hud]`): `scale` (1.0, 0.5 to 2.0) sizes the round, points, and perk rows on top of Minecraft's GUI scale, through `GameHud.scaled`, which lays each layer out in the scaled screen size so it stays in its corner. Prompts and notices keep their size. `show_popups` (true) turns off the +N / -N points popups.
- **Not built yet:** power-up timers and revive bars (phase 6).
- **Text always fits.** `client/WrappedText` breaks any line wider than the screen (or 320 GUI pixels, whichever is smaller) onto more lines. Messages that used to go to the action bar (refusals, wand feedback) now go through `game/Notices` → `NoticePacket` → `client/NoticeOverlay`, drawn wrapped where the action bar sits and faded out after 3 seconds. Perk badges start a new row above when they'd reach the middle of the screen. `InteractionTextLintTest` fails the build on `displayClientMessage(..., true)` or `drawCenteredString` in the mod's code, so new text can't skip the wrapping.
- **Renameable names** (phase 7). Every CoD name players see comes from the lang file, so a resource pack can rename it before a public release: perks and their potions (`effect.hellstorm.*`, `item.minecraft.potion.effect.*`), power-ups (`powerup.hellstorm.*`), machines' block and item names, the upgraded gun (`item.hellstorm.upgraded`), and the two machine names used in text, `name.hellstorm.pack_a_punch` and `name.hellstorm.mystery_crate`. Prompts read those through the client's `Language`. Server messages that name a machine are `Refusal.Translated` and `GameWorld.announceNamed`: translatable keys (`message.hellstorm.box.*`, `message.hellstorm.pap.*`) whose `%s` is filled with the machine's name key, so renaming the machine once renames it in every message. Op-facing text (`describe`, audits, map-maker command feedback) stays in English. `MachineNamesLintTest` fails on a CoD name written into a prompt, refusal, or announcement, and on a `name.`/`message.hellstorm` key missing from `en_us.json`.
