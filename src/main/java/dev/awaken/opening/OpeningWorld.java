package dev.awaken.opening;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The opening as the world keeps it ({@code awaken:opening}, schemaVersion 1): the story's gate in Choir Square for the
 * group running now, the group's clock (the first arrival, the cinematic), whether the Bell Bridge is already in the
 * river (world state: it stays down) and which version of the stage (the run's furniture, the infirmary and the
 * Registry) is built.
 */
public final class OpeningWorld extends SavedData {
    public static final int SCHEMA = 1;

    /** The opening's Bell Crypt in the square ("" none yet). */
    String gate = "";
    /** The group's first arrival in the square, and the moment its cinematic began (game time, -1: not yet). */
    long firstArrival = -1, cinematicAt = -1;
    /** When the market emptied toward the runners (-1 not yet in this group's run). */
    long rushAt = -1;
    boolean bridgeDown;
    /** The stage's version built into this world (0: none). */
    int stage;
    /** The world's first dawn was set (a new Carillon world opens at first light). */
    boolean dawnSet;

    public static final Codec<OpeningWorld> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(w -> SCHEMA),
            Codec.STRING.optionalFieldOf("gate", "").forGetter(w -> w.gate),
            Codec.LONG.optionalFieldOf("firstArrival", -1L).forGetter(w -> w.firstArrival),
            Codec.LONG.optionalFieldOf("cinematicAt", -1L).forGetter(w -> w.cinematicAt),
            Codec.LONG.optionalFieldOf("rushAt", -1L).forGetter(w -> w.rushAt),
            Codec.BOOL.optionalFieldOf("bridgeDown", false).forGetter(w -> w.bridgeDown),
            Codec.INT.optionalFieldOf("stage", 0).forGetter(w -> w.stage),
            Codec.BOOL.optionalFieldOf("dawnSet", false).forGetter(w -> w.dawnSet)
    ).apply(i, (schema, gate, first, cine, rush, bridge, stage, dawn) -> {
        OpeningWorld w = new OpeningWorld();
        w.gate = gate;
        w.firstArrival = first;
        w.cinematicAt = cine;
        w.rushAt = rush;
        w.bridgeDown = bridge;
        w.stage = stage;
        w.dawnSet = dawn;
        return w;
    }));

    public static final SavedDataType<OpeningWorld> TYPE = new SavedDataType<>(Awaken.id("opening"), OpeningWorld::new, CODEC);

    public static OpeningWorld get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean bridgeDown() {
        return bridgeDown;
    }

    /** A group's run is over (everyone went through): the next group gets its own clock and its own crowd. */
    void newGroup() {
        firstArrival = -1;
        cinematicAt = -1;
        rushAt = -1;
        setDirty();
    }
}
