"""What each ladder step runs (SPEC 34.3-34.5), family by family: its riders, its look layers and the numbers its own code reads
(power code asks with c.num(level, key, def) and c.step(level)). The names and texts are the P rows in power_ladder_table.py.

A look rides an event kind the power really sends (the "on" list); tools/art/specs/power_ladder_table.py build checks the names,
and the GameTest power_ladder_complete checks every power's steps against what it sent. Edit here, then run:
    python3 tools/art/specs/power_ladder_table.py build
"""
from power_ladder_table import (S, aftershock, bloom, burst, chain, column, dome, echo, every, extend, fissure, forks, ghost, grow,
                                guard, helix, linger, orbit, pierce, push, ramp, refund, rings, sigil, split, status, trail)

PI2 = 1.5708  # a quarter turn: an orbit stood on its side, round the arm or the aim

# ---- Kinetic: heavy, low, the ground answers ------------------------------------------------------------------------
S("haymaker", 2, [extend(charge=0.2)],
  [orbit("CHARGE", "shard", n=6, r0=0.75, r1=0.12, spin=0.32, size=0.13, at="hand")])
S("haymaker", 3, [],
  [rings("IMPACT", n=2, gap=2, r0=0.2, r1=1.9, width=0.35, life=8, face="dir", col="core"),
   burst("IMPACT", "wind", n=8, speed=0.55, life=10, size=0.4)], behind=0.5, reach=3.5)
S("haymaker", 4, [],
  [trail("IMPACT", width=0.42, life=26, at="target", col="edge"),
   burst("IMPACT", "dust", n=10, speed=0.18, life=24, size=0.5, grav=-0.004, col="edge")], fly=0.6)
S("haymaker", 5, [echo(0.4, 7)],
  [orbit("CHARGE", "ember", n=10, r0=0.3, r1=0.26, spin=0.55, size=0.12, at="hand", tilt=PI2, col="accent"),
   rings("IMPACT", n=3, gap=1, r0=0.15, r1=1.0, width=0.25, life=7, face="dir", col="core"),
   bloom("IMPACT", size=0.8)])

S("shockwave", 2, [extend(range=0.15)],
  [grow("CONE", 1.15), burst("CONE", "shard", n=10, speed=0.42, life=16, size=0.2, grav=0.05, col="edge")])
S("shockwave", 3, [],
  [fissure("CONE", n=1, length=6.5, width=0.12, cool=30, along="dir", at="origin")], spine=0.5, delay=10)
S("shockwave", 4, [push(0.0, 0.45), status("stun", 10)],
  [grow("CONE", 1.25), burst("CONE", "dust", n=14, speed=0.3, life=22, size=0.6, grav=0.02, col="edge")])
S("shockwave", 5, [status("stun", 10)],
  [fissure("CONE", n=1, length=8.5, width=0.22, cool=50, along="dir", at="origin", col="accent"),
   burst("CONE", "plate", n=12, speed=0.5, life=20, size=0.45, grav=0.07),
   rings("CONE", n=2, gap=4, r0=0.4, r1=3.2, width=0.4, life=10, col="core")], burn=40, dps=1.0)

S("ground_slam", 2, [status("slow", 20, 1)],
  [fissure("SLAM", n=4, length=4.5, width=0.15, cool=40)])
S("ground_slam", 3, [aftershock(0.5, 10, 4.5)],
  [rings("SLAM", n=1, delay=10, r0=0.5, r1=5.0, width=0.5, life=12, col="edge")])
S("ground_slam", 4, [],
  [burst("SLAM", "plate", n=14, speed=0.38, life=24, size=0.45, grav=0.06, at="ground")], lift=0.65, inner=0.5)
S("ground_slam", 5, [],
  [dome("SLAM", radius=4.6, life=14, at="ground"), fissure("SLAM", n=6, length=5.5, width=0.18, cool=50, col="accent"),
   sigil("SLAM", at="ground", size=4.2, spin=0.02, face="up", life=40), bloom("SLAM", size=1.3)], pull=0.7)

S("leap", 2, [extend(range=0.15)],
  [trail("DASH", width=0.35, life=26, at="feet"), burst("DASH", "dust", n=10, speed=0.2, life=20, size=0.5, col="edge")])
S("leap", 3, [status("slow", 20, 1)],
  [fissure("SLAM", n=3, length=3.5, width=0.13, cool=36)], land=1.3)
S("leap", 4, [status("lift", 8)],
  [rings("SLAM", n=1, delay=2, r0=0.4, r1=3.6, width=0.45, life=10, col="core")], steady=20)
S("leap", 5, [],
  [sigil("SLAM", at="ground", size=3.0, spin=0.03, face="up", life=36), fissure("SLAM", n=5, length=4.0, width=0.18, cool=48, col="accent"),
   burst("SLAM", "plate", n=12, speed=0.45, life=22, size=0.45, grav=0.07)], plunge=1.0)

S("terrain_rip", 2, [extend(radius=0.15)],
  [trail("TRAIL", width=0.5, life=30, at="body", col="accent"), burst("SLAM", "ember", n=10, speed=0.3, life=18, size=0.16)])
