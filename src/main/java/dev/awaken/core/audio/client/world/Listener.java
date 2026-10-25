package dev.awaken.core.audio.client.world;

import dev.awaken.core.audio.world.Families;
import dev.awaken.terrain.Wind;
import dev.awaken.terrain.client.ClientWind;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;

/**
 * What the world around the listener is like, for the Conductor: a ring of {@value #COLUMNS} columns on three circles
 * (5, 11 and 20 blocks) refreshed three a tick, each with its top block's kind and height and its biome's family;
 * open sky over the head, water around the camera, the wind (terrain's shared {@link Wind} field), height, rain and
 * the time of day. The family weights ease over about ten seconds, so a biome edge is a slow blend, never a switch.
 * Allocation-free per tick.
 */
final class Listener {
    static final int COLUMNS = 24, INNER = 16;
    static final int KIND_NONE = 0, KIND_LEAVES = 1, KIND_GROUND = 2, KIND_WATER = 3;
    private static final int PER_TICK = 3;
    private static final float[] RADIUS = {5F, 11F, 20F};

    // the ring, refreshed a few columns a tick
    final int[] kind = new int[COLUMNS];
    final int[] colX = new int[COLUMNS], colZ = new int[COLUMNS], colTop = new int[COLUMNS];
    final boolean[] canopyCol = new boolean[COLUMNS];
    final byte[] family = new byte[COLUMNS + 1];
    private int next;

    // the state the Conductor reads
    final float[] familyWeight = new float[Families.COUNT];
    private final float[] familyTarget = new float[Families.COUNT];
    double x, y, z;
    float canopy, open, underwater, rain, thunder, strength, gust, height;
    long dayTime;
    boolean valid;
    /** The camera's biome (the place soundscape's key). */
    net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome;

    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    void reset() {
        valid = false;
        java.util.Arrays.fill(familyWeight, 0F);
        java.util.Arrays.fill(family, (byte) -1);
        java.util.Arrays.fill(kind, KIND_NONE);
        canopy = open = underwater = 0F;
        biome = null;
        next = 0;
    }

    void update(Minecraft mc, ClientLevel level) {
        Camera cam = mc.gameRenderer.mainCamera();
        Vec3 c = cam.position();
        boolean fresh = !valid;
        x = c.x;
        y = c.y;
        z = c.z;
        for (int k = 0; k < (fresh ? COLUMNS : PER_TICK); k++) {
            sample(level, next);
            next = (next + 1) % COLUMNS;
        }
        biome = level.getBiome(pos.set(Mth.floor(x), Mth.floor(y), Mth.floor(z)));
        family[COLUMNS] = (byte) Families.of(biome);

        int hits = 0;
        for (int i = 0; i < INNER; i++) if (canopyCol[i]) hits++;
        float canopyGoal = hits / (float) INNER;
        float openGoal = level.canSeeSky(pos.set(Mth.floor(x), Mth.floor(y) + 1, Mth.floor(z))) ? 1F : 0.15F;
        float waterGoal = cam.getFluidInCamera() == FogType.WATER ? 1F : 0F;
        canopy = fresh ? canopyGoal : canopy + (canopyGoal - canopy) * 0.1F;
        open = fresh ? openGoal : open + (openGoal - open) * 0.05F;
        underwater = fresh ? waterGoal : underwater + (waterGoal - underwater) * 0.25F;

        // the families: the camera's biome counts four columns' worth, so the ground you stand on leads
        java.util.Arrays.fill(familyTarget, 0F);
        float total = 0F;
        for (int i = 0; i <= COLUMNS; i++) {
            int f = family[i];
            float w = i == COLUMNS ? 4F : 1F;
            if (f >= 0) familyTarget[f] += w;
            total += w;
        }
        for (int f = 0; f < Families.COUNT; f++) {
            float goal = familyTarget[f] / total;
            familyWeight[f] = fresh ? goal : familyWeight[f] + (goal - familyWeight[f]) * 0.008F;
        }

        Wind.Field wind = ClientWind.field();
        gust = wind == null ? 0F : wind.gust(x, z);
        strength = wind == null ? 0F : Wind.strength(wind.steady, gust);
        height = Mth.clamp((float) (y - level.getSeaLevel() - 30) / 70F, 0F, 1F);
        rain = level.getRainLevel(1F);
        thunder = level.getThunderLevel(1F);
        dayTime = level.getOverworldClockTime();
        valid = true;
    }

    private void sample(ClientLevel level, int i) {
        int ring = i < 8 ? 0 : i < 16 ? 1 : 2;
        float a = (i % 8) * (Mth.TWO_PI / 8F) + ring * 0.39F;
        int cx = Mth.floor(x + Mth.cos(a) * RADIUS[ring]), cz = Mth.floor(z + Mth.sin(a) * RADIUS[ring]);
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, cx, cz);
        BlockState st = level.getBlockState(pos.set(cx, top - 1, cz));
        colX[i] = cx;
        colZ[i] = cz;
        colTop[i] = top;
        boolean leaves = st.is(BlockTags.LEAVES);
        kind[i] = top <= level.getMinY() ? KIND_NONE : leaves ? KIND_LEAVES : !st.getFluidState().isEmpty() ? KIND_WATER : KIND_GROUND;
        canopyCol[i] = leaves && top > y + 1 && top < y + 30;
        family[i] = (byte) Families.of(level.getBiome(pos.set(cx, Mth.clamp(top, level.getMinY(), level.getMaxY()), cz)));
    }

    /** Leaves in the wind: canopy × (0.12 + 0.88 strength) × (0.75 + 0.25 gust) (as terrain's leaf rustle had it). */
    float rustle() {
        return canopy * (0.12F + 0.88F * strength) * (0.75F + 0.25F * gust);
    }

    /** Wind that grows with height and open sky and softens under trees. */
    float highWind() {
        return open * (0.12F + 0.88F * height) * (0.1F + 0.9F * strength) * (1F - 0.6F * canopy) * 0.8F;
    }
}
