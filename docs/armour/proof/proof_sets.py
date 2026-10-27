"""The proof's armour sets, rebuilt clean (docs/armour/PLAN.md Part D). Every set is laid out in lanes:

    * one hero per zone (head, shoulders, chest front, back, hips and legs, feet), the rest supports it and never shares its volume;
    * every limb's height is cut into bands, each owned by one part, with a gap of at least 0.3 between bands;
    * left and right never touch: the legs' layers are outer-biased and every leg part stays on its own side of the centre line;
    * hanging cloth is thin, hangs outside everything else and stops short of the next band;
    * no two faces share a plane (the checker in audit.py fails at 0.08), and no part passes through another.

Lane map, in each limb's own frame (model pixels, y down, -z front), the right side; the left is the mirror.

    arm   shell x -4..2, y -3..11, z -3..3     pauldron y -4.6..0.2 | elbow 3.8..6.2 | cuff 6.8..10.2
    leg   shell x -2.5..1.9, y -0.5..12.9      panel 0.2..6.4 | knee 6.9..9.1 | boot cuff 9.5..10.9 | toe 11.3..12.9
    body  shell x +-5, y -1..13, z +-3         mantle or pauldrons 0..4.4 | front hero 2.6..8.4 | belt 8.7..11.5 | back hero 2.8..8.8
    head  shell +-5, y -9..1                   crown -10.5..-8.5 | brim -8.5..-5.5 | cheeks -5..0
"""
import armour_proof as A
from armour_proof import part, mirrored, shell_parts


def twins(parts, names, extra=None):
    ren = {n: n[:-1] + "l" if n.endswith("r") else n + "_l" for n in names}
    ren.update({"right_arm": "left_arm", "right_leg": "left_leg"})
    if extra:
        ren.update(extra)
    return mirrored(parts, ren)


def pair(parts, names, extra=None):
    return parts + twins(parts, names, extra)


A.R.update({
    "sandscale": ["#3a2a14", "#5e4522", "#8a6a34", "#b8914a", "#e0bf70", "#f6e4a8"],
    "frostfur": ["#2a3342", "#46556b", "#6b7f99", "#93a8c0", "#bccbdc", "#e8eff6"],
    "pollen": ["#0f2a12", "#1d4f1e", "#2f7a2c", "#58b24a", "#9ae07a", "#e2ffc0"],
    "frostglow": ["#10283a", "#1d4f6e", "#2f86b0", "#63c0e6", "#a8e6ff", "#f0fcff"],
    "thorn": ["#14200c", "#24381a", "#3a5426", "#587a34", "#7ea04a", "#a8c870"],
    "sand": ["#3d3322", "#6a5a3a", "#988356", "#c4ae78", "#e6d6a0", "#f8f0cc"],
    "cloak_red": ["#240808", "#4a1010", "#7a1c1c", "#a82c2c", "#d44e48", "#f08c80"],
    "coat": ["#101a22", "#1b2c38", "#2b4254", "#3e5d73", "#5c8099", "#8eb0c6"],
    "linen": ["#4a4234", "#7a6f58", "#a89c80", "#cfc4a6", "#ece3c8", "#faf6e6"],
    "lamp": ["#3a1404", "#7a3208", "#c46a14", "#f4a02a", "#ffd070", "#fff4c8"],
})


# ---------------------------------------------------------------------------------------------------------------------
# Lane helpers: the same band on every set, so a leg or an arm can never be built two ways at once

def leg_panel(kind, ramp, front=True, back=True, **o):
    """The hanging hero of the legs: a thin panel on each thigh, 4.1 wide so left and right keep 0.2 apart."""
    out = []
    if front:
        out.append(part("panel_f_r", "right_leg", (-2.4, 0, 0, 4.1, 6.2, 0.8), kind, ramp=ramp, pose=(0, 0.2, -3.4, 0, 0, 0), swing=-0.08, **o))
    if back:
        out.append(part("panel_b_r", "right_leg", (-2.4, 0, 0, 4.1, 6.2, 0.8), kind, ramp=ramp, pose=(0, 0.2, 2.6, 0, 0, 0), swing=0.2, **o))
    return out


def leg_knee(kind, ramp, **o):
    return [part("knee_r", "right_leg", (-2.3, 6.9, -3.7, 3.9, 2.2, 1.1), kind, ramp=ramp, **o)]


def leg_boot(cuff_kind, cuff_ramp, toe_kind, toe_ramp, cuff_o=None, toe_o=None):
    return [part("boot_cuff_r", "right_leg", (-3.0, 9.5, -3.0, 4.8, 1.4, 6.0), cuff_kind, ramp=cuff_ramp, **(cuff_o or {})),
            part("toe_r", "right_leg", (-2.3, 11.3, -4.4, 4.0, 1.6, 1.8), toe_kind, ramp=toe_ramp, **(toe_o or {}))]


def legs_all(panel, knee=None, boot=None):
    parts = panel + (knee or []) + (boot or [])
    return pair(parts, [p["name"] for p in parts])


def arm_cuff(kind, ramp, y0=6.8, h=3.4, **o):
    return part("cuff_r", "right_arm", (-4.7, y0, -3.7, 6.4, h, 7.4), kind, ramp=ramp, **o)