S("terrain_rip", 3, [],
  [burst("SLAM", "shard", n=14, speed=0.45, life=18, size=0.22, grav=0.05)], rocks=3, share=0.35)
S("terrain_rip", 4, [],
  [rings("SLAM", n=1, r0=0.3, r1=2.6, width=0.35, life=9, col="edge")], twin=0.6, delay=8)
S("terrain_rip", 5, [],
  [fissure("SLAM", n=1, length=7.0, width=0.2, cool=44, along="dir", col="accent"),
   rings("SLAM", n=2, gap=3, r0=0.5, r1=4.0, width=0.45, life=11, col="core")], hurl=1.0)

S("brace", 2, [],
  [fissure("RING", n=3, length=1.7, width=0.1, cool=30)], reflect=0.1)
S("brace", 3, [],
  [rings("SLAM", n=1, r0=0.4, r1=4.2, width=0.5, life=10, col="core")], shove=0.4)
S("brace", 4, [],
  [sigil("AURA", at="ground", size=1.7, spin=0.02, face="up", life=0, col="edge")], projectile=0.5)
S("brace", 5, [],
  [orbit("AURA", "plate", n=3, r0=1.15, r1=1.1, spin=0.08, size=0.42, at="body"),
   burst("SLAM", "plate", n=10, speed=0.6, life=16, size=0.4, grav=0.05)], blocks=3, share=0.5)

S("thunderclap", 2, [],
  [forks("CAST", n=3, reach=0.7, life=8, at="hand")], stun=4)
S("thunderclap", 3, [chain(1, 0.5, 5.0)],
  [forks("STRIKE", n=2, reach=2.4, life=6)])
S("thunderclap", 4, [],
  [orbit("RING", "bolt", n=5, r0=0.5, r1=0.7, spin=0.2, size=0.2, at="head", life=60, col="core")], storm=60, share=0.35)
S("thunderclap", 5, [],
  [rings("RING", n=1, r0=0.5, r1=6.5, width=0.18, life=8, col="core"), column("CAST", height=12, width=0.45, life=10, at="feet")], chainbuff=60, chain=0.5)

# ---- Velocity: streaks, red, the line you leave ---------------------------------------------------------------------
S("dash", 2, [],
  [rings("DASH", n=1, r0=0.3, r1=1.7, width=0.25, life=8, face="dir", col="core"), trail("DASH", width=0.22, life=14, at="back")], shove=0.6)
S("dash", 3, [],
  [orbit("DASH", "spark", n=4, r0=0.24, r1=0.2, spin=0.45, size=0.1, at="hand", life=20, col="core")], bonus=0.25, ticks=20)
S("dash", 4, [],
  [rings("DASH", n=1, r0=0.7, r1=0.95, width=0.12, life=28, at="body")], chain_from=3, share=0.5)
S("dash", 5, [],
  [trail("DASH", width=0.12, life=30, at="body", col="core")], snap=0.6, delay=12)

S("flow", 2, [],
  [dome("RING", radius=8.0, life=12, col="edge")], extra=10)
S("flow", 3, [],
  [trail("AURA", width=0.55, life=0, at="body")], kill=10, cap=40)
S("flow", 4, [],
  [rings("CHARGE_END", n=1, r0=0.4, r1=3.0, width=0.35, life=8, col="core"), burst("CHARGE_END", "spark", n=10, speed=0.4, life=10, size=0.16)],
  ticks=60, speed=0.2)
S("flow", 5, [status("mark", 200)],
  [bloom("CHARGE_END", size=1.0)], snap=0.3)

S("afterimage", 2, [extend(duration=0.25)],
  [bloom("AFTERIMAGE", size=0.6, col="mid")])
S("afterimage", 3, [],
  [burst("AFTERIMAGE", "slash", n=2, speed=0.2, life=8, size=0.6)], mimic=0.5)
S("afterimage", 4, [],
  [forks("AFTERIMAGE", n=4, reach=1.2, life=12, col="edge")], snare=40)
S("afterimage", 5, [],
  [burst("AFTERIMAGE", "slash", n=8, speed=0.5, life=10, size=0.5, col="core")], swap=40)

S("wall_run", 2, [],
  [every(3, burst("TRAIL", "spark", n=3, speed=0.18, life=8, size=0.12, at="feet", col="core"))], extra=10)
S("wall_run", 3, [],
  [every(30, rings("TRAIL", n=1, r0=0.2, r1=1.4, width=0.2, life=8, at="body", face="dir"))], kick=0.35)
S("wall_run", 4, [],
  [trail("TRAIL", width=0.06, life=40, at="feet", col="core")], steady=1.0)
S("wall_run", 5, [],
  [trail("TRAIL", width=0.3, life=18, at="body")], chain=20)

S("water_run", 2, [],
  [every(4, burst("TRAIL", "dust", n=4, speed=0.15, life=12, size=0.3, grav=0.03, at="feet", col="edge"))], noslow=1.0)
