package dev.awaken.world.stilled;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.core.AwakenCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /awaken stilled ...} for game masters: {@code remove} (the Stilled or left chest nearest you within 8 blocks
 * goes, for good: its spot is never used again), {@code list} (the placed ones within 3,000 blocks) and {@code place
 * <pose> <carry>} (one at your feet, for looking at poses).
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
