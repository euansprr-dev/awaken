package dev.awaken.gates.content;

import com.mojang.serialization.Codec;
import dev.awaken.Awaken;
import dev.awaken.core.Forces;
import dev.awaken.progression.Rank;
import net.minecraft.resources.Identifier;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The 15 gate themes, exactly as in the plan's gate generator (docs/plan.html, THEMES): name, omen, minimum rank,
 * Gate Lord and forces. Each theme has its own palette and its own biome {@code awaken:gate_<id>} (fog, particles,
 * sound) in data/awaken/worldgen/biome.
 */
public enum Theme {
    BELL_CRYPT("bell_crypt", "Bell Crypt", "Bells ring under your feet", 0, "bell_warden", "the Bell Warden", List.of("Spirit"), false),
    FUNGAL_HOLLOW("fungal_hollow", "Fungal Hollow", "The air tastes sweet and wet", 0, "mother_of_spores", "Mother of Spores", List.of("Spores"), false),
    EMBER_MINES("ember_mines", "Ember Mines", "Warm stone, cold wind", 0, "ember_tyrant", "the Ember Tyrant", List.of("Heat"), false),
    DROWNED_ARCHIVE("drowned_archive", "Drowned Archive", "Salt, and the smell of old paper", 1, "drowned_archivist", "the Drowned Archivist", List.of("Water"), false),
    CLOCKWORK_FOUNDRY("clockwork_foundry", "Clockwork Foundry", "Salt, and machine oil", 1, "gearwright_colossus", "the Gearwright Colossus", List.of("Charge"), false),
    GLASS_DESERT("glass_desert", "Glass Desert", "Your reflection blinks first", 1, "glass_serpent", "the Glass Serpent", List.of("Heat"), false),
    HIVE("hive", "Hive", "A hum you feel in your teeth", 0, "hive_regent", "the Hive Regent", List.of("Spores"), false),
    OVERGROWN_TEMPLE("overgrown_temple", "Overgrown Temple", "Incense and wet roots", 0, "root_saint", "the Root Saint", List.of("Spirit"), false),
    LANTERN_NIGHT_MARKET("lantern_night_market", "Lantern Night Market", "Music, and someone haggling", 1, "lantern_broker", "the Lantern Broker", List.of(), true),
    FROST_MONASTERY("frost_monastery", "Frost Monastery", "Chanting above a sea of clouds", 2, "abbot_of_frost", "the Abbot of Frost", List.of("Frost"), false),
    MIRROR_PALACE("mirror_palace", "Mirror Palace", "Footsteps that match yours", 2, "mirror_knight", "the Mirror Knight", List.of("Mirror"), false),
    PRECURSOR_LAB("precursor_lab", "Precursor Lab", "A voice counting down in no language", 2, "unit_zero", "Unit Zero", List.of("Charge"), false),
    NIGHTMARE("nightmare", "Nightmare", "You've been here before. You haven't.", 2, "the_sleeper", "the Sleeper", List.of("Spirit"), false),
    SKY_ARCHIPELAGO("sky_archipelago", "Sky Archipelago", "The ground is much too far below", 3, "sky_leviathan", "the Sky Leviathan", List.of("Gravity"), false),
    ABYSSAL_SHRINE("abyssal_shrine", "Abyssal Shrine", "Nothing. No sound at all.", 4, "hollow_herald", "the Hollow Herald", List.of("Spirit", "Gravity"), false);

    public static final Codec<Theme> CODEC = Codec.STRING.xmap(Theme::byId, t -> t.id);

    public final String id;
    public final String displayName;
    public final String omen;
    public final int minRank;
    public final String bossKey;
    public final String bossName;
    public final List<String> forces;
    public final boolean friendly;
    private volatile Palette palette;

    Theme(String id, String displayName, String omen, int minRank, String bossKey, String bossName, List<String> forces, boolean friendly) {
        this.id = id;
        this.displayName = displayName;
        this.omen = omen;
        this.minRank = minRank;
        this.bossKey = bossKey;
        this.bossName = bossName;
        this.forces = forces;
        this.friendly = friendly;
    }

    /** Theme id as the mobs module expects it: {@code awaken:bell_crypt}. */
    public String themeId() {
        return Awaken.MODID + ":" + id;
    }

    public String bossId() {
        return Awaken.MODID + ":" + bossKey;
    }

    public Identifier biome() {
        return Awaken.id("gate_" + id);
    }

    public boolean fits(Rank rank) {
        return rank.ordinal() >= minRank;
    }

    /** Selection weight in the Director (friendly gates are rarer). */
    public double weight() {
        return friendly ? 0.35 : 1.0;
    }

    /** Environmental tags the theme puts on its mobs. Heat and Gravity are forces, not tags. */
    public Set<Forces.Tag> mobTags() {
        Set<Forces.Tag> out = EnumSet.noneOf(Forces.Tag.class);
        for (String f : forces) {
            switch (f) {
                case "Spores" -> out.add(Forces.Tag.SPORES);
                case "Water" -> out.add(Forces.Tag.WATER);
                case "Mirror" -> out.add(Forces.Tag.MIRROR);
                case "Frost" -> out.add(Forces.Tag.FROSTED);
                case "Charge" -> out.add(Forces.Tag.CHARGED);
                case "Spirit" -> out.add(Forces.Tag.HAUNTED);
                default -> {}
            }
        }
        return out;
    }

    public static Theme byId(String id) {
        for (Theme t : values()) if (t.id.equals(id) || t.name().equalsIgnoreCase(id)) return t;
        return BELL_CRYPT;
    }

    public static Theme parse(String s) {
        for (Theme t : values()) if (t.id.equalsIgnoreCase(s) || t.name().equalsIgnoreCase(s)) return t;
        return null;
    }

    public Palette palette() {
        Palette p = palette;
        if (p == null) palette = p = Palettes.build(this);
        return p;
    }
}
