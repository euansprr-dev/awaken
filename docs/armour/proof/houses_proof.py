"""Houses and garments proof (docs/armour/PLAN.md Part C): disposable, changes no production file. Same engine and the same limits
as armour_proof.py (a still, a software renderer, no game). It builds

  * three D-rank houses, one per named beast (Briarguard, Wyrmscale, Antlerhold), beside today's Beastbound, and
  * a garment kit (cloak, scarf, hat, goggles, bandolier, lantern) on one plate set, in four looks: armed, travelling,
    wayfarer and off duty (the plates hidden, the base layer painted as a coat).

    python3 docs/armour/proof/houses_proof.py OUTDIR
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import armour_proof as A  # noqa: E402
from armour_proof import part, mirrored, shell_parts  # noqa: E402
from PIL import Image  # noqa: E402

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
W, H, CTR = A.W, A.H, A.CTR


def twins(parts, names, extra=None):
    ren = {n: n[:-1] + "l" if n.endswith("r") else n + "_l" for n in names}
    ren.update({"right_arm": "left_arm", "right_leg": "left_leg"})
    if extra:
        ren.update(extra)
    return mirrored(parts, ren)


# ---------------------------------------------------------------------------------------------------------------------
# D-rank houses

def briarguard():
    """The Briar Sow's house: a boar-skull helm with swept tusks, a rib cuirass over studded leather, tusk pauldrons, thorns."""
    L = dict(ramp="leather")
    s = []
    for p in shell_parts("hide", ramp="leather", base=3):
        if p["name"] == "sh_head":
            p["o"].update(window=[(1, 6, 3, 7)])
        s.append(p)
    head = [
        part("skull_cap", "head", (-5.5, -10, -5.5, 11, 3, 11), "plate", ramp="bone", base=3),
        part("brow", "head", (-5.5, -8, -6.7, 11, 2, 1.7), "plate", ramp="bone", base=4),
        part("cheek_r", "head", (-6.5, -5, -5.5, 1.5, 5, 7), "plate", ramp="bone", base=3),
        part("tusk_r", "head", (-0.7, -0.7, -3.2, 1.4, 1.4, 3.2), "plate", ramp="bone", base=4, pose=(-3.4, -2.4, -5.4, 0.45, 0, 0)),
        part("tusk_r2", "tusk_r", (-0.5, -0.5, -3, 1, 1, 3), "plate", ramp="bone", base=5, pose=(0, 0, -3.1, -0.85, 0, 0)),
        part("vine_r", "head", (-0.5, 0, 0, 1, 7, 1), "hide", ramp="thorn", pose=(-5.8, -6, 3.5, 0, 0, 0), swing=0.5, base=3),
        part("thorn_crest", "head", (-0.5, -3, -1, 1, 3, 2), "plate", ramp="thorn", base=4, pose=(0, -10, -1, 0.1, 0, 0)),
        part("pollen_eye", "head", (-3.6, -5.2, -5.9, 1, 1, 1), "glass", ramp="pollen"),
    ]
    s += head + twins([head[2], head[3], head[4], head[5]], ["cheek_r", "tusk_r", "tusk_r2", "vine_r"], {"tusk_r": "tusk_l", "tusk_r2": "tusk_l2"})
    # lone pollen eye on the other side
    s.append(part("pollen_eye_l", "head", (2.6, -5.2, -5.9, 1, 1, 1), "glass", ramp="pollen"))
    body = [
        part("rib_1", "body", (-4.4, 0.8, -4.0, 8.8, 1, 1.3), "plate", ramp="bone", base=4),
        part("rib_2", "body", (-4.6, 2.8, -4.0, 9.2, 1, 1.3), "plate", ramp="bone", base=4),
        part("rib_3", "body", (-4.5, 4.8, -4.0, 9.0, 1, 1.3), "plate", ramp="bone", base=3),
        part("rib_4", "body", (-4.2, 6.8, -4.0, 8.4, 1, 1.3), "plate", ramp="bone", base=3),
        part("sternum", "body", (-0.7, 0.8, -4.7, 1.4, 7, 1), "plate", ramp="bone", base=4),
        part("belt", "body", (-4.6, 9, -3.6, 9.2, 2, 7.2), "studded", ramp="leather", stud="bone"),
        part("clasp_skull", "body", (-1.5, 8.6, -4.7, 3, 2.6, 1.2), "plate", ramp="bone", base=4),
        part("pelt", "body", (-4.5, 0, 0, 9, 9, 1.4), "hide", ramp="pelt", fringe=True, pose=(0, 1, 3.4, 0.05, 0, 0), swing=0.5, base=3),
        part("spine_1", "body", (-0.5, -3, -0.5, 1, 3, 1), "plate", ramp="thorn", base=4, pose=(0, 2, 3.1, 0.9, 0, 0)),
        part("spine_2", "body", (-0.5, -3, -0.5, 1, 3, 1), "plate", ramp="thorn", base=4, pose=(0, 5, 3.1, 0.9, 0, 0)),
        part("pauld_r", "right_arm", (-5.5, -3.5, -4.5, 9, 3, 9), "plate", ramp="leather", base=3, trim=None),
        part("pauld_rim_r", "right_arm", (-6, -0.8, -5, 10, 1.2, 10), "plate", ramp="bronze", base=4),
        part("shoulder_tusk_r", "right_arm", (-0.8, -5, -0.8, 1.6, 5, 1.6), "plate", ramp="bone", base=4, pose=(-1.5, -3.2, 0, -0.1, 0, -0.5)),
        part("shoulder_tusk_r2", "shoulder_tusk_r", (-0.6, -3.5, -0.6, 1.2, 3.5, 1.2), "plate", ramp="bone", base=5, pose=(0, -5, 0, 0, 0, 0.7)),
        part("vambrace_r", "right_arm", (-4.5, 6.5, -3.5, 7, 4, 7), "studded", ramp="leather", stud="bronze"),
        part("band_r", "right_arm", (-4.7, 8.2, -3.7, 7.4, 1.2, 7.4), "plate", ramp="bronze", base=4),
    ]
    s += body + twins(body[10:], ["pauld_r", "pauld_rim_r", "shoulder_tusk_r", "shoulder_tusk_r2", "vambrace_r", "band_r"],
                      {"shoulder_tusk_r": "shoulder_tusk_l", "shoulder_tusk_r2": "shoulder_tusk_l2"})
    legs = [
        part("fur_skirt_f", "right_leg", (-2.5, 0, 0, 5, 7, 1.4), "hide", ramp="pelt", fringe=True, pose=(0, -1, -4.1, 0, 0, 0), swing=-0.1, base=3),
        part("fur_skirt_b", "right_leg", (-2.5, 0, 0, 5, 7, 1.4), "hide", ramp="pelt", fringe=True, pose=(0, -1, 2.7, 0, 0, 0), swing=0.25, base=2),
        part("knee_plate_r", "right_leg", (-2.5, 0, 0, 5, 3, 2), "plate", ramp="bone", base=4, pose=(0, 4.2, -4.6, 0, 0, 0)),
        part("knee_thorn_r", "right_leg", (-0.5, -0.5, -3, 1, 1, 3), "plate", ramp="thorn", base=4, pose=(0, 5.6, -4.6, 0.3, 0, 0)),
        part("boot_cuff_r", "right_leg", (-3.5, 8, -3.5, 7, 2, 7), "hide", ramp="pelt", fringe=True, base=3),
        part("toe_r", "right_leg", (-2.5, 10, -4.6, 5, 2, 1.7), "plate", ramp="bone", base=3),
    ]
    s += legs + twins(legs, ["fur_skirt_f", "fur_skirt_b", "knee_plate_r", "knee_thorn_r", "boot_cuff_r", "toe_r"])
    return s


