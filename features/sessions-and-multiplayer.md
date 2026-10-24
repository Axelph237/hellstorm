# Sessions and multiplayer

## Goal

Co-op works on dedicated servers from day one. Joining and leaving a game never loses a player's normal inventory, and late joiners and disconnects are handled cleanly.

## Data shape

```java
record InventorySnapshot(UUID player, CompoundTag inventory, CompoundTag effects, GameType gameMode, Vec3 pos, ResourceKey<Level> dim) {}
```

Snapshots are stored in `MapSavedData` so they survive a crash.

## Implementation

- **Joining** (`/hs game join` or a join pad block):
  1. Save an `InventorySnapshot`.
  2. Clear the inventory and effects. The player stays where they are until the game starts; the countdown ending moves everyone to the start room in adventure mode.
  3. Give the starting loadout (configurable `ItemSpec` list, default a TacZ pistol and a knife).
  4. Set starting points.
- **Late join.** Joining during `RoundActive` puts the player in `Spectating(respawnNextRound = true)`.
- **Leaving** (`/hs game leave`, game over, or kick): restore the snapshot exactly and delete it.
- **Disconnect.** The player's state is kept for `sessions.reconnect_grace_ticks`. Reconnecting in time restores their game state. After that they are removed from the game, and their snapshot is restored on their next login.
- **Crash.** On server start, any leftover snapshot is restored the next time that player logs in.
- **Server authority.** Every purchase, revive, and pickup is decided on the server. Clients only send intent packets ("use pressed on block X"), which the server validates.
- **Packets.** One `SimpleChannel` with protocol version checks. Broadcast scope is the players in that game, never `PacketDistributor.ALL`.

## Config surface

- `sessions.starting_loadout` (list of `ItemSpec`).
- `sessions.reconnect_grace_ticks` (1200).
- `sessions.max_players` (4, map overridable).

## Assets

None.

## Depends on

`game-loop-and-rounds.md`, `economy-and-points.md`, `configuration.md`.

## Verification

- GameTest: join then leave restores the exact inventory, effects, game mode, and position.
- In game with 2 clients: one disconnects mid-round and reconnects within the grace time with the same points and perks.
- Kill the server mid-game, restart, log in: the player's normal inventory comes back.
- Two maps in two dimensions run two games at once without sharing state.

## Phase

2 (join and leave), 7 (edge cases).

## As built (phase 7)

- **Joining and leaving** (built in phase 2): the game saves a snapshot (`game/Sessions`: inventory, effects, game mode, health, food, XP, position) once and clears the player when it takes them over: at the end of the countdown for someone who joined before the start (see "As built (waiting for the start)" below), at `/hs game join` for a late joiner. Leaving, game end, or the next login outside a game restores it exactly.
- **Commands live under `/hs game`.** `join`, `leave`, `start`, `stop`, and `status` (see `game-loop-and-rounds.md` for who may run each).
- **Late join.** Joining during a round or the break spectates until the next round (`Game.join`).
- **Player cap.** `players.max_players` (4). `/hs game join` on a full game says so; `Game.join` refuses too.
- **Disconnect grace** (`Game.disconnect`, `Game.reconnect`, `players.reconnect_grace_ticks`, 1200 = one minute).
  - In the lobby or after game over, a disconnect simply leaves the game.
  - Otherwise the player keeps their place and points, and their perks and game inventory stay on their saved player. Someone down becomes a spectator instead of bleeding out unseen, and spectators who are away aren't respawned at the next round. Someone up still counts as fighting, so a dropped connection doesn't end the game by itself.
  - Back within the grace time, they carry on: points as they left them, the game's adventure mode put back (a server may log them in with its own, a LAN world's default for one), or spectator mode if they were out. After it, `Game.tick` removes them; their next login outside a game restores their snapshot, which also takes away the game's perks.
  - On logout nobody is saved crawling (`Downs.standUp`), and only someone who left the game for good loses their perks then.
- **Not hurt outside the fight.** In the lobby, the countdown, and after game over, game players take no damage (`Game.protectsPlayers`).
- **Tests.** JUnit `DisconnectTest`: back in time keeps the place and points; outlasting the grace time removes them; someone away still counts as fighting, until the grace runs out; someone down who drops spectates and isn't respawned while away; the lobby just leaves; a full game takes no more. Checked live over LAN with two clients: a player with 4,821 points disconnected mid-round, was kept (offline, 4,821, still alive) while the round went on, reconnected five seconds later into the same game with the same points and the HUD, in adventure mode.


