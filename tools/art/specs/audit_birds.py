"""The sound audit, part 1: birds and wings in the open world and in Carillon (SPEC §66.3).

Every one of these was synthesised from scratch (audio_wild.py and audio_city_life.py: whistles on pitch curves, voiced
formants, filtered noise grains), which is what Euan heard as "super fake" on 11 Nov 2026. Now each is a recipe of
Minecraft's own recordings: a parrot's wingbeats and chirps, a bat's take-off, a chick's peep, wood knocked. The crow,
the owl, the gull and the dove have no recording in the game: with AmbientSounds (the pack's sound profile) the code
plays its recordings of the real birds (wildlife.client.WildSound.real, citizens.client.CityNature.voice); the recipes
here are what plays without it.
"""
from audit_kit import A, E, F, group, pick, row

G = group("birds", "Birds and wings",
          "What you hear on a beach, in a wood and in the city's squares when a bird takes off, calls or sings.")

FLY = ("mob/parrot/fly", 1, 8)
WILD = "wildlife.client.Birds / WildSound (client only, at the bird, Ambient slider)"
WAS_WILD = "ours, synthesised (tools/art/specs/audio_wild.py)"
WAS_CITY = "ours, synthesised (tools/art/specs/audio_city_life.py)"


def notes(first, more, vol, dist=None, spread=0.04, chance=0.9, jitter=0):
    """A phrase of chirps: first = (files or None for any parrot chirp, pitch); more = [(ms, files or None, pitch)]."""
    def one(files, pitch, at, v, ch):
        if files is None:
            return E("entity.parrot.ambient", v, pitch, at=at, spread=spread if at else 0.0, chance=ch, jitter=jitter if at else 0)
        return F(files, v * 0.7, pitch, dist=dist, at=at, spread=spread if at else 0.0, chance=ch, jitter=jitter if at else 0)
    out = [one(first[0], first[1], 0, vol, 1.0)]
    for ms, files, pitch in more:
        out.append(one(files, pitch, ms, vol, chance))
    return out


def knocks(files, n, vol, pitch, dist, shape, step=50):
    """A woodpecker's drum: n strikes a tick apart, swelling and fading by `shape` (a list of levels)."""
    return [F(files, vol * shape[min(i, len(shape) - 1)], pitch, dist=dist, at=i * step, spread=0.02 if i else 0.0) for i in range(n)]


DRUM_SHAPE = [0.75, 0.9, 1.0, 1.0, 0.96, 0.9, 0.84, 0.76, 0.68, 0.6, 0.52, 0.44, 0.36]
I1, I2, I3, I4, I5, I6 = ("mob/parrot/idle%d" % n for n in range(1, 7))

