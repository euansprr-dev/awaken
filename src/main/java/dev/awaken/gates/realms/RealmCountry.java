package dev.awaken.gates.realms;

import dev.awaken.gates.GateRecord;
import dev.awaken.gates.run.GateMobs;
import dev.awaken.gates.run.Herds;
import dev.awaken.gates.run.RealmPacks;
import dev.awaken.gates.run.Rooms;
import dev.awaken.gates.world.FloorLayout;
import dev.awaken.gates.world.GateLayout;
import dev.awaken.progression.Progression;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.List;
import java.util.Map;

/**
 * Who lives in an open land (SPEC §50.9 "Who lives there"). A land under a sky is read in cells, and each cell's haunt
 * comes from the seed and the shape of the ground alone: a herd on the most level ground, small creatures, a pack that
 * walks a beat, a lookout on the highest point with its reserve behind it, the big one at home far from the way, or
 * nothing at all. So the same land has the same life for everyone in it, a herd is where a herd would stand, and there
 * are quiet stretches. A haunt fills when someone comes near enough to see it from a distance and nobody is close enough
 * to see it arrive. The run's own places (its legs, its sites, the way in) are left alone.
 *
 * <p>Herds and small creatures are the wide world's own wild animals, at the gate's level, never counted as monsters of
 * the gate: a theme has its kit, and a blueprint's {@code life.herds} names its own instead. Packs, lookouts and the one
 * at home are the theme's monsters, counted and paid as any other. Nothing here is saved but which hostile haunts have
 * been filled ({@link RealmState#woken}); the haunts themselves are found again from the seed.
 */
public final class RealmCountry {
    private RealmCountry() {}

    /** The land is read in cells this wide; each holds one haunt or nothing. */
    static final int CELL = 56;
    /** A haunt fills when someone comes this near and nobody is nearer than UNSEEN, as a den's pack does. */
    private static final int FILL = 92, UNSEEN = 50;
    /** In a hundred cells this many hold nothing, so the country has quiet stretches and nothing comes at even steps. (42 at first: the Cinder Crown's walk met nine herds of rams for one pack.) */
    private static final int QUIET = 50;
    /** A herd's ground is looked at again this long after it was last filled, and filled again if most of the herd is gone. */
    private static final int REFILL = 20 * 60 * 6;
    /** The run's packs come first: nothing of the country's stands while the gate holds more monsters than this, or where a pack already stands. */
    private static final int ROOM = Rooms.GATE_CAP - 12, CROWD = 24;

    public enum Kind { NONE, HERD, SMALL, PROWL, WATCH, LAIR }

    /** One cell's haunt: what, where, a second place (a beat's far stop, a lookout's reserve), its number, and a roll of its own. */
    public record Haunt(Kind kind, int x, int y, int z, int bx, int by, int bz, int site, int roll) {}

    private static final Haunt NOTHING = new Haunt(Kind.NONE, 0, 0, 0, 0, 0, 0, 0, 0);

    /** A theme's country: what stands in herds, the small things, what is in the air, and who keeps watch. Empty means none. */
    private record Kit(String herd, String small, String flier, String lookout) {}

    private static final Kit BARE = new Kit("", "", "", "");
    // No creature here changes the land: nothing that plants, grows or leaves a trail.
    private static final Map<String, Kit> KITS = Map.ofEntries(
            Map.entry("ember_mines", new Kit("crag_ram", "scree_marmot", "peak_kite", "ember_imp")),
            Map.entry("overgrown_temple", new Kit("crested_basilisk", "dart_frog", "lumen_moth", "thorn_archer")),
            Map.entry("frost_monastery", new Kit("frost_stag", "frostfinch", "", "hail_stray")),
            Map.entry("glass_desert", new Kit("dune_ray", "glass_scarab", "", "heat_shimmer")),
            Map.entry("fungal_hollow", new Kit("mossback", "mudskipper", "lumen_moth", "")),
            // the Hive's ordinary monsters are too small to be seen on a rock: its elite, the Door, keeps watch
            Map.entry("hive", new Kit("pollen_grazer", "", "", "hive_guard")),
            Map.entry("bell_crypt", new Kit("", "", "hollow_owl", "crypt_archer")),
            Map.entry("drowned_archive", new Kit("bog_toad", "mudskipper", "bogwick", "salt_stray")),
            Map.entry("clockwork_foundry", new Kit("", "", "", "rivet_gunner")),
            Map.entry("sky_archipelago", new Kit("crag_ram", "", "peak_kite", "sky_raider")),
            Map.entry("abyssal_shrine", new Kit("", "", "", "abyss_watcher")),
            Map.entry("precursor_lab", new Kit("", "", "", "coil_unit")),
            Map.entry("nightmare", new Kit("", "", "", "night_terror")));

