package dev.awaken.mobs.boss;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;

/**
 * A boss's fight script (SPEC §35.2): {@code data/awaken/kit/fights/<id>.json}. Per phase, a weighted pool of moves
 * with conditions and strings, the transition into it; the intro, the enrage and the death. Read by {@code FightRunner}.
 */
public final class FightScripts {
    private FightScripts() {}

    public static final int SCHEMA_VERSION = 1;

    /** Where the target must be for an entry: anywhere, close in, far off, behind the body, or in the air. */
    public enum When { ANY, NEAR, FAR, BEHIND, AIR }

    public record Entry(String move, int weight, When when, List<List<String>> then) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("move").forGetter(Entry::move),
                Codec.INT.optionalFieldOf("weight", 1).forGetter(Entry::weight),
                dev.awaken.core.Codecs.lenientEnum(When.class, When.ANY).optionalFieldOf("when", When.ANY).forGetter(Entry::when),
                Codec.STRING.listOf().listOf().optionalFieldOf("then", List.of()).forGetter(Entry::then)
        ).apply(i, Entry::new));
    }

    public record Phase(List<Entry> pool, Optional<String> transition) {
        static final Codec<Phase> CODEC = RecordCodecBuilder.create(i -> i.group(
                Entry.CODEC.listOf().fieldOf("pool").forGetter(Phase::pool),
                Codec.STRING.optionalFieldOf("transition").forGetter(Phase::transition)
        ).apply(i, Phase::new));
    }

    /** Before the fight: kneeling, standing or hovering; the move it plays when the fight starts. */
    public record Intro(String pose, Optional<String> move) {
        static final Intro NONE = new Intro("idle", Optional.empty());
        static final Codec<Intro> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("pose", "idle").forGetter(Intro::pose),
                Codec.STRING.optionalFieldOf("move").forGetter(Intro::move)
        ).apply(i, Intro::new));
    }

    /** Under {@code below} health it moves faster (never harder): its ticks shrink by {@code speed}. */
    public record Enrage(float below, float speed) {
        static final Enrage DEFAULT = new Enrage(0.15F, 1.2F);
        static final Codec<Enrage> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("below", 0.15F).forGetter(Enrage::below),
                Codec.FLOAT.optionalFieldOf("speed", 1.2F).forGetter(Enrage::speed)
        ).apply(i, Enrage::new));
    }

    public record Fight(int schemaVersion, String id, String moves, Intro intro, List<Phase> phases, Enrage enrage, Optional<String> death) {
        public static final Codec<Fight> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("schemaVersion").forGetter(Fight::schemaVersion),
                Codec.STRING.fieldOf("id").forGetter(Fight::id),
                Codec.STRING.fieldOf("moves").forGetter(Fight::moves),
                Intro.CODEC.optionalFieldOf("intro", Intro.NONE).forGetter(Fight::intro),
                Phase.CODEC.listOf().fieldOf("phases").forGetter(Fight::phases),
                Enrage.CODEC.optionalFieldOf("enrage", Enrage.DEFAULT).forGetter(Fight::enrage),
                Codec.STRING.optionalFieldOf("death").forGetter(Fight::death)
        ).apply(i, Fight::new));

        public Phase phase(int p) {
            return phases.get(Math.max(0, Math.min(phases.size() - 1, p)));
        }
    }
}