ROWS = [
    # ---- wings ---------------------------------------------------------------------------------------------------------
    row("wild.flush", "wildlife", G, "A small bird bursts off as you come near",
        WILD + ": Birds.flush when you run at a songbird or come within 3 blocks, a monster nears it, or the world hushes there",
        WAS_WILD + ": filtered noise grains", "Synthetic: bursts of filtered noise, a hiss with a rhythm, not feathers",
        scene="flush", picks=[
            ("Two of a parrot's wingbeats, raised to a small bird's, over a quiet bat's take-off",
             [F(FLY, 1.0, 1.3, dist=20), E("entity.parrot.fly", 1.0, 1.5, at=50, spread=0.08, jitter=50),
              E("entity.bat.takeoff", 0.16, 1.45, spread=0.06)]),
            ("One parrot wingbeat, raised: the smallest take-off", [F(FLY, 1.0, 1.35, dist=20)]),
            ("A bat's take-off, raised, with a flutter after it",
             [E("entity.bat.takeoff", 0.28, 1.4), E("entity.parrot.fly", 1.0, 1.6, at=100, spread=0.08)])]),
    row("wild.wings.big", "wildlife", G, "Large wings: a crow, a duck or a heron takes off",
        WILD + ": Birds.flush for a crow or woodpecker, Birds.swim (a duck off the water), Birds.heron (a heron leaving)",
        WAS_WILD + ": filtered noise grains", "Synthetic: slow noise swells with a 3.5 kHz edge, no feather in it",
        scene="bigwings", picks=[
            ("Three slow parrot wingbeats, lowered, with a soft leathery flap under the first",
             [F(FLY, 1.0, 0.74, dist=28), E("item.armor.equip_elytra", 0.45, 0.8),
              E("entity.parrot.fly", 1.0, 0.66, at=150, spread=0.06, jitter=50),
              E("entity.parrot.fly", 0.8, 0.7, at=350, spread=0.06, chance=0.8, jitter=50)]),
            ("The same, with a phantom's wingbeat raised under them: heavier",
             [F(FLY, 1.0, 0.74, dist=28), E("entity.phantom.flap", 0.5, 1.6),
              E("entity.parrot.fly", 1.0, 0.66, at=150, spread=0.06, jitter=50)]),
            ("A bat's take-off, lowered: a clatter of wings",
             [E("entity.bat.takeoff", 0.3, 0.7), E("entity.parrot.fly", 1.0, 0.7, at=100, spread=0.06)])]),
    row("city.pigeon.flutter", "citizens", G, "A flock of pigeons takes off from a square",
        "citizens.client.CityCritters (client only): the flock's first bird when you walk into it, and the towers' flocks leaving at dawn",
        WAS_CITY + ": noise grains", "Synthetic, and the loudest of the bird sounds (peaks near full scale)",
        scene="flock", picks=[
            ("A flock: six or seven parrot wingbeats tumbling over each other, over a bat's take-off",
             [F(FLY, 1.0, 0.95, dist=24), E("entity.bat.takeoff", 0.26, 0.9),
              E("entity.parrot.fly", 1.0, 1.1, at=50, spread=0.08, jitter=50), E("entity.parrot.fly", 1.0, 0.9, at=150, spread=0.08, jitter=50),
              E("entity.parrot.fly", 0.9, 1.05, at=250, spread=0.08, jitter=50), E("entity.bat.takeoff", 0.16, 1.05, at=200, chance=0.7),
              E("entity.parrot.fly", 0.8, 0.85, at=400, spread=0.08, chance=0.8, jitter=50),
              E("entity.parrot.fly", 0.7, 1.0, at=600, spread=0.08, chance=0.7, jitter=100),
              E("entity.parrot.fly", 0.5, 0.9, at=850, spread=0.08, chance=0.5, jitter=100)]),
            ("A smaller flock: three wingbeats",
             [F(FLY, 1.0, 0.95, dist=24), E("entity.parrot.fly", 1.0, 1.1, at=100, spread=0.08, jitter=50),
              E("entity.parrot.fly", 0.8, 0.9, at=300, spread=0.08, jitter=50)]),
            ("The flock with a phantom's wingbeat raised under it: the air moved",
             [F(FLY, 1.0, 0.95, dist=24), E("entity.phantom.flap", 0.4, 1.8), E("entity.parrot.fly", 1.0, 1.1, at=50, spread=0.08, jitter=50),
              E("entity.parrot.fly", 1.0, 0.9, at=200, spread=0.08, jitter=50), E("entity.parrot.fly", 0.8, 1.0, at=450, spread=0.08, chance=0.8, jitter=100)])]),

    # ---- small birds' voices -------------------------------------------------------------------------------------------
    row("wild.chip", "wildlife", G, "A songbird's chip note",
        WILD + ": a songbird idling on its perch or the ground, and one time in three as it takes off",
        WAS_WILD + ": a sine glide", "Synthetic: a pure tone glide at 5 to 8 kHz, the 'ding' Euan named",
        picks=[
            ("A parrot's chirp, raised to a small bird's", [E("entity.parrot.ambient", 0.55, 1.7)]),
            ("A chick's peep", [E("entity.baby_chicken.ambient", 2.2, 1.3)]),
            ("Two quick chirps", [E("entity.parrot.ambient", 0.5, 1.85), E("entity.parrot.ambient", 0.4, 1.7, at=100, chance=0.6)])]),
    row("wild.song.robin", "wildlife", G, "A robin sings from its branch (a blackbird sings it lower)",
        WILD + ": WildSound.song, a perched songbird at its pause; three times as often at dawn, never at night",
        WAS_WILD + ": whistles on pitch curves", "Synthetic whistles: every note a pure tone, which is what read as fake",
        scene="song", picks=[
            ("A phrase of eight to ten parrot chirps, raised, wandering up and down with a breath in the middle",
             notes((None, 1.6), [(100, None, 1.85), (250, None, 1.5), (350, None, 1.95), (600, None, 1.7), (700, None, 1.45),
                                 (850, None, 1.9), (1050, None, 1.6), (1150, None, 1.8), (1400, None, 1.5)], 0.6, spread=0.07, chance=0.85, jitter=50)),
            ("Three chirps and no more", notes((None, 1.7), [(150, None, 1.9), (400, None, 1.6)], 0.6, spread=0.06, chance=1.0)),
            ("Chick peeps in place of the chirps: softer, thinner",
             [E("entity.baby_chicken.ambient", 2.2, 1.3), E("entity.baby_chicken.ambient", 2.2, 1.5, at=250, spread=0.06),
              E("entity.baby_chicken.ambient", 2.0, 1.2, at=600, spread=0.06, chance=0.8)])]),
    row("wild.song.tit", "wildlife", G, "A tit sings: two notes, see-saw",
        WILD + ": WildSound.song for a blue tit", WAS_WILD + ": whistles on pitch curves", "Synthetic whistles",
        scene="song", picks=[
            ("A high thin parrot note and a lower one, back and forth three or four times",
             notes(([I4], 1.8), [(200, [I2], 1.5), (450, [I4], 1.8), (650, [I2], 1.5), (900, [I4], 1.8), (1100, [I2], 1.5),
                                 (1350, [I4], 1.8), (1550, [I2], 1.5)], 0.6, dist=28, spread=0.02, chance=0.92)),
            ("Twice only", notes(([I4], 1.8), [(200, [I2], 1.5), (450, [I4], 1.8), (650, [I2], 1.5)], 0.6, dist=28, spread=0.02, chance=1.0)),
            ("Any parrot chirps, high then low", notes((None, 1.9), [(200, None, 1.5), (450, None, 1.9), (650, None, 1.5)], 0.6, spread=0.03, chance=1.0))]),
    row("wild.song.finch", "wildlife", G, "A chaffinch sings: a run downhill and a flourish",
        WILD + ": WildSound.song for a chaffinch", WAS_WILD + ": whistles on pitch curves", "Synthetic whistles",
        scene="song", picks=[
            ("Eight parrot chirps quickening down the scale, then one low and one high to end it",
             notes(([I2], 2.0), [(150, [I2], 1.95), (300, [I2], 1.88), (400, [I2], 1.8), (500, [I2], 1.72), (600, [I2], 1.64),
                                 (700, [I2], 1.56), (800, [I2], 1.5), (950, [I6], 1.35), (1100, [I4], 1.7)], 0.6, dist=32, spread=0.02, chance=0.95)),
            ("The run only, shorter", notes(([I2], 2.0), [(150, [I2], 1.9), (300, [I2], 1.8), (400, [I2], 1.7), (500, [I2], 1.6)], 0.6, dist=32, spread=0.02, chance=1.0)),
            ("Any parrot chirps, downhill", notes((None, 2.0), [(150, None, 1.85), (300, None, 1.7), (450, None, 1.55), (650, None, 1.8)], 0.6, spread=0.04, chance=1.0))]),
    row("wild.song.wren", "wildlife", G, "A wren sings: loud, fast, a trill",
        WILD + ": WildSound.song for a wren (a little louder than the rest)", WAS_WILD + ": whistles on pitch curves", "Synthetic whistles",
        scene="song", picks=[
            ("A fast run of short parrot chirps, a breath, a lower run, then three high ones",
             notes(([I1], 1.9), [(50, [I1], 2.0), (100, [I1], 1.9), (150, [I1], 2.0), (200, [I1], 1.85), (250, [I1], 2.0), (300, [I1], 1.9),
                                 (500, [I5], 1.7), (550, [I5], 1.75), (600, [I5], 1.7), (650, [I5], 1.75), (700, [I5], 1.7), (750, [I5], 1.8),
                                 (900, [I4], 1.9), (950, [I4], 1.8), (1000, [I4], 1.9)], 0.6, dist=36, spread=0.03, chance=0.95)),
            ("One short trill", notes(([I1], 1.9), [(50, [I1], 2.0), (100, [I1], 1.9), (150, [I1], 2.0), (200, [I1], 1.85), (250, [I1], 2.0)], 0.6, dist=36, spread=0.03, chance=1.0)),
            ("Any parrot chirps, fast", notes((None, 1.9), [(50, None, 2.0), (100, None, 1.85), (200, None, 2.0), (250, None, 1.9), (350, None, 2.0)], 0.6, spread=0.04, chance=1.0))]),
    row("city.songbird", "citizens", G, "A garden bird in the city's trees",
        "citizens.client.CitySound: from a tree near you by day, more at dawn; only without AmbientSounds (with it, its real garden birds sing)",
        WAS_CITY + ": whistles on pitch curves", "Synthetic whistles",
        scene="song", picks=[
            ("Three or four parrot chirps, raised", notes((None, 1.7), [(150, None, 1.9), (350, None, 1.6), (500, None, 1.8)], 0.6, spread=0.06, chance=0.8)),
            ("One chirp", [E("entity.parrot.ambient", 0.6, 1.75)]),
            ("A chick's peep, twice", [E("entity.baby_chicken.ambient", 2.2, 1.3), E("entity.baby_chicken.ambient", 2.0, 1.45, at=250, chance=0.8)])]),

    # ---- the woodpecker ------------------------------------------------------------------------------------------------
    row("wild.woodpecker.drum", "wildlife", G, "A woodpecker drums on a trunk",
        WILD + ": Birds.cling, most often in the morning; heard 48 blocks off",
        WAS_WILD + ": a struck modal body (sine modes)", "Synthetic: tuned sine modes ringing, a wooden toy more than a tree",
        picks=[
            ("Thirteen of Minecraft's wooden clicks a tick apart, swelling and falling away: a real drum roll's speed",
             knocks(["random/wood_click"], 13, 0.5, 0.95, 48, DRUM_SHAPE)),
            ("The same on Minecraft's wood footfalls: duller, deeper", knocks(("step/wood", 1, 6), 11, 0.8, 1.5, 48, DRUM_SHAPE)),
            ("Nine clicks: a shorter roll", knocks(["random/wood_click"], 9, 0.5, 0.95, 48, DRUM_SHAPE[2:]))]),
    row("wild.woodpecker.tap", "wildlife", G, "A woodpecker pecks at the bark",
        WILD + ": Birds.cling, half the times it pecks", WAS_WILD + ": a struck modal body (sine modes)", "Synthetic: sine modes",
        picks=[
            ("Two to four wooden clicks, unevenly",
             [F(["random/wood_click"], 0.3, 1.15, dist=20), F(["random/wood_click"], 0.3, 1.15, dist=20, at=200, spread=0.03, chance=0.85),
              F(["random/wood_click"], 0.26, 1.15, dist=20, at=350, spread=0.03, chance=0.6), F(["random/wood_click"], 0.3, 1.15, dist=20, at=650, spread=0.03, chance=0.4)]),
            ("Minecraft's wood knocked, twice", [E("block.wood.hit", 0.5, 1.5), E("block.wood.hit", 0.45, 1.5, at=250, spread=0.03, chance=0.8)]),
            ("One click", [F(["random/wood_click"], 0.3, 1.15, dist=20)])]),

    # ---- the birds with no recording in the game -----------------------------------------------------------------------
    row("wild.crow.caw", "wildlife", G, "A crow caws",
        WILD + ": a crow on the ground now and then, and half the times it is flushed. With AmbientSounds: its real crows (WildSound.real), "
               "one recording at a time, as loud as the crow is near",
        WAS_WILD + ": a voiced source through formants", "Synthetic voice: a buzz through filters. Minecraft has no crow",
        scene="caw", picks=[
            pick("With AmbientSounds: its recording of real crows. Without: a parrot's squawk dropped low, twice",
                 [F(("mob/parrot/death", 1, 4), 0.7, 0.6, dist=48), F(("mob/parrot/death", 1, 4), 0.6, 0.58, dist=48, at=500, spread=0.04, chance=0.7)]),
            ("Without AmbientSounds: a hen's squawk dropped low, twice",
             [F(["mob/chicken/hurt1", "mob/chicken/hurt2"], 0.5, 0.7, dist=48), F(["mob/chicken/hurt1", "mob/chicken/hurt2"], 0.45, 0.68, dist=48, at=550, spread=0.04, chance=0.7)]),
            pick("What plays with AmbientSounds: real crows", [A("animals.crow", 0.5)], code=True)]),
    row("city.gull.call", "citizens", G, "A gull cries (over a beach, and over the city's river)",
        "wildlife.client.Birds.soar (a gull circling over the shore, every 25 s each) and citizens.client.CityCritters (a city gull "
        "flushed, calling from a roof, in flight). With AmbientSounds: its real gulls (WildSound.real, CityNature.voice), well apart",
        WAS_CITY + ": a voiced source through formants", "Synthetic voice, heard every few seconds on a beach with six gulls up. Minecraft has no gull",
        scene="beach", picks=[
            pick("With AmbientSounds: its recording of real gulls. Without: three or four of a parrot's harsh cries, lowered",
                 [F(["mob/parrot/hurt1", "mob/parrot/hurt2"], 0.5, 0.62, dist=48), F(["mob/parrot/hurt1", "mob/parrot/hurt2"], 0.5, 0.6, dist=48, at=300, spread=0.05),
                  F(["mob/parrot/hurt1", "mob/parrot/hurt2"], 0.45, 0.58, dist=48, at=600, spread=0.05, chance=0.8),
                  F(["mob/parrot/hurt1", "mob/parrot/hurt2"], 0.4, 0.56, dist=48, at=900, spread=0.05, chance=0.5)]),
            ("Without AmbientSounds: a parrot's whistles, lowered, three in a row",
             [F([I3, I6, I5], 0.6, 0.8, dist=48), F([I3, I6, I5], 0.6, 0.78, dist=48, at=250, spread=0.05), F([I3, I6, I5], 0.5, 0.76, dist=48, at=500, spread=0.05, chance=0.8)]),
            pick("What plays with AmbientSounds: real gulls", [A("animals.seagull", 0.45)], code=True)]),
    row("city.pigeon.coo", "citizens", G, "A pigeon coos on a roof",
        "citizens.client.CityCritters: a perched pigeon calling, within 28 blocks. With AmbientSounds: its mourning dove (CityNature.voice)",
        WAS_CITY + ": a voiced source through formants", "Synthetic voice. Minecraft has no pigeon",
        picks=[
            pick("With AmbientSounds: its mourning dove. Without: a hen's low cluck, dropped lower still",
                 [F(("mob/chicken/picky/ambient", 1, 8), 1.0, 0.62, dist=24)]),
            ("Without AmbientSounds: a frog's deep croak", [F(["mob/frog/idle5", "mob/frog/idle6", "mob/frog/idle7"], 1.0, 0.9, dist=24)]),
            pick("What plays with AmbientSounds: a real dove", [A("animals.mouring_dove", 0.18)], code=True)]),
    row("wild.owl.hoot", "wildlife", G, "An owl hoots at night",
        WILD + ": an owl on its branch at night, turning its head to follow you. Now WildSound.real: with AmbientSounds one of its "
               "recordings of a real owl, as loud as the owl is near; without it the owl keeps silent, as owls mostly do",
        WAS_WILD + ": a sine with a breath of noise", "Synthetic: a pure low tone. Minecraft has no owl",
        scene="twice", picks=[
            pick("With AmbientSounds: its recordings of a real owl. Without: silence", [A("animals.owl", 0.9)], code=True),
            pick("Without AmbientSounds, if silence is too little: a frog's deep croak lowered, a soft hoot of a kind",
                 [F(["mob/frog/idle5", "mob/frog/idle6", "mob/frog/idle7"], 1.0, 0.8, dist=48)], code=True)]),
    row("wild.raptor.call", "wildlife", G, "A buzzard or a kestrel calls from high up",
        WILD + ": Birds.soar, each bird about every 45 s; heard 96 blocks off",
        WAS_WILD + ": whistles on pitch curves", "Synthetic whistle. Minecraft has no bird of prey, and neither has AmbientSounds",
        scene="far", picks=[
            ("A fox's scream, raised and far off: the nearest thing in the game to a hawk's cry",
             [F(("mob/fox/screech", 1, 4), 0.32, 1.45, dist=96)]),
            ("A parrot's whistle, lowered and far off", [F([I4, I6], 0.5, 0.75, dist=96)]),
            ("Higher and shorter: a kestrel's", [F(("mob/fox/screech", 1, 4), 0.26, 1.8, dist=96)])]),
    row("wild.duck.quack", "wildlife", G, "A duck quacks",
        WILD + ": Birds.swim, a duck on still water now and then, and as it leaves the water",
        WAS_WILD + ": a voiced source through formants", "Synthetic voice. Minecraft has no duck: a hen is its nearest fowl",
        picks=[
            ("A hen's cluck, lowered, once or twice", [E("entity.chicken.ambient", 0.8, 0.78), E("entity.chicken.ambient", 0.7, 0.76, at=300, spread=0.03, chance=0.6)]),
            ("The fussy hen's low cluck", [E("entity.chicken_picky.ambient", 1.6, 0.9)]),
            ("A frog's croak, raised", [E("entity.frog.ambient", 0.8, 1.25)])]),
    row("wild.heron.croak", "wildlife", G, "A heron croaks as it leaves",
        WILD + ": Birds.heron, when you come within 12 blocks of one", WAS_WILD + ": a voiced source through formants", "Synthetic voice. Minecraft has no heron",
        picks=[
            ("A hen's squawk dropped very low: a big bird's bark", [F(["mob/chicken/hurt1", "mob/chicken/hurt2"], 0.55, 0.55, dist=48)]),
            ("A parrot's squawk dropped very low", [F(("mob/parrot/death", 1, 4), 0.7, 0.5, dist=48)]),
            ("A frog's deep croak, lowered", [F(["mob/frog/idle5", "mob/frog/idle6", "mob/frog/idle7"], 1.0, 0.7, dist=48)])]),

    # ---- the wild's other small voices ---------------------------------------------------------------------------------
    row("wild.squirrel.chatter", "wildlife", G, "A squirrel scolds you from a trunk",
        "wildlife.client.Critters: a squirrel alarmed, or chattering on a branch", WAS_WILD + ": noise bursts and a whistle",
        "Synthetic. Minecraft has no squirrel: a bat's squeak lowered is its nearest small scold",
        picks=[
            ("Three or four of a bat's squeaks, lowered, in a quick row",
             [E("entity.bat.ambient", 0.1, 0.62), E("entity.bat.ambient", 0.1, 0.64, at=100, spread=0.04), E("entity.bat.ambient", 0.09, 0.6, at=200, spread=0.04),
              E("entity.bat.ambient", 0.08, 0.62, at=350, spread=0.04, chance=0.7)]),
            ("Three parrot chirps, a little raised", [E("entity.parrot.ambient", 0.4, 1.25), E("entity.parrot.ambient", 0.4, 1.3, at=100, spread=0.04), E("entity.parrot.ambient", 0.35, 1.2, at=200, spread=0.04)]),
            ("A parrot nibbling, raised: teeth on a nut", [E("entity.parrot.eat", 0.8, 1.3)])]),
    row("wild.frog.plop", "wildlife", G, "A frog plops into the water",
        "wildlife.client.Critters: a frog jumping into a pond as you come near", WAS_WILD + ": a falling sine (a cavity resonance)", "Synthetic: a sine drop",
        picks=[
            ("The fishing bobber's splash, raised: a small plop", [E("entity.fishing_bobber.splash", 0.22, 1.4)]),
            ("A drip into a cauldron, with a fish's flick", [E("block.pointed_dripstone.drip_water_into_cauldron", 1.6, 0.8), E("entity.fish.swim", 0.12, 1.6)]),
            ("A bottle filling, raised: a gulp of water", [E("item.bottle.fill", 0.4, 1.3)])]),
    row("wild.cricket", "wildlife", G, "Crickets in the grass at night",
        "wildlife.client.WildSound.crickets: three spots in the grass round you at night, each silent while you walk within 6 blocks. "
        "Only without AmbientSounds (with it, its real crickets sing and ours stay off)",
        WAS_WILD + ": a pulsed sine at 4.5 kHz", "Synthetic: a pure pulsed tone, exactly the steady high tone the ding check looks for",
        scene="night", picks=[
            ("Minecraft's own night insects: the firefly bush's sound", [E("block.firefly_bush.idle", 1.0, 1.0)]),
            ("The same, lower and nearer", [E("block.firefly_bush.idle", 1.0, 0.8)]),
            pick("Nothing: the night is quiet without AmbientSounds", [], code=True)]),
    row("wild.rustle", "wildlife", G, "Something small moves through the leaves",
        "wildlife.client.Critters: a squirrel, rabbit or fox starting, landing or slipping into cover", WAS_WILD + ": noise ticks",
        "Synthetic: scattered noise ticks",
        picks=[
            ("Leaf litter underfoot, with a brush of grass after", [E("block.leaf_litter.step", 1.2, 1.0), E("block.grass.step", 2.0, 0.9, at=100, chance=0.7)]),
            ("Azalea leaves brushed", [E("block.azalea_leaves.step", 1.2, 1.0)]),
            ("A berry bush shaken", [E("block.sweet_berry_bush.pick_berries", 0.5, 1.1)])]),
]
