package dev.awaken.village.road;

import dev.awaken.Awaken;
import dev.awaken.village.plan.VillagePlan;
import dev.awaken.village.world.GenTerrain;
import dev.awaken.village.world.Plans;
import dev.awaken.village.world.SettlementGrid;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The roads' bench (SPEC §28.9, Cost): the network over the real overworld's noise for a seed (Tectonic's when it is
 * installed), asked for the way world generation asks. First a few lattice chunks far out, each with every cache emptied
 * (what a teleport into new land waits for); then the city's land, cold (what its pregeneration waits for before the
 * world opens); then every lattice chunk within a radius, nearest the centre first (what exploring it costs in all),
 * and every settlement and hub there. Logs the time, the roads and plans made, the noise columns read, and a
 * fingerprint of every leg (its points, heights, flags, land and waymarkers), plan and hub in the area: a change that
 * makes the roads cheaper must leave it as it was. The GameTest roadbench_area runs it, only when asked for
 * ({@code -Ptests=roadbench_}; seed, radius and how far out the cold chunks lie from road-bench.request in the server's
 * folder).
 */
public final class RoadBench {
    private RoadBench() {}

    public static String run(ServerLevel level, long seed, int radius, int far) {
        var reg = level.registryAccess();
        var ns = reg.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var preset = reg.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        var gen = new NoiseBasedChunkGenerator(MultiNoiseBiomeSource.createFromPreset(preset), ns);
        var rs = RandomState.create(reg.lookupOrThrow(Registries.NOISE), seed, ns.value());
        LevelHeightAccessor heights = LevelHeightAccessor.create(-64, 384);
        RoadWorld had = RoadWorld.get();
        SettlementGrid.Anchor hadFirst = dev.awaken.village.world.FirstHamlet.found();
        StringBuilder out = new StringBuilder();
        try {
            RoadNet.clear();
            Plans.clear();
            var t = GenTerrain.of(gen, heights, rs);
            var site = dev.awaken.terrain.city.SitePicker.pick(gen, rs, heights, false);
            int[] centre = {site.x(), site.z()};
            SettlementGrid.Anchor first = dev.awaken.village.world.FirstHamlet.search(seed, centre, t);
            dev.awaken.village.world.FirstHamlet.use(first);
            long c0 = GenTerrain.COLUMNS.sum(), t0 = System.nanoTime();
            RoadWorld w = RoadWorld.of(seed, centre[0], centre[1], gen, rs, heights, true);
            RoadWorld.use(w);
            line(out, "seed %d, centre %d %d, the roads' world opened in %.0f ms (%d columns)", seed, centre[0], centre[1], (System.nanoTime() - t0) / 1e6,
                    GenTerrain.COLUMNS.sum() - c0);
            // cold: one lattice chunk far out at a time, nothing known
            long coldSum = 0;
            for (int k = 0; k < 4; k++) {
                double a = Math.PI / 4 + k * Math.PI / 2;
                int cx = Road.lattice(centre[0] + Math.cos(a) * far), cz = Road.lattice(centre[1] + Math.sin(a) * far);
                RoadPlanners.settle();
                RoadNet.clear();
                Plans.clear();
                w.forget();
                long r0 = RoadNet.planned, p0 = Plans.made(), col0 = GenTerrain.COLUMNS.sum(), s0 = System.nanoTime();
                List<Road.Leg> legs = RoadNet.startsIn(cx, cz);
                long ms = (System.nanoTime() - s0) / 1_000_000;
                coldSum += ms;
                line(out, "cold chunk %d,%d (%d blocks out): %d legs in %d ms; %d roads laid (%.0f ms), %d plans made (%s), sites %.0f ms, crossroads %.0f ms, %d columns",
                        cx, cz, far, legs.size(), ms, RoadNet.planned - r0, RoadNet.planNanos / 1e6, Plans.made() - p0, Plans.stats(), RoadNet.SITE_NS.sum() / 1e6,
                        RoadNet.DRY_NS.sum() / 1e6, GenTerrain.COLUMNS.sum() - col0);
            }
            line(out, "cold chunks: %d ms on average (%d road planners beside the asking thread)", coldSum / 4, RoadPlanners.threads());
            // the city's land, pregenerated before the world opens: its lattice chunks one after another, nearest first
            RoadPlanners.settle();
            RoadNet.clear();
            Plans.clear();
            w.forget();
            List<int[]> city = lattice(centre, CITY);
            long r0 = RoadNet.planned, p0 = Plans.made(), col0 = GenTerrain.COLUMNS.sum(), s0 = System.nanoTime();
            int cityLegs = 0;
            for (int[] c : city) cityLegs += RoadNet.startsIn(c[0], c[1]).size();
            line(out, "the city's land, cold: %d lattice chunks within %d, %d legs in %d ms; %d roads laid, %d plans made, %d columns", city.size(), CITY,
                    cityLegs, (System.nanoTime() - s0) / 1_000_000, RoadNet.planned - r0, Plans.made() - p0, GenTerrain.COLUMNS.sum() - col0);
            // the area: every lattice chunk, nearest first, then its settlements and hubs
            RoadPlanners.settle();
            RoadNet.clear();
            Plans.clear();
            w.forget();
            col0 = GenTerrain.COLUMNS.sum();
            s0 = System.nanoTime();
            List<int[]> chunks = lattice(centre, radius);
            // the planners lay it nearest first, as ahead of a player standing at the centre; world generation follows
            for (int k = 0; k < chunks.size(); k++) {
                int[] c = chunks.get(k);
                RoadNet.fanOut(w, c[0], c[1], RoadPlanners.AHEAD, k);
                RoadPlanners.job("L" + c[0] + "," + c[1], RoadPlanners.AHEAD, k * 4L + 3, () -> RoadNet.startsIn(c[0], c[1]));
            }
            long fp = 0xcbf29ce484222325L;
            int legs = 0;
            for (int[] c : chunks) {
                for (Road.Leg l : RoadNet.startsIn(c[0], c[1])) {
                    legs++;
                    fp = leg(fp, c, l);
                }
            }
            long roadsNs = System.nanoTime() - s0, roadsCols = GenTerrain.COLUMNS.sum() - col0;
            long roads = RoadNet.planned, roadNs = RoadNet.planNanos, plans = Plans.made();
            line(out, "area within %d: %d lattice chunks, %d legs in %.1f s (%d columns); %d roads laid (%.0f ms each), %d plans made (%s), sites %.0f ms, "
                            + "crossroads %.0f ms", radius, chunks.size(), legs, roadsNs / 1e9, roadsCols, roads, roads == 0 ? 0 : roadNs / 1e6 / roads, plans, Plans.stats(),
                    RoadNet.SITE_NS.sum() / 1e6, RoadNet.DRY_NS.sum() / 1e6);
            long s1 = System.nanoTime();
            int settled = 0;
            for (SettlementGrid.Anchor a : SettlementGrid.near(seed, centre[0], centre[1], radius, centre)) {
                VillagePlan p = Plans.of(a, w.terrain());
                fp = mix(fp, a.key().hashCode());
                if (p != null) {
                    fp = mix(fp, RoadTests.text(p).hashCode());
                    settled++;
                }
            }
            int pitch = w.regions.pitch();
            for (int gx = Math.floorDiv(centre[0] - radius, pitch); gx <= Math.floorDiv(centre[0] + radius, pitch); gx++)
                for (int gz = Math.floorDiv(centre[1] - radius, pitch); gz <= Math.floorDiv(centre[1] + radius, pitch); gz++) {
                    int[] h = w.regions.heart(gx, gz);
                    if (Math.hypot(h[0] - centre[0], h[1] - centre[1]) > radius) continue;
                    RoadNet.Hub hub = RoadNet.hub(w, gx, gz);
                    fp = mix(mix(mix(fp, hub.kind().ordinal()), hub.x()), hub.z());
                }
            line(out, "the rest of the area's %d settlements and its hubs in %.1f s; %d noise columns in all, %d roads, %d plans", settled,
                    (System.nanoTime() - s1) / 1e9, GenTerrain.COLUMNS.sum() - col0, RoadNet.planned, Plans.made());
            line(out, "fingerprint %016x", fp);
        } finally {
            RoadPlanners.settle();
            RoadNet.clear();
            Plans.clear();
            dev.awaken.village.world.FirstHamlet.use(hadFirst);
            RoadWorld.use(had);
        }
        return out.toString();
    }

