package dev.awaken.caves.client;

import dev.awaken.caves.CaveRegion;
import dev.awaken.caves.CavesModule;
import dev.awaken.caves.skin.Drips;
import dev.awaken.core.audio.client.Soundscape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The cave region round the camera (SPEC §42.7), read from its biome once a second, and what the region adds to the air
 * and the ear: the Rootways' spores and pollen drifting in your light and its old roots creaking overhead; the Ochre
 * Deep's warm dust, its rock ticking as it settles, the chime crickets' chorus off in the dark and a warm hum under it
 * all. Client only; nothing saved or sent. The motes follow the {@code cave_particles} setting (and the game's own
 * particle level); the sounds sit under the Soundscape's nature level and its hush.
 */
final class RegionClient {
    private RegionClient() {}

    /** The region at the camera (null: none), for the motes, the sounds and the underlife. */
    static CaveRegion here;
    /** Motes tried a tick in the box round the camera (20 x 10 x 20), and the share of the Rootways' that are pollen. */
    static final int MOTES = 2;
    static final float POLLEN = 0.25F;
    /** Ticks between the regions' sounds (at least, then up to as much again). */
    static final int CREAK = 500, TICK = 140, CHORUS = 520;
    static final float HUM = 0.45F;

    private static final RandomSource R = RandomSource.create();
    private static final BlockPos.MutableBlockPos M = new BlockPos.MutableBlockPos();
    private static long nextCreak, nextTick, nextChorus;
    private static Loop hum;

    static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused()) return;
        long now = level.getGameTime();
        Vec3 c = mc.gameRenderer.mainCamera().position();
        if (now % 20 == 0) here = level.dimension() == Level.OVERWORLD ? at(level.getBiome(BlockPos.containing(c))) : null;
        hum(mc);
        if (here == null) return;
        if (Drips.enabled) motes(level, c);
        sounds(mc, level, c, now);
    }

    static CaveRegion at(Holder<Biome> biome) {
        for (CaveRegion r : CaveRegion.values()) if (biome.is(r.biome())) return r;
        return null;
    }

    static void clear() {
        here = null;
        if (hum != null) hum.goal = 0F;
    }

    private static void motes(ClientLevel level, Vec3 c) {
        for (int i = 0; i < MOTES; i++) {
            double x = c.x + (R.nextDouble() - 0.5) * 20, y = c.y + (R.nextDouble() - 0.5) * 10, z = c.z + (R.nextDouble() - 0.5) * 20;
            if (!level.getBlockState(M.set(x, y, z)).isAir()) continue;
            SimpleParticleType p = here == CaveRegion.ROOTWAYS ? (R.nextFloat() < POLLEN ? Drips.POLLEN.get() : Drips.SPORE.get())
                    : here == CaveRegion.OCHRE_DEEP ? Drips.WARM_DUST.get() : null;
            if (p != null) level.addParticle(p, x, y, z, 0, 0, 0);
        }
    }

    private static void sounds(Minecraft mc, ClientLevel level, Vec3 c, long now) {
        if (here == CaveRegion.ROOTWAYS) {
            if (now >= nextCreak) {
                nextCreak = now + CREAK + R.nextInt(CREAK);
                // from the roof somewhere over and round you
                int x = Mth.floor(c.x) + R.nextInt(21) - 10, z = Mth.floor(c.z) + R.nextInt(21) - 10, y = Mth.floor(c.y);
                for (int dy = 1; dy < 12; dy++) {
                    if (!level.getBlockState(M.set(x, y + dy, z)).isAir()) {
                        play(mc, CavesModule.ROOT_CREAK.get(), x + 0.5, y + dy, z + 0.5, 0.75F, 0.85F + R.nextFloat() * 0.25F);
                        break;
                    }
                }
            }
        } else if (here == CaveRegion.OCHRE_DEEP) {
            if (now >= nextTick) {
                nextTick = now + TICK + R.nextInt(TICK);
                double a = R.nextDouble() * Mth.TWO_PI, d = 3 + R.nextDouble() * 9;
                play(mc, CavesModule.ROCK_TICK.get(), c.x + Math.cos(a) * d, c.y + R.nextDouble() * 4 - 1, c.z + Math.sin(a) * d,
                        0.6F, 0.9F + R.nextFloat() * 0.3F);
            }
            if (now >= nextChorus) {
                nextChorus = now + CHORUS + R.nextInt(CHORUS);
                double a = R.nextDouble() * Mth.TWO_PI, d = 10 + R.nextDouble() * 10;
                play(mc, CavesModule.CHIME_CRICKET.get(), c.x + Math.cos(a) * d, c.y - 2 + R.nextDouble() * 4, c.z + Math.sin(a) * d,
                        0.7F, 0.95F + R.nextFloat() * 0.1F);
            }
        }
    }

    private static void play(Minecraft mc, SoundEvent event, double x, double y, double z, float volume, float pitch) {
        float hush = Soundscape.hushAt(x, y, z) * Mth.clamp(Soundscape.natureLevel(), 0F, 1F);
        if (hush < 0.2F) return;
        mc.getSoundManager().play(new SimpleSoundInstance(event, SoundSource.AMBIENT, volume * hush, pitch, R, x, y, z));
    }

    /** The Ochre Deep's warm hum round the listener: eased in over a few seconds as you enter, out as you leave. */
    private static void hum(Minecraft mc) {
        float goal = here == CaveRegion.OCHRE_DEEP ? HUM * Mth.clamp(Soundscape.natureLevel(), 0F, 1F) : 0F;
        if (hum != null && hum.isStopped()) hum = null;
        if (hum == null) {
            if (goal < 0.01F) return;
            hum = new Loop(CavesModule.WARM_HUM.get());
            mc.getSoundManager().play(hum);
        }
        hum.goal = goal;
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        float goal, level;

        Loop(SoundEvent event) {
            super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            looping = true;
            delay = 0;
            attenuation = SoundInstance.Attenuation.NONE;
            relative = true;
            volume = 0.001F;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            level += (goal - level) * 0.015F;
            volume = Math.max(0.001F, level);
            if (goal < 0.004F && level < 0.004F) stop();
        }
    }
}
