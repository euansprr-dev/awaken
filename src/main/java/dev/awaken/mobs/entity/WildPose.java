package dev.awaken.mobs.entity;

/**
 * Held poses of the P3 bodies, read by {@code mobs.client.anim.WildRenderer} every frame from synced data. Each bit
 * plays its own clip of the creature's rig on top of the idle and the gait (a clip the rig lacks is skipped).
 */
public interface WildPose {
    /** A drip lurker hanging from the roof (the body flips), a cricket singing, a wickling reaching up to a vine. */
    int S_HANG = 1, S_CHIRP = 1 << 1, S_RIPEN = 1 << 2;
    /** A hushling's ears flared at a sound, a jelly's bell emptied, an otter carrying a find in its mouth. */
    int S_LISTEN = 1 << 3, S_DEFLATE = 1 << 4, S_CARRY = 1 << 5;
    /** A mount resting (at the Hearth or where it was told to stay), a fish out of water, ridden, wearing a bridle. */
    int S_REST = 1 << 6, S_BEACHED = 1 << 7, S_RIDDEN = 1 << 8, S_BRIDLED = 1 << 9;
    /** In water (swimmers pitch with their swim and play their swim loop). */
    int S_SWIM = 1 << 10;
    /** Asleep: the seep heart's plates shut round its core (its "sleep" clip, eased slowly in and out). */
    int S_SLEEP = 1 << 11;

    /** The pose bits this frame (client side). */
    int wildState();

    /** Seconds since the body's last heartbeat (its "beat" clip plays once from it), or -1 for none. Client side. */
    default float beatTime(float partialTicks) {
        return -1.0F;
    }
}