    /** Per gate: its cells as they have been asked for, and when each herd ground was last filled. Never saved. */
    private static final Int2ObjectOpenHashMap<Long2ObjectOpenHashMap<Haunt>> HAUNTS = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectOpenHashMap<Long2LongOpenHashMap> FILLED = new Int2ObjectOpenHashMap<>();

    /** A server is starting: gate ids begin again in another world. */
    static void reset() {
        HAUNTS.clear();
        FILLED.clear();
    }

    /** Whether a land has a country at all: open ground under a sky. A crawl, and anything under a roof, has only its run. */
    static boolean has(Land land) {
        return !land.indoors() && !(land instanceof dev.awaken.gates.realms.kit.crawl.CrawlLand);
    }

    /** Asked twice a second for each player in a Realm; the work is done for each once every two seconds. */
    static void tick(ServerLevel level, ServerPlayer p, GateRecord g, RealmPlan plan, RealmRef ref, RealmState st, RealmData data, int lx, int lz) {
        Land land = plan.land();
        if (!has(land) || (p.tickCount / 10 + p.getId()) % 4 != 2) return;
        Long2ObjectOpenHashMap<Haunt> cells = HAUNTS.get(g.id);
        if (cells == null) HAUNTS.put(g.id, cells = new Long2ObjectOpenHashMap<>());
        Kit kit = KITS.getOrDefault(plan.bp().theme(), BARE);
        int pcx = Math.floorDiv(lx, CELL), pcz = Math.floorDiv(lz, CELL);
        for (int cz = pcz - 2; cz <= pcz + 2; cz++) {
            for (int cx = pcx - 2; cx <= pcx + 2; cx++) {
                long key = (long) cx << 32 ^ cz & 0xFFFFFFFFL;
                Haunt h = cells.get(key);
                if (h == null) cells.put(key, h = find(land, plan.seed(), cx, cz));
                if (h.kind() == Kind.NONE) continue;
                int dx = h.x() - lx, dz = h.z() - lz;
                if (dx * dx + dz * dz <= FILL * FILL) fill(level, g, plan, ref, st, data, kit, h, key);
            }
        }
        // what is in the air keeps near whoever is out in the land, as a Realm's own flies do
        if (!kit.flier().isEmpty() && plan.bp().life().ambient().isEmpty()) RealmLife.flies(level, p, g, land, ref, kit.flier(), lx, lz, 3, 9, 34);
    }

    private static void fill(ServerLevel level, GateRecord g, RealmPlan plan, RealmRef ref, RealmState st, RealmData data, Kit kit, Haunt h, long key) {
        BlockPos at = Realms.world(ref.cell(), h.x(), h.y(), h.z());
        if (!dev.awaken.core.api.Ready.loaded(level, at)) return;
        if (h.kind() == Kind.HERD || h.kind() == Kind.SMALL) {
            herd(level, g, plan, ref, kit, h, key, at);
            return;
        }
        String done = "haunt_" + h.site();
        if (st.woken.contains(done)) {
            // the one at home, put down: it leaves a cache where it lived, as a den's pack does
            if (h.kind() == Kind.LAIR && st.woken.contains("hauntl" + h.site()) && !st.woken.contains("hauntd" + h.site()) && near(level, at, 30)
                    && !RealmPacks.alive(level, at, "c" + h.site())) {
                st.woken.add("hauntd" + h.site());
                data.setDirty();
                RealmRun.spoils(level, at);
            }
            return;
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL || GateMobs.inGate(g.id) > ROOM || near(level, at, UNSEEN) || RealmPacks.near(level, at, CROWD) > 0) return;
        int rank = g.plan.rank().ordinal();
        char letter = h.kind() == Kind.LAIR ? 'l' : h.kind() == Kind.WATCH ? 'w' : 'p';
        // each kind has its share of a land; a cell past the share holds nothing
        int share = h.kind() == Kind.LAIR ? (rank >= 3 ? 2 : 1) : h.kind() == Kind.WATCH ? 2 + (rank + 1) / 2 : 4 + rank + rank / 2;
        int made = 0;
        if (tally(st, letter) < share) {
            FloorLayout f = GateLayout.of(g.plan).floor(0, 1);
            BlockPos second = Realms.world(ref.cell(), h.bx(), h.by(), h.bz());
            made = switch (h.kind()) {
                case LAIR -> RealmPacks.lair(level, g, f, at, h.site());
                case WATCH -> dev.awaken.core.api.Ready.loaded(level, second) ? RealmPacks.lookout(level, g, f, at, second, h.site(), kit.lookout()) : 0;
                default -> RealmPacks.prowl(level, g, f, at, h.site(), Herds.of(plan.bp().theme(), h.site(), plan.seed(), false), new long[]{at.asLong(), second.asLong()});
            };
        }
        // said once in the log, so a run that felt empty or crowded can be read back afterwards
        dev.awaken.Awaken.LOG.info("[country] gate {}: {} at {} {} {} (haunt {}): {} stood", g.id, h.kind(), at.getX(), at.getY(), at.getZ(), h.site(), made);
        // nothing could stand there, or the land has had its share: either way the cell is settled and never asked again
        st.woken.add(done);
        if (made > 0) st.woken.add("haunt" + letter + h.site());
        data.setDirty();
    }

