package dev.awaken.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What the world remembers of its settlements ({@code awaken:villages}, schemaVersion 1; SPEC §28.6): each one met so
 * far by its key with its name, kind and place; its prosperity and mood (0-100, what W4's troubles and growth move);
 * each player's standing there; the damage done to it with the blocks that stood there (W4's Burning and Rebuild put
 * them back); buildings added; a short history the Journal shows; the notice board's requests and the day's trade
 * counts. Beside them, the emerald payments already made (EconomyHooks pays once per claim) and who has a bed at an
 * inn tonight. The people's own records are the people engine's ({@code awaken:citizens}).
 */
public final class VillageState extends SavedData {
    static final int SCHEMA = 1;
    /** Standing points at which a settlement knows a player better (Known, Friend, Kin). */
    public static final int KNOWN = 10, FRIEND = 35, KIN = 80;
    public static final String[] STANDINGS = {"stranger", "known", "friend", "kin"};

    public record Damage(long pos, BlockState original, long day, Optional<UUID> by) {
        static final Codec<Damage> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("pos").forGetter(Damage::pos),
                BlockState.CODEC.fieldOf("original").forGetter(Damage::original),
                Codec.LONG.fieldOf("day").forGetter(Damage::day),
                UUIDUtil.STRING_CODEC.optionalFieldOf("by").forGetter(Damage::by)
        ).apply(i, Damage::new));
    }

    public record History(long day, String text) {
        static final Codec<History> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("day").forGetter(History::day),
                Codec.STRING.fieldOf("text").forGetter(History::text)
        ).apply(i, History::new));
    }

    /** A notice on the board: what's wanted (an item id and count), how much is in, the pay, why. */
    public static final class Request {
        final String id, item, text;
        final int need, pay, standing;
        int have;
        final long posted;

        Request(String id, String item, int need, int have, int pay, int standing, long posted, String text) {
            this.id = id;
            this.item = item;
            this.need = need;
            this.have = have;
            this.pay = pay;
            this.standing = standing;
            this.posted = posted;
            this.text = text;
        }

        static final Codec<Request> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(r -> r.id),
                Codec.STRING.fieldOf("item").forGetter(r -> r.item),
                Codec.INT.fieldOf("need").forGetter(r -> r.need),
                Codec.INT.fieldOf("have").forGetter(r -> r.have),
                Codec.INT.fieldOf("pay").forGetter(r -> r.pay),
                Codec.INT.fieldOf("standing").forGetter(r -> r.standing),
                Codec.LONG.fieldOf("posted").forGetter(r -> r.posted),
                Codec.STRING.fieldOf("text").forGetter(r -> r.text)
        ).apply(i, Request::new));
    }

    /** One play-day's counts (trades bought from players, standing earned by trade) keyed "uuid|what". */
    public record Day(long day, Map<String, Integer> counts) {
        static final Codec<Day> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("day").forGetter(Day::day),
                Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("counts").forGetter(Day::counts)
        ).apply(i, (d, c) -> new Day(d, new HashMap<>(c))));
    }

    public static final class Village {
        final String key;
        String name;
        int kind, family, radius, prosperity, mood;
        long origin, founded, requestDay;
        final Map<UUID, Integer> standing;
        final List<Damage> damage;
        final List<String> added;
        final List<History> history;
        final List<Request> requests;
        Day day;

        Village(String key, String name, int kind, int family, long origin, int radius, int prosperity, int mood, long founded,
                Map<UUID, Integer> standing, List<Damage> damage, List<String> added, List<History> history, List<Request> requests,
                long requestDay, Day day) {
            this.key = key;
            this.name = name;
            this.kind = kind;
            this.family = family;
            this.origin = origin;
            this.radius = radius;
            this.prosperity = prosperity;
            this.mood = mood;
            this.founded = founded;
            this.standing = new HashMap<>(standing);
            this.damage = new ArrayList<>(damage);
            this.added = new ArrayList<>(added);
            this.history = new ArrayList<>(history);
            this.requests = new ArrayList<>(requests);
            this.requestDay = requestDay;
            this.day = new Day(day.day(), new HashMap<>(day.counts()));
        }

        static final Codec<Village> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("key").forGetter(v -> v.key),
                Codec.STRING.fieldOf("name").forGetter(v -> v.name),
                Codec.INT.fieldOf("kind").forGetter(v -> v.kind),
                Codec.INT.fieldOf("family").forGetter(v -> v.family),
                Codec.LONG.fieldOf("origin").forGetter(v -> v.origin),
                Codec.INT.fieldOf("radius").forGetter(v -> v.radius),
                Codec.INT.optionalFieldOf("prosperity", 50).forGetter(v -> v.prosperity),
                Codec.INT.optionalFieldOf("mood", 60).forGetter(v -> v.mood),
                Codec.LONG.optionalFieldOf("founded", 0L).forGetter(v -> v.founded),
                Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT).optionalFieldOf("standing", Map.of()).forGetter(v -> v.standing),
                Damage.CODEC.listOf().optionalFieldOf("damage", List.of()).forGetter(v -> v.damage),
                Codec.STRING.listOf().optionalFieldOf("added", List.of()).forGetter(v -> v.added),
                History.CODEC.listOf().optionalFieldOf("history", List.of()).forGetter(v -> v.history),
                Request.CODEC.listOf().optionalFieldOf("requests", List.of()).forGetter(v -> v.requests),
                Codec.LONG.optionalFieldOf("requestDay", -1L).forGetter(v -> v.requestDay),
                Day.CODEC.optionalFieldOf("today", new Day(-1, Map.of())).forGetter(v -> v.day)
        ).apply(i, Village::new));

        public String key() {
            return key;
        }

        public String name() {
            return name;
        }

        public int prosperity() {
            return prosperity;
        }

        public int mood() {
            return mood;
        }

        public List<Damage> damage() {
            return List.copyOf(damage);
        }

        public List<History> history() {
            return List.copyOf(history);
        }

        public List<String> added() {
            return List.copyOf(added);
        }

        int points(UUID p) {
            return standing.getOrDefault(p, 0);
        }

        /** Today's count of something (resets with the play-day). */
        int count(long today, String what) {
            if (day.day() != today) day = new Day(today, new HashMap<>());
            return day.counts().getOrDefault(what, 0);
        }

        void add(long today, String what, int n) {
            count(today, what);
            day.counts().merge(what, n, Integer::sum);
        }
    }

    /** A night's bed at an inn: where, and the respawn point it replaced (given back after one use, or the next night). */
    public record Stay(String village, long bed, Optional<ServerPlayer.RespawnConfig> before, long day) {
        static final Codec<Stay> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("village").forGetter(Stay::village),
                Codec.LONG.fieldOf("bed").forGetter(Stay::bed),
                ServerPlayer.RespawnConfig.CODEC.optionalFieldOf("before").forGetter(Stay::before),
                Codec.LONG.fieldOf("day").forGetter(Stay::day)
        ).apply(i, Stay::new));
    }

    public static final Codec<VillageState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(s -> SCHEMA),
            Village.CODEC.listOf().optionalFieldOf("villages", List.of()).forGetter(s -> List.copyOf(s.villages.values())),
            Codec.STRING.listOf().optionalFieldOf("claims", List.of()).forGetter(s -> List.copyOf(s.claims)),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Stay.CODEC).optionalFieldOf("inn", Map.of()).forGetter(s -> s.inn)
    ).apply(i, VillageState::new));

    public static final SavedDataType<VillageState> TYPE = new SavedDataType<>(Awaken.id("villages"), VillageState::new, CODEC);

    final Map<String, Village> villages = new LinkedHashMap<>();
    final Set<String> claims = new HashSet<>();
    final Map<UUID, Stay> inn = new HashMap<>();

    public VillageState() {}

    private VillageState(int schema, List<Village> vs, List<String> claims, Map<UUID, Stay> inn) {
        for (Village v : vs) villages.put(v.key, v);
        this.claims.addAll(claims);
        this.inn.putAll(inn);
    }

    public static VillageState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** The record of a settlement (made the first time it is met). */
    Village village(String key, String name, int kind, int family, long origin, int radius, long today) {
        Village v = villages.get(key);
        if (v == null) {
            v = new Village(key, name, kind, family, origin, radius, 50, 60, today, Map.of(), List.of(), List.of(), List.of(), List.of(), -1,
                    new Day(today, Map.of()));
            v.history.add(new History(today, "First found."));
            villages.put(key, v);
            setDirty();
        }
        return v;
    }

    /** A player's standing tier at a settlement: 0 stranger, 1 known, 2 friend, 3 kin. */
    static int tier(int points) {
        return points >= KIN ? 3 : points >= FRIEND ? 2 : points >= KNOWN ? 1 : 0;
    }
}
