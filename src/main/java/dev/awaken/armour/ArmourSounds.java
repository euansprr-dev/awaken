package dev.awaken.armour;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;

import java.util.Locale;

/**
 * What a set sounds like (SPEC §44.12): each of the 29 is made of one of eight things, and that decides the sound of
 * putting a piece on, of walking in it and of a blow landing on it. The sounds themselves are Minecraft's own recordings,
 * named in sounds.json (tools/art/armour_sounds.py).
 */
public final class ArmourSounds {
    private ArmourSounds() {}

    /** What a set is made of, and how loud its stride is beside plate's (each recording has its own level too). */
    public enum Family {
        HIDE(0.85F), CLOTH(0.8F), MAIL(0.95F), PLATE(1F), HEAVY(1.1F), CRYSTAL(0.95F), BONE(0.95F), FEATHER(0.8F);

        public final float stride;
        public final String id = name().toLowerCase(Locale.ROOT);

        Family(float stride) {
            this.stride = stride;
        }
    }

    public static Family of(ArmourSet set) {
        return switch (set) {
            case WILDHIDE, ROOTWARDEN, NIGHTCOURT -> Family.HIDE;
            case CLEFTRUNNER, VEILWOVEN, SOVEREIGN, SIGNALS_ECHO -> Family.CLOTH;
            case AMBERWING, LANTERNLIGHT, TIDEBREAKER, RIFTWALKER -> Family.MAIL;
            case BELLGUARD, BLADEWARD, GATEWROUGHT, SUNWARD -> Family.PLATE;
            case HORNLORD, VAULTBREAKER, LORDSCALE, LEVIATHAN, STARFALL -> Family.HEAVY;
            case FROSTWALKER, SHARDCROWN, HALO_CHOIR, DREAMSTEEL -> Family.CRYSTAL;
            case BEASTBOUND, DEEPCARAPACE, HOLLOWHEART -> Family.BONE;
            case CLOUDRUNNER, GALEWING -> Family.FEATHER;
        };
    }

    /** A piece going on. */
    public static Holder<SoundEvent> equip(ArmourSet set) {
        return ArmourContent.EQUIP.get(of(set).ordinal());
    }

    /** One stride in the set. */
    public static SoundEvent move(Family family) {
        return ArmourContent.MOVE.get(family.ordinal()).get();
    }

    /** A blow landing on it: soft things thud, metal knocks, crystal rings, bone cracks. */
    public static SoundEvent struck(Family family) {
        return (switch (family) {
            case HIDE, CLOTH, FEATHER -> ArmourContent.STRUCK_SOFT;
            case MAIL, PLATE, HEAVY -> ArmourContent.STRUCK_PLATE;
            case CRYSTAL -> ArmourContent.STRUCK_CRYSTAL;
            case BONE -> ArmourContent.STRUCK_BONE;
        }).get();
    }
}
