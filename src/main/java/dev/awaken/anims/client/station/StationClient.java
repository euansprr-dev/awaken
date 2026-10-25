package dev.awaken.anims.client.station;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.anims.Ease;
import dev.awaken.core.api.FirstPerson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.Nullable;

/**
 * Station scenes (Hands P5; SPEC §24): at a crafting table the view leans in over the table top, the 3×3 grid IS the
 * table top (each ingredient lies on its square in the world, the result floats over the middle), and your hands lay
 * every ingredient on its square and take the result. The screen ({@link TableScreen}) is the vanilla crafting menu,
 * so every item moves by the menu's own clicks and the server checks them; only the grid is drawn in the world instead
 * of on the screen. Sneak + use opens the plain screen; moving, jumping, E or Esc leaves. Drawn for you (others see you
 * at the table as before).
 * <p>
 * Lean-in 12 ticks (6 if you come back within 12 s), out 10; the view turns to the table and narrows a little; nothing
 * moves the player's own look.
 */
public final class StationClient {
    private StationClient() {}

    /** The table being worked (null: none), the way into it from the player (horizontal, snapped to an axis). */
    static @Nullable BlockPos table;
    static double fx, fz, rx, rz;
    private static int openedAt = -1000, closedAt = -1000, ticks, usedAt = -100;
    private static @Nullable BlockPos used;
    private static boolean open;
    private static float lastFov = 70F;
    private static final ItemStack[] SEEN = new ItemStack[10];
    private static final ItemStackRenderState[] ITEMS = new ItemStackRenderState[10];
    /** A hand's tap toward a spot (world), per hand (0 right, 1 left). */
    private static final double[] TAP = new double[6];
    private static final int[] TAP_AT = {-100, -100};
    static final int TAP_TICKS = 9;

    static {
        java.util.Arrays.fill(SEEN, ItemStack.EMPTY);
        for (int i = 0; i < ITEMS.length; i++) ITEMS[i] = new ItemStackRenderState();
    }

    public static void init(IEventBus bus) {
        bus.addListener(PlayerInteractEvent.RightClickBlock.class, StationClient::onUse);
        bus.addListener(EventPriority.LOW, ScreenEvent.Opening.class, StationClient::onOpening);
        bus.addListener(EventPriority.LOW, ViewportEvent.ComputeCameraAngles.class, StationClient::onAngles);
        bus.addListener(ComputeFovModifierEvent.class, StationClient::onFovModifier);
        bus.addListener(EventPriority.LOWEST, ViewportEvent.ComputeFov.class, e -> lastFov = (float) e.getFOV());
        bus.addListener(SubmitCustomGeometryEvent.class, StationClient::onSubmit);
        FirstPerson.world("anims_station", 200, StationClient::pose);
    }

    public static void tick() {
        ticks++;
        if (!open || !(Minecraft.getInstance().gui.screen() instanceof TableScreen s)) return;
        // what changed on the grid: a hand lays it there (or takes the result)
        AbstractContainerMenu menu = s.getMenu();
        for (int i = 0; i < 10; i++) {
            ItemStack now = menu.getSlot(i).getItem();
            if (ItemStack.matches(now, SEEN[i])) continue;
            boolean taken = i == 0 && now.isEmpty() && !SEEN[i].isEmpty();
            SEEN[i] = now.copy();
            if (i == 0 && !taken) continue; // a result appearing is the recipe, not a hand
            int col = i == 0 ? 1 : (i - 1) % 3;
            int hand = col == 0 ? 1 : col == 2 ? 0 : mainSide();
            spot(i, TAP, hand * 3);
            TAP_AT[hand] = ticks;
        }
    }

    /** Dev (the pilot's "station <slot> <button>"): a click on a slot of the open table scene, as a player's. */
    public static void devClick(int slot, int button) {
        if (Minecraft.getInstance().gui.screen() instanceof TableScreen s) s.devClick(slot, button);
    }

