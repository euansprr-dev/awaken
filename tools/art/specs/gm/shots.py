"""What the gates' monsters throw (SPEC §60.7): one 16x8 sprite for each kind of shot, its tip to the right, drawn in
the colours of the body that throws it. The game flies them tip first with the answer-colour glow round them
(mobs.def.ShotLook, mobs.client.ShotRenderer). Names and order match ShotLook."""
import os

from PIL import Image


def _c(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


SHOTS = {
    # a rib of the Mourner's: a curve of old bone, its tip in cold light
    "rib": ({"b": "#e2d8bc", "d": "#a89c7c", "k": "#4a4234", "c": "#9ad8ff"}, [
        "................",
        "....bbbbbb......",
        "..bbddddddbb....",
        ".bd........dbb..",
        "kb...........dbc",
        "k.............bc",
        "................",
        "................"]),
    # a spore pod: pale, flecked gold, shedding
    "spore": ({"p": "#efe6c8", "s": "#b6ac88", "g": "#e8d04a", "k": "#5a5444"}, [
        "................",
        ".....sppps......",
        "....sppgppps....",
        "..k.spppppgp....",
        ".k.kspgpppps....",
        "....spppgpps....",
        ".....sppps......",
        "................"]),
    # a cinder: a lump of coal, fire in its cracks, sparks behind it
    "cinder": ({"c": "#22222a", "d": "#42424e", "e": "#ff8a2a", "h": "#ffe6a8"}, [
        "................",
        "......cdcc......",
        "....ecdhecd.....",
        "..e.cdecddec....",
        ".e..cehdcecd....",
        "....dcdechc.....",
        "......cddc......",
        "................"]),
    # a thorn off the Thorn Bud: a pale point, a leaf at its heel
    "thorn": ({"g": "#5c8c42", "d": "#2e4e22", "t": "#8a6e52", "w": "#e6ffd8"}, [
        "................",
        "................",
        "..gd............",
        ".gdd..tttttt....",
        "..gdttddddddttww",
        ".....tttttttt...",
        "................",
        "................"]),
    # ink: a black gout, a glint of teal in it, drops trailing
    "ink": ({"k": "#0b0f16", "n": "#1d2534", "t": "#3fe0d0"}, [
        "................",
        "......nkkn......",
        "...k.nkkkkkn....",
        ".k..nkktkkkkn...",
        "..k.nkkkktkkn...",
        ".....nkkkkkn....",
        "......nkkn......",
        "................"]),
    # a quill pen, nib first, the nib wet and lit
    "quill": ({"c": "#efe6c8", "s": "#b6ac88", "k": "#0b0f16", "t": "#3fe0d0"}, [
        "................",
        ".ccs............",
        "cccss...........",
        ".cccsssccccccckt",
        "cccss...........",
        ".ccs............",
        "................",
        "................"]),
    # an index card out of the Curator's drawers, turning over
    "card": ({"c": "#efe6c8", "s": "#b6ac88", "k": "#1d2534"}, [
        "................",
        "....cccccccc....",
        "....ckkkkkkc....",
        "....cccccccc....",
        "....ckkkkc.c....",
        "....cccccccc....",
        "....ssssssss....",
        "................"]),
    # a rivet: a brass head, hot, on an iron shank
    "rivet": ({"b": "#d6ac44", "d": "#86601a", "i": "#52525e", "l": "#ffe08a"}, [
        "................",
        "................",
        "..........bbb...",
        "..iiiiiiiibblb..",
        "..iiiiiiiidbbb..",
        "..........ddd...",
        "................",
        "................"]),
    # a sliver of dark glass, white along its edges
    "shard": ({"g": "#202636", "d": "#30384c", "w": "#e6f0ff"}, [
        "................",
        "................",
        "....wwww........",
        "..wwggddwwww....",
        ".wggddggddggwwww",
        "...wwwwggddww...",
        ".......wwww.....",
        "................"]),
    # a clay flask of lamp oil, its wick alight behind it
    "oil": ({"c": "#6a4a2c", "d": "#3a2a1a", "f": "#ffb44a", "h": "#fff0c0"}, [
        "................",
        ".....dcccd......",
        "....dccccccd....",
        ".fh.dccccccdd...",
        "fhf.dccccccdd...",
        "....dccccccd....",
        ".....dcccd......",
        "................"]),
    # a hailstone: rough, white and blue
    "hail": ({"w": "#e2f4fa", "b": "#94c2d6", "d": "#527a94"}, [
        "................",
        ".....bwwb.......",
        "....bwwwwwb.....",
        "...dwwbwwwwb....",
        "...bwwwwbwwd....",
        "....bwwwwwb.....",
        ".....dbwb.......",
        "................"]),
    # one point of the Snow Star: an ice spike with its barbs
    "point": ({"w": "#e2f4fa", "b": "#94c2d6", "l": "#a8e4ff"}, [
        "................",
        "................",
        "....b...........",
        "..bbwbbbbb......",
        ".bwwwwwwwwwbbbll",
        "..bbwbbbbb......",
        "....b...........",
        "................"]),
    # an arc: a jag of blue-white current
    "arc": ({"w": "#e8f8ff", "b": "#6ac8ff"}, [
        "................",
        "..b.............",
        ".bwb....bb......",
        "..bwwb.bwwb...b.",
        "....bwwwbbwwbbwb",
        ".....bb...bwwwb.",
        "............b...",
        "................"]),
    # a gust: a curl of wind you can see
    "gust": ({"w": "#c8ccd2", "g": "#8892a6"}, [
        "................",
        "....gwwwg.......",
        "..gw.....wg.....",
        ".g..gwwg..wg..g.",
        "....w..g...wgw..",
        "....gwg.........",
        "................",
        "................"]),
    # the Balloonist's quarrel: a short bolt, a lead weight behind its point
    "quarrel": ({"t": "#987e48", "d": "#564626", "i": "#3a4252", "l": "#eaf2ff"}, [
        "................",
        "................",
        ".dd.........ii..",
        ".tdtttttttttiil.",
        ".dd.........ii..",
        "................",
        "................",
        "................"]),
    # a spar out of the Gale's wreck: splintered timber
    "spar": ({"t": "#b89c62", "d": "#766034", "k": "#3a2e1a"}, [
        "................",
        "................",
        ".k.tttt.........",
        "kdttddtttttttt..",
        ".ktdddddddddttt.",
        "..k.ttttttt..t..",
        "................",
        "................"]),
    # the Hollow Priest's bolt: a ring of violet with nothing in it
    "ring": ({"v": "#9a5cff", "h": "#e6d4ff", "k": "#06040c"}, [
        "................",
        ".....vvvv.......",
        "....vhkkhv......",
        "...vhk..khv.....",
        "...vhk..khv.....",
        "....vhkkhv......",
        ".....vvvv.......",
        "................"]),
    # one of the Nothing's: a small hood, a ring where its face would be
    "hood": ({"k": "#06040c", "d": "#140e22", "v": "#9a5cff", "m": "#4a2a8a"}, [
        "................",
        "....mdddm.......",
        "...mdkkkkdm.....",
        "..mdkkvvkkdmm...",
        "..mdkkvvkkddmmm.",
        "...mdkkkkdm.....",
        "....mdddm.......",
        "................"]),
}


def images():
    """{name: 16x8 RGBA image}."""
    out = {}
    for name, (pal, rows) in SHOTS.items():
        if len(rows) != 8 or any(len(r) != 16 for r in rows):
            raise ValueError(f"shot {name}: a sprite is 8 rows of 16")
        im = Image.new("RGBA", (16, 8), (0, 0, 0, 0))
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch != ".":
                    im.putpixel((i, j), _c(pal[ch]))
        out[name] = im
    return out


def write(folder):
    os.makedirs(folder, exist_ok=True)
    for name, im in images().items():
        im.save(os.path.join(folder, name + ".png"))
    return len(SHOTS)


def java_names():
    """The looks as mobs.def.ShotLook lists them (its ids, in order), read from the Java."""
    import re
    here = os.path.dirname(os.path.abspath(__file__))
    src = open(os.path.join(here, "../../../../src/main/java/dev/awaken/mobs/def/ShotLook.java")).read()
    return re.findall(r'^\s+[A-Z_]+\("([a-z_]+)"', src, re.M)
