package dev.awaken.gates;

import dev.awaken.core.AwakenConfig;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Layout;
import dev.awaken.gates.content.Modifier;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.content.Twist;
import dev.awaken.gates.run.GateMemory;
import dev.awaken.gates.world.Hash;
import dev.awaken.progression.Curve;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;

/**
 * The pacing Director's gate upkeep (SPEC sections 2 and 4.10, plan generator). Every 1000 ticks per online awakened
 * player: keep 1–4 gates within 300–1500 blocks, rolling new ones with the plan's rules. Every decision is logged for
 * {@code /awaken why} and stored in the gate's trace for {@code /awaken gate trace}.
 */
public final class Director {
    private Director() {}

    public static final int PERIOD = 1000;
    public static final int MIN_DIST = 300, MAX_DIST = 1500;
    static final String[] DIRS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    /** The outcome of one roll: the plan, its trace, and how many play-days until it breaks. */
    public record Roll(GatePlan plan, List<String> trace, int breaksInDays) {}

    // ---- the roll (pure: same seed, rank and memory → same result) ------------------------------------------------

    /** Rolls a gate for a player and updates their novelty memory exactly as the plan's generator does. */
    public static Roll roll(long seed, Rank playerRank, GateMemory mem, Rank forceRank, Theme forceTheme) {
        SplittableRandom rng = new SplittableRandom(seed);
        List<String> tr = new ArrayList<>();
        // 1 rank: 70% yours, 20% one below, 10% one above
        double r = rng.nextDouble();
        Rank g = playerRank;
        String why;
        if (r < 0.7) why = "your rank (70%)";
        else if (r < 0.9) {
            g = playerRank.below();
            why = playerRank != Rank.E ? "one below (20%)" : "your rank (nothing below E)";
        } else {
            g = playerRank.above();
            why = playerRank != Rank.SOVEREIGN ? "one above (10%)" : "your rank (nothing above Sovereign)";
        }
        if (forceRank != null) {
            g = forceRank;
            why = "forced";
        }
        tr.add(String.format("rank      roll %.2f → %s → %s", r, why, g.label));

        // 2 theme: fits the rank, not one of the last 3 (relax the memory only if nothing fits)
        List<Theme> eligible = new ArrayList<>();
        int keep = 3;
        while (eligible.isEmpty() && keep >= 0) {
            List<String> recent = mem.lastThemes.subList(0, Math.min(keep, mem.lastThemes.size()));
            for (Theme t : Theme.values()) if (t.fits(g) && !recent.contains(t.id)) eligible.add(t);
            keep--;
        }
        Theme theme = pickTheme(eligible, rng.nextDouble());
        if (forceTheme != null) theme = forceTheme;
        tr.add("theme     " + eligible.size() + " fit rank " + g.label
                + (mem.lastThemes.isEmpty() ? "" : ", skipped last " + mem.lastThemes.size() + " (" + String.join(", ", names(mem.lastThemes)) + ")")
                + " → " + theme.displayName + (forceTheme != null ? " (forced)" : ""));

        // 3 layout: not one of the last 2
        List<Layout> lays = new ArrayList<>();
        for (Layout l : Layout.values()) if (!mem.lastLayouts.contains(l.name())) lays.add(l);
        Layout layout = lays.get((int) (rng.nextDouble() * lays.size()));
        tr.add("layout    " + lays.size() + " allowed" + (mem.lastLayouts.isEmpty() ? "" : ", skipped " + String.join(", ", layoutNames(mem.lastLayouts)))
                + " → " + layout.displayName);

        // 4 modifiers: within the rank's budget, no duplicates; friendly gates have none
        List<Modifier> mods = new ArrayList<>();
        if (theme.friendly) tr.add("modifiers friendly gate → none");
        else {
            int budget = Curve.modifierBudget(g);
            int want = Curve.modifierMax(g) - (rng.nextDouble() < 0.25 ? 1 : 0);
            List<Modifier> pool = new ArrayList<>(List.of(Modifier.values()));
            int spent = 0;
            while (mods.size() < want && !pool.isEmpty()) {
                Modifier m = pool.remove((int) (rng.nextDouble() * pool.size()));
                if (spent + m.cost <= budget) {
                    mods.add(m);
                    spent += m.cost;
                }
            }
            StringBuilder sb = new StringBuilder();
            for (Modifier m : mods) sb.append(sb.isEmpty() ? "" : ", ").append(m.displayName).append(" (").append(m.cost).append(")");
            tr.add("modifiers budget " + budget + ", up to " + want + " → " + (mods.isEmpty() ? "none" : sb) + ", spent " + spent);
        }

        // 5 twist: 1 in 8, guaranteed on the 12th gate without one
        Twist twist = Twist.NONE;
        if (mem.sinceTwist >= 11) {
            twist = pickTwist(rng.nextDouble());
            tr.add("twist     pity timer: 11 gates without one → forced → " + twist.displayName);
        } else {
            double tw = rng.nextDouble();
            if (tw < 0.125) {
                twist = pickTwist(rng.nextDouble());
                tr.add(String.format("twist     roll %.3f < 0.125 → %s", tw, twist.displayName));
            } else tr.add(String.format("twist     roll %.3f ≥ 0.125 · %d since the last → none", tw, mem.sinceTwist + 1));
        }

        // 6 shape: floors and the break timer (play-days)
        int floors = Curve.floorsFor(g, rng.nextDouble() < 0.5);
        int days = Curve.breakDays(g, rng.nextDouble() < 0.5);
        tr.add("shape     " + floors + " floors, breaks in " + days + " play-day" + (days > 1 ? "s" : ""));

        // the second gate / inner gate: another theme of the same rank, never the main one
        List<Theme> others = new ArrayList<>();
        for (Theme t : Theme.values()) if (t.fits(g) && t != theme && !t.friendly) others.add(t);
        Theme annex = others.isEmpty() ? theme : others.get((int) (rng.nextDouble() * others.size()));
        int annexRule = (int) (rng.nextDouble() * 3);
        long planSeed = Hash.mix(seed ^ 0x51ED270B27C3A5L);
        GatePlan plan = new GatePlan(planSeed, GatePlan.CONTENT_VERSION, g, theme, layout, mods, twist, floors, theme.bossId(),
                annex, annexRule);
        List<String> combos = plan.combos();
        tr.add("forces    " + (combos.isEmpty() ? "no special interactions" : String.join(" · ", combos)));

        mem.rememberTheme(theme.id);
        mem.rememberLayout(layout.name());
        mem.sinceTwist = twist == Twist.NONE ? mem.sinceTwist + 1 : 0;
        mem.gatesRolled++;
        return new Roll(plan, tr, days);
    }

