package dev.awaken.veil.client;

import dev.awaken.veil.Cord;
import dev.awaken.veil.SpiritSight;
import dev.awaken.veil.Veil;
import dev.awaken.veil.VeilContent;
import dev.awaken.veil.net.VeilPayloads;
import dev.awaken.veil.world.TempleBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * What a spirit sees: the grey-blue fog at the edge of Awareness, faint ores, amber secrets, rifts, gates, the cord home
 * and the blue things it may touch. Everything here comes from server-sent positions (or, for blue blocks, from blocks
 * the client already has within six blocks).
 */
final class VeilWorld {
    private VeilWorld() {}

    private static final List<BlockPos> BLUE = new ArrayList<>();

    static int oreColour(byte kind) {
        return switch (kind) {
            case SpiritSight.COAL -> 0x6A7590;
            case SpiritSight.IRON -> 0xD8B7A0;
            case SpiritSight.COPPER -> 0xE07A4A;
            case SpiritSight.GOLD -> 0xF2C94C;
            case SpiritSight.REDSTONE -> 0xFF4A4F;
            case SpiritSight.LAPIS -> 0x3C6CF0;
            case SpiritSight.DIAMOND -> 0x6FF0E8;
            case SpiritSight.EMERALD -> 0x4FE08A;
            case SpiritSight.QUARTZ -> 0xF2EEE6;
            default -> 0xB48CFF;
        };
    }

