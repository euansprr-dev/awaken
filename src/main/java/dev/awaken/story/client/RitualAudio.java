package dev.awaken.story.client;

import dev.awaken.mobs.Ritual;
import dev.awaken.story.RitualAudioRules;
import dev.awaken.story.RitualSounds;
import dev.awaken.story.net.StoryPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * One listener's ritual mix. Every voice and delayed cue belongs to this scene and this client level. The hall's pulse
 * (a drum and the tone under it, on the music channel) plays outside the effects' voice budget.
 */
final class RitualAudio {
    private RitualAudio() {}
    private static final int DIRECT = 0, HEAVY = 1, FOOT = 2, DETAIL = 3, PULSE = 4;
    private static final double EVERYWHERE = Double.POSITIVE_INFINITY;
    private static final Voice[] voices = new Voice[RitualAudioRules.MAX_VOICES];
    private static final Voice[] drums = new Voice[12];
    private static int drumNext, pulseBeat = -1, pulseAge = -1;
    private static final RitualAudioRules.Budget budget = new RitualAudioRules.Budget();
    private static final Map<SoundEvent, Long> nextSound = new HashMap<>();
    private static final Step[] steps = new Step[12];
    private static final Pending[] swings = new Pending[12];
    private static ClientLevel level;
    private static long readingUntil, nextHeart, voidToll;
    /** The bed, the drum and the tone dip under a heavy hit until this tick. */
    private static long heavyUntil;
    /** The lit plate's timer: when it lit (-1 stopped), where, its window and the pitch of its latch. */
    private static long plateLit = -1;
    private static Vec3 plate = Vec3.ZERO;
    private static int plateWindow;
    private static float plateLock;
    private static int scene = StoryPayloads.RitualScene.NONE;
    private static boolean suspended, closed;
    private static Bed bed, fading;
    private static Drone drone;

    private static final class Step {
        int id = -1;
        long seen;
        final RitualAudioRules.Footfall clock = new RitualAudioRules.Footfall();
    }
    private static final class Pending { int id = -1; long due; }
    static { for (int i = 0; i < 12; i++) { steps[i] = new Step(); swings[i] = new Pending(); } }

    static void init() { dev.awaken.core.audio.client.Soundscape.register(() -> bed == null || bed.isStopped() ? 0 : 1); }

    private static boolean playable() {
        var mc = Minecraft.getInstance();
        // Chat, the inventory or the K menu never silence the hall; a pause freezes the scene clock and the engine holds the voices.
        return level != null && mc.level == level && mc.player != null;
    }

    static void scene(int before, int after) {
        var current = Minecraft.getInstance().level;
        if (level != current || before != after && (before < 0 || after < 0)) reset();
        level = current;
        scene = after;
        if (before != after) {
            for (Pending p : swings) p.id = -1;
            plateLit = -1;
            closed = false;
        }
        // its hand rises (only live from the Judgement: a relog into the End never replays it)
        if (after == RitualClient.END && before == RitualClient.JUDGEMENT) play(RitualSounds.RAISE.get(), null, 0.8F, 1, DIRECT, 80);
        if (after == StoryPayloads.RitualScene.VOID && before != after) {
            nextHeart = RitualClient.ticks + 50;
            voidToll = RitualClient.ticks + 22;
        } else if (after == StoryPayloads.RitualScene.TEAR && before != after) {
            ui(RitualSounds.TEAR.get(), 1, 0.4F, 20);
        }
    }

    static void reset() {
        stopAll();
        level = null;
        scene = StoryPayloads.RitualScene.NONE;
        readingUntil = nextHeart = heavyUntil = 0;
        voidToll = -1;
        suspended = closed = false;
    }

    private static void stopAll() {
        var manager = Minecraft.getInstance().getSoundManager();
        for (int i = 0; i < voices.length; i++) {
            if (voices[i] != null) manager.stop(voices[i]);
            voices[i] = null;
        }
        for (int i = 0; i < drums.length; i++) {
            if (drums[i] != null) manager.stop(drums[i]);
            drums[i] = null;
        }
        if (bed != null) manager.stop(bed);
        if (fading != null) manager.stop(fading);
        if (drone != null) manager.stop(drone);
        bed = fading = null;
        drone = null;
        plateLit = -1;
        pulseBeat = pulseAge = -1;
        budget.clear();
        nextSound.clear();
        for (Step step : steps) { step.id = -1; step.clock.clear(); }
        for (Pending p : swings) p.id = -1;
    }

