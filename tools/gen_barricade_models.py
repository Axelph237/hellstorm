"""Writes the barricade blockstate and board models (vanilla oak planks only). Run: python3 tools/gen_barricade_models.py"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/packapunch"
BOARDS = 6
# Board models sit on the block's north face. `facing` is the window's outward direction, and boards go on the
# inside face (toward the zone), so each facing rotates the model to its opposite side.
ROTATION = {"north": 180, "east": 270, "south": 0, "west": 90}


def board_model(n):
    y0 = 1 + n * 2.5
    tilt = -4 if n % 2 == 0 else 4
    return {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": {"planks": "minecraft:block/oak_planks", "particle": "minecraft:block/oak_planks"},
        "elements": [{
            "from": [-1, y0, 0], "to": [17, y0 + 2, 2],
            "rotation": {"origin": [8, y0 + 1, 1], "axis": "z", "angle": 0 if n in (0, BOARDS - 1) else (22.5 if tilt > 0 else -22.5)},
            "faces": {face: {"texture": "#planks"} for face in ("north", "south", "up", "down", "east", "west")},
        }],
    }


def main():
    models = ROOT / "models/block"
    models.mkdir(parents=True, exist_ok=True)
    for n in range(BOARDS):
        (models / f"barricade_board_{n + 1}.json").write_text(json.dumps(board_model(n), indent=2) + "\n")
    (models / "barricade_empty.json").write_text(json.dumps({"textures": {"particle": "minecraft:block/oak_planks"}}, indent=2) + "\n")
    parts = [{"apply": {"model": "packapunch:block/barricade_empty"}}]
    for facing, y in ROTATION.items():
        for n in range(1, BOARDS + 1):
            parts.append({
                "when": {"facing": facing, "boards": "|".join(str(b) for b in range(n, BOARDS + 1))},
                "apply": {"model": f"packapunch:block/barricade_board_{n}", "y": y},
            })
    states = ROOT / "blockstates"
    states.mkdir(parents=True, exist_ok=True)
    (states / "barricade.json").write_text(json.dumps({"multipart": parts}, indent=2) + "\n")
    print(f"wrote {BOARDS} board models and blockstates/barricade.json")


if __name__ == "__main__":
    main()
