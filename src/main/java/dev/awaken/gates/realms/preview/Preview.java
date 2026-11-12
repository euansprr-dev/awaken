package dev.awaken.gates.realms.preview;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.gates.realms.Blueprint;
import dev.awaken.gates.realms.Blueprints;
import dev.awaken.gates.realms.Land;
import dev.awaken.gates.realms.RealmPlan;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.progression.Rank;
import net.minecraft.world.level.block.state.BlockState;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

/**
 * A gate's real blocks drawn from its tour's own cameras, in the time a GameTest takes to start and with no game client
 * (SPEC §50.8). Asked for through the environment, by tools/preview/preview.sh; does nothing otherwise. Also writes a hash
 * of every block the land paints, which proves a change to the kit left a land exactly as it was.
 */
public final class Preview {
    private Preview() {}

    private record Shot(String name, double x, double y, double z, float yaw, float pitch, boolean open) {}

    private static final int H = V.H;
    private static final double REACH = 900;
    private static final double[] SUN = norm(0.4, 0.85, 0.3);
    private static final double[] FACE = {0.5, 1.0, 0.8, 0.8, 0.6, 0.6, 0.9};
    private static final int[][] N = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    private static final Map<BlockState, Long> STABLE = new ConcurrentHashMap<>();
    private static final ThreadLocal<short[]> TMP = ThreadLocal.withInitial(() -> new short[16 * 16 * H]);

    public static void runIfAsked() {
        String tour = System.getenv("AWAKEN_PREVIEW");
        if (tour == null || tour.isBlank()) return;
        try {
            run(Path.of(tour), Path.of(System.getenv("AWAKEN_PREVIEW_OUT")), Path.of(System.getenv("AWAKEN_PREVIEW_ROOT")));
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException("the preview failed", e);
        } catch (RuntimeException | Error e) {
            e.printStackTrace(); // the test framework swallows the reason otherwise
            throw e;
        }
    }

    private static void run(Path tour, Path out, Path root) throws IOException, NoSuchAlgorithmException {
        Files.createDirectories(out);
        List<String> lines = Files.readAllLines(tour);
        String[] which = landOf(lines);
        if (which == null) throw new IOException("no 'cmd awaken gate realm RANK id' or 'realm_enter RANK id' line in " + tour);
        Rank rank = Rank.valueOf(which[0]);
        String id = which[1];
        Blueprint bp = Blueprints.get(id).orElseThrow(() -> new IOException("no blueprint"));

        String env = System.getenv("AWAKEN_PREVIEW_SEEDS");
        String[] seeds = (env == null || env.isBlank() ? "1" : env).split(",");
        StringBuilder sha = new StringBuilder();
        for (int i = 0; i < seeds.length; i++) {
            long seed = Long.parseLong(seeds[i].trim());
            long t0 = System.currentTimeMillis();
            Land land = RealmPlan.of(id, bp, rank, seed, RealmPlan.CURRENT_VERSION).land();
            List<Shot> shots = i == 0 ? shots(lines, land) : List.of();
            Volume vol = new Volume(land, shots);
            String hash = paint(land, vol);
            sha.append(seed).append(' ').append(hash).append('\n');
            System.out.println("[preview] seed " + seed + " painted in " + (System.currentTimeMillis() - t0) + " ms, " + vol.kept() + " chunks kept");
            if (i == 0) new Camera(land, bp, vol, root, out).all(shots);
        }
        Files.writeString(out.resolve("volume.sha"), sha);
    }

    /** Which Realm a tour walks: {rank, id} from its 'cmd awaken gate realm RANK id' or 'realm_enter RANK id' line, or null. */
    public static String[] landOf(List<String> lines) {
        String[] found = null;
        for (String line : lines) {
            String[] w = line.trim().split("\\s+");
            if (w.length >= 6 && w[0].equals("cmd") && (w[3].equals("realm") || w[3].equals("realm_enter"))) found = new String[]{w[4], w[5]};
        }
        return found;
    }

    /** What one drawing came to, for the bench: the hash of every block, the report's text, and where the time went. */
    public record Drawn(String hash, String report, int shots, long paintMs, long shotsMs) {}

    /**
     * The bench's way in (realms/bench): one land that's already made, drawn into {@code out} exactly as a preview is.
     * The cameras come from a tour's lines; or from one eye given outright ({x, y, z, yaw, pitch} in the land's own
     * coordinates); or, with neither, one look from the Mouth along the way you arrive.
     */
    public static Drawn draw(Land land, Blueprint bp, List<String> tourLines, String name, double[] eye, Path root, Path out) throws IOException, NoSuchAlgorithmException {
        Files.createDirectories(out);
        List<Shot> shots = eye != null ? List.of(new Shot(name, eye[0], eye[1], eye[2], (float) eye[3], (float) eye[4], false))
                : shots(tourLines != null ? tourLines : List.of("shot " + name), land);
        long t0 = System.currentTimeMillis();
        Volume vol = new Volume(land, shots);
        String hash = paint(land, vol);
        long t1 = System.currentTimeMillis();
        new Camera(land, bp, vol, root, out).all(shots);
        return new Drawn(hash, Files.readString(out.resolve("report.txt")), shots.size(), t1 - t0, System.currentTimeMillis() - t1);
    }

