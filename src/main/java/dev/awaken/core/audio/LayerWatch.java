package dev.awaken.core.audio;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.MobHooks;
import dev.awaken.core.api.MobRole;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The server half of the layered music: every {@value Intensity#PERIOD} ticks (players spread over the period) it
 * asks the providers which context each player is in, scans the player's surroundings when there is one (hostiles
 * hunting them, a Lord), runs {@link Intensity} and sends {@link AudioNet.LayerState} only when something changed.
 * Damage traded with hostiles and a Lord's death arrive from events. Players in no context cost one provider check.
 */
final class LayerWatch {
    private LayerWatch() {}

    /** A Lord's fall is heard by players this close (blocks). */
    static final double FELL_RADIUS = 64;
    /** At most this many living things are looked at per scan. */
    static final int SCAN_CAP = 96;

    static final class SlotState {
        final MusicLayers.Slot slot;
        final Intensity intensity = new Intensity();
        @Nullable Identifier context, sent;
        int forced = -1, sentLevel = -1;

        SlotState(MusicLayers.Slot slot) {
            this.slot = slot;
        }
    }

    static final class PlayerState {
        final Intensity.Signals signals = new Intensity.Signals();
        final SlotState[] slots = {new SlotState(MusicLayers.Slot.MUSIC), new SlotState(MusicLayers.Slot.AMBIENCE)};
        /** Game master: audition a context anywhere (music slot), pin a level. */
        @Nullable Identifier override;
        int pinned = -1;
        long victorySent = Long.MIN_VALUE;
    }

    private static final Map<UUID, PlayerState> STATES = new HashMap<>();
    private static final List<LivingEntity> FOUND = new ArrayList<>();
    private static final EntityTypeTest<net.minecraft.world.entity.Entity, LivingEntity> LIVING = EntityTypeTest.forClass(LivingEntity.class);
    private static final Predicate<LivingEntity> HOSTILE = LayerWatch::hostile;

    static PlayerState state(ServerPlayer p) {
        return STATES.computeIfAbsent(p.getUUID(), u -> new PlayerState());
    }

