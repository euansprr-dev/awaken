"""The ladder's gestures (SPEC 36, docs/power-levels/PLAN.md 4.5): each power's cast at levels 3 and 5, written as edits on its
level-1 clips (power_gestures.py, power_casts.py's family clips) so they keep its character. Importing this module registers

    p_<power>_l3, p_<power>_l5                  the begin clip (an instant cast) or the begin, _hold and _end of a channel
    fp_p_<power>_l3, fp_p_<power>_l5            the first-person twins (instant casts)

and fills LEVELS[power] = {3: {begin3p, hold3p, end3p, begin1p, hold1p}, 5: {...}}, which power_gestures.write_table() puts
on each casts.json row as "levels" (powers.fx.CastProfiles.at picks the row for the event's level bits).

Level 3 is the family's accent (a step, a hang, a lower crouch); level 5 is the power's own new gesture, the body line of
power_ladder_table.py (what the K menu says it does). The release tick never moves: a level must not make a power slower to use
(the server's wind-up equals it). What changes is how the body arrives, hits and holds: a hang at the release, a flourish after
it, a second hand. The clipping check of power_casts.py runs on every clip.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import fp_casts as FC  # noqa: E402
import fp_gestures as G1  # noqa: E402
import power_cast_table as T  # noqa: E402
import power_gestures as PG  # noqa: E402
import power_ladder_table as LT  # noqa: E402

PC = PG.PC
L, EOC, EIOS, EOX, EIX, EOS = "linear", "easeoutcubic", "easeinoutsine", "easeoutexpo", "easeinexpo", "easeoutsine"
FAMILY = {p["id"]: p["fam"] for p in LT.L}
LEVELS = {}


class S:
    """An absolute value in an edit (the rest add)."""

    def __init__(self, v):
        self.v = tuple(float(x) for x in v) if isinstance(v, (tuple, list)) else v


def d(**kw):
    return kw


def edit(p, delta, k=1.0):
    """A pose with an edit applied; k < 1 blends it in part way (a settle key takes half). Flags only flip at k = 1."""
    q = dict(p)
    for key, v in (delta or {}).items():
        if key not in PC.DEFAULTS:
            raise KeyError(key)
        if isinstance(v, S):
            v = v.v
            if isinstance(v, tuple):
                q[key] = tuple(a + (b - a) * k for a, b in zip(q[key], v))
            elif isinstance(v, bool) or v is None:
                if k >= 1:
                    q[key] = v
            else:
                base = q[key] if q[key] is not None else 0.0
                q[key] = base + (v - base) * k
        elif isinstance(v, bool):
            if k >= 1:
                q[key] = v
        elif isinstance(v, tuple):
            q[key] = tuple(a + b * k for a, b in zip(q[key], v))
        else:
            q[key] = (q[key] if q[key] is not None else 0.0) + v * k
    return q


def sample(keys, t):
    """The pose at a key's tick (edits key off existing ticks only)."""
    for kt, p, _ in keys:
        if abs(kt - t) < 1e-6:
            return p
    raise KeyError(t)


def vary(keys, release, length, at=None, pre=None, hit=None, hang=0, flourish=()):
    """An instant cast's clip with a level's edits: returns (keys, length, end). `at` edits named keys, `pre` every key before the
    release, `hit` the release and everything after (the last key, the settle, takes half); `hang` holds the release pose that
    many ticks; `flourish` adds keys after it [(ticks after the hang, edit on the release pose, easing)], pushing the
    follow-through back."""
    out = []
    for t, p, e in keys:
        if at and t in at:
            p = edit(p, at[t])
        if pre and 0 < t < release:
            p = edit(p, pre)
        if hit and t >= release:
            p = edit(p, hit, 0.5 if t == keys[-1][0] else 1.0)
        out.append((t, p, e))
    if release not in [t for t, _, _ in out]:
        raise ValueError(f"release {release} is not a key")
    rel_pose = sample(out, release)
    push = hang + (max(dt for dt, _, _ in flourish) if flourish else 0)
    res = []
    for t, p, e in out:
        res.append((t + push if t > release else t, p, e))
        if t == release:
            if hang:
                res.append((release + hang, rel_pose, L))
            for dt, delta, ease in flourish:
                res.append((release + hang + dt, edit(rel_pose, delta), ease))
    res.sort(key=lambda k: k[0])
    if flourish and len(res) > 1:
        # the follow-through eases out of the flourish rather than snapping
        last_f = release + push
        res = [(t, p, EIOS if t > last_f and e in (EOX, EIX) else e) for t, p, e in res]
    return res, length + push, res[-1][0]


