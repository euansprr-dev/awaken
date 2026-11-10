"""The Bell Crypt's monsters (SPEC §60). Room: dark blue stone, warm candles. So they are bronze, bone and old cloth,
and each carries one cold blue flame: where you look, and often where you hit."""
import math

from . import kit as K
from .kit import Rig, Ramp, hexc, p_bands, p_stone, p_strokes, s, c, bump

BRONZE = Ramp("#2a1c0c", "#46300f", "#6a4a18", "#8f6a24", "#b88c38", "#dcb458", "#f4dc90")
VERDI = Ramp("#1c3a34", "#2a5a4e", "#3e7a68", "#5aa088")
BONE = Ramp("#4a4234", "#6a604c", "#8c8068", "#b0a488", "#d0c6aa", "#ece4cc")
CLOTH = Ramp("#4a3e30", "#6a5a46", "#8a7860", "#a8967a", "#c4b294", "#dccdb0")
VEIL = Ramp("#2e2a38", "#423d50", "#5a5468", "#746d84", "#8e88a0", "#aaa4bc")
SLAB = Ramp("#4e5260", "#6c7080", "#8c90a0", "#aeb2c0", "#ccd0da", "#e6e8ee")
STONE = Ramp("#22242c", "#30333e", "#424654", "#585d6e", "#72788a", "#9096a8")
IRON = Ramp("#141418", "#1e1e24", "#2c2c34", "#3e3e48")
COAT = Ramp("#2c2620", "#40372c", "#574b3c", "#70614e", "#8a7962", "#a4937a")
COLD, HOT = "#9ad8ff", "#e8f8ff"
SIDES2 = ((1, "l"), (-1, "r"))


# ---------------------------------------------------------------------------------------------------------------------
# The Toller (toll_ghoul): a ringer whose head is its bell. It walks on its knuckles; the bell swings up before it tolls.

def toller():
    r = Rig("toll_ghoul", (128, 64))
    r.shadow = 0.6
    body = K.at(r.bone("body", (0, 13, 1)), 13)
    body.cube("hips", -3, -2, -2, 6, 4, 4)
    body.bone("skirt", (0, 2, 2)).cube("skirt", -3, 0, 0, 6, 6, 0)
    for side, nm in SIDES2:
        K.bent_leg(body, "1_" + nm, (2.5, 2, 0), 5, 5, 25, thick=(3, 2), side=side, share=None if side > 0 else ("thigh1_l", "shinb1_l"))
    chest = body.bone("chest", (0, -2, 0), (38, 0, 0))
    chest.cube("ribs", -4, -7, -2.5, 8, 7, 5)
    chest.cube("yoke", -6, -9, -3, 12, 3, 6)
    # the arms it walks on (the motion layer plants them as its front legs): shoulders high, elbows back, fists down
    shoulder_y = 11 - 8 * math.cos(38 * K.DEG)
    for side, nm in SIDES2:
        K.bent_leg(chest, "0_" + nm, (6, -8, 0), 11, 11, -10, thick=(3, 2), side=side, world_y=shoulder_y, tilt=38, splay=5, foot=(4, 4, 5, -3),
                   share=None if side > 0 else ("thigh0_l", "shinb0_l", "footb0_l"))
    neck = chest.bone("neck", (0, -9, -1), (-8, 0, 0))
    neck.cube("neck", -1.5, -2, -1.5, 3, 2, 3)
    # the bell is its head: its own axis runs crown (+y) to mouth (-y), turned so the mouth looks at you and a little
    # down, and set so its lower wall sits on the neck
    head = neck.bone("head", (0, -2, 0), (90, 0, 0))
    head.cube("bknob", -1, 4, 4, 2, 2, 2)
    head.cube("bcrown", -2, 2, 3, 4, 2, 4)
    head.cube("bshoulder", -4, -1, 1, 8, 3, 8)
    head.cube("bwaist", -5, -5, 0, 10, 4, 10)
    head.cube("blip", -6, -7, -1, 12, 2, 12)
    head.bone("clapper", (0, -5, 5)).cube("clapper", -1, -5, -1, 2, 4, 2)
    head.bone("chain", (0, 6, 5), (-120, 0, 0)).cube("chain", -0.5, 0, -0.5, 1, 15, 1)
    r.look = [("head", 0.45, 0.25), ("neck", 0.25, 0.15)]
    r.pack()

    limbs = ("0_l", "0_r", "1_l", "1_r")  # the feet a cue leaves on the ground stay on their spots
    K.moves(r, toll="Bell Toll")
    wind = {"chest": (-26, 0, 0), "neck": (-10, 0, 0), "head": (-72, 0, 0), "clapper": (30, 0, 0), "chain": (108, 0, 0),
            "leg0_l": (-34, 0, -16), "leg0_r": (-34, 0, 16), "shin0_l": (-22, 0, 0), "shin0_r": (-22, 0, 0)}
    hit = {"chest": (15, 0, 0), "neck": (4, 0, 0), "head": (18, 0, 0), "clapper": (-36, 0, 0), "chain": (-30, 0, 0), "body": {"pos": (0, -1.5, 0)}}
    ringing = {"chest": (6, 0, 0), "head": (-4, 0, 20), "clapper": (-10, 0, -30), "chain": (-2, 0, -20)}
    K.act(r, "toll", wind, hit, w=1.1, length=0.6, t_hit=0.1, open_pose=ringing, lag={"clapper": 0.05, "chain": 0.08}, plant=limbs)
    # still ringing: the bell goes on swinging side to side, less each time, the clapper a beat behind it
    swing = [(0.0, 20), (0.34, -17), (0.7, 13), (1.08, -9), (1.5, 6), (1.95, -3), (2.6, 0)]
    K.keyed(r, "toll_open", 2.6, [(t, K.planted(r, K.plus(K.scaled(ringing, 1.0 if t < 2.0 else 0.6),
                                                           {"head": (0, 0, z - 20), "clapper": (0, 0, -1.4 * z + 30), "chain": (0, 0, -z + 20)}), limbs))
                                  for t, z in swing], lag={"clapper": 0.07, "chain": 0.1})
    # its plain blow: a backhand with one long arm; every third, both fists from overhead
    K.plain(r, {"chest": (-4, 20, 0), "leg0_r": (24, 0, 30), "shin0_r": (-34, 0, 0), "head": (0, -14, 0)},
            {"chest": (8, -24, 0), "leg0_r": (-58, 0, -6), "shin0_r": (14, 0, 0), "head": (6, 10, 0)},
            heavy=({"chest": (-24, 0, 0), "head": (-30, 0, 0), "leg0_l": (-128, 0, -8), "leg0_r": (-128, 0, 8), "shin0_l": (-30, 0, 0), "shin0_r": (-30, 0, 0)},
                   {"chest": (20, 0, 0), "head": (14, 0, 0), "body": {"pos": (0, -1.5, 0)}}),
            lag={"clapper": 0.05, "chain": 0.08}, plant=limbs)

    K.loop(r, "idle", 6.0, lambda p: {
        "head": (1.5 * s(p), 2.0 * s(p, 0.25) + 10 * bump(p, 0.62, 0.07), 2.5 * s(p, 0.1) + 5 * bump(p, 0.66, 0.06)),
        "chest": (0.9 * s(p * 2), 0, 0.6 * s(p)),
        "body": {"pos": (0, 0.3 * s(p * 2), 0)},
        "chain": (0, 0, 2.0 * s(p, 0.4))}, n=16)
    K.loop(r, "walk", 1.0, lambda p: {
        "head": (5 * s(p * 2, 0.12), 0, 3.0 * s(p, 0.1)), "chest": (2.0 * s(p * 2), 0, 2.2 * s(p)), "chain": (4 * s(p * 2, 0.3), 0, 4 * s(p, 0.3))})
    K.loop(r, "run", 0.6, lambda p: {
        "head": (10 + 7 * s(p * 2, 0.12), 0, 4.0 * s(p, 0.1)), "chest": (8 + 3.0 * s(p * 2), 0, 3.0 * s(p)), "chain": (10 + 6 * s(p * 2, 0.3), 0, 6 * s(p, 0.3))})
    K.fallback_walk(r, [("leg0_l", 0.0, 1), ("leg0_r", 0.5, -1), ("leg1_l", 0.5, 1), ("leg1_r", 0.0, -1)])
    # angry and still it rocks from fist to fist: one comes off the floor, then the other
    K.loop(r, "angry", 2.0, lambda p: K.planted(r, {
        "chest": (5 + 1.4 * s(p * 2), 0, 2.5 * s(p)), "head": (10 + 2 * s(p * 2, 0.2), 0, 3 * s(p)), "body": {"pos": (0, -0.8, 0)}}, limbs,
        lift={"0_l": 2.2 * max(0.0, s(p)), "0_r": 2.2 * max(0.0, -s(p))}))
    K.flinch(r, {"chest": (-13, 0, 0), "neck": (-6, 0, 0), "head": (-10, 0, 20), "clapper": (0, 0, -26)}, plant=limbs)
    lying = {"chest": (44, 0, 6), "head": (30, 0, 44), "leg0_l": (-80, 0, -52), "leg0_r": (-72, 0, 58), "shin0_l": (30, 0, 0), "shin0_r": (36, 0, 0),
             "leg1_l": (40, 0, 0), "leg1_r": (48, 0, 0), "shin1_l": (60, 0, 0), "shin1_r": (64, 0, 0), "body": {"rot": (20, 0, 0)}}
    fl = lambda q, sink=0.0: K.on_floor(r, q, sink=sink)  # noqa: E731
    K.keyed(r, "death", 1.3, [
        (0.0, {}),
        (0.14, K.planted(r, {"chest": (-22, 0, 0), "head": (-34, 0, -8), "leg0_l": (-30, 0, -24), "leg0_r": (-30, 0, 24)}, limbs)),
        (0.5, fl(K.blend({}, lying, 0.8))),
        (0.64, fl(K.plus(lying, {"head": (0, 0, -10)}), 0.6)),
        (0.86, fl(K.plus(lying, {"head": (0, 0, 6), "clapper": {"rot": (0, 0, -30), "scale": (0.5, 0.5, 0.5)}}))),
        (1.3, fl(K.plus(lying, {"clapper": {"rot": (0, 0, 10), "scale": (0.02, 0.02, 0.02)}})))],
        lag={"chain": 0.1})
    return r


