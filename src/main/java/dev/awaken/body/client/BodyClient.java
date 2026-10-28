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
        // the tactical sprint's line, just above the hotbar
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterGuiLayersEvent e) -> e.registerAbove(
                net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR, dev.awaken.Awaken.id("body_sprint"), SprintBar::render));
        modBus.addListener((RegisterParticleProvidersEvent e) -> e.registerSpriteSet(BodyContent.DUST.get(), BodyDustParticle.Provider::new));
        modBus.addListener((RegisterRenderStateModifiersEvent e) ->
                e.registerEntityModifier(new TypeToken<EntityRenderer<Entity, EntityRenderState>>() {}, BodyAnims::modify));
        // Same renderer base as Arms, registered last: paired weapons and wrist poses have already populated both hands.
        modBus.addListener(EventPriority.LOWEST, RegisterRenderStateModifiersEvent.class, e ->
                e.registerEntityModifier(new TypeToken<EntityRenderer<Entity, EntityRenderState>>() {}, BodyHeldItems::modify));
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(MovementInputUpdateEvent.class, ClientMoves::onInput);
        bus.addListener(ClientTickEvent.Post.class, e -> tick());
        bus.addListener(ViewportEvent.ComputeCameraAngles.class, BodyCamera::onAngles);
        bus.addListener(ComputeFovModifierEvent.class, BodyCamera::onFov);
        // first person (core.api.FirstPerson): the moves' hands, and the grip's hands and feet on the line in the world
        // (the feel settings' firstPersonMoves switches them all off)
        dev.awaken.core.api.FirstPerson.view((pt, v) -> {
            if (dev.awaken.body.client.feel.FeelConfig.fpMoves()) BodyFp.viewPose(pt, v);
        });
        dev.awaken.core.api.FirstPerson.reducedMotion = dev.awaken.body.client.feel.FeelConfig::reduced;
        dev.awaken.core.api.FirstPerson.touchesOn = dev.awaken.body.client.feel.FeelConfig::fpTouches;
        dev.awaken.core.api.FirstPerson.stationsOn = dev.awaken.body.client.feel.FeelConfig::stations;
        dev.awaken.core.api.FirstPerson.world("body_grip", 100, (pt, rig) -> dev.awaken.body.client.feel.FeelConfig.fpMoves() && GripView.pose(pt, rig));
        dev.awaken.core.api.FirstPerson.world("body_moves", 90, (pt, rig) -> dev.awaken.body.client.feel.FeelConfig.fpMoves() && BodyFp.worldPose(pt, rig));
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
            ActionCamera.reset();
            dev.awaken.body.client.feel.ShoulderView.reset();
        });
        // the action camera (SPEC §17.13): Shoulder Surfing's view when it's installed, else vanilla's third person
        dev.awaken.body.client.feel.ShoulderView.init();
        if (!dev.awaken.body.client.feel.ShoulderView.INSTALLED) {
            bus.addListener(net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent.class, ActionCamera::onDetached);
        }
        Body.installClient(new Body.ClientQuery() {
            @Override public boolean sliding() { return ClientMoves.sliding; }
            @Override public boolean tacticalSprinting() { return ClientMoves.tac; }
            @Override public boolean mantling() { return ClientMoves.mantling || ClientMoves.vaulting; }
            @Override public @Nullable Vec3 slideDirection() { return ClientMoves.slideDirection(); }
            @Override public boolean wallRunning() { return RunnerMoves.state == RunnerMoves.State.RUN; }
            @Override public boolean hanging() { return RunnerMoves.hanging(); }
            @Override public boolean stairSliding() { return ClientMoves.sliding && ClientMoves.stairSliding; }
        }, ClientMoves::fell);
        MovementHooks.installClient(new MovementHooks.ClientAccess() {
            @Override public void castStarting(net.minecraft.world.entity.player.Player p, String powerId, String family, boolean motionPredicted) {
                ClientMoves.castStarting(p, powerId, family, motionPredicted);
            }

            @Override public boolean ownsMovementKey(net.minecraft.world.entity.player.Player p) {
                // the rope has its own key (SPEC §17.30); only someone who binds both to one key gives it to the rope
                return dev.awaken.body.ascent.client.AscentMoves.has(dev.awaken.body.ascent.AscentPayloads.Tier.TETHER)
                        && dev.awaken.core.client.AwakenKeys.MOVEMENT.same(dev.awaken.core.client.AwakenKeys.ROPE);
            }
        });
        StatusSections.add(StatusSections.Tab.POWERS, new BodySection());
        StatusSections.add(StatusSections.Tab.POWERS, new AscentSection());
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
        ActionCamera.tick(mc);
        dev.awaken.body.client.feel.ShoulderView.tick(mc);
        GripMoves.forget(mc.player);
        GripView.tick(mc.player);
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
            case BodyPayloads.SEEN_VAULT, BodyPayloads.SEEN_VAULT + 1, BodyPayloads.SEEN_VAULT + 2, BodyPayloads.SEEN_VAULT + 3,
                 BodyPayloads.SEEN_VAULT + 4 -> {
                // someone else's crossing (SPEC §17.27): its kind's clip, and the dust off the rail their hand meets
                if (e == mc.player) return;
                int kind = msg.what() - BodyPayloads.SEEN_VAULT;
                BodyAnims.vault(e.getId(), kind, msg.yaw());
                if (mc.level instanceof net.minecraft.client.multiplayer.ClientLevel cl) {
                    double up = kind == dev.awaken.body.VaultRules.HIGH ? 2.5 : kind == dev.awaken.body.VaultRules.OVER ? 1.6 : 1.5;
                    BodyFx.lip(cl, e.getX() + dx * 0.8, e.getY() + up, e.getZ() + dz * 0.8, dx, dz);
                }
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