def pauldron(kind, ramp, trims=None, n=3, **o):
    """n lames, each wider and deeper than the one above, touching: a clean step, never an overlap."""
    out = []
    y = -4.8
    for k in range(n):
        w, d = 6.6 + 0.25 * k, 8.0 + 0.8 * k      # the inner edge stays at 1.6 (clear of the body lane); only the outer edge and depth step
        h = 1.6
        out.append(part("pauld_%d_r" % (k + 1), "right_arm", (-5.0 - 0.25 * k, y, -d / 2, w - 0.0, h, d), kind, ramp=ramp, base=4 - (k % 2),
                        **dict(o, **({"trim": trims} if trims and k == n - 1 else {}))))
        y += h
    return out


# ---------------------------------------------------------------------------------------------------------------------
# B: Veilwoven, the Veil's weavers (cloth hero: the hood, the mantle, the cape)

def veilwoven():
    s = []
    for p in shell_parts("silk", base=2, fold=4):
        if p["name"] == "sh_head":
            p["o"].update(window=(1, 6, 3, 7))
        if p["name"] == "sh_body":
            p["o"].update(thread=A.thread_line)
        if p["name"] in ("sh_leg_r", "sh_leg_l"):
            p["o"].update(base=2, fold=0)
        if p["name"] in ("sh_arm_r", "sh_arm_l"):
            p["o"].update(fold=0)
        s.append(p)
    hood = [
        part("hood_top", "head", (-5.5, -10.5, -5.5, 11, 2, 11), "silk", base=3),
        part("hood_back", "head", (-5.5, -8.5, 4.5, 11, 8, 2), "silk", base=3, fold=4),
        part("hood_side_r", "head", (-7, -8.5, -5.5, 2, 8, 10), "silk", base=3, fold=3, hem="silver", hem_rows=1),
        part("hood_brim", "head", (-5, -8.5, -7, 10, 3, 2), "silk", base=4, hem="silver", hem_rows=1),
        part("peak_1", "head", (-2, -4, -2, 4, 4, 4), "silk", pose=(0, -9.9, 3.5, -0.3, 0, 0), swing=-0.1, base=3, rooted="+y"),
        part("peak_2", "peak_1", (-1.5, -4, -1.5, 3, 4, 3), "silk", pose=(0, -3.4, 0, -0.5, 0, 0), swing=-0.2, base=4, rooted="+y"),
        part("brow_gem", "head", (-1, -7.8, -7.9, 2, 2, 1), "glass", rooted="+z"),
    ]
    s += hood + twins([hood[2]], ["hood_side_r"])
    s += [
        part("mantle_1", "body", (-8.5, 0.2, -4.0, 17, 2, 8), "silk", base=4),
        part("mantle_2", "body", (-9.45, 2.2, -4.6, 18.9, 2, 9.2), "silk", base=3, hem="silver", hem_rows=1),
        part("clasp", "body", (-1.2, 2.5, -5.6, 2.4, 2.4, 1), "plate", base=4),
        part("clasp_gem", "body", (-0.5, 3.1, -6.5, 1, 1.2, 0.9), "glass", ramp="rose", rooted="+z"),
        part("tabard", "body", (-3.5, 0, 0, 7, 7.8, 1), "silk", pose=(0, 5.0, -4.6, 0, 0, 0), swing=-0.05, base=4, hem="silver", hem_rows=2,
             thread=A.thread_diamond),
        part("belt", "body", (-3.0, 8.7, -3.5, 6.0, 2.4, 7), "silk", base=1, bevel=True),
        part("buckle", "body", (-1.5, 8.9, -5.4, 3, 2, 0.8), "plate", base=4),
        part("cape_a", "body", (-4.5, 0, 0, 9, 7, 1), "silk", pose=(0, 1.0, 4.8, 0.04, 0, 0), swing=0.28, base=3, fold=3, contact=True,
             thread=A.thread_diamond),
        part("cape_b", "cape_a", (-4.5, 0, 0, 9, 7, 1), "silk", pose=(0, 7, 0, 0.1, 0, 0), swing=0.5, base=3, fold=3, hem="silver", hem_rows=2),
    ]
    arm = [arm_cuff("plate", "silver", base=3, rivet=True),
           part("cuff_gem_r", "right_arm", (-5.6, 7.6, -1, 1.1, 1.6, 2), "glass", rooted="+x")]
    s += pair(arm, ["cuff_r", "cuff_gem_r"])
    s += legs_all(leg_panel("silk", "silk", base=3, fold=3, hem="silver", hem_rows=1, contact=True),
                  boot=leg_boot("silk", "silk", "plate", "silver", {"base": 4, "hem": "silver", "hem_rows": 1}, {"base": 4}))
    return s


# ---------------------------------------------------------------------------------------------------------------------
# C: Gatewrought, the Gate's plate (hard hero: the pauldrons, the breastplate, the helm)

