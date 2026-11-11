package dev.awaken.kin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The Hearth's family as the world remembers it (SPEC §69.6; schema 1), by the Hearth's owner: for each of the two, its
 * body, its name, whether it is at home, with someone or waiting somewhere, and whether it has arrived. The record comes
 * first and the body second: a body that is lost is made again from this, and a body this does not name removes itself,
 * so neither can be lost and neither can be two.
 */
public final class FamilyWorld extends SavedData {
    public static final int SCHEMA = 1;
    /** At home; out with someone; told to wait where it is; waiting at a door its person went through (it rejoins them by itself). */
    public static final int HOME = 0, WITH = 1, WAITING = 2, DOOR = 3;

    /** One of the two. */
    public static final class One {
        /** Its body, when it has one in the world. */
        public Optional<UUID> id;
        public String name;
        public int state;
        /** Who it is out with, or waiting for. */
        public Optional<UUID> with;
        public Optional<BlockPos> waitAt;
        /** When a wait ends by itself (game time; 0 for none). */
        public long waitUntil;
        public boolean arrived;

        One(Optional<UUID> id, String name, int state, Optional<UUID> with, Optional<BlockPos> waitAt, long waitUntil, boolean arrived) {
            this.id = id;
            this.name = name;
            this.state = state;
            this.with = with;
            this.waitAt = waitAt;
            this.waitUntil = waitUntil;
            this.arrived = arrived;
        }

        One(String name) {
            this(Optional.empty(), name, HOME, Optional.empty(), Optional.empty(), 0L, false);
        }

        static final Codec<One> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.optionalFieldOf("id").forGetter(o -> o.id),
                Codec.STRING.optionalFieldOf("name", "").forGetter(o -> o.name),
                Codec.INT.optionalFieldOf("state", HOME).forGetter(o -> o.state),
                UUIDUtil.STRING_CODEC.optionalFieldOf("with").forGetter(o -> o.with),
                BlockPos.CODEC.optionalFieldOf("wait_at").forGetter(o -> o.waitAt),
                Codec.LONG.optionalFieldOf("wait_until", 0L).forGetter(o -> o.waitUntil),
                Codec.BOOL.optionalFieldOf("arrived", false).forGetter(o -> o.arrived)
        ).apply(i, One::new));

        /** Home, with nobody, waiting nowhere. */
        public void home() {
            state = HOME;
            with = Optional.empty();
            waitAt = Optional.empty();
            waitUntil = 0;
        }
    }

    /** One Hearth's two. */
    public static final class Home {
        public final One aya, dubu;
        /** When the family's clock first saw this Hearth (game time): they set out half a minute later. */
        public long seen;
        /** How far the arrival has got: 0 not yet, 1 Dubu is on his way, 2 Aya is, 3 both are home. */
        public int stage;
        /** When the arrival's next step is due. */
        public long next;
        /** A bed someone of the household has slept in here: where Aya sleeps. */
        public Optional<BlockPos> bed;

        Home(One aya, One dubu, long seen, int stage, long next, Optional<BlockPos> bed) {
            this.aya = aya;
            this.dubu = dubu;
            this.seen = seen;
            this.stage = stage;
            this.next = next;
            this.bed = bed;
        }

        Home(long seen) {
            this(new One(FamilyKind.AYA.name), new One(FamilyKind.DUBU.name), seen, 0, 0L, Optional.empty());
        }

        static final Codec<Home> CODEC = RecordCodecBuilder.create(i -> i.group(
                One.CODEC.fieldOf("aya").forGetter(h -> h.aya),
                One.CODEC.fieldOf("dubu").forGetter(h -> h.dubu),
                Codec.LONG.optionalFieldOf("seen", 0L).forGetter(h -> h.seen),
                Codec.INT.optionalFieldOf("stage", 0).forGetter(h -> h.stage),
                Codec.LONG.optionalFieldOf("next", 0L).forGetter(h -> h.next),
                BlockPos.CODEC.optionalFieldOf("bed").forGetter(h -> h.bed)
        ).apply(i, Home::new));

        public One one(FamilyKind k) {
            return k == FamilyKind.DUBU ? dubu : aya;
        }
    }

    final Map<UUID, Home> homes = new HashMap<>();
    /** Players who have asked one of them along (the Tasks row is done), and who have had Dubu's hint at the yard's edge. */
    final Set<UUID> asked = new HashSet<>(), hinted = new HashSet<>();

    public static final Codec<FamilyWorld> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(w -> SCHEMA),
            Codec.unboundedMap(Codec.STRING, Home.CODEC).optionalFieldOf("homes", Map.of()).forGetter(w -> {
                Map<String, Home> m = new HashMap<>();
                w.homes.forEach((k, v) -> m.put(k.toString(), v));
                return m;
            }),
            UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("asked", List.of()).forGetter(w -> List.copyOf(w.asked)),
            UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("hinted", List.of()).forGetter(w -> List.copyOf(w.hinted))
    ).apply(i, (schema, homes, asked, hinted) -> {
        FamilyWorld w = new FamilyWorld();
        homes.forEach((k, v) -> {
            try {
                w.homes.put(UUID.fromString(k), v);
            } catch (IllegalArgumentException ignored) {
                // a key that isn't an id is dropped
            }
        });
        w.asked.addAll(asked);
        w.hinted.addAll(hinted);
        return w;
    }));

    public static final SavedDataType<FamilyWorld> TYPE = new SavedDataType<>(Awaken.id("family"), FamilyWorld::new, CODEC);

    public static FamilyWorld get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** The record of the Hearth this player owns, if the family's clock has seen it. */
    public @org.jspecify.annotations.Nullable Home of(UUID owner) {
        return homes.get(owner);
    }

    Home ensure(UUID owner, long now) {
        Home h = homes.get(owner);
        if (h == null) {
            h = new Home(now);
            homes.put(owner, h);
            setDirty();
        }
        return h;
    }
}
