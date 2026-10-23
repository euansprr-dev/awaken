package dev.awaken.gear;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The extra geometry of the late sets' armour (Lordscale's horns, crest, pauldrons, spines and claws; Sovereign Weave's
 * crown, collar, cape, pauldrons and fins), from {@code assets/awaken/gear_silhouettes/<set>.json}. tools/art/specs/gear.py
 * writes the file and paints the parts into {@code textures/entity/equipment/silhouette/<set>.png}; the client builds the
 * armour model from it ({@code gear.client.Silhouettes}). Plain data so a GameTest can check it on the server.
 *
 * <p>A part: a box {@code [x, y, z, w, h, d]} in its parent's space (model pixels, y down, -z the front), a pose
 * {@code [x, y, z, xRot, yRot, zRot]} (ModelPart order), texture offset, mirror (the left twin of a right part), and the
 * armour slot whose model carries it (head, chest, feet).
 */
public record Silhouette(int schema, List<Integer> texture, List<Part> parts) {
    public static final int SCHEMA = 1;
    public static final Set<String> BASE = Set.of("head", "body", "right_arm", "left_arm", "right_leg", "left_leg");

    public record Part(String name, String parent, String slot, List<Float> box, List<Integer> uv, boolean mirror, List<Float> pose) {
        public static final Codec<Part> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Part::name),
                Codec.STRING.fieldOf("parent").forGetter(Part::parent),
                Codec.STRING.fieldOf("slot").forGetter(Part::slot),
                Codec.FLOAT.listOf().fieldOf("box").forGetter(Part::box),
                Codec.INT.listOf().fieldOf("uv").forGetter(Part::uv),
                Codec.BOOL.optionalFieldOf("mirror", false).forGetter(Part::mirror),
                Codec.FLOAT.listOf().optionalFieldOf("pose", List.of(0f, 0f, 0f, 0f, 0f, 0f)).forGetter(Part::pose)
        ).apply(i, Part::new));

        /** Texture area the box's faces use (ModelPart.Cube's layout). */
        public int uvWidth() {
            return (int) Math.ceil(2 * (box.get(5) + box.get(3)));
        }

        public int uvHeight() {
            return (int) Math.ceil(box.get(5) + box.get(4));
        }
    }

    public static final Codec<Silhouette> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(Silhouette::schema),
            Codec.INT.listOf().fieldOf("texture").forGetter(Silhouette::texture),
            Part.CODEC.listOf().fieldOf("parts").forGetter(Silhouette::parts)
    ).apply(i, Silhouette::new));

    public static String path(HunterTier tier) {
        return "gear_silhouettes/" + tier.id + ".json";
    }

    public static String texture(HunterTier tier) {
        return "textures/entity/equipment/silhouette/" + tier.id + ".png";
    }

    /** What's wrong with this spec, or nothing: parents known before use, sizes, slots, UVs inside and apart. */
    public List<String> problems() {
        List<String> out = new ArrayList<>();
        if (texture.size() != 2 || texture.get(0) < 64 || texture.get(1) < 32) out.add("texture size " + texture);
        int tw = texture.size() == 2 ? texture.get(0) : 64, th = texture.size() == 2 ? texture.get(1) : 32;
        Set<String> known = new HashSet<>(BASE);
        List<int[]> owned = new ArrayList<>();
        for (Part p : parts) {
            if (!known.add(p.name())) out.add(p.name() + ": duplicate name");
            if (!BASE.contains(p.parent()) && !known.contains(p.parent())) out.add(p.name() + ": parent " + p.parent() + " not defined before it");
            if (!Set.of("head", "chest", "feet").contains(p.slot())) out.add(p.name() + ": slot " + p.slot());
            if (p.box().size() != 6 || p.box().get(3) <= 0 || p.box().get(4) <= 0 || p.box().get(5) <= 0) out.add(p.name() + ": box " + p.box());
            if (p.pose().size() != 6) out.add(p.name() + ": pose " + p.pose());
            if (p.uv().size() != 2 || p.box().size() != 6) continue;
            int u = p.uv().get(0), v = p.uv().get(1), w = p.uvWidth(), h = p.uvHeight();
            if (u < 0 || v < 0 || u + w > tw || v + h > th) out.add(p.name() + ": texture area outside " + tw + "x" + th);
            if (u < 64 && v < 32) out.add(p.name() + ": texture area overlaps the base layer");
            if (p.mirror()) continue; // a twin shares its right-hand part's area
            for (int[] o : owned) {
                if (u < o[0] + o[2] && o[0] < u + w && v < o[1] + o[3] && o[1] < v + h) out.add(p.name() + ": texture area overlaps another part");
            }
            owned.add(new int[]{u, v, w, h});
        }
        return out;
    }
}
