package dev.awaken.citizens.client;

import dev.awaken.city.CityPlan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.List;

/**
 * What AmbientSounds brings to Carillon (M4 B3b, SPEC §16.0 decision 30), when the player has it (the pack's sound
 * profile). Its own engine already plays real birds, crickets and owls wherever there's greenery (the Garden Ward, the
 * Terrace's gardens), wind once you're high on the roofs or the wall, and rain; the server leaves our synthetic birds and
 * crickets out of the beds for such a player (citizens.Soundscape). What its engine never does in a paved city is done
 * here with its recordings, referenced by id and never copied: gulls over the river (it only has them on beaches), a
 * chorus of them on the quays now and then, doves on the roofs of the quiet districts by day, and owls in the towers and
 * along the wall at night.
 * <p>Its files are stereo, so they can't come from a point: each plays unplaced, as loud as its distance allows (the way
 * AmbientSounds plays its own). Nothing here runs without the mod.
 */
public final class CityNature {
    private CityNature() {}

    /** Whether this client runs AmbientSounds (told to the server at login: CitizensNet.Ears). */
    public static final boolean INSTALLED = ModList.get().isLoaded("ambientsounds");

    private static final Identifier SEAGULL = as("animals.seagull"), SEAGULL_LONG = as("animals.seagull-long"),
            DOVE = as("animals.mouring_dove"), OWL = as("animals.owl");
    /** How near the river gulls are heard, the quay's chorus, and an owl's tower or the wall (blocks). */
    static final double GULLS = 64, CHORUS = 24, OWLS = 48;
    /** The districts doves sing over: Bellwatch, the Terrace, the Garden Ward, the Wallring (plan ids). */
    private static final boolean[] DOVES = {false, false, true, false, true, false, true, true};

    private static Identifier as(String path) {
        return Identifier.fromNamespaceAndPath("ambientsounds", path);
    }

    private static final RandomSource R = RandomSource.create();
    private static final long[] NEXT = new long[4];

    static void tick(ClientTickEvent.Post e) {
        if (!INSTALLED) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Entity p = mc.getCameraEntity();
        if (level == null || p == null || mc.isPaused() || !CityLife.known()) return;
        CityPlan plan = CityLife.plan();
        if (plan == null) return;
        long now = level.getGameTime();
        int tod = (int) Math.floorMod(level.getOverworldClockTime(), 24000L);
        boolean day = tod < 12000 || tod > 23500;
        double x = p.getX() - CityLife.cx, z = p.getZ() - CityLife.cz;
        double river = toRiver(plan.river, x, z);
        if (day && river < GULLS && due(0, now, 120, 320)) play(SEAGULL, 0.6F * (float) (1 - river / GULLS));
        if (day && river < CHORUS && due(1, now, 3600, 7200)) play(SEAGULL_LONG, 0.35F * (float) (1 - river / CHORUS));
        int d = plan.district((int) Math.floor(x), (int) Math.floor(z));
        if (tod < 11000 && d >= 0 && d < DOVES.length && DOVES[d] && due(2, now, 500, 1400)) play(DOVE, 0.3F);
        if (tod > 13500 && tod < 22500) {
            double owl = Math.abs(Math.hypot(x, z) - plan.wallRadius);
            for (CityPlan.District t : plan.districts) owl = Math.min(owl, Math.hypot(t.bellX() - x, t.bellZ() - z));
            if (owl < OWLS && due(3, now, 600, 1600)) play(OWL, 0.4F * (float) (1 - owl / OWLS));
        }
    }

    /** Distance from (x, z) (plan coordinates) to the river's line. */
    private static double toRiver(List<float[]> river, double x, double z) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i + 1 < river.size(); i++) {
            float[] a = river.get(i), b = river.get(i + 1);
            double dx = b[0] - a[0], dz = b[1] - a[1], len2 = dx * dx + dz * dz;
            double t = len2 < 1e-6 ? 0 : Math.max(0, Math.min(1, ((x - a[0]) * dx + (z - a[1]) * dz) / len2));
            best = Math.min(best, Math.hypot(x - a[0] - dx * t, z - a[1] - dz * t));
        }
        return best;
    }

    private static boolean due(int slot, long now, int min, int max) {
        if (now < NEXT[slot]) return false;
        NEXT[slot] = now + min + R.nextInt(Math.max(1, max - min));
        return true;
    }

    private static void play(Identifier sound, float volume) {
        if (volume <= 0.02F) return;
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.AMBIENT, volume,
                0.95F + R.nextFloat() * 0.1F, R, false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
    }
}
