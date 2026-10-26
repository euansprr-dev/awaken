package dev.awaken.delve.cave;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * The kinds of cave place (PLAN 8.2). Each is a structure ({@code awaken:delve/<kind>}) placed where it fits under the
 * ground and joined to the natural caves; each passes M44's five tests (a rule, a resource, a sensory set, a story
 * entrance, a route) and has a route that never needs the rope. {@code big} places get a caption that interrupts
 * (IMPORTANT); the small ones are filed quietly. The ordinal is sent to clients: new kinds go on the end.
 */
public enum CaveKind implements StringRepresentable {
    /** A round shaft from the surface into a cave system, down to a lit grotto with a lode. */
    SINKHOLE("sinkhole", "a way down", true),
    /** The Delvers' old workings: dressed galleries, timber shafts, a rail incline, a chasm and its bridges. */
    MINE("mine", "old mine", true),
    /** Below y 0, where the Deep presses into the rock: Deepglass, a Hollows nest, the seep heart. */
    SEEP("seep", "a seep", true),
    /** A Delver ore chute: 48 blocks of cut steps to slide, out over an underground lake. */
    CHUTE("chute", "old chute", false),
    /** Behind a waterfall: a glowing pool, a geode you climb into, a buried cache. */
    GROTTO("grotto", "a grotto", false);

    public static final Codec<CaveKind> CODEC = StringRepresentable.fromEnum(CaveKind::values);
    private static final CaveKind[] VALUES = values();

    public final String id;
    /** The caption's second half ("THE DELVERS' DEEP · old mine"). */
    public final String what;
    /** A place big enough that its caption takes the screen (IMPORTANT); the rest are filed (AMBIENT). */
    public final boolean big;

    CaveKind(String id, String what, boolean big) {
        this.id = id;
        this.what = what;
        this.big = big;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public static @Nullable CaveKind byId(String id) {
        for (CaveKind k : VALUES) if (k.id.equals(id.toLowerCase(Locale.ROOT))) return k;
        return null;
    }

    public static @Nullable CaveKind byOrdinal(int i) {
        return i >= 0 && i < VALUES.length ? VALUES[i] : null;
    }
}
