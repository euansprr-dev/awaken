package dev.awaken.citizens;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

/**
 * One settlement's people as this world keeps them (schemaVersion 1; since W0 one entry of {@link CitizensSave}, the
 * world's {@code awaken:citizens}, which was Carillon's whole file before): every record's place in its day (struct of
 * arrays: segment, node, edge progress, goal, spot, state), the settlement's state that citizens change (lamps, stalls
 * and their stock, laundry, district safety and gratitude, repairs waiting for a mason, the chimneys and bells found so
 * far), the events running, and what its citizens remember of each player. Bodies are never saved: the ledger is the
 * truth (SPEC §16.5). Its sizes are its roster's (the record arrays) and its site's (a safety and a gratitude per
 * district).
 */
public final class CitizensData {
    public static final int SCHEMA = 1;

    // record states
    static final byte WALK = 0, INSIDE = 1, STAND = 2, ABSENT = 3;
    // lamp states
    static final byte LAMP_OUT = 0, LAMP_LIT = 1, LAMP_BROKEN = 2;

    private static final Codec<int[]> INTS = Codec.INT_STREAM.xmap(IntStream::toArray, Arrays::stream);
    private static final Codec<long[]> LONGS = Codec.LONG_STREAM.xmap(LongStream::toArray, Arrays::stream);