def held_keys(keys, delta, ramp):
    """A channel's clip with an edit: ramped in over a begin clip (it starts from standing), whole on a hold or its let-go."""
    if not delta:
        return keys
    last = max(t for t, _, _ in keys) or 1.0
    return [(t, edit(p, delta, t / last if ramp else 1.0), e) for t, p, e in keys]


# ---- level 3: the family's accent ---------------------------------------------------------------------------------------
# a step (Kinetic), a deeper lean (Velocity), a hang in the air (Flight), the push held (Energy), lower (Shadow), the head and a
# held beat (Mind), a firm set (Architect), the lock (Tech), a lift (Spirit). Channels lean into the hold instead.

L3 = {
    "Kinetic": dict(hit=d(shift=0.8, lean=4, rk=6), hang=1, held=d(twist=4, rk=6, lk=6)),
    "Velocity": dict(pre=d(lean=4), hit=d(lean=6, rk=6, lk=8), hang=1, held=d(lean=4)),
    "Flight": dict(hit=d(rise=0.8), hang=2, held=d(rise=0.5)),
    "Energy": dict(hit=d(lean=3, shift=0.5), hang=2, held=d(rk=6, lk=6, lean=-2)),
    "Shadow": dict(hit=d(lean=5, rk=12, lk=12), hang=1, held=d(rk=10, lk=10)),
    "Mind": dict(hit=d(head_z=4), hang=2, held=d(head_z=4)),
    "Architect": dict(hit=d(shift=0.5, lean=3), hang=2, held=d(lean=3)),
    "Tech": dict(hit=d(twist=-6), hang=1, held=d(twist=-4)),
    "Spirit": dict(hit=d(rise=0.6, lean=-3), hang=2, held=d(rise=0.4)),
}

# ---- level 5: the power's own gesture (its body line in power_ladder_table.py) -------------------------------------------