def wyrmscale():
    """The Dune Wyrm's house: overlapping sand scales, glass fins swept back from the helm and shoulders, a three-link tail."""
    s = []
    for p in shell_parts("scale", ramp="sandscale", base=3):
        if p["name"] == "sh_head":
            p["o"].update(window=[(1, 6, 4, 7)])
        s.append(p)
    head = [
        part("hood_scarf", "head", (-5.5, -2.5, -5.6, 11, 2.5, 1.2), "silk", ramp="sand", base=3, fold=3),
        part("helm_cap", "head", (-5.5, -10, -5.5, 11, 2.5, 11), "scale", ramp="sandscale", base=4, rim="amber"),
        part("helm_brow", "head", (-5.5, -7.7, -6.5, 11, 2, 1.5), "scale", ramp="sandscale", base=3, rim="amber"),
        part("fin_c", "head", (-0.7, -6, -3, 1.4, 6, 4), "glass", ramp="deep", pose=(0, -10, 1, -0.5, 0, 0), swing=-0.1),
        part("fin_r", "head", (-0.6, -5, -2, 1.2, 5, 3), "glass", ramp="deep", pose=(-3.5, -9.5, 1.5, -0.55, -0.35, -0.35), swing=-0.1),
        part("ear_fin_r", "head", (-0.5, -1.5, -2, 1, 3, 4), "glass", ramp="deep", pose=(-5.9, -3, 0.5, 0, 0.3, 0.2)),
    ]
    s += head + twins(head[4:], ["fin_r", "ear_fin_r"], {"fin_r": "fin_l", "ear_fin_r": "ear_fin_l"})
    body = [
        part("belly_1", "body", (-4.5, 0.8, -3.9, 9, 2, 1.3), "scale", ramp="sandscale", base=4, rim="amber"),
        part("belly_2", "body", (-4.6, 2.8, -3.9, 9.2, 2, 1.5), "scale", ramp="sandscale", base=3, rim="amber"),
        part("belly_3", "body", (-4.7, 4.8, -3.9, 9.4, 2, 1.7), "scale", ramp="sandscale", base=4, rim="amber"),
        part("belly_4", "body", (-4.8, 6.8, -3.9, 9.6, 2, 1.9), "scale", ramp="sandscale", base=3, rim="amber"),
        part("sash", "body", (-5, 9, -3.6, 10, 2.4, 7.2), "silk", ramp="sand", base=3, bevel=True),
        part("sash_tail", "body", (-1, 0, 0, 2, 8, 1), "silk", ramp="sand", pose=(2.6, 10.5, -4.2, 0, 0, 0), swing=-0.45, base=4, hem="amber", hem_rows=1),
        part("gem", "body", (-0.8, 8.6, -4.7, 1.6, 1.6, 1), "glass", ramp="deep"),
        part("back_fin_1", "body", (-0.7, -5, -1.5, 1.4, 5, 3), "glass", ramp="deep", pose=(0, 2.5, 3.2, -0.6, 0, 0), swing=-0.05),
        part("back_fin_2", "body", (-0.6, -4, -1.2, 1.2, 4, 2.4), "glass", ramp="deep", pose=(0, 6, 3.2, -0.6, 0, 0), swing=-0.05),
        part("tail_1", "body", (-1.6, 0, -1.6, 3.2, 5, 3.2), "scale", ramp="sandscale", base=3, pose=(0, 9.5, 3.4, 0.55, 0, 0), swing=0.25),
        part("tail_2", "tail_1", (-1.3, 0, -1.3, 2.6, 5, 2.6), "scale", ramp="sandscale", base=4, pose=(0, 5, 0, 0.45, 0, 0), swing=0.4, rim="amber"),
        part("tail_3", "tail_2", (-1.0, 0, -1.0, 2.0, 4, 2.0), "scale", ramp="sandscale", base=3, pose=(0, 5, 0, 0.45, 0, 0), swing=0.5),
        part("tail_barb", "tail_3", (-0.6, 0, -0.6, 1.2, 4, 1.2), "glass", ramp="deep", pose=(0, 4, 0, 0.3, 0, 0), swing=0.2),
        part("pauld_1_r", "right_arm", (-5.5, -3.5, -4.5, 9, 2, 9), "scale", ramp="sandscale", base=4, rim="amber"),
        part("pauld_2_r", "right_arm", (-6, -1.7, -4.9, 10, 2, 9.8), "scale", ramp="sandscale", base=3, rim="amber"),
        part("shoulder_fin_r", "right_arm", (-0.6, -6, -2, 1.2, 6, 4), "glass", ramp="deep", pose=(-2.5, -3.5, 1, -0.5, 0, -0.5), swing=-0.1),
        part("bracer_r", "right_arm", (-4.5, 6.5, -3.5, 7, 4, 7), "scale", ramp="sandscale", base=3, rim="amber"),
        part("bracer_gem_r", "right_arm", (-1.5, 7.5, -4.4, 2, 2, 1), "glass", ramp="deep"),
        part("wrap_r", "right_arm", (-4.5, 10.2, -3.5, 7, 2.5, 7), "silk", ramp="sand", base=3, fold=2),
    ]
    s += body + twins(body[13:], ["pauld_1_r", "pauld_2_r", "shoulder_fin_r", "bracer_r", "bracer_gem_r", "wrap_r"], {"shoulder_fin_r": "shoulder_fin_l"})
    legs = [
        part("panel_f_r", "right_leg", (-2.5, 0, 0, 5, 9, 1.2), "silk", ramp="sand", pose=(0, -1, -3.9, 0, 0, 0), swing=-0.12, base=3, fold=3, hem="amber", hem_rows=1, contact=True),
        part("panel_b_r", "right_leg", (-2.5, 0, 0, 5, 9, 1.2), "silk", ramp="sand", pose=(0, -1, 2.8, 0, 0, 0), swing=0.28, base=2, fold=3, hem="amber", hem_rows=1, contact=True),
        part("greave_r", "right_leg", (-2.6, 5.5, -3.8, 5.2, 4, 1.2), "scale", ramp="sandscale", base=4, rim="amber"),
        part("boot_cuff_r", "right_leg", (-3.5, 8, -3.5, 7, 2, 7), "scale", ramp="sandscale", base=3, rim="amber"),
        part("talon_r", "right_leg", (-2.2, 10, -5.2, 4.4, 1.8, 2.4), "glass", ramp="deep"),
    ]
    s += legs + twins(legs, ["panel_f_r", "panel_b_r", "greave_r", "boot_cuff_r", "talon_r"])
    return s