    /** A block a mason must put back: where, what it was, and the earliest game time to start. */
    public record Repair(long pos, BlockState state, long after) {
        static final Codec<Repair> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("pos").forGetter(Repair::pos),
                BlockState.CODEC.fieldOf("state").forGetter(Repair::state),
                Codec.LONG.optionalFieldOf("after", 0L).forGetter(Repair::after)
        ).apply(i, Repair::new));
    }

    /** One running city event (CityEvents): kind, phase, timers and a few kind-specific ints. */
    public record Event(int id, int kind, int phase, long since, long until, int[] data) {
        static final Codec<Event> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("id").forGetter(Event::id),
                Codec.INT.fieldOf("kind").forGetter(Event::kind),
                Codec.INT.fieldOf("phase").forGetter(Event::phase),
                Codec.LONG.fieldOf("since").forGetter(Event::since),
                Codec.LONG.fieldOf("until").forGetter(Event::until),
                INTS.fieldOf("data").forGetter(Event::data)
        ).apply(i, Event::new));

        @Override
        public boolean equals(Object o) {
            return o instanceof Event e && id == e.id && kind == e.kind && phase == e.phase && since == e.since && until == e.until
                    && Arrays.equals(data, e.data);
        }

        @Override
        public int hashCode() {
            return id * 31 + kind;
        }
    }

    /** A deed some citizens saw: what, on which play-day, how much it weighed, where (district). */
    public record Deed(String deed, long day, int weight, int district) {
        static final Codec<Deed> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("deed").forGetter(Deed::deed),
                Codec.LONG.fieldOf("day").forGetter(Deed::day),
                Codec.INT.fieldOf("weight").forGetter(Deed::weight),
                Codec.INT.fieldOf("district").forGetter(Deed::district)
        ).apply(i, Deed::new));
    }

    /**
     * What the city remembers of one player: each citizen's familiarity (0..100: seen around, heard of, saw them do
     * something) and regard (-100..100), the last few deeds witnessed, and when gossip last ran for them.
     */
    public static final class Memory {
        static final Codec<Memory> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("player").forGetter(m -> m.player),
                Codec.STRING.fieldOf("name").forGetter(m -> m.name),
                INTS.fieldOf("know").forGetter(m -> m.know),
                INTS.fieldOf("regard").forGetter(m -> m.regard),
                Deed.CODEC.listOf().fieldOf("deeds").forGetter(m -> m.deeds)
        ).apply(i, Memory::new));

        final UUID player;
        String name;
        final int[] know, regard;
        final List<Deed> deeds;

        Memory(UUID player, String name, int[] know, int[] regard, List<Deed> deeds) {
            this.player = player;
            this.name = name;
            this.know = know;
            this.regard = regard;
            this.deeds = new ArrayList<>(deeds);
        }

        Memory(UUID player, String name, int n) {
            this(player, name, new int[n], new int[n], List.of());
        }
    }

    public static final Codec<CitizensData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("schemaVersion").forGetter(d -> d.schemaVersion),
            Codec.STRING.fieldOf("roster").forGetter(d -> d.roster),
            Records.CODEC.fieldOf("records").forGetter(Records::of),
            World.CODEC.fieldOf("world").forGetter(World::of),
            Event.CODEC.listOf().fieldOf("events").forGetter(d -> d.events),
            Memory.CODEC.listOf().fieldOf("memories").forGetter(d -> d.memories),
            LONGS.fieldOf("days").forGetter(d -> new long[]{d.lastPlayDay, d.gossipDay, d.eventDay, d.nextEventId}),
            LONGS.optionalFieldOf("lights", new long[0]).forGetter(d -> d.lights),
            Timing.CODEC.optionalFieldOf("timing", Timing.EMPTY).forGetter(Timing::of)
    ).apply(i, CitizensData::new));

    /** The ledger's timing and event spots (see the fields). */
    record Timing(long[] lastGame, long[] lastClock, int cursor, int[] roleSpot) {
        static final Timing EMPTY = new Timing(new long[0], new long[0], 0, new int[0]);
        static final Codec<Timing> CODEC = RecordCodecBuilder.create(i -> i.group(
                LONGS.fieldOf("lastGame").forGetter(Timing::lastGame), LONGS.fieldOf("lastClock").forGetter(Timing::lastClock),
                Codec.INT.fieldOf("cursor").forGetter(Timing::cursor), INTS.fieldOf("roleSpot").forGetter(Timing::roleSpot)
        ).apply(i, Timing::new));

        static Timing of(CitizensData d) {
            if (d.beforeSave != null) d.beforeSave.accept(d);
            return new Timing(d.lastGame, d.lastClock, d.cursor, d.roleSpot);
        }
    }

    public int schemaVersion = SCHEMA;
    /** The saved data this settlement's state lives in (null while a test holds it on its own). */
    @Nullable CitizensSave owner;
    /** The roster's sha256 these records belong to ("" = not set up yet). */
    public String roster = "";

    // per record
    int[] seg = new int[0], at = new int[0], next = new int[0], prog = new int[0], goal = new int[0], place = new int[0],
            spot = new int[0], stop = new int[0], legs = new int[0], role = new int[0], event = new int[0];
    byte[] state = new byte[0], carry = new byte[0];
    long[] dwellUntil = new long[0], hurtUntil = new long[0];

    // the city's state
    byte[] lamps = new byte[0], stallOpen = new byte[0], stock = new byte[0], cloths = new byte[0];
    byte[] safety = new byte[8], gratitude = new byte[8];
    final List<Repair> repairs = new ArrayList<>();
    /** Station furniture already placed (bit per item, CitizenWork's order). */
    long[] furnished = new long[0];
    /** Bell block positions per roster bell (BlockPos.asLong; 0 = not found yet). */
    long[] bellPos = new long[0];
    /** Chimney tops found, as pairs (plot id, BlockPos.asLong); -1 position = the plot has none. */
    long[] chimneys = new long[0];
    /** Lantern lights in the world right now (Lanterns): cleared on start, so none is ever left behind. */
    long[] lights = new long[0];
    /** The ledger's timing (each record's last update, game time and clock) and slice cursor, and event spots (x, y, z
     *  hundredths; MIN_VALUE x = none): saved so a loaded ledger runs on exactly as it would have. */
    long[] lastGame = new long[0], lastClock = new long[0];
    int cursor;
    int[] roleSpot = new int[0];
    /** Set by the ledger before saving (Citizens keeps it current). */
    java.util.function.Consumer<CitizensData> beforeSave;

    final List<Event> events = new ArrayList<>();
    final List<Memory> memories = new ArrayList<>();
    long lastPlayDay = -1, gossipDay = -1, eventDay = -1, nextEventId = 1;

    public CitizensData() {
        Arrays.fill(safety, (byte) 80);
    }

    private CitizensData(int schema, String roster, Records r, World w, List<Event> events, List<Memory> memories, long[] days, long[] lights,
                         Timing timing) {
        this(schema, roster, r, w, events, memories, days);
        this.lights = lights.clone();
        this.lastGame = timing.lastGame().clone();
        this.lastClock = timing.lastClock().clone();
        this.cursor = timing.cursor();
        this.roleSpot = timing.roleSpot().clone();
    }

    private CitizensData(int schema, String roster, Records r, World w, List<Event> events, List<Memory> memories, long[] days) {
        this.schemaVersion = schema;
        this.roster = roster;
        r.into(this);
        w.into(this);
        this.events.addAll(events);
        this.memories.addAll(memories);
        lastPlayDay = days.length > 0 ? days[0] : -1;
        gossipDay = days.length > 1 ? days[1] : -1;
        eventDay = days.length > 2 ? days[2] : -1;
        nextEventId = days.length > 3 ? days[3] : 1;
    }

    /** The world's file is written at its next save. */
    void setDirty() {
        if (owner != null) owner.setDirty();
    }

    /**
     * Sizes every per-record array for {@code n} records and the settlement's stations and districts (a fresh ledger,
     * or a new roster).
     */
    void size(int n, int lampCount, int stallCount, int lineCount, int bellCount, int districts) {
        seg = filled(n, -1);
        at = new int[n];
        next = filled(n, -1);
        prog = new int[n];
        goal = new int[n];
        place = filled(n, -1);
        spot = filled(n, -1);
        stop = new int[n];
        legs = new int[n];
        role = new int[n];
        event = filled(n, -1);
        state = new byte[n];
        carry = new byte[n];
        dwellUntil = new long[n];
        hurtUntil = new long[n];
        lamps = new byte[lampCount];
        stallOpen = new byte[stallCount];
        stock = new byte[stallCount];
        Arrays.fill(stock, (byte) 70);
        cloths = new byte[lineCount];
        bellPos = new long[bellCount];
        safety = new byte[Math.max(1, districts)];
        gratitude = new byte[Math.max(1, districts)];
        Arrays.fill(safety, (byte) 80);
        repairs.clear();
        furnished = new long[0];
        chimneys = new long[0];
        events.clear();
        setDirty();
    }

    int records() {
        return seg.length;
    }

    Memory memory(UUID player, String name, boolean create) {
        for (Memory m : memories) if (m.player.equals(player)) {
            if (!name.isEmpty() && !name.equals(m.name)) m.name = name;
            return m;
        }
        if (!create) return null;
        Memory m = new Memory(player, name, records());
        memories.add(m);
        setDirty();
        return m;
    }

    private static int[] filled(int n, int v) {
        int[] a = new int[n];
        Arrays.fill(a, v);
        return a;
    }

    private static int[] toInts(byte[] b) {
        int[] a = new int[b.length];
        for (int i = 0; i < b.length; i++) a[i] = b[i];
        return a;
    }

    private static byte[] toBytes(int[] a) {
        byte[] b = new byte[a.length];
        for (int i = 0; i < a.length; i++) b[i] = (byte) a[i];
        return b;
    }

    // ---- codec parts -------------------------------------------------------------------------------------------

    /** The per-record arrays. */
    record Records(int[] seg, int[] at, int[] next, int[] prog, int[] goal, int[] place, int[] spot, int[] stop, int[] legs,
                   int[] role, int[] event, int[] state, int[] carry, long[] dwellUntil, long[] hurtUntil) {
        static final Codec<Records> CODEC = RecordCodecBuilder.create(i -> i.group(
                INTS.fieldOf("seg").forGetter(Records::seg), INTS.fieldOf("at").forGetter(Records::at),
                INTS.fieldOf("next").forGetter(Records::next), INTS.fieldOf("prog").forGetter(Records::prog),
                INTS.fieldOf("goal").forGetter(Records::goal), INTS.fieldOf("place").forGetter(Records::place),
                INTS.fieldOf("spot").forGetter(Records::spot), INTS.fieldOf("stop").forGetter(Records::stop),
                INTS.fieldOf("legs").forGetter(Records::legs), INTS.fieldOf("role").forGetter(Records::role),
                INTS.fieldOf("event").forGetter(Records::event), INTS.fieldOf("state").forGetter(Records::state),
                INTS.fieldOf("carry").forGetter(Records::carry), LONGS.fieldOf("dwellUntil").forGetter(Records::dwellUntil),
                LONGS.fieldOf("hurtUntil").forGetter(Records::hurtUntil)
        ).apply(i, Records::new));

        static Records of(CitizensData d) {
            return new Records(d.seg, d.at, d.next, d.prog, d.goal, d.place, d.spot, d.stop, d.legs, d.role, d.event,
                    toInts(d.state), toInts(d.carry), d.dwellUntil, d.hurtUntil);
        }

        void into(CitizensData d) {
            d.seg = seg.clone();
            d.at = at.clone();
            d.next = next.clone();
            d.prog = prog.clone();
            d.goal = goal.clone();
            d.place = place.clone();
            d.spot = spot.clone();
            d.stop = stop.clone();
            d.legs = legs.clone();
            d.role = role.clone();
            d.event = event.clone();
            d.state = toBytes(state);
            d.carry = toBytes(carry);
            d.dwellUntil = dwellUntil.clone();
            d.hurtUntil = hurtUntil.clone();
        }
    }

    /** The city's state that citizens change. */
    record World(int[] lamps, int[] stallOpen, int[] stock, int[] cloths, int[] safety, int[] gratitude, List<Repair> repairs,
                 long[] furnished, long[] bellPos, long[] chimneys) {
        static final Codec<World> CODEC = RecordCodecBuilder.create(i -> i.group(
                INTS.fieldOf("lamps").forGetter(World::lamps), INTS.fieldOf("stallOpen").forGetter(World::stallOpen),
                INTS.fieldOf("stock").forGetter(World::stock), INTS.fieldOf("cloths").forGetter(World::cloths),
                INTS.fieldOf("safety").forGetter(World::safety), INTS.fieldOf("gratitude").forGetter(World::gratitude),
                Repair.CODEC.listOf().fieldOf("repairs").forGetter(World::repairs), LONGS.fieldOf("furnished").forGetter(World::furnished),
                LONGS.fieldOf("bellPos").forGetter(World::bellPos), LONGS.fieldOf("chimneys").forGetter(World::chimneys)
        ).apply(i, World::new));

        static World of(CitizensData d) {
            return new World(toInts(d.lamps), toInts(d.stallOpen), toInts(d.stock), toInts(d.cloths), toInts(d.safety),
                    toInts(d.gratitude), List.copyOf(d.repairs), d.furnished, d.bellPos, d.chimneys);
        }

        void into(CitizensData d) {
            d.lamps = toBytes(lamps);
            d.stallOpen = toBytes(stallOpen);
            d.stock = toBytes(stock);
            d.cloths = toBytes(cloths);
            // each settlement's own district count (Carillon's eight, as schema 1 always had); never none
            d.safety = toBytes(safety.length > 0 ? safety : new int[]{80});
            d.gratitude = toBytes(gratitude.length == d.safety.length ? gratitude : Arrays.copyOf(gratitude, d.safety.length));
            d.repairs.clear();
            d.repairs.addAll(repairs);
            d.furnished = furnished.clone();
            d.bellPos = bellPos.clone();
            d.chimneys = chimneys.clone();
        }
    }
}
