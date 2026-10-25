package dev.awaken.citizens.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.Awaken;
import dev.awaken.citizens.CitizenBody;
import dev.awaken.citizens.CitizensContent;
import dev.awaken.city.CityPlan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The places in Carillon that sound (M4 B3b, SPEC §16.0 decision 28): every sound comes from somewhere (Sound Physics
 * in the pack echoes it down the lanes). Under them lie the district beds the server picks for the ambience slot
 * ({@code citizens.Soundscape}).
 * <ul>
 *   <li>loops at their place, eased in and out as you come and go: the two fountains, water lapping along the quays,
 *   the mill wheels on the race, and from evening till late the taverns, muffled through their walls;</li>
 *   <li>one-shots on their own clocks: hammers at the four smithies through the working day (now and then the
 *   bellows), the stalls by day and their criers calling (once the recordings are in), boats creaking along the river, songbirds in the gardens (a dawn chorus), gulls over the
 *   quay, and at night the Watch's handbell on its rounds;</li>
 *   <li>the Choir's carillon from the spire at dawn, noon and dusk (our own three tunes), heard across the city.</li>
 * </ul>
 * Each place list comes from the plan once. Loops are checked every 10 ticks (at most 3 of a kind, nearest first);
 * nothing allocates per tick but a new sound.
 */
public final class CitySound {
    private CitySound() {}

    private static final SoundEvent FOUNTAIN = CitizensContent.FOUNTAIN.get(), LAP = CitizensContent.LAP.get(), MILL = CitizensContent.MILL.get(),
            TAVERN = CitizensContent.TAVERN.get(), ANVIL = CitizensContent.ANVIL.get(), BELLOWS = CitizensContent.BELLOWS.get(),
            STALL = CitizensContent.STALL.get(), BOAT = CitizensContent.BOAT.get(), SONGBIRD = CitizensContent.SONGBIRD.get(),
            HANDBELL = CitizensContent.HANDBELL.get(), DAWN = CitizensContent.CARILLON_DAWN.get(), NOON = CitizensContent.CARILLON_NOON.get(),
            DUSK = CitizensContent.CARILLON_DUSK.get();
    /** The criers (recorded; null until their recordings are in). */
    private static final SoundEvent CRIER = CitizensContent.CRIER == null ? null : CitizensContent.CRIER.get();

    /** One kind of place: its points (world x, y, z), how near it starts, how many may play. */
    static final class Places {
        final SoundEvent event;
        final double start;
        final int most;
        final List<double[]> at = new ArrayList<>();
        final Loop[] playing;

        Places(SoundEvent event, double start, int most) {
            this.event = event;
            this.start = start;
            this.most = most;
            this.playing = new Loop[most];
        }
    }

    private static Places fountains, laps, mills, taverns;
    private static final List<double[]> ANVILS = new ArrayList<>(), STALLS = new ArrayList<>(), RIVER = new ArrayList<>(), TREES = new ArrayList<>();
    private static double[] spire;
    private static boolean built;
    private static int clock;
    private static long lastTod = -1;
    private static final long[] NEXT = new long[8];
    private static final RandomSource R = RandomSource.create();

