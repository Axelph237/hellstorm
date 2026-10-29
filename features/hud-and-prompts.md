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
  - Round counter (top left, large text, the zombies left under it).
  - A row per player (face, short name, points), the middle of the right side by default, with "+N" popups off your points.
  - Power-up timers (bottom center).
  - Revive bar and downed indicators.
- **Prompts.** When the crosshair targets a feature block within reach, show what it is and its cost in gold brackets, for example "AK-47 [1000]", "Vigoraid [2500]", "Mystery Crate [950]", "Dante's Toil [5000]", or "Door [750]". Show the cost in red when the player can't afford it, and "No power" in red instead when it needs the power on.
- **Sync.** The server sends `HudSyncPacket` deltas once per tick at most. Points popups are client-side animations from the delta.
- **Icons.** Perks show their effect icons (the perk icon sheet) and power-up timers their power-up's item, so neither needs a texture of its own.

## Config surface

- Client config: `hud.scale`, `hud.show_popups`.

## Assets

- **Needed from you:** a points icon and a downed icon (both 16×16). Listed in `NEEDED/TEXTURES.md`.

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
- **`client/GameHud`** draws every element in `client/hud/HudElements` (round, players, timers, bar), in the `hud.scale`d screen.
- **HUD elements** (2026-10-29, `client/hud/HudElement`). Each element is a subclass with an id, which gets its own client config section `[hud.<id>]` from `HudElement.define`: `x` and `y` (0 to 1) and `hidden`, plus anything the subclass adds in `defineMore`. An element measures its box each frame (`size`, empty when it has nothing to show) and is drawn with the box at `MARGIN + fraction × (screen − 2 × MARGIN − box)` on each axis (`HudElement.place`): 0 against the left or top edge, 1 against the right or bottom, 0.5 centred, never off the screen at any size or GUI scale. `ClientConfig` builds the sections from `HudElements.ALL`, which are client classes, so the client config is registered only on the client (`Hellstorm`, through `DistExecutor`). JUnit `client/hud/HudElementTest` (placement, every section's keys, short names).
  - **`round`** (`RoundCounter`, 0, 0): the round number in red at three times text size, and under it the phase while it isn't a plain round ("Starting in 3", "Game over"), otherwise how many zombies are left.
  - **`players`** (`PlayerBoard`, 1, 0.5): a row for every player, in join order. Its own setting, `justify`: `RIGHT` (the default) reads points, name, face, with the faces against the box's right side and the names right-aligned to them; `LEFT` reads face, name, points. The face is the player's skin (8×8 with its hat layer, `PlayerFaceRenderer`, from the client's player list, Steve if they aren't in it), tinted red while they're down; the skeleton's face (`textures/entity/skeleton/skeleton.png`) once they're dead. The name is cut to its first 6 letters and "..." (`PlayerBoard.shortName`). The points sit in a column past the longest name, gold for yours, red while down, grey once dead. Point changes rise off your row's points, on the side away from the name, as gold "+N" or red "-N", and fade over 1.2 seconds.
  - **`timers`** (`PowerUpTimers`, 0.5, 0): the team's timed power-ups in a row, each its power-up's item (16×16, the same item as the floating pickup), then the name and seconds left in gold, grey in flashes over the last 5 seconds.
  - **`bar`** (`ProgressBar`, 0.5, 0.4): see Progress bar. 0.4 puts it about where it was, just above the crosshair, a few pixels off at some GUI scales.
  - **History.** Until 2026-10-29 the round was bottom left and every player's name and points bottom right. That day the layout moved through a points line of your own over the rows, then a `player_list` setting with six fixed spots, before free positions replaced it.
- **`client/PromptOverlay`:** under the crosshair while in a game and looking at a door or a machine with something to sell, it shows "{label} [{cost}]" with gold brackets (just the label when it's free): the gun or item name for a wall-buy (buying, refilling ammo, or topping up), the perk name, "Mystery Crate", "Dante's Toil", "Power", "Door", or "Debris". A Mystery Crate or upgrader result waiting to be taken shows its item name. The cost is red when you can't afford it. Anything that needs power shows "No power" in red instead while the power is off. Doors come from the synced map; machines from their block entity's `Prompted`.
- **World prompts** (`client/WorldPrompt`, the default). The same text floats at the thing itself instead of under the crosshair, turned to face you. `[hud] prompt_style` in the client config picks `WORLD` or `HUD` (the old crosshair prompt).
  - **Where.** Machines: 0.35 above the top of every block the machine covers (`FeatureBlock.blocks`; both halves of the upgrader), pulled 0.4 toward you so a wall-buy's text clears the wall. A machine with a block above it (a trap set into a wall) keeps that height but moves out half a block in front of the face you're looking at first, so the block above doesn't hide it (see `traps.md`). Doors: the face your crosshair hit, out at the door's edge on that side and centered on the whole door; looking at its top or bottom, the side you're nearer. Windows the same, 0.35 out so it clears the boards. A downed teammate: above their head ("Revive <name>"). The revive bar stays on the HUD.
  - **Windows.** "Rebuild barricade" while you look at a window with boards missing, worded like "Revive <name>": no key, no price, since rebuilding is free and pays. Read from the synced map's window and the barricade's `boards` blockstate. Nothing once the window is full.
  - **Per player.** Each client builds the text from its own points, power, inventory, and effects, so the red price and "No power" are yours alone. That is why it isn't a vanilla text display entity, which every player would see the same.
  - **Look.** Only for what you're looking at. Hidden by walls like any block. Name-tag size at 4 blocks or closer, and never smaller on screen beyond that. A faint dark background behind it.
- **Progress bar.** One bar over the crosshair, 100 by 5 GUI pixels on a dark frame, its label above it: "Reviving", "Being revived", "Bleeding out" (red), and "Rebuilding" while you hold use on a window. `Game.status` builds it, as `GameWorld.Bar(label, progress, color, perTick)`. The HUD only arrives every 4 ticks, so `perTick` (how far the bar moves a tick) lets the bar move on smoothly between updates, from how long ago the last one came, capped at full. Without it a board's bar (15 ticks) moved in four jumps and never showed full before the board went on. A rebuilding bar fills over `windows.repair_ticks`, and the board goes on as it fills (see `windows-and-barricades.md`).
- **Client config** (`hellstorm-client.toml`, `[hud]`): `scale` (1.0, 0.5 to 2.0) sizes every HUD element on top of Minecraft's GUI scale: the elements are laid out in the screen size divided by it, so each stays where its `x` and `y` put it. Prompts and notices keep their size. `show_popups` (true) turns off the +N / -N points popups. Each element's `[hud.<id>]` section is above.
- **Text always fits.** `client/WrappedText` breaks any line wider than the screen (or 320 GUI pixels, whichever is smaller) onto more lines. Messages that used to go to the action bar (refusals, wand feedback) now go through `game/Notices` → `NoticePacket` → `client/NoticeOverlay`, drawn wrapped where the action bar sits and faded out after 3 seconds. Perk badges start a new row above when they'd reach the middle of the screen. `InteractionTextLintTest` fails the build on `displayClientMessage(..., true)` or `drawCenteredString` in the mod's code, so new text can't skip the wrapping.
- **Renameable names** (phase 7). Every CoD name players see comes from the lang file, so a resource pack can rename it before a public release: perks and their potions (`effect.hellstorm.*`, `item.minecraft.potion.effect.*`), power-ups (`powerup.hellstorm.*`), machines' block and item names, the upgraded gun (`item.hellstorm.upgraded`), and the two machine names used in text, `name.hellstorm.pack_a_punch` and `name.hellstorm.mystery_crate`. Prompts read those through the client's `Language`. Server messages that name a machine are `Refusal.Translated` and `GameWorld.announceNamed`: translatable keys (`message.hellstorm.box.*`, `message.hellstorm.pap.*`) whose `%s` is filled with the machine's name key, so renaming the machine once renames it in every message. Op-facing text (`describe`, audits, map-maker command feedback) stays in English. `MachineNamesLintTest` fails on a CoD name written into a prompt, refusal, or announcement, and on a `name.`/`message.hellstorm` key missing from `en_us.json`.
- **Command errors** (2026-10-23, `command/CommandErrors`, `util/Highlight`). Every `/hs` command fails the same way, as one chat message: a title in red, underlined, naming what failed ("Unable to validate map", "Unable to join game"), then a line for each error, "ERROR:" in red and the message in white with its key details in gold: ids and names, numbers, positions as (x, y, z), and suggested commands. Warnings that come with them follow as "WARNING:" lines in yellow.
  - **How.** Each command is registered through `CommandErrors.titled(title, command)`, which holds its title. A command fails by throwing `CommandErrors.fail("Room {} has no boxes", id)`: each `{}` is filled by the next detail, marked for gold (`Highlight`). `CommandErrors.failAll(errors, warnings)` gives several lines at once. Any other error the command meets, such as Minecraft's "A player is required to run this command here", is shown under the same title. A marked message is still one plain string, so a validation problem keeps its gold details through the map's problem list and the problems packet; `Highlight.plain` strips the marks.
  - **Kept that way.** JUnit `CommandErrorsLintTest` fails on any `.executes(` not wrapped in `CommandErrors.titled`, and on any `sendFailure` or exception type of our own outside `CommandErrors`. `CommandErrorsTest` checks the layout: the red underlined title, only the title underlined, "ERROR:" red, the message white, its details gold, warnings yellow.
  - **Not covered.** Minecraft's own errors from reading a command before it runs (an unknown subcommand, a bad number) keep Minecraft's format.
