#!/usr/bin/env python3
"""Builds page.html from page.src.html: embeds the game's own block textures for the demos and turns PLAN.md's
sections 2 onward into the page's collapsible engineering notes. Run from anywhere; touches only docs/made-by-hand."""
import base64
import html
import json
import pathlib
import re

HERE = pathlib.Path(__file__).resolve().parent
TEX = HERE.parent.parent / "src/main/resources/assets/awaken/textures/block"

# (look id, name, where it comes from, texture file). Only AWAKEN's own art, so the page carries no vanilla textures.
LOOKS = [
    ("ashlar", "Ashlar", "Carillon", "ashlar_1"),
    ("city_bricks", "City brick", "Carillon", "city_bricks_4"),
    ("plaster", "Rose plaster", "Carillon", "plaster_rose_3"),
    ("timber", "Pale timber", "Carillon", "timber_pale_side_1"),
    ("slate_tiles", "Slate tiles", "Carillon", "slate_tiles"),
    ("clay_tiles", "Clay tiles", "Carillon", "clay_tiles"),
    ("crypt_tracery", "Crypt tracery", "a crypt gate", "crypt_tracery"),
    ("crypt_flagstone", "Wet flagstone", "a crypt gate", "crypt_flagstone_wet"),
    ("desert_tile", "Desert tile", "a desert gate", "desert_tile"),
    ("desert_brick", "Desert brick", "a desert gate", "desert_brick"),
    ("choir_bricks", "Choir brick", "the Descent", "choir_bricks"),
    ("abyss_tiles", "Abyss tile", "an abyss gate", "abyss_tiles"),
    ("court_tile", "Court tile", "the Descent", "court_tile"),
    ("foundry_brick", "Foundry brick", "a foundry gate", "foundry_brick"),
    ("foundry_brass", "Foundry brass", "a foundry gate", "foundry_brass"),
    ("ember_basalt", "Ember basalt", "an ember gate", "ember_basalt"),
    ("archive_planks", "Archive planks", "an archive gate", "archive_planks"),
    ("bluepine_planks", "Bluepine", "the Awakened Lands", "bluepine_planks"),
    ("elder_planks", "Elder wood", "the Awakened Lands", "elder_planks"),
    ("dream_wallpaper", "Torn wallpaper", "a Lucid Dream", "dream_wallpaper_torn"),
]


def looks_json() -> str:
    out = []
    for id_, name, src, file in LOOKS:
        data = (TEX / f"{file}.png").read_bytes()
        out.append({"id": id_, "name": name, "from": src, "data": "data:image/png;base64," + base64.b64encode(data).decode()})
    return json.dumps(out, separators=(",", ":"))


def inline(text: str) -> str:
    t = html.escape(text, quote=False)
    t = re.sub(r"`([^`]+)`", r"<code>\1</code>", t)
    t = re.sub(r"\*\*([^*]+)\*\*", r"<b>\1</b>", t)
    t = re.sub(r"\[([^\]]+)\]\((https?://[^)]+)\)", r'<a href="\2">\1</a>', t)
    return t


def md_block(lines: list[str]) -> str:
    out, i = [], 0
    while i < len(lines):
        ln = lines[i]
        if not ln.strip():
            i += 1
        elif ln.startswith("### "):
            out.append(f"<h3>{inline(ln[4:])}</h3>")
            i += 1
        elif ln.startswith("|"):
            rows = []
            while i < len(lines) and lines[i].startswith("|"):
                rows.append(lines[i])
                i += 1
            head = [c.strip() for c in rows[0].strip("|").split("|")]
            body = "".join(
                "<tr>" + "".join(f"<td>{inline(c.strip())}</td>" for c in r.strip().strip("|").split("|")) + "</tr>" for r in rows[2:]
            )
            out.append('<div class="tw"><table><tr>' + "".join(f"<th>{inline(c)}</th>" for c in head) + "</tr>" + body + "</table></div>")
        elif ln.startswith("- "):
            items = []
            while i < len(lines) and lines[i].startswith("- "):
                items.append(lines[i][2:])
                i += 1
            out.append("<ul>" + "".join(f"<li>{inline(x)}</li>" for x in items) + "</ul>")
        else:
            para = [ln]
            i += 1
            while i < len(lines) and lines[i].strip() and not re.match(r"(###? |\||- )", lines[i]):
                para.append(lines[i])
                i += 1
            out.append(f"<p>{inline(' '.join(para))}</p>")
    return "\n".join(out)


def engineering() -> str:
    text = (HERE / "PLAN.md").read_text(encoding="utf-8")
    parts = re.split(r"^## ", text, flags=re.M)[1:]
    out = []
    for part in parts:
        title, *rest = part.split("\n")
        out.append(f'<details class="eng"><summary>{inline(title)}</summary>\n{md_block(rest)}\n</details>')
    return "\n".join(out)


def main() -> None:
    src = (HERE / "page.src.html").read_text(encoding="utf-8")
    page = src.replace("/*__LOOKS__*/[]", looks_json()).replace("<!--__ENGINEERING__-->", engineering())
    (HERE / "page.html").write_text(page, encoding="utf-8")
    print(f"page.html {len(page) // 1024} KB")


if __name__ == "__main__":
    main()
