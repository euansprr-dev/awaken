package dev.awaken.arms.client;

import dev.awaken.arms.Archetype;
import dev.awaken.arms.Archetypes;
import dev.awaken.core.api.BodyMaterial;
import dev.awaken.powers.client.fx.FxClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * What a blow looks like in the world (SPEC §24), on the shared effects engine (powers' FxClient: its budget, its
 * settings): the blade's arc as the blow lands, a crescent the hand's own arc sweeps, pushed out ahead of the eye so
 * it faces whoever looks (a thrust a straight streak), tinted by the weapon and brighter with each rhythm step (gold
 * on a perfect heavy); and where it lands, sparks or chips by what it met. The fighter's own on their prediction;
 * everyone else's from the server's word.
 */
final class ArmsFx {
    private ArmsFx() {}

    /** A slash waiting for its move's wind-up to end. */
    private record Pending(int entity, int at, Archetypes.Loaded style, Archetype.Move move, int cadence, boolean perfect) {}

    private static final Pending[] PENDING = new Pending[16];

    /** Queues the move's arc for the tick its blow starts. */
    static void move(Entity wielder, Archetypes.Loaded style, Archetype.Move move, int cadence, boolean perfect) {
        Archetype.MoveDef def = style.move(move);
        if (def == null) return;
        Pending p = new Pending(wielder.getId(), ArmsPredict.ticks + def.windup(), style, move, cadence, perfect);
        for (int i = 0; i < PENDING.length; i++) {
            if (PENDING[i] == null || PENDING[i].entity == wielder.getId()) {
                PENDING[i] = p;
                return;
            }
        }
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (int i = 0; i < PENDING.length; i++) {
            Pending p = PENDING[i];
            if (p == null || ArmsPredict.ticks < p.at) continue;
            PENDING[i] = null;
            Entity e = mc.level.getEntity(p.entity);
            if (e != null) slash(e, p);
        }
    }

    // the hands' camera space (ArmsHands): the resting hand, the shoulder its arc turns about, at the hand's depth;
    // the trail is that arc pushed out ahead of the eye, so it faces whoever looks and lines up with the blade
    private static final float HX = 0.56F, HY = -0.52F, SX = 0.35F, SY = -0.75F, HAND_DEPTH = 0.72F, DEPTH = 1.5F;

    private static void slash(Entity e, Pending p) {
        Archetype.MoveDef def = p.style.move(p.move);
        if (def == null) return;
        float yaw = e.getYRot() * Mth.DEG_TO_RAD;
        Vec3 forward = e.getViewVector(1F);
        int inv = e instanceof LivingEntity l && l.getMainArm() == HumanoidArm.LEFT ? -1 : 1;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)).scale(inv);
        Vec3 up = right.scale(inv).cross(forward);
        int argb = colour(e instanceof LivingEntity l ? l.getMainHandItem() : ItemStack.EMPTY, p.cadence, p.perfect, p.move);
        if (def.swish() == Archetype.Swish.THRUST) {
            Vec3 from = forward.scale(0.5).add(right.scale(0.22)).add(up.scale(-0.3));
            FxClient.thrust(e, from, forward, 0.3F, def.reach() * 0.85F, 0.11F, def.active(), argb);
            return;
        }
        List<Float> a = def.swing().from(), b = def.swing().to();
        float ax = HX + a.get(0) - SX, ay = HY + a.get(1) - SY, bx = HX + b.get(0) - SX, by = HY + b.get(1) - SY;
        float start = (float) Mth.atan2(ay, ax) * Mth.RAD_TO_DEG, end = (float) Mth.atan2(by, bx) * Mth.RAD_TO_DEG;
        float hand = (Mth.sqrt(ax * ax + ay * ay) + Mth.sqrt(bx * bx + by * by)) * 0.5F;
        float k = DEPTH / HAND_DEPTH;
        Vec3 centre = forward.scale(DEPTH).add(right.scale(SX * k)).add(up.scale(SY * k));
        // the rim on the blade's visible middle: in first person the tip runs off the top of the view
        FxClient.slash(e, centre, right, up, start, Mth.wrapDegrees(end - start), (hand + 0.28F) * k, 0.4F * k, def.active(), argb);
    }

    /** The blade's light: the weapon's material, brighter each rhythm step; gold on a perfect heavy or an Execution. */
    static int colour(ItemStack stack, int cadence, boolean perfect, Archetype.Move move) {
        if (perfect || move == Archetype.Move.EXECUTION) return 0xE0FFD060;
        String path = stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        int rgb;
        if (path.contains("diamond")) rgb = 0xA0F0FF;
        else if (path.contains("netherite")) rgb = 0xD0B4FF;
        else if (path.contains("gold")) rgb = 0xFFD98A;
        else if (path.contains("copper")) rgb = 0xFFC09A;
        else if (path.contains("iron")) rgb = 0xE6EEF8;
        else if (path.contains("stone")) rgb = 0xD8D8D8;
        else if (path.contains("wood")) rgb = 0xE6D2A8;
        else if (path.endsWith("_blade")) rgb = 0x9FD8FF;
        else rgb = 0xF0F6FF;
        int alpha = Math.round(255 * Math.min(0.9F, 0.42F + 0.14F * cadence));
        return alpha << 24 | rgb;
    }

    /** Where a blade landed, along the line from the wielder. */
    static void hit(Entity target, Entity by, Archetype.MoveDef def) {
        Vec3 centre = target.getBoundingBox().getCenter();
        Vec3 dir = centre.subtract(by.getEyePosition());
        dir = dir.lengthSqr() < 1e-6 ? Vec3.ZERO : dir.normalize();
        // on the body's near side, where the blade met it (from its centre the flare would be hidden inside)
        Vec3 at = centre.subtract(dir.scale(target.getBbWidth() * 0.55));
        float size = Mth.clamp(0.35F + def.motion() * 0.3F, 0.5F, 1.6F);
        FxClient.bladeHit(at, dir, size, BodyMaterial.of(target));
    }

    static void reset() {
        java.util.Arrays.fill(PENDING, null);
    }
}
