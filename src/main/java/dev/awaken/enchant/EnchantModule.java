package dev.awaken.enchant;

import dev.awaken.core.api.ForgeHooks;
import dev.awaken.core.api.Sources;
import dev.awaken.enchant.net.EnchantPayloads;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Enchanting (SPEC §45): the Inscribing Table, pages learned once and for good, our wards and banes, room and depth from
 * the forge, and vanilla's enchanting folded in.
 */
public final class EnchantModule {
    private EnchantModule() {}

    public static void init(IEventBus modBus) {
        EnchantContent.register(modBus);
        modBus.addListener(EnchantModule::registerPayloads);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(EnchantEffects::onIncoming);
        // a bane last of all, after the Arms damage stack (SPEC §45.6)
        bus.addListener(EventPriority.LOWEST, LivingIncomingDamageEvent.class, EnchantEffects::onIncomingLast);
        bus.addListener(EnchantEffects::onPlayerTick);
        bus.addListener(EnchantEffects::onDrops);
        bus.addListener(EnchantFx::onBlockDrops);
        bus.addListener(EnchantFx::onLootingDrops);
        bus.addListener(EnchantFx::onPickupXp);
        bus.addListener(EnchantFx::onFall);
        bus.addListener(Words::onPlayerTick);
        bus.addListener(Words::onLogout);
        bus.addListener(Words::onDeath);
        // after Harvest's extra and the glints: what Gather takes is the whole of what fell
        bus.addListener(net.neoforged.bus.api.EventPriority.LOW, Words::onBlockDrops);
        bus.addListener(net.neoforged.bus.api.EventPriority.LOW, Words::onLivingDrops);
        bus.addListener(Lantern::onPlayerTick);
        bus.addListener(Lantern::onLogout);
        bus.addListener(Lantern::onStopping);
        bus.addListener(Pages::onDeath);
        bus.addListener(Doors::onDeath);
        bus.addListener(Doors::onBreak);
        bus.addListener(Pages::onUseItem);
        bus.addListener(Fold::onUseBlock);
        bus.addListener(Fold::onAnvil);
        bus.addListener(Fold::onGrindstoneTake);
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) Inscribe.logout(sp);
        });
        Pages.install();
        EnchantGoals.register();
        ForgeHooks.cardLine(s -> EnchantRules.room(s) > 0 ? EnchantRules.roomLine(s) : null);
        Sources.declare("a gate's Lord carries its kind's page; the village scribe sells a few; old books teach theirs (enchant.Pages, SPEC §45.2)",
                "enchantment_page");
        Sources.declare("built into the Precursor ruins (the vaults, the monolith's chamber, the ocean lab, the sky platform, the spire's crown) through EnchantApi.placeStone; never held (enchant.WordStones, SPEC §45.14)",
                "word_stone");
        dev.awaken.core.api.EnchantApi.stones(WordStones::place);
        Sources.neverHeld("the Inscribing Table's tool, only drawn in your hand while you write (enchant.client)", "burin");
        Sources.neverHeld("the light a Lantern helmet sheds: set and cleared by the helmet, nothing to see or hold (enchant.Lantern, SPEC §45.16)", "lantern_glow");
        EnchantCommands.register();
        EnchantTests.register();
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToServer(EnchantPayloads.Act.TYPE, EnchantPayloads.Act.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) Inscribe.onAct(sp, msg);
        });
        r.playToClient(EnchantPayloads.Open.TYPE, EnchantPayloads.Open.STREAM_CODEC);
        r.playToClient(EnchantPayloads.State.TYPE, EnchantPayloads.State.STREAM_CODEC);
        r.playToClient(EnchantPayloads.Done.TYPE, EnchantPayloads.Done.STREAM_CODEC);
        r.playToClient(EnchantPayloads.Closed.TYPE, EnchantPayloads.Closed.STREAM_CODEC);
        r.playToClient(EnchantPayloads.Fx.TYPE, EnchantPayloads.Fx.STREAM_CODEC);
    }
}
