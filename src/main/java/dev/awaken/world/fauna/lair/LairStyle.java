package dev.awaken.world.fauna.lair;

import com.mojang.serialization.Codec;
import dev.awaken.terrain.api.BiomeTable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * One named beast's lair: whose it is, what the place is called, where the beast stands (relative to the centre and
 * the floor layer), how big the flattened floor is, whether it may sit in shallow water, and how steep the ground may
 * be. Structure JSON: {@code data/awaken/worldgen/structure/lair/<beast>.json} ({@code "lair": "<style>"}).
 */
public enum LairStyle implements StringRepresentable {
    WALLOW("briar_sow", "the Bramble Wallow", 0, 1, 0, 7, false, 6),
    GLASS_HOLLOW("dune_wyrm", "the Glass Hollow", 0, 1, 5, 8, false, 6),
    ANTLER_GROVE("old_antler", "the Antler Grove", 0, 1, 1, 7, false, 6),
    TOAD_THRONE("mire_mother", "the Toad Throne", 0, 2, 0, 8, true, 4),
    SERPENT_SHRINE("plumed_coil", "the Serpent Shrine", 0, 1, 0, 7, false, 7),
    SILK_HOLLOW("weaver_queen", "the Silk Hollow", 2, 1, 2, 7, false, 6),
    PETAL_BOWER("empress_moth", "the Petal Bower", 0, 1, 0, 7, false, 6),
    RAMS_CAIRN("stonehorn", "the Ram's Cairn", 0, 1, 3, 8, false, 16),
    SPORE_RING("mycelord", "the Spore Ring", 0, 1, 0, 8, false, 6),
    // P3: on the floor of the deep sea, and in a cave of its own deep under the dripstone and the lush caves
    SUNKEN_SHRINE("lantern_maw", "the Sunken Shrine", 0, 2, 0, 8, false, 6),
    CRYSTAL_NEST("thousandfoot", "the Crystal Nest", 0, 1, 1, 8, false, 6);

    /** Where a lair goes: on the land (the first nine), on the sea floor under deep water, or carved out underground. */
    public enum Placement { SURFACE, SEABED, CAVE }

    public static final Codec<LairStyle> CODEC = StringRepresentable.fromEnum(LairStyle::values);
    /** Half-width of a lair's footprint, the scattered ring outside the floor included. */
    public static final int EXTENT = 11;
    /** Blocks above the floor a lair may use, and below it for foundations. */
    public static final int HEIGHT = 12, DEPTH = 5;

    public final String beast;
    /** "the Bramble Wallow": the place's name, as the toasts and the tracker say it. */
    public final String title;
    public final int denX, denY, denZ, radius;
    /** May sit in shallow water (the floor is then raised to the water line). */
    public final boolean wet;
    /** Largest height difference allowed across the floor before the site is rejected. */
    public final int maxSlope;
    /** Lives high up (the peaks): its biome is looked up at the surface rather than near sea level. */
    public final boolean highland;
    /** P3: where it is placed (the sea floor and caves have their own site search and no terrain flattening in caves). */
    public final Placement placement;
    /** The biomes where it wears its second look: {@code #awaken:lair_style/<style>} (the biome table). */
    private final TagKey<Biome> secondLook;

    LairStyle(String beast, String title, int denX, int denY, int denZ, int radius, boolean wet, int maxSlope) {
        this.beast = beast;
        this.title = title;
        this.denX = denX;
        this.denY = denY;
        this.denZ = denZ;
        this.radius = radius;
        this.wet = wet;
        this.maxSlope = maxSlope;
        this.highland = beast.equals("stonehorn");
        this.placement = beast.equals("lantern_maw") ? Placement.SEABED : beast.equals("thousandfoot") ? Placement.CAVE : Placement.SURFACE;
        this.secondLook = BiomeTable.tag("lair_style/" + name().toLowerCase(Locale.ROOT));
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public BlockPos den(BlockPos centre) {
        return centre.offset(denX, denY, denZ);
    }

    /**
     * The look that fits the biome it was placed in (1 where the biome table's {@code #awaken:lair_style/<style>} lists
     * it): red sand in the badlands, snow on the cold ground, pale oak, coral in the warm deep, a mossy nest under the
     * lush caves. Styles without a second look have no tag.
     */
    public int variant(Holder<Biome> biome) {
        return biome.is(secondLook) ? 1 : 0;
    }

    public static @Nullable LairStyle byBeast(String beast) {
        for (LairStyle s : values()) if (s.beast.equals(beast)) return s;
        return null;
    }
}
