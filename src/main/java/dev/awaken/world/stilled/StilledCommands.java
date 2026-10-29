package dev.awaken.world.stilled;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.core.AwakenCommands;
import dev.awaken.core.api.Passage;
import dev.awaken.core.api.TheLull;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@code /awaken stilled ...} for game masters: {@code remove} (the Stilled or left chest nearest you within 8 blocks
 * goes, for good: its spot is never used again), {@code list} (the placed ones within 3,000 blocks) and {@code place
 * <pose> <carry>} (one at your feet, for looking at poses). Tours: {@code view <aldo|north1|north2|hamlet|chest>
 * [distance] [side]} stands you looking at a north-road figure (or the first left chest) from in front of it, turned
 * {@code side} degrees round it; a figure not placed yet gets you beside its spot, so the placer sets it within its
 * next check (about 5 s), and a second view frames it.
 */
final class StilledCommands {
    private StilledCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            var st = Commands.literal("stilled").requires(AwakenCommands.admin());
            st.then(Commands.literal("remove").executes(StilledCommands::remove));
            st.then(Commands.literal("list").executes(StilledCommands::list));
            st.then(Commands.literal("place").then(Commands.argument("pose", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(StilledText.Pose.values()).map(p -> p.name().toLowerCase(Locale.ROOT)), b))
                    .then(Commands.argument("carry", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"letter", "key", "keepsake"}, b))
                            .executes(StilledCommands::place))));
            st.then(Commands.literal("view").then(Commands.argument("spot", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"aldo", "north1", "north2", "hamlet", "chest"}, b))
                    .executes(c -> view(c, 3.2, 35))
                    .then(Commands.argument("distance", DoubleArgumentType.doubleArg(1.5, 40))
                            .executes(c -> view(c, DoubleArgumentType.getDouble(c, "distance"), 35))
                            .then(Commands.argument("side", DoubleArgumentType.doubleArg(-180, 180))
                                    .executes(c -> view(c, DoubleArgumentType.getDouble(c, "distance"), DoubleArgumentType.getDouble(c, "side")))))));
            root.then(st);
        });
    }

    static int remove(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = p.level();
        Stilled data = Stilled.get(level.getServer());
        BlockPos best = null;
        for (BlockPos at : BlockPos.betweenClosed(p.blockPosition().offset(-8, -4, -8), p.blockPosition().offset(8, 4, 8))) {
            var s = level.getBlockState(at);
            if (!s.is(StilledContent.STILLED.get()) && !s.is(StilledContent.LEFT_CHEST.get())) continue;
            if (best == null || at.distSqr(p.blockPosition()) < best.distSqr(p.blockPosition())) best = at.immutable();
        }
        if (best == null) {
            c.getSource().sendFailure(Component.literal("No Stilled or left chest within 8 blocks."));
            return 0;
        }
        long key = best.asLong();
        // the spot stays used (a figure never sits there again); a chest's settlement keeps its record so none stands there again
        for (Map.Entry<String, Long> e : data.placed.entrySet()) if (e.getValue() == key) e.setValue(Stilled.SKIPPED);
        for (Map.Entry<String, Long> e : data.chests.entrySet()) if (e.getValue() == key) e.setValue(Stilled.SKIPPED);
        data.setDirty();
        level.setBlock(best, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos gone = best;
        c.getSource().sendSuccess(() -> Component.literal("Removed the one at " + gone.toShortString() + "."), true);
        return 1;
    }

    static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Stilled data = Stilled.get(p.level().getServer());
        int n = 0;
        for (Map.Entry<String, Long> e : data.placed.entrySet()) {
            if (e.getValue() == Stilled.SKIPPED) continue;
            BlockPos at = BlockPos.of(e.getValue());
            if (Math.hypot(at.getX() - p.getX(), at.getZ() - p.getZ()) > 3000) continue;
            String line = e.getKey() + " at " + at.toShortString() + (p.level().getBlockEntity(at) instanceof StilledBlockEntity be
                    ? " (" + be.pose().name().toLowerCase(Locale.ROOT) + ", " + (be.carry.isEmpty() ? "nothing" : be.carry) + ")" : "");
            c.getSource().sendSuccess(() -> Component.literal(line), false);
            n++;
        }
        int found = n;
        c.getSource().sendSuccess(() -> Component.literal(found + " Stilled within 3,000 blocks; " + data.chests.size() + " left chest(s) in the world."), false);
        return n;
    }

    static int view(CommandContext<CommandSourceStack> c, double distance, double side) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = p.level().getServer().overworld();
        Stilled data = Stilled.get(level.getServer());
        String id = StringArgumentType.getString(c, "spot");
        if (id.equals("chest")) {
            for (long at : data.chests.values()) {
                if (at == Stilled.SKIPPED) continue;
                BlockPos pos = BlockPos.of(at);
                Passage.go(p, level, pos.getX() + 0.5, pos.getZ() + 0.5, () -> {
                    BlockState s = level.getBlockState(pos);
                    float facing = s.hasProperty(LeftChestBlock.FACING) ? s.getValue(LeftChestBlock.FACING).toYRot() : 0F;
                    frame(p, level, pos, facing, distance, side, 0.5);
                });
                return 1;
            }
            c.getSource().sendFailure(Component.literal("No left chest stands yet: carry a Stilled's key near its settlement's well."));
            return 0;
        }
        Long placed = data.placed.get(id);
        if (placed != null && placed == Stilled.SKIPPED) {
            c.getSource().sendFailure(Component.literal(id + " was never placed (its spot was dropped)."));
            return 0;
        }
        if (placed != null) {
            BlockPos pos = BlockPos.of(placed);
            Passage.go(p, level, pos.getX() + 0.5, pos.getZ() + 0.5, () -> {
                float facing = level.getBlockEntity(pos) instanceof StilledBlockEntity be ? be.yaw() : 0F;
                frame(p, level, pos, facing, distance, side, 1.0);
            });
            return 1;
        }
        BlockPos centre = TheLull.centre(level.getServer());
        int ridge = TheLull.ridge(level.getServer());
        for (StilledSpots.Spot s : StilledSpots.fixed(centre.getX(), centre.getZ(), ridge, StilledPlacer.firstHamlet(level, centre, ridge), level.getSeed())) {
            if (!s.id().equals(id)) continue;
            Passage.go(p, level, s.x() + 0.5, s.z() + 0.5, () -> {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s.x() + 8, s.z());
                p.teleportTo(level, s.x() + 8.5, y, s.z() + 0.5, Set.of(), p.getYRot(), 20F, true);
            });
            c.getSource().sendSuccess(() -> Component.literal(id + " isn't placed yet: standing by its spot (" + s.x() + ", " + s.z() + ")."), false);
            return 1;
        }
        c.getSource().sendFailure(Component.literal("No such spot: aldo, north1, north2, hamlet (when the first hamlet is far from the road) or chest."));
        return 0;
    }

    /** Stands {@code p} on the ground {@code distance} from {@code at}, {@code side} degrees round from its front, looking at it. */
    static void frame(ServerPlayer p, ServerLevel level, BlockPos at, float facing, double distance, double side, double height) {
        double a = Math.toRadians(facing + side);
        double x = at.getX() + 0.5 - Math.sin(a) * distance, z = at.getZ() + 0.5 + Math.cos(a) * distance;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        double tx = at.getX() + 0.5, ty = at.getY() + height, tz = at.getZ() + 0.5, eye = y + 1.62;
        float yaw = (float) Math.toDegrees(Math.atan2(-(tx - x), tz - z));
        float pitch = (float) -Math.toDegrees(Math.atan2(ty - eye, Math.hypot(tx - x, tz - z)));
        p.teleportTo(level, x, y, z, Set.of(), yaw, pitch, true);
    }

    static int place(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        StilledText.Pose pose;
        try {
            pose = StilledText.Pose.valueOf(StringArgumentType.getString(c, "pose").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            c.getSource().sendFailure(Component.literal("No such pose."));
            return 0;
        }
        String carry = StringArgumentType.getString(c, "carry");
        long seed = p.level().getGameTime() * 31 + p.blockPosition().asLong();
        StilledSpots.Spot spot = new StilledSpots.Spot("dev_" + Long.toHexString(seed & 0xFFFFFF), p.getBlockX(), p.getBlockZ(), StilledSpots.Where.CELL,
                pose, pose == StilledText.Pose.PAIR ? StilledText.Look.MAN : StilledText.Look.values()[(int) Math.floorMod(seed, 4L)], carry, -1, "", seed);
        BlockPos at = p.blockPosition();
        ServerLevel level = p.level();
        level.setBlock(at, StilledContent.STILLED.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(at) instanceof StilledBlockEntity be)) return 0;
        Stilled data = Stilled.get(level.getServer());
        StilledPlacer.fill(level, data, be, new StilledPlacer.Ready(spot, at, p.getYRot() + 180F, null));
        be.changed();
        data.placed.put(spot.id(), at.asLong());
        data.setDirty();
        p.teleportTo(p.getX(), p.getY() + 2.1, p.getZ());
        c.getSource().sendSuccess(() -> Component.literal("Placed " + spot.id() + " (" + be.pose() + ", " + be.carry + ")."), true);
        return 1;
    }
}
