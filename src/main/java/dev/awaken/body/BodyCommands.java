package dev.awaken.body;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.awaken.body.net.BodyPayloads;
import dev.awaken.core.AwakenCommands;
import dev.awaken.core.AwakenNet;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /awaken body}: the moves your body has and how to do them (never the ones still to come). Game masters:
 * {@code try <roll|sprint|slide|mantle|slidejump|tacslide|hit|release> [delay]} makes your client press the keys of a
 * move as a player would (the autopilot can't hold keys; the delay lines a frame up with a screenshot), {@code forget}
 * clears which unlocks were announced.
 */
final class BodyCommands {
    private BodyCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            var body = Commands.literal("body").executes(c -> list(c.getSource()));
            var admin = Commands.literal("try").requires(AwakenCommands.admin())
                    .then(Commands.argument("move", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(BodyPayloads.TRIES, b))
                            .executes(c -> tryMove(c.getSource(), StringArgumentType.getString(c, "move"), 0))
                            .then(Commands.argument("delay", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 200))
                                    .executes(c -> tryMove(c.getSource(), StringArgumentType.getString(c, "move"),
                                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "delay")))));
            body.then(admin);
            body.then(Commands.literal("forget").requires(AwakenCommands.admin()).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                p.setData(BodyContent.MEMORY, BodyMemory.EMPTY);
                c.getSource().sendSuccess(() -> Component.literal("The body forgot its announcements"), false);
                return 1;
            }));
            root.then(body);
        });
    }

    private static int list(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        int n = 0;
        for (Move m : Move.values()) {
            if (!Body.unlocked(p, m)) continue;
            src.sendSuccess(() -> Component.literal(m.title + ": ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(m.how).withStyle(ChatFormatting.WHITE)), false);
            n++;
        }
        if (n == 0) src.sendSuccess(() -> Component.literal("You move like anyone else. For now.").withStyle(ChatFormatting.GRAY), false);
        return n;
    }

    private static int tryMove(CommandSourceStack src, String name, int delay) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        int what = -1;
        for (int i = 0; i < BodyPayloads.TRIES.length; i++) if (BodyPayloads.TRIES[i].equals(name)) what = i;
        if (what < 0) {
            src.sendFailure(Component.literal("Unknown move " + name));
            return 0;
        }
        // the server's own input checks (sprinting, a held crouch) are what the keys would satisfy; the client presses
        // them for real, this only covers the moment before the server has seen those key states
        Move move = switch (name) {
            case "roll" -> Move.ROLL;
            case "sprint" -> Move.TACTICAL_SPRINT;
            case "slide", "slidejump", "tacslide" -> Move.SLIDE;
            case "mantle" -> Move.MANTLE;
            default -> null;
        };
        BodyServer.assist(p, move, 20 * 10 + delay);
        AwakenNet.send(p, new BodyPayloads.Try(what, delay));
        return 1;
    }
}
