package dev.awaken.mobs.def;

import dev.awaken.Awaken;
import net.minecraft.resources.Identifier;

/**
 * What a gate monster's shot is (SPEC §60.7): the thing its body throws, drawn in flight in place of the plain glowing
 * core (the glow stays round it: its colour still says how the shot is answered). Each is a 16x8 sprite, its tip to
 * the right, in {@code textures/entity/monsters/shots/<id>.png} (painted by tools/art/specs/gm/shots.py). A dart
 * flies point first, drawn as two crossed planes along its flight; the rest face the eye, some turning as they go.
 */
public enum ShotLook {
    RIB("rib", 1.0F, true, 0.55F),
    SPORE("spore", 0.9F, false, 0.12F),
    CINDER("cinder", 0.8F, false, 0.3F),
    THORN("thorn", 0.8F, true, 0.0F),
    INK("ink", 0.9F, false, 0.0F),
    QUILL("quill", 1.1F, true, 0.0F),
    CARD("card", 0.8F, false, 0.5F),
    RIVET("rivet", 0.6F, true, 0.35F),
    SHARD("shard", 0.9F, true, 0.2F),
    OIL("oil", 0.8F, false, 0.0F),
    HAIL("hail", 0.7F, false, 0.25F),
    POINT("point", 1.0F, true, 0.0F),
    ARC("arc", 1.2F, true, 0.0F),
    GUST("gust", 1.1F, false, 0.4F),
    QUARREL("quarrel", 1.0F, true, 0.0F),
    SPAR("spar", 1.4F, true, 0.3F),
    RING("ring", 0.9F, false, 0.15F),
    HOOD("hood", 0.9F, false, 0.0F);

    public final String id;
    /** Its length in blocks (it is half as wide). */
    public final float length;
    /** A dart: drawn along its flight. Else it faces the eye. */
    public final boolean along;
    /** Radians a tick it turns (about its flight if a dart, else in the eye's plane). */
    public final float spin;

    ShotLook(String id, float length, boolean along, float spin) {
        this.id = id;
        this.length = length;
        this.along = along;
        this.spin = spin;
    }

    public Identifier texture() {
        return Awaken.id("textures/entity/monsters/shots/" + id + ".png");
    }

    private static final ShotLook[] ALL = values();

    /** The look a shot's synced byte names (0: none). */
    public static ShotLook byIndex(int oneBased) {
        return oneBased > 0 && oneBased <= ALL.length ? ALL[oneBased - 1] : null;
    }
}