    /** The cameras, worked out the way the walk would: same commands, same sites, a standing eye height above each. */
    private static List<Shot> shots(List<String> lines, Land land) {
        Land.Site m = land.mouth();
        double x = m.x() + 0.5, y = m.y(), z = m.z() + 0.5;
        float yaw = land.mouthYaw(), pitch = 0;
        boolean open = false;
        List<Shot> out = new ArrayList<>();
        for (String line : lines) {
            String[] w = line.trim().split("\\s+");
            if (w.length >= 8 && w[0].equals("cmd") && w[3].equals("realm_at")) {
                var sites = land.sites(Land.SiteKind.valueOf(w[4].toUpperCase(Locale.ROOT)));
                int i = Integer.parseInt(w[5]);
                if (i >= sites.size()) {
                    System.out.println("[preview] " + w[4] + " has only " + sites.size());
                    continue;
                }
                Land.Site s = sites.get(i);
                x = s.x() + Integer.parseInt(w[6]) + 0.5;
                y = s.y() + Integer.parseInt(w[7]);
                z = s.z() + Integer.parseInt(w[8]) + 0.5;
            } else if (w.length >= 8 && w[0].equals("cmd") && w[3].equals("realm_leg")) {
                int back = Integer.parseInt(w[6]);
                double[] view = w[4].equalsIgnoreCase("first_fight") ? land.firstFightView(back)
                        : land.legView(Land.LegKind.valueOf(w[4].toUpperCase(Locale.ROOT)), Integer.parseInt(w[5]), back);
                if (view == null) {
                    System.out.println("[preview] this run has no " + w[4] + " leg " + w[5]);
                    continue;
                }
                x = Math.round(view[0]) + 0.5;
                y = view[1] + Integer.parseInt(w[7]);
                z = Math.round(view[2]) + 0.5;
                yaw = (float) view[3];
                pitch = back == 0 ? 18 : 10;
            } else if (w.length >= 4 && w[0].equals("cmd") && w[3].equals("realm_heart")) open = true;
            else if (w.length >= 3 && w[0].equals("look")) {
                yaw = Float.parseFloat(w[1]);
                pitch = Float.parseFloat(w[2]);
            } else if (w.length >= 2 && (w[0].equals("shot") || w[0].equals("shotnow")) && !w[1].endsWith("_crossing")) out.add(new Shot(w[1], x, y + 1.62, z, yaw, pitch, open));
        }
        return out;
    }

    private static String paint(Land land, Volume keep) throws NoSuchAlgorithmException {
        int half = land.halfSize() + 96 >> 4, n = 2 * half + 1;
        long[] hashes = new long[n * n];
        IntStream.range(0, n * n).parallel().forEach(i -> {
            int cx = i % n - half, cz = i / n - half;
            V v = new V(cx * 16, cz * 16);
            land.paint(v);
            long[] h = {1125899906842597L};
            short[] tmp = keep.wants(cx, cz) ? TMP.get() : null;
            int[] range = {H, -1};
            if (tmp != null) java.util.Arrays.fill(tmp, (short) 0);
            v.emit((x, y, z, s) -> {
                h[0] = 31 * (31 * h[0] + (x & 15) * 16 + (z & 15) + y * 256L) + STABLE.computeIfAbsent(s, Preview::stable);
                if (tmp != null) {
                    tmp[((x & 15) << 4 | z & 15) * H + y] = (short) keep.id(s);
                    range[0] = Math.min(range[0], y);
                    range[1] = Math.max(range[1], y);
                }
            });
            hashes[i] = h[0];
            if (tmp != null && range[1] >= 0) keep.put(cx, cz, tmp, range[0], range[1]);
        });
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        ByteBuffer buf = ByteBuffer.allocate(hashes.length * 8);
        for (long h : hashes) buf.putLong(h);
        return java.util.HexFormat.of().formatHex(md.digest(buf.array()));
    }

    private static long stable(BlockState s) {
        long h = 0xcbf29ce484222325L;
        for (char c : s.toString().toCharArray()) h = (h ^ c) * 0x100000001b3L;
        return h;
    }

    private static double[] norm(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        return new double[]{x / l, y / l, z / l};
    }

