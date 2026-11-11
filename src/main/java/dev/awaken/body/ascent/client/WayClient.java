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
    /** Which of Rope and Wings' six moves have been made once (a bit each, WayUp.Move), and whether the road has been heard at all. */
    static int moves;
    static boolean synced;
    /**
     * The leap, a move at a time (SPEC §71.3): which of its moves may be made (a bit each, WayUp.Leap; all of them
     * until the server says otherwise, so nothing is ever held back by a message that has not come) and which stands
     * have been taken.
     */
    static int leap = 0xF, stands;
    static final long[] PLACES = new long[WayUp.Rung.VALUES.length];
    static long gateAt = WayMemory.NONE;
    /** The first flight: the pier's end and the way off it, waiting for the gift's scene to finish; then running. */
    private static @Nullable Vec3 pierEnd;
    private static double flyX, flyZ;
    private static boolean flightPending, formPending;
    private static int clock, flightFrom;

    // ---- the Leaper's Gauntlet (SPEC §71) ------------------------------------------------------------------------------
    /** Stands near enough to tick, and when each last said so (their block entities, twice a second). */
    private static final java.util.Map<Long, int[]> STANDS = new java.util.HashMap<>();
    /** The stand being held, how long, and the wait after a take was sent. */
    private static long holding = WayMemory.NONE;
    private static int held, takeWait;
    /** A stroke playing: the stand's move, since when; and what the body is held still at. */
    private static int stroke = -1, strokeAt;
    private static @Nullable Vec3 strokeAtPos;
    private static int rooted;
    private static @Nullable Vec3 root, lastGround;
    /** The spot the course last marked for each of the leap's moves (take-off, landing), and the lesson waiting for a title to finish. */
    private static final Vec3[][] SPOTS = new Vec3[WayUp.Leap.VALUES.length][];
    private static int spotPending = -1;
    private static boolean edgePending;
    /** A card of keys waiting for a title to leave (a rung's ordinal, -1 for none). */
    private static int cardPending = -1;
    /** Ticks a stand is held to take it, and how long the body is then held still for the stroke. */
    static final int TAKE = 20, STROKE = 40;

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
        // standing on a stand still to take, the Use key is the stand's: nothing in the hand is used, nothing is placed
        bus.addListener(net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered.class, e -> {
            if (e.isUseItem() && onStand() != WayMemory.NONE) {
                e.setSwingHand(false);
                e.setCanceled(true);
            }
        });
    }

    private static void onPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(WayPayloads.Way.TYPE, (msg, ctx) -> {
            told = msg.told();
            done = msg.done();
            // a rung handed down arrives with another's gift: its own lesson waits a quarter minute and more
            if ((msg.owed() & ~owed) != 0) Lesson.deferOwed(400);
            owed = msg.owed();
            System.arraycopy(msg.places(), 0, PLACES, 0, Math.min(PLACES.length, msg.places().length));
            gateAt = msg.gateAt();
            moves = msg.moves();
            leap = msg.leap();
            stands = msg.stands();
            synced = true;
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
                // never before the gift's own scene (it is told in the same tick and starts a tick later)
                flightFrom = clock + 12;
            }
        });
        event.register(WayPayloads.Stroke.TYPE, (msg, ctx) -> onStroke(msg));
        event.register(WayPayloads.Spot.TYPE, (msg, ctx) -> onSpot(msg));
    }

    /** Whether one of the leap's moves may be made now (the leap's own rules ask before a second jump, a dash or a slam). */
    public static boolean canLeap(WayUp.Leap l) {
        return (leap & l.bit()) != 0;
    }

    /** Whether a stand of the Gauntlet has been taken. */
    public static boolean stand(WayUp.Leap l) {
        return (stands & l.bit()) != 0;
    }

    static boolean isOwed(WayUp.Rung r) {
        return (owed & r.bit()) != 0 && (done & r.bit()) == 0;
    }

    /** Whether the Leaper's Gauntlet's gate stands open for you. */
    public static boolean gateOpen() {
        return gateAt != WayMemory.NONE;
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
        told = done = owed = moves = stands = 0;
        leap = 0xF;
        synced = false;
        STANDS.clear();
        holding = WayMemory.NONE;
        held = takeWait = rooted = 0;
        stroke = spotPending = cardPending = -1;
        edgePending = false;
        root = lastGround = strokeAtPos = null;
        java.util.Arrays.fill(SPOTS, null);
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
        // every gift is named in the Unlock title (SPEC §71.2; Euan, 2026-11-11); what teaches it waits for the title
        switch (mode) {
            case AscentPayloads.LEAP_EDGE -> {
                // the old course's edge, until the Gauntlet's stands replace it: the body is held where it last stood,
                // so the title is not read from the bottom of the pit (his run of 11 Nov: the gift, and a second on, the pit)
                show(dev.awaken.body.ascent.AscentUnlocks.DOUBLE_JUMP);
                root(p, 100);
                edgePending = true;
            }
            case AscentPayloads.GIFT_LEAP -> {
                // the old course's far landing: the leap's other two moves came with it, and each is named
                show(dev.awaken.body.ascent.AscentUnlocks.AIR_DASH);
                show(dev.awaken.body.ascent.AscentUnlocks.SLAM);
            }
            case AscentPayloads.GIFT_WINGS, AscentPayloads.WINGS_EDGE -> show(dev.awaken.body.ascent.AscentUnlocks.WINGS);
            case AscentPayloads.GIFT_SKY -> show(dev.awaken.body.ascent.AscentUnlocks.FLIGHT);
            case AscentPayloads.GIFT_SOVEREIGN -> show(dev.awaken.body.ascent.AscentUnlocks.FORM);
            case AscentPayloads.SKY_EDGE -> Lesson.begin(Lesson.Kind.SKY_STRIP, null, null);
            default -> { }
        }
    }

    static void show(Identifier unlock) {
        var u = dev.awaken.core.api.Unlocks.get(unlock);
        if (u != null) dev.awaken.core.api.Unlocks.client().show(u);
    }

    /** A gift's scene ends: its card of keys, or the lesson that follows it. */
    static void sceneEnded(int mode) {
        if (!BodyConfig.wayUp()) return;
        switch (mode) {
            // a card of keys never shares the screen with a title: it follows it
            case AscentPayloads.GIFT_LEAP -> cardPending = WayUp.Rung.LEAP.ordinal();
            case AscentPayloads.GIFT_SKY -> cardPending = WayUp.Rung.SKY.ordinal();
            case AscentPayloads.GIFT_SOVEREIGN -> formPending = true;
            default -> { }
        }
    }

    // ---- the Gauntlet's stands -----------------------------------------------------------------------------------------

    /** A stand's block entity says it is there (twice a second while it is near enough to tick). */
    static void standSeen(BlockPos pos, int leapOrdinal) {
        int[] v = STANDS.computeIfAbsent(pos.asLong(), k -> new int[2]);
        v[0] = leapOrdinal;
        v[1] = clock;
    }

    /** The stand the player stands on that is still theirs to take, or NONE. */
    private static long onStand() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !BodyConfig.wayUp() || !p.onGround() || STANDS.isEmpty()) return WayMemory.NONE;
        long at = p.blockPosition().asLong();
        int[] v = STANDS.get(at);
        if (v == null || clock - v[1] > 30 || takeable(v[0]) == null) return WayMemory.NONE;
        return at;
    }

    /** The move a stand would give this player now, or null: taken already, or not built yet. */
    private static WayUp.@Nullable Leap takeable(int leapOrdinal) {
        if (leapOrdinal < 0 || leapOrdinal >= WayUp.Leap.VALUES.length) return null;
        WayUp.Leap l = WayUp.Leap.VALUES[leapOrdinal];
        return stand(l) || l == WayUp.Leap.DIVE && !WayUp.DIVE_BUILT ? null : l;
    }

    /**
     * Once a tick: the nearest stand still to take is lit and carries its prompt; standing on it with Use held for a
     * second takes it (the server checks it all again and answers with the stroke).
     */
    private static void tickStands(Minecraft mc, LocalPlayer p) {
        if (takeWait > 0) takeWait--;
        if (STANDS.isEmpty()) return;
        STANDS.values().removeIf(v -> clock - v[1] > 40);
        if (stroke >= 0 || takeWait > 0 || dev.awaken.core.api.Unlocks.client().pending() || mc.gui.screen() != null) {
            held = 0;
            Lesson.progress = 0;
            return;
        }
        long best = WayMemory.NONE;
        double bestD = 6 * 6;
        for (var e : STANDS.entrySet()) {
            if (takeable(e.getValue()[0]) == null) continue;
            BlockPos at = BlockPos.of(e.getKey());
            double d = p.distanceToSqr(at.getX() + 0.5, at.getY() + 0.25, at.getZ() + 0.5);
            if (d < bestD) {
                bestD = d;
                best = e.getKey();
            }
        }
        if (best == WayMemory.NONE) {
            held = 0;
            Lesson.progress = 0;
            return;
        }
        BlockPos at = BlockPos.of(best);
        // lit for you: a slow lift of gold off the plate
        if (clock % 4 == 0) {
            var r = p.getRandom();
            MoteParticle.next(r.nextFloat() < 0.3F ? 0xFFF3C9 : 0xF0C766, 0.7F + r.nextFloat() * 0.4F, 30 + r.nextInt(14), -0.02F, false);
            p.level().addParticle(AscentContent.MOTE.get(), at.getX() + 0.2 + r.nextDouble() * 0.6, at.getY() + 0.3, at.getZ() + 0.2 + r.nextDouble() * 0.6,
                    0, 0.02 + r.nextDouble() * 0.02, 0);
        }
        boolean on = onStand() == best;
        Lesson.Kind k = Lesson.kind();
        // never over another lesson: the stand's prompt takes the screen only when it is free, or already its own
        if (k == null || k == Lesson.Kind.STAND_NEAR || k == Lesson.Kind.STAND_ON || k == Lesson.Kind.REACH) {
            Vec3 top = new Vec3(at.getX() + 0.5, at.getY() + (on ? 1.9 : 0.6), at.getZ() + 0.5);
            Lesson.begin(on ? Lesson.Kind.STAND_ON : Lesson.Kind.STAND_NEAR, top, null);
            Lesson.keep();
        }
        if (on && mc.options.keyUse.isDown() && p.getDeltaMovement().horizontalDistanceSqr() < 0.004) {
            if (holding != best) held = 0;
            holding = best;
            held++;
            Lesson.progress = held / (float) TAKE;
            if (held == 1) AscentSounds.local(p, net.minecraft.sounds.SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.5F, 0.7F);
            if (held >= TAKE) {
                held = 0;
                takeWait = 60;
                Lesson.progress = 0;
                net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new WayPayloads.Take(best));
            }
        } else {
            held = 0;
            Lesson.progress = 0;
        }
    }

    /** The server's word: the stand is taken and the move is yours. The stroke plays, then its title. */
    private static void onStroke(WayPayloads.Stroke msg) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || msg.leap() < 0 || msg.leap() >= WayUp.Leap.VALUES.length) return;
        stroke = msg.leap();
        strokeAt = clock;
        BlockPos at = BlockPos.of(msg.pos());
        strokeAtPos = new Vec3(at.getX() + 0.5, at.getY() + 0.25, at.getZ() + 0.5);
        stands |= WayUp.Leap.VALUES[stroke].bit();
        Lesson.fade();
        root(p, STROKE);
        // the works' own stroke: Minecraft's anvil, low, and its bell after it (no synthetic ding)
        AscentSounds.local(p, net.minecraft.sounds.SoundEvents.ANVIL_PLACE, 0.8F, 0.55F);
        AscentSounds.duck(STROKE + 100);
        dev.awaken.body.client.feel.FeelDirector.remember();
        AscentFx.ring(strokeAtPos.x, strokeAtPos.y + 0.1, strokeAtPos.z, new Vec3(0, 1, 0), 0.7F);
    }

    /** A mark of the course (SPEC §71.4): the gulf's figure, a spot's lesson, a spot shown again after three misses. */
    private static void onSpot(WayPayloads.Spot msg) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !BodyConfig.wayUp()) return;
        Vec3 from = Vec3.atBottomCenterOf(BlockPos.of(msg.from())).add(0, 1, 0), to = Vec3.atBottomCenterOf(BlockPos.of(msg.to())).add(0, 1, 0);
        if (msg.kind() == WayPayloads.SPOT_REACH) {
            if (!stand(WayUp.Leap.DOUBLE) && Lesson.kind() == null) Lesson.begin(Lesson.Kind.REACH, from.lerp(to, 0.5), Lesson.leapPath(from, to));
            SPOTS[WayUp.Leap.DOUBLE.ordinal()] = new Vec3[]{from, to};
            return;
        }
        int l = msg.leap();
        if (msg.kind() == WayPayloads.SPOT_MISS && l < 0) {
            // the course did not say which move: the spot nearest where the player stands
            double best = Double.MAX_VALUE;
            for (int i = 0; i < SPOTS.length; i++) {
                if (SPOTS[i] != null && SPOTS[i][0].distanceToSqr(from) < best) {
                    best = SPOTS[i][0].distanceToSqr(from);
                    l = i;
                }
            }
        }
        if (l < 0 || l >= SPOTS.length) return;
        // a hall's first spot teaches; the later ones only if they are missed
        if (msg.kind() == WayPayloads.SPOT_USE && msg.n() > 1) return;
        SPOTS[l] = new Vec3[]{from, to};
        spotPending = l;
    }

    /** The lesson of a marked spot, once no title is on the screen and nothing else is teaching. */
    private static void beginSpot(int l) {
        Vec3[] s = SPOTS[l];
        if (s == null) return;
        Vec3 mid = s[0].lerp(s[1], 0.5);
        switch (WayUp.Leap.VALUES[l]) {
            case DOUBLE -> Lesson.begin(Lesson.Kind.LEAP_SPOT, mid.add(0, 2.0, 0), Lesson.leapPath(s[0], s[1]));
            case DASH -> Lesson.begin(Lesson.Kind.DASH_SPOT, mid.add(0, 1.4, 0), Lesson.dashPath(s[0], s[1]));
            case SLAM -> Lesson.begin(Lesson.Kind.SLAM_SPOT, s[1], Lesson.slamPath(s[0], s[1]));
            default -> { }
        }
    }

    /** Holds the body where it stands (or last stood, if it has already left the ground) for this many ticks. */
    private static void root(LocalPlayer p, int ticks) {
        Vec3 at = p.onGround() || lastGround == null || lastGround.distanceToSqr(p.position()) > 36 ? p.position() : lastGround;
        root = at;
        rooted = ticks;
        if (!p.onGround()) p.setPos(at.x, at.y, at.z);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (mc.level == null || p == null) return;
        clock++;
        if (p.onGround() && !AscentMoves.active()) lastGround = p.position();
        // held still for a stroke or a title at an edge: the client's own position, so nothing rubber-bands
        if (rooted > 0) {
            rooted--;
            Vec3 at = root;
            if (at != null && !AscentMoves.active() && mc.gui.screen() == null) {
                Vec3 v = p.getDeltaMovement();
                p.setPos(at.x, p.onGround() ? at.y : p.getY(), at.z);
                p.setDeltaMovement(0, Math.min(0, v.y), 0);
            }
        }
        Lesson.tick(p);
        if (!BodyConfig.wayUp()) return;
        tickStands(mc, p);
        var unlocks = dev.awaken.core.api.Unlocks.client();
        // a stroke: light climbs the legs for a second and a half, and the title lands as the press does
        if (stroke >= 0) {
            int t = clock - strokeAt;
            AscentScene.risingLight(p, p, Math.min(79, t * 2));
            if (t == 12) {
                AscentSounds.local(p, net.minecraft.sounds.SoundEvents.BELL_RESONATE, 0.5F, 0.8F);
                show(dev.awaken.body.ascent.AscentUnlocks.of(WayUp.Leap.VALUES[stroke]));
            }
            if (t >= STROKE) {
                // what follows the title: the lesson of this move's first spot, if the course has marked one
                if (SPOTS[stroke] != null) spotPending = stroke;
                stroke = -1;
            }
        }
        // what waits for a title to leave: an old edge's prompt, a spot's lesson, a card of keys
        if (!unlocks.pending() && stroke < 0) {
            if (edgePending) {
                edgePending = false;
                Lesson.begin(Lesson.Kind.LEAP_EDGE, null, Lesson.hopPath(p, 7));
            } else if (spotPending >= 0 && Lesson.kind() == null && canLeap(WayUp.Leap.VALUES[spotPending])) {
                int l = spotPending;
                spotPending = -1;
                beginSpot(l);
            } else if (cardPending >= 0 && Lesson.kind() == null) {
                Lesson.card(WayUp.Rung.VALUES[cardPending]);
                cardPending = -1;
            }
        }
        // the first flight begins once the core's scene has had its say
        Vec3 end = pierEnd;
        if (flightPending && end != null && clock >= flightFrom && AscentScene.pending == 0 && !AscentScene.running() && !unlocks.pending()) {
            flightPending = false;
            Vec3 from = end.add(-flyX * 6, 0, -flyZ * 6);
            Lesson.begin(Lesson.Kind.FIRST_FLIGHT, end.add(flyX * 3, 0.6, flyZ * 3), Lesson.runOff(from, end, flyX, flyZ));
        }
        if (Lesson.kind() == Lesson.Kind.FIRST_FLIGHT && end != null && clock % 20 == 0) {
            // a line of rings of light off the pier's end, sinking as a glide would
            Vec3 way = new Vec3(flyX, -0.14, flyZ).normalize();
            for (int i = 1; i <= 4; i++) {
                double d = 6 + i * 10;
                AscentFx.ring(end.x + flyX * d, end.y + 1.2 - d * 0.14, end.z + flyZ * d, way, 1.2F);
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
        double d = Math.sqrt(d2);
        // wider with distance so it stays a line you can see, never a pillar up close
        float wide = (float) Math.min(3.2, 0.3 + d / 160.0);
        // fog takes everything past the view distance: a far beam is drawn nearer on the same line of sight, the whole
        // of it scaled about the eye, so it looks exactly as it would from here (it may show over a hill that would hide it)
        double clear = Math.max(64, mc.options.getEffectiveRenderDistance() * 16 * 0.55);
        float s = d > clear ? (float) (clear / d) : 1F;
        PoseStack ps = e.getPoseStack();
        float time = (e.getLevelRenderState().gameTime % 40) + e.getLevelRenderState().worldPartialTicks;
        ps.pushPose();
        ps.translate(dx * s, (-64 - cam.y) * s, dz * s);
        ps.scale(s, s, s);
        ps.translate(-0.5, 0, -0.5);
        BeaconRenderer.submitBeaconBeam(ps, e.getSubmitNodeCollector(), BEAM, 1F, time, 0, 448, colour, wide * 0.5F, wide);
        ps.popPose();
    }
}
