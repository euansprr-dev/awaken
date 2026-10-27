package dev.awaken.village;

import dev.awaken.Awaken;
import dev.awaken.citizens.Lines;
import dev.awaken.citizens.api.Settlement;
import dev.awaken.citizens.api.SettlementSpec;
import dev.awaken.citizens.api.Settlements;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.RenownHooks;
import dev.awaken.village.plan.VillagePlan;
import dev.awaken.village.world.GenTerrain;
import dev.awaken.village.world.Plans;
import dev.awaken.village.world.SettlementGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The settlements at work on this server (SPEC §28.6): as a player comes within reach of one, its plan is made (on a
 * worker thread: the generator's estimate is slow) and its people start on the people engine (W0's
 * {@code Settlements}), which sleeps and wakes them with the players' coming and going. Holds what a village knows of
 * players (standing), and answers when a player speaks to someone, breaks part of a house or reads the notice board.
 */
public final class VillageService {
    private VillageService() {}

    public static final net.minecraft.resources.Identifier FLAG = Awaken.id("awakened_villages");
    /** How near a player brings a settlement to life (its radius plus this; the engine wakes it at + 192). */
    static final int START = 180;
    /** Past this (plus its radius) from every player a settlement stops (its saved state stays). */
    static final int STOP = 1400;

    /** A settlement running now: its plan, its people, its record. */
    public record Live(String key, VillagePlan plan, Settlement settlement, VillageState.Village rec) {}

    static final Map<String, Live> LIVE = new HashMap<>();
    private static final Map<Integer, Live> BY_ID = new HashMap<>();
    private static final Set<String> PLANNING = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Settlements whose ground isn't painted where the plan says (a world older than the pack): never started. */
    private static final Set<String> ABSENT = new HashSet<>();
    private static @Nullable ExecutorService planner;

    /** The world asked for AWAKEN's settlements (the built-in pack's feature flag): new worlds only. */
    public static boolean enabled(MinecraftServer server) {
        FeatureFlagSet set = FeatureFlags.REGISTRY.fromNames(Set.of(FLAG), id -> {});
        return !set.isEmpty() && set.isSubsetOf(server.getWorldData().enabledFeatures());
    }

    public static long today(MinecraftServer server) {
        return PlayDays.current(server);
    }

    static @Nullable Live live(Settlement s) {
        return BY_ID.get(s.id());
    }

    public static @Nullable Live at(Level level, BlockPos pos) {
        for (Live l : LIVE.values()) if (l.settlement().inside(pos)) return l;
        return null;
    }

    // ---- start and stop ---------------------------------------------------------------------------------------------