    private static Theme pickTheme(List<Theme> eligible, double r) {
        double tot = 0;
        for (Theme t : eligible) tot += t.weight();
        double x = r * tot;
        for (Theme t : eligible) {
            x -= t.weight();
            if (x <= 0) return t;
        }
        return eligible.get(eligible.size() - 1);
    }

    private static Twist pickTwist(double r) {
        int tot = 0;
        for (Twist t : Twist.values()) tot += t.weight;
        double x = r * tot;
        for (Twist t : Twist.values()) {
            if (t.weight == 0) continue;
            x -= t.weight;
            if (x <= 0) return t;
        }
        return Twist.GATE_WITHIN;
    }

    private static List<String> names(List<String> themeIds) {
        List<String> out = new ArrayList<>();
        for (String id : themeIds) out.add(Theme.byId(id).displayName);
        return out;
    }

    private static List<String> layoutNames(List<String> ids) {
        List<String> out = new ArrayList<>();
        for (String id : ids) {
            Layout l = Layout.parse(id);
            out.add(l == null ? id : l.displayName);
        }
        return out;
    }

    // ---- upkeep -----------------------------------------------------------------------------------------------------

    static void tick(MinecraftServer server) {
        if (!AwakenConfig.GATES.get()) return;
        int t = server.getTickCount();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (Math.floorMod(t + p.getUUID().hashCode(), PERIOD) == 0) upkeep(server, p, false);
        }
    }

    /** One Director pass for one player. {@code force} skips the chance roll (commands). Returns the new gate or null. */
    public static GateRecord upkeep(MinecraftServer server, ServerPlayer p, boolean force) {
        if (!Progression.profile(p).awakened() && !force) return null;
        if (p.level().dimension() != Level.OVERWORLD) return null;
        if (!force && p.getData(GatesRegistry.MEMORY.get()).gatesRolled == 0) return firstGate(server, p);
        GateRegistry reg = GateRegistry.get(server);
        // the cap is per owner (SPEC "Together"): a friend's gates nearby don't use up yours, and yours aren't theirs
        int standing = 0;
        for (GateRecord g : reg.near(p.blockPosition(), MAX_DIST, true)) {
            if (!g.scripted && !g.wandering() && ownedBy(g, p)) standing++;
        }
        double chance = standing == 0 ? 1.0 : standing < 2 ? 0.3 : standing < 4 ? 0.08 : 0.0;
        double r = p.getRandom().nextDouble();
        if (!force && r >= chance) {
            if (standing >= 4) DirectorLog.log(p, "gate", standing + " gates already open nearby (max 4) → none");
            return null;
        }
        DirectorLog.log(p, "gate", String.format("%d open within %d blocks, chance %.0f%%, roll %.2f → spawn", standing, MAX_DIST, chance * 100, r));
        return spawnFor(server, p, null, null, MIN_DIST, MAX_DIST);
    }

    // ---- the first gate, placed on purpose (SPEC §27.6; §21.0.14 asked for it and it was never built) ---------------

    /** A player's first gate's id + 1 (0: none yet), and how many Director passes it has waited. */
    public static final String FIRST_GATE = "awaken:first_gate", FIRST_WAIT = "awaken:first_gate_wait";
    /** Its distance out past the player, along the way they left the city. */
    static final int FIRST_MIN = 250, FIRST_MAX = 400;

    /**
     * The first gate waits until the player is ready for it (level 3, or about half an hour after awakening) and out of
     * the city, then forms 250-400 blocks further along the bearing from the city's centre to them, at their own rank,
     * and says so ("A gate is drawing breath · 320 m N"); the Horizon raises it until they've cleared a floor. Before,
     * the first gate was rolled at random 300-1500 blocks out the moment they awakened, in the infirmary, unannounced.
     */
    static GateRecord firstGate(MinecraftServer server, ServerPlayer p) {
        var prof = Progression.profile(p);
        long waited = prof.counter(FIRST_WAIT);
        Progression.setCounter(p, FIRST_WAIT, waited + 1);
        boolean inCity = dev.awaken.terrain.api.CityBasin.reserved(p.getBlockX(), p.getBlockZ());
        boolean ready = prof.level() >= 3 || waited >= 36; // 36 passes of 50 s: half an hour
        if (!ready || inCity && waited < 72 || dev.awaken.core.api.OpeningHooks.inOpening(p)) return null;
        var city = dev.awaken.terrain.api.CityBasin.site(server);
        BlockPos centre = city.map(c -> new BlockPos(c.x(), 0, c.z())).orElse(server.overworld().getRespawnData().pos());
        double dx = p.getX() - centre.getX(), dz = p.getZ() - centre.getZ();
        // out along the way they left; in the middle of it all, north (the High Gate's road)
        double angle = dx * dx + dz * dz < 48 * 48 ? -Math.PI / 2 : Math.atan2(dz, dx);
        GateRecord g = spawnFor(server, p, Progression.rank(p), null, FIRST_MIN, FIRST_MAX, false, angle, Math.toRadians(25));
        if (g == null) return null;
        Progression.setCounter(p, FIRST_GATE, g.id + 1L);
        int dist = (int) Math.round(Math.sqrt(p.blockPosition().distSqr(g.pos.atY(p.getBlockY()))));
        String dir = DIRS[(int) Math.round(((Math.toDegrees(Math.atan2(g.pos.getX() - p.getX(), -(g.pos.getZ() - p.getZ()))) + 360) % 360) / 45.0) % 8];
        dev.awaken.signal.Signal.toast(p, net.minecraft.network.chat.Component.literal("A gate is drawing breath · " + dist + " m " + dir
                + " · its floors make you stronger"), dev.awaken.core.api.Notices.Tier.IMPORTANT, "gate");
        DirectorLog.log(p, "gate", "the first gate, on purpose: #" + g.id + " " + dist + " m " + dir + " after " + waited + " passes");
        return g;
    }

    /** The Horizon line: a player's first gate, raised while it stands and until they've cleared a floor. */
    static java.util.List<dev.awaken.core.api.HorizonHooks.Site> firstGateSite(ServerPlayer p) {
        var prof = Progression.profile(p);
        long id = prof.counter(FIRST_GATE) - 1;
        if (id < 0 || prof.counter("awaken:floors_cleared") > 0 || p.level().dimension() != Level.OVERWORLD) return java.util.List.of();
        GateRecord g = GateRegistry.get(p.level().getServer()).gate((int) id);
        if (g == null || !g.state.standing()) return java.util.List.of();
        return java.util.List.of(new dev.awaken.core.api.HorizonHooks.Site("gate:first", g.plan.rank().label + "-rank gate · its floors make you stronger",
                g.pos, "", true));
    }

    /** Picks a spot, rolls the gate and starts building it. */
    /** A rig room on some floor (a Gauntlet with a press and a ring, for the tour to see). */
    private static boolean hasRig(GatePlan plan) {
        for (var f : dev.awaken.gates.world.GateLayout.of(plan).main) for (var r : f.rooms) {
            if (r.rig == null) continue;
            var c = r.rig.course;
            if (c == null || (c.presses() > 0 && c.rings().length > 0)) return true;
        }
        return false;
    }

    public static GateRecord spawnFor(MinecraftServer server, ServerPlayer p, Rank forceRank, Theme forceTheme, int minDist, int maxDist) {
        return spawnFor(server, p, forceRank, forceTheme, minDist, maxDist, false);
    }

    /** As above; {@code wantRig} (tours): rolled again until the gate has a Gate Rig room on one of its floors. */
    public static GateRecord spawnFor(MinecraftServer server, ServerPlayer p, Rank forceRank, Theme forceTheme, int minDist, int maxDist,
                                      boolean wantRig) {
        return spawnFor(server, p, forceRank, forceTheme, minDist, maxDist, wantRig, Double.NaN, 0);
    }

    /** As above, toward {@code towards} (radians, x = cos, z = sin) within {@code spread} either side; NaN: any way. */
    static GateRecord spawnFor(MinecraftServer server, ServerPlayer p, Rank forceRank, Theme forceTheme, int minDist, int maxDist,
                               boolean wantRig, double towards, double spread) {
        ServerLevel over = server.overworld();
        GateRegistry reg = GateRegistry.get(server);
        BlockPos spot = null;
        String dir = "";
        int dist = 0;
        for (int attempt = 0; attempt < 10 && spot == null; attempt++) {
            double angle = Double.isNaN(towards) ? p.getRandom().nextDouble() * Math.PI * 2 : towards + (p.getRandom().nextDouble() * 2 - 1) * spread;
            dist = minDist + p.getRandom().nextInt(Math.max(1, maxDist - minDist));
            int x = p.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = p.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
            BlockPos c = surfaceEstimate(over, x, z);
            if (c == null) continue;
            // never inside Carillon (its core plus the reserve margin; SPEC §16.2)
            if (dev.awaken.terrain.api.CityBasin.reserved(c.getX(), c.getZ())) continue;
            if (!reg.near(c, 96, false).isEmpty()) continue;
            // never within a break's reach of a Hearth the player doesn't share: a veteran's gates stay off a newcomer's home
            if (nearForeignHearth(over, c, p)) continue;
            spot = c;
            int d = (int) Math.round(((Math.toDegrees(Math.atan2(Math.sin(angle), Math.cos(angle))) + 90 + 360) % 360) / 45.0) % 8;
            dir = DIRS[d];
        }
        if (spot == null) {
            DirectorLog.log(p, "gate", "no dry ground found around you → try again later");
            return null;
        }
        if (!reg.hasFreeCell()) {
            DirectorLog.log(p, "gate", "no free gate cell (all 4096 in use); no gate this time");
            return null;
        }
        GateMemory mem = p.getData(GatesRegistry.MEMORY.get());
        int id = reg.nextId();
        long seed = Hash.mix(over.getSeed() ^ Hash.of(id, mem.gatesRolled, p.getUUID().hashCode()));
        Roll roll = roll(seed, Progression.rank(p), mem, forceRank, forceTheme);
        for (int i = 1; wantRig && i < 256 && !hasRig(roll.plan()); i++) roll = roll(Hash.mix(seed + i), Progression.rank(p), mem, forceRank, forceTheme);
        List<String> trace = new ArrayList<>(roll.trace());
        trace.add(0, "seed      " + Long.toHexString(roll.plan().seed()) + " · rolled for " + p.getGameProfile().name());
        trace.add("place     " + dist + " m " + dir + " at " + spot.getX() + " " + spot.getY() + " " + spot.getZ()
                + (HearthHooks.nearest(over, spot, 256).isPresent() ? " (within 256 of a Hearth)" : ""));
        GateRecord g = GateService.create(server, id, roll.plan(), spot, p.getRandom().nextInt(2), Optional.of(p.getUUID()),
                roll.breaksInDays(), trace, false);
        g.nearHearth = HearthHooks.nearest(over, spot, 256).isPresent();
        for (String line : roll.trace()) DirectorLog.log(p, "gate", "#" + id + " " + line.replaceAll("\\s+", " "));
        return g;
    }

    /** Gates made before owners were counted (none on record) count for whoever is near, as they always did. */
    static boolean ownedBy(GateRecord g, ServerPlayer p) {
        return g.owner.isEmpty() || g.owner.get().equals(p.getUUID());
    }

    /** Reach of a break around a Hearth (the warning radius): a gate rolled for someone else never forms inside it. */
    public static final int FOREIGN_HEARTH_RADIUS = 256;

    /** Whether {@code pos} lies within {@link #FOREIGN_HEARTH_RADIUS} of a Hearth this player doesn't own or share. */
    static boolean nearForeignHearth(ServerLevel level, BlockPos pos, ServerPlayer p) {
        for (BlockPos anchor : HearthHooks.within(level, pos, FOREIGN_HEARTH_RADIUS)) if (!HearthHooks.belongs(p, anchor)) return true;
        return false;
    }

    /** Height from the generator (no chunk load); null over oceans, rivers and beaches. */
    static BlockPos surfaceEstimate(ServerLevel level, int x, int z) {
        var source = level.getChunkSource();
        BiomeResolver resolver = source.getGenerator().getBiomeSource().createUncachedResolver(source.randomState());
        Holder<Biome> biome = resolver.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(64), QuartPos.fromBlock(z));
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH) || biome.is(BiomeTags.IS_DEEP_OCEAN)) return null;
        int y = source.getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, source.randomState()) - 1;
        if (y <= level.getSeaLevel() || y > level.getMaxY() - 20) return null;
        return new BlockPos(x, y, z);
    }
}
