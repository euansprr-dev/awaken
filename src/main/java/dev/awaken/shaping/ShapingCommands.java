package dev.awaken.shaping;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.core.AwakenCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * For tours: {@code /awaken admin shaping knock <pos> <side> <top|mid|under> [times]} is the Mallet's own knock on that side
 * of the block, high, in the middle or low, as many times as asked (each knock at the same place steps through the cuts).
 * The side needn't be open: a knock "south top" on a block with a neighbour to the south is the knock on its top near that
 * edge, which is what a corner's block needs.
 */
final class ShapingCommands {
    private ShapingCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> root.then(Commands.literal("admin").requires(AwakenCommands.admin()).then(Commands.literal("shaping")
                .then(Commands.literal("knock").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("side", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(
                                new String[] {"north", "south", "east", "west"}, b))
                                .then(Commands.argument("part", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        new String[] {"top", "mid", "under"}, b))
                                        .executes(c -> knock(c, 1))
                                        .then(Commands.argument("times", IntegerArgumentType.integer(1, 4))
                                                .executes(c -> knock(c, IntegerArgumentType.getInteger(c, "times")))))))))));
    }

    private static int knock(CommandContext<CommandSourceStack> c, int times) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        if (!(sp.level() instanceof ServerLevel level)) return 0;
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, level, "pos");
        Direction side = Direction.byName(StringArgumentType.getString(c, "side"));
        String part = StringArgumentType.getString(c, "part");
        if (side == null || !side.getAxis().isHorizontal() || !(part.equals("top") || part.equals("mid") || part.equals("under"))) {
            c.getSource().sendFailure(Component.literal("side is north, south, east or west; part is top, mid or under"));
            return 0;
        }
        double y = part.equals("top") ? 0.9 : part.equals("under") ? 0.1 : 0.5;
        Vec3 hit = new Vec3(pos.getX() + 0.5 + side.getStepX() * 0.5, pos.getY() + y, pos.getZ() + 0.5 + side.getStepZ() * 0.5);
        int done = 0;
        for (int i = 0; i < times; i++) if (Shaping.knock(sp, level, pos, side, hit)) done++;
        return done;
    }
}
