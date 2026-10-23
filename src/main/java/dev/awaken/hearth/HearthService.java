package dev.awaken.hearth;

import dev.awaken.core.AwakenConfig;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.PlayDays;
import dev.awaken.hearth.echo.Duty;
import dev.awaken.hearth.echo.EchoRole;
import dev.awaken.hearth.echo.Echoes;
import dev.awaken.hearth.player.HearthPlayer;
import dev.awaken.hearth.room.Room;
import dev.awaken.hearth.room.RoomScanner;
import dev.awaken.hearth.room.RoomType;
import dev.awaken.hearth.town.Town;
import dev.awaken.hearth.trophy.Trophies;
import dev.awaken.progression.Perks;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.portal.TeleportTransition;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The Hearth's server side: Anchors, pads, room scans, protection, respawn, Fracture healing, and the per-second upkeep
 * that drives the town, the Echoes on duty and breaks near home. All of it is budgeted (SPEC performance header).
 */
public final class HearthService {
    private HearthService() {}

    public static final String OVERWORLD = Level.OVERWORLD.identifier().toString();
    /** Block reads a room scan may do per server tick (roughly 0.2-0.4 ms). */
    private static final int SCAN_BUDGET = 12_000;
    private static final int RESCAN_EVERY = 20 * 60 * 5;

    private static RoomScanner activeScan;
    private static final Deque<UUID> scanQueue = new ArrayDeque<>();