UP_R, UP_L = S((-170, -8, 0)), S((-170, 8, 0))
L5 = {
    # Kinetic
    "haymaker": dict(held=d(twist=6, lean=-3, rk=8, lk=8),  # a stepping lunge punch with the off arm thrown back
                     end=dict(hit=d(shift=2.2, lean=8, rl=(16, 0, 0), ll=(-14, 0, 0), la=S((56, 0, -30)), le=S(12)), hang=2)),
    "shockwave": dict(pre=d(rl=(-22, 0, 0), rk=14, rise=0.6, ra=S((-118, 0, 42)), la=S((-118, 0, -42)), re=S(70), le=S(70)),
                      hit=d(ra=S((-30, 0, 12)), la=S((-30, 0, -12)), re=S(15), le=S(15), lean=4), hang=2),
    "terrain_rip": dict(at={4: d(ra=UP_R, la=UP_L, lean=-6, re=S(30), le=S(30))}, hit=d(lean=8, shift=0.8), hang=2),
    "brace": dict(hit=d(rk=6, lk=6), hang=2,
                  flourish=[(2, d(ra=S((-50, 0, 72)), la=S((-50, 0, -72)), re=S(10), le=S(10), lean=4), EOX)]),
    "thunderclap": dict(at={4: d(ra=S((-170, -12, 0)), la=S((-170, 12, 0)), re=S(25), le=S(25), rise=1.2, lean=-14)}, hang=2),
    # Velocity
    "dash": dict(pre=d(lean=6), hit=d(lean=10, rk=8, lk=10, shift=0.6, la=S((80, 0, -14)), le=S(10)), hang=2),
    "afterimage": dict(hit=d(side=2.4, tilt=10, rl=(0, 0, 20)), hang=2),
    "flow": dict(at={4: d(head_x=S(14))}, hit=d(ra=S((-80, 0, 60)), la=S((-80, 0, -60)), head_x=S(-6)), hang=2),
    "lightning_step": dict(pre=d(ra=S((-120, -30, 0)), re=S(80)), hang=1),
    # Flight
    "gravity_well": dict(hit=d(la=S((-86, 0, 0)), la_aim=True, le=S(0)), hang=1, flourish=[(3, d(twist=-18, lean=4), EIOS)]),
    "telekinesis": dict(held=d(la=S((-96, 30, 0)), le=S(30)), end=dict(hit=d(twist=-20, lean=6), hang=1)),
    "gravity_flip": dict(flourish=[(2, d(ra=S((-140, -4, -30)), la=S((-140, 4, 30)), rise=1.0), EOC), (4, d(), EIX)]),
    # Energy
    "beam": dict(held=d(rl=(8, 0, 6), ll=(-6, 0, -6), rk=10, lk=10, la=S((-88, 40, 0)), le=S(60)), end=dict(hit=d(re=30))),
    "charged_shot": dict(held=d(ra=S((-20, 0, 85)), re=S(110), twist=18, la=S((-80, 20, 0)), le=S(20)),
                         end=dict(hit=d(ra=S((-100, -6, 0)), ra_aim=True, re=S(0), twist=-24, lean=10, shift=1.5), hang=1)),
    "nova": dict(hit=d(head_x=S(-20), ra=(-20, 0, 10), la=(-20, 0, -10), lean=-4), hang=2),
    "barrier": dict(hang=1, flourish=[(2, d(ra=(-34, 0, 24), la=(-34, 0, -24)), EIOS), (4, d(ra=(0, 0, 46), la=(0, 0, -46)), EIOS),
                                      (6, d(ra=(26, 0, 20), la=(26, 0, -20)), EIOS), (8, d(), EIOS)]),
    "overload": dict(at={3: d(rise=1.0, lean=-4),
                         5: d(ra=S((-70, -40, 0)), la=S((-70, 40, 0)), re=S(110), le=S(110), lean=14, head_x=S(16))},
                     hit=d(head_x=S(-18), rise=0.5), hang=2),
    "prism_lance": dict(held=d(head_x=S(-16), lean=-6, rise=0.6)),
    # Shadow
    "shadow_step": dict(hang=1, flourish=[(3, d(ra=S((-70, 60, 20)), lean=6, rk=15, lk=15), EOC)]),
    "cloak": dict(hang=1, flourish=[(3, d(ra=S((-12, 0, 8)), la=S((-12, 0, -8)), re=S(20), le=S(20), rk=10, lk=10), EIOS)]),
    "echo_swap": dict(hang=1, flourish=[(2, d(shift=-2.0, lean=-6), EOX)]),
    "legion": dict(hit=d(ra=S((-150, 0, -10)), re=S(20), la=S((10, 0, -14)), le=S(0)), hang=2, flourish=[(3, d(re=S(70)), EIX)]),
    # Mind
    "dominate": dict(hit=d(ra=S((-130, -10, 0)), la=S((-130, 10, 0)), ra_aim=False, re=S(30), le=S(30)),
                     flourish=[(2, d(ra=(8, 0, 0), la=(-8, 0, 0)), EIOS), (4, d(ra=(-8, 0, 0), la=(8, 0, 0)), EIOS), (6, d(), EIOS)]),
    "read_intent": dict(hang=2, flourish=[(4, d(ra=S((-92, -4, 0)), ra_aim=True, re=S(0), head_z=S(0)), EIOS)]),
    "decoy": dict(flourish=[(2, d(twist=20, ra=(0, -40, 0)), EOX), (4, d(twist=40, ra=(0, -80, 0)), EOX)]),
    "foresight": dict(pre=d(head_x=S(14)), hit=d(head_x=S(-16), ra=(-10, 0, 14), la=(-10, 0, -14)), hang=2),
    # Architect
    "wall": dict(hit=d(ra=S((-92, -8, 0)), la=S((-92, 8, 0)), re=S(0), le=S(0), shift=0.8, lean=4), hang=2),
    "ramp": dict(hit=d(ra=(-40, 0, 10), rise=0.8, lean=-4), hang=2),
    "floor": dict(hang=1, flourish=[(3, d(ra=(0, 30, 24), la=(0, -30, -24)), L)]),
    "cone": dict(flourish=[(2, d(ra=(30, 0, 14), la=(30, 0, -14)), L), (4, d(ra=(60, 0, 28), la=(60, 0, -28)), L)]),
    "rampart": dict(hit=d(ra=S((-170, 0, -14)), la=S((-170, 0, 14)), re=S(5), le=S(5)), hang=3),
    "spire": dict(flourish=[(3, d(ra=S((-90, 40, 30)), twist=20, rise=0.5), EIOS),
                            (6, d(ra=S((-170, 0, -10)), twist=-10, rise=1.2, re=S(10)), EIOS)]),
    "collapse": dict(flourish=[(2, d(ra=UP_R, la=UP_L, ra_aim=False, la_aim=False, lean=-28), EOC),
                               (5, d(ra=S((-30, -6, 0)), la=S((-30, 6, 0)), lean=-8, rk=20, lk=20), EIX)]),
    "citadel": dict(flourish=[(3, d(ra=S((-60, 0, 90)), la=S((-60, 0, -90)), lean=-20, rk=-40, lk=-40), EIOS),
                              (7, d(ra=S((-160, 0, -40)), la=S((-160, 0, 40)), lean=-34, rise=1.0, rk=-60, lk=-60), EIOS)]),
    # Tech
    "grapple": dict(hang=1, flourish=[(3, d(lean=10, rk=40, lk=40, ra=S((-60, -10, 0)), la=S((-60, 10, 0)), ra_aim=False, re=S(90),
                                            le=S(90), head_x=S(10)), EOC)]),
    "jet_burst": dict(pre=d(rk=30, lk=30, lean=8), hit=d(rise=1.0, ra=(-20, 0, 0), la=(-20, 0, 0)), hang=1),
    "drone": dict(hit=d(ra=S((-92, 0, 0)), ra_aim=True, re=S(0)), hang=2),
    "turret": dict(hit=d(lean=6, rk=10, la=S((-30, 20, 10)), head_x=S(10)), hang=3),
    "railgun": dict(held=d(rl=(0, 0, 6), ll=(0, 0, -6), rk=10, lk=10, twist=-6),
                    end=dict(hit=d(twist=18, lean=-10, shift=-1.5, ra=(-24, 0, 0)), hang=1)),
    "nanite_skin": dict(hit=d(rk=10, lk=10, lean=6, head_x=S(10)), hang=3),
    "drone_swarm": dict(hit=d(la=S((10, 0, -14)), le=S(0), ra=S((-172, 0, -6)), re=S(5), rise=0.5), hang=3),
    # Spirit
    "spirit_palm": dict(hit=d(shift=1.6, lean=8, rl=(14, 0, 0), ll=(-12, 0, 0), rk=8), hang=2),
    "mend": dict(hit=d(ra=S((-145, 0, -36)), la=S((-145, 0, 36)), re=S(20), le=S(20)), hang=2),
    "third_eye": dict(hit=d(head_x=S(-24), lean=-6), hang=3),
    "cord_lash": dict(hit=d(la=S((-88, 20, 0)), le=S(10)),
                      flourish=[(3, d(ra=S((-40, -10, 0)), la=S((-40, 10, 0)), re=S(90), le=S(90), lean=-10, shift=-1.0), EIX)]),
    "bulwark": dict(hit=d(ra=(-30, 0, 10), la=(-30, 0, -10), rise=0.6), hang=3),
    "hundred_palms": dict(hit=d(rl=(0, 0, 8), ll=(0, 0, -8), rk=14, lk=14, lean=4)),
    "void_maw": dict(hit=d(ra=(-6, 0, 0)), hang=2, flourish=[(3, d(re=S(45), lean=-6), EIX)]),
    "waking_dream": dict(flourish=[(2, d(ra=(-20, 30, 0), la=(-20, -30, 0)), EIOS), (4, d(ra=(10, 50, 0), la=(10, -50, 0)), EIOS)]),
    "storm_call": dict(at={3: d(la=S((-168, 0, 10)), le=S(5)), 5: d(la=S((-168, 0, 10)), le=S(5))},
                       hit=d(la=S((-40, -10, 0)), le=S(0)), hang=2),
    "ember_step": dict(flourish=[(2, d(twist=45, rk=10, lk=10), EOC), (4, d(twist=-30), EIOS)]),
    "frost_nova": dict(hang=1, flourish=[(3, d(ra=S((-120, 0, 60)), la=S((-120, 0, -60)), lean=-24, rk=-50, lk=-50, re=S(10), le=S(10)),
                                          EOC)]),
    "ghost_step": dict(hit=d(la=S((70, 0, -20)), le=S(5), shift=1.0), hang=2),
    "twin_strike": dict(flourish=[(2, d(side=2.0, tilt=6), EOX), (4, d(side=-2.0, tilt=-6), EOX), (6, d(), EIOS)]),
}


