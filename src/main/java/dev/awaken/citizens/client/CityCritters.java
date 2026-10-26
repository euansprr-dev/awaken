package dev.awaken.citizens.client;

import dev.awaken.citizens.CitizenBody;
import dev.awaken.citizens.CitizensContent;
import dev.awaken.citizens.CityCritter;
import dev.awaken.citizens.Roster;
import dev.awaken.city.CityPlan;
import dev.awaken.mobs.client.anim.HeroRig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Carillon's animals (M4 B3b; SPEC §16.0 decision 27): pigeon flocks on the square, at the fountains, the wells and the
 * bell towers that burst up as you run past and wheel round before settling again (the tower flocks lift when their
 * bell rings); gulls riding the air over the river and squabbling on the nets; cats on roofs and doorsteps, asleep by
 * day, watching you, slipping away when you come too close, their eyes catching the lamps at night; dogs at the heels
 * of their owners; carters' horses drawing carts down the avenues by day, hooves on the stones and bells on the collar;
 * boats moored along both quays, rocking on the swell, and boatmen rowing round the canal and up and down the river,
 * lanterns lit on their bows after dusk.
 * <p>All of it on the client ({@link CityCritter}, never on the server): homes come from the plan (the same file the
 * server paints from), animals are made within reach of the player and dropped beyond it, carts run on the world clock
 * (every client sees them in the same place), and so do the rowed boats. Caps: 40 pigeons, 16 gulls, 10 cats, 8 dogs,
 * 4 carts, 8 boats near you.
 */
public final class CityCritters {
    private CityCritters() {}

    // states (CityCritter.state): birds below LAND count as airborne from FLY
    static final int GROUND = 0, PERCH = 1, FLY = 2, LAND = 3, SIT = 10, WALK = 11, FLEE = 12, HOP = 13, FOLLOW = 20, VISIT = 21, DRIVE = 30;
    static final double RANGE_BIRD = 56, RANGE_GULL = 72, RANGE_CAT = 40, RANGE_DOG = 48, RANGE_CART = 96, RANGE_BOAT = 88, SLACK = 12;
    static final int CAP_PIGEON = 40, CAP_GULL = 16, CAP_CAT = 10, CAP_DOG = 8, CAP_CART = 4, CAP_BOAT = 8;
    /** A cart horse's pace (blocks a tick: a heavy walk) and the axle's distance behind the horse (blocks). */
    static final double CART_SPEED = 0.085, AXLE = 1.75;
    /** A rowed boat's pace (blocks a tick: an easy pull), how far right of the line it keeps on the river and the canal. */
    static final double BOAT_SPEED = 0.06, KEEP_RIVER = 3.5, KEEP_CANAL = 1.0, KEEP_ROAD = 1.6;
    /** A moored boat every this many blocks along each quay; none this near a bridge. */
    static final double MOOR_EVERY = 24, MOOR_CLEAR = 6;
    /** How near the end of its river run a rowed boat drifts round (blocks). */
    static final double TURN = 4;
    /** Where a boat's lantern hangs: forward of its middle and above the water (blocks; the rig's stem post). */
    static final double LANTERN_FWD = 2.03, LANTERN_UP = 1.06;

    /** One place animals live: a flock's patch of ground, a cat's house, a cart's route. */
    static final class Home {
        final int kind, seed, size;
        final double x, z, radius;
        final boolean tower;
        final List<CityCritter> members = new ArrayList<>();
        boolean spawned;
        /** Ticks a cart stood waiting (it runs on the clock less these). */
        long delay;
        /** A cart's road or a boat's water (world x, z pairs), its length; a loop is driven round, else back and forth. */
        double[] road;
        double length, speed = CART_SPEED, keep = KEEP_ROAD;
        boolean loop;
        /** A moored boat's heading (degrees, the entity's yaw). */
        float facing;
        /** Where the flock's ground is (cached once its chunk is in), and a threat's last time. */
        double groundY = Double.NaN;
        long alarmed;

        Home(int kind, double x, double z, double radius, int size, int seed, boolean tower) {
            this.kind = kind;
            this.x = x;
            this.z = z;
            this.radius = radius;
            this.size = size;
            this.seed = seed;
            this.tower = tower;
        }
    }

    private static final List<Home> HOMES = new ArrayList<>();
    private static final List<CitizenBody> BODIES = new ArrayList<>();
    private static final Map<CitizenBody, CityCritter> DOGS = new HashMap<>();
    private static boolean built;
    private static int nextId = -2_000_000, clock;
    private static final int[] COUNT = new int[6];
    private static final RandomSource R = RandomSource.create();
    /** Clip indices per animal, by name (the rig's clip order is the model's). */
    private static final Map<String, Integer>[] CLIPS = clipTables();
    /** Tours log what's about every 30 s (the shots' proof). */
    private static final boolean AUTOPILOT = System.getProperty("awaken.autopilot") != null;
    private static double px, py, pz, pSpeed;
    private static long now;
    /** Where a cart's horse and its axle are (cartAt's out: x, z, heading dx, dz, distance along), reused every tick. */
    private static final double[] AT = new double[5], AXLE_AT = new double[5];

    @SuppressWarnings("unchecked")
    private static Map<String, Integer>[] clipTables() {
        Map<String, Integer>[] t = new Map[CritterRenderer.IDS.length];
        for (int k = 0; k < t.length; k++) {
            t[k] = new HashMap<>();
            HeroRig rig = CritterRenderer.rigs().get(CritterRenderer.IDS[k]);
            int i = 0;
            for (String name : rig.clips.keySet()) t[k].put(name, i++);
        }
        return t;
    }

    static void init() {
        CityCritter.brain = CityCritters::think;
    }

    // ---- homes ----------------------------------------------------------------------------------------------------