    /** Leaving the scene's level discards sounds and queued attacks; coming back never replays missed beats. */
    static void suspend() {
        if (!suspended) stopAll();
        suspended = true;
        voidToll = -1;
        nextHeart = RitualClient.ticks + 34;
    }

    static void tick() {
        if (!playable()) { suspend(); return; }
        suspended = false;
        prune();
        boolean hall = scene >= RitualClient.WAITING && scene < RitualClient.END;
        boolean dark = scene == StoryPayloads.RitualScene.VOID;
        if (hall || dark) {
            if (bed != null && bed.dark != dark) { // the hall's air never carries into the black: it fades under the new bed
                if (fading != null) Minecraft.getInstance().getSoundManager().stop(fading);
                fading = bed;
                fading.end();
                bed = null;
            }
            if (bed == null || bed.isStopped()) {
                bed = new Bed(dark);
                Minecraft.getInstance().getSoundManager().play(bed);
            }
        } else if (bed != null) { bed.end(); }
        pulse();
        plateTimer();
        if (dark) {
            if (voidToll >= 0 && RitualClient.ticks >= voidToll) {
                voidToll = -1;
                ui(RitualSounds.BELL.get(), 0.8F, 0.4F, 140);
            }
            if (RitualClient.ticks >= nextHeart) {
                ui(RitualSounds.HEARTBEAT.get(), 1, 0.38F, 30);
                nextHeart = RitualClient.ticks + 34;
            }
        }
        if (scene != RitualClient.JUDGEMENT) return;
        long now = RitualClient.ticks;
        // Tick every tracked statue, including those behind the camera. Rendering never emits sound.
        for (Entity e : level.entitiesForRendering()) {
            if (!Ritual.walking(e)) continue;
            Step slot = null;
            for (Step s : steps) if (s.id == e.getId()) { slot = s; break; }
            if (slot == null) for (Step s : steps) if (s.seen < now - 1) {
                slot = s; s.id = e.getId(); s.clock.clear(); break;
            }
            if (slot == null) continue;
            slot.seen = now;
            int plant = slot.clock.sample(Ritual.walkSeconds(e, 1), true);
            if (plant >= 0) {
                Vec3 at = Ritual.plantedFoot(e, plant);
                SoundEvent clip = switch (Math.floorMod(e.getId() + plant, 4)) {
                    case 0 -> RitualSounds.FOOTSTEP1.get(); case 1 -> RitualSounds.FOOTSTEP2.get();
                    case 2 -> RitualSounds.FOOTSTEP3.get(); default -> RitualSounds.FOOTSTEP4.get();
                };
                at(clip, at, 0.8F, 1, FOOT, 0, 48);
                RitualCamera.step(at); // its dust is RitualFx's (which keeps the same clock)
            }
        }
        for (Step s : steps) if (s.seen != now) { s.id = -1; s.clock.clear(); }
        for (Pending p : swings) if (p.id >= 0 && now >= p.due) {
            Entity e = level.getEntity(p.id);
            if (Ritual.windingUp(e)) {
                at(Math.floorMod(p.id, 2) == 0 ? RitualSounds.SWING.get() : RitualSounds.SWING2.get(), e.position(), 0.85F, 1, DETAIL, 3, 32);
            }
            p.id = -1;
        }
    }

