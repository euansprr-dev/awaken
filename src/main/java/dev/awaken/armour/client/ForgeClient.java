package dev.awaken.armour.client;

import dev.awaken.armour.ArmourContent;
import dev.awaken.armour.FittingStandBlockEntity;
import dev.awaken.armour.ForgeSong;
import dev.awaken.armour.Forged;
import dev.awaken.armour.net.ArmourPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.jspecify.annotations.Nullable;

/**
 * The Forge Song's client (SPEC §44.4): the stand drawn, its list and the song's screens, the camera, the HUD stepping
 * aside while the camera holds the view, and a forged piece's quality on its tooltip.
 */
final class ForgeClient {
    private ForgeClient() {}

    static void init(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                e -> e.registerBlockEntityRenderer(ArmourContent.STAND_BE.get(), FittingStandRenderer::new));
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, e -> {
            e.register(ArmourPayloads.Offers.TYPE, (msg, ctx) -> Minecraft.getInstance().execute(() -> onOffers(msg)));
            e.register(ArmourPayloads.Done.TYPE, (msg, ctx) -> Minecraft.getInstance().execute(() -> onDone(msg)));
        });
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(EventPriority.LOWEST, ViewportEvent.ComputeCameraAngles.class, e -> ForgeCamera.angles(e, now(e.getPartialTick())));
        bus.addListener(EventPriority.LOWEST, ViewportEvent.ComputeFov.class, ForgeCamera::fov);
        bus.addListener(EventPriority.HIGH, RenderHandEvent.class, ForgeCamera::hand);
        bus.addListener(ExtractBlockOutlineRenderStateEvent.class, ForgeCamera::outline);
        bus.addListener(RenderPlayerEvent.Pre.class, ForgeCamera::player);
        bus.addListener(RenderGuiLayerEvent.Pre.class, ForgeClient::hideHud);
        bus.addListener(ClientTickEvent.Post.class, e -> tick());
        bus.addListener(ItemTooltipEvent.class, ForgeClient::onTooltip);
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            ForgeSongScreen.last = null;
            ForgeCamera.end();
        });
    }

    // ---- messages ------------------------------------------------------------------------------------------------------

    /**
     * The stand's list came: your own work waiting on it opens the song where you left it (counted in again); someone
     * else's at the anvil lets you help (the off-beats); else the list.
     */
    private static void onOffers(ArmourPayloads.Offers o) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        FittingStandBlockEntity be = stand(o.pos());
        var w = be == null ? null : be.work();
        if (w != null && w.forger.equals(mc.player.getUUID())) {
            send(o.pos(), ArmourPayloads.RESUME, 0, 0F, 0F);
            mc.gui.setScreen(new ForgeSongScreen(o.pos(), false));
        } else if (w != null && w.stage == ForgeSong.SHAPE) {
            mc.gui.setScreen(new ForgeSongScreen(o.pos(), true));
        } else mc.gui.setScreen(new ForgeScreen(o, w == null ? null : name(w.forger)));
    }

    private static void onDone(ArmourPayloads.Done d) {
        if (Minecraft.getInstance().gui.screen() instanceof ForgeSongScreen s && s.pos.equals(d.pos())) s.done(d);
    }

    // ---- keeping the camera honest -------------------------------------------------------------------------------------

    /** The camera holds only while one of the forge's screens is open (anything else that opens, or a death, lets it go). */
    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (ForgeSongScreen.last != null && !(mc.gui.screen() instanceof ForgeSongScreen)) {
            if (mc.getConnection() != null) send(ForgeSongScreen.last, ArmourPayloads.LEAVE, 0, 0F, 0F);
            ForgeSongScreen.last = null;
        }
        if (!ForgeCamera.active()) return;
        boolean ours = mc.gui.screen() instanceof ForgeScreen || mc.gui.screen() instanceof ForgeSongScreen;
        if (!ours || mc.player == null || !mc.player.isAlive()) ForgeCamera.end();
    }

    /** The HUD steps aside while the forge's camera holds the view (subtitles and chat stay). */
    private static void hideHud(RenderGuiLayerEvent.Pre e) {
        if (!ForgeCamera.active()) return;
        String path = e.getName().getPath();
        if (path.equals("subtitle_overlay") || path.equals("chat")) return;
        e.setCanceled(true);
    }

    // ---- the tooltip ---------------------------------------------------------------------------------------------------

    /** A forged piece names its quality (in its colour), a twin-struck piece says so, and who made it, plainly. */
    private static void onTooltip(ItemTooltipEvent e) {
        Forged f = e.getItemStack().get(ArmourContent.FORGED.get());
        if (f == null) return;
        var lines = e.getToolTip();
        int at = Math.min(1, lines.size());
        ForgeSong.Quality q = ForgeSong.Quality.of(f.quality());
        String text = q.label + (f.twin() ? ", twin-struck" : "") + (f.flaw() ? ", with a scale flaw" : "");
        lines.add(at++, Component.literal(text).withColor(q.colour & 0xFFFFFF));
        if (!f.maker().isEmpty()) lines.add(at, Component.literal("Forged by " + f.maker()).withColor(0x6F8299));
    }

    // ---- shared ------------------------------------------------------------------------------------------------------

    static void send(BlockPos pos, int act, int a, float t, float aim) {
        ClientPacketDistributor.sendToServer(new ArmourPayloads.Act(pos, act, a, t, aim));
    }

    static @Nullable FittingStandBlockEntity stand(BlockPos pos) {
        var level = Minecraft.getInstance().level;
        return level != null && level.getBlockEntity(pos) instanceof FittingStandBlockEntity be ? be : null;
    }

    /** The client's game time with the frame's part of a tick. */
    static float now(float partial) {
        var level = Minecraft.getInstance().level;
        return level == null ? 0F : level.getGameTime() + partial;
    }

    static String name(java.util.UUID id) {
        var c = Minecraft.getInstance().getConnection();
        var info = c == null ? null : c.getPlayerInfo(id);
        return info == null ? "Someone" : info.getProfile().name();
    }

    /** A sound the smith's own client plays the moment they act (the server plays it to everyone else). */
    static void play(Vec3 at, SoundEvent sound, float volume, float pitch) {
        var level = Minecraft.getInstance().level;
        if (level != null) level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.BLOCKS, volume, pitch, false);
    }
}
