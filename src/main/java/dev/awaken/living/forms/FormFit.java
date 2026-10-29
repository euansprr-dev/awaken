package dev.awaken.living.forms;

import dev.awaken.mobs.client.anim.HeroRig;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A form body's own pieces on a vanilla body that a model pack moves (SPEC §21.13 item 17; Euan 2026-10-29: "use the fresh animations
 * 1:1"). With Fresh Animations on, a creature from C up keeps the vanilla body, which the pack animates, at its rung's height; its rig's
 * extras (crowns, plates, horns, claws, crystals, capes: every bone that isn't the body itself) ride the vanilla part they grow from.
 * Each moves from the form's body onto the vanilla one: a piece on the body keeps its distance from the surface it sits on, one inside
 * spreads with it, and a chain (a crown's tip, a horn's segments) moves with its root. Pure, so the tests read it; the client bakes it.
 */
public final class FormFit {
    private FormFit() {}

    /** A vanilla part at rest: its pivot and rotation in the model, and its box (min corner, size) in its own frame. */
    public record Part(String name, float px, float py, float pz, float rx, float bx, float by, float bz, float w, float h, float d) {}

    /** One extra bone, flat: its pose in the vanilla model at rest (the part's motion is added as it's drawn), at 1/scale of the rig. */
    public record Piece(String anchor, String bone, float x, float y, float z, float xRot, float yRot, float zRot, float scale,
                        List<HeroRig.Cube> cubes) {}

    /** The model's feet, in pixels: a rung's growth is about them, as the renderer's scale is. */
    static final float GROUND = 24.0F;
    private static final float HALF_PI = (float) (Math.PI / 2);

    /** The vanilla parts the pieces ride, read from the vanilla models (26.3). */
    private static final Map<String, List<Part>> VANILLA = new HashMap<>();
    /** Per layout, "bone:part": the form bones each vanilla part grows from; the first named for a part is the one whose box is its. */
    private static final Map<String, String[]> ANCHORS = new HashMap<>();

    private static Part p(String n, float px, float py, float pz, float rx, float bx, float by, float bz, float w, float h, float d) {
        return new Part(n, px, py, pz, rx, bx, by, bz, w, h, d);
    }

    private static List<Part> legs(float x, float y, float front, float hind, float h) {
        return List.of(p("right_front_leg", -x, y, front, 0, -2, 0, -2, 4, h, 4), p("left_front_leg", x, y, front, 0, -2, 0, -2, 4, h, 4),
                p("right_hind_leg", -x, y, hind, 0, -2, 0, -2, 4, h, 4), p("left_hind_leg", x, y, hind, 0, -2, 0, -2, 4, h, 4));
    }

    private static List<Part> with(List<Part> a, List<Part> b) {
        List<Part> out = new ArrayList<>(a);
        out.addAll(b);
        return List.copyOf(out);
    }

    static {
        Part head = p("head", 0, 0, 0, 0, -4, -8, -4, 8, 8, 8), body = p("body", 0, 0, 0, 0, -4, 0, -2, 8, 12, 4);
        VANILLA.put("zombie", List.of(head, body, p("right_arm", -5, 2, 0, 0, -3, -2, -2, 4, 12, 4), p("left_arm", 5, 2, 0, 0, -1, -2, -2, 4, 12, 4),
                p("right_leg", -1.9F, 12, 0, 0, -2, 0, -2, 4, 12, 4), p("left_leg", 1.9F, 12, 0, 0, -2, 0, -2, 4, 12, 4)));
        VANILLA.put("skeleton", List.of(head, body, p("right_arm", -5, 2, 0, 0, -1, -2, -1, 2, 12, 2), p("left_arm", 5, 2, 0, 0, -1, -2, -1, 2, 12, 2),
                p("right_leg", -2, 12, 0, 0, -1, 0, -1, 2, 12, 2), p("left_leg", 2, 12, 0, 0, -1, 0, -1, 2, 12, 2)));
        VANILLA.put("creeper", with(List.of(p("head", 0, 6, 0, 0, -4, -8, -4, 8, 8, 8), p("body", 0, 6, 0, 0, -4, 0, -2, 8, 12, 4)),
                legs(2, 18, -4, 4, 6)));
        VANILLA.put("spider", List.of(p("head", 0, 15, -3, 0, -4, -4, -8, 8, 8, 8), p("body0", 0, 15, 0, 0, -3, -3, -3, 6, 6, 6),
                p("body1", 0, 15, 9, 0, -5, -4, -6, 10, 8, 12)));
        VANILLA.put("pig", with(List.of(p("head", 0, 12, -6, 0, -4, -4, -8, 8, 8, 8), p("body", 0, 11, 2, HALF_PI, -5, -10, -7, 10, 16, 8)),
                legs(3, 18, -5, 7, 6)));
        VANILLA.put("cow", with(List.of(p("head", 0, 4, -8, 0, -4, -4, -6, 8, 8, 6), p("body", 0, 5, 2, HALF_PI, -6, -10, -7, 12, 18, 10)),
                legs(4, 12, -5, 7, 12)));
        VANILLA.put("sheep", with(List.of(p("head", 0, 6, -8, 0, -3, -4, -6, 6, 6, 8), p("body", 0, 5, 2, HALF_PI, -4, -10, -7, 8, 16, 6)),
                legs(3, 12, -5, 7, 12)));
        VANILLA.put("chicken", List.of(p("head", 0, 15, -4, 0, -2, -6, -2, 4, 6, 3), p("body", 0, 16, 0, HALF_PI, -3, -4, -3, 6, 8, 6),
                p("right_leg", -2, 19, 1, 0, -1, 0, -3, 3, 5, 3), p("left_leg", 1, 19, 1, 0, -1, 0, -3, 3, 5, 3)));
        String[] walker = {"torso:body", "hips:body", "head:head", "arm_r:right_arm", "arm_l:left_arm", "leg_r:right_leg", "leg_l:left_leg"};
        String[] beast = {"torso:body", "head:head", "neck:head", "leg_fr:right_front_leg", "leg_fl:left_front_leg", "leg_br:right_hind_leg",
                "leg_bl:left_hind_leg"};
        ANCHORS.put("zombie", walker);
        ANCHORS.put("skeleton", walker);
        ANCHORS.put("creeper", new String[]{"torso:body", "hips:body", "head:head", "leg_fr:right_front_leg", "leg_fl:left_front_leg",
                "leg_br:right_hind_leg", "leg_bl:left_hind_leg"});
        ANCHORS.put("spider", new String[]{"torso:body0", "head:head", "abdomen:body1"});
        ANCHORS.put("pig", beast);
        ANCHORS.put("cow", beast);
        ANCHORS.put("sheep", beast);
        ANCHORS.put("chicken", new String[]{"torso:body", "head:head", "neck:head", "leg_r:right_leg", "leg_l:left_leg"});
    }

    /** A vanilla part of a layout, or null. */
    public static @Nullable Part part(String layout, String name) {
        for (Part p : VANILLA.getOrDefault(layout, List.of())) if (p.name.equals(name)) return p;
        return null;
    }

    /** The body itself, which the vanilla model draws instead: never a piece (what grows on it is). */
    public static boolean body(String bone) {
        return switch (bone) {
            case "base", "hips", "torso", "head", "neck", "abdomen", "tail", "spinner" -> true;
            default -> bone.startsWith("leg_") || bone.startsWith("shin_") || bone.startsWith("arm_") || bone.startsWith("fore_")
                    || bone.startsWith("hand_") || bone.startsWith("coxa_") || bone.startsWith("ear_") || bone.startsWith("wing_")
                    || bone.startsWith("eye_") || bone.startsWith("jaw") || bone.startsWith("fang");
        };
    }

    /** A vanilla part's box at rest, in the model ({min xyz, max xyz}, pixels). */
    public static float[] box(Part v) {
        Quaternionf q = new Quaternionf().rotationX(v.rx);
        float[] out = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int c = 0; c < 8; c++) {
            Vector3f at = q.transform(new Vector3f(v.bx + ((c & 1) == 0 ? 0 : v.w), v.by + ((c & 2) == 0 ? 0 : v.h), v.bz + ((c & 4) == 0 ? 0 : v.d)));
            grow(out, at.add(v.px, v.py, v.pz));
        }
        return out;
    }

    /**
     * The rig's extras fitted to the layout's vanilla body, drawn at {@code scale} about its feet (the rung's height over vanilla's). Empty
     * for a layout without vanilla parts here.
     */
    public static List<Piece> fit(HeroRig rig, String layout, float scale) {
        String[] anchors = ANCHORS.get(layout);
        if (anchors == null) return List.of();
        int n = rig.bones.size();
        Quaternionf[] q = new Quaternionf[n];
        Vector3f[] p = new Vector3f[n];
        String[] anchorOf = new String[n];
        for (int i = 0; i < n; i++) {
            HeroRig.Bone b = rig.bones.get(i);
            Quaternionf local = new Quaternionf().rotationZYX(b.rz(), b.ry(), b.rx());
            Vector3f at = new Vector3f(b.px(), b.py(), b.pz());
            int par = b.parent();
            q[i] = par < 0 ? local : new Quaternionf(q[par]).mul(local);
            p[i] = par < 0 ? at : q[par].transform(at).add(p[par]);
            anchorOf[i] = par < 0 ? null : anchorOf[par];
            for (String a : anchors) if (a.startsWith(b.name() + ":")) anchorOf[i] = a.substring(b.name().length() + 1);
        }
        // each part's two boxes: the form's (its first bone's cubes) and vanilla's grown to the rung, both in the rig's pixels
        Map<String, float[]> form = new HashMap<>(), van = new HashMap<>();
        for (String a : anchors) {
            String bone = a.substring(0, a.indexOf(':')), name = a.substring(a.indexOf(':') + 1);
            int i = rig.bone(bone);
            Part v = part(layout, name);
            if (i < 0 || v == null || form.containsKey(name) || rig.bones.get(i).cubes().isEmpty()) continue;
            float[] f = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            for (int j = i; j < n; j++) {
                // a limb's box is all of it: the forearm, hand and shin below the bone that carries the vanilla part
                if (j != i && !(segment(rig.bones.get(j).name()) && under(rig, j, i))) continue;
                for (HeroRig.Cube c : rig.bones.get(j).cubes()) {
                    for (int k = 0; k < 8; k++) {
                        Vector3f at = new Vector3f(c.x() + ((k & 1) == 0 ? 0 : c.w()), c.y() + ((k & 2) == 0 ? 0 : c.h()), c.z() + ((k & 4) == 0 ? 0 : c.d()));
                        grow(f, q[j].transform(at).add(p[j]));
                    }
                }
            }
            float[] b = box(v);
            for (int k = 0; k < 6; k += 3) {
                b[k] *= scale;
                b[k + 1] = GROUND + (b[k + 1] - GROUND) * scale;
                b[k + 2] *= scale;
            }
            form.put(name, f);
            van.put(name, b);
        }
        boolean[] extra = new boolean[n];
        Vector3f[] chain = new Vector3f[n];
        List<Piece> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            HeroRig.Bone b = rig.bones.get(i);
            String a = anchorOf[i];
            extra[i] = a != null && !body(b.name()) && form.containsKey(a);
            if (!extra[i]) continue;
            float[] f = form.get(a), v = van.get(a);
            int par = b.parent();
            boolean onBody = par < 0 || !extra[par];
            List<HeroRig.Cube> cubes = b.cubes();
            Vector3f shift;
            if (onBody && cubes.size() > 1) {
                // plates round the body: each keeps its own distance from the surface it sits on; what grows from them moves by its own pivot
                Quaternionf back = new Quaternionf(q[i]).conjugate();
                List<HeroRig.Cube> moved = new ArrayList<>(cubes.size());
                for (HeroRig.Cube c : cubes) {
                    Vector3f mid = q[i].transform(new Vector3f(c.x() + c.w() / 2F, c.y() + c.h() / 2F, c.z() + c.d() / 2F)).add(p[i]);
                    Vector3f d = back.transform(remap(mid, f, v).sub(mid));
                    moved.add(new HeroRig.Cube(c.u(), c.v(), c.x() + d.x, c.y() + d.y, c.z() + d.z, c.w(), c.h(), c.d(), c.mirror(), c.inflate()));
                }
                cubes = moved;
                shift = new Vector3f();
            } else if (onBody || chain[par] == null) {
                // a chain's root moves by its one cube (or its pivot), and its tips with it
                Vector3f at = onBody && cubes.size() == 1 ? centre(cubes.getFirst(), q[i], p[i]) : new Vector3f(p[i]);
                shift = chain[i] = remap(at, f, v).sub(at);
            } else {
                shift = chain[i] = chain[par];
            }
            // into the vanilla model's own pixels: its body is drawn at `scale` about the feet
            Vector3f e = q[i].getEulerAnglesZYX(new Vector3f());
            out.add(new Piece(a, b.name(), (p[i].x + shift.x) / scale, GROUND + (p[i].y + shift.y - GROUND) / scale, (p[i].z + shift.z) / scale,
                    e.x, e.y, e.z, 1F / scale, List.copyOf(cubes)));
        }
        return out;
    }

    private static boolean segment(String bone) {
        return bone.startsWith("fore_") || bone.startsWith("hand_") || bone.startsWith("shin_");
    }

    private static boolean under(HeroRig rig, int bone, int top) {
        for (int k = rig.bones.get(bone).parent(); k >= 0; k = rig.bones.get(k).parent()) if (k == top) return true;
        return false;
    }

    private static Vector3f centre(HeroRig.Cube c, Quaternionf q, Vector3f p) {
        return q.transform(new Vector3f(c.x() + c.w() / 2F, c.y() + c.h() / 2F, c.z() + c.d() / 2F)).add(p);
    }

    /** Where a point on or round the form's box lands on or round vanilla's: outside keeps its distance from the face, inside spreads. */
    static Vector3f remap(Vector3f at, float[] f, float[] v) {
        return new Vector3f(axis(at.x, f[0], f[3], v[0], v[3]), axis(at.y, f[1], f[4], v[1], v[4]), axis(at.z, f[2], f[5], v[2], v[5]));
    }

    static float axis(float c, float fa, float fb, float va, float vb) {
        if (fb - fa < 1e-3F) return c + (va + vb) / 2F - (fa + fb) / 2F;
        if (c <= fa) return c + va - fa;
        if (c >= fb) return c + vb - fb;
        return va + (c - fa) * (vb - va) / (fb - fa);
    }

    private static void grow(float[] box, Vector3f at) {
        box[0] = Math.min(box[0], at.x);
        box[1] = Math.min(box[1], at.y);
        box[2] = Math.min(box[2], at.z);
        box[3] = Math.max(box[3], at.x);
        box[4] = Math.max(box[4], at.y);
        box[5] = Math.max(box[5], at.z);
    }
}