def gatewrought(helm=True, back=True):
    S = dict(ramp="slate")
    s = []
    for p in shell_parts("plate", ramp="slate", base=2):
        if p["name"] == "sh_head":
            p["o"].update(window=[(1, 6, 4, 4), (3, 4, 5, 7)] if helm else [(1, 6, 3, 7)])
        if p["name"] == "sh_body":
            p["o"].update(rune=A.rune_line, glowc="signal")
        if p["name"] in ("sh_leg_r", "sh_leg_l"):
            p["o"].update(base=2)
        s.append(p)
    if helm:
        head = [
            part("helm_crown", "head", (-5.5, -10, -5.5, 11, 2, 11), "plate", base=3, **S),
            part("helm_brow", "head", (-5, -8, -6.5, 10, 3, 1.5), "plate", base=3, trim="brass", rune=A.rune_bar, glowc="signal", **S),
            part("crest", "head", (-0.5, -14, -5.5, 1, 4, 11), "plate", ramp="brass", base=4, rooted="+y"),
        ]
        s += head
    s += [
        part("breast", "body", (-3, 2.7, -4.4, 6, 5.5, 1.4), "plate", base=3, rivet=True, trim="brass", rune=A.rune_line, glowc="signal", **S),
        part("lame_1", "body", (-3.0, 8.5, -3.7, 6.0, 1.3, 7.4), "plate", base=3, **S),
        part("lame_2", "body", (-3.0, 9.9, -3.9, 6.0, 1.3, 7.8), "plate", base=4, trim="brass", **S),
    ]
    if back:
        s += [part("back_plate", "body", (-3, 2.8, 3.2, 6, 6, 1.4), "plate", base=3, rivet=True, rune=A.rune_cross, glowc="signal", **S)]
    arm = pauldron("plate", "slate", trims="brass") + [
        part("elbow_r", "right_arm", (-4.7, 3.8, -3.7, 6.4, 2.4, 7.4), "plate", ramp="brass", base=4, rivet=True),
        arm_cuff("plate", "slate", y0=6.9, h=3.3, base=3, rune=A.rune_bar, glowc="signal", trim="brass"),
    ]
    s += pair(arm, ["pauld_1_r", "pauld_2_r", "pauld_3_r", "elbow_r", "cuff_r"])
    s += legs_all([part("tasset_1_r", "right_leg", (-2.4, 0, 0, 4.1, 2.9, 0.8), "plate", ramp="slate", pose=(0, 0.5, -3.4, 0, 0, 0), swing=-0.06, base=3),
                   part("tasset_2_r", "right_leg", (-2.4, 0, 0, 4.1, 3.0, 1.1), "plate", ramp="slate", pose=(0, 3.4, -3.7, 0, 0, 0), swing=-0.1, base=4, trim="brass")],
                  knee=leg_knee("plate", "brass", base=4, rivet=True),
                  boot=leg_boot("plate", "slate", "plate", "brass", {"base": 3, "trim": "brass"}, {"base": 4}))
    return s


# ---------------------------------------------------------------------------------------------------------------------
# D houses (Beastbound tier, one per named beast)

def _beast_legs(panel_kind, panel_ramp, knee, boot, panel_o, front_only=False):
    panel = leg_panel(panel_kind, panel_ramp, back=not front_only, **panel_o)
    return legs_all(panel, knee=knee, boot=boot)


def briarguard():
    """The Briar Sow's house: a boar-skull helm with swept tusks, a rib cuirass, tusk pauldrons."""
    s = []
    for p in shell_parts("hide", ramp="leather", base=3):
        if p["name"] == "sh_head":
            p["o"].update(window=[(1, 6, 3, 7)])
        s.append(p)
    head = [
        part("skull_cap", "head", (-5.5, -10, -5.5, 11, 2.5, 11), "plate", ramp="bone", base=3),
        part("brow", "head", (-5, -7.5, -6.7, 10, 1.8, 1.7), "plate", ramp="bone", base=4),
        part("tusk_r", "head", (-0.7, -0.7, -3.2, 1.4, 1.4, 3.2), "plate", ramp="bone", base=4, pose=(-4.1, -6.6, -5.6, 0.5, 0, 0), rooted="+z"),
        part("tusk_r2", "tusk_r", (-0.5, -0.5, -3, 1, 1, 3), "plate", ramp="bone", base=5, pose=(0, 0, -2.6, -0.9, 0, 0), rooted="+z"),
        part("thorn_crest", "head", (-0.5, -3, -3, 1, 3, 6), "plate", ramp="thorn", base=4, pose=(0, -9.6, 0, 0, 0, 0), rooted="+y"),
        part("pollen_bud", "head", (-0.6, -1.4, -0.6, 1.2, 1.4, 1.2), "glass", ramp="pollen", pose=(0, -12.3, 0, 0, 0, 0), rooted="+y"),
    ]
    s += head + twins(head[2:4], ["tusk_r", "tusk_r2"], {"tusk_r": "tusk_l", "tusk_r2": "tusk_l2"})
    s += [
        part("rib_1", "body", (-3.0, 2.6, -4.1, 6.0, 1, 1.0), "plate", ramp="bone", base=4),
        part("rib_2", "body", (-3.0, 4.4, -4.1, 6.0, 1, 1.0), "plate", ramp="bone", base=4),
        part("rib_3", "body", (-3.0, 6.2, -4.1, 6.0, 1, 1.0), "plate", ramp="bone", base=3),
        part("sternum", "body", (-0.7, 2.6, -4.9, 1.4, 4.6, 0.8), "plate", ramp="bone", base=4),
        part("belt", "body", (-3.0, 8.8, -3.5, 6.0, 2.4, 7), "studded", ramp="leather", stud="bone"),
        part("clasp_skull", "body", (-1.5, 9.0, -4.6, 3, 2, 1.1), "plate", ramp="bone", base=4),
        part("pelt", "body", (-4.5, 0, 0, 9, 7.8, 1.4), "hide", ramp="pelt", fringe=True, pose=(0, 1.0, 3.4, 0.05, 0, 0), swing=0.5, base=3),
    ]
    arm = pauldron("plate", "leather", n=2) + [
        part("shoulder_tusk_r", "right_arm", (-0.8, -5, -0.8, 1.6, 5, 1.6), "plate", ramp="bone", base=4, pose=(-2.4, -4.1, 0, 0, 0, -0.35), rooted="+y"),
        part("shoulder_tusk_r2", "shoulder_tusk_r", (-0.6, -3.5, -0.6, 1.2, 3.5, 1.2), "plate", ramp="bone", base=5, pose=(0, -4.4, 0, 0, 0, 0.5), rooted="+y"),
        part("vambrace_r", "right_arm", (-4.7, 4.0, -3.7, 6.4, 3.6, 7.4), "studded", ramp="leather", stud="bronze"),
        part("band_r", "right_arm", (-4.9, 7.6, -3.9, 6.6, 1.4, 7.8), "plate", ramp="bronze", base=4),
    ]
    s += pair(arm, ["pauld_1_r", "pauld_2_r", "shoulder_tusk_r", "shoulder_tusk_r2", "vambrace_r", "band_r"],
              {"shoulder_tusk_r": "shoulder_tusk_l", "shoulder_tusk_r2": "shoulder_tusk_l2"})
    s += _beast_legs("hide", "pelt", [part("knee_r", "right_leg", (-2.3, 6.9, -3.7, 3.9, 2.2, 1.1), "plate", ramp="bone", base=4)],
                     leg_boot("hide", "pelt", "plate", "bone", {"fringe": True, "base": 3}, {"base": 3}), dict(fringe=True, base=3), front_only=False)
    return s


