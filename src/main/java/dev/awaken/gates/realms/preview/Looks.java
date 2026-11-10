package dev.awaken.gates.realms.preview;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * What each block state looks like to the preview, read straight from the asset files: the game's client isn't running,
 * so its baked models aren't there. Good enough to judge shape and tone, not a stand-in for the real renderer.
 */
final class Looks {
    static final int AIR = 0, FULL = 1, BOX = 2, CROSS = 3;
    private static final String[] DIRS = {"down", "up", "north", "south", "west", "east"};
    private static final int[][] N = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    static final class Tex {
        int size;
        int[] px;
        boolean[] glow;
        boolean cut;
        int r, g, b;
    }

    static final class Look {
        String id;
        int kind;
        boolean solid, lamp;
        double[][] boxes;
        final Tex[] faces = new Tex[6];
    }

    private record Model(Map<String, String> textures, JsonArray elements) {}

    private record Layer(Tex tex, int tint, boolean glow) {}

    private final List<Path> bases;
    private final int grass, foliage, water;
    private final Map<String, Model> models = new HashMap<>();
    private final Map<String, Tex> textures = new HashMap<>();
    final TreeSet<String> missing = new TreeSet<>();

    Looks(List<Path> bases, int grass, int foliage, int water) {
        this.bases = bases;
        this.grass = grass;
        this.foliage = foliage;
        this.water = water;
    }

    Look of(BlockState s) {
        Look l = new Look();
        l.id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
        boolean fluid = s.getBlock() instanceof LiquidBlock;
        VoxelShape shape = s.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        if (!fluid && (shape.isEmpty() || s.getRenderShape() == RenderShape.INVISIBLE && !s.hasBlockEntity())) return l;

        if (fluid) l.kind = FULL;
        else if (s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty() && !s.canOcclude()) l.kind = CROSS;
        else if (Block.isShapeFullBlock(shape)) l.kind = FULL;
        else {
            l.kind = BOX;
            List<AABB> boxes = shape.toAabbs();
            l.boxes = new double[boxes.size()][];
            for (int i = 0; i < boxes.size(); i++) {
                AABB a = boxes.get(i);
                l.boxes[i] = new double[]{a.minX, a.minY, a.minZ, a.maxX, a.maxY, a.maxZ};
            }
        }
        l.solid = l.kind == FULL && !fluid && s.canOcclude();
        l.lamp = s.getLightEmission() >= 10;

        try {
            faces(l, s, fluid);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return l;
    }

    private void faces(Look l, BlockState s, boolean fluid) throws IOException {
        String[] id = l.id.split(":");
        if (fluid) {
            boolean w = id[1].equals("water");
            Tex t = tex("minecraft:block/" + (w ? "water_still" : "lava_still"));
            Tex tinted = w ? tinted(t, water) : t;
            for (int f = 0; f < 6; f++) l.faces[f] = tinted;
            return;
        }
        JsonObject bs = json(id[0], "blockstates/" + id[1] + ".json");
        JsonObject v = bs == null ? null : pick(bs, props(s));
        if (v == null) {
            missing.add(l.id);
            return;
        }
        Model m = model(v.get("model").getAsString());
        int rx = v.has("x") ? v.get("x").getAsInt() : 0, ry = v.has("y") ? v.get("y").getAsInt() : 0;
        if (l.kind == CROSS) {
            double[][] boxes = plainBoxes(m, ry);
            if (boxes != null) {
                l.kind = BOX;
                l.boxes = boxes;
            }
        }
        // a cluster of candles collides as one block but is drawn as thin sticks: show the sticks, or it reads as a white crate
        if (l.kind == BOX && id[1].endsWith("_candles")) l.boxes = sticks(m);
        boolean leafy = id[1].contains("leaves") || id[1].contains("vine");
        for (int f = 0; f < 6; f++) l.faces[f] = composite(m, modelDir(f, rx, ry), leafy ? foliage : grass);
    }

    /** The boxes of a small walk-through prop (a candle, a lamp), or null for a real plant: crossed flat planes or tilted ones. */
    private static double[][] plainBoxes(Model m, int ry) {
        if (m.elements() == null || m.elements().isEmpty()) return null;
        double[][] out = new double[m.elements().size()][];
        for (int i = 0; i < out.length; i++) {
            JsonObject el = m.elements().get(i).getAsJsonObject();
            if (el.has("rotation")) return null;
            JsonArray a = el.getAsJsonArray("from"), b = el.getAsJsonArray("to");
            double[] box = new double[6];
            for (int k = 0; k < 3; k++) {
                if (b.get(k).getAsDouble() - a.get(k).getAsDouble() < 1) return null;
                box[k] = a.get(k).getAsDouble() / 16;
                box[k + 3] = b.get(k).getAsDouble() / 16;
            }
            for (int r = 0; r < ry / 90; r++) box = new double[]{1 - box[5], box[1], box[0], 1 - box[2], box[4], box[3]};
            out[i] = box;
        }
        return out;
    }

    private static double[][] sticks(Model m) {
        List<double[]> out = new ArrayList<>();
        for (JsonElement je : m.elements()) {
            JsonArray a = je.getAsJsonObject().getAsJsonArray("from"), b = je.getAsJsonObject().getAsJsonArray("to");
            double[] box = new double[6];
            boolean thick = true;
            for (int k = 0; k < 3; k++) {
                thick &= b.get(k).getAsDouble() - a.get(k).getAsDouble() >= 1.5;
                box[k] = a.get(k).getAsDouble() / 16;
                box[k + 3] = b.get(k).getAsDouble() / 16;
            }
            if (thick) out.add(box);
        }
        return out.toArray(new double[0][]);
    }

    /** A world direction back through the variant's rotation: y undone first, then x. */
    private static int modelDir(int world, int rx, int ry) {
        int[] d = N[world].clone();
        for (int i = 0; i < ((360 - ry) % 360) / 90; i++) d = new int[]{-d[2], d[1], d[0]};
        for (int i = 0; i < ((360 - rx) % 360) / 90; i++) d = new int[]{d[0], d[2], -d[1]};
        for (int i = 0; i < 6; i++) if (N[i][0] == d[0] && N[i][1] == d[1] && N[i][2] == d[2]) return i;
        throw new IllegalStateException();
    }

    private Tex composite(Model m, int dir, int tint) throws IOException {
        List<Layer> layers = new ArrayList<>();
        double base = -1;
        if (m.elements() != null) for (JsonElement je : m.elements()) {
            JsonObject el = je.getAsJsonObject();
            JsonObject faces = el.getAsJsonObject("faces");
            if (faces == null || !faces.has(DIRS[dir])) continue;
            double a = area(el, dir);
            if (base < 0) base = a;
            else if (a < base * 0.8) continue;
            JsonObject face = faces.getAsJsonObject(DIRS[dir]);
            String name = resolve(face.get("texture").getAsString(), m.textures());
            if (name == null) continue;
            layers.add(new Layer(tex(name), face.has("tintindex") ? tint : -1, el.has("light_emission") && el.get("light_emission").getAsInt() > 0));
        }
        if (layers.isEmpty()) {
            String p = resolve("#particle", m.textures());
            if (p == null) return null;
            return tex(p);
        }
        int size = 0;
        for (Layer l : layers) size = Math.max(size, l.tex.size);
        Tex out = new Tex();
        out.size = size;
        out.px = new int[size * size];
        out.glow = new boolean[size * size];
        for (Layer l : layers) for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            int src = l.tex.px[y * l.tex.size / size * l.tex.size + x * l.tex.size / size];
            int a = src >>> 24;
            if (a < 16) continue;
            if (l.tint != -1) src = multiply(src, l.tint);
            int i = y * size + x;
            out.px[i] = a > 240 ? src | 0xFF000000 : over(out.px[i], src, a);
            if (l.glow) out.glow[i] = true;
        }
        finish(out);
        return out;
    }

