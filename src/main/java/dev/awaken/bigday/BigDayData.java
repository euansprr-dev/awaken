package dev.awaken.bigday;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Big Days' world state (schema 1, SPEC §43.8): per home, when its last Big Day was, the next raid's play-day once one is
 * set, how many it has held, the raid in hand (warned, or tonight's), and what a raid broke that's still being put right.
 */
final class BigDayData extends SavedData {
    static final int SCHEMA = 1;

    /** A block a raid broke while nobody was home, put back by the mend. */
    record Broken(BlockPos pos, BlockState state) {
        static final Codec<Broken> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Broken::pos),
                BlockState.CODEC.fieldOf("state").forGetter(Broken::state)
        ).apply(i, Broken::new));
    }

    /** One raid: warned the day before, fought (or not) the night of {@code day}. */
    static final class Raid {
        static final int WARNED = 0, NIGHT = 1, AWAY = 2;
        static final Codec<Raid> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("owner").forGetter(r -> r.owner),
                Codec.LONG.fieldOf("day").forGetter(r -> r.day),
                Codec.BOOL.fieldOf("first").forGetter(r -> r.first),
                BlockPos.CODEC.fieldOf("breach").forGetter(r -> r.breach),
                Codec.STRING.fieldOf("theme").forGetter(r -> r.theme),
                Codec.INT.fieldOf("level").forGetter(r -> r.level),
                Codec.INT.fieldOf("phase").forGetter(r -> r.phase),
                Codec.INT.fieldOf("wave").forGetter(r -> r.wave),
                Codec.BOOL.fieldOf("lord_down").forGetter(r -> r.lordDown),
                Codec.LONG.optionalFieldOf("fault_at", -1L).forGetter(r -> r.faultAt)
        ).apply(i, Raid::new));

        final UUID owner;
        final long day;
        final boolean first;
        final BlockPos breach;
        final String theme;
        final int level;
        int phase, wave;
        boolean lordDown;
        long faultAt, nextWaveAt;
        // not saved: a restart mid-night counts the raid's creatures again by their tag
        boolean burst, lordOut;
        final List<UUID> stream = new ArrayList<>();

        Raid(UUID owner, long day, boolean first, BlockPos breach, String theme, int level, int phase, int wave, boolean lordDown, long faultAt) {
            this.owner = owner;
            this.day = day;
            this.first = first;
            this.breach = breach;
            this.theme = theme;
            this.level = level;
            this.phase = phase;
            this.wave = wave;
            this.lordDown = lordDown;
            this.faultAt = faultAt;
            this.lordOut = wave > BigDays.WAVES.length;
        }
    }

    /** One home's Big Days. */
    static final class Home {
        static final Codec<Home> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.optionalFieldOf("last", -99L).forGetter(h -> h.last),
                Codec.LONG.optionalFieldOf("next", -1L).forGetter(h -> h.next),
                Codec.INT.optionalFieldOf("raids", 0).forGetter(h -> h.raids),
                Codec.BOOL.optionalFieldOf("ready", false).forGetter(h -> h.ready),
                Raid.CODEC.optionalFieldOf("raid").forGetter(h -> Optional.ofNullable(h.raid)),
                Broken.CODEC.listOf().optionalFieldOf("mending", List.of()).forGetter(h -> h.mending)
        ).apply(i, (last, next, raids, ready, raid, mending) -> {
            Home h = new Home();
            h.last = last;
            h.next = next;
            h.raids = raids;
            h.ready = ready;
            h.raid = raid.orElse(null);
            h.mending.addAll(mending);
            return h;
        }));

        long last = -99, next = -1;
        int raids;
        /** Its owner holds the first Great Bell (D rank, until the Ringer gives the Tenor). */
        boolean ready;
        @Nullable Raid raid;
        final List<Broken> mending = new ArrayList<>();
    }

    static final Codec<BigDayData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(d -> SCHEMA),
            Codec.unboundedMap(Codec.STRING, Home.CODEC).optionalFieldOf("homes", Map.of()).forGetter(d -> {
                Map<String, Home> out = new HashMap<>();
                d.homes.forEach((k, v) -> out.put(Long.toString(k), v));
                return out;
            })
    ).apply(i, (schema, homes) -> {
        BigDayData d = new BigDayData();
        homes.forEach((k, v) -> d.homes.put(Long.parseLong(k), v));
        return d;
    }));

    static final SavedDataType<BigDayData> TYPE = new SavedDataType<>(Awaken.id("big_days"), BigDayData::new, CODEC);

    final Map<Long, Home> homes = new HashMap<>();

    static BigDayData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    Home home(BlockPos anchor) {
        return homes.computeIfAbsent(anchor.asLong(), k -> new Home());
    }

    @Nullable Home peek(BlockPos anchor) {
        return homes.get(anchor.asLong());
    }
}
