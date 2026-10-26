# Textures needed

This mod never adds texture files. Anything that needs a custom texture is left missing, or falls back to a vanilla texture or text, and is listed here for you to supply.

| Path | Size | Used for | Feature | Fallback until supplied |
|---|---|---|---|---|
| `assets/hellstorm/textures/special/zone_highlight.png` | 16×16 | Face texture on the hovered zone outline | `zone-tools-and-outlines.md` | Thicker lines only |
| `assets/hellstorm/textures/gui/perk/<perk_id>.png` | 16×16 | HUD icon per default perk (vigorade, reload, hellfire, saints_brew, jockey_energy, off_hand), if the HUD gets a perk row | `perk-machines.md`, `hud-and-prompts.md` | The effect icons on `gui/perk_icons.png` could serve |
| `assets/hellstorm/textures/gui/powerup/<id>.png` | 16×16 | HUD timer icon for timed power-ups (insta_kill, double_points, fire_sale) | `power-ups.md`, `hud-and-prompts.md` | Text timer across the top of the HUD |
| `assets/hellstorm/textures/entity/powerup/<id>.png` | 16×16 | Floating power-up sprite (max_ammo, insta_kill, double_points, nuke, carpenter, fire_sale) | `power-ups.md` | Renders each power-up's `item` (defaults: spectral arrow, skeleton skull, gold ingot, TNT, oak planks, emerald) |
| `assets/hellstorm/textures/gui/points.png` | 16×16 | Points icon on the HUD | `hud-and-prompts.md` | No icon |
| `assets/hellstorm/textures/gui/downed.png` | 16×16 | Downed player indicator | `downs-and-revives.md` | Red text |
| `assets/hellstorm/textures/gui/inputs.png` (add to the sheet) | as drawn | Hover looks for the input box, toggle and spinner halves; a focused look for text fields; disabled looks | `purchasables-foundation.md` | Hover: a faint white fill inside the box (`UiKit.HOVER`), nothing on the toggle and spinner. Focus: the text cursor only. Disabled: not used yet |
