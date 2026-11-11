package dev.awaken.caves;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.Awaken;
import dev.awaken.core.AwakenCommands;
import dev.awaken.core.api.Passage;
import dev.awaken.delve.api.Lodes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import dev.awaken.caves.skin.CaveSkin;
import dev.awaken.caves.skin.SkinBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

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

    record Stand(double x, double y, double z, float yaw, float pitch) {}

    /** How many chunks round the command the growth search reads. */
    private static final int GROWTH_CHUNKS = 7;

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            var ids = CaveRegion.ORDER.stream().map(r -> r.id).toList();
            root.then(Commands.literal("caves").requires(AwakenCommands.admin()).then(Commands.literal("goto")
                    .then(Commands.argument("region", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(ids, b))
                            .executes(c -> go(c, 0))
                            .then(Commands.argument("pick", IntegerArgumentType.integer(0, 9))
                                    .executes(c -> go(c, IntegerArgumentType.getInteger(c, "pick"))))))
                    .then(Commands.literal("growth").then(Commands.argument("pick", IntegerArgumentType.integer(0, 49))
                            .executes(c -> growth(c, IntegerArgumentType.getInteger(c, "pick"), 8, false))
                            .then(Commands.argument("distance", IntegerArgumentType.integer(2, 14))
                                    .executes(c -> growth(c, IntegerArgumentType.getInteger(c, "pick"), IntegerArgumentType.getInteger(c, "distance"), false)))))
                    .then(Commands.literal("heart").then(Commands.argument("pick", IntegerArgumentType.integer(0, 49))
                            .then(Commands.argument("distance", IntegerArgumentType.integer(2, 14))
                                    .executes(c -> growth(c, IntegerArgumentType.getInteger(c, "pick"), IntegerArgumentType.getInteger(c, "distance"), true)))))
                    // SPEC §42.4 "Settled": what floats in the land round here, and the re-check run by hand
                    .then(Commands.literal("floaters").executes(c -> Floaters.census(c.getSource(), 6, -1))
                            .then(Commands.argument("radius", IntegerArgumentType.integer(0, Floaters.MAX_RADIUS))
                                    .executes(c -> Floaters.census(c.getSource(), IntegerArgumentType.getInteger(c, "radius"), -1))
                                    .then(Commands.literal("look").then(Commands.argument("pick", IntegerArgumentType.integer(0, 49))
                                            .executes(c -> Floaters.census(c.getSource(), IntegerArgumentType.getInteger(c, "radius"),
                                                    IntegerArgumentType.getInteger(c, "pick")))))))
                    .then(Commands.literal("at").then(Commands.argument("x", IntegerArgumentType.integer())
                            .then(Commands.argument("y", IntegerArgumentType.integer()).then(Commands.argument("z", IntegerArgumentType.integer())
                                    .executes(c -> Floaters.at(c.getSource(), IntegerArgumentType.getInteger(c, "x"),
                                            IntegerArgumentType.getInteger(c, "y"), IntegerArgumentType.getInteger(c, "z")))))))
                    .then(Commands.literal("resettle").executes(c -> Floaters.resettle(c.getSource(), 6))
                            .then(Commands.argument("radius", IntegerArgumentType.integer(0, Floaters.MAX_RADIUS))
                                    .executes(c -> Floaters.resettle(c.getSource(), IntegerArgumentType.getInteger(c, "radius"))))));
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

    /**
     * {@code /awaken caves growth <pick> [distance]} (admin; for tours): to a spot on the floor looking at a bloom of a grown ember
     * vine in the loaded land round where the command runs (SPEC §42.10), nearest first and each at least {@link #APART} from the
     * ones before. The spot goes to the log, so another build can be shot from exactly there. {@code /awaken caves heart} is the
     * same for lode hearts open to the air (SPEC §42.11).
     */
    private static int growth(CommandContext<CommandSourceStack> c, int pick, int distance, boolean hearts) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = p.level();
        BlockPos from = BlockPos.containing(c.getSource().getPosition());
        var vine = CaveSkin.EMBER_VINE.get();
        List<BlockPos> blooms = new ArrayList<>();
        int cx = from.getX() >> 4, cz = from.getZ() >> 4;
        for (int dx = -GROWTH_CHUNKS; dx <= GROWTH_CHUNKS; dx++) {
            for (int dz = -GROWTH_CHUNKS; dz <= GROWTH_CHUNKS; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null) continue;
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    if (section.hasOnlyAir() || !section.maybeHas(st -> hearts ? Lodes.isHeart(st) : st.is(vine))) continue;
                    int y0 = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                BlockState st = section.getBlockState(x, y, z);
                                if (hearts ? Lodes.isHeart(st) : st.is(vine) && st.getValue(SkinBlocks.EmberVine.BLOOM)) blooms.add(new BlockPos((cx + dx << 4) + x, y0 + y, (cz + dz << 4) + z));
                            }
                        }
                    }
                }
            }
        }
        blooms.sort(Comparator.comparingDouble(b -> b.distSqr(from)));
        List<BlockPos> picks = new ArrayList<>();
        Stand stand = null;
        for (BlockPos b : blooms) {
            if (!picks.stream().allMatch(q -> q.distSqr(b) >= APART * APART)) continue;
            Direction wall = hearts ? openSide(level, b) : level.getBlockState(b).getValue(SkinBlocks.EmberVine.FACING);
            Stand s = wall == null ? null : stand(level, b, wall, distance);
            if (s == null) continue;
            picks.add(b);
            if (picks.size() > pick) {
                stand = s;
                break;
            }
        }
        if (stand == null) {
            Awaken.LOG.info("[caves] growth pick {}: only {} of {} blooms can be seen", pick, picks.size(), blooms.size());
            c.getSource().sendFailure(Component.literal("Only " + picks.size() + " blooms in view here, of " + blooms.size() + "."));
            return 0;
        }
        BlockPos bloom = picks.get(pick);
        p.teleportTo(level, stand.x, stand.y, stand.z, Set.of(), stand.yaw, stand.pitch, true);
        Awaken.LOG.info(String.format(Locale.ROOT, "[caves] growth pick %d, distance %d: stand %.2f %.2f %.2f %.1f %.1f, bloom at %s, of %d blooms",
                pick, distance, stand.x, stand.y, stand.z, stand.yaw, stand.pitch, bloom.toShortString(), blooms.size()));
        return 1;
    }

    private static boolean solidAt(ServerLevel level, BlockPos b) {
        return Lodes.isHeart(level.getBlockState(b));
    }

    /** The way a block's open side looks back into it (the direction of the wall, seen from the air), or null if it's buried. */
    private static @Nullable Direction openSide(ServerLevel level, BlockPos b) {
        for (Direction d : Direction.Plane.HORIZONTAL) if (level.getBlockState(b.relative(d)).isAir()) return d.getOpposite();
        return null;
    }

    /** Where to stand to see a bloom from about this far: on a floor, with nothing solid between, a little to one side if that's open. */
    static @Nullable Stand stand(ServerLevel level, BlockPos bloom, Direction wall, int distance) {
        Direction out = wall.getOpposite();
        // the face toward the air: for a vine that's its plane, for a heart its open side
        Vec3 target = Vec3.atCenterOf(bloom).add(wall.getStepX() * (solidAt(level, bloom) ? -0.52 : 0.3), 0, wall.getStepZ() * (solidAt(level, bloom) ? -0.52 : 0.3));
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int d = distance; d >= 2; d--) {
            for (int side : new int[] {d / 2, -d / 2, 0}) {
                BlockPos column = bloom.relative(out, d).relative(out.getClockWise(), side);
                for (int dy = 2; dy >= -8; dy--) {
                    int x = column.getX(), y = column.getY() + dy, z = column.getZ();
                    if (!open(level, m.set(x, y, z)) || !open(level, m.set(x, y + 1, z)) || open(level, m.set(x, y - 1, z))) continue;
                    Vec3 eye = new Vec3(x + 0.5, y + 1.62, z + 0.5);
                    if (level.clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty())).getType() != HitResult.Type.MISS) break;
                    double dx = target.x - eye.x, dz = target.z - eye.z;
                    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)), pitch = (float) -Math.toDegrees(Math.atan2(target.y - eye.y, Math.sqrt(dx * dx + dz * dz)));
                    return new Stand(x + 0.5, y, z + 0.5, yaw, pitch);
                }
            }
        }
        return null;
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
