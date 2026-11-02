package dev.awaken.gates.client;

import dev.awaken.gates.net.GatePayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ItemPickupParticle;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A gate monster's drops flying into its killer (SPEC §13, "Gate spoils"). The loot is already in the killer's Satchel
 * on the server; these are client-only copies (negative ids, never sent anywhere) that pop out of the monster like a
 * drop, hang a moment and fly in with vanilla's pickup flight and sound. Nothing here can be picked up or duplicated.
 */
final class SpoilsFlights {
    private SpoilsFlights() {}

    private static final int HANG = 10;
    /** Client-only entity ids: below anything the server hands out. */
    private static int nextId = -0x4000_0000;
    private static int ticks;

    private record Flight(ItemEntity item, int killer, int due) {}

    private static final List<Flight> FLIGHTS = new ArrayList<>();

    static void start(GatePayloads.SpoilsFlight msg) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        for (ItemStack s : msg.items()) {
            ItemEntity e = new ItemEntity(level, msg.x(), msg.y(), msg.z(), s.copy()); // a drop's own pop: a little up, a little aside
            e.setId(nextId--);
            e.setNeverPickUp();
            level.addEntity(e);
            FLIGHTS.add(new Flight(e, msg.killer(), ticks + HANG));
        }
    }

    static void tick(ClientTickEvent.Post e) {
        ticks++;
        if (FLIGHTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        for (Iterator<Flight> it = FLIGHTS.iterator(); it.hasNext(); ) {
            Flight f = it.next();
            if (level != null && f.item.level() == level && ticks < f.due && !f.item.isRemoved()) continue;
            it.remove();
            if (level == null || f.item.level() != level || f.item.isRemoved()) continue;
            Entity to = level.getEntity(f.killer);
            if (to != null) {
                EntityRenderState state = mc.getEntityRenderDispatcher().extractEntity(f.item, 1.0F);
                mc.particleEngine.add(new ItemPickupParticle(level, state, to, f.item.getDeltaMovement()));
                level.playLocalSound(f.item.getX(), f.item.getY(), f.item.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F,
                        (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 1.4F + 2.0F, false);
            }
            level.removeEntity(f.item.getId(), Entity.RemovalReason.DISCARDED);
        }
    }

    static void clear() {
        FLIGHTS.clear();
    }
}
