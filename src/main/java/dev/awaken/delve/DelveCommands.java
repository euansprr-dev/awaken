package dev.awaken.delve;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.core.AwakenCommands;
import dev.awaken.delve.api.Lodes;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.Optional;

/**
 * {@code /awaken delve}: the lodes within 96 of you (ore, how far, which way). Admin, for tours and tests:
 * {@code stage cave [ore]} (a chamber round you with a lode on its north wall and a lantern), {@code stage rope} (a
 * shaft ahead with a piton and its rope), {@code fork <ore>} (a tuned fork), {@code ring} (strike the fork in hand on
 * the stone underfoot), {@code heart} (break the nearest heart within 16 as you), {@code cavein} (bring the nearest
 * heart's roof down), {@code heavy} (a heavy strike released on the glint), {@code demo strike|charge} (the strike's
 * feel on the block in view).
 */
final class DelveCommands {
    private DelveCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            var d = Commands.literal("delve").executes(DelveCommands::status);
            var ores = Arrays.stream(Ore.values()).map(Ore::id).toList();
            d.then(Commands.literal("stage").requires(AwakenCommands.admin())
                    .then(Commands.literal("cave").executes(c -> stageCave(c, Ore.IRON))
                            .then(Commands.argument("ore", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(ores, b))
                                    .executes(c -> stageCave(c, ore(c)))))
                    .then(Commands.literal("rope").executes(DelveCommands::stageRope)));
            d.then(Commands.literal("fork").requires(AwakenCommands.admin())
                    .then(Commands.argument("ore", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(ores, b))
                            .executes(DelveCommands::fork)));
            d.then(Commands.literal("ring").requires(AwakenCommands.admin()).executes(DelveCommands::ring));
            d.then(Commands.literal("heart").requires(AwakenCommands.admin()).executes(DelveCommands::heart));
            d.then(Commands.literal("cavein").requires(AwakenCommands.admin()).executes(DelveCommands::caveIn));
            d.then(Commands.literal("heavy").requires(AwakenCommands.admin()).executes(DelveCommands::heavy));
            d.then(Commands.literal("demo").requires(AwakenCommands.admin())
                    .then(Commands.literal("strike").executes(c -> demo(c, DelveNet.Demo.BLOWS)))
                    .then(Commands.literal("charge").executes(c -> demo(c, DelveNet.Demo.CHARGE))));
            root.then(d);
        });
    }

    private static Ore ore(CommandContext<CommandSourceStack> c) {
        Ore o = Ore.byId(StringArgumentType.getString(c, "ore"));
        return o == null ? Ore.IRON : o;
    }

    private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        int shown = 0;
        for (Ore o : Ore.values()) {
            Optional<Lodes.View> v = Lodes.nearest(p.level(), p.blockPosition(), o, 96);
            if (v.isEmpty()) continue;
            shown++;
            BlockPos n = v.get().nearest();
            String way = way(n.getX() - p.getX(), n.getZ() - p.getZ());
            int dy = n.getY() - p.getBlockY();
            c.getSource().sendSuccess(() -> Component.literal(o.title + " · " + Math.round(v.get().distance()) + " blocks " + way
                    + (dy == 0 ? "" : dy > 0 ? ", " + dy + " up" : ", " + (-dy) + " down") + (v.get().heartBroken() ? " · opened" : "")).withColor(o.tint), false);
        }
        if (shown == 0) c.getSource().sendSuccess(() -> Component.literal("No lode within 96 blocks.").withColor(0xC8C2B0), false);
        return shown;
    }

    private static String way(double dx, double dz) {
        String[] names = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
        double deg = Math.toDegrees(Math.atan2(-dx, dz));
        return names[Math.floorMod((int) Math.round(deg / 45.0), 8)];
    }

    /** A chamber round the player (15 x 6 x 15), a lode of {@code ore} along its north wall with its heart in the middle, and a lantern. */
    private static int stageCave(CommandContext<CommandSourceStack> c, Ore ore) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = p.level();
        BlockPos o = p.blockPosition();
        boolean deep = o.getY() < 0;
        BlockState rock = (deep ? Blocks.DEEPSLATE : Blocks.STONE).defaultBlockState();
        for (int dx = -9; dx <= 9; dx++) for (int dz = -9; dz <= 9; dz++) for (int dy = -1; dy <= 7; dy++) {
            boolean inside = Math.abs(dx) <= 7 && Math.abs(dz) <= 7 && dy >= 0 && dy <= 5 - (Math.abs(dx) + Math.abs(dz) > 10 ? 1 : 0);
            level.setBlock(o.offset(dx, dy, dz), inside ? Blocks.AIR.defaultBlockState() : rock, Block.UPDATE_CLIENTS);
        }
        LongArrayList cells = new LongArrayList();
        BlockPos heart = o.offset(0, 2, -8);
        cells.add(heart.asLong());
        for (int dx = -6; dx <= 6; dx++) for (int dy = 1; dy <= 3; dy++) {
            if (dx == 0 && dy == 2) continue;
            if ((dy == 1 || dy == 3) && Math.floorMod(dx * 7 + dy * 3, 5) == 0) continue;   // a ragged band, not a slab
            cells.add(o.offset(dx, dy, -8).asLong());
            if (Math.floorMod(dx * 5 + dy, 3) == 0) cells.add(o.offset(dx, dy, -9).asLong());
        }
        Lode l = LodePlans.handmade(ore, cells.toLongArray());
        LodePlans.EXTRA.removeIf(x -> x.heartPos().distManhattan(heart) < 64);
        LodePlans.EXTRA.add(l);
        DelveData.get(level).forget(l.id(), heart);
        for (long cell : l.cells()) {
            BlockPos b = BlockPos.of(cell);
            level.setBlock(b, cell == l.heart() ? ore.heart().defaultBlockState() : ore.block(deep).defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        level.setBlock(o.offset(-2, 0, -5), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), Block.UPDATE_ALL);
        level.setBlock(o.offset(3, 0, -6), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), Block.UPDATE_ALL);
        if (ore != Ore.DEEPGLASS && deep) {
            // Deepglass in the east wall, some of it open to the chamber
            for (int dz = -2; dz <= 2; dz++) for (int dy = 1; dy <= 3; dy++) if (Math.floorMod(dz + dy, 2) == 0) level.setBlock(o.offset(8, dy, dz), DelveContent.DEEPGLASS.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        p.teleportTo(level, o.getX() + 0.5, o.getY(), o.getZ() + 2.5, java.util.Set.of(), 180F, 0F, true);
        c.getSource().sendSuccess(() -> Component.literal("Staged a " + ore.word() + " lode of " + l.size() + " on the north wall, heart at " + heart.toShortString()), false);
        return 1;
    }

    /** A shaft ahead of the player (3 x 3, 20 deep) with a piton on its far wall and its rope hanging. */
    private static int stageRope(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = p.level();
        Direction f = p.getDirection();
        BlockPos o = p.blockPosition().relative(f, 3);
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) for (int dy = -20; dy <= -1; dy++) {
            level.setBlock(o.offset(a, dy, b), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        for (int a = -2; a <= 2; a++) for (int b = -2; b <= 2; b++) {
            if (Math.abs(a) <= 1 && Math.abs(b) <= 1) continue;
            for (int dy = -21; dy <= -1; dy++) level.setBlock(o.offset(a, dy, b), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) level.setBlock(o.offset(a, -21, b), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        // the piton on the far wall, just under the lip
        BlockPos piton = o.relative(f, 1).below();
        level.setBlock(piton, DelveContent.PITON.get().defaultBlockState().setValue(PitonBlock.FACING, f.getOpposite()), Block.UPDATE_ALL);
        int n = PitonBlock.payOut(level, piton);
        c.getSource().sendSuccess(() -> Component.literal("Staged a shaft with a piton and " + n + " blocks of rope"), false);
        return n;
    }

    private static int fork(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ItemStack fork = new ItemStack(DelveContent.TUNING_FORK.get());
        fork.set(DelveContent.TUNED.get(), ore(c).id());
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, fork);
        return 1;
    }

    private static int ring(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ItemStack fork = p.getMainHandItem();
        Ore tuned = TuningForkItem.tuned(fork);
        if (!fork.is(DelveContent.TUNING_FORK.get()) || tuned == null) {
            c.getSource().sendFailure(Component.literal("Hold a tuned fork"));
            return 0;
        }
        Optional<BlockPos> t = TuningForkItem.strike(p.level(), p, fork, p.blockPosition().below(), tuned);
        c.getSource().sendSuccess(() -> Component.literal(t.map(b -> "It sings toward " + b.toShortString()).orElse("Nothing of it within 48")), false);
        return t.isPresent() ? 1 : 0;
    }

    private static Lode nearestHeart(ServerPlayer p) {
        BlockPos at = p.blockPosition();
        Lode best = null;
        double bd = 16 * 16;
        for (Lode l : LodePlans.touching(p.level().getSeed(), at.getX() - 16, at.getZ() - 16, at.getX() + 16, at.getZ() + 16, true)) {
            double d = l.heartPos().distSqr(at);
            if (d < bd) {
                bd = d;
                best = l;
            }
        }
        return best;
    }

    private static int heart(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Lode l = nearestHeart(p);
        if (l == null || !(p.level().getBlockState(l.heartPos()).getBlock() instanceof LodeHeartBlock)) {
            c.getSource().sendFailure(Component.literal("No lode heart within 16"));
            return 0;
        }
        p.gameMode.destroyBlock(l.heartPos());
        return 1;
    }

    private static int caveIn(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Lode l = nearestHeart(p);
        boolean ok = l != null && CaveIns.consider(p.level(), l.heartPos(), p, true);
        if (!ok) c.getSource().sendFailure(Component.literal("No chamber here that would come down"));
        return ok ? 1 : 0;
    }

    private static int heavy(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        HeavyStrikes.forget(p.getUUID());
        int n = HeavyStrikes.release(p, HeavyStrikes.GLINT, true).size();
        c.getSource().sendSuccess(() -> Component.literal("Bored " + n), false);
        return n;
    }

    private static int demo(CommandContext<CommandSourceStack> c, int kind) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        var hit = HeavyStrikes.aim(p);
        if (hit == null) {
            c.getSource().sendFailure(Component.literal("Look at a block"));
            return 0;
        }
        PacketDistributor.sendToPlayer(p, new DelveNet.Demo(kind, hit.getBlockPos().asLong(), hit.getDirection().get3DDataValue()));
        return 1;
    }
}