    static void cue(RitualClient.Cue c) {
        if (!playable() || scene < 0) return;
        switch (c.kind) {
            case RitualClient.TOLL -> at(RitualSounds.BELL.get(), c.a, c.id < 0 ? 0.85F : 0.6F, c.f, DIRECT, 10, 96);
            case RitualClient.WORD -> {
                // the world dips while the words are read: the ring's rule stays up across its plates, its timers must not
                readingUntil = RitualClient.ticks + (c.id == 1 ? 90 : Math.max(60, c.ticks));
                ui(RitualSounds.WORDS.get(), c.id == 2 ? 0.84F : 1, c.id == 2 ? 0.4F : 0.45F, 20);
            }
            case RitualClient.CUE_DOORS -> at(RitualSounds.DOORS.get(), c.a, 1, 1, HEAVY, 80, 96);
            case RitualClient.LIGHTS_OUT -> at(RitualSounds.FLAMES_OUT.get(), c.a, 0.9F, 1, HEAVY, 20, 96);
            case RitualClient.CANDLE -> at(RitualSounds.CANDLE.get(), c.a, 0.4F, 0.8F + c.f * 0.6F, DETAIL, 6, 32);
            case RitualClient.PLATE -> {
                // the plate's timer: a grind rising under ticks from the plate itself, until you stand on it or time runs out
                stop(RitualSounds.PLATE_RISE.get());
                plateLit = RitualClient.ticks;
                plate = c.a;
                plateWindow = Math.max(1, c.ticks);
                plateLock = c.f;
                at(RitualSounds.PLATE_RISE.get(), c.a, 0.32F, 1, DIRECT, 0, EVERYWHERE);
            }
            case RitualClient.PLATE_ON -> {
                if (plateLit < 0) return;
                plateLit = -1;
                stop(RitualSounds.PLATE_RISE.get());
                ui(RitualSounds.PLATE_LOCK.get(), plateLock, 0.8F, 0);
            }
            case RitualClient.PLATE_MISS -> at(RitualSounds.BELL.get(), c.b, 0.42F, 0.8F, DIRECT, 10, 96);
            case RitualClient.RULE_STRIKE -> heavy(at(RitualSounds.IMPACT.get(), c.b, 1, 1, HEAVY, 4, 64), 24);
            // a statue's blade caught you (pits and the gaze have their own sound where they land)
            case RitualClient.HIT -> { if (c.id >= 0) ui(RitualSounds.STRUCK.get(), 1, 0.9F, 10); }
            case RitualClient.SHAKE -> {
                if (scene == RitualClient.WAKING && c.f >= 0.3F) at(RitualSounds.RUMBLE.get(), c.a, c.f >= 0.9F ? 0.75F : 0.6F, 1, HEAVY, 20, 96);
            }
            case RitualClient.EYES -> {
                // the god fills the hall: in both ears, not a point thirty blocks up
                if (c.f == 0) play(RitualSounds.EYES.get(), null, 0.9F, 1, HEAVY, 80);
                else heavyUntil = Math.max(heavyUntil, RitualClient.ticks + 40); // the bloom
            }
            case RitualClient.RISE -> heavy(at(RitualSounds.STATUE_RISE.get(), c.a, 0.95F, 1, HEAVY, 60, 96), 50);
            case RitualClient.GAZE_CHARGE -> at(RitualSounds.GAZE_CHARGE.get(), RitualFx.eyes(0), 0.8F, 1, DIRECT, 30, 96);
            case RitualClient.GAZE_FIRE -> {
                stop(RitualSounds.GAZE_CHARGE.get());
                heavy(at(RitualSounds.GAZE_FIRE.get(), c.a, 0.9F, 1, HEAVY, 30, 96), 30);
                heavy(at(RitualSounds.GAZE_IMPACT.get(), c.b, 1, 1, HEAVY, 30, 64), 30);
                Voice travel = at(RitualSounds.GAZE_TRAVEL.get(), c.a, 0.7F, 1, DIRECT, 30, EVERYWHERE);
                if (travel != null) { travel.from = c.a; travel.to = c.b; }
            }
            case RitualClient.SWING -> {
                for (Pending p : swings) if (p.id == c.id) return;
                at(RitualSounds.WINDUP.get(), c.a, 0.7F, 1, DETAIL, 0, 32); // its joints grind as the glaive goes up
                for (Pending p : swings) if (p.id < 0) { p.id = c.id; p.due = RitualClient.ticks + RitualAudioRules.SWING_SOUND_DELAY; break; }
            }
            case RitualClient.IMPACT -> heavy(at(Math.floorMod(c.id, 2) == 0 ? RitualSounds.IMPACT.get() : RitualSounds.IMPACT2.get(),
                    c.a, 1, 1, HEAVY, 4, 64), 24);
            case RitualClient.PIT -> { if (c.f == 1) at(RitualSounds.PIT.get(), RitualClient.altar, 0.9F, 1, HEAVY, 60, 64); }
            case RitualClient.DRAIN -> ui(RitualSounds.DRAIN.get(), 1, 0.6F, 80);
            case RitualClient.ERROR -> ui(RitualSounds.ERROR.get(), 1, 0.6F, 40);
            case RitualClient.DEATH -> {
                stopAll(); // the closing hand cuts the drain, the pulse and all room tails into black
                closed = true;
                ui(RitualSounds.CLOSE.get(), 1, 1, 20);
            }
            default -> {}
        }
    }

