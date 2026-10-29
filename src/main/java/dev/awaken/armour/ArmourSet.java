package dev.awaken.armour;

import dev.awaken.gear.HunterTier;
import dev.awaken.progression.Rank;
import org.jspecify.annotations.Nullable;

/**
 * The 29 armour sets (SPEC §44.2): four a rank at E, five at each rank from D to S, one of each archetype and from D up
 * one Lord-forged. Six are today's Hunter sets ({@link #hunter}), kept by their item ids and drawn in their new look; the
 * id names the set's meshes and texture (assets/awaken/armour/&lt;id&gt;/, textures/entity/armour/&lt;id&gt;.png).
 */
public enum ArmourSet {
    WILDHIDE("wildhide", "Wildhide", Rank.E, Archetype.STRIDER, HunterTier.E),
    BELLGUARD("bellguard", "Bellguard", Rank.E, Archetype.BULWARK, null),
    ROOTWARDEN("rootwarden", "Rootwarden", Rank.E, Archetype.CHANNELER, null),
    AMBERWING("amberwing", "Amberwing", Rank.E, Archetype.DUELIST, null),

    BEASTBOUND("beastbound", "Beastbound", Rank.D, Archetype.BULWARK, HunterTier.D),
    CLEFTRUNNER("cleftrunner", "Cleftrunner", Rank.D, Archetype.STRIDER, null),
    LANTERNLIGHT("lanternlight", "Lanternlight", Rank.D, Archetype.CHANNELER, null),
    BLADEWARD("bladeward", "Bladeward", Rank.D, Archetype.DUELIST, null),
    HORNLORD("hornlord", "Hornlord", Rank.D, Archetype.LORD, null),

    GATEWROUGHT("gatewrought", "Gatewrought", Rank.C, Archetype.DUELIST, HunterTier.C),
    VAULTBREAKER("vaultbreaker", "Vaultbreaker", Rank.C, Archetype.BULWARK, null),
    FROSTWALKER("frostwalker", "Frostwalker", Rank.C, Archetype.STRIDER, null),
    SUNSHARD("sunshard", "Sunshard", Rank.C, Archetype.CHANNELER, null),
    SHARDCROWN("shardcrown", "Shardcrown", Rank.C, Archetype.LORD, null),

    VEILWOVEN("veilwoven", "Veilwoven", Rank.B, Archetype.CHANNELER, HunterTier.B),
    DEEPCARAPACE("deepcarapace", "Deepcarapace", Rank.B, Archetype.BULWARK, null),
    CLOUDRUNNER("cloudrunner", "Cloudrunner", Rank.B, Archetype.STRIDER, null),
    TIDEBREAKER("tidebreaker", "Tidebreaker", Rank.B, Archetype.DUELIST, null),
    HOLLOWHEART("hollowheart", "Hollowheart", Rank.B, Archetype.LORD, null),

    LORDSCALE("lordscale", "Lordscale", Rank.A, Archetype.LORD, HunterTier.A),
    LEVIATHAN("leviathan", "Leviathan", Rank.A, Archetype.BULWARK, null),
    GALEWING("galewing", "Galewing", Rank.A, Archetype.STRIDER, null),
    HALO_CHOIR("halo_choir", "Halo Choir", Rank.A, Archetype.CHANNELER, null),
    DREAMSTEEL("dreamsteel", "Dreamsteel", Rank.A, Archetype.DUELIST, null),

    SOVEREIGN("sovereign", "Sovereign Weave", Rank.S, Archetype.LORD, HunterTier.S),
    STARFALL("starfall", "Starfall", Rank.S, Archetype.BULWARK, null),
    RIFTWALKER("riftwalker", "Riftwalker", Rank.S, Archetype.STRIDER, null),
    NIGHTCOURT("nightcourt", "Nightcourt", Rank.S, Archetype.DUELIST, null),
    SIGNALS_ECHO("signals_echo", "Signal's Echo", Rank.S, Archetype.CHANNELER, null);

    public final String id;
    public final String setName;
    public final Rank rank;
    public final Archetype archetype;
    /** The Hunter tier whose items are this set, or null for a set with items of its own. */
    public final @Nullable HunterTier hunter;

    ArmourSet(String id, String setName, Rank rank, Archetype archetype, @Nullable HunterTier hunter) {
        this.id = id;
        this.setName = setName;
        this.rank = rank;
        this.archetype = archetype;
        this.hunter = hunter;
    }

    private static final ArmourSet[] VALUES = values();

    /** The highest rank whose own-item sets are built (P2: E and D; plan §6.2 adds C and B, then A and S). */
    static final Rank MADE_TO = Rank.D;

    /** Whether the set's pieces exist as items: the Hunter sets always, the others from the phase that builds them. */
    public boolean made() {
        return hunter != null || rank.ordinal() <= MADE_TO.ordinal();
    }

    /** The Hunter tier of this set's rank: its armour numbers, hide, trophies, bindings and metal. */
    public HunterTier tier() {
        for (HunterTier t : HunterTier.values()) if (t.rank == rank) return t;
        throw new IllegalStateException("no Hunter tier at " + rank);
    }

    public static ArmourSet of(HunterTier tier) {
        for (ArmourSet s : VALUES) if (s.hunter == tier) return s;
        throw new IllegalArgumentException("no armour set for " + tier);
    }

    public static @Nullable ArmourSet byId(String id) {
        for (ArmourSet s : VALUES) if (s.id.equals(id)) return s;
        return null;
    }
}
