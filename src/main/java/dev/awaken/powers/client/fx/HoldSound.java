package dev.awaken.powers.client.fx;

import dev.awaken.powers.fx.CastProfiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * A channel's held sound (a cast profile's {@code holdSound}; Charged Shot's charge): it plays from the cast's origin (the
 * ball between the hands) and follows it, for the caster and everyone near, and is cut on the let-go with a two-tick fade, so
 * the release's crack lands on silence. The file carries the build itself (it rises to the full charge), so a short hold
 * stops partway up. One per entity.
 */
final class HoldSound extends AbstractTickableSoundInstance {
    private static final int SLOTS = 16;
    private static final HoldSound[] PLAYING = new HoldSound[SLOTS];
    private static final double[] P = new double[3];
    /** Ticks of fade after the let-go. */
    private static final int FADE = 2;

    private final int entity, profile;
    private final float full;
    private int fading = -1;

    private HoldSound(SoundEvent sound, int entity, int profile, float volume) {
        super(sound, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.entity = entity;
        this.profile = profile;
        this.full = volume;
        this.volume = volume;
        this.looping = false;
        this.delay = 0;
    }

    /** Starts a profile's held sound on an entity (stopping one it had), placed at the entity's origin this frame. */
    static void start(int entityId, CastProfiles.Profile prof) {
        if (prof.holdSound() == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(entityId);
        SoundEvent ev = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse(prof.holdSound()));
        if (e == null || ev == null) return;
        int slot = -1;
        for (int i = 0; i < SLOTS; i++) {
            HoldSound h = PLAYING[i];
            if (h != null && (h.entity == entityId || h.isStopped())) {
                if (!h.isStopped()) h.stop();
                PLAYING[i] = null;
            }
            if (PLAYING[i] == null && slot < 0) slot = i;
        }
        if (slot < 0) return;
        HoldSound h = new HoldSound(ev, entityId, prof.id(), 1f);
        h.place(e);
        PLAYING[slot] = h;
        mc.getSoundManager().play(h);
    }

    static void reset() {
        for (int i = 0; i < SLOTS; i++) {
            if (PLAYING[i] != null && !PLAYING[i].isStopped()) PLAYING[i].stop();
            PLAYING[i] = null;
        }
    }

    private void place(Entity e) {
        Anchors.of(e, FxRecipes.originAnchor(e), 1f, P);
        x = P[0];
        y = P[1];
        z = P[2];
    }

    @Override
    public void tick() {
        var level = Minecraft.getInstance().level;
        Entity e = level == null ? null : level.getEntity(entity);
        if (e == null || e.isRemoved()) {
            stop();
            return;
        }
        place(e);
        if (fading < 0 && !CastAnims.holding(entity, profile)) fading = 0;
        if (fading >= 0) {
            volume = full * Math.max(0f, 1f - ++fading / (float) FADE);
            if (fading >= FADE) stop();
        }
    }
}