    private static int mainSide() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && p.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT ? 1 : 0;
    }

    private static boolean on() {
        return FirstPerson.stationsOn.getAsBoolean() && Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    // ---- opening and closing ----------------------------------------------------------------------------------------

    private static void onUse(PlayerInteractEvent.RightClickBlock e) {
        if (!e.getLevel().isClientSide() || !(e.getLevel().getBlockState(e.getPos()).getBlock() instanceof CraftingTableBlock)) return;
        used = e.getPos().immutable();
        usedAt = ticks;
    }

    /** A crafting screen opening just after a table was used (not sneaking): the scene takes its place. */
    private static void onOpening(ScreenEvent.Opening e) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !(e.getNewScreen() instanceof CraftingScreen cs) || used == null || ticks - usedAt > 10 || !on()) return;
        if (p.isShiftKeyDown()) return; // the escape hatch: the plain screen
        begin(p, used);
        e.setNewScreen(new TableScreen(cs.getMenu(), p.getInventory(), cs.getTitle()));
    }

    private static void begin(LocalPlayer p, BlockPos pos) {
        table = pos;
        double dx = pos.getX() + 0.5 - p.getX(), dz = pos.getZ() + 0.5 - p.getZ();
        // the grid lines up with the block: the way in snapped to the nearest axis
        if (Math.abs(dx) >= Math.abs(dz)) {
            fx = Math.signum(dx);
            fz = 0;
        } else {
            fx = 0;
            fz = Math.signum(dz);
        }
        rx = -fz;
        rz = fx;
        boolean quick = ticks - closedAt < 240;
        openedAt = ticks - (quick ? 6 : 0);
        open = true;
        java.util.Arrays.fill(SEEN, ItemStack.EMPTY);
        TAP_AT[0] = TAP_AT[1] = -100;
    }

    static void closed() {
        if (!open) return;
        open = false;
        closedAt = ticks;
    }

    /** How far in the scene is (0 out, 1 in): eased in over 12 ticks, out over 10. */
    static float weight(float pt) {
        float t = ticks + pt;
        float in = Ease.at(Ease.OUT_CUBIC, (t - openedAt) / 12F);
        if (open) return in;
        return Math.min(in, 1 - Ease.at(Ease.IN_OUT_SINE, (t - closedAt) / 10F));
    }

    // ---- the grid on the table --------------------------------------------------------------------------------------

    /** The top's centre. */
    static Vec3 top() {
        BlockPos b = table;
        return b == null ? Vec3.ZERO : new Vec3(b.getX() + 0.5, b.getY() + 1.0, b.getZ() + 0.5);
    }

    /** Where a slot lies on the table (0 the result, floating over the middle; 1..9 the grid, row 0 the far one). */
    static void spot(int slot, double[] out, int at) {
        BlockPos b = table;
        if (b == null) return;
        double cx = b.getX() + 0.5, cy = b.getY() + 1.0, cz = b.getZ() + 0.5;
        if (slot == 0) {
            out[at] = cx;
            out[at + 1] = cy + 0.42;
            out[at + 2] = cz;
            return;
        }
        int row = (slot - 1) / 3, col = (slot - 1) % 3;
        double u = (col - 1) * 0.3, v = (1 - row) * 0.3;
        out[at] = cx + rx * u + fx * v;
        out[at + 1] = cy + 0.01;
        out[at + 2] = cz + rz * u + fz * v;
    }

    /**
     * The slot under the screen's cursor (gui coordinates), from a ray through the view: a grid cell on the top, or the
     * floating result; -1 for none.
     */
    static int pick(double mx, double my, int guiW, int guiH) {
        Minecraft mc = Minecraft.getInstance();
        if (table == null) return -1;
        var cam = mc.gameRenderer.mainCamera();
        Vec3 o = cam.position();
        float tan = (float) Math.tan(Math.toRadians(lastFov) / 2);
        float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        float nx = (float) (mx / guiW * 2 - 1), ny = (float) (1 - my / guiH * 2);
        org.joml.Vector3f d = new org.joml.Vector3f(nx * tan * aspect, ny * tan, -1).normalize().rotate(cam.rotation());
        // the result: near the ray, over the middle
        double[] r = new double[3];
        spot(0, r, 0);
        double wx = r[0] - o.x, wy = r[1] - o.y, wz = r[2] - o.z, along = wx * d.x + wy * d.y + wz * d.z;
        double px = wx - d.x * along, py = wy - d.y * along, pz = wz - d.z * along;
        if (along > 0 && px * px + py * py + pz * pz < 0.15 * 0.15) return 0;
        // the top's plane
        double topY = table.getY() + 1.0;
        if (Math.abs(d.y) < 1e-4) return -1;
        double t = (topY - o.y) / d.y;
        if (t <= 0) return -1;
        double hx = o.x + d.x * t - (table.getX() + 0.5), hz = o.z + d.z * t - (table.getZ() + 0.5);
        double u = hx * rx + hz * rz, v = hx * fx + hz * fz;
        if (Math.abs(u) > 0.45 || Math.abs(v) > 0.45) return -1;
        int col = Mth.clamp((int) Math.floor((u + 0.45) / 0.3), 0, 2), row = Mth.clamp((int) Math.floor((0.45 - v) / 0.3), 0, 2);
        return 1 + row * 3 + col;
    }

    // ---- the view ---------------------------------------------------------------------------------------------------

    private static void onAngles(ViewportEvent.ComputeCameraAngles e) {
        float w = weight((float) e.getPartialTick());
        if (w <= 0 || table == null) return;
        Vec3 o = e.getCamera().position(), t = top();
        // aim at the middle of the top, the view leaning in over it
        double ax = t.x - o.x, ay = t.y - o.y, az = t.z - o.z;
        float yaw = (float) (Mth.atan2(-ax, az) * Mth.RAD_TO_DEG), pitch = (float) (-Mth.atan2(ay, Math.sqrt(ax * ax + az * az)) * Mth.RAD_TO_DEG);
        e.setYaw(e.getYaw() + Mth.wrapDegrees(yaw - e.getYaw()) * w);
        e.setPitch(Mth.lerp(w, e.getPitch(), pitch));
        // the slightest breath while you work
        e.setPitch(e.getPitch() + 0.25F * w * Mth.sin((ticks + (float) e.getPartialTick()) * 0.12F));
    }

    private static void onFovModifier(ComputeFovModifierEvent e) {
        float w = weight(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
        if (w > 0) e.setNewFovModifier(e.getNewFovModifier() * (1 - 0.36F * w));
    }

    // ---- the world: the items on the top, the hovered square --------------------------------------------------------

    private static final double[] P = new double[3];
    private static final VoxelShape CELL = Shapes.box(-0.14, 0, -0.14, 0.14, 0.02, 0.14);

    private static void onSubmit(SubmitCustomGeometryEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (!open || table == null || mc.level == null || !(mc.gui.screen() instanceof TableScreen s)) return;
        Vec3 cam = e.getLevelRenderState().cameraRenderState.pos;
        PoseStack ps = e.getPoseStack();
        BlockPos above = table.above();
        int light = LightCoordsUtil.pack(mc.level.getBrightness(LightLayer.BLOCK, above), mc.level.getBrightness(LightLayer.SKY, above));
        float t = ticks + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float yaw = (float) (Mth.atan2(-fx, fz) * Mth.RAD_TO_DEG);
        for (int i = 0; i < 10; i++) {
            ItemStack st = s.getMenu().getSlot(i).getItem();
            if (st.isEmpty()) continue;
            spot(i, P, 0);
            ItemStackRenderState r = ITEMS[i];
            mc.getItemModelResolver().updateForTopItem(r, st, i == 0 ? ItemDisplayContext.GROUND : ItemDisplayContext.FIXED, mc.level, null, i);
            ps.pushPose();
            ps.translate(P[0] - cam.x, P[1] - cam.y, P[2] - cam.z);
            if (i == 0) {
                // the result floats over the middle, turning slowly
                ps.translate(0, 0.03 * Mth.sin(t * 0.1F), 0);
                ps.rotateDegrees(Axis.YP, t * 2.5F);
                ps.scale(0.55F, 0.55F, 0.55F);
            } else {
                // an ingredient lies flat on its square, square to the table
                ps.rotateDegrees(Axis.YP, 180F - yaw);
                ps.rotateDegrees(Axis.XP, 90F);
                ps.scale(0.26F, 0.26F, 0.26F);
            }
            r.submit(ps, e.getSubmitNodeCollector(), light, OverlayTexture.NO_OVERLAY, 0);
            ps.popPose();
        }
        int hover = s.hovered;
        if (hover > 0) {
            spot(hover, P, 0);
            ps.pushPose();
            ps.translate(P[0] - cam.x, P[1] - cam.y, P[2] - cam.z);
            e.getSubmitNodeCollector().submitShapeOutline(ps, CELL, RenderTypes.lines(), 0xCC7FC3FF, 2F, true);
            ps.popPose();
        }
    }

    // ---- the hands --------------------------------------------------------------------------------------------------

    /** The world layer's source: both hands resting at the table's near edge, one going to each square it works. */
    private static boolean pose(float pt, FirstPerson.Rig rig) {
        Minecraft mc = Minecraft.getInstance();
        float w = weight(pt);
        if (w <= 0.01F || table == null || mc.player == null) return false;
        Vec3 eye = mc.gameRenderer.mainCamera().position(), c = top();
        rig.fx = fx;
        rig.fz = fz;
        float t = ticks + pt;
        for (int h = 0; h < 2; h++) {
            double side = h == 0 ? 1 : -1;
            // resting: just off the near corners, below the edge (in frame at its foot, clear of the grid)
            double restX = c.x - fx * 0.62 + rx * side * 0.42, restY = c.y - 0.08, restZ = c.z - fz * 0.62 + rz * side * 0.42;
            double x = restX, y = restY, z = restZ;
            float u = (t - TAP_AT[h]) / TAP_TICKS;
            if (u >= 0 && u < 1) {
                // out to the square and back (the hand lays the thing down at the middle of it), lifted on the way
                float k = Mth.sin(u * Mth.PI);
                x = Mth.lerp(k, restX, TAP[h * 3]);
                y = Mth.lerp(k, restY, TAP[h * 3 + 1] + 0.04) + 0.08 * Mth.sin(u * Mth.TWO_PI) * Mth.sin(u * Mth.TWO_PI);
                z = Mth.lerp(k, restZ, TAP[h * 3 + 2]);
            }
            // in and out of the scene: up from below the table's edge
            y -= (1 - w) * 0.6;
            double sx = eye.x + rx * side * 0.33 - fx * 0.1, sy = eye.y - 0.4, sz = eye.z + rz * side * 0.33 - fz * 0.1;
            rig.reach(h == 0 ? FirstPerson.Limb.RIGHT_ARM : FirstPerson.Limb.LEFT_ARM, sx, sy, sz, x, y, z, FirstPerson.Rig.ARM_REACH, 0, 1, 0, w);
        }
        return true;
    }
}