    static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (Math.floorMod(p.getId() + now, Intensity.PERIOD) == 0) evaluate(p, now);
        }
    }

    /** One evaluation for one player (also called by commands for an immediate answer). */
    static void evaluate(ServerPlayer p, long now) {
        PlayerState st = state(p);
        Intensity.Signals s = st.signals;
        s.clearScan();
        boolean any = false;
        boolean gone = p.isDeadOrDying() || p.isSpectator() || p.isRemoved();
        for (SlotState slot : st.slots) {
            s.forced = -1;
            Identifier ctx = gone ? null
                    : slot.slot == MusicLayers.Slot.MUSIC && st.override != null ? st.override
                    : MusicLayers.resolve(slot.slot, p, s);
            slot.context = ctx;
            slot.forced = s.forced;
            any |= ctx != null;
        }
        if (any) scan(p, s);
        boolean arrival = s.arrival;
        s.arrival = false;
        boolean victory = now - s.lordFell <= Intensity.PERIOD * 2 && st.victorySent != s.lordFell;
        if (victory) st.victorySent = s.lordFell;
        for (SlotState slot : st.slots) {
            if (slot.context == null) {
                if (slot.sent != null) send(p, slot, null, -1, gone && p.isDeadOrDying() ? AudioNet.LayerState.DEATH : 0, "left");
                slot.intensity.reset();
                continue;
            }
            s.forced = st.pinned >= 0 && slot.slot == MusicLayers.Slot.MUSIC ? st.pinned : slot.forced;
            int level = slot.intensity.update(now, s);
            boolean changed = !slot.context.equals(slot.sent) || level != slot.sentLevel;
            int flags = (arrival && slot.slot == MusicLayers.Slot.MUSIC ? AudioNet.LayerState.ARRIVAL : 0)
                    | (victory ? AudioNet.LayerState.VICTORY : 0);
            if (changed || flags != 0) {
                send(p, slot, slot.context, level, flags, changed ? slot.intensity.why() : (arrival ? "arrival" : "victory"));
            }
        }
        s.forced = -1;
    }

    private static void send(ServerPlayer p, SlotState slot, @Nullable Identifier ctx, int level, int flags, String why) {
        slot.sent = ctx;
        slot.sentLevel = level;
        AwakenNet.send(p, new AudioNet.LayerState(slot.slot.ordinal(), ctx == null ? "" : ctx.toString(), Math.max(0, level), flags));
        String what = ctx == null ? "off" : ctx + " " + Intensity.name(level);
        String f = ((flags & AudioNet.LayerState.ARRIVAL) != 0 ? " +arrival" : "") + ((flags & AudioNet.LayerState.VICTORY) != 0 ? " +victory" : "")
                + ((flags & AudioNet.LayerState.DEATH) != 0 ? " +death" : "");
        DirectorLog.log(p, "audio", "layers " + slot.slot.name().toLowerCase(java.util.Locale.ROOT) + ": " + what + f + " (" + why + ")");
        Awaken.LOG.info("[audio] layers server {} {}: {}{} ({})", p.getGameProfile().name(), slot.slot, what, f, why);
    }

    /** Fills the scan half of the signals: hostiles hunting or near the player, a Lord. */
    static void scan(ServerPlayer p, Intensity.Signals s) {
        FOUND.clear();
        p.level().getEntities(LIVING, p.getBoundingBox().inflate(Intensity.LORD_RADIUS), HOSTILE, FOUND, SCAN_CAP);
        for (int i = 0; i < FOUND.size(); i++) {
            LivingEntity e = FOUND.get(i);
            double d = e.distanceTo(p);
            boolean hunts = e instanceof Mob m && m.getTarget() == p;
            if (MobRole.of(e).role() == MobRole.Role.BOSS && d <= Intensity.LORD_RADIUS) {
                s.lordAlive = true;
                s.lordDistance = Math.min(s.lordDistance, d);
                s.lordHunting |= hunts;
            }
            if (hunts && d <= Intensity.HUNT_RADIUS) {
                s.hunting++;
                if (d <= Intensity.SWARM_RADIUS) s.huntingClose++;
            }
            if (d <= Intensity.NEAR_RADIUS) s.near++;
        }
        FOUND.clear();
    }

    /**
     * Hostile for the music: alive, not a player, not one of yours (an Echo) or the Ritual's cast, and either an
     * AWAKEN monster, a vanilla monster, or anything hunting a player right now; peaceful fauna never count.
     */
    static boolean hostile(LivingEntity e) {
        if (!e.isAlive() || e instanceof Player) return false;
        MobRole.Role role = MobRole.of(e).role();
        if (role == MobRole.Role.ECHO || role == MobRole.Role.RITUAL) return false;
        if (role == MobRole.Role.NONE && !(e instanceof Enemy)) return e instanceof Mob m && m.getTarget() instanceof Player;
        return !MobHooks.peaceful(e);
    }

    /** A blow traded between a player and a hostile: combat (and the Lord's layer when the foe is a boss). */
    static void onDamage(LivingDamageEvent.Post e) {
        LivingEntity victim = e.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        Entity src = e.getSource().getEntity();
        long now = level.getServer().getTickCount();
        if (victim instanceof ServerPlayer p && src instanceof LivingEntity foe && foe != p && hostile(foe)) {
            mark(p, foe, now);
        } else if (src instanceof ServerPlayer p && victim != p && hostile(victim)) {
            mark(p, victim, now);
        }
    }

    private static void mark(ServerPlayer p, LivingEntity foe, long now) {
        Intensity.Signals s = state(p).signals;
        s.lastCombat = now;
        if (MobRole.of(foe).role() == MobRole.Role.BOSS) s.lastLordHit = now;
    }

    /** A Lord's death ends the fight for everyone near; a player's death ends their music at once. */
    static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) return;
        long now = level.getServer().getTickCount();
        if (dead instanceof ServerPlayer p) {
            PlayerState st = STATES.get(p.getUUID());
            if (st == null) return;
            for (SlotState slot : st.slots) {
                if (slot.sent != null) send(p, slot, null, -1, AudioNet.LayerState.DEATH, "death");
                slot.intensity.reset();
            }
            st.signals.reset();
            return;
        }
        if (MobRole.of(dead).role() != MobRole.Role.BOSS) return;
        double r2 = FELL_RADIUS * FELL_RADIUS;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(dead) <= r2) state(p).signals.lordFell = now;
        }
    }

    static @Nullable Identifier current(ServerPlayer p, MusicLayers.Slot slot) {
        PlayerState st = STATES.get(p.getUUID());
        return st == null ? null : st.slots[slot.ordinal()].sent;
    }

    static int level(ServerPlayer p, MusicLayers.Slot slot) {
        PlayerState st = STATES.get(p.getUUID());
        return st == null || st.slots[slot.ordinal()].sent == null ? -1 : st.slots[slot.ordinal()].sentLevel;
    }

    static void forget(UUID player) {
        STATES.remove(player);
        GateMusic.INSTANCE.forget(player);
    }

    static void clear() {
        STATES.clear();
        GateMusic.INSTANCE.clear();
    }
}
