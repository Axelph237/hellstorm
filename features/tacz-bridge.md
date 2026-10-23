# TacZ bridge

## Goal

TacZ is reached through exactly one package. Every other system asks the bridge for what it needs: gun stacks, ammo, hit and kill facts, damage changes, and per-stack stat changes. When TacZ updates, only this package changes.

## Data shape

```java
record GunHit(ServerPlayer attacker, LivingEntity target, ResourceLocation gunId, boolean headshot, float amount) {}
record GunKill(ServerPlayer attacker, LivingEntity target, ResourceLocation gunId, boolean headshot) {}
record GunInfo(ResourceLocation gunId, String type, boolean papTier) {}   // type: "pistol", "rifle", ...
```

Stack NBT the bridge reads and writes:
- TacZ keys: `GunId`, `GunCurrentAmmoCount`, `DummyAmmo`, `MaxDummyAmmo` (from `GunItemDataAccessor`).
- Our keys: `pap.tier` (int), used by the default TacZ upgrade in `upgrader.md`.

## Implementation

`tacz/TaczBridge` is the only class that imports `com.tacz.*`.

- **Gun stacks.** `ItemStack createGun(ResourceLocation gunId)` uses `GunItemBuilder.create().setId(gunId).setAmmoCount(maxMag).build()`, then calls `IGun#setMaxDummyAmmoAmount` and `IGun#setDummyAmmoAmount` so reserve ammo lives in NBT, not in ammo items.
- **Gun listing.** `List<ResourceLocation> allGuns()` reads `TimelessAPI.getAllCommonGunIndex()`. The box pool and `PunchAudit` use it.
- **Identity.** `Optional<GunInfo> inspect(ItemStack)` uses `IGun.getIGunOrNull(stack)` and `TimelessAPI.getCommonGunIndex(id)`. How to read the gun type from `CommonGunIndex` is a phase 0 spike.
- **Ammo.** `refillAmmo(ItemStack)` sets the magazine to full and the dummy reserve to its max. Max Ammo, wall-buy refills, and the upgrader call it.
- **Unlimited ammo.** `setUnlimitedAmmo(stack, true)` tags a gun (`hellstorm.unlimited_ammo` in its NBT). While any entity holds it, its dummy reserve is topped back to max every tick (`TaczEvents.onLivingTick`). The magazine still empties and needs reloading. Topping up only on TacZ's reload or fire events isn't enough: TacZ refuses a reload before posting its reload event once the reserve is empty, and a single reload can use up the whole reserve. The starting guns use it when `players.starting_guns_unlimited_ammo` is on (default on). Wall-buys and box entries get a per-weapon `unlimited_ammo` field in phase 5.
- **Hit and kill events.** A `@SubscribeEvent` on `EntityHurtByGunEvent.Pre` and `EntityKillByGunEvent` returns early unless `getLogicalSide()` is server, because TacZ posts each event on both sides. It converts each one into a `GunHit` or `GunKill` and hands it to the game (`Economy`, `GameEvents`). The events carry the gun id but not the stack, so the bridge reads `attacker.getMainHandItem()` when it needs the stack.
- **Gun balance.** Per-gun damage and reserve multipliers from datapacks scale every gun's reserve when it's made and every hit on the server. See `gun-balance.md`.
- **Damage changes.** Inside the same `Pre` handler the bridge asks the game for a damage multiplier (Insta-Kill, upgrade tier, perks) and calls `setBaseAmount(base * multiplier)`. TacZ reads the value back after posting.
- **Per-stack stats.** `AttachmentPropertyEvent` gives `getGunItem()` and an `AttachmentCacheProperty`. For stacks with `pap.tier`, the bridge scales `GunProperties.DAMAGE`, `ROUNDS_PER_MINUTE`, and the mag size. Hellfire scales RPM the same way. This class is outside TacZ's `api` package, so it is the most fragile seam.

## Config surface

- `tacz.reserve_ammo_mags`: reserve size in magazines for new guns (default 8).
- `tacz.pap_damage_multiplier`, `tacz.pap_rpm_multiplier`, `tacz.pap_mag_multiplier`.
- All values resolve through `GameSettings` (see `configuration.md`).

## Assets

None.

## Depends on

`configuration.md`.

## Verification

- `/hs give tacz:ak47` gives a working AK with a full magazine and a reserve stored as dummy ammo, and no ammo items appear in the inventory.
- Shooting a zombie produces one `GunHit` on the server and none on the client (log counter).
- A headshot kill produces `GunKill` with `headshot = true`.
- Phase 0 spike: an `AttachmentPropertyEvent` override visibly changes RPM and mag size in live fire, and dummy ammo works with every reload type in the default gun pack.

## Phase 0 spike results (2026-10-19)

Run with `./gradlew runGameTestServer` (`gametest/TaczSpikeTests`). All four pass.

