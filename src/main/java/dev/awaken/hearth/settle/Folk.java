package dev.awaken.hearth.settle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The people of one Hearth (SPEC §28.8, PLAN 4.7): everyone its household knows who could come to live here, who does,
 * which bed is whose, and the work they have in hand (Oren's commissions, Ilse's brews, the mine's evening haul, the
 * letters Pell brings). Saved with the Hearth ({@code HearthRecord} part {@code folk}, schema 1). Knowing someone is
 * sticky: once a member of the household knew them they stay known, so an offline owner's settlers still come.
 */
public final class Folk {
    public static final int SCHEMA = 1;

    /**
     * Someone who could live here. {@code id}: "oren_blacksmith" (a townsperson), "ilse" (a Wayfarer), "village:&lt;key&gt;"
     * (a friend's cousin from that village), "carillon" (someone from the city), "rescued:&lt;village&gt;:&lt;rec&gt;" (one you
     * brought home). {@code role}: what they do at home (oren, mira, teodor, sun, vael, ilse, hettie, wren, pell, miner,
     * farmer, smith, hand). {@code trade}: their trade on the people engine (Roster.JOBS). {@code face}: the skin their
     * body wears (a Carillon named face "oren", a texture "wayfarer/ilse", or "" for one picked like a villager's).
     */
    public record Person(String id, String given, String family, String role, String trade, String face, boolean slim, String from) {
        public static final Codec<Person> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Person::id),
                Codec.STRING.fieldOf("given").forGetter(Person::given),
                Codec.STRING.optionalFieldOf("family", "").forGetter(Person::family),
                Codec.STRING.fieldOf("role").forGetter(Person::role),
                Codec.STRING.fieldOf("trade").forGetter(Person::trade),
                Codec.STRING.optionalFieldOf("face", "").forGetter(Person::face),
                Codec.BOOL.optionalFieldOf("slim", false).forGetter(Person::slim),
                Codec.STRING.optionalFieldOf("from", "").forGetter(Person::from)
        ).apply(i, Person::new));

        /** "Ilse", "Ada Fenwick". */
        public String name() {
            return family.isEmpty() ? given : given + " " + family;
        }
    }

    /** Someone who lives here: who, their bed (none while they wait for one again), coming or home, since which play-day. */
    public static final class Settler {
        public static final int COMING = 1, HOME = 2;
        public final Person who;
        public BlockPos bed;
        public int state;
        public long since;

        public Settler(Person who, BlockPos bed, int state, long since) {
            this.who = who;
            this.bed = bed;
            this.state = state;
            this.since = since;
        }

        public boolean home() {
            return state == HOME;
        }

        private record Saved(Person who, Optional<BlockPos> bed, int state, long since) {}

        public static final Codec<Settler> CODEC = RecordCodecBuilder.<Saved>create(i -> i.group(
                Person.CODEC.fieldOf("who").forGetter(Saved::who),
                BlockPos.CODEC.optionalFieldOf("bed").forGetter(Saved::bed),
                Codec.INT.optionalFieldOf("state", HOME).forGetter(Saved::state),
                Codec.LONG.optionalFieldOf("since", 0L).forGetter(Saved::since)
        ).apply(i, Saved::new)).xmap(s -> new Settler(s.who, s.bed.orElse(null), s.state, s.since),
                s -> new Saved(s.who, Optional.ofNullable(s.bed), s.state, s.since));
    }

    /** A piece Oren forges overnight from the makings a player handed him, a temper up; {@code ready}: the play-day it's done. */
    public record Commission(UUID player, String item, int temper, long ready) {
        public static final Codec<Commission> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("player").forGetter(Commission::player),
                Codec.STRING.fieldOf("item").forGetter(Commission::item),
                Codec.INT.optionalFieldOf("temper", 1).forGetter(Commission::temper),
                Codec.LONG.fieldOf("ready").forGetter(Commission::ready)
        ).apply(i, Commission::new));
    }

    /** Herbs Ilse is brewing into tonics for a player, done on {@code ready}. */
    public record Brew(UUID player, String herb, int count, long ready) {
        public static final Codec<Brew> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("player").forGetter(Brew::player),
                Codec.STRING.fieldOf("herb").forGetter(Brew::herb),
                Codec.INT.fieldOf("count").forGetter(Brew::count),
                Codec.LONG.fieldOf("ready").forGetter(Brew::ready)
        ).apply(i, Brew::new));
    }

    /** Things waiting to be handed over (a haul with no chest by the shaft, a finished piece): an item id and a count. */
    public record Stack(String item, int count, String from) {
        public static final Codec<Stack> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("item").forGetter(Stack::item),
                Codec.INT.fieldOf("count").forGetter(Stack::count),
                Codec.STRING.optionalFieldOf("from", "").forGetter(Stack::from)
        ).apply(i, Stack::new));
    }

    private record Bed(UUID player, BlockPos pos) {
        static final Codec<Bed> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("player").forGetter(Bed::player),
                BlockPos.CODEC.fieldOf("pos").forGetter(Bed::pos)
        ).apply(i, Bed::new));
    }

    private record Taken(long lode, int count) {
        static final Codec<Taken> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("lode").forGetter(Taken::lode),
                Codec.INT.fieldOf("count").forGetter(Taken::count)
        ).apply(i, Taken::new));
    }

    private record Day(UUID player, long day) {
        static final Codec<Day> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("player").forGetter(Day::player),
                Codec.LONG.fieldOf("day").forGetter(Day::day)
        ).apply(i, Day::new));
    }

    /** Everyone the household knows who could come, in the order they came to know them. */
    public final LinkedHashMap<String, Person> known = new LinkedHashMap<>();
    public final List<Settler> settlers = new ArrayList<>();
    /** The bed each member of the household last woke in at home: theirs, never a settler's. */
    public final Map<UUID, BlockPos> ownBeds = new HashMap<>();
    public final List<Commission> commissions = new ArrayList<>();
    public final List<Brew> brews = new ArrayList<>();
    public final List<Stack> stash = new ArrayList<>();
    /** How much of each lode the mine has brought up (a lode gives the mine at most half its blocks). */
    public final Map<Long, Integer> lodeTaken = new HashMap<>();
    /** Play-days: the mine's last evening haul, Hettie's last breeding, Wren's last line. */
    public long haulDay = -1, breedDay = -1, ropeDay = -1;
    /** Each player's last letter from Pell, and who Wren gave her course to. */
    public final Map<UUID, Long> letters = new HashMap<>();
    public final java.util.Set<UUID> wrenGave = new java.util.HashSet<>();
    /** The rope lines Wren hung (where each starts), so she hangs a new one somewhere else. */
    public final List<BlockPos> ropes = new ArrayList<>();

    public Settler settler(String id) {
        for (Settler s : settlers) if (s.who.id().equals(id)) return s;
        return null;
    }

    public int living() {
        int n = 0;
        for (Settler s : settlers) if (s.home()) n++;
        return n;
    }

    // ---- saved ---------------------------------------------------------------------------------------------------

    private record Saved(int schema, List<Person> known, List<Settler> settlers, List<Bed> beds, List<Commission> commissions, List<Brew> brews,
                         List<Stack> stash, List<Taken> taken, List<Long> days, List<Day> letters, List<UUID> wren, List<BlockPos> ropes) {}

    public static final Codec<Folk> CODEC = RecordCodecBuilder.<Saved>create(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(Saved::schema),
            Person.CODEC.listOf().optionalFieldOf("known", List.of()).forGetter(Saved::known),
            Settler.CODEC.listOf().optionalFieldOf("settlers", List.of()).forGetter(Saved::settlers),
            Bed.CODEC.listOf().optionalFieldOf("beds", List.of()).forGetter(Saved::beds),
            Commission.CODEC.listOf().optionalFieldOf("commissions", List.of()).forGetter(Saved::commissions),
            Brew.CODEC.listOf().optionalFieldOf("brews", List.of()).forGetter(Saved::brews),
            Stack.CODEC.listOf().optionalFieldOf("stash", List.of()).forGetter(Saved::stash),
            Taken.CODEC.listOf().optionalFieldOf("taken", List.of()).forGetter(Saved::taken),
            Codec.LONG.listOf().optionalFieldOf("days", List.of()).forGetter(Saved::days),
            Day.CODEC.listOf().optionalFieldOf("letters", List.of()).forGetter(Saved::letters),
            UUIDUtil.CODEC.listOf().optionalFieldOf("wren", List.of()).forGetter(Saved::wren),
            BlockPos.CODEC.listOf().optionalFieldOf("ropes", List.of()).forGetter(Saved::ropes)
    ).apply(i, Saved::new)).xmap(Folk::fromSaved, Folk::toSaved);

    private static Folk fromSaved(Saved s) {
        Folk f = new Folk();
        for (Person p : s.known) f.known.put(p.id(), p);
        f.settlers.addAll(s.settlers);
        for (Bed b : s.beds) f.ownBeds.put(b.player(), b.pos());
        f.commissions.addAll(s.commissions);
        f.brews.addAll(s.brews);
        f.stash.addAll(s.stash);
        for (Taken t : s.taken) f.lodeTaken.put(t.lode(), t.count());
        if (s.days.size() >= 3) {
            f.haulDay = s.days.get(0);
            f.breedDay = s.days.get(1);
            f.ropeDay = s.days.get(2);
        }
        for (Day d : s.letters) f.letters.put(d.player(), d.day());
        f.wrenGave.addAll(s.wren);
        f.ropes.addAll(s.ropes);
        return f;
    }

    private Saved toSaved() {
        List<Bed> beds = new ArrayList<>();
        ownBeds.forEach((p, b) -> beds.add(new Bed(p, b)));
        List<Taken> taken = new ArrayList<>();
        lodeTaken.forEach((l, n) -> taken.add(new Taken(l, n)));
        List<Day> days = new ArrayList<>();
        letters.forEach((p, d) -> days.add(new Day(p, d)));
        return new Saved(SCHEMA, List.copyOf(known.values()), List.copyOf(settlers), beds, List.copyOf(commissions), List.copyOf(brews),
                List.copyOf(stash), taken, List.of(haulDay, breedDay, ropeDay), days, List.copyOf(wrenGave), List.copyOf(ropes));
    }
}
