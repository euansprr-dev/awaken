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
 * standing, seen from anywhere in the land, and the Lord's door carries one short seal of the same light for each. A
 * Warden down, its pillar and one seal fade out; the last one down, the door opens. The Lord is never lit or named.
 * Under a roof, and in a crawl, the run itself shows the way and nothing here is drawn.
 */
public final class RealmLights {
    private RealmLights() {}

    /** Told again every half minute, in case a client missed it, as a Realm's air is. */
    private static final int AGAIN = 600;
    /** How tall a Warden's pillar stands, and a seal at the door. */
    static final int WARDEN_HEIGHT = 360, SEAL_HEIGHT = 11;
    /** How long after you arrive the System says what the lights are (the arch's card has had its turn by then), and in how many open lands. */
    private static final int TEACH_AFTER = 20 * 6, TEACH_LANDS = 2;

    /** Per player: the gate last told, and when they arrived there (game time). */
    private static final Map<ServerPlayer, long[]> TOLD = new WeakHashMap<>();
    /** Per land: where its seals stand, one per Warden (land coordinates). A land never changes, so this is found once. */
    private static final Map<Land, List<int[]>> SEALS = new WeakHashMap<>();

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
        long now = p.level().getGameTime();
        long[] told = TOLD.get(p);
        if (told == null || told[0] != g.id) TOLD.put(p, told = new long[]{g.id, now, 0});
        if (told[2] == 0 || (t + p.getId()) % AGAIN < 10) {
            send(p, g, plan, ref);
            if (told[2] == 0) told[2] = 1;
        }
        if (told[2] == 1 && now - told[1] >= TEACH_AFTER) {
            told[2] = 2;
            teach(p, g);
        }
    }

    /** The first time or two you're in an open land, the System says what the lights are. Then never again (SPEC §50.9; Euan, 11 Nov: teach, then go quiet). */
    private static void teach(ServerPlayer p, GateRecord g) {
        RealmState st = RealmData.get(p.level().getServer()).state(g.id);
        if (st.heartOpen || g.bossDead) return;
        GateMemory mem = p.getData(GatesRegistry.MEMORY.get());
        if (mem.openLandsTold >= TEACH_LANDS) return;
        mem.openLandsTold++;
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.GATE, Component.literal("Find the Wardens"))
                .body(Component.literal("Each pillar of light in the sky stands over a Warden. Beat every one of them and the seals on the Lord's door break."))
                .priority(4).ttl(20 * 10).build());
    }

    /** What is lit for this gate now: a pillar and a seal for each Warden still standing, nothing once the door is open. */
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
            if (st.wardensDown.contains(s.index())) continue;
            out.add(new GatePayloads.RealmLight(Realms.world(ref.cell(), s.x(), s.y(), s.z()), colour, WARDEN_HEIGHT, false));
            if (i < seals.size()) {
                int[] c = seals.get(i);
                out.add(new GatePayloads.RealmLight(Realms.world(ref.cell(), c[0], c[1], c[2]), colour, SEAL_HEIGHT, true));
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
     * A Warden is down: its light and one seal fade, everyone in the gate hears a seal break and is told how many are left.
     * {@code left}: Wardens still standing. The last one is said by the door opening.
     */
    static void wardenDown(ServerLevel level, GateRecord g, int left) {
        RealmPlan plan = Realms.plan(g);
        if (plan == null || !shows(plan.land())) return;
        sync(level, g);
        Component line = Component.literal(left <= 0 ? "The last seal on the Lord's door has broken. The way in is open."
                : left == 1 ? "A seal on the Lord's door has broken. One Warden is left, under the last light."
                : "A seal on the Lord's door has broken. " + left + " Wardens are left, each under a light.");
        for (ServerPlayer p : level.players()) {
            if (Runs.run(p).gateId != g.id) continue;
            Signal.toast(p, line);
            level.playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.AMBIENT, 0.7F, left <= 0 ? 0.7F : 1.1F);
        }
    }

    /** At the shut door, with a count: said once for each count you come back with (SPEC §50.9). */
    static String doorCount(int left) {
        return left == 1 ? "One seal is left on this door. The last Warden is under the light that's still burning."
                : left + " seals are left on this door, one for each Warden still standing. Each is under a pillar of light.";
    }

    /**
     * Where a land's seals stand, one per Warden, land coordinates (x, y of the feet, z): a short row just outside the
     * Lord's door, across the way in. The door is the run of blocks the moment {@code heart_open} clears that lies nearest
     * the way in; the row stands a couple of blocks out from it, on the side away from the Lord. Empty when the land has no door.
     */
    public static List<int[]> seals(Land land) {
        synchronized (SEALS) {
            List<int[]> s = SEALS.get(land);
            if (s == null) SEALS.put(land, s = findSeals(land));
            return s;
        }
    }

    private static List<int[]> findSeals(Land land) {
        List<Land.Edit> open = land.change("heart_open");
        int n = land.sites(Land.SiteKind.WARDEN).size();
        if (open.isEmpty() || n == 0 || land.sites(Land.SiteKind.LORD).isEmpty()) return List.of();
        Land.Site mouth = land.mouth(), lord = land.site(Land.SiteKind.LORD);
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
        int count = 0, foot = Integer.MAX_VALUE;
        for (Land.Edit e : open) {
            if (Math.abs(e.x() - first.x()) > 9 || Math.abs(e.z() - first.z()) > 9 || Math.abs(e.y() - first.y()) > 12) continue;
            sx += e.x();
            sz += e.z();
            count++;
            foot = Math.min(foot, e.y());
        }
        double cx = sx / count, cz = sz / count;
        // out from the door, away from the Lord (toward the way in when the Lord stands right behind it)
        double ox = cx - lord.x(), oz = cz - lord.z(), ol = Math.hypot(ox, oz);
        if (ol < 1) {
            ox = mouth.x() - cx;
            oz = mouth.z() - cz;
            ol = Math.max(1, Math.hypot(ox, oz));
        }
        ox /= ol;
        oz /= ol;
        double side = 2.5;
        List<int[]> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double across = (i - (n - 1) / 2.0) * side;
            int x = (int) Math.round(cx + ox * 3 - oz * across), z = (int) Math.round(cz + oz * 3 + ox * across);
            int g = land.ground(x, z) + 1;
            out.add(new int[]{x, Math.abs(g - foot) <= 3 ? g : foot, z});
        }
        return List.copyOf(out);
    }
}