    private static double smooth(double a, double b, double x) {
        double t = Math.min(1, Math.max(0, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** The painted blocks near any camera, as palette indices, each chunk only as tall as what's in it. */
    private static final class Volume {
        static final class Chunk {
            final int y0, y1;
            final short[] b;
            /** Light is only stored where there is some: block light near a lamp, sky light under a roof. */
            byte[] block, sky;
            /** Per column, the lowest cell the sky reaches straight down; everything from there up is sky light 15. */
            short[] open;

            Chunk(int y0, int y1, short[] b) {
                this.y0 = y0;
                this.y1 = y1;
                this.b = b;
            }
        }

        final int half, n;
        final Chunk[] chunks;
        final List<Shot> shots;
        final Map<BlockState, Integer> ids = new ConcurrentHashMap<>();
        final List<BlockState> states = new ArrayList<>();

        Volume(Land land, List<Shot> shots) {
            half = land.halfSize() + 96 >> 4;
            n = 2 * half + 1;
            chunks = new Chunk[n * n];
            this.shots = shots;
        }

        boolean wants(int cx, int cz) {
            double r = REACH + 24;
            for (Shot s : shots) {
                double dx = cx * 16 + 8 - s.x, dz = cz * 16 + 8 - s.z;
                if (dx * dx + dz * dz < r * r) return true;
            }
            return false;
        }

        int id(BlockState s) {
            return ids.computeIfAbsent(s, k -> {
                synchronized (states) {
                    states.add(k);
                    return states.size();
                }
            });
        }

        void put(int cx, int cz, short[] full, int y0, int y1) {
            int h = y1 - y0 + 1;
            short[] b = new short[256 * h];
            for (int c = 0; c < 256; c++) System.arraycopy(full, c * H + y0, b, c * h, h);
            chunks[(cz + half) * n + cx + half] = new Chunk(y0, y1, b);
        }

        Chunk chunk(int cx, int cz) {
            int i = cx + half, j = cz + half;
            return i < 0 || j < 0 || i >= n || j >= n ? null : chunks[j * n + i];
        }

        int get(int x, int y, int z) {
            Chunk c = chunk(x >> 4, z >> 4);
            if (c == null || y < c.y0 || y > c.y1) return 0;
            return c.b[((x & 15) << 4 | z & 15) * (c.y1 - c.y0 + 1) + y - c.y0] & 0xFFFF;
        }

        void set(int x, int y, int z, int id) {
            Chunk c = chunk(x >> 4, z >> 4);
            if (c != null && y >= c.y0 && y <= c.y1) c.b[((x & 15) << 4 | z & 15) * (c.y1 - c.y0 + 1) + y - c.y0] = (short) id;
        }

        /** The candlelight and skylight at a cell, 0 to 15, as the game's light engine would have them. */
        int light(int x, int y, int z, boolean skyLight) {
            Chunk c = chunk(x >> 4, z >> 4);
            if (c == null || y < c.y0 || y > c.y1) return skyLight ? 15 : 0;
            int col = (x & 15) << 4 | z & 15;
            if (skyLight && c.open != null && y >= c.open[col]) return 15;
            byte[] l = skyLight ? c.sky : c.block;
            return l == null ? 0 : l[col * (c.y1 - c.y0 + 1) + y - c.y0];
        }

        /**
         * Spreads a land's light through its air, block by block: out from every lamp, then down every open column and in
         * under every roof. Only the cells at the edge of a shadow start the sky's spread, as in the game, so an open
         * land a kilometre wide costs no more than its roofs.
         */
        void flood(boolean[] opaque, int[] emit) {
            long[] q = new long[1 << 22];
            int tail = 0;
            for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
                Chunk c = chunks[j * n + i];
                if (c == null) continue;
                int h = c.y1 - c.y0 + 1, cx = i - half, cz = j - half;
                c.open = new short[256];
                for (int col = 0; col < 256; col++) {
                    int open = c.y1 + 1;
                    for (int y = c.y1; y >= c.y0; y--) {
                        int id = c.b[col * h + y - c.y0] & 0xFFFF;
                        if (id != 0 && opaque[id]) break;
                        open = y;
                    }
                    c.open[col] = (short) open;
                    for (int y = c.y0; y <= c.y1; y++) {
                        int id = c.b[col * h + y - c.y0] & 0xFFFF;
                        if (id == 0 || emit[id] <= 0) continue;
                        if (c.block == null) c.block = new byte[256 * h];
                        c.block[col * h + y - c.y0] = (byte) emit[id];
                        if (tail < q.length - 1) q[tail++] = pack(cx * 16 + (col >> 4), y, cz * 16 + (col & 15));
                    }
                }
            }
            spread(q, tail, opaque, false);
            tail = 0;
            for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
                Chunk c = chunks[j * n + i];
                if (c == null) continue;
                int h = c.y1 - c.y0 + 1, cx = i - half, cz = j - half;
                for (int col = 0; col < 256; col++) {
                    int x = cx * 16 + (col >> 4), z = cz * 16 + (col & 15);
                    // a shadowed cell beside a column the sky reaches lower down takes 14 from it
                    int from = Math.min(Math.min(openAt(x - 1, z), openAt(x + 1, z)), Math.min(openAt(x, z - 1), openAt(x, z + 1)));
                    for (int y = Math.max(from, c.y0); y < c.open[col]; y++) {
                        int id = c.b[col * h + y - c.y0] & 0xFFFF;
                        if (id != 0 && opaque[id]) continue;
                        if (y < openAt(x - 1, z) && y < openAt(x + 1, z) && y < openAt(x, z - 1) && y < openAt(x, z + 1)) continue;
                        if (c.sky == null) c.sky = new byte[256 * h];
                        c.sky[col * h + y - c.y0] = 14;
                        if (tail < q.length - 1) q[tail++] = pack(x, y, z);
                    }
                }
            }
            spread(q, tail, opaque, true);
        }

        /** The lowest cell of a column the sky reaches; a column nobody kept is treated as never lit. */
        private int openAt(int x, int z) {
            Chunk c = chunk(x >> 4, z >> 4);
            return c == null ? Integer.MAX_VALUE : c.open[(x & 15) << 4 | z & 15];
        }

        private void spread(long[] q, int tail, boolean[] opaque, boolean skyLight) {
            int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
            int head = 0;
            while (head != tail) {
                long p = q[head];
                head = (head + 1) % q.length;
                int x = (int) (p >> 42) - (1 << 20), y = (int) (p >> 21 & 0x1FFFFF) - (1 << 20), z = (int) (p & 0x1FFFFF) - (1 << 20);
                int level = light(x, y, z, skyLight);
                if (level <= 1) continue;
                for (int[] d : dirs) {
                    int ax = x + d[0], ay = y + d[1], az = z + d[2];
                    Chunk c = chunk(ax >> 4, az >> 4);
                    if (c == null || ay < c.y0 || ay > c.y1) continue;
                    int col = (ax & 15) << 4 | az & 15, h = c.y1 - c.y0 + 1, k = col * h + ay - c.y0, id = c.b[k] & 0xFFFF;
                    if (id != 0 && opaque[id] || skyLight && ay >= c.open[col]) continue;
                    byte[] l = skyLight ? c.sky : c.block;
                    if (l == null) {
                        l = new byte[256 * h];
                        if (skyLight) c.sky = l;
                        else c.block = l;
                    }
                    if (l[k] >= level - 1) continue;
                    l[k] = (byte) (level - 1);
                    int next = (tail + 1) % q.length;
                    if (next != head) {
                        q[tail] = pack(ax, ay, az);
                        tail = next;
                    }
                }
            }
        }

        private static long pack(int x, int y, int z) {
            return (long) (x + (1 << 20)) << 42 | (long) (y + (1 << 20)) << 21 | z + (1 << 20);
        }

        int kept() {
            int k = 0;
            for (Chunk c : chunks) if (c != null) k++;
            return k;
        }
    }

    /** Thresholds the first two gates set: see docs/gates-as-worlds/BAR.md. */
    private static final int FLAT_WALL = 25, MIN_RELIEF = 40, MIN_SHAPED = 4, MIN_BRIGHT_CONTRAST = 115;
    /** A view too dark to play in: its median brightness under this, or more than this share of it nearly black. */
    private static final int BLACK_LUM = 25, BLACK_DARK = 60;
    private static final java.util.regex.Pattern SHAPED = java.util.regex.Pattern.compile("_stairs|_slab|_wall$|_fence|_bars|chain|lantern|candle|_trapdoor|_door|_vines|fern|grass|_carpet|_drift|_crumbs|bones|_petals|_rope|_lily|_drip|_flower|_rail|_comb_|_shelf|reed|_cap|_arch|_flute|_rib|_corbel|_link");

    private static final class Hit {
        double t, u, v, x, y, z;
        int face, ix, iy, iz;
        Looks.Look look;
    }

    /** Casts a ray a pixel through the block grid and shades what it finds; plain enough to read a build by, no more. */
    private static final class Camera {
        final Land land;
        final Volume vol;
        final Path out;
        final Looks looks;
        final Looks.Look[] table;
        final int w, h;
        final int[] sky, fog;
        final double fogStart, fogEnd;
        final boolean indoors;
        /** The game's light for every block and sky level, {@code block * 16 + sky}. */
        final float[][] light;

        Camera(Land land, Blueprint bp, Volume vol, Path root, Path out) throws IOException {
            this.land = land;
            this.vol = vol;
            this.out = out;
            String[] biome = bp.sky().biomes().getFirst().split(":");
            JsonObject b = JsonParser.parseString(Files.readString(root.resolve("src/main/resources/data").resolve(biome[0]).resolve("worldgen/biome").resolve(biome[1] + ".json"))).getAsJsonObject();
            JsonObject fx = b.getAsJsonObject("effects"), at = b.getAsJsonObject("attributes");
            looks = new Looks(List.of(root.resolve("src/main/resources/assets"), Path.of("/Volumes/MEDIA/Awaken/.ref/assets/assets")),
                    colour(fx, "grass_color", 0x91BD59), colour(fx, "foliage_color", 0x77AB2F), colour(fx, "water_color", 0x3F76E4));
            int s = colour(at, "minecraft:visual/sky_color", 0x78A7FF), f = colour(at, "minecraft:visual/fog_color", 0xC0D8FF);
            sky = new int[]{s >> 16 & 255, s >> 8 & 255, s & 255};
            fog = new int[]{f >> 16 & 255, f >> 8 & 255, f & 255};
            fogStart = at.has("minecraft:visual/fog_start_distance") ? at.get("minecraft:visual/fog_start_distance").getAsDouble() : 96;
            fogEnd = at.has("minecraft:visual/fog_end_distance") ? at.get("minecraft:visual/fog_end_distance").getAsDouble() : 900;
            table = new Looks.Look[65536];
            for (int i = 0; i < vol.states.size(); i++) table[i + 1] = looks.of(vol.states.get(i));
            indoors = land.indoors();
            Path dim = root.resolve("src/main/resources/data/awaken/dimension_type/realms.json");
            JsonObject realms = Files.exists(dim) ? JsonParser.parseString(Files.readString(dim)).getAsJsonObject().getAsJsonObject("attributes") : null;
            light = TrueLight.table(TrueLight.env(at, realms));
            boolean[] opaque = new boolean[65536];
            int[] emit = new int[65536];
            for (int i = 0; i < vol.states.size(); i++) {
                opaque[i + 1] = table[i + 1] != null && table[i + 1].solid;
                emit[i + 1] = vol.states.get(i).getLightEmission();
            }
            long t0 = System.currentTimeMillis();
            vol.flood(opaque, emit);
            System.out.println("[preview] light spread in " + (System.currentTimeMillis() - t0) + " ms");
            String size = System.getenv("AWAKEN_PREVIEW_W");
            w = size == null || size.isBlank() ? 960 : Integer.parseInt(size);
            h = w * 9 / 16;
        }

        private static int colour(JsonObject o, String key, int fallback) {
            if (o == null || !o.has(key) || !o.get(key).isJsonPrimitive()) return fallback;
            String v = o.get(key).getAsString();
            return v.startsWith("#") ? Integer.parseInt(v.substring(1), 16) : o.get(key).getAsInt();
        }

        void all(List<Shot> shots) throws IOException {
            StringBuilder report = new StringBuilder();
            boolean opened = false;
            for (Shot s : shots) {
                if (s.open && !opened) {
                    opened = true;
                    for (Land.Edit e : land.change("heart_open")) vol.set(e.x(), e.y(), e.z(), e.state() == null || e.state().isAir() ? 0 : vol.id(e.state()));
                    for (int i = 0; i < vol.states.size(); i++) if (table[i + 1] == null) table[i + 1] = looks.of(vol.states.get(i));
                }
                long t0 = System.currentTimeMillis();
                report.append(one(s)).append(String.format(Locale.ROOT, "  %d ms%n", System.currentTimeMillis() - t0));
            }
            if (!looks.missing.isEmpty()) report.append("no art found: ").append(looks.missing).append('\n');
            Files.writeString(out.resolve("report.txt"), report);
            System.out.print("[preview]\n" + report);
        }

        private String one(Shot s) throws IOException {
            double yaw = Math.toRadians(s.yaw), pit = Math.toRadians(s.pitch);
            double sy = Math.sin(yaw), cy = Math.cos(yaw), sp = Math.sin(pit), cp = Math.cos(pit);
            double[] f = {-sy * cp, -sp, cy * cp}, r = {-cy, 0, -sy}, u = {-sy * sp, cp, cy * sp};
            double th = Math.tan(Math.toRadians(35)), asp = (double) w / h;
            int[] px = new int[w * h];
            int[] counts = new int[vol.states.size() + 2];
            int[] near = new int[1], skyN = new int[1];
            int[] plane = new int[w * h];
            IntStream.range(0, h).parallel().forEach(j -> {
                Hit hit = new Hit(), shade = new Hit();
                for (int i = 0; i < w; i++) {
                    double nx = (2 * (i + 0.5) / w - 1) * th * asp, ny = (1 - 2 * (j + 0.5) / h) * th;
                    double[] d = norm(f[0] + r[0] * nx + u[0] * ny, f[1] + r[1] * nx + u[1] * ny, f[2] + r[2] * nx + u[2] * ny);
                    if (!trace(s.x, s.y, s.z, d[0], d[1], d[2], REACH, hit, false)) {
                        px[j * w + i] = skyAt(d[1]);
                        synchronized (skyN) { skyN[0]++; }
                        continue;
                    }
                    px[j * w + i] = shade(hit, shade);
                    plane[j * w + i] = planeOf(hit);
                    synchronized (counts) {
                        counts[vol.get(hit.ix, hit.iy, hit.iz)]++;
                        if (hit.t < 5) near[0]++;
                    }
                }
            });
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            img.setRGB(0, 0, w, h, px, 0, w);
            ImageIO.write(img, "png", out.resolve(s.name + ".png").toFile());

            int total = w * h, top = 0, vanilla = 0;
            Map<String, Integer> byBlock = new java.util.HashMap<>();
            for (int i = 1; i < counts.length; i++) if (counts[i] > 0) {
                String id = table[i] == null ? "?" : table[i].id;
                byBlock.merge(id, counts[i], Integer::sum);
                if (id.startsWith("minecraft:")) vanilla += counts[i];
            }
            String topId = "-";
            for (var e : byBlock.entrySet()) if (e.getValue() > top) {
                top = e.getValue();
                topId = e.getKey();
            }
            int hitN = Math.max(1, total - skyN[0]);
            int shaped = 0;
            for (int i = 1; i < counts.length; i++) if (counts[i] > 0 && table[i] != null && SHAPED.matcher(table[i].id).find()) shaped += counts[i];
            int[] depth = depth(plane, px, w, h);
            int shapedPct = shaped * 100 / hitN;
            StringBuilder flags = new StringBuilder();
            if (near[0] * 100 / total > 40) flags.append(" BLOCKED");
            if (skyN[0] * 100 / total > 75) flags.append(" ALL-SKY");
            if (top * 100 / hitN > 60) flags.append(" ONE-BLOCK");
            if (vanilla * 100 / hitN > 30) flags.append(" VANILLA");
            if (depth[1] > FLAT_WALL) flags.append(" FLAT");
            if (depth[0] < MIN_RELIEF || shapedPct < MIN_SHAPED) flags.append(" PLAIN");
            if (depth[3] > 60 && depth[2] < MIN_BRIGHT_CONTRAST) flags.append(" WASHED");
            if (depth[3] < BLACK_LUM || depth[4] > BLACK_DARK) flags.append(" BLACK");
            flags.insert(0, String.format(Locale.ROOT, "  relief %d wall %d shaped %d contrast %d  lum %d dark %d bright %d pool %d", depth[0], depth[1], shapedPct, depth[2], depth[3], depth[4], depth[5], depth[6]));
            return String.format(Locale.ROOT, "%-26s near<5 %2d%%  sky %2d%%  top %s %d%%  vanilla %d%%%s", s.name, near[0] * 100 / total, skyN[0] * 100 / total, topId, top * 100 / hitN, vanilla * 100 / hitN, flags);
        }

        /** Which flat plane of the world a pixel lies on; 0 for sky. */
        private static int planeOf(Hit h) {
            int c = h.face <= 1 ? h.iy : h.face <= 3 ? h.iz : h.face <= 5 ? h.ix : h.ix * 31 + h.iz;
            return 1 + ((h.face / 2) << 24 ^ (c & 0xFFFFF));
        }

        /**
         * How much the view actually steps in and out. relief: of every hundred neighbouring pixels, how many cross from one
         * plane to another. wall: the share of the view on the single biggest upright plane. contrast: the spread of brightness
         * across what isn't sky. A view of big flat walls in even light scores low on the first and third and high on the second.
         * Then the light itself, over the same pixels: their median brightness (0 to 255), the share under 20 and over 200, and pool, the
         * share over 90: how much of a dark place its lights reach. (bright only counts lamps and near-white things, candle sticks mostly.)
         */
        private static int[] depth(int[] plane, int[] px, int w, int h) {
            long pairs = 0, steps = 0;
            Map<Integer, Integer> upright = new java.util.HashMap<>();
            int hits = 0;
            int[] hist = new int[256];
            for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) {
                int a = plane[j * w + i];
                if (a == 0) continue;
                hits++;
                int c = px[j * w + i];
                hist[(((c >> 16) & 255) * 30 + ((c >> 8) & 255) * 59 + (c & 255) * 11) / 100]++;
                int axis = (a - 1) >> 24;
                if (axis == 1 || axis == 2) upright.merge(a, 1, Integer::sum);
                if (i + 1 < w && plane[j * w + i + 1] != 0) {
                    pairs++;
                    if (plane[j * w + i + 1] != a) steps++;
                }
                if (j + 1 < h && plane[(j + 1) * w + i] != 0) {
                    pairs++;
                    if (plane[(j + 1) * w + i] != a) steps++;
                }
            }
            if (hits == 0) return new int[]{100, 0, 255, 0, 0, 0, 0};
            int big = 0;
            for (int n : upright.values()) big = Math.max(big, n);
            int lo = -1, hi = 0, run = 0, median = -1, dark = 0, bright = 0, pool = 0;
            for (int l = 0; l < 256; l++) {
                run += hist[l];
                if (l < 20) dark += hist[l];
                if (l > 200) bright += hist[l];
                if (l > 90) pool += hist[l];
                if (lo < 0 && run * 100L >= hits * 5L) lo = l;
                if (median < 0 && run * 2L >= hits) median = l;
                if (run * 100L <= hits * 95L) hi = l;
            }
            return new int[]{(int) (steps * 1000 / Math.max(1, pairs)), big * 100 / hits, hi - lo, median, (int) (dark * 100L / hits), (int) (bright * 100L / hits), (int) (pool * 100L / hits)};
        }

        private int skyAt(double dy) {
            double k = smooth(-0.05, 0.55, dy);
            return (int) (fog[0] + (sky[0] - fog[0]) * k) << 16 | (int) (fog[1] + (sky[1] - fog[1]) * k) << 8 | (int) (fog[2] + (sky[2] - fog[2]) * k);
        }

        private int shade(Hit h, Hit scratch) {
            Looks.Tex tx = h.look.faces[h.face == 6 ? 2 : h.face];
            if (tx == null) return 0xFF00FF;
            int p = texel(tx, h.u, h.v);
            int c = tx.px[p];
            double r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
            // a texture read one texel a block away is noise; far off it's only its average colour
            double m = smooth(28, 110, h.t);
            r += (tx.r - r) * m;
            g += (tx.g - g) * m;
            b += (tx.b - b) * m;

            double haze = 0.8 * smooth(fogStart, fogEnd, h.t);
            if (tx.glow != null && tx.glow[p] || h.look.lamp) {
                r *= 1.15;
                g *= 1.15;
                b *= 1.15;
            } else {
                // what the game shows: texture x its lightmap x the face's shade, with the corners softened
                int[] n = h.face == 6 ? N[1] : N[h.face];
                int bx = h.ix + n[0], by = h.iy + n[1], bz = h.iz + n[2];
                int block = vol.light(bx, by, bz, false), sky = vol.light(bx, by, bz, true);
                if (!h.look.solid) {
                    // a slab, a fence or a plant sits in its own lit cell
                    block = Math.max(block, vol.light(h.ix, h.iy, h.iz, false));
                    sky = Math.max(sky, vol.light(h.ix, h.iy, h.iz, true));
                }
                float[] l = light[block * 16 + sky];
                double ao = h.face == 6 ? 1 : ao(h), f = FACE[h.face] * (0.55 + 0.45 * ao);
                // the sun's own shadow isn't in the game's lightmap: kept out of doors, under open sky, to read a build by
                if (!indoors && sky == 15 && trace(h.x + n[0] * 0.02, h.y + n[1] * 0.02, h.z + n[2] * 0.02, SUN[0], SUN[1], SUN[2], 110, scratch, true)) f *= 0.62;
                r *= f * l[0];
                g *= f * l[1];
                b *= f * l[2];
            }
            r += (fog[0] - r) * haze;
            g += (fog[1] - g) * haze;
            b += (fog[2] - b) * haze;
            return clamp(r) << 16 | clamp(g) << 8 | clamp(b);
        }

        private static int clamp(double v) {
            return v < 0 ? 0 : v > 255 ? 255 : (int) v;
        }

        private double ao(Hit h) {
            int[] n = N[h.face];
            int px = h.ix + n[0], py = h.iy + n[1], pz = h.iz + n[2];
            double lx = h.x - Math.floor(h.x), ly = h.y - Math.floor(h.y), lz = h.z - Math.floor(h.z);
            int[] a, b;
            double fa, fb;
            if (n[1] != 0) {
                a = N[5];
                b = N[3];
                fa = lx;
                fb = lz;
            } else if (n[2] != 0) {
                a = N[5];
                b = N[1];
                fa = lx;
                fb = ly;
            } else {
                a = N[3];
                b = N[1];
                fa = lz;
                fb = ly;
            }
            double o = 0;
            for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) {
                if (i == 0 && j == 0 || !solid(px + a[0] * i + b[0] * j, py + a[1] * i + b[1] * j, pz + a[2] * i + b[2] * j)) continue;
                double wa = i == 0 ? 1 : i < 0 ? 1 - fa : fa, wb = j == 0 ? 1 : j < 0 ? 1 - fb : fb;
                double w = wa * wb;
                o += w * w * (i != 0 && j != 0 ? 0.7 : 1);
            }
            return 1 - Math.min(1, o * 0.75);
        }

