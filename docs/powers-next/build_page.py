#!/usr/bin/env python3
"""Builds the approval page for Powers, the Next Level: docs/powers-next/plan.html (pictures inlined, so it is one file).

    python3 docs/powers-next/hands/storyboard.py docs/powers-next/boards     the drawings and their words
    python3 docs/powers-next/build_page.py                                    the page

"Today" pictures are stills from the CI film (run 42788742556, build 6027401c) under run-autopilot/screenshots-ci/.
"""
import base64
import html
import io
import json
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../.."))
FILM = os.path.join(ROOT, "run-autopilot/screenshots-ci/powers-next-before-42788742556")
FRAMES = os.path.join(HERE, "boards/frames")
LISTEN = os.path.join(ROOT, "run-autopilot/powers-next/listen/index.html")
E = html.escape


def uri(im, q=80):
    b = io.BytesIO()
    im.convert("RGB").save(b, "JPEG", quality=q, optimize=True)
    return "data:image/jpeg;base64," + base64.b64encode(b.getvalue()).decode()


def still(name, w=640):
    im = Image.open(os.path.join(FILM, f"nb_{name}.png"))
    return uri(im.resize((w, int(w * im.height / im.width)), Image.LANCZOS), 78)


def shots(kind, items, cols=4):
    """A row of pictures, each with its words as text. kind: today | new."""
    cells = "".join(f'<figure class="shot"><img src="{src}" alt="{E(alt)}" loading="lazy"><figcaption><b>{E(name)}</b>{(" " + E(note)) if note else ""}</figcaption></figure>'
                    for src, name, note, alt in items)
    return f'<div class="shots {kind} c{cols}">{cells}</div>'


def film_row(items, cols=4):
    return shots("today", [(still(f), name, note, f"The game today: {name}") for f, name, note in items], cols)


def board(row_index, cols=3, film=None):
    doc = json.load(open(os.path.join(FRAMES, "manifest.json")))[row_index]
    out = []
    for i, fr in enumerate(doc["frames"]):
        if film:
            src = still(f"{film}_{'abcd'[i]}")
        else:
            src = uri(Image.open(os.path.join(FRAMES, fr["file"])), 84)
        out.append((src, fr["name"].capitalize() if not film else f"Still {i + 1}", fr["note"], ("Drawing: " if doc["proposed"] else "The game today: ") + fr["note"]))
    return shots("new" if doc["proposed"] else "today", out, cols)


def tag(kind):
    return {"today": '<span class="tag today">Today, from the game</span>', "new": '<span class="tag new">Proposed, a drawing</span>'}[kind]


def decision(n, key, title, body, rec, ifno, extra=""):
    return f'''<fieldset class="rec" data-doc="{key}" id="d-{key}">
<legend><span class="num">{n}</span>{E(title)}</legend>
<p>{body}</p>
<p class="recline"><b>I recommend:</b> {rec}</p>
<p class="ifno"><b>If you say no:</b> {ifno}</p>
{extra}
<div class="answer"><label><input type="radio" name="{key}" value="yes" id="r-{key}-yes"> Yes</label>
<label><input type="radio" name="{key}" value="no" id="r-{key}-no"> No</label>
<input type="text" class="note" data-note="{key}" id="n-{key}" placeholder="Anything to add" aria-label="Note for decision {n}"></div>
</fieldset>'''


