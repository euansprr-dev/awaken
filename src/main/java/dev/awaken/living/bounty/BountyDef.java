package dev.awaken.living.bounty;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.living.quest.QuestDef;

import java.util.List;

/**
 * An Association bounty template (SPEC §21.0.9), from {@code data/<ns>/awaken/bounty/*.json}, schema 1: a notice for
 * players whose level is in {@code band} (0: 1-8, 1: 8-20, 2: 20-45, 3: 45-80, 4: 80+), with one objective (kill or
 * collect) and its pay: a share of a level and essence (coin once Carillon's economy exists). A {@code featured} notice
 * is put on the board before the others and only until it is done once (the gear tiers' part jobs, docs/arms-progression
 * PLAN §3.6); {@code keep} means Hale only needs to see what a collect notice asks for and hands it back.
 */
public record BountyDef(int schemaVersion, String title, int band, QuestDef.Objective objective, double levels, int essence,
                        List<String> requires, String text, boolean featured, boolean keep) {
    public BountyDef(int schemaVersion, String title, int band, QuestDef.Objective objective, double levels, int essence,
                     List<String> requires, String text) {
        this(schemaVersion, title, band, objective, levels, essence, requires, text, false, false);
    }

    public static final Codec<BountyDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", 1).forGetter(BountyDef::schemaVersion),
            Codec.STRING.fieldOf("title").forGetter(BountyDef::title),
            Codec.INT.optionalFieldOf("band", 0).forGetter(BountyDef::band),
            QuestDef.Objective.CODEC.fieldOf("objective").forGetter(BountyDef::objective),
            Codec.DOUBLE.optionalFieldOf("levels", 0.2).forGetter(BountyDef::levels),
            Codec.INT.optionalFieldOf("essence", 0).forGetter(BountyDef::essence),
            Codec.STRING.listOf().optionalFieldOf("requires", List.of()).forGetter(BountyDef::requires),
            Codec.STRING.optionalFieldOf("text", "").forGetter(BountyDef::text),
            Codec.BOOL.optionalFieldOf("featured", false).forGetter(BountyDef::featured),
            Codec.BOOL.optionalFieldOf("keep", false).forGetter(BountyDef::keep)
    ).apply(i, BountyDef::new));
}
