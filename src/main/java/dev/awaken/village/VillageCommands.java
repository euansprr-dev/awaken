package dev.awaken.village;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.city.CityPlan;
import dev.awaken.core.AwakenCommands;
import dev.awaken.village.plan.VillagePlan;
import dev.awaken.village.world.GenTerrain;
import dev.awaken.village.world.Plans;
import dev.awaken.village.world.SettlementGrid;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@code /awaken village ...} (game masters):
 * <pre>
 * list                  the settlements meant to be within 2,000 blocks (planned ones with their names and size)
 * view &lt;key&gt; &lt;name&gt;     stand at a framing of a settlement: hill, green, street, inn, smithy, mill, dock, field, pen,
 *                       notice, bell, house1, house2 (inside), night (the street at dusk); key "first" or "nearest"
 * info                  the settlement you stand in: its plan, people, state and your standing
 * stats                 plans made and the painter's cost
 * </pre>
 */
final class VillageCommands {
    private VillageCommands() {}

    static final List<String> VIEWS = List.of("hill", "green", "street", "inn", "smithy", "mill", "dock", "field", "pen", "notice", "bell",
            "house1", "house2", "night", "gate", "gate_out", "market", "scribe", "wall", "pass", "head", "shaft", "level1", "winze", "face", "cavern",
            "stable", "marker", "assay");