S("water_run", 3, [],
  [every(6, rings("TRAIL", n=1, r0=0.2, r1=1.2, width=0.18, life=14, at="feet"))], keep=1.0)
S("water_run", 4, [],
  [every(10, sigil("TRAIL", at="feet", size=1.6, spin=0.02, face="up", life=14, col="edge"))], stand=40)
S("water_run", 5, [],
  [every(4, rings("TRAIL", n=1, r0=0.3, r1=1.6, width=0.22, life=16, at="feet", col="core"))], wave=0.8)

S("lightning_step", 2, [],
  [grow("LIGHTNING", 1.25), burst("BLINK", "bolt", n=6, speed=0.25, life=8, size=0.18, col="core")], range=2)
S("lightning_step", 3, [],
  [forks("IMPACT", n=4, reach=2.2, life=10, at="ground")], field=40, radius=2.5)
S("lightning_step", 4, [],
  [orbit("BLINK", "bolt", n=3, r0=0.4, r1=0.4, spin=0.35, size=0.14, at="feet", life=20, col="core")], window=20)
S("lightning_step", 5, [],
  [column("BLINK", height=14, width=0.4, life=9), column("IMPACT", height=14, width=0.4, life=9)], stun=10)

# ---- Flight and gravity: rising, orbiting, inward -------------------------------------------------------------------
S("hover", 2, [extend(duration=0.25)],
  [orbit("AURA", "wind", n=6, r0=0.75, r1=0.7, spin=0.2, size=0.32, at="feet")])
S("hover", 3, [],
  [orbit("AURA", "wind", n=8, r0=0.95, r1=0.5, spin=0.3, size=0.28, at="body", col="edge")], drift=0.5)
S("hover", 4, [],
  [orbit("AURA", "mote", n=10, r0=1.4, r1=1.4, spin=0.12, size=0.12, at="body", tilt=0.5, col="core")], arrows=0.5)
S("hover", 5, [],
  [sigil("AURA", at="feet", size=2.4, spin=0.02, face="up")], platform=3)

S("flight", 2, [],
  [trail("AURA", width=0.08, life=0, at="hand", col="core"), trail("AURA", width=0.08, life=0, at="feet", col="core")], drain=0.1)
S("flight", 3, [],
  [every(30, rings("TRAIL", n=1, r0=0.5, r1=3.0, width=0.3, life=8, face="dir", col="edge"))], speed=0.1)
S("flight", 4, [],
  [trail("AURA", width=0.16, life=0, at="back", col="edge")], drain=0.1)
S("flight", 5, [],
  [sigil("AURA", at="back", size=1.4, spin=0.01, face="aim", col="accent")], burst=40)

S("gravity_well", 2, [],
  [bloom("VORTEX", size=0.6, col="edge"), burst("VORTEX", "dust", n=12, speed=-0.25, life=20, size=0.4, col="edge")], pull=0.15)
S("gravity_well", 3, [],
  [orbit("VORTEX", "plate", n=8, r0=2.6, r1=1.4, spin=0.15, size=0.32, at="origin")], debris=0.15)
S("gravity_well", 4, [ramp(0.5, 60)],
  [rings("VORTEX", n=3, gap=15, r0=3.0, r1=0.4, width=0.25, life=14, col="core")])
S("gravity_well", 5, [],
  [sigil("VORTEX", at="origin", size=3.2, spin=0.1, face="up", col="accent"), bloom("NOVA", size=1.4)], burst=0.3)

S("telekinesis", 2, [extend(duration=0.25)],
  [orbit("LIFT", "rune", n=6, r0=0.9, r1=0.9, spin=0.12, size=0.18, at="origin")])
S("telekinesis", 3, [],
  [burst("LIFT", "mote", n=10, speed=0.12, life=20, size=0.14)], pair=1)
S("telekinesis", 4, [],
  [trail("LIFT", width=0.25, life=30, at="target", col="edge")], tumble=0.4)
S("telekinesis", 5, [],
  [trail("TRAIL", width=0.3, life=24, at="body", col="accent"), sigil("CAST", at="hand", size=0.8)], all=1.0)

S("meteor_dive", 2, [],
  [rings("METEOR", n=2, gap=3, r0=0.4, r1=1.4, width=0.25, life=8, face="dir", col="core")], speed=0.15)
S("meteor_dive", 3, [],
  [rings("SLAM", n=1, r0=2.2, r1=2.6, width=0.6, life=40, at="ground", col="accent")], burn=40, radius=2.6, dps=1.0)
S("meteor_dive", 4, [aftershock(0.5, 12, 3.0)],
  [rings("SLAM", n=1, delay=12, r0=0.4, r1=3.4, width=0.4, life=10, col="edge")], hop=0.5)
S("meteor_dive", 5, [],
  [sigil("SLAM", at="ground", size=3.6, spin=0.03, face="up", life=40), bloom("SLAM", size=1.4)], meteors=3, share=0.4)

S("gravity_flip", 2, [],
  [burst("LIFT", "dust", n=14, speed=0.15, life=26, size=0.35, grav=-0.03, col="edge")], hang=10)