    private static double area(JsonObject el, int dir) {
        JsonArray a = el.getAsJsonArray("from"), b = el.getAsJsonArray("to");
        double[] s = new double[3];
        for (int i = 0; i < 3; i++) s[i] = Math.abs(b.get(i).getAsDouble() - a.get(i).getAsDouble());
        return dir < 2 ? s[0] * s[2] : dir < 4 ? s[0] * s[1] : s[1] * s[2];
    }

    private static int over(int dst, int src, int a) {
        int ia = 255 - a;
        return Math.max(dst >>> 24, a) << 24 | ((src >> 16 & 255) * a + (dst >> 16 & 255) * ia) / 255 << 16
                | ((src >> 8 & 255) * a + (dst >> 8 & 255) * ia) / 255 << 8 | ((src & 255) * a + (dst & 255) * ia) / 255;
    }

    private static int multiply(int c, int tint) {
        return c & 0xFF000000 | (c >> 16 & 255) * (tint >> 16 & 255) / 255 << 16 | (c >> 8 & 255) * (tint >> 8 & 255) / 255 << 8 | (c & 255) * (tint & 255) / 255;
    }

    private static Tex tinted(Tex t, int tint) {
        Tex o = new Tex();
        o.size = t.size;
        o.px = t.px.clone();
        for (int i = 0; i < o.px.length; i++) o.px[i] = multiply(o.px[i], tint);
        finish(o);
        return o;
    }

