package dev.awaken.arms;

import net.minecraft.server.level.ServerPlayer;

/** What the Hall of Arms (SPEC §52) asks of Arms' own books. */
public final class ArmsApiAccess {
    private ArmsApiAccess() {}

    public static boolean knows(ServerPlayer p, String style) {
        return ArmsIssue.INSTANCE.knows(p, style);
    }

    /** Dev (/awaken hall learn): teaches a pattern as Hale's rack would. */
    public static boolean learn(ServerPlayer p, String style) {
        return ArmsIssue.INSTANCE.learn(p, style, "the Hall");
    }
}