    public static void init() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(HearthService::onServerTick);
        bus.addListener(HearthService::onPlayerTick);
        bus.addListener(HearthService::onBreak);
        bus.addListener(HearthService::onPlace);
        bus.addListener(HearthService::onRespawnPosition);
        bus.addListener(HearthService::onSetSpawn);
        bus.addListener(HearthService::onLogin);
        bus.addListener(HearthService::onRespawn);
        bus.addListener(HearthService::onWake);
        bus.addListener(HearthService::onGrief);
        bus.addListener(HearthService::onExplosion);
        bus.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) -> {
            activeScan = null;
            scanQueue.clear();
            REST.clear();
        });
    }

    /** The world's feature flag. Safe to call before the config has loaded (defaults to on). */
    public static boolean enabled() {
        try {
            return AwakenConfig.HEARTH.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    public static HearthData data(MinecraftServer server) {
        return HearthData.get(server);
    }

    public static Optional<HearthRecord> hearthOf(ServerPlayer player) {
        return data(player.level().getServer()).of(player.getUUID());
    }

    public static HearthPlayer player(Player p) {
        return p.getData(HearthContent.PLAYER.get());
    }

    public static Optional<ServerPlayer> owner(MinecraftServer server, HearthRecord r) {
        return Optional.ofNullable(server.getPlayerList().getPlayer(r.owner));
    }

    public static ServerLevel levelOf(MinecraftServer server, HearthRecord r) {
        return server.overworld();
    }

    public static void dirty(MinecraftServer server) {
        data(server).setDirty();
    }

    // ---- the Anchor -------------------------------------------------------------------------------------------------

    public static boolean canPlaceAnchor(ServerPlayer sp, ServerLevel level, BlockPos pos) {
        if (!enabled()) {
            sp.sendOverlayMessage(Component.literal("The Hearth is turned off on this world."));
            return false;
        }
        if (level.dimension() != Level.OVERWORLD) {
            sp.sendOverlayMessage(Component.literal("The Anchor only takes root in the waking world."));
            return false;
        }
        HearthData d = data(level.getServer());
        if (d.of(sp.getUUID()).isPresent()) {
            sp.sendOverlayMessage(Component.literal("You already have a Hearth. Break your old Anchor to move home."));
            return false;
        }
        Optional<HearthRecord> other = d.containing(pos, OVERWORLD);
        if (other.isPresent()) {
            sp.sendOverlayMessage(Component.literal("This land is " + other.get().ownerName + "'s Hearth."));
            return false;
        }
        return true;
    }

    public static void onAnchorPlaced(ServerPlayer sp, ServerLevel level, BlockPos pos) {
        HearthData d = data(level.getServer());
        if (d.of(sp.getUUID()).isPresent() || level.dimension() != Level.OVERWORLD) return;
        HearthRecord r = new HearthRecord(sp.getUUID(), sp.getGameProfile().name(), pos.immutable(), OVERWORLD);
        r.createdDay = PlayDays.current(level.getServer());
        r.scanAt = level.getGameTime() + 20;
        r.announceFirstScan = true;
        d.put(r);
        HearthPlayer hp = player(sp);
        hp.respawnAtHearth = true;
        level.playSound(null, pos, HearthContent.SND_ANCHOR.get(), SoundSource.BLOCKS, 1f, 1f);
        level.sendParticles(HearthContent.HEARTH_EMBER.get(), pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 40, 0.6, 0.6, 0.6, 0.02);
        Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Home exists."))
                .body(Component.literal("The Anchor takes root. Everything within " + r.radius() + " blocks is your Hearth: you wake here, you heal here, and people will come."))
                .row(Component.literal("Rooms"), Component.literal("Build them. The Hearth notices what's inside."))
                .row(Component.literal("Respawn"), Component.literal("Here"))
                .footer(Component.literal("Use the Anchor to see your Hearth")).ttl(20 * 12).priority(4).build());
        if (Progression.profile(sp).awakened()) Progression.markNewThing(sp, "hearth");
        DirectorLog.log(sp, "hearth", "anchor placed at " + pos.toShortString());
        Town.onHearthCreated(sp, level, r);
        HearthViews.refresh(sp);
    }

    public static void onAnchorUsed(ServerPlayer sp, ServerLevel level, BlockPos pos) {
        HearthData d = data(level.getServer());
        Optional<HearthRecord> r = d.at(pos, level.dimension().identifier().toString());
        if (r.isEmpty()) {
            // An Anchor with no owner (placed by a command or left from an old world): the first homeless player claims it.
            if (canPlaceAnchor(sp, level, pos)) onAnchorPlaced(sp, level, pos);
            return;
        }
        if (!r.get().owner.equals(sp.getUUID())) {
            sp.sendOverlayMessage(Component.literal("This is " + r.get().ownerName + "'s Hearth. It's warm here."));
            return;
        }
        player(sp).respawnAtHearth = true;
        Upgrades.offerAnchor(sp, level, r.get());
    }

    public static void onAnchorRemoved(ServerLevel level, BlockPos pos) {
        HearthData d = data(level.getServer());
        Optional<HearthRecord> r = d.at(pos, level.dimension().identifier().toString());
        if (r.isEmpty()) return;
        HearthRecord h = r.get();
        Duty.releaseAll(level, h);
        Town.dissolve(level, h);
        d.remove(h.owner);
        if (activeScan != null && activeScan.hearth() == h) activeScan = null;
        owner(level.getServer(), h).ifPresent(sp -> {
            player(sp).respawnAtHearth = false;
            Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.WARNING, Component.literal("Your Anchor went cold."))
                    .body(Component.literal("Place it again wherever home should be. The town will follow.")).build());
            Trophies.applyHallBonus(sp, null);
            HearthViews.refresh(sp);
        });
    }

    // ---- pads and room-relevant changes ------------------------------------------------------------------------------

    public static void onPadPlaced(ServerLevel level, BlockPos pos, EchoRole role) {
        data(level.getServer()).containing(pos, level.dimension().identifier().toString()).ifPresent(h -> {
            h.pads.put(pos.immutable(), role);
            h.scanAt = level.getGameTime() + 40;
            dirty(level.getServer());
            owner(level.getServer(), h).ifPresent(sp -> {
                Duty.reassign(sp, level, h);
                HearthViews.refresh(sp);
            });
        });
    }

    public static void onPadRemoved(ServerLevel level, BlockPos pos) {
        data(level.getServer()).containing(pos, level.dimension().identifier().toString()).ifPresent(h -> {
            if (h.pads.remove(pos) == null) return;
            Duty.releasePad(level, h, pos);
            h.mineProgress.remove(pos);
            h.scanAt = level.getGameTime() + 40;
            dirty(level.getServer());
            owner(level.getServer(), h).ifPresent(sp -> {
                Duty.reassign(sp, level, h);
                HearthViews.refresh(sp);
            });
        });
    }

    public static void describePad(ServerPlayer sp, ServerLevel level, BlockPos pos, EchoRole role) {
        Optional<HearthRecord> h = data(level.getServer()).containing(pos, level.dimension().identifier().toString());
        var w = SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(role.label + " pad"))
                .body(Component.literal("An Echo standing here " + role.does + "."));
        if (h.isEmpty()) {
            w.footer(Component.literal("Outside any Hearth: place it within your Anchor's radius"));
        } else {
            String who = Duty.workerName(level, h.get(), pos);
            w.row(Component.literal("Working"), Component.literal(who == null ? "Nobody yet: bind another Echo" : who));
            if (role == EchoRole.MINE) {
                int prog = h.get().mineProgress.getOrDefault(pos, 0);
                w.row(Component.literal("Next haul"), Component.literal(Math.max(0, (Duty.MINE_TICKS - prog) / 20) + "s of work"));
            }
        }
        Signal.send(sp, w.ttl(20 * 6).build());
    }

    /** Something room-relevant changed at {@code pos}: rescan that Hearth soon (debounced). */
    public static void blockChanged(ServerLevel level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD) return;
        data(level.getServer()).containing(pos, OVERWORLD).ifPresent(h -> h.scanAt = level.getGameTime() + 40);
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (RoomScanner.isFurniture(e.getPlacedBlock()) || e.getPlacedBlock().isCollisionShapeFullBlock(level, e.getPos())) {
            blockChanged(level, e.getPos());
        }
    }

    private static void onBreak(BreakBlockEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        BlockState s = e.getState();
        if (s.is(HearthContent.HEARTH_ANCHOR.get())) {
            Optional<HearthRecord> r = data(level.getServer()).at(e.getPos(), level.dimension().identifier().toString());
            Player p = e.getPlayer();
            boolean op = p instanceof ServerPlayer sp && sp.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
            if (r.isPresent() && !r.get().owner.equals(p.getUUID()) && !op) {
                e.setCanceled(true);
                p.sendOverlayMessage(Component.literal("Only " + r.get().ownerName + " can move this Hearth."));
                return;
            }
        }
        if (level.dimension() == Level.OVERWORLD && (RoomScanner.isFurniture(s) || s.isCollisionShapeFullBlock(level, e.getPos()))) {
            blockChanged(level, e.getPos());
        }
    }

    // ---- Hearth Shield (Perks.HEARTH_SHIELD): during a break near home, nothing breaks your buildings -------------------

    private static boolean shielded(ServerLevel level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD) return false;
        Optional<HearthRecord> h = data(level.getServer()).containing(pos, OVERWORLD);
        if (h.isEmpty() || !Breaks.active(h.get())) return false;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(h.get().owner);
        return owner != null && Progression.hasPerk(owner, Perks.HEARTH_SHIELD);
    }

    private static void onGrief(EntityMobGriefingEvent e) {
        Entity ent = e.getEntity();
        if (ent instanceof Player || !(ent.level() instanceof ServerLevel level)) return;
        if (shielded(level, ent.blockPosition())) e.setCanGrief(false);
    }

    private static void onExplosion(ExplosionEvent.Detonate e) {
        if (!(e.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (e.getExplosion().getIndirectSourceEntity() instanceof Player) return;
        BlockPos centre = BlockPos.containing(e.getExplosion().center());
        if (!shielded(level, centre)) return;
        int before = e.getAffectedBlocks().size();
        e.getAffectedBlocks().removeIf(p -> data(level.getServer()).containing(p, OVERWORLD).isPresent());
        if (before > 0 && e.getAffectedBlocks().size() < before) {
            level.sendParticles(ParticleTypes.WAX_ON, centre.getX() + 0.5, centre.getY() + 1, centre.getZ() + 0.5, 20, 1.5, 1, 1.5, 0.1);
        }
    }

    // ---- respawn ----------------------------------------------------------------------------------------------------

    private static void onSetSpawn(PlayerSetSpawnEvent e) {
        // Sleeping in a bed elsewhere moves your respawn there; using your Anchor brings it back.
        if (e.getEntity() instanceof ServerPlayer sp && !e.isForced()) {
            Optional<HearthRecord> h = hearthOf(sp);
            if (h.isEmpty() || e.getNewSpawn() == null || !h.get().contains(e.getNewSpawn())) player(sp).respawnAtHearth = false;
        }
    }

    private static void onRespawnPosition(PlayerRespawnPositionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || e.isFromEndFight() || !enabled()) return;
        if (!player(sp).respawnAtHearth) return;
        Optional<HearthRecord> h = hearthOf(sp);
        if (h.isEmpty()) return;
        ServerLevel level = levelOf(sp.level().getServer(), h.get());
        Vec3 spot = safeSpotNear(level, h.get().pos);
        TeleportTransition old = e.getTeleportTransition();
        e.setTeleportTransition(new TeleportTransition(level, spot, Vec3.ZERO, old.yRot(), old.xRot(), old.postTeleportTransition()));
    }

    /** A standing spot next to the Anchor (two air blocks over something solid), or right on top of it. */
    public static Vec3 safeSpotNear(ServerLevel level, BlockPos anchor) {
        level.getChunk(anchor.getX() >> 4, anchor.getZ() >> 4);
        int[][] ring = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}};
        for (int dy = 0; dy >= -2; dy--) {
            for (int[] o : ring) {
                BlockPos feet = anchor.offset(o[0], dy, o[1]);
                if (level.getBlockState(feet.below()).isSolid() && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                        && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                    return Vec3.atBottomCenterOf(feet);
                }
            }
        }
        return Vec3.atBottomCenterOf(anchor.above());
    }

    // ---- login, respawn, waking -------------------------------------------------------------------------------------

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        hearthOf(sp).ifPresent(h -> {
            if (!h.ownerName.equals(sp.getGameProfile().name())) {
                h.ownerName = sp.getGameProfile().name();
                dirty(sp.level().getServer());
            }
            Trophies.applyHallBonus(sp, h);
        });
        Auras.refresh(sp);
        HearthViews.refresh(sp);
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        hearthOf(sp).ifPresent(h -> Trophies.applyHallBonus(sp, h));
        Auras.refresh(sp);
        HearthViews.refresh(sp);
    }

    private static void onWake(PlayerWakeUpEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || e.wakeImmediately() || !enabled()) return;
        if (!(sp.level() instanceof ServerLevel level) || !Hearth.isSanctuary(level, sp.blockPosition())) return;
        boolean fractured = Progression.profile(sp).fractureUntil() > level.getGameTime();
        if (fractured) Progression.clearFracture(sp);
        sp.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 30, 0, true, true));
        Signal.toast(sp, Component.literal(fractured ? "You slept in your Sanctuary. The Fracture is gone." : "Well rested in your Sanctuary."));
    }

    // ---- Fracture heals with rest at home -----------------------------------------------------------------------------

    private record Rest(int seconds, double x, double y, double z) {}

    private static final Map<UUID, Rest> REST = new HashMap<>();

    /** Seconds of quiet rest inside the radius that clear Fracture, by Anchor tier. */
    public static int restSeconds(int tier) {
        return Math.max(10, 30 - 4 * (tier - 1));
    }

    private static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || sp.tickCount % 20 != 0 || !enabled()) return;
        if (!(sp.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            REST.remove(sp.getUUID());
            return;
        }
        long now = level.getGameTime();
        if (Progression.profile(sp).fractureUntil() <= now) {
            REST.remove(sp.getUUID());
            return;
        }
        Optional<HearthRecord> h = data(level.getServer()).containing(sp.blockPosition(), OVERWORLD);
        boolean recentlyHurt = sp.getLastHurtByMobTimestamp() > 0 && sp.tickCount - sp.getLastHurtByMobTimestamp() < 200;
        if (h.isEmpty() || recentlyHurt) {
            REST.remove(sp.getUUID());
            return;
        }
        Rest prev = REST.get(sp.getUUID());
        boolean still = prev != null && sp.distanceToSqr(prev.x, prev.y, prev.z) < 0.25;
        int secs = still || sp.isSleeping() ? prev.seconds + 1 : 0;
        REST.put(sp.getUUID(), new Rest(secs, sp.getX(), sp.getY(), sp.getZ()));
        int need = restSeconds(h.get().tier);
        if (h.get().roomAt(sp.blockPosition(), RoomType.SANCTUARY).isPresent()) need = need / 2;
        if (secs >= 3 && secs < need) {
            sp.sendOverlayMessage(Component.literal("Resting at your Hearth · " + (need - secs) + "s").withStyle(ChatFormatting.GOLD));
        } else if (secs >= need) {
            Progression.clearFracture(sp);
            REST.remove(sp.getUUID());
            level.sendParticles(HearthContent.HEARTH_EMBER.get(), sp.getX(), sp.getY() + 1, sp.getZ(), 24, 0.5, 0.8, 0.5, 0.01);
            Signal.toast(sp, Component.literal("The Hearth's warmth mends you. Fracture healed."));
        }
    }

    // ---- server tick: scans and upkeep --------------------------------------------------------------------------------

    private static void onServerTick(ServerTickEvent.Post e) {
        MinecraftServer server = e.getServer();
        if (!enabled()) return;
        ServerLevel overworld = server.overworld();
        HearthData d = data(server);
        long now = overworld.getGameTime();

        // 1. Room scans: one at a time, budgeted.
        if (activeScan == null) {
            if (server.getTickCount() % 10 == 0) {
                for (HearthRecord h : d.all()) {
                    if (h.scanAt == 0 && now - h.lastScan > RESCAN_EVERY) h.scanAt = now;
                    if (h.scanAt != 0 && h.scanAt <= now && !scanQueue.contains(h.owner) && overworld.isLoaded(h.pos)) scanQueue.add(h.owner);
                }
            }
            UUID next = scanQueue.poll();
            if (next != null) {
                d.of(next).ifPresent(h -> {
                    h.scanAt = 0;
                    activeScan = new RoomScanner(overworld, h, rooms -> onRoomsScanned(server, h, rooms));
                });
            }
        }
        if (activeScan != null) {
            activeScan.step(SCAN_BUDGET);
            if (activeScan.done()) activeScan = null;
        }

        // 2. Upkeep, once a second, spread across Hearths by owner hash so they don't all run on the same tick.
        int tick = server.getTickCount();
        DUE.clear();
        for (HearthRecord h : d.all()) if ((tick + (h.owner.hashCode() & 15)) % 20 == 0) DUE.add(h);
        for (int i = 0; i < DUE.size(); i++) upkeep(server, overworld, DUE.get(i), now);
        DUE.clear();
    }

    /** Reused every tick so upkeep never allocates (and Hearths removed during upkeep don't break the loop). */
    private static final List<HearthRecord> DUE = new ArrayList<>();

    private static void upkeep(MinecraftServer server, ServerLevel level, HearthRecord h, long now) {
        boolean loaded = level.isPositionEntityTicking(h.pos);
        if (loaded && !level.getBlockState(h.pos).is(HearthContent.HEARTH_ANCHOR.get())) {
            onAnchorRemoved(level, h.pos); // broken by something that skipped the block callbacks
            return;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(h.owner);
        if (loaded) {
            Town.upkeep(level, h, owner, now);
            if (owner != null) Duty.upkeep(owner, level, h, now);
        }
        if (owner != null && server.getTickCount() % 100 < 20) Breaks.check(owner, level, h);
    }

    /** Called when a room scan finishes: store rooms, update trophy bonuses, tell the owner about new rooms. */
    static void onRoomsScanned(MinecraftServer server, HearthRecord h, List<Room> rooms) {
        EnumSet<RoomType> before = EnumSet.noneOf(RoomType.class);
        for (Room r : h.rooms) before.addAll(r.types());
        h.rooms = rooms;
        h.lastScan = server.overworld().getGameTime();
        Set<String> trophies = new java.util.HashSet<>();
        EnumSet<RoomType> after = EnumSet.noneOf(RoomType.class);
        for (Room r : rooms) {
            after.addAll(r.types());
            if (r.types().contains(RoomType.TROPHY_HALL)) trophies.addAll(r.trophies());
        }
        h.hallTrophies = Set.copyOf(trophies);
        boolean announce = h.scannedOnce || h.announceFirstScan;
        h.scannedOnce = true;
        h.announceFirstScan = false;
        owner(server, h).ifPresent(sp -> {
            if (announce) {
                for (RoomType t : after) {
                    if (before.contains(t)) continue;
                    Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Room recognised: " + t.label))
                            .body(Component.literal("The Hearth noticed " + t.needs + ". " + capitalise(t.gives) + "."))
                            .ttl(20 * 6).build());
                }
            }
            Trophies.applyHallBonus(sp, h);
            HearthViews.refresh(sp);
        });
    }

    /** Starts a full scan right now (commands and tests). */
    public static List<Room> scanNow(ServerLevel level, HearthRecord h) {
        RoomScanner s = new RoomScanner(level, h, rooms -> onRoomsScanned(level.getServer(), h, rooms));
        s.runToEnd();
        return h.rooms;
    }

    static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
