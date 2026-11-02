package dev.awaken.arms.anvil;

import dev.awaken.core.AwakenCommands;
import dev.awaken.core.CoreModule;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The War Anvil (SPEC §46): weapons forged by hand, a cinematic of your own (called from {@code ArmsModule.init}). */
public final class AnvilModule {
    private AnvilModule() {}

    public static void init(IEventBus modBus) {
        AnvilContent.register(modBus);
        modBus.addListener(AnvilModule::registerPayloads);
        modBus.addListener(AnvilModule::creativeTab);
        WarAnvil.init();
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> WarAnvil.forget(e.getEntity().getUUID()));
        registerCommands();
        WeaponGoals.register();
        AnvilTests.register();
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToServer(AnvilPayloads.Act.TYPE, AnvilPayloads.Act.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) WarAnvil.onAct(sp, msg);
        });
        r.playToClient(AnvilPayloads.Open.TYPE, AnvilPayloads.Open.STREAM_CODEC);
        r.playToClient(AnvilPayloads.Done.TYPE, AnvilPayloads.Done.STREAM_CODEC);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CoreModule.TAB_KEY)) event.accept(AnvilContent.ANVIL_ITEM.get());
    }

    private static void registerCommands() {
        // dev and tours: begin the Longsword at the nearest War Anvil, as its window's Forge it would
        AwakenCommands.add((root, ctx) -> root.then(Commands.literal("arms").then(Commands.literal("anvil").requires(AwakenCommands.admin())
                .then(Commands.literal("forge").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (!(p.level() instanceof ServerLevel level)) return 0;
                    WarAnvilBlockEntity be = nearest(level, p);
                    if (be == null) {
                        c.getSource().sendFailure(Component.literal("No War Anvil within " + WarAnvil.REACH + " blocks."));
                        return 0;
                    }
                    WarAnvil.start(p, level, be);
                    return 1;
                })))));
    }

    private static WarAnvilBlockEntity nearest(ServerLevel level, ServerPlayer p) {
        WarAnvilBlockEntity best = null;
        double bestD = Double.MAX_VALUE;
        var at = p.blockPosition();
        int r = WarAnvil.REACH;
        for (var q : net.minecraft.core.BlockPos.betweenClosed(at.offset(-r, -2, -r), at.offset(r, 2, r))) {
            if (!(level.getBlockEntity(q) instanceof WarAnvilBlockEntity be)) continue;
            double d = q.distSqr(at);
            if (d < bestD) {
                bestD = d;
                best = be;
            }
        }
        return best;
    }
}