    /** A herd on its ground, or a few small creatures: all at once the first time, and again later if most have gone. */
    private static void herd(ServerLevel level, GateRecord g, RealmPlan plan, RealmRef ref, Kit kit, Haunt h, long key, BlockPos at) {
        List<String> named = plan.bp().life().herds();
        String id = h.kind() == Kind.SMALL ? kit.small() : named.isEmpty() ? kit.herd() : named.get(h.roll() % named.size());
        if (id.isEmpty()) return;
        Long2LongOpenHashMap filled = FILLED.get(g.id);
        if (filled == null) {
            FILLED.put(g.id, filled = new Long2LongOpenHashMap());
            filled.defaultReturnValue(Long.MIN_VALUE);
        }
        long now = level.getGameTime(), last = filled.get(key);
        if (last != Long.MIN_VALUE && now - last < REFILL || near(level, at, UNSEEN)) return;
        filled.put(key, now);
        int want = h.kind() == Kind.HERD ? 4 + h.roll() % 4 : 2 + h.roll() % 3, have = RealmLife.count(level, at, id, 40);
        if (have * 2 >= want) return;
        Land land = plan.land();
        int lv = Progression.contentLevel(g.plan.rank()), spread = h.kind() == Kind.HERD ? 6 : 4, made = 0;
        RandomSource r = level.getRandom();
        for (int i = have; i < want; i++) {
            BlockPos spot = RealmLife.open(level, land, ref, h.x() - spread + r.nextInt(spread * 2 + 1), h.z() - spread + r.nextInt(spread * 2 + 1));
            if (spot == null) continue;
            LivingEntity e = RealmLife.creature(level, g, id, spot, lv);
            if (e instanceof Mob m) m.setHomeTo(at, 18); // a herd keeps to its ground
            if (e != null) made++;
        }
        dev.awaken.Awaken.LOG.info("[country] gate {}: {} of {} at {} {} {}: {} of {} came", g.id, h.kind(), id, at.getX(), at.getY(), at.getZ(), made, want - have);
    }

    private static boolean near(ServerLevel level, BlockPos at, int reach) {
        for (ServerPlayer o : level.players()) if (!o.isSpectator() && o.blockPosition().closerThan(at, reach)) return true;
        return false;
    }

    private static int tally(RealmState st, char letter) {
        int n = 0;
        for (String s : st.woken) if (s.length() > 6 && s.charAt(5) == letter && s.startsWith("haunt")) n++;
        return n;
    }

    // ---- finding a cell's haunt: the seed and the ground, nothing else ------------------------------------------------------

