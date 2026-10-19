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
- Our keys: `pap.tier` (int), used by the default TacZ upgrade in `pack-a-punch.md`.

## Implementation

`tacz/TaczBridge` is the only class that imports `com.tacz.*`.

- **Gun stacks.** `ItemStack createGun(ResourceLocation gunId)` uses `GunItemBuilder.create().setId(gunId).setAmmoCount(maxMag).build()`, then calls `IGun#setMaxDummyAmmoAmount` and `IGun#setDummyAmmoAmount` so reserve ammo lives in NBT, not in ammo items.
- **Gun listing.** `List<ResourceLocation> allGuns()` reads `TimelessAPI.getAllCommonGunIndex()`. The box pool and `PunchAudit` use it.
- **Identity.** `Optional<GunInfo> inspect(ItemStack)` uses `IGun.getIGunOrNull(stack)` and `TimelessAPI.getCommonGunIndex(id)`. How to read the gun type from `CommonGunIndex` is a phase 0 spike.
- **Ammo.** `refillAmmo(ItemStack)` sets the magazine to full and the dummy reserve to its max. Max Ammo, wall-buy refills, and PaP call it.
- **Hit and kill events.** A `@SubscribeEvent` on `EntityHurtByGunEvent.Pre` and `EntityKillByGunEvent` returns early unless `getLogicalSide()` is server, because TacZ posts each event on both sides. It converts each one into a `GunHit` or `GunKill` and hands it to the game (`Economy`, `GameEvents`). The events carry the gun id but not the stack, so the bridge reads `attacker.getMainHandItem()` when it needs the stack.
- **Damage changes.** Inside the same `Pre` handler the bridge asks the game for a damage multiplier (Insta-Kill, PaP tier, perks) and calls `setBaseAmount(base * multiplier)`. TacZ reads the value back after posting.
- **Per-stack stats.** `AttachmentPropertyEvent` gives `getGunItem()` and an `AttachmentCacheProperty`. For stacks with `pap.tier`, the bridge scales `GunProperties.DAMAGE`, `ROUNDS_PER_MINUTE`, and the mag size. Double Tap scales RPM the same way. This class is outside TacZ's `api` package, so it is the most fragile seam.

## Config surface

- `tacz.reserve_ammo_mags`: reserve size in magazines for new guns (default 8).
- `tacz.pap_damage_multiplier`, `tacz.pap_rpm_multiplier`, `tacz.pap_mag_multiplier`.
- All values resolve through `GameSettings` (see `configuration.md`).

## Assets

None.

## Depends on

`configuration.md`.

## Verification

- `/pap give tacz:ak47` gives a working AK with a full magazine and a reserve stored as dummy ammo, and no ammo items appear in the inventory.
- Shooting a zombie produces one `GunHit` on the server and none on the client (log counter).
- A headshot kill produces `GunKill` with `headshot = true`.
- Phase 0 spike: an `AttachmentPropertyEvent` override visibly changes RPM and mag size in live fire, and dummy ammo works with every reload type in the default gun pack.

## Phase 0 spike results (2026-10-19)

Run with `./gradlew runGameTestServer` (`gametest/TaczSpikeTests`). All four pass.

- **Dummy ammo works for every magazine-fed gun.** All 53 magazine-fed guns in the default pack reload from dummy ammo.
- **`inventory` feed guns can't use dummy ammo.** TacZ's reload returns early when `useInventoryAmmo` is true, so dummy ammo never applies. In the default pack that is only `tacz:minigun`. The bridge must give these guns real ammo items (`AmmoItemBuilder`) for reserve and refills.
- **Always set a fire mode.** `createGun` sets the first mode from `GunData.getFireModeSet()`. Without it, scripted guns stall: `tacz:hk_mk23`'s Lua reload logic only sets timings for `SEMI` and `BURST`, so its reload never finished.
- **The RPM override works in live fire.** Setting `ROUNDS_PER_MINUTE` to 60 through `AttachmentPropertyEvent` is cached and enforced. 330 ms after the first shot, the stock AK fires again and the 60 RPM AK returns `COOL_DOWN`.
- **Mag size can't be changed through the cache.** `GunProperties` has no capacity entry, and capacity comes from `AttachmentDataUtils.getAmmoCountWithAttachment` (extended-mag level). The PaP mag boost installs an extended-mag attachment instead.
- **Gun type for the downed-pistol rule** is `CommonGunIndex.getType()` (for example `"pistol"`).
- **TacZ timers use wall-clock time.** Draw, reload, and shoot timers use `System.currentTimeMillis`, and the GameTest server ticks faster than real time, so GameTests involving them must poll for completion, not wait a fixed number of ticks.
- **Vanilla side:** `Mob.goalSelector` and `targetSelector` are accessible without an access transformer. `Player.setForcedPose(Pose.SWIMMING)` produces the crawl pose and hitbox.

## Phase

0 (scaffold and spikes), extended in 5 (PaP, perks).