def wyrmscale():
    """The Dune Wyrm's house: overlapping sand scales, glass fins swept back from the helm and shoulders, a three-link tail."""
    s = []
    for p in shell_parts("scale", ramp="sandscale", base=3):
        if p["name"] == "sh_head":
            p["o"].update(window=[(1, 6, 4, 7)])
        s.append(p)
    head = [
        part("helm_cap", "head", (-5.5, -10, -5.5, 11, 2.5, 11), "scale", ramp="sandscale", base=4, rim="amber"),
        part("helm_brow", "head", (-5, -7.5, -6.5, 10, 2, 1.5), "scale", ramp="sandscale", base=3, rim="amber"),
        # a setting along the ridge: the three crystals stand in it, not on a corner of the cap
        part("crown_mount", "head", (-4.4, -11.7, -3.4, 8.8, 1.9, 6.8), "plate", ramp="brass", base=4),
        part("fin_c", "head", (-0.7, -6, -2.0, 1.4, 6, 3.6), "glass", ramp="deep", pose=(0, -10.8, 0.4, -0.3, 0, 0), swing=-0.08, rooted="+y"),
        part("fin_r", "head", (-0.6, -5, -1.5, 1.2, 5, 3.0), "glass", ramp="deep", pose=(-3.4, -10.8, 0.4, -0.3, 0, -0.32), swing=-0.08, rooted="+y"),
    ]
    s += head + twins(head[4:], ["fin_r"], {"fin_r": "fin_l"})
    s += [
        part("belly_1", "body", (-3.0, 1.0, -4.0, 6, 2, 1.0), "scale", ramp="sandscale", base=4, rim="amber"),
        part("belly_2", "body", (-3.0, 3.0, -4.3, 6, 2, 1.3), "scale", ramp="sandscale", base=3, rim="amber"),
        part("belly_3", "body", (-3.0, 5.0, -4.6, 6, 2, 1.6), "scale", ramp="sandscale", base=4, rim="amber"),
        part("belly_4", "body", (-3.0, 7.0, -4.9, 6, 2, 1.9), "scale", ramp="sandscale", base=3, rim="amber"),
        part("sash", "body", (-3.0, 9.3, -3.5, 6.0, 2.2, 7), "silk", ramp="sand", base=3, bevel=True),
        part("sash_gem", "body", (-0.8, 9.6, -4.4, 1.6, 1.6, 0.9), "glass", ramp="deep", rooted="+z"),
        part("dorsal_mount", "body", (-1.8, 2.4, 3.0, 3.6, 5.6, 1.6), "plate", ramp="brass", base=4),
        part("back_fin_1", "body", (-0.7, -5, -0.8, 1.4, 5, 1.6), "glass", ramp="deep", pose=(0, 3.0, 3.8, -0.4, 0, 0), swing=-0.05, rooted="+y"),
        part("tail_mount", "body", (-2.0, 10.6, 2.4, 4.0, 2.0, 2.6), "plate", ramp="brass", base=4),
        part("tail_1", "body", (-1.3, 0, -1.1, 2.6, 4.6, 2.2), "scale", ramp="sandscale", base=3, pose=(0, 11.9, 3.7, 0.5, 0, 0), swing=0.25, rooted="-y"),
        part("tail_2", "tail_1", (-1.1, 0, -1.0, 2.2, 4.6, 2.0), "scale", ramp="sandscale", base=4, pose=(0, 4.0, 0, 0.45, 0, 0), swing=0.4, rim="amber", rooted="-y"),
        part("tail_3", "tail_2", (-0.9, 0, -0.9, 1.8, 3.8, 1.8), "scale", ramp="sandscale", base=3, pose=(0, 4.0, 0, 0.45, 0, 0), swing=0.5, rooted="-y"),
        part("tail_barb", "tail_3", (-0.55, 0, -0.55, 1.1, 3.4, 1.1), "glass", ramp="deep", pose=(0, 3.2, 0, 0.3, 0, 0), swing=0.2, rooted="-y"),
    ]
    arm = pauldron("scale", "sandscale", n=2, rim="amber") + [
        part("shoulder_fin_r", "right_arm", (-0.6, -6, -1.4, 1.2, 6, 2.8), "glass", ramp="deep", pose=(-2.8, -4.1, 0.6, -0.3, 0, -0.3), swing=-0.05, rooted="+y"),
        part("bracer_r", "right_arm", (-4.7, 3.9, -3.7, 6.4, 3.2, 7.4), "scale", ramp="sandscale", base=3, rim="amber"),
        part("bracer_gem_r", "right_arm", (-5.6, 4.7, -1, 1.1, 1.6, 2), "glass", ramp="deep", rooted="+x"),
        part("wrap_r", "right_arm", (-4.7, 7.5, -3.7, 6.4, 2.7, 7.4), "silk", ramp="sand", base=3, fold=2),
    ]
    s += pair(arm, ["pauld_1_r", "pauld_2_r", "shoulder_fin_r", "bracer_r", "bracer_gem_r", "wrap_r"], {"shoulder_fin_r": "shoulder_fin_l"})
    s += _beast_legs("silk", "sand", [part("greave_r", "right_leg", (-2.3, 6.9, -3.7, 3.9, 2.2, 1.1), "scale", ramp="sandscale", base=4, rim="amber")],
                     leg_boot("scale", "sandscale", "glass", "deep", {"base": 3, "rim": "amber"}, {}),
                     dict(base=3, fold=3, hem="amber", hem_rows=1, contact=True), front_only=True)
    return s