def antlerhold():
    """The Old Antler's house: a branching antler crown with frost-lit tines, a fur mantle, a bell, hoof-bone plates."""
    s = []
    for p in shell_parts("hide", ramp="frostfur", base=3):
        if p["name"] == "sh_head":
            p["o"].update(window=[(1, 6, 3, 7)])
        s.append(p)
    head = [
        part("hood_fur", "head", (-5.5, -10, -5.5, 11, 3, 11), "hide", ramp="frostfur", fringe=True, base=4),
        part("hood_side_r", "head", (-6.5, -8, -5, 1.5, 8, 10), "hide", ramp="frostfur", fringe=True, base=3),
        part("fur_brim", "head", (-5.5, -8, -6.6, 11, 2, 1.6), "hide", ramp="frostfur", fringe=True, base=5),
        part("beam_r", "head", (-0.8, -7, -0.8, 1.6, 7, 1.6), "plate", ramp="bone", base=4, pose=(-3.2, -9.5, 0.5, -0.25, 0, -0.45)),
        part("tine_r1", "beam_r", (-0.6, -5, -0.6, 1.2, 5, 1.2), "plate", ramp="bone", base=5, pose=(0, -3.5, 0, 0, 0, -0.7)),
        part("tine_r2", "beam_r", (-0.6, -4, -0.6, 1.2, 4, 1.2), "plate", ramp="bone", base=4, pose=(0, -6.5, 0, -0.4, 0, 0.5)),
        part("tine_r3", "beam_r", (-0.5, -3.5, -0.5, 1, 3.5, 1), "plate", ramp="bone", base=4, pose=(0, -1.5, 0, -1.0, 0, 0)),
        part("tip_r1", "tine_r1", (-0.5, -1.4, -0.5, 1, 1.4, 1), "glass", ramp="frostglow", pose=(0, -5, 0, 0, 0, 0)),
        part("tip_r2", "tine_r2", (-0.5, -1.4, -0.5, 1, 1.4, 1), "glass", ramp="frostglow", pose=(0, -4, 0, 0, 0, 0)),
        part("tip_r3", "beam_r", (-0.5, -1.4, -0.5, 1, 1.4, 1), "glass", ramp="frostglow", pose=(0, -7, 0, 0, 0, 0)),
    ]
    s += head + twins(head[1:2] + head[3:], ["hood_side_r", "beam_r", "tine_r1", "tine_r2", "tine_r3", "tip_r1", "tip_r2", "tip_r3"],
                      {"hood_side_r": "hood_side_l", "beam_r": "beam_l", "tine_r1": "tine_l1", "tine_r2": "tine_l2", "tine_r3": "tine_l3",
                       "tip_r1": "tip_l1", "tip_r2": "tip_l2", "tip_r3": "tip_l3"})
    body = [
        part("mantle_1", "body", (-8, -0.5, -4.5, 16, 2.5, 9), "hide", ramp="frostfur", fringe=True, base=5),
        part("mantle_2", "body", (-9, 1.8, -5, 18, 2.5, 10), "hide", ramp="frostfur", fringe=True, base=4),
        part("harness_f", "body", (-1, 0, 0, 2, 10, 1), "studded", ramp="leather", stud="bone", pose=(-2.5, 3, -4.2, 0, 0, 0)),
        part("harness_f2", "body", (-1, 0, 0, 2, 10, 1), "studded", ramp="leather", stud="bone", pose=(1.5, 3, -4.2, 0, 0, 0)),
        part("hoofplate", "body", (-3.5, 3.6, -4.7, 7, 5, 1.5), "plate", ramp="bone", base=3, rivet=True),
        part("bell", "body", (-1.2, 8.5, -5.4, 2.4, 2.8, 2.4), "plate", ramp="brass", base=4),
        part("bell_clapper", "body", (-0.4, 11.0, -4.6, 0.8, 1, 0.8), "plate", ramp="brass", base=5, swing=0.3),
        part("belt", "body", (-4.6, 9.3, -3.6, 9.2, 1.8, 7.2), "studded", ramp="leather", stud="bone"),
        part("cloak_a", "body", (-5.5, 0, 0, 11, 8, 1.4), "hide", ramp="frostfur", fringe=True, pose=(0, 1.5, 3.8, 0.05, 0, 0), swing=0.28, base=3),
        part("cloak_b", "cloak_a", (-5.5, 0, 0, 11, 9, 1.4), "hide", ramp="frostfur", fringe=True, pose=(0, 8, 0, 0.1, 0, 0), swing=0.5, base=3),
        part("cuff_r", "right_arm", (-4.5, 6.5, -3.5, 7, 4.5, 7), "hide", ramp="frostfur", fringe=True, base=5),
        part("bracer_r", "right_arm", (-4.5, 4.2, -3.5, 7, 2.5, 7), "plate", ramp="bone", base=3, rivet=True),
    ]
    s += body + twins(body[10:], ["cuff_r", "bracer_r"])
    legs = [
        part("fur_f_r", "right_leg", (-2.5, 0, 0, 5, 8, 1.4), "hide", ramp="frostfur", fringe=True, pose=(0, -1, -4.1, 0, 0, 0), swing=-0.1, base=4),
        part("fur_b_r", "right_leg", (-2.5, 0, 0, 5, 8, 1.4), "hide", ramp="frostfur", fringe=True, pose=(0, -1, 2.7, 0, 0, 0), swing=0.25, base=3),
        part("knee_r", "right_leg", (-2.5, 0, 0, 5, 3, 2), "plate", ramp="bone", base=4, pose=(0, 4.8, -4.6, 0, 0, 0)),
        part("boot_fur_r", "right_leg", (-3.5, 7.5, -3.5, 7, 2.6, 7), "hide", ramp="frostfur", fringe=True, base=5),
        part("hoof_r", "right_leg", (-2.5, 10, -4.6, 5, 2, 1.7), "plate", ramp="bone", base=3),
    ]
    s += legs + twins(legs, ["fur_f_r", "fur_b_r", "knee_r", "boot_fur_r", "hoof_r"])
    return s


