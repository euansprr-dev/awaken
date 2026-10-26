package dev.awaken.body.ascent.client;

import dev.awaken.body.ascent.AscentContent;
import dev.awaken.body.ascent.AscentPayloads;
import dev.awaken.body.ascent.AscentState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Other players' Ascent as this client sees it: their state and rope from the server's Seen messages, their moments
 * (a rope firing, wings opening, the boom) with sound and light. Fixed slots, nothing allocated per tick.
 */
public final class AscentRemote {
    private AscentRemote() {}

    static final class Slot {
        int entity = -1;
        AscentState state = AscentState.NONE;
        double ax, ay, az;
        int since;
        /** The fittings on their rope (RopeKit.bits), and the hand's distance from the anchor last tick (winding in?). */
        int kit;
        double ropeLen = -1, ropeLenO = -1;
    }

    static final Slot[] SLOTS = new Slot[32];

    static {
        for (int i = 0; i < SLOTS.length; i++) SLOTS[i] = new Slot();
    }

    static @Nullable Slot of(int entityId) {
        for (Slot s : SLOTS) if (s.entity == entityId) return s;
        return null;
    }

    /** The state another entity is in (NONE when unknown); for the local player, the prediction. */
    public static AscentState stateOf(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        if (e == mc.player) return AscentMoves.state();
        Slot s = of(e.getId());
        return s == null ? AscentState.NONE : s.state;
    }

    static void seen(AscentPayloads.Seen msg) {
        AscentState st = AscentState.byId(msg.state());
        if (st == null) return;
        Slot s = of(msg.entityId());
        if (st == AscentState.NONE) {
            if (s != null) {
                if (s.state.rope) AscentFx.loose(msg.entityId(), s.ax, s.ay, s.az);
                s.entity = -1;
            }
            AscentAnims.stop(msg.entityId());
            return;
        }
        if (s == null) s = free();
        AscentState was = s.entity == msg.entityId() ? s.state : AscentState.NONE;
        if (was.rope && !st.rope) AscentFx.loose(msg.entityId(), s.ax, s.ay, s.az);
        s.entity = msg.entityId();
        s.state = st;
        s.ax = msg.x();
        s.ay = msg.y();
        s.az = msg.z();
        s.since = AscentMoves.ticks;
        s.kit = msg.kit();
        s.ropeLen = s.ropeLenO = -1;
        AscentAnims.play(msg.entityId(), st, was);
    }

    /** Someone's rope shortening by more than a little a tick: they're winding it in (their gold reel shows). */
    static boolean winding(Slot s) {
        return s.ropeLen >= 0 && s.ropeLenO >= 0 && s.ropeLenO - s.ropeLen > 0.12;
    }

