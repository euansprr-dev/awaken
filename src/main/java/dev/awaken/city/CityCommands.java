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
 * repaint [radius]     paint the chunks round you again, live (dev; a repaired or edited plan)
 * dump                 paint the whole city into memory and write city-dump.bin for tools/city/render.py (dev)
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
                .then(Commands.literal("dump").executes(c -> dump(c.getSource())))));
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
                "Carillon at %d %d (street y %d, basin %s), plan %s, %d plots, %d chunks. Graphs: %s. Pregen %.1f s. You: %s. Paint: %s",
                l.cx, l.cz, l.streetY, l.carved ? "carved" : "flattened by the city", l.plan.sha256.substring(0, 12), l.plan.plots.size(),
                l.chunks.length, d == null || !d.built() ? "not baked" : d.street.nodes() + "/" + d.street.edges() + " street, "
                        + d.roof.nodes() + "/" + d.roof.edges() + " roof", d == null ? 0 : d.pregenSeconds, where, City.STATS.summary());
        src.sendSuccess(() -> Component.literal(msg), false);
        dev.awaken.Awaken.LOG.info("[city] info: {}", msg);
        return 1;
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
        p.teleportTo(level, l.cx + v.x(), l.streetY + v.ry(), l.cz + v.z(), Set.of(), v.yaw(), v.pitch(), true);
        dev.awaken.Awaken.LOG.info("[city] view {} at {} {} {} yaw {} pitch {}", name, l.cx + v.x(), l.streetY + v.ry(), l.cz + v.z(),
                v.yaw(), v.pitch());
        return 1;
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
