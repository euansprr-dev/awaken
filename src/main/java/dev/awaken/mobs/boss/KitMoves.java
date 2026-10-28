package dev.awaken.mobs.boss;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.core.api.Strikes;

import java.util.List;
import java.util.Optional;

/**
 * A kit body's move table (SPEC §35.2): {@code data/awaken/kit/moves/<id>.json}, written by tools/art/specs/kit from the
 * same numbers as the clips. The server runs it (timing, tracking, root motion, the hit capsules); the client plays the
 * clips of the same names, stretched to the same ticks.
 */
public final class KitMoves {
    private KitMoves() {}

    public static final int SCHEMA_VERSION = 1;

    /** The shortest wind-up a class may have, in ticks: a blow you can't read in time is a bug (SPEC §35.2). */
    public static int minWindup(Strikes.Kind kind) {
        return switch (kind) {
            case LIGHT -> 8;
            case HEAVY, GRAB -> 12;
            case PERILOUS -> 14;
            default -> 6;
        };
    }

    /** The longest hold a HEAVY move may add (0.6 s). */
    public static final int MAX_HOLD = 12;

    /** A move's ticks as it plays: wind-up, the hold it was given, active, recovery. */
    public record Played(int windup, int hold, int active, int recover) {
        public int total() {
            return windup + hold + active + recover;
        }
    }

    /**
     * How a move plays at {@code speed} (1 normally, the fight's enrage speed under its line): the wind-up and recovery
     * shrink, the wind-up never under its class's tell; the strike itself keeps its ticks. Both sides compute it alike.
     */
    public static Played played(Move m, float speed, int hold) {
        float sp = Math.max(1, speed);
        int w = Math.min(m.time().windup(), Math.max(m.kind() == Strikes.Kind.NONE ? 1 : minWindup(m.kind()), Math.round(m.time().windup() / sp)));
        return new Played(w, Math.max(0, Math.min(MAX_HOLD, hold)), Math.max(1, m.time().active()), Math.max(1, Math.round(m.time().recover() / sp)));
    }

    /** A capsule in the body's space: blocks from the feet, +z forward, +y up, +x the body's left. */
    public record Capsule(float ax, float ay, float az, float bx, float by, float bz, float r) {
        static final Codec<Capsule> CODEC = Codec.FLOAT.listOf().comapFlatMap(
                l -> l.size() == 7 ? DataResult.success(new Capsule(l.get(0), l.get(1), l.get(2), l.get(3), l.get(4), l.get(5), l.get(6)))
                        : DataResult.error(() -> "a capsule is [ax, ay, az, bx, by, bz, r]"),
                c -> List.of(c.ax, c.ay, c.az, c.bx, c.by, c.bz, c.r));
    }

    public record Time(int windup, int hold, int active, int recover, float track, int lock) {
        static final Codec<Time> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("windup").forGetter(Time::windup),
                Codec.INT.optionalFieldOf("hold", 0).forGetter(Time::hold),
                Codec.INT.fieldOf("active").forGetter(Time::active),
                Codec.INT.fieldOf("recover").forGetter(Time::recover),
                Codec.FLOAT.optionalFieldOf("track", 0F).forGetter(Time::track),
                Codec.INT.optionalFieldOf("lock", 0).forGetter(Time::lock)
        ).apply(i, Time::new));
    }

    /** Root motion: blocks forward over the active ticks, and up (a hop, a rise to hover). */
    public record Motion(float advance, float rise) {
        static final Motion NONE = new Motion(0, 0);
        static final Codec<Motion> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("advance", 0F).forGetter(Motion::advance),
                Codec.FLOAT.optionalFieldOf("rise", 0F).forGetter(Motion::rise)
        ).apply(i, Motion::new));
    }

    public record Move(String id, String title, Strikes.Kind kind, boolean signature, List<String> kinds, List<Float> range, Time time,
                       Motion motion, List<List<Capsule>> hits, float damage, float knock, Optional<String> ability, List<Float> origin) {
        static final Codec<Move> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Move::id),
                Codec.STRING.optionalFieldOf("title", "").forGetter(Move::title),
                dev.awaken.core.Codecs.lenientEnum(Strikes.Kind.class, Strikes.Kind.NONE).fieldOf("class").forGetter(Move::kind),
                Codec.BOOL.optionalFieldOf("signature", false).forGetter(Move::signature),
                Codec.STRING.listOf().optionalFieldOf("kinds", List.of()).forGetter(Move::kinds),
                Codec.FLOAT.listOf().optionalFieldOf("range", List.of(0F, 4F)).forGetter(Move::range),
                Time.CODEC.fieldOf("time").forGetter(Move::time),
                Motion.CODEC.optionalFieldOf("motion", Motion.NONE).forGetter(Move::motion),
                Capsule.CODEC.listOf().listOf().optionalFieldOf("hits", List.of()).forGetter(Move::hits),
                Codec.FLOAT.optionalFieldOf("damage", 1F).forGetter(Move::damage),
                Codec.FLOAT.optionalFieldOf("knock", 0.5F).forGetter(Move::knock),
                Codec.STRING.optionalFieldOf("ability").forGetter(Move::ability),
                Codec.FLOAT.listOf().optionalFieldOf("origin", List.of(0F, 1F, 1F)).forGetter(Move::origin)
        ).apply(i, Move::new));

        /** Every tick the move takes with no hold. */
        public int length() {
            return time.windup + time.active + time.recover;
        }

        public float minRange() {
            return range.isEmpty() ? 0 : range.get(0);
        }

        public float maxRange() {
            return range.size() < 2 ? 4 : range.get(1);
        }

        public boolean is(String kind) {
            return kinds.contains(kind);
        }
    }

    public record Table(int schemaVersion, String id, List<Move> moves) {
        public static final Codec<Table> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("schemaVersion").forGetter(Table::schemaVersion),
                Codec.STRING.fieldOf("id").forGetter(Table::id),
                Move.CODEC.listOf().fieldOf("moves").forGetter(Table::moves)
        ).apply(i, Table::new));

        /** The move's index (the synced id), or -1. */
        public int index(String move) {
            for (int k = 0; k < moves.size(); k++) if (moves.get(k).id().equals(move)) return k;
            return -1;
        }

        public Move get(int index) {
            return moves.get(index);
        }
    }
}
