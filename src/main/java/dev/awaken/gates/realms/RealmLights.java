package dev.awaken.gates.realms;

import dev.awaken.core.AwakenNet;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.net.GatePayloads;
import dev.awaken.gates.run.GateMemory;
import dev.awaken.gates.run.Runs;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * An open land shows you what to do (SPEC §50.9 "Lights and seals"; Euan, 12 Nov: "nothing guided me... the big building
 * was closed. So how do I open the doors?"). A pillar of light in the land's colour stands over each Warden still
 * standing, seen from anywhere in the land, and the Lord's door carries one seal for each, a disc of the same light. A
 * Warden down, its pillar fades out and its seal goes dark and splits; the last one down, the door opens. The Lord is never lit or named.
 * Under a roof, and in a crawl, the run itself shows the way and nothing here is drawn.
 */
public final class RealmLights {
    private RealmLights() {}

    /** Told again every half minute, in case a client missed it, as a Realm's air is. */
    private static final int AGAIN = 600;
    /** How tall a Warden's pillar stands, and how high over its feet a seal's disc hangs at the door. */
    static final int WARDEN_HEIGHT = 360, SEAL_LIFT = 5;
    /** How long after you arrive the System says what the lights are (the arch's card has had its turn by then), and in how many open lands. */
    private static final int TEACH_AFTER = 20 * 6, TEACH_LANDS = 2;

    /** Per player: the gate last told, and when they arrived there (game time). */
    private static final Map<ServerPlayer, long[]> TOLD = new WeakHashMap<>();
    /** Per land: where its seals stand, one per Warden (land coordinates), and the way the row faces. Found once: a land never changes. */
    private static final Map<Land, Row> SEALS = new WeakHashMap<>();

    /** A land's row of seals, and which way is out from the door (a unit vector, away from the Lord). */
    private record Row(List<int[]> seals, double ox, double oz) {}

    /** A land's colour, by its theme: the light it shows its Wardens in. */
    private static final Map<String, Integer> COLOURS = Map.ofEntries(
            Map.entry("hive", 0xFFF2A93B),
            Map.entry("bell_crypt", 0xFFE9D8A6),
            Map.entry("ember_mines", 0xFFFF7A33),
            Map.entry("fungal_hollow", 0xFF7FE0C4),
            Map.entry("overgrown_temple", 0xFF9BE36B),
            Map.entry("glass_desert", 0xFFA8F0FF),
            Map.entry("frost_monastery", 0xFFBFE4FF),
            Map.entry("drowned_archive", 0xFF5FD3C9),
            Map.entry("sky_archipelago", 0xFFFFF1C4),
            Map.entry("lantern_night_market", 0xFFFFB36B),
            Map.entry("clockwork_foundry", 0xFFFFC870),
            Map.entry("mirror_palace", 0xFFE0E8FF),
            Map.entry("precursor_lab", 0xFF8CF0FF),
            Map.entry("nightmare", 0xFFC6A0FF),
            Map.entry("abyssal_shrine", 0xFF9C8CFF));

    static int colour(String theme) {
        return COLOURS.getOrDefault(theme, 0xFFF4E2B0);
    }

    /** Whether a land shows its Wardens: open ground under a sky, with Wardens and a Lord behind a door. */
    public static boolean shows(Land land) {
        return RealmCountry.has(land) && !land.sites(Land.SiteKind.LORD).isEmpty() && !land.sites(Land.SiteKind.WARDEN).isEmpty();
    }

    /** Called each half second for a player in a Realm: the lights on arrival and every half minute, and the one line that says what they are. */
    static void tick(ServerPlayer p, GateRecord g, RealmPlan plan, RealmRef ref, int t) {
        Land land = plan.land();
        if (!shows(land)) return;
        // the player's own ticks: a world whose clock is stopped still counts them
        long now = p.tickCount;
        long[] told = TOLD.get(p);
        if (told == null || told[0] != g.id) TOLD.put(p, told = new long[]{g.id, now, 0});
        if (told[2] == 0 || (t + p.getId()) % AGAIN < 10) {
            send(p, g, plan, ref);
            if (told[2] == 0) told[2] = 1;
        }
        if (told[2] == 1 && now - told[1] >= TEACH_AFTER) {
            told[2] = 2;
            teach(p, g, land);
        }
    }

    /** The first time or two you're in an open land, the System says what the lights are. Then never again (SPEC §50.9; Euan, 11 Nov: teach, then go quiet). */
    private static void teach(ServerPlayer p, GateRecord g, Land land) {
        RealmState st = RealmData.get(p.level().getServer()).state(g.id);
        if (st.heartOpen || g.bossDead) return;
        GateMemory mem = p.getData(GatesRegistry.MEMORY.get());
        if (mem.openLandsTold >= TEACH_LANDS) return;
        mem.openLandsTold++;
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.GATE, Component.literal("Find the Wardens"))
                .body(Component.literal(teachLine(hasDoor(land))))
                .priority(4).ttl(20 * 10).build());
    }

    /**
     * What is lit for this gate now: a pillar and a whole seal for each Warden still standing, a broken seal for each one
     * down (so the door always shows how many there were and how many are left), nothing once the door is open.
     */
    static List<GatePayloads.RealmLight> lit(MinecraftServer server, GateRecord g, RealmPlan plan, RealmRef ref) {
        Land land = plan.land();
        RealmState st = RealmData.get(server).state(g.id);
        if (!shows(land) || st.heartOpen || g.bossDead) return List.of();
        int colour = colour(plan.bp().theme());
        List<Land.Site> wardens = land.sites(Land.SiteKind.WARDEN);
        List<int[]> seals = seals(land);
        List<GatePayloads.RealmLight> out = new ArrayList<>(wardens.size() * 2);
        for (int i = 0; i < wardens.size(); i++) {
            Land.Site s = wardens.get(i);
            boolean down = st.wardensDown.contains(s.index());
            if (!down) out.add(new GatePayloads.RealmLight(Realms.world(ref.cell(), s.x(), s.y(), s.z()), colour, WARDEN_HEIGHT, false, false));
            if (i < seals.size()) {
                int[] c = seals.get(i);
                out.add(new GatePayloads.RealmLight(Realms.world(ref.cell(), c[0], c[1], c[2]), colour, SEAL_LIFT, true, down));
            }
        }
        return out;
    }

    private static void send(ServerPlayer p, GateRecord g, RealmPlan plan, RealmRef ref) {
        if (!AwakenNet.canReceive(p, GatePayloads.RealmLights.TYPE)) return;
        AwakenNet.send(p, new GatePayloads.RealmLights(g.id, lit(p.level().getServer(), g, plan, ref)));
    }

    /** Everyone in the gate, now: a Warden fell or the door opened. */
    static void sync(ServerLevel level, GateRecord g) {
        RealmPlan plan = Realms.plan(g);
        if (plan == null || !shows(plan.land())) return;
        RealmRef ref = g.realm.orElseThrow();
        for (ServerPlayer p : level.players()) if (Runs.run(p).gateId == g.id) send(p, g, plan, ref);
    }

    /**
     * A Warden is down: its light fades and its seal breaks, everyone in the gate hears a seal break and is told how many are left.
     * {@code left}: Wardens still standing. The last one is said by the door opening.
     */
    static void wardenDown(ServerLevel level, GateRecord g, int left) {
        RealmPlan plan = Realms.plan(g);
        if (plan == null || !shows(plan.land())) return;
        sync(level, g);
        Component line = Component.literal(brokenLine(hasDoor(plan.land()), left));
        for (ServerPlayer p : level.players()) {
            if (Runs.run(p).gateId != g.id) continue;
            Signal.toast(p, line);
            level.playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.AMBIENT, 0.7F, left <= 0 ? 0.7F : 1.1F);
        }
    }

    /**
     * Whether the land's Lord is behind a door (every open land today: the designed ones' and a rolled land's grate across
     * the way in). The seals stand by it and the words say so; a land without one has its seals at the edge of the Lord's
     * place, and the words say that instead (SPEC §50.9).
     */
    static boolean hasDoor(Land land) {
        return !land.change("heart_open").isEmpty();
    }

    /** Where the seals are, in words: by the door, or at the edge of the Lord's place for a land with no door. */
    private static String sealsAt(boolean door) {
        return door ? "on the Lord's door" : "at the edge of the Lord's place";
    }

    /** The System's word on the lights, the first time or two (SPEC §50.9). */
    static String teachLine(boolean door) {
        return "Each pillar of light in the sky stands over a Warden. Beat every one of them and the seals " + sealsAt(door) + " break.";
    }

    /** Said when a Warden falls; {@code left}: Wardens still standing. */
    static String brokenLine(boolean door, int left) {
        String seal = "seal " + sealsAt(door) + " has broken.";
        return left <= 0 ? "The last " + seal + (door ? " The way in is open." : " The way to the Lord is open.")
                : left == 1 ? "A " + seal + " One Warden is left, under the last light."
                : "A " + seal + " " + left + " Wardens are left, each under a light.";
    }

    /** At the shut door, with a count: said once for each count you come back with (SPEC §50.9). */
    static String doorCount(int left) {
        return left == 1 ? "One seal is left on this door. The last Warden is under the light that's still burning."
                : left + " seals are left on this door, one for each Warden still standing. Each is under a pillar of light.";
    }

    /**
     * Where a land's seals stand, one per Warden, land coordinates (x, y of the feet, z): a short row just outside the
     * Lord's door, across the way in. The door is the run of blocks the moment {@code heart_open} clears that lies nearest
     * the way in; the row stands a couple of blocks out from it, on the side away from the Lord. A land with no door (none
     * today: a rolled land's door is the grate across its way in) would have its row where the way comes to the Lord's
     * place. A row that would stand in something (the Barrow's door is a stair up its ship's side, and away from the Lord
     * runs along the hull) faces the Mouth instead, or the nearest open quarter. Empty when the land has no Wardens or no Lord.
     */
    public static List<int[]> seals(Land land) {
        return row(land).seals();
    }

    /**
     * For tours: stand {@code back} blocks out in front of the seals, {@code up} over their feet, facing them; as
     * {@link Land#legView} gives it (x, y, z, yaw, then the x and z to walk toward). Null when the land has no seals.
     */
    public static double[] sealView(Land land, int back) {
        Row r = row(land);
        if (r.seals().isEmpty()) return null;
        double mx = 0, mz = 0;
        int y = Integer.MAX_VALUE;
        for (int[] s : r.seals()) {
            mx += s[0];
            mz += s[2];
            y = Math.min(y, s[1]);
        }
        mx /= r.seals().size();
        mz /= r.seals().size();
        double x = mx + r.ox() * back, z = mz + r.oz() * back;
        double yaw = Math.toDegrees(Math.atan2(-(mx - x), mz - z));
        return new double[]{x, y, z, yaw, mx, mz};
    }

    private static Row row(Land land) {
        synchronized (SEALS) {
            Row r = SEALS.get(land);
            if (r == null) SEALS.put(land, r = findSeals(land));
            return r;
        }
    }

    private static Row findSeals(Land land) {
        List<Land.Edit> open = land.change("heart_open");
        int n = land.sites(Land.SiteKind.WARDEN).size();
        if (n == 0 || land.sites(Land.SiteKind.LORD).isEmpty()) return new Row(List.of(), 0, 1);
        Land.Site mouth = land.mouth(), lord = land.site(Land.SiteKind.LORD);
        double cx, cz;
        int foot;
        if (open.isEmpty()) {
            // no door: where the way comes to the Lord's place, from the leg that leads there (or from the Mouth)
            double fx = mouth.x(), fz = mouth.z();
            Land.Leg lordLeg = null;
            for (Land.Leg l : land.route()) if (l.kind() == Land.LegKind.LORD) lordLeg = l;
            if (lordLeg != null) {
                for (Land.Leg p : land.route()) {
                    if (p.exits().contains(lordLeg.id()) && (p.x() != lord.x() || p.z() != lord.z())) {
                        fx = p.x();
                        fz = p.z();
                        break;
                    }
                }
            }
            double dx = fx - lord.x(), dz = fz - lord.z(), dl = Math.max(1, Math.hypot(dx, dz));
            // at the edge of the Lord's place, but never past the leg it's met from
            double reach = Math.max(0, Math.min(lord.radius(), dl - 8));
            cx = lord.x() + dx / dl * reach;
            cz = lord.z() + dz / dl * reach;
            foot = land.ground((int) Math.round(cx), (int) Math.round(cz)) + 1;
        } else {
            Land.Edit first = null;
            double best = Double.MAX_VALUE;
            for (Land.Edit e : open) {
                double d = Math.hypot(e.x() - mouth.x(), e.z() - mouth.z());
                if (d < best) {
                    best = d;
                    first = e;
                }
            }
            // the door: what opens within a few blocks of that first block
            double sx = 0, sz = 0;
            int count = 0;
            foot = Integer.MAX_VALUE;
            for (Land.Edit e : open) {
                if (Math.abs(e.x() - first.x()) > 9 || Math.abs(e.z() - first.z()) > 9 || Math.abs(e.y() - first.y()) > 12) continue;
                sx += e.x();
                sz += e.z();
                count++;
                foot = Math.min(foot, e.y());
            }
            cx = sx / count;
            cz = sz / count;
        }
        // out from the door, away from the Lord (toward the way in when the Lord stands right behind it); then toward the
        // Mouth, then each quarter nearest that, for a row that would stand in something
        double ox = cx - lord.x(), oz = cz - lord.z(), ol = Math.hypot(ox, oz);
        double mx = mouth.x() - cx, mz = mouth.z() - cz, ml = Math.max(1, Math.hypot(mx, mz));
        List<double[]> outs = new ArrayList<>();
        if (ol >= 1) outs.add(new double[]{ox / ol, oz / ol});
        outs.add(new double[]{mx / ml, mz / ml});
        double[] lead = outs.getFirst();
        List<double[]> quarters = new ArrayList<>(List.of(new double[]{1, 0}, new double[]{-1, 0}, new double[]{0, 1}, new double[]{0, -1}));
        quarters.sort((a, b) -> Double.compare(b[0] * lead[0] + b[1] * lead[1], a[0] * lead[0] + a[1] * lead[1]));
        outs.addAll(quarters);
        Drawn drawn = new Drawn();
        Row firstTry = null;
        for (double[] o : outs) {
            Row r = rowAt(land, cx, cz, foot, o[0], o[1], n);
            if (firstTry == null) firstTry = r;
            boolean clear = true;
            for (int[] seal : r.seals()) if (!drawn.clear(land, seal)) clear = false;
            if (clear) return r;
        }
        return firstTry;
    }

    private static Row rowAt(Land land, double cx, double cz, int foot, double ox, double oz, int n) {
        // far enough apart that each seal is a thing of its own from the road (they were 2.5 apart and read as one glow)
        double side = n <= 3 ? 4 : 3;
        List<int[]> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double across = (i - (n - 1) / 2.0) * side;
            int x = (int) Math.round(cx + ox * 3 - oz * across), z = (int) Math.round(cz + oz * 3 + ox * across);
            int g = land.ground(x, z) + 1;
            out.add(new int[]{x, Math.abs(g - foot) <= 3 ? g : foot, z});
        }
        return new Row(List.copyOf(out), ox, oz);
    }

    /** Whether a seal hangs in the open: nothing solid over its feet up to a block over its disc. */
    public static boolean sealClear(Land land, int[] seal) {
        return new Drawn().clear(land, seal);
    }

    /**
     * The chunk last drawn to look for room. One at a time: a {@link dev.awaken.gates.realms.kit.V} is the thread's one
     * scratch volume, so drawing the next chunk wipes the last.
     */
    private static final class Drawn {
        private long key = Long.MIN_VALUE;
        private dev.awaken.gates.realms.kit.V v;

        boolean clear(Land land, int[] seal) {
            int x = seal[0], z = seal[2];
            long k = (long) (x >> 4) << 32 | (z >> 4) & 0xFFFFFFFFL;
            if (v == null || k != key) {
                v = new dev.awaken.gates.realms.kit.V(x >> 4 << 4, z >> 4 << 4);
                land.paint(v);
                key = k;
            }
            for (int y = seal[1] + 1; y <= seal[1] + SEAL_LIFT + 2; y++) if (dev.awaken.gates.realms.kit.V.holds(v.get(x, y, z))) return false;
            return true;
        }
    }
}
