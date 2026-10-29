package dev.awaken.kin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * What one player and the land's beasts are to each other (SPEC §43.5), kind by kind: Kinship (points, read as Wary,
 * Curious, Friendly, Kin), whether they've met one and where, how much of its want they've watched, near misses, the
 * fear a killing leaves, and the rumour of where its kind lives. Saved on the player (schema 1), kept through death.
 */
public final class Kinship {
    public static final int SCHEMA = 1;
    /** Points at which each step begins. */
    public static final int CURIOUS = 10, FRIENDLY = 40, KIN = 100;
    public static final int HELP = 10, DAY = 4, HURT = 15, KILL = 60;
    /** Three days of running from you after you kill one. */
    public static final long FEAR_TICKS = 3 * 24000L;

    public enum Step {
        WARY("Wary"), CURIOUS("Curious"), FRIENDLY("Friendly"), KIN("Kin");

        public final String label;

        Step(String label) {
            this.label = label;
        }
    }

    /** One kind as this player knows it. */
    public static final class Kind {
        public int points;
        public boolean met, heard;
        public long metDay;
        public int metX, metZ;
        public int watched;
        public long lastWatch;
        public int misses;
        public long fearUntil;
        public String lastLine = "";
        public int rumourX, rumourZ;
        public boolean rumour;
        /** Play time (ticks) when the rumour was first given: a day of play unfollowed makes it plainer (SPEC §43.11). */
        public long rumourAt;

        static final Codec<Kind> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("points", 0).forGetter(k -> k.points),
                Codec.BOOL.optionalFieldOf("met", false).forGetter(k -> k.met),
                Codec.BOOL.optionalFieldOf("heard", false).forGetter(k -> k.heard),
                Codec.LONG.optionalFieldOf("met_day", 0L).forGetter(k -> k.metDay),
                Codec.INT.listOf().optionalFieldOf("met_at", java.util.List.of(0, 0)).forGetter(k -> java.util.List.of(k.metX, k.metZ)),
                Codec.INT.optionalFieldOf("watched", 0).forGetter(k -> k.watched),
                Codec.INT.optionalFieldOf("misses", 0).forGetter(k -> k.misses),
                Codec.LONG.optionalFieldOf("fear_until", 0L).forGetter(k -> k.fearUntil),
                Codec.STRING.optionalFieldOf("last_line", "").forGetter(k -> k.lastLine),
                Codec.INT.listOf().optionalFieldOf("rumour", java.util.List.of()).forGetter(k -> k.rumour ? java.util.List.of(k.rumourX, k.rumourZ) : java.util.List.of()),
                Codec.LONG.optionalFieldOf("rumour_at", 0L).forGetter(k -> k.rumourAt)
        ).apply(i, (points, met, heard, day, at, watched, misses, fear, line, rumour, rumourAt) -> {
            Kind k = new Kind();
            k.points = points;
            k.met = met;
            k.heard = heard;
            k.metDay = day;
            if (at.size() == 2) {
                k.metX = at.get(0);
                k.metZ = at.get(1);
            }
            k.watched = watched;
            k.misses = misses;
            k.fearUntil = fear;
            k.lastLine = line;
            if (rumour.size() == 2) {
                k.rumour = true;
                k.rumourX = rumour.get(0);
                k.rumourZ = rumour.get(1);
            }
            k.rumourAt = rumourAt;
            return k;
        }));

        public Step step() {
            return points >= KIN ? Step.KIN : points >= FRIENDLY ? Step.FRIENDLY : points >= CURIOUS ? Step.CURIOUS : Step.WARY;
        }
    }

    public final Map<KinKind, Kind> kinds = new EnumMap<>(KinKind.class);
    /** The first fire's Lampwing (the lesson, JOURNEY part 3): 0 not yet, 1 it came, 2 it's done. */
    public int lesson;
    /** The day "made today" counters and the days lived were last counted for (the owner's own beasts). */
    public long countedDay = -1;

    static final MapCodec<Kinship> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(k -> SCHEMA),
            Codec.unboundedMap(Codec.STRING, Kind.CODEC).optionalFieldOf("kinds", Map.of()).forGetter(k -> {
                Map<String, Kind> m = new HashMap<>();
                k.kinds.forEach((kind, v) -> m.put(kind.id(), v));
                return m;
            }),
            Codec.INT.optionalFieldOf("lesson", 0).forGetter(k -> k.lesson),
            Codec.LONG.optionalFieldOf("counted_day", -1L).forGetter(k -> k.countedDay)
    ).apply(i, (schema, kinds, lesson, day) -> {
        Kinship k = new Kinship();
        kinds.forEach((id, v) -> k.kinds.put(KinKind.of(id), v));
        k.lesson = lesson;
        k.countedDay = day;
        return k;
    }));

    public static Kinship of(ServerPlayer p) {
        return p.getData(KinContent.KINSHIP.get());
    }

    public Kind kind(KinKind k) {
        return kinds.computeIfAbsent(k, x -> new Kind());
    }

    public Step step(KinKind k) {
        Kind s = kinds.get(k);
        return s == null ? Step.WARY : s.step();
    }

    public boolean afraid(KinKind k, long gameTime) {
        Kind s = kinds.get(k);
        return s != null && s.fearUntil > gameTime;
    }

    /** Spirit warms the wild's beasts to you sooner: +1% a point, to +30% (SPEC §43.4). */
    public static int warmed(ServerPlayer p, int points) {
        if (points <= 0) return points;
        int spi = dev.awaken.progression.Progression.profile(p).stat(dev.awaken.progression.Stat.SPI);
        return Math.round(points * (1F + Math.min(30, Math.max(0, spi)) / 100F));
    }

    /** Adds (or takes) Kinship; true when it crossed into a new step. */
    public static boolean add(ServerPlayer p, KinKind k, int points) {
        Kinship ks = of(p);
        Kind s = ks.kind(k);
        Step before = s.step();
        s.points = Math.max(0, Math.min(1000, s.points + warmed(p, points)));
        p.setData(KinContent.KINSHIP.get(), ks);
        return s.step() != before;
    }

    public static void save(ServerPlayer p, Kinship ks) {
        p.setData(KinContent.KINSHIP.get(), ks);
    }
}
