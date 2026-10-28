package dev.awaken.arms.client;

import dev.awaken.arms.Archetype;
import dev.awaken.arms.Archetypes;
import dev.awaken.arms.ArmsData;
import dev.awaken.arms.ArmsItems;
import dev.awaken.core.api.BodyMaterial;
import dev.awaken.powers.client.fx.FxClient;
import dev.awaken.powers.fx.FxStyle;
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
 * What a blow looks like in the world (SPEC §25), on the shared effects engine (powers' FxClient: its budget, its
 * settings): the blade's arc as the blow lands, a crescent the hand's own arc sweeps, pushed out ahead of the eye so
 * it faces whoever looks (a thrust a straight streak), tinted by the weapon and brighter with each rhythm step (gold
 * on a perfect heavy, an Execution and the Art, whose wave also rolls on along the ground); and where it lands, sparks
 * or chips by what it met. The fighter's own on their prediction;
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
        // on the blade's own clock: its blow starts half the active before the first active tick (ArmsHands.blowStart)
        if (def != null) move(wielder, style, move, cadence, perfect, ArmsHands.blowStart(def));
    }

    /** As above, the blow {@code after} ticks from now (a strike that waited on its press resumes part way in). */
    static void move(Entity wielder, Archetypes.Loaded style, Archetype.Move move, int cadence, boolean perfect, float after) {
        Pending p = new Pending(wielder.getId(), ArmsPredict.ticks + Math.round(Math.max(0F, after)), style, move, cadence, perfect);
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
        ItemStack held = e instanceof LivingEntity l ? l.getMainHandItem() : ItemStack.EMPTY;
        int argb = colour(held, p.cadence, p.perfect, p.move);
        // a blade that lays its own ribbon (ours, or any in first person): the hand's arc stands aside for it
        if (BladeTrail.ownRibbon(e, !held.isEmpty() && ArmsItems.of(held.getItem()) != null)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || e.getId() != mc.player.getId() || !mc.options.getCameraType().isFirstPerson()) {
                BladeTrail.open(e.getId(), (def.active() + 1) * 50 + 30, argb);
            }
            if (def.art().isPresent()) artWave(e, def);
            return;
        }
        if (def.swish() == Archetype.Swish.THRUST) {
            Vec3 from = forward.scale(0.5).add(right.scale(0.22)).add(up.scale(-0.3));
            FxClient.thrust(e, from, forward, 0.3F, def.reach() * 0.85F, 0.11F, def.active(), argb);
            return;
        }
        if (def.art().isPresent()) artWave(e, def);
        List<Float> a = def.swing().from(), b = def.swing().to();
        float ax = HX + a.get(0) - SX, ay = HY + a.get(1) - SY, bx = HX + b.get(0) - SX, by = HY + b.get(1) - SY;
        float start = (float) Mth.atan2(ay, ax) * Mth.RAD_TO_DEG, end = (float) Mth.atan2(by, bx) * Mth.RAD_TO_DEG;
        float hand = (Mth.sqrt(ax * ax + ay * ay) + Mth.sqrt(bx * bx + by * by)) * 0.5F;
        float k = DEPTH / HAND_DEPTH;
        Vec3 centre = forward.scale(DEPTH).add(right.scale(SX * k)).add(up.scale(SY * k));
        // the rim on the blade's visible middle: in first person the tip runs off the top of the view
        FxClient.slash(e, centre, right, up, start, Mth.wrapDegrees(end - start), (hand + 0.28F) * k, 0.4F * k, def.active(), argb);
    }

    /** The Art's wave rolls on along the ground from the blade, out to its range. */
    private static void artWave(Entity e, Archetype.MoveDef def) {
        Vec3 forward = e.getViewVector(1F);
        Vec3 flat = new Vec3(forward.x, 0, forward.z);
        if (flat.lengthSqr() > 1e-6) FxClient.groundWave(e, flat.normalize(), def.art().get().range(), def.arc() * 0.5F, def.active() * 3, FxStyle.ARCHITECT);
    }

    /**
     * The blade's light: our weapons' own (their row's trail), anything else by its material; brighter each rhythm
     * step; gold on a perfect heavy or an Execution.
     */
    static int colour(ItemStack stack, int cadence, boolean perfect, Archetype.Move move) {
        if (perfect || move == Archetype.Move.EXECUTION || move == Archetype.Move.ART) return 0xE0FFD060;
        ArmsData.Weapon own = stack.isEmpty() ? null : ArmsItems.of(stack.getItem());
        String path = stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        int rgb;
        if (own != null) rgb = own.trail();
        else if (path.contains("diamond")) rgb = 0xA0F0FF;
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

    /**
     * The blow's way through the struck body and its weight (CombatFeedback.blow): ahead from the wielder, swept
     * sideways as the swing travels across (a thrust straight in), heavier with the move's damage.
     */
    static void blow(Entity target, Entity by, Archetype.MoveDef def, dev.awaken.arms.Blow b) {
        if (target instanceof LivingEntity && b.cleave() > 0) {
            // the server's own way for the blow, the one its push took (our creatures' clips play along it)
            dev.awaken.core.api.CombatFeedback.blow(target.getId(), b.dirX(), b.dirZ(), Mth.clamp(def.motion() / 1.2F, 0.6F, 3F));
            return;
        }
        float yaw = by.getYRot() * Mth.DEG_TO_RAD;
        float fx = -Mth.sin(yaw), fz = Mth.cos(yaw), rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
        float side = 0;
        if (def.swish() != Archetype.Swish.THRUST) {
            List<Float> from = def.swing().from(), to = def.swing().to();
            float dx = to.get(0) - from.get(0), dy = to.get(1) - from.get(1);
            side = Mth.clamp(dx / Math.max(0.05F, Math.abs(dx) + Math.abs(dy)), -1, 1);
            if (by instanceof LivingEntity l && l.getMainArm() == HumanoidArm.LEFT) side = -side;
        }
        dev.awaken.core.api.CombatFeedback.blow(target.getId(), fx * (1 - 0.6F * Math.abs(side)) + rx * side, fz * (1 - 0.6F * Math.abs(side)) + rz * side,
                Mth.clamp(def.motion() / 1.2F, 0.6F, 3F));
    }

    /** Where a blade landed, along the line from the wielder. */
    static void hit(Entity target, Entity by, Archetype.MoveDef def, dev.awaken.arms.Blow b) {
        blow(target, by, def, b);
        Vec3 centre = target.getBoundingBox().getCenter();
        Vec3 dir = centre.subtract(by.getEyePosition());
        dir = dir.lengthSqr() < 1e-6 ? Vec3.ZERO : dir.normalize();
        // on the body's near side, where the blade met it (from its centre the flare would be hidden inside)
        Vec3 at = centre.subtract(dir.scale(target.getBbWidth() * 0.55));
        float size = Mth.clamp(0.35F + def.motion() * 0.3F, 0.5F, 1.6F);
        FxClient.bladeHit(at, dir, size, BodyMaterial.of(target));
    }

    /**
     * Where a shot met a raised guard (SPEC §25 "Shots"), from the world yaw it came from: sparks off the blade back the
     * way it came, in the weapon's light. The fighter's own in first person on the guard's blade (just ahead of the eye,
     * toward the side it struck); anyone else's at the body's guard, chest high, out on that side.
     */
    static void guardSpark(Entity e, int fromYaw, boolean deflected) {
        Minecraft mc = Minecraft.getInstance();
        float yaw = fromYaw * Mth.DEG_TO_RAD;
        Vec3 from = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        boolean own = mc.player != null && e.getId() == mc.player.getId() && mc.options.getCameraType().isFirstPerson();
        Vec3 at;
        if (own) {
            float look = e.getYRot() * Mth.DEG_TO_RAD;
            int inv = e instanceof LivingEntity l && l.getMainArm() == HumanoidArm.LEFT ? -1 : 1;
            Vec3 forward = e.getViewVector(1F);
            Vec3 right = new Vec3(-Mth.cos(look), 0, -Mth.sin(look));
            Vec3 up = right.cross(forward);
            float side = Mth.clamp(Mth.sin(Mth.wrapDegrees(fromYaw - e.getYRot()) * Mth.DEG_TO_RAD), -0.7F, 0.7F);
            at = e.getEyePosition(1F).add(forward.scale(GUARD_DEPTH)).add(right.scale(inv * GUARD_X + side * GUARD_SIDE)).add(up.scale(GUARD_Y));
        } else {
            at = e.position().add(0, e.getBbHeight() * 0.68, 0).add(from.scale(e.getBbWidth() * 0.5 + 0.3));
        }
        ItemStack held = e instanceof LivingEntity l ? l.getMainHandItem() : ItemStack.EMPTY;
        int rgb = colour(held, 0, false, Archetype.Move.STRIKE_1) & 0xFFFFFF;
        FxClient.guardSpark(at.x, at.y, at.z, from.x, 0.15, from.z, rgb, deflected ? 1F : 0.55F, own);
    }

    // the guard's blade in first person (ArmsHands' guard pose), camera space: ahead, right of centre, below the eye;
    // a shot from one side meets the blade further out on that side
    private static final float GUARD_DEPTH = 0.8F, GUARD_X = 0.1F, GUARD_Y = -0.2F, GUARD_SIDE = 0.2F;

    static void reset() {
        java.util.Arrays.fill(PENDING, null);
    }
}
