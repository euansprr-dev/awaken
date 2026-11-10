package dev.awaken.gates.realms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** What has happened in one Realm (SPEC §50.7), kept with the gate and deleted when it's released. Sets of site indexes. */
public final class RealmState {
    public static final int SCHEMA = 1;

    public final Set<Integer> wardensDown = new LinkedHashSet<>();
    public final Set<Integer> densCleared = new LinkedHashSet<>();
    public final Set<Integer> findsTaken = new LinkedHashSet<>();
    public final Set<Integer> waystonesFound = new LinkedHashSet<>();
    /** Which sites have been woken (their monsters are in the world or dead). */
    public final Set<String> woken = new LinkedHashSet<>();
    public boolean built, heartOpen, lordWoken;
    /** The door has closed behind whoever's fighting the Lord. */
    public boolean doorShut;
    /** How far the Lord's hall has changed in this fight: 0 not yet, then each stage in turn. */
    public int heartStage;
    /** When the bell next tolls and when the doors lift again, in game time; not saved, a restart just starts the count over. */
    public long tollAt, tollOpenAt;
    /** When the salt next turns, and which way it's gone; not saved, the crust is laid out hard and a restart only begins the count again. */
    public long saltAt;
    public boolean saltSoft, saltTold, saltHardTold;
    public boolean waxTold;
    public long rootsAt;
    public long[] rootOpen;
    public boolean[] rootShown;
    public boolean rootsTold;
    public final java.util.Map<java.util.UUID, net.minecraft.world.phys.Vec3> rootLast = new java.util.HashMap<>();
    public final java.util.Map<java.util.UUID, Integer> rootStill = new java.util.HashMap<>();
    /** Whether this gate's bell has rung once yet. */
    public boolean tolled;
    /** How much honey the wax-folk have sold in this Realm: they only have so much to spare. */
    public int folkTrades;

    public static final Codec<RealmState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("schemaVersion").forGetter(s -> SCHEMA),
            Codec.INT.listOf().fieldOf("wardens").forGetter(s -> List.copyOf(s.wardensDown)),
            Codec.INT.listOf().fieldOf("dens").forGetter(s -> List.copyOf(s.densCleared)),
            Codec.INT.listOf().fieldOf("finds").forGetter(s -> List.copyOf(s.findsTaken)),
            Codec.INT.listOf().fieldOf("waystones").forGetter(s -> List.copyOf(s.waystonesFound)),
            Codec.STRING.listOf().fieldOf("woken").forGetter(s -> List.copyOf(s.woken)),
            Codec.BOOL.fieldOf("built").forGetter(s -> s.built),
            Codec.BOOL.fieldOf("heart_open").forGetter(s -> s.heartOpen),
            Codec.BOOL.fieldOf("lord_woken").forGetter(s -> s.lordWoken),
            Codec.BOOL.optionalFieldOf("door_shut", false).forGetter(s -> s.doorShut),
            Codec.INT.optionalFieldOf("heart_stage", 0).forGetter(s -> s.heartStage),
            Codec.INT.optionalFieldOf("folk_trades", 0).forGetter(s -> s.folkTrades)
    ).apply(i, (v, w, d, f, ws, wk, b, h, l, ds, hs, ft) -> {
        RealmState s = new RealmState();
        s.wardensDown.addAll(w);
        s.densCleared.addAll(d);
        s.findsTaken.addAll(f);
        s.waystonesFound.addAll(ws);
        s.woken.addAll(wk);
        s.built = b;
        s.heartOpen = h;
        s.lordWoken = l;
        s.doorShut = ds;
        s.heartStage = hs;
        s.folkTrades = ft;
        return s;
    }));
}
