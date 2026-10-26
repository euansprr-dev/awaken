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
            "house1", "house2", "night");

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
                .then(Commands.literal("view").then(Commands.argument("key", StringArgumentType.word())
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
            if (VillageService.talk(p, l.settlement(), rec)) return 1;
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
        List<SettlementGrid.Anchor> near = SettlementGrid.near(seed, p.getBlockX(), p.getBlockZ(), 4000, centre);
        if (key.equals("first")) return Plans.of(SettlementGrid.first(seed, centre), terrain(level));
        for (SettlementGrid.Anchor a : near) {
            if (!key.equals("nearest") && !key.equals(a.key()) && !(key.equals("nearest_village") && a.kind() == VillagePlan.Kind.VILLAGE)) continue;
            VillagePlan plan = Plans.of(a, terrain(level));
            if (plan != null && (!key.equals("nearest_village") || plan.kind == VillagePlan.Kind.VILLAGE)) return plan;
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
        if (v == null) {
            src.sendFailure(Component.literal(plan.name + " has no " + name + "."));
            return 0;
        }
        double dx = v[3] - v[0], dy = v[4] - v[1], dz = v[5] - v[2];
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)), pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        p.teleportTo(level, plan.ox + v[0], plan.baseY + v[1], plan.oz + v[2], Set.of(), yaw, pitch, true);
        dev.awaken.Awaken.LOG.info("[village] view {} {} of {} at {} {} {}", key, name, plan, plan.ox + v[0], plan.baseY + v[1], plan.oz + v[2]);
        src.sendSuccess(() -> Component.literal(plan.name + ": " + name), false);
        return 1;
    }

    /** A framing: eye position and look-at point (plan coordinates, y over the street level), or null. */
    static double @Nullable [] frame(VillagePlan p, String name) {
        switch (name) {
            case "hill" -> {
                // from the highest ground just outside, looking down over the roofs to the green
                double best = Double.NEGATIVE_INFINITY;
                double[] at = null;
                for (int a = 0; a < 24; a++) {
                    double ang = a * Math.PI / 12;
                    int d = Math.min(p.r - 2, (int) (p.radius() * 0.9));
                    int x = (int) Math.round(Math.cos(ang) * d), z = (int) Math.round(Math.sin(ang) * d);
                    double h = p.lift(x, z);
                    if (p.kind(x, z) == VillagePlan.WATER) continue;
                    if (h > best) {
                        best = h;
                        at = new double[]{x + 0.5, h + 14, z + 0.5, 0, 3, 0};
                    }
                }
                return at;
            }
            case "green" -> {
                int g = p.greenR + 3;
                return new double[]{p.well[0] + g * 0.7, 2.6, p.well[1] + g * 0.7, p.well[0], 1, p.well[1]};
            }
            case "street", "night" -> {
                VillagePlan.Street s = p.streets.getFirst();
                int k = Math.min(s.points() - 1, s.points() * 3 / 4);
                int toward = Math.max(0, k - 5);
                return new double[]{s.x()[k] + 0.5, s.lift()[k] + 1.7, s.z()[k] + 0.5, s.x()[toward], s.lift()[toward] + 2.5, s.z()[toward]};
            }
            case "notice" -> {
                int f = p.notice[2];
                int sx = f == 1 ? 1 : f == 3 ? -1 : 0, sz = f == 2 ? 1 : f == 0 ? -1 : 0;
                return new double[]{p.notice[0] + 0.5 + sx * 2.5, 1.7, p.notice[1] + 0.5 + sz * 2.5, p.notice[0] + 0.5, 1.4, p.notice[1] + 0.5};
            }
            case "bell" -> {
                VillagePlan.Bell b = p.bell;
                if (b == null) return null;
                return new double[]{b.bx() + 6.5, 3, b.bz() + 6.5, b.bx() + 0.5, b.tower() ? 7 : 3, b.bz() + 0.5};
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
            default -> {
                // a building from the street in front of it, or (house1, house2) inside it
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
                        return new double[]{b.doorX() - fx + 0.5, q.lift() + 1.6, b.doorZ() - fz + 0.5, mx - fx * 3, q.lift() + 1.2, mz - fz * 3};
                    }
                    return new double[]{b.outX() + fx * 9 + 0.5 + fz * 3, q.lift() + 3, b.outZ() + fz * 9 + 0.5 + fx * 3, mx, q.lift() + 3, mz};
                }
                return null;
            }
        }
    }
}
