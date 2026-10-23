"""Core shared items."""
from pixelart import *


def build(out):
    out.item("essence_shard", shard("amber"))
    out.item("lord_heart", orb("crimson", core_ramp="amber"))