def _strike(keys):
    """A channel's let-go: the key it strikes on (its first expo-eased key, else its second)."""
    for t, _, e in keys:
        if t > 0 and e == EOX:
            return t
    return keys[1][0] if len(keys) > 1 else keys[0][0]


def _three_p(power, level, spec):
    prof = PG.PROFILES[power]
    tag = f"p_{power}_l{level}"
    begin = PC.CLIPS[prof["begin3p"]]
    note = f"{power} at level {level}"
    if prof["hold3p"] is None and not begin.loop:
        keys, length, end = vary(begin.keys, float(prof["release"]), begin.length, at=spec.get("at"), pre=spec.get("pre"),
                                 hit=spec.get("hit"), hang=spec.get("hang", 0), flourish=spec.get("flourish", ()))
        PC.clip(tag, keys, length, end=end, note=note)
        return dict(begin3p=tag, hold3p=None, end3p=prof["end3p"])
    # a channel: the gather leans into the level's hold, the hold and its let-go carry it, the strike takes the level's edits
    held = spec.get("held")
    if begin.loop:  # Haymaker: the begin is the hold itself
        PC.clip(tag, held_keys(begin.keys, held, False), begin.length, loop=True, note=note + " (held)")
    else:
        PC.clip(tag, held_keys(begin.keys, held, True), begin.length, end=begin.end, note=note)
    hold_name = None
    if prof["hold3p"]:
        hold = PC.CLIPS[prof["hold3p"]]
        hold_name = tag + "_hold"
        PC.clip(hold_name, held_keys(hold.keys, held, False), hold.length, loop=True, note=note + " (held)")
    end_name = None
    if prof["end3p"]:
        endc = PC.CLIPS[prof["end3p"]]
        end_name = tag + "_end"
        e = spec.get("end") or {}
        base = held_keys(endc.keys, held, False) if len(endc.keys) == 1 else endc.keys
        if len(base) == 1:
            PC.clip(end_name, [(0, edit(base[0][1], e.get("hit")), base[0][2])], endc.length, end=0, note=note + " (let go)")
        else:
            keys, length, end = vary(base, _strike(base), endc.length, hit=e.get("hit"), hang=e.get("hang", 0),
                                     flourish=e.get("flourish", ()))
            PC.clip(end_name, keys, length, end=end, note=note + " (let go)")
    return dict(begin3p=tag, hold3p=hold_name, end3p=end_name)


