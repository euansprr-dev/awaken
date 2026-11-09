package dev.awaken.hall.client;

import dev.awaken.hall.HallNet;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import org.jspecify.annotations.Nullable;

/** The Hall of Arms' client side (SPEC §52). */
public final class HallClient {
    private HallClient() {}

    static HallNet.@Nullable Wall wall;
    /** Every suit as the server last judged it (the Armour wing). */
    static dev.awaken.armour.net.ArmourPayloads.@Nullable Offers suits;
    /** Who is at work on this stand, if it isn't you. */
    static @Nullable String busy;

    /** The Fitting Stand's door: the Hall opens on the Armour wing. */
    public static void openStand(dev.awaken.armour.net.ArmourPayloads.Offers offers, @Nullable String forger) {
        suits = offers;
        busy = forger;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof HallScreen h) h.wing(HallScreen.ARMOUR);
        else mc.gui.setScreen(new HallScreen(HallScreen.ARMOUR));
    }

    static net.minecraft.core.BlockPos standPos() {
        return suits != null && suits.stand() ? suits.pos() : net.minecraft.core.BlockPos.ZERO;
    }

    public static void init(IEventBus modBus) {
        HallConfig.register();
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post.class,
                e -> HallMoment.draw(e.getGuiGraphics(), e.getScreen().width, e.getScreen().height));
        modBus.addListener(RegisterRenderPipelinesEvent.class, HallPipelines::register);
        modBus.addListener(RegisterPictureInPictureRenderersEvent.class, e -> e.register(PicState.class, PicRenderer::new));
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, e -> {
            e.register(HallNet.Wall.TYPE, (m, ctx) -> wall = m);
            e.register(HallNet.Suits.TYPE, (m, ctx) -> {
                suits = m.offers();
                if (!m.offers().stand()) busy = null;
            });
            e.register(HallNet.Show.TYPE, (m, ctx) -> {
                var open = Minecraft.getInstance().gui.screen();
                if (open instanceof HallScreen h) h.show(m.style(), m.tier());
                else if (open instanceof AnvilStage a) a.show(m.style(), m.tier());
            });
        });
    }
}
