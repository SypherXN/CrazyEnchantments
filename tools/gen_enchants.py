"""Generate docs/enchant-list.md from the shipped Enchantments.yml.

Run from the repo root: python tools/gen_enchants.py
Read-only apart from writing the one markdown file.
"""
import collections
import pathlib
import re

import yaml

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "paper/src/main/resources/Enchantments.yml"
OUT = ROOT / "docs/enchant-list.md"

CODE = re.compile(r"&[0-9a-fk-orA-FK-OR]")
TYPE_ORDER = ["Sword", "Axe", "Weapons", "Bow", "Armor", "Helmet", "Chestplate",
              "Leggings", "Boots", "Pickaxe", "Tool", "Weapons-and-Tools",
              "Hoe", "Damaged-Items"]
RARITY_ORDER = ["Common", "Rare", "Elite", "Unique", "Legendary", "Mythical", "Ultimate"]


def clean(value):
    return CODE.sub("", value).strip() if isinstance(value, str) else ""


def main():
    data = yaml.safe_load(SRC.read_text(encoding="utf-8"))["Enchantments"]
    records = []
    for key, body in data.items():
        if not isinstance(body, dict):
            continue
        info = body.get("Info") or {}
        records.append({
            "name": clean(body.get("Name") or key),
            "type": (body.get("Enchantment-Type") or "Unknown").strip(),
            "max": body.get("MaxPower"),
            "rarity": (body.get("Categories") or ["Uncategorised"])[0],
            "desc": [clean(d) for d in (info.get("Description") or []) if clean(d)],
        })

    by_type = collections.defaultdict(list)
    for record in records:
        by_type[record["type"]].append(record)

    def sort_key(record):
        rarity = record["rarity"]
        rank = RARITY_ORDER.index(rarity) if rarity in RARITY_ORDER else 99
        return rank, rarity, record["name"].lower()

    counts = collections.Counter((r["type"], r["rarity"]) for r in records)
    types = [t for t in TYPE_ORDER if t in by_type]
    types += sorted(t for t in by_type if t not in TYPE_ORDER)

    lines = ["# CrazyEnchantments - full enchant list", "",
             "Generated from `paper/src/main/resources/Enchantments.yml`. Rarity is the "
             "shipped `Categories` value; max level is `MaxPower`.", ""]

    for item_type in types:
        rows = sorted(by_type[item_type], key=sort_key)
        lines += [f"## {item_type} ({len(rows)})", ""]
        current = None
        for record in rows:
            if record["rarity"] != current:
                current = record["rarity"]
                lines += [f"### {current}", ""]
            effect = " ".join(record["desc"]) or "_no description_"
            lines.append(f"- **{record['name']}** (max {record['max']}) - {effect}")
        lines.append("")

    lines += ["## Totals", "",
              "| Item type | " + " | ".join(RARITY_ORDER) + " | Total |",
              "|---" * (len(RARITY_ORDER) + 2) + "|"]
    for item_type in types:
        cells = [str(counts[(item_type, r)]) for r in RARITY_ORDER]
        lines.append(f"| {item_type} | " + " | ".join(cells) + f" | {len(by_type[item_type])} |")
    totals = [str(sum(counts[(t, r)] for t in types)) for r in RARITY_ORDER]
    lines.append("| **All** | " + " | ".join(totals) + f" | {len(records)} |")
    lines.append("")

    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {OUT} with {len(records)} enchants across {len(types)} item types")
    print(dict(collections.Counter(r["rarity"] for r in records)))


if __name__ == "__main__":
    main()
