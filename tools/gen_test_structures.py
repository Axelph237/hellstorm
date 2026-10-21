"""Writes the GameTest structure templates: a stone floor with air above. Run: python3 tools/gen_test_structures.py"""
import gzip
import struct
from pathlib import Path

DATA_VERSION = 3465  # Minecraft 1.20.1
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/data/hellstorm/structures"


def string(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def payload(tag):
    kind, value = tag
    if kind == "int":
        return struct.pack(">i", value)
    if kind == "string":
        return string(value)
    if kind == "list":
        elem_kind, items = value
        ids = {"int": 3, "compound": 10, "end": 0}
        return struct.pack(">bi", ids[elem_kind], len(items)) + b"".join(payload((elem_kind, i)) for i in items)
    if kind == "compound":
        out = b""
        for name, child in value.items():
            out += struct.pack(">b", {"int": 3, "string": 8, "list": 9, "compound": 10}[child[0]]) + string(name) + payload(child)
        return out + b"\x00"
    raise ValueError(kind)


def floor_template(size_x, size_y, size_z):
    blocks = [
        ("compound", {"pos": ("list", ("int", [x, 0, z])), "state": ("int", 0)})
        for x in range(size_x)
        for z in range(size_z)
    ]
    root = {
        "DataVersion": ("int", DATA_VERSION),
        "size": ("list", ("int", [size_x, size_y, size_z])),
        "palette": ("list", ("compound", [{"Name": ("string", "minecraft:stone")}])),
        "blocks": ("list", ("compound", [b[1] for b in blocks])),
        "entities": ("list", ("end", [])),
    }
    return b"\x0a" + string("") + payload(("compound", root))


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / "floor.nbt"
    path.write_bytes(gzip.compress(floor_template(9, 5, 9)))
    print(f"wrote {path}")


if __name__ == "__main__":
    main()
