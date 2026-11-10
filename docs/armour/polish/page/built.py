#!/usr/bin/env python3
"""The "built since your yes" pictures for the Armour Finish page: strips cut from the CI tours of the build
(run-autopilot/screenshots-ci/<prefix>-armour-audit-<tour>-<run>/), written to docs/armour/polish/page/img/built_*.jpg.

    python3 docs/armour/polish/page/built.py

A tour that has not run yet leaves its strips out; the page shows what exists."""
import glob
import os

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../../../.."))
CI = os.path.join(ROOT, "run-autopilot/screenshots-ci")
IMG = os.path.join(HERE, "img")
W, H = 3416, 1858


def newest(prefixes, tour):
    for prefix in prefixes:
        dirs = sorted(d for d in glob.glob(os.path.join(CI, f"{prefix}-armour-audit-{tour}-*")) if glob.glob(os.path.join(d, "*.png")))
        if dirs:
            return dirs[-1]
    return None


def font(size):
    for p in ("/System/Library/Fonts/Supplemental/Arial Bold.ttf", "/System/Library/Fonts/Helvetica.ttc"):
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def strip(out, tiles, size, quality=84):
    """tiles: (folder, shot, crop box in a 3416x1858 shot, label). Missing shots leave the whole strip out."""
    w, h = size
    paths = [(os.path.join(d, shot + ".png") if d else None, box, label) for d, shot, box, label in tiles]
    if any(p is None or not os.path.exists(p) for p, _, _ in paths):
        print("  (not yet)", out)
        return False
    sheet = Image.new("RGB", (len(tiles) * w + (len(tiles) - 1) * 6, h), (12, 11, 9))
    d = ImageDraw.Draw(sheet)
    f = font(max(13, h // 26))
    for k, (path, box, label) in enumerate(paths):
        im = Image.open(path).convert("RGB")
        sx, sy = im.size[0] / W, im.size[1] / H
        sheet.paste(im.crop((int(box[0] * sx), int(box[1] * sy), int(box[2] * sx), int(box[3] * sy))).resize((w, h), Image.LANCZOS), (k * (w + 6), 0))
        if label:
            x0 = k * (w + 6) + 10
            tw = d.textlength(label, font=f)
            d.rectangle((x0 - 6, 8, x0 + tw + 6, 8 + f.size + 10), fill=(12, 11, 9))
            d.text((x0, 12), label, font=f, fill=(255, 122, 46) if label != "TODAY" else (236, 228, 212))
    sheet.save(os.path.join(IMG, out), quality=quality, optimize=True)
    print("  wrote", out)
    return True


FIG = (1148, 400, 2268, 1800)      # the worn tours' figure, fov 50
CLOSE = (1108, 60, 2308, 1560)     # the living tour's figure, fov 40
FP = (1780, 880, 3416, 1858)       # the first-person arm


def main():
    os.makedirs(IMG, exist_ok=True)
    before = newest(["before"], "worn-1")
    worn = newest(["after5", "after6"], "worn-1")
    living = newest(["after5", "after6"], "living")
    # the first moments run (after5) was shot through the world's own first light, with the HUD hiding the hand and the
    # marked arm turned away: only the run after those fixes counts
    moments = newest(["after7"], "moments")
    extras = newest(["after7"], "extras")
    # C-rank: the three sets whose plates changed in stage 3, from behind (and Shardcrown's forearm from your own eyes)
    strip("built_c_gatewrought.jpg", [(before, "w_gatewrought_back", FIG, "TODAY"), (worn, "w_gatewrought_back", FIG, "BUILT")], (400, 500))
    strip("built_c_vaultbreaker.jpg", [(before, "w_vaultbreaker_back", FIG, "TODAY"), (worn, "w_vaultbreaker_back", FIG, "BUILT")], (400, 500))
    strip("built_c_shardcrown.jpg", [(before, "w_shardcrown_front", FIG, "TODAY"), (worn, "w_shardcrown_front", FIG, "BUILT")], (400, 500))
    strip("built_c_shardcrown_fp.jpg", [(before, "w_shardcrown_fp", FP, "TODAY"), (worn, "w_shardcrown_fp", FP, "BUILT")], (560, 335))
    strip("built_c_front.jpg", [(worn, f"w_{s}_front", FIG, s.upper()) for s in ("gatewrought", "vaultbreaker", "frostwalker", "sunward", "shardcrown")], (300, 375))
    strip("built_c_back.jpg", [(worn, f"w_{s}_back", FIG, "") for s in ("gatewrought", "vaultbreaker", "frostwalker", "sunward", "shardcrown")], (300, 375))
    # living parts: the same set a second apart, or standing beside moving
    strip("built_live_marker.jpg", [(living, f"l_signals_echo_{k}", CLOSE, "") for k in "abcd"], (300, 375))
    strip("built_live_sun.jpg", [(living, f"l_sunward_{k}", CLOSE, "") for k in "abcd"], (300, 375))
    strip("built_live_halo.jpg", [(living, f"l_halo_choir_{k}", CLOSE, "") for k in "abcd"], (300, 375))
    strip("built_live_lantern.jpg", [(living, "l_lanternlight_a", CLOSE, "STANDING"), (living, "l_lanternlight_walk_a", CLOSE, "WALKING"),
                                    (living, "l_lanternlight_walk_b", CLOSE, ""), (living, "l_lanternlight_walk_c", CLOSE, "")], (300, 375))
    strip("built_live_galewing.jpg", [(living, "l_galewing_a", CLOSE, "STANDING"), (living, "l_galewing_fall_a", CLOSE, "FALLING"),
                                     (living, "l_galewing_fall_b", CLOSE, ""), (living, "l_galewing_fall_c", CLOSE, "")], (300, 375))
    strip("built_live_grow.jpg", [(living, f"l_grow_{k}", CLOSE, lab) for k, lab in (("before", "MASTERY III"), ("a", "IV: 0.15 S"), ("b", "0.3 S"), ("d", "GROWN"))], (300, 375))
    # the wake: the light climbing the body when the last piece goes on
    for s in ("lordscale", "halo_choir"):
        strip(f"built_wake_{s}.jpg", [(moments, f"m_{s}_wake_{k}", FIG, lab) for k, lab in (("0", "THREE PIECES"), ("a", "THE FOURTH"), ("b", ""), ("c", ""), ("e", ""))], (300, 375))
    strip("built_wake_line.jpg", [(moments, "m_wake_line", (0, 0, W, H), "")], (960, 522))
    strip("built_held.jpg", [(moments, f"m_held_{p}_fp", (1108, 560, 3416, 1858), p.upper()) for p in ("helmet", "chestplate", "leggings", "boots")], (400, 225))
    strip("built_frames.jpg", [(moments, "m_frames", (608, 300, 2808, 1500), "")], (960, 524))
    strip("built_ground.jpg", [(moments, "m_ground", (608, 500, 2808, 1700), "")], (960, 524))
    strip("built_marks.jpg", [(moments, f"m_marks_{i}", (1108, 300, 2308, 1700), lab) for i, lab in enumerate(("PLAIN", "FLAWLESS", "SCALE FLAW", "FLAWLESS", "SCALE FLAW"))], (300, 350))
    # the bigger options
    strip("built_kick_galewing.jpg", [(extras, f"k_galewing_{k}", CLOSE, lab) for k, lab in (("rest", "AT REST"), ("a", "GUST"), ("b", ""), ("d", ""))], (300, 375))
    strip("built_kick_halo.jpg", [(extras, f"k_halo_choir_{k}", CLOSE, lab) for k, lab in (("rest", "AT REST"), ("a", "HYMN"), ("b", ""), ("d", ""))], (300, 375))
    strip("built_admire.jpg", [(extras, f"a_lordscale_{k}", (0, 0, W, H), "") for k in "abcd"], (480, 261))
    strip("built_admire_night.jpg", [(extras, f"a_sunward_{k}", (0, 0, W, H), "") for k in "abcd"], (480, 261))


def fig(name, alt, caption, wide=True):
    if not os.path.exists(os.path.join(IMG, name)):
        return ""
    return (f'<figure class="{"wide" if wide else ""}"><img src="img/{name}" alt="{alt}" loading="lazy">'
            f'<figcaption>{caption}</figcaption></figure>')


def group(title, lead, figures):
    figures = [f for f in figures if f]
    if not figures:
        return f'<div class="col"><h3>{title}</h3><p>{lead}</p><p class="muted">The pictures for this part are still being shot on GitHub. They land here when they are.</p></div>'
    # the words in the reading column, the pictures at the page's full width
    return f'<div class="col"><h3>{title}</h3><p>{lead}</p></div><div class="views">' + "".join(figures) + "</div>"


def section():
    """The "built since your yes" part of the page, with whichever pictures exist."""
    rows = [
        ("0. Faults", "Your arms, wrists and legs wear the armour in every first-person view our own code draws. Your own charge shows on your own arm. The sheen no longer crosses armour in the dark.", "Built. Seen in the game, all 29."),
        ("1. Light", "Plates part from each other by day. From C-rank up the thing a stranger would name is lit after dark; E and D stay dark.", "Built. Seen in the game, day and night."),
        ("2. Hands", "A glove, a cuff and a hand plate on every set, each in its own materials.", "Built. Seen in the game, all 29."),
        ("3. Plates", "C-rank only: a gate on Gatewrought&rsquo;s back, a vault door on Vaultbreaker&rsquo;s, crystal on Shardcrown&rsquo;s forearm.", "<strong>Waits for your eye.</strong> B, A and S are not started."),
        ("4. Parts that move", "Twelve sets have one or two parts that live: a marker, a halo, rays, lanterns, plumes, wings, shards, a heart.", "Built. Seen in the game."),
        ("5. Sound", "45 sounds, every one from Minecraft&rsquo;s own recordings: the Forge Song remade, and new ones for putting a piece on, walking in a set and a blow landing on it.", "<strong>Waits for your ear.</strong> Nobody has heard any of it."),
        ("6. Moments and marks", "A set lights feet to crown when its last piece goes on. A forged piece shows its flaw, its fold and its maker&rsquo;s mark. A piece in your hand, on the ground or in a frame is the piece, not an icon.", "Built. Tested; shots below as they land."),
        ("After the pass", "The suit answers its own ability (wings beat on Gust, the halo rings on Hymn). Hold O to Admire: the camera circles you once.", "Built. Shots below as they land."),
    ]
    table = ('<div class="tablewrap"><table class="built"><thead><tr><th>Stage</th><th>What it does</th><th>Where it stands</th></tr></thead><tbody>'
             + "".join(f"<tr><td>{a}</td><td>{b}</td><td>{c}</td></tr>" for a, b, c in rows) + "</tbody></table></div>")
    parts = [
        '<div class="col"><h2 id="built">Built since your yes</h2>'
        "<p>You said: go with the full plan and all recommendations. This is what is built, what the real game has shown of it on GitHub&rsquo;s machines, and the three things only you can do. It all sits on its own branch. Nothing is merged.</p>"
        + table + "</div>",
        group("C-rank, for your eye", "You asked to see C-rank before the rest. Three sets changed shape, on limbs and backs only. No helm was touched.", [
            fig("built_c_gatewrought.jpg", "Gatewrought from behind, today and built", "Gatewrought. Today the back is a blank slab. Built: the gate&rsquo;s two pillars and lintel, with a lit door between them.", False),
            fig("built_c_vaultbreaker.jpg", "Vaultbreaker from behind, today and built", "Vaultbreaker. Built: a riveted vault door on the back with its diamond lock, and darker steel so the set stops being grey on grey.", False),
            fig("built_c_shardcrown.jpg", "Shardcrown from the front, today and built", "Shardcrown. Built: crystal growing out of the forearm, lit from inside.", False),
            fig("built_c_shardcrown_fp.jpg", "Shardcrown's arm from your own eyes, today and built", "The same forearm from your own eyes.", False),
            fig("built_c_front.jpg", "The five C-rank sets from the front, as built", "All five C-rank sets as they stand now, front."),
            fig("built_c_back.jpg", "The five C-rank sets from behind, as built", "And from behind."),
        ]),
        '<div class="col"><div class="dec"><h3>8. C-rank: go on to B, A and S the same way?</h3><p>The same kind of change on the eleven sets left: limbs, belts and backs, one feature each where a back is plain, no helm touched. <span class="rec">Recommended: yes.</span></p>'
        '<div class="ans" data-key="d8" hidden><button type="button" data-v="yes" aria-pressed="false">Yes, go on</button><button type="button" data-v="change" aria-pressed="false">Change something first</button><button type="button" data-v="stop" aria-pressed="false">Stop at C</button><textarea id="note-d8" aria-label="A note on decision 8" placeholder="What to change, or anything to add"></textarea><span class="saved" aria-live="polite"></span></div></div></div>',
        group("Parts that move", "Each strip is one set a second apart, or standing beside moving. They move from the game&rsquo;s own state, so everyone sees the same and nothing is sent or saved.", [
            fig("built_live_marker.jpg", "Signal's Echo's marker at four moments", "Signal&rsquo;s Echo: the System&rsquo;s marker turns and rides over the hood."),
            fig("built_live_halo.jpg", "Halo Choir's halo at four moments", "Halo Choir: the halo leans round and the small wings breathe."),
            fig("built_live_sun.jpg", "Sunward's rays at four moments", "Sunward: the rays turn slowly behind the disc."),
            fig("built_live_lantern.jpg", "Lanternlight standing and walking", "Lanternlight: the shoulder lanterns and the brow lamp swing with your stride."),
            fig("built_live_galewing.jpg", "Galewing standing and falling", "Galewing from behind: the wings that hang down the back open in a fall, a glide or a sprint."),
            fig("built_live_grow.jpg", "Halo Choir's mastery IV rays growing in", "Mastery IV used to pop in one frame. Now its parts grow over half a second."),
        ]),
        group("The moment a set becomes whole", "Put the last piece on and the light climbs you, boots first. The first time, the System says in one line what the set does. It also sounds the first three notes of its Forge Song, which you have not heard yet.", [
            fig("built_wake_lordscale.jpg", "Lordscale as the fourth piece goes on", "Lordscale: three pieces, then the helm goes on."),
            fig("built_wake_halo_choir.jpg", "Halo Choir as the fourth piece goes on", "Halo Choir, the same moment."),
            fig("built_wake_line.jpg", "The System's line in chat", "The one line, the first time only."),
        ]),
        group("Forge marks, and a piece in the world", "Armour now shows how it was made, as weapons do. And a piece you hold, drop or frame is drawn as the piece itself.", [
            fig("built_marks.jpg", "Forged pieces with their marks", "Galewing plain, Flawless (the fold&rsquo;s ripples and a maker&rsquo;s mark on the forearm) and with a scale flaw; Vaultbreaker Flawless and flawed."),
            fig("built_held.jpg", "Galewing's four pieces held in first person", "Galewing&rsquo;s four pieces in your hand."),
            fig("built_frames.jpg", "Pieces in item frames", "In frames on a wall."),
            fig("built_ground.jpg", "Pieces lying on the ground", "Dropped on the ground."),
        ]),
        group("After the pass: the suit in a fight, and Admire", "Both of the bigger options you chose are built.", [
            fig("built_kick_galewing.jpg", "Galewing's wings on Gust", "Galewing: its ability fires and the wings are thrown open, then fold."),
            fig("built_kick_halo.jpg", "Halo Choir's halo on Hymn", "Halo Choir: the halo rings wider and its wings beat."),
            fig("built_admire.jpg", "Admire circling Lordscale", "Admire: hold O and the camera leaves your eyes and circles you once."),
            fig("built_admire_night.jpg", "Admire at night in Sunward", "The same at night. The set is drawn in a light that shows it whatever the hour."),
        ]),
        '<div class="col"><h3>The three things only you can do</h3>'
        '<ul><li><strong>Your eye on C-rank</strong>: decision 8, above.</li>'
        '<li><strong>Your ear on the sounds.</strong> A listening page runs on your Mac only (it plays Minecraft&rsquo;s recordings from your own copy of the game, so it is never hosted). In the armour worktree run <code>python3 tools/art/armour_sounds.py listen</code>, then open <code>http://127.0.0.1:8767</code>. Each of the 45 sounds has three picks; press <em>Use this</em> on one. Pick 1 is in the game until you choose.</li>'
        '<li><strong>The measuring run on your Mac</strong> (many armoured players at once). You said yes to it; I will still ask before I launch the game.</li></ul>',
        '<p class="muted">Not checked anywhere yet: slim-arm skins, the VFX Look, the grips with the weapon-feel session, and how any of it sounds.</p></div>',
    ]
    return '<div class="builtsec">' + "".join(parts) + "</div>"


def page():
    import json
    import sys
    sys.path.insert(0, HERE)
    import build as B
    data = {"ranks": B.RANKS, "sets": {k: {"name": v[0], "arch": v[1], "today": v[2], "gives": v[3]} for k, v in B.SETS.items()}}
    html = open(os.path.join(HERE, "template.html")).read().replace("/*DATA*/", json.dumps(data)).replace("<!--BUILT-->", section())
    with open(os.path.join(HERE, "index.html"), "w") as f:
        f.write(html)
    print("index.html written")


if __name__ == "__main__":
    main()
    page()