    /** A heavy hit that played: the bed, the drum and the tone dip under it for this many ticks. */
    private static void heavy(Voice played, int ticks) {
        if (played != null) heavyUntil = Math.max(heavyUntil, RitualClient.ticks + ticks);
    }

    /** The hall's pulse: the drum on its schedule, and the tone under it from the flames dying to the hand closing. */
    private static void pulse() {
        boolean on = scene >= RitualClient.DOORS && scene <= RitualClient.END && !closed && RitualAudioConfig.pulse() > 0;
        if (!on) {
            if (drone != null) drone.end();
            return;
        }
        int age = (int) RitualClient.beatAge(0);
        if (scene != pulseBeat || age != pulseAge) { // a beat resent with the same age never beats twice
            pulseBeat = scene;
            pulseAge = age;
            float g = RitualAudioRules.pulse(scene, age);
            if (g != 0) drum(g < 0 ? RitualSounds.PULSE_HARD.get() : RitualSounds.PULSE_SOFT.get(), Math.abs(g) * (RitualClient.ticks < heavyUntil ? 0.55F : 1));
        }
        if ((drone == null || drone.isStopped()) && RitualAudioRules.drone(scene, age) > 0) {
            drone = new Drone();
            Minecraft.getInstance().getSoundManager().play(drone);
        }
    }

    private static void drum(SoundEvent event, float gain) {
        var manager = Minecraft.getInstance().getSoundManager();
        if (drums[drumNext] != null) manager.stop(drums[drumNext]); // twelve tails at once at most
        Voice v = new Voice(event, null, gain, 1, PULSE);
        drums[drumNext] = v;
        drumNext = (drumNext + 1) % drums.length;
        manager.play(v);
    }

    /** The lit plate ticks from where it is, quicker and louder toward its deadline. */
    private static void plateTimer() {
        if (plateLit < 0) return;
        int age = (int) (RitualClient.ticks - plateLit);
        if (age >= plateWindow) { plateLit = -1; return; }
        int i = RitualAudioRules.plateTick(age);
        if (i < 0) return;
        float u = age / (float) plateWindow;
        at(i % 2 == 0 ? RitualSounds.PLATE_TICK.get() : RitualSounds.PLATE_TOCK.get(), plate, 0.55F + 0.45F * u, 1 + 0.12F * u, DIRECT, 0, EVERYWHERE);
    }

    private static void ui(SoundEvent event, float pitch, float gain, int gap) { play(event, null, gain, pitch, DIRECT, gap); }
    private static Voice at(SoundEvent event, Vec3 at, float gain, float pitch, int group, int gap, double range) {
        var player = Minecraft.getInstance().player;
        if (player == null || player.position().distanceTo(at) >= range) return null;
        return play(event, at, gain, pitch, group, gap);
    }
    private static Voice play(SoundEvent event, Vec3 at, float gain, float pitch, int group, int gap) {
        if (!playable() || RitualAudioConfig.effects() <= 0) return null;
        prune();
        long now = RitualClient.ticks;
        if (now < nextSound.getOrDefault(event, 0L)) return null;
        // Reserve the bed's voice before its first tick too (cues can arrive before tick starts it).
        boolean wantsBed = scene >= RitualClient.WAITING && scene < RitualClient.END || scene == StoryPayloads.RitualScene.VOID;
        int count = wantsBed || bed != null && !bed.isStopped() ? 1 : 0, heavy = 0, free = -1;
        for (int i = 0; i < voices.length; i++) {
            if (voices[i] == null) free = i;
            else { count++; if (voices[i].group == HEAVY) heavy++; }
        }
        // Guidance and the beam replace a small detail when the room has filled its voice budget.
        if (count >= RitualAudioRules.MAX_VOICES && group <= HEAVY) {
            for (int i = 0; i < voices.length; i++) if (voices[i] != null && voices[i].group >= FOOT) {
                Minecraft.getInstance().getSoundManager().stop(voices[i]); voices[i] = null; free = i; count--; break;
            }
        }
        // A heavy hit is never dropped for the heavy budget: the oldest heavy's tail fades out under it.
        if (RitualAudioRules.Budget.stealsHeavy(group, heavy)) {
            int oldest = -1;
            for (int i = 0; i < voices.length; i++) if (voices[i] != null && voices[i].group == HEAVY
                    && (oldest < 0 || voices[i].born < voices[oldest].born)) oldest = i;
            if (oldest >= 0) { voices[oldest].fadeOut(5); voices[oldest] = null; free = oldest; count--; heavy--; }
        }
        if (free < 0 || !budget.accept(group, now, count, heavy)) return null;
        Voice voice = new Voice(event, at, gain, pitch, group);
        voices[free] = voice;
        nextSound.put(event, now + gap);
        Minecraft.getInstance().getSoundManager().play(voice);
        return voice;
    }
    private static void prune() {
        var manager = Minecraft.getInstance().getSoundManager();
        for (int i = 0; i < voices.length; i++) if (voices[i] != null && RitualClient.ticks - voices[i].born > 2
                && !manager.isActive(voices[i])) voices[i] = null;
        if (bed != null && bed.isStopped()) bed = null;
        if (fading != null && fading.isStopped()) fading = null;
    }
    private static void stop(SoundEvent event) {
        for (int i = 0; i < voices.length; i++) if (voices[i] != null && voices[i].event == event) {
            Minecraft.getInstance().getSoundManager().stop(voices[i]); voices[i] = null;
        }
    }

