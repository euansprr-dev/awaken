package dev.awaken.bells;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The bells' world state (schema 5, SPEC §43.7, §43.14, §43.16): each home's tower (where its bell frame stands, how far
 * it has grown, where each of its bells hangs, the far six that answer its song, the Lampwing's old bell, the Great
 * Belfry's two pieces, its Hundred Peal, Ilse's visit), an old world's sleeping places out in the land, kept only until
 * they're cleared (SPEC §43.16); the bells gates' Lords kept, lying on their arenas' floors, and those a closed gate took
 * back (stage 4); and the land's: the play-day the Deep answered the Hundred Peal, and where the Kinsong last rang.
 */
final class BellWorld extends SavedData {
    static final int SCHEMA = 5;

    /** One home's tower. */
    static final class Home {
        private static final Codec<Map<String, Long>> SPOTS = Codec.unboundedMap(Codec.STRING, Codec.LONG);
        static final Codec<Home> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.optionalFieldOf("frame", 0L).forGetter(h -> h.frame == null ? 0L : h.frame.asLong()),
                Codec.INT.optionalFieldOf("stage", 0).forGetter(h -> h.stage),
                SPOTS.optionalFieldOf("placed", Map.of()).forGetter(h -> spots(h.placed)),
                SPOTS.optionalFieldOf("far", Map.of()).forGetter(h -> spots(h.far)),
                Codec.LONG.optionalFieldOf("roost", 0L).forGetter(h -> h.roost == null ? 0L : h.roost.asLong()),
                Codec.BOOL.optionalFieldOf("roosted", false).forGetter(h -> h.roosted),
                Codec.BOOL.optionalFieldOf("clapper", false).forGetter(h -> h.clapper),
                Codec.BOOL.optionalFieldOf("relic", false).forGetter(h -> h.relic),
                Codec.LONG.optionalFieldOf("pealed", -1L).forGetter(h -> h.pealed),
                Codec.BOOL.optionalFieldOf("ilse", false).forGetter(h -> h.ilse),
                Codec.BOOL.optionalFieldOf("beams", false).forGetter(h -> h.beams),
                SPOTS.optionalFieldOf("yard", Map.of()).forGetter(h -> spots(h.yard))
        ).apply(i, (frame, stage, placed, far, roost, roosted, clapper, relic, pealed, ilse, beams, yard) -> {
            Home h = new Home();
            h.frame = frame == 0L ? null : BlockPos.of(frame);
            h.stage = stage;
            placed.forEach((k, v) -> h.placed.put(Integer.parseInt(k), BlockPos.of(v)));
            far.forEach((k, v) -> h.far.put(Integer.parseInt(k), BlockPos.of(v)));
            h.roost = roost == 0L ? null : BlockPos.of(roost);
            h.roosted = roosted;
            h.clapper = clapper;
            h.relic = relic;
            h.pealed = pealed;
            h.ilse = ilse;
            h.beams = beams;
            yard.forEach((k, v) -> h.yard.put(Integer.parseInt(k), BlockPos.of(v)));
            h.rebuild();
            return h;
        }));

        private static Map<String, Long> spots(Map<Integer, BlockPos> m) {
            Map<String, Long> out = new HashMap<>();
            m.forEach((k, v) -> out.put(Integer.toString(k), v.asLong()));
            return out;
        }

        /** The bell frame, once placed. */
        @Nullable BlockPos frame;
        /**
         * What stands round the frame: 0 the frame alone, 1 and 2 an old belfry and tower that raised themselves (before
         * stage 3), 3 the Great Belfry.
         */
        int stage;
        /** An old belfry or tower's vanilla bells were made beams (schema 4). */
        boolean beams;
        /** Great Bells the Registry's wagon left in the yard, waiting to be lifted (schema 4): number → where. */
        final Map<Integer, BlockPos> yard = new HashMap<>();
        /** The Great Belfry's two pieces, set into the frame (schema 2). */
        boolean clapper, relic;
        /** The play-day of this home's Hundred Peal, or -1 (schema 2). */
        long pealed = -1;
        /** Ilse has been to this home, after the Wake (schema 2). */
        boolean ilse;
        // not saved: when the beasts raising the Great Belfry finish it (game time), or 0
        long raising;
        /** Where each bell hanging here hangs (schema 3): on a hook at home. */
        final Map<Integer, BlockPos> placed = new HashMap<>();
        /** The far six in this home's song (schema 3): bells people still ring, where they hang. */
        final Map<Integer, BlockPos> far = new HashMap<>();
        /** The Lampwing's old bell, while it hangs in its tree (schema 3); and whether it was ever hung. */
        @Nullable BlockPos roost;
        boolean roosted;
        /** This home's song: every bell placed here, and the far six that answer it. Derived, never saved. */
        final Set<Integer> hung = new HashSet<>();

        /** {@link #hung} again from {@link #placed} and {@link #far}. */
        void rebuild() {
            hung.clear();
            hung.addAll(placed.keySet());
            hung.addAll(far.keySet());
        }
        // not saved: the last song rung here (play-day × 2, +1 at dusk), so a restart can't ring one twice in a tick
        long sung = -1;
    }

    /** A sleeping bell's place. */
    static final class Site {
        static final int PLANNED = 0, ASLEEP = 1, AWAKE = 2, GONE = 3;
        static final Codec<Site> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("bell").forGetter(s -> s.bell),
                Codec.STRING.fieldOf("kind").forGetter(s -> s.kind.name()),
                Codec.LONG.fieldOf("pos").forGetter(s -> s.pos.asLong()),
                Codec.INT.optionalFieldOf("state", PLANNED).forGetter(s -> s.state),
                UUIDUtil.STRING_CODEC.optionalFieldOf("keeper").forGetter(s -> Optional.ofNullable(s.keeper)),
                Codec.BOOL.optionalFieldOf("painted", false).forGetter(s -> s.painted)
        ).apply(i, (bell, kind, pos, state, keeper, painted) -> {
            Site s = new Site(bell, BellSites.Kind.valueOf(kind), BlockPos.of(pos));
            s.state = state;
            s.keeper = keeper.orElse(null);
            s.painted = painted;
            return s;
        }));

        final String bell;
        final BellSites.Kind kind;
        /** Planned: the column (y 0); built: where the bell sits. */
        BlockPos pos;
        int state;
        @Nullable UUID keeper;
        /** The land round it has been woken (its grey put back to green). */
        boolean painted;

        Site(String bell, BellSites.Kind kind, BlockPos pos) {
            this.bell = bell;
            this.kind = kind;
            this.pos = pos;
        }
    }

    static final Codec<BellWorld> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(w -> SCHEMA),
            Codec.unboundedMap(Codec.STRING, Home.CODEC).optionalFieldOf("homes", Map.of()).forGetter(w -> {
                Map<String, Home> out = new HashMap<>();
                w.homes.forEach((k, v) -> out.put(Long.toString(k), v));
                return out;
            }),
            Codec.unboundedMap(Codec.STRING, Site.CODEC).optionalFieldOf("sites", Map.of()).forGetter(w -> w.sites),
            Codec.LONG.optionalFieldOf("deep_answered", -1L).forGetter(w -> w.deepAnswered),
            Song.CODEC.listOf().optionalFieldOf("kinsongs", List.of()).forGetter(w -> new ArrayList<>(w.kinsongs)),
            Codec.LONG.optionalFieldOf("woke", -1L).forGetter(w -> w.woke),
            Kept.CODEC.listOf().optionalFieldOf("kept", List.of()).forGetter(w -> new ArrayList<>(w.kept)),
            Codec.unboundedMap(Codec.STRING, Codec.INT.listOf()).optionalFieldOf("lost", Map.of()).forGetter(w -> {
                Map<String, List<Integer>> out = new HashMap<>();
                w.lost.forEach((k, v) -> out.put(k.toString(), new ArrayList<>(v)));
                return out;
            })
    ).apply(i, (schema, homes, sites, deep, kinsongs, woke, kept, lost) -> {
        BellWorld w = new BellWorld();
        homes.forEach((k, v) -> w.homes.put(Long.parseLong(k), v));
        w.sites.putAll(sites);
        w.deepAnswered = deep;
        w.kinsongs.addAll(kinsongs);
        w.woke = woke;
        w.kept.addAll(kept);
        lost.forEach((k, v) -> w.lost.put(UUID.fromString(k), new ArrayList<>(v)));
        return w;
    }));

    static final SavedDataType<BellWorld> TYPE = new SavedDataType<>(Awaken.id("bells"), BellWorld::new, CODEC);

    final Map<Long, Home> homes = new HashMap<>();
    /** Sleeping places by key: "c<cell x>,<cell z>" for the land's own, "h<anchor>" for a home's Wren Bell. */
    final Map<String, Site> sites = new HashMap<>();
    /** The play-day the Deep answered the first Hundred Peal in this world, or -1. */
    long deepAnswered = -1;
    /** The play-day the bells first heard the world awake (the Wake chosen), or -1; Ilse visits three days on. */
    long woke = -1;
    /** Where the Kinsong rang lately, newest last, at most {@link #KINSONGS} kept. */
    final List<Song> kinsongs = new ArrayList<>();
    static final int KINSONGS = 64;

    /** Bells gates' Lords kept, lying on their arenas' floors until lifted or the gate closes (stage 4, schema 5). */
    final List<Kept> kept = new ArrayList<>();
    /** Bells a closed gate took back before these hunters lifted them, said to each when next about (stage 4). */
    final Map<UUID, List<Integer>> lost = new HashMap<>();

    /** A Lord's bell on its arena's floor: which gate, where, which bell, and who hasn't lifted theirs yet. */
    static final class Kept {
        static final Codec<Kept> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("gate").forGetter(k -> k.gate),
                Codec.LONG.fieldOf("pos").forGetter(k -> k.pos.asLong()),
                Codec.INT.fieldOf("no").forGetter(k -> k.no),
                UUIDUtil.STRING_CODEC.listOf().fieldOf("left").forGetter(k -> new ArrayList<>(k.left))
        ).apply(i, (gate, pos, no, left) -> new Kept(gate, BlockPos.of(pos), no, left)));

        final String gate;
        final BlockPos pos;
        final int no;
        final Set<UUID> left = new HashSet<>();

        Kept(String gate, BlockPos pos, int no, List<UUID> left) {
            this.gate = gate;
            this.pos = pos;
            this.no = no;
            this.left.addAll(left);
        }
    }

    /** One Kinsong: where (x, z) and on which play-day. */
    record Song(int x, int z, long day) {
        static final Codec<Song> CODEC = RecordCodecBuilder.create(i -> i.group(Codec.INT.fieldOf("x").forGetter(Song::x),
                Codec.INT.fieldOf("z").forGetter(Song::z), Codec.LONG.fieldOf("day").forGetter(Song::day)).apply(i, Song::new));
    }

    static BellWorld get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    Home home(BlockPos anchor) {
        return homes.computeIfAbsent(anchor.asLong(), k -> new Home());
    }

    @Nullable Home peek(BlockPos anchor) {
        return homes.get(anchor.asLong());
    }
}
