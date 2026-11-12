package dev.awaken.gates.client;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.textures.FilterMode;
import dev.awaken.Awaken;
import dev.awaken.compat.client.FarTerrain;
import dev.awaken.gates.net.GatePayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

/**
 * Light pillars over every known gate, coloured by rank, visible up to 1500 blocks. Pillars beyond a safe distance are
 * drawn at a clamped distance along the view ray and scaled down by the same factor, so they look exactly as far away as
 * they are but never fall past the far plane or into the fog. Their shader still hides them behind any land in front of
 * where they really stand, the chunks' and Far Sight's alike (gate_pillar.fsh; SPEC §7).
 */
public final class Pillars {
    private Pillars() {}

    /** What a draw is: a gate's pillar, a Warden's pillar, or a seal at the Lord's door, whole or broken (SPEC §50.9). */
    private static final int GATE = 0, WARDEN = 1, SEAL = 2, SEAL_BROKEN = 3;

    private record Draw(double dx, double dy, double dz, float scale, int height, int core, int glow, float solid, float halo, float alpha, int kind) {
        Draw(double dx, double dy, double dz, float scale, int height, int core, int glow, float solid, float halo) {
            this(dx, dy, dz, scale, height, core, glow, solid, halo, 1F, GATE);
        }
    }

    private static final ContextKey<List<Draw>> KEY = new ContextKey<>(Awaken.id("gate_pillars"));
    public static final int HEIGHT = 900;

    static void onExtract(ExtractLevelRenderStateEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.dimension() == dev.awaken.gates.realms.RealmsModule.REALMS) {
            realm(e, mc);
            return;
        }
        if (mc.level == null || mc.level.dimension() != Level.OVERWORLD) return;
        List<GatePayloads.Beacon> beacons = ClientGates.beacons();
        if (beacons.isEmpty()) return;
        Vec3 cam = e.getCamera().position();
        double clamp = Math.max(48, mc.options.getEffectiveRenderDistance() * 16 * 0.7);
        List<Draw> out = new ArrayList<>();
        for (GatePayloads.Beacon b : beacons) {
            // the beam rises from the top of the vortex, which grows with rank
            var size = dev.awaken.gates.GateVortex.size(b.rank());
            double bx = b.pos().getX() + 0.5, by = b.pos().getY() + size.centreY() + size.ry() * 1.08, bz = b.pos().getZ() + 0.5;
            double rx = bx - cam.x, ry = by - cam.y, rz = bz - cam.z;
            double dist = Math.sqrt(rx * rx + rz * rz + ry * ry);
            if (dist > GatePayloads.BEACON_RANGE + 100) continue;
            float k = dist > clamp ? (float) (clamp / dist) : 1F;
            float widen = (float) Math.max(1.0, Math.sqrt(rx * rx + rz * rz) / 96.0);
            // off screen (behind the camera, say): no pass of its own on the main target this frame, which on a tiled
            // GPU costs a load and store of colour and depth at 4K. The box holds the widest ribbon (the haze, 16 × halo)
            double r = 0.32 * widen * 16 * k + 2, px = cam.x + rx * k, py = cam.y + ry * k, pz = cam.z + rz * k;
            if (!e.getFrustum().isVisible(new net.minecraft.world.phys.AABB(px - r, py - 2, pz - r, px + r, py + HEIGHT * k, pz + r))) continue;
            int colour = ClientGates.rankColour(b.rank());
            int core = colour, glow = colour;
            if (b.rank() == 6) {
                core = 0xFF0C0A08;
                glow = 0xFFD4AF37;
            }
            if (b.broken()) {
                core = 0xFF7A1018;
                glow = 0xFF5A5A5A;
            }
            if ((b.flags() & GatePayloads.Beacon.RED) != 0 && !b.broken()) glow = 0xFFFF2030;
            out.add(new Draw(rx * k - 0.5, ry * k, rz * k - 0.5, k, HEIGHT, core, glow, 0.22F * widen, 0.32F * widen));
        }
        e.getRenderState().setRenderData(KEY, out);
    }

    /**
     * An open land's lights (SPEC §50.9 "Lights and seals"): a pillar over each Warden still standing, in the land's own
     * colour, and at the Lord's door a short seal for each. A light that went out fades away rather than vanish. Only the
     * gate the run HUD says you are in: another gate's land shares this dimension.
     */
    private static void realm(ExtractLevelRenderStateEvent e, Minecraft mc) {
        int gate = ClientGates.lightsGate;
        List<GatePayloads.RealmLight> lit = ClientGates.realmLights;
        List<ClientGates.Fading> fading = ClientGates.fading;
        if (gate < 0 || gate != ClientGates.hud().gateId() || lit.isEmpty() && fading.isEmpty()) return;
        Vec3 cam = e.getCamera().position();
        double clamp = Math.max(48, mc.options.getEffectiveRenderDistance() * 16 * 0.7);
        long now = System.currentTimeMillis();
        List<Draw> out = new ArrayList<>();
        for (GatePayloads.RealmLight l : lit) realmDraw(e, cam, clamp, l, 1F, out);
        for (ClientGates.Fading f : fading) {
            float a = 1F - (now - f.at()) / (float) ClientGates.FADE;
            if (a > 0.01F) realmDraw(e, cam, clamp, f.light(), a * a, out);
        }
        e.getRenderState().setRenderData(KEY, out);
    }

    private static void realmDraw(ExtractLevelRenderStateEvent e, Vec3 cam, double clamp, GatePayloads.RealmLight l, float alpha, List<Draw> out) {
        double rx = l.pos().getX() + 0.5 - cam.x, ry = l.pos().getY() - cam.y, rz = l.pos().getZ() + 0.5 - cam.z;
        double flat = Math.sqrt(rx * rx + rz * rz), dist = Math.sqrt(flat * flat + ry * ry);
        float k = dist > clamp ? (float) (clamp / dist) : 1F;
        // a Warden's pillar keeps about one width on screen past 48 blocks, so from across the land it is a column of light
        // and not a thread (two tours read the old one as a laser line from the Mouth); a seal is its own size
        float widen = l.seal() ? 1F : (float) Math.max(1.0, flat / 48.0);
        float reach = l.seal() ? SEAL_REACH : WARDEN_HAZE * widen;
        double r = reach * k + 2, px = cam.x + rx * k, py = cam.y + ry * k, pz = cam.z + rz * k;
        double top = (l.seal() ? l.height() + SEAL_REACH : l.height()) * k;
        if (!e.getFrustum().isVisible(new net.minecraft.world.phys.AABB(px - r, py - 2, pz - r, px + r, py + top, pz + r))) return;
        int kind = !l.seal() ? WARDEN : l.broken() ? SEAL_BROKEN : SEAL;
        out.add(new Draw(rx * k - 0.5, ry * k, rz * k - 0.5, k, l.height(), l.colour(), l.colour(), widen, widen, alpha, kind));
    }

    /**
     * The pillar itself (Euan, 2026-10-26: vanilla's beacon beam read as "a weird boxed outline" over Carillon, its halo a
     * flat translucent square column; then, of our first ribbons, "the outline of the main light is still there and
     * blocky"): soft light, never a box. Ribbons turned to face the camera about the pillar's axis, each with a bell-shaped
     * falloff across its width (several columns per side, the last at nothing with no slope, so no edge shows; a straight
     * ramp from the middle to the edge read as a hard-edged slab): a near-white core, the rank's glow, a wide haze. All
     * fade in at the foot and thin out with height to nothing well under the far plane, without the world's fog (it
     * counts height as distance, and cut the column off flat at the fog line; its blend also greyed the haze), and each
     * fades out as the camera comes inside its width. The core and glow are light added, full at
     * night; the haze is coloured air, alpha-blended, which carries the pillar against a daylight sky.
     * <p>Drawn after the level, in a pass of its own on the main target (depth-tested, no depth written), not through the
     * level's translucent passes: with improved transparency on, their composite writes the nearest translucent depth
     * into the main depth wherever a translucent shows, however faint, so the whole quad of every ribbon read as solid to
     * Lens, which left its clouds out there (the Far Sight tour's frames at the Roost: a hard-edged slab of plain sky
     * around the core). After the level, the pillar is sky to Lens and its clouds pass in front. Nothing allocated.
     */
    static void onAfterLevel(RenderLevelStageEvent.AfterLevel e) {
        List<Draw> draws = e.getLevelRenderState().getRenderData(KEY);
        if (draws == null || draws.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        float time = (e.getLevelRenderState().gameTime % 24000) + e.getLevelRenderState().worldPartialTicks;
        float dark = mc.level == null ? 1F : mc.level.getSkyDarken() / 11F;
        float day = 0.4F + 0.6F * dark; // 0.4 at noon, 1 at night
        // the far plane is at least four render distances out (Camera.update): every vertex stays well inside it
        float far = mc.options.getEffectiveRenderDistance() * 16 * 4 * 0.85F;
        // the pillars' own pipelines hide them behind any land in front of where they really stand (RigPipelines); a
        // driver that can't build them draws them as before, tested only against the chunks' depth where they are drawn
        var haze = RenderSystem.getCompiledPipelineNullable(RigPipelines.PILLAR_HAZE);
        var light = RenderSystem.getCompiledPipelineNullable(RigPipelines.PILLAR_LIGHT);
        stretched = haze != null && light != null;
        int quadBytes = stretched ? 96 : 64;
        GpuBuffer buf = ring.currentBuffer();
        int hazeQuads, allQuads;
        try (GpuBufferSlice.MappedView view = buf.map(false, true)) {
            at = MemoryUtil.memAddress(view.data());
            end = at + (long) MAX_QUADS * quadBytes;
            for (Draw d : draws) pillar(d, time, day, far, false);
            hazeQuads = quads(view, quadBytes);
            for (Draw d : draws) pillar(d, time, day, far, true);
            allQuads = quads(view, quadBytes);
        }
        if (allQuads > 0) {
            RenderTarget main = mc.gameRenderer.mainRenderTarget();
            RenderSystem.AutoStorageIndexBuffer quadIndex = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            GpuBuffer indices = quadIndex.getBuffer(allQuads * 6);
            GpuBuffer planes = stretched ? farPlanes() : null;
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "awaken gate pillars",
                    main.getColorTextureView(), Optional.empty(), main.getDepthTextureView(), OptionalDouble.empty())) {
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(e.getModelViewMatrix())));
                if (stretched) {
                    pass.setUniform("GatePillar", planes);
                    pass.setUniform("FarDepthSampler", FarTerrain.depthTexture().getTextureView(),
                            RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                }
                pass.setIndexBuffer(indices, quadIndex.type());
                pass.setVertexBuffer(0, buf.slice());
                if (hazeQuads > 0) {
                    pass.setPipeline(stretched ? haze : RenderSystem.getCompiledPipeline(RenderPipelines.DEBUG_QUADS));
                    pass.drawIndexed(hazeQuads * 6, 1, 0, 0, 0);
                }
                if (allQuads > hazeQuads) {
                    var glow = RenderSystem.getCompiledPipelineNullable(RigPipelines.GLOW);
                    pass.setPipeline(stretched ? light : glow != null ? glow : RenderSystem.getCompiledPipeline(RenderPipelines.DEBUG_QUADS));
                    pass.drawIndexed((allQuads - hazeQuads) * 6, 1, 0, hazeQuads * 4, 0);
                }
            }
            if (stretched) planeRing.rotate();
        }
        ring.rotate();
    }

    /**
     * This frame's far-land planes for the pillar shader: Far Sight's depth convention, and whether to test against it
     * at all (only while it draws and shares a depth; never under Distant Horizons, whose depth Lens trusts only once it
     * proves itself). The device's depth range always, for the pillar's own depth.
     */
    private static GpuBuffer farPlanes() {
        boolean test = FarTerrain.active() && FarTerrain.readable() && !DH;
        int flags = (FarTerrain.reverseZ() ? 1 : 0) | (FarTerrain.zeroToOne() ? 2 : 0)
                | (RenderSystem.getDevice().getDeviceInfo().isZZeroToOne() ? 4 : 0);
        GpuBuffer b = planeRing.currentBuffer();
        try (GpuBufferSlice.MappedView view = b.map(false, true)) {
            Std140Builder.intoBuffer(view.data()).putVec4(FarTerrain.near(), FarTerrain.far(), flags, test ? 1F : 0F);
        }
        return b;
    }

    private static final boolean DH = net.neoforged.fml.ModList.get().isLoaded("distanthorizons");
    private static final MappableRingBuffer planeRing = new MappableRingBuffer(() -> "awaken gate pillar planes",
            GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, new Std140SizeCalculator().putVec4().get());

    /** Room for every pillar's quads (a far pillar takes ~400, a near one ~800); past it the rest are left out. */
    private static final int MAX_QUADS = 24576;
    private static final MappableRingBuffer ring = new MappableRingBuffer(() -> "awaken gate pillars",
            GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, MAX_QUADS * 96);
    /** Where the next vertex goes in the mapped buffer, and where it ends. */
    private static long at, end;
    /** Vertices carry the pillar's stretch (POSITION_TEX_COLOR, the pillars' own pipelines) or not (POSITION_COLOR). */
    private static boolean stretched;
    /** How many times further off this pillar really stands than where it is drawn (1 / its scale). */
    private static float stretch = 1F;

    private static int quads(GpuBufferSlice.MappedView view, int quadBytes) {
        return (int) ((at - MemoryUtil.memAddress(view.data())) / quadBytes);
    }

    /** One vertex: position, the stretch when the pillars' own pipelines draw, and colour (r, g, b, a bytes). */
    private static void vertex(float x, float y, float z, int argb) {
        MemoryUtil.memPutFloat(at, x);
        MemoryUtil.memPutFloat(at + 4, y);
        MemoryUtil.memPutFloat(at + 8, z);
        at += 12;
        if (stretched) {
            MemoryUtil.memPutFloat(at, stretch);
            MemoryUtil.memPutFloat(at + 4, 0F);
            at += 8;
        }
        MemoryUtil.memPutInt(at, (argb & 0xFF00FF00) | (argb >> 16 & 0xFF) | (argb & 0xFF) << 16);
        at += 4;
    }

    /** Rows up the column (denser near the foot, where it is seen from close by). */
    private static final float[] ROWS = {0F, 0.004F, 0.012F, 0.025F, 0.045F, 0.07F, 0.1F, 0.14F, 0.19F, 0.25F, 0.32F, 0.4F, 0.5F, 0.6F,
            0.7F, 0.8F, 0.9F, 1F};
    /** The falloff across a ribbon, (1 - x²)³ at x = i / n from the middle out: bell-shaped, nothing and flat at the edge. */
    private static final float[] NEAR = bell(6), FAR = bell(3);

    private static float[] bell(int n) {
        float[] w = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            float x = i / (float) n, v = 1F - x * x;
            w[i] = v * v * v;
        }
        return w;
    }

    private static void pillar(Draw d, float time, float day, float far, boolean light) {
        float k = d.scale();
        stretch = 1F / k;
        float bx = (float) d.dx() + 0.5F * k, by = (float) d.dy(), bz = (float) d.dz() + 0.5F * k;
        // the ribbons turn about the pillar's axis to face the camera (the camera is at the origin)
        float tx = -bx, tz = -bz, tl = (float) Math.sqrt(tx * tx + tz * tz);
        if (tl < 1e-4F) return;
        float rx = -tz / tl, rz = tx / tl;
        float breath = (0.9F + 0.1F * Mth.sin(time * 0.045F + bx * 0.01F)) * d.alpha();
        if (d.kind() == SEAL || d.kind() == SEAL_BROKEN) {
            seal(d, bx, by, bz, rx, rz, time, day, breath, light);
            return;
        }
        // its top stays inside the far plane (vanilla's beam, and our first pillar, were cut off flat there)
        float h = Math.min(d.height() * k, (float) Math.sqrt(Math.max(0F, far * far - tl * tl)) - by);
        if (h < 6F) return;
        if (d.kind() == WARDEN) {
            warden(d, bx, by, bz, rx, rz, h, time, day, breath, light);
            return;
        }
        float[] bell = k < 0.999F ? FAR : NEAR;
        if (light) {
            // the core: thin and hot, light added (Lens's bloom takes it); stronger at night; it reaches highest
            ribbon(bell, bx, by, bz, rx, rz, h, d.solid() * 1.4F * k, brighten(d.core()), (0.55F + 0.45F * day) * breath, time, 0.6F, 1F);
            ribbon(bell, bx, by, bz, rx, rz, h, d.halo() * 3.5F * k, airy(d.glow(), day), 0.35F * day * breath, time, 1.0F, 0.8F);
        } else {
            // the glow and the haze in the rank's colour: by day they carry the pillar, at night the light does (pillar
            // tour: added light vanished against a noon sky); they thin out sooner than the core
            float tint = 1.15F - day;
            int air = airy(d.glow(), day);
            ribbon(bell, bx, by, bz, rx, rz, h, d.halo() * 6F * k, air, 0.5F * tint * breath, time, 1.0F, 0.65F);
            ribbon(bell, bx, by, bz, rx, rz, h, d.halo() * 16F * k, air, 0.18F * tint * d.alpha(), time, 0.4F, 0.5F);
        }
    }

    /** A Warden's pillar: half-widths of its core, its body and its haze, in blocks at 48 blocks off (wider further out). */
    private static final float WARDEN_CORE = 0.9F, WARDEN_BODY = 2.4F, WARDEN_HAZE = 6.5F;

    /**
     * A Warden's pillar (SPEC §50.9 "Lights and seals"; manager's tour review, 12 Nov: "still read as thin laser lines"):
     * its own look, not a gate's. A column of the land's colour several blocks wide, soft at both edges, with a warm core
     * that never burns to a white thread. By day the body is coloured air strong enough to stand against a noon sky (only
     * lightly paled, so amber stays amber); at night the added light carries it.
     */
    private static void warden(Draw d, float bx, float by, float bz, float rx, float rz, float h, float time, float day, float breath, boolean light) {
        float w = d.solid() * d.scale();
        int c = d.core();
        if (light) {
            ribbon(NEAR, bx, by, bz, rx, rz, h, WARDEN_CORE * w, warm(c), (0.7F + 0.3F * day) * breath, time, 0.5F, 1F);
            ribbon(NEAR, bx, by, bz, rx, rz, h, WARDEN_BODY * w, c, 0.3F * day * breath, time, 0.8F, 0.85F);
        } else {
            int air = tinted(c, day);
            ribbon(NEAR, bx, by, bz, rx, rz, h, WARDEN_BODY * w, air, (0.75F - 0.35F * day) * breath, time, 0.8F, 0.8F);
            ribbon(NEAR, bx, by, bz, rx, rz, h, WARDEN_HAZE * w, air, (0.28F - 0.1F * day) * d.alpha(), time, 0.4F, 0.55F);
        }
    }

    /** How far a seal's light reaches from its middle, in blocks (its halo). */
    private static final float SEAL_REACH = 2.8F;
    private static final float[] SEAL_FACE_R = {0F, 1.3F, 1.55F, 1.85F, 2.1F}, SEAL_FACE = {0.6F, 0.6F, 0.8F, 0.45F, 0F};
    private static final float[] SEAL_HALO_R = {1.8F, 2.2F, SEAL_REACH}, SEAL_HALO = {0.3F, 0.14F, 0F};
    private static final float[] SEAL_RING_R = {0.8F, 1.0F, 1.18F, 1.36F, 1.56F}, SEAL_RING = {0F, 0.6F, 1F, 0.6F, 0F};
    private static final float[] SEAL_CORE_R = {0F, 0.25F, 0.5F, 0.8F}, SEAL_CORE = {1F, 0.8F, 0.35F, 0F};
    private static final float[] BROKEN_R = {0.85F, 1.05F, 1.4F, 1.72F, 1.9F}, BROKEN = {0F, 0.75F, 0.9F, 0.75F, 0F};
    private static final float[] EMBER_R = {1.05F, 1.25F, 1.45F}, EMBER = {0F, 1F, 0F};

    /**
     * A seal at the Lord's door (manager's tour review, 12 Nov: "I can't tell there are three seals, or which are broken"):
     * a disc you can count from the road, hung over its feet and turned to face you. Whole, it's a medallion: a dark face
     * with a rim, so it stands out even against a lit door, a ring of the land's light round a hot middle, a halo, and a
     * soft shaft of light down to the ground. Broken, its Warden down, it's a dark ring split in two with a crack top and
     * bottom, the halves pulled apart, a last red glow cooling in it, and nothing lit.
     */
    private static void seal(Draw d, float bx, float by, float bz, float rx, float rz, float time, float day, float breath, boolean light) {
        float k = d.scale(), cy = by + d.height() * k;
        int c = d.core();
        if (d.kind() == SEAL_BROKEN) {
            if (light) disc(bx, cy, bz, rx, rz, EMBER_R, EMBER, k, 0xFF9A3A16, 0.2F * d.alpha(), 0.22F * k);
            else disc(bx, cy, bz, rx, rz, BROKEN_R, BROKEN, k, ash(c), 0.9F * d.alpha(), 0.22F * k);
            return;
        }
        if (light) {
            disc(bx, cy, bz, rx, rz, SEAL_RING_R, SEAL_RING, k, warm(c), (0.85F + 0.15F * day) * breath, 0F);
            disc(bx, cy, bz, rx, rz, SEAL_CORE_R, SEAL_CORE, k, warm(c), 0.9F * breath, 0F);
            // the shaft from the ground up to the disc, fading out into it
            ribbon(NEAR, bx, by, bz, rx, rz, d.height() * k, 0.45F * k, warm(c), 0.35F * breath, time, 0.3F, 1F);
        } else {
            disc(bx, cy, bz, rx, rz, SEAL_FACE_R, SEAL_FACE, k, dark(c), d.alpha(), 0F);
            disc(bx, cy, bz, rx, rz, SEAL_HALO_R, SEAL_HALO, k, tinted(c, day), (1.1F - 0.4F * day) * d.alpha(), 0F);
        }
    }

    /** Segments round a disc. */
    private static final int ROUND = 28;

    /**
     * A disc facing the camera (turned about the upright, as the ribbons are): rings from {@code radii[i]} to the next, each
     * edge's strength {@code profile[i]}, sizes times {@code k}. {@code split} over 0 cracks it top and bottom and pulls the
     * halves apart by that much, the left one sagging.
     */
    private static void disc(float cx, float cy, float cz, float rx, float rz, float[] radii, float[] profile, float k, int rgb, float strength, float split) {
        if (strength < 0.004F) return;
        int quad = stretched ? 96 : 64;
        for (int s = 0; s < ROUND; s++) {
            float a0 = s * Mth.TWO_PI / ROUND, a1 = (s + 1) * Mth.TWO_PI / ROUND, mid = (a0 + a1) * 0.5F;
            float ox = 0, oy = 0;
            if (split > 0) {
                // the cracks: a little off the top and bottom, so it reads broken and not just two arcs
                if (gap(mid, Mth.HALF_PI + 0.3F) < 0.2F || gap(mid, -Mth.HALF_PI - 0.25F) < 0.2F) continue;
                boolean left = Mth.cos(mid) < 0;
                ox = left ? -split : split;
                oy = left ? -split * 0.8F : 0;
            }
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            for (int i = 0; i + 1 < radii.length; i++) {
                if (at + quad > end) return;
                float r0 = radii[i] * k, r1 = radii[i + 1] * k;
                int i0 = col(rgb, strength * profile[i]), i1 = col(rgb, strength * profile[i + 1]);
                vertex(cx + rx * (r0 * c0 + ox), cy + r0 * s0 + oy, cz + rz * (r0 * c0 + ox), i0);
                vertex(cx + rx * (r1 * c0 + ox), cy + r1 * s0 + oy, cz + rz * (r1 * c0 + ox), i1);
                vertex(cx + rx * (r1 * c1 + ox), cy + r1 * s1 + oy, cz + rz * (r1 * c1 + ox), i1);
                vertex(cx + rx * (r0 * c1 + ox), cy + r0 * s1 + oy, cz + rz * (r0 * c1 + ox), i0);
            }
        }
    }

    /** How far apart two angles are, in radians (0 to pi). */
    private static float gap(float a, float b) {
        return Math.abs(Mth.wrapDegrees((a - b) * Mth.RAD_TO_DEG)) * Mth.DEG_TO_RAD;
    }

    /** The land's colour, paled only a little by day (a Warden's light stays its own colour against a noon sky). */
    private static int tinted(int c, float day) {
        float lift = 0.2F * Mth.clamp((1F - day) / 0.6F, 0F, 1F);
        return lerpWhite(c, lift);
    }

    /** Halfway to white: a warm core, not a white thread. */
    private static int warm(int c) {
        return lerpWhite(c, 0.5F);
    }

    /** A seal's face: the colour deep in shadow. */
    private static int dark(int c) {
        int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
        return 0xFF000000 | r * 18 / 100 << 16 | g * 16 / 100 << 8 | b * 14 / 100;
    }

    /** A broken seal: the colour burnt to ash, a dark grey with a little of its warmth left. */
    private static int ash(int c) {
        int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255, grey = (r + g + b) / 3;
        return 0xFF000000 | (grey * 22 + r * 6) / 100 << 16 | (grey * 22 + g * 4) / 100 << 8 | (grey * 22 + b * 3) / 100;
    }

    private static int lerpWhite(int c, float t) {
        int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
        return 0xFF000000 | (int) (r + (255 - r) * t) << 16 | (int) (g + (255 - g) * t) << 8 | (int) (b + (255 - b) * t);
    }

    /**
     * One soft ribbon: per row, the bell's columns from the middle out on each side. {@code reach}: the share of the
     * height where it has thinned to nothing (with no slope there either, so no top shows).
     */
    private static void ribbon(float[] bell, float bx, float by, float bz, float rx, float rz, float h, float half,
                               int argb, float strength, float time, float shimmer, float reach) {
        int n = bell.length - 1;
        // from inside or beside a ribbon it would hang across the view as a sheet (Euan, at Carillon and under the
        // Wayfarers' canopy at dusk: planes and a cone in the sky): it fades out as the camera comes within twice its
        // half-width, so up close only the core is left, a soft line
        float cx = bx, cz = bz, near = Mth.clamp((float) (Math.sqrt(cx * cx + cz * cz) - half * 0.5F) / (half * 1.5F), 0F, 1F);
        strength *= near * near * (3F - 2F * near);
        if (strength < 0.004F) return;
        float pa = 0;
        for (int i = 0; i < ROWS.length; i++) {
            float f = ROWS[i];
            if (i > 0 && ROWS[i - 1] >= reach) break;
            float y = by + f * h;
            // fade in over the foot (the first few blocks), thin out with height; rising bands of light
            // (a short seal at a door fades in over its first quarter, not its first six blocks)
            float foot = Mth.clamp(f * h / Math.min(6F, h * 0.25F), 0F, 1F), up = Mth.clamp(1F - f / reach, 0F, 1F);
            float bands = 1F - shimmer * 0.22F * (0.5F + 0.5F * Mth.sin(f * h * 0.08F - time * 0.09F));
            float a = strength * foot * up * up * (3F - 2F * up) * bands;
            if (i > 0) {
                float y0 = by + ROWS[i - 1] * h;
                if (at + (long) n * (stretched ? 192 : 128) > end) return;
                for (int j = 0; j < n; j++) {
                    float x0 = half * j / n, x1 = half * (j + 1) / n;
                    int b0 = col(argb, pa * bell[j]), b1 = col(argb, a * bell[j]), o0 = col(argb, pa * bell[j + 1]), o1 = col(argb, a * bell[j + 1]);
                    for (int side = -1; side <= 1; side += 2) {
                        float ix = rx * x0 * side, iz = rz * x0 * side, ox = rx * x1 * side, oz = rz * x1 * side;
                        vertex(bx + ix, y0, bz + iz, b0);
                        vertex(bx + ox, y0, bz + oz, o0);
                        vertex(bx + ox, y, bz + oz, o1);
                        vertex(bx + ix, y, bz + iz, b1);
                    }
                }
            }
            pa = a;
        }
    }

    /** The colour with the strength as its alpha (the additive blend weighs the colour by it). */
    private static int col(int rgb, float a) {
        return Mth.clamp((int) (a * 255F), 0, 255) << 24 | (rgb & 0xFFFFFF);
    }

    /**
     * By day the glow is lit air, paler than the sky behind it: the rank's colour lifted toward white (CI tour: a C-rank
     * haze in its own pale blue, darker than a noon sky, blended away to nothing and left a thread); at night its own.
     */
    private static int airy(int c, float day) {
        float lift = 0.55F * Mth.clamp((1F - day) / 0.6F, 0F, 1F);
        int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
        return 0xFF000000 | (int) (r + (255 - r) * lift) << 16 | (int) (g + (255 - g) * lift) << 8 | (int) (b + (255 - b) * lift);
    }

    /** Toward white: the core burns hotter than its glow (a dark core, the S-rank's, stays dark). */
    private static int brighten(int c) {
        int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
        if (r + g + b < 120) return c;
        return 0xFF000000 | Math.min(255, r + (255 - r) * 2 / 3) << 16 | Math.min(255, g + (255 - g) * 2 / 3) << 8 | Math.min(255, b + (255 - b) * 2 / 3);
    }
}
