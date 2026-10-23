package dev.awaken.core.audio.client;

import dev.awaken.Awaken;
import dev.awaken.core.audio.AudioNet;
import dev.awaken.core.audio.AudioSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Ambience beds, looped at their source while you are near and eased in and out (the Ambient/Environment slider):
 * <ul>
 *   <li>a rift tear's crackle and the tearing drone under it (the anomaly's own hum still pulses on top);</li>
 *   <li>a shimmer grove's glitter and breath;</li>
 *   <li>a falling star's whistle, following it down, and the hiss and ticking of the meteor while it cools (sealed),
 *   with a last settling sound when it opens;</li>
 *   <li>the Well's deep shaft drone, and Precursor ruins humming at night (from the server's emitter list).</li>
 * </ul>
 * Entities are tracked from join/leave events (no scans); everything else is checked every 10 ticks. The gate hum is
 * the gates module's own ({@code gates.client.GateHum}).
 */
final class Ambience {
    private Ambience() {}

    private static final Identifier RIFT = Awaken.id("rift_tear"), SHIMMER = Awaken.id("anomaly_shimmer"),
            FALLING_STAR = Awaken.id("anomaly_falling_star"), STAR_BLOCK = Awaken.id("anomaly_fallen_star");

    /** Start within this distance of the source, stop beyond STOP (blocks). */
    static final double RIFT_START = 40, GROVE_START = 30, STAR_START = 26, WELL_START = 44, RUIN_START = 36, SLACK = 8;

    private static final List<Entity> ENTITIES = new ArrayList<>();
    private static List<AudioNet.Emitter> emitters = List.of();
    private static final Map<Object, Bed> PLAYING = new HashMap<>();
    private static final Map<Entity, Bed> WHISTLES = new HashMap<>();
    private static int clock;