def antlerhold():
    """The Old Antler's house: a branching antler crown with frost-lit tines, a fur mantle, a bell, hoof-bone plates."""
    s = []
    for p in shell_parts("hide", ramp="frostfur", base=3):
        if p["name"] == "sh_head":
            p["o"].update(window=[(1, 6, 3, 7)])
        s.append(p)
    head = [
        part("hood_fur", "head", (-5.5, -10, -5.5, 11, 2.5, 11), "hide", ramp="frostfur", fringe=True, base=4),
        part("fur_brim", "head", (-5, -7.5, -6.6, 10, 2, 1.6), "hide", ramp="frostfur", fringe=True, base=5),
        part("hood_side_r", "head", (-6.6, -7.5, -4.6, 1.6, 7.5, 9.2), "hide", ramp="frostfur", fringe=True, base=3),
        part("beam_r", "head", (-0.8, -7, -0.8, 1.6, 7, 1.6), "plate", ramp="bone", base=4, pose=(-3.2, -9.4, 0.5, -0.2, 0, -0.4), rooted="+y"),
        part("tine_r1", "beam_r", (-0.5, -5, -0.5, 1.0, 5, 1.0), "plate", ramp="bone", base=5, pose=(0, -3.5, 0, 0, 0, -0.55), rooted="+y"),
        part("tine_r2", "beam_r", (-0.5, -4, -0.5, 1.0, 4, 1.0), "plate", ramp="bone", base=4, pose=(0, -6.0, 0, -0.35, 0, 0.45), rooted="+y"),
        part("tip_r1", "tine_r1", (-0.5, -1.4, -0.5, 1, 1.4, 1), "glass", ramp="frostglow", pose=(0, -4.4, 0, 0, 0, 0), rooted="+y"),
        part("tip_r2", "tine_r2", (-0.5, -1.4, -0.5, 1, 1.4, 1), "glass", ramp="frostglow", pose=(0, -3.4, 0, 0, 0, 0), rooted="+y"),
    ]
    s += head + twins(head[2:], ["hood_side_r", "beam_r", "tine_r1", "tine_r2", "tip_r1", "tip_r2"],
                      {"hood_side_r": "hood_side_l", "beam_r": "beam_l", "tine_r1": "tine_l1", "tine_r2": "tine_l2", "tip_r1": "tip_l1", "tip_r2": "tip_l2"})
    s += [
        part("mantle_1", "body", (-8, 0, -4.5, 16, 2.3, 9), "hide", ramp="frostfur", fringe=True, base=5),
        part("mantle_2", "body", (-9.45, 2.3, -5.0, 18.9, 2.3, 10), "hide", ramp="frostfur", fringe=True, base=4),
        part("bell", "body", (-1.2, 2.4, -7.4, 2.4, 2.8, 2.4), "plate", ramp="brass", base=4),
        part("hoofplate", "body", (-3.4, 5.2, -4.4, 6.8, 3.4, 1.4), "plate", ramp="bone", base=3, rivet=True),
        part("belt", "body", (-3.0, 8.8, -3.5, 6.0, 2.4, 7), "studded", ramp="leather", stud="bone"),
        part("cloak_a", "body", (-5.5, 0, 0, 11, 7.6, 1.4), "hide", ramp="frostfur", fringe=True, pose=(0, 1.5, 5.1, 0.05, 0, 0), swing=0.28, base=3),
        part("cloak_b", "cloak_a", (-5.5, 0, 0, 11, 8, 1.4), "hide", ramp="frostfur", fringe=True, pose=(0, 7.6, 0, 0.1, 0, 0), swing=0.5, base=3),
    ]
    arm = [part("bracer_r", "right_arm", (-4.7, 3.8, -3.7, 6.4, 2.4, 7.4), "plate", ramp="bone", base=3, rivet=True),
           part("cuff_r", "right_arm", (-4.8, 6.8, -3.8, 6.5, 3.4, 7.6), "hide", ramp="frostfur", fringe=True, base=5)]
    s += pair(arm, ["bracer_r", "cuff_r"])
    s += _beast_legs("hide", "frostfur", [part("knee_r", "right_leg", (-2.3, 6.9, -3.7, 3.9, 2.2, 1.1), "plate", ramp="bone", base=4)],
                     leg_boot("hide", "frostfur", "plate", "bone", {"fringe": True, "base": 5}, {"base": 3}), dict(fringe=True, base=4), front_only=False)
    return s


# ---------------------------------------------------------------------------------------------------------------------
# Garments: each slot has a layer order, so garments stack behind and over each other without sharing volume