## As built (waiting for the start)

- **State.** `LifeState.Waiting`, next to `Alive`, `Downed`, and `Spectating`. `Game.join` gives it to anyone who joins in the lobby or the countdown; joining a running game still spectates until the next round. `PlayerState.waiting()` asks for it, and `PlayerState.inPlay()` is now only alive or down.
- **What waiting means.** As in a CoD pregame lobby: you're on the roster, nothing more.
  - The game takes nothing yet. No snapshot, no loadout, no adventure mode, no move: `/hs game join` leaves the inventory, effects, game mode, and position alone. The hooks that run a game's rules on its players use `GameManager.playing`, which skips waiting players: food isn't held at 17, a death is a vanilla death rather than a game one, and the perk limit on drinking doesn't apply (`Perks.limitRefusal`). Leaving, the game ending, or logging out doesn't touch their effects (`Perks.clearBuffs` is skipped) and has no snapshot to restore.
  - No buying: `Game.charge` refuses with `Refusal.NotStarted` ("Wait for the game to start"), so every machine, door, wall-buy, box, trap, and switch refuses. No earning (hits, kills, repairs), no window repairs, no power-ups, no reviving, and nothing targets them (`Game.targetable`).
  - They get the HUD: the phase ("Waiting to start", "Starting in N") and the roster, where they're listed as up.
  - They can `/hs game start` (it's open to players in the game) and `/hs game leave` ("Left the game", no inventory message). `/hs game status` lists them as "waiting for the start".
  - A waiting player who disconnects, in the lobby or the countdown, simply leaves the game (`Game.disconnect`): the start can't take over someone offline.
  - Damage still can't hurt them, as before, since the lobby and countdown protect every game player (`Game.protectsPlayers`).
- **The start.** When the countdown ends, every player (all of them waiting) becomes `Alive`, then `GameWorld.enterGame` saves their snapshot and gives the starting loadout, and `respawn` moves them to a start cell in adventure mode. A late joiner gets `enterGame` from `/hs game join` and then spectates, as before.
- **Tests.**
  - JUnit `GameTest.joiningBeforeTheStartWaitsUntilTheCountdownEnds`: waiting isn't alive or in play, isn't a target, can't buy (`NotStarted`), earns nothing from a repair, can't collect a power-up; someone joining in the countdown waits too; nobody is taken over before the countdown ends; then both are alive, targetable, entered, respawned, and can buy. `lateJoinersSpectateUntilTheNextRound` now checks they spectate rather than wait.
  - JUnit `DisconnectTest.droppingOutDuringTheCountdownJustLeavesToo`.
  - GameTest `GameLoopGameTests.joiningBeforeTheStartWaitsAndTheStartPutsThemInPlay`: through the real `/hs game join` and `/hs game start`, a creative fake player with diamonds keeps them and creative mode, has no snapshot, can't buy, and isn't a target; after the countdown they're alive, a target, able to buy, in adventure mode, saved, and holding the starting pistol; restoring gives the diamonds and creative back.
  - GameTests that buy or fight now play the game first: `PurchaseGameTests.play` starts it and runs the countdown out within the tick, and `PurchaseGameTests.game` calls it.

## Blocks players can't use (2026-10-23)

- **In a game, a player's right-click uses only Hellstorm's own blocks** (`GameHooks.onRightClickBlock`, for players `GameManager.playing` finds, so not while waiting). Doors, trapdoors, fence gates, levers, buttons, chests and every other block stay as the map left them: the event's `useBlock` is denied, so the held item still works (aiming, eating). Buying a door from one of its blocks comes first, as before. Zombies can still open wooden doors.
- **Exceptions** go in the block tag `hellstorm:usable_in_game` (empty), which a map's datapack can add to.
- **The client denies the same clicks** (`client/InGameBlockUse`, while the HUD says it's in a game), so a door doesn't swing open and shut again. The click still goes to the server, which decides.
- **Not covered:** stepping on pressure plates and tripwires, and hitting blocks (a note block still plays).
- **Tests.** GameTest `TerrainGameTests.aPlayerInAGameCantOpenDoorsOrFlipLevers`: in a game, clicking a trapdoor, a door, and a lever leaves them shut and off; after the game, the trapdoor opens.