# ---------------------------------------------------------------------------------------------------------------------
# Garments: parts that hang on any worn piece; the base set below is Gatewrought's plate

def garment_cloak(ramp="cloak_red", trim="brass"):
    return [
        part("yoke", "body", (-6.8, -0.8, -4.2, 13.6, 2.6, 8.4), "silk", ramp=ramp, base=3, hem=trim, hem_rows=1),
        part("cloak_a", "body", (-6, 0, 0, 12, 8, 1.2), "silk", ramp=ramp, pose=(0, 1, 4.6, 0.04, 0, 0), swing=0.3, base=3, fold=3, contact=True),
        part("cloak_b", "cloak_a", (-6, 0, 0, 12, 9, 1.2), "silk", ramp=ramp, pose=(0, 8, 0, 0.1, 0, 0), swing=0.5, base=3, fold=3, hem=trim, hem_rows=1),
        part("cloak_chain", "body", (-3, -0.2, -4.7, 6, 0.9, 0.9), "plate", ramp=trim, base=4),
        part("cloak_gem", "body", (-0.6, 0.4, -5.4, 1.2, 1.2, 0.8), "glass", ramp="signal"),
    ]


def garment_scarf(ramp="linen"):
    return [
        part("scarf_wrap", "body", (-5, -1.8, -3.6, 10, 2.6, 7.2), "silk", ramp=ramp, base=4, fold=2),
        part("scarf_t1", "body", (-1.3, 0, 0, 2.6, 7, 1), "silk", ramp=ramp, pose=(-2.6, -0.5, 3.8, 0, 0, 0), swing=0.45, base=4, fold=2),
        part("scarf_t2", "scarf_t1", (-1.3, 0, 0, 2.6, 7, 1), "silk", ramp=ramp, pose=(0, 7, 0, 0.1, 0, 0), swing=0.7, base=3, fold=2, hem="brass", hem_rows=1),
    ]