S("gravity_flip", 3, [],
  [forks("LIFT", n=3, reach=3.0, life=10, col="mid")], collide=0.4)
S("gravity_flip", 4, [],
  [orbit("RING", "mote", n=8, r0=0.7, r1=0.7, spin=0.1, size=0.12, at="body", life=40, col="core")], float=40)
S("gravity_flip", 5, [],
  [rings("SLAM", n=2, gap=3, r0=0.5, r1=4.0, width=0.4, life=10, col="core")], drops=2)

# ---- Energy: orbs, straight lines, bloom ----------------------------------------------------------------------------
S("beam", 2, [ramp(0.2, 30)],
  [helix("BEAM", strands=1, turns=0.85, radius=0.2, width=0.045, spin=0.2, col="accent"),
   every(8, burst("BEAM", "ember", n=3, speed=0.12, life=14, size=0.12, grav=0.01, at="end", col="mid"))])
S("beam", 3, [pierce(1, 0.6)],
  [helix("BEAM", strands=2, turns=0.85, radius=0.22, width=0.045, spin=0.22, col="accent")])
S("beam", 4, [linger("burn", 40, 1.6, 1.0)],
  [every(12, orbit("BEAM", "glow", n=6, r0=0.55, r1=0.6, spin=0.3, size=0.16, at="end", life=14, col="core"))])
S("beam", 5, [],
  [helix("BEAM", strands=3, turns=0.9, radius=0.24, width=0.05, spin=0.25, col="accent"),
   sigil("CHARGE", at="hand", size=0.75, spin=0.08, face="aim"),
   bloom("CHARGE_END", size=0.9, col="core")], pulse=0.2)

S("charged_shot", 2, [extend(charge=0.15)],
  [orbit("CHARGE", "bolt", n=5, r0=0.34, r1=0.24, spin=0.5, size=0.14, at="hand", tilt=PI2, col="core")])
S("charged_shot", 3, [chain(1, 0.5, 6.0)],
  [forks("IMPACT", n=2, reach=2.0, life=8)])
S("charged_shot", 4, [],
  [burst("CHARGE_END", "spark", n=12, speed=0.35, life=10, size=0.16, col="core")], over=10)
S("charged_shot", 5, [],
  [column("IMPACT", height=14, width=0.42, life=9), sigil("CHARGE", at="hand", size=0.6, spin=0.1, face="aim")], line=40, dps=1.5)

S("nova", 2, [extend(radius=0.1)],
  [orbit("NOVA", "mote", n=10, r0=1.0, r1=5.0, spin=0.2, size=0.2, at="origin", life=14, tilt=0.6),
   orbit("NOVA", "mote", n=10, r0=1.0, r1=5.0, spin=-0.2, size=0.2, at="origin", life=14, tilt=-0.6, col="core")])
S("nova", 3, [],
  [dome("NOVA", radius=4.5, life=26, col="edge")], hold=20, burn=40)
S("nova", 4, [],
  [forks("NOVA", n=3, reach=6.0, life=8)], arcs=3, share=0.5, reach=4.0)
S("nova", 5, [status("blind", 20), linger("burn", 40, 2.0, 1.0)],
  [bloom("CHARGE", size=1.2, at="body"), column("NOVA", height=14, width=0.7, life=14),
   rings("NOVA", n=1, delay=2, r0=0.5, r1=7.0, width=0.5, life=12, at="ground", col="core")], rise=0.35)

S("barrier", 2, [extend(duration=0.2)],
  [orbit("DOME", "hex", n=12, r0=1.3, r1=1.3, spin=0.03, size=0.3, at="body", tilt=0.3)])
S("barrier", 3, [],
  [bloom("NOVA", size=0.9)], stored=0.5)
S("barrier", 4, [],
  [rings("NOVA", n=1, delay=4, r0=1.4, r1=1.0, width=0.2, life=10, at="body", col="edge")], reform=60, strength=0.33)
S("barrier", 5, [],
  [orbit("DOME", "plate", n=3, r0=1.25, r1=1.25, spin=0.07, size=0.55, at="body", col="accent"), bloom("NOVA", size=1.5)], blocks=3)

S("overload", 2, [],
  [forks("CHARGE", n=4, reach=1.5, life=10, at="body")], glow=40)
S("overload", 3, [],
  [forks("NOVA", n=5, reach=7.0, life=8)], forks=5, share=0.4)
S("overload", 4, [],
  [orbit("AURA", "bolt", n=4, r0=0.8, r1=0.8, spin=0.3, size=0.16, at="body", col="core")], live=0.4)
S("overload", 5, [linger("burn", 60, 3.0, 1.5)],
  [dome("NOVA", radius=6.0, life=16), rings("NOVA", n=2, gap=4, r0=0.5, r1=7.5, width=0.5, life=12, at="ground", col="core")], pull=0.8)

S("prism_lance", 2, [],
  [every(8, bloom("BEAM", size=0.5, at="end", col="accent"))], reach=4)
