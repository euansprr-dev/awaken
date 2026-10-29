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
 * ({@link ArmourForge}). P0: the six Hunter sets (module {@code gear}) in their new look; P1: the Forge Song.
 */
public final class ArmourModule {
    private ArmourModule() {}

    public static void init(IEventBus modBus) {
        ArmourContent.register(modBus);
        modBus.addListener(ArmourModule::registerPayloads);
        NeoForge.EVENT_BUS.addListener(ArmourModule::onEquip);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> ArmourForge.forget(e.getEntity().getUUID()));
        String[] hunter = new String[24];
        int i = 0;
        for (ArmourSet s : ArmourSet.values()) {
            if (s.hunter == null) continue;
            for (String piece : new String[]{"helmet", "chestplate", "leggings", "boots"}) hunter[i++] = s.id + "_" + piece;
        }
        ArmourNow.register();
        Sources.declare("forged at a Fitting Stand, in the Forge Song (SPEC §44.4); E sets by hand at a works table", hunter);
        ArmourTests.register();
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToServer(ArmourPayloads.Act.TYPE, ArmourPayloads.Act.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) ArmourForge.onAct(sp, msg);
        });
        r.playToClient(ArmourPayloads.Offers.TYPE, ArmourPayloads.Offers.STREAM_CODEC);
        r.playToClient(ArmourPayloads.Done.TYPE, ArmourPayloads.Done.STREAM_CODEC);
    }

    /** A Hunter piece made before the Forge Song gains its story, Plain, the first time it is worn (SPEC §44.7). */
    private static void onEquip(LivingEquipmentChangeEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer) || e.getSlot().getType() != EquipmentSlot.Type.HUMANOID_ARMOR) return;
        ItemStack to = e.getTo();
        if (to.has(ArmourContent.FORGED.get())) return;
        ArmourLooks.Look look = ArmourLooks.of(to);
        if (look != null) to.set(ArmourContent.FORGED.get(), Forged.plain(look.set()));
    }
}
