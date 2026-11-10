package dev.awaken.body.ascent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.Awaken;
import dev.awaken.body.BodyConfig;
import dev.awaken.body.ascent.AscentContent;
import dev.awaken.body.ascent.AscentPayloads;
import dev.awaken.body.ascent.WayMemory;
import dev.awaken.body.ascent.WayPayloads;
import dev.awaken.body.ascent.WayUp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

/**
 * The Way Up on the client (SPEC §63): the road as the server last told it (which places woke, which lessons are done or
 * owed), the thin beam that stands on the place that woke for you, and where each {@link Lesson} begins: at the vault's
 * mast, at the chasm's edge, off a sky platform's pier, in the Sky Warden's fight, and when the Sovereign's gold line
 * first fills.
 */
public final class WayClient {
    private WayClient() {}

    static int told, done, owed;
    static final long[] PLACES = new long[WayUp.Rung.VALUES.length];
    static long gateAt = WayMemory.NONE;
    /** The first flight: the pier's end and the way off it, waiting for the gift's scene to finish; then running. */
    private static @Nullable Vec3 pierEnd;
    private static double flyX, flyZ;
    private static boolean flightPending, formPending;
    private static int clock;

    /** The beam's own texture (the rope's braid of light, not the beacon's) and how near and far it is drawn. */
    private static final Identifier BEAM = Awaken.id("textures/ascent/rope.png");
    private static final double BEAM_NEAR = 24, BEAM_FAR = 1400;