def paint_toller(r, P):
    for k, nm in enumerate(("bknob", "bcrown", "bshoulder", "bwaist", "blip")):
        P.paint(nm, BRONZE, 2.2 + k * 0.4, p_bands(4, 0.5), rim=0.9, ao=0.9)
    for nm in ("bshoulder", "bwaist"):  # verdigris runs from the crown end
        for fname in K.SIDES:
            f = P.face(nm, fname)
            for i in range(f.w):
                if K._h(nm, fname, i) % 3 == 0:
                    for j in range(f.h - 1 - K._h(fname, i, 7) % 2, f.h):
                        f.set(i, j, VERDI.at(1 + (K._h(i, j, nm) % 3)))
    f = P.face("blip", "top")  # the mouth: a dark throat in a bronze rim, the cold light deep in it
    for i, j in f.each():
        rim = i in (0, f.w - 1) or j in (0, f.h - 1)
        f.set(i, j, BRONZE.at(4) if rim else hexc("#07060a") if 1 < i < f.w - 2 and 1 < j < f.h - 2 else hexc("#1c1408"))
    K.lit(P, "blip", "top", 4, 4, 4, 4, COLD, HOT)
    P.paint("clapper", Ramp("#5a8cb0", "#9ad8ff", "#e8f8ff"), 1.4)
    K.lit_cube(P, "clapper", COLD, HOT, a=230, hot=lambda fn, i, j, f: fn == "top")
    P.paint("ribs", CLOTH, 2.0, p_bands(2, 0.9), rim=0.4)
    P.paint("yoke", CLOTH, 3.0, p_bands(3, 0.7), rim=0.6)
    P.paint("hips", CLOTH, 1.4, p_bands(2, 0.7))
    P.paint("neck", BONE, 2.4)
    P.paint("chain", IRON, 2.6, p_bands(2, 0.9))
    K.tatter(P, "skirt", CLOTH, 1.8, seed=1)
    P.paint("thigh0_l", CLOTH, 1.8, p_bands(3, 1.0), rim=0.3)
    P.paint("shinb0_l", CLOTH, 2.3, p_bands(3, 1.0), rim=0.3)
    P.paint("footb0_l", BONE, 2.8, p_bands(2, 0.6, vertical=True))
    P.paint("thigh1_l", CLOTH, 1.3, p_bands(3, 0.8))
    P.paint("shinb1_l", BONE, 1.8)
    return P.skin, P.glow


def toller_motion(r):
    rm = K.room(r, K.legs_of(r), crouch=0.8)
    return {"gait": K.gait("ripple-alternate", rm, duty=(0.72, 0.48), lift=(2.2, 3.6), crouch=0.8, rear=1.0, swing=0.32, bob=0.5, roll=0.045, lag=-0.25,
                           lead=0.12, regrip=[5.0, 12.0]),
            "body": K.body(headLevel=0.5, twist=0.2, slope=0.7, land=1.2, shift=0.5)}


