package dev.awaken.mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.mobs.MobSounds;
import dev.awaken.mobs.def.BoltKind;
import dev.awaken.mobs.def.ShotLook;
import dev.awaken.mobs.entity.AwBolt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.joml.Vector3f;

/**
 * A creature's shot (SPEC §47.2): a glowing core in the colour of how you answer it (white-gold, amber, red), a halo
 * breathing round it and a tail tapering back along its flight in its maker's colour, all light (Lens blooms it); a
 * seep heart's shard keeps its glass under the glow. It hums as it flies, from where it is, so one behind you is heard.
 */
final class ShotRenderer extends EntityRenderer<AwBolt, ShotRenderer.State> {
    private final ItemModelResolver items;

    ShotRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.items = ctx.getItemModelResolver();
    }

    static final class State extends ThrownItemRenderState {
        int cls, theme;
        float vx, vy, vz, age;
        boolean shard;
        BoltKind.Form form = BoltKind.Form.ROUND;
        /** A gate monster's own shot (SPEC §60.7), or null. */
        ShotLook look;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    protected int getBlockLightLevel(AwBolt entity, BlockPos pos) {
        return 15;
    }

    @Override
    public void extractRenderState(AwBolt e, State s, float pt) {
        super.extractRenderState(e, s, pt);
        s.shard = e.kind() == BoltKind.DEEP;
        s.look = e.look();
        // a body's own shot is the thing itself with the glow round it: the glow keeps its round form, smaller
        s.form = s.look != null ? BoltKind.Form.ROUND : e.kind().form;
        if (s.shard) items.updateForNonLiving(s.item, e.getItem(), ItemDisplayContext.GROUND, e);
        else s.item.clear();
        s.cls = e.shownClass().colour();
        s.theme = e.theme() & 0xFFFFFF;
        Vec3 v = e.getDeltaMovement();
        s.vx = (float) v.x;
        s.vy = (float) v.y;
        s.vz = (float) v.z;
        s.age = e.age() + pt;
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector out, CameraRenderState camera) {
        if (s.shard) {
            ps.pushPose();
            ps.scale(0.9F, 0.9F, 0.9F);
            ps.rotate(camera.orientation);
            s.item.submit(ps, out, s.lightCoords, OverlayTexture.NO_OVERLAY, s.outlineColor);
            ps.popPose();
        }
        // the camera's axes (the same for every shot this frame) and the shot's place relative to the eye
        RIGHT.set(1, 0, 0).rotate(camera.orientation);
        UP.set(0, 1, 0).rotate(camera.orientation);
        final float rx = RIGHT.x, ry = RIGHT.y, rz = RIGHT.z, ux = UP.x, uy = UP.y, uz = UP.z;
        final float ex = (float) (s.x - camera.pos.x), ey = (float) (s.y - camera.pos.y), ez = (float) (s.z - camera.pos.z);
        final int cls = s.cls, theme = s.theme;
        final float vx = s.vx, vy = s.vy, vz = s.vz, age = s.age;
        final boolean shard = s.shard || s.look != null;
        final BoltKind.Form form = s.form;
        final ShotLook look = s.look;
        if (look != null) {
            out.submitCustomGeometry(ps, sprite(look), (pose, vc) -> thing(pose, vc, look, rx, ry, rz, ux, uy, uz, vx, vy, vz, age));
        }
        out.order(1).submitCustomGeometry(ps, CarrierPipelines.light(), (pose, vc) -> draw(pose, vc, rx, ry, rz, ux, uy, uz, ex, ey, ez, cls, theme,
                vx, vy, vz, age, shard, form));
        super.submit(s, ps, out, camera);
    }

    private static final Vector3f RIGHT = new Vector3f(), UP = new Vector3f();
    private static final RenderType[] SPRITES = new RenderType[ShotLook.values().length];

    private static RenderType sprite(ShotLook look) {
        RenderType t = SPRITES[look.ordinal()];
        if (t == null) SPRITES[look.ordinal()] = t = RenderTypes.entityCutout(look.texture());
        return t;
    }

    /**
     * The thing a body throws (SPEC §60.7): its 16x8 sprite, tip first. A dart is two planes crossed along its flight
     * (turning about it if it spins); anything else faces the eye, turning in the eye's plane.
     */
    private static void thing(PoseStack.Pose pose, VertexConsumer vc, ShotLook look, float rx, float ry, float rz, float ux, float uy, float uz,
                              float vx, float vy, float vz, float age) {
        float hl = look.length * 0.5F, hw = look.length * 0.25F;
        float turn = look.spin * age;
        float speed = Mth.sqrt(vx * vx + vy * vy + vz * vz);
        if (look.along && speed > 1.0E-3F) {
            float fx = vx / speed, fy = vy / speed, fz = vz / speed;
            // two axes across the flight: one level (the flight crossed with up), one from the two
            float ax = fz, ay = 0, az = -fx;
            float an = Mth.sqrt(ax * ax + az * az);
            if (an < 1.0E-4F) {
                ax = 1;
                az = 0;
                an = 1;
            }
            ax /= an;
            az /= an;
            float bx = fy * az - fz * ay, by = fz * ax - fx * az, bz = fx * ay - fy * ax;
            float c = Mth.cos(turn), s = Mth.sin(turn);
            float px = ax * c + bx * s, py = ay * c + by * s, pz = az * c + bz * s;
            float qx = bx * c - ax * s, qy = by * c - ay * s, qz = bz * c - az * s;
            plane(pose, vc, fx * hl, fy * hl, fz * hl, px * hw, py * hw, pz * hw);
            plane(pose, vc, fx * hl, fy * hl, fz * hl, qx * hw, qy * hw, qz * hw);
            return;
        }
        float c = Mth.cos(turn), s = Mth.sin(turn);
        // its tip leads: the sprite's long axis lies along the flight as the eye sees it, else along the eye's right
        float lx = rx, ly = ry, lz = rz, wx = ux, wy = uy, wz = uz;
        if (speed > 1.0E-3F && look.spin == 0) {
            float dr = vx * rx + vy * ry + vz * rz, du = vx * ux + vy * uy + vz * uz;
            float n = Mth.sqrt(dr * dr + du * du);
            if (n > 1.0E-4F) {
                dr /= n;
                du /= n;
                lx = rx * dr + ux * du;
                ly = ry * dr + uy * du;
                lz = rz * dr + uz * du;
                wx = ux * dr - rx * du;
                wy = uy * dr - ry * du;
                wz = uz * dr - rz * du;
            }
        }
        float px = lx * c + wx * s, py = ly * c + wy * s, pz = lz * c + wz * s;
        float qx = wx * c - lx * s, qy = wy * c - ly * s, qz = wz * c - lz * s;
        plane(pose, vc, px * hl, py * hl, pz * hl, qx * hw, qy * hw, qz * hw);
    }

    /** One plane of a sprite, centred on the shot: (lx, ly, lz) half its length (toward its tip), (wx, wy, wz) half its width. */
    private static void plane(PoseStack.Pose pose, VertexConsumer vc, float lx, float ly, float lz, float wx, float wy, float wz) {
        vertex(pose, vc, -lx - wx, -ly - wy, -lz - wz, 0, 1);
        vertex(pose, vc, lx - wx, ly - wy, lz - wz, 1, 1);
        vertex(pose, vc, lx + wx, ly + wy, lz + wz, 1, 0);
        vertex(pose, vc, -lx + wx, -ly + wy, -lz + wz, 0, 0);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float u, float v) {
        vc.addVertex(pose, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
    }

    private static void draw(PoseStack.Pose pose, VertexConsumer vc, float rx, float ry, float rz, float ux, float uy, float uz,
                             float ex, float ey, float ez, int cls, int theme, float vx, float vy, float vz, float age, boolean shard, BoltKind.Form form) {
        float breathe = 0.85F + 0.15F * Mth.sin(age * 0.9F);
        float c = shard ? 0.6F : 1F;
        // the tail: back along its flight, as long as about seven ticks of it, the class's colour into the maker's
        float speed = Mth.sqrt(vx * vx + vy * vy + vz * vz);
        if (speed > 1.0E-3F) {
            float len = Math.min(3.2F, speed * 7F) / speed;
            float mx = -vx * len * 0.4F, my = -vy * len * 0.4F, mz = -vz * len * 0.4F;
            float tx = -vx * len, ty = -vy * len, tz = -vz * len;
            ribbon(pose, vc, 0, 0, 0, mx, my, mz, 0.34F * c, 0.2F * c, ex, ey, ez, CarrierView.argb(CarrierView.hot(cls, 0.3F), 0.75F),
                    CarrierView.argb(cls, 0.45F));
            ribbon(pose, vc, mx, my, mz, tx, ty, tz, 0.2F * c, 0.02F, ex, ey, ez, CarrierView.argb(theme, 0.4F), CarrierView.argb(theme, 0F));
        }
        if (speed > 1.0E-3F && form != BoltKind.Form.ROUND) {
            float fx = vx / speed, fy = vy / speed, fz = vz / speed;
            int hot = CarrierView.argb(CarrierView.hot(cls, 0.8F), 1F), body = CarrierView.argb(cls, 0.85F), none = CarrierView.argb(cls, 0F);
            flare(pose, vc, rx, ry, rz, ux, uy, uz, 0.6F * breathe, CarrierView.argb(cls, 0.2F));
            if (form == BoltKind.Form.DART) {
                // a needle along its flight: a point, a shoulder a little behind it, and the shaft thinning back
                ribbon(pose, vc, fx * 0.62F, fy * 0.62F, fz * 0.62F, fx * 0.12F, fy * 0.12F, fz * 0.12F, 0.02F, 0.26F, ex, ey, ez, hot, hot);
                ribbon(pose, vc, fx * 0.12F, fy * 0.12F, fz * 0.12F, -fx * 0.42F, -fy * 0.42F, -fz * 0.42F, 0.26F, 0.05F, ex, ey, ez, hot, body);
            } else {
                // a bead, its point trailing
                ribbon(pose, vc, 0, 0, 0, -fx * 0.62F, -fy * 0.62F, -fz * 0.62F, 0.5F, 0.02F, ex, ey, ez, body, none);
                flare(pose, vc, rx, ry, rz, ux, uy, uz, 0.32F, body);
                flare(pose, vc, rx, ry, rz, ux, uy, uz, 0.16F, hot);
            }
            return;
        }
        flare(pose, vc, rx, ry, rz, ux, uy, uz, 1.15F * breathe * c, CarrierView.argb(cls, 0.22F));
        flare(pose, vc, rx, ry, rz, ux, uy, uz, 0.5F * c, CarrierView.argb(cls, 0.75F));
        flare(pose, vc, rx, ry, rz, ux, uy, uz, 0.22F * c, CarrierView.argb(CarrierView.hot(cls, 0.8F), 1F));
    }

    /** A tapering ribbon from a to b (shot-relative), facing the eye: width wa at a, wb at b. */
    private static void ribbon(PoseStack.Pose pose, VertexConsumer vc, float ax, float ay, float az, float bx, float by, float bz,
                               float wa, float wb, float ex, float ey, float ez, int ca, int cb) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        // the side: the line's direction crossed with the eye's line to it
        float vx = ex + (ax + bx) * 0.5F, vy = ey + (ay + by) * 0.5F, vz = ez + (az + bz) * 0.5F;
        float nx = dy * vz - dz * vy, ny = dz * vx - dx * vz, nz = dx * vy - dy * vx;
        float n = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (n < 1.0E-6F) return;
        nx /= n;
        ny /= n;
        nz /= n;
        float ha = wa * 0.5F, hb = wb * 0.5F;
        int ra = ca & 0x00FFFFFF, rb = cb & 0x00FFFFFF;
        // two halves, bright on the line and nothing at the edge
        vc.addVertex(pose, ax, ay, az).setColor(ca);
        vc.addVertex(pose, bx, by, bz).setColor(cb);
        vc.addVertex(pose, bx + nx * hb, by + ny * hb, bz + nz * hb).setColor(rb);
        vc.addVertex(pose, ax + nx * ha, ay + ny * ha, az + nz * ha).setColor(ra);
        vc.addVertex(pose, ax, ay, az).setColor(ca);
        vc.addVertex(pose, bx, by, bz).setColor(cb);
        vc.addVertex(pose, bx - nx * hb, by - ny * hb, bz - nz * hb).setColor(rb);
        vc.addVertex(pose, ax - nx * ha, ay - ny * ha, az - nz * ha).setColor(ra);
    }

    private static void flare(PoseStack.Pose pose, VertexConsumer vc, float rx, float ry, float rz, float ux, float uy, float uz, float r, int argb) {
        int rim = argb & 0x00FFFFFF;
        int n = 10;
        for (int i = 0; i < n; i++) {
            float a0 = i * Mth.TWO_PI / n, a1 = (i + 1) * Mth.TWO_PI / n;
            float c0 = Mth.cos(a0) * r, s0 = Mth.sin(a0) * r, c1 = Mth.cos(a1) * r, s1 = Mth.sin(a1) * r;
            vc.addVertex(pose, 0, 0, 0).setColor(argb);
            vc.addVertex(pose, 0, 0, 0).setColor(argb);
            vc.addVertex(pose, rx * c1 + ux * s1, ry * c1 + uy * s1, rz * c1 + uz * s1).setColor(rim);
            vc.addVertex(pose, rx * c0 + ux * s0, ry * c0 + uy * s0, rz * c0 + uz * s0).setColor(rim);
        }
    }

    // ---- its hum -------------------------------------------------------------------------------------------------------

    /** Shots humming at once at most (a ring of sixteen is heard as a few). */
    static final int HUMS = 4;
    private static int humming;

    /** A shot joined the client's world: it hums from where it is while it flies. */
    static void onJoin(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide() || !(e.getEntity() instanceof AwBolt b) || humming >= HUMS) return;
        Minecraft.getInstance().getSoundManager().play(new Hum(b));
    }

    static final class Hum extends AbstractTickableSoundInstance {
        private final Entity shot;
        private float level;

        Hum(Entity shot) {
            super(MobSounds.SHOT_HUM.get(), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
            this.shot = shot;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.pitch = 0.9F + shot.getId() % 7 * 0.035F;
            this.x = shot.getX();
            this.y = shot.getY();
            this.z = shot.getZ();
            humming++;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            boolean gone = shot.isRemoved();
            level += ((gone ? 0F : 0.9F) - level) * 0.5F;
            volume = Math.max(0.001F, level);
            x = shot.getX();
            y = shot.getY();
            z = shot.getZ();
            if (gone && level < 0.02F) {
                stop();
                humming = Math.max(0, humming - 1);
            }
        }
    }

    /** Leaving a world: the count starts over (the sound engine dropped the hums). */
    static void reset() {
        humming = 0;
    }
}
