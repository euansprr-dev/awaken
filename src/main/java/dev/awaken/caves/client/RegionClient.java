package dev.awaken.caves.client;

import dev.awaken.caves.CaveRegion;
import dev.awaken.caves.CavesModule;
import dev.awaken.caves.skin.Drips;
import dev.awaken.caves.skin.CaveSkin;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The cave region round the camera (SPEC §42.7, §42.8), read from its biome once a second, and what the region adds to
 * the air and the ear: the Rootways' spores and pollen drifting in your light and its old roots creaking overhead; the
 * Ochre Deep's warm dust, its rock ticking as it settles, the chime crickets' chorus off in the dark and a warm hum under
 * it all; the Pale Halls' mist over still water and drips echoing into pools; the Windcleft's mica glinting on the walls,
 * the wind moaning in the cracks and pebbles falling; the Drowned Galleries' mist and the lakes lapping; the Glimmer
 * Hollows' glittering dust and its crystals ringing as you pass; the Black Vaults' ash and a deep rumble far off; the
 * Violet Deep's rising seep and its hum. Client only; nothing saved or sent. The motes follow the {@code cave_particles}
 * setting (and the game's own particle level); the sounds sit under the Soundscape's nature level and its hush.
 */
final class RegionClient {
    private RegionClient() {}

    /** The region at the camera (null: none), for the motes, the sounds and the underlife. */
    static CaveRegion here;
    /** Motes tried a tick in the box round the camera (20 x 10 x 20), and the share of the Rootways' that are pollen. */
    static final int MOTES = 2;
    static final float POLLEN = 0.25F;
    /** Ticks between the regions' sounds (at least, then up to as much again). */
    static final int CREAK = 500, TICK = 140, CHORUS = 520, POOL = 90, PEBBLES = 400, RING = 30, RUMBLE = 700;
    /** The beds' loudness: the Ochre Deep's hum, the Windcleft's wind, the Drowned Galleries' lapping, the Violet Deep's hum. */
    static final float HUM = 0.45F, WIND = 0.5F, LAP = 0.4F, VIOLET = 0.4F;
    /** How far a Glimmer Hollows crystal rings from you, and how fast you must be moving (blocks a tick) to set one off. */
    static final int RING_REACH = 6;
    static final double RING_SPEED = 0.08;

    private static final RandomSource R = RandomSource.create();
    private static final BlockPos.MutableBlockPos M = new BlockPos.MutableBlockPos();
    private static long nextCreak, nextTick, nextChorus, nextPool, nextPebbles, nextRing, nextRumble;
    private static double lastX, lastY, lastZ;
    private static Loop bed;

    static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused()) return;
        long now = level.getGameTime();
        Vec3 c = mc.gameRenderer.mainCamera().position();
        // once a second, and at once after a jump (a teleport, a respawn), so nothing of the old region shows up in the new
        double jx = c.x - lastX, jy = c.y - lastY, jz = c.z - lastZ;
        if (now % 20 == 0 || jx * jx + jy * jy + jz * jz > 8 * 8) here = level.dimension() == Level.OVERWORLD ? at(level.getBiome(BlockPos.containing(c))) : null;
        lastX = c.x;
        lastY = c.y;
        lastZ = c.z;
        bed(mc);
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
        if (bed != null) bed.goal = 0F;
    }

    private static void motes(ClientLevel level, Vec3 c) {
        for (int i = 0; i < MOTES; i++) {
            double x = c.x + (R.nextDouble() - 0.5) * 20, y = c.y + (R.nextDouble() - 0.5) * 10, z = c.z + (R.nextDouble() - 0.5) * 20;
            if (!level.getBlockState(M.set(x, y, z)).isAir()) continue;
            SimpleParticleType p = switch (here) {
                case ROOTWAYS -> R.nextFloat() < POLLEN ? Drips.POLLEN.get() : Drips.SPORE.get();
                case OCHRE_DEEP -> Drips.WARM_DUST.get();
                case GLIMMER_HOLLOWS -> Drips.GLITTER.get();
                case BLACK_VAULTS -> Drips.ASH.get();
                case VIOLET_DEEP -> Drips.SEEP.get();
                case PALE_HALLS, DROWNED_GALLERIES -> {
                    // low over still water: a wisp hangs a little over the surface
                    double w = water(level, x, y, z);
                    if (Double.isNaN(w)) yield null;
                    y = w + 0.2 + R.nextDouble() * 0.6;
                    yield Drips.MIST.get();
                }
                case WINDCLEFT -> {
                    // on the wall: the mica in the slate catching your light
                    Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(R);
                    if (!level.getBlockState(M.set(x + d.getStepX(), y, z + d.getStepZ())).isSolidRender()) yield null;
                    x = d.getStepX() == 0 ? x : Math.floor(x) + 0.5 + d.getStepX() * 0.48;
                    z = d.getStepZ() == 0 ? z : Math.floor(z) + 0.5 + d.getStepZ() * 0.48;
                    yield Drips.MICA.get();
                }
            };
            if (p != null) level.addParticle(p, x, y, z, 0, 0, 0);
        }
    }

    /** The top of still water one or two blocks under the spot, or NaN. */
    private static double water(ClientLevel level, double x, double y, double z) {
        int bx = Mth.floor(x), by = Mth.floor(y), bz = Mth.floor(z);
        for (int dy = 1; dy <= 2; dy++) {
            if (level.getFluidState(M.set(bx, by - dy, bz)).is(FluidTags.WATER)) return by - dy + 1 - 0.1;
            if (!level.getBlockState(M).isAir()) return Double.NaN;
        }
        return Double.NaN;
    }

    private static void sounds(Minecraft mc, ClientLevel level, Vec3 c, long now) {
        switch (here) {
            case ROOTWAYS -> {
                if (now >= nextCreak) {
                    nextCreak = now + CREAK + R.nextInt(CREAK);
                    // from the roof somewhere over and round you
                    int x = Mth.floor(c.x) + R.nextInt(21) - 10, z = Mth.floor(c.z) + R.nextInt(21) - 10;
                    int roof = roof(level, x, Mth.floor(c.y), z);
                    if (roof > 0) play(mc, CavesModule.ROOT_CREAK.get(), x + 0.5, roof, z + 0.5, 0.75F, 0.85F + R.nextFloat() * 0.25F);
                }
            }
            case OCHRE_DEEP -> {
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
            case PALE_HALLS -> {
                if (now >= nextPool) {
                    nextPool = now + POOL + R.nextInt(POOL);
                    // a drop falling into a pool somewhere round you: each pool rings at its own pitch (its place picks it)
                    int x = Mth.floor(c.x) + R.nextInt(25) - 12, z = Mth.floor(c.z) + R.nextInt(25) - 12;
                    int roof = roof(level, x, Mth.floor(c.y), z);
                    if (roof > 0) play(mc, CavesModule.POOL_DRIP.get(), x + 0.5, c.y - 1, z + 0.5, 0.7F, 0.8F + ((x * 31 + z * 17) & 7) * 0.06F);
                }
            }
            case WINDCLEFT -> {
                if (now >= nextPebbles) {
                    nextPebbles = now + PEBBLES + R.nextInt(PEBBLES);
                    double a = R.nextDouble() * Mth.TWO_PI, d = 6 + R.nextDouble() * 12;
                    play(mc, CavesModule.PEBBLE_FALL.get(), c.x + Math.cos(a) * d, c.y + 2 + R.nextDouble() * 8, c.z + Math.sin(a) * d,
                            0.7F, 0.9F + R.nextFloat() * 0.25F);
                }
            }
            case GLIMMER_HOLLOWS -> {
                // a crystal near you rings as you pass it, walking or faster
                Vec3 v = mc.player.getDeltaMovement();
                if (now >= nextRing && v.x * v.x + v.z * v.z > RING_SPEED * RING_SPEED) {
                    nextRing = now + RING + R.nextInt(RING);
                    int x = Mth.floor(c.x) + R.nextInt(2 * RING_REACH + 1) - RING_REACH, z = Mth.floor(c.z) + R.nextInt(2 * RING_REACH + 1) - RING_REACH;
                    for (int y = Mth.floor(c.y) - 3; y <= Mth.floor(c.y) + 4; y++) {
                        Block b = level.getBlockState(M.set(x, y, z)).getBlock();
                        if (b == CaveSkin.AMBER_CRYSTAL.get() || b == CaveSkin.GLACIAL_CRYSTAL.get()) {
                            play(mc, CavesModule.CRYSTAL_RING.get(), x + 0.5, y + 0.5, z + 0.5, 0.6F,
                                    (b == CaveSkin.GLACIAL_CRYSTAL.get() ? 1.15F : 0.95F) + R.nextFloat() * 0.12F);
                            break;
                        }
                    }
                }
            }
            case BLACK_VAULTS -> {
                if (now >= nextRumble) {
                    nextRumble = now + RUMBLE + R.nextInt(RUMBLE);
                    double a = R.nextDouble() * Mth.TWO_PI, d = 16 + R.nextDouble() * 16;
                    play(mc, CavesModule.VAULT_RUMBLE.get(), c.x + Math.cos(a) * d, c.y - 6, c.z + Math.sin(a) * d, 0.9F, 0.85F + R.nextFloat() * 0.2F);
                }
            }
            default -> { }
        }
    }

    /** The first solid block over (x, y, z) within 11, or 0. */
    private static int roof(ClientLevel level, int x, int y, int z) {
        for (int dy = 1; dy < 12; dy++) if (!level.getBlockState(M.set(x, y + dy, z)).isAir()) return y + dy;
        return 0;
    }

    private static void play(Minecraft mc, SoundEvent event, double x, double y, double z, float volume, float pitch) {
        float hush = Soundscape.hushAt(x, y, z) * Mth.clamp(Soundscape.natureLevel(), 0F, 1F);
        if (hush < 0.2F) return;
        mc.getSoundManager().play(new SimpleSoundInstance(event, SoundSource.AMBIENT, volume * hush, pitch, R, x, y, z));
    }

    /**
     * The region's bed round the listener (the Ochre Deep's warm hum, the Windcleft's wind, the Drowned Galleries' lapping,
     * the Violet Deep's hum): eased in over a few seconds as you enter, out as you leave; walking from one region into
     * another, the old bed fades out under the new.
     */
    private static void bed(Minecraft mc) {
        SoundEvent event = null;
        float loud = 0F;
        if (here != null) {
            switch (here) {
                case OCHRE_DEEP -> { event = CavesModule.WARM_HUM.get(); loud = HUM; }
                case WINDCLEFT -> { event = CavesModule.WIND_MOAN.get(); loud = WIND; }
                case DROWNED_GALLERIES -> { event = CavesModule.LAKE_LAP.get(); loud = LAP; }
                case VIOLET_DEEP -> { event = CavesModule.VIOLET_HUM.get(); loud = VIOLET; }
                default -> { }
            }
        }
        float goal = loud * Mth.clamp(Soundscape.natureLevel(), 0F, 1F);
        if (bed != null && bed.isStopped()) bed = null;
        if (bed != null && bed.event != event) {
            bed.goal = 0F;   // it fades out and stops itself
            bed = null;
        }
        if (bed == null) {
            if (event == null || goal < 0.01F) return;
            bed = new Loop(event);
            mc.getSoundManager().play(bed);
        }
        bed.goal = goal;
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        final SoundEvent event;
        float goal, level;

        Loop(SoundEvent event) {
            super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.event = event;
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