        private boolean solid(int x, int y, int z) {
            int id = vol.get(x, y, z);
            return id != 0 && table[id] != null && table[id].solid;
        }

        private static int texel(Looks.Tex t, double u, double v) {
            int x = Math.min(t.size - 1, Math.max(0, (int) (u * t.size))), y = Math.min(t.size - 1, Math.max(0, (int) (v * t.size)));
            return y * t.size + x;
        }

        private boolean trace(double ox, double oy, double oz, double dx, double dy, double dz, double reach, Hit h, boolean shadow) {
            int ix = (int) Math.floor(ox), iy = (int) Math.floor(oy), iz = (int) Math.floor(oz);
            int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
            double tdx = dx == 0 ? 1e30 : Math.abs(1 / dx), tdy = dy == 0 ? 1e30 : Math.abs(1 / dy), tdz = dz == 0 ? 1e30 : Math.abs(1 / dz);
            double tmx = dx == 0 ? 1e30 : (dx > 0 ? ix + 1 - ox : ox - ix) * tdx;
            double tmy = dy == 0 ? 1e30 : (dy > 0 ? iy + 1 - oy : oy - iy) * tdy;
            double tmz = dz == 0 ? 1e30 : (dz > 0 ? iz + 1 - oz : oz - iz) * tdz;
            double t = 0;
            int face = -1, ccx = Integer.MIN_VALUE, ccz = Integer.MIN_VALUE;
            Volume.Chunk c = null;
            while (t < reach) {
                if (iy >= H && dy >= 0 || iy < 0 && dy <= 0) return false;
                int cx = ix >> 4, cz = iz >> 4;
                if (cx != ccx || cz != ccz) {
                    ccx = cx;
                    ccz = cz;
                    c = vol.chunk(cx, cz);
                }
                if (c != null && iy >= c.y0 && iy <= c.y1) {
                    int id = c.b[((ix & 15) << 4 | iz & 15) * (c.y1 - c.y0 + 1) + iy - c.y0] & 0xFFFF;
                    if (id != 0 && table[id] != null && table[id].kind != Looks.AIR && touch(table[id], ix, iy, iz, ox, oy, oz, dx, dy, dz, t, face, h, shadow)) return true;
                }
                if (tmx < tmy && tmx < tmz) {
                    t = tmx;
                    tmx += tdx;
                    ix += sx;
                    face = sx > 0 ? 4 : 5;
                } else if (tmy < tmz) {
                    t = tmy;
                    tmy += tdy;
                    iy += sy;
                    face = sy > 0 ? 0 : 1;
                } else {
                    t = tmz;
                    tmz += tdz;
                    iz += sz;
                    face = sz > 0 ? 2 : 3;
                }
            }
            return false;
        }