    static void onJoin(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide()) return;
        Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getEntity().getType());
        if (type.equals(RIFT) || type.equals(SHIMMER)) {
            ENTITIES.add(e.getEntity());
        } else if (type.equals(FALLING_STAR)) {
            Entity star = e.getEntity();
            Bed w = new Bed(AudioSounds.AMB_STAR_WHISTLE.get(), false, star::position, () -> 1.0, 0.05F);
            w.level = 1.0F; // a one-shot that follows the light down: no fade in
            WHISTLES.put(star, w);
            Minecraft.getInstance().getSoundManager().play(w);
        }
    }

    static void onLeave(EntityLeaveLevelEvent e) {
        if (!e.getLevel().isClientSide()) return;
        ENTITIES.remove(e.getEntity());
        Bed w = WHISTLES.remove(e.getEntity());
        if (w != null) w.release(0.45F); // the impact's boom covers the last 300 ms
    }

    static void emitters(List<AudioNet.Emitter> list) {
        emitters = List.copyOf(list);
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            clear();
            return;
        }
        if (++clock % 10 != 0) return;
        Vec3 me = mc.player.position();
        ENTITIES.removeIf(Entity::isRemoved);
        for (Entity ent : ENTITIES) {
            if (PLAYING.containsKey(ent)) continue;
            boolean rift = BuiltInRegistries.ENTITY_TYPE.getKey(ent.getType()).equals(RIFT);
            double start = rift ? RIFT_START : GROVE_START;
            if (ent.position().distanceTo(me) > start) continue;
            Bed b = rift ? new Bed(AudioSounds.AMB_RIFT.get(), true, () -> ent.position().add(0, 1.4, 0), () -> 1.0, 0.06F)
                    : new Bed(AudioSounds.AMB_GROVE.get(), true, () -> ent.position().add(0, 1.0, 0), () -> 0.9, 0.04F);
            b.stopAt = start + SLACK;
            b.alive = () -> !ent.isRemoved();
            start(b, ent);
        }
        long day = Math.floorMod(mc.level.getOverworldClockTime(), 24000L);
        for (AudioNet.Emitter em : emitters) {
            if (PLAYING.containsKey(em)) continue;
            Vec3 at = new Vec3(em.x() + 0.5, em.y() + 0.5, em.z() + 0.5);
            Bed b = switch (em.kind()) {
                case AudioNet.WELL -> at.distanceTo(me) > WELL_START ? null
                        : new Bed(AudioSounds.AMB_WELL.get(), true, () -> at, () -> 1.0, 0.03F);
                case AudioNet.RUIN -> at.distanceTo(me) > RUIN_START || night(day) <= 0.01 ? null
                        : new Bed(AudioSounds.AMB_RUIN_NIGHT.get(), true, () -> at, Ambience::ruinLevel, 0.02F);
                case AudioNet.STAR -> at.distanceTo(me) > STAR_START ? null : starBed(em, at);
                default -> null;
            };
            if (b == null) continue;
            b.stopAt = (em.kind() == AudioNet.WELL ? WELL_START : em.kind() == AudioNet.RUIN ? RUIN_START : STAR_START) + SLACK;
            b.alive = () -> emitters.contains(em);
            start(b, em);
        }
        List<Object> done = new ArrayList<>();
        PLAYING.forEach((key, b) -> {
            boolean gone = !b.alive.getAsBoolean() || b.pos.get().distanceTo(me) > b.stopAt;
            if (gone) b.end();
            if (b.isStopped() || (gone && !mc.getSoundManager().isActive(b))) done.add(key);
        });
        for (Object k : done) PLAYING.remove(k);
    }

    /** The meteor cools while sealed; when it opens, a last settling sound, once. */
    private static Bed starBed(AudioNet.Emitter em, Vec3 at) {
        Bed b = new Bed(AudioSounds.AMB_STAR_COOLING.get(), true, () -> at, () -> 0.85, 0.04F);
        BlockPos pos = new BlockPos(em.x(), em.y(), em.z());
        b.onTick = () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || b.ending) return;
            Boolean sealed = sealed(mc.level.getBlockState(pos));
            if (sealed == null || !sealed) {
                b.end();
                if (sealed != null) {
                    mc.getSoundManager().play(new SimpleSoundInstance(AudioSounds.AMB_STAR_COOLED.get(), SoundSource.AMBIENT, 0.9F, 1.0F,
                            SoundInstance.createUnseededRandom(), pos));
                }
            }
        };
        return b;
    }

    /** The fallen star block's "sealed" property, or null if the block there is something else. */
    static Boolean sealed(BlockState st) {
        if (!BuiltInRegistries.BLOCK.getKey(st.getBlock()).equals(STAR_BLOCK)) return null;
        for (Property<?> p : st.getProperties()) {
            if (p.getName().equals("sealed") && st.getValue(p) instanceof Boolean v) return v;
        }
        return null;
    }

    private static double ruinLevel() {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : 0.8 * night(Math.floorMod(level.getOverworldClockTime(), 24000L));
    }

    /** 0 by day, 1 at night, eased across dusk and dawn (overworld clock). */
    static double night(long day) {
        if (day >= 13500 && day <= 22500) return 1.0;
        if (day > 12000 && day < 13500) return (day - 12000) / 1500.0;
        if (day > 22500 && day < 23800) return (23800 - day) / 1300.0;
        return 0.0;
    }

    private static void start(Bed b, Object key) {
        PLAYING.put(key, b);
        Minecraft.getInstance().getSoundManager().play(b);
    }

    static void clear() {
        for (Bed b : PLAYING.values()) b.stopNow();
        for (Bed b : WHISTLES.values()) b.stopNow();
        PLAYING.clear();
        WHISTLES.clear();
        ENTITIES.clear();
        emitters = List.of();
    }

    /** One bed: a positional sound that follows its source, eased toward its target volume. */
    static final class Bed extends AbstractTickableSoundInstance {
        final Supplier<Vec3> pos;
        final DoubleSupplier target;
        float ease;
        float level;
        boolean ending;
        double stopAt = 64;
        java.util.function.BooleanSupplier alive = () -> true;
        Runnable onTick;

        Bed(SoundEvent event, boolean loop, Supplier<Vec3> pos, DoubleSupplier target, float ease) {
            super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.pos = pos;
            this.target = target;
            this.ease = ease;
            this.looping = loop;
            this.delay = 0;
            this.volume = 0.001F;
            Vec3 p = pos.get();
            this.x = p.x;
            this.y = p.y;
            this.z = p.z;
        }

        void end() {
            ending = true;
        }

        /** Ends with its own release speed (fraction of the remaining level per tick). */
        void release(float speed) {
            ease = speed;
            ending = true;
        }

        void stopNow() {
            stop();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (onTick != null) onTick.run();
            Vec3 p = pos.get();
            x = p.x;
            y = p.y;
            z = p.z;
            float goal = ending ? 0F : (float) target.getAsDouble();
            level += (goal - level) * (ease <= 0 ? 1F : ease);
            volume = Math.max(0.001F, level);
            if (ending && level < 0.01F) stop();
        }
    }
}
