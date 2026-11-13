package dev.awaken.gates.realms.bench;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.awaken.gates.realms.Blueprint;
import dev.awaken.gates.realms.Blueprints;
import dev.awaken.gates.realms.Land;
import dev.awaken.gates.realms.LandPrograms;
import dev.awaken.gates.realms.RealmPlan;
import dev.awaken.gates.realms.check.FloatCheck;
import dev.awaken.gates.realms.check.WalkCheck;
import dev.awaken.gates.realms.kit.Piece;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.preview.Preview;
import dev.awaken.progression.Rank;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One land checked in one go, for whoever is building it (SPEC §50.8): the preview's pictures and its report, the whole
 * land scanned for anything floating, and the walk from the Mouth to every site, with where the time went. Asked for
 * through the environment by tools/bench/bench.sh; does nothing otherwise. Dev tooling: nothing in the game calls this.
 */
public final class Bench {
    private Bench() {}

    /**
     * One thing to check. {@code what}: a tour file (its cameras, its Realm) or a blueprint id (one look from the Mouth).
     * {@code eye}: one camera given outright, {x, y, z, yaw, pitch} in the land's coordinates; then only the picture is
     * made. {@code lands}: where the land's class is loaded from when the gate's code was just rebuilt, or null.
     */
    public record Ask(String what, long seed, String shotName, double[] eye, Path root, Path out, ClassLoader lands) {}

    public record Answer(boolean clean, String text) {}