    static void event(AscentPayloads.SeenEvent msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(msg.entityId());
        if (e == null) return;
        switch (msg.what()) {
            case AscentPayloads.EV_FIRE -> AscentSounds.local(e, AscentContent.ROPE_FIRE.get(), 0.7F, 1F);
            case AscentPayloads.EV_LAUNCH -> AscentSounds.local(e, AscentContent.LAUNCH.get(), 0.7F, 1F);
            case AscentPayloads.EV_DEPLOY -> AscentSounds.local(e, AscentContent.WINGS_OPEN.get(), 0.9F, 1F);
            case AscentPayloads.EV_FLAP -> {
                AscentSounds.local(e, AscentContent.FLAP.get(), 0.8F, 1F);
                WingLayer.flap(e.getId());
            }
            case AscentPayloads.EV_CATCH -> AscentSounds.local(e, AscentContent.ROPE_STRAIN.get(), 0.6F, 0.9F);
            case AscentPayloads.EV_LAND -> AscentSounds.local(e, AscentContent.LAND.get(), Math.min(1F, 0.3F + msg.speed() * 0.5F), 1F);
            case AscentPayloads.EV_DOUBLE, AscentPayloads.EV_DASH -> {
                AscentSounds.local(e, AscentContent.LAUNCH.get(), 0.6F, msg.what() == AscentPayloads.EV_DOUBLE ? 1.3F : 1.15F);
                AscentFx.ring(e.getX(), e.getY() + (msg.what() == AscentPayloads.EV_DOUBLE ? 0.1 : 0.9), e.getZ(),
                        msg.what() == AscentPayloads.EV_DOUBLE ? new net.minecraft.world.phys.Vec3(0, 1, 0) : e.getLookAngle(), 0.3F);
            }
            case AscentPayloads.EV_DIVE -> AscentSounds.local(e, AscentContent.WINGS_FOLD.get(), 0.8F, 0.7F);
            case AscentPayloads.EV_SLAM_LAND -> {
                AscentSounds.local(e, AscentContent.BOOM.get(), 0.7F, 1.35F);
                AscentFx.ring(e.getX(), e.getY() + 0.15, e.getZ(), new net.minecraft.world.phys.Vec3(0, 1, 0), 0.5F);
            }
            case AscentPayloads.EV_ROLL -> {
                AscentSounds.local(e, AscentContent.LAUNCH.get(), 0.6F, 1.45F);
                AscentAnims.roll(e.getId(), msg.speed() >= 0 ? 1 : -1);
            }
            case AscentPayloads.EV_SOVEREIGN -> {
                if (e == mc.player) SovereignClient.begin(mc.player, (int) msg.speed());
                else SovereignClient.seen(e, true);
            }
            case AscentPayloads.EV_SOVEREIGN_END -> {
                if (e == mc.player) SovereignClient.end();
                else SovereignClient.seen(e, false);
            }
            case AscentPayloads.EV_JUDGEMENT -> SovereignClient.judgement(e, msg.speed(), e == mc.player);
            case AscentPayloads.EV_RAPPEL_KICK, AscentPayloads.EV_RAPPEL_PLANT -> {
                boolean kick = msg.what() == AscentPayloads.EV_RAPPEL_KICK;
                AscentSounds.local(e, kick ? AscentContent.RAPPEL_KICK.get() : AscentContent.RAPPEL_PLANT.get(), kick ? 0.75F : 0.5F, 1F);
                if (kick) AscentAnims.kick(e.getId());
                // the rope runs down their wall: grit off it at their feet
                Slot s = of(e.getId());
                if (s != null) {
                    double nx = e.getX() - s.ax, nz = e.getZ() - s.az, nl = Math.sqrt(nx * nx + nz * nz);
                    if (nl > 0.05) AscentFx.grit(e, e.getX() - nx / nl * 0.45, e.getY() + 0.35, e.getZ() - nz / nl * 0.45, nx / nl, nz / nl, kick ? 8 : 4);
                }
            }
            case AscentPayloads.EV_REMEMBER -> {
                // someone's body remembering: the swell, light rising round them, their wings unfolding once
                AscentSounds.local(e, AscentContent.REMEMBER.get(), 0.8F, 1F);
                remembering(e.getId());
            }
            case AscentPayloads.EV_BOOM -> {
                AscentSounds.local(e, AscentContent.BOOM.get(), 1F, 1F);
                AscentFx.ring(e.getX(), e.getY() + 0.9, e.getZ(), e.getDeltaMovement(), 1F);
            }
            default -> {
            }
        }
        if (msg.what() == AscentPayloads.EV_FIRE) {
            Slot s = of(e.getId());
            if (s != null && s.state.rope) AscentFx.bite(e, s.ax, s.ay, s.az);
        }
    }

    // others' "your body remembers" scenes: who, and how far in (a few at most)
    private static final int[] REMEMBERING = {-1, -1, -1, -1}, REMEMBER_AGE = new int[4];

    private static void remembering(int entityId) {
        for (int i = 0; i < REMEMBERING.length; i++) {
            if (REMEMBERING[i] < 0 || REMEMBERING[i] == entityId || i == REMEMBERING.length - 1) {
                REMEMBERING[i] = entityId;
                REMEMBER_AGE[i] = 0;
                return;
            }
        }
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        for (Slot s : SLOTS) {
            if (s.entity < 0) continue;
            Entity e = mc.level == null ? null : mc.level.getEntity(s.entity);
            if (e == null || e.isRemoved()) {
                s.entity = -1;
                continue;
            }
            if (s.state.rope) {
                double dx = e.getX() - s.ax, dy = e.getY() + dev.awaken.body.ascent.AscentRules.HAND_HEIGHT - s.ay, dz = e.getZ() - s.az;
                s.ropeLenO = s.ropeLen;
                s.ropeLen = Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
        }
        for (int i = 0; i < REMEMBERING.length; i++) {
            if (REMEMBERING[i] < 0) continue;
            Entity e = mc.level == null ? null : mc.level.getEntity(REMEMBERING[i]);
            int t = ++REMEMBER_AGE[i];
            if (e == null || mc.player == null || t > 80) {
                REMEMBERING[i] = -1;
                continue;
            }
            AscentScene.risingLight(mc.player, e, t);
            if (t == 34) WingLayer.flourish(e.getId(), 46);
        }
    }

    static void reset() {
        for (Slot s : SLOTS) s.entity = -1;
        java.util.Arrays.fill(REMEMBERING, -1);
    }

    private static Slot free() {
        for (Slot s : SLOTS) if (s.entity < 0) return s;
        Slot oldest = SLOTS[0];
        for (Slot s : SLOTS) if (s.since < oldest.since) oldest = s;
        return oldest;
    }
}
