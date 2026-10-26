package dev.awaken.living.client;

import dev.awaken.Awaken;
import dev.awaken.core.audio.client.Soundscape;
import dev.awaken.core.audio.client.world.Conductor;
import dev.awaken.living.LivingContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Happenings heard before they are seen (world sound, SPEC §13; docs/sound/PLAN.md §6.3): each tell the server sends
 * ({@link PulseFx#SHOWN}) sounds from where it is now, louder as you come near, so a rumour can be followed by ear. A
 * stampede rumbles, a murmuration's wings turn, the Deep Tide rolls in (and the world falls quiet under it), a caravan's
 * bells walk, the Wild Hunt's horn calls and its hounds bay (and everything else falls silent for 200 blocks), kites cry
 * over a carrion circle, a migrating herd lows, two beasts roar, a storm's rain roars from under its cell (its thunder is
 * {@link StormSky}'s, late by its distance). Registered with the world sound Conductor: its voices count in the budget,
 * and a happening near lets nature step back.
 */
final class PulseSound {
    private PulseSound() {}

    private static final Identifier BAY = Awaken.id("fauna.wisp_hound.ambient"), KITE = Awaken.id("fauna.peak_kite.ambient"),
            LOW = Identifier.withDefaultNamespace("entity.cow.ambient"), ROAR = Identifier.withDefaultNamespace("entity.ravager.ambient");
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Map<Integer, Loop> LOOPS = new HashMap<>();
    private static final Map<Integer, Integer> NEXT = new HashMap<>();
    private static float emphasis;
    private static int clock;

    static void init() {
        Soundscape.register(new Conductor.Source() {
            @Override
            public int voices() {
                return LOOPS.size();
            }

            @Override
            public float emphasis() {
                return emphasis;
            }
        });
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            reset();
            return;
        }
        StormSky.tick(mc);
        if (++clock % 5 != 0) return;
        Vec3 me = mc.player.position();
        float now = LivingClient.ticks, loudest = 0F;
        for (Map.Entry<Integer, PulseFx.Shown> e : PulseFx.SHOWN.entrySet()) {
            int id = e.getKey();
            PulseFx.Shown s = e.getValue();
            float k = Mth.clamp((now - s.since) / PulseFx.LERP, 0F, 1F);
            double x = Mth.lerp(k, s.px, s.x), y = Mth.lerp(k, s.py, s.y), z = Mth.lerp(k, s.pz, s.z);
            float d = (float) me.distanceTo(new Vec3(x, y, z));
            switch (s.tell) {
                case "dustline" -> loudest = Math.max(loudest, loop(mc, id, LivingContent.TELL_STAMPEDE.get(), x, y, z, d, 260F, 0.9F));
                case "flock" -> loudest = Math.max(loudest, loop(mc, id, LivingContent.TELL_WINGS.get(), x, y + 12, z, d, 200F, 0.7F));
                case "lanterns" -> loudest = Math.max(loudest, loop(mc, id, LivingContent.TELL_BELLS.get(), x, y + 1, z, d, 160F, 0.6F));
                case "fog" -> {
                    loudest = Math.max(loudest, loop(mc, id, LivingContent.TELL_TIDE.get(), x, y, z, d, 320F, 0.8F));
                    Soundscape.hush(s, new Vec3(x, y, z), Math.max(200F, s.radius * 1.5F), 0.3F, 30);
                }
                case "ghost" -> {
                    loudest = Math.max(loudest, call(mc, id, LivingContent.TELL_HORN.get().location(), x, y + 4, z, d, 400F, 0.9F, 1F, 500, 800));
                    call(mc, -id - 1, BAY, x, y, z, d, 220F, 0.7F, 0.7F, 120, 200);
                    Soundscape.hush(s, new Vec3(x, y, z), 200F, 0.1F, 30);
                }
                case "circle" -> loudest = Math.max(loudest, call(mc, id, KITE, x, y + 20, z, d, 180F, 0.6F, 0.9F, 100, 180));
                case "haze" -> loudest = Math.max(loudest, call(mc, id, LOW, x, y, z, d, 220F, 0.5F, 0.6F, 120, 200));
                case "roar" -> loudest = Math.max(loudest, call(mc, id, ROAR, x, y, z, d, 260F, 0.7F, 0.7F, 100, 160));
                case "storm" -> {
                    // its rain roaring under the cell, heard from the core (a little ahead of the centre)
                    double rx = x + s.dx * dev.awaken.living.pulse.StormCell.RAIN_AHEAD, rz = z + s.dz * dev.awaken.living.pulse.StormCell.RAIN_AHEAD;
                    float dr = (float) me.distanceTo(new Vec3(rx, y + 20, rz));
                    loudest = Math.max(loudest, loop(mc, id, LivingContent.TELL_STORM.get(), rx, y + 20, rz, dr, 340F, 0.75F));
                }
                default -> {}
            }
        }
        LOOPS.entrySet().removeIf(e -> {
            Loop l = e.getValue();
            if (!PulseFx.SHOWN.containsKey(e.getKey())) l.goal = 0F;
            return l.isStopped();
        });
        NEXT.keySet().removeIf(id -> !PulseFx.SHOWN.containsKey(id < 0 ? -id - 1 : id)); // a hunt's bay is keyed -id - 1
        emphasis = 0.8F * loudest;
    }

    /** How near a tell is, 0 at the edge of hearing to 1 on top of it, shaped so the far half is faint. */
    private static float near(float d, float hear) {
        float g = Mth.clamp(1F - d / hear, 0F, 1F);
        return g * g;
    }

    /** A tell's loop at its centre, its level following the distance; returns how near it is. */
    private static float loop(Minecraft mc, int id, SoundEvent sound, double x, double y, double z, float d, float hear, float base) {
        float g = near(d, hear);
        Loop l = LOOPS.get(id);
        if (l == null || l.isStopped()) {
            if (g <= 0.01F) return g;
            l = new Loop(sound, x, y, z);
            LOOPS.put(id, l);
            mc.getSoundManager().play(l);
        }
        l.goal = base * g;
        l.to(x, y, z);
        return g;
    }

    /** A tell's call now and then (a horn, a bay, a cry), every {@code min..max} ticks; returns how near it is. */
    private static float call(Minecraft mc, int id, Identifier sound, double x, double y, double z, float d, float hear, float base,
                              float pitch, int min, int max) {
        float g = near(d, hear);
        Integer due = NEXT.get(id);
        if (due == null) {
            NEXT.put(id, clock + min / 2 + RANDOM.nextInt(Math.max(1, max - min)));
            return g;
        }
        if (clock < due) return g;
        NEXT.put(id, clock + min + RANDOM.nextInt(Math.max(1, max - min)));
        if (g > 0.01F) {
            mc.getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.AMBIENT, base * g, pitch * (0.95F + 0.1F * RANDOM.nextFloat()),
                    RANDOM, false, 0, SoundInstance.Attenuation.NONE, x, y, z, false));
        }
        return g;
    }

    static void reset() {
        StormSky.reset();
        StormFallback.reset();
        for (Loop l : LOOPS.values()) l.goal = 0F;
        LOOPS.clear();
        NEXT.clear();
        emphasis = 0F;
    }

    /** A tell's loop: placed at the happening (attenuation off: the distance is in its level), eased, gone once silent. */
    private static final class Loop extends AbstractTickableSoundInstance {
        float goal, level;
        private double tx, ty, tz;

        Loop(SoundEvent sound, double x, double y, double z) {
            super(sound, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.001F;
            this.x = tx = x;
            this.y = ty = y;
            this.z = tz = z;
        }

        void to(double x, double y, double z) {
            tx = x;
            ty = y;
            tz = z;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            x += (tx - x) * 0.1;
            y += (ty - y) * 0.1;
            z += (tz - z) * 0.1;
            level += (goal - level) * 0.05F;
            volume = Math.max(0.001F, level);
            if (goal < 0.005F && level < 0.005F) stop();
        }
    }
}
