package dev.awaken.caves;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.Awaken;
import dev.awaken.core.AwakenCommands;
import dev.awaken.core.api.Passage;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@code /awaken caves goto <region> [pick]} (admin; for tours, and for finding a region to play in): to the nearest cave
 * of a region, found as {@code /locate biome} finds one (from the biome source, nothing generated), then the open floor
 * round it with the most room, facing the longest way through the dark. Its land is made off the tick (a {@link Passage}:
 * generating it here stopped the server for 27 s on the CI runner), and only what is loaded is read. The search starts where
 * the command runs ({@code execute positioned} moves it). {@code pick} takes the next-best floor (each at least
 * {@link #APART} blocks from the better ones), for another look in the same region.
 */
final class RegionCommands {
    private RegionCommands() {}

    /** How far the search goes (blocks), the chunks searched round what it finds (the passage's), how far apart the picks are. */
    static final int SEARCH = 3200, NEAR = Passage.RADIUS, APART = 14;
    /** Up and down from the found cell, the grid the floors are tried on, and how far each of the eight rays looks. */
    static final int SPAN = 24, STEP = 2, RAY = 16;
    /** No lava this near a floor (across, and from a block under it to two over): it flows in once the land is live. */
    static final int LAVA_CLEAR = 4;

    private record Spot(BlockPos at, int room, float yaw) {}

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            var ids = CaveRegion.ORDER.stream().map(r -> r.id).toList();
            root.then(Commands.literal("caves").requires(AwakenCommands.admin()).then(Commands.literal("goto")
                    .then(Commands.argument("region", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(ids, b))
                            .executes(c -> go(c, 0))
                            .then(Commands.argument("pick", IntegerArgumentType.integer(0, 9))
                                    .executes(c -> go(c, IntegerArgumentType.getInteger(c, "pick")))))));
        });
    }

    private static int go(CommandContext<CommandSourceStack> c, int pick) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(c, "region");
        CaveRegion region = CaveRegion.ORDER.stream().filter(r -> r.id.equals(id)).findFirst().orElse(null);
        if (region == null) {
            c.getSource().sendFailure(Component.literal("No such cave region: " + String.join(", ",
                    CaveRegion.ORDER.stream().map(r -> r.id).toList())));
            return 0;
        }
        ServerLevel level = p.level();
        if (level.dimension() != Level.OVERWORLD) {
            c.getSource().sendFailure(Component.literal("The cave regions are in the overworld."));
            return 0;
        }
        long t0 = System.nanoTime();
        // from where the command runs (a tour's "execute positioned" keeps every pick's search the same)
        var found = level.findClosestBiome3d(b -> b.is(region.biome()), BlockPos.containing(c.getSource().getPosition()), SEARCH, 32, 64);
        if (found == null) {
            c.getSource().sendFailure(Component.literal("No " + id + " within " + SEARCH + " blocks (a world made before the caves pack has none)."));
            return 0;
        }
        BlockPos near = found.getFirst();
        CommandSourceStack src = c.getSource();
        Passage.go(p, level, near.getX() + 0.5, near.getZ() + 0.5, () -> arrive(src, p, level, region, near, pick, t0));
        return 1;
    }

    /** The land round the found cell is made: the floor with the most room, and there. */
    private static void arrive(CommandSourceStack src, ServerPlayer p, ServerLevel level, CaveRegion region, BlockPos near, int pick, long t0) {
        String id = region.id;
        List<Spot> spots = spots(level, region, near, near.getX() >> 4, near.getZ() >> 4);
        if (spots.isEmpty()) {
            src.sendFailure(Component.literal("Found " + id + " at " + near.toShortString() + " but no open floor round it."));
            return;
        }
        // the best, then each next one far enough from those before it
        List<Spot> picks = new ArrayList<>();
        for (Spot s : spots) {
            if (picks.stream().allMatch(q -> q.at.distSqr(s.at) >= APART * APART)) picks.add(s);
            if (picks.size() > pick) break;
        }
        Spot s = picks.get(Math.min(pick, picks.size() - 1));
        p.teleportTo(level, s.at.getX() + 0.5, s.at.getY(), s.at.getZ() + 0.5, Set.of(), s.yaw, 8F, true);
        double ms = (System.nanoTime() - t0) / 1e6;
        Awaken.LOG.info("[caves] goto {} pick {}: {} (room {}, of {} spots; {} ms)", id, pick, s.at.toShortString(), s.room, spots.size(), Math.round(ms));
        src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "%s at %s (found in %.0f ms)", id,
                s.at.toShortString(), ms)).withColor(0x9FE6D6), false);
    }

    /**
     * Every dry floor in the region round {@code near}, two air cells over it and no lava near, best room first. A ray
     * stops where the region does, so a room inside it beats one on its edge (a tour sees the region, not its neighbour).
     */
    private static List<Spot> spots(ServerLevel level, CaveRegion region, BlockPos near, int cx, int cz) {
        List<Spot> out = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int x0 = (cx - NEAR) << 4, x1 = ((cx + NEAR + 1) << 4) - 1, z0 = (cz - NEAR) << 4, z1 = ((cz + NEAR + 1) << 4) - 1;
        int y0 = Math.max(level.getMinY() + 1, near.getY() - SPAN), y1 = Math.min(CaveRegion.MAX_Y, near.getY() + SPAN);
        // rays near the loaded land's edge stop there, so its middle wins
        for (int x = x0 + STEP; x <= x1 - STEP; x += STEP) {
            for (int z = z0 + STEP; z <= z1 - STEP; z += STEP) {
                for (int y = y0; y <= y1; y++) {
                    if (!open(level, m.set(x, y, z)) || !open(level, m.set(x, y + 1, z))) continue;
                    if (open(level, m.set(x, y - 1, z))) continue;
                    if (!level.getBiome(m.set(x, y, z)).is(region.biome()) || lavaNear(level, m, x, y, z)) continue;
                    int room = 0, best = -1;
                    float yaw = 0F;
                    for (int d = 0; d < 8; d++) {
                        double a = d * Math.PI / 4;
                        int run = 0;
                        while (run < RAY && open(level, m.set(x + (int) Math.round(-Math.sin(a) * (run + 1)), y + 1,
                                z + (int) Math.round(Math.cos(a) * (run + 1)))) && level.getBiome(m).is(region.biome())) run++;
                        room += run;
                        if (run > best) {
                            best = run;
                            yaw = d * 45F;
                        }
                    }
                    out.add(new Spot(new BlockPos(x, y, z), room, yaw));
                }
            }
        }
        out.sort(Comparator.comparingInt((Spot s) -> s.room).reversed());
        return out;
    }

    private static boolean lavaNear(ServerLevel level, BlockPos.MutableBlockPos m, int x, int y, int z) {
        for (int dx = -LAVA_CLEAR; dx <= LAVA_CLEAR; dx++) for (int dz = -LAVA_CLEAR; dz <= LAVA_CLEAR; dz++) {
            if (level.getChunkSource().getChunkNow(x + dx >> 4, z + dz >> 4) == null) continue;
            for (int dy = -1; dy <= 2; dy++) if (level.getFluidState(m.set(x + dx, y + dy, z + dz)).is(FluidTags.LAVA)) return true;
        }
        return false;
    }

    /** Air (or a plant) without water, in loaded land: nothing here makes a chunk. */
    private static boolean open(ServerLevel level, BlockPos.MutableBlockPos m) {
        if (level.getChunkSource().getChunkNow(m.getX() >> 4, m.getZ() >> 4) == null) return false;
        return level.getBlockState(m).getCollisionShape(level, m).isEmpty() && level.getFluidState(m).isEmpty();
    }
}