def garment_cloak(ramp="cloak_red", trim="brass"):
    """Back slot: replaces the set's back part. Layer 2: behind the scarf tails (z 4.8..5.8)."""
    return [
        part("cloak_a", "body", (-5.5, 0, 0, 11, 8, 1.2), "silk", ramp=ramp, pose=(0, 1.2, 6.2, 0.04, 0, 0), swing=0.35, base=3, fold=3, contact=True),
        part("cloak_b", "cloak_a", (-5.5, 0, 0, 11, 8.5, 1.2), "silk", ramp=ramp, pose=(0, 8, 0, 0.1, 0, 0), swing=0.55, base=3, fold=3, hem=trim, hem_rows=1),
        part("cloak_chain", "body", (-2.6, 0.2, -4.4, 5.2, 0.9, 0.9), "plate", ramp=trim, base=4),
    ]


def garment_scarf(ramp="linen"):
    """Neck slot: a wrap (3.1 each side, inside the pauldrons) and two tails that hang at layer 1 (z 4.8..5.8)."""
    return [
        part("scarf_wrap", "body", (-3.1, -1.8, -3.3, 6.2, 2.4, 6.6), "silk", ramp=ramp, base=4, fold=2),
        part("scarf_t1", "body", (-1.3, 0, 0, 2.6, 6.5, 1), "silk", ramp=ramp, pose=(-1.6, 0.6, 4.8, 0, 0, 0), swing=0.15, base=4, fold=2),
        part("scarf_t2", "scarf_t1", (-1.3, 0, 0, 2.6, 6.5, 1), "silk", ramp=ramp, pose=(0, 6.5, 0, 0.05, 0, 0), swing=0.2, base=3, fold=2, hem="brass", hem_rows=1),
    ]


def garment_hat():
    return [
        part("hat_brim", "head", (-6.8, -9.8, -6.8, 13.6, 1, 13.6), "silk", ramp="coat", base=3, bevel=True),
        part("hat_band", "head", (-4.3, -10.9, -4.3, 8.6, 1.1, 8.6), "plate", ramp="brass", base=4),
        part("hat_crown", "head", (-4, -14.9, -4, 8, 4, 8), "silk", ramp="coat", base=4, fold=4),
        part("hat_feather", "head", (-0.5, -3, 0, 1, 3, 2.5), "silk", ramp="cloak_red", base=4, pose=(3.8, -14.5, -1.5, 0.5, 0, 0.3), swing=-0.3),
    ]


def garment_goggles():
    return [
        part("gog_strap", "head", (-5.3, -7.6, -5.3, 10.6, 1.2, 10.6), "plate", ramp="leather", base=3),
        part("gog_lens_r", "head", (-4, -7.9, -6.3, 3, 2.2, 1.0), "glass", ramp="lamp"),
        part("gog_lens_l", "head", (1, -7.9, -6.3, 3, 2.2, 1.0), "glass", ramp="lamp"),
        part("gog_bridge", "head", (-1, -7.3, -6.0, 2, 1.0, 0.7), "plate", ramp="brass", base=4),
    ]


def garment_lantern():
    return [
        part("lamp_chain", "body", (-0.3, 0, -0.3, 0.6, 3, 0.6), "plate", ramp="brass", base=3, pose=(-6.4, 9.6, -1, 0, 0, 0), swing=0.2),
        part("lamp_cap", "lamp_chain", (-1.8, 2.6, -1.8, 3.6, 0.9, 3.6), "plate", ramp="brass", base=4),
        part("lamp_body", "lamp_chain", (-1.5, 3.5, -1.5, 3, 3.5, 3), "glass", ramp="lamp"),
    ]


