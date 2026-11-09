package dev.awaken.delve.forged;

import dev.awaken.delve.TrueStrikes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * How a forged pick digs and what it remembers (SPEC §49.3, §49.6). Its speed stands on the item; what the smith did, a
 * Dressing's edge and bluntness go on top here, on both sides. The tally is held per player and written to the stack
 * every {@value #FLUSH} breaks, on a change of slot, a death or a logout, so a crash can lose at most that many of a
 * number that's only there to be looked at, and the stack isn't rewritten on every block.
 */
public final class ForgedRules {
    private ForgedRules() {}

    public static final int FLUSH = 20;

    /** Breaks not yet written to the pick in {@code slot}. */
    private static final class Pending {
        int slot, blocks, ores, hearts;
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    public static void init(IEventBus bus) {
        bus.addListener(ForgedRules::onBreakSpeed);
        bus.addListener(EventPriority.LOW, false, ForgedRules::onBreak);
        bus.addListener((ServerTickEvent.Post e) -> tick(e.getServer()));
        // before the drops are gathered, while it's still in the slot it was struck from
        bus.addListener(EventPriority.HIGH, (LivingDeathEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) flush(p);
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) flush(p);
            PENDING.remove(e.getEntity().getUUID());
        });
        bus.addListener((ServerStoppedEvent e) -> PENDING.clear());
    }

    /** Only where the head actually bites: a Delver on dirt digs as a hand does, sharp or blunt. */
    private static void onBreakSpeed(PlayerEvent.BreakSpeed e) {
        Player p = e.getEntity();
        ItemStack s = p.getMainHandItem();
        if (!ForgedPicks.is(s) || s.getDestroySpeed(e.getState()) <= 1F) return;
        e.setNewSpeed(e.getNewSpeed() * ForgedPicks.speedFactor(s));
    }

    private static void onBreak(BreakBlockEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p) || !ForgedPicks.is(p.getMainHandItem())) return;
        Pending c = pending(p);
        c.blocks++;
        BlockState s = e.getState();
        if (s.is(Tags.BlockItems.ORES.block()) || TrueStrikes.applies(s)) c.ores++;
        if (c.blocks >= FLUSH) flush(p);
    }

    /** A lode's heart broke under this player's strike. Written at once: a notch on the haft shouldn't wait. */
    public static void heart(ServerPlayer p) {
        if (!ForgedPicks.is(p.getMainHandItem())) return;
        pending(p).hearts++;
        flush(p);
    }

    private static Pending pending(ServerPlayer p) {
        int slot = p.getInventory().getSelectedSlot();
        Pending c = PENDING.get(p.getUUID());
        if (c != null && c.slot != slot) {
            flush(p);
            c = null;
        }
        if (c == null) {
            c = new Pending();
            c.slot = slot;
            PENDING.put(p.getUUID(), c);
        }
        return c;
    }

    /** A pick put away has its breaks written before anyone can move it further. */
    private static void tick(MinecraftServer server) {
        for (Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Pending> e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) {
                it.remove();
            } else if (p.getInventory().getSelectedSlot() != e.getValue().slot) {
                write(p, e.getValue());
                it.remove();
            }
        }
    }

    /** Writes what this player has pending. */
    public static void flush(ServerPlayer p) {
        Pending c = PENDING.remove(p.getUUID());
        if (c != null) write(p, c);
    }

    private static void write(ServerPlayer p, Pending c) {
        ItemStack s = p.getInventory().getItem(c.slot);
        if (!ForgedPicks.is(s) || c.blocks == 0 && c.hearts == 0) return;
        PickForged d = ForgedPicks.data(s);
        PickForged.Tally t = d.tally();
        s.set(DelveForged.PICK_FORGED.get(), d.withTally(new PickForged.Tally(t.blocks() + c.blocks, t.ores() + c.ores, t.hearts() + c.hearts,
                Math.max(0, t.keen() - c.blocks))));
    }
}
