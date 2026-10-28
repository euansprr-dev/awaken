package dev.awaken.body;

import dev.awaken.body.ascent.Ascent;
import dev.awaken.body.ascent.AscentState;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/** Current climbing appearance, kept separately from inventory and sent even to observers who missed the start. */
public final class BodyStow {
    private BodyStow() {}

    public static boolean climbVault(int kind) {
        return kind == VaultRules.HIGH || kind == VaultRules.ONTO || kind == VaultRules.OVER;
    }

    static boolean climbing(@Nullable BodyMotion m, AscentState ascent, long now) {
        return ascent.rope || m != null && (m.hanging || m.gripping || m.runClimb && m.runUntil > now
                || m.mantleUntil > now || m.climbOutUntil > now || m.vaultUntil > now && climbVault(m.vaultKind));
    }

    static void sync(ServerPlayer p) {
        boolean stowed = !BodyServer.off(p) && !p.isInLiquid()
                && climbing(p.getExistingDataOrNull(BodyContent.MOTION), Ascent.state(p), BodyServer.now(p));
        if (stowed != Boolean.TRUE.equals(p.getExistingDataOrNull(BodyContent.STOWED))) p.setData(BodyContent.STOWED, stowed);
    }

    /** A dimension change or replacement player has no climb to carry across. Nothing here is saved. */
    static void reset(ServerPlayer p) {
        BodyMotion m = p.getExistingDataOrNull(BodyContent.MOTION);
        if (m != null) {
            long now = BodyServer.now(p);
            BodyServer.endTactical(p, m, now, false);
            BodyServer.endSlide(p, m, now, false);
            BodyServer.endVault(p, m, now);
            p.removeData(BodyContent.MOTION);
        }
        if (p.hasData(BodyContent.STOWED)) p.removeData(BodyContent.STOWED);
    }
}