def _first_person(power, level, spec, row):
    """The first-person twin of an instant cast: the hands hold the release as long as the body does and reach further at
    level 5. A channel keeps its first-person clips (its hold hands over on a keyed event)."""
    base_name, hold1p = G1.NAMES[power]
    if row["channel"] or hold1p is not None or base_name not in FC.CLIPS:
        return dict(begin1p=base_name, hold1p=hold1p)
    c = FC.CLIPS[base_name]
    rel = float(PG.PROFILES[power]["release"])
    hang = spec.get("hang", 0) + (max(dt for dt, _, _ in spec["flourish"]) if spec.get("flourish") else 0)
    amp = 1.15 if level == 5 else 1.0
    fists = c["fists"]

    def track(keys, rest):
        if not keys:
            return keys
        out = []
        for i, (t, h, e) in enumerate(keys):
            last = i == len(keys) - 1
            if t >= rel and not last and amp != 1.0:
                h = tuple(r + (v - r) * amp for v, r in zip(h, rest))
            out.append((t + hang if t > rel else t, h, e))
            if t <= rel < (keys[i + 1][0] if not last else rel + 1) and hang:
                out.append((rel + hang, FC.F.sample(keys, rel), L))
        out.sort(key=lambda k: k[0])
        # a key exactly on the release was copied above; drop a duplicate tick
        seen, res = set(), []
        for k in out:
            if k[0] in seen:
                continue
            seen.add(k[0])
            res.append(k)
        return res

    main = track(c["main"], FC.REST_FIST if fists else FC.REST)
    off = track(c["off"], FC.OFF_OUT if fists else FC.REST)
    events = [(t + hang if t > rel else t, n) for t, n in c["events"]]
    name = f"fp_p_{power}_l{level}"
    FC.clip(name, c["length"] + hang, main=main, off=off, both=c["both"], loop=c["loop"], events=events, fists=fists,
            to_stance=c["to_stance"], stow=c.get("stow", fists), note=f"{power} at level {level}")
    return dict(begin1p=name, hold1p=None)


# The big level-5 forms get their own short Lens look on your screen (assets/awaken/lens/looks_powers.json, CastLens).
LENS5 = {"flow": "awaken:awake_flow", "gravity_well": "awaken:awake_gravity_well", "beam": "awaken:awake_beam", "nova": "awaken:awake_nova",
         "overload": "awaken:awake_overload", "legion": "awaken:awake_legion"}


def register_all():
    LEVELS.clear()
    for power, row in T.R.items():
        fam = FAMILY[power]
        by = {}
        for level, spec in ((3, L3[fam]), (5, L5[power])):
            fields = _three_p(power, level, spec)
            fields.update(_first_person(power, level, spec, row))
            if level == 5 and power in LENS5:
                fields["lens"] = LENS5[power]
            by[level] = fields
        LEVELS[power] = by


register_all()
