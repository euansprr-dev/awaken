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
 * The bells' world state (schema 2, SPEC §43.7, §43.14): each home's tower (where its bell frame stands, how far it has
 * grown, the bells hung there from everyone who lives in it, the Great Belfry's two pieces, its Hundred Peal, Ilse's
 * visit) and each sleeping place out in the land (its bell, its kind, where it was built, whether it's awake, the keeper
 * that gathered round it); and the land's: the play-day the Deep answered the Hundred Peal, and where the Kinsong last
 * rang.
 */
final class BellWorld extends SavedData {
    static final int SCHEMA = 2;

    /** One home's tower. */
    static final class Home {
        static final Codec<Home> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.optionalFieldOf("frame", 0L).forGetter(h -> h.frame == null ? 0L : h.frame.asLong()),
                Codec.INT.optionalFieldOf("stage", 0).forGetter(h -> h.stage),
                Codec.INT.listOf().optionalFieldOf("hung", List.of()).forGetter(h -> new ArrayList<>(h.hung)),
                Codec.STRING.optionalFieldOf("wren", "").forGetter(h -> h.wren),
                Codec.BOOL.optionalFieldOf("clapper", false).forGetter(h -> h.clapper),
                Codec.BOOL.optionalFieldOf("relic", false).forGetter(h -> h.relic),
                Codec.LONG.optionalFieldOf("pealed", -1L).forGetter(h -> h.pealed),
                Codec.BOOL.optionalFieldOf("ilse", false).forGetter(h -> h.ilse)
        ).apply(i, (frame, stage, hung, wren, clapper, relic, pealed, ilse) -> {
            Home h = new Home();
            h.frame = frame == 0L ? null : BlockPos.of(frame);
            h.stage = stage;
            h.hung.addAll(hung);
            h.wren = wren;
            h.clapper = clapper;
            h.relic = relic;
            h.pealed = pealed;
            h.ilse = ilse;
            return h;
        }));

        /** The bell frame, once placed. */
        @Nullable BlockPos frame;
        /** What has been built round the frame: 0 the frame alone, 1 the belfry (20 bells), 2 the tower (50), 3 the Great Belfry. */
        int stage;
        /** The Great Belfry's two pieces, set into the frame (schema 2). */
        boolean clapper, relic;
        /** The play-day of this home's Hundred Peal, or -1 (schema 2). */
        long pealed = -1;
        /** Ilse has been to this home, after the Wake (schema 2). */
        boolean ilse;
        // not saved: when the beasts raising the Great Belfry finish it (game time), or 0
        long raising;
        /** Every bell held by anyone who lives here. */
        final Set<Integer> hung = new HashSet<>();
        /** The key of this home's near sleeping bell (the Wren Bell's place), once chosen. */
        String wren = "";
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
            Codec.LONG.optionalFieldOf("woke", -1L).forGetter(w -> w.woke)
    ).apply(i, (schema, homes, sites, deep, kinsongs, woke) -> {
        BellWorld w = new BellWorld();
        homes.forEach((k, v) -> w.homes.put(Long.parseLong(k), v));
        w.sites.putAll(sites);
        w.deepAnswered = deep;
        w.kinsongs.addAll(kinsongs);
        w.woke = woke;
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