    private record Timed<T>(T value, long millis) {}

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "awaken-bench");
        t.setDaemon(true);
        return t;
    });

    public static void runIfAsked() {
        String serve = System.getenv("AWAKEN_BENCH_SERVE");
        if (serve != null && !serve.isBlank()) {
            Serve.run(Path.of(serve));
            return;
        }
        String what = System.getenv("AWAKEN_BENCH");
        if (what == null || what.isBlank()) return;
        String seed = System.getenv("AWAKEN_BENCH_SEED");
        Path out = Path.of(System.getenv("AWAKEN_BENCH_OUT"));
        Ask ask = new Ask(what, seed == null || seed.isBlank() ? 1 : Long.parseLong(seed.trim()), "mouth", null, Path.of(System.getenv("AWAKEN_BENCH_ROOT")), out, null);
        System.out.print("[bench]\n" + answer(ask).text());
    }

    /** {@link #check}, with a failure written down as the report instead of thrown: whoever asked reads one file either way. */
    public static Answer answer(Ask a) {
        try {
            return check(a);
        } catch (Throwable e) {
            StringWriter w = new StringWriter();
            Throwable cause = e instanceof java.lang.reflect.InvocationTargetException && e.getCause() != null ? e.getCause() : e;
            cause.printStackTrace(new PrintWriter(w));
            boolean memory = false;
            for (Throwable t = e; t != null && !memory; t = t.getCause()) memory = t instanceof OutOfMemoryError;
            String text = "BENCH FAILED " + a.what() + ": " + (memory ? "out of memory with a heap of " + (Runtime.getRuntime().maxMemory() >> 20)
                    + " MB. This land needs more: BENCH_XMX=4g tools/bench/bench.sh restart, or bench.sh once" : cause.toString()) + "\n" + w;
            try {
                Files.createDirectories(a.out());
                Files.writeString(a.out().resolve("bench.txt"), text);
            } catch (IOException io) {
                io.printStackTrace();
            }
            return new Answer(false, text);
        }
    }

    public static Answer check(Ask a) throws Exception {
        long t0 = System.currentTimeMillis();
        Files.createDirectories(a.out());
        List<String> lines = null;
        String id;
        Rank rank = Rank.E;
        if (a.what().endsWith(".txt")) {
            Path tour = a.root().resolve(a.what());
            lines = Files.readAllLines(tour);
            String[] which = Preview.landOf(lines);
            if (which == null) throw new IOException("no 'cmd awaken gate realm_enter RANK id' line in " + tour);
            rank = Rank.valueOf(which[0]);
            id = which[1];
        } else id = a.what().contains(":") ? a.what() : "awaken:" + a.what();
        Blueprint bp = blueprint(id, a.root());
        Land land = make(new Land.Ctx(bp, id, rank, a.seed(), RealmPlan.CURRENT_VERSION), a.root(), a.lands());
        boolean only = a.eye() != null;

        // the three read the land and never change it, so they run side by side; a land too big for that (Amber Reach is
        // 2,048 across, 17,000 chunks) is drawn first and checked after, one at a time, or the heap doesn't hold all three
        int h = land.halfSize() + 16, across = (2 * h >> 4) + 1;
        boolean together = across * across <= BIG_LAND;
        java.util.concurrent.Callable<Timed<FloatCheck.Report>> scan = () -> {
            long t = System.currentTimeMillis();
            try {
                return new Timed<>(FloatCheck.scan(land, -h, -h, h, h), System.currentTimeMillis() - t);
            } catch (OutOfMemoryError e) {
                return new Timed<>(null, System.currentTimeMillis() - t);
            }
        };
        java.util.concurrent.Callable<Timed<WalkCheck.Walk>> walk = () -> {
            long t = System.currentTimeMillis();
            try {
                return new Timed<>(WalkCheck.walk(land), System.currentTimeMillis() - t);
            } catch (OutOfMemoryError e) {
                return new Timed<>(null, System.currentTimeMillis() - t);
            }
        };
        Future<Timed<FloatCheck.Report>> floats = only || !together ? null : POOL.submit(scan);
        Future<Timed<WalkCheck.Walk>> reach = only || !together ? null : POOL.submit(walk);
        Preview.Drawn drawn;
        try {
            drawn = Preview.draw(land, bp, lines, a.shotName(), a.eye(), a.root(), a.out());
        } catch (Throwable e) {
            if (floats != null) floats.cancel(true);
            if (reach != null) reach.cancel(true);
            throw e;
        }
        List<String> names = new ArrayList<>(), flagged = new ArrayList<>();
        String art = null;
        for (String line : drawn.report().split("\n")) {
            if (line.startsWith("no art found")) {
                art = line.strip();
                continue;
            }
            String[] w = line.trim().split("\\s+");
            if (w.length < 2 || !Files.exists(a.out().resolve(w[0] + ".png"))) continue;
            names.add(w[0]);
            StringBuilder f = new StringBuilder();
            for (int i = 1; i < w.length; i++) if (FLAG.matcher(w[i]).matches()) f.append(' ').append(w[i]);
            if (!f.isEmpty()) flagged.add(w[0] + f);
        }

        StringBuilder s = new StringBuilder();
        boolean clean = true;
        if (only) {
            s.append(String.format(Locale.ROOT, "BENCH %s seed %d   total %d ms  (paint %d, shots %d)%n", id, a.seed(), System.currentTimeMillis() - t0, drawn.paintMs(), drawn.shotsMs()));
            s.append(drawn.report());
            if (!names.isEmpty()) s.append("shot: ").append(a.out().resolve(names.getFirst() + ".png").toAbsolutePath()).append('\n');
        } else {
            Timed<FloatCheck.Report> fl = together ? floats.get() : scan.call();
            Timed<WalkCheck.Walk> walked = together ? reach.get() : walk.call();
            Timed<List<String>> re = new Timed<>(walked.value() == null ? null : walked.value().unreachable(), walked.millis());
            dev.awaken.gates.realms.check.RouteCheck.Report route = dev.awaken.gates.realms.check.RouteCheck.check(land, walked.value());
            long t1 = System.currentTimeMillis();
            Path sheet = sheet(a.out(), names);
            long sheetMs = System.currentTimeMillis() - t1;
            int sites = 0;
            for (Land.Site site : land.sites()) if (site.kind() != Land.SiteKind.PORTAL) sites++;
            clean = fl.value() != null && fl.value().clean() && re.value() != null && re.value().isEmpty() && route.problems().isEmpty();
            s.append(String.format(Locale.ROOT, "BENCH %s seed %d   total %d ms  (paint %d, floats %d, reach %d, shots %d, sheet %d)%n", id, a.seed(),
                    System.currentTimeMillis() - t0, drawn.paintMs(), fl.millis(), re.millis(), drawn.shotsMs(), sheetMs));
            if (fl.value() == null) s.append("floats: NOT SCANNED, the scan ran out of memory (").append(across * across).append(" chunks, heap ").append(Runtime.getRuntime().maxMemory() >> 20)
                    .append(" MB). Use bench.sh once, or BENCH_XMX=4g bench.sh restart\n");
            else floaters(s, land, fl.value());
            String settled = settled(land);
            if (settled != null) s.append("settled: ").append(settled).append('\n');
            if (re.value() == null) s.append("reach: NOT WALKED, the walk ran out of memory. Use bench.sh once, or BENCH_XMX=4g bench.sh restart\n");
            else if (re.value().isEmpty()) s.append("reach: all ").append(sites).append(" sites\n");
            else {
                s.append("reach: ").append(re.value().size()).append(" of ").append(sites).append(" sites can't be walked to from the Mouth\n");
                for (String bad : re.value()) s.append("  ").append(bad).append('\n');
            }
            s.append(route.summary()).append('\n');
            for (String wrong : route.problems()) s.append("  ").append(wrong).append('\n');
            beats(s, land);
            // where each leg is, so whoever writes a tour can stand a camera in it
            if (land.designed()) for (Land.Leg leg : land.route()) s.append("  leg ").append(leg.id()).append(' ').append(leg.kind().name().toLowerCase(Locale.ROOT)).append(" at ")
                    .append(leg.x()).append(' ').append(leg.y()).append(' ').append(leg.z()).append("  (").append(leg.name()).append(")\n");
            s.append("shots: ").append(names.size());
            if (!flagged.isEmpty()) s.append(", flagged ").append(flagged.size()).append(": ").append(String.join("; ", flagged));
            s.append('\n');
            if (art != null) s.append(art).append('\n');
            s.append("volume: ").append(drawn.hash(), 0, 16).append('\n');
            s.append("sheet: ").append(sheet.toAbsolutePath()).append('\n');
        }
        Files.writeString(a.out().resolve("bench.txt"), s);
        return new Answer(clean, s.toString());
    }

    /**
     * The beats (SPEC §72): how many lines were laid and how many fell back to the way of before, every line's beats, what
     * the composer said it couldn't do, and any move a line asks for that a walk doesn't need. Nothing for a land with none.
     */
    private static void beats(StringBuilder s, Land land) {
        if (land.lines().isEmpty()) return;
        int laid = 0, fell = 0;
        java.util.EnumMap<Land.Family, Integer> fams = new java.util.EnumMap<>(Land.Family.class);
        for (Land.Line l : land.lines()) {
            if (l.fallback()) fell++;
            else laid++;
            for (Land.Family f : l.families()) fams.merge(f, 1, Integer::sum);
        }
        List<String> needless = WalkCheck.needlessMoves(land);
        s.append("beats: ").append(laid).append(" lines, ").append(fell).append(" fell back, ").append(land.moves().size()).append(" moves, ")
                .append(land.holds().size()).append(" holds, ").append(needless.size()).append(" needless; families ").append(fams).append('\n');
        for (Land.Line l : land.lines()) s.append("  line ").append(l.leg()).append(l.fallback() ? " FELL BACK" : "").append(" at ").append(l.x0()).append(' ').append(l.y0())
                .append(' ').append(l.z0()).append(" to ").append(l.x1()).append(' ').append(l.y1()).append(' ').append(l.z1()).append(": ").append(String.join(", ", l.beats())).append('\n');
        if (land instanceof dev.awaken.gates.realms.kit.crawl.GeneratedCrawl<?> g) for (String n : g.beatRun().log()) s.append("  fallback ").append(n).append('\n');
        for (String n : needless) s.append("  needless ").append(n).append('\n');
    }

    /** More chunks than this and the three checks take turns (the lands of 420 to 460 half-size are about 3,500). */
    private static final int BIG_LAND = 6000;

    /** A report line's warnings are its words in capitals: FLAT, PLAIN, ALL-SKY... */
    private static final Pattern FLAG = Pattern.compile("[A-Z]{3,}(-[A-Z]+)*");

    /** The blueprint as it stands in the source tree, so a gate that isn't in the running game's data yet still checks. */
    private static Blueprint blueprint(String id, Path root) throws IOException {
        String[] at = id.split(":");
        Path file = root.resolve("src/main/resources/data").resolve(at[0]).resolve("realms").resolve(at[1] + ".json");
        if (!Files.exists(file)) return Blueprints.get(id).orElseThrow(() -> new IOException("no blueprint " + id + ": looked for " + file));
        return Blueprint.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file))).getOrThrow(why -> new IOException(file + ": " + why));
    }

    private static Land make(Land.Ctx ctx, Path root, ClassLoader lands) throws Exception {
        String program = ctx.bp().land().program();
        if (lands == null && LandPrograms.has(program)) return LandPrograms.make(ctx);
        Class<?> c = Class.forName(className(program, root), true, lands != null ? lands : Bench.class.getClassLoader());
        return (Land) c.getConstructor(Land.Ctx.class).newInstance(ctx);
    }

    /** A program's class, read from LandPrograms as it stands in the source tree; one not listed yet is its name in camel case. */
    static String className(String program, Path root) throws IOException {
        Path src = root.resolve("src/main/java/dev/awaken/gates/realms/LandPrograms.java");
        if (Files.exists(src)) {
            String text = Files.readString(src);
            Matcher m = Pattern.compile("\"" + Pattern.quote(program) + "\"\\s*,\\s*([\\w.]+)::new").matcher(text);
            if (m.find()) {
                String c = m.group(1);
                if (c.contains(".")) return c;
                Matcher imp = Pattern.compile("import\\s+([\\w.]+\\." + c + ")\\s*;").matcher(text);
                return imp.find() ? imp.group(1) : "dev.awaken.gates.realms.land." + c;
            }
        }
        StringBuilder camel = new StringBuilder();
        for (String part : program.substring(program.indexOf(':') + 1).split("_")) if (!part.isEmpty()) camel.append(Character.toUpperCase(part.charAt(0))).append(part, 1, part.length());
        return "dev.awaken.gates.realms.land." + camel;
    }

    /** What floats, gathered by what it is and the piece it's nearest, then the first dozen with where they are. */
    private static void floaters(StringBuilder s, Land land, FloatCheck.Report r) {
        s.append("floats: ").append(r.floaters().size()).append('\n');
        if (r.clean()) return;
        Map<String, Integer> groups = new LinkedHashMap<>();
        List<String> first = new ArrayList<>();
        V v = null;
        int seen = 0;
        for (FloatCheck.Floater f : r.floaters()) {
            if (seen++ == 4000) break; // enough to see the pattern; past this the land is plainly broken
            int ox = f.x() >> 4 << 4, oz = f.z() >> 4 << 4;
            if (v == null || v.x0 != ox || v.z0 != oz) {
                v = new V(ox, oz);
                land.paint(v);
            }
            BlockState state = v.get(f.x(), f.y(), f.z());
            String key = (state == null ? "air" : BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()) + " near " + nearest(land, f.x(), f.z());
            groups.merge(key, 1, Integer::sum);
            if (first.size() < 12) first.add(String.format(Locale.ROOT, "    at %d %d %d: %s, %s%s", f.x(), f.y(), f.z(), key, f.what(), f.blocks() > 1 ? " of " + f.blocks() + " blocks" : ""));
        }
        groups.entrySet().stream().sorted((p, q) -> q.getValue() - p.getValue()).limit(20).forEach(e -> s.append("  ").append(e.getValue()).append(" x ").append(e.getKey()).append('\n'));
        if (groups.size() > 20) s.append("  and ").append(groups.size() - 20).append(" more kinds\n");
        s.append("  the first ").append(first.size()).append(":\n");
        for (String line : first) s.append(line).append('\n');
    }

    private static String nearest(Land land, int x, int z) {
        String label = "no piece";
        double best = Double.MAX_VALUE;
        for (Piece<?> p : land.pieces()) {
            double dx = Math.max(0, Math.max(p.x0 - x, x - p.x1)), dz = Math.max(0, Math.max(p.z0 - z, z - p.z1)), d = dx * dx + dz * dz;
            if (d < best) {
                best = d;
                label = p.label;
            }
        }
        return label;
    }

    /** Kit sheet C's Settle keeps a tally of what it took away; this reads it when that class is there and says nothing when it isn't. */
    private static String settled(Land land) {
        try {
            Object tally = Class.forName("dev.awaken.gates.realms.kit.Settle").getMethod("lastTally", Land.class).invoke(null, land);
            String text = tally == null ? "" : tally.toString().strip();
            if (text.startsWith("settled")) text = text.substring(7).replaceFirst("^[: ]+", "");
            return text.isEmpty() ? null : text;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Every picture on one sheet, in the tour's order, each with its name (what tools/preview/sheet.py draws). */
    private static Path sheet(Path out, List<String> names) throws IOException {
        int cols = 4, w = 480, h = w * 9 / 16, rows = Math.max(1, (names.size() + cols - 1) / cols);
        BufferedImage[] tiles = new BufferedImage[names.size()];
        java.util.stream.IntStream.range(0, tiles.length).parallel().forEach(i -> {
            try {
                tiles[i] = ImageIO.read(out.resolve(names.get(i) + ".png").toFile());
            } catch (IOException e) {
                tiles[i] = null;
            }
        });
        BufferedImage sheet = new BufferedImage(cols * w, rows * h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(20, 16, 12));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for (int i = 0; i < tiles.length; i++) {
            int x = i % cols * w, y = i / cols * h;
            if (tiles[i] != null) g.drawImage(tiles[i], x, y, w, h, null);
            g.setColor(Color.BLACK);
            g.drawString(names.get(i), x + 7, y + 17);
            g.setColor(new Color(255, 240, 80));
            g.drawString(names.get(i), x + 6, y + 16);
        }
        g.dispose();
        Path file = out.resolve("sheet.png");
        ImageIO.write(sheet, "png", file.toFile());
        return file;
    }
}
