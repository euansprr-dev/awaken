package dev.awaken.trails;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What a player has opened, finished, hidden and followed (docs/trails/PLAN.md §6). Saved (schemaVersion 1), kept through
 * death, per player. {@code latched} holds "trail|step" for every step seen done, so breaking a room later does not un-tick it.
 */
public final class TrailState {
    public static final int SCHEMA = 1;

    final Set<String> opened = new LinkedHashSet<>(), finished = new LinkedHashSet<>(), hidden = new LinkedHashSet<>(), latched = new LinkedHashSet<>();
    String followed = "";
    /** The play-day the last new trail was announced, and how many that day (at most two are announced a day). */
    long newDay = -1;
    int newToday;

    static TrailState fresh() {
        return new TrailState();
    }

    static final MapCodec<TrailState> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(s -> SCHEMA),
            Codec.STRING.listOf().optionalFieldOf("opened", List.of()).forGetter(s -> new ArrayList<>(s.opened)),
            Codec.STRING.listOf().optionalFieldOf("finished", List.of()).forGetter(s -> new ArrayList<>(s.finished)),
            Codec.STRING.listOf().optionalFieldOf("hidden", List.of()).forGetter(s -> new ArrayList<>(s.hidden)),
            Codec.STRING.listOf().optionalFieldOf("latched", List.of()).forGetter(s -> new ArrayList<>(s.latched)),
            Codec.STRING.optionalFieldOf("followed", "").forGetter(s -> s.followed),
            Codec.LONG.optionalFieldOf("newDay", -1L).forGetter(s -> s.newDay),
            Codec.INT.optionalFieldOf("newToday", 0).forGetter(s -> s.newToday)
    ).apply(i, (v, op, fi, hi, la, fo, nd, nt) -> {
        TrailState s = new TrailState();
        s.opened.addAll(op);
        s.finished.addAll(fi);
        s.hidden.addAll(hi);
        s.latched.addAll(la);
        s.followed = fo;
        s.newDay = nd;
        s.newToday = nt;
        return s;
    }));

    public static TrailState of(ServerPlayer p) {
        return p.getData(TrailsContent.STATE);
    }

    void save(ServerPlayer p) {
        p.setData(TrailsContent.STATE, this);
    }

    public static String key(String trail, String step) {
        return trail + "|" + step;
    }
}