    /** About how far out the city pregenerates (its reach and a margin: CityPregen). */
    static final int CITY = 374;

    /** The lattice chunks within a radius of the centre, nearest first. */
    static List<int[]> lattice(int[] centre, int radius) {
        List<int[]> chunks = new ArrayList<>();
        int lat = Road.LATTICE;
        for (int cx = Math.floorDiv(centre[0] - radius, 16 * lat) * lat; cx <= Math.floorDiv(centre[0] + radius, 16 * lat) * lat; cx += lat)
            for (int cz = Math.floorDiv(centre[1] - radius, 16 * lat) * lat; cz <= Math.floorDiv(centre[1] + radius, 16 * lat) * lat; cz += lat)
                if (Math.hypot(cx * 16 + 8 - centre[0], cz * 16 + 8 - centre[1]) <= radius) chunks.add(new int[]{cx, cz});
        chunks.sort((u, v) -> Double.compare(Math.hypot(u[0] * 16 - centre[0], u[1] * 16 - centre[1]), Math.hypot(v[0] * 16 - centre[0], v[1] * 16 - centre[1])));
        return chunks;
    }

    /** A leg as world generation paints it: where it starts, its road's ends, its points and the waymarkers on it. */
    static long leg(long fp, int[] chunk, Road.Leg l) {
        Road r = l.road();
        fp = mix(mix(mix(fp, chunk[0]), chunk[1]), r.key().hashCode());
        fp = mix(mix(mix(mix(fp, l.index()), l.i0()), l.i1()), r.cls());
        fp = mix(mix(fp, (r.from().name() + "|" + r.from().settlement() + "|" + r.to().name() + "|" + r.to().settlement()).hashCode()), r.litFrom() * 31 + r.litTo());
        for (int i = l.i0(); i <= l.i1(); i++)
            fp = mix(mix(mix(mix(mix(fp, Float.floatToIntBits(r.x()[i])), Float.floatToIntBits(r.z()[i])), r.y()[i]), r.flags()[i]), r.fam()[i]);
        for (Road.Marker m : r.markers()) {
            if (m.point() < l.i0() || m.point() > l.i1()) continue;
            fp = mix(mix(mix(mix(fp, m.x()), m.y()), m.z()), m.cairn() ? 1 : 0);
            for (Road.Arm a : m.arms()) fp = mix(mix(mix(fp, a.side()), a.name().hashCode()), a.distance());
        }
        return fp;
    }

    static long mix(long fp, long v) {
        return (fp ^ v) * 0x100000001b3L;
    }

    private static void line(StringBuilder out, String fmt, Object... args) {
        String s = String.format(Locale.ROOT, fmt, args);
        Awaken.LOG.info("[road] bench: " + s);
        out.append(s).append('\n');
    }
}
