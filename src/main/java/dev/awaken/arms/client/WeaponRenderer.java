package dev.awaken.arms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.arms.Archetype;
import dev.awaken.arms.ArmsRules;
import dev.awaken.arms.FpSwings;
import dev.awaken.arms.WeaponMesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.util.Mth;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
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
     * who lays its ribbon (an entity id, -1 for nobody: a stand, a frame); and in third person, the arm it is in (0 not
     * a hand's, 1 right, 2 left) and whose, so a move's wrist turns it ({@link ArmsWrist}).
     */
    record State(int energy, int flash, boolean hand, int wielder, int arm, int holder) {}

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
        // third person: the wrist turns the weapon about its grip, its working side into the blow and out of the body
        boolean turn = state != null && state.arm() != 0 && ArmsWrist.turn(state.holder(), state.arm() == 2, WRIST);
        if (turn) {
            // baked in the forged weapon's frame: one turned over in its hold takes it turned over too
            if (FpSwings.flipped3(weapon)) WRIST.premul(FLIP).mul(FLIP);
            poseStack.pushPose();
            Vector3f g = FpSwings.grip3(weapon); // a haft is held down it (the fist's spot, not the item's grip)
            if (g == null) poseStack.rotateAround(WRIST, GRIP, GRIP, 0.5F);
            else poseStack.rotateAround(WRIST, g.x, g.y, g.z);
        }
        boolean ours = WeaponPipelines.ok();
        int packed = ours ? (state == null ? 0 : state.energy()) | ((state == null ? 0 : state.flash()) | (foil ? 256 : 0)) << 16 : overlay;
        collector.submitCustomGeometry(poseStack, ours ? WeaponPipelines.weapon(texture) : WeaponPipelines.fallback(texture),
                (pose, buffer) -> emit(mesh, pose, buffer, light, packed, ours));
        // its light lifting off it, from where it is drawn (the energy: a charging heavy sheds more)
        WeaponMotes.emit(mesh, poseStack.last().pose(), state != null && state.hand(), state == null ? 0 : state.energy() / 255F);
        // a blow in the air: the blade's own ribbon, from its middle to its point
        if (state != null && state.wielder() >= 0 && BladeTrail.open(state.wielder())) {
            float[] line = blade(mesh);
            // first person from mid-blade (the tip's path mostly leaves the view), from 78% along seen from outside
            float k = state.hand() ? (0.5F - 0.78F) / (1F - 0.78F) : 0F;
            BladeTrail.sample(state.wielder(), poseStack.last().pose(), line[0] + (line[0] - line[3]) * -k, line[1] + (line[1] - line[4]) * -k,
                    line[2] + (line[2] - line[5]) * -k, line[3], line[4], line[5], state.hand());
        }
        if (ours && !mesh.anchors().isEmpty()) {
            float energy = state == null ? 0 : state.energy() / 255F, flash = state == null ? 0 : state.flash() / 255F;
            collector.submitCustomGeometry(poseStack, WeaponPipelines.glow(), (pose, buffer) -> halos(mesh, pose, buffer, energy, flash));
        }
        if (turn) poseStack.popPose();
    }

    /** Where the hand closes on every weapon (arms_weapons.HAND: along the item's diagonal, item space). */
    private static final float GRIP = 3F / 16F;
    private static final Quaternionf WRIST = new Quaternionf();
    /** Half round about the item's diagonal (arms_holds.third_person's turn-over). */
    private static final Quaternionf FLIP = new Quaternionf(Mth.SQRT_OF_TWO / 2, Mth.SQRT_OF_TWO / 2, 0F, 0F);

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
        State s = resting(stack);
        return ArmsWrist.marking == 0 ? s : inArm(s, ArmsWrist.marking, ArmsWrist.markedHolder);
    }

    private static final State[] ARMS = new State[16];

    /** A hand's weapon in third person, marked with its arm and holder (a few kept, nothing made per frame). */
    private static State inArm(@Nullable State s, int arm, int holder) {
        int energy = s == null ? 0 : s.energy(), flash = s == null ? 0 : s.flash(), wielder = s == null ? -1 : s.wielder();
        int slot = Math.floorMod(holder * 2 + arm, ARMS.length);
        State st = ARMS[slot];
        if (st == null || st.energy() != energy || st.flash() != flash || st.wielder() != wielder || st.arm() != arm || st.holder() != holder) {
            ARMS[slot] = st = new State(energy, flash, false, wielder, arm, holder);
        }
        return st;
    }

    private @Nullable State resting(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return null;
        boolean hand = mc.options.getCameraType().isFirstPerson();
        if (stack != p.getMainHandItem()) {
            // the off hand's twin (a pair, the Daggers): it lays the ribbon while it strikes, at rest otherwise
            if (stack == ArmsOffHand.twinOf(p)) return ArmsHands.offStrikes() ? twin(p, hand) : null;
            return other(mc, stack);
        }
        int wielder = ArmsHands.offStrikes() ? -1 : p.getId();
        int energy = 0, flash = 0;
        if (ArmsPredict.fights(p)) {
            float charge = ArmsPredict.charge();
            energy = charge >= 0 ? Math.round(charge * 255) : ArmsPredict.resonance >= ArmsRules.RESONANCE_MAX ? 150 : 0;
            flash = ArmsPredict.glinting() || ArmsPredict.perfectHeavy && ArmsPredict.move == Archetype.Move.HEAVY ? 255 : 0;
        }
        if (last == null || last.energy() != energy || last.flash() != flash || last.hand() != hand || last.wielder() != wielder) {
            last = new State(energy, flash, hand, wielder, 0, -1);
        }
        return last;
    }

    private static @Nullable State twinState;

    private static State twin(LocalPlayer p, boolean hand) {
        if (twinState == null || twinState.hand() != hand || twinState.wielder() != p.getId()) twinState = new State(0, 0, hand, p.getId(), 0, -1);
        return twinState;
    }

    /** Someone else's blade: at rest, but its holder known, so its blows trace (a few kept, nothing made per frame). */
    private static @Nullable State other(Minecraft mc, ItemStack stack) {
        if (mc.level == null) return null;
        for (var o : mc.level.players()) {
            if (o.getMainHandItem() != stack) continue;
            int slot = Math.floorMod(o.getId(), OTHERS.length);
            State st = OTHERS[slot];
            if (st == null || st.wielder() != o.getId()) OTHERS[slot] = st = new State(0, 0, false, o.getId(), 0, -1);
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
