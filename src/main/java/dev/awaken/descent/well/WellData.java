package dev.awaken.descent.well;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Where the Well is (world-global SavedData). It is placed once per world and never moves. */
public final class WellData extends SavedData {
    public static final int NONE = 0, CHOSEN = 1, BUILT = 2;

    public static final Codec<WellData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schema", 1).forGetter(d -> 1),
            Codec.INT.fieldOf("state").forGetter(d -> d.state),
            Codec.INT.fieldOf("x").forGetter(d -> d.x),
            Codec.INT.fieldOf("y").forGetter(d -> d.y),
            Codec.INT.fieldOf("z").forGetter(d -> d.z),
            Codec.INT.optionalFieldOf("bottom", 0).forGetter(d -> d.bottom),
            Codec.INT.optionalFieldOf("spawn_x", 0).forGetter(d -> d.spawnX),
            Codec.INT.optionalFieldOf("spawn_z", 0).forGetter(d -> d.spawnZ),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("guardian_times", java.util.Map.of()).forGetter(d -> java.util.Map.copyOf(d.guardianTimes)),
            Codec.INT.optionalFieldOf("style", 1).forGetter(d -> d.style)
    ).apply(i, (schema, state, x, y, z, bottom, sx, sz, times, style) -> {
        WellData d = new WellData(state, x, y, z, bottom);
        d.spawnX = sx;
        d.spawnZ = sz;
        d.guardianTimes.putAll(times);
        d.style = style;
        return d;
    }));

    public static final SavedDataType<WellData> TYPE = new SavedDataType<>(Awaken.id("descent_well"), WellData::new, CODEC);

    public int state;
    /** Which look the Well was built in ({@link Well#STYLE}); an older look is rebuilt in place on start. */
    public int style = 1;
    public int x, y, z, bottom;
    /** World spawn when the site was chosen (the Well is 400-700 blocks from it). */
    public int spawnX, spawnZ;
    /** Game time each stratum's Guardian last woke (key = depth), so the respawn cooldown survives restarts. */
    public final java.util.Map<String, Long> guardianTimes = new java.util.HashMap<>();

    public WellData() {}

    private WellData(int state, int x, int y, int z, int bottom) {
        this.state = state;
        this.x = x;
        this.y = y;
        this.z = z;
        this.bottom = bottom;
    }

    public static WellData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean built() {
        return state == BUILT;
    }

    public BlockPos center() {
        return new BlockPos(x, y, z);
    }

    /** Standing spot on the ring beside the lift, where climbing out puts you. */
    public BlockPos rim() {
        return new BlockPos(x + Well.RING_INNER + 4, y + 1, z + 1);
    }
}