    static void register() {
        AwakenCommands.add((root, ctx) -> root.then(Commands.literal("village").requires(AwakenCommands.admin())
                .then(Commands.literal("list").executes(c -> list(c.getSource())))
                .then(Commands.literal("stats").executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal(Plans.stats()), false);
                    return 1;
                }))
                .then(Commands.literal("info").executes(c -> info(c.getSource())))
                .then(Commands.literal("board").executes(c -> board(c.getSource())))
                .then(Commands.literal("trade").then(Commands.argument("job", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(VillageService.TRADERS, b))
                        .executes(c -> trade(c.getSource(), StringArgumentType.getString(c, "job")))))
                .then(Commands.literal("standing").then(Commands.argument("points", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-50, 200))
                        .executes(c -> standing(c.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "points")))))
                .then(Commands.literal("start").executes(c -> start(c.getSource())))
                .then(dev.awaken.village.trouble.TroubleCommands.node())
                .then(Commands.literal("grow").executes(c -> grow(c.getSource(), false)).then(Commands.literal("now").executes(c -> grow(c.getSource(), true))))
                .then(dev.awaken.village.road.RoadCommands.node())
                .then(Commands.literal("view").then(Commands.argument("key", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("first", "nearest", "nearest_village", "nearest_town", "first_town",
                                "nearest_mine", "nearest_wayhouse"), b))
                        .then(Commands.argument("name", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(VIEWS, b))
                                .executes(c -> view(c.getSource(), StringArgumentType.getString(c, "key"), StringArgumentType.getString(c, "name"))))))));
    }

    /** The running settlement the player stands in, or the nearest within 300. */
    static VillageService.@Nullable Live here(ServerPlayer p) {
        VillageService.Live best = VillageService.at(p.level(), p.blockPosition());
        if (best != null) return best;
        double bd = 300;
        for (VillageService.Live l : VillageService.all()) {
            double d = Math.hypot(l.plan().ox - p.getX(), l.plan().oz - p.getZ());
            if (d < bd) {
                bd = d;
                best = l;
            }
        }
        return best;
    }

    private static int info(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        VillageService.Live l = here(p);
        if (l == null) {
            src.sendFailure(Component.literal("No village running near you."));
            return 0;
        }
        VillageState.Village v = l.rec();
        String msg = String.format(Locale.ROOT, "%s | people %d, bodies %d, awake %s | prosperity %d, mood %d | your standing: %s (%d) | damage %d, requests %d | %s",
                l.plan(), l.settlement().people(), l.settlement().bodies(), l.settlement().awake(), v.prosperity, v.mood,
                VillageService.standingName(p, l), v.points(p.getUUID()), v.damage.size(), v.requests.size(), Plans.stats());
        src.sendSuccess(() -> Component.literal(msg), false);
        dev.awaken.Awaken.LOG.info("[village] info {}", msg);
        return 1;
    }

    private static int board(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        VillageService.Live l = here(p);
        if (l == null) {
            src.sendFailure(Component.literal("No village running near you."));
            return 0;
        }
        VillageNet.openBoard(p, l);
        return 1;
    }

    /** Opens a trader's goods (the nearest of that trade in the village): for tours and testing. */
    private static int trade(CommandSourceStack src, String job) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        VillageService.Live l = here(p);
        if (l == null) {
            src.sendFailure(Component.literal("No village running near you."));
            return 0;
        }
        for (int rec = 0; rec < l.settlement().people(); rec++) {
            if (!l.settlement().job(rec).equals(job)) continue;
            if (VillageService.talk(p, l.settlement(), rec, true)) return 1;
        }
        src.sendFailure(Component.literal(l.plan().name + " has no " + job + "."));
        return 0;
    }

    private static int standing(CommandSourceStack src, int points) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        VillageService.Live l = here(p);
        if (l == null) return 0;
        VillageService.standing(p, l, points - l.rec().points(p.getUUID()), "command");
        return 1;
    }

    /**
     * Growth (SPEC §28.7): a plot fenced past a lane's end now (its prosperity aside); {@code now}: the stage it's at
     * done at once (the masons start, or finish).
     */
    private static int grow(CommandSourceStack src, boolean now) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        VillageService.Live l = here(p);
        if (l == null) return 0;
        ServerLevel level = src.getServer().overworld();
        var st = dev.awaken.village.trouble.TroubleState.get(src.getServer());
        var g = st.memory(l.key()).growth();
        long day = VillageService.today(src.getServer());
        String what;
        if (g.stage() == dev.awaken.village.growth.Growth.NONE) {
            what = dev.awaken.village.growth.Growth.begin(level, l, g, day) ? "a plot fenced" : "no room past any lane's end";
        } else {
            what = dev.awaken.village.growth.Growth.hurry(level, l, g, day, now);
        }
        st.setDirty();
        src.sendSuccess(() -> Component.literal(l.plan().name + ": " + what), false);
        return 1;
    }

    /** Starts the people of the settlement nearest you now (they start by themselves within reach; tours don't wait). */
    private static int start(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        ServerLevel level = src.getServer().overworld();
        VillagePlan plan = find(level, p, "nearest");
        if (plan == null) return 0;
        VillageService.Live l = VillageService.start(level, plan);
        src.sendSuccess(() -> Component.literal(l == null ? plan.name + ": not started (not painted here, or not loaded)" : plan.name + " started"), false);
        return l == null ? 0 : 1;
    }

    static GenTerrain terrain(ServerLevel level) {
        var src = level.getChunkSource();
        return GenTerrain.of(src.getGenerator(), level, src.randomState());
    }

    /** The plan for a key ("first", "nearest", or a grid key) near a player. */
    static @Nullable VillagePlan find(ServerLevel level, ServerPlayer p, String key) {
        long seed = level.getSeed();
        int[] centre = SettlementGrid.centre();
        if (key.equals("first")) return Plans.of(SettlementGrid.first(seed, centre), terrain(level));
        // (SPEC §28.9) the first town a player meets: its region's town
        if (key.equals("first_town")) {
            var town = dev.awaken.village.road.RoadNet.firstTown();
            VillagePlan plan = town == null ? null : Plans.of(town, terrain(level));
            if (plan != null) return plan;
            key = "nearest_town";
        }
        VillagePlan.Kind want = switch (key) {
            case "nearest_village" -> VillagePlan.Kind.VILLAGE;
            case "nearest_town" -> VillagePlan.Kind.TOWN;
            case "nearest_mine" -> VillagePlan.Kind.MINING;
            case "nearest_wayhouse" -> VillagePlan.Kind.WAYHOUSE;
            default -> null;
        };
        List<SettlementGrid.Anchor> near = new java.util.ArrayList<>(SettlementGrid.near(seed, p.getBlockX(), p.getBlockZ(),
                want == VillagePlan.Kind.TOWN || want == VillagePlan.Kind.MINING ? 3200 : 4000, centre));
        // the nearest first: each one asked may have to be planned
        near.sort(java.util.Comparator.comparingDouble(a -> Math.hypot(a.x() - p.getX(), a.z() - p.getZ())));
        for (SettlementGrid.Anchor a : near) {
            if (!key.equals("nearest") && !key.equals(a.key()) && want == null) continue;
            // the kind a plan takes may differ from its anchor's (a village with no room is a hamlet; a mining town with no
            // mountain is a village)
            if (want == VillagePlan.Kind.TOWN && a.kind() != VillagePlan.Kind.TOWN || want == VillagePlan.Kind.WAYHOUSE && a.kind() != VillagePlan.Kind.WAYHOUSE
                    || want == VillagePlan.Kind.MINING && a.kind() != VillagePlan.Kind.MINING
                    || want == VillagePlan.Kind.VILLAGE && a.kind() != VillagePlan.Kind.VILLAGE && a.kind() != VillagePlan.Kind.MINING) continue;
            VillagePlan plan = Plans.of(a, terrain(level));
            if (plan != null && (want == null || plan.kind == want)) return plan;
        }
        return null;
    }

    private static int list(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        ServerLevel level = src.getServer().overworld();
        for (SettlementGrid.Anchor a : SettlementGrid.near(level.getSeed(), p.getBlockX(), p.getBlockZ(), 2000, SettlementGrid.centre())) {
            VillagePlan plan = Plans.cached(a.key());
            String what = plan != null ? plan.toString() : Plans.known(a.key()) ? "no site" : "not planned yet";
            int d = (int) Math.hypot(a.x() - p.getX(), a.z() - p.getZ());
            src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "%s %s %d m: %s", a.key(), a.kind(), d, what)), false);
        }
        return 1;
    }

    /** Stands the player at a named framing of a settlement. */
    private static int view(CommandSourceStack src, String key, String name) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        ServerLevel level = src.getServer().overworld();
        VillagePlan plan = find(level, p, key);
        if (plan == null) {
            src.sendFailure(Component.literal("No settlement '" + key + "' near you."));
            return 0;
        }
        double[] v = frame(plan, name);
        // a village without a mill shows its water's other work, or its beasts
        if (v == null && name.equals("mill")) v = frame(plan, "dock");
        if (v == null && name.equals("mill")) v = frame(plan, "pen");
        if (v == null) {
            src.sendFailure(Component.literal(plan.name + " has no " + name + "."));
            return 0;
        }
        double dx = v[3] - v[0], dy = v[4] - v[1], dz = v[5] - v[2];
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)), pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        double[] e = v;
        // framings give the eye; the player stands 1.62 under it (a spectator stays exactly there), once its land is made (SPEC §28.9)
        dev.awaken.core.api.Passage.go(p, level, plan.ox + e[0], plan.oz + e[2], () -> {
            p.teleportTo(level, plan.ox + e[0], plan.baseY + e[1] - 1.62, plan.oz + e[2], Set.of(), yaw, pitch, true);
            dev.awaken.Awaken.LOG.info("[village] view {} {} of {} at {} {} {}", key, name, plan, plan.ox + e[0], plan.baseY + e[1], plan.oz + e[2]);
            src.sendSuccess(() -> Component.literal(plan.name + ": " + name), false);
        });
        return 1;
    }

    /** A framing: the eye and the point it looks at (plan coordinates, y over the street level; a ground block's eye is 2.62 over it), or null. */
    static double @Nullable [] frame(VillagePlan p, String name) {
        switch (name) {
            case "hill" -> {
                // high over the main street's outer half and a little off it: the roofs, the smoke and the street running
                // in to the green
                VillagePlan.Street s = p.streets.getFirst();
                double want = Math.max(p.greenR + 14, p.radius() * 0.6);
                int k = 0;
                for (int i = 1; i < s.points(); i++)
                    if (Math.abs(Math.hypot(s.x()[i], s.z()[i]) - want) < Math.abs(Math.hypot(s.x()[k], s.z()[k]) - want)) k = i;
                double x = s.x()[k], z = s.z()[k], len = Math.max(1, Math.hypot(x, z)), ux = x / len, uz = z / len;
                double ex = x + ux * 8 - uz * 9, ez = z + uz * 8 + ux * 9;
                // never over a roof: out along the street until no house stands within six
                for (int step = 0; step < 12 && houseNear(p, ex, ez, 6); step++) {
                    ex += ux * 3;
                    ez += uz * 3;
                }
                return new double[]{ex + 0.5, s.lift()[k] + 20, ez + 0.5, 0, 2, 0};
            }
            case "green" -> {
                // where the main street comes into the green, looking across it to the well
                VillagePlan.Street s = p.streets.getFirst();
                int k = 0;
                double want = p.greenR + 5;
                for (int i = 1; i < s.points(); i++)
                    if (Math.abs(Math.hypot(s.x()[i], s.z()[i]) - want) < Math.abs(Math.hypot(s.x()[k], s.z()[k]) - want)) k = i;
                return new double[]{s.x()[k] + 0.5, s.lift()[k] + 3.2, s.z()[k] + 0.5, p.well[0] + 0.5, 1.6, p.well[1] + 0.5};
            }
            case "street", "night" -> {
                VillagePlan.Street s = p.streets.getFirst();
                int k = Math.min(s.points() - 1, s.points() * 3 / 4);
                int toward = Math.max(0, k - 5);
                return new double[]{s.x()[k] + 0.5, s.lift()[k] + 2.6, s.z()[k] + 0.5, s.x()[toward], s.lift()[toward] + 3, s.z()[toward]};
            }
            case "notice" -> {
                int f = p.notice[2];
                int sx = f == 1 ? 1 : f == 3 ? -1 : 0, sz = f == 2 ? 1 : f == 0 ? -1 : 0;
                return new double[]{p.notice[0] + 0.5 + sx * 2.5, 2.6, p.notice[1] + 0.5 + sz * 2.5, p.notice[0] + 0.5, 2, p.notice[1] + 0.5};
            }
            case "smithy" -> {
                // the forge shed, where the work is (else the smithy itself, below)
                VillagePlan.Forge f = p.forge;
                if (f != null) return fromStreet(p, f.anvilX() + 0.5, f.anvilZ() + 0.5, f.lift() + 1.6, 8, 2.8);
                return frameBuilding(p, name);
            }
            case "bell" -> {
                VillagePlan.Bell b = p.bell;
                if (b == null) return null;
                return fromStreet(p, b.bx() + 0.5, b.bz() + 0.5, b.bry() * 0.75, b.tower() ? 15 : 9, 2.8);
            }
            case "field" -> {
                if (p.fields.isEmpty()) return null;
                VillagePlan.Field f = p.fields.getFirst();
                double cx = (f.x0() + f.x1()) / 2.0, cz = (f.z0() + f.z1()) / 2.0;
                return new double[]{f.x0() - 4, f.lift() + 4, f.z0() - 4, cx, f.lift(), cz};
            }
            case "pen" -> {
                if (p.pens.isEmpty()) return null;
                VillagePlan.Pen f = p.pens.getFirst();
                double cx = (f.x0() + f.x1()) / 2.0, cz = (f.z0() + f.z1()) / 2.0;
                return new double[]{f.x0() - 3, f.lift() + 4, f.z0() - 3, cx, f.lift(), cz};
            }
            case "dock" -> {
                VillagePlan.Dock d = p.dock;
                if (d == null) return null;
                return new double[]{d.x0() - 6, d.lift() + 5, d.z0() - 6, d.endX(), d.lift(), d.endZ()};
            }
            case "gate", "gate_out", "market", "wall", "pass", "head", "shaft", "level1", "winze", "face", "cavern", "stable", "marker" -> {
                return dev.awaken.village.road.RoadCommands.frame(p, name);
            }
            case "scribe", "assay" -> {
                // (SPEC §28.9) in a town's packed streets the nearest street point may be behind the shop: frame its door
                if (p.kind == VillagePlan.Kind.TOWN || p.kind == VillagePlan.Kind.MINING) return dev.awaken.village.road.RoadCommands.frame(p, name);
                return frameBuilding(p, name);
            }
            default -> {
                return frameBuilding(p, name);
            }
        }
    }

    /** A building from the street in front of it, or (house1, house2) inside it. */
    private static double @Nullable [] frameBuilding(VillagePlan p, String name) {
        String use = switch (name) {
            case "house1", "house2" -> "house";
            default -> name;
        };
        int skip = name.equals("house2") ? 1 : 0;
        for (VillagePlan.Building b : p.buildings) {
            if (!b.use().equals(use)) continue;
            if (name.equals("house2") && p.plots.get(b.plot()).storeys() < 2 && skip-- > 0) continue;
            if (skip-- > 0) continue;
            CityPlan.Plot q = p.plots.get(b.plot());
            int fx = b.outX() - b.doorX(), fz = b.outZ() - b.doorZ();
            double mx = (q.x0() + q.x1()) / 2.0 + 0.5, mz = (q.z0() + q.z1()) / 2.0 + 0.5;
            if (name.startsWith("house")) {
                // just inside the door, looking across the room
                return new double[]{b.doorX() - fx + 0.5, q.lift() + 2.6, b.doorZ() - fz + 0.5, mx - fx * 3, q.lift() + 2, mz - fz * 3};
            }
            return fromStreet(p, mx, mz, q.lift() + 3, 11, 2.8);
        }
        return null;
    }

    private static boolean houseNear(VillagePlan p, double x, double z, int r) {
        for (int dz = -r; dz <= r; dz++)
            for (int dx = -r; dx <= r; dx++) if (p.plotAt((int) Math.floor(x) + dx, (int) Math.floor(z) + dz) >= 0) return true;
        return false;
    }

    /**
     * A framing from the street:the street point nearest {@code dist} from a target (never one at its foot), the eye
     * {@code eye} over the paving, looking at the target at {@code ty}.
     */
    private static double[] fromStreet(VillagePlan p, double tx, double tz, double ty, double dist, double eye) {
        double bd = Double.MAX_VALUE;
        double[] at = null;
        for (VillagePlan.Street s : p.streets) {
            for (int k = 0; k < s.points(); k++) {
                double d = Math.hypot(s.x()[k] - tx, s.z()[k] - tz);
                if (d < 5) continue;
                double score = Math.abs(d - dist);
                if (score < bd) {
                    bd = score;
                    at = new double[]{s.x()[k] + 0.5, s.lift()[k] + eye, s.z()[k] + 0.5, tx, ty, tz};
                }
            }
        }
        return at != null ? at : new double[]{tx + dist, ty + eye, tz, tx, ty, tz};
    }
}