# ---------------------------------------------------------------------------------------------------------------------
# The Mourner (crypt_archer): a tall veiled column with no arms. Its ribs lie shut across its front; it leans back, they
# open like wings, and three of them fly. Open, it cannot turn, and you can count what it has left.

RIB_Y = (-11.5, -9.5, -7.5, -5.5, -3.5)
GONE = ((0, "l"), (1, "r"), (2, "l"))


def _ribs(open_y, fan, gone=(), droop=0.0, only=None):
    d = {}
    for side, nm in SIDES2:
        for k in range(5):
            if only is not None and k not in only:
                continue
            d[f"rib{k}_{nm}"] = {"rot": (0, side * -open_y, side * -((2 - k) * fan - droop))}
            if (k, nm) in gone:
                d[f"rib{k}_{nm}"]["scale"] = (0.02, 0.02, 0.02)
    return d


def mourner():
    r = Rig("crypt_archer", (128, 64))
    r.shadow = 0.5
    base = r.bone("base", (0, 24, 0))
    base.cube("hem", -5, -3, -4, 10, 3, 8)
    base.cube("gown", -4, -14, -3, 8, 11, 6)
    spine = base.bone("spine", (0, -14, 0))
    spine.cube("torso", -4, -13, -3, 8, 13, 6)
    spine.bone("heart", (0, -8, -3.5)).cube("heart", -1, -2, -0.5, 2, 4, 1)
    for side, nm in SIDES2:
        for k, y in enumerate(RIB_Y):
            first = k == 0 and side > 0
            rib = spine.bone(f"rib{k}_{nm}", (side * 4, y, 3))
            rib.cube(f"rib{k}_{nm}", 0 if side > 0 else -1, -0.5, -8, 1, 1, 8, mirror=side < 0, share=None if first else "rib0_l")
            rib.bone(f"hook{k}_{nm}", (side * 0.5, 0, -8)).cube(f"hook{k}_{nm}", -4 if side > 0 else 0, -0.5, -1, 4, 1, 1, mirror=side < 0,
                                                             share=None if first else "hook0_l")
    head = spine.bone("head", (0, -13, 0), (6, 0, 0))
    head.cube("cowl", -4, -7, -4, 8, 7, 8)
    head.cube("peak", -3, -9, -2, 6, 2, 7)
    head.bone("cape", (0, -7, 4), (7, 0, 0)).cube("cape", -3, 0, 0, 6, 26, 0)
    r.look = [("head", 0.7, 0.4)]
    r.pack()

    K.moves(r, volley="Mourning Volley")
    rib_lag = {f"rib{k}_{nm}": 0.025 * k for k in range(5) for nm in "lr"}
    wind = dict(_ribs(104, 19), spine=(-13, 0, 0), head=(-14, 0, 0), cape=(16, 0, 0))
    hit = dict(_ribs(46, 10, gone=GONE), spine=(15, 0, 0), head=(8, 0, 0), cape=(-12, 0, 0))
    spent = dict(_ribs(92, 5, gone=GONE, droop=16), spine=(5, 0, 0), head=(12, 0, 0))
    K.act(r, "volley", wind, hit, w=1.1, length=0.5, open_pose=spent, lag=dict(rib_lag, cape=0.1))
    # its plain blow: the two lowest pairs stab forward
    K.plain(r, dict(_ribs(64, 0, only=(3, 4)), spine=(-7, 0, 0)), dict(_ribs(-14, 0, only=(3, 4)), spine=(11, 0, 0), head=(6, 0, 0)), lag={"cape": 0.1})

    def idle(p):
        d = _ribs(3 + 3 * s(p * 2), 0)  # the cage breathes
        d.update(spine=(0.8 * s(p), 0, 1.2 * s(p, 0.2)), head=(1.2 * s(p, 0.3), 3 * s(p, 0.6), 0))
        return d
    K.loop(r, "idle", 7.0, idle, n=14)

    def glide(p, lean, flare):
        d = _ribs(flare + 2 * s(p * 2), 2)
        d.update(spine=(lean + 0.8 * s(p * 2), 0, 1.0 * s(p)), head=(-lean * 0.6, 0, 0), base={"pos": (0, 0.4 * s(p * 2, 0.25), 0)},
                 cape=(lean * 1.6 + 2 * s(p * 2, 0.3), 0, 0))
        return d
    K.loop(r, "walk", 1.6, lambda p: glide(p, 6, 8))
    K.loop(r, "run", 1.0, lambda p: glide(p, 12, 16))
    K.loop(r, "angry", 2.4, lambda p: dict(_ribs(16 + 5 * s(p * 2), 4), spine=(-3 + s(p * 2), 0, 0), head=(4, 0, 0)))
    K.flinch(r, dict(_ribs(34, 8), spine=(-11, 0, 0), head=(-15, 0, 6), cape=(14, 0, 0)))
    # it comes apart: the ribs burst open, the column drops into itself like a sheet let go
    K.keyed(r, "death", 1.2, [
        (0.0, {}),
        (0.16, dict(_ribs(118, 24), spine=(-16, 0, 0), head=(-22, 0, 0))),
        (0.5, dict(_ribs(124, 10, droop=30), spine={"rot": (22, 0, 6), "pos": (0, -6, 0)}, head=(30, 0, 10), base={"scale": (1.1, 0.62, 1.1)})),
        (0.8, dict(_ribs(128, 4, droop=58), spine={"rot": (40, 0, 10), "pos": (0, -10, 0)}, head=(46, 0, 16), base={"scale": (1.22, 0.3, 1.22)})),
        (1.2, dict(_ribs(128, 2, droop=66), spine={"rot": (44, 0, 10), "pos": (0, -10.5, 0)}, head=(50, 0, 18), base={"scale": (1.25, 0.26, 1.25)},
                   heart={"scale": (0.02, 0.02, 0.02)}))],
        lag=dict(rib_lag, cape=0.12))
    return r


def paint_mourner(r, P):
    P.paint("hem", VEIL, 1.6, p_strokes(2, 3, seed=2), rim=0.3)
    P.paint("gown", VEIL, 2.6, p_strokes(2, 6, seed=3), rim=0.3)
    P.paint("torso", VEIL, 1.0, p_strokes(2, 5, seed=5), rim=0.2)
    P.paint("cowl", VEIL, 3.4, rim=0.8)
    P.paint("peak", VEIL, 3.0, rim=0.7)
    K.void(P, "cowl", "north", rim=VEIL.at(4.4))  # the veil: no face, a flat dark with two cold points low under the brow
    for i in (2, 5):
        K.lit(P, "cowl", "north", i, 4, 1, 1, COLD, HOT, a=255)
    P.paint("rib0_l", BONE, 3.8, rim=0.6)
    P.paint("hook0_l", BONE, 4.6, rim=0.5)
    K.lit_cube(P, "heart", COLD, HOT, a=240, hot=lambda fn, i, j, f: (i + j) % 2 == 0)
    K.tatter(P, "cape", VEIL, 3.2, seed=1, fall=0.09)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Chime (bell_wisp): a hand-bell that flies, the wisp burning where its clapper was. It tips its mouth at you before
