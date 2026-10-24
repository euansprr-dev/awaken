package dev.awaken.terrain.client;

import dev.awaken.terrain.TerrainSounds;
import dev.awaken.terrain.Wind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Wind in the leaves (the Ambient/Environment slider), two beds around the listener:
 * <ul>
 * <li><b>rustle</b>: loud under canopy and in gusts, nothing in open land. Canopy is the share of 16 columns on two rings
 * around the camera (5 and 11 blocks) whose top is leaves above head height, two columns a tick;</li>
 * <li><b>high wind</b>: deeper wind that grows with height above sea level and with open sky, softer under trees.</li>
 * </ul>
 * Both follow {@link Wind} where the camera stands (its gusts pass over you), ease toward their level, and stop when
 * silent so they cost nothing in caves and towns.
 */
final class LeafRustle {
    private LeafRustle() {}

    private static final int RING = 16;
    private static final boolean[] CANOPY = new boolean[RING];
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
    private static int next;
    private static float canopy, open;
    private static Bed rustle, high;

    static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        Wind.Field wind = ClientWind.field();
        if (level == null || mc.player == null || wind == null || !TerrainClientConfig.leafRustle()) {
            stopAll();
            return;
        }
        Vec3 c = mc.gameRenderer.mainCamera().position();
        for (int k = 0; k < 2; k++) {
            int i = next;
            next = (next + 1) % RING;
            float a = i * (Mth.TWO_PI / 8F) + (i >= 8 ? 0.39F : 0F);
            float r = i >= 8 ? 11F : 5F;
            int x = Mth.floor(c.x + Mth.cos(a) * r), z = Mth.floor(c.z + Mth.sin(a) * r);
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            CANOPY[i] = top > c.y + 1 && top < c.y + 30 && level.getBlockState(POS.set(x, top - 1, z)).is(BlockTags.LEAVES);
        }
        int hits = 0;
        for (boolean b : CANOPY) if (b) hits++;
        canopy += (hits / (float) RING - canopy) * 0.1F;
        open += ((level.canSeeSky(POS.set(Mth.floor(c.x), Mth.floor(c.y) + 1, Mth.floor(c.z))) ? 1F : 0.15F) - open) * 0.05F;

        float gust = wind.gust(c.x, c.z);
        float strength = Wind.strength(wind.steady, gust);
        float height = Mth.clamp((float) (c.y - level.getSeaLevel() - 30) / 70F, 0F, 1F);
        float rustleGoal = canopy * (0.12F + 0.88F * strength) * (0.75F + 0.25F * gust);
        float highGoal = open * (0.12F + 0.88F * height) * (0.1F + 0.9F * strength) * (1F - 0.6F * canopy) * 0.8F;
        rustle = drive(mc, rustle, TerrainSounds.LEAF_RUSTLE.get(), rustleGoal, 0.9F + 0.2F * strength);
        high = drive(mc, high, TerrainSounds.WIND_HIGH.get(), highGoal, 0.8F + 0.25F * strength);
    }

    /** Keeps a bed playing while it has something to say, and lets it go when it falls silent. */
    private static Bed drive(Minecraft mc, Bed bed, SoundEvent event, float goal, float pitch) {
        if (bed != null && bed.isStopped()) bed = null;
        if (bed == null) {
            if (goal < 0.02F) return null;
            bed = new Bed(event);
            mc.getSoundManager().play(bed);
        }
        bed.goal = goal;
        bed.pitchGoal = pitch;
        return bed;
    }

    static void stopAll() {
        if (rustle != null) rustle.goal = 0F;
        if (high != null) high.goal = 0F;
    }

    static String status() {
        return String.format(java.util.Locale.ROOT, "canopy %.2f, open sky %.2f, rustle %.2f, high wind %.2f",
                canopy, open, rustle == null ? 0F : rustle.level, high == null ? 0F : high.level);
    }

    /** A looping bed around the listener whose volume eases toward its goal; it stops itself once silent. */
    private static final class Bed extends AbstractTickableSoundInstance {
        float goal, pitchGoal = 1F, level;

        Bed(SoundEvent event) {
            super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.001F;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            level += (goal - level) * 0.04F;
            volume = Math.max(0.001F, level);
            pitch += (pitchGoal - pitch) * 0.02F;
            if (goal < 0.01F && level < 0.01F) stop();
        }
    }
}
