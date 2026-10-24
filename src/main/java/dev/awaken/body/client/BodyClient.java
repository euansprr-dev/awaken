package dev.awaken.body.client;

import com.google.common.reflect.TypeToken;
import dev.awaken.body.Body;
import dev.awaken.body.BodyContent;
import dev.awaken.body.Move;
import dev.awaken.body.net.BodyPayloads;
import dev.awaken.core.api.MovementHooks;
import dev.awaken.progression.client.StatusSections;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

/** Client entry of the Awakened Body: prediction, the camera, the body's looks, its dust, the Status line. */
public final class BodyClient {
    private BodyClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(BodyClient::onPayloads);
        modBus.addListener((RegisterParticleProvidersEvent e) -> e.registerSpriteSet(BodyContent.DUST.get(), BodyDustParticle.Provider::new));
        modBus.addListener((RegisterRenderStateModifiersEvent e) ->
                e.registerEntityModifier(new TypeToken<EntityRenderer<Entity, EntityRenderState>>() {}, BodyAnims::modify));
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(MovementInputUpdateEvent.class, ClientMoves::onInput);
        bus.addListener(ClientTickEvent.Post.class, e -> tick());
        bus.addListener(ViewportEvent.ComputeCameraAngles.class, BodyCamera::onAngles);
        bus.addListener(ComputeFovModifierEvent.class, BodyCamera::onFov);
        bus.addListener(RenderFrameEvent.Pre.class, BodyCamera::onFrame);
        bus.addListener(RenderHandEvent.class, BodyCamera::onHand);
        // around the cast animations' own lean (LOWEST / HIGHEST), so the two nest
        bus.addListener(EventPriority.LOW, RenderPlayerEvent.Pre.class, BodyAnims::onPre);
        bus.addListener(EventPriority.HIGH, RenderPlayerEvent.Post.class, BodyAnims::onPost);
        bus.addListener(InputEvent.InteractionKeyMappingTriggered.class, e -> {
            if (e.isAttack()) ClientMoves.onAttackKey();
        });
        // a respawn or a new dimension is a new player: whatever was running is over
        bus.addListener(ClientPlayerNetworkEvent.Clone.class, e -> ClientMoves.forgetMoves());
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            ClientMoves.reset();
            BodyAnims.reset();
            RunnerFx.reset();
            TryDriver.release();
        });
        Body.installClient(new Body.ClientQuery() {
            @Override public boolean sliding() { return ClientMoves.sliding; }
            @Override public boolean tacticalSprinting() { return ClientMoves.tac; }
            @Override public boolean mantling() { return ClientMoves.mantling; }
            @Override public @Nullable Vec3 slideDirection() { return ClientMoves.slideDirection(); }
            @Override public boolean wallRunning() { return RunnerMoves.state == RunnerMoves.State.RUN; }
            @Override public boolean hanging() { return RunnerMoves.hanging(); }
        }, ClientMoves::fell);
        MovementHooks.installClient(new MovementHooks.ClientAccess() {
            @Override public void castStarting(net.minecraft.world.entity.player.Player p, String powerId, String family, boolean motionPredicted) {
                ClientMoves.castStarting(p, powerId, family, motionPredicted);
            }

            @Override public boolean ownsMovementKey(net.minecraft.world.entity.player.Player p) {
                return dev.awaken.body.ascent.client.AscentMoves.has(dev.awaken.body.ascent.AscentPayloads.Tier.TETHER);
            }
        });
        StatusSections.add(StatusSections.Tab.STATUS, new BodySection());
        dev.awaken.body.ascent.client.AscentClient.init(modBus);
    }

    private static void onPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(BodyPayloads.Refuse.TYPE, (msg, ctx) -> ClientMoves.refused(msg.move()));
        event.register(BodyPayloads.Seen.TYPE, (msg, ctx) -> seen(msg));
        event.register(BodyPayloads.Try.TYPE, (msg, ctx) -> TryDriver.start(msg.what(), msg.delay()));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        ClientMoves.ticks++;
        BodyAnims.tick();
        RunnerFx.tick();
        TryDriver.tick();
    }

    /** Someone else's move (ours were predicted), a trip or a momentum hit: animate it and kick up its dust. */
    private static void seen(BodyPayloads.Seen msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(msg.entityId());
        if (e == null) return;
        double dx = -Mth.sin(msg.yaw() * Mth.DEG_TO_RAD), dz = Mth.cos(msg.yaw() * Mth.DEG_TO_RAD);
        switch (msg.what()) {
            case BodyPayloads.SEEN_TRIP -> BodyFx.hitBurst(e, dx, dz, true);
            case BodyPayloads.SEEN_MOMENTUM -> BodyFx.hitBurst(e, dx, dz, false);
            case BodyPayloads.SEEN_RUNNER_END -> {
                if (e != mc.player) BodyAnims.runnerEnd(e.getId());
            }
            default -> {
                int clip = msg.what() - BodyPayloads.SEEN_CLIP;
                if (clip >= 0 && clip < dev.awaken.body.BodyClips.RUNNER.size()) {
                    if (e != mc.player) {
                        BodyAnims.runner(e.getId(), dev.awaken.body.BodyClips.RUNNER.get(clip));
                        RunnerFx.seen(e, dev.awaken.body.BodyClips.RUNNER.get(clip), msg.yaw());
                    }
                    return;
                }
                Move m = Move.byId(msg.what());
                if (m == null || e == mc.player) return;
                BodyAnims.play(e.getId(), m, msg.yaw());
                switch (m) {
                    case ROLL -> BodyFx.rollRing(e);
                    case TACTICAL_SPRINT -> BodyFx.kick(e, dx, dz);
                    case SLIDE -> BodyFx.slideSpray(e, dx, dz, 1.2F);
                    case MANTLE -> {
                        if (mc.level instanceof net.minecraft.client.multiplayer.ClientLevel cl) BodyFx.lip(cl, e.getX() + dx * 0.7, e.getY() + 1.3, e.getZ() + dz * 0.7, dx, dz);
                    }
                }
            }
        }
    }
}
