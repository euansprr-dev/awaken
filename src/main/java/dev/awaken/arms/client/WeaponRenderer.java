package dev.awaken.arms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.arms.Archetype;
import dev.awaken.arms.ArmsRules;
import dev.awaken.arms.WeaponMesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A sculpted weapon in the hands, on a back, in a frame (SPEC §25 "Weapons"): its mesh drawn through the weapon shader
 * ({@link WeaponPipelines}), or vanilla's entity light if that shader didn't build. The fighter's own blade carries
 * the fight in it: a heavy's charge fills it with light, the glint flashes through it, a full Resonance (the Art
 * ready) keeps it humming. Item models name it as {@code {"type": "awaken:weapon", "weapon": "<id>"}}.
 */
final class WeaponRenderer implements SpecialModelRenderer<WeaponRenderer.State> {
    static final Identifier TYPE = Awaken.id("weapon");

    /**
     * The blade's live state (0..255 each): its energy and its flash; whether it is the fighter's own in first person;
     * who holds it (an entity id, -1 for nobody: a stand, a frame).
     */
    record State(int energy, int flash, boolean hand, int wielder) {}

    private final String weapon;
    private final Identifier texture;

    private WeaponRenderer(String weapon) {
        this.weapon = weapon;
        this.texture = Awaken.id("textures/item/arms/" + weapon + ".png");
    }

    @Override
    public void submit(@Nullable State state, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, boolean foil,
                       int outlineColor) {
        WeaponMesh mesh = WeaponMeshes.get(weapon);
        if (mesh == null) return;
        boolean ours = WeaponPipelines.ok();
        int packed = ours ? (state == null ? 0 : state.energy()) | ((state == null ? 0 : state.flash()) | (foil ? 256 : 0)) << 16 : overlay;
        collector.submitCustomGeometry(poseStack, ours ? WeaponPipelines.weapon(texture) : WeaponPipelines.fallback(texture),
                (pose, buffer) -> emit(mesh, pose, buffer, light, packed, ours));
        // its light lifting off it, from where it is drawn (the energy: a charging heavy sheds more)
        WeaponMotes.emit(mesh, poseStack.last().pose(), state != null && state.hand(), state == null ? 0 : state.energy() / 255F);
        // a blow in the air: the blade's own ribbon, from its middle to its point
        if (state != null && state.wielder() >= 0 && BladeTrail.open(state.wielder())) {
            float[] line = blade(mesh);
            BladeTrail.sample(state.wielder(), poseStack.last().pose(), line[0], line[1], line[2], line[3], line[4], line[5], state.hand());
        }
        if (ours && !mesh.anchors().isEmpty()) {
            float energy = state == null ? 0 : state.energy() / 255F, flash = state == null ? 0 : state.flash() / 255F;
            collector.submitCustomGeometry(poseStack, WeaponPipelines.glow(), (pose, buffer) -> halos(mesh, pose, buffer, energy, flash));
        }
    }

    private static final int HALO_SIDES = 10;
    private static final Vector3f AT = new Vector3f(), RIGHT = new Vector3f(), UP = new Vector3f(), SCALE = new Vector3f();

    /**
     * A soft light round each stone, facing the eye: a fan whose rim fades to nothing (the falloff is in the colours),
     * breathing slowly, swelling with the blade's energy and flaring with its flash.
     */
    private static void halos(WeaponMesh mesh, PoseStack.Pose pose, VertexConsumer buffer, float energy, float flash) {
        var camera = Minecraft.getInstance().gameRenderer.mainCamera();
        float t = (Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime() % 24000) / 20F;
        float scale = pose.pose().getScale(SCALE).x;
        for (WeaponMesh.Anchor a : mesh.anchors()) {
            pose.pose().transformPosition(a.x(), a.y(), a.z(), AT);
            float r = a.radius() * scale * (1 + 0.35F * energy + 0.5F * flash);
            camera.leftVector().mul(-r, RIGHT);
            camera.upVector().mul(r, UP);
            float k = Math.min(1F, 0.30F + 0.08F * (float) Math.sin(t * 1.7 + a.x() * 9) + 0.45F * energy + 0.5F * flash);
            int cr = a.rgb() >> 16 & 255, cg = a.rgb() >> 8 & 255, cb = a.rgb() & 255, ca = Math.round(k * 255);
            for (int i = 0; i < HALO_SIDES; i++) {
                double a0 = i * Math.PI * 2 / HALO_SIDES, a1 = (i + 1) * Math.PI * 2 / HALO_SIDES;
                buffer.addVertex(AT.x(), AT.y(), AT.z()).setColor(cr, cg, cb, ca);
                rim(buffer, a0, cr, cg, cb);
                rim(buffer, a1, cr, cg, cb);
            }
        }
    }