# it dives, and a dive that misses leaves it ringing on the floor.

def chime():
    r = Rig("bell_wisp", (64, 32))
    r.shadow = 0.3
    b = r.bone("body", (0, 17, 0))
    b.cube("loop", -1.5, -11, -0.5, 3, 1, 1)
    b.cube("handle", -0.5, -10, -0.5, 1, 3, 1)
    b.cube("ccrown", -1.5, -7, -1.5, 3, 2, 3)
    b.cube("cbody", -2.5, -5, -2.5, 5, 3, 5)
    b.cube("clip", -3.5, -2, -3.5, 7, 2, 7)
    fl = b.bone("flame", (0, 0, 0))
    fl.cube("flame", -1.5, -1, -1.5, 3, 4, 3)
    fl.bone("flametip", (0, 3, 0)).cube("flametip", -0.5, 0, -0.5, 1, 3, 1)
    for side, nm in SIDES2:
        b.bone("streamer_" + nm, (side * 1.5, -10.5, 0), (16, 0, side * -12)).cube("streamer_" + nm, 0, 0, 0, 0, 9, 1, share=None if side > 0 else "streamer_l")
    r.pack()

    K.moves(r, dive="Grave Chime")
    wind = {"body": {"rot": (-68, 0, 0), "pos": (0, 2, 2)}, "flame": {"scale": (1.25, 1.7, 1.25)}, "streamer_l": (30, 0, 0), "streamer_r": (30, 0, 0)}
    hit = {"body": {"rot": (-100, 0, 0), "pos": (0, -3, -5)}, "flame": {"scale": (0.8, 2.4, 0.8)}, "streamer_l": (52, 0, 10), "streamer_r": (52, 0, -10)}
    floor = K.on_floor(r, {"body": (-96, 0, 74), "flame": {"scale": (0.7, 0.5, 0.7)}, "streamer_l": (10, 0, 30), "streamer_r": (80, 0, 0)},
                       skip=("streamer_l", "streamer_r"))
    K.act(r, "dive", wind, hit, w=0.8, length=0.4, t_hit=0.08, stuck=floor)
    # ringing on the floor: it rocks on its lip, less each time
    rock = [(0.0, 74), (0.22, 90), (0.46, 66), (0.72, 84), (1.0, 72), (1.4, 78), (2.6, 76)]
    K.keyed(r, "dive_open", 2.6, [(t, K.on_floor(r, K.plus({k: v for k, v in floor.items() if k != "body"}, {"body": (-96, 0, z)}),
                                                 skip=("streamer_l", "streamer_r"))) for t, z in rock])
    K.plain(r, {"body": {"rot": (24, 0, 0), "pos": (0, 1.5, 1.5)}, "flame": {"scale": (1.2, 1.5, 1.2)}},
            {"body": {"rot": (-58, 0, 0), "pos": (0, -1, -4)}, "flame": {"scale": (0.9, 1.8, 0.9)}})

    def hover(p):
        return {"body": {"rot": (2.5 * s(p, 0.1), 0, 4 * s(p)), "pos": (0, 0.9 * s(p * 2), 0)},
                "flame": {"scale": (1 + 0.08 * s(p * 5), 1 + 0.16 * s(p * 7, 0.3), 1 + 0.08 * s(p * 5))}, "flametip": (0, 0, 9 * s(p * 6))}
    K.loop(r, "idle", 4.0, hover, n=16)
    K.loop(r, "fly", 1.0, lambda p: {"body": (3 * s(p * 2), 0, 2.5 * s(p))})
    K.loop(r, "angry", 1.2, lambda p: {"body": (-14 + 3 * s(p * 2), 0, 6 * s(p)), "flame": {"scale": (1.15, 1.35 + 0.15 * s(p * 4), 1.15)}})
    K.flinch(r, {"body": {"rot": (20, 0, 46), "pos": (0, 0, 2)}, "flame": {"scale": (0.6, 0.6, 0.6)}})
    K.keyed(r, "death", 1.0, [
        (0.0, {}),
        (0.12, {"body": (30, 0, 40), "flame": {"scale": (1.3, 1.6, 1.3)}}),
        (0.34, {"body": {"rot": (-40, 0, 120), "pos": (0, -3, 0)}, "flame": {"scale": (0.3, 0.3, 0.3)}}),
        (0.62, {"body": {"rot": (-80, 0, 196), "pos": (0, -6, 0)}, "flame": {"scale": (0.02, 0.02, 0.02)}}),
        (1.0, {"body": {"rot": (-92, 0, 250), "pos": (0, -6.5, 0)}, "flame": {"scale": (0.02, 0.02, 0.02)}})])
    return r


def paint_chime(r, P):
    P.paint("loop", BRONZE, 3.2)
    P.paint("handle", BRONZE, 2.6)
    P.paint("ccrown", BRONZE, 3.4, rim=0.9)
    P.paint("cbody", BRONZE, 3.0, p_bands(3, 0.5), rim=0.9)
    P.paint("clip", BRONZE, 3.8, rim=1.0, ao=1.0)
    f = P.face("clip", "bottom")
    for i, j in f.each():
        f.set(i, j, BRONZE.at(3) if i in (0, f.w - 1) or j in (0, f.h - 1) else hexc("#0a0806"))
    K.lit_cube(P, "flame", COLD, HOT, a=240, hot=lambda fn, i, j, f: fn in K.SIDES and j < 2 and 0 < i < f.w - 1)
    K.lit_cube(P, "flametip", COLD, HOT, a=180, hot=lambda fn, i, j, f: j < 1)
    for fname in ("west", "east"):
        f = P.face("streamer_l", fname)
        for i, j in f.each():
            f.set(i, j, CLOTH.at(4.6 - j * 0.25))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Pallbearer (crypt_keeper, the elite): a stone coffin that walks on two long arms, elbows high behind it. Blows on
# its lid do nothing. It heaves itself up and drops on you, then lies with its open back to the ceiling.

SEGS = (("c0", 6, -6, 6), ("c1", 8, -12, 6), ("c2", 10, -18, 6), ("c3", 12, -23, 5), ("c4", 14, -27, 4), ("c5", 10, -31, 4), ("c6", 8, -34, 3))


