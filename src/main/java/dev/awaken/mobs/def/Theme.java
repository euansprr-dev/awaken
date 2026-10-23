package dev.awaken.mobs.def;

import dev.awaken.progression.Rank;

/**
 * Where a creature belongs: the 15 gate themes (ids exactly as in docs/plan.html's gate generator), the ten Descent
 * strata, the Veil, the Lucidium, and the nightmare templates. The ramp names match tools/art/pixelart.py so textures
 * generated for a theme share its colours.
 */
public enum Theme {
    BELL("awaken:bell_crypt", "Bell Crypt", Rank.E, 0xB4BEDD, 0x9AA8FF, BoltKind.SPIRIT),
    FUNGAL("awaken:fungal_hollow", "Fungal Hollow", Rank.E, 0xD2A8DA, 0xE2DF6A, BoltKind.SPORE),
    EMBER("awaken:ember_mines", "Ember Mines", Rank.E, 0xFFB884, 0xFF8A2A, BoltKind.FIRE),
    DROWNED("awaken:drowned_archive", "Drowned Archive", Rank.D, 0x9CCFDC, 0x3DD8C8, BoltKind.WATER),
    CLOCKWORK("awaken:clockwork_foundry", "Clockwork Foundry", Rank.D, 0xEACB8A, 0xFFC94A, BoltKind.CHARGE),
    GLASS("awaken:glass_desert", "Glass Desert", Rank.D, 0xF3E2B8, 0xFFF4D0, BoltKind.GLASS),
    HIVE("awaken:hive", "Hive", Rank.E, 0xFFD87A, 0xFFB020, BoltKind.HONEY),
    TEMPLE("awaken:overgrown_temple", "Overgrown Temple", Rank.E, 0xA8D894, 0x7CFF8A, BoltKind.THORN),
    LANTERN("awaken:lantern_market", "Lantern Night Market", Rank.D, 0xFFDDAA, 0xFFB347, BoltKind.FIRE),
    FROST("awaken:frost_monastery", "Frost Monastery", Rank.C, 0xC8E8FF, 0xBFF4FF, BoltKind.FROST),
    MIRROR("awaken:mirror_palace", "Mirror Palace", Rank.C, 0xEEF2F8, 0xFFFFFF, BoltKind.GLASS),
    PRECURSOR("awaken:precursor_lab", "Precursor Lab", Rank.C, 0x9CCBFF, 0x7FC3FF, BoltKind.CHARGE),
    NIGHTMARE("awaken:nightmare", "Nightmare", Rank.C, 0xD89ACB, 0xFF7AD8, BoltKind.DREAM),
    SKY("awaken:sky_archipelago", "Sky Archipelago", Rank.B, 0xF2F6FF, 0xD6ECFF, BoltKind.GRAVITY),
    ABYSS("awaken:abyssal_shrine", "Abyssal Shrine", Rank.A, 0x7D6CB4, 0xA27AFF, BoltKind.VOID),

    RIM("awaken:stratum_1", "The Rim", Rank.E, 0xA6D08C, 0x9CFF7A, BoltKind.THORN),
    GARDENS("awaken:stratum_2", "The Hanging Gardens", Rank.D, 0x9CD69A, 0xD8FF7A, BoltKind.THORN),
    CHOIR("awaken:stratum_3", "The Drowned Choir", Rank.D, 0x8EC4D6, 0x6AF0FF, BoltKind.WATER),
    ORCHARD("awaken:stratum_4", "The Bone Orchard", Rank.C, 0xE8DEC4, 0xFF5A5A, BoltKind.SPIRIT),
    SILENT("awaken:stratum_5", "The Silent Halls", Rank.C, 0xC4C8D2, 0xE6F0FF, BoltKind.VOID),
    FOUNDRY("awaken:stratum_6", "The Precursor Foundry", Rank.B, 0x9CB8D6, 0xFFB347, BoltKind.CHARGE),
    INVERTED("awaken:stratum_7", "The Inverted Sea", Rank.B, 0x8CB4E6, 0x7FC3FF, BoltKind.WATER),
    DREAMING("awaken:stratum_8", "The Dreaming Stratum", Rank.A, 0xDCA8D8, 0xF6C6E6, BoltKind.DREAM),
    COURT("awaken:stratum_9", "The Hollow Court", Rank.S, 0x6E6688, 0xD4AF37, BoltKind.VOID),
    SEED("awaken:stratum_10", "The Seed", Rank.SOVEREIGN, 0xE6F0FF, 0xFFF2B0, BoltKind.SIGNAL),

    VEIL("awaken:veil", "The Veil", Rank.C, 0x9AA4F0, 0xC3CBFF, BoltKind.SPIRIT),
    DREAM("awaken:dream", "The Lucidium", Rank.E, 0xE8B8E0, 0xF6C6E6, BoltKind.DREAM),
    FEAR("awaken:nightmare/generic", "Nightmares", Rank.D, 0x8A7090, 0xFF4A8A, BoltKind.DREAM);

    public final String id;
    public final String display;
    /** Rank whose content level a spawn egg (or a caller without a level) uses. */
    public final Rank home;
    /** Multiplied over vanilla textures. */
    public final int tint;
    /** Emissive accent: telegraphs, glow layers, particles. */
    public final int glow;
    public final BoltKind bolt;

    Theme(String id, String display, Rank home, int tint, int glow, BoltKind bolt) {
        this.id = id;
        this.display = display;
        this.home = home;
        this.tint = tint;
        this.glow = glow;
        this.bolt = bolt;
    }

    public boolean isGate() {
        return ordinal() <= ABYSS.ordinal();
    }

    public boolean isStratum() {
        return ordinal() >= RIM.ordinal() && ordinal() <= SEED.ordinal();
    }

    public static Theme byId(String id) {
        for (Theme t : values()) if (t.id.equals(id)) return t;
        return null;
    }
}
