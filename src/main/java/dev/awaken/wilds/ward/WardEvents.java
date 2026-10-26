package dev.awaken.wilds.ward;

import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.WardHooks;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The ward's side of the world's events: a block a survival player places inside a ward is queued to crumble; a site's
 * walls refuse picks, blasts and pistons. Placement listens last and skips cancelled events, so anything else that
 * refuses a placement wins first.
 */
public final class WardEvents {
    private WardEvents() {}

    /** "The ward holds" says so at most once in this many ticks per player (the sound plays every time). */
    static final int HOLD_TELL_EVERY = 100;
    /** The once-per-player flag for the first ward a player builds in. */
    static final String NOTICED = "awaken:ward_noticed";

    private static final Map<UUID, Long> TOLD = new HashMap<>();

    public static void init() {
        var bus = NeoForge.EVENT_BUS;
        WardHooks.register(Wards::structureWards);
        bus.addListener(EventPriority.LOWEST, false, BlockEvent.EntityPlaceEvent.class, WardEvents::onPlace);
        bus.addListener(EventPriority.LOWEST, false, BreakBlockEvent.class, WardEvents::onBreak);
        bus.addListener(ExplosionEvent.Detonate.class, WardEvents::onExplode);
        bus.addListener(PistonEvent.Pre.class, WardEvents::onPiston);
        bus.addListener(ServerTickEvent.Post.class, e -> Crumble.tick(e.getServer()));
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> TOLD.remove(e.getEntity().getUUID()));
        bus.addListener(ServerStoppingEvent.class, e -> {
            Crumble.flush(e.getServer());
            Crumble.clear();
            Wards.clear();
            TOLD.clear();
        });
    }

    static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && e.getLevel() instanceof ServerLevel level) placed(p, level, e.getPos(), e.getPlacedBlock());
    }

    /** A player placed {@code placed} at {@code pos}: inside an enforced ward it is queued to crumble. True if queued. */
    public static boolean placed(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState placed) {
        if (!Wards.binds(p)) return false;
        var ward = Wards.enforced(level, pos);
        if (ward.isEmpty()) return false;
        Crumble.queue(level, pos, placed, Crumble.itemFor(p, placed), p);
        if (Progression.profile(p).counter(NOTICED) == 0) {
            Progression.setCounter(p, NOTICED, 1);
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Precursor ward"))
                    .body(Component.literal("Nothing you bring stays here. It comes back to you."))
                    .presentation(SignalWindow.Presentation.TOAST).ttl(20 * 6).build());
            DirectorLog.log(p, "wilds", "first block placed in a ward (" + ward.get().id() + ", " + ward.get().kind() + ")");
        }
        return true;
    }

    static void onBreak(BreakBlockEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !(e.getPlayer() instanceof ServerPlayer p) || !refuses(p, level, e.getPos(), e.getState())) return;
        e.setCanceled(true);
        e.setNotifyClient(true);
    }

    /** Whether the ward refuses this player breaking this block (a site's wall in an enforced ward); says so if it does. */
    public static boolean refuses(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state) {
        if (!Wards.binds(p) || !Wards.sealed(level, pos, state)) return false;
        WardFx.play(level, WardFx.Kind.HOLD, pos, state);
        long now = level.getGameTime();
        Long last = TOLD.get(p.getUUID());
        if (last == null || now - last >= HOLD_TELL_EVERY || now < last) {
            TOLD.put(p.getUUID(), now);
            p.sendOverlayMessage(Component.literal("The ward holds. These walls answer only to their door."));
        }
        return true;
    }

    static void onExplode(ExplosionEvent.Detonate e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !Wards.on()) return;
        e.getAffectedBlocks().removeIf(pos -> Wards.sealed(level, pos, level.getBlockState(pos)));
    }

    static void onPiston(PistonEvent.Pre e) {
        if (e.getLevel() instanceof ServerLevel level && Wards.enforced(level, e.getPos()).isPresent()) e.setCanceled(true);
    }
}
