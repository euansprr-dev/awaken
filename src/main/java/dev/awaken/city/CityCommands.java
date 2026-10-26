package dev.awaken.city;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.core.AwakenCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * {@code /awaken city ...} (game masters):
 * <pre>
 * info                 where the city stands, its plan hash, the graphs, the district under you, the pregen numbers
 * view &lt;name&gt;          stand at a named viewpoint (sky shots, street level per district, the studio framings)
 * monsters [radius]    count the hostile creatures round you (the Commons step's night check)
 * repaint [radius]     paint the chunks round you again, live (dev; a repaired or edited plan)
 * dump                 paint the whole city into memory and write city-dump.bin for tools/city/render.py (dev)
 * scan [radius]        find tree wood hanging in the air within radius of the centre (default 400; reads the chunks from disk)
 * tidy [radius]        scan, then remove the floating tree wood it finds (cities made before the sweep took the lands' bark)
 * floater &lt;n&gt;        stand beside the n-th floating group of the last scan
 * </pre>
 */
final class CityCommands {
    private CityCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> root.then(Commands.literal("city").requires(AwakenCommands.admin())
                .then(Commands.literal("info").executes(c -> info(c.getSource())))
                .then(Commands.literal("view").then(Commands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(CityViews.VIEWS.keySet(), b))
                        .executes(c -> view(c.getSource(), StringArgumentType.getString(c, "name")))))
                .then(Commands.literal("repaint").executes(c -> repaint(c.getSource(), 0))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(0, 8))
                                .executes(c -> repaint(c.getSource(), IntegerArgumentType.getInteger(c, "radius")))))
                .then(Commands.literal("dump").executes(c -> dump(c.getSource())))
                .then(Commands.literal("monsters").executes(c -> monsters(c.getSource(), 96))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(8, 256))
                                .executes(c -> monsters(c.getSource(), IntegerArgumentType.getInteger(c, "radius")))))
                .then(Commands.literal("scan").executes(c -> scan(c.getSource(), 400, false))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(32, 700))
                                .executes(c -> scan(c.getSource(), IntegerArgumentType.getInteger(c, "radius"), false))))
                .then(Commands.literal("tidy").executes(c -> scan(c.getSource(), 400, true))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(32, 700))
                                .executes(c -> scan(c.getSource(), IntegerArgumentType.getInteger(c, "radius"), true))))
                .then(Commands.literal("floater").then(Commands.argument("n", IntegerArgumentType.integer(1, 500))
                        .executes(c -> floater(c.getSource(), IntegerArgumentType.getInteger(c, "n")))))));
    }

    private static CityLayout need(CommandSourceStack src) {
        CityLayout l = City.layout();
        if (l == null) src.sendFailure(Component.literal("This world has no city (Carillon is off, or no site)."));
        return l;
    }

    private static int info(CommandSourceStack src) {
        CityLayout l = need(src);
        if (l == null) return 0;
        CityData d = CityData.peek(src.getServer());
        BlockPos at = BlockPos.containing(src.getPosition());
        String where = l.inCity(at.getX(), at.getZ()) ? CityPlan.DISTRICTS[l.district(at.getX(), at.getZ())] : "outside the wall";
        String msg = String.format(Locale.ROOT,
                "Carillon at %d %d (street y %d, basin %s), plan %s, %d plots, %d chunks. Graphs: %s. Pregen %.1f s. You: %s. Paint: %s. Lights: %s",
                l.cx, l.cz, l.streetY, l.carved ? "carved" : "flattened by the city", l.plan.sha256.substring(0, 12), l.plan.plots.size(),
                l.chunks.length, d == null || !d.built() ? "not baked" : d.street.nodes() + "/" + d.street.edges() + " street, "
                        + d.roof.nodes() + "/" + d.roof.edges() + " roof", d == null ? 0 : d.pregenSeconds, where, City.STATS.summary(), CityLights.info());
        src.sendSuccess(() -> Component.literal(msg), false);
        dev.awaken.Awaken.LOG.info("[city] info: {}", msg);
        return 1;
    }

    /** Tours: how many hostile creatures stand within a radius of you, by kind (the Commons step's night check). */
    private static int monsters(CommandSourceStack src, int radius) {
        CityLayout l = need(src);
        if (l == null) return 0;
        net.minecraft.world.phys.Vec3 at = src.getPosition();
        var box = new net.minecraft.world.phys.AABB(at, at).inflate(radius, 64, radius);
        java.util.Map<String, Integer> kinds = new java.util.TreeMap<>();
        int n = 0, surface = 0, fields = 0;
        for (var m : src.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box, CityMonsters::hostile)) {
            if (m.distanceToSqr(at) > (double) radius * radius) continue;
            kinds.merge(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).getPath(), 1, Integer::sum);
            n++;
            // out on the ground (under open sky), not in the caves beneath; and of those, outside the wall
            if (!src.getLevel().canSeeSky(m.blockPosition())) continue;
            surface++;
            if (l.plan.kind(l.px(m.getBlockX()), l.pz(m.getBlockZ())) == CityPlan.VOID) fields++;
        }
        int px = l.px((int) Math.floor(at.x)), pz = l.pz((int) Math.floor(at.z));
        double r = Math.sqrt((double) px * px + (double) pz * pz);
        String msg = String.format(Locale.ROOT, "%d hostile within %d of you, %d of them under open sky, %d out in the fields (%.0f from the "
                        + "centre, spawns pass %.0f%%): %s; spawns checked %d, thinned %d, crowded %d", n, radius, surface, fields, r,
                100 * CityMonsters.pass(r), kinds, CityMonsters.CHECKED.get(), CityMonsters.THINNED.get(), CityMonsters.CROWDED.get());
        src.sendSuccess(() -> Component.literal(msg), false);
        dev.awaken.Awaken.LOG.info("[city] monsters: {}", msg);
        return n;
    }

    private static int view(CommandSourceStack src, String name) throws CommandSyntaxException {
        CityLayout l = need(src);
        if (l == null) return 0;
        CityViews.View v = CityViews.VIEWS.get(name);
        if (v == null) {
            src.sendFailure(Component.literal("No view " + name + "; try " + String.join(", ", CityViews.VIEWS.keySet())));
            return 0;
        }
        ServerPlayer p = src.getPlayerOrException();
        ServerLevel level = src.getServer().overworld();
        // street views stand on open ground: the nearest street, square or quay cell to the named spot
        CityViews.View at = v;
        if (CityViews.ALONG.contains(name)) at = along(l.plan, v);
        else if (CityViews.GROUND.contains(name)) {
            // past the basin the ground lies as it lies: the eye and the target are heights over it (the Commons step)
            int wx = l.cx + (int) Math.floor(v.x()), wz = l.cz + (int) Math.floor(v.z());
            level.getChunk(wx >> 4, wz >> 4);
            int g = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz) - 1 - l.streetY;
            at = new CityViews.View(v.x(), v.ry() + g, v.z(), v.tx(), v.tr() + g, v.tz());
        } else if (CityViews.EXACT.contains(name)) at = v;
        else if (name.startsWith("studio_") && v.ry() < 25 && !CityPlan.walkable(l.plan.kind((int) Math.floor(v.x()), (int) Math.floor(v.z())))
                && !CityPlan.water(l.plan.kind((int) Math.floor(v.x()), (int) Math.floor(v.z()))) && l.plan.inside((int) Math.floor(v.x()), (int) Math.floor(v.z()))) {
            // a framing from above the streets: over open ground, at its height above it
            int[] cell = openCell(l.plan, (int) Math.floor(v.x()), (int) Math.floor(v.z()));
            at = new CityViews.View(cell[0] + 0.5, v.ry(), cell[1] + 0.5, v.tx(), v.tr(), v.tz());
        } else if (v.ry() < 12) {
            int[] cell = openCell(l.plan, (int) Math.floor(v.x()), (int) Math.floor(v.z()));
            at = new CityViews.View(cell[0] + 0.5, l.plan.lift(cell[0], cell[1]) + 1.0, cell[1] + 0.5, v.tx(), v.tr(), v.tz());
        }
        p.teleportTo(level, l.cx + at.x(), l.streetY + at.ry(), l.cz + at.z(), Set.of(), at.yaw(), at.pitch(), true);
        dev.awaken.Awaken.LOG.info("[city] view {} at {} {} {} yaw {} pitch {}", name, l.cx + v.x(), l.streetY + v.ry(), l.cz + v.z(),
                v.yaw(), v.pitch());
        return 1;
    }

    /**
     * A street view near the named spot: of the open cells within 24 blocks, the one with the longest clear run of open
     * ground in one of eight directions; the camera stands there at eye height and looks down that run.
     */
    static CityViews.View along(CityPlan p, CityViews.View v) {
        int x0 = (int) Math.floor(v.x()), z0 = (int) Math.floor(v.z());
        int bx = x0, bz = z0, bd = 0, best = -1;
        int[][] dirs = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};
        for (int dx = -24; dx <= 24; dx++) {
            for (int dz = -24; dz <= 24; dz++) {
                int x = x0 + dx, z = z0 + dz;
                byte k = p.kind(x, z);
                if (k != CityPlan.AVENUE && k != CityPlan.STREET && k != CityPlan.LANE && k != CityPlan.RING) continue;
                for (int d = 0; d < 8; d++) {
                    int run = 0;
                    while (run < 70 && CityPlan.walkable(p.kind(x + dirs[d][0] * (run + 1), z + dirs[d][1] * (run + 1)))) run++;
                    // the middle of the street, not against a wall: open ground on both sides of the line of sight
                    int[] side = dirs[(d + 2) & 7];
                    int left = 0, right = 0;
                    while (left < 4 && CityPlan.walkable(p.kind(x + side[0] * (left + 1), z + side[1] * (left + 1)))) left++;
                    while (right < 4 && CityPlan.walkable(p.kind(x - side[0] * (right + 1), z - side[1] * (right + 1)))) right++;
                    // a long run in the middle of a street, near the spot
                    int score = run * 4 + 8 * Math.min(left, right) - (Math.abs(dx) + Math.abs(dz));
                    if (score > best) {
                        best = score;
                        bx = x;
                        bz = z;
                        bd = d;
                    }
                }
            }
        }
        double ey = p.lift(bx, bz) + 1.0;
        return new CityViews.View(bx + 0.5, ey, bz + 0.5, bx + 0.5 + dirs[bd][0] * 40, ey + 7, bz + 0.5 + dirs[bd][1] * 40);
    }

    /** The nearest cell (spiralling out) that is open ground with open ground round it, not under a building. */
    static int[] openCell(CityPlan p, int x, int z) {
        for (int r = 0; r <= 20; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int cx = x + dx, cz = z + dz;
                    if (!CityPlan.walkable(p.kind(cx, cz))) continue;
                    int open = 0;
                    for (int[] d : CityPainter.NB) if (CityPlan.walkable(p.kind(cx + 2 * d[0], cz + 2 * d[1]))) open++;
                    if (open >= 2) return new int[]{cx, cz};
                }
            }
        }
        return new int[]{x, z};
    }

    private static int repaint(CommandSourceStack src, int radius) {
        CityLayout l = need(src);
        if (l == null) return 0;
        ServerLevel level = src.getLevel();
        if (level.dimension() != Level.OVERWORLD) return 0;
        BlockPos at = BlockPos.containing(src.getPosition());
        CityBuffer buf = new CityBuffer(16, 16);
        int n = 0, chunks = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = (at.getX() >> 4) + dx, cz = (at.getZ() >> 4) + dz;
                if (!l.touches(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                CityPainter.paintChunk(l, cx, cz, level.getSeed(), buf);
                n += CityWriter.toLevel(level, chunk, buf, l.streetY);
                chunks++;
            }
        }
        int fn = n, fc = chunks;
        src.sendSuccess(() -> Component.literal("Repainted " + fc + " chunks, " + fn + " blocks changed"), false);
        return chunks;
    }

    private static int scan(CommandSourceStack src, int radius, boolean tidy) {
        CityLayout l = need(src);
        if (l == null) return 0;
        CityScan.Result r = CityScan.scan(src.getServer().overworld(), l, radius, tidy);
        for (String line : CityScan.report(r, 40)) src.sendSuccess(() -> Component.literal(line), false);
        return r.count(false);
    }

    private static int floater(CommandSourceStack src, int n) throws CommandSyntaxException {
        var list = CityScan.last;
        if (n > list.size()) {
            src.sendFailure(Component.literal("The last scan found " + list.size() + " floating groups"));
            return 0;
        }
        CityScan.Floater f = list.get(n - 1);
        ServerPlayer p = src.getPlayerOrException();
        // stand 14 blocks out from it, towards the city centre, looking at it
        CityLayout l = City.layout();
        double dx = l.cx - f.x(), dz = l.cz - f.z(), len = Math.max(1, Math.hypot(dx, dz));
        double sx = f.x() + 0.5 + dx / len * 14, sz = f.z() + 0.5 + dz / len * 14;
        double yaw = Math.toDegrees(Math.atan2(-(f.x() + 0.5 - sx), f.z() + 0.5 - sz));
        p.teleportTo(src.getServer().overworld(), sx, f.y() + 1, sz, Set.of(), (float) yaw, 0F, true);
        return 1;
    }

    private static int dump(CommandSourceStack src) {
        CityLayout l = need(src);
        if (l == null) return 0;
        try {
            Path out = src.getServer().getServerDirectory().resolve("city-dump.bin");
            int n = CityDump.write(out, l, src.getLevel().getSeed());
            src.sendSuccess(() -> Component.literal("Wrote " + n + " chunks to " + out), false);
            return n;
        } catch (java.io.IOException e) {
            src.sendFailure(Component.literal("Dump failed: " + e));
            return 0;
        }
    }
}