    /** What one cell of a land holds. The same answer every time it is asked. */
    static Haunt find(Land land, long seed, int cx, int cz) {
        long h = mix(seed, cx, cz);
        int x0 = cx * CELL, z0 = cz * CELL;
        if (land.edge(x0 + CELL / 2, z0 + CELL / 2) > -20 || h % 100 < QUIET) return NOTHING;
        // nine places in the cell, each a little off its square: the most level, the highest, and a second level one
        int[] xs = new int[9], zs = new int[9], ys = new int[9], slopes = new int[9];
        int flat = -1, high = -1, n = 0;
        for (int i = 0; i < 9; i++) {
            int x = x0 + 5 + i % 3 * 17 + (int) (h >>> 8 + i * 3 & 7), z = z0 + 5 + i / 3 * 17 + (int) (h >>> 35 + i * 3 & 7);
            slopes[i] = -1;
            if (land.edge(x, z) > -12 || !free(land, x, z, 24)) continue;
            int y = land.ground(x, z);
            // under the water is the most level ground a land has, and nothing stands there (Amber Reach's first herd
            // ground was a pool's bed: none of five came; its lookout's reserve was in the honey)
            if (y < land.water()) continue;
            xs[i] = x;
            zs[i] = z;
            ys[i] = y;
            slopes[i] = Math.max(Math.max(Math.abs(land.ground(x + 3, z) - y), Math.abs(land.ground(x - 3, z) - y)),
                    Math.max(Math.abs(land.ground(x, z + 3) - y), Math.abs(land.ground(x, z - 3) - y)));
            n++;
            if (flat < 0 || slopes[i] < slopes[flat]) flat = i;
            if (high < 0 || y > ys[high]) high = i;
        }
        if (n == 0) return NOTHING;
        int second = -1;
        for (int i = 0; i < 9; i++) {
            if (i == flat || slopes[i] < 0 || slopes[i] > 3 || apart(xs, zs, i, flat) < 14 * 14) continue;
            if (second < 0 || slopes[i] < slopes[second]) second = i;
        }
        int k = (int) (h / 100 % 100), level = slopes[flat], a = flat, b = second;
        // mostly animals: a hostile haunt is an event, not the texture of the land (the run's own dens are that)
        Kind kind = k < 44 ? Kind.HERD : k < 72 ? Kind.SMALL : k < 86 ? Kind.PROWL : k < 95 ? Kind.WATCH : Kind.LAIR;
        if (kind == Kind.LAIR && (level > 3 || !free(land, xs[flat], zs[flat], 64))) kind = Kind.HERD;
        if (kind == Kind.WATCH) {
            // a perch is a real high point with level ground in earshot behind it; without one the cell's pack is on the prowl
            if (high != flat && ys[high] - ys[flat] >= 4 && level <= 3 && apart(xs, zs, high, flat) <= 34 * 34) {
                a = high;
                b = flat;
            } else kind = Kind.PROWL;
        }
        if (kind == Kind.PROWL && level > 3) kind = Kind.SMALL;
        if (kind == Kind.HERD && level > 2) kind = Kind.SMALL;
        if (kind == Kind.SMALL && level > 4) return NOTHING;
        if (b < 0) b = a;
        return new Haunt(kind, xs[a], ys[a] + 1, zs[a], xs[b], ys[b] + 1, zs[b], 1000 + ((cx & 0x7F) << 7 | cz & 0x7F), (int) (h / 10000 % 1000));
    }

    /** For tours and checks: the n-th haunt of a kind, counted outward from the way in, or null when the land has fewer. */
    public static Haunt nth(Land land, long seed, Kind kind, int n) {
        Land.Site mouth = land.mouth();
        int half = land.halfSize() / CELL + 1;
        List<Haunt> found = new java.util.ArrayList<>();
        for (int cz = -half; cz <= half; cz++) {
            for (int cx = -half; cx <= half; cx++) {
                Haunt h = find(land, seed, cx, cz);
                if (h.kind() == kind) found.add(h);
            }
        }
        found.sort(java.util.Comparator.comparingDouble(h -> Math.hypot(h.x() - mouth.x(), h.z() - mouth.z())));
        return n < found.size() ? found.get(n) : null;
    }

    private static int apart(int[] xs, int[] zs, int i, int j) {
        int dx = xs[i] - xs[j], dz = zs[i] - zs[j];
        return dx * dx + dz * dz;
    }

    /** Clear of the run and of everything built: every site and every leg keeps its own ground, and the way in keeps more. */
    static boolean free(Land land, int x, int z, int margin) {
        for (Land.Site s : land.sites()) {
            int r = s.radius() + (s.kind() == Land.SiteKind.MOUTH || s.kind() == Land.SiteKind.PORTAL ? Math.max(margin, 56) : margin);
            int dx = s.x() - x, dz = s.z() - z;
            if (dx * dx + dz * dz < r * r) return false;
        }
        for (Land.Leg leg : land.route()) {
            int r = leg.radius() + margin + 2, dx = leg.x() - x, dz = leg.z() - z;
            if (dx * dx + dz * dz < r * r) return false;
        }
        return true;
    }

    private static long mix(long seed, int cx, int cz) {
        long h = seed * 0x9E3779B97F4A7C15L ^ cx * 0x632BE59BD9B4E019L ^ cz * 0xD6E8FEB86659FD93L + 0x2545F4914F6CDD1DL;
        h ^= h >>> 32;
        h *= 0xD6E8FEB86659FD93L;
        h ^= h >>> 29;
        h *= 0x9FB21C651E98DF25L;
        h ^= h >>> 32;
        return h & Long.MAX_VALUE;
    }
}
