package dev.awaken.above.ruin;

import com.mojang.serialization.Codec;
import dev.awaken.Awaken;
import dev.awaken.above.logs.PrecursorLogs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/** The four kinds of Precursor ruin in the overworld, plus the guaranteed vault near spawn. */
public enum RuinKind implements StringRepresentable {
    VAULT("vault", "Sealed Vault", "precursor_vault", "precursor_vaults", PrecursorLogs.Pool.UNKNOWN,
            "Something sealed is waiting."),
    SKY("sky_platform", "Sky Platform", "precursor_sky_platform", "precursor_sky_platforms", PrecursorLogs.Pool.ASCENDANT,
            "Something hangs above the clouds."),
    OCEAN("ocean_lab", "Ocean Lab", "precursor_ocean_lab", "precursor_ocean_labs", PrecursorLogs.Pool.SLEEPER,
            "Something hums under the water."),
    MONOLITH("monolith", "Monolith", "precursor_monolith", "precursor_monoliths", PrecursorLogs.Pool.KEEPER,
            "A tall shadow stands over the sand."),
    SPAWN_VAULT("spawn_vault", "Sealed Vault", "precursor_spawn_vault", "precursor_spawn_vault", PrecursorLogs.Pool.UNKNOWN,
            "Something sealed is close. It's been waiting for you.");

    public static final Codec<RuinKind> CODEC = StringRepresentable.fromEnum(RuinKind::values);

    public final String id;
    public final String displayName;
    public final ResourceKey<Structure> structure;
    public final ResourceKey<StructureSet> set;
    /** Which Precursor logs its terminal holds. */
    public final PrecursorLogs.Pool logs;
    /** The omen the Scanner gives when pointed towards one. */
    public final String omen;

    RuinKind(String id, String displayName, String structure, String set, PrecursorLogs.Pool logs, String omen) {
        this.id = id;
        this.displayName = displayName;
        this.structure = ResourceKey.create(Registries.STRUCTURE, Awaken.id(structure));
        this.set = ResourceKey.create(Registries.STRUCTURE_SET, Awaken.id(set));
        this.logs = logs;
        this.omen = omen;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public boolean isVault() {
        return this == VAULT || this == SPAWN_VAULT;
    }

    public static RuinKind byId(String id) {
        for (RuinKind k : values()) if (k.id.equals(id)) return k;
        return null;
    }
}
