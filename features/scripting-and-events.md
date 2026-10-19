# Scripting and events

## Goal

Map and pack makers react to game events with sandboxed Lua. Scripts can run Minecraft commands, load other scripts, and register listeners. Datapack EventDefs bind events to script functions without code.

## Data shape

```java
sealed interface GameEvent permits GameStart, GameEnd, RoundStart, RoundEnd, ZombieSpawned, ZombieKilled,
    PlayerDowned, PlayerRevived, PlayerBledOut, WallBuy, MysteryBoxOpened, MysteryBoxMoved, PerkBought,
    PapUsed, DoorOpened, PowerOn, TrapActivated, PowerUpSpawned, PowerUpCollected, WaveEventStarted {}

record ZombieKilled(UUID killer, ResourceLocation weapon, boolean headshot, ResourceLocation profile, BlockPos pos) implements GameEvent {}
// ... one record per event
```

Each event has a codec that converts it to a read-only Lua table (snake_case keys).

EventDef, `data/<ns>/packapunch/events/<id>.json`:

```json
{ "event": "round_end", "script": "mymap:announcer", "function": "on_round_end",
  "filter": { "round_min": 5 }, "priority": 0 }
```

Script, `data/mymap/packapunch/scripts/announcer.lua`:

```lua
local util = require("mymap:util")

function on_round_end(e)
  command("title @a title " .. util.json_text("Round " .. e.round .. " survived"))
end

events.on("zombie_killed", function(e)
  if e.headshot then command("playsound minecraft:entity.experience_orb.pickup master @a") end
end)
```

## Implementation

**Event bus.** `GameEvents.emit(game, event)` delivers to subscribers in priority order on the server thread. The Lua runtime is one subscriber. A Forge-event adapter for Java addons can be added later as another. In the MVP events are notifications only. Cancellable events come later.

**Runtime** (`script/LuaRuntime`), one per running game:
- Built at game start from the current datapack scripts and discarded at game end, so globals never leak between games. `/pap script reload` rebuilds it mid-game.
- LuaJ from FiguraMC's maintained fork (`com.github.FiguraMC.luaj:luaj-core` and `luaj-jse`, `3.0.8-figura`, MIT), bundled with Forge jarJar using range `[3.0.8,)`. TacZ already jarJars the same artifacts, so Forge loads one shared copy, and the mod keeps working if TacZ ever drops it. Figura runs untrusted player scripts with this fork, so it is built with sandboxing in mind. Found in the TacZ jar's `META-INF/jarjar/metadata.json` during phase 0.

**Sandbox** (LuaJ's `SampleSandboxed` pattern):
- `Globals` built by hand with only `JseBaseLib`, `PackageLib`, `Bit32Lib`, `TableLib`, `StringLib`, `JseMathLib`. `LuaC` is installed for text chunks only.
- Not loaded: `io`, `os`, `luajava`, `debug`, `coroutine` (exposed only as our own wrapper if needed).
- Removed: `dofile`, `loadfile`, `string.dump`, binary chunk loading.
- Each listener call runs in a coroutine on its own `LuaThread`. A debug hook set from Java (the `debug` table is never visible to scripts) counts instructions and errors after `scripting.instruction_budget`.
- Errors are caught and logged with script id and line. A listener that fails `scripting.max_consecutive_errors` times is disabled for the rest of the game.
- **Scope of the sandbox.** It protects the host machine (no files, network, or reflection). It does not protect the world, since commands run at `scripting.command_permission_level`, the same trust datapack functions get. Memory can't be capped exactly in LuaJ. The instruction budget bounds what one call can allocate.

**API:**
- `events.on(name, fn)` registers a listener. Unknown event names are a load error.
- `command(str)` runs through `server.getCommands().performPrefixedCommand` with a source at the map origin and the configured permission level. Returns `success, result`.
- `require("ns:path")` resolves only against `data/<ns>/packapunch/scripts/<path>.lua` through a custom `package.searchers` entry. Modules are cached. Cycles raise an error naming the chain.
- `log.info/warn/error(msg)`.
- `game.round()`, `game.players()`, `game.points(uuid)`, `game.zone_of(uuid)`, read-only.

**EventDef loading.** A reload listener parses EventDefs. At runtime build, each EventDef looks up its script and function. A missing script or function is a load ERROR shown in `/pap map validate`.

**Contract for other features.** Each feature emits its event as part of being done. This file's catalogue lists them.

## Config surface

- `scripting.enabled` (true).
- `scripting.command_permission_level` (2).
- `scripting.instruction_budget` (1,000,000).
- `scripting.max_consecutive_errors` (5).

## Assets

None.

## Depends on

`game-loop-and-rounds.md` (event bus first appears there), `configuration.md`.

## Verification

- JUnit sandbox tests: `io`, `os`, `luajava`, `debug`, `dofile`, `loadfile`, `string.dump`, and binary `load` are all unreachable from a script.
- JUnit: `while true do end` is stopped by the budget. A listener that errors 5 times is disabled.
- JUnit: `require` loads a helper, caches it (loaded once), and reports a cycle with its chain.
- JUnit: an EventDef with `round_min: 5` fires on round 5 and not round 4.
- In game: a script bound to `zombie_killed`, `wall_buy`, and `mystery_box_opened` runs its command exactly once per occurrence, and the server keeps ticking during a runaway script.

## Phase

3. Events are added by every later phase.