- **Dummy ammo works for every magazine-fed gun.** All 53 magazine-fed guns in the default pack reload from dummy ammo.
- **`inventory` feed guns can't use dummy ammo.** TacZ's reload returns early when `useInventoryAmmo` is true, so dummy ammo never applies. In the default pack that is only `tacz:minigun`. The bridge must give these guns real ammo items (`AmmoItemBuilder`) for reserve and refills.
- **Always set a fire mode.** `createGun` sets the first mode from `GunData.getFireModeSet()`. Without it, scripted guns stall: `tacz:hk_mk23`'s Lua reload logic only sets timings for `SEMI` and `BURST`, so its reload never finished.
- **The RPM override works in live fire.** Setting `ROUNDS_PER_MINUTE` to 60 through `AttachmentPropertyEvent` is cached and enforced. 330 ms after the first shot, the stock AK fires again and the 60 RPM AK returns `COOL_DOWN`.
- **Mag size can't be changed through the cache.** `GunProperties` has no capacity entry, and capacity comes from `AttachmentDataUtils.getAmmoCountWithAttachment` (extended-mag level). The upgrader's mag boost installs an extended-mag attachment instead.
- **Gun type for the downed-pistol rule** is `CommonGunIndex.getType()` (for example `"pistol"`).
- **TacZ timers use wall-clock time.** Draw, reload, and shoot timers use `System.currentTimeMillis`, and the GameTest server ticks faster than real time, so GameTests involving them must poll for completion, not wait a fixed number of ticks.
- **Vanilla side:** `Mob.goalSelector` and `targetSelector` are accessible without an access transformer. `Player.setForcedPose(Pose.SWIMMING)` produces the crawl pose and hitbox.

## As built (no terrain damage)

While a match is underway in a level (`GameManager.matchUnderway`: started, from the first round until the game is over), nothing there breaks, burns, or changes a block, guns included: the map is only reset through its features, so terrain lost in play would stay lost. In the lobby and outside games, explosions and bullets work as they always do, so map makers' worlds behave normally. TacZ's own switches for this are in its common config (`AmmoConfig`), which is global, not per world, so the hooks below check the level instead. TacZ changes terrain three ways:

- **Explosive ammo** (rockets, grenade launchers). `EntityKineticBullet` calls `ExplodeUtil.createExplosion`, which runs a `ProjectileExplosion` (an `Explosion` subclass) with `BlockInteraction.DESTROY` when the ammo's `explosion_destroys_block` and the config `ExplosiveAmmoDestroysBlock` are on, and fire when `ExplosiveAmmoFire` is on. Its `explode()` posts Forge's `ExplosionEvent.Detonate`. During a match `GameHooks.onExplosion` empties the affected-block list (below). The blast still hurts and knocks back.
- **No explosion push in a match** (2026-10-23, `mixin/ProjectileExplosionMixin`). In CoD explosives hurt players but don't throw them. `ProjectileExplosion.explode()` reads its `knockback` flag once per entity it hurt, and that flag guards both the push (`setDeltaMovement`) and the knockback sent to a hit player's client in the explode packet. The mixin redirects that read to false while a match is underway in the level, so nothing is pushed, players or zombies; the damage is unchanged. It redirects a TacZ field, not a Minecraft method, so it needs no refmap. Vanilla explosions aren't covered: they have no such flag, and changing them would need a mixin on Minecraft's `Explosion`, which this build has no refmap for. GameTest `KnockbackGameTests.aGunsExplosionPushesNothingDuringAMatch`: TacZ's explosion pushes a zombie before a match, not during it, and again after. Run with the mixin left out, it fails (pushed 0.052 during the match). It uses a zombie because a fake player can't be hurt, and TacZ pushes only what it hurt.
- **Glass.** On every block hit, `EntityKineticBullet.onHitBlock` posts TacZ's `AmmoHitBlockEvent`, and TacZ's own handler `DestroyGlassBlock` calls `level.destroyBlock` on glass, stained glass, and panes (config `DestroyGlass`). `destroyBlock` posts no Forge event. Cancelling `AmmoHitBlockEvent` would skip the rest of the hit, so the bullet would fly on through the block. Instead `mixin/DestroyGlassBlockMixin` cancels the handler at its head during a match.
- **Incendiary rounds.** For a bullet with `ignite_block` (from its ammo or an attachment), `onHitBlock` places fire in front of the face it hit when the config `IgniteBlock` is on. `mixin/EntityKineticBulletMixin` redirects that config read, the only one in the method, to false during a match. Setting the mob it hits on fire (`IgniteEntity`) stays, and the lava particles still show.

During a match, every explosion in the level leaves the blocks alone, a gun's or any other (`GameHooks.onExplosion` empties `ExplosionEvent.Detonate`'s block list; vanilla's fire lands only on those blocks, and the packet clients get uses the same list, so there are no ghost holes). The glass and fire mixins check `matchUnderway` too.

Not covered: vanilla's own reactions to a projectile that is itself on fire (TNT, campfires, candles), which a bullet only is after passing through fire or lava.

Tests (`gametest/TerrainGameTests`): outside a match a vanilla blast and TacZ's `ExplodeUtil.createExplosion` (block damage asked for) both break a shell of glass, and a bullet with `ignite_block` run through TacZ's `onHitBlock` breaks glass and lights the floor; a game still in its lobby doesn't stop a gun's blast; in a started match, both blasts leave the shell whole and the bullet leaves the glass and starts no fire.

## Phase

0 (scaffold and spikes), extended in 5 (the upgrader, perks).
