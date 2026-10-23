"""Regenerates the power catalog table in docs/modules/powers.md from the builder calls in
src/main/java/dev/awaken/powers/catalog/*.java (python3 tools/content/powers_catalog.py).

The table sits between <!-- catalog:start --> and <!-- catalog:end -->. Numbers are the base values before scaling.
"""
import glob
import os
import re

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
SRC = os.path.join(ROOT, "src/main/java/dev/awaken/powers/catalog")
DOC = os.path.join(ROOT, "docs/modules/powers.md")

FAMILY = {"KINETIC": "Kinetic", "VELOCITY": "Velocity", "FLIGHT": "Flight & Gravity", "ENERGY": "Energy",
          "SHADOW": "Shadow", "MIND": "Mind", "ARCHITECT": "Architect", "TECH": "Tech", "SPIRIT": "Spirit"}
STAT = {"KINETIC": "STR", "VELOCITY": "AGI", "FLIGHT": "INT", "ENERGY": "INT", "SHADOW": "SPI", "MIND": "PER",
        "ARCHITECT": "INT", "TECH": "INT", "SPIRIT": "SPI"}


def parse(text):
    out = []
    for m in re.finditer(r'PowerType\.builder\("(\w+)", "([^"]+)", Family\.(\w+), Slot\.(\w+)\)(.*?)\.register\(\);', text, re.S):
        pid, name, fam, slot, chain = m.groups()
        def num(key, default=None):
            mm = re.search(r'\.' + key + r'\(([\d.]+)f?\)', chain)
            return float(mm.group(1)) if mm else default
        rank = re.search(r'\.unlock\(Rank\.(\w+)\)', chain)
        forces = re.search(r'\.forces\(([^)]*)\)', chain)
        desc = re.search(r'\.desc\("((?:[^"\\]|\\.)*)"\)', chain)
        chan = re.search(r'\.(channel|toggle)\(([\d.]+)f?, (-?\d+)\)', chain)
        variants = re.findall(r'Variant\.of\("([^"]+)", "([^"]+)"\)', chain)
        out.append({
            "id": pid, "name": name, "family": fam, "slot": slot.lower(),
            "rank": rank.group(1) if rank else "E", "hidden": ".hidden()" in chain,
            "cost": num("cost", 0), "cd": num("cooldown", 20), "dmg": num("damage", 0),
            "forces": "+".join(f.split(".")[-1].lower() for f in forces.group(1).split(",")) if forces else "",
            "desc": desc.group(1).replace('\\"', '"') if desc else "",
            "mode": chan.group(1) if chan else "", "drain": float(chan.group(2)) if chan else 0,
            "variants": variants,
        })
    return out


def fmt(v):
    return str(int(v)) if v == int(v) else f"{v:g}"


def table(powers):
    lines = []
    order = list(FAMILY)
    powers.sort(key=lambda p: (order.index(p["family"]),))
    current = None
    for p in powers:
        if p["family"] != current:
            current = p["family"]
            lines.append("")
            lines.append(f"### {FAMILY[current]} (scales with {STAT[current]})")
            lines.append("")
            lines.append("| Power | Key | Rank | Energy | Cooldown | Base dmg | Forces | What it does | Variants |")
            lines.append("|---|---|---|---|---|---|---|---|---|")
        cost = fmt(p["cost"]) if p["slot"] != "passive" else "-"
        if p["mode"] == "channel":
            cost += (f" + {fmt(p['drain'])}/s" if p["drain"] else "") + " (hold)"
        elif p["mode"] == "toggle":
            cost += (f" + {fmt(p['drain'])}/s" if p["drain"] else "") + " (toggle)"
        cd = "-" if p["slot"] == "passive" else fmt(p["cd"] / 20) + " s"
        dmg = fmt(p["dmg"]) if p["dmg"] else "-"
        rank = p["rank"] + (" · hidden" if p["hidden"] else "")
        variants = "; ".join(f"**{n}**: {b}" for n, b in p["variants"]) or "-"
        lines.append(f"| **{p['name']}** `{p['id']}` | {p['slot']} | {rank} | {cost} | {cd} | {dmg} | {p['forces'] or '-'} | {p['desc']} | {variants} |")
    return "\n".join(lines).strip("\n")


def main():
    powers = []
    for f in sorted(glob.glob(os.path.join(SRC, "*.java"))):
        powers += parse(open(f).read())
    with open(DOC) as f:
        doc = f.read()
    start, end = "<!-- catalog:start -->", "<!-- catalog:end -->"
    a, b = doc.index(start) + len(start), doc.index(end)
    doc = doc[:a] + "\n" + f"{len(powers)} powers.\n\n" + table(powers) + "\n" + doc[b:]
    with open(DOC, "w") as f:
        f.write(doc)
    print(len(powers), "powers written to", DOC)


if __name__ == "__main__":
    main()
