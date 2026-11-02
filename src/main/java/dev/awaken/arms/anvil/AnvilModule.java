package dev.awaken.arms.anvil;

import com.mojang.brigadier.arguments.StringArgumentType;
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
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The War Anvil (SPEC §46): weapons forged by hand, a cinematic of your own (called from {@code ArmsModule.init}). */
public final class AnvilModule {
    private AnvilModule() {}

    public static void init(IEventBus modBus) {
        AnvilContent.register(modBus);
        modBus.addListener(AnvilModule::registerPayloads);
        modBus.addListener(AnvilModule::creativeTab);
        WarAnvil.init();
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            WarAnvil.forget(e.getEntity().getUUID());
            AnvilNow.forget(e.getEntity().getUUID());
        });
        NeoForge.EVENT_BUS.addListener(WarAnvil::lordFell);
        // a weapon of ours held without a story gains its Plain one (read twice a second: a component check, no allocation)
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer sp && sp.tickCount % 10 == 0) WarAnvil.storyOnHold(sp);
        });
        registerCommands();
        WeaponGoals.register();
        AnvilNow.register();
        dev.awaken.core.api.Sources.declare("forged at a War Anvil (SPEC §46.7): by hand at E, in the Forge's heat from D",
                AnvilCosts.table().keySet().toArray(String[]::new));
        AnvilTests.register();
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToServer(AnvilPayloads.Act.TYPE, AnvilPayloads.Act.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) WarAnvil.onAct(sp, msg);
        });
        r.playToClient(AnvilPayloads.Open.TYPE, AnvilPayloads.Open.STREAM_CODEC);
        r.playToClient(AnvilPayloads.Done.TYPE, AnvilPayloads.Done.STREAM_CODEC);
        r.playToClient(AnvilPayloads.Held.TYPE, AnvilPayloads.Held.STREAM_CODEC);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CoreModule.TAB_KEY)) event.accept(AnvilContent.ANVIL_ITEM.get());
    }

    private static void registerCommands() {
        // dev and tours: /awaken arms anvil forge [weapon [memory]] (the Beastbound Longsword when none is named), and
        // /awaken arms anvil held <temper|reforge|attune> with the weapon in your main hand (SPEC §46.8)
        AwakenCommands.add((root, ctx) -> root.then(Commands.literal("arms").then(Commands.literal("anvil").requires(AwakenCommands.admin())
                .then(Commands.literal("forge").executes(c -> forge(c.getSource(), WarAnvil.WEAPON, false))
                        .then(Commands.argument("weapon", StringArgumentType.word())
                                .executes(c -> forge(c.getSource(), StringArgumentType.getString(c, "weapon"), false))
                                .then(Commands.literal("memory").executes(c -> forge(c.getSource(), StringArgumentType.getString(c, "weapon"), true)))))
                .then(Commands.literal("held")
                        .then(Commands.literal("temper").executes(c -> held(c.getSource(), AnvilWork.TEMPER)))
                        .then(Commands.literal("reforge").executes(c -> held(c.getSource(), AnvilWork.REFORGE)))
                        .then(Commands.literal("attune").executes(c -> held(c.getSource(), AnvilWork.ATTUNE)))))));
    }

    /** Dev and tours: temper, reforge or attune the weapon in your main hand at the nearest War Anvil; says why not in the window's words. */
    private static int held(net.minecraft.commands.CommandSourceStack src, int kind) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        if (!(p.level() instanceof ServerLevel level)) return 0;
        WarAnvilBlockEntity be = nearest(level, p);
        if (be == null) {
            src.sendFailure(Component.literal("No War Anvil within " + WarAnvil.REACH + " blocks."));
            return 0;
        }
        if (AnvilHeld.begin(p, level, be, kind)) return 1;
        String why = "Hold one of your weapons, with the anvil free.";
        if (dev.awaken.arms.ArmsItems.of(p.getMainHandItem().getItem()) != null && be.work() == null && be.slot().isEmpty())
            for (AnvilPayloads.Option o : AnvilHeld.options(p, level, be.getBlockPos(), p.getMainHandItem())) if (o.kind() == kind) why = o.line();
        src.sendFailure(Component.literal(why));
        return 0;
    }

    /** Dev and tours: begin a weapon (or a blade alone) at the nearest War Anvil, as the Armoury's Forge it would; says why not. */
    private static int forge(net.minecraft.commands.CommandSourceStack src, String weapon, boolean memory) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        if (!(p.level() instanceof ServerLevel level)) return 0;
        WarAnvilBlockEntity be = nearest(level, p);
        if (be == null) {
            src.sendFailure(Component.literal("No War Anvil within " + WarAnvil.REACH + " blocks."));
            return 0;
        }
        String why = WarAnvil.missing(p, level, be.getBlockPos(), weapon, memory);
        if (why != null) {
            src.sendFailure(Component.literal(why));
            return 0;
        }
        return WarAnvil.begin(p, level, be, weapon, memory) ? 1 : 0;
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