S("prism_lance", 3, [pierce(1, 1.0)],
  [helix("BEAM", strands=1, turns=0.7, radius=0.18, width=0.04, spin=0.25, col="accent")])
S("prism_lance", 4, [],
  [every(10, burst("BEAM", "spark", n=4, speed=0.25, life=8, size=0.12, at="end", col="core"))], bounce=1)
S("prism_lance", 5, [],
  [orbit("CHARGE", "flare", n=6, r0=0.55, r1=0.55, spin=0.08, size=0.22, at="head", col="core")], crown=6)

# ---- Shadow: jagged, ink, the cut ----------------------------------------------------------------------------------
S("shadow_step", 2, [],
  [burst("BLINK", "smoke", n=10, speed=0.1, life=26, size=0.6, col="edge")], window=20)
S("shadow_step", 3, [],
  [burst("BLINK", "smoke", n=8, speed=0.25, life=16, size=0.5, at="end", col="edge")], blind=20)
S("shadow_step", 4, [],
  [sigil("BLINK", at="origin", size=1.3, spin=0.03, face="up", life=40, col="edge")], back=40)
S("shadow_step", 5, [],
  [burst("BLINK", "slash", n=3, speed=0.3, life=9, size=0.9, at="end", col="core"),
   sigil("BLINK", at="end", size=1.8, spin=0.05, face="up", life=24)], hold=20, radius=3.0)

S("cloak", 2, [extend(duration=0.15)],
  [every(20, burst("AURA", "smoke", n=3, speed=0.05, life=20, size=0.5, at="body", col="edge"))], outline=1)
S("cloak", 3, [],
  [trail("AURA", width=0.12, life=0, at="hand", col="edge")], free=1)
S("cloak", 4, [status("mark", 60)],
  [burst("SMOKE", "slash", n=2, speed=0.15, life=10, size=0.5)], bleed=60, dps=1.0)
S("cloak", 5, [],
  [dome("CHARGE_END", radius=5.0, life=20, col="edge"), dome("SMOKE", radius=5.0, life=60, col="edge")], dusk=5.0)

S("echo_swap", 2, [],
  [grow("BLINK", 1.2), forks("RING", n=2, reach=1.0, life=10, col="edge")], range=4)
S("echo_swap", 3, [status("slow", 40, 1)],
  [burst("SMOKE", "smoke", n=6, speed=0.08, life=20, size=0.45, col="edge")])
S("echo_swap", 4, [],
  [ghost("BLINK", delay=3, scale=0.5)], back=40)
S("echo_swap", 5, [],
  [sigil("RING", at="origin", size=1.5, spin=0.04, face="up", life=60)], copy=60)

S("legion", 2, [],
  [bloom("SUMMON", size=0.7, col="edge")], extra=1)
S("legion", 3, [],
  [burst("SUMMON", "smoke", n=10, speed=0.3, life=14, size=0.5, col="edge")], lunge=1)
S("legion", 4, [],
  [rings("SUMMON", n=1, r0=0.3, r1=1.8, width=0.3, life=10, col="edge")], ink=60)
S("legion", 5, [],
  [sigil("GLYPH", at="ground", size=4.5, spin=0.02, face="up", life=60)], captain=1)

# ---- Mind: concentric, still, gold -----------------------------------------------------------------------------------
S("danger_sense", 2, [], [], arc=1)
S("danger_sense", 3, [], [], lead=4)
S("danger_sense", 4, [], [], shots=1)
S("danger_sense", 5, [],
  [every(20, rings("PSI", n=1, r0=0.3, r1=2.2, width=0.18, life=10, at="feet", col="core"))], slow=20)

S("dominate", 2, [extend(duration=0.12)],
  [orbit("AURA", "rune", n=5, r0=0.45, r1=0.45, spin=0.1, size=0.16, at="head", col="core")])
S("dominate", 3, [],
  [burst("IMPACT", "mote", n=6, speed=0.1, life=20, size=0.14)], mark=60)
S("dominate", 4, [],
  [rings("CHARGE_END", n=2, gap=3, r0=0.3, r1=3.5, width=0.3, life=10)], burst=0.5, radius=3.5)
S("dominate", 5, [],
  [sigil("AURA", at="head", size=0.9, spin=0.05, face="up", col="accent")], hard=1.5, elite=1)

S("read_intent", 2, [],
  [rings("PSI", n=1, r0=0.3, r1=0.8, width=0.08, life=20, at="feet")], range=4)
S("read_intent", 3, [], [], tells=1)
S("read_intent", 4, [], [], walls=1)
S("read_intent", 5, [],
  [sigil("AURA", at="head", size=0.7, spin=0.05, face="aim", col="core")], crit=1)

S("decoy", 2, [extend(duration=0.2)],
  [bloom("BLINK", size=0.6, col="mid")])
S("decoy", 3, [], [], echo=0.3)
S("decoy", 4, [],
  [orbit("PSI", "rune", n=4, r0=0.5, r1=0.5, spin=0.15, size=0.16, at="head", life=40)], confuse=40)
