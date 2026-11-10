package dev.awaken.finds;

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

/**
 * {@code /awaken finds ...} for game masters and tours (SPEC §58.9): {@code place <kind>} stands one up on a level pad
 * where you are; {@code gallery [set]} stands every kind (or one set) in a row running east from you, each on its own
 * pad; {@code near} lists the world's own finds within 400 blocks; {@code locate <kind>} looks for the nearest.
 */
final class FindsCommands {
    private FindsCommands() {}

    static void init() {
        AwakenCommands.add((root, ctx) -> {
            var f = Commands.literal("finds").requires(AwakenCommands.admin());
            f.then(Commands.literal("place").then(Commands.argument("kind", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(Kinds.all().stream().map(Kind::id), b)).executes(FindsCommands::place)));
            f.then(Commands.literal("gallery").executes(c -> gallery(c, ""))
                    .then(Commands.argument("set", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(Kind.FSet.values()).map(s -> s.name().toLowerCase(java.util.Locale.ROOT)), b))
                            .executes(c -> gallery(c, StringArgumentType.getString(c, "set")))));
            f.then(Commands.literal("near").executes(FindsCommands::near));
            f.then(Commands.literal("locate").then(Commands.argument("kind", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(Kinds.all().stream().map(Kind::id), b)).executes(FindsCommands::locate)));
            root.then(f);
        });
    }

    private static int place(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Kind k = Kinds.byId(StringArgumentType.getString(c, "kind"));
        if (k == null) {
            c.getSource().sendFailure(Component.literal("No find is called that."));
            return 0;
        }
        ServerLevel level = (ServerLevel) p.level();
        Site s = FindsFeature.stamp(level, k, p.blockPosition().below(), p.getDirection().get2DDataValue(), level.random.nextLong());
        c.getSource().sendSuccess(() -> Component.literal(k.name() + " at " + s.x() + " " + s.y() + " " + s.z()), false);
        return 1;
    }

    private static int gallery(CommandContext<CommandSourceStack> c, String set) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) p.level();
        BlockPos at = p.blockPosition().below();
        int x = at.getX() + 6, n = 0;
        for (Kind k : Kinds.all()) {
            if (!set.isEmpty() && !k.set().name().equalsIgnoreCase(set)) continue;
            int r = k.reach() + 4;
            x += r;
            FindsFeature.stamp(level, k, new BlockPos(x, at.getY(), at.getZ()), 0, 1000L + n * 7919L);
            x += r;
            n++;
        }
        int count = n, end = x;
        c.getSource().sendSuccess(() -> Component.literal(count + " finds stood east of you, as far as x " + end), false);
        return n;
    }

    private static int near(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) p.level();
        var sites = Sites.near(level, p.getBlockX() - 400, p.getBlockZ() - 400, p.getBlockX() + 400, p.getBlockZ() + 400, 0);
        StringBuilder sb = new StringBuilder(sites.size() + " finds within 400 blocks:");
        for (Site s : sites) sb.append("\n  ").append(s.kind().name()).append(" at ").append(s.x()).append(' ').append(s.y()).append(' ').append(s.z());
        c.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return sites.size();
    }

    private static int locate(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) p.level();
        Kind k = Kinds.byId(StringArgumentType.getString(c, "kind"));
        if (k == null) {
            c.getSource().sendFailure(Component.literal("No find is called that."));
            return 0;
        }
        Site best = null;
        long bestD = Long.MAX_VALUE;
        for (int ring = 0; ring <= 6 && best == null; ring++) {
            int r = 600 * (ring + 1);
            for (Site s : Sites.near(level, p.getBlockX() - r, p.getBlockZ() - r, p.getBlockX() + r, p.getBlockZ() + r, 0)) {
                if (s.kind() != k) continue;
                long dx = s.x() - p.getBlockX(), dz = s.z() - p.getBlockZ(), d = dx * dx + dz * dz;
                if (d < bestD) {
                    bestD = d;
                    best = s;
                }
            }
        }
        if (best == null) {
            c.getSource().sendFailure(Component.literal("None within 4,200 blocks."));
            return 0;
        }
        Site s = best;
        c.getSource().sendSuccess(() -> Component.literal(k.name() + " at " + s.x() + " " + s.y() + " " + s.z()), false);
        return 1;
    }
}
