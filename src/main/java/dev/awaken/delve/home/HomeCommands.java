package dev.awaken.delve.home;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.core.AwakenCommands;
import dev.awaken.descent.Strata;
import dev.awaken.descent.world.Layout;
import dev.awaken.descent.world.Terrain;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Admin tools for tours and tests (SPEC §28.5): {@code /awaken delve home} (your trail), {@code home stage} (a winding
 * tunnel carved down from where you stand, its trail walked as if you'd come by it, you at its end looking back, the
 * thread run), {@code home thread} (run the thread again), {@code vent stage} (a tall cavern carved round you, a magma
 * floor with a vent on it), {@code descent grotto [n]} (to the first grotto of stratum n, 3 by default).
 */
final class HomeCommands {
    private HomeCommands() {}

    /** Blocks the stage's tunnel runs, and how far down it goes at most. */
    static final int TUNNEL_LEN = 64, TUNNEL_DROP = 30;
    /** The vent stage's cavern: radius and height. */
    static final int CAVERN_R = 10, CAVERN_H = 30;

    static void register() {
        AwakenCommands.add((root, ctx) -> root.then(Commands.literal("delve")
                .then(Commands.literal("home").requires(AwakenCommands.admin()).executes(HomeCommands::status)
                        .then(Commands.literal("stage").executes(HomeCommands::stageHome))
                        .then(Commands.literal("thread").executes(c -> {
                            HomeServer.thread(c.getSource().getPlayerOrException());
                            return 1;
                        })))
                .then(Commands.literal("vent").requires(AwakenCommands.admin())
                        .then(Commands.literal("stage").executes(HomeCommands::stageVent)))
                .then(Commands.literal("descent").requires(AwakenCommands.admin())
                        .then(Commands.literal("grotto").executes(c -> grotto(c, 3))
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, Strata.COUNT))
                                        .executes(c -> grotto(c, IntegerArgumentType.getInteger(c, "n"))))))));
    }

    private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        HomeTrail t = p.getData(HomeModule.TRAIL.get());
        String s = t.isEmpty() ? "No way home known (stand under the open sky)."
                : t.text(p.getBlockX(), p.getBlockY(), p.getBlockZ()) + " · " + t.size() + " crumbs, " + Math.round(t.length(p.getBlockX(), p.getBlockY(), p.getBlockZ()))
                + " blocks walked" + (t.shown() ? " · on the Now line" : " · not shown");
        c.getSource().sendSuccess(() -> Component.literal(s), false);
        return 1;
    }

    private static BlockState rockAt(int y) {
        return (y < 0 ? Blocks.DEEPSLATE : Blocks.STONE).defaultBlockState();
    }

    private static boolean solid(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.getFluidState().isEmpty() && s.isSolidRender();
    }

    /**
     * A winding tunnel 3 wide and 3 high carved down from where you stand along the way you face (a block down every
     * two, to {@link #TUNNEL_DROP} down, swinging 4 to each side), floored and roofed where it needs it, a little glow
     * lichen on its roof; its trail as if you'd walked it in, you at the far end looking back up it; the thread runs.
     */
    private static int stageHome(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = p.level();
        if (level.dimension() != Level.OVERWORLD) {
            c.getSource().sendFailure(Component.literal("The way home is the overworld's."));
            return 0;
        }
        Direction f = p.getDirection(), side = f.getClockWise();
        BlockPos start = p.blockPosition();
        List<BlockPos> centre = new ArrayList<>();
        for (int i = 0; i <= TUNNEL_LEN; i++) {
            int lateral = (int) Math.round(Math.sin(i / 9.0) * 4.0);
            centre.add(start.relative(f, i).relative(side, lateral).below(Math.min(i / 2, TUNNEL_DROP)));
        }
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        BlockState lichen = Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.UP), true);
        for (int i = 0; i < centre.size(); i++) {
            BlockPos at = centre.get(i);
            for (int w = -1; w <= 1; w++) {
                BlockPos col = at.relative(side, w);
                for (int h = 0; h <= 2; h++) level.setBlock(col.above(h), Blocks.AIR.defaultBlockState(), flags);
                if (!solid(level, col.below())) level.setBlock(col.below(), rockAt(col.getY() - 1), flags);
                // a roof past the mouth, so it reads as a tunnel all the way down
                if (i > 6 && !solid(level, col.above(3))) level.setBlock(col.above(3), rockAt(col.getY() + 3), flags);
            }
            if (i > 6 && i % 9 == 4) level.setBlock(at.above(2), lichen, flags);
        }
        // the trail: a crumb every 4 blocks along the middle, the mouth (under the sky) first
        long[] path = new long[centre.size() / HomeTrail.CRUMB + 1];
        for (int i = 0; i < path.length; i++) path[i] = centre.get(Math.min(i * HomeTrail.CRUMB, centre.size() - 1)).asLong();
        HomeTrail t = p.getData(HomeModule.TRAIL.get());
        t.stage(path, level.getGameTime());
        BlockPos end = centre.getLast(), back = centre.get(centre.size() - 5);
        p.teleportTo(level, end.getX() + 0.5, end.getY(), end.getZ() + 0.5, Set.of(), yaw(end, back), 18F, true);
        HomeServer.thread(p);
        c.getSource().sendSuccess(() -> Component.literal("A way home staged: " + t.text(end.getX(), end.getY(), end.getZ())), false);
        return 1;
    }

    /**
     * A cavern {@link #CAVERN_R} round and {@link #CAVERN_H} high carved round you, sealed with rock where it opens,
     * a deepslate floor, three small lava pockets on its far side, and the vent on a magma crust six ahead of you.
     */
    private static int stageVent(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = p.level();
        Direction f = p.getDirection();
        BlockPos feet = p.blockPosition();
        BlockPos mid = feet.relative(f, 6);
        int floor = feet.getY() - 1;
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -CAVERN_R - 2; dx <= CAVERN_R + 2; dx++) {
            for (int dz = -CAVERN_R - 2; dz <= CAVERN_R + 2; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > CAVERN_R + 1.6) continue;
                int x = mid.getX() + dx, z = mid.getZ() + dz;
                // a dome: the roof comes down toward the walls
                int roof = floor + (int) Math.round(CAVERN_H * Math.sqrt(Math.max(0, 1 - Math.pow(Math.min(d, CAVERN_R) / (CAVERN_R + 2.0), 2))));
                for (int y = floor - 1; y <= floor + CAVERN_H + 2; y++) {
                    m.set(x, y, z);
                    boolean inside = d <= CAVERN_R && y > floor && y < roof;
                    if (inside) level.setBlock(m, Blocks.AIR.defaultBlockState(), flags);
                    else if (y <= floor || y <= roof + 1) level.setBlock(m, rockAt(y), flags);
                }
            }
        }
        for (int k = 0; k < 3; k++) {
            double a = Math.toRadians(f.toYRot() + 50 * (k - 1));
            int x = mid.getX() + (int) Math.round(-Math.sin(a) * 7), z = mid.getZ() + (int) Math.round(Math.cos(a) * 7);
            level.setBlock(m.set(x, floor, z), Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
        }
        VentFeature.dress(level, new BlockPos(mid.getX(), floor, mid.getZ()), VentFeature.Kind.LAVA, RandomSource.create(mid.asLong()));
        p.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, Set.of(), f.toYRot(), 0F, true);
        c.getSource().sendSuccess(() -> Component.literal("A vent staged 6 ahead of you, over magma, in a cavern " + CAVERN_H + " high."), false);
        return 1;
    }

    /** To the first grotto of stratum {@code n}: standing on its floor across the pool, looking at it. */
    private static int grotto(CommandContext<CommandSourceStack> c, int n) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Terrain.hasTunnels(Strata.formOf(n))) {
            c.getSource().sendFailure(Component.literal("Stratum " + n + " has no tunnels (try 1, 2, 3, 4, 8 or 10)."));
            return 0;
        }
        var server = c.getSource().getServer();
        ServerLevel target = server.getLevel(Strata.dimensionOf(n));
        if (target == null) {
            c.getSource().sendFailure(Component.literal("That stratum does not exist in this world."));
            return 0;
        }
        Terrain t = Terrain.forDepth(n, target.getSeed());
        if (t.grottos().isEmpty()) {
            c.getSource().sendFailure(Component.literal("No grotto in stratum " + n + "."));
            return 0;
        }
        Terrain.Grotto g = t.grottos().getFirst();
        double back = g.a() * 0.62, turn = g.turn() + Math.PI;
        BlockPos at = Layout.world(n, new BlockPos((int) Math.floor(g.x() + Math.cos(turn) * back), g.floorY(), (int) Math.floor(g.z() + Math.sin(turn) * back)));
        BlockPos centre = Layout.world(n, new BlockPos(g.x(), g.floorY(), g.z()));
        float yaw = yaw(at, centre);
        // there once its land is made (SPEC §28.9, "Arrivals")
        dev.awaken.core.api.Passage.go(p, target, at.getX() + 0.5, at.getZ() + 0.5, () -> {
            // room to stand, should a spire of crystal have grown just there
            for (int h = 0; h <= 1; h++) if (!target.getBlockState(at.above(h)).isAir()) target.setBlock(at.above(h), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            p.teleportTo(target, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), yaw, 12F, true);
            p.resetFallDistance();
        });
        c.getSource().sendSuccess(() -> Component.literal("To stratum " + n + "'s grotto at " + centre.toShortString()
                + (g.cache() ? " (with a cache)" : "")), false);
        return 1;
    }

    /** The yaw that looks from a to b (Minecraft's: 0 is +z, 90 is -x). */
    static float yaw(BlockPos a, BlockPos b) {
        return (float) Mth.wrapDegrees(Math.toDegrees(Math.atan2(-(b.getX() - a.getX()), b.getZ() - a.getZ())));
    }
}
