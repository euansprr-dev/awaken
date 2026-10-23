package dev.awaken.powers.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.Awaken;
import dev.awaken.powers.net.PowerPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Energy beams, rails, grapple lines and spirit cords: the beacon beam drawn along any direction. The caster's own
 * channelled beam follows their live aim every frame; beams seen from others use the server's end point.
 */
final class BeamRenderer {
    private BeamRenderer() {}

    private record Active(int casterId, Vec3 end, int colour, int until, float width, float range, boolean follow) {}

    record Draw(Vec3 from, Vec3 to, int colour, float width) {}

    private static final List<Active> BEAMS = new ArrayList<>();
    private static final ContextKey<List<Draw>> KEY = new ContextKey<>(Awaken.id("power_beams"));

    static void onBeam(PowerPayloads.Beam msg) {
        int until = ClientPowers.clientTicks + msg.ticks();
        // a newer packet for the same caster and kind replaces the old beam
        BEAMS.removeIf(b -> b.casterId() == msg.casterId() && b.follow() == msg.follow() && b.follow());
        BEAMS.add(new Active(msg.casterId(), new Vec3(msg.x(), msg.y(), msg.z()), msg.colour(), until, msg.width(), msg.range(), msg.follow()));
        if (BEAMS.size() > 64) BEAMS.remove(0);
    }

    static void tick() {
        if (BEAMS.isEmpty()) return;
        for (Iterator<Active> it = BEAMS.iterator(); it.hasNext(); ) if (it.next().until() < ClientPowers.clientTicks) it.remove();
    }

    static void reset() {
        BEAMS.clear();
    }

    static void onExtract(ExtractLevelRenderStateEvent e) {
        if (BEAMS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = e.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        List<Draw> draws = new ArrayList<>();
        for (Active b : BEAMS) {
            Entity caster = mc.level.getEntity(b.casterId());
            if (caster == null) continue;
            Vec3 from = handOf(caster, pt);
            Vec3 to = b.end();
            if (b.follow() && caster == mc.player) {
                Vec3 eye = caster.getEyePosition(pt);
                Vec3 look = caster.getViewVector(pt);
                Vec3 far = eye.add(look.scale(b.range()));
                BlockHitResult hit = mc.level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
                to = hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
                // stop on the first living thing along the aim
                for (Entity en : mc.level.getEntities(caster, caster.getBoundingBox().expandTowards(look.scale(b.range())).inflate(1.0),
                        x -> x instanceof LivingEntity && x.isAlive())) {
                    var clip = en.getBoundingBox().inflate(0.3).clip(eye, to);
                    if (clip.isPresent()) to = clip.get();
                }
            }
            draws.add(new Draw(from, to, b.colour(), b.width()));
        }
        e.getRenderState().setRenderData(KEY, draws);
    }

    /** Roughly where the right hand is, so beams leave the hand rather than the face. */
    static Vec3 handOf(Entity e, float pt) {
        if (!(e instanceof LivingEntity)) return e.getPosition(pt).add(0, e.getBbHeight() * 0.5, 0);
        Vec3 look = e.getViewVector(pt);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 right = new Vec3(-flat.z, 0, flat.x);
        Minecraft mc = Minecraft.getInstance();
        boolean firstPerson = e == mc.player && mc.options.getCameraType().isFirstPerson();
        double down = firstPerson ? 0.3 : 0.45;
        return e.getEyePosition(pt).add(look.scale(firstPerson ? 0.5 : 0.3)).add(right.scale(firstPerson ? 0.3 : 0.35)).add(0, -down, 0);
    }

    static void onSubmit(SubmitCustomGeometryEvent e) {
        List<Draw> draws = e.getLevelRenderState().getRenderData(KEY);
        if (draws == null || draws.isEmpty()) return;
        Vec3 cam = e.getLevelRenderState().cameraRenderState.pos;
        PoseStack ps = e.getPoseStack();
        float time = (e.getLevelRenderState().gameTime % 40) + e.getLevelRenderState().worldPartialTicks;
        for (Draw d : draws) {
            Vec3 dir = d.to().subtract(d.from());
            double len = dir.length();
            if (len < 0.05) continue;
            Vector3f axis = new Vector3f((float) (dir.x / len), (float) (dir.y / len), (float) (dir.z / len));
            int blocks = Math.max(1, (int) Math.ceil(len));
            ps.pushPose();
            ps.translate(d.from().x - cam.x, d.from().y - cam.y, d.from().z - cam.z);
            ps.rotate(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), axis));
            ps.scale(1f, (float) (len / blocks), 1f);
            ps.translate(-0.5f, 0f, -0.5f);
            BeaconRenderer.submitBeaconBeam(ps, e.getSubmitNodeCollector(), BeaconRenderer.BEAM_LOCATION, 1.0f, time * 4f, 0, blocks,
                    d.colour(), d.width(), d.width() * 1.6f);
            ps.popPose();
        }
    }
}