    /** Per-tick gizmos (ClientTickEvent.Post runs inside the per-tick gizmo collector). */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        long age = VeilClientState.age;
        VeilPayloads.Sight sight = VeilClientState.sight;
        if (VeilClientState.spirit()) {
            float pulse = 0.5F + 0.5F * Mth.sin(age * 0.15F);
            for (int i = 0; i < sight.ores().length; i++) {
                int rgb = oreColour(i < sight.oreKinds().length ? sight.oreKinds()[i] : 0);
                Gizmos.cuboid(BlockPos.of(sight.ores()[i]), 0.02F, GizmoStyle.strokeAndFill(ARGB.color(110, rgb), 1.2F, ARGB.color(26, rgb)))
                        .setAlwaysOnTop();
            }
            secrets(sight.secrets(), (int) (90 + 90 * pulse), (int) (20 + 30 * pulse));
            rifts(mc, sight.rifts(), age);
            for (VeilPayloads.GateMark g : sight.gates()) {
                int rgb = g.colour() & 0xFFFFFF;
                Gizmos.cuboid(new AABB(g.pos()).inflate(1.0, 2.0, 1.0), GizmoStyle.stroke(ARGB.color(g.scouted() ? 120 : 200, rgb), 2.0F)).setAlwaysOnTop();
                Gizmos.billboardTextOverBlock(g.label() + (g.scouted() ? "  ·  scouted" : ""), g.pos().above(3), 0, ARGB.color(255, rgb), 0.6F)
                        .setAlwaysOnTop();
            }
            cord(mc, p, age);
            if (age % 10 == 0) scanBlue(mc, p);
            int blue = ARGB.color((int) (130 + 110 * pulse), 0x7FC3FF);
            for (BlockPos b : BLUE) Gizmos.cuboid(b, 0.04F, GizmoStyle.strokeAndFill(blue, 2.0F, ARGB.color(30, 0x7FC3FF))).setAlwaysOnTop();
        } else {
            BLUE.clear();
            if (sight.awake() && sight.secrets().length > 0) {
                float pulse = 0.5F + 0.5F * Mth.sin(age * 0.1F);
                secrets(sight.secrets(), (int) (40 + 50 * pulse), (int) (8 + 14 * pulse));
            }
            if (VeilClientState.state.dreaming() && Veil.inStillpoint(p)) {
                // the In door wakes while you sleep
                for (int y = 0; y < 3; y++) for (int z = -1; z <= 1; z++) {
                    BlockPos d = TempleBuilder.DOOR_IN.offset(0, y, z);
                    Gizmos.cuboid(d, 0.01F, GizmoStyle.fill(ARGB.color((int) (60 + 50 * Mth.sin(age * 0.1F)), 0xF6C6E6)));
                }
                if (age % 3 == 0) mc.level.addParticle(ParticleTypes.REVERSE_PORTAL, TempleBuilder.DOOR_IN.getX() + 0.5,
                        TempleBuilder.DOOR_IN.getY() + mc.level.getRandom().nextDouble() * 3, TempleBuilder.DOOR_IN.getZ() + mc.level.getRandom().nextDouble() * 3 - 1,
                        0.05, 0.02, 0);
            }
        }
    }

    private static void secrets(long[] secrets, int strokeAlpha, int fillAlpha) {
        for (long s : secrets) {
            Gizmos.cuboid(BlockPos.of(s), 0.05F, GizmoStyle.strokeAndFill(ARGB.color(strokeAlpha, 0xF2A541), 1.6F, ARGB.color(fillAlpha, 0xFFD28A)))
                    .setAlwaysOnTop();
        }
    }

    private static void rifts(Minecraft mc, long[] rifts, long age) {
        for (long r : rifts) {
            Vec3 c = Vec3.atCenterOf(BlockPos.of(r));
            for (int k = 0; k < 3; k++) {
                float phase = (age * 0.05F + k / 3F) % 1F;
                Gizmos.circle(c.add(0, (phase - 0.5) * 1.6, 0), 0.4F + 0.9F * (1 - Math.abs(phase - 0.5F) * 2),
                        GizmoStyle.stroke(ARGB.color((int) (200 * (1 - Math.abs(phase - 0.5F) * 2)), 0xB48CFF), 2.0F)).setAlwaysOnTop();
            }
            if (age % 2 == 0) {
                var rnd = mc.level.getRandom();
                mc.level.addParticle(ParticleTypes.REVERSE_PORTAL, c.x + (rnd.nextDouble() - 0.5), c.y + (rnd.nextDouble() - 0.5) * 1.5,
                        c.z + (rnd.nextDouble() - 0.5), 0, 0.02, 0);
            }
        }
    }

    private static void cord(Minecraft mc, LocalPlayer p, long age) {
        var st = VeilClientState.state;
        if (!st.bodyHere()) return;
        Vec3 from = st.body();
        Vec3 to = p.position().add(0, 0.4, 0);
        double tension = Cord.tension(p.position().distanceTo(st.anchor()), Math.max(1, st.cord()));
        int rgb = tension >= Cord.DANGER ? 0xFF8A7A : tension >= Cord.WARN ? 0xFFCF7A : 0xC3CBFF;
        int alpha = tension >= Cord.WARN ? (int) (150 + 90 * Mth.sin(age * 0.6F)) : 120;
        // a gentle sag so it reads as a cord, not a laser
        Vec3 mid = from.lerp(to, 0.5).add(0, -Math.min(2.0, from.distanceTo(to) * 0.06), 0);
        Gizmos.line(from, mid, ARGB.color(alpha, rgb), 2.0F).setAlwaysOnTop();
        Gizmos.line(mid, to, ARGB.color(alpha, rgb), 2.0F).setAlwaysOnTop();
    }

    private static void scanBlue(Minecraft mc, LocalPlayer p) {
        BLUE.clear();
        Entity cam = mc.getCameraEntity() == null ? p : mc.getCameraEntity();
        BlockPos c = cam.blockPosition();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -6; x <= 6; x++) for (int y = -4; y <= 5; y++) for (int z = -6; z <= 6; z++) {
            m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
            if (mc.level.getBlockState(m).is(VeilContent.VEIL_INTERACTABLE)) {
                BLUE.add(m.immutable());
                if (BLUE.size() >= 64) return;
            }
        }
    }

    // ---- fog, colour, camera ----------------------------------------------------------------------------------------

    static void onFog(ViewportEvent.RenderFog e) {
        if (!VeilClientState.spirit()) return;
        float a = Math.max(Cord.MIN_AWARENESS, VeilClientState.state.awareness());
        e.setNearPlaneDistance(a * 0.35F);
        e.setFarPlaneDistance(a);
        var fog = e.getFogData();
        fog.skyEnd = Math.min(fog.skyEnd, a * 1.5F);
        fog.cloudEnd = Math.min(fog.cloudEnd, a * 2F);
        fog.renderDistanceEnd = Math.min(fog.renderDistanceEnd, a * 1.1F);
        fog.renderDistanceStart = Math.min(fog.renderDistanceStart, a * 0.5F);
    }

    static void onFogColour(ViewportEvent.ComputeFogColor e) {
        if (!VeilClientState.spirit()) return;
        e.setRed(Mth.lerp(0.75F, e.getRed(), 0.55F));
        e.setGreen(Mth.lerp(0.75F, e.getGreen(), 0.62F));
        e.setBlue(Mth.lerp(0.75F, e.getBlue(), 0.74F));
    }

    static void onCamera(ViewportEvent.ComputeCameraAngles e) {
        if (!VeilClientState.possessing()) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        float pt = (float) e.getPartialTick();
        e.setYaw(p.getViewYRot(pt));
        e.setPitch(p.getViewXRot(pt));
    }
}
