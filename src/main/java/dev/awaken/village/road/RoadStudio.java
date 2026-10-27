package dev.awaken.village.road;

import dev.awaken.Awaken;
import dev.awaken.village.plan.VillagePlan;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * The roads' studio (SPEC §28.9): when {@code road-survey.request} is in the server's folder, the whole network within a
 * radius of the centre is laid over the real overworld's noise for a seed (no chunks: the generator's own estimate, as
 * world generation reads it) and written with the land under it for {@code tools/city/roadmap.py}: hubs, settlements,
 * every road with its bridges and waymarkers, and what it all cost.
 */
public final class RoadStudio {
    private RoadStudio() {}

    /** The land under a dump: its top's lift over the street, under the sea level marked wet. */
    static java.util.function.IntBinaryOperator land(dev.awaken.village.plan.Terrain t, int ox, int oz, int baseY) {
        return (x, z) -> {
            int top = t.top(ox + x, oz + z) - baseY;
            return t.water(ox + x, oz + z) > 0 ? top - 2000 : top;
        };
    }

    public static void survey(ServerLevel level, long seed, int radius, Path dir) throws java.io.IOException {
        var reg = level.registryAccess();
        var ns = reg.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var preset = reg.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        var gen = new NoiseBasedChunkGenerator(MultiNoiseBiomeSource.createFromPreset(preset), ns);
        var rs = RandomState.create(reg.lookupOrThrow(Registries.NOISE), seed, ns.value());
        LevelHeightAccessor heights = LevelHeightAccessor.create(-64, 384);
        RoadWorld had = RoadWorld.get();
        SettlementGrid.Anchor hadFirst = dev.awaken.village.world.FirstHamlet.found();
        long t0 = System.currentTimeMillis();
        try {
            RoadNet.clear();
            Plans.clear();
            var t = dev.awaken.village.world.GenTerrain.of(gen, heights, rs);
            // where the city would stand in this world (uncarved here: the land round it as nature made it)
            var site = dev.awaken.terrain.city.SitePicker.pick(gen, rs, heights, false);
            int[] centre = {site.x(), site.z()};
            SettlementGrid.Anchor first = dev.awaken.village.world.FirstHamlet.search(seed, centre, t);
            dev.awaken.village.world.FirstHamlet.use(first);
            RoadWorld w = RoadWorld.of(seed, centre[0], centre[1], gen, rs, heights, true);
            RoadWorld.use(w);
            // every lattice chunk within the radius: the same path world generation takes
            int lat = Road.LATTICE;
            int legs = 0;
            for (int cx = Math.floorDiv(centre[0] - radius, 16 * lat) * lat; cx <= Math.floorDiv(centre[0] + radius, 16 * lat) * lat; cx += lat)
                for (int cz = Math.floorDiv(centre[1] - radius, 16 * lat) * lat; cz <= Math.floorDiv(centre[1] + radius, 16 * lat) * lat; cz += lat) {
                    if (Math.hypot(cx * 16 - centre[0], cz * 16 - centre[1]) > radius) continue;
                    legs += RoadNet.startsIn(cx, cz).size();
                }
            long tRoads = System.currentTimeMillis() - t0;
            StringBuilder sb = new StringBuilder();
            sb.append("{\"seed\":").append(seed).append(",\"radius\":").append(radius).append(",\"centre\":[").append(centre[0]).append(',').append(centre[1]).append(']');
            // the land: tops and water on a 24-block lattice
            int step = 24, n = radius * 2 / step + 1;
            sb.append(",\"land\":{\"step\":").append(step).append(",\"n\":").append(n).append(",\"x0\":").append(centre[0] - radius)
                    .append(",\"z0\":").append(centre[1] - radius).append(",\"top\":[");
            StringBuilder wet = new StringBuilder(), fam = new StringBuilder();
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < n; i++) {
                    int x = centre[0] - radius + i * step, z = centre[1] - radius + j * step;
                    long l = t.both(x, z);
                    if (i + j > 0) {
                        sb.append(',');
                        wet.append(',');
                        fam.append(',');
                    }
                    sb.append((int) (l >> 32));
                    wet.append((int) l);
                    fam.append((i % 4 == 0 && j % 4 == 0) ? t.family(x, z) : -1);
                }
            }
            sb.append("],\"wet\":[").append(wet).append("],\"fam\":[").append(fam).append("]}");
            // the region hearts and hubs
            sb.append(",\"hubs\":[");
            boolean firstOut = true;
            int pitch = w.regions.pitch();
            for (int gx = Math.floorDiv(centre[0] - radius, pitch) - 1; gx <= Math.floorDiv(centre[0] + radius, pitch) + 1; gx++)
                for (int gz = Math.floorDiv(centre[1] - radius, pitch) - 1; gz <= Math.floorDiv(centre[1] + radius, pitch) + 1; gz++) {
                    int[] h = w.regions.heart(gx, gz);
                    if (Math.hypot(h[0] - centre[0], h[1] - centre[1]) > radius) continue;
                    RoadNet.Hub hub = RoadNet.hub(w, gx, gz);
                    if (!firstOut) sb.append(',');
                    firstOut = false;
                    sb.append(String.format(Locale.ROOT, "{\"gx\":%d,\"gz\":%d,\"hx\":%d,\"hz\":%d,\"kind\":\"%s\",\"x\":%d,\"z\":%d,\"region\":\"%s\",\"town\":%b,\"first\":%b}",
                            gx, gz, h[0], h[1], hub.kind(), hub.x(), hub.z(), hub.region().replace("\"", "'"), RoadNet.townCell(w, gx, gz), FirstTown.is(w, gx, gz)));
                }
            sb.append("],\"settlements\":[");
            firstOut = true;
            for (SettlementGrid.Anchor a : SettlementGrid.near(seed, centre[0], centre[1], radius, centre)) {
                VillagePlan p = Plans.cached(a.key());
                if (!firstOut) sb.append(',');
                firstOut = false;
                sb.append(String.format(Locale.ROOT, "{\"key\":\"%s\",\"ax\":%d,\"az\":%d,\"anchor\":\"%s\"", a.key(), a.x(), a.z(), a.kind()));
                if (p != null) sb.append(String.format(Locale.ROOT, ",\"x\":%d,\"z\":%d,\"r\":%d,\"kind\":\"%s\",\"name\":\"%s\",\"people\":%d",
                        p.ox, p.oz, p.radius(), p.kind, p.name, p.people.size()));
                sb.append('}');
            }
            sb.append("],\"roads\":[");
            firstOut = true;
            for (Road r : RoadNet.laid()) {
                if (!firstOut) sb.append(',');
                firstOut = false;
                sb.append(String.format(Locale.ROOT, "{\"key\":\"%s\",\"cls\":%d,\"from\":\"%s\",\"to\":\"%s\",\"pts\":[", r.key(), r.cls(),
                        r.from().name().replace("\"", "'"), r.to().name().replace("\"", "'")));
                for (int i = 0; i < r.points(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append(String.format(Locale.ROOT, "[%.1f,%.1f,%d,%d]", r.x()[i], r.z()[i], r.y()[i], r.flags()[i]));
                }
                sb.append("],\"marks\":[");
                for (int k = 0; k < r.markers().size(); k++) {
                    Road.Marker m = r.markers().get(k);
                    if (k > 0) sb.append(',');
                    sb.append(String.format(Locale.ROOT, "{\"x\":%d,\"z\":%d,\"cairn\":%b,\"arms\":[", m.x(), m.z(), m.cairn()));
                    for (int q = 0; q < m.arms().size(); q++) {
                        Road.Arm arm = m.arms().get(q);
                        if (q > 0) sb.append(',');
                        sb.append(String.format(Locale.ROOT, "[%d,\"%s\",%d]", arm.side(), arm.name().replace("\"", "'"), arm.distance()));
                    }
                    sb.append("]}");
                }
                sb.append("]}");
            }
            // dumps for tools/city/render.py: the towns and wayhouses laid, the first hamlet, a few legs of road (bridges first)
            int towns = 0, ways = 0, mines = 0;
            for (SettlementGrid.Anchor a : SettlementGrid.near(seed, centre[0], centre[1], radius, centre)) {
                VillagePlan p = Plans.cached(a.key());
                if (p == null) continue;
                String name = p.kind == VillagePlan.Kind.TOWN && towns < 3 ? "survey-town-" + towns++ : p.kind == VillagePlan.Kind.WAYHOUSE && ways < 2
                        ? "survey-wayhouse-" + ways++ : p.kind == VillagePlan.Kind.MINING && mines < 2 ? "survey-mine-" + mines++ : a.first() ? "survey-first" : null;
                if (name == null) continue;
                dev.awaken.city.SettlementPaint.dump(dir.resolve(name + ".bin"), p, seed, p.chunks(), land(t, p.ox, p.oz, p.baseY));
                Files.writeString(dir.resolve(name + ".txt"), p + "\n");
            }
            int legsDumped = 0;
            for (Road r : RoadNet.laid()) {
                for (Road.Leg leg : r.legs()) {
                    boolean bridge = false;
                    for (int i = leg.i0(); i <= leg.i1(); i++) bridge |= (r.flags()[i] & RoadPlanner.BRIDGE) != 0;
                    boolean marked = false;
                    for (Road.Marker m : r.markers()) marked |= m.point() >= leg.i0() && m.point() <= leg.i1();
                    if (!bridge && !marked || legsDumped >= 6) continue;
                    RoadPiece piece = new RoadPiece(leg);
                    RoadPaint lp = piece.paint();
                    java.util.List<Long> cs = new java.util.ArrayList<>();
                    for (int cx = (lp.ox + lp.rx0) >> 4; cx <= (lp.ox + lp.rx0 + lp.rw) >> 4; cx++)
                        for (int cz = (lp.oz + lp.rz0) >> 4; cz <= (lp.oz + lp.rz0 + lp.rd) >> 4; cz++) cs.add(net.minecraft.world.level.ChunkPos.pack(cx, cz));
                    dev.awaken.city.SettlementPaint.dump(dir.resolve("survey-leg-" + legsDumped + ".bin"), lp, seed, cs.stream().mapToLong(Long::longValue).toArray(),
                            land(t, lp.ox, lp.oz, lp.baseY));
                    Files.writeString(dir.resolve("survey-leg-" + legsDumped + ".txt"), r.key() + " leg " + leg.index() + (bridge ? " (a bridge)" : "")
                            + (marked ? " (a waymarker)" : "") + " at " + lp.ox + "," + lp.oz + "\n");
                    legsDumped++;
                }
            }
            long total = System.currentTimeMillis() - t0;
            sb.append(String.format(Locale.ROOT, "],\"legs\":%d,\"ms\":%d,\"roadsMs\":%d,\"stats\":\"%s | %s\"}", legs, total, tRoads, RoadNet.stats(), Plans.stats()));
            Files.writeString(dir.resolve("road-survey.json"), sb.toString());
            Awaken.LOG.info("[road] survey seed {} within {}: {} roads, {} legs, roads laid in {} ms ({} ms in all); {}; {}", seed, radius,
                    RoadNet.laid().size(), legs, tRoads, total, RoadNet.stats(), Plans.stats());
        } finally {
            RoadNet.clear();
            Plans.clear();
            dev.awaken.village.world.FirstHamlet.use(hadFirst);
            RoadWorld.use(had);
        }
    }
}
