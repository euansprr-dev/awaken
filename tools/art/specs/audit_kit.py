"""The sound audit's table kit (SPEC §66; tools/art/sound_audit.py reads the tables built with it).

A row is one sound event of ours and up to three picks for it. A pick is a recipe: recordings that already exist in the
game, each with a volume, a pitch and how many milliseconds in it starts. The first recording of a recipe is the event
itself in sounds.json; the rest are layered by the client (core.audio.client.SoundLayers), because sounds.json plays
one recording an event. Nothing of Mojang's is copied: a recipe only names recordings.

    E(event, ...)      one of Minecraft's sound events by name ("entity.parrot.fly"): it follows the game's variants
    F(files, ...)      Minecraft's own files by name ("mob/parrot/fly1"), when a sound needs its own hearing distance
                       or only some of an event's takes
    A(event, ...)      one of AmbientSounds' recordings by its event id ("animals.crow"): only ever played from code
                       that has checked the mod is there, so a pick that uses one is marked code=True
"""

GROUPS = []  # page and audit order: (key, title, lead)


def group(key, title, lead=""):
    GROUPS.append((key, title, lead))
    return key


def E(event, vol=1.0, pitch=1.0, at=0, spread=0.0, chance=1.0, jitter=0):
    return {"e": "minecraft:" + event, "vol": vol, "pitch": pitch, "at": at, "spread": spread, "chance": chance, "jitter": jitter}


def F(files, vol=1.0, pitch=1.0, dist=None, at=0, spread=0.0, chance=1.0, jitter=0):
    """files: "mob/parrot/fly1" or a list of them, or ("mob/parrot/fly", 1, 8) for fly1..fly8."""
    if isinstance(files, tuple):
        stem, lo, hi = files
        files = [f"{stem}{n}" for n in range(lo, hi + 1)]
    elif isinstance(files, str):
        files = [files]
    return {"f": list(files), "vol": vol, "pitch": pitch, "dist": dist, "at": at, "spread": spread, "chance": chance, "jitter": jitter}


def A(event, vol=1.0, pitch=1.0, at=0):
    return {"e": "ambientsounds:" + event, "vol": vol, "pitch": pitch, "at": at, "spread": 0.0, "chance": 1.0, "jitter": 0}


def pick(say, layers, code=False):
    """say: plain words for the page. code=True: the game plays this from code (the table writes nothing for it)."""
    return {"say": say, "layers": layers, "code": code}


def row(id, module, group, name, where, was, why, picks, scene="alone", note="", keep=False):
    """id: the event; module: its merge folder; where: when and from where it plays; was: its source before the audit;
    why: the verdict in a sentence; picks: up to three, the first is what the game plays; keep=True: the sound stays as
    it was built and the picks are only offered on the page."""
    return dict(id=id, module=module, group=group, name=name, where=where, was=was, why=why,
                picks=[p if isinstance(p, dict) else pick(*p) for p in picks], scene=scene, note=note, keep=keep)