    private static void finish(Tex t) {
        long r = 0, g = 0, b = 0, n = 0;
        for (int c : t.px) {
            if (c >>> 24 < 128) {
                t.cut = true;
                continue;
            }
            r += c >> 16 & 255;
            g += c >> 8 & 255;
            b += c & 255;
            n++;
        }
        if (n > 0) {
            t.r = (int) (r / n);
            t.g = (int) (g / n);
            t.b = (int) (b / n);
        }
    }

    private static String resolve(String ref, Map<String, String> textures) {
        for (int i = 0; i < 16 && ref != null && ref.startsWith("#"); i++) ref = textures.get(ref.substring(1));
        return ref;
    }

    private Model model(String ref) throws IOException {
        String key = ref.contains(":") ? ref : "minecraft:" + ref;
        Model hit = models.get(key);
        if (hit != null) return hit;
        String[] p = key.split(":");
        JsonObject o = json(p[0], "models/" + p[1] + ".json");
        Model parent = o != null && o.has("parent") ? model(o.get("parent").getAsString()) : new Model(Map.of(), null);
        Map<String, String> tx = new HashMap<>(parent.textures());
        if (o != null && o.has("textures")) for (var e : o.getAsJsonObject("textures").entrySet()) {
            JsonElement v = e.getValue();
            tx.put(e.getKey(), v.isJsonObject() ? v.getAsJsonObject().get("sprite").getAsString() : v.getAsString());
        }
        Model out = new Model(tx, o != null && o.has("elements") ? o.getAsJsonArray("elements") : parent.elements());
        models.put(key, out);
        return out;
    }

    private Tex tex(String ref) throws IOException {
        String key = ref.contains(":") ? ref : "minecraft:" + ref;
        Tex hit = textures.get(key);
        if (hit != null) return hit;
        String[] p = key.split(":");
        Tex t = new Tex();
        Path file = file(p[0], "textures/" + p[1] + ".png");
        if (file == null) {
            missing.add(key);
            t.size = 1;
            t.px = new int[]{0xFFFF00FF};
        } else {
            BufferedImage img = ImageIO.read(file.toFile());
            t.size = img.getWidth();
            t.px = img.getRGB(0, 0, t.size, t.size, null, 0, t.size);
        }
        finish(t);
        textures.put(key, t);
        return t;
    }

    private static Map<String, String> props(BlockState s) {
        Map<String, String> out = new HashMap<>();
        String text = s.toString();
        int open = text.indexOf('[');
        if (open < 0) return out;
        for (String kv : text.substring(open + 1, text.length() - 1).split(",")) {
            String[] p = kv.split("=");
            out.put(p[0], p[1]);
        }
        return out;
    }

    private static JsonObject pick(JsonObject bs, Map<String, String> props) {
        if (bs.has("variants")) for (var e : bs.getAsJsonObject("variants").entrySet()) if (matches(e.getKey(), props)) return first(e.getValue());
        if (bs.has("multipart")) for (JsonElement je : bs.getAsJsonArray("multipart")) {
            JsonObject part = je.getAsJsonObject();
            if (!part.has("when") || when(part.getAsJsonObject("when"), props)) return first(part.get("apply"));
        }
        return null;
    }

    private static JsonObject first(JsonElement v) {
        return v.isJsonArray() ? v.getAsJsonArray().get(0).getAsJsonObject() : v.getAsJsonObject();
    }

    private static boolean matches(String key, Map<String, String> props) {
        if (key.isEmpty()) return true;
        for (String kv : key.split(",")) {
            String[] p = kv.split("=");
            if (!p[1].equals(props.get(p[0]))) return false;
        }
        return true;
    }

    private static boolean when(JsonObject w, Map<String, String> props) {
        if (w.has("OR")) {
            for (JsonElement e : w.getAsJsonArray("OR")) if (when(e.getAsJsonObject(), props)) return true;
            return false;
        }
        if (w.has("AND")) {
            for (JsonElement e : w.getAsJsonArray("AND")) if (!when(e.getAsJsonObject(), props)) return false;
            return true;
        }
        for (var e : w.entrySet()) if (!java.util.Arrays.asList(e.getValue().getAsString().split("\\|")).contains(props.get(e.getKey()))) return false;
        return true;
    }

    private Path file(String ns, String rel) {
        for (Path b : bases) {
            Path p = b.resolve(ns).resolve(rel);
            if (Files.exists(p)) return p;
        }
        return null;
    }

    private JsonObject json(String ns, String rel) throws IOException {
        Path p = file(ns, rel);
        return p == null ? null : JsonParser.parseString(Files.readString(p)).getAsJsonObject();
    }
}
