"""Look-test samples for the shared palette (not shipped; written under textures/_sample)."""
from pixelart import *


def build(out):
    out.block("../_sample/gate_frame", glyph(stone("obsidian", seed="gf", bevel=True), "signal", seed="gf"))
    out.block("../_sample/precursor_plate", circuits(plates("precursor", seed="pp"), "signal", seed="pp"))
    out.block("../_sample/crypt_brick", bricks("stone", seed="cb"))
    out.block("../_sample/moss_stone", overlay_noise(stone("stone", seed="ms"), "moss", seed="ms", density=0.25))
    out.block("../_sample/frost_brick", bricks("frost", seed="fb"))
    out.block("../_sample/dream_cloud", stone("cloud", seed="dc", scale=2, contrast=0.6, lo=2))
    out.item("../_sample/power_core", orb("signal", core_ramp="amber", ring=True))
    out.item("../_sample/essence_shard", shard("amber"))
    out.item("../_sample/gate_blade", blade())
    out.item("../_sample/dream_journal", book())
    out.item("../_sample/satchel", pouch())
    out.item("../_sample/scanner", device())
    out.item("../_sample/warden_trophy", trophy())
