package dev.awaken.anims.client.station;

import com.mojang.math.Axis;
import dev.awaken.anims.station.FurnaceContents;
import dev.awaken.anims.station.StationView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.HashMap;
import java.util.Map;

/** Depth-tested ordinary item models in the recessed mouth, whether the menu is open or closed. */
final class FurnaceWorld {
    private FurnaceWorld() {}
    private static final Map<BlockPos, View> VIEWS = new HashMap<>();
    private static View[] visible = new View[0];
    private static ClientLevel level;
    private static final double[] POINT = new double[3];
    private static final View MENU = new View(new FurnaceContents.Snapshot(BlockPos.ZERO, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));

    private static final class View {
        FurnaceContents.Snapshot snapshot;
        BlockState state;
        StationView.Station station;
        BlockPos lightAt;
        float yaw;
        final ItemStackRenderState[] items = {new ItemStackRenderState(), new ItemStackRenderState(), new ItemStackRenderState()};
        View(FurnaceContents.Snapshot snapshot) { this.snapshot = snapshot; }
    }

    static void init(IEventBus modBus) {
        FurnaceModels.init(modBus);
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, e -> e.register(FurnaceContents.Update.TYPE, (m, ctx) -> accept(m)));
        NeoForge.EVENT_BUS.addListener(SubmitCustomGeometryEvent.class, FurnaceWorld::render);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> clear());
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> {
            if (level != Minecraft.getInstance().level) clear();
        });
    }

    private static void clear() { VIEWS.clear(); visible = new View[0]; level = Minecraft.getInstance().level; }

    private static void accept(FurnaceContents.Update update) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().identifier().equals(update.dimension())) return;
        if (update.reset() || level != mc.level) clear();
        level = mc.level;
        for (BlockPos pos : update.removed()) VIEWS.remove(pos);
        for (var snapshot : update.changed()) {
            View view = VIEWS.get(snapshot.pos());
            if (view == null) VIEWS.put(snapshot.pos(), new View(snapshot));
            else view.snapshot = snapshot;
        }
        visible = VIEWS.values().toArray(View[]::new);
    }

    static boolean supported(BlockPos pos, BlockState state) {
        Minecraft mc = Minecraft.getInstance();
        if (!FurnaceModels.active(state) || mc.level == null) return false;
        var blockEntity = mc.level.getBlockEntity(pos);
        // A third-party block-entity renderer keeps sole ownership of its visible contents.
        return blockEntity != null && mc.getBlockEntityRenderDispatcher().getRenderer(blockEntity) == null;
    }

    private static void render(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        BlockPos menuPos = StationClient.kind == StationView.Kind.FURNACE && mc.gui.screen() instanceof StationScreen<?> ? StationClient.table : null;
        boolean renderedMenu = false;
        for (View view : visible) {
            boolean menu = view.snapshot.pos().equals(menuPos);
            draw(event, view, menu);
            renderedMenu |= menu;
        }
        if (menuPos != null && !renderedMenu) {
            if (!MENU.snapshot.pos().equals(menuPos)) MENU.snapshot = new FurnaceContents.Snapshot(menuPos, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
            draw(event, MENU, true);
        }
    }

    private static void draw(SubmitCustomGeometryEvent event, View view, boolean menu) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos pos = view.snapshot.pos();
        var camera = event.getLevelRenderState().cameraRenderState.pos;
        if (pos.distToCenterSqr(camera.x, camera.y, camera.z) > FurnaceContents.RANGE * FurnaceContents.RANGE) return;
        BlockState state = mc.level.getBlockState(pos);
        if (!supported(pos, state)) return;
        var facing = state.getValue(AbstractFurnaceBlock.FACING);
        double fx = -facing.getStepX(), fz = -facing.getStepZ();
        // The five opaque outer faces already hide these items; skip their submissions from behind too.
        if ((camera.x - pos.getX() - .5) * -fx + (camera.z - pos.getZ() - .5) * -fz < .5) return;
        if (view.state != state || view.station == null || !view.station.pos().equals(pos)) {
            view.state = state;
            view.station = new StationView.Station(pos, StationView.Kind.FURNACE, fx, fz);
            view.lightAt = pos.relative(facing);
            view.yaw = (float) (Mth.atan2(-fx, fz) * Mth.RAD_TO_DEG);
        }
        StationView.Station station = view.station;
        BlockPos lightAt = view.lightAt;
        int light = LightCoordsUtil.pack(Math.max(state.getValue(AbstractFurnaceBlock.LIT) ? 10 : 0, mc.level.getBrightness(LightLayer.BLOCK, lightAt)),
                mc.level.getBrightness(LightLayer.SKY, lightAt));
        float yaw = view.yaw;
        StationScreen<?> screen = menu && mc.gui.screen() instanceof StationScreen<?> s ? s : null;
        var pose = event.getPoseStack();
        for (int slot = 0; slot < 3; slot++) {
            ItemStack item = screen == null ? view.snapshot.item(slot) : screen.getMenu().getSlot(slot).getItem();
            if (item.isEmpty() && screen != null && screen.dragging(slot)) item = screen.getMenu().getCarried();
            if (item.isEmpty()) continue;
            StationView.spot(station, slot, POINT, 0);
            ItemStackRenderState model = view.items[slot];
            mc.getItemModelResolver().updateForTopItem(model, item, ItemDisplayContext.FIXED, mc.level, null, slot);
            pose.pushPose();
            pose.translate(POINT[0] - camera.x, POINT[1] - camera.y, POINT[2] - camera.z);
            pose.rotateDegrees(Axis.YP, 180 - yaw);
            pose.scale(.24F, .24F, .24F);
            model.submit(pose, event.getSubmitNodeCollector(), light, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }
}
