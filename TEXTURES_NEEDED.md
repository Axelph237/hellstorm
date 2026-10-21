# Textures needed

This mod never adds texture files. Anything that needs a custom texture is left missing, or falls back to a vanilla texture or text, and is listed here for you to supply.

| Path | Size | Used for | Feature | Fallback until supplied |
|---|---|---|---|---|
| `assets/hellstorm/textures/special/zone_highlight.png` | 16×16 | Face texture on the hovered zone outline | `zone-tools-and-outlines.md` | Thicker lines only |
| `assets/hellstorm/textures/gui/perk/<perk_id>.png` | 16×16 | HUD icon per default perk (vigorade, reload, hellfire, saints_brew, jockey_energy, off_hand) | `perk-machines.md`, `hud-and-prompts.md` | Colored text badge |
| `assets/hellstorm/textures/mob_effect/vigorade.png` | 18×18 | Vigorade's effect icon (top-right HUD, inventory) | `perk-machines.md` | Red square with "J" (`client/PerkEffectIcon`; remove it once the icons exist) |
| `assets/hellstorm/textures/mob_effect/reload.png` | 18×18 | RE:Load's effect icon | `perk-machines.md` | Green square with "S" |
| `assets/hellstorm/textures/mob_effect/hellfire.png` | 18×18 | Hellfire's effect icon | `perk-machines.md` | Orange square with "D" |
| `assets/hellstorm/textures/mob_effect/saints_brew.png` | 18×18 | Saint's Brew's effect icon | `perk-machines.md` | Blue square with "Q" |
| `assets/hellstorm/textures/mob_effect/off_hand.png` | 18×18 | Off-Hand's effect icon | `perk-machines.md` | Dark green square with "M" |
| `assets/hellstorm/textures/mob_effect/jockey_energy.png` | 18×18 | Jockey Energy's effect icon | `perk-machines.md` | Yellow square with "S" |
| `assets/hellstorm/textures/gui/powerup/<id>.png` | 16×16 | HUD timer icon for timed power-ups (insta_kill, double_points, black_friday) | `power-ups.md`, `hud-and-prompts.md` | Text timer across the top of the HUD |
| `assets/hellstorm/textures/entity/powerup/<id>.png` | 16×16 | Floating power-up sprite (max_ammo, insta_kill, double_points, fat_man, pax_romana, black_friday) | `power-ups.md` | Renders each power-up's `item` (defaults: spectral arrow, skeleton skull, gold ingot, TNT, oak planks, emerald) |
| `assets/hellstorm/textures/gui/points.png` | 16×16 | Points icon on the HUD | `hud-and-prompts.md` | No icon |
| `assets/hellstorm/textures/gui/downed.png` | 16×16 | Downed player indicator | `downs-and-revives.md` | Red text |
| `assets/hellstorm/textures/block/mystery_box.png` | 16×16 | Optional distinct box look | `mystery-box.md` | Vanilla barrel and chest textures |
| `assets/hellstorm/textures/block/pap_machine.png` | 16×16 | Optional distinct PaP machine look | `pack-a-punch.md` | Vanilla textures |
