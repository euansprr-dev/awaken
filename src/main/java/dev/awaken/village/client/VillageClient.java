package dev.awaken.village.client;

import dev.awaken.citizens.client.CityGlow;
import dev.awaken.village.VillageNet;
import dev.awaken.village.trouble.TroubleContent;
import dev.awaken.village.trouble.TroubleNet;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The villages on the client: the notice board's screen (their sounds and smoke are the citizens' client's), and the
 * troubles' (SPEC §28.7): whoever you bring home, a player carrying them, rings of light, a trail under a Tracks hunter's
 * eyes, a festival's lanterns, the rooftop race's marks.
 */
public final class VillageClient {
    private VillageClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterClientPayloadHandlersEvent e) -> {
            e.register(VillageNet.Board.TYPE, (msg, ctx) -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gui.screen() instanceof NoticeBoardScreen s) s.update(msg);
                else mc.gui.setScreen(new NoticeBoardScreen(msg));
            });
            e.register(TroubleNet.Ring.TYPE, (msg, ctx) -> TroubleFx.ring(msg));
            e.register(TroubleNet.Trail.TYPE, (msg, ctx) -> TroubleFx.trail(msg));
            e.register(TroubleNet.Festoons.TYPE, (msg, ctx) -> TroubleFx.festoons(msg));
            e.register(TroubleNet.Marks.TYPE, (msg, ctx) -> TroubleFx.marks(msg));
        });
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> e.registerEntityRenderer(TroubleContent.RESCUEE.get(), RescueeRenderer::new));
        modBus.addListener((RegisterRenderStateModifiersEvent e) -> e.registerAvatarEntityModifier(
                new net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier() {
                    @Override
                    public <T extends net.minecraft.world.entity.Avatar & net.minecraft.client.entity.ClientAvatarEntity> void accept(T avatar,
                            net.minecraft.client.renderer.entity.state.AvatarRenderState state) {
                        CarryArmPose.modify(avatar, state);
                    }
                }));
        NeoForge.EVENT_BUS.addListener(TroubleFx::tick);
        NeoForge.EVENT_BUS.addListener(SubmitCustomGeometryEvent.class, TroubleFx::submit);
        CityGlow.source(TroubleFx.GLOW);
    }
}
