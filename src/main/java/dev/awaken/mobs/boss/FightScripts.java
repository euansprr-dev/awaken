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

    /** 2 added the mind (SPEC §48): a file at 1, or at 2 without one, plays as it always did. */
    public static final int SCHEMA_VERSION = 2;

    /** Where the target must be for an entry: anywhere, close in, far off, behind the body, or in the air. */
    public enum When { ANY, NEAR, FAR, BEHIND, AIR }

    /** What a fight can see of you (SPEC §48.1): Watch's facts, and the moment you stop after running away. */
    public enum Fact { NONE, DRINK, GUARD, HEAVY, RECOVERY, DODGE, CAST, PAUSED }

    /**
     * A fork in a string (SPEC §48.4), one per link at most two to a string: the next blow if your guard is up, if you've
     * just dodged, or if you've done neither. Absent: the string goes on as written.
     */
    public record Fork(Optional<String> guard, Optional<String> dodged, Optional<String> stayed) {
        static final Codec<Fork> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("guard").forGetter(Fork::guard),
                Codec.STRING.optionalFieldOf("dodged").forGetter(Fork::dodged),
                Codec.STRING.optionalFieldOf("stayed").forGetter(Fork::stayed)
        ).apply(i, Fork::new));
    }

    /** {@code forks}: where its string can change its mind. {@code band}: the distance it's chosen at, if tighter than its reach. */
    public record Entry(String move, int weight, When when, List<List<String>> then, List<Fork> forks, List<Float> band) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("move").forGetter(Entry::move),
                Codec.INT.optionalFieldOf("weight", 1).forGetter(Entry::weight),
                dev.awaken.core.Codecs.lenientEnum(When.class, When.ANY).optionalFieldOf("when", When.ANY).forGetter(Entry::when),
                Codec.STRING.listOf().listOf().optionalFieldOf("then", List.of()).forGetter(Entry::then),
                Fork.CODEC.listOf().optionalFieldOf("forks", List.of()).forGetter(Entry::forks),
                Codec.FLOAT.listOf().optionalFieldOf("band", List.of()).forGetter(Entry::band)
        ).apply(i, Entry::new));
    }

    /**
     * An answer to something it has seen you start (SPEC §48.2): the first of {@code moves} that reaches you, after it
     * shows you it noticed. {@code after}: how long the fact must have held (a guard kept up); {@code from}: the first
     * phase it's used in; {@code then}: the strings it may go on with; {@code press}: at a phrase's last blow it may carry on
     * instead of opening, for something you started while it was still swinging (the opening then comes after the answer).
     */
    public record Answer(Fact on, List<String> moves, float chance, int cooldown, int after, int from, List<List<String>> then, boolean press) {
        static final Codec<Answer> CODEC = RecordCodecBuilder.create(i -> i.group(
                dev.awaken.core.Codecs.lenientEnum(Fact.class, Fact.NONE).fieldOf("on").forGetter(Answer::on),
                Codec.STRING.listOf().fieldOf("moves").forGetter(Answer::moves),
                Codec.FLOAT.optionalFieldOf("chance", 1F).forGetter(Answer::chance),
                Codec.INT.optionalFieldOf("cooldown", 200).forGetter(Answer::cooldown),
                Codec.INT.optionalFieldOf("after", 0).forGetter(Answer::after),
                Codec.INT.optionalFieldOf("from", 0).forGetter(Answer::from),
                Codec.STRING.listOf().listOf().optionalFieldOf("then", List.of()).forGetter(Answer::then),
                Codec.BOOL.optionalFieldOf("press", false).forGetter(Answer::press)
        ).apply(i, Answer::new));
    }

    /** {@code react}: its reaction time in ticks (0: by its rank). {@code hold}: how often, and how long, any blow is held a beat. */
    public record Mind(int react, float holdChance, int holdTicks, java.util.Map<String, Integer> cooldowns, List<Answer> answers) {
        static final Codec<Mind> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("react", 0).forGetter(Mind::react),
                Codec.FLOAT.optionalFieldOf("holdChance", 0.25F).forGetter(Mind::holdChance),
                Codec.INT.optionalFieldOf("holdTicks", 6).forGetter(Mind::holdTicks),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("cooldowns", java.util.Map.of()).forGetter(Mind::cooldowns),
                Answer.CODEC.listOf().optionalFieldOf("answers", List.of()).forGetter(Mind::answers)
        ).apply(i, Mind::new));
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

    /**
     * {@code duel}: the fight plays as a duel (SPEC §47.3): every phrase (a move and its string) ends in an opening, a
     * deflected blow recoils and the phrase goes on, its last blow turned aside makes it reel, and it answers your habits.
     * {@code stance}: a duel's stance size (Arms' posture; 0 = a Lord's own), tuned with the sim per boss.
     * {@code mirrors}: it fights the way you do (SPEC §47.6, the Examiner): through its first phase it reads the habit you
     * lean on, and from its second the moves that answer it come up more; its shots wear your most-used power's look.
     * {@code mind}: it watches you and answers (SPEC §48); a fight without one plays as it did.
     */
    public record Fight(int schemaVersion, String id, String moves, Intro intro, List<Phase> phases, Enrage enrage, Optional<String> death, boolean duel,
                        int stance, boolean mirrors, Optional<Mind> mind) {
        public static final Codec<Fight> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("schemaVersion").forGetter(Fight::schemaVersion),
                Codec.STRING.fieldOf("id").forGetter(Fight::id),
                Codec.STRING.fieldOf("moves").forGetter(Fight::moves),
                Intro.CODEC.optionalFieldOf("intro", Intro.NONE).forGetter(Fight::intro),
                Phase.CODEC.listOf().fieldOf("phases").forGetter(Fight::phases),
                Enrage.CODEC.optionalFieldOf("enrage", Enrage.DEFAULT).forGetter(Fight::enrage),
                Codec.STRING.optionalFieldOf("death").forGetter(Fight::death),
                Codec.BOOL.optionalFieldOf("duel", false).forGetter(Fight::duel),
                Codec.INT.optionalFieldOf("stance", 0).forGetter(Fight::stance),
                Codec.BOOL.optionalFieldOf("mirrors", false).forGetter(Fight::mirrors),
                Mind.CODEC.optionalFieldOf("mind").forGetter(Fight::mind)
        ).apply(i, Fight::new));

        public Phase phase(int p) {
            return phases.get(Math.max(0, Math.min(phases.size() - 1, p)));
        }
    }
}
