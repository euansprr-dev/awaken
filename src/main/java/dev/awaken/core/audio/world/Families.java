package dev.awaken.core.audio.world;

import dev.awaken.Awaken;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/**
 * The world sound families (docs/sound/PLAN.md §6.2). Every overworld biome is in exactly one family tag
 * {@code #awaken:soundscape/<family>}, written by tools/terrain/biomes.py from the biome table; a family plays the
 * soundscape {@code awaken:family/<family>}. Keep the order and names in step with biomes.py's FAMILIES.
 */
public final class Families {
    private Families() {}

    public static final String[] NAMES = {"greenwood", "blossom", "duskwood", "conifer", "open_grass", "dry_warmth",
            "desert", "canopy", "mire", "heights", "snowfield", "coast", "open_sea", "river", "sporewild", "hollows"};
    public static final int COUNT = NAMES.length;
    /** The soundscape every place shares under its family: wind and leaves. */
    public static final Identifier WORLD = Awaken.id("world");

    private static final TagKey<Biome>[] TAGS = tags();
    private static final Identifier[] SOUNDSCAPES = soundscapes();

    @SuppressWarnings("unchecked")
    private static TagKey<Biome>[] tags() {
        TagKey<Biome>[] t = new TagKey[COUNT];
        for (int i = 0; i < COUNT; i++) t[i] = TagKey.create(Registries.BIOME, Awaken.id("soundscape/" + NAMES[i]));
        return t;
    }

    private static Identifier[] soundscapes() {
        Identifier[] s = new Identifier[COUNT];
        for (int i = 0; i < COUNT; i++) s[i] = Awaken.id("family/" + NAMES[i]);
        return s;
    }

    public static TagKey<Biome> tag(int family) {
        return TAGS[family];
    }

    /** The soundscape a family plays. */
    public static Identifier soundscape(int family) {
        return SOUNDSCAPES[family];
    }

    /** The family a biome is in, or -1 (the Nether, the End, Awaken's own dimensions, another mod's biome). */
    public static int of(Holder<Biome> biome) {
        for (int i = 0; i < COUNT; i++) if (biome.is(TAGS[i])) return i;
        return -1;
    }
}
