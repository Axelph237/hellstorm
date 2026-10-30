# UI needed

The screens and HUD pieces Hellstorm needs designed. **Required** is UI that exists today and needs updating, mostly the machines' settings screens. **Optional** is UI for features that don't exist yet. **Components** are the parts to make, shared by all of them.

Hellstorm adds no texture files of its own (see `TEXTURES.md`). Anything drawn from flat colors, Minecraft's own sprites and item icons can be built now. Themed art comes from you: list each file in `TEXTURES.md` when it's designed.

## Required

### Machine settings screens

Each opens when an op in creative uses the machine outside a game. All five are built on `client/settings/SettingsScreen`, a column of labelled rows with Done and Cancel. Since 2026-10-25 they use the starter kit in `client/ui` (`UiKit`), drawn from your `gui/frame.png`, `gui/input_box.png`, `gui/switch.png` and `gui/spinners.png`: the screen is tinted 90% black, the frame goes around the rows with the title in the gap of its top edge, and buttons, text fields, item slots and dropdown lists are the input box stretched, in its focused, hovered, disabled and pressed looks. Number fields have the − and + spinners in two corners of their box, and yes/no settings are switches.

| Block | Screen | What it sets | Needs |
|---|---|---|---|
| Wall-Buy (`hellstorm:wall_buy`) | `WallBuyScreen` | Item sold (the held item), cost, ammo cost, upgraded ammo cost, buying again, needs power | The new look, and an **item picker**: choose any TacZ gun or item from a searchable list instead of holding it first. This brings back what `/hs wallbuy set <cost> gun <gun>` did. |
| Vending Machine (`hellstorm:perk_machine`) | `WallBuyScreen`, titled "Vending Machine" | The drink sold, cost, needs power | The new look, and the item picker, listing the perk drinks and cans first. |
| Mystery Crate (`hellstorm:mystery_crate`) | `MysteryBoxScreen` | Pool, cost (blank uses `box.cost`) | The new look. A pool picker listing the loaded pools in place of typing an id. |
| Dante's Toil (`hellstorm:pap_machine`) | `PapMachineScreen` | Cost (blank uses `pap.default_cost`) | The new look. |
| Trap Controller (`hellstorm:trap_controller`) | `TrapScreen` | Name, cost, active ticks, cooldown ticks, needs power, signal strength (blank uses the `[traps]` defaults) | The new look. Times shown in seconds as well as ticks. |
| Power Switch (`hellstorm:power_switch`) | `PowerSwitchScreen` | Cost (0 is free) | The new look. |

### In-game HUD

| Piece | Drawn by | Today | Needs |
|---|---|---|---|
| Round counter | `client/hud/RoundCounter` | The round number large in your Roman numerals, top left, on your HUD panel, with the zombies left or the phase under it (countdown, between rounds) | The round-change animation. A D glyph if rounds past 399 should be numerals too. |
| Points | `client/GameHud` | A row per player, middle right by default, anywhere with `[hud.players]` `x` and `y`: points, the first 6 letters of the name, face (red when down, a skeleton's when dead); mirrored on the left | A styled panel. |
| Points popups | `client/GameHud` | "+N" rising off your points as you earn | Styled, with a different look for a headshot or a kill. |
| Power-up timers | `client/GameHud` | Insta-Kill, Double Points and Fire Sale as text along the top, grey as they run out | An icon and a countdown ring or bar for each. |
| Revive and bleed-out bar | `client/GameHud` | A thin colored bar | A styled bar, with the reviver's or downed player's name. |
| Perk icons | `client/PerkEffectIcon` | Your perk icon sheet, top right with the other effects | A row of perk icons on the HUD, if wanted (the sheet's icons could serve). |
| Prompts | `client/PromptOverlay`, `client/WorldPrompt` | "Load-Up [2500]" floating at the machine, or on the HUD (`prompt_style`), with a vending machine's drink lore under it | A key hint for the Hellstorm use key ("[O] Buy", O by default), a style for "No power", and the background. |
| Notices | `client/NoticeOverlay` | One line of text: refusals, "The power is on", round survived | Styled banners: a large one for round start, the power and wave events, a small one for refusals. |
| Downed view | `client/DownedView` | The screen goes black and white while you're down | A bleed-out timer, and "Hold use to revive" for teammates looking at you. |

## Optional

Features that don't exist yet, each needing its own screen.

### Players

| UI | What it does | Replaces |
|---|---|---|
| Lobby screen | Who has joined, the countdown, Join, Leave and Start buttons, the map's name | `/hs game join`, `leave`, `start` |
| Game over screen | The round reached, and each player's points, kills, headshots, downs and revives | Nothing: the game just ends |
| Scoreboard | Held during a game, like the player list: each player's points, kills, headshots and downs | Nothing |
| Spectator banner | "You'll spawn next round" while you wait, with the round's progress | A chat line |

### Map makers

| UI | What it does | Replaces |
|---|---|---|
| Zone manager | Lists every room, door and window. Rooms: rename, delete, start room. Doors: cost, power, style. Windows: boards. Click one to see its outline. | `/hs zones list`, `set`, `remove` |
| Validation report | Errors and warnings as rows, colored by severity. Click one to highlight its spot or teleport there. | `/hs map validate` in chat |
| Map pack browser | The packs in `hellstorm/maps/`, with export (name, force) and import (corner, replace) | `/hs map export`, `import`, `packs` |
| Crate pool editor | A pool's entries and weights, include all TacZ guns, leave out wall-buys | Editing `box_pools/*.json` by hand |
| Game settings editor | The server config's game sections (rounds, economy, players, windows and the rest), for ops in game | Editing `hellstorm-server.toml` |
| Debug panel | The debug rules as toggles (show errors, show paths, no zombies, infinite money, god), and a mob profile picker for testing | `/hs debug`, `/hs mobtest` |

## Components

The shared parts every screen above is built from.

### Layout

- **Panel.** A framed window with a title bar.
- **Tabs.** For screens with sections, such as the game settings editor.
- **Scrolling list.** With a scrollbar, for long lists like the item picker and the validation report.
- **Row.** A label on the left and a field on the right, as the settings screens use.
- **Divider and section header.**
- **Confirm dialog.** For deleting a room or importing over a map.

### Controls

- **Button.** Primary, secondary and danger styles, each with normal, hover, pressed and disabled looks.
- **Toggle.** On and off, for settings like "Needs power".
- **Number field.** With + and − buttons and a red look when the value is out of range.
- **Text field.** With a placeholder hint.
- **Dropdown or selector.** For choices like "Buying again" and the trap's style.
- **Search box.** For filtering a list as you type.
- **Item slot.** Shows the item a machine sells and opens the item picker when clicked.
- **Item list entry.** An item icon, its name and its id.

### Feedback

- **Severity row.** An error, warning or note marker for the validation report.
- **Tooltip.** For explaining a setting, such as what a blank cost means.
- **Error line.** Where the server refuses a setting, under the field.
- **Toast.** A short "Saved" or "Exported" message.

### HUD

- **Big number.** For the round counter, with a change animation.
- **Animated counter.** For points that count up.
- **Icon with label.** For perks and power-ups.
- **Timer ring or bar.** For power-ups and the bleed-out.
- **Progress bar.** For reviving and bleeding out.
- **Key hint.** A key cap, such as "[O]", before a prompt.
- **Banner.** Large and small, for round changes, events and refusals.
