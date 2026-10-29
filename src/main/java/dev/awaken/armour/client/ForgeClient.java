package dev.awaken.armour.client;

import dev.awaken.armour.Archetype;
import dev.awaken.armour.ArmourBonus;
import dev.awaken.armour.ArmourContent;
import dev.awaken.armour.ArmourLooks;
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
        bus.addListener(net.neoforged.neoforge.client.event.RegisterClientCommandsEvent.class, e -> e.getDispatcher().register(
                net.minecraft.commands.Commands.literal("awaken").then(net.minecraft.commands.Commands.literal("armour")
                        .then(net.minecraft.commands.Commands.literal("autoplay").then(net.minecraft.commands.Commands.argument("on",
                                com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(c -> {
                                    autoplay = com.mojang.brigadier.arguments.BoolArgumentType.getBool(c, "on");
                                    return 1;
                                }))))));
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            ForgeSongScreen.last = null;
            ForgeCamera.end();
        });
    }

    /** The song plays itself on the beat (tours and demos: {@code /awaken armour autoplay true}); the server judges it as any. */
    static boolean autoplay;

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

    /**
     * A forged piece names its quality (in its colour), a twin-struck piece says so, who made it, plainly, and its mastery
     * once it has grown; a Hunter piece adds its archetype's ability (its own bonuses are the gear module's lines).
     */
    private static void onTooltip(ItemTooltipEvent e) {
        var lines = e.getToolTip();
        ArmourLooks.Look look = ArmourLooks.of(e.getItemStack());
        if (look != null && look.set().hunter != null && look.set().archetype != Archetype.LORD) {
            lines.add(Component.literal(dev.awaken.gear.SetBonus.FULL + " worn: " + ArmourBonus.ability(look.set().archetype)).withColor(0x8A93A6));
        }
        Forged f = e.getItemStack().get(ArmourContent.FORGED.get());
        if (f == null) return;
        int at = Math.min(1, lines.size());
        ForgeSong.Quality q = ForgeSong.Quality.of(f.quality());
        String text = q.label + (f.twin() ? ", twin-struck" : "") + (f.flaw() ? ", with a scale flaw" : "");
        lines.add(at++, Component.literal(text).withColor(q.colour & 0xFFFFFF));
        if (!f.maker().isEmpty()) lines.add(at++, Component.literal("Forged by " + f.maker()).withColor(0x6F8299));
        if (f.trueRing()) lines.add(at++, Component.literal("Its Deepglass rings true").withColor(0x9FD8E8));
        var force = f.inlayForce();
        if (force != null) lines.add(at++, Component.literal(dev.awaken.armour.ArmourInlay.line(force)).withColor(dev.awaken.armour.ArmourInlay.colour(force) & 0xFFFFFF));
        ArmourLooks.Look drawn = ArmourLooks.drawn(e.getItemStack());
        if (drawn != null && look != null && drawn.set() != look.set())
            lines.add(at++, Component.literal("Wears the look of " + drawn.set().setName).withColor(0xB8A6FF));
        int step = f.masteryStep();
        if (step > 0 || f.mastery() > 0) {
            String next = step < Forged.MASTERY_BUILT ? " (" + f.mastery() + " / " + Forged.MASTERY[step + 1] + ")" : "";
            lines.add(at, Component.literal("Mastery " + Forged.numeral(step) + next).withColor(look == null ? 0xC9D2E3 : look.set().rank.colour));
        }
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
