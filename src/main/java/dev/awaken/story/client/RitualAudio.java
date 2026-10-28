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

/** One listener's ritual mix. Every voice and delayed cue belongs to this scene and this client level. */
final class RitualAudio {
    private RitualAudio() {}
    private static final int DIRECT = 0, HEAVY = 1, FOOT = 2, DETAIL = 3;
    private static final Voice[] voices = new Voice[RitualAudioRules.MAX_VOICES];
    private static final RitualAudioRules.Budget budget = new RitualAudioRules.Budget();
    private static final Map<SoundEvent, Long> nextSound = new HashMap<>();
    private static final Step[] steps = new Step[12];
    private static final Pending[] swings = new Pending[12];
    private static ClientLevel level;
    private static long readingUntil, nextHeart, voidToll;
    private static int scene = StoryPayloads.RitualScene.NONE;
    private static boolean suspended;
    private static Bed bed;

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
        return level != null && mc.level == level && mc.player != null && mc.gui.screen() == null && !mc.isPaused();
    }

    static void scene(int before, int after) {
        var current = Minecraft.getInstance().level;
        if (level != current || before != after && (before < 0 || after < 0)) reset();
        level = current;
        scene = after;
        if (before != after) for (Pending p : swings) p.id = -1;
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
        readingUntil = nextHeart = 0;
        voidToll = -1;
        suspended = false;
    }

    private static void stopAll() {
        var manager = Minecraft.getInstance().getSoundManager();
        for (int i = 0; i < voices.length; i++) {
            if (voices[i] != null) manager.stop(voices[i]);
            voices[i] = null;
        }
        if (bed != null) manager.stop(bed);
        bed = null;
        budget.clear();
        nextSound.clear();
        for (Step step : steps) { step.id = -1; step.clock.clear(); }
        for (Pending p : swings) p.id = -1;
    }

    /** Menus/pause discard sounds and queued attacks; closing them never replays missed beats. */
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
            if (bed == null || bed.isStopped()) {
                bed = new Bed(dark);
                Minecraft.getInstance().getSoundManager().play(bed);
            }
        } else if (bed != null) { bed.end(); }
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
                SoundEvent clip = switch (Math.floorMod(e.getId() + plant, 3)) {
                    case 0 -> RitualSounds.FOOTSTEP1.get(); case 1 -> RitualSounds.FOOTSTEP2.get(); default -> RitualSounds.FOOTSTEP3.get();
                };
                at(clip, at, 0.48F, 1, FOOT, 0, 36);
                RitualCamera.shakeNear(at, 0.28F, 5, 12);
            }
        }
        for (Step s : steps) if (s.seen != now) { s.id = -1; s.clock.clear(); }
        for (Pending p : swings) if (p.id >= 0 && now >= p.due) {
            Entity e = level.getEntity(p.id);
            if (Ritual.windingUp(e)) at(RitualSounds.SWING.get(), e.position(), 0.5F, 1, DETAIL, 3, 24);
            p.id = -1;
        }
    }

    static void cue(RitualClient.Cue c) {
        if (!playable() || scene < 0) return;
        switch (c.kind) {
            case RitualClient.TOLL -> at(RitualSounds.BELL.get(), c.a, c.id < 0 ? 0.6F : 0.38F, c.f, DIRECT, 100, 96);
            case RitualClient.WORD -> {
                readingUntil = RitualClient.ticks + Math.max(60, c.ticks);
                ui(RitualSounds.WORDS.get(), c.id == 2 ? 0.84F : 1, 0.35F, 20);
            }
            case RitualClient.CUE_DOORS -> at(RitualSounds.DOORS.get(), c.a, 0.6F, 1, HEAVY, 80, 64);
            case RitualClient.LIGHTS_OUT -> at(RitualSounds.FLAMES_OUT.get(), c.a, 0.6F, 1, DETAIL, 20, 64);
            case RitualClient.CANDLE -> at(RitualSounds.CANDLE.get(), c.a, 0.32F, 0.8F + c.f * 0.6F, DETAIL, 6, 24);
            case RitualClient.PLATE_MISS -> at(RitualSounds.BELL.get(), c.b, 0.3F, 0.8F, DIRECT, 100, 96);
            case RitualClient.RULE_STRIKE -> at(RitualSounds.IMPACT.get(), c.b, 0.55F, 1, HEAVY, 4, 32);
            case RitualClient.HIT -> { /* The world impact already gives this blow its sound; do not double it in UI. */ }
            case RitualClient.SHAKE -> {
                if (scene == RitualClient.WAKING && c.f >= 0.3F) at(RitualSounds.RUMBLE.get(), c.a, 0.5F, 1, DETAIL, 60, 64);
            }
            case RitualClient.EYES -> { if (c.f == 0) at(RitualSounds.EYES.get(), c.a, 0.6F, 1, HEAVY, 80, 96); }
            case RitualClient.RISE -> at(RitualSounds.STATUE_RISE.get(), c.a, 0.55F, 1, HEAVY, 60, 64);
            case RitualClient.GAZE_CHARGE -> at(RitualSounds.GAZE_CHARGE.get(), RitualFx.eyes(0), 0.6F, 1, DIRECT, 30, 48);
            case RitualClient.GAZE_FIRE -> {
                stop(RitualSounds.GAZE_CHARGE.get());
                at(RitualSounds.GAZE_FIRE.get(), c.a, 0.55F, 1, HEAVY, 30, 64);
                at(RitualSounds.GAZE_IMPACT.get(), c.b, 0.55F, 1, HEAVY, 30, 48);
                Voice travel = at(RitualSounds.GAZE_TRAVEL.get(), c.a, 0.45F, 1, DIRECT, 30, Double.POSITIVE_INFINITY);
                if (travel != null) { travel.from = c.a; travel.to = c.b; }
            }
            case RitualClient.SWING -> {
                for (Pending p : swings) if (p.id == c.id) return;
                for (Pending p : swings) if (p.id < 0) { p.id = c.id; p.due = RitualClient.ticks + RitualAudioRules.SWING_SOUND_DELAY; break; }
            }
            case RitualClient.IMPACT -> at(RitualSounds.IMPACT.get(), c.a, 0.55F, 1, HEAVY, 4, 32);
            case RitualClient.PIT -> { if (c.f == 1) at(RitualSounds.PIT.get(), RitualClient.altar, 0.55F, 1, HEAVY, 60, 48); }
            case RitualClient.DRAIN -> ui(RitualSounds.DRAIN.get(), 1, 0.5F, 80);
            case RitualClient.DEATH -> {
                stopAll(); // the closing hand cuts the drain and all room tails into black
                ui(RitualSounds.CLOSE.get(), 1, 0.65F, 20);
            }
            default -> {}
        }
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
        Voice(SoundEvent event, Vec3 at, float gain, float speed, int group) {
            super(event, at == null ? SoundSource.PLAYERS : SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
            this.event = event; this.group = group; volume = gain; pitch = speed;
            relative = at == null;
            if (relative) attenuation = Attenuation.NONE;
            else { x = at.x; y = at.y; z = at.z; }
        }
        @Override public float getVolume() {
            float duck = !relative && RitualClient.ticks < readingUntil ? 0.45F : 1;
            return super.getVolume() * RitualAudioConfig.effects() * duck;
        }
        @Override public void tick() {
            if (!playable()) { stop(); return; }
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
            float target = ending ? 0 : (dark ? 0.25F : RitualClient.ticks < readingUntil ? 0.16F : 0.5F) * RitualAudioConfig.ambience();
            volume += (target - volume) * (ending ? 0.12F : 0.04F);
            if (ending && volume < 0.001F) stop();
        }
    }
}
