package dev.awaken.gates.run;

import dev.awaken.Awaken;
import dev.awaken.gates.GateService;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.world.RigWells;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityEvent;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Gravity Wells while people are in them (SPEC §17 "Gate Rig"), stratum 7's way (descent rules, the tides): gravity is
 * an attribute, so a well is a transient modifier that turns it over (-2 x base: as strong, the other way). Inside a
 * well, or anywhere in the room while it surges, everything that walks falls up, monsters too; stepping out drops you
 * softly. Someone upside down has their eyes at the other end of their body, so the camera hangs below the ceiling at
 * the height it stands above a floor, and others see them walking the ceiling (gates.client.RigClient).
 */
public final class RigWellsLive {
    private RigWellsLive() {}

    public static final Identifier WELL = Awaken.id("gate_well_gravity");
    static final Identifier LANDING = Awaken.id("gate_well_landing");
    /** Down becomes up at full strength; a soft landing for this long after you come down. */
    public static final double FLIP = -2.0;
    static final int LANDING_TICKS = 60;
    static final double LANDING_FALL = 24;

    static void tick(ServerLevel level, RigDirector.Live l, long now) {
        RigWells.Field f = l.rig.wells;
        boolean surge = l.st().applied.contains(0);
        Set<UUID> up = new HashSet<>();
        if (!l.here.isEmpty()) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, l.box(), e -> e.isAlive() && !e.isSpectator() && !e.isNoGravity())) {
                boolean in = surge || inWell(l, f, e.position());
                if (in) up.add(e.getUUID());
                flip(level, e, in, l, now);
                if (in) e.resetFallDistance();
            }
        }
        // whoever left the room (or the room emptied) comes the right way up
        for (UUID u : l.flipped) {
            if (up.contains(u)) continue;
            Entity e = level.getEntity(u);
            if (e instanceof LivingEntity le) flip(level, le, false, l, now);
        }
        l.flipped.clear();
        l.flipped.addAll(up);
        for (Iterator<Map.Entry<UUID, Long>> it = l.landing.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (now < e.getValue()) continue;
            Entity en = level.getEntity(e.getKey());
            if (en instanceof LivingEntity le) {
                AttributeInstance fall = le.getAttribute(Attributes.SAFE_FALL_DISTANCE);
                if (fall != null) fall.removeModifier(LANDING);
            }
            it.remove();
        }
    }

    static boolean inWell(RigDirector.Live l, RigWells.Field f, Vec3 pos) {
        double[] q = l.local(pos.add(0, 0.9, 0));
        for (int i = 0; i < f.wells(); i++) if (f.box(i).contains(q[0], q[1], q[2])) return true;
        return false;
    }

    private static void flip(ServerLevel level, LivingEntity e, boolean up, RigDirector.Live l, long now) {
        AttributeInstance g = e.getAttribute(Attributes.GRAVITY);
        if (g == null || g.hasModifier(WELL) == up) return;
        if (up) g.addOrUpdateTransientModifier(new AttributeModifier(WELL, FLIP, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        else {
            g.removeModifier(WELL);
            AttributeInstance fall = e.getAttribute(Attributes.SAFE_FALL_DISTANCE);
            if (fall != null) {
                fall.addOrUpdateTransientModifier(new AttributeModifier(LANDING, LANDING_FALL, AttributeModifier.Operation.ADD_VALUE));
                l.landing.put(e.getUUID(), now + LANDING_TICKS);
            }
        }
        e.resetFallDistance();
        e.refreshDimensions();
        // the one who flips hears it from their own client at once (predicted); everyone else hears it here
        Player except = e instanceof ServerPlayer sp ? sp : null;
        level.playSound(except, e.getX(), e.getY() + 1, e.getZ(), up ? GatesRegistry.SND_RIG_FLIP_UP.get() : GatesRegistry.SND_RIG_FLIP_DOWN.get(),
                SoundSource.PLAYERS, 0.8F, 1F + 0.1F * (float) level.getRandom().nextGaussian());
    }

    /** A player leaving the gate keeps nothing of a well. */
    static void clear(ServerPlayer p) {
        AttributeInstance g = p.getAttribute(Attributes.GRAVITY);
        boolean had = g != null && g.hasModifier(WELL);
        if (g != null) g.removeModifier(WELL);
        AttributeInstance fall = p.getAttribute(Attributes.SAFE_FALL_DISTANCE);
        if (fall != null) fall.removeModifier(LANDING);
        if (had) p.refreshDimensions();
    }

    /** Upside down in a gate, the eyes are as far from the ceiling as they would be from a floor (both sides compute it). */
    public static void onSize(EntityEvent.Size e) {
        if (!(e.getEntity() instanceof LivingEntity le) || !le.isAddedToLevel() || !GateService.isGateLevel(le.level())) return;
        if (!upsideDown(le)) return;
        EntityDimensions d = e.getNewSize();
        e.setNewSize(d.withEyeHeight(Math.max(0.1F, d.height() - d.eyeHeight())));
    }

    /** Falling up in a well (the modifier is synced to every client that tracks the entity). */
    public static boolean upsideDown(LivingEntity e) {
        AttributeInstance g = e.getAttribute(Attributes.GRAVITY);
        return g != null && g.hasModifier(WELL);
    }
}
