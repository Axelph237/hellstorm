# Default gun balance

Written by `tools/balance_guns.py` from TacZ's default gun pack. Damage is per shot (a shotgun's is the whole shot, split
across its pellets). Reserve assumes the default `players.reserve_magazines` of 4. "Full load" is magazine
plus reserve times damage per shot (shotguns at 70% of their pellets), against the AK-47's stock 1350.

| Gun | Type | Damage | Magazine | Reserve | DPS | Full load |
|---|---|---|---|---|---|---|
| `tacz:fn_evolys` | mg | 12 | 75 | 300 → 225 | 150 | 4500 → 3600 |
| `tacz:m249` | mg | 7.5 | 75 | 300 → 225 | 94 | 2812 → 2250 |
| `tacz:rpk` | mg | 10 | 40 | 160 → 120 | 105 | 2000 → 1600 |
| `tacz:b93r` | pistol | 7.5 → 7.9 | 20 | 80 → 160 | 56 → 59 | 750 → 1418 |
| `tacz:cz75` | pistol | 5 | 16 | 64 → 256 | 75 | 400 → 1360 |
| `tacz:deagle` | pistol | 16 | 7 | 28 → 77 | 80 | 560 → 1344 |
| `tacz:deagle_golden` | pistol | 12 | 9 | 36 → 108 | 70 | 540 → 1404 |
| `tacz:glock_17` | pistol | 6 → 8.1 | 17 | 68 → 170 | 40 → 54 | 510 → 1515 |
| `tacz:hk_mk23` | pistol | 12 → 16.2 | 12 | 48 → 132 | 10 → 14 | 720 → 2333 |
| `tacz:lonetrail` | pistol | 21.5 → 29 | 1 | 4 → 32 | 32 → 44 | 108 → 958 |
| `tacz:m1911` | pistol | 11 | 7 | 28 → 119 | 64 | 385 → 1386 |
| `tacz:m9a4` | pistol | 6 → 8.1 | 17 | 68 → 170 | 40 → 54 | 510 → 1515 |
| `tacz:p320` | pistol | 10 | 12 | 48 → 120 | 75 | 600 → 1320 |
| `tacz:rhino357` | pistol | 10.5 → 14.2 | 6 | 24 → 108 | 35 → 47 | 315 → 1616 |
| `tacz:taurus500` | pistol | 40 | 5 | 20 → 30 | 80 | 1000 → 1400 |
| `tacz:taurus943` | pistol | 6 → 8.1 | 8 | 32 → 256 | 18 → 24 | 240 → 2138 |
| `tacz:timeless50` | pistol | 15 | 8 | 32 → 80 | 75 | 600 → 1320 |
| `tacz:ak47` | rifle | 9 | 30 | 120 | 90 | 1350 |
| `tacz:aug` | rifle | 7 → 7.4 | 30 | 120 → 150 | 83 → 87 | 1050 → 1323 |
| `tacz:fn_fal` | rifle | 13 → 14.3 | 20 | 80 | 76 → 83 | 1300 → 1430 |
| `tacz:g36k` | rifle | 7 | 30 | 120 → 150 | 91 | 1050 → 1260 |
| `tacz:hk416d` | rifle | 6.5 | 30 | 120 → 180 | 102 | 975 → 1365 |
| `tacz:hk_g3` | rifle | 12 → 14.4 | 20 | 80 | 70 → 84 | 1200 → 1440 |
| `tacz:m16a1` | rifle | 8 | 20 | 80 → 140 | 100 | 800 → 1280 |
| `tacz:m16a4` | rifle | 8 → 10.8 | 30 | 120 | 53 → 72 | 1200 → 1620 |
| `tacz:m4a1` | rifle | 6.5 | 30 | 120 → 180 | 88 | 975 → 1365 |
| `tacz:mk14` | rifle | 16 → 16.8 | 10 | 40 → 70 | 80 → 84 | 800 → 1344 |
| `tacz:qbz_191` | rifle | 7.5 | 30 | 120 → 150 | 94 | 1125 → 1350 |
| `tacz:qbz_95` | rifle | 7.5 → 7.9 | 30 | 120 → 150 | 82 → 87 | 1125 → 1418 |
| `tacz:scar_h` | rifle | 14 | 20 | 80 | 133 | 1400 |
| `tacz:scar_l` | rifle | 7.5 → 7.9 | 30 | 120 → 150 | 81 → 85 | 1125 → 1418 |
| `tacz:sks_tactical` | rifle | 11 → 11.6 | 10 | 40 → 110 | 82 → 87 | 550 → 1386 |
| `tacz:spr15hb` | rifle | 10 → 11.5 | 15 | 60 → 105 | 75 → 86 | 750 → 1380 |
| `tacz:type_81` | rifle | 9 | 30 | 120 | 94 | 1350 |
| `tacz:m320` | rpg | 10 | 1 | 4 → 12 | explosive | explosive |
| `tacz:rpg7` | rpg | 20 | 1 | 4 → 8 | explosive | explosive |
| `tacz:aa12` | shotgun | 30 | 8 | 32 → 56 | 122 | 840 → 1344 |
| `tacz:db_long` | shotgun | 30 → 40.5 | 2 | 8 → 64 | 35 → 47 | 210 → 1871 |
| `tacz:db_short` | shotgun | 24 → 32.4 | 2 | 8 → 64 | 42 → 57 | 168 → 1497 |
| `tacz:m1014` | shotgun | 40 | 6 | 24 → 42 | 93 | 840 → 1344 |
| `tacz:m870` | shotgun | 36 → 43.2 | 5 | 20 → 40 | 76 → 91 | 630 → 1361 |
| `tacz:spas_12` | shotgun | 64 | 5 | 20 → 25 | 149 | 1120 → 1344 |
| `tacz:hk_mp5a5` | smg | 6 → 6.3 | 30 | 120 → 210 | 82 → 86 | 900 → 1512 |
| `tacz:p90` | smg | 5.5 → 6.3 | 50 | 200 | 74 → 85 | 1375 → 1581 |
| `tacz:ump45` | smg | 9 | 25 | 100 → 150 | 99 | 1125 → 1575 |
| `tacz:uzi` | smg | 6.5 → 8.5 | 20 | 80 → 160 | 65 → 84 | 650 → 1521 |
| `tacz:vector45` | smg | 7 | 20 | 80 → 200 | 140 | 700 → 1540 |
| `tacz:ai_awp` | sniper | 42 | 5 | 20 → 25 | 120 | 1050 → 1260 |
| `tacz:kar98` | sniper | 26 | 4 | 16 → 44 | 108 | 520 → 1248 |
| `tacz:m107` | sniper | 55 | 10 | 40 → 30 | 367 | 2750 → 2200 |
| `tacz:m700` | sniper | 24 → 25.2 | 5 | 20 → 45 | 72 → 76 | 600 → 1260 |
| `tacz:m95` | sniper | 75 | 5 | 20 → 15 | 189 | 1875 → 1500 |
| `tacz:springfield1873` | sniper | 35 → 47.2 | 1 | 4 → 26 | 52 → 71 | 175 → 1276 |