    static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 7 || !enabled(server)) return;
        ServerLevel level = server.overworld();
        long seed = level.getSeed();
        int[] centre = SettlementGrid.centre();
        // GameTests register their settlements themselves: none is planned round a test's player by itself (one planned
        // so claimed the ground of the tests near it, and an Ember or a sister Anchor was refused "inside a village")
        boolean quiet = server instanceof net.minecraft.gametest.framework.GameTestServer;
        for (ServerPlayer p : quiet ? List.<ServerPlayer>of() : level.players()) {
            // (SPEC §28.9) a town or wayhouse whose site isn't found yet joins a later pass: the server thread never plans
            for (SettlementGrid.Anchor a : SettlementGrid.near(seed, p.getBlockX(), p.getBlockZ(), 420, centre, false)) {
                if (LIVE.containsKey(a.key()) || ABSENT.contains(a.key())) continue;
                if (!Plans.known(a.key())) {
                    plan(level, a);
                    continue;
                }
                VillagePlan plan = Plans.cached(a.key());
                if (plan == null) continue;
                double d = Math.hypot(p.getX() - plan.ox, p.getZ() - plan.oz);
                if (d < plan.radius() + START) start(level, plan);
            }
        }
        // settlements far from everyone stop (their records stay in the world's file and the engine's)
        if (server.getTickCount() % 200 == 7) {
            for (Live l : List.copyOf(LIVE.values())) {
                boolean near = false;
                for (ServerPlayer p : level.players())
                    near |= Math.hypot(p.getX() - l.plan().ox, p.getZ() - l.plan().oz) < l.plan().radius() + STOP;
                if (!near) stop(l);
            }
            Inn.expire(server);
        }
    }

    /** Plans a settlement on a worker thread (the land read is slow); the next tick starts it. */
    private static void plan(ServerLevel level, SettlementGrid.Anchor a) {
        if (!PLANNING.add(a.key())) return;
        if (planner == null) planner = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Awaken village planner");
            t.setDaemon(true);
            return t;
        });
        var src = level.getChunkSource();
        planner.submit(() -> {
            try {
                Plans.of(a, GenTerrain.of(src.getGenerator(), level, src.randomState()));
            } catch (RuntimeException e) {
                Awaken.LOG.error("[village] planning {} failed", a.key(), e);
            } finally {
                PLANNING.remove(a.key());
            }
        });
    }

    /** Its people start (or its saved ones go on), once its ground is seen to be painted. */
    static @Nullable Live start(ServerLevel level, VillagePlan plan) {
        BlockPos well = plan.ground(plan.well[0], plan.well[1]);
        if (!level.isLoaded(well)) return null;
        if (!level.getBlockState(well).is(Blocks.WATER)) {
            // a world made before the settlement could generate here (or a plan changed under it): no people
            ABSENT.add(plan.key);
            Awaken.LOG.info("[village] {} isn't painted at {} (an older world): left empty", plan.name, well.toShortString());
            return null;
        }
        return register(level, plan);
    }

    /** Registers a settlement's people with the engine (tests and commands call it directly). */
    public static @Nullable Live register(ServerLevel level, VillagePlan plan) {
        Live had = LIVE.get(plan.key);
        if (had != null) return had;
        long t0 = System.nanoTime();
        // (SPEC §28.7) with the families of the houses it has grown
        var mem = dev.awaken.village.trouble.TroubleState.get(level.getServer()).peek(plan.key);
        var source = VillageRoster.of(plan, level.getSeed(), mem == null ? java.util.List.of() : mem.growth().houses());
        SettlementSpec spec = new SettlementSpec("village:" + plan.key, plan.name, new BlockPos(plan.ox, plan.baseY, plan.oz), plan.radius(), source, false);
        Settlement s = Settlements.register(level, spec).orElse(null);
        if (s == null) {
            ABSENT.add(plan.key);
            return null;
        }
        VillageState st = VillageState.get(level.getServer());
        VillageState.Village rec = st.village(plan.key, plan.name, plan.kind.ordinal(), plan.family, BlockPos.asLong(plan.ox, plan.baseY, plan.oz),
                plan.radius(), today(level.getServer()));
        Live l = new Live(plan.key, plan, s, rec);
        LIVE.put(plan.key, l);
        BY_ID.put(s.id(), l);
        Awaken.LOG.info(String.format(Locale.ROOT, "[village] %s's %d people start (%s, settlement %d) in %.1f ms", plan.name, s.people(), plan.key,
                s.id(), (System.nanoTime() - t0) / 1e6));
        return l;
    }

    public static void stop(Live l) {
        LIVE.remove(l.key());
        BY_ID.remove(l.settlement().id());
        Settlements.unregister(l.settlement().id());
    }

    static void serverStopped() {
        LIVE.clear();
        BY_ID.clear();
        ABSENT.clear();
        PLANNING.clear();
    }

    // ---- standing ---------------------------------------------------------------------------------------------------

    /** A player's standing at a settlement: 0 stranger, 1 known, 2 friend, 3 kin (its record; the people's own recognition lifts a stranger). */
    public static int tier(ServerPlayer p, Live l) {
        int t = VillageState.tier(l.rec().points(p.getUUID()));
        if (t == 0 && l.settlement().standing(p) >= RenownHooks.KNOWN) t = 1;
        return t;
    }

    public static String standingName(ServerPlayer p, Live l) {
        return VillageState.STANDINGS[tier(p, l)];
    }

    /** Standing earned (or lost); the village's people know the player better as it rises. */
    public static void standing(ServerPlayer p, Live l, int points, String why) {
        VillageState st = VillageState.get(p.level().getServer());
        int before = tier(p, l);
        int now = Math.clamp(l.rec().points(p.getUUID()) + points, -50, 200);
        l.rec().standing.put(p.getUUID(), now);
        st.setDirty();
        if (points > 0) l.settlement().acquaint(p, Math.max(2, points * 2), Math.min(100, 20 + now));
        int after = tier(p, l);
        if (after > before) {
            String name = VillageState.STANDINGS[after];
            p.sendSystemMessage(Component.translatable("awaken.village.standing." + name, l.plan().name));
            history(p.level().getServer(), l, p.getName().getString() + " became " + (after == 1 ? "known" : after == 2 ? "a friend" : "kin") + " here.");
        }
        Awaken.LOG.info("[village] {} {} standing {} at {} ({}): {}", p.getName().getString(), points >= 0 ? "+" : "", points, l.plan().name, why, now);
    }

    public static void history(MinecraftServer server, Live l, String text) {
        l.rec().history.add(new VillageState.History(today(server), text));
        while (l.rec().history.size() > 32) l.rec().history.removeFirst();
        VillageState.get(server).setDirty();
    }

    // ---- talking, trading ---------------------------------------------------------------------------------------------

    static final Set<String> TRADERS = Set.of("farmer", "shepherd", "woodcutter", "miner", "fisher", "hunter", "smith", "miller", "tavernkeeper",
            "healer", "scribe", "carter", "stallkeeper", "assayer");

    /** Someone a player spoke to: a trader opens their goods, the elder tells the news; anyone else greets them. */
    static boolean talk(ServerPlayer p, Settlement s, int rec) {
        return talk(p, s, rec, false);
    }

    /** {@code remote}: opened by a command from wherever the player stands (the trader may be across the green). */
    static boolean talk(ServerPlayer p, Settlement s, int rec, boolean remote) {
        Live l = live(s);
        if (l == null) return false;
        String job = s.job(rec);
        long seed = rec * 7919L + p.getUUID().getLeastSignificantBits() + p.level().getGameTime() / 200;
        int tod = (int) Math.floorMod(p.level().getOverworldClockTime(), 24000L);
        Lines lines = Lines.get();
        int tier = tier(p, l);
        String st = VillageState.STANDINGS[tier];
        if (job.equals("elder")) {
            say(p, l, rec, lines.pick(seed, tod, "village.elder.news", "village.news.village", "village.news.region"), 120);
            return true;
        }
        if (!TRADERS.contains(job)) return false;
        List<Trade.Deal> deals = Trade.deals(job, l.plan().family, l.plan().seed ^ rec, today(p.level().getServer()), (ServerLevel) p.level());
        if (deals == null || deals.isEmpty()) return false;
        // (SPEC §28.9) the day a caravan came in, the stalls carry what it brought from where it set out
        int came = l.rec().count(today(p.level().getServer()), "caravan_land");
        if (job.equals("stallkeeper") && came > 0) {
            deals = new ArrayList<>(deals);
            Trade.specialty(deals, came - 1);
        }
        // the smith's rack shows two patterns of the land's rank, drawn by the village, never one this fighter knows (docs/arms-progression PLAN §3.4)
        if (job.equals("smith")) {
            int land = dev.awaken.living.api.Regions.levelAt((ServerLevel) p.level(), l.plan().ox, l.plan().oz);
            deals = new ArrayList<>(deals);
            for (var o : dev.awaken.core.api.ArmsApi.villagePatterns(p, l.plan().seed, land)) {
                deals.add(new Trade.Deal("pattern_" + o.style(), net.minecraft.world.item.Items.EMERALD, o.emeralds(), o.blueprint(), false, 1));
            }
        }
        // a mining town's store has no ore while its mine is shut
        if (job.equals("assayer") && dev.awaken.village.trouble.DeepMine.shut(p.level().getServer(), l.key())) {
            deals = new ArrayList<>(deals.stream().filter(d -> d.buys() || d.id().equals("lantern")).toList());
        }
        String opener = switch (job) {
            case "tavernkeeper" -> lines.pick(seed, tod, "village.inn.offer");
            case "scribe" -> lines.pick(seed, tod, "village.scribe.open");
            default -> lines.pick(seed, tod, "village.trade.open." + st);
        };
        say(p, l, rec, opener, 90);
        String name = s.personName(rec), standing = Component.translatable("awaken.village.standing_short." + st).getString();
        String title = name + ", " + job.replace('_', ' ') + "  ·  " + standing;
        // the screen's title fits over its right half: a long name keeps only the given name
        if (title.length() > 28) title = name.split(" ")[0] + ", " + job.replace('_', ' ') + " · " + standing;
        Trade.Shop shop = new Trade.Shop(p, l, rec, deals, tier);
        shop.remote = remote;
        shop.open(Component.literal(title));
        return true;
    }

    static void say(ServerPlayer p, Live l, int rec, String line, int ticks) {
        if (line.isEmpty()) return;
        l.settlement().say(p, rec, fill(l, p, Lines.fill(line, p.getName().getString(), l.plan().name, "", hour(p))), ticks);
    }

    public static int hour(ServerPlayer p) {
        int tod = (int) Math.floorMod(p.level().getOverworldClockTime(), 24000L);
        int h = (6 + tod / 1000) % 24;
        return h % 12 == 0 ? 12 : h % 12;
    }

    /** A trade went through: counted for today, standing (a few a day), the village's prosperity, the inn's room. */
    static void traded(ServerPlayer p, Live l, int rec, Trade.Deal d) {
        MinecraftServer server = p.level().getServer();
        long today = today(server);
        VillageState.Village v = l.rec();
        v.add(today, p.getUUID() + "|" + l.key() + "|" + d.id(), 1);
        String sk = p.getUUID() + "|standing";
        if (v.count(today, sk) < 4) {
            v.add(today, sk, 1);
            standing(p, l, 1, "trade " + d.id());
        }
        if (v.count(today, "trades") % 5 == 4) v.prosperity = Math.min(100, v.prosperity + 1);
        v.add(today, "trades", 1);
        VillageState.get(server).setDirty();
        if (d.id().startsWith("pattern_")) dev.awaken.core.api.ArmsApi.villageBought(p, d.id().substring("pattern_".length()));
        if (d.id().equals(Inn.ROOM)) Inn.grant(p, l, rec);
        else if (Math.floorMod(v.count(today, "trades"), 3) == 1) {
            int tod = (int) Math.floorMod(p.level().getOverworldClockTime(), 24000L);
            say(p, l, rec, Lines.get().pick(p.level().getGameTime(), tod, "village.trade.done"), 70);
        }
    }

    /** (SPEC §28.9) A caravan came in: today its stalls sell the goods of the land it set out from. */
    public static void caravan(MinecraftServer server, Live l, String from) {
        long today = today(server);
        int fam = Math.floorMod(from.hashCode(), dev.awaken.city.VillageStyles.FAMILIES.length);
        if (fam == l.plan().family) fam = (fam + 3) % dev.awaken.city.VillageStyles.FAMILIES.length;
        if (l.rec().count(today, "caravan_land") == 0) l.rec().add(today, "caravan_land", fam + 1);
        VillageState.get(server).setDirty();
    }

    // ---- the voice: standing names and place names in lines ----------------------------------------------------------

    public static String fill(Live l, ServerPlayer p, String line) {
        if (line.indexOf('{') < 0) return line;
        MinecraftServer server = p.level().getServer();
        String region = "these parts";
        try {
            region = dev.awaken.living.region.RegionService.map(server).regionAt(l.plan().ox, l.plan().oz).name();
        } catch (RuntimeException e) {
            // no region map on this server: the plain words
        }
        String elder = "the elder";
        for (int i = 0; i < l.settlement().people(); i++) {
            if (l.settlement().job(i).equals("elder")) {
                elder = l.settlement().personName(i).split(" ")[0];
                break;
            }
        }
        String gate = "the hills";
        var gates = dev.awaken.core.api.GateHooks.near((ServerLevel) p.level(), BlockPos.containing(l.plan().ox, l.plan().baseY, l.plan().oz), 1200);
        if (!gates.isEmpty()) {
            BlockPos g = gates.getFirst().pos();
            gate = compass(g.getX() - l.plan().ox, g.getZ() - l.plan().oz);
        }
        return line.replace("{village}", l.plan().name).replace("{region}", region).replace("{elder}", elder).replace("{gate}", gate)
                .replace("{landmark}", "the old stones").replace("{name}", p.getName().getString());
    }

    static String compass(double dx, double dz) {
        String[] dirs = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        double deg = (Math.toDegrees(Math.atan2(dx, -dz)) + 360 + 22.5) % 360;
        return dirs[(int) (deg / 45) % 8];
    }

    static final class Voice implements Settlements.Voice {
        @Override
        public String standing(Settlement s, ServerPlayer p) {
            Live l = live(s);
            return l == null ? "stranger" : standingName(p, l);
        }

        @Override
        public String fill(Settlement s, ServerPlayer p, String line) {
            Live l = live(s);
            return l == null ? line : VillageService.fill(l, p, line);
        }
    }

    // ---- what players do to a village ------------------------------------------------------------------------------------

    /**
     * (SPEC §28.7) A trouble's damage (a roof burned, a house charred): kept with the block that stood there for the
     * Rebuild, wherever on the settlement's ground or its eaves it is. No one's blamed.
     */
    public static void damaged(Live l, BlockPos pos, net.minecraft.world.level.block.state.BlockState was, long day) {
        VillageState.Village v = l.rec();
        if (v.damage.size() >= 4096 || was.isAir()) return;
        long key = pos.asLong();
        for (VillageState.Damage d : v.damage) if (d.pos() == key) return; // the first block that stood there is the one to put back
        v.damage.add(new VillageState.Damage(key, was, day, java.util.Optional.empty()));
    }

    /** Part of a house broken (or blown up): the block that stood there is kept for the Rebuild (W4); seen, it costs standing. */
    public static void broke(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState was, @Nullable ServerPlayer by) {
        Live l = at(level, pos);
        if (l == null || !l.plan().owns(pos.getX(), pos.getZ()) || was.isAir()) return;
        VillageState.Village v = l.rec();
        long today = today(level.getServer());
        if (v.damage.size() < 2048) {
            v.damage.add(new VillageState.Damage(pos.asLong(), was, today, java.util.Optional.ofNullable(by == null ? null : by.getUUID())));
            v.mood = Math.max(0, v.mood - (v.damage.size() % 8 == 0 ? 1 : 0));
            VillageState.get(level.getServer()).setDirty();
        }
        if (by == null || by.isCreative()) return;
        RenownHooks.witness(by, Awaken.id("village_broke"), pos, -1);
        String k = by.getUUID() + "|broke";
        if (v.count(today, k) < 6) {
            v.add(today, k, 1);
            if (v.count(today, k) % 2 == 0) standing(by, l, -1, "broke " + was.getBlock().getDescriptionId());
        }
    }

    // ---- for the API ----------------------------------------------------------------------------------------------------

    /** A running settlement by its key, or null (W4's troubles). */
    public static @Nullable Live live(String key) {
        return LIVE.get(key);
    }

    public static List<Live> all() {
        return new ArrayList<>(LIVE.values());
    }

    static VillageState.@Nullable Village record(MinecraftServer server, String key) {
        return VillageState.get(server).villages.get(key);
    }

    static Map<UUID, Integer> standings(Live l) {
        return Map.copyOf(l.rec().standing);
    }
}
