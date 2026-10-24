package dev.awaken.mobs.client;

import dev.awaken.mobs.entity.RitualStatue;
import dev.awaken.mobs.entity.SeatedOne;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Where the ritual cast's eyes and the Seated One's palm are this frame, measured on the posed model by their
 * renderers (client API for the story's ritual effects). Null when the entity wasn't drawn in the last few frames.
 */
public final class RitualAnchors {
    private RitualAnchors() {}

    static final class Seen {
        Vec3 left = Vec3.ZERO, right = Vec3.ZERO, palm = Vec3.ZERO;
        long seenAt;
    }

    private static final Map<Integer, Seen> SEEN = new HashMap<>();

    static Seen of(int entity) {
        return SEEN.computeIfAbsent(entity, k -> new Seen());
    }

    static void forgetOld() {
        long now = net.minecraft.util.Util.getMillis();
        if (SEEN.size() > 64) SEEN.values().removeIf(s -> now - s.seenAt > 10_000);
    }

    /** Measured in the last quarter second (it was drawn), or null. */
    private static @Nullable Seen fresh(int entity) {
        Seen s = SEEN.get(entity);
        return s == null || net.minecraft.util.Util.getMillis() - s.seenAt > 250 ? null : s;
    }

    /** Between the two eyes. */
    public static @Nullable Vec3 eyes(SeatedOne e) {
        Seen s = fresh(e.getId());
        return s == null ? null : s.left.add(s.right).scale(0.5);
    }

    /** One eye: side -1 its right, +1 its left (as you face it, left and right on screen). */
    public static @Nullable Vec3 eye(SeatedOne e, int side) {
        Seen s = fresh(e.getId());
        return s == null ? null : side < 0 ? s.right : s.left;
    }

    public static @Nullable Vec3 palm(SeatedOne e) {
        Seen s = fresh(e.getId());
        return s == null ? null : s.palm;
    }

    /** How bright the Seated One's eyes are (0 closed, 1 open, more the moment they open and while it gazes). */
    public static float eyeLight(SeatedOne s, float partial) {
        float age = s.clientStageSeen == Long.MIN_VALUE ? 999 : (float) (s.level().getGameTime() - s.clientStageSeen) + partial;
        return switch (s.stage()) {
            case SeatedOne.WAKING -> age < 20 ? 0 : age < 58 ? 0.15F + 0.5F * (age - 20) / 38F : age < 70 ? 2.4F - (age - 58) * 0.11F : 1.0F;
            case SeatedOne.AWAKE -> 1.0F + 0.06F * net.minecraft.util.Mth.sin((s.tickCount + partial) * 0.21F);
            case SeatedOne.GAZE -> 1.0F + Math.min(1.2F, age / 40F);
            case SeatedOne.RAISE, SeatedOne.CLOSE -> 1.25F;
            case SeatedOne.SPENT -> Math.max(0, 1.0F - age / 40F);
            default -> 0;
        };
    }

    public static @Nullable Vec3 statueEyes(RitualStatue e) {
        Seen s = fresh(e.getId());
        return s == null ? null : s.left;
    }
}