S("decoy", 5, [],
  [rings("BLINK", n=1, r0=0.4, r1=3.0, width=0.25, life=10, col="core")], count=3)

S("foresight", 2, [], [], lines=1)
S("foresight", 3, [extend(duration=0.15)],
  [dome("RING", radius=6.0, life=16, col="edge")])
S("foresight", 4, [], [], stagger=20)
S("foresight", 5, [],
  [sigil("AURA", at="feet", size=2.2, spin=0.02, face="up", col="accent")], misses=2)

# ---- Architect: grids, seams, the made thing -------------------------------------------------------------------------
for _p in ("wall", "ramp", "floor", "cone", "rampart"):
    S(_p, 2, [], [burst("CONSTRUCT", "plate", n=8, speed=0.08, life=24, size=0.3, col="edge")], cd=0.5, longer=100)
S("wall", 3, [], [burst("CONSTRUCT", "spark", n=8, speed=0.2, life=10, size=0.14, col="core")], ricochet=1)
S("wall", 4, [], [burst("CONSTRUCT", "shard", n=10, speed=0.06, life=30, size=0.24, col="core")], tough=2.0)
S("wall", 5, [], [bloom("CONSTRUCT", size=1.0)], mirror=5.0)
S("ramp", 3, [], [burst("CONSTRUCT", "spark", n=6, speed=0.15, life=14, size=0.14, col="core")], rush=40)
S("ramp", 4, [], [burst("CONSTRUCT", "mote", n=10, speed=0.05, life=30, size=0.14, col="core")], rails=0.6)
S("ramp", 5, [], [bloom("CONSTRUCT", size=0.9)], launch=1.4)
S("floor", 3, [], [burst("CONSTRUCT", "mote", n=8, speed=0.05, life=24, size=0.14, col="core")], spring=0.9)
S("floor", 4, [], [burst("CONSTRUCT", "plate", n=8, speed=0.04, life=30, size=0.3, col="core")], hold=2.0)
S("floor", 5, [], [bloom("CONSTRUCT", size=0.9)], bridge=120)
S("cone", 3, [], [burst("CONSTRUCT", "mote", n=10, speed=0.03, life=40, size=0.14, grav=0.004, col="core")], heal=0.5)
S("cone", 4, [], [burst("CONSTRUCT", "shard", n=8, speed=0.06, life=26, size=0.22, col="core")], shelter=1)
S("cone", 5, [], [bloom("CONSTRUCT", size=1.0)], pagoda=1)
S("rampart", 3, [], [burst("CONSTRUCT", "spark", n=8, speed=0.1, life=16, size=0.14, col="core")], bolts=40, bolt=4.0)
S("rampart", 4, [], [burst("CONSTRUCT", "mote", n=8, speed=0.04, life=26, size=0.14, col="accent")], gate=1)
S("rampart", 5, [], [bloom("CONSTRUCT", size=1.0)], keep=1)

S("spire", 2, [],
  [column("PILLAR", height=8, width=0.5, life=12, col="core")], lift=0.15)
S("spire", 3, [],
  [burst("PILLAR", "shard", n=12, speed=0.35, life=14, size=0.22, grav=0.05, at="ground")], spikes=0.5)
S("spire", 4, [],
  [grow("PILLAR", 1.3)], tall=0.3)
S("spire", 5, [],
  [orbit("PILLAR", "plate", n=8, r0=1.4, r1=1.2, spin=0.08, size=0.4, at="origin", life=40, tilt=0.4)], stair=1)

S("collapse", 2, [extend(radius=0.25)],
  [orbit("RING", "plate", n=8, r0=2.0, r1=1.2, spin=0.18, size=0.35, at="body", life=16)])
S("collapse", 3, [],
  [trail("IMPACT", width=0.1, life=16, at="target", col="core")], seek=1)
S("collapse", 4, [aftershock(0.3, 2, 2.0)],
  [burst("IMPACT", "shard", n=10, speed=0.35, life=14, size=0.2, grav=0.05)])
S("collapse", 5, [status("stun", 16)],
  [bloom("IMPACT", size=0.8)], comet=1)

S("citadel", 2, [extend(duration=0.1)],
  [orbit("DOME", "rune", n=10, r0=4.5, r1=4.5, spin=0.04, size=0.3, at="origin", tilt=0.2)])
S("citadel", 3, [], [column("CONSTRUCT", height=10, width=0.4, life=20)], spires=40, bolt=3.0)
S("citadel", 4, [], [sigil("GLYPH", at="ground", size=5.0, spin=0.01, face="up", life=0)], heal=0.5)
S("citadel", 5, [], [column("DOME", height=18, width=0.7, life=30, col="core")], burn=1.0)

# ---- Tech: precise, thin, holographic ------------------------------------------------------------------------------
S("grapple", 2, [],
  [sigil("TETHER", at="end", size=0.7, spin=0.15, face="aim", life=10, col="core")], range=2)
S("grapple", 3, [status("stun", 10)],
  [forks("IMPACT", n=3, reach=1.0, life=8)])
S("grapple", 4, [],
  [grow("TETHER", 1.2)], reel=0.2)
