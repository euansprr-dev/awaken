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

    /** What the Armoury's list says of each style: the wall must agree with it. */
    public static java.util.List<dev.awaken.arms.net.ArmsPayloads.StyleEntry> styles(ServerPlayer p) {
        return ArmsArmoury.view(p, ArmsArmoury.BAND, false).styles();
    }
}
