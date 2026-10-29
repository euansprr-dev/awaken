package dev.awaken.world.stilled.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.Awaken;
import dev.awaken.world.stilled.StilledText;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Stilled's figure (SPEC §43.3): one of Carillon's people gone to stone, on the citizens' own body with the elbows and
 * knees split so a pose can bend, and the props they sat down with. Everything comes from one rig,
 * {@code assets/awaken/rigs/stilled.json}, written by tools/art/stilled.py and drawn the same way by tools/art/rig.py:
 * the parts (pivot, rotation, box-UV cubes on a 128 x 128 texture), and each pose (rotations in degrees, moves in
 * pixels, the props it hides, the second figure of two). A pair or a parent with a child is this model drawn twice.
 */
public final class StilledModel extends Model<StilledModel.Figure> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Awaken.id("stilled"), "main");
    static final String RIG = "/assets/awaken/rigs/stilled.json";

    /** Which figure of a block is drawn: the first (with its props), or the second of two (no props). */
    public record Figure(StilledText.Pose pose, boolean second) {}

    /** The other figure of two, in the first's model space: moved (pixels), turned, and scaled about its feet. */
    public record Second(String pose, float x, float z, float yaw, float scale) {}

    /** A pose set on every part: where each sits, whether it shows, and the second figure if there is one. */
    private record Posed(PartPose[] poses, boolean[] hidden, @Nullable Second second) {}

    private static @Nullable JsonObject rig;

    private final ModelPart[] parts;
    private final boolean[] prop;
    private final Map<StilledText.Pose, Posed> first = new EnumMap<>(StilledText.Pose.class), second = new EnumMap<>(StilledText.Pose.class);

    public StilledModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
        JsonObject r = rig();
        JsonArray defs = r.getAsJsonArray("parts");
        parts = new ModelPart[defs.size()];
        prop = new boolean[defs.size()];
        Map<String, Integer> index = new HashMap<>();
        Map<String, String> parent = new HashMap<>();
        for (int i = 0; i < defs.size(); i++) {
            JsonObject d = defs.get(i).getAsJsonObject();
            String name = d.get("name").getAsString();
            index.put(name, i);
            parent.put(name, d.get("parent").getAsString());
            prop[i] = "props".equals(d.get("group").getAsString());
        }
        for (int i = 0; i < defs.size(); i++) parts[i] = find(root, defs.get(i).getAsJsonObject().get("name").getAsString(), parent);
        JsonObject poses = r.getAsJsonObject("poses");
        for (StilledText.Pose p : StilledText.Pose.values()) {
            String key = p.name().toLowerCase(java.util.Locale.ROOT);
            JsonObject q = poses.getAsJsonObject(key);
            if (q == null) {
                Awaken.LOG.warn("[stilled] the rig has no pose {}", key);
                continue;
            }
            Posed one = set(defs, index, q);
            first.put(p, one);
            if (one.second() != null && poses.has(one.second().pose())) second.put(p, set(defs, index, poses.getAsJsonObject(one.second().pose())));
        }
    }

    private static ModelPart find(ModelPart root, String name, Map<String, String> parent) {
        String up = parent.get(name);
        return (up == null || up.isEmpty() ? root : find(root, up, parent)).getChild(name);
    }

    private static Posed set(JsonArray defs, Map<String, Integer> index, JsonObject q) {
        JsonObject rot = q.has("rot") ? q.getAsJsonObject("rot") : new JsonObject();
        JsonObject move = q.has("move") ? q.getAsJsonObject("move") : new JsonObject();
        PartPose[] poses = new PartPose[defs.size()];
        boolean[] hidden = new boolean[defs.size()];
        for (int i = 0; i < defs.size(); i++) {
            JsonObject d = defs.get(i).getAsJsonObject();
            String name = d.get("name").getAsString();
            float[] piv = floats(d.getAsJsonArray("pivot"));
            float[] m = move.has(name) ? floats(move.getAsJsonArray(name)) : new float[3];
            float[] a = rot.has(name) ? floats(rot.getAsJsonArray(name)) : floats(d.getAsJsonArray("rot"));
            poses[i] = PartPose.offsetAndRotation(piv[0] + m[0], piv[1] + m[1], piv[2] + m[2],
                    a[0] * Mth.DEG_TO_RAD, a[1] * Mth.DEG_TO_RAD, a[2] * Mth.DEG_TO_RAD);
        }
        if (q.has("hide")) for (JsonElement e : q.getAsJsonArray("hide")) {
            Integer i = index.get(e.getAsString());
            if (i != null) hidden[i] = true;
        }
        Second two = null;
        if (q.has("second")) {
            JsonObject s = q.getAsJsonObject("second");
            two = new Second(s.get("pose").getAsString(), f(s, "x", 0), f(s, "z", 0), f(s, "yaw", 0), f(s, "scale", 1));
        }
        return new Posed(poses, hidden, two);
    }

    private static float f(JsonObject o, String k, float def) {
        return o.has(k) ? o.get(k).getAsFloat() : def;
    }

    private static float[] floats(JsonArray a) {
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    /** The second figure of this pose, or null when it stands alone. */
    @Nullable Second second(StilledText.Pose pose) {
        Posed s = first.get(pose);
        return s == null ? null : s.second();
    }

    @Override
    public void setupAnim(Figure f) {
        super.setupAnim(f);
        Posed s = (f.second() ? second : first).get(f.pose());
        if (s == null) return;
        for (int i = 0; i < parts.length; i++) {
            parts[i].loadPose(s.poses()[i]);
            parts[i].visible = !s.hidden()[i] && !(f.second() && prop[i]);
        }
    }

    /** The rig file, read once from the mod's own resources (the same file tools/art/rig.py draws). */
    static synchronized JsonObject rig() {
        if (rig == null) {
            try (InputStream in = StilledModel.class.getResourceAsStream(RIG)) {
                if (in == null) throw new IllegalStateException("missing " + RIG);
                try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    rig = JsonParser.parseReader(reader).getAsJsonObject();
                }
            } catch (java.io.IOException e) {
                throw new IllegalStateException("can't read " + RIG, e);
            }
        }
        return rig;
    }

    public static LayerDefinition createLayer() {
        JsonObject r = rig();
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> made = new HashMap<>();
        List<JsonObject> left = new ArrayList<>();
        for (JsonElement e : r.getAsJsonArray("parts")) left.add(e.getAsJsonObject());
        // parents come before their children in the file, but don't rely on it
        while (!left.isEmpty()) {
            int before = left.size();
            for (var it = left.iterator(); it.hasNext(); ) {
                JsonObject d = it.next();
                String up = d.get("parent").getAsString();
                PartDefinition parent = up.isEmpty() ? mesh.getRoot() : made.get(up);
                if (parent == null) continue;
                CubeListBuilder cubes = CubeListBuilder.create();
                for (JsonElement ce : d.getAsJsonArray("cubes")) {
                    JsonObject c = ce.getAsJsonObject();
                    JsonArray uv = c.getAsJsonArray("uv");
                    float[] from = floats(c.getAsJsonArray("from")), size = floats(c.getAsJsonArray("size"));
                    cubes.texOffs(uv.get(0).getAsInt(), uv.get(1).getAsInt()).mirror(c.has("mirror") && c.get("mirror").getAsBoolean())
                            .addBox(from[0], from[1], from[2], size[0], size[1], size[2], new CubeDeformation(f(c, "inflate", 0)));
                }
                float[] piv = floats(d.getAsJsonArray("pivot")), a = floats(d.getAsJsonArray("rot"));
                String name = d.get("name").getAsString();
                made.put(name, parent.addOrReplaceChild(name, cubes, PartPose.offsetAndRotation(piv[0], piv[1], piv[2],
                        a[0] * Mth.DEG_TO_RAD, a[1] * Mth.DEG_TO_RAD, a[2] * Mth.DEG_TO_RAD)));
                it.remove();
            }
            if (left.size() == before) throw new IllegalStateException(RIG + ": parts whose parent is missing: " + left);
        }
        JsonArray tex = r.getAsJsonArray("texture");
        return LayerDefinition.create(mesh, tex.get(0).getAsInt(), tex.get(1).getAsInt());
    }
}
