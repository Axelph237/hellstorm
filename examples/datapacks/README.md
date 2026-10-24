# Example datapacks

Copy a folder into a world's `datapacks/` directory (for a dev world, `run/saves/<world>/datapacks/`), then run `/reload`.
Scripts load when a game opens (`/hs game join`). `/hs scripts reload` picks up edits for a game that's already running.

- `hellstorm_example` adds two mob profiles (skeletons from round 2, rare triple-health iron golems from round 3) alongside the default zombie. It also shows a title when a round ends, says a line every round from round 3 on, and plays a sound on headshot kills. It uses `require`, a module that returns functions, `events.on`, and an EventDef with a round filter. Its upgrade file (`hellstorm/punch/upgrades.json`) turns a stick into a Fire Aspect II blaze rod (`/give @s stick{pap:{punchable:1b,punch_id:"hellstorm_example:stick"}}`) and an AK into a gilded AK with double damage that can still take the default upgrade (`punch_id:"hellstorm_example:ak_gold"`), with its NBT written once as SNBT and once as JSON. Its `defaults` change every upgraded pistol (adds "Hellfire-forged" lore, `merge`), rifle (fires 1.5 times as fast as stock, `modify`), and SMG (loses its "Upgraded" name, `remove`).
- `hellstorm_sandbox_check` checks the sandbox: every line it logs should say "blocked". Its runaway `round_start` listener must be stopped and reported while the server keeps running.
