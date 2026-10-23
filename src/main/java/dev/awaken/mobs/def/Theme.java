package dev.awaken.mobs.def;

import dev.awaken.progression.Rank;

/**
 * Where a creature belongs: the 15 gate themes (ids exactly as in docs/plan.html's gate generator), the ten Descent
 * strata, the Veil, the Lucidium, the nightmare templates and the overworld's biome families (three from P1, six from P2). The ramp names
 * match tools/art/pixelart.py so textures generated for a theme share its colours.
 */
public enum Theme {
    BELL("awaken:bell_crypt", "Bell Crypt", Rank.E, 0x9CA8D8, 0x9AA8FF, BoltKind.SPIRIT),
    FUNGAL("awaken:fungal_hollow", "Fungal Hollow", Rank.E, 0xC696D2, 0xE2DF6A, BoltKind.SPORE),
    EMBER("awaken:ember_mines", "Ember Mines", Rank.E, 0xFF9A62, 0xFF8A2A, BoltKind.FIRE),
    DROWNED("awaken:drowned_archive", "Drowned Archive", Rank.D, 0x80C2D4, 0x3DD8C8, BoltKind.WATER),
    CLOCKWORK("awaken:clockwork_foundry", "Clockwork Foundry", Rank.D, 0xE6B866, 0xFFC94A, BoltKind.CHARGE),
    GLASS("awaken:glass_desert", "Glass Desert", Rank.D, 0xF0D8A0, 0xFFF4D0, BoltKind.GLASS),
    HIVE("awaken:hive", "Hive", Rank.E, 0xFFC850, 0xFFB020, BoltKind.HONEY),
    TEMPLE("awaken:overgrown_temple", "Overgrown Temple", Rank.E, 0x92CE7A, 0x7CFF8A, BoltKind.THORN),
    LANTERN("awaken:lantern_market", "Lantern Night Market", Rank.D, 0xFFC890, 0xFFB347, BoltKind.FIRE),
    FROST("awaken:frost_monastery", "Frost Monastery", Rank.C, 0xB2DCFF, 0xBFF4FF, BoltKind.FROST),
    MIRROR("awaken:mirror_palace", "Mirror Palace", Rank.C, 0xE2E8F6, 0xFFFFFF, BoltKind.GLASS),
    PRECURSOR("awaken:precursor_lab", "Precursor Lab", Rank.C, 0x88B8FF, 0x7FC3FF, BoltKind.CHARGE),
    NIGHTMARE("awaken:nightmare", "Nightmare", Rank.C, 0xCC88C4, 0xFF7AD8, BoltKind.DREAM),
    SKY("awaken:sky_archipelago", "Sky Archipelago", Rank.B, 0xE6EEFF, 0xD6ECFF, BoltKind.GRAVITY),
    ABYSS("awaken:abyssal_shrine", "Abyssal Shrine", Rank.A, 0x6E5CAA, 0xA27AFF, BoltKind.VOID),

    RIM("awaken:stratum_1", "The Rim", Rank.E, 0x96C878, 0x9CFF7A, BoltKind.THORN),
    GARDENS("awaken:stratum_2", "The Hanging Gardens", Rank.D, 0x8ED088, 0xD8FF7A, BoltKind.THORN),
    CHOIR("awaken:stratum_3", "The Drowned Choir", Rank.D, 0x7CB8D0, 0x6AF0FF, BoltKind.WATER),
    ORCHARD("awaken:stratum_4", "The Bone Orchard", Rank.C, 0xEAD8B8, 0xFF5A5A, BoltKind.SPIRIT),
    SILENT("awaken:stratum_5", "The Silent Halls", Rank.C, 0xBCC2CE, 0xE6F0FF, BoltKind.VOID),
    FOUNDRY("awaken:stratum_6", "The Precursor Foundry", Rank.B, 0x8CAAD0, 0xFFB347, BoltKind.CHARGE),
    INVERTED("awaken:stratum_7", "The Inverted Sea", Rank.B, 0x7CA8E2, 0x7FC3FF, BoltKind.WATER),
    DREAMING("awaken:stratum_8", "The Dreaming Stratum", Rank.A, 0xD49AD0, 0xF6C6E6, BoltKind.DREAM),
    COURT("awaken:stratum_9", "The Hollow Court", Rank.S, 0x6A6086, 0xD4AF37, BoltKind.VOID),
    SEED("awaken:stratum_10", "The Seed", Rank.SOVEREIGN, 0xE8F0FF, 0xFFF2B0, BoltKind.SIGNAL),

    VEIL("awaken:veil", "The Veil", Rank.C, 0x8E9AEE, 0xC3CBFF, BoltKind.SPIRIT),
    DREAM("awaken:dream", "The Lucidium", Rank.E, 0xE2A8DA, 0xF6C6E6, BoltKind.DREAM),
    FEAR("awaken:nightmare/generic", "Nightmares", Rank.D, 0x846A8C, 0xFF4A8A, BoltKind.DREAM),

    // The Awakened layer: overworld creatures by biome family (spawned by the world module, docs/modules/fauna.md).
    GREENWILD("awaken:greenwild", "Greenwild", Rank.E, 0xC8DCB0, 0xFFD27A, BoltKind.THORN),
    SUNSCORCH("awaken:sunscorch", "Sunscorch", Rank.E, 0xF2DCB0, 0xFFE9A0, BoltKind.GLASS),
    FROSTWILD("awaken:frostwild", "Frostwild", Rank.E, 0xDCE8F4, 0x9FE8FF, BoltKind.FROST),
    // P2: the rest of the land (docs/modules/fauna.md, "P2")
    MIRE("awaken:mire", "The Mire", Rank.E, 0xB8C8A0, 0xB8FF6A, BoltKind.SPORE),
    CANOPY("awaken:canopy", "The Canopy", Rank.E, 0xA8D8A0, 0x5CF0FF, BoltKind.THORN),
    DUSKWOOD("awaken:duskwood", "Duskwood", Rank.E, 0xB4ACC4, 0xD8C8FF, BoltKind.SPIRIT),
    BLOSSOM("awaken:blossom", "Blossomwood", Rank.E, 0xF4D8E8, 0xFFB8E0, BoltKind.HONEY),
    CRAG("awaken:crag", "The Crags", Rank.E, 0xD0D0D4, 0xA8D8FF, BoltKind.PLAIN),
    SPORE("awaken:spore", "Sporewild", Rank.E, 0xD8C0D8, 0xFF9AE0, BoltKind.SPORE);

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

    /** An overworld biome family (the Awakened layer), not a gate or a stratum. */
    public boolean isWild() {
        return ordinal() >= GREENWILD.ordinal();
    }

    public static Theme byId(String id) {
        for (Theme t : values()) if (t.id.equals(id)) return t;
        return null;
    }
}
