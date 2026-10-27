package dev.awaken.arms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What a fighter has learned and been given on the way to their weapons (docs/arms-progression/PLAN.md §4): the styles
 * whose patterns they know, whether Hale's issue is taken, the no-drought count of kills without an essence shard, the
 * pity counters for weapons, and which homes have already taught them. Saved (schemaVersion 1), kept through death,
 * per player. A second attachment beside {@code awaken:arms_memory} (a record) so each can change on its own.
 */
public final class ArmsProgress {
    public static final int SCHEMA = 1;

    final Set<String> styles;
    boolean issued;
    /** Whether the one-time backfill from Blueprints held and styles used has run. */
    boolean migrated;
    /** Kills of awakened creatures in a row that left no essence shard (the no-drought rule). */
    int shardMisses;
    /** Lord kills since the last weapon of any kind, and kills of elites, Wardens and camp hearts since the last Marked weapon. */
    int lordsWithoutWeapon, markedWithout;
    boolean firstMarked;
    /** Gates that have already taught this fighter (the last 64 ids), people who have, and Pattern Stands taken (a position each). */
    final List<Integer> claimedGates;
    final Set<String> claimedPeople;
    final Set<Long> claimedStands;

    ArmsProgress(Set<String> styles, boolean issued, boolean migrated, int shardMisses, int lordsWithoutWeapon, int markedWithout,
                 boolean firstMarked, List<Integer> claimedGates, Set<String> claimedPeople, Set<Long> claimedStands) {
        this.styles = new HashSet<>(styles);
        this.issued = issued;
        this.migrated = migrated;
        this.shardMisses = shardMisses;
        this.lordsWithoutWeapon = lordsWithoutWeapon;
        this.markedWithout = markedWithout;
        this.firstMarked = firstMarked;
        this.claimedGates = new ArrayList<>(claimedGates);
        this.claimedPeople = new HashSet<>(claimedPeople);
        this.claimedStands = new HashSet<>(claimedStands);
    }

    static ArmsProgress fresh() {
        return new ArmsProgress(Set.of(), false, false, 0, 0, 0, false, List.of(), Set.of(), Set.of());
    }

    static final MapCodec<ArmsProgress> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(p -> SCHEMA),
            Codec.STRING.listOf().optionalFieldOf("styles", List.of()).forGetter(p -> List.copyOf(p.styles)),
            Codec.BOOL.optionalFieldOf("issued", false).forGetter(p -> p.issued),
            Codec.BOOL.optionalFieldOf("migrated", false).forGetter(p -> p.migrated),
            Codec.INT.optionalFieldOf("shardMisses", 0).forGetter(p -> p.shardMisses),
            Codec.INT.optionalFieldOf("lordsWithoutWeapon", 0).forGetter(p -> p.lordsWithoutWeapon),
            Codec.INT.optionalFieldOf("markedWithout", 0).forGetter(p -> p.markedWithout),
            Codec.BOOL.optionalFieldOf("firstMarked", false).forGetter(p -> p.firstMarked),
            Codec.INT.listOf().optionalFieldOf("claimedGates", List.of()).forGetter(p -> List.copyOf(p.claimedGates)),
            Codec.STRING.listOf().optionalFieldOf("claimedPeople", List.of()).forGetter(p -> List.copyOf(p.claimedPeople)),
            Codec.LONG.listOf().optionalFieldOf("claimedStands", List.of()).forGetter(p -> List.copyOf(p.claimedStands))
    ).apply(i, (v, st, is, mg, sm, lw, mw, fm, cg, cp, cs) -> new ArmsProgress(Set.copyOf(st), is, mg, sm, lw, mw, fm, cg, Set.copyOf(cp), Set.copyOf(cs))));

    public static ArmsProgress of(ServerPlayer p) {
        return p.getData(ArmsContent.PROGRESS);
    }

    /** Whether this fighter knows the style's pattern (the Longsword is everyone's). */
    public boolean knows(String style) {
        return style.equals(Arms.LONGSWORD) || styles.contains(style);
    }
}
