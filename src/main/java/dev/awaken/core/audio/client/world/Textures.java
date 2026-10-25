package dev.awaken.core.audio.client.world;

import dev.awaken.core.audio.world.SoundscapeDef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * The Conductor's textures (docs/sound/PLAN.md §6.1): continuous sounds that belong to a spot, at most
 * {@value #MAX} loops at once, placed on real blocks the {@link Listener} found (the canopy overhead, the open side of
 * the ridge, the water). They play in the world, so Sound Physics gives them the room and its walls; it judges a loop
 * once, when it starts, so a texture whose spot has moved more than {@value #MOVE} blocks, or that has played
 * {@value #RENEW_TICKS} ticks, starts again at its new spot while the old one fades: walking indoors muffles it.
 */
final class Textures {
    static final int MAX = 6;
    private static final double MOVE = 6.0;
    private static final int RENEW_TICKS = 240;
    private static final float EASE = 0.04F;

    private final Loop[] loops = new Loop[MAX];

    /** Sets this tick's goal for a texture (key: its soundscape and index) heard from {@code (x, y, z)}. */
    void drive(Minecraft mc, Object key, Identifier sound, float goal, float pitch, double x, double y, double z, boolean placed) {
        Loop cur = null;
        for (Loop l : loops) if (l != null && l.key == key && !l.leaving) cur = l;
        if (cur != null) cur.driven = true;
        if (cur != null && (!placed || goal < 0.01F)) {
            cur.goal = 0F;
            return;
        }
        if (!placed || goal < 0.02F) return;
        if (cur != null) {
            cur.goal = goal;
            cur.pitchGoal = pitch;
            double dx = cur.getX() - x, dy = cur.getY() - y, dz = cur.getZ() - z;
            boolean moved = dx * dx + dy * dy + dz * dz >= MOVE * MOVE && cur.age >= 60; // not on every flicker of the ring
            if (!moved && cur.age < RENEW_TICKS) return;
            cur.leaving = true; // move: a new loop at the new spot fades in while this one fades out
            cur.goal = 0F;
        }
        int free = -1;
        for (int i = 0; i < MAX; i++) if (loops[i] == null || loops[i].isStopped()) free = i;
        if (free < 0) return; // full: the texture stays where it was, or waits
        Loop l = new Loop(key, sound, x, y, z, pitch);
        l.goal = goal;
        loops[free] = l;
        mc.getSoundManager().play(l);
    }

    /** Fades every texture that wasn't driven this tick. */
    void endTick() {
        for (int i = 0; i < MAX; i++) {
            Loop l = loops[i];
            if (l == null) continue;
            if (l.isStopped()) {
                loops[i] = null;
                continue;
            }
            if (!l.driven) l.goal = 0F;
            l.driven = false;
        }
    }

    int voices() {
        int n = 0;
        for (Loop l : loops) if (l != null && !l.isStopped()) n++;
        return n;
    }

    void stopAll() {
        for (int i = 0; i < MAX; i++) {
            if (loops[i] != null) loops[i].goal = 0F;
        }
    }

    String status() {
        StringBuilder sb = new StringBuilder("textures:");
        for (Loop l : loops) {
            if (l != null && !l.isStopped()) {
                sb.append(String.format(java.util.Locale.ROOT, " %s %.2f%s", l.getIdentifier().getPath(), l.level, l.leaving ? " (leaving)" : ""));
            }
        }
        return sb.toString();
    }

    /** One texture: a loop at a spot, eased toward its goal; it stops itself once silent. */
    private static final class Loop extends AbstractTickableSoundInstance {
        final Object key;
        float goal, pitchGoal = 1F, level;
        int age;
        boolean leaving, driven = true;

        Loop(Object key, Identifier sound, double x, double y, double z, float pitch) {
            super(net.minecraft.sounds.SoundEvent.createVariableRangeEvent(sound), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.key = key;
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.NONE; // the Conductor sets the level; the position pans it
            this.volume = 0.001F;
            this.pitch = this.pitchGoal = pitch;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            age++;
            level += (goal - level) * EASE;
            volume = Math.max(0.001F, level);
            pitch += (pitchGoal - pitch) * 0.02F;
            if (goal < 0.01F && level < 0.01F) stop();
        }
    }

    /** The goal's drive factor for a texture (see {@link SoundscapeDef.Drive}). */
    static float drive(SoundscapeDef.Drive d, Listener at) {
        return switch (d) {
            case CONSTANT -> 1F;
            case RUSTLE -> at.rustle();
            case HIGH_WIND -> at.highWind();
            case OPEN -> Mth.clamp((at.open - 0.15F) / 0.85F, 0F, 1F);
            case WIND -> 0.15F + 0.85F * at.strength * (0.6F + 0.4F * at.gust);
        };
    }
}