S("grapple", 5, [],
  [rings("IMPACT", n=1, r0=0.3, r1=2.5, width=0.3, life=9, col="core")], kick=1.0)

S("jet_burst", 2, [],
  [rings("EXHAUST", n=3, gap=1, r0=0.2, r1=0.35, width=0.1, life=6, at="feet", face="dir", col="core")], speed=0.1)
S("jet_burst", 3, [],
  [trail("EXHAUST", width=0.25, life=40, at="feet", col="accent")], burn=40)
S("jet_burst", 4, [],
  [burst("EXHAUST", "spark", n=8, speed=0.2, life=12, size=0.12, at="feet", col="core")], hang=10)
S("jet_burst", 5, [],
  [rings("EXHAUST", n=1, r0=0.4, r1=3.5, width=0.35, life=9, face="dir", col="core")], boom=0.8)

S("drone", 2, [extend(duration=0.1)],
  [sigil("CAST", at="hand", size=0.5, spin=0.12, face="aim", life=12)])
S("drone", 3, [status("mark", 60)],
  [every(10, rings("HOLO", n=1, r0=0.3, r1=0.6, width=0.05, life=10, at="target", col="core"))])
S("drone", 4, [], [], bursts=3)
S("drone", 5, [], [], overwatch=1, dps=2.0)

S("turret", 2, [], [], sight=4)
S("turret", 3, [status("slow", 30, 1)], [])
S("turret", 4, [], [], shield=0.5)
S("turret", 5, [], [], rail=100)

S("railgun", 2, [extend(charge=0.15)],
  [orbit("CHARGE", "ring", n=3, r0=0.3, r1=0.3, spin=0.4, size=0.3, at="hand", tilt=PI2, col="core")])
S("railgun", 3, [push(0.8, 0.1)],
  [rings("RAIL", n=4, gap=1, r0=0.4, r1=0.6, width=0.08, life=14, face="dir", col="edge")])
S("railgun", 4, [extend(charge=0.15)],
  [bloom("CHARGE", size=0.5, at="hand", col="core")])
S("railgun", 5, [],
  [helix("RAIL", strands=2, turns=0.6, radius=0.18, width=0.04, spin=0.3, col="core")], blast=0.6, delay=10)

S("nanite_skin", 2, [extend(duration=0.12)],
  [orbit("AURA", "hex", n=10, r0=0.7, r1=0.7, spin=0.06, size=0.2, at="body", tilt=0.4)])
S("nanite_skin", 3, [], [], arc=0.5)
S("nanite_skin", 4, [], [], repair=1)
S("nanite_skin", 5, [],
  [orbit("AURA", "plate", n=4, r0=0.5, r1=0.5, spin=0.05, size=0.3, at="body", col="accent"),
   burst("CHARGE_END", "hex", n=14, speed=0.4, life=16, size=0.2)], swarm=0.5)

S("drone_swarm", 2, [extend(duration=0.1)], [])
S("drone_swarm", 3, [pierce(1, 0.8)], [])
S("drone_swarm", 4, [], [], retarget=1)
S("drone_swarm", 5, [], [], lance=1.0)

# ---- Spirit: soft, echoing, jade ------------------------------------------------------------------------------------
S("spirit_palm", 2, [],
  [burst("PALM", "petal", n=10, speed=0.18, life=22, size=0.2, col="mid")], push=0.15)
S("spirit_palm", 3, [pierce(1, 0.6)],
  [ghost("PALM", delay=3, scale=0.7)])
S("spirit_palm", 4, [],
  [grow("ECHO", 1.2)], slow=40)
S("spirit_palm", 5, [],
  [sigil("PALM", at="hand", size=0.9, spin=0.06, face="aim", life=16, col="accent")], giant=0.6, reach=6.0)

S("aura", 2, [],
  [every(26, orbit("AURA", "wisp", n=6, r0=0.9, r1=0.9, spin=0.08, size=0.14, at="feet", life=30))], reach=1)
S("aura", 3, [], [], calm=1.0)
S("aura", 4, [status("slow", 20)],
  [every(26, rings("AURA", n=1, r0=0.5, r1=3.0, width=0.2, life=12, at="feet"))])
S("aura", 5, [],
  [every(26, orbit("AURA", "wisp", n=4, r0=1.2, r1=1.2, spin=0.15, size=0.2, at="body", life=30, tilt=0.5, col="core"))], strike=0.5)

S("mend", 2, [],
  [rings("HEAL", n=1, r0=0.4, r1=1.8, width=0.25, life=20, at="feet")], more=0.1)
S("mend", 3, [guard(60, 0.2)],
  [dome("HEAL", radius=1.3, life=60, at="body", col="edge")])
S("mend", 4, [],
  [rings("HEAL", n=1, delay=4, r0=0.5, r1=5.0, width=0.3, life=14, col="edge")], share=1.0)
S("mend", 5, [],
  [sigil("HEAL", at="feet", size=2.6, spin=0.02, face="up", life=80), burst("HEAL", "petal", n=16, speed=0.08, life=40, size=0.2, grav=-0.01)],
  bloom=80)

