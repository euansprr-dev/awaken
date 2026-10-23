package dev.awaken.mobs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-player boss memory (attachment {@code awaken:mobs_boss_record}, kept through death): how many bosses in a row
 * dropped no Power Core (the SPEC pity timer guarantees one after three) and which boss types this player has felled
 * (a first kill is a trophy moment).
 */
public record BossRecord(int schemaVersion, int sinceCore, List<String> killed) {
    public static final int SCHEMA = 1;
    public static final BossRecord EMPTY = new BossRecord(SCHEMA, 0, List.of());
    /** A Power Core is guaranteed when this many bosses in a row gave none. */
    public static final int PITY = 3;

    public static final MapCodec<BossRecord> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(BossRecord::schemaVersion),
            Codec.INT.optionalFieldOf("since_core", 0).forGetter(BossRecord::sinceCore),
            Codec.STRING.listOf().optionalFieldOf("killed", List.of()).forGetter(BossRecord::killed)
    ).apply(i, BossRecord::new));

    public boolean pityDue() {
        return sinceCore >= PITY;
    }

    public boolean hasKilled(String bossId) {
        return killed.contains(bossId);
    }

    public BossRecord afterBoss(boolean gotCore) {
        return new BossRecord(SCHEMA, gotCore ? 0 : sinceCore + 1, killed);
    }

    public BossRecord withKill(String bossId) {
        if (killed.contains(bossId)) return this;
        List<String> k = new ArrayList<>(killed);
        k.add(bossId);
        return new BossRecord(SCHEMA, sinceCore, List.copyOf(k));
    }
}
