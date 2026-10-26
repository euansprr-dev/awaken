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
    /** The once-per-player flag for the first climb that refused a block (its card offered the Ascent page). */
    static final String CLIMB_SHOWN = "awaken:climb_refused";

    private static final Map<UUID, Long> TOLD = new HashMap<>();

    public static void init() {
        var bus = NeoForge.EVENT_BUS;
        WardHooks.register(Wards::structureWards);
        // a climb refuses before anything else hears the placement (the arrival tracker would log a block never placed)
        bus.addListener(EventPriority.HIGH, false, BlockEvent.EntityPlaceEvent.class, WardEvents::onClimbPlace);
        bus.addListener(EventPriority.HIGH, false, net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock.class, WardEvents::onClimbBucket);
        bus.addListener(EventPriority.HIGH, false, net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock.class, WardEvents::onCacheOpen);
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

    static void onClimbPlace(BlockEvent.EntityPlaceEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && e.getLevel() instanceof ServerLevel level && refusesClimb(p, level, e.getPos())) e.setCanceled(true);
    }

    /** Buckets fire no placement: a water column up a spire is a climb too. */
    static void onClimbBucket(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel level)
                || !(e.getItemStack().getItem() instanceof net.minecraft.world.item.BucketItem)) return;
        BlockPos at = e.getPos().relative(e.getFace() == null ? net.minecraft.core.Direction.UP : e.getFace());
        if (refusesClimb(p, level, at)) e.setCanceled(true);
    }

    /**
     * A block (or a bucket) a player puts down at a climb (SPEC §27.5): refused at once, the ward's hold light where it
     * would have gone, and the place says what it answers to, worded by what the player holds. Without the rope the
     * lock is kept as a promise, and the first time ever a card offers to show the band's Ascent page. True if refused.
     */
    public static boolean refusesClimb(ServerPlayer p, ServerLevel level, BlockPos pos) {
        var ward = Wards.climbAt(level, pos, p);
        if (ward.isEmpty()) return false;
        WardFx.play(level, WardFx.Kind.HOLD, pos, level.getBlockState(pos));
        WardHooks.Ward w = ward.get();
        String kind = w.kind().equals("hero_crown") ? "tree" : w.kind().contains("sky") ? "sky" : "spire";
        boolean rope = dev.awaken.core.api.HorizonHooks.can(p, dev.awaken.core.api.HorizonHooks.TETHER);
        if (dev.awaken.core.Throttle.allow(p, "ward_climb", 60)) {
            p.sendOverlayMessage(rope ? Component.literal("Climb it by rope: hold ").append(Component.keybind("key.awaken.movement"))
                    .append(Component.literal(kind.equals("sky") ? " at the lowest ring." : " at a ring."))
                    : Component.literal(switch (kind) {
                        case "sky" -> "Nothing you build reaches it. Its rings answer to a line.";
                        case "tree" -> "The great tree sheds what you build. Its rings answer to a line.";
                        default -> "The spire sheds what you build. Its platforms answer to a line.";
                    }));
        }
        if (!rope) {
            String key = (kind.equals("tree") ? "hero:" : "landmark:") + w.id();
            dev.awaken.core.api.Promises.seen(p, key, w.box().getCenter(), dev.awaken.core.api.Promises.Need.tier(dev.awaken.core.api.HorizonHooks.TETHER),
                    switch (kind) {
                        case "sky" -> "A sky platform";
                        case "tree" -> "A great tree's crown";
                        default -> "A Tether Spire's crown";
                    });
            if (Progression.profile(p).counter(CLIMB_SHOWN) == 0) {
                Progression.setCounter(p, CLIMB_SHOWN, 1);
                Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(switch (kind) {
                            case "sky" -> "A sky platform";
                            case "tree" -> "A great tree";
                            default -> "A Tether Spire";
                        }))
                        .body(Component.literal("It sheds what you build. Its way up answers to a line, and a Precursor vault keeps a line "
                                + "for a hunter it opens to."))
                        .choice(dev.awaken.progression.Progression.BAND_ACTION + ":ascent", "Show me", "Later").ttl(20 * 40).build());
            }
        }
        DirectorLog.log(p, "wilds", "a climb refused a block (" + w.id() + ", " + w.kind() + (rope ? ", has the rope)" : ")"));
        return true;
    }

    /**
     * The cache's inner seal (SPEC §27.5): a site's unopened cache keeps its best roll for a player who came without
     * building (`awaken:arrived_by_movement`), and vanilla rolls a chest once, for everyone. So for someone who built
     * their way in, it stays shut and says so, rather than spending its prize on the wrong arrival. Opened once, it's a
     * chest like any other.
     */
    static void onCacheOpen(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock e) {
        if (e.getEntity() instanceof ServerPlayer p && e.getLevel() instanceof ServerLevel level && sealsCache(p, level, e.getPos())) e.setCanceled(true);
    }

    /** Whether the cache at {@code pos} stays shut for this player (and says so). */
    public static boolean sealsCache(ServerPlayer p, ServerLevel level, BlockPos pos) {
        if (!Wards.on() || !Wards.binds(p)) return false;
        if (!(level.getBlockEntity(pos) instanceof net.minecraft.world.RandomizableContainer c) || c.getLootTable() == null) return false;
        var ward = WardHooks.at(level, pos);
        if (ward.isEmpty() || dev.awaken.core.api.ArrivalHooks.byMovement(p)) return false;
        WardFx.play(level, WardFx.Kind.HOLD, pos, level.getBlockState(pos));
        if (dev.awaken.core.Throttle.allow(p, "ward_cache", 40)) {
            p.sendOverlayMessage(Component.literal(Wards.climb(ward.get())
                    ? "The cache's inner seal holds: you built your way up. Come back by rope."
                    : "The cache's inner seal holds: you built your way here. Come back without building."));
        }
        return true;
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
            boolean door = WardHooks.at(level, pos).map(Wards::rankDoored).orElse(false);
            p.sendOverlayMessage(Component.literal(door ? "The ward holds. These walls answer only to their door."
                    : "The ward holds. These walls were sealed to stay."));
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