S("third_eye", 2, [extend(duration=0.1)],
  [sigil("AURA", at="head", size=0.3, spin=0.0, face="aim", col="core")])
S("third_eye", 3, [], [], exposed=0.1)
S("third_eye", 4, [], [], secrets=1)
S("third_eye", 5, [],
  [column("AURA", height=10, width=0.25, life=20, col="edge")], range=64)

S("cord_lash", 2, [],
  [grow("TETHER", 1.2)], range=2)
S("cord_lash", 3, [], [], twin=1)
S("cord_lash", 4, [status("stun", 16)],
  [orbit("IMPACT", "mote", n=5, r0=0.4, r1=0.4, spin=0.2, size=0.12, at="target", life=30, col="core")])
S("cord_lash", 5, [], [], chain=1)

S("bulwark", 2, [extend(duration=0.15)],
  [orbit("DOME", "rune", n=8, r0=2.2, r1=2.2, spin=0.05, size=0.25, at="origin", tilt=0.3)])
S("bulwark", 3, [push(0.4)], [rings("DOME", n=2, gap=8, r0=2.0, r1=3.5, width=0.25, life=12, col="edge")])
S("bulwark", 4, [], [sigil("DOME", at="ground", size=3.0, spin=0.01, face="up", life=0)], rest=0.5)
S("bulwark", 5, [], [burst("DOME", "petal", n=14, speed=0.3, life=20, size=0.3)], open=1.0)

S("hundred_palms", 2, [], [grow("FLURRY", 1.1)], more=0.1)
S("hundred_palms", 3, [], [], seek=1)
S("hundred_palms", 4, [], [bloom("ECHO", size=1.0)], last=2.0)
S("hundred_palms", 5, [], [sigil("FLURRY", at="back", size=1.6, spin=0.04, face="aim", col="accent")], figure=0.3)

S("void_maw", 2, [], [orbit("VORTEX", "shard", n=10, r0=2.2, r1=2.0, spin=0.12, size=0.25, at="origin", col="edge")], pull=0.15)
S("void_maw", 3, [], [bloom("VORTEX", size=0.7, col="edge")], hunger=0.05)
S("void_maw", 4, [], [burst("NOVA", "shard", n=12, speed=0.4, life=14, size=0.22, grav=0.04)], debris=0.3)
S("void_maw", 5, [], [rings("NOVA", n=1, r0=3.0, r1=0.3, width=0.5, life=8, col="core")], bite=1.0)

S("waking_dream", 2, [], [burst("CONSTRUCT", "mote", n=10, speed=0.05, life=30, size=0.14)], fast=0.15)
S("waking_dream", 3, [], [], bigger=1)
S("waking_dream", 4, [], [], lasting=2.0)
S("waking_dream", 5, [], [sigil("CAST", at="hand", size=0.7, spin=0.05, face="aim")], lucid=1)

S("storm_call", 2, [], [burst("CAST", "smoke", n=8, speed=0.05, life=30, size=0.9, at="head", col="edge")], harder=0.1)
S("storm_call", 3, [chain(1, 0.5, 5.0)], [forks("STRIKE", n=2, reach=2.4, life=6)])
S("storm_call", 4, [], [grow("STRIKE", 1.15)], more=1)
S("storm_call", 5, [], [], follow=120)

S("ember_step", 2, [], [trail("DASH", width=0.18, life=30, at="feet", col="accent")], longer=0.2)
S("ember_step", 3, [], [], burn=40)
S("ember_step", 4, [], [], flare=0.5)
S("ember_step", 5, [], [burst("DASH", "feather", n=10, speed=0.25, life=20, size=0.3, col="accent")], phoenix=1.0)

S("frost_nova", 2, [extend(radius=0.1)], [burst("FROST", "shard", n=12, speed=0.2, life=24, size=0.25, grav=0.02)])
S("frost_nova", 3, [], [], shatter=0.5)
S("frost_nova", 4, [linger("frost", 60, 3.0)], [])
S("frost_nova", 5, [], [sigil("FROST", at="ground", size=4.0, spin=0.02, face="up", life=40)], bloom=0.5)

S("ghost_step", 2, [], [trail("BLINK", width=0.3, life=30, at="body", col="edge")], range=2)
S("ghost_step", 3, [status("slow", 40, 1)], [burst("IMPACT", "mote", n=6, speed=0.1, life=16, size=0.12)])
S("ghost_step", 4, [], [grow("BLINK", 1.15)], range=2)
S("ghost_step", 5, [], [sigil("BLINK", at="origin", size=1.6, spin=0.05, face="aim", life=60, col="accent")], door=60)

S("twin_strike", 2, [extend(duration=0.15)], [orbit("AURA", "wisp", n=4, r0=0.6, r1=0.6, spin=0.1, size=0.15, at="body")])
S("twin_strike", 3, [], [], share=0.7)
S("twin_strike", 4, [extend(duration=0.3)], [])
S("twin_strike", 5, [], [], casts=0.5)
