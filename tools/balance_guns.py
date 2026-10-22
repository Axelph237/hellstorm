#!/usr/bin/env python3
"""Writes Hellstorm's default gun balance for TacZ's default gun pack.

Reads each gun's stock stats from the extracted gun pack (TacZ unpacks it into run/tacz/ on first launch) and writes
src/main/resources/data/<gun namespace>/hellstorm/gun_balance/<gun>.json for every gun it changes, plus a Markdown table
of the result. Rerunning it replaces the files it wrote before.

The model, per gun:
- A shot's damage is TacZ's `damage`, which for shotguns is split across the pellets; a shotgun counts PELLETS_LANDED of it.
- Sustained damage per second uses the gun's rpm, capped at SEMI_CLICK_RPM for guns with no automatic mode.
- A gun below its class's DPS floor gets more damage, up to MAX_DAMAGE times.
- Reserve ammo is sized so a full load (magazine plus reserve) deals the class's share of an AK-47's full load. Guns still
  below the floor after the damage boost get more ammo on top, in proportion to what's missing.

Usage: tools/balance_guns.py [gun pack dir]
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
PACK = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "run/tacz/tacz_default_gun"
OUT = ROOT / "src/main/resources/data"
TABLE = ROOT / "features/gun-balance-table.md"

RESERVE_MAGAZINES = 4  # GameSettings.DEFAULT players.reserve_magazines
PELLETS_LANDED = 0.7
SEMI_CLICK_RPM = 450
MAX_DAMAGE = 1.35
RESERVE_RANGE = (0.75, 8.0)
BASELINE = "ak47"

# class: (DPS floor, share of the baseline's full-load damage)
CLASSES = {
    "pistol": (60, 1.0),
    "smg": (85, 1.1),
    "rifle": (85, 1.0),
    "mg": (95, 1.25),
    "shotgun": (90, 1.0),
    "sniper": (75, 0.9),
}
# Launchers do their damage by explosion, which this model doesn't measure: they get more rounds, by hand.
BY_HAND = {
    "rpg7": {"reserve_multiplier": 2.0},
    "m320": {"reserve_multiplier": 3.0},
}


def lenient_json(path):
    text = path.read_text()
    text = re.sub(r"//[^\n]*", "", text)
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return json.loads(re.sub(r",(\s*[}\]])", r"\1", text))


def guns():
    for index_path in sorted((PACK / "data").glob("*/index/guns/*.json")):
        namespace = index_path.parts[-4]
        index = lenient_json(index_path)
        data_ns, data_path = index["data"].split(":")
        data = lenient_json(PACK / "data" / data_ns / "data/guns" / (data_path + ".json"))
        yield namespace, index_path.stem, index["type"], data


def snap(value, step):
    return round(round(value / step) * step, 2)


def clamp(value, low, high):
    return max(low, min(high, value))


def shot(gun_type, data):
    return data["bullet"]["damage"] * (PELLETS_LANDED if gun_type == "shotgun" else 1)


def dps(gun_type, data):
    automatic = "auto" in data.get("fire_mode", [])
    rpm = data["rpm"] if automatic else min(data["rpm"], SEMI_CLICK_RPM)
    return shot(gun_type, data) * rpm / 60


def full_load(gun_type, data, damage=1.0, reserve=1.0):
    magazine = data["ammo_amount"]
    return shot(gun_type, data) * damage * (magazine + round(magazine * RESERVE_MAGAZINES * reserve))


def main():
    all_guns = list(guns())
    baseline = next(d for ns, gid, t, d in all_guns if gid == BASELINE)
    baseline_load = full_load("rifle", baseline)
    for namespace in {ns for ns, _, _, _ in all_guns}:
        for stale in (OUT / namespace / "hellstorm/gun_balance").glob("*.json"):
            stale.unlink()
    rows = []
    for namespace, gun_id, gun_type, data in all_guns:
        if gun_id in BY_HAND:
            balance = BY_HAND[gun_id]
        elif gun_type in CLASSES and "ammo_amount" in data:
            floor, share = CLASSES[gun_type]
            stock_dps = dps(gun_type, data)
            damage = snap(clamp(floor / stock_dps, 1.0, MAX_DAMAGE), 0.05)
            missing = max(0.0, 1 - stock_dps * damage / floor)
            target = baseline_load * share * (1 + missing)
            per_shot = shot(gun_type, data) * damage
            magazine = data["ammo_amount"]
            reserve = snap(clamp((target / per_shot - magazine) / (magazine * RESERVE_MAGAZINES), *RESERVE_RANGE), 0.25)
            balance = {"damage_multiplier": damage, "reserve_multiplier": reserve}
        else:
            print(f"skipped {namespace}:{gun_id} ({gun_type}, no magazine size)", file=sys.stderr)
            continue
        balance = {k: v for k, v in balance.items() if v != 1.0}
        if balance:
            path = OUT / namespace / "hellstorm/gun_balance" / f"{gun_id}.json"
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(balance, indent=2) + "\n")
        rows.append((gun_type, namespace, gun_id, data, balance))

    lines = [
        "# Default gun balance",
        "",
        "Written by `tools/balance_guns.py` from TacZ's default gun pack. Damage is per shot (a shotgun's is the whole shot, split",
        f"across its pellets). Reserve assumes the default `players.reserve_magazines` of {RESERVE_MAGAZINES}. \"Full load\" is magazine",
        f"plus reserve times damage per shot (shotguns at {PELLETS_LANDED:.0%} of their pellets), against the AK-47's stock {baseline_load:.0f}.",
        "",
        "| Gun | Type | Damage | Magazine | Reserve | DPS | Full load |",
        "|---|---|---|---|---|---|---|",
    ]
    for gun_type, namespace, gun_id, data, balance in sorted(rows, key=lambda r: (r[0], r[2])):
        damage = balance.get("damage_multiplier", 1.0)
        reserve = balance.get("reserve_multiplier", 1.0)
        magazine = data["ammo_amount"]
        stock_reserve = magazine * RESERVE_MAGAZINES
        new_reserve = round(stock_reserve * reserve)
        dmg = data["bullet"]["damage"]

        def change(before, after, fmt="{:g}"):
            return fmt.format(before) if before == after else f"{fmt.format(before)} → {fmt.format(after)}"

        explosive = gun_type == "rpg"
        lines.append(f"| `{namespace}:{gun_id}` | {gun_type} | {change(dmg, round(dmg * damage, 1))} | {magazine} | "
                     f"{change(stock_reserve, new_reserve)} | "
                     f"{'explosive' if explosive else change(round(dps(gun_type, data)), round(dps(gun_type, data) * damage))} | "
                     f"{'explosive' if explosive else change(round(full_load(gun_type, data)), round(full_load(gun_type, data, damage, reserve)))} |")
    TABLE.write_text("\n".join(lines) + "\n")
    written = sum(1 for row in rows if row[4])
    print(f"balanced {written} of {len(rows)} guns, wrote {TABLE.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