    private static final class Voice extends AbstractTickableSoundInstance {
        final SoundEvent event;
        final int group;
        final long born = RitualClient.ticks;
        Vec3 from, to;
        private int fade = -1, fadeTicks;
        Voice(SoundEvent event, Vec3 at, float gain, float speed, int group) {
            super(event, group == PULSE ? SoundSource.MUSIC : at == null ? SoundSource.PLAYERS : SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
            this.event = event; this.group = group; volume = gain; pitch = speed;
            relative = at == null;
            if (relative) attenuation = Attenuation.NONE;
            else { x = at.x; y = at.y; z = at.z; }
        }
        /** Leaves the budget now and fades out over these ticks (a heavy hit stolen by a newer one). */
        void fadeOut(int ticks) { fade = fadeTicks = Math.max(1, ticks); }
        @Override public float getVolume() {
            float faded = fade >= 0 ? fade / (float) fadeTicks : 1;
            if (group == PULSE) return super.getVolume() * RitualAudioConfig.pulse();
            float duck = !relative && RitualClient.ticks < readingUntil ? 0.7F : 1;
            return super.getVolume() * RitualAudioConfig.effects() * duck * faded;
        }
        @Override public void tick() {
            if (!playable()) { stop(); return; }
            if (fade >= 0 && --fade <= 0) { stop(); return; }
            if (from != null) {
                float t = Math.clamp((RitualClient.ticks - born) / 12F, 0, 1);
                Vec3 p = from.lerp(to, t); x = p.x; y = p.y; z = p.z;
                if (t >= 1) stop();
            }
        }
    }
    private static final class Bed extends AbstractTickableSoundInstance {
        private final boolean dark;
        private boolean ending;
        Bed(boolean dark) {
            super(dark ? RitualSounds.VOID.get() : RitualSounds.HALL.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.dark = dark; looping = relative = true; attenuation = Attenuation.NONE; volume = 0.001F;
        }
        void end() { ending = true; }
        @Override public boolean canStartSilent() { return true; }
        @Override public void tick() {
            if (!playable()) { stop(); return; }
            float duck = RitualClient.ticks < heavyUntil ? 0.55F : 1; // the air dips under every heavy hit so it punches
            float target = ending ? 0 : (dark ? 0.25F : RitualClient.ticks < readingUntil ? 0.3F : 0.5F) * duck * RitualAudioConfig.ambience();
            volume += (target - volume) * (ending ? 0.12F : 0.04F);
            if (ending && volume < 0.001F) stop();
        }
    }

    /** The tone under the pulse (music): its level follows the scene, it dips under heavy hits and bends up through the End. */
    private static final class Drone extends AbstractTickableSoundInstance {
        private boolean ending;
        Drone() {
            super(RitualSounds.PULSE_DRONE.get(), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            looping = relative = true; attenuation = Attenuation.NONE; volume = 0.001F;
        }
        void end() { ending = true; }
        @Override public boolean canStartSilent() { return true; }
        @Override public void tick() {
            if (!playable()) { stop(); return; }
            float age = RitualClient.beatAge(0);
            float duck = RitualClient.ticks < heavyUntil ? 0.6F : 1;
            float target = ending ? 0 : RitualAudioRules.drone(scene, age) * duck * RitualAudioConfig.pulse();
            volume += (target - volume) * (ending ? 0.12F : 0.3F);
            pitch = RitualAudioRules.dronePitch(scene, age);
            if (ending && volume < 0.001F) stop();
        }
    }
}
