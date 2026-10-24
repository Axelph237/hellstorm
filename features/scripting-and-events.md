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

EventDef, `data/<ns>/hellstorm/events/<id>.json`:

```json
{ "event": "round_end", "script": "mymap:announcer", "function": "on_round_end",
  "filter": { "round_min": 5 }, "priority": 0 }
```

Script, `data/mymap/hellstorm/scripts/announcer.lua`:

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

**Runtime** (`script/LuaRuntime`), one per game:
- Built when the game's lobby opens (the first `/hs game join` in a level) from the current datapack scripts, and discarded with the game, so globals never leak between games. `/hs scripts reload` rebuilds it for a running game. `/hs scripts check` loads everything with commands switched off and lists the load errors.
- **Entry scripts.** Each namespace's `main` script (`data/<ns>/hellstorm/scripts/main.lua`) runs on build, as does every script an EventDef names. Other scripts run only when something `require`s them.
- LuaJ from FiguraMC's fork (`com.github.FiguraMC.luaj:luaj-core`, `3.0.8-figura`, MIT), compile-only and bundled with jarJar using range `[3.0.8-figura,)`. TacZ jarJars the same artifact, so Forge loads one shared copy (confirmed on a real server boot).

**Sandbox:**
- `Globals` built by hand with only `BaseLib`, `TableLib`, `StringLib`, and `MathLib`. `LuaC` compiles text. No undumper is installed, so binary chunks can't load.
- Not loaded: `io`, `os`, `luajava`, `coroutine` (LuaJ runs each coroutine on its own Java thread), `package` (its Java searcher can load classes), and `bit32` (absent from the Figura fork).
- Removed: `debug`, `dofile`, `loadfile`, `load`, `loadstring`, `collectgarbage`, `module`, `string.dump`. `print` goes to the log. `require` is our own function.
- **Instruction budget.** An `InstructionBudget` subclass of `DebugLib` is the interpreter's instruction hook (the `debug` table is never visible). Each listener call and each script load gets `scripting.instruction_budget` instructions. Past that it throws a `java.lang.Error`, which `pcall` can't catch, so a runaway loop can't swallow its own stop.
- `scripting.max_string_length` caps the bytes a script builds with `string.rep`, `..` (metered by the instruction hook on `OP_CONCAT`, which also charges one instruction per 64 bytes), `string.format`, `string.gsub` and `table.concat`. Deep recursion is caught as a stack overflow, and a listener that runs the server out of memory is switched off at once. Errors name the line that failed: `InstructionBudget` counts, then calls `DebugLib`'s own `onInstruction`, which moves each frame's position (until 2026-10-24 it didn't, so every error named the function's first line).
- **Nested dispatch.** Only the outermost `guarded` call resets the instruction budget: an event a listener's command fires at once runs on what's left of the same budget, so a loop of commands can't multiply the work. `game_start` counts as round 1 for `round_min`/`round_max`, and its table has `round = 1`. `/hs scripts reload` with `scripting.enabled` off stops the game's scripts and fails saying so; `/hs scripts check` still lints and warns. GameTests `ScriptsOffGameTests`.
- **Shared string metatable** (`script/SharedStrings`, 2026-10-24). LuaJ keeps one string metatable for the whole JVM, and TacZ's gun scripts, which load first, own it. Hellstorm installs a router there before its own `StringLib` loads: while a Hellstorm listener runs (`LuaRuntime.guarded` enters it, and `command` leaves it), string methods such as `("x"):rep(n)` go to that runtime's own capped `string` table; otherwise they go to the metatable that was there before, so TacZ's scripts behave as they did. `getmetatable("")` gives the runtime's own metatable, so a script can add its own string methods but can't reach or change TacZ's. Until 2026-10-24 the runtime put TacZ's metatable back, so method calls skipped the `rep` cap, reached `string.dump`, and could change TacZ's string methods.
- Errors are caught and logged with the script and line, and sent in chat to ops in the game. A listener that fails `scripting.max_consecutive_errors` times in a row is disabled for the rest of the game. A success resets the count.
- **Scope of the sandbox.** It protects the host machine (no files, network, threads, or reflection). It does not protect the world: commands run at `scripting.command_permission_level`, the same trust datapack functions get. Memory isn't capped exactly: the instruction budget bounds what one call can build, and `string.rep` is capped (method-call syntax `("x"):rep(n)` uses the shared metatable and isn't).
- **Scripting settings live only in the server config**, never in a map's settings override, so a downloaded map can't raise its own permission level.

**API:**
- `events.on(name, fn)` registers a listener. Unknown event names are a load error.
- `command(str)` runs through `server.getCommands().performPrefixedCommand` with a silent source at the map origin (the center of the first start zone) and the configured permission level. Returns `success, result`.
- `require("ns:path")` resolves only against `data/<ns>/hellstorm/scripts/<path>.lua`. Modules are cached (a module runs once). Cycles raise an error naming the chain, for example `require cycle: a:main -> a:x -> a:y -> a:x`.
- `log.info/warn/error(msg)`.
- `game.round()` and `game.players()` (a list of `{uuid, name, points, alive}`), read-only.

**EventDef loading.** A reload listener parses EventDefs. At runtime build, each EventDef looks up its script and function. A missing script or function is a load ERROR shown in `/hs map validate`.

**Contract for other features.** Each feature emits its event as part of being done. This file's catalogue lists them.

## Config surface

- `scripting.enabled` (true).
- `scripting.command_permission_level` (2).
- `scripting.instruction_budget` (1,000,000).
- `scripting.max_consecutive_errors` (5).
- `scripting.max_string_length` (1,000,000 bytes).
- Per world, `/hs debug show_errors <true|false>` (default true, saved like a gamerule): send script errors to ops' chat. They always go to the log.

## Examples

`examples/datapacks/` (in the repo, not the jar) has `hellstorm_example`, a working map script, and `hellstorm_sandbox_check`, which tries what the sandbox must block.

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