def garment_hat():
    return [
        part("hat_brim", "head", (-6.8, -9.6, -6.8, 13.6, 1, 13.6), "silk", ramp="coat", base=3, bevel=True),
        part("hat_crown", "head", (-4, -13.4, -4, 8, 4, 8), "silk", ramp="coat", base=4, fold=4),
        part("hat_band", "head", (-4.3, -10.8, -4.3, 8.6, 1.2, 8.6), "plate", ramp="brass", base=4),
        part("hat_feather", "head", (-0.5, -3, 0, 1, 3, 2.5), "silk", ramp="cloak_red", base=4, pose=(3.8, -12.5, -1.5, 0.5, 0, 0.3), swing=-0.3),
    ]


def garment_goggles():
    return [
        part("gog_strap", "head", (-5.3, -7.6, -5.3, 10.6, 1.2, 10.6), "plate", ramp="leather", base=3),
        part("gog_lens_r", "head", (-4, -8, -6.3, 3, 2.4, 1.2), "glass", ramp="lamp"),
        part("gog_lens_l", "head", (1, -8, -6.3, 3, 2.4, 1.2), "glass", ramp="lamp"),
        part("gog_bridge", "head", (-1, -7.4, -6.1, 2, 1.2, 1), "plate", ramp="brass", base=4),
    ]


