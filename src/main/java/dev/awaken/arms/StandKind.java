package dev.awaken.arms;

import net.minecraft.util.StringRepresentable;

/** The make of a Pattern Stand, so a structure's stand looks as if it belongs there; {@code lift} is how high the weapon hangs above the block. */
public enum StandKind implements StringRepresentable {
    RACK("rack", 1.12F), LECTERN("lectern", 1.1F), PLINTH("plinth", 1.2F), BIER("bier", 0.82F);

    public final String id;
    public final float lift;

    StandKind(String id, float lift) {
        this.id = id;
        this.lift = lift;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public static StandKind of(String id) {
        for (StandKind k : values()) if (k.id.equals(id)) return k;
        return RACK;
    }
}
