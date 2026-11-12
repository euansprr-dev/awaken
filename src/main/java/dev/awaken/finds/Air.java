package dev.awaken.finds;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A place's own air (SPEC §59.4): one fog colour and how close it comes, one mote, a recording or two on a loop. The
 * client fades it in over the last fourteen blocks to the place's edge and holds it at full strength inside. Common
 * code: it only names things; {@code finds.client.FindsClient} draws them.
 */
public final class Air {
    /** A recording played near the listener about every {@code every} ticks while the air is on. */
    public record Loop(Supplier<SoundEvent> sound, int every, float volume, float pitch) {}

    public final int fog;
    public final float far;
    public Supplier<ParticleOptions> mote = () -> null;
    public float motes;
    public float band = 6, drift = -0.01F;
    public boolean below;
    public final List<Loop> loops = new ArrayList<>();

    private Air(int fog, float far) {
        this.fog = fog;
        this.far = far;
    }

    /** Fog of a colour (0xRRGGBB) that closes to {@code far} blocks at full strength. */
    public static Air of(int fog, float far) {
        return new Air(fog, far);
    }

    /** Motes round the camera: about {@code perTick} a tick, up to {@code band} above the feet, drifting at {@code drift} a tick (negative falls). */
    public Air mote(Supplier<ParticleOptions> mote, float perTick, float band, float drift) {
        this.mote = mote;
        this.motes = perTick;
        this.band = band;
        this.drift = drift;
        return this;
    }

    public Air loop(Supplier<SoundEvent> sound, int every, float volume, float pitch) {
        loops.add(new Loop(sound, every, volume, pitch));
        return this;
    }

    /** Only while the camera is below the place's ground (a cave's air stays in the cave). */
    public Air below() {
        below = true;
        return this;
    }
}
