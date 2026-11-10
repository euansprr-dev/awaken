package dev.awaken.anims.client.station;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.anims.station.StationPresence;
import dev.awaken.anims.station.StationPresence.At;
import dev.awaken.anims.station.StationView;
import dev.awaken.core.api.PlayerClips;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * A body at a station, as everyone sees it in third person (SPEC §63.5), read from each player's synced
 * {@link StationPresence.At}: bent over a table, on one knee before a furnace, bent to a box, and a reach to each slot
 * they touch (clips from {@code tools/art/specs/station_moves.py}, through {@link PlayerClips}; without the animation
 * library, vanilla's arm swing for each reach). Standing more than {@link #TOUCH} from the station the body only
 * reaches toward it. Also what lies there for others: the stacks on a table someone else is working, and the largest
 * stacks in any chest whose lid is up. Everything stops the tick the presence goes.
 */
final class StationBodies {
    private StationBodies() {}

    static final String BENCH = "station_bench", BENCH_R = "station_bench_reach_r", BENCH_L = "station_bench_reach_l", KNEEL = "station_kneel",
            KNEEL_REACH = "station_kneel_reach", BOX = "station_box", BOX_REACH = "station_box_reach", FAR = "station_far", FAR_REACH = "station_far_reach";
    /** Further from the station than this, the body reaches toward it and does not pretend to touch it (SPEC §24). */
    static final double TOUCH = 2.5, SEEN = 16;

    private static final class Body {
        int entity, beats;
        @Nullable String clip;
        long reachEnd;
        At at = At.NOWHERE;
        final ItemStackRenderState[] items = new ItemStackRenderState[10];
    }

    private static final Map<UUID, Body> BODIES = new HashMap<>();
    private static final double[] P = new double[3];

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) {
            BODIES.clear();
            return;
        }
        long now = level.getGameTime();
        for (Player p : level.players()) {
            At at = p.getExistingData(StationPresence.AT).orElse(At.NOWHERE);
            Body b = BODIES.get(p.getUUID());
            if (!at.here()) continue;
            if (b == null || b.entity != p.getId()) {
                if (b != null && b.clip != null) PlayerClips.stop(b.entity);
                b = new Body();
                b.entity = p.getId();
                b.beats = at.beats();
                BODIES.put(p.getUUID(), b);
            }
            b.at = at;
            BlockPos pos = at.pos();
            boolean far = p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > TOUCH * TOUCH;
            if (at.beats() != b.beats) {
                b.beats = at.beats();
                String reach = far ? FAR_REACH : switch (at.kind()) {
                    case StationPresence.KNEEL -> KNEEL_REACH;
                    case StationPresence.BOX -> BOX_REACH;
                    // a table's left column the left arm, the rest the right (mirrored for a left-handed body)
                    default -> (at.beatSlot() >= 1 && (at.beatSlot() - 1) % 3 == 0) == (p.getMainArm() != HumanoidArm.LEFT) ? BENCH_L : BENCH_R;
                };
                if (PlayerClips.play(b.entity, reach, 1)) {
                    b.clip = reach;
                    b.reachEnd = now + (reach.equals(KNEEL_REACH) ? 8 : reach.equals(FAR_REACH) ? 6 : 7);
                } else {
                    p.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
                }
                continue;
            }
            if (now < b.reachEnd) continue;
            String want = far ? FAR : at.kind() == StationPresence.KNEEL ? KNEEL : at.kind() == StationPresence.BOX ? BOX : BENCH;
            if (!want.equals(b.clip) && PlayerClips.play(b.entity, want, b.clip == null ? 4 : 2)) b.clip = want;
        }
        // whoever has left their station (or the level) stands up again
        for (Iterator<Map.Entry<UUID, Body>> it = BODIES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Body> e = it.next();
            Player p = level.getPlayerByUUID(e.getKey());
            if (p != null && p.getId() == e.getValue().entity && p.getExistingData(StationPresence.AT).orElse(At.NOWHERE).here()) continue;
            if (e.getValue().clip != null) PlayerClips.stop(e.getValue().entity);
            it.remove();
        }
    }

    static void clear() {
        BODIES.clear();
    }

    /** What lies at the stations others are working, and in any open chest (yours too): drawn in the world. */
    static void submit(SubmitCustomGeometryEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (BODIES.isEmpty() || mc.level == null || mc.player == null) return;
        Vec3 cam = e.getLevelRenderState().cameraRenderState.pos;
        PoseStack ps = e.getPoseStack();
        for (Map.Entry<UUID, Body> en : BODIES.entrySet()) {
            Body b = en.getValue();
            At at = b.at;
            if (at.shown().isEmpty()) continue;
            BlockPos pos = at.pos();
            if (pos.distToCenterSqr(cam.x, cam.y, cam.z) > SEEN * SEEN) continue;
            boolean mine = en.getKey().equals(mc.player.getUUID());
            if (at.kind() == StationPresence.BENCH) {
                // your own table is drawn by your own scene (with your hand and its settles); a plain screen shows it too
                if (mine && StationClient.table != null && StationClient.table.equals(pos) && mc.gui.screen() instanceof StationScreen<?>) continue;
                StationView.Kind kind = StationView.kindOf(mc.level.getBlockState(pos));
                if (kind == null || !kind.bench()) continue;
                StationView.Station s = new StationView.Station(pos, kind, at.fx(), at.fz());
                int light = LightCoordsUtil.pack(mc.level.getBrightness(LightLayer.BLOCK, pos.above()), mc.level.getBrightness(LightLayer.SKY, pos.above()));
                float yaw = (float) (Mth.atan2(-at.fx(), at.fz()) * Mth.RAD_TO_DEG);
                for (int i = 0; i < at.shown().size() && i < kind.slots; i++) {
                    ItemStack st = at.shown().get(i);
                    if (st.isEmpty()) continue;
                    StationView.spot(s, i, P, 0);
                    ItemStackRenderState r = state(b, i);
                    mc.getItemModelResolver().updateForTopItem(r, st, ItemDisplayContext.FIXED, mc.level, null, i);
                    int n = StationClient.pile(st.getCount());
                    for (int k = 0; k < n; k++) {
                        ps.pushPose();
                        ps.translate(P[0] + (s.rx() * 0.012 - s.fx() * 0.012) * k - cam.x, P[1] + 0.006 * k - cam.y, P[2] + (s.rz() * 0.012 - s.fz() * 0.012) * k - cam.z);
                        ps.rotateDegrees(Axis.YP, 180F - yaw);
                        if (!kind.upright) ps.rotateDegrees(Axis.XP, 90F);
                        float size = kind == StationView.Kind.TABLE && i == 0 ? 0.17F : kind.size;
                        ps.scale(size, size, size);
                        r.submit(ps, e.getSubmitNodeCollector(), light, OverlayTexture.NO_OVERLAY, 0);
                        ps.popPose();
                    }
                }
            } else if (at.kind() == StationPresence.BOX) {
                // a chest with its lid up: its largest stacks lie in it, two rows of three (a barrel's or a shulker's
                // inside cannot be seen)
                var block = mc.level.getBlockState(pos).getBlock();
                if (!(block instanceof ChestBlock) && !(block instanceof EnderChestBlock)) continue;
                int light = LightCoordsUtil.pack(Math.max(4, mc.level.getBrightness(LightLayer.BLOCK, pos.above())), mc.level.getBrightness(LightLayer.SKY, pos.above()));
                float yaw = (float) (Mth.atan2(-at.fx(), at.fz()) * Mth.RAD_TO_DEG);
                double rx = -at.fz(), rz = at.fx();
                for (int i = 0; i < at.shown().size() && i < StationPresence.BOX_SHOWN; i++) {
                    ItemStack st = at.shown().get(i);
                    if (st.isEmpty()) continue;
                    double across = (i % 3 - 1) * 0.22, in = (i / 3 == 0 ? -0.12 : 0.13);
                    ItemStackRenderState r = state(b, i);
                    mc.getItemModelResolver().updateForTopItem(r, st, ItemDisplayContext.FIXED, mc.level, null, i);
                    int n = StationClient.pile(st.getCount());
                    for (int k = 0; k < n; k++) {
                        ps.pushPose();
                        ps.translate(pos.getX() + 0.5 + rx * across + at.fx() * in + rx * 0.014 * k - cam.x, pos.getY() + 0.2 + 0.03 * k - cam.y,
                                pos.getZ() + 0.5 + rz * across + at.fz() * in + rz * 0.014 * k - cam.z);
                        ps.rotateDegrees(Axis.YP, 180F - yaw + (i * 37 % 50) - 25);
                        ps.rotateDegrees(Axis.XP, 90F);
                        ps.scale(0.2F, 0.2F, 0.2F);
                        r.submit(ps, e.getSubmitNodeCollector(), light, OverlayTexture.NO_OVERLAY, 0);
                        ps.popPose();
                    }
                }
            }
        }
    }

    private static ItemStackRenderState state(Body b, int i) {
        if (b.items[i] == null) b.items[i] = new ItemStackRenderState();
        return b.items[i];
    }
}