    private static void rim(VertexConsumer buffer, double angle, int r, int g, int b) {
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        buffer.addVertex(AT.x() + RIGHT.x() * c + UP.x() * s, AT.y() + RIGHT.y() * c + UP.y() * s, AT.z() + RIGHT.z() * c + UP.z() * s)
                .setColor(r, g, b, 0);
    }

    /** The mesh's corners; vanilla's pipeline draws quads, so there each triangle repeats its last corner. */
    private static void emit(WeaponMesh mesh, PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, boolean ours) {
        int accent = ours ? mesh.accent() : 0xFFFFFF;
        int r = accent >> 16 & 255, g = accent >> 8 & 255, b = accent & 255;
        float[] p = mesh.pos(), uv = mesh.uv();
        byte[] n = mesh.normal(), along = mesh.along();
        for (int i = 0, count = mesh.vertices(); i < count; i++) {
            corner(buffer, pose, p, uv, n, i, r, g, b, ours ? along[i] & 255 : 255, light, overlay);
            if (!ours && i % 3 == 2) corner(buffer, pose, p, uv, n, i, r, g, b, 255, light, overlay);
        }
    }

    private static void corner(VertexConsumer buffer, PoseStack.Pose pose, float[] p, float[] uv, byte[] n, int i, int r, int g, int b, int a,
                               int light, int overlay) {
        buffer.addVertex(pose, p[i * 3], p[i * 3 + 1], p[i * 3 + 2])
                .setColor(r, g, b, a)
                .setUv(uv[i * 2], uv[i * 2 + 1])
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, n[i * 3] / 127F, n[i * 3 + 1] / 127F, n[i * 3 + 2] / 127F);
    }

    private static @Nullable WeaponMesh lineOf;
    private static final float[] LINE = new float[6];

    /**
     * Where the blade runs on a mesh (item space): its outer fifth, the line from the corner nearest the pommel to the
     * one furthest along (the weapons are straight along it), from 78% of the way to the point. Once per mesh.
     */
    private static float[] blade(WeaponMesh mesh) {
        if (lineOf == mesh) return LINE;
        float[] p = mesh.pos();
        byte[] al = mesh.along();
        int tip = 0, base = 0;
        for (int i = 0; i < mesh.vertices(); i++) {
            int a = al[i] & 255;
            if (a > (al[tip] & 255)) tip = i;
            if (a < (al[base] & 255)) base = i;
        }
        for (int c = 0; c < 3; c++) {
            LINE[c] = p[base * 3 + c] + (p[tip * 3 + c] - p[base * 3 + c]) * 0.78F;
            LINE[3 + c] = p[tip * 3 + c];
        }
        lineOf = mesh;
        return LINE;
    }

    private static @Nullable State last;
    private static final State[] OTHERS = new State[8];

    /** The fighter's own main-hand blade carries the fight; anyone else's (and one in a frame) is at rest. */
    @Override
    public @Nullable State extractArgument(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return null;
        if (stack != p.getMainHandItem()) return other(mc, stack);
        boolean hand = mc.options.getCameraType().isFirstPerson();
        int energy = 0, flash = 0;
        if (ArmsPredict.fights(p)) {
            float charge = ArmsPredict.charge();
            energy = charge >= 0 ? Math.round(charge * 255) : ArmsPredict.resonance >= ArmsRules.RESONANCE_MAX ? 150 : 0;
            flash = ArmsPredict.glinting() || ArmsPredict.perfectHeavy && ArmsPredict.move == Archetype.Move.HEAVY ? 255 : 0;
        }
        if (last == null || last.energy() != energy || last.flash() != flash || last.hand() != hand || last.wielder() != p.getId()) {
            last = new State(energy, flash, hand, p.getId());
        }
        return last;
    }

    /** Someone else's blade: at rest, but its holder known, so its blows trace (a few kept, nothing made per frame). */
    private static @Nullable State other(Minecraft mc, ItemStack stack) {
        if (mc.level == null) return null;
        for (var o : mc.level.players()) {
            if (o.getMainHandItem() != stack) continue;
            int slot = Math.floorMod(o.getId(), OTHERS.length);
            State st = OTHERS[slot];
            if (st == null || st.wielder() != o.getId()) OTHERS[slot] = st = new State(0, 0, false, o.getId());
            return st;
        }
        return null;
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        WeaponMesh mesh = WeaponMeshes.get(weapon);
        float[] b = mesh == null ? new float[]{0, 0, 0, 1, 1, 1} : mesh.bounds();
        for (int k = 0; k < 8; k++) output.accept(new Vector3f(b[(k & 1) * 3], b[1 + (k >> 1 & 1) * 3], b[2 + (k >> 2 & 1) * 3]));
    }

    record Unbaked(String weapon) implements SpecialModelRenderer.Unbaked<State> {
        static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("weapon").forGetter(Unbaked::weapon)).apply(i, Unbaked::new));

        @Override
        public SpecialModelRenderer<State> bake(SpecialModelRenderer.BakingContext context) {
            return new WeaponRenderer(weapon);
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