    private static void build(CityPlan plan) {
        built = true;
        HOMES.clear();
        int cx = CityLife.cx, cz = CityLife.cz;
        RandomSource r = RandomSource.create(0xC17F7L);
        // pigeons: four flocks round the square's floor, one at each fountain, most wells, and a roosting flock in
        // every district's bell tower
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI / 2 + 0.6;
            HOMES.add(new Home(CityCritter.PIGEON, cx + plan.squareX + Math.cos(a) * 15, cz + plan.squareZ + Math.sin(a) * 15, 5, 9 + r.nextInt(4), r.nextInt(), false));
        }
        for (CityPlan.Prop p : plan.props) {
            double mx = cx + (p.x0() + p.x1()) / 2.0 + 0.5, mz = cz + (p.z0() + p.z1()) / 2.0 + 0.5;
            switch (p.kind()) {
                case "fountain" -> HOMES.add(new Home(CityCritter.PIGEON, mx, mz, 4, 6 + r.nextInt(3), r.nextInt(), false));
                case "well" -> {
                    if (r.nextFloat() < 0.7F) HOMES.add(new Home(CityCritter.PIGEON, mx, mz, 4, 4 + r.nextInt(4), r.nextInt(), false));
                }
                case "net" -> HOMES.add(new Home(CityCritter.GULL, mx, mz, 3, 2 + r.nextInt(2), r.nextInt(), false));
                default -> { }
            }
        }
        for (CityPlan.District d : plan.districts) {
            HOMES.add(new Home(CityCritter.PIGEON, cx + d.bellX() + 0.5, cz + d.bellZ() + 0.5, 3, 7 + r.nextInt(4), r.nextInt(), true));
        }
        // gulls ride the air over the river, a few flocks along it
        List<float[]> river = plan.river;
        double carry = 0;
        for (int i = 0; i + 1 < river.size(); i++) {
            float[] a = river.get(i), b = river.get(i + 1);
            double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
            for (double t = 30 - carry; t < len; t += 70) {
                double f = t / len;
                HOMES.add(new Home(CityCritter.GULL, cx + a[0] + (b[0] - a[0]) * f, cz + a[1] + (b[1] - a[1]) * f, 22, 3 + r.nextInt(3), r.nextInt(), true));
            }
            carry = (carry + len) % 70;
        }
        // cats: one house in five in Bellwatch (cats on sills), one in twelve elsewhere
        for (CityPlan.Plot pl : plan.plots) {
            if (pl.storeys() < 1) continue;
            if (r.nextFloat() >= (pl.district() == 2 ? 0.2F : 0.08F)) continue;
            HOMES.add(new Home(CityCritter.CAT, cx + (pl.x0() + pl.x1()) / 2.0 + 0.5, cz + (pl.z0() + pl.z1()) / 2.0 + 0.5,
                    Math.max(2, Math.min(pl.x1() - pl.x0(), pl.z1() - pl.z0()) / 2.0), 1, r.nextInt(), pl.hasDoor()));
        }
        // carts: roads chained from the avenues
        for (int k = 0; k < 6; k++) {
            double[] road = road(plan, cx, cz, r);
            if (road == null) continue;
            Home h = new Home(CityCritter.HORSE, road[0], road[1], 0, 1, r.nextInt(), false);
            h.road = road;
            for (int i = 2; i < road.length; i += 2) h.length += Math.hypot(road[i] - road[i - 2], road[i + 1] - road[i - 1]);
            if (h.length > 60) HOMES.add(h);
        }
        boats(plan, cx, cz, r);
    }

    /**
     * The boats: one moored every {@value #MOOR_EVERY} blocks along each quay inside the wall (none by a bridge), two
     * boatmen rowing up and down the river and one round the canal (out of the river, round the Quay's island, back).
     */
    private static void boats(CityPlan plan, int cx, int cz, RandomSource r) {
        List<float[]> river = plan.river;
        double inner = plan.wallRadius - 12;
        double moor = CityLife.RIVER_HALF - 1.8;
        List<Double> water = new ArrayList<>();
        double along = 0;
        // (M4 B3b) the roster's berths: each boatman works over his own boat (citizens.json "moorings"); a roster
        // without them falls back to a boat every MOOR_EVERY blocks
        Roster ro = Roster.get();
        boolean berths = ro != null && !ro.moorings.isEmpty();
        if (berths) {
            for (Roster.Mooring m : ro.moorings) {
                Home h = new Home(CityCritter.BOAT, cx + m.boatX(), cz + m.boatZ(), 0, 1, r.nextInt(), false);
                h.facing = m.yaw() + (r.nextBoolean() ? 180F : 0F);
                HOMES.add(h);
            }
        }
        for (int i = 0; i + 1 < river.size(); i++) {
            float[] a = river.get(i), b = river.get(i + 1);
            double len = Math.hypot(b[0] - a[0], b[1] - a[1]), dx = (b[0] - a[0]) / len, dz = (b[1] - a[1]) / len;
            for (double t = 0; t < len; t += 4, along += 4) {
                double x = a[0] + dx * t, z = a[1] + dz * t;
                if (Math.hypot(x, z) > inner) continue;
                water.add(cx + x);
                water.add(cz + z);
                if (along % MOOR_EVERY >= 4 || berths) continue;
                for (int side = -1; side <= 1; side += 2) {
                    if (r.nextFloat() < 0.25F) continue;
                    double mx = x - dz * moor * side, mz = z + dx * moor * side;
                    if (nearBridge(plan, mx, mz)) continue;
                    Home h = new Home(CityCritter.BOAT, cx + mx + 0.5, cz + mz + 0.5, 0, 1, r.nextInt(), false);
                    h.facing = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F + (r.nextBoolean() ? 180F : 0F);
                    HOMES.add(h);
                }
            }
        }
        if (water.size() >= 4) {
            for (int k = 0; k < 2; k++) rowed(water, false, KEEP_RIVER, r);
        }
        // the canal leaves the river and comes back to it: round it, then down the river to where it began
        List<float[]> canal = CityLife.path("canal");
        if (canal.size() >= 2) {
            List<Double> loop = new ArrayList<>();
            for (float[] q : canal) {
                loop.add(cx + q[0] + 0.5);
                loop.add(cz + q[1] + 0.5);
            }
            // the river's points between where the canal rejoins it and where it left, in that order
            float[] end = canal.getLast(), start = canal.getFirst();
            List<float[]> back = new ArrayList<>();
            for (float[] q : river) if (q[1] > Math.min(start[1], end[1]) && q[1] < Math.max(start[1], end[1])) back.add(q);
            if (end[1] > start[1]) java.util.Collections.reverse(back);
            for (float[] q : back) {
                loop.add(cx + q[0] + 0.5);
                loop.add(cz + q[1] + 0.5);
            }
            loop.add(loop.get(0));
            loop.add(loop.get(1));
            rowed(loop, true, KEEP_CANAL, r);
        }
    }

    private static void rowed(List<Double> pts, boolean loop, double keep, RandomSource r) {
        Home h = new Home(CityCritter.BOAT, pts.get(0), pts.get(1), 0, 1, r.nextInt(), false);
        h.road = new double[pts.size()];
        for (int i = 0; i < pts.size(); i++) h.road[i] = pts.get(i);
        for (int i = 2; i < h.road.length; i += 2) h.length += Math.hypot(h.road[i] - h.road[i - 2], h.road[i + 1] - h.road[i - 1]);
        h.loop = loop;
        h.speed = BOAT_SPEED;
        h.keep = keep;
        if (h.length > 40) HOMES.add(h);
    }

    private static boolean nearBridge(CityPlan plan, double x, double z) {
        for (CityPlan.Bridge b : plan.bridges) {
            if (x > Math.min(b.x0(), b.x1()) - MOOR_CLEAR && x < Math.max(b.x0(), b.x1()) + MOOR_CLEAR
                    && z > Math.min(b.z0(), b.z1()) - MOOR_CLEAR && z < Math.max(b.z0(), b.z1()) + MOOR_CLEAR) return true;
        }
        return false;
    }

    /** A cart's road: avenue segments chained end to end from a seeded start, never turning back, about 600 long. */
    private static double[] road(CityPlan plan, int cx, int cz, RandomSource r) {
        List<CityPlan.Street> av = new ArrayList<>();
        for (CityPlan.Street s : plan.streets) if (s.rank() == 1) av.add(s);
        if (av.isEmpty()) return null;
        CityPlan.Street cur = av.get(r.nextInt(av.size()));
        boolean forward = r.nextBoolean();
        List<Double> pts = new ArrayList<>();
        double ex = forward ? cur.ax() : cur.bx(), ez = forward ? cur.az() : cur.bz();
        pts.add(cx + ex);
        pts.add(cz + ez);
        double total = 0;
        List<CityPlan.Street> used = new ArrayList<>();
        for (int step = 0; step < 24 && total < 600; step++) {
            used.add(cur);
            double fx = forward ? cur.bx() : cur.ax(), fz = forward ? cur.bz() : cur.az();
            total += Math.hypot(fx - ex, fz - ez);
            pts.add(cx + fx);
            pts.add(cz + fz);
            ex = fx;
            ez = fz;
            CityPlan.Street next = null;
            boolean nf = true;
            int seen = 0;
            for (CityPlan.Street s : av) {
                if (used.contains(s)) continue;
                boolean a = Math.abs(s.ax() - ex) < 3 && Math.abs(s.az() - ez) < 3, b = Math.abs(s.bx() - ex) < 3 && Math.abs(s.bz() - ez) < 3;
                if (!a && !b) continue;
                if (r.nextInt(++seen) == 0) {
                    next = s;
                    nf = a;
                }
            }
            if (next == null) break;
            cur = next;
            forward = nf;
        }
        if (pts.size() < 4) return null;
        double[] out = new double[pts.size()];
        for (int i = 0; i < out.length; i++) out[i] = pts.get(i);
        return out;
    }

    // ---- once a tick: spawn and drop ------------------------------------------------------------------------------

    static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer p = mc.player;
        if (level == null || p == null || mc.isPaused()) return;
        if (!CityLife.known()) return;
        CityPlan plan = CityLife.plan();
        if (plan == null) return;
        if (!built) build(plan);
        double npx = p.getX(), npz = p.getZ();
        pSpeed = Math.hypot(npx - px, npz - pz);
        px = npx;
        py = p.getY();
        pz = npz;
        now = level.getGameTime();
        if (++clock % 10 != 0) return;
        int tod = (int) Math.floorMod(level.getOverworldClockTime(), 24000L);
        boolean night = tod >= 13000 && tod < 23000;
        for (int k = 0; k < COUNT.length; k++) COUNT[k] = 0;
        for (Home h : HOMES) {
            h.members.removeIf(c -> c.isRemoved());
            if (h.spawned) COUNT[h.kind] += h.members.size();
        }
        for (Home h : HOMES) {
            double reach = switch (h.kind) {
                case CityCritter.PIGEON -> RANGE_BIRD;
                case CityCritter.GULL -> RANGE_GULL;
                case CityCritter.CAT -> RANGE_CAT;
                case CityCritter.BOAT -> RANGE_BOAT;
                default -> RANGE_CART;
            };
            double hx = h.x, hz = h.z;
            if (h.road != null) {
                cartAt(h, level, 0, AT);
                hx = AT[0];
                hz = AT[1];
            }
            double d = Math.hypot(hx - px, hz - pz);
            boolean wanted = d < reach && awake(h, night, tod);
            if (h.spawned && (d > reach + SLACK || !awake(h, night, tod))) {
                for (CityCritter c : h.members) c.discard();
                h.members.clear();
                h.spawned = false;
            } else if (!h.spawned && wanted && level.isLoaded(net.minecraft.core.BlockPos.containing(hx, py, hz))) {
                int cap = switch (h.kind) {
                    case CityCritter.PIGEON -> CAP_PIGEON;
                    case CityCritter.GULL -> CAP_GULL;
                    case CityCritter.CAT -> CAP_CAT;
                    case CityCritter.BOAT -> CAP_BOAT;
                    default -> CAP_CART;
                };
                int n = h.size;
                if (COUNT[h.kind] + n > cap) continue;
                // a boat needs its water in view (a chunk not yet in, or a plan that doesn't match the world)
                if (h.kind == CityCritter.BOAT && Double.isNaN(water(level, hx, hz))) continue;
                spawn(level, h, n, night);
                COUNT[h.kind] += n;
                h.spawned = true;
            }
        }
        dogs(level);
        if (AUTOPILOT && clock % 600 == 0) {
            int[] c = counts();
            dev.awaken.Awaken.LOG.info("[citizens] animals near you: {} pigeons, {} gulls, {} cats, {} dogs, {} carts, {} boats ({} homes)",
                    c[CityCritter.PIGEON], c[CityCritter.GULL], c[CityCritter.CAT], c[CityCritter.DOG], c[CityCritter.HORSE],
                    c[CityCritter.BOAT], HOMES.size());
        }
    }

    /** Who is about at this hour: pigeons on the ground by day, in their towers at night; carts by day. */
    private static boolean awake(Home h, boolean night, int tod) {
        return switch (h.kind) {
            case CityCritter.PIGEON -> !night || h.tower;
            case CityCritter.GULL -> !night || !h.tower;
            case CityCritter.HORSE -> tod < 12600 || tod > 23600;
            // boatmen row from first light till the taverns empty; moored boats are always there
            case CityCritter.BOAT -> h.road == null || tod < 18000 || tod > 23000;
            default -> true;
        };
    }

    private static void spawn(ClientLevel level, Home h, int n, boolean night) {
        RandomSource r = RandomSource.create(h.seed ^ (now / 24000L));
        for (int i = 0; i < n; i++) {
            CityCritter c = new CityCritter(CitizensContent.CRITTER.get(), level);
            c.setId(nextId--);
            c.kind = h.kind;
            c.seed = h.seed + i * 977;
            c.home = HOMES.indexOf(h);
            c.variant = Math.floorMod(c.seed >> 4, 64);
            double x = h.x, z = h.z, y;
            switch (h.kind) {
                case CityCritter.PIGEON -> {
                    double a = r.nextDouble() * Math.PI * 2, rr = r.nextDouble() * h.radius;
                    x += Math.cos(a) * rr;
                    z += Math.sin(a) * rr;
                    if (h.tower || night) {
                        y = top(level, x, z) ;
                        c.state = PERCH;
                    } else {
                        y = ground(level, x, z, h);
                        c.state = GROUND;
                    }
                }
                case CityCritter.GULL -> {
                    if (h.tower) {
                        double a = r.nextDouble() * Math.PI * 2;
                        x += Math.cos(a) * h.radius;
                        z += Math.sin(a) * h.radius;
                        y = top(level, x, z) + 14 + r.nextDouble() * 10;
                        c.state = FLY;
                        c.timer = 999999;
                    } else {
                        x += (r.nextDouble() - 0.5) * 3;
                        z += (r.nextDouble() - 0.5) * 3;
                        y = top(level, x, z);
                        c.state = PERCH;
                    }
                }
                case CityCritter.CAT -> {
                    double[] spot = catSpot(level, h, r);
                    x = spot[0];
                    y = spot[1];
                    z = spot[2];
                    c.state = SIT;
                }
                case CityCritter.BOAT -> {
                    if (h.road != null) {
                        cartAt(h, level, 0, AT);
                        x = AT[0];
                        z = AT[1];
                        c.state = DRIVE;
                        c.crew = true;
                        c.setYRot((float) (Mth.atan2(AT[3], AT[2]) * Mth.RAD_TO_DEG) - 90F);
                    } else {
                        c.state = PERCH;
                        c.setYRot(h.facing);
                    }
                    y = water(level, x, z);
                }
                default -> {
                    cartAt(h, level, 0, AT);
                    x = AT[0];
                    y = ground(level, x, AT[1], null);
                    z = AT[1];
                    c.state = DRIVE;
                }
            }
            c.setPos(x, y, z);
            if (h.kind != CityCritter.BOAT) c.setYRot(r.nextFloat() * 360F);
            c.yRotO = c.getYRot();
            c.xo = x;
            c.yo = y;
            c.zo = z;
            c.since = now;
            c.timer = c.state == FLY ? c.timer : 20 + r.nextInt(80);
            level.addEntity(c);
            h.members.add(c);
        }
    }

    // ---- dogs at their owners' heels ----------------------------------------------------------------------------

    static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() && e.getEntity() instanceof CitizenBody b) BODIES.add(b);
    }

    static void onLeave(EntityLeaveLevelEvent e) {
        if (!e.getLevel().isClientSide() || !(e.getEntity() instanceof CitizenBody b)) return;
        BODIES.remove(b);
        CityCritter d = DOGS.remove(b);
        if (d != null) d.discard();
    }

    /** One citizen in seven who keeps a dog (residents, children, the Watch, porters, washers, shopkeepers). */
    static boolean keepsDog(int rec) {
        Roster r = Roster.get();
        if (r == null || rec < 0 || rec >= r.records || !r.isCitizen(rec)) return false;
        String job = Roster.JOBS[r.job[rec]];
        boolean ok = switch (job) {
            case "resident", "child", "watch", "porter", "washer", "shopkeeper", "miller" -> true;
            default -> false;
        };
        return ok && Math.floorMod(rec * 2654435761L >> 7, 7) == 0;
    }

    private static void dogs(ClientLevel level) {
        int n = 0;
        for (var it = DOGS.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            if (en.getValue().isRemoved() || en.getKey().isRemoved() || en.getKey().distanceToSqr(px, py, pz) > (RANGE_DOG + SLACK) * (RANGE_DOG + SLACK)) {
                en.getValue().discard();
                it.remove();
            } else n++;
        }
        for (CitizenBody b : BODIES) {
            if (n >= CAP_DOG) break;
            if (DOGS.containsKey(b) || b.isRemoved() || b.settlement() != 0 || !keepsDog(b.record())) continue;
            if (b.distanceToSqr(px, py, pz) > RANGE_DOG * RANGE_DOG) continue;
            CityCritter c = new CityCritter(CitizensContent.CRITTER.get(), level);
            c.setId(nextId--);
            c.kind = CityCritter.DOG;
            c.seed = b.record() * 7919;
            c.variant = Math.floorMod(b.record() * 31, 64);
            c.owner = b.getId();
            c.state = FOLLOW;
            double fx = -Mth.sin(b.getYRot() * Mth.DEG_TO_RAD), fz = Mth.cos(b.getYRot() * Mth.DEG_TO_RAD);
            c.setPos(b.getX() - fx * 1.4, b.getY(), b.getZ() - fz * 1.4);
            c.xo = c.getX();
            c.yo = c.getY();
            c.zo = c.getZ();
            c.setYRot(b.getYRot());
            c.yRotO = c.getYRot();
            level.addEntity(c);
            DOGS.put(b, c);
            n++;
        }
    }

    // ---- the brain ----------------------------------------------------------------------------------------------

    private static void think(CityCritter c) {
        if (!c.level().isClientSide()) {
            c.discard();
            return;
        }
        c.wheelO = c.wheel;
        c.cartDxO = c.cartDx;
        c.cartDzO = c.cartDz;
        c.cartYawO = c.cartYaw;
        c.cartYO = c.cartY;
        c.speed = 0F;
        ClientLevel level = (ClientLevel) c.level();
        Home h = c.home >= 0 && c.home < HOMES.size() ? HOMES.get(c.home) : null;
        try {
            switch (c.kind) {
                case CityCritter.PIGEON, CityCritter.GULL -> bird(c, level, h);
                case CityCritter.CAT -> cat(c, level, h);
                case CityCritter.DOG -> dog(c, level);
                case CityCritter.HORSE -> horse(c, level, h);
                case CityCritter.BOAT -> boat(c, level, h);
                default -> c.discard();
            }
        } catch (RuntimeException ex) {
            c.discard();
        }
    }

    private static void clip(CityCritter c, String name) {
        Integer i = CLIPS[c.kind].get(name);
        int idx = i == null ? -1 : i;
        if (idx == c.clip) return;
        c.prevClip = c.clip;
        c.prevStart = c.clipStart;
        c.prevEnd = c.tickCount;
        c.clip = idx;
        c.clipStart = c.tickCount;
    }

    private static double dist2Player(CityCritter c) {
        double dx = c.getX() - px, dy = c.getY() - py, dz = c.getZ() - pz;
        return dx * dx + dy * dy * 0.5 + dz * dz;
    }

    private static void lookAtPlayer(CityCritter c, double within) {
        if (dist2Player(c) < within * within) {
            c.lookX = px;
            c.lookY = py + 1.5;
            c.lookZ = pz;
        } else c.lookX = Double.NaN;
    }

    /** Turns toward a heading at most {@code rate} degrees a tick. */
    private static void face(CityCritter c, double dx, double dz, float rate) {
        if (dx * dx + dz * dz < 1e-6) return;
        float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        float d = Mth.wrapDegrees(want - c.getYRot());
        c.setYRot(c.getYRot() + Mth.clamp(d, -rate, rate));
    }

    /** Steps along the ground toward (tx, tz) at {@code speed}; true when there. Steps up to 1.2, else stops. */
    private static boolean walk(CityCritter c, ClientLevel level, double tx, double tz, double speed, float turn, Home h) {
        double dx = tx - c.getX(), dz = tz - c.getZ(), d = Math.sqrt(dx * dx + dz * dz);
        if (d < Math.max(0.12, speed)) return true;
        face(c, dx, dz, turn);
        double k = Math.min(speed, d) / d;
        double nx = c.getX() + dx * k, nz = c.getZ() + dz * k;
        double ny = ground(level, nx, nz, null);
        if (Math.abs(ny - c.getY()) > 1.2) return true;
        c.setPos(nx, c.getY() + (ny - c.getY()) * 0.5, nz);
        c.speed = (float) (d * k);
        return false;
    }

    // ---- birds --------------------------------------------------------------------------------------------------

    private static void bird(CityCritter c, ClientLevel level, Home h) {
        boolean gull = c.kind == CityCritter.GULL;
        if (h == null) {
            c.discard();
            return;
        }
        RandomSource r = c.getRandom();
        switch (c.state) {
            case GROUND, PERCH -> {
                boolean ground = c.state == GROUND;
                if (threat(c, gull ? 6.5 : 4.0, gull ? 9 : 8) || h.alarmed > now - 4) {
                    if (h.alarmed < now - 4) {
                        h.alarmed = now;
                        sound(level, c, gull ? CitySounds.GULL_CALL : CitySounds.PIGEON_FLUTTER, gull ? 0.7F : 0.9F, 0.9F + r.nextFloat() * 0.2F);
                    }
                    takeOff(c, r);
                    return;
                }
                // a citizen walking through: hop aside
                if (ground && c.tickCount % 5 == 0 && nearBody(c, 0.9)) {
                    c.state = HOP;
                    c.timer = 8;
                    c.vx = (r.nextDouble() - 0.5) * 0.25;
                    c.vz = (r.nextDouble() - 0.5) * 0.25;
                    c.vy = 0.22;
                    clip(c, "fly");
                    return;
                }
                if (--c.timer > 0) {
                    if (ground && c.clip == CLIPS[c.kind].get("walk")) {
                        if (walk(c, level, c.tx, c.tz, gull ? 0.06 : 0.045, 18F, h)) c.timer = 0;
                    }
                    return;
                }
                // what next: on the ground walk, peck or stand; perched, preen or call
                float roll = r.nextFloat();
                if (ground && roll < 0.45F) {
                    double a = r.nextDouble() * Math.PI * 2, rr = r.nextDouble() * h.radius;
                    c.tx = h.x + Math.cos(a) * rr;
                    c.tz = h.z + Math.sin(a) * rr;
                    clip(c, "walk");
                    c.timer = 120;
                } else if (ground && roll < 0.8F) {
                    clip(c, "peck");
                    c.timer = 30 + r.nextInt(60);
                } else if (roll < 0.9F || ground) {
                    clip(c, "idle");
                    c.timer = 40 + r.nextInt(80);
                } else {
                    clip(c, "call");
                    c.timer = 30;
                    if (dist2Player(c) < 28 * 28) {
                        sound(level, c, gull ? CitySounds.GULL_CALL : CitySounds.PIGEON_COO, gull ? 0.55F : 0.35F, 0.9F + r.nextFloat() * 0.25F);
                    }
                }
                // perched birds come back down to their ground after a while (unless it's their tower)
                if (!ground && !h.tower && c.tickCount - c.since > 400 + r.nextInt(600) && dist2Player(c) > 12 * 12 && !gull) {
                    landAt(c, level, h, r, true);
                }
            }
            case HOP -> {
                c.vy -= 0.04;
                c.setPos(c.getX() + c.vx, c.getY() + c.vy, c.getZ() + c.vz);
                double g = ground(level, c.getX(), c.getZ(), null);
                if (--c.timer <= 0 || c.getY() <= g) {
                    c.setPos(c.getX(), g, c.getZ());
                    c.state = GROUND;
                    c.timer = 20;
                    clip(c, "idle");
                }
            }
            case FLY -> fly(c, level, h, r, gull);
            case LAND -> land(c, level, h, r);
            default -> c.state = GROUND;
        }
    }

    /** A threat: the player close, or running past within {@code run}; for a gull, the player only. */
    private static boolean threat(CityCritter c, double near, double run) {
        double d2 = dist2Player(c);
        return d2 < near * near || (d2 < run * run && pSpeed > 0.17);
    }

    private static boolean nearBody(CityCritter c, double within) {
        for (CitizenBody b : BODIES) if (!b.isRemoved() && b.distanceToSqr(c) < within * within) return true;
        return false;
    }

    private static void takeOff(CityCritter c, RandomSource r) {
        c.state = FLY;
        c.since = now;
        // staggered: the flock goes up in a burst, not in step
        c.timer = 160 + r.nextInt(200);
        double ax = c.getX() - px, az = c.getZ() - pz, al = Math.max(0.3, Math.sqrt(ax * ax + az * az));
        double away = c.kind == CityCritter.GULL ? 0.16 : 0.22;
        c.vx = ax / al * away + (r.nextDouble() - 0.5) * 0.12;
        c.vz = az / al * away + (r.nextDouble() - 0.5) * 0.12;
        c.vy = 0.30 + r.nextDouble() * 0.14;
        c.flock = r.nextInt(1000);
        clip(c, "fly");
    }

    /** In the air: climb away, then wheel round the home as a flock; settle when the coast is clear. */
    private static void fly(CityCritter c, ClientLevel level, Home h, RandomSource r, boolean gull) {
        double gy = !Double.isNaN(h.groundY) ? h.groundY : top(level, h.x, h.z);
        double radius = gull ? 14 + Math.floorMod(c.seed, 12) : 8 + Math.floorMod(c.seed, 5);
        double height = gy + (gull ? 14 + Math.floorMod(c.seed >> 3, 12) : 9 + Math.floorMod(c.seed >> 3, 6));
        double speed = gull ? 0.032 : 0.07;
        double ang = (now + h.seed % 1000) * speed / Math.max(4, radius) * 8 + Math.floorMod(c.seed, 360) * Mth.DEG_TO_RAD * (gull ? 1 : 0.35);
        double tx = h.x + Math.cos(ang) * radius, tz = h.z + Math.sin(ang) * radius;
        double ax = (tx - c.getX()) * 0.012, ay = (height - c.getY()) * 0.01, az = (tz - c.getZ()) * 0.012;
        c.vx = c.vx * 0.94 + ax;
        c.vy = c.vy * 0.9 + ay;
        c.vz = c.vz * 0.94 + az;
        double sp = Math.sqrt(c.vx * c.vx + c.vz * c.vz), max = gull ? 0.32 : 0.42;
        if (sp > max) {
            c.vx *= max / sp;
            c.vz *= max / sp;
        }
        c.setPos(c.getX() + c.vx, c.getY() + c.vy, c.getZ() + c.vz);
        face(c, c.vx, c.vz, gull ? 6F : 14F);
        c.speed = (float) sp;
        // flap while climbing, glide when level or falling (a gull mostly glides, beating now and then)
        boolean flap = c.vy > 0.03 || (gull ? (now + c.seed) % 140 < 26 : (now + c.seed) % 60 < 38);
        clip(c, flap ? "fly" : "glide");
        if (gull && (now + c.seed) % 300 == 0 && dist2Player(c) < 48 * 48) sound(level, c, CitySounds.GULL_CALL, 0.6F, 0.85F + r.nextFloat() * 0.3F);
        if (--c.timer <= 0 && !threat(c, 10, 14)) landAt(c, level, h, r, !h.tower && r.nextFloat() < 0.6F);
    }

    /** Picks somewhere to come down: the flock's own ground, else a roof nearby (a tower flock: its tower). */
    private static void landAt(CityCritter c, ClientLevel level, Home h, RandomSource r, boolean ground) {
        c.state = LAND;
        double a = r.nextDouble() * Math.PI * 2, rr = r.nextDouble() * h.radius;
        double x = h.x + Math.cos(a) * rr, z = h.z + Math.sin(a) * rr;
        if (!ground && !h.tower) {
            double best = -1;
            for (int k = 0; k < 6; k++) {
                double ra = r.nextDouble() * Math.PI * 2, rd = 4 + r.nextDouble() * 12;
                double qx = h.x + Math.cos(ra) * rd, qz = h.z + Math.sin(ra) * rd;
                double qy = top(level, qx, qz);
                if (qy > best) {
                    best = qy;
                    x = qx;
                    z = qz;
                }
            }
        }
        c.tx = x;
        c.tz = z;
        c.ty = ground && !h.tower ? ground(level, x, z, h) : top(level, x, z);
        c.timer = ground && !h.tower ? 1 : 0; // 1: lands on its ground
    }

    private static void land(CityCritter c, ClientLevel level, Home h, RandomSource r) {
        double dx = c.tx - c.getX(), dy = c.ty - c.getY(), dz = c.tz - c.getZ();
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double sp = Mth.clamp(d * 0.08, 0.05, 0.3);
        if (d < 0.2) {
            c.setPos(c.tx, c.ty, c.tz);
            c.state = c.timer == 1 ? GROUND : PERCH;
            c.since = now;
            c.timer = 20 + r.nextInt(40);
            clip(c, "idle");
            return;
        }
        c.vx = c.vx * 0.85 + dx / d * sp * 0.15;
        c.vy = c.vy * 0.85 + dy / d * sp * 0.15;
        c.vz = c.vz * 0.85 + dz / d * sp * 0.15;
        if (d < 2.2) {
            c.vx = dx / d * sp;
            c.vy = dy / d * sp;
            c.vz = dz / d * sp;
        }
        c.setPos(c.getX() + c.vx, c.getY() + c.vy, c.getZ() + c.vz);
        face(c, dx, dz, 12F);
        c.speed = (float) sp;
        clip(c, d < 1.8 ? "flare" : dy < -0.5 ? "glide" : "fly");
    }

    /** The bells: a tower flock within reach of a bell that rings lifts and wheels round its tower. */
    static void onSound(PlaySoundEvent e) {
        SoundInstance s = e.getSound();
        if (s == null || HOMES.isEmpty()) return;
        Identifier id = s.getIdentifier();
        if (!id.getNamespace().equals("awaken") || !id.getPath().startsWith("city.bell")) return;
        for (Home h : HOMES) {
            if (!h.spawned || !h.tower || h.kind != CityCritter.PIGEON) continue;
            if (Math.hypot(h.x - s.getX(), h.z - s.getZ()) > 24) continue;
            for (CityCritter c : h.members) if (c.state == PERCH || c.state == GROUND) takeOff(c, c.getRandom());
            if (!h.members.isEmpty()) sound((ClientLevel) h.members.getFirst().level(), h.members.getFirst(), CitySounds.PIGEON_FLUTTER, 1.0F, 0.95F);
        }
    }

    // ---- cats ---------------------------------------------------------------------------------------------------

    /** A cat's places on its house: the ridge, an eave corner, the doorstep. */
    private static double[] catSpot(ClientLevel level, Home h, RandomSource r) {
        int pick = r.nextInt(h.tower ? 4 : 3);
        double x = h.x, z = h.z;
        if (pick == 1 || pick == 2) {
            x += (r.nextBoolean() ? 1 : -1) * h.radius * 0.8;
            z += (r.nextBoolean() ? 1 : -1) * h.radius * 0.8;
        } else if (pick == 3) {
            // the doorstep: the ground in front of the house, found by walking out of it
            for (int k = 0; k < 4; k++) {
                double a = k * Math.PI / 2;
                double qx = h.x + Math.cos(a) * (h.radius + 2.5), qz = h.z + Math.sin(a) * (h.radius + 2.5);
                double qy = ground(level, qx, qz, null);
                if (qy < top(level, h.x, h.z) - 3) return new double[]{qx, qy, qz};
            }
        }
        return new double[]{x, top(level, x, z), z};
    }

    private static void cat(CityCritter c, ClientLevel level, Home h) {
        if (h == null) {
            c.discard();
            return;
        }
        RandomSource r = c.getRandom();
        double d2 = dist2Player(c);
        switch (c.state) {
            case SIT -> {
                boolean sleeping = c.clip == CLIPS[c.kind].get("sleep");
                if (!sleeping) lookAtPlayer(c, 9);
                else c.lookX = Double.NaN;
                if ((d2 < 2.4 * 2.4 && !sleeping) || (sleeping && d2 < 1.3 * 1.3) || (pSpeed > 0.2 && d2 < 4 * 4)) {
                    if (r.nextFloat() < (sleeping ? 0.5F : 0.35F) || pSpeed > 0.2) {
                        double[] s = catSpot(level, h, r);
                        c.tx = s[0];
                        c.tz = s[2];
                        c.state = FLEE;
                        c.timer = 120;
                        clip(c, "trot");
                        if (r.nextFloat() < 0.3F) sound(level, c, CitySounds.CAT_MEOW, 0.4F, 1.1F + r.nextFloat() * 0.2F);
                        return;
                    }
                    if (sleeping) clip(c, "sit");
                    c.timer = Math.max(c.timer, 60);
                }
                if (d2 < 1.6 * 1.6 && (now + c.seed) % 80 == 0) sound(level, c, CitySounds.CAT_PURR, 0.5F, 1F);
                if (--c.timer > 0) return;
                float roll = r.nextFloat();
                int tod = (int) Math.floorMod(level.getOverworldClockTime(), 24000L);
                boolean night = tod >= 13000 && tod < 23000;
                if (roll < (night ? 0.35F : 0.15F)) {
                    double[] s = catSpot(level, h, r);
                    c.tx = s[0];
                    c.tz = s[2];
                    c.state = WALK;
                    c.timer = 400;
                    clip(c, "walk");
                    return;
                }
                float sl = night ? 0.15F : 0.4F;
                String next = roll < 0.15F + sl ? "sleep" : roll < 0.5F + sl * 0.5F ? "loaf" : roll < 0.85F ? "sit" : "groom";
                clip(c, next);
                c.timer = 300 + r.nextInt(900);
                if (next.equals("sit") && d2 < 10 * 10 && r.nextFloat() < 0.25F) sound(level, c, CitySounds.CAT_MEOW, 0.35F, 0.9F + r.nextFloat() * 0.3F);
            }
            case WALK, FLEE -> {
                c.lookX = Double.NaN;
                boolean flee = c.state == FLEE;
                double dx = c.tx - c.getX(), dz = c.tz - c.getZ();
                boolean there = walkCat(c, level, flee ? 0.11 : 0.045);
                if (c.state == HOP) return;
                clip(c, flee ? "trot" : "walk");
                if (there || --c.timer <= 0 || dx * dx + dz * dz < 0.01) {
                    c.state = SIT;
                    c.timer = 200 + r.nextInt(400);
                    clip(c, r.nextFloat() < 0.5F ? "sit" : "groom");
                }
            }
            case HOP -> {
                // a cat's leap up or down a storey: a short arc over 8 ticks
                float t = (c.tickCount - c.since) / 8F;
                if (t >= 1F) {
                    c.setPos(c.tx, c.ty, c.tz);
                    c.state = WALK;
                    return;
                }
                double sx = c.vx, sy = c.vy, sz = c.vz;
                double y = Mth.lerp(t, sy, c.ty) + Math.sin(t * Math.PI) * 0.9;
                c.setPos(Mth.lerp(t, sx, c.tx), y, Mth.lerp(t, sz, c.tz));
                c.speed = 0.12F;
            }
            default -> c.state = SIT;
        }
    }

    /** A cat walking along roofs and lanes: steps of up to 1.2 walked, up to 4 down or 2.5 up leapt. */
    private static boolean walkCat(CityCritter c, ClientLevel level, double speed) {
        double dx = c.tx - c.getX(), dz = c.tz - c.getZ(), d = Math.sqrt(dx * dx + dz * dz);
        if (d < 0.15) return true;
        face(c, dx, dz, 16F);
        double k = Math.min(speed, d) / d;
        double nx = c.getX() + dx * k, nz = c.getZ() + dz * k;
        double ny = top(level, nx, nz);
        double step = ny - c.getY();
        if (Math.abs(step) <= 1.2) {
            c.setPos(nx, c.getY() + step * 0.6, nz);
            c.speed = (float) (d * k);
            return false;
        }
        if (step < 4.5 && step > -6) {
            // leap: to a point a block ahead at the new height
            double lk = Math.min(1.2, d) / d;
            c.vx = c.getX();
            c.vy = c.getY();
            c.vz = c.getZ();
            c.tx = c.getX() + dx * lk;
            c.tz = c.getZ() + dz * lk;
            c.ty = top(level, c.tx, c.tz);
            c.state = HOP;
            c.since = c.tickCount;
            clip(c, "trot");
            return false;
        }
        return true;
    }

    // ---- dogs ---------------------------------------------------------------------------------------------------

    private static void dog(CityCritter c, ClientLevel level) {
        var owner = level.getEntity(c.owner);
        if (!(owner instanceof CitizenBody b) || b.isRemoved()) {
            c.discard();
            return;
        }
        RandomSource r = c.getRandom();
        double yaw = b.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Math.sin(yaw), fz = Math.cos(yaw), side = (c.seed & 1) == 0 ? 0.8 : -0.8;
        double ox = b.getX() - fx * 1.4 + fz * side, oz = b.getZ() - fz * 1.4 - fx * side;
        double d = Math.hypot(ox - c.getX(), oz - c.getZ());
        double ownerSpeed = Math.hypot(b.getX() - b.xo, b.getZ() - b.zo);
        double d2p = dist2Player(c);
        if (d > 14) {
            c.setPos(ox, b.getY(), oz);
            return;
        }
        switch (c.state) {
            case VISIT -> {
                lookAtPlayer(c, 12);
                double pdx = px - c.getX(), pdz = pz - c.getZ(), pd = Math.sqrt(pdx * pdx + pdz * pdz);
                if (--c.timer <= 0 || d > 10 || pd > 10) {
                    c.state = FOLLOW;
                    return;
                }
                if (pd > 1.3) {
                    walk(c, level, px - pdx / pd * 1.2, pz - pdz / pd * 1.2, 0.13, 20F, null);
                    clip(c, "trot");
                } else {
                    face(c, pdx, pdz, 20F);
                    clip(c, c.timer % 80 < 50 ? "sit" : "pant");
                    if (c.timer % 70 == 0 && r.nextFloat() < 0.5F) sound(level, c, CitySounds.DOG_BARK, 0.45F, 1.1F + r.nextFloat() * 0.2F);
                }
            }
            default -> {
                c.state = FOLLOW;
                if (d > 2.6) {
                    walk(c, level, ox, oz, Math.min(0.16, ownerSpeed + 0.05), 18F, null);
                    clip(c, "trot");
                    c.timer = 0;
                } else if (d > 0.6 && ownerSpeed > 0.01) {
                    walk(c, level, ox, oz, Math.max(0.04, ownerSpeed), 14F, null);
                    clip(c, "walk");
                    c.timer = 0;
                } else {
                    // waiting: sniff about, sit, then lie down; up close to you, watch and pant
                    c.timer++;
                    boolean near = d2p < 5 * 5;
                    lookAtPlayer(c, 6);
                    if (near) clip(c, c.timer > 40 ? "pant" : "sit");
                    else if (c.timer < 60) clip(c, (c.seed + c.timer / 30) % 3 == 0 ? "sniff" : "idle");
                    else clip(c, c.timer < 400 ? "sit" : "lie");
                    if (near && ownerSpeed < 0.01 && c.timer % 200 == 100 && r.nextFloat() < 0.35F) {
                        c.state = VISIT;
                        c.timer = 140;
                    }
                }
            }
        }
    }

    // ---- horses and carts ---------------------------------------------------------------------------------------

    /**
     * Where a cart's horse (or a rowed boat) is on its road now by the world clock, {@code back} blocks behind it: into
     * {@code out} (x, z, heading dx, dz, distance along). A road is driven there and back; a loop round and round.
     */
    private static void cartAt(Home h, ClientLevel level, double back, double[] out) {
        double t = level.getOverworldClockTime() - h.delay;
        double period = h.loop ? h.length : h.length * 2;
        double raw = Math.floorMod((long) (t * h.speed * 1000 + (h.seed & 0xFFFFF) * 1000L), (long) (period * 1000)) / 1000.0;
        boolean fwd = h.loop || raw < h.length;
        double s = (fwd ? raw : period - raw) - (fwd ? back : -back);
        s = h.loop ? Math.floorMod((long) (s * 1000), (long) (h.length * 1000)) / 1000.0 : Mth.clamp(s, 0, h.length);
        double[] road = h.road;
        double acc = 0;
        for (int i = 2; i < road.length; i += 2) {
            double sx = road[i - 2], sz = road[i - 1], ex = road[i], ez = road[i + 1];
            double len = Math.hypot(ex - sx, ez - sz);
            if (acc + len >= s || i == road.length - 2) {
                double f = len < 1e-6 ? 0 : (s - acc) / len;
                double dx = (ex - sx) / Math.max(1e-6, len), dz = (ez - sz) / Math.max(1e-6, len);
                if (!fwd) {
                    dx = -dx;
                    dz = -dz;
                }
                // keep to the right of the road (the citizens' lanes keep right too, and so do boats)
                out[0] = sx + (ex - sx) * f - dz * h.keep;
                out[1] = sz + (ez - sz) * f + dx * h.keep;
                out[2] = dx;
                out[3] = dz;
                out[4] = s;
                return;
            }
            acc += len;
        }
        out[0] = road[0];
        out[1] = road[1];
        out[2] = 1;
        out[3] = 0;
        out[4] = 0;
    }

    private static void horse(CityCritter c, ClientLevel level, Home h) {
        if (h == null || h.road == null) {
            c.discard();
            return;
        }
        RandomSource r = c.getRandom();
        double[] at = AT;
        cartAt(h, level, 0, at);
        // someone in the road ahead: the horse stands until they move (this client's cart falls behind the clock)
        double ax = px - at[0], az = pz - at[1];
        double ahead = ax * at[2] + az * at[3], across = Math.abs(ax * at[3] - az * at[2]);
        boolean blocked = ahead > 0 && ahead < 3.2 && across < 1.6 && Math.abs(py - c.getY()) < 2.5;
        if (blocked) {
            h.delay++;
            cartAt(h, level, 0, at);
        }
        double ox = c.getX(), oz = c.getZ();
        double y = ground(level, at[0], at[1], null);
        c.setPos(at[0], c.getY() + (y - c.getY()) * 0.3, at[1]);
        double moved = Math.hypot(c.getX() - ox, c.getZ() - oz);
        c.speed = moved > 2 ? 0F : (float) moved;
        face(c, at[2], at[3], 8F);
        double[] axle = AXLE_AT;
        cartAt(h, level, AXLE, axle);
        c.cartDx = axle[0] - c.getX();
        c.cartDz = axle[1] - c.getZ();
        c.cartY = (float) (ground(level, axle[0], axle[1], null) - c.getY());
        double hx = c.getX() - axle[0], hz = c.getZ() - axle[1];
        c.cartYaw = (float) (Mth.atan2(hz, hx) * Mth.RAD_TO_DEG) - 90F;
        c.wheel += (float) (c.speed / 0.5);
        if (c.speed > 0.01F) {
            clip(c, "walk");
            // four beats a stride; heavy, low hooves
            if ((c.tickCount + c.seed) % 7 == 0) sound(level, c, CitySounds.HOOF, 0.4F, 0.78F + r.nextFloat() * 0.12F);
            if ((c.tickCount + c.seed) % 40 == 0) sound(level, c, CitySounds.CART_ROLL, 0.5F, 0.9F + r.nextFloat() * 0.2F);
        } else {
            if (c.clip != CLIPS[c.kind].get("toss") || c.tickCount - c.clipStart > 24) clip(c, "idle");
            if (r.nextInt(240) == 0) {
                clip(c, "toss");
                sound(level, c, CitySounds.HARNESS_BELLS, 0.5F, 1F);
                sound(level, c, CitySounds.HORSE_SNORT, 0.4F, 0.85F);
            }
        }
        lookAtPlayer(c, blocked ? 8 : 4);
    }

    // ---- boats --------------------------------------------------------------------------------------------------

    /** A moored boat rocks where it's tied; a rowed one follows its water by the clock, the oarlocks creaking each stroke. */
    private static void boat(CityCritter c, ClientLevel level, Home h) {
        if (h == null) {
            c.discard();
            return;
        }
        int tod = (int) Math.floorMod(level.getOverworldClockTime(), 24000L);
        boolean night = tod >= 12500 || tod < 300;
        if (h.road == null) {
            // one moored boat in three keeps its lantern lit through the night (someone sleeps aboard)
            c.lamp = night && Math.floorMod(c.seed, 3) == 0;
            if (Double.isNaN(h.groundY)) h.groundY = water(level, h.x, h.z); // its water, found once
            if (!Double.isNaN(h.groundY)) c.setPos(h.x, h.groundY, h.z);
            clip(c, "idle");
            return;
        }
        c.lamp = night;
        double[] at = AT;
        cartAt(h, level, 0, at);
        double ox = c.getX(), oz = c.getZ();
        double y = water(level, at[0], at[1]);
        c.setPos(at[0], Double.isNaN(y) ? c.getY() : y, at[1]);
        double moved = Math.hypot(c.getX() - ox, c.getZ() - oz);
        c.speed = moved > 2 ? 0F : (float) moved;
        face(c, at[2], at[3], 5F);
        // at either end of the river the boatman rests on his oars while the boat comes round
        boolean turning = !h.loop && (at[4] < TURN || at[4] > h.length - TURN);
        clip(c, turning ? "drift" : "walk");
        // the stroke is 2.4 s: the oarlocks at the catch
        if (!turning && (c.tickCount + c.seed) % 48 == 0 && dist2Player(c) < 24 * 24) {
            sound(level, c, CitySounds.OARLOCK, 0.25F, 1.2F + c.getRandom().nextFloat() * 0.15F);
        }
    }

    private static final BlockPos.MutableBlockPos WATER = new BlockPos.MutableBlockPos();

    /** The water's surface at (x, z): the first water found going down from the street (under any bridge); NaN if none. */
    static double water(ClientLevel level, double x, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        for (int y = CityLife.sy; y > CityLife.sy - 16; y--) {
            if (level.getFluidState(WATER.set(bx, y, bz)).is(net.minecraft.tags.FluidTags.WATER)) return y + 0.89;
        }
        return Double.NaN;
    }

    /** The carter's lantern at dusk (CityGlow), hung at the cart's front; the boats' lanterns on their bows. */
    static final CityGlow.Source LANTERNS = (out, partial, cx, cy, cz) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        int tod = (int) Math.floorMod(mc.level.getOverworldClockTime(), 24000L);
        if (tod < 11400 && tod > 600) return;
        for (Home h : HOMES) {
            if (h.kind != CityCritter.HORSE || !h.spawned) continue;
            for (CityCritter c : h.members) {
                double x = Mth.lerp(partial, c.xo, c.getX()) + Mth.lerp(partial, c.cartDxO, c.cartDx) * 0.55;
                double z = Mth.lerp(partial, c.zo, c.getZ()) + Mth.lerp(partial, c.cartDzO, c.cartDz) * 0.55;
                double y = Mth.lerp(partial, c.yo, c.getY());
                if (out.far(x, y, z)) continue;
                float f = out.flicker(c.seed);
                out.halo(x, y + 2.0, z, CityGlow.LANTERN_HALO, CityGlow.LANTERN_CORE, f);
                out.pool(x, y + 0.03, z, CityGlow.LANTERN_POOL, CityGlow.LANTERN_POOL_A * f);
            }
        }
        for (Home h : HOMES) {
            if (h.kind != CityCritter.BOAT || !h.spawned) continue;
            for (CityCritter c : h.members) {
                if (!c.lamp) continue;
                float yaw = Mth.rotLerp(partial, c.yRotO, c.getYRot()) * Mth.DEG_TO_RAD;
                double x = Mth.lerp(partial, c.xo, c.getX()) - Mth.sin(yaw) * LANTERN_FWD;
                double z = Mth.lerp(partial, c.zo, c.getZ()) + Mth.cos(yaw) * LANTERN_FWD;
                double y = Mth.lerp(partial, c.yo, c.getY());
                if (out.far(x, y, z)) continue;
                float f = out.flicker(c.seed);
                out.halo(x, y + LANTERN_UP, z, CityGlow.LANTERN_HALO * 0.8F, CityGlow.LANTERN_CORE, f);
                // its light on the water round the bow
                out.pool(x, y + 0.02, z, CityGlow.LANTERN_POOL * 0.6F, CityGlow.LANTERN_POOL_A * 0.6F * f);
            }
        }
    };

    // ---- the ground -------------------------------------------------------------------------------------------

    /** The walkable ground at (x, z): the top of the motion-blocking column, below leaves. */
    static double ground(ClientLevel level, double x, double z, Home h) {
        if (h != null && !Double.isNaN(h.groundY) && Math.hypot(x - h.x, z - h.z) < h.radius + 1) {
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            return y > level.getMinY() + 1 ? y : h.groundY;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        if (h != null && y > level.getMinY() + 1) h.groundY = y;
        return y > level.getMinY() + 1 ? y : CityLife.sy + 1;
    }

    /** The top of whatever stands at (x, z): a roof, a ridge, a tower's top. */
    static double top(ClientLevel level, double x, double z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        return y > level.getMinY() + 1 ? y : CityLife.sy + 1;
    }

    private static void sound(ClientLevel level, CityCritter c, SoundEvent ev, float vol, float pitch) {
        if (ev == null) return;
        level.playLocalSound(c.getX(), c.getY(), c.getZ(), ev, SoundSource.NEUTRAL, vol, pitch, false);
    }

    /** How many of each kind are about (the autopilot's status line). */
    static int[] counts() {
        int[] out = new int[6];
        for (Home h : HOMES) if (h.spawned) out[h.kind] += h.members.size();
        out[CityCritter.DOG] = DOGS.size();
        return out;
    }

    static void clear() {
        HOMES.clear();
        BODIES.clear();
        DOGS.clear();
        built = false;
    }
}
