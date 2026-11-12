package dev.awaken.together;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.core.AwakenCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * {@code /awaken together status | with <player> | leave | fights full|gentle}: the band's buttons, by hand (servers,
 * tests, tours). {@code /awaken together partner ...} plays a second player for tours (game masters).
 */
final class TogetherCommands {
    private TogetherCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> root.then(Commands.literal("together")
                .then(Commands.literal("status").executes(c -> status(c.getSource())))
                .then(Commands.literal("with").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    PartyService.ask(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "player"));
                    return 1;
                })))
                .then(Commands.literal("leave").executes(c -> {
                    PartyService.leave(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("share")
                        .then(Commands.literal("equal").executes(c -> share(c.getSource().getPlayerOrException(), SharedXp.EQUAL)))
                        .then(Commands.literal("half").executes(c -> share(c.getSource().getPlayerOrException(), SharedXp.HALF)))
                        .then(Commands.literal("own").executes(c -> share(c.getSource().getPlayerOrException(), SharedXp.OWN))))
                .then(Commands.literal("fights")
                        .then(Commands.literal("full").executes(c -> Fights.set(c.getSource().getPlayerOrException(), false) ? 1 : 0))
                        .then(Commands.literal("gentle").executes(c -> Fights.set(c.getSource().getPlayerOrException(), true) ? 1 : 0)))
                .then(Commands.literal("partner").requires(AwakenCommands.admin())
                        .then(Commands.literal("spawn").then(Commands.argument("name", StringArgumentType.word()).executes(c -> {
                            ServerPlayer me = c.getSource().getPlayerOrException();
                            ServerPlayer p = TourPartner.spawn(me, StringArgumentType.getString(c, "name"));
                            c.getSource().sendSuccess(() -> Component.literal(p.getGameProfile().name() + " is here. Set them up with /awaken as " + p.getGameProfile().name() + " ..."), false);
                            return 1;
                        })))
                        .then(Commands.literal("join").then(Commands.argument("name", StringArgumentType.word()).executes(c -> {
                            // no card: the partner has no hands to answer one
                            ServerPlayer me = c.getSource().getPlayerOrException();
                            ServerPlayer p = partner(c.getSource(), StringArgumentType.getString(c, "name"));
                            return p != null && PartyService.join(me, p) ? 1 : 0;
                        })))
                        .then(Commands.literal("here").then(Commands.argument("name", StringArgumentType.word()).executes(c -> {
                            ServerPlayer me = c.getSource().getPlayerOrException();
                            ServerPlayer p = partner(c.getSource(), StringArgumentType.getString(c, "name"));
                            if (p == null) return 0;
                            p.teleportTo(me.level(), me.getX() + 1.5, me.getY(), me.getZ(), java.util.Set.of(), me.getYRot(), 0, true);
                            return 1;
                        })))
                        .then(Commands.literal("strike").then(Commands.argument("name", StringArgumentType.word()).executes(c -> {
                            ServerPlayer p = partner(c.getSource(), StringArgumentType.getString(c, "name"));
                            return p != null && TourPartner.strike(p, 12).isPresent() ? 1 : 0;
                        })))
                        .then(Commands.literal("face").then(Commands.argument("name", StringArgumentType.word()).executes(c -> {
                            ServerPlayer me = c.getSource().getPlayerOrException();
                            ServerPlayer p = partner(c.getSource(), StringArgumentType.getString(c, "name"));
                            if (p == null) return 0;
                            TourPartner.face(p, me.getEyePosition());
                            return 1;
                        })))
                        .then(Commands.literal("ping").then(Commands.argument("name", StringArgumentType.word()).executes(c -> {
                            // the partner points at the ground three blocks ahead of where they stand
                            ServerPlayer p = partner(c.getSource(), StringArgumentType.getString(c, "name"));
                            if (p == null) return 0;
                            TogetherNet.pingFrom(p, p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(3)), "Over here");
                            return 1;
                        })))
                        .then(Commands.literal("hurt").then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("amount", FloatArgumentType.floatArg(0)).executes(c -> {
                                    ServerPlayer p = partner(c.getSource(), StringArgumentType.getString(c, "name"));
                                    if (p == null) return 0;
                                    p.hurtServer(p.level(), p.damageSources().generic(), FloatArgumentType.getFloat(c, "amount"));
                                    return 1;
                                }))))
                        .then(Commands.literal("remove").then(Commands.argument("name", StringArgumentType.word()).executes(c ->
                                TourPartner.remove(c.getSource().getServer(), StringArgumentType.getString(c, "name")) ? 1 : 0))))));
    }

    private static ServerPlayer partner(CommandSourceStack src, String name) {
        ServerPlayer p = TourPartner.get(name);
        if (p == null) src.sendFailure(Component.literal("There's no tour partner called " + name + ". Spawn one first."));
        return p;
    }

    private static int status(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        PartyData.Party party = PartyService.settled(p);
        StringBuilder line = new StringBuilder();
        if (party == null) {
            line.append("You aren't travelling with anyone.");
        } else {
            line.append("Travelling together:");
            for (UUID id : party.members) {
                ServerPlayer m = src.getServer().getPlayerList().getPlayer(id);
                line.append(' ').append(m != null ? m.getGameProfile().name() : "(away)");
                if (m != null && Fights.gentle(m)) line.append(" (Gentle)");
            }
            line.append('.');
        }
        line.append(" Your fights are ").append(Fights.gentle(p) ? "Gentle, with " + Fights.windsLeft(p) + " second winds left." : "Full.");
        src.sendSuccess(() -> Component.literal(line.toString()), false);
        return 1;
    }

    private static int share(ServerPlayer p, int share) {
        SharedXp.set(p, share);
        return 1;
    }
}
