package dev.awaken.gear.premium;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The premium test's model spec (docs/armour/PREMIUM-TEST.md; branch pack/armour-premium-test, never merged), written by
 * tools/art/specs/gear_premium.py: a whole armour set as parts, every part drawn by one slot, in the same pixel
 * density and painter rules as the weapons. Plain data so a GameTest can check it on the server.
 *
 * <p>A part: a box {@code [x, y, z, w, h, d]} (whole texel sizes) in its parent's space, {@code grow} (x, y, z) that makes
 * the cube its true size without moving the texture, a pose {@code [x, y, z, xRot, yRot, zRot]}, its texture offset, a mirror
 * flag (the left twin of a right part) and the slot that draws it. Parents are the six base parts or a part defined before.
 */
public record PremiumSpec(int schema, List<Integer> texture, List<Part> parts) {
    public static final int SCHEMA = 1;
    public static final Set<String> BASE = Set.of("head", "body", "right_arm", "left_arm", "right_leg", "left_leg");
    public static final Set<String> SLOTS = Set.of("head", "chest", "legs", "feet");
    /** The albedo lives in the left half of the atlas; the material map in the right (the weapons' arrangement). */
    public static final int ALBEDO_WIDTH = 128;

    public record Part(String name, String parent, String slot, List<Float> box, List<Float> grow, List<Float> pose, List<Integer> uv, boolean mirror) {
        public static final Codec<Part> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Part::name),
                Codec.STRING.fieldOf("parent").forGetter(Part::parent),
                Codec.STRING.fieldOf("slot").forGetter(Part::slot),
                Codec.FLOAT.listOf().fieldOf("box").forGetter(Part::box),
                Codec.FLOAT.listOf().fieldOf("grow").forGetter(Part::grow),
                Codec.FLOAT.listOf().fieldOf("pose").forGetter(Part::pose),
                Codec.INT.listOf().fieldOf("uv").forGetter(Part::uv),
                Codec.BOOL.optionalFieldOf("mirror", false).forGetter(Part::mirror)
        ).apply(i, Part::new));

        public int uvWidth() {
            return (int) Math.ceil(2 * (box.get(5) + box.get(3)));
        }

        public int uvHeight() {
            return (int) Math.ceil(box.get(5) + box.get(4));
        }
    }

    public static final Codec<PremiumSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(PremiumSpec::schema),
            Codec.INT.listOf().fieldOf("texture").forGetter(PremiumSpec::texture),
            Part.CODEC.listOf().fieldOf("parts").forGetter(PremiumSpec::parts)
    ).apply(i, PremiumSpec::new));

    public static final String PATH = "gear_premium/veilwoven.json";
    public static final String TEXTURE = "textures/entity/equipment/premium/veilwoven.png";
    public static final String GLOW = "textures/entity/equipment/premium/veilwoven_glow.png";

    /** What's wrong with this spec, or nothing: sizes, whole texel boxes, known parents, slots, UVs inside the albedo half and apart. */
    public List<String> problems() {
        List<String> out = new ArrayList<>();
        if (texture.size() != 2 || texture.get(0) != 2 * ALBEDO_WIDTH) out.add("texture size " + texture);
        int th = texture.size() == 2 ? texture.get(1) : 256;
        Set<String> known = new HashSet<>(BASE);
        List<int[]> owned = new ArrayList<>();
        for (Part p : parts) {
            if (!known.add(p.name())) out.add(p.name() + ": duplicate name");
            if (!BASE.contains(p.parent()) && !known.contains(p.parent())) out.add(p.name() + ": parent " + p.parent() + " not defined before it");
            if (!SLOTS.contains(p.slot())) out.add(p.name() + ": slot " + p.slot());
            if (p.box().size() != 6 || p.grow().size() != 3 || p.pose().size() != 6 || p.uv().size() != 2) {
                out.add(p.name() + ": a list has the wrong length");
                continue;
            }
            for (int k = 3; k < 6; k++) {
                float v = p.box().get(k);
                if (v < 1 || v != Math.rint(v)) out.add(p.name() + ": box size " + v + " is not whole texels");
            }
            int u = p.uv().get(0), v = p.uv().get(1), w = p.uvWidth(), h = p.uvHeight();
            if (u < 0 || v < 0 || u + w > ALBEDO_WIDTH || v + h > th) out.add(p.name() + ": texture area outside the albedo half");
            if (p.mirror()) continue; // a twin shares its right-hand part's area
            for (int[] o : owned) {
                if (u < o[0] + o[2] && o[0] < u + w && v < o[1] + o[3] && o[1] < v + h) out.add(p.name() + ": texture area overlaps another part");
            }
            owned.add(new int[]{u, v, w, h});
        }
        return out;
    }
}