def coat_base():
    """Off duty: the base layer is a dark teal coat with a high collar and brass buttons; no plates."""
    s = []
    for p in shell_parts("silk", ramp="coat", base=2, fold=4):
        if p["name"] == "sh_head":
            p["o"].update(ramp="coat", base=3, window=[(1, 6, 3, 7)])
        if p["name"] == "sh_body":
            p["o"].update(thread=lambda i, j, fw, fh: i == fw // 2 and 1 < j < fh - 1 and j % 3 == 1, base=3)
        if p["name"] in ("sh_arm_r", "sh_arm_l"):
            p["o"].update(fold=0, hem="brass", hem_rows=1)
        if p["name"] in ("sh_leg_r", "sh_leg_l"):
            p["o"].update(base=1, fold=0)
        s.append(p)
    s += [
        part("belt", "body", (-3.0, 8.8, -3.5, 6.0, 2.4, 7), "plate", ramp="leather", base=3),
        part("buckle", "body", (-1.3, 9.0, -4.4, 2.6, 2.0, 0.9), "plate", ramp="brass", base=4),
    ]
    s += legs_all(leg_panel("silk", "coat", base=3, fold=3, hem="brass", hem_rows=1, contact=True))
    return s


def no_helm(parts):
    """Headwear replaces the helm: its parts are hidden and the shell's face opens."""
    out = [p for p in parts if not (p["name"].startswith("helm_") or p["name"] == "crest")]
    for p in out:
        if p["name"] == "sh_head":
            p["o"] = dict(p["o"], window=[(1, 6, 3, 7)])
    return out


def no_back(parts):
    return [p for p in parts if p["name"] != "back_plate"]


def looks():
    armed = gatewrought()
    travelling = no_back(gatewrought()) + garment_cloak() + garment_scarf()
    wayfarer = no_helm(no_back(gatewrought())) + garment_cloak("moss", "brass") + garment_scarf() + garment_hat() + garment_goggles()
    off = coat_base() + garment_scarf("cloak_red") + garment_hat() + garment_goggles() + garment_lantern()
    return [("Armed", armed), ("Travelling", travelling), ("Wayfarer", wayfarer), ("Off duty", off)]


# ---------------------------------------------------------------------------------------------------------------------
# Three more styles (Euan's references, 2026-10-27): trimmed robes in five colours, horned steel with a bandolier, skeleton armour

A.R.update({
    "robe_red": ["#240808", "#4a1010", "#7a1c1c", "#b02a2a", "#d8433f", "#f07a6c"],
    "robe_purple": ["#1c0f33", "#33195c", "#4d2488", "#6a35b3", "#8b53d6", "#b08af0"],
    "robe_lilac": ["#3a3550", "#5a5278", "#8478a6", "#a99cc8", "#cbc0e2", "#ece6f6"],
    "robe_black": ["#07070a", "#0e0e13", "#16161d", "#202029", "#2c2c38", "#3d3d4e"],
    "robe_olive": ["#1d2113", "#343b1f", "#4f5a2c", "#6c7a3a", "#8c9c52", "#b2c47a"],
    "ironleather": ["#1a0f08", "#2e1b0e", "#472b16", "#62401f", "#80592c", "#a07840"],
})
FORCE_ROBES = {"Heat": "robe_red", "Gravity": "robe_purple", "Frost": "robe_lilac", "Charge": "robe_black", "Spirit": "robe_olive"}


def cantor(main="robe_purple", trim="gold", glowtrim=False):
    """The trimmed robe: flat colour, a bold gold border round the hood's opening, two stripes down the front and on the skirt, bands
    at the cuffs, small epaulettes. One design, five Force colours (a palette swap)."""
    R = dict(ramp=main, trim=trim, glowtrim=glowtrim)
    s = []
    for p in shell_parts("robe", base=3, **R):
        if p["name"] == "sh_head":
            p["o"].update(window=(1, 6, 3, 7), frame=trim)
        if p["name"] == "sh_body":
            p["o"].update(stripes=(2, 5), stripe_from=1, back_stripes=(3, 4), hem=1)
        if p["name"] in ("sh_arm_r", "sh_arm_l"):
            p["o"].update(bands=[(8, 9)])
        if p["name"] in ("sh_leg_r", "sh_leg_l"):
            p["o"].update(stripes=(2,), back_stripes=(2,), hem=2, base=2)
        s.append(p)
    hood = [
        part("hood_top", "head", (-5.5, -10.5, -5.5, 11, 2, 11), "robe", border=True, base=3, **R),
        part("hood_back", "head", (-5.5, -8.5, 4.5, 11, 8, 2), "robe", border=True, base=3, **R),
        part("hood_side_r", "head", (-7, -8.5, -5.5, 2, 5, 10), "robe", border=True, base=3, **R),
        part("hood_brim", "head", (-5, -8.5, -7, 10, 3, 2), "robe", border=True, base=4, **R),
    ]
    s += hood + twins([hood[2]], ["hood_side_r"])
    ep = [part("epaulette_r", "right_arm", (-5.4, -4.1, -2.4, 4.4, 1.5, 4.8), "robe", border=True, base=4, **R)]
    s += pair(ep, ["epaulette_r"])
    s += legs_all(leg_panel("robe", main, stripes=(2,), back_stripes=(2,), hem=1, base=3, trim=trim, glowtrim=glowtrim),
                  boot=leg_boot("robe", main, "robe", main, {"base": 3, "border": True, "trim": trim}, {"base": 4, "border": True, "trim": trim}))
    return s


def ironhorn():
    """D, the Ember Tyrant's crown: white steel, a horned helm, a leather bandolier and belt, a pouch at the back."""
    ST, LE = dict(ramp="steel"), dict(ramp="ironleather")
    s = []
    for p in shell_parts("plate", ramp="steel", base=3):
        if p["name"] == "sh_head":
            p["o"].update(window=(1, 6, 3, 7))
        if p["name"] in ("sh_leg_r", "sh_leg_l"):
            p["o"].update(base=3)
        s.append(p)
    head = [
        part("helm_cap", "head", (-5.5, -10, -5.5, 11, 2.5, 11), "plate", base=4, **ST),
        part("helm_band", "head", (-5, -7.5, -6.5, 10, 1.6, 1.5), "plate", base=3, **ST),
        part("horn_socket_r", "head", (-7.0, -9.6, -1.4, 1.8, 3.0, 2.8), "plate", base=4, rivet=True, **ST),
        part("horn_1_r", "head", (-3.4, -0.8, -0.8, 3.4, 1.6, 1.6), "plate", base=5, pose=(-6.4, -8.1, 0, 0, 0, 0.3), rooted="+x", **ST),
        part("horn_2_r", "horn_1_r", (-3.0, -0.7, -0.7, 3.0, 1.4, 1.4), "plate", base=5, pose=(-2.8, 0, 0, 0, 0, 0.55), rooted="+x", **ST),
        part("horn_3_r", "horn_2_r", (-2.6, -0.6, -0.6, 2.6, 1.2, 1.2), "plate", base=5, pose=(-2.4, 0, 0, 0, 0, 0.6), rooted="+x", **ST),
        part("horn_tip_r", "horn_3_r", (-1.2, -0.5, -0.5, 1.2, 1.0, 1.0), "glass", ramp="ember", pose=(-2.1, 0, 0, 0, 0, 0), rooted="+x"),
    ]
    s += head + twins(head[2:], ["horn_socket_r", "horn_1_r", "horn_2_r", "horn_3_r", "horn_tip_r"])
    s += [
        part("breast", "body", (-3, 2.7, -4.4, 6, 5.4, 1.4), "plate", base=3, rivet=True, **ST),
        part("belt", "body", (-3.0, 8.6, -3.5, 6.0, 2.0, 7), "plate", base=3, **LE),
        part("buckle", "body", (-1.3, 8.8, -4.3, 2.6, 1.6, 0.8), "plate", base=5, **ST),
        part("strap", "body", (-0.9, 0, -0.45, 1.8, 9.2, 0.9), "plate", base=3, pose=(-2.4, 3.3, -4.85, 0, 0, -0.4), **LE),
        part("pouch", "body", (-1.5, 0, 0, 3, 3, 1.8), "studded", pose=(-1.6, 10.0, 3.0, 0, 0, 0), stud="bronze", **LE),
        part("back_plate", "body", (-3, 2.8, 3.2, 6, 5.8, 1.4), "plate", base=3, rivet=True, **ST),
    ]
    arm = pauldron("plate", "steel", n=3) + [
        part("elbow_r", "right_arm", (-4.7, 3.8, -3.7, 6.4, 2.4, 7.4), "plate", base=4, rivet=True, **ST),
        arm_cuff("plate", "ironleather", y0=6.9, h=3.3, base=3),
    ]
    s += pair(arm, ["pauld_1_r", "pauld_2_r", "pauld_3_r", "elbow_r", "cuff_r"])
    s += legs_all([part("tasset_1_r", "right_leg", (-2.4, 0, 0, 4.1, 2.9, 0.8), "plate", base=3, pose=(0, 0.5, -3.4, 0, 0, 0), swing=-0.06, **ST),
                   part("tasset_2_r", "right_leg", (-2.4, 0, 0, 4.1, 3.0, 1.1), "plate", base=4, pose=(0, 3.4, -3.7, 0, 0, 0), swing=-0.1, **ST)],
                  knee=leg_knee("plate", "steel", base=4, rivet=True),
                  boot=leg_boot("plate", "ironleather", "plate", "steel", {"base": 3}, {"base": 4}))
    return s


def bonewright(ramp="bone", marrow="cream"):
    """E, skeleton armour: a skull whose eye sockets are the helm's opening (your own eyes show in them), a rib cuirass, scapula
    pauldrons, a pelvis, hanging bone plates. Flintguard is the same set in flint, the black skeleton."""
    B = dict(ramp=ramp, marrow=marrow)
    s = []
    for p in shell_parts("bone", base=3, **B):
        if p["name"] == "sh_head":
            p["o"].update(skull=True, window=[(1, 2, 4, 5), (5, 6, 4, 5)], base=4)
        if p["name"] == "sh_body":
            p["o"].update(ribs=True)
        s.append(p)
    head = [
        part("skull_crown", "head", (-5.5, -10.2, -5.5, 11, 2.2, 11), "plate", base=4, **B),
        part("skull_brow", "head", (-5, -8.0, -6.5, 10, 1.6, 1.5), "plate", base=4, **B),
    ]
    s += head
    s += [
        part("clavicle", "body", (-3.0, 1.9, -4.2, 6, 0.9, 1.2), "plate", base=4, **B),
        part("rib_1", "body", (-3.0, 3.2, -4.2, 6, 1.0, 1.2), "plate", base=4, **B),
        part("rib_2", "body", (-3.0, 5.0, -4.2, 6, 1.0, 1.2), "plate", base=4, **B),
        part("rib_3", "body", (-3.0, 6.8, -4.2, 6, 1.0, 1.2), "plate", base=3, **B),
        part("sternum", "body", (-0.6, 3.2, -5.0, 1.2, 4.6, 0.8), "plate", base=5, **B),
        part("pelvis", "body", (-3.0, 8.8, -3.6, 6.0, 2.4, 7.2), "plate", base=3, **B),
        part("spine", "body", (-0.7, 2.8, 3.1, 1.4, 6.0, 1.3), "plate", base=5, **B),
    ]
    arm = pauldron("plate", ramp, n=2) + [
        part("elbow_r", "right_arm", (-4.7, 3.8, -3.7, 6.4, 2.4, 7.4), "plate", base=4, **B),
        arm_cuff("plate", ramp, y0=6.9, h=3.3, base=3, ramp_=None) if False else arm_cuff("plate", ramp, y0=6.9, h=3.3, base=3),
    ]
    s += pair(arm, ["pauld_1_r", "pauld_2_r", "elbow_r", "cuff_r"])
    s += legs_all([part("tasset_1_r", "right_leg", (-2.4, 0, 0, 4.1, 2.9, 0.8), "plate", ramp=ramp, base=4, pose=(0, 0.5, -3.4, 0, 0, 0), swing=-0.06),
                   part("tasset_2_r", "right_leg", (-2.4, 0, 0, 4.1, 3.0, 1.1), "plate", ramp=ramp, base=3, pose=(0, 3.4, -3.7, 0, 0, 0), swing=-0.1)],
                  knee=leg_knee("plate", ramp, base=5),
                  boot=leg_boot("plate", ramp, "plate", ramp, {"base": 3}, {"base": 4}))
    return s


def flintguard():
    return bonewright(ramp="flint", marrow="slate")


SETS = {"B Veilwoven": veilwoven, "C Gatewrought": gatewrought, "Briarguard": briarguard, "Wyrmscale": wyrmscale, "Antlerhold": antlerhold,
        "Force robe": cantor, "Ironhorn": ironhorn, "Bonewright": bonewright, "Flintguard": flintguard}
