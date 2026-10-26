package dev.awaken.wilds.ward;

import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.WardHooks;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
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
 * The ward's side of the world's events: a block a survival player places inside a ward is queued to crumble, or at a
 * climb refused outright ({@link ClimbWard}); a site's walls (and a climb's cache) refuse picks, blasts and pistons.
 * The crumble listens last and skips cancelled events, so anything else that refuses a placement wins first; a climb
 * refuses early, before the arrival tracker logs a block that never stood.
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
        ClimbWard.init();
        bus.addListener(EventPriority.HIGH, false, BlockEvent.EntityPlaceEvent.class, WardEvents::onClimbPlace);
        bus.addListener(EventPriority.HIGH, false, PlayerInteractEvent.RightClickBlock.class, WardEvents::onClimbBucket);
        bus.addListener(PlayerInteractEvent.RightClickBlock.class, Caches::onUse);
        bus.addListener(EventPriority.LOWEST, false, BlockEvent.EntityPlaceEvent.class, WardEvents::onPlace);
        bus.addListener(EventPriority.LOWEST, false, BreakBlockEvent.class, WardEvents::onBreak);
        bus.addListener(ExplosionEvent.Detonate.class, WardEvents::onExplode);
        bus.addListener(PistonEvent.Pre.class, WardEvents::onPiston);
        bus.addListener(ServerTickEvent.Post.class, e -> Crumble.tick(e.getServer()));
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            TOLD.remove(e.getEntity().getUUID());
            ClimbWard.forget(e.getEntity().getUUID());
        });
        bus.addListener(ServerStoppingEvent.class, e -> {
            Crumble.flush(e.getServer());
            Crumble.clear();
            Wards.clear();
            TOLD.clear();
            ClimbWard.clear();
        });
    }

    static void onClimbPlace(BlockEvent.EntityPlaceEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && e.getLevel() instanceof ServerLevel level
                && ClimbWard.refuses(p, level, e.getPos(), e.getPlacedBlock())) e.setCanceled(true);
    }

    /** Buckets fire no placement: a water column up a spire is a climb by building too. An empty bucket still fills. */
    static void onClimbBucket(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel level)
                || !(e.getItemStack().getItem() instanceof BucketItem bucket) || bucket.getContent() == Fluids.EMPTY) return;
        BlockPos at = e.getPos().relative(e.getFace() == null ? Direction.UP : e.getFace());
        if (ClimbWard.refuses(p, level, at, null)) {
            e.setCanceled(true);
            e.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
        }
    }

    static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && e.getLevel() instanceof ServerLevel level) placed(p, level, e.getPos(), e.getPlacedBlock());
    }

    /** A player placed {@code placed} at {@code pos}: inside an enforced ward it is queued to crumble. True if queued. */
    public static boolean placed(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState placed) {
        if (!Wards.binds(p)) return false;
        var ward = Wards.enforced(level, pos);
        if (ward.isEmpty() || Wards.climb(ward.get())) return false; // a climb refuses instead (ClimbWard)
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

    /**
     * Whether the ward refuses this player breaking this block (a site's wall in an enforced ward, or a climb's cache);
     * says so if it does, in the site's own words: only a vault has a door to answer to.
     */
    public static boolean refuses(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state) {
        if (!Wards.binds(p)) return false;
        boolean cache = Caches.isCache(level, pos) && ClimbWard.at(p, level, pos).isPresent();
        if (!cache && !Wards.sealed(level, pos, state)) return false;
        WardFx.play(level, WardFx.Kind.HOLD, pos, state);
        long now = level.getGameTime();
        Long last = TOLD.get(p.getUUID());
        if (last == null || now - last >= HOLD_TELL_EVERY || now < last) {
            TOLD.put(p.getUUID(), now);
            p.sendOverlayMessage(Component.translatable(cache ? "awaken.wilds.cache.holds" : "awaken.wilds.ward.hold." + holdWords(level, pos)));
        }
        return true;
    }

    /** Which wall line a site says (lang {@code awaken.wilds.ward.hold.<words>}): a vault's door, a climb's, a ruin's. */
    static String holdWords(ServerLevel level, BlockPos pos) {
        var w = WardHooks.at(level, pos);
        if (w.isEmpty()) return "ruin";
        if (Wards.rankDoored(w.get())) return "door";
        return switch (w.get().kind()) {
            case Wards.SPIRE -> "spire";
            case Wards.SKY -> "sky";
            case Wards.HERO_CROWN -> "tree";
            default -> "ruin";
        };
    }

    static void onExplode(ExplosionEvent.Detonate e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !Wards.on()) return;
        e.getAffectedBlocks().removeIf(pos -> Wards.sealed(level, pos, level.getBlockState(pos))
                || Caches.isCache(level, pos) && Wards.enforced(level, pos).map(Wards::climb).orElse(false));
    }

    static void onPiston(PistonEvent.Pre e) {
        if (e.getLevel() instanceof ServerLevel level && Wards.enforced(level, e.getPos()).isPresent()) e.setCanceled(true);
    }
}