    private static void build(CityPlan plan) {
        built = true;
        int cx = CityLife.cx, sy = CityLife.sy, cz = CityLife.cz;
        fountains = new Places(FOUNTAIN, 22, 2);
        laps = new Places(LAP, 22, 3);
        mills = new Places(MILL, 30, 2);
        taverns = new Places(TAVERN, 20, 2);
        ANVILS.clear();
        STALLS.clear();
        RIVER.clear();
        TREES.clear();
        for (CityPlan.Prop p : plan.props) {
            double x = cx + (p.x0() + p.x1()) / 2.0 + 0.5, z = cz + (p.z0() + p.z1()) / 2.0 + 0.5, y = sy + p.ry() + 1;
            if (p.kind().equals("fountain")) fountains.at.add(new double[]{x, y + 1.5, z});
            else if (p.kind().equals("stall")) STALLS.add(new double[]{x, y + 1, z});
        }
        // the quays: both banks of the river, a point every 16 blocks, at the water's edge
        double half = CityLife.RIVER_HALF + 1.5;
        List<float[]> rv = plan.river;
        for (int i = 0; i + 1 < rv.size(); i++) {
            float[] a = rv.get(i), b = rv.get(i + 1);
            double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
            double nx = -(b[1] - a[1]) / len, nz = (b[0] - a[0]) / len;
            for (double t = 0; t < len; t += 16) {
                double x = cx + a[0] + (b[0] - a[0]) * t / len, z = cz + a[1] + (b[1] - a[1]) * t / len;
                laps.at.add(new double[]{x + nx * half, sy - 6, z + nz * half});
                laps.at.add(new double[]{x - nx * half, sy - 6, z - nz * half});
                RIVER.add(new double[]{x, sy - 6, z});
            }
        }
        for (double[] m : millRace()) mills.at.add(new double[]{cx + m[0], sy - 4, cz + m[1]});
        for (CityPlan.Plot pl : plan.plots) {
            if (!pl.use().equals("tavern") || !pl.hasDoor()) continue;
            taverns.at.add(new double[]{cx + pl.doorX() + 0.5, sy + pl.lift() + 1.5, cz + pl.doorZ() + 0.5});
        }
        for (int[] tr : plan.trees) {
            int d = plan.district(tr[0], tr[1]);
            if (d == 6 || d == 4 || (tr[2] == 2)) TREES.add(new double[]{cx + tr[0] + 0.5, sy + plan.lift(tr[0], tr[1]) + 5, cz + tr[1] + 0.5});
        }
        for (CityPlan.Landmark l : plan.landmarks) if (l.id().equals("choir")) spire = new double[]{cx + l.x() + 0.5, sy + 61, cz + l.z() + 0.5};
        // the smithies' anvils (the roster's data, from the plan's plots marked as forges by the citizens file)
        for (double[] a : smithies()) ANVILS.add(new double[]{cx + a[0] + 0.5, sy + 1.5, cz + a[1] + 0.5});
    }

