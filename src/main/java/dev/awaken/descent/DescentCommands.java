package dev.awaken.descent;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.awaken.core.AwakenCommands;
import dev.awaken.descent.player.Strain;
import dev.awaken.descent.well.WellData;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /awaken descent go | well | rule | strain clear | travel | finale | seed. */
final class DescentCommands {
    private DescentCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> root.then(Commands.literal("descent")
                .then(Commands.literal("go").requires(AwakenCommands.admin())
                        .then(Commands.argument("n", IntegerArgumentType.integer(0, Strata.COUNT))
                                .executes(c -> go(c, IntegerArgumentType.getInteger(c, "n"))))
                        .then(Commands.literal("endless")
                                .then(Commands.argument("d", IntegerArgumentType.integer(1, 4096))
                                        .executes(c -> go(c, Strata.ENDLESS_BASE + IntegerArgumentType.getInteger(c, "d"))))))
                .then(Commands.literal("well").executes(DescentCommands::well))
                .then(Commands.literal("rule")
                        .then(Commands.argument("n", IntegerArgumentType.integer(1, Strata.COUNT))
                                .executes(c -> rule(c, IntegerArgumentType.getInteger(c, "n")))))
                .then(Commands.literal("strain").then(Commands.literal("clear").requires(AwakenCommands.admin())
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            Strain.clear(p);
                            c.getSource().sendSuccess(() -> Component.literal("Strain cleared."), false);
                            return 1;
                        })))
                .then(Commands.literal("travel")
                        .then(Commands.argument("depth", IntegerArgumentType.integer(0, DescentTravel.RIM_ENTRANCE))
                                .executes(c -> {
                                    DescentWaystones.travel(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "depth"));
                                    return 1;
                                })))
                .then(Commands.literal("finale").requires(AwakenCommands.admin()).executes(c -> {
                    var boss = Descent.beginFinale(c.getSource().getPlayerOrException());
                    c.getSource().sendSuccess(() -> Component.literal(boss != null ? "The finale begins." : "Stand in stratum 10 (and only one finale at a time)."), false);
                    return boss != null ? 1 : 0;
                }))
                .then(Commands.literal("seed").requires(AwakenCommands.admin()).executes(c -> {
                    Descent.completeSeed(c.getSource().getPlayerOrException());
                    return 1;
                }))));
    }

    private static int go(CommandContext<CommandSourceStack> c, int depth) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        DescentTravel.send(p, depth, DescentTravel.Arrival.ENTRANCE, false);
        c.getSource().sendSuccess(() -> Component.literal("Going to " + Strata.label(depth)), true);
        return 1;
    }

    private static int well(CommandContext<CommandSourceStack> c) {
        WellData d = WellData.get(c.getSource().getServer());
        if (!d.built()) {
            c.getSource().sendFailure(Component.literal(d.state == WellData.CHOSEN
                    ? "The Well is being raised at " + d.x + " " + d.z + "." : "The Well has not been placed yet."));
            return 0;
        }
        var pos = c.getSource().getPosition();
        double dx = d.x + 0.5 - pos.x, dz = d.z + 0.5 - pos.z;
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        String dir = compass(dx, dz);
        boolean here = c.getSource().getLevel().dimension() == net.minecraft.world.level.Level.OVERWORLD;
        c.getSource().sendSuccess(() -> Component.literal("The Well: " + d.x + " " + d.y + " " + d.z
                + (here ? " · " + dist + " blocks " + dir : " (in the overworld)")).withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    static String compass(double dx, double dz) {
        String[] names = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};
        double a = Math.atan2(dz, dx);
        int i = (int) Math.round(a / (Math.PI / 4));
        return names[Math.floorMod(i, 8)];
    }

    private static int rule(CommandContext<CommandSourceStack> c, int n) {
        Strata.Def def = Strata.def(n);
        c.getSource().sendSuccess(() -> Component.literal(Strata.label(n) + " · opens at " + def.opensAt().label + "-rank\n" + def.rule()), false);
        if (c.getSource().getPlayer() instanceof ServerPlayer p) {
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(Strata.label(n)))
                    .row(Component.literal("Rule"), Component.literal(def.rule()))
                    .row(Component.literal("Opens at"), Component.literal(def.opensAt().label + "-rank"))
                    .ttl(20 * 10).build());
        }
        return 1;
    }
}