def build():
    P = {}
    P["offscreen"] = film_row([("haymaker_a", "Haymaker, charging.", "No hand on screen."), ("haymaker_b", "Half a second in.", "Still only a sliver at the bottom edge."),
                               ("nova_a", "Nova, going off.", "Two fist tips at the very bottom."), ("frost_nova_a", "Frost Nova, winding up.", "Nothing.")])
    P["arm"] = film_row([("dominate_b", "Dominate.", "The arm lies across the middle of the view."), ("dominate_c", "A moment later.", "It covers the thing you're aiming at."),
                         ("railgun_a", "Railgun, charging.", "Both fists sit behind the hotbar."), ("mend_b", "Mend.", "Two blocks at the edges.")])
    P["weapon"] = film_row([("spirit_palm_b", "Spirit Palm.", "Thrown with the weapon hand, so the weapon swings across the view."), ("hundred_palms_b", "Hundred Palms.", "The same."),
                            ("shockwave_c", "Shockwave.", "The weapon rides through the stomp."), ("nova_d", "After Nova.", "The weapon pops back in one frame.")])
    P["ring"] = film_row([("thunderclap_c", "Thunderclap.", ""), ("frost_nova_c", "Frost Nova.", ""), ("telekinesis_b", "Telekinesis.", ""), ("gravity_well_b", "Gravity Well.", "")])
    P["blank"] = film_row([("barrier_c", "Barrier, just raised.", "Nothing to see."), ("mend_c", "Mend, healing.", "Nothing to see."),
                           ("dash_a", "Dash.", "Plain white squares."), ("shadow_step_b", "Shadow Step, on arrival.", "Sky and a name plate. One to check in real play.")])
    P["good"] = film_row([("storm_call_d", "Storm Call.", "It looks like a storm."), ("haymaker_c", "Haymaker's hit.", "It lands hard."),
                          ("nova_b", "Nova's burst.", "It fills the sky."), ("cloak_a", "Cloak.", "The world dims round you.")])
    P["palm_today"] = board(0, 4, film="spirit_palm")
    P["palm_new"] = board(1, 3)
    P["nova_today"] = board(2, 4, film="nova")
    P["nova_new"] = board(3, 3)
    P["arms"] = board(4, 3)
    P["ways"] = board(5, 3)
    P["tag_today"], P["tag_new"] = tag("today"), tag("new")
    P["listen_path"] = E(LISTEN)

    powers = [("Haymaker", "haymaker"), ("Gravity Well", "gravity_well"), ("Spirit Palm", "spirit_palm"), ("Nova", "nova"), ("Frost Nova", "frost_nova"), ("Shadow Step", "shadow_step")]
    P["ears"] = "".join(
        f'<fieldset class="ear" data-doc="ear_{k}"><legend>{n}</legend>'
        + "".join(f'<label><input type="radio" name="ear_{k}" value="{v}" id="ear-{k}-{v}"> {t}</label>' for v, t in (("a", "New A"), ("b", "New B"), ("today", "Keep today's"), ("none", "None of them")))
        + f'<input type="text" class="note" data-note="ear_{k}" id="earn-{k}" placeholder="What you heard" aria-label="Note on {n}"></fieldset>' for n, k in powers)

    new_powers = "".join(f'<label class="pick"><input type="checkbox" data-pick="{k}" id="pick-{k}"> {n}</label>' for n, k in
                         (("Brand", "brand"), ("Hoarfrost Lances", "hoarfrost"), ("Updraft", "updraft"), ("Backdraft", "backdraft"), ("Thornwake", "thornwake")))
    D = [
        ("map", "Yesterday's plan stays the map",
         "Powers in Full Voice stays exactly as you approved it: what each power does, the States, the Duets, the R key, the new powers. This plan becomes the way each one is built from now on.",
         "yes. It was a good plan for what powers do. It said much less about hands and it built sounds the old way.",
         "tell me which parts of yesterday's plan to drop, and I'll redo those first.",
         '<p class="ask">Have you played Haymaker, Shockwave, Gravity Well and Railgun since yesterday? If you have, a line on how they felt would help more than anything else on this page.</p>'),
        ("arm", "Give the arm an elbow and a wrist, and let it show the power",
         "Still blocky, still no fingers. The forearm can point anywhere, the wrist can turn, and light runs down the arm while you cast. At level 5 a faint trace of the pattern stays on your arm at rest.",
         "yes, with the proof deciding whether it stays.",
         "every animation is still redone and brought into view, on today's straight arm.", ""),
        ("offhand", "Let the left hand cast, so your weapon stays in your hand",
         "One-handed casts go to the left hand and the weapon drops into a guard. Two-handed casts lower the weapon properly and bring it back.",
         "yes. It's the biggest change to how a fight flows.",
         "casts keep taking the weapon away, with a proper lowering in place of the pop.", ""),
        ("voice", "Build every power sound from Minecraft's own recordings",
         "A score for each power, layered from the game's recordings by name, with the sound of whatever you hit added in. This replaces how yesterday's plan made sounds. The four packs made yesterday get remade this way too.",
         "yes, once the listening page has told you it's the right direction.",
         "the rest of the sounds are made yesterday's way: a few recordings under synthesis, with the ring checked out.", ""),
        ("variety", "Takes and ways",
         "Two or three takes of every cast, and about twenty powers that change shape with what your body is doing: standing, sprinting or in the air.",
         "yes.",
         "one animation a power, and a cast is the same wherever you throw it from.", ""),
        ("look", "A look of its own for every power",
         "No power ends as only a ring, a flash and a number. Barrier and Mend show on your own arms. The plain squares and the game's own fire go.",
         "yes.",
         "effects stay as they are, apart from the showpieces yesterday's plan already covers.", ""),
        ("arms", "Weapon combos",
         "Weaves are performed by the weapon, and twelve Arms Duets pair one power with one weapon. Built together with the weapon session.",
         "yes, after the families, so the weapons have settled first.",
         "Weaves stay as a ring and an echo, and powers and weapons only share the screen.", ""),
        ("finishers", "Break finishers",
         "Nine short first-person finishers, one a family, when you cast into a boss you've just broken.",
         "yes.",
         "a cast into a broken boss stays as it is today.", ""),
        ("newpowers", "New powers",
         "Tick the ones you want. Each fills a gap I measured, each is built to the new bar, and they'd join the queue behind the eight you approved yesterday.",
         "Brand, Hoarfrost Lances and Updraft. Hold Backdraft and Thornwake until those three have been played.",
         "none of the five are built, and Heat and Frost each stay at one power.",
         f'<div class="picks">{new_powers}</div>'),
        ("proof", "A proof first, then stop",
         "Spirit Palm, Nova, Shockwave and Frost Nova rebuilt to this bar, with Haymaker's charge brought into view. About 14 hours of building. Then you play them and flip the sounds between old and new.",
         "yes. Two things in this plan can only be judged in the game.",
         "I'd build straight through the families, which is faster and much harder to stop cheaply.", ""),
        ("order", "After the proof: starters, then one family at a time",
         "Each power is opened once: yesterday's rework, its hands, its voice and its look together.",
         "yes.",
         "yesterday's stages are finished first on the old method, and every power gets opened a second time for hands and voice.", ""),
    ]
    P["decisions"] = "\n".join(decision(i + 1, k, t, b, r, n, x) for i, (k, t, b, r, n, x) in enumerate(D))
    P["rec_ids"] = json.dumps([d[0] for d in D])
    page = open(os.path.join(HERE, "page_template.html")).read()
    for k, v in P.items():
        page = page.replace("{{" + k + "}}", v)
    assert "{{" not in page, page[page.index("{{"):page.index("{{") + 60]
    out = os.path.join(HERE, "plan.html")
    open(out, "w").write(page)
    print(f"{os.path.relpath(out, ROOT)}: {os.path.getsize(out) / 1e6:.1f} MB")


if __name__ == "__main__":
    build()