def pallbearer():
    r = Rig("crypt_keeper", (128, 128))
    r.shadow = 0.9
    body = K.at(r.bone("body", (0, 24, 0)), 24)
    for nm, w, y, h in SEGS:
        body.cube(nm, -w / 2, y, -3, w, h, 6)
    slab = body.bone("slab", (7, -17, -3))
    for nm, w, y, h in SEGS:
        slab.cube("s" + nm, -w / 2 - 7, y + 17, -1, w, h, 1)
    # its arms are what it walks on (to the motion layer, its two legs): shoulders at the coffin's widest, elbows back
    for side, nm in SIDES2:
        K.bent_leg(body, "0_" + nm, (7, -22, 0.5), 13, 13, -32, thick=(3, 2), side=side, splay=9, foot=(5, 3, 6, -3),
                   share=None if side > 0 else ("thigh0_l", "shinb0_l", "footb0_l"))
    r.pack()

    limbs = ("0_l", "0_r")
    ARMS = ("thigh0_l", "shinb0_l", "footb0_l", "thigh0_r", "shinb0_r", "footb0_r")
    # flat on its lid, arms thrown out to the sides, its open back to the ceiling
    def lying(extra=None):
        """Face down, its hands flat on the floor beside its shoulders as if to push itself up."""
        q = K.on_floor(r, K.plus({"body": {"rot": (84, 0, 0), "pos": (0, 0, -2)}, "leg0_l": (0, 0, -60), "leg0_r": (0, 0, 60)}, extra or {}), skip=ARMS)
        for side, nm in SIDES2:
            sh = K.point(r, "leg0_" + nm, pose=q)
            q = K.solve(r, q, [("leg0_" + nm, 0), ("leg0_" + nm, 2), ("shin0_" + nm, 0)], ("foot0_" + nm, (0, 3, 0)), (side * (abs(sh[0]) + 9), 24.0, sh[2] + 3))  # (its palm)
            q = K.level(r, q, "foot0_" + nm)
        return q
    flat = lying()
    K.moves(r, slam="Bell Slam", rush="Coffin Rush")
    K.act(r, "slam", {"body": {"rot": (-22, 0, 0), "pos": (0, 6, 1)}, "slab": (0, -22, 0)}, flat, w=1.5, length=0.5, t_hit=0.13, over=0.0, back=0.0,
          after=flat, open_pose=K.plus(flat, {"slab": (0, -8, 0)}), plant=limbs)
    K.act(r, "rush", {"body": {"rot": (-10, 0, 0), "pos": (0, 2, 1.5)}, "slab": (0, -8, 0)}, {"body": {"rot": (30, 0, 0), "pos": (0, 2.5, -3)}},
          w=0.9, length=0.7, t_hit=0.12, stuck=K.plus(flat, {"slab": (0, -8, 0)}), plant=limbs)
    # its plain blow: the lid swings wide and slams shut on you; every third, the whole coffin shoulders in behind it
    K.plain(r, {"body": {"rot": (-8, 0, 0)}, "slab": (0, -38, 0)}, {"body": {"rot": (12, 0, 0), "pos": (0, 0, -2)}, "slab": (0, 4, 0)},
            heavy=({"body": {"rot": (-14, 10, 0), "pos": (0, 2, 1)}, "slab": (0, -62, 0)}, {"body": {"rot": (20, -8, 0), "pos": (0, 1, -3.5)}, "slab": (0, 5, 0)}),
            plant=limbs)

    # something knocks from inside, twice
    K.loop(r, "idle", 8.0, lambda p: {"body": (1.0 * s(p), 0, 0.7 * s(p, 0.3)),
                                      "slab": (0, -5 * bump(p, 0.30, 0.012) - 3.5 * bump(p, 0.345, 0.012) - 0.6 - 0.6 * s(p * 2), 0)}, n=32)
    K.loop(r, "walk", 1.2, lambda p: {"body": {"rot": (5 + 3 * s(p * 2), 0, 3 * s(p)), "pos": (0, 1.6 + 0.8 * s(p * 2, 0.25), 0)}, "slab": (0, -2 - 2 * s(p * 2), 0)})
    K.loop(r, "run", 0.8, lambda p: {"body": {"rot": (12 + 4 * s(p * 2), 0, 4 * s(p)), "pos": (0, 2.4 + 1.0 * s(p * 2, 0.25), 0)}, "slab": (0, -4 - 3 * s(p * 2), 0)})
    K.fallback_walk(r, [("leg0_l", 0.0, 1), ("leg0_r", 0.5, -1)], swing=(16, 24))
    K.loop(r, "angry", 2.0, lambda p: {"body": (6 + s(p * 2), 0, 0), "slab": (0, -7 - 3 * s(p * 2), 0)}, plant=limbs)
    K.flinch(r, {"body": (-9, 0, 6), "slab": (0, -14, 0)}, plant=limbs)
    # the light pours out of its seam, it sways back on its arms, and comes down on its face for good
    back = K.planted(r, {"body": {"rot": (-26, 0, 6), "pos": (0, 3, 2)}, "slab": (0, -9, 0)}, limbs)
    K.keyed(r, "death", 1.5, [
        (0.0, {}),
        (0.2, K.planted(r, {"body": {"rot": (-18, 0, 4), "pos": (0, 2, 1)}, "slab": (0, -14, 0)}, limbs)),
        (0.5, back)]
        + K.fall(r, 0.5, back, 0.9, flat, steps=5, skip=ARMS)
        + [(1.02, K.plus(flat, {"body": {"rot": (-4, 0, 0), "pos": (0, 0.8, 0)}})), (1.5, flat)])
    return r


