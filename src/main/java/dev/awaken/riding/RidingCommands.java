package dev.awaken.riding;

import dev.awaken.core.AwakenCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** {@code /awaken riding status | with <player> | bring <player> | off}: the K menu's buttons, by hand. */
final class RidingCommands {
    private RidingCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> root.then(Commands.literal("riding")
                .then(Commands.literal("status").executes(c -> status(c.getSource())))
                .then(Commands.literal("with").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    Bonds.ask(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "player"), true);
                    return 1;
                })))
                .then(Commands.literal("bring").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    Bonds.ask(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "player"), false);
                    return 1;
                })))
                .then(Commands.literal("off").executes(c -> {
                    Bonds.letGo(c.getSource().getPlayerOrException());
                    return 1;
                }))));
    }

    private static int status(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        ServerPlayer other = Bonds.partner(p);
        String line = other == null ? "You aren't tied to anyone."
                : (Bonds.rider(p) ? "You're riding along with " : "Riding along with you: ") + other.getGameProfile().name()
                + ", " + (int) Math.sqrt(p.distanceToSqr(other)) + " blocks away.";
        src.sendSuccess(() -> Component.literal(line), false);
        return 1;
    }
}
