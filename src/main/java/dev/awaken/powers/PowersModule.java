package dev.awaken.powers;

import dev.awaken.core.api.PowerCoreHooks;
import dev.awaken.powers.catalog.PowerCatalog;
import dev.awaken.powers.content.PowerContent;
import dev.awaken.powers.net.PowerPayloads;
import dev.awaken.progression.Rank;
import dev.awaken.progression.RewardHooks;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Entry point for the powers module (SPEC section 6): the power framework and catalog, Power Cores, energy and
 * cooldowns, key casting, the Five Forces, and combat feel. Client side lives in {@code powers.client}.
 */
public final class PowersModule {
    private PowersModule() {}

    public static void init(IEventBus modBus) {
        PowerCatalog.bootstrap();
        PowerSounds.SOUNDS.register(modBus);
        PowerContent.init(modBus);
        dev.awaken.powers.fx.PowerFx.init(modBus); // power visuals: particle types, FX payload, FX tests
        modBus.addListener(PowersModule::registerPayloads);
        PowerEvents.register(NeoForge.EVENT_BUS);
        // the Horizon line: Flight takes you to the sky sites
        dev.awaken.core.api.HorizonHooks.addReach((p, need) -> need.equals(dev.awaken.core.api.HorizonHooks.WINGS) && Powers.owns(p, "awaken:flight"));
        RewardHooks.powerName = id -> { PowerType t = PowerRegistry.get(id); return t == null ? id : t.name; };
        RewardHooks.powerLearner = (player, id) -> {
            Identifier parsed = Identifier.tryParse(id);
            if (parsed != null) Powers.learn(player, parsed, 0);
        };
        PowerCoreHooks.install(new PowerCoreHooks.Roller() {
            @Override
            public ItemStack roll(Rank rank, RandomSource random) {
                PowerType t = PowerRegistry.roll(rank, random);
                return t == null ? ItemStack.EMPTY : PowerContent.core(t, PowerRegistry.rollVariant(t, random));
            }

            @Override
            public ItemStack forPower(String powerId, int variant) {
                PowerType t = PowerRegistry.get(powerId);
                return t == null ? ItemStack.EMPTY : PowerContent.core(t, variant);
            }
        });
        Starter.init(modBus); // the first-power choice on awakening (plan v2 §4.1)
        PowersCommands.register();
        PowersTests.register();
        PowerPreviewTests.register();
        PowerHitTests.register();
        MoveTwistTests.register(); // the Awakened Body's family twists
        MasteryCommands.register();
        MasteryTests.register();
        PowerLadderTests.register(); // the ladders (SPEC 36.9)
        TogetherPowerTests.register();
        StarterTests.register();
        PowersTests.registerIsolated(modBus);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToServer(PowerPreviews.Request.TYPE, PowerPreviews.Request.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p) PowerPreviews.request(p, msg);
        });
        r.playToClient(PowerPreviews.Reply.TYPE, PowerPreviews.Reply.STREAM_CODEC);
        r.playToServer(PowerPayloads.Cast.TYPE, PowerPayloads.Cast.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) Casting.onPayload(sp, msg);
        });
        r.playToServer(PowerPayloads.Equip.TYPE, PowerPayloads.Equip.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) Powers.equip(sp, msg.slot(), msg.power());
        });
        r.playToClient(PowerPayloads.Feedback.TYPE, PowerPayloads.Feedback.STREAM_CODEC);
        r.playToClient(PowerPayloads.Feel.TYPE, PowerPayloads.Feel.STREAM_CODEC);
        r.playToClient(PowerPayloads.Marks.TYPE, PowerPayloads.Marks.STREAM_CODEC);
        r.playToClient(PowerPayloads.Beam.TYPE, PowerPayloads.Beam.STREAM_CODEC);
    }
}