def paint_pallbearer(r, P):
    rng = P.rng
    for nm, w, y, h in SEGS:
        P.paint(nm, STONE, 1.8, p_stone(rng, 0.7, 4), rim=0.5, ao=0.6)
        f = P.face(nm, "south")  # the back stands open: dark inside a stone rim
        for i, j in f.each():
            f.set(i, j, STONE.at(3.0) if i in (0, f.w - 1) else hexc("#050508"))
        P.paint("s" + nm, SLAB, 3.0, p_stone(rng, 0.45, 5), rim=0.6, ao=0.3)
        f, g = P.face("s" + nm, "north"), P.face("s" + nm, "north", glow=True)  # the lid: iron bands, a seam leaking cold light
        band = nm in ("c1", "c3", "c5")
        for j in range(h):
            if band and j >= h - 2:
                for i in range(w):
                    f.set(i, j, IRON.at(2.6 if j == h - 2 else 1))
            else:
                f.set(w // 2 - 1, j, hexc("#0a1420"))
                f.set(w // 2, j, hexc(COLD))
                g.set(w // 2, j, hexc(COLD, 200))
    for i in (4, 9):  # the bearer inside: two cold points and a glow where a chest would be
        K.lit(P, "c4", "south", i, 1, 1, 1, COLD, HOT, a=255)
    K.lit(P, "c3", "south", 5, 1, 2, 3, COLD, HOT, a=200)
    P.paint("thigh0_l", CLOTH, 2.2, p_bands(3, 1.0), rim=0.4)
    P.paint("shinb0_l", CLOTH, 2.8, p_bands(3, 1.0), rim=0.4)
    P.paint("footb0_l", BONE, 3.4, p_bands(2, 0.7, vertical=True), rim=0.6)
    return P.skin, P.glow


def pallbearer_motion(r):
    rm = K.room(r, K.legs_of(r), crouch=0.4)
    return {"gait": K.gait("alternate", rm, duty=(0.7, 0.5), lift=(2.5, 4.0), crouch=0.4, swing=0.38, bob=0.9, roll=0.07, regrip=[6.0, 14.0], speed=0.6),
            "body": K.body(headLevel=0.0, twist=0.1, slope=0.4, land=1.6, shift=0.3)}


# ---------------------------------------------------------------------------------------------------------------------
# The Sexton (the_sexton, the Warden): a gravedigger bent double under the headstones it carries, eyeless under its
# hood, a lantern swinging in front of it on a pole. It hunts by sound; when it snuffs the lantern the room goes dark.

def sexton():
    r = Rig("the_sexton", (128, 128))
    r.shadow = 1.0
    body = K.at(r.bone("body", (0, 5, 3)), 5)
    body.cube("pelvis", -5, -4, -3, 10, 6, 6)
    for side, nm in SIDES2:
        K.bent_leg(body, "_" + nm, (3.5, 0, 0), 11, 12, 35, thick=(4, 3), side=side, foot=(4, 3, 5, -2.5),
                   share=None if side > 0 else ("thigh_l", "shinb_l", "footb_l"))
    chest = body.bone("chest", (0, -4, 0), (50, 0, 0))
    chest.cube("back", -7, -15, -4, 14, 15, 8)
    for k, (x, h, lean) in enumerate(((-4.5, 10, -9), (0, 14, 0), (4.5, 9, 10))):
        chest.bone(f"stone{k}", (x, -11 + k, 4), (-50, 0, lean)).cube(f"stone{k}", -2.5, -h, -1, 5, h, 2)
    pole = chest.bone("pole", (0, -14, 3), (-62, 0, 0))
    pole.cube("pole", -0.5, -22, -0.5, 1, 22, 1)
    boom = pole.bone("boom", (0, -22, 0), (12, 0, 0))
    boom.cube("boom", -0.5, -1, -15, 1, 1, 15)
    lamp = boom.bone("lamp", (0, 0, -14.5))
    lamp.cube("lchain", -0.5, 0, -0.5, 1, 5, 1)
    lamp.cube("lcap", -2.5, 4, -2.5, 5, 1, 5)
    lamp.cube("lfoot", -2.5, 9, -2.5, 5, 1, 5)
    for k, (x, z) in enumerate(((-2.5, -2.5), (1.5, -2.5), (-2.5, 1.5), (1.5, 1.5))):
        lamp.cube(f"lbar{k}", x, 5, z, 1, 4, 1, share=None if k == 0 else "lbar0")
    lamp.bone("wick", (0, 7, 0)).cube("wick", -1.5, -2, -1.5, 3, 4, 3)
    head = chest.bone("head", (0, -15, -1), (-34, 0, 0))
    head.cube("hood", -3.5, -6, -5, 7, 7, 8)
    arm = chest.bone("arm_r", (-8, -12, 0), (-62, 0, 8))
    arm.cube("upper_r", -2, 0, -2, 4, 12, 4)
    fore = arm.bone("fore_r", (0, 12, 0), (-24, 0, 0))
    fore.cube("forec_r", -1.5, 0, -1.5, 3, 11, 3)
    sp = fore.bone("spade", (0, 10, 0), (-147, 0, 0))
    sp.cube("shaft", -0.5, -6, -0.5, 1, 20, 1)
    sp.cube("blade", -3.5, 14, -0.5, 7, 9, 1)
    arm = chest.bone("arm_l", (8, -12, 0), (-44, 0, -8))
    arm.cube("upper_l", -2, 0, -2, 4, 12, 4)
    fore = arm.bone("fore_l", (0, 12, 0), (-30, 0, 0))
    fore.cube("forec_l", -1.5, 0, -1.5, 3, 11, 3)
    fore.bone("fist_l", (0, 11, 0)).cube("fist_l", -2.5, 0, -2.5, 5, 4, 5)
    r.look = [("head", 0.5, 0.3)]
    r.pack()

    legs = ("_l", "_r")

    def spade(pose, target, turn=147):
        """The pose with the spade in line with the forearm and its blade's edge brought to a point in model space."""
        return K.solve(r, K.plus(pose, {"spade": (turn, 0, 0)}), [("arm_r", 0), ("fore_r", 0), ("arm_r", 2)], ("spade", (0, 23, 0)), target)

    K.moves(r, stamp="Grave Stamp", snuff="Last Light", knell="Knell")
    lagc = {"lamp": 0.12}
    # Grave Stamp: the spade goes up over its shoulder and is driven into the floor; it has to wrench it out again
    dug = spade({"chest": (22, 0, 0), "head": (-8, 0, 0), "lamp": (-20, 0, 0), "arm_l": (-30, 0, -10), "fore_l": (-24, 0, 0), "body": {"pos": (0, -1.5, 0)}},
                (-7, 27, -14))
    K.act(r, "stamp", {"chest": (-22, 0, 0), "head": (14, 0, 0), "arm_r": (-96, 0, 0), "fore_r": (-30, 0, 0), "spade": (128, 0, 0), "lamp": (22, 0, 0),
                       "arm_l": (-10, 0, -10)},
          dug, w=1.1, length=0.6, t_hit=0.11, over=0.0, back=0.0, after=dug, lag=lagc, plant=legs,
          open_pose=spade({"chest": (26, 0, 0), "head": (-4, 0, 0), "arm_l": (-36, 0, -10), "fore_l": (-28, 0, 0), "body": {"pos": (0, -2, 0)}}, (-7, 27, -14)))
    # Last Light: it reaches up and pinches the lantern out
    K.act(r, "snuff", {"chest": (-10, 0, 0), "head": (-10, 0, 0), "arm_l": (-118, 0, 14), "fore_l": (-44, 0, 0), "lamp": (10, 0, 0)},
          {"chest": (-12, 0, 0), "head": (-12, 0, 0), "arm_l": (-126, 0, 16), "fore_l": (-24, 0, 0), "lamp": (4, 0, 0), "wick": {"scale": (0.02, 0.02, 0.02)}},
          w=1.3, length=0.5, t_hit=0.1, over=0.04, lag={"lamp": 0.1}, plant=legs,
          open_pose={"chest": (4, 0, 0), "head": (8, 12, 0), "arm_l": (-40, 0, 0), "fore_l": (-16, 0, 0), "wick": {"scale": (0.02, 0.02, 0.02)}})
    # Knell: it rears, rings the spade off the stones on its back, and levels it at you
    K.act(r, "knell", {"chest": (-30, 0, 0), "head": (-18, 0, 0), "arm_r": (-150, 0, -10), "fore_r": (-40, 0, 0), "spade": (60, 0, 0), "lamp": (30, 0, 0),
                       "arm_l": (-20, 0, -16), "stone1": (0, 0, 5)},
          spade({"chest": (10, 0, 0), "head": (-10, 0, 0), "lamp": (-18, 0, 0), "arm_l": (-24, 0, -12), "fore_l": (-20, 0, 0)}, (-6, 6, -30)),
          w=2.1, length=0.6, t_hit=0.12, lag=lagc, plant=legs,
          open_pose=spade({"chest": (18, 0, 0), "head": (10, 0, 0), "arm_l": (-34, 0, -8), "fore_l": (-28, 0, 0), "body": {"pos": (0, -1.5, 0)}}, (-8, 24.5, -12)))
    # its plain blow: a jab with the spade's edge; every third, a chop from the shoulder
    K.plain(r, {"chest": (-6, -14, 0), "arm_r": (10, 0, 14), "fore_r": (-46, 0, 0), "spade": (96, 0, 0)},
            spade({"chest": (10, 16, 0), "head": (-6, 0, 0)}, (-4, 12, -25)),
            heavy=({"chest": (-20, 0, 0), "head": (10, 0, 0), "arm_r": (-110, 0, 6), "fore_r": (-34, 0, 0), "spade": (120, 0, 0), "lamp": (20, 0, 0)},
                   spade({"chest": (20, 0, 0), "head": (-6, 0, 0), "lamp": (-18, 0, 0), "arm_l": (-28, 0, -10), "fore_l": (-22, 0, 0)}, (-6, 25.5, -15))),
            lag=lagc, plant=legs)

    K.loop(r, "idle", 8.0, lambda p: {
        "head": (2 * s(p, 0.2) - 9 * bump(p, 0.55, 0.05), 14 * bump(p, 0.55, 0.06) - 10 * bump(p, 0.8, 0.05), 0),
        "chest": (1.0 * s(p * 2), 0, 0.5 * s(p)), "arm_l": (2 * s(p, 0.3), 0, 0), "spade": (0, 0, 1.2 * s(p)),
        "stone0": (0, 0, 1.2 * s(p, 0.1)), "stone2": (0, 0, -1.2 * s(p, 0.45))}, n=16)
    K.loop(r, "walk", 1.3, lambda p: {
        "chest": (2.5 * s(p * 2), 0, 2.4 * s(p)), "head": (-2 * s(p * 2, 0.1), 0, -1.5 * s(p)), "arm_r": (8 * s(p, 0.5), 0, 0), "arm_l": (10 * s(p), 0, 0),
        "stone0": (0, 0, 2 * s(p * 2, 0.2)), "stone1": (2 * s(p * 2, 0.3), 0, 0), "stone2": (0, 0, -2 * s(p * 2, 0.4))})
    K.loop(r, "run", 0.8, lambda p: {
        "chest": (9 + 4 * s(p * 2), 0, 3.2 * s(p)), "head": (-8 - 3 * s(p * 2, 0.1), 0, 0), "arm_r": (-14 + 12 * s(p, 0.5), 0, 0), "arm_l": (16 * s(p), 0, 0),
        "stone0": (0, 0, 3 * s(p * 2, 0.2)), "stone1": (3 * s(p * 2, 0.3), 0, 0), "stone2": (0, 0, -3 * s(p * 2, 0.4))})
    K.fallback_walk(r, [("leg_l", 0.0, 1), ("leg_r", 0.5, -1)], swing=(22, 32))
    K.loop(r, "angry", 2.4, lambda p: {"chest": (6 + 1.5 * s(p * 2), 0, 0), "head": (-10, 0, 3 * s(p)), "arm_r": (-16, 0, 6), "fore_r": (-10, 0, 0),
                                       "body": {"pos": (0, -1, 0)}}, plant=legs)
    K.flinch(r, {"chest": (-11, 0, 4), "head": (-16, 0, 0), "arm_l": (-14, 0, -12), "lamp": (24, 0, 0)}, plant=legs)
    # it goes down on its knees, then on its face; the stones slide off its back and the lantern goes out
    knees = K.planted(r, {"body": {"pos": (0, -4, 0)}, "chest": (12, 0, 0), "head": (8, 0, 0), "arm_r": (30, 0, 18), "spade": (10, 0, 16), "lamp": (-20, 0, 0),
                          "arm_l": (-44, 0, -14), "fore_l": (-40, 0, 0)}, legs)
    down = {"body": {"rot": (30, 0, 0)}, "chest": (34, 0, 0), "head": (20, 0, 10), "leg_l": (-70, 0, 0), "leg_r": (-78, 0, 0), "shin_l": (96, 0, 0),
            "shin_r": (104, 0, 0), "arm_r": (60, 0, 26), "fore_r": (24, 0, 0), "spade": (30, 0, 30), "arm_l": (-60, 0, -30), "fore_l": (-30, 0, 0),
            "stone0": (0, 0, -26), "stone2": (0, 0, 28), "stone1": (18, 0, 0), "lamp": (-30, 0, 0)}
    skip = ("shaft", "blade", "pole", "boom", "lchain", "lcap", "lfoot", "lbar0", "wick")
    K.keyed(r, "death", 1.8, [
        (0.0, {}),
        (0.2, K.planted(r, {"chest": (-16, 0, 0), "head": (-20, 0, 0), "arm_r": (-20, 0, 16), "arm_l": (-24, 0, -16), "lamp": (30, 0, 0)}, legs)),
        (0.7, knees)]
        + K.fall(r, 0.7, knees, 1.2, down, steps=3, skip=skip)
        + [(1.34, K.on_floor(r, K.plus(K.scaled(down, 0.96), {"wick": {"scale": (0.3, 0.3, 0.3)}}), skip=skip)),
           (1.8, K.on_floor(r, K.plus(down, {"wick": {"scale": (0.02, 0.02, 0.02)}}), skip=skip))],
        lag={"lamp": 0.14, "stone0": 0.06, "stone2": 0.09, "stone1": 0.12})
    return r


def paint_sexton(r, P):
    rng = P.rng
    P.paint("back", COAT, 2.4, p_strokes(3, 5, seed=1), rim=0.5)
    P.paint("pelvis", COAT, 1.6, p_strokes(3, 4, seed=2))
    P.paint("hood", COAT, 3.2, rim=0.8)
    K.void(P, "hood", "north", rim=COAT.at(4.2))
    f = P.face("hood", "north")
    for i in range(1, f.w - 1, 2):
        f.set(i, 3, hexc("#3a3028"))  # stitched shut where eyes would be
    for nm in ("thigh_l", "upper_r", "upper_l"):
        P.paint(nm, COAT, 2.0, p_bands(4, 0.6))
    for nm in ("shinb_l", "forec_r", "forec_l"):
        P.paint(nm, CLOTH, 2.2, p_bands(3, 0.9))
    P.paint("footb_l", IRON, 2.2)
    P.paint("fist_l", BONE, 3.0, p_bands(2, 0.7, vertical=True))
    for k in range(3):
        P.paint(f"stone{k}", SLAB, 2.6, p_stone(rng, 0.6, 4), rim=0.8)
        K.rows(P, f"stone{k}", "south", (2, 4, 6), SLAB.at(0.6), 1, 4)
    wood = Ramp("#3a2c1c", "#57432a", "#75603e")
    for nm in ("pole", "boom", "shaft"):
        P.paint(nm, wood, 1.2)
    for nm in ("lchain", "lcap", "lfoot", "lbar0"):
        P.paint(nm, IRON, 2.6)
    P.paint("blade", IRON, 2.8, rim=1.2)
    K.lit_cube(P, "wick", "#ffb04a", "#fff0c0", a=240, hot=lambda fn, i, j, f: fn in K.SIDES and 0 < j < f.h - 1 and i == 1)
    return P.skin, P.glow


def sexton_motion(r):
    rm = K.room(r, K.legs_of(r), crouch=0.5)
    return {"gait": K.gait("alternate", rm, duty=(0.64, 0.45), lift=(2.5, 4.0), crouch=0.5, swing=0.34, bob=0.9, roll=0.06, regrip=[6.0, 14.0]),
            "body": K.body(headLevel=0.5, twist=0.2, slope=0.6, land=1.4, shift=0.6)}


# ---------------------------------------------------------------------------------------------------------------------

STILL = {"blink": [0, 0]}
MONSTERS = [
    K.Monster("toll_ghoul", "The Toller", toller, paint_toller, kind="strider", motion=toller_motion, box=(0.8, 1.7), arms=True,
              life=dict(STILL, temperament="monster", idles={"look": 3, "settle": 2}, mass=1.5, headSpeed=4.5, springHz=1.6, springGain=1.3),
              voice={"ambient": [("block.bell.resonate", 0.16, 0.55), ("block.chain.step", 0.5, 0.6), ("entity.skeleton.ambient", 0.45, 0.5)],
                     "hurt": [("block.bell.use", 0.45, 0.7), ("entity.skeleton.hurt", 0.7, 0.55)],
                     "death": [("block.bell.use", 0.8, 0.45), ("entity.skeleton.death", 0.8, 0.5)],
                     "step": [("block.chain.step", 0.3, 0.7), ("block.wool.step", 0.5, 0.7)]}),
    K.Monster("crypt_archer", "The Mourner", mourner, paint_mourner, kind="slider", box=(0.7, 2.25),
              motion={"body": K.body(headLevel=0.6, twist=0.3, land=1.2, shift=0.2)},
              life=dict(STILL, temperament="spirit", idles={"look": 4, "settle": 1}, bob=0.0, sway=0.6),
              voice={"ambient": [("entity.stray.ambient", 0.45, 0.5), ("block.wool.step", 0.4, 0.5)], "hurt": [("entity.skeleton.hurt", 0.7, 0.7)],
                     "death": [("entity.skeleton.death", 0.8, 0.6), ("block.bone_block.break", 0.8, 0.7)], "step": [("block.wool.step", 0.25, 0.6)]}),
    K.Monster("bell_wisp", "The Chime", chime, paint_chime, kind="floater", box=(0.5, 1.1), lift=10,
              motion={"fly": K.flight(tilt=0.25, bank=0.4, fast=5)},
              life=dict(STILL, temperament="spirit", springHz=2.2, idles={"settle": 1}),
              voice={"ambient": [("block.amethyst_block.chime", 0.3, 0.6), ("entity.vex.ambient", 0.35, 0.6)], "hurt": [("block.bell.use", 0.35, 1.1)],
                     "death": [("block.bell.use", 0.5, 0.9), ("block.candle.extinguish", 1.0, 0.8)]}),
    K.Monster("crypt_keeper", "The Pallbearer", pallbearer, paint_pallbearer, kind="strider", motion=pallbearer_motion, box=(1.0, 2.2),
              sunk=("thigh", "shinb", "footb"),  # in a cue the game keeps its hands planted; the clip alone lets them dip
              life=dict(STILL, temperament="beast", breathDepth=0.0, idles={"settle": 2, "look": 1}, mass=2.6, bob=0.8),
              voice={"ambient": [("block.deepslate_bricks.step", 0.6, 0.5), ("entity.wither_skeleton.ambient", 0.45, 0.5)],
                     "hurt": [("block.stone.hit", 0.9, 0.6), ("entity.wither_skeleton.hurt", 0.6, 0.5)],
                     "death": [("block.stone.break", 1.0, 0.5), ("entity.wither_skeleton.death", 0.8, 0.5)],
                     "step": [("block.deepslate_bricks.step", 0.5, 0.6)]}),
    K.Monster("the_sexton", "The Sexton", sexton, paint_sexton, kind="strider", motion=sexton_motion, box=(1.1, 2.7), sunk=("shaft", "blade"),
              life=dict(STILL, temperament="beast", idles={"look": 3, "settle": 2, "sniff": 1}, mass=2.6),
              voice={"ambient": [("entity.warden.sniff", 0.6, 0.8), ("block.lantern.step", 0.6, 0.6), ("entity.warden.tendril_clicks", 0.6, 0.7)],
                     "hurt": [("entity.warden.hurt", 0.8, 1.1), ("block.stone.hit", 0.8, 0.6)], "death": [("entity.warden.death", 0.9, 1.1)],
                     "step": [("entity.iron_golem.step", 0.6, 0.7)]}),
]
