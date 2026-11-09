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
        }
    }

    private static void run(Path tour, Path out, Path root) throws IOException, NoSuchAlgorithmException {
        Files.createDirectories(out);
        List<String> lines = Files.readAllLines(tour);
        String id = null;
        Rank rank = Rank.E;
        for (String line : lines) {
            String[] w = line.trim().split("\\s+");
            if (w.length >= 6 && w[0].equals("cmd") && w[3].equals("realm")) {
                rank = Rank.valueOf(w[4]);
                id = w[5];
            }
        }
        if (id == null) throw new IOException("no 'cmd awaken gate realm RANK id' line in " + tour);
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
            } else if (w.length >= 4 && w[0].equals("cmd") && w[3].equals("realm_heart")) open = true;
            else if (w.length >= 3 && w[0].equals("look")) {
                yaw = Float.parseFloat(w[1]);
                pitch = Float.parseFloat(w[2]);
            } else if (w.length >= 2 && (w[0].equals("shot") || w[0].equals("shotnow")) && !w[1].equals("amber_crossing")) out.add(new Shot(w[1], x, y + 1.62, z, yaw, pitch, open));
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

        int kept() {
            int k = 0;
            for (Chunk c : chunks) if (c != null) k++;
            return k;
        }
    }

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
            StringBuilder flags = new StringBuilder();
            if (near[0] * 100 / total > 40) flags.append(" BLOCKED");
            if (skyN[0] * 100 / total > 75) flags.append(" ALL-SKY");
            if (top * 100 / hitN > 60) flags.append(" ONE-BLOCK");
            if (vanilla * 100 / hitN > 30) flags.append(" VANILLA");
            return String.format(Locale.ROOT, "%-26s near<5 %2d%%  sky %2d%%  top %s %d%%  vanilla %d%%%s", s.name, near[0] * 100 / total, skyN[0] * 100 / total, topId, top * 100 / hitN, vanilla * 100 / hitN, flags);
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

            double light;
            if (tx.glow != null && tx.glow[p] || h.look.lamp) light = 1.15;
            else {
                double ao = h.face == 6 ? 1 : ao(h);
                int[] n = h.face == 6 ? N[1] : N[h.face];
                boolean lit = !trace(h.x + n[0] * 0.02, h.y + n[1] * 0.02, h.z + n[2] * 0.02, SUN[0], SUN[1], SUN[2], 110, scratch, true);
                light = FACE[h.face] * (0.62 + (lit ? 0.38 : 0)) * (0.55 + 0.45 * ao);
            }
            double haze = 0.8 * smooth(fogStart, fogEnd, h.t);
            r = r * light;
            g = g * light;
            b = b * light;
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
