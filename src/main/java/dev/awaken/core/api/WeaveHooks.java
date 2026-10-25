package dev.awaken.core.api;

import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Weaves (SPEC §25 "Weaves"): a power cast in the beat after an opening the blade earned (a deflect, a perfect dodge, a
 * broken stance, a finisher in full rhythm) fuses with the weapon. The powers module asks {@link #costScale} for a
 * cast's energy and reports each landed cast through {@link #cast}; Arms installs the weaver. Server only; with
 * nothing installed casts are untouched.
 */
public final class WeaveHooks {
    private WeaveHooks() {}

    public interface Weaver {
        /** The share of its energy a cast costs now (1 outside a Weave's beat). */
        default float costScale(ServerPlayer player) {
            return 1F;
        }

        /** A cast landed: its power family (the enum's name), its Force if it has one (a name, or null) and its level. */
        default void cast(ServerPlayer player, String family, @Nullable String force, int level) {}
    }

    private static volatile Weaver weaver = new Weaver() {};

    public static void install(Weaver w) {
        weaver = w;
    }

    public static float costScale(ServerPlayer player) {
        return weaver.costScale(player);
    }

    public static void cast(ServerPlayer player, String family, @Nullable String force, int level) {
        weaver.cast(player, family, force, level);
    }
}
