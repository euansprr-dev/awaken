package dev.awaken.hall;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.awaken.arms.ArmsArmoury;
import dev.awaken.core.AwakenCommands;
import dev.awaken.core.AwakenNet;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The Hall of Arms (SPEC §52): where you choose what to make and wear. Its screens are client-side; its numbers live here. */
public final class HallModule {
    private HallModule() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, e -> {
            var r = e.registrar("1");
            r.playToClient(HallNet.Wall.TYPE, HallNet.Wall.STREAM_CODEC);
            r.playToClient(HallNet.Show.TYPE, HallNet.Show.STREAM_CODEC);
            r.playToClient(HallNet.Suits.TYPE, HallNet.Suits.STREAM_CODEC);
            r.playToServer(HallNet.Ask.TYPE, HallNet.Ask.STREAM_CODEC, (m, ctx) -> {
                if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp) HallServer.onAsk(sp, m);
            });
        });
        HallTests.register();
        // dev: the wall from any door, a weapon brought close (tours have no way to aim at a niche by name)
        AwakenCommands.add((root, ctx) -> root.then(Commands.literal("hall").requires(AwakenCommands.admin())
                .then(Commands.literal("wall").then(Commands.argument("door", IntegerArgumentType.integer(0, 2)).executes(c -> {
                    ArmsArmoury.open(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "door"));
                    return 1;
                })))
                .then(Commands.literal("learn").then(Commands.argument("style", StringArgumentType.word()).executes(c ->
                        dev.awaken.arms.ArmsApiAccess.learn(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "style")) ? 1 : 0)))
                .then(Commands.literal("pattern").then(Commands.argument("set", StringArgumentType.word()).executes(c -> {
                    var set = dev.awaken.armour.ArmourSet.byId(StringArgumentType.getString(c, "set"));
                    if (set == null) return 0;
                    dev.awaken.armour.ArmourForge.learn(c.getSource().getPlayerOrException(), set);
                    return 1;
                })))
                .then(Commands.literal("suit").then(Commands.argument("set", StringArgumentType.word())
                        .then(Commands.argument("slot", IntegerArgumentType.integer(0, 3)).executes(c -> {
                            AwakenNet.send(c.getSource().getPlayerOrException(), new HallNet.Show("suit:" + StringArgumentType.getString(c, "set"),
                                    IntegerArgumentType.getInteger(c, "slot")));
                            return 1;
                        }))))
                // dev: a piece stood on the nearest Fitting Stand, as a finished forge would leave it (the one-suit rule, SPEC §44.14)
                .then(Commands.literal("fit").then(Commands.argument("set", StringArgumentType.word())
                        .then(Commands.argument("slot", IntegerArgumentType.integer(0, 3)).executes(c -> {
                            var set = dev.awaken.armour.ArmourSet.byId(StringArgumentType.getString(c, "set"));
                            return set != null && dev.awaken.armour.ArmourForge.fitForTour(c.getSource().getPlayerOrException(), set,
                                    IntegerArgumentType.getInteger(c, "slot")) ? 1 : 0;
                        }))))
                .then(Commands.literal("view").then(Commands.argument("what", StringArgumentType.word())
                        .then(Commands.argument("n", IntegerArgumentType.integer(0, 9)).executes(c -> {
                            AwakenNet.send(c.getSource().getPlayerOrException(), new HallNet.Show(StringArgumentType.getString(c, "what"),
                                    IntegerArgumentType.getInteger(c, "n")));
                            return 1;
                        }))))
                .then(Commands.literal("piece").then(Commands.argument("style", StringArgumentType.word())
                        .then(Commands.argument("tier", IntegerArgumentType.integer(0, 5)).executes(c -> {
                            AwakenNet.send(c.getSource().getPlayerOrException(), new HallNet.Show(StringArgumentType.getString(c, "style"),
                                    IntegerArgumentType.getInteger(c, "tier")));
                            return 1;
                        }))))));
    }
}
