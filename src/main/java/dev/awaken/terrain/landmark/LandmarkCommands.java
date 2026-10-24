package dev.awaken.terrain.landmark;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.Awaken;
import dev.awaken.core.AwakenCommands;
import dev.awaken.terrain.fault.FaultNoise;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Set;

/**
 * {@code /awaken terrain landmark <gate_scar|shard_drift|aurora_spire> [seed]} builds a landmark where you stand (a drift lies
 * along the fault there, or east-west where there is none); {@code /awaken terrain landmarkvisit <landmark> [distance
 * above]} stands you south of the nearest generated one, looking at it ({@code [terrain] landmarkvisit} in the log, for
 * the autopilot). Game masters only.
 */
public final class LandmarkCommands {
    private LandmarkCommands() {}

    public static void register() {
        AwakenCommands.add((root, ctx) -> {
            var t = Commands.literal("terrain").requires(AwakenCommands.admin());
            for (Landmark l : Landmark.values()) {
                t.then(Commands.literal("landmark").then(Commands.literal(l.getSerializedName())
                        .executes(c -> build(c, l, c.getSource().getLevel().getRandom().nextLong()))
                        .then(Commands.argument("seed", LongArgumentType.longArg()).executes(c -> build(c, l, LongArgumentType.getLong(c, "seed"))))));
                t.then(Commands.literal("landmarkvisit").then(Commands.literal(l.getSerializedName())
                        .executes(c -> visit(c, l, 80, 2))
                        .then(Commands.argument("distance", IntegerArgumentType.integer(0, 512))
                                .then(Commands.argument("above", IntegerArgumentType.integer(0, 256))
                                        .executes(c -> visit(c, l, IntegerArgumentType.getInteger(c, "distance"), IntegerArgumentType.getInteger(c, "above")))))));
            }
            t.then(Commands.literal("landscost").executes(c -> {
                String line = "chunks with faults near " + LandsClock.CHUNKS.sum() + ";" + LandsClock.report();
                Awaken.LOG.info("[terrain] landscost {}", line);
                c.getSource().sendSuccess(() -> Component.literal("Lands cost:" + line), false);
                return 1;
            }));
            root.then(t);
        });
    }

    private static int build(CommandContext<CommandSourceStack> c, Landmark l, long seed) {
        ServerLevel level = c.getSource().getLevel();
        BlockPos at = BlockPos.containing(c.getSource().getPosition());
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
        BlockPos centre = new BlockPos(at.getX(), y, at.getZ());
        long t0 = System.nanoTime();
        LandmarkPiece piece = switch (l) {
            case GATE_SCAR -> LandmarkPiece.scar(centre, seed);
            case SHARD_DRIFT -> drift(level, centre, seed);
            case AURORA_SPIRE -> LandmarkPiece.spire(centre, seed);
        };
        int placed = piece.build(level, piece.getBoundingBox());
        long ms = (System.nanoTime() - t0) / 1_000_000;
        Awaken.LOG.info("[terrain] landmark {} seed {} at {} {} {}: {} blocks in {} ms", l.getSerializedName(), seed, at.getX(), y, at.getZ(), placed, ms);
        c.getSource().sendSuccess(() -> Component.literal("Built " + l.getSerializedName() + " (seed " + seed + "): " + placed + " blocks"), false);
        return placed;
    }

    /** A drift along the fault where you stand (east-west where there is none), each shard over the ground under it. */
    private static LandmarkPiece drift(ServerLevel level, BlockPos centre, long seed) {
        float angle = 0F;
        var fault = FaultNoise.fault(level.getChunkSource().randomState());
        if (fault != null) {
            double gx = fault.get(centre.getX() + 2, 0, centre.getZ()) - fault.get(centre.getX() - 2, 0, centre.getZ());
            double gz = fault.get(centre.getX(), 0, centre.getZ() + 2) - fault.get(centre.getX(), 0, centre.getZ() - 2);
            angle = (float) (Math.atan2(gz, gx) + Math.PI / 2);
        }
        ShardDrift.Shard[] shards = ShardDrift.layout(seed, angle);
        int[] ground = new int[shards.length];
        for (int i = 0; i < shards.length; i++) {
            int x = centre.getX() + (int) Math.round(shards[i].x()), z = centre.getZ() + (int) Math.round(shards[i].z());
            ground[i] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - centre.getY();
        }
        return LandmarkPiece.drift(centre, seed, angle, ground);
    }

    /**
     * Finds the nearest landmark of that kind the world generated and stands you {@code distance} blocks south of its
     * centre, {@code above} blocks over the ground there, looking at it.
     */
    private static int visit(CommandContext<CommandSourceStack> c, Landmark l, int distance, int above) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        ServerLevel level = sp.level().getServer().overworld();
        var structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(LandmarkWorldgen.structure(l));
        if (structure.isEmpty()) {
            Awaken.LOG.info("[terrain] landmarkvisit {} none (no lands pack)", l.getSerializedName());
            c.getSource().sendFailure(Component.literal("This world has no " + l.getSerializedName()));
            return 0;
        }
        var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(structure.get()), sp.blockPosition(), 100, false);
        if (found == null) {
            Awaken.LOG.info("[terrain] landmarkvisit {} none within 1600 blocks", l.getSerializedName());
            c.getSource().sendFailure(Component.literal("No " + l.getSerializedName() + " nearby"));
            return 0;
        }
        BlockPos start = found.getFirst();
        var chunk = level.getChunk(start.getX() >> 4, start.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS);
        var st = chunk.getStartForStructure(structure.get().value());
        BlockPos centre = start;
        int top = 20, bottom = 0;
        if (st != null) {
            for (var piece : st.getPieces()) {
                if (piece instanceof LandmarkPiece lp) {
                    centre = lp.centre();
                    int[] b = lp.voxels().bounds();
                    top = b[4];
                    bottom = b[1];
                }
            }
        }
        int vx = centre.getX(), vz = centre.getZ() + distance;
        level.getChunk(vx >> 4, vz >> 4);
        int vy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, vx, vz);
        while (vy > level.getMinY() + 1) {
            var below = level.getBlockState(new BlockPos(vx, vy - 1, vz));
            if (!below.is(BlockTags.LOGS) && !below.is(BlockTags.LEAVES) && !below.canBeReplaced()) break;
            vy--;
        }
        vy += above;
        // look at the middle of what stands there (a drift's shards, a spire's crystal, a scar's spires and bowl)
        double aimY = switch (l) {
            case SHARD_DRIFT -> centre.getY() + (top + bottom) * 0.5;
            case AURORA_SPIRE -> centre.getY() + top * 0.4;
            case GATE_SCAR -> centre.getY() + Math.min(8, top * 0.3);
        };
        double aim = aimY - (vy + 1.62);
        float pitch = (float) -Math.toDegrees(Math.atan2(aim, Math.max(4, distance)));
        sp.teleportTo(level, vx + 0.5, vy + 0.1, vz + 0.5, Set.<Relative>of(), 180.0F, pitch, true);
        Awaken.LOG.info("[terrain] landmarkvisit {} centre {} {} {} spans {}..{}, viewed from {} {} {}", l.getSerializedName(), centre.getX(), centre.getY(),
                centre.getZ(), bottom, top, vx, vy, vz);
        BlockPos f = centre;
        c.getSource().sendSuccess(() -> Component.literal(l.getSerializedName() + " at " + f.toShortString()), false);
        return 1;
    }
}