    /** The mill race's points (plan.json "millRace"), four along each of its segments. */
    private static List<double[]> millRace() {
        List<double[]> out = new ArrayList<>();
        List<float[]> race = CityLife.path("millRace");
        for (int i = 0; i + 1 < race.size(); i++) {
            float[] a = race.get(i), b = race.get(i + 1);
            for (int k = 0; k <= 3; k++) {
                double f = k / 3.0;
                out.add(new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f});
            }
        }
        return out;
    }

    /** The anvils' blocks (citizens.json "smithies"[].block, plan coordinates). */
    private static List<double[]> smithies() {
        List<double[]> out = new ArrayList<>();
        try (InputStream in = CityPlan.class.getResourceAsStream("/data/awaken/city/citizens.json")) {
            if (in == null) return out;
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : o.getAsJsonArray("smithies")) {
                var b = e.getAsJsonObject().getAsJsonArray("block");
                out.add(new double[]{b.get(0).getAsDouble(), b.get(2).getAsDouble()});
            }
        } catch (Exception ex) {
            Awaken.LOG.warn("[citizens] smithies: {}", ex.toString());
        }
        return out;
    }

    // ---- once a tick ------------------------------------------------------------------------------------------------

    static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Entity p = mc.getCameraEntity();
        if (level == null || p == null || mc.isPaused() || !CityLife.known()) return;
        CityPlan plan = CityLife.plan();
        if (plan == null) return;
        if (!built) build(plan);
        int tod = (int) Math.floorMod(level.getOverworldClockTime(), 24000L);
        long now = level.getGameTime();
        double x = p.getX(), y = p.getY(), z = p.getZ();
        carillon(level, tod, x, z);
        if (++clock % 10 == 0) {
            loops(fountains, x, y, z, 0.8F);
            loops(laps, x, y, z, 0.8F);
            loops(mills, x, y, z, 0.9F);
            float tav = tod >= 11000 && tod < 18500 ? (tod < 12500 ? (tod - 11000) / 1500F : tod > 17500 ? (18500 - tod) / 1000F : 1F) : 0F;
            loops(taverns, x, y, z, tav * 0.9F);
        }
        boolean day = tod < 12000 || tod > 23500;
        // forges through the working day, one blow pattern every few seconds from the nearest smithy in reach
        if (tod >= 900 && tod < 10800 && due(0, now, 50, 120)) {
            double[] a = nearest(ANVILS, x, z, 48);
            if (a != null) {
                play(level, a, ANVIL, 0.85F, 0.95F + R.nextFloat() * 0.1F);
                if (R.nextFloat() < 0.25F) play(level, a, BELLOWS, 0.6F, 0.9F + R.nextFloat() * 0.2F);
            }
        }
        if (tod >= 1000 && tod < 11500 && due(1, now, 40, 110)) {
            double[] s = random(STALLS, x, z, 22);
            if (s != null) play(level, s, STALL, 0.55F, 0.9F + R.nextFloat() * 0.25F);
        }
        // a stallholder calling their wares, through the busy middle of the day (voices are pitched only a little)
        if (CRIER != null && tod >= 1500 && tod < 10500 && due(6, now, 100, 280)) {
            double[] s = random(STALLS, x, z, 30);
            if (s != null) play(level, s, CRIER, 0.5F, 0.95F + R.nextFloat() * 0.1F);
        }
        if (due(2, now, 90, 260)) {
            double[] b = random(RIVER, x, z, 26);
            if (b != null) play(level, b, BOAT, 0.5F, 0.85F + R.nextFloat() * 0.3F);
        }
        boolean chorus = tod > 22800 || tod < 1500;
        if (day && due(3, now, chorus ? 14 : 50, chorus ? 50 : 150)) {
            double[] t = random(TREES, x, z, 30);
            if (t != null) play(level, t, SONGBIRD, chorus ? 0.55F : 0.4F, 0.9F + R.nextFloat() * 0.3F);
        }
        if (day && due(4, now, 120, 320)) {
            double[] g = random(RIVER, x, z, 60);
            if (g != null) {
                double[] high = {g[0] + (R.nextDouble() - 0.5) * 20, g[1] + 22 + R.nextDouble() * 10, g[2] + (R.nextDouble() - 0.5) * 20};
                play(level, high, CitySounds.GULL_CALL, 0.7F, 0.85F + R.nextFloat() * 0.3F);
            }
        }
        // the Watch on its rounds: a lantern-bearer rings the handbell now and then
        if (!day && due(5, now, 600, 1400)) {
            for (Entity en : level.entitiesForRendering()) {
                if (en instanceof CitizenBody b && CityGlow.carries(b) && b.distanceToSqr(x, y, z) < 40 * 40) {
                    level.playLocalSound(b.getX(), b.getY() + 1.4, b.getZ(), HANDBELL, SoundSource.NEUTRAL, 0.25F, 1.05F, false);
                    break;
                }
            }
        }
    }

    /** The carillon: when the clock passes dawn (0), noon (6000) or dusk (12000), the spire plays that hour's tune. */
    private static void carillon(ClientLevel level, int tod, double x, double z) {
        long prev = lastTod;
        lastTod = tod;
        if (prev < 0 || spire == null) return;
        SoundEvent tune = null;
        if (crossed(prev, tod, 0)) tune = DAWN;
        else if (crossed(prev, tod, 6000)) tune = NOON;
        else if (crossed(prev, tod, 12000)) tune = DUSK;
        if (tune == null || Math.hypot(spire[0] - x, spire[2] - z) > 420) return;
        level.playLocalSound(spire[0], spire[1], spire[2], tune, SoundSource.AMBIENT, 4.0F, 1F, false);
    }

    private static boolean crossed(long prev, long now, long mark) {
        // the clock moves forward by a tick at a time (a jump of more than a minute is a /time set: no tune)
        long d = Math.floorMod(now - prev, 24000L);
        if (d == 0 || d > 1200) return false;
        long since = Math.floorMod(now - mark, 24000L);
        return since < d;
    }

    private static boolean due(int slot, long now, int min, int max) {
        if (now < NEXT[slot]) return false;
        NEXT[slot] = now + min + R.nextInt(Math.max(1, max - min));
        return true;
    }

    private static double[] nearest(List<double[]> pts, double x, double z, double within) {
        double best = within * within;
        double[] out = null;
        for (double[] q : pts) {
            double d = (q[0] - x) * (q[0] - x) + (q[2] - z) * (q[2] - z);
            if (d < best) {
                best = d;
                out = q;
            }
        }
        return out;
    }

    /** One of the points within reach, picked at random (reservoir; no list made). */
    private static double[] random(List<double[]> pts, double x, double z, double within) {
        double w2 = within * within;
        double[] out = null;
        int seen = 0;
        for (double[] q : pts) {
            double d = (q[0] - x) * (q[0] - x) + (q[2] - z) * (q[2] - z);
            if (d < w2 && R.nextInt(++seen) == 0) out = q;
        }
        return out;
    }

    private static void play(ClientLevel level, double[] at, SoundEvent ev, float vol, float pitch) {
        level.playLocalSound(at[0], at[1], at[2], ev, SoundSource.AMBIENT, vol, pitch, false);
    }

    /** Keeps the nearest few of a kind playing (each eased to `level`), and lets the rest go. */
    private static void loops(Places pl, double x, double y, double z, float level) {
        if (pl == null) return;
        Minecraft mc = Minecraft.getInstance();
        for (int s = 0; s < pl.most; s++) {
            Loop l = pl.playing[s];
            if (l == null) continue;
            double d = Math.hypot(l.px - x, l.pz - z);
            if (d > pl.start + 8 || level <= 0.01F) l.ending = true;
            if (l.isStopped() || (l.ending && !mc.getSoundManager().isActive(l))) pl.playing[s] = null;
            else l.target = level;
        }
        if (level <= 0.01F) return;
        for (int s = 0; s < pl.most; s++) {
            if (pl.playing[s] != null) continue;
            double best = pl.start * pl.start;
            double[] pick = null;
            for (double[] q : pl.at) {
                double d = (q[0] - x) * (q[0] - x) + (q[2] - z) * (q[2] - z);
                if (d >= best || taken(pl, q)) continue;
                best = d;
                pick = q;
            }
            if (pick == null) return;
            Loop l = new Loop(pl.event, pick);
            l.target = level;
            pl.playing[s] = l;
            mc.getSoundManager().play(l);
        }
    }

    private static boolean taken(Places pl, double[] q) {
        for (Loop l : pl.playing) if (l != null && l.at == q) return true;
        return false;
    }

    static void clear() {
        built = false;
        lastTod = -1;
        for (Places pl : new Places[]{fountains, laps, mills, taverns}) {
            if (pl == null) continue;
            for (int s = 0; s < pl.most; s++) {
                if (pl.playing[s] != null) pl.playing[s].stopNow();
                pl.playing[s] = null;
            }
        }
    }

    /** A place's loop: positional, eased in and out (so walking past never pops). */
    static final class Loop extends AbstractTickableSoundInstance {
        final double[] at;
        final double px, pz;
        float target, level;
        boolean ending;

        Loop(SoundEvent event, double[] at) {
            super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.at = at;
            this.px = at[0];
            this.pz = at[2];
            this.x = at[0];
            this.y = at[1];
            this.z = at[2];
            this.looping = true;
            this.delay = 0;
            this.volume = 0.001F;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        void stopNow() {
            stop();
        }

        @Override
        public void tick() {
            float goal = ending ? 0F : target;
            level += (goal - level) * 0.05F;
            volume = Math.max(0.001F, level);
            if (ending && level < 0.01F) stop();
        }
    }
}