def garment_bandolier():
    return [
        part("strap", "body", (-0.9, -7, 0, 1.8, 14, 0.9), "studded", ramp="leather", stud="bronze", pose=(0, 6.5, -3.9, 0, 0, 0.62)),
        part("pouch_1", "body", (-1.4, 0, 0, 2.8, 3, 1.5), "studded", ramp="leather", stud="bronze", pose=(-2.6, 4.4, -4.1, 0, 0, 0)),
        part("pouch_2", "body", (-1.4, 0, 0, 2.8, 3, 1.5), "studded", ramp="leather", stud="bronze", pose=(0.4, 7.2, -4.1, 0, 0, 0)),
        part("pouch_3", "body", (-1.4, 0, 0, 2.8, 3, 1.5), "studded", ramp="leather", stud="bronze", pose=(3.2, 10, -4.1, 0, 0, 0)),
    ]


def garment_lantern():
    return [
        part("lamp_chain", "body", (-0.3, 0, -0.3, 0.6, 3, 0.6), "plate", ramp="brass", base=3, pose=(-5.6, 10.5, -1, 0, 0, 0), swing=0.2),
        part("lamp_body", "lamp_chain", (-1.5, 0, -1.5, 3, 3.5, 3), "glass", ramp="lamp", pose=(0, 3, 0, 0, 0, 0)),
        part("lamp_cap", "lamp_chain", (-1.8, -0.8, -1.8, 3.6, 1, 3.6), "plate", ramp="brass", base=4, pose=(0, 3, 0, 0, 0, 0)),
    ]


