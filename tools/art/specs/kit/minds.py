"""A mind for every boss (SPEC §48.6): the Hive Regent's and the Gearwright's answers and forks, made from each body's own moves.

A boss whose script already has a `mind` keeps it: that's how the two pilots are written, and how a boss gets a hand-made one
later. Nothing here adds a move, damage or speed; it only says which of the moves a boss already has answer what you start.
"""


def _windup(m):
    return (m["time"]["windup"], m["id"])


def _has(m, kind):
    return kind in m.get("kinds", [])


def _big(m):
    """Its ending move, or something that fills the room: those keep their place in the fight, and answer nothing."""
    return (m.get("signature") and "hits" not in m) or m["range"][1] >= 40


def _strings(entry):
    yield entry["move"]
    for string in entry.get("then", []):
        yield from string


def _roles(fight, rows):
    """The moves its pools and strings use, sorted by what they're good for, each list quickest first."""
    by = {r["id"]: r for r in rows}
    used = {m for ph in fight["phases"] for e in ph["pool"] for m in _strings(e)}
    blows = sorted((by[m] for m in used if by[m]["class"] != "NONE" and not _big(by[m]) and not _has(by[m], "behind")), key=_windup)
    gaps = [m for m in blows if _has(m, "gap")]
    far = gaps + [m for m in blows if _has(m, "ranged") and not _has(m, "gap")]
    near = [m for m in blows if not _has(m, "gap") and not _has(m, "ranged") and m["range"][0] <= 2.5]
    return {
        "far": far,
        "gaps": gaps,
        "light": [m for m in near if m["class"] == "LIGHT"],
        "heavy": [m for m in near if m["class"] == "HEAVY"],
        "beats_near": [m for m in near if m["class"] in ("PERILOUS", "GRAB")],
        "beats": [m for m in blows if m["class"] in ("PERILOUS", "GRAB")],
    }


def _ids(*lists, limit=99):
    out = []
    for moves in lists:
        for m in moves:
            if m["id"] not in out:
                out.append(m["id"])
    return out[:limit]


def _everywhere(fight, moves):
    """The first of these that every phase has: it can follow an answer whenever the answer comes."""
    for m in moves:
        if all(any(m["id"] in _strings(e) for e in ph["pool"]) for ph in fight["phases"]):
            return m["id"]
    return None


def _answers(fight, r):
    far, light = _ids(r["far"], limit=3), _ids(r["light"], limit=2)
    hard = _ids(r["beats_near"], r["heavy"], limit=1)  # the blow that punishes: guard-beater first, else the heavy that breaks it
    follow = _everywhere(fight, r["light"])
    out = []

    def add(on, moves, **rest):
        if moves:
            out.append({"on": on, "moves": moves, **rest})

    add("DRINK", _ids(r["far"][:3], r["beats_near"][:1], r["heavy"][:1], r["light"][:2]), chance=1.0, cooldown=240, press=True)
    cast = {"chance": 0.7, "cooldown": 200}
    if follow and r["gaps"] and r["far"][0] is r["gaps"][0]:
        cast["then"] = [[follow]]  # it came to you: one quick blow follows
    add("CAST", far, **cast)
    add("HEAVY", light, chance=0.7, cooldown=160)
    add("GUARD", _ids(r["beats"], r["heavy"], limit=3), after=30, cooldown=180)
    add("RECOVERY", hard + [m for m in light[:1] if m not in hard], chance=0.5, cooldown=200)
    add("PAUSED", far, chance=0.9, cooldown=200)
    return out


def _cooldowns(rows, r):
    out = {m["id"]: 60 for m in r["gaps"]}
    for m in rows:  # a shot or storm with no blade of its own takes the room's attention: it comes back slowly
        if m["class"] == "PERILOUS" and "hits" not in m and not _big(m):
            out[m["id"]] = 240
    return out


def _forks(entry, r):
    """What the blow after the first turns into, by what you're doing at the link (SPEC §48.4). Otherwise it goes on as written."""
    first = entry["move"]
    fork = {}
    guard = next((m["id"] for m in r["beats_near"] + r["heavy"] if m["id"] != first), None)
    dodged = next((m["id"] for m in r["far"] if m["id"] != first), None)
    if guard:
        fork["guard"] = guard
    if dodged:
        fork["dodged"] = dodged
    return [fork] if fork else []


def fit(fight, rows):
    """Gives `fight` a mind made from `rows` (its move table), unless it was written with one."""
    if "mind" in fight:
        return
    r = _roles(fight, rows)
    fight["schemaVersion"] = 2
    fight["mind"] = {"cooldowns": _cooldowns(rows, r), "answers": _answers(fight, r)}
    for ph in fight["phases"]:
        for e in ph["pool"]:
            if e.get("then"):
                forks = _forks(e, r)
                if forks:
                    e["forks"] = forks
