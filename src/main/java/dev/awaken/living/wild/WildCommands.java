package dev.awaken.living.wild;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.awaken.core.AwakenCommands;
import dev.awaken.living.api.Wildness;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /awaken wild here} (admin): how wild the land is where you stand, the land's own value beside the kept one, and
 * what working it out costs. {@code /awaken wild map [radius]}: a map of the bands round you in chat, north up.
 */
public final class WildCommands {
    private WildCommands() {}

    /** The map's colours, Tamed to Deep wild (the plan's ramp). */
    private static final int[] COLOURS = {0xE8DEC4, 0xD6D8A0, 0x5E9C4E, 0x1E5C3C};

    public static void register() {
        AwakenCommands.add((root, ctx) -> {
            var l = Commands.literal("wild").requires(AwakenCommands.admin());
            l.then(Commands.literal("here").executes(c -> here(c.getSource())));
            l.then(Commands.literal("map").executes(c -> map(c.getSource(), 512))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(64, 4096))
                            .executes(c -> map(c.getSource(), IntegerArgumentType.getInteger(c, "radius")))));
            l.then(Commands.literal("signs").executes(c -> signs(c.getSource())));
            root.then(l);
        });
    }

    private static int here(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        Vec3 p = src.getPosition();
        int x = (int) Math.floor(p.x), z = (int) Math.floor(p.z);
        long t0 = System.nanoTime();
        double land = Wildness.land(level, x, z);
        long cost = System.nanoTime() - t0;
        double now = Wildness.at(level, x, z), hearth = Wildness.hearthKeep(level, x, z);
        Wildness.Band b = Wildness.band(now);
        src.sendSuccess(() -> Component.literal(String.format("%s here: %.2f (the land %.2f, a Hearth leaves %.0f%%; %.2f ms to work out; %d chunks queued)",
                b.title, now, land, hearth * 100, cost / 1e6, WildRecord.pending(level))).withColor(COLOURS[b.ordinal()]), false);
        return (int) Math.round(now * 100);
    }

    /** 33 columns by 17 rows of the bands, sampled afresh (no record), you in the middle. */
    private static int map(CommandSourceStack src, int radius) {
        ServerLevel level = src.getLevel();
        Vec3 p = src.getPosition();
        int cols = 33, rows = 17;
        double step = 2.0 * radius / (cols - 1);
        int[] counts = new int[4];
        for (int r = 0; r < rows; r++) {
            MutableComponent line = Component.empty();
            for (int k = 0; k < cols; k++) {
                int x = (int) Math.floor(p.x + (k - cols / 2) * step), z = (int) Math.floor(p.z + (r - rows / 2) * step * 2);
                boolean you = k == cols / 2 && r == rows / 2;
                Wildness.Band b = Wildness.band(Wildness.land(level, x, z) * Wildness.hearthKeep(level, x, z));
                counts[b.ordinal()]++;
                line.append(Component.literal(you ? "◆" : "█").withColor(you ? 0xFF4040 : COLOURS[b.ordinal()]));
            }
            src.sendSuccess(() -> line, false);
        }
        int n = cols * rows;
        src.sendSuccess(() -> Component.literal(String.format("%d blocks across: tamed %d%%, pasture %d%%, wild %d%%, deep %d%%",
                radius * 2, counts[0] * 100 / n, counts[1] * 100 / n, counts[2] * 100 / n, counts[3] * 100 / n)), false);
        return counts[3];
    }

    /** The signs within 48 blocks (kind, what, how many, age, distance) and your trail. */
    private static int signs(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        net.minecraft.server.level.ServerPlayer p = src.getPlayerOrException();
        var level = p.level();
        long now = level.getGameTime();
        var near = dev.awaken.living.population.Spoor.of(level).near(p.getX(), p.getZ(), 48);
        src.sendSuccess(() -> Component.literal(near.size() + " signs within 48 blocks; " + dev.awaken.living.hunt.Tracking.describe(p)), false);
        int shown = 0;
        for (var m : near) {
            if (shown++ >= 12) break;
            int d = (int) Math.hypot(m.x() - p.getX(), m.z() - p.getZ());
            src.sendSuccess(() -> Component.literal("  " + m.kind().name().toLowerCase(java.util.Locale.ROOT) + " " + m.species() + " ×" + m.count()
                    + " · " + m.age(now) / 20 + " s old · " + d + " m"), false);
        }
        return near.size();
    }
}
