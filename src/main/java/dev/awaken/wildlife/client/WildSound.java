package dev.awaken.wildlife.client;

import dev.awaken.Awaken;
import dev.awaken.core.audio.client.Soundscape;
import dev.awaken.core.audio.client.world.Conductor;
import dev.awaken.wildlife.Density;
import dev.awaken.wildlife.WildCritter;
import dev.awaken.wildlife.WildKinds;
import dev.awaken.wildlife.WildlifeModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The wild's voices (SPEC "Wild life"), from the animals you can see: a song is a bird on a branch with its bill open,
 * a drum is the woodpecker on that trunk, the hoot the owl turning its head. Placed, mono, quiet: they sit on top of
 * AmbientSounds' bed of real recordings (heard everywhere, from nowhere), never over it. Counted in the Conductor's
 * voice budget, at most {@link #VOICES} at once and a few new a second, quieter where the world is hushed.
 * <p>Two things here have no body: crickets in the grass at night (a few voices placed in the grass round you, each
 * falling silent when you walk within six blocks of it and starting again a while after you've passed), and the hush a
 * predator brings (a monster near you quiets the birds and insects round it, as docs/sound/PLAN.md planned).
 */
final class WildSound {
    private WildSound() {}

    static final int VOICES = 6;
    private static final SoundInstance[] PLAYING = new SoundInstance[VOICES];
    private static final RandomSource R = RandomSource.create();
    private static int newThisSecond;
    private static long second;
    /** Hostile creatures near the player (x, y, z), refreshed every second. */
    private static final double[] HOSTILE = new double[48];
    private static int hostiles;
    /** The crickets: a spot in the grass, when it next chirps, until when it keeps quiet. */
    private static final long[] CRICKET = new long[3];
    private static final long[] CRICKET_NEXT = new long[3], CRICKET_QUIET = new long[3];
    private static @Nullable SoundEvent gull;
    private static boolean registered;

    static void init() {
        if (registered) return;
        registered = true;
        Soundscape.register(new Conductor.Source() {
            @Override
            public int voices() {
                return active();
            }
        });
    }

    static int active() {
        int n = 0;
        var sm = Minecraft.getInstance().getSoundManager();
        for (int i = 0; i < VOICES; i++) {
            if (PLAYING[i] != null && !sm.isActive(PLAYING[i])) PLAYING[i] = null;
            if (PLAYING[i] != null) n++;
        }
        return n;
    }

    /** A call from an animal: placed at it, quieter as the world hushes there; dropped beyond earshot or over budget. */
    static void play(WildCritter c, SoundEvent event, float volume, float pitch) {
        play(c.getX(), c.getY() + 0.2, c.getZ(), event, volume, pitch);
    }

    static void play(double x, double y, double z, SoundEvent event, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double d = Math.sqrt((x - Wildlife.camX) * (x - Wildlife.camX) + (y - Wildlife.camY) * (y - Wildlife.camY) + (z - Wildlife.camZ) * (z - Wildlife.camZ));
        if (d > 96) return;
        float hush = Soundscape.hushAt(x, y, z);
        if (hush < 0.25F) return;
        long sec = Wildlife.now / 20;
        if (sec != second) {
            second = sec;
            newThisSecond = 0;
        }
        if (newThisSecond >= 3) return;
        int slot = -1;
        var sm = mc.getSoundManager();
        for (int i = 0; i < VOICES; i++) {
            if (PLAYING[i] == null || !sm.isActive(PLAYING[i])) {
                slot = i;
                break;
            }
        }
        if (slot < 0) return;
        SoundInstance s = new SimpleSoundInstance(event, SoundSource.AMBIENT, volume * hush, pitch, R, x, y, z);
        PLAYING[slot] = s;
        newThisSecond++;
        sm.play(s);
    }

    // ---- songs ----------------------------------------------------------------------------------------------------------

    /** A songbird's song by its kind (the coat names it): a blackbird sings the robin's phrases, lower. */
    static void song(WildCritter c) {
        List<String> vs = WildFaunaData.VARIANTS.get(WildKinds.RIGS[WildKinds.SONGBIRD]);
        String sp = vs.get(Math.floorMod(c.variant, vs.size()));
        float pitch = 0.95F + c.getRandom().nextFloat() * 0.1F;
        SoundEvent e = switch (sp) {
            case "blue_tit" -> WildlifeModule.SONG_TIT.get();
            case "chaffinch" -> WildlifeModule.SONG_FINCH.get();
            case "wren" -> WildlifeModule.SONG_WREN.get();
            default -> WildlifeModule.SONG_ROBIN.get();
        };
        if (sp.equals("blackbird")) pitch *= 0.72F;
        play(c, e, sp.equals("wren") ? 0.8F : 0.7F, pitch);
    }

    /** How likely a perched songbird is to sing at its next pause: the dawn chorus three times as often, none at night. */
    static float songChance(WildCritter c) {
        int t = Wildlife.tod;
        float base = 0.14F;
        if (t > 22600 || t < 1800) base *= 3F;
        else if (t > 11000 && t < 12800) base *= 1.6F;
        else if (t > 4500 && t < 8000) base *= 0.7F;
        return base * Mth.clamp(Soundscape.hushAt(c.getX(), c.getY(), c.getZ()) * 1.5F - 0.5F, 0F, 1F);
    }

    static float hootChance(WildCritter c) {
        int t = Wildlife.tod;
        return (t > 13000 && t < 22500 ? 0.2F : 0.05F) * Soundscape.hushAt(c.getX(), c.getY(), c.getZ());
    }

    static void gull(WildCritter c) {
        if (gull == null) gull = BuiltInRegistries.SOUND_EVENT.getValue(Awaken.id("city.gull.call"));
        if (gull != null) play(c, gull, 0.7F, 0.9F + c.getRandom().nextFloat() * 0.25F);
    }

    // ---- predators ----------------------------------------------------------------------------------------------------

    static boolean hostileNear(double x, double y, double z, double r) {
        for (int i = 0; i < hostiles; i++) {
            double dx = HOSTILE[i * 3] - x, dy = HOSTILE[i * 3 + 1] - y, dz = HOSTILE[i * 3 + 2] - z;
            if (dx * dx + dy * dy * 0.5 + dz * dz < r * r) return true;
        }
        return false;
    }

    static void tick(ClientLevel level) {
        if (Wildlife.now % 20 == 0) predators(level);
        crickets(level);
    }

    /**
     * Monsters within 40 of the player are where the wild goes quiet: each one within 24 hushes the birds and insects
     * within 24 of it to a quarter (the Conductor's hush, so AmbientSounds steps back too), renewed while it stays.
     */
    private static void predators(ClientLevel level) {
        hostiles = 0;
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof Enemy) || !(e instanceof Mob m) || !m.isAlive()) continue;
            double dx = e.getX() - Wildlife.px, dz = e.getZ() - Wildlife.pz;
            double d2 = dx * dx + dz * dz;
            if (d2 > 40 * 40 || Math.abs(e.getY() - Wildlife.py) > 24) continue;
            if (hostiles < HOSTILE.length / 3) {
                HOSTILE[hostiles * 3] = e.getX();
                HOSTILE[hostiles * 3 + 1] = e.getY();
                HOSTILE[hostiles * 3 + 2] = e.getZ();
                hostiles++;
            }
            if (d2 < 24 * 24) Soundscape.hush(e, e.position(), 24F, 0.25F, 50);
        }
    }

    // ---- crickets -----------------------------------------------------------------------------------------------------

    private static void crickets(ClientLevel level) {
        int t = Wildlife.tod;
        if (t < 12800 || t > 22800 || Wildlife.rain > 0.2F) return;
        for (int i = 0; i < CRICKET.length; i++) {
            if (CRICKET[i] != 0L) {
                int x = BlockPos.getX(CRICKET[i]), y = BlockPos.getY(CRICKET[i]), z = BlockPos.getZ(CRICKET[i]);
                double dx = x + 0.5 - Wildlife.px, dz = z + 0.5 - Wildlife.pz, d2 = dx * dx + dz * dz;
                // you walk up: it stops; it starts again a while after you've moved on, or another sings elsewhere
                if (d2 < 6 * 6 && Wildlife.pSpeed > 0.02) {
                    CRICKET_QUIET[i] = Wildlife.now + 120 + R.nextInt(80);
                    continue;
                }
                if (d2 > 22 * 22) CRICKET[i] = 0L;
                else if (Wildlife.now >= CRICKET_NEXT[i] && Wildlife.now >= CRICKET_QUIET[i]) {
                    play(x + 0.5, y + 0.1, z + 0.5, WildlifeModule.CRICKET.get(), 0.55F, 0.92F + (i * 0.05F));
                    CRICKET_NEXT[i] = Wildlife.now + 60 + R.nextInt(50);
                }
                continue;
            }
            if (Wildlife.now % 40 != i * 13) continue;
            // a new one somewhere in the grass 7-16 blocks off
            double a = R.nextDouble() * Math.PI * 2, d = 7 + R.nextDouble() * 9;
            int x = Mth.floor(Wildlife.px + Math.cos(a) * d), z = Mth.floor(Wildlife.pz + Math.sin(a) * d);
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            var st = level.getBlockState(new BlockPos(x, y, z));
            var below = level.getBlockState(new BlockPos(x, y - 1, z));
            boolean grass = st.is(BlockTags.REPLACEABLE_BY_TREES) || below.is(BlockTags.DIRT) && st.isAir();
            if (!grass || level.getBiome(new BlockPos(x, y, z)).value().getBaseTemperature() < 0.15F) continue;
            int fam = Sites.get(net.minecraft.world.level.ChunkPos.pack(x >> 4, z >> 4)) instanceof Sites.Site s ? s.h.family : -1;
            if (fam >= 0 && Density.lambda(WildKinds.FIREFLY, s(fam), 15000, 0F, 0F) <= 0F) continue;
            CRICKET[i] = BlockPos.asLong(x, y, z);
            CRICKET_NEXT[i] = Wildlife.now + R.nextInt(40);
        }
    }

    private static final Density.Habitat PROBE = new Density.Habitat();

    /** A meadow of the given family, to ask the tables whether its nights have insects at all. */
    private static Density.Habitat s(int family) {
        return PROBE.set(family, 0.3F, 0.8F, 4, 2, 8, 4, 0, 1, 0);
    }

    static void clear() {
        hostiles = 0;
        for (int i = 0; i < CRICKET.length; i++) CRICKET[i] = 0L;
        for (int i = 0; i < VOICES; i++) PLAYING[i] = null;
    }
}
