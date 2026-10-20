# Textures needed

This mod never adds texture files. Anything that needs a custom texture is left missing, or falls back to a vanilla texture or text, and is listed here for you to supply.

| Path | Size | Used for | Feature | Fallback until supplied |
|---|---|---|---|---|
| `assets/packapunch/textures/special/zone_highlight.png` | 16×16 | Face texture on the hovered zone outline | `zone-tools-and-outlines.md` | Thicker lines only |
| `assets/packapunch/textures/gui/perk/<perk_id>.png` | 16×16 | HUD icon per default perk (juggernog, speed_cola, double_tap, quick_revive, stamin_up, mule_kick) | `perk-machines.md`, `hud-and-prompts.md` | Colored text badge |
| `assets/packapunch/textures/mob_effect/juggernog.png` | 18×18 | Juggernog's effect icon (top-right HUD, inventory) | `perk-machines.md` | Red square with "J" (`client/PerkEffectIcon`; remove it once the icons exist) |
| `assets/packapunch/textures/mob_effect/speed_cola.png` | 18×18 | Speed Cola's effect icon | `perk-machines.md` | Green square with "S" |
| `assets/packapunch/textures/mob_effect/double_tap.png` | 18×18 | Double Tap's effect icon | `perk-machines.md` | Orange square with "D" |
| `assets/packapunch/textures/mob_effect/quick_revive.png` | 18×18 | Quick Revive's effect icon | `perk-machines.md` | Blue square with "Q" |
| `assets/packapunch/textures/mob_effect/mule_kick.png` | 18×18 | Mule Kick's effect icon | `perk-machines.md` | Dark green square with "M" |
| `assets/packapunch/textures/mob_effect/stamin_up.png` | 18×18 | Stamin-Up's effect icon | `perk-machines.md` | Yellow square with "S" |
| `assets/packapunch/textures/gui/powerup/<id>.png` | 16×16 | HUD timer icon for timed power-ups (insta_kill, double_points, fire_sale) | `power-ups.md`, `hud-and-prompts.md` | Text timer across the top of the HUD |
| `assets/packapunch/textures/entity/powerup/<id>.png` | 16×16 | Floating power-up sprite (max_ammo, insta_kill, double_points, nuke, carpenter, fire_sale) | `power-ups.md` | Renders each power-up's `item` (defaults: spectral arrow, skeleton skull, gold ingot, TNT, oak planks, emerald) |
| `assets/packapunch/textures/gui/points.png` | 16×16 | Points icon on the HUD | `hud-and-prompts.md` | No icon |
| `assets/packapunch/textures/gui/downed.png` | 16×16 | Downed player indicator | `downs-and-revives.md` | Red text |
| `assets/packapunch/textures/block/mystery_box.png` | 16×16 | Optional distinct box look | `mystery-box.md` | Vanilla barrel and chest textures |
| `assets/packapunch/textures/block/pap_machine.png` | 16×16 | Optional distinct PaP machine look | `pack-a-punch.md` | Vanilla textures |