def coat_base():
    """The off-duty base layer: the shells painted as a dark teal coat with a high collar and brass buttons."""
    s = []
    for p in shell_parts("silk", ramp="coat", base=2, fold=4):
        if p["name"] == "sh_head":
            p["o"].update(ramp="coat", base=3, window=[(1, 6, 3, 7)])
        if p["name"] == "sh_body":
            p["o"].update(thread=lambda i, j, fw, fh: i == fw // 2 and 1 < j < fh - 1 and j % 3 == 1, base=3)
        if p["name"] in ("sh_arm_r", "sh_arm_l"):
            p["o"].update(fold=0, hem="brass", hem_rows=1)
        s.append(p)
    s += [
        part("coat_collar", "body", (-5.5, -2.2, -3.8, 11, 2.6, 7.6), "silk", ramp="coat", base=4, fold=4),
        part("coat_skirt_f", "body", (-4.5, 0, 0, 9, 9, 1.2), "silk", ramp="coat", pose=(0, 11, -3.9, 0, 0, 0), swing=-0.1, base=3, fold=3, hem="brass", hem_rows=1, contact=True),
        part("coat_skirt_b", "body", (-4.5, 0, 0, 9, 10, 1.2), "silk", ramp="coat", pose=(0, 11, 2.8, 0, 0, 0), swing=0.35, base=2, fold=3, hem="brass", hem_rows=1, contact=True),
        part("belt", "body", (-4.6, 9, -3.5, 9.2, 1.8, 7), "plate", ramp="leather", base=3),
        part("buckle", "body", (-1.3, 8.8, -4.5, 2.6, 2.2, 1), "plate", ramp="brass", base=4),
    ]
    return s


def no_helm(parts):
    """Headwear replaces the helm: its parts are hidden and the shell's face opens."""
    out = [p for p in parts if not (p["name"].startswith("helm_") or p["name"].startswith("crest") or p["name"] == "gorget")]
    for p in out:
        if p["name"] == "sh_head":
            p["o"] = dict(p["o"], window=[(1, 6, 3, 7)])
    return out


def looks():
    plate = A.gatewrought_v2
    armed = plate()
    travelling = plate() + garment_cloak() + garment_scarf()
    wayfarer = no_helm(plate()) + garment_cloak("cloak_red", "brass") + garment_hat() + garment_goggles() + garment_bandolier() + garment_lantern()
    off = coat_base() + garment_scarf("cloak_red") + garment_hat() + garment_lantern() + garment_goggles()
    return [("Armed", armed), ("Travelling", travelling), ("Wayfarer", wayfarer), ("Off duty", off)]


def main(out):
    os.makedirs(out, exist_ok=True)
    size = (256, 256)
    kw = dict(scale=8, size=(W, H), centre=CTR, pitch=-0.2)
    kn = {k: v for k, v in kw.items() if k != "scale"}
    rows = []
    today = A.G.render_set("D", 0.7, scale=8, size=(W, H), centre=CTR, pitch=-0.2)
    todayb = A.G.render_set("D", math.pi, scale=8, size=(W, H), centre=CTR, pitch=-0.2)
    rows.append([A.G.render_set("D", 0.0, scale=8, size=(W, H), centre=CTR, pitch=-0.2), today, todayb, today])
    for name, fn in (("Briarguard", briarguard), ("Wyrmscale", wyrmscale), ("Antlerhold", antlerhold)):
        parts, alb, glw = A.prepare(fn(), size)
        rows.append([A.render(parts, alb, 0.0, 0.0, **kw), A.render(parts, alb, 0.7, 0.0, **kw), A.render(parts, alb, math.pi, 0.0, **kw),
                     A.render_night(parts, alb, glw, 0.7, **kn)])
        print(name, len(parts), "cubes,", len(parts) * 12, "triangles")
        big = dict(scale=20, size=(520, 560), centre=(260, 360), pitch=-0.15)
        A.grid([[A.render(parts, alb, 0.55, 0.0, **big), A.render_night(parts, alb, glw, 0.55, **big)]], w=520, h=560).save(
            os.path.join(out, "closeup_%s.png" % name))
    A.grid(rows).save(os.path.join(out, "houses_D.png"))

    lk = looks()
    top, back = [], []
    for name, parts in lk:
        ps, alb, glw = A.prepare(parts, size)
        top.append(A.render(ps, alb, 0.45, 0.15, **kw))
        back.append(A.render(ps, alb, math.pi - 0.45, 0.3, **kw))
        print(name, len(ps), "cubes")
    A.grid([top, back]).save(os.path.join(out, "garments.png"))


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "renders"))
