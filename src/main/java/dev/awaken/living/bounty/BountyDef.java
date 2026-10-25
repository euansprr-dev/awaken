package dev.awaken.living.bounty;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.living.quest.QuestDef;

import java.util.List;

/**
 * An Association bounty template (SPEC §21.0.9), from {@code data/<ns>/awaken/bounty/*.json}, schema 1: a notice for
 * players whose level is in {@code band} (0: 1-8, 1: 8-20, 2: 20-45, 3: 45-80, 4: 80+), with one objective (kill or
 * collect) and its pay: a share of a level and essence (coin once Carillon's economy exists).
 */
public record BountyDef(int schemaVersion, String title, int band, QuestDef.Objective objective, double levels, int essence,
                        List<String> requires, String text) {
    public static final Codec<BountyDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", 1).forGetter(BountyDef::schemaVersion),
            Codec.STRING.fieldOf("title").forGetter(BountyDef::title),
            Codec.INT.optionalFieldOf("band", 0).forGetter(BountyDef::band),
            QuestDef.Objective.CODEC.fieldOf("objective").forGetter(BountyDef::objective),
            Codec.DOUBLE.optionalFieldOf("levels", 0.2).forGetter(BountyDef::levels),
            Codec.INT.optionalFieldOf("essence", 0).forGetter(BountyDef::essence),
            Codec.STRING.listOf().optionalFieldOf("requires", List.of()).forGetter(BountyDef::requires),
            Codec.STRING.optionalFieldOf("text", "").forGetter(BountyDef::text)
    ).apply(i, BountyDef::new));
}
