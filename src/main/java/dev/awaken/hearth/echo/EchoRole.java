package dev.awaken.hearth.echo;

import com.mojang.serialization.Codec;

/** Jobs an Echo does at home while standing on a role pad. */
public enum EchoRole {
    GUARD("guard", "Guard", "patrols the Hearth and holds breaks", 0xFF8A7A),
    HAUL("haul", "Haul", "picks up loose items and sorts them into chests", 0xCFA843),
    FARM("farm", "Farm", "harvests and replants ripe crops", 0x86AD5F),
    MINE("mine", "Mine", "brings common ore up from below", 0xA19FA8),
    TRAIN("train", "Train", "spars with itself and slowly grows stronger", 0x7FC3FF);

    public static final Codec<EchoRole> CODEC = Codec.STRING.xmap(EchoRole::byId, r -> r.id);

    public final String id;
    public final String label;
    public final String does;
    public final int colour;

    EchoRole(String id, String label, String does, int colour) {
        this.id = id;
        this.label = label;
        this.does = does;
        this.colour = colour;
    }

    public static EchoRole byId(String id) {
        for (EchoRole r : values()) if (r.id.equals(id)) return r;
        return GUARD;
    }
}