        private boolean touch(Looks.Look l, int ix, int iy, int iz, double ox, double oy, double oz, double dx, double dy, double dz, double t, int face, Hit h, boolean shadow) {
            double lx = ox - ix, ly = oy - iy, lz = oz - iz;
            switch (l.kind) {
                case Looks.FULL -> {
                    if (face < 0) return false;
                    if (shadow && !l.solid && l.faces[1] != null && l.faces[1].cut) return false;
                    return accept(l, ix, iy, iz, lx + dx * t, ly + dy * t, lz + dz * t, t, face, h);
                }
                case Looks.BOX -> {
                    double best = 1e30;
                    int bestFace = -1;
                    for (double[] b : l.boxes) {
                        double t0 = 0, t1 = 1e30;
                        int f = -1;
                        double[] o = {lx, ly, lz}, d = {dx, dy, dz};
                        boolean miss = false;
                        for (int a = 0; a < 3 && !miss; a++) {
                            if (d[a] == 0) {
                                if (o[a] < b[a] || o[a] > b[a + 3]) miss = true;
                                continue;
                            }
                            double n = (b[a] - o[a]) / d[a], m = (b[a + 3] - o[a]) / d[a];
                            int nf = d[a] > 0 ? AXIS[a][0] : AXIS[a][1];
                            if (n > m) {
                                double s = n;
                                n = m;
                                m = s;
                            }
                            if (n > t0) {
                                t0 = n;
                                f = nf;
                            }
                            t1 = Math.min(t1, m);
                            if (t0 > t1) miss = true;
                        }
                        if (!miss && f >= 0 && t0 < best) {
                            best = t0;
                            bestFace = f;
                        }
                    }
                    if (bestFace < 0) return false;
                    return accept(l, ix, iy, iz, lx + dx * best, ly + dy * best, lz + dz * best, best, bestFace, h);
                }
                case Looks.CROSS -> {
                    if (shadow) return false;
                    double best = 1e30, bu = 0, bv = 0;
                    double a = dx - dz, b = dx + dz;
                    Looks.Tex tx = l.faces[2];
                    if (tx == null) return false;
                    double[] ts = {a == 0 ? -1 : (lz - lx) / a, b == 0 ? -1 : (1 - lx - lz) / b};
                    for (double q : ts) {
                        if (q < t || q >= best) continue;
                        double px = lx + dx * q, py = ly + dy * q, pz = lz + dz * q;
                        if (px < 0 || px > 1 || py < 0 || py > 1 || pz < 0 || pz > 1) continue;
                        if (tx.px[texel(tx, px, 1 - py)] >>> 24 < 128) continue;
                        best = q;
                        bu = px;
                        bv = 1 - py;
                    }
                    if (best == 1e30) return false;
                    h.t = best;
                    h.face = 6;
                    h.u = bu;
                    h.v = bv;
                    h.look = l;
                    h.ix = ix;
                    h.iy = iy;
                    h.iz = iz;
                    h.x = ix + lx + dx * best;
                    h.y = iy + ly + dy * best;
                    h.z = iz + lz + dz * best;
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }

        private static final int[][] AXIS = {{4, 5}, {0, 1}, {2, 3}};

        private boolean accept(Looks.Look l, int ix, int iy, int iz, double x, double y, double z, double t, int face, Hit h) {
            Looks.Tex tx = l.faces[face];
            double u, v;
            switch (face) {
                case 0 -> {
                    u = x;
                    v = 1 - z;
                }
                case 1 -> {
                    u = x;
                    v = z;
                }
                case 2 -> {
                    u = 1 - x;
                    v = 1 - y;
                }
                case 3 -> {
                    u = x;
                    v = 1 - y;
                }
                case 4 -> {
                    u = z;
                    v = 1 - y;
                }
                default -> {
                    u = 1 - z;
                    v = 1 - y;
                }
            }
            if (tx != null && tx.cut && tx.px[texel(tx, u, v)] >>> 24 < 128) return false;
            h.t = t;
            h.face = face;
            h.u = u;
            h.v = v;
            h.look = l;
            h.ix = ix;
            h.iy = iy;
            h.iz = iz;
            h.x = ix + x;
            h.y = iy + y;
            h.z = iz + z;
            return true;
        }
    }
}
