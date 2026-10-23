package dev.awaken.mobs.def;

import dev.awaken.core.api.MobRole;
import net.minecraft.world.BossEvent;

/** How dangerous a creature is by design. Maps onto {@link MobRole.Role} for the rest of the mod. */
public enum Tier {
    NORMAL(MobRole.Role.NORMAL, 1.0),
    ELITE(MobRole.Role.ELITE, 1.0),
    WARDEN(MobRole.Role.WARDEN, 1.0),
    /** One of the 15 Gate Lords. */
    LORD(MobRole.Role.BOSS, 1.0),
    /** A Descent stratum guardian. */
    GUARDIAN(MobRole.Role.BOSS, 1.15),
    /** One of the eight Sovereigns of the Hollow Court. */
    SOVEREIGN(MobRole.Role.BOSS, 1.5),
    /** The Signal's Shadow. */
    FINAL(MobRole.Role.BOSS, 2.0),
    /** An authored nightmare template (Lucidium). */
    NIGHTMARE(MobRole.Role.NIGHTMARE, 1.0);

    public final MobRole.Role role;
    /** Extra health on top of the role multiplier (SPEC section 2 scaling stays the base). */
    public final double extraHealth;

    Tier(MobRole.Role role, double extraHealth) {
        this.role = role;
        this.extraHealth = extraHealth;
    }

    /** Has a boss bar, phases, an arena, never despawns. */
    public boolean isBoss() {
        return this == LORD || this == GUARDIAN || this == SOVEREIGN || this == FINAL || this == NIGHTMARE;
    }

    /** Counts for {@code awaken:bosses_killed} and the Power Core pity timer. */
    public boolean countsAsBoss() {
        return this == LORD || this == GUARDIAN || this == SOVEREIGN || this == FINAL;
    }

    public boolean hasBar() {
        return isBoss() || this == WARDEN;
    }

    public BossEvent.BossBarOverlay overlay() {
        return switch (this) {
            case LORD, GUARDIAN -> BossEvent.BossBarOverlay.NOTCHED_6;
            case SOVEREIGN, FINAL -> BossEvent.BossBarOverlay.NOTCHED_10;
            default -> BossEvent.BossBarOverlay.PROGRESS;
        };
    }
}
