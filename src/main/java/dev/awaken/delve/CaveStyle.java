package dev.awaken.delve;

import dev.awaken.Awaken;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.Tags;

/**
 * The style a cave takes from the land over it (SPEC §34.7): decoration only, no cave biomes. Roots and glow under the
 * woods; the crystal lands' own crystal growing through the rock under them (Glass Steppe's sunshard, the Frost Spires'
 * frostspar, the Shattered Highlands' fault seam: mined, they're the Forge's catalysts); sandstone under deserts and
 * badlands; ice under snow. Each has its air's colour and its motes' (amber dust, blue cold). Pure: the biome over the
 * column decides, so the feature (by the chunk's middle) and the client (by the camera's column) agree.
 */
public enum CaveStyle {
    NONE(0, 0),
    ROOTS(0x4E6B3A, 0xB8D08A),
    SUNSHARD(0xB07A2E, 0xFFD38A),
    FROSTSPAR(0x4A7FA8, 0xB4E4FF),
    FAULT_SEAM(0x6A4C8C, 0xD2B4FF),
    SAND(0xA8783A, 0xE8B060),
    ICE(0x3E78A8, 0xA8DCFF);

    /** The air's colour under this land (Depths' fog), and its motes'. */
    public final int air, dust;

    CaveStyle(int air, int dust) {
        this.air = air;
        this.dust = dust;
    }

    public static CaveStyle of(Holder<Biome> b) {
        if (b.is(Tags.Biomes.IS_UNDERGROUND) || b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_RIVER)) return NONE;
        var key = b.unwrapKey();
        if (key.isPresent() && key.get().identifier().getNamespace().equals(Awaken.MODID)) {
            switch (key.get().identifier().getPath()) {
                case "glass_steppe" -> { return SUNSHARD; }
                case "frost_spires" -> { return FROSTSPAR; }
                case "shattered_highlands" -> { return FAULT_SEAM; }
                case "elderwood", "lumen_forest" -> { return ROOTS; }
                default -> { }
            }
        }
        if (b.is(Tags.Biomes.IS_SNOWY) || b.is(Tags.Biomes.IS_ICY)) return ICE;
        if (b.is(Tags.Biomes.IS_DESERT) || b.is(BiomeTags.IS_BADLANDS)) return SAND;
        if (b.is(BiomeTags.IS_FOREST) || b.is(BiomeTags.IS_TAIGA) || b.is(BiomeTags.IS_JUNGLE)) return ROOTS;
        return NONE;
    }

    /** The crystal lands' crystal block (the Lands' own, by id: air when that pack is off). */
    public Block crystal() {
        String id = switch (this) {
            case SUNSHARD -> "sunshard";
            case FROSTSPAR -> "frostspar";
            case FAULT_SEAM -> "fault_seam";
            default -> null;
        };
        return id == null ? Blocks.AIR : BuiltInRegistries.BLOCK.getValue(Awaken.id(id));
    }

    /** The crystal's little cluster to crown a growth (air for the fault seam, which has none). */
    public Block cluster() {
        String id = switch (this) {
            case SUNSHARD -> "sunshard_cluster";
            case FROSTSPAR -> "frostspar_cluster";
            default -> null;
        };
        return id == null ? Blocks.AIR : BuiltInRegistries.BLOCK.getValue(Awaken.id(id));
    }
}