    static {
        java.util.Arrays.fill(PLACES, WayMemory.NONE);
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(WayClient::onPayloads);
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterGuiLayersEvent e) -> e.registerAbove(
                net.neoforged.neoforge.client.gui.VanillaGuiLayers.CROSSHAIR, Awaken.id("ascent_lesson"), Lesson::render));
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ClientTickEvent.Post.class, e -> tick());
        bus.addListener(SubmitCustomGeometryEvent.class, WayClient::onSubmit);
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> reset());
    }

    private static void onPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(WayPayloads.Way.TYPE, (msg, ctx) -> {
            told = msg.told();
            done = msg.done();
            owed = msg.owed();
            System.arraycopy(msg.places(), 0, PLACES, 0, Math.min(PLACES.length, msg.places().length));
            gateAt = msg.gateAt();
            LocalPlayer p = Minecraft.getInstance().player;
            // a place has just lit up for you: its own low note (the vault's call, quiet and high)
            if (msg.woke() >= 0 && p != null) AscentSounds.local(p, AscentContent.VOW_CALL.get(), 0.5F, 1.3F);
        });
        event.register(WayPayloads.Lesson.TYPE, (msg, ctx) -> {
            if (msg.kind() == WayPayloads.FIRST_FLIGHT) {
                pierEnd = new Vec3(msg.x(), msg.y(), msg.z());
                flyX = msg.dx();
                flyZ = msg.dz();
                flightPending = true;
            }
        });
    }

    static boolean isOwed(WayUp.Rung r) {
        return (owed & r.bit()) != 0 && (done & r.bit()) == 0;
    }

    public static boolean isDone(WayUp.Rung r) {
        return (done & r.bit()) != 0;
    }

    /** The place that woke for a rung, or null. */
    public static @Nullable BlockPos place(WayUp.Rung r) {
        long v = PLACES[r.ordinal()];
        return v == WayMemory.NONE ? null : BlockPos.of(v);
    }

    static void reset() {
        told = done = owed = 0;
        java.util.Arrays.fill(PLACES, WayMemory.NONE);
        gateAt = WayMemory.NONE;
        pierEnd = null;
        flightPending = formPending = false;
        Lesson.reset();
    }

    // ---- where lessons begin -----------------------------------------------------------------------------------------

    /** A gift's scene begins (AscentScene): the short ones carry their lesson at once. */
    static void sceneBegan(int mode) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !BodyConfig.wayUp()) return;
        if (mode == AscentPayloads.LEAP_EDGE) Lesson.begin(Lesson.Kind.LEAP_EDGE, null, Lesson.hopPath(p, 7));
        else if (mode == AscentPayloads.SKY_EDGE) Lesson.begin(Lesson.Kind.SKY_STRIP, null, null);
    }

    /** A gift's scene ends: its card of keys, or the lesson that follows it. */
    static void sceneEnded(int mode) {
        if (!BodyConfig.wayUp()) return;
        switch (mode) {
            case AscentPayloads.GIFT_LEAP -> Lesson.card(WayUp.Rung.LEAP);
            case AscentPayloads.GIFT_SKY -> Lesson.card(WayUp.Rung.SKY);
            case AscentPayloads.GIFT_SOVEREIGN -> formPending = true;
            default -> { }
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (mc.level == null || p == null) return;
        clock++;
        Lesson.tick(p);
        if (!BodyConfig.wayUp()) return;
        // the first flight begins once the core's scene has had its say
        Vec3 end = pierEnd;
        if (flightPending && end != null && !AscentScene.running()) {
            flightPending = false;
            Vec3 from = end.add(-flyX * 6, 0, -flyZ * 6);
            Lesson.begin(Lesson.Kind.FIRST_FLIGHT, end.add(flyX * 3, 0.6, flyZ * 3), Lesson.runOff(from, end, flyX, flyZ));
        }
        if (Lesson.kind() == Lesson.Kind.FIRST_FLIGHT && end != null && clock % 20 == 0) {
            // a line of rings of light off the pier's end, sinking as a glide would
            Vec3 way = new Vec3(flyX, -0.14, flyZ).normalize();
            for (int i = 1; i <= 5; i++) {
                double d = 4 + i * 7;
                AscentFx.ring(end.x + flyX * d, end.y + 1.2 - d * 0.14, end.z + flyZ * d, way, 1.7F);
            }
        } else if (Lesson.kind() != Lesson.Kind.FIRST_FLIGHT && !flightPending && pierEnd != null && AscentMoves.state().airborne()) {
            pierEnd = null;
        }
        // the form: its key the first time the gold line is full
        if (formPending && Lesson.kind() == null && AscentMoves.aether >= 0.95 && mc.gui.screen() == null) {
            formPending = false;
            Lesson.begin(Lesson.Kind.FORM, null, null);
        }
    }

    // ---- the beam on the place that woke ------------------------------------------------------------------------------

    /** The rung whose place is lit: told, not yet held (the rope first, then wings). */
    private static WayUp.@Nullable Rung lit() {
        for (WayUp.Rung r : new WayUp.Rung[]{WayUp.Rung.ROPE, WayUp.Rung.WINGS}) {
            if ((told & r.bit()) != 0 && PLACES[r.ordinal()] != WayMemory.NONE && !AscentMoves.has(AscentPayloads.Tier.values()[r.ordinal()])) return r;
        }
        return null;
    }

    private static void onSubmit(SubmitCustomGeometryEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.level.dimension() != Level.OVERWORLD || !BodyConfig.wayUp()) return;
        WayUp.Rung r = lit();
        if (r == null) return;
        BlockPos at = BlockPos.of(PLACES[r.ordinal()]);
        Vec3 cam = e.getLevelRenderState().cameraRenderState.pos;
        double dx = at.getX() + 0.5 - cam.x, dz = at.getZ() + 0.5 - cam.z, d2 = dx * dx + dz * dz;
        if (d2 < BEAM_NEAR * BEAM_NEAR || d2 > BEAM_FAR * BEAM_FAR) return;
        // the rank's colour: D green for the vault, C blue for the sky platform
        int colour = r == WayUp.Rung.ROPE ? 0xFF6FE09A : 0xFF8FBEFF;
        // wider with distance so it stays a line you can see, never a pillar up close
        float wide = (float) Math.min(1.6, 0.25 + Math.sqrt(d2) / 420.0);
        PoseStack ps = e.getPoseStack();
        float time = (e.getLevelRenderState().gameTime % 40) + e.getLevelRenderState().worldPartialTicks;
        ps.pushPose();
        ps.translate(dx - 0.5, -64 - cam.y, dz - 0.5);
        BeaconRenderer.submitBeaconBeam(ps, e.getSubmitNodeCollector(), BEAM, 1F, time, 0, 448, colour, wide * 0.5F, wide);
        ps.popPose();
    }
}
