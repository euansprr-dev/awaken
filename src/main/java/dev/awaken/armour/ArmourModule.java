package dev.awaken.armour;

import dev.awaken.armour.net.ArmourPayloads;
import dev.awaken.core.api.Sources;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The Forged Armour (SPEC §44, docs/armour/PLAN.md): 29 sets drawn as sculpted meshes on the body's bones by
 * {@code armour.client.ArmourLayer}, in place of vanilla's flat shell, and made in the Forge Song at a Fitting Stand
 * ({@link ArmourForge}). P0: the six Hunter sets (module {@code gear}) in their new look; P1: the Forge Song; P2: the
 * E and D sets' own items ({@link ArmourItems}), their patterns ({@link ArmourPatterns}) and what wearing a set does.
 */
public final class ArmourModule {
    private ArmourModule() {}

    public static void init(IEventBus modBus) {
        ArmourContent.register(modBus);
        ArmourItems.init(modBus);
        modBus.addListener(ArmourModule::registerPayloads);
        NeoForge.EVENT_BUS.addListener(ArmourModule::onEquip);
        NeoForge.EVENT_BUS.addListener(ArmourPatterns::onDeath);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            ArmourForge.forget(e.getEntity().getUUID());
            if (e.getEntity() instanceof ServerPlayer sp) ArmourAbilities.onLogout(sp);
        });
        NeoForge.EVENT_BUS.addListener(ArmourAbilities::onTick);
        NeoForge.EVENT_BUS.addListener(ArmourAbilities::onIncoming);
        NeoForge.EVENT_BUS.addListener(ArmourAbilities::onDamaged);
        NeoForge.EVENT_BUS.addListener(ArmourAbilities::onTracking);
        ArmourAbilities.init();
        String[] hunter = new String[24];
        int i = 0;
        for (ArmourSet s : ArmourSet.values()) {
            if (s.hunter == null) continue;
            for (String piece : new String[]{"helmet", "chestplate", "leggings", "boots"}) hunter[i++] = s.id + "_" + piece;
        }
        ArmourNow.register();
        Sources.declare("forged at a Fitting Stand, in the Forge Song (SPEC §44.4); E sets by hand at a works table", hunter);
        Sources.declare("forged at a Fitting Stand, in the Forge Song, once its set's pattern is known (SPEC §44.4)", ArmourItems.pieceIds());
        Sources.declare("carried by gate creatures of its rank: elites and wardens sometimes, the gate's Lord always (SPEC §44.4)",
                ArmourItems.patternIds());
        ArmourTests.register();
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToServer(ArmourPayloads.Act.TYPE, ArmourPayloads.Act.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) ArmourForge.onAct(sp, msg);
        });
        r.playToClient(ArmourPayloads.Offers.TYPE, ArmourPayloads.Offers.STREAM_CODEC);
        r.playToClient(ArmourPayloads.Done.TYPE, ArmourPayloads.Done.STREAM_CODEC);
        r.playToClient(ArmourPayloads.Charge.TYPE, ArmourPayloads.Charge.STREAM_CODEC);
    }

    /** A Hunter piece made before the Forge Song gains its story, Plain, the first time it is worn (SPEC §44.7). */
    private static void onEquip(LivingEquipmentChangeEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || e.getSlot().getType() != EquipmentSlot.Type.HUMANOID_ARMOR) return;
        ArmourAbilities.state(sp).dirty = true;
        ItemStack to = e.getTo();
        if (to.has(ArmourContent.FORGED.get())) return;
        ArmourLooks.Look look = ArmourLooks.of(to);
        if (look != null) to.set(ArmourContent.FORGED.get(), Forged.plain(look.set()));
    }
}
