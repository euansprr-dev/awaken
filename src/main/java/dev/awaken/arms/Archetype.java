package dev.awaken.arms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A fighting style (SPEC §25): its moves on one timing table, its guard and its dodge. Every weapon of the style shares
 * these rows, so the server's hits, both views' clips, the trails and the sounds stay in step. Read from
 * {@code data/awaken/arms/archetypes.json} on both sides ({@link Archetypes}).
 */
public record Archetype(String id, String name, float baseSpeed, Guard guard, Dodge dodge, List<String> chain, List<String> masteryChain,
                        int buffer, int late, Map<String, MoveDef> moves, Traits traits) {

    /**
     * What sets a style apart beyond its rows (SPEC §25 "Styles"): a blow from behind multiplied (the Daggers' backstab;
     * 1 for none), and the draw cut (the Katana: the first strike after the blade has rested this long, in ticks, is
     * drawn from the sheath, its motion and posture multiplied; 0 for none). A spirit struck multiplied (the Scythe);
     * the struck body pulled this far toward the fighter (the Chain-blade); power energy back for each blow landed (the
     * War-staff); reach grown with each step of the chain (the Precursor Arm's blade); a riposte that throws the body
     * this hard (the Gauntlets).
     */
    public record Traits(float backstab, int drawIdle, float drawMotion, float drawPosture, float spirit, float pull, float energy,
                         float reachPerStep, float riposteThrow) {
        public static final Traits NONE = new Traits(1F, 0, 1F, 1F, 1F, 0F, 0F, 0F, 0F);
        static final Codec<Traits> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("backstab", 1F).forGetter(Traits::backstab),
                Codec.INT.optionalFieldOf("drawIdle", 0).forGetter(Traits::drawIdle),
                Codec.FLOAT.optionalFieldOf("drawMotion", 1F).forGetter(Traits::drawMotion),
                Codec.FLOAT.optionalFieldOf("drawPosture", 1F).forGetter(Traits::drawPosture),
                Codec.FLOAT.optionalFieldOf("spirit", 1F).forGetter(Traits::spirit),
                Codec.FLOAT.optionalFieldOf("pull", 0F).forGetter(Traits::pull),
                Codec.FLOAT.optionalFieldOf("energy", 0F).forGetter(Traits::energy),
                Codec.FLOAT.optionalFieldOf("reachPerStep", 0F).forGetter(Traits::reachPerStep),
                Codec.FLOAT.optionalFieldOf("riposteThrow", 0F).forGetter(Traits::riposteThrow)
        ).apply(i, Traits::new));
    }

    /** The moves every style may have; the JSON keys are their lower-case names. */
    public enum Move {
        // only append: the ordinals are on the wire
        STRIKE_1, STRIKE_2, STRIKE_3, STRIKE_4, STRIKE_5, HEAVY, RIPOSTE, SLIPCUT, EXECUTION, ART, UNDERCUT, PLUNGE;

        public final String key = name().toLowerCase(Locale.ROOT);
        private static final Move[] VALUES = values();

        public static @Nullable Move byKey(String key) {
            for (Move m : VALUES) if (m.key.equals(key)) return m;
            return null;
        }

        public static @Nullable Move byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : null;
        }

        public boolean strike() {
            return ordinal() <= STRIKE_5.ordinal();
        }
    }

    /** How a move sounds through the air: a quick cut, a heavy's slow arc, a thrust's straight hiss, an Art's release. */
    public enum Swish {
        LIGHT, HEAVY, THRUST, ART;

        public final String key = name().toLowerCase(Locale.ROOT);

        static final Codec<Swish> CODEC = Codec.STRING.comapFlatMap(s -> {
            for (Swish w : values()) if (w.key.equals(s)) return DataResult.success(w);
            return DataResult.error(() -> "unknown swish " + s);
        }, w -> w.key);
    }

    /** The guard: ticks after the raise that deflect, how much a block takes off, its arc, a broken guard's stagger. */
    public record Guard(int deflect, float reduction, float heavyReduction, float angle, int breakTicks, int lockout) {
        static final Codec<Guard> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("deflect").forGetter(Guard::deflect),
                Codec.FLOAT.fieldOf("reduction").forGetter(Guard::reduction),
                Codec.FLOAT.fieldOf("heavyReduction").forGetter(Guard::heavyReduction),
                Codec.FLOAT.fieldOf("angle").forGetter(Guard::angle),
                Codec.INT.fieldOf("breakTicks").forGetter(Guard::breakTicks),
                Codec.INT.fieldOf("lockout").forGetter(Guard::lockout)
        ).apply(i, Guard::new));
    }

    /** The dodge: its length and distance, the evade and perfect ticks, its charges and the attacker's slow beat. */
    public record Dodge(int ticks, float distance, int evade, int perfect, int charges, int recharge, int slow) {
        static final Codec<Dodge> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("ticks").forGetter(Dodge::ticks),
                Codec.FLOAT.fieldOf("distance").forGetter(Dodge::distance),
                Codec.INT.fieldOf("evade").forGetter(Dodge::evade),
                Codec.INT.fieldOf("perfect").forGetter(Dodge::perfect),
                Codec.INT.fieldOf("charges").forGetter(Dodge::charges),
                Codec.INT.fieldOf("recharge").forGetter(Dodge::recharge),
                Codec.INT.fieldOf("slow").forGetter(Dodge::slow)
        ).apply(i, Dodge::new));
    }

    /**
     * The weapon in first person from the swing's start pose to its end pose, and its lunge (blocks). A pose is the
     * hand's place on screen (x right, y up: blocks from vanilla's resting hand), the blade's roll in the screen's
     * plane (degrees, + toward the off hand) and optionally the tip's tilt forward (degrees: a chop, a thrust). The
     * hand travels between poses on an arc about the shoulder.
     */
    public record Swing(List<Float> from, List<Float> to, float lunge, String form) {
        static final Codec<Swing> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.listOf(3, 4).fieldOf("from").forGetter(Swing::from),
                Codec.FLOAT.listOf(3, 4).fieldOf("to").forGetter(Swing::to),
                Codec.FLOAT.optionalFieldOf("lunge", 0F).forGetter(Swing::lunge),
                // the move's shape (slash, chop, stab, rise...): the blow pins or lifts by it (SPEC §25 "Fight Feel")
                Codec.STRING.optionalFieldOf("form", "").forGetter(Swing::form)
        ).apply(i, Swing::new));
    }

    /** An Art's wave: its reach grows this many blocks a tick through the active ticks, out to {@code range}. */
    public record ArtDef(float wave, float range) {
        static final Codec<ArtDef> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("wave").forGetter(ArtDef::wave),
                Codec.FLOAT.fieldOf("range").forGetter(ArtDef::range)
        ).apply(i, ArtDef::new));
    }

    /** A held heavy: the hold that starts it, the glint (release within 2 ticks: perfect) and the longest hold. */
    public record Charge(int min, int glint, int max, float glintMotion, int glintPosture, int glintHitstop) {
        static final Codec<Charge> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("min").forGetter(Charge::min),
                Codec.INT.fieldOf("glint").forGetter(Charge::glint),
                Codec.INT.fieldOf("max").forGetter(Charge::max),
                Codec.FLOAT.fieldOf("glintMotion").forGetter(Charge::glintMotion),
                Codec.INT.fieldOf("glintPosture").forGetter(Charge::glintPosture),
                Codec.INT.fieldOf("glintHitstop").forGetter(Charge::glintHitstop)
        ).apply(i, Charge::new));

        public boolean perfect(int held) {
            return Math.abs(held - glint) <= 2;
        }
    }

    /**
     * One move's row. windup, active and recovery in ticks (the hit lands on the active ticks); cadence = the recovery
     * ticks where the next press earns a rhythm step; motion = damage multiplier; posture = what it does to a stance;
     * arc (degrees) and reach (blocks) = what it hits; window = how long after a deflect or dodge it can be called;
     * swish = its sound through the air; art = an Art's travelling wave.
     */
    public record MoveDef(int windup, int active, int recovery, List<Integer> cadence, float motion, int posture, float arc, float reach,
                          int hitstop, boolean finisher, int window, float bossCap, Optional<Charge> charge, Swing swing, Swish swish,
                          Optional<ArtDef> art) {
        static final Codec<MoveDef> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("windup").forGetter(MoveDef::windup),
                Codec.INT.fieldOf("active").forGetter(MoveDef::active),
                Codec.INT.fieldOf("recovery").forGetter(MoveDef::recovery),
                Codec.INT.listOf(2, 2).optionalFieldOf("cadence", List.of()).forGetter(MoveDef::cadence),
                Codec.FLOAT.fieldOf("motion").forGetter(MoveDef::motion),
                Codec.INT.fieldOf("posture").forGetter(MoveDef::posture),
                Codec.FLOAT.fieldOf("arc").forGetter(MoveDef::arc),
                Codec.FLOAT.fieldOf("reach").forGetter(MoveDef::reach),
                Codec.INT.fieldOf("hitstop").forGetter(MoveDef::hitstop),
                Codec.BOOL.optionalFieldOf("finisher", false).forGetter(MoveDef::finisher),
                Codec.INT.optionalFieldOf("window", 0).forGetter(MoveDef::window),
                Codec.FLOAT.optionalFieldOf("bossCap", 0F).forGetter(MoveDef::bossCap),
                Charge.CODEC.optionalFieldOf("charge").forGetter(MoveDef::charge),
                Swing.CODEC.fieldOf("swing").forGetter(MoveDef::swing),
                Swish.CODEC.optionalFieldOf("swish", Swish.LIGHT).forGetter(MoveDef::swish),
                ArtDef.CODEC.optionalFieldOf("art").forGetter(MoveDef::art)
        ).apply(i, MoveDef::new));

        /** How far the move reaches {@code sinceStart} ticks in: its reach, or an Art's wave grown out to its range. */
        public float reachAt(long sinceStart) {
            if (art.isEmpty()) return reach;
            return Math.min(art.get().range(), reach + art.get().wave() * Math.max(0, sinceStart - windup + 1));
        }

        /** Ticks from the press to the end of the active window. */
        public int activeEnd() {
            return windup + active;
        }

        public int total() {
            return windup + active + recovery;
        }

        public boolean hasCadence() {
            return cadence.size() == 2;
        }
    }

    static final Codec<Archetype> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(Archetype::id),
            Codec.STRING.fieldOf("name").forGetter(Archetype::name),
            Codec.FLOAT.fieldOf("baseSpeed").forGetter(Archetype::baseSpeed),
            Guard.CODEC.fieldOf("guard").forGetter(Archetype::guard),
            Dodge.CODEC.fieldOf("dodge").forGetter(Archetype::dodge),
            Codec.STRING.listOf().fieldOf("chain").forGetter(Archetype::chain),
            Codec.STRING.listOf().optionalFieldOf("masteryChain", List.of()).forGetter(Archetype::masteryChain),
            Codec.INT.optionalFieldOf("buffer", 5).forGetter(Archetype::buffer),
            Codec.INT.optionalFieldOf("late", 6).forGetter(Archetype::late),
            Codec.unboundedMap(Codec.STRING, MoveDef.CODEC).fieldOf("moves").forGetter(Archetype::moves),
            Traits.CODEC.optionalFieldOf("traits", Traits.NONE).forGetter(Archetype::traits)
    ).apply(i, Archetype::new));

    /** The moves by enum, resolved once (the map's keys checked against {@link Move}). */
    Map<Move, MoveDef> byMove() {
        Map<Move, MoveDef> out = new EnumMap<>(Move.class);
        moves.forEach((k, v) -> {
            Move m = Move.byKey(k);
            if (m == null) throw new IllegalArgumentException("archetype " + id + ": unknown move " + k);
            out.put(m, v);
        });
        return out;
    }
}
