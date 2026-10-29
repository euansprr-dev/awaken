package dev.awaken.arms.client;

import dev.awaken.arms.Archetypes;
import dev.awaken.arms.Arms;
import dev.awaken.arms.ArmsSounds;
import dev.awaken.arms.FpSwings;
import dev.awaken.core.api.CombatFeedback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The weapon's own cutting motion while vanilla mines with it (SPEC §40 "The chop"; Euan 2026-10-29: "when it does cut,
 * there is no animation"): an axe chops wood, a blade hacks a web, bamboo or leaves, a loop (FpSwings.Loop) for as long
 * as the key mines. Each blow lands on the frame the block breaks: the loop's rate is solved every tick from the block's
 * own break time (the progress vanilla's MultiPlayerGameMode adds a tick), inside a band that keeps it a chop. A blow
 * plays the block's hit sound and knocks chips off where the blade strikes; vanilla's own hit sound (every 4 ticks, out
 * of step with the blows) is hushed on a block a chop is showing. In third person the body plays the loop's clip
 * (ArmsClips.CHOP, HACK), and everyone else's chop is read from what every client already sees of them (the arm swing a
 * miner sends every few ticks, the block their look falls on): nothing new crosses the wire.
 */
final class ArmsChop {
    private ArmsChop() {}

    /** The rate band (loop ticks a tick): slower or faster than this and the blow stops reading as a chop. */
    private static final float SLOWEST = 0.6F, FASTEST = 1.7F;
    /** Ticks the hands take back to rest when the cutting stops (ArmsHands' blend). */
    static final float OUT = 4F;
    /** Loop ticks before the blow that the drive leaves its set (the whoosh). */
    private static final float DRIVE = 2F;
    private static final int CHIPS = 5;

    // vanilla mined with the weapon this tick (ArmsPredict.onKey), on this block, face and point
    private static boolean wanted;
    private static @Nullable BlockPos wantPos;
    private static Direction wantFace = Direction.UP;
    private static Vec3 wantAt = Vec3.ZERO;
    // cutting now, an axe's chop (else the hack); the loop's clock (loop ticks, never wrapped) now and a tick ago, its rate
    private static boolean on, chop;
    private static float phase, phaseO, rate = 1F;
    // the block cut (and the one before, whose last hit sound the server may still send), its state, the ticks vanilla
    // has added progress to it
    private static @Nullable BlockPos at, before;
    private static @Nullable BlockState cutState;
    private static Direction face = Direction.UP;
    private static Vec3 point = Vec3.ZERO;
    private static int progressTicks;
    private static boolean destroying;
    // the blows and whooshes shown so far (frame by frame: on the frame the blade lands); the body's clip on the clock
    private static int blows, drives;
    private static boolean bodySynced;
    /** Our own sounds play through the hush. */
    private static boolean playing;

    /** Everyone else's chop, by entity id. */
    private static final Map<Integer, Other> OTHERS = new HashMap<>();

    private static final class Other {
        float lastAnim;
        boolean wasSwinging, on, chop;
        int swings, firstSwing, lastSwing, start, movedAt = -1000;
        @Nullable BlockPos pos;
        @Nullable BlockState state;
        Direction face = Direction.UP;
        Vec3 point = Vec3.ZERO;
    }

    /** Vanilla mines this tick with a fighting weapon in hand (ArmsPredict.onKey's MINE): the cutting goes on. */
    static void want(Minecraft mc) {
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        wanted = true;
        wantPos = hit.getBlockPos();
        wantFace = hit.getDirection();
        wantAt = hit.getLocation();
    }

    /** Cutting now (the hands on the loop, or easing out of it once this turns false). */
    static boolean on() {
        return on;
    }

    /** The hands' cut this frame (into d, q), as FpSwings.sample gives a move; false when not cutting (ArmsHands.lab). */
    static boolean pose(FpSwings.@Nullable Style st, boolean ours, float partial, Vector3f d, Quaternionf q) {
        return on && FpSwings.cut(st, chop, Mth.lerp(partial, phaseO, phase), ours, d, q);
    }

    /** Client tick (after vanilla's mining): the clock, its rate, the body's clip, everyone else's chop. */
    static void tick(Minecraft mc, LocalPlayer p) {
        if (mc.level == null) return;
        phaseO = phase;
        boolean w = wanted;
        wanted = false;
        boolean axe = p.getMainHandItem().is(ItemTags.AXES);
        Archetypes.Loaded style = w ? Arms.styleOf(p.getMainHandItem()) : null;
        FpSwings.Loop loop = style == null ? null : FpSwings.loop(FpSwings.style(style.id()), axe);
        if (loop == null || wantPos == null) {
            if (on) stop(p);
        } else {
            follow(mc, p, loop, axe);
        }
        others(mc, p);
    }

    private static void follow(Minecraft mc, LocalPlayer p, FpSwings.Loop loop, boolean axe) {
        BlockPos pos = wantPos;
        BlockState state = mc.level.getBlockState(pos);
        // the block's progress as vanilla sums it: one more tick of it for every tick it goes on destroying this block
        boolean d = mc.gameMode != null && mc.gameMode.isDestroying();
        progressTicks = d && destroying && pos.equals(at) ? progressTicks + 1 : 0;
        destroying = d;
        if (!pos.equals(at)) before = at;
        at = pos;
        face = wantFace;
        point = wantAt;
        if (!state.isAir()) cutState = state;
        float g = d ? state.getDestroyProgress(p, mc.level, pos) : 0F;
        // the ticks of clock until the frame the block goes (it vanishes the tick it breaks; a pose shows a tick late)
        int need = g > 0 ? Math.max(0, Mth.ceil(1F / g - progressTicks - 1e-3F) - 1) : -1;
        float period = loop.period();
        boolean third = !mc.options.getCameraType().isFirstPerson();
        if (!on || chop != axe) {
            on = true;
            chop = axe;
            // begun on the way up; a block that goes sooner than the first blow would land begins it further on, and
            // one gone at once (bamboo to a blade) begins on the blow
            float start = loop.enter();
            if (!d) start = period - 1F;
            else if (need < period - start) start = period - Math.max(1, need);
            phase = phaseO = start;
            rate = 1F;
            blows = Mth.floor(start / period);
            drives = Mth.floor((start + DRIVE) / period);
            bodySynced = third && start == loop.enter() && ArmsBodies.chop(p, axe);
        }
        if (third) rate = 1F; // the body's clip runs at its own pace, the blows on it
        else if (need > 0) rate = solve(period - Mth.positiveModulo(phase, period), period, need);
        else if (need < 0) rate = 1F; // between blocks (vanilla's 5-tick wait): the loop's own pace
        float was = phase;
        phase += rate;
        if (!third && bodySynced) {
            ArmsBodies.unchop(p);
            bodySynced = false;
        } else if (third && !bodySynced && crossed(was, phase, period, loop.enter())) {
            bodySynced = ArmsBodies.chop(p, axe);
        }
    }

    /**
     * The rate that brings the next blow, or one a whole loop later, to land {@code need} ticks from now, {@code rest}
     * loop ticks away: the one nearest the loop's own pace inside the band.
     */
    static float solve(float rest, float period, int need) {
        float best = 1F, gap = Float.MAX_VALUE;
        for (int j = 0; j < 4; j++) {
            float r = Mth.clamp((rest + j * period) / need, SLOWEST, FASTEST);
            float off = Math.abs((float) Math.log(r));
            if (off < gap) {
                gap = off;
                best = r;
            }
        }
        return best;
    }

    private static boolean crossed(float from, float to, float period, float at) {
        return Mth.floor((to - at) / period) > Mth.floor((from - at) / period);
    }

    /** Each frame: a blow lands, or a drive leaves its set, on the frame the hands show it. */
    static void frame(Minecraft mc) {
        if (!on || mc.player == null || mc.level == null) return;
        Archetypes.Loaded style = Arms.styleOf(mc.player.getMainHandItem());
        FpSwings.Loop loop = style == null ? null : FpSwings.loop(FpSwings.style(style.id()), chop);
        if (loop == null) return;
        float shown = Mth.lerp(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false), phaseO, phase);
        int b = Mth.floor(shown / loop.period()), dr = Mth.floor((shown + DRIVE) / loop.period());
        if (dr > drives) {
            drives = dr;
            whoosh(mc, mc.player.getX(), mc.player.getEyeY() - 0.3, mc.player.getZ(), chop);
        }
        if (b > blows) {
            blows = b;
            BlockState state = at == null ? null : mc.level.getBlockState(at);
            if (state != null && state.isAir()) state = cutState; // the blow that broke it
            if (at != null && state != null) {
                blow(mc, at, state, face, point, chop);
                if (mc.options.getCameraType().isFirstPerson()) CombatFeedback.kick(0F, chop ? 0.7F : 0.35F, 0F, chop ? 4 : 3);
            }
        }
    }

    private static void stop(LocalPlayer p) {
        on = false;
        if (bodySynced) ArmsBodies.unchop(p);
        bodySynced = false;
        destroying = false;
        progressTicks = 0;
        at = before = null;
        cutState = null;
    }

    /** The blade lands: the block's own hit, louder and lower than vanilla's tap, wood's knock under an axe, chips. */
    private static void blow(Minecraft mc, BlockPos pos, BlockState state, Direction face, Vec3 point, boolean chop) {
        RandomSource r = mc.level.getRandom();
        SoundType type = state.getSoundType();
        play(mc, point, type.getHitSound(), SoundSource.BLOCKS, (type.getVolume() + 1F) * (chop ? 0.32F : 0.24F),
                type.getPitch() * (chop ? 0.7F : 0.85F) + r.nextFloat() * 0.08F);
        if (chop && state.is(BlockTags.MINEABLE_WITH_AXE)) {
            play(mc, point, ArmsSounds.HIT_WOOD.get(), SoundSource.BLOCKS, 0.35F, 0.85F + r.nextFloat() * 0.15F);
        }
        BlockParticleOption chip = new BlockParticleOption(ParticleTypes.BLOCK, state, pos);
        float out = chop ? 0.14F : 0.09F;
        for (int i = 0; i < CHIPS; i++) {
            mc.level.addParticle(chip, point.x + face.getStepX() * 0.05, point.y + face.getStepY() * 0.05, point.z + face.getStepZ() * 0.05,
                    face.getStepX() * out + (r.nextFloat() - 0.5F) * 0.12F, face.getStepY() * out + r.nextFloat() * 0.1F,
                    face.getStepZ() * out + (r.nextFloat() - 0.5F) * 0.12F);
        }
    }

    private static void whoosh(Minecraft mc, double x, double y, double z, boolean chop) {
        RandomSource r = mc.level.getRandom();
        play(mc, new Vec3(x, y, z), chop ? ArmsSounds.SWISH_HEAVY.get() : ArmsSounds.SWISH_QUICK.get(), SoundSource.PLAYERS,
                chop ? 0.2F : 0.25F, (chop ? 0.9F : 1.05F) + r.nextFloat() * 0.1F);
    }

    private static void play(Minecraft mc, Vec3 at, SoundEvent sound, SoundSource source, float volume, float pitch) {
        playing = true;
        try {
            mc.level.playLocalSound(at.x, at.y, at.z, sound, source, volume, pitch, false);
        } finally {
            playing = false;
        }
    }

    /** Vanilla's mining hit sound on a block a chop is showing: hushed (the blows play their own, in step). */
    static void onSound(PlaySoundEvent e) {
        SoundInstance s = e.getSound();
        if (playing || s == null || s.getSource() != SoundSource.BLOCKS || !on && OTHERS.isEmpty()) return;
        boolean hush = on && (hits(s, at, cutState) || hits(s, before, cutState));
        if (!hush) {
            for (Other o : OTHERS.values()) {
                if (o.on && hits(s, o.pos, o.state)) {
                    hush = true;
                    break;
                }
            }
        }
        if (hush) e.setSound(null);
    }

    private static boolean hits(SoundInstance s, @Nullable BlockPos pos, @Nullable BlockState state) {
        return pos != null && state != null && s.getIdentifier().equals(state.getSoundType().getHitSound().location())
                && Math.abs(s.getX() - pos.getX() - 0.5) < 0.6 && Math.abs(s.getY() - pos.getY() - 0.5) < 0.6
                && Math.abs(s.getZ() - pos.getZ() - 0.5) < 0.6;
    }

    // ---- everyone else ----------------------------------------------------------------------------------------------

    /** A move of theirs began (ArmsBodies.move): its clip takes the body, and their swings a while after are its own. */
    static void moved(int entityId) {
        Other o = OTHERS.get(entityId);
        if (o != null) {
            o.movedAt = ArmsPredict.ticks;
            o.on = false;
        }
    }

    /**
     * Another player near cuts while their mining swings keep coming (a miner's client swings the arm every few ticks,
     * and the server passes it on; one swing is a block placed or a log stripped, never a chop), a fighting weapon in
     * hand, no move of theirs just begun, their look on a block it cuts. The body plays the loop's clip from its start
     * and the blows land on its clock, at the block.
     */
    private static void others(Minecraft mc, LocalPlayer self) {
        int now = ArmsPredict.ticks;
        for (Player p : mc.level.players()) {
            if (p == self || p.distanceToSqr(self) > 32 * 32) continue;
            boolean swinging = p.isSwinging() && p.getCurrentSwing() != null && p.getCurrentSwing().hand() == InteractionHand.MAIN_HAND;
            Other o = OTHERS.get(p.getId());
            if (o == null) {
                if (!swinging) continue;
                o = new Other();
                OTHERS.put(p.getId(), o);
            }
            float anim = p.getSwingAnimation(1F);
            if (swinging && (!o.wasSwinging || anim < o.lastAnim)) {
                if (now - o.lastSwing > 8) {
                    o.swings = 0;
                    o.firstSwing = now;
                }
                o.swings++;
                o.lastSwing = now;
            }
            o.wasSwinging = swinging;
            o.lastAnim = anim;
            boolean axe = p.getMainHandItem().is(ItemTags.AXES);
            Archetypes.Loaded style = Arms.styleOf(p.getMainHandItem());
            FpSwings.Loop loop = style == null ? null : FpSwings.loop(FpSwings.style(style.id()), axe);
            boolean go = loop != null && now - o.lastSwing <= 6 && now - o.movedAt > 20 && !p.isCreative();
            if (o.on && (!go || o.chop != axe)) {
                o.on = false;
                ArmsBodies.unchop(p);
            }
            if (!o.on && go && o.swings >= 2 && cuts(mc, p, o)) {
                ArmsBodies.chop(p, axe); // without the library the blows still land, on vanilla's arm swing
                o.on = true;
                o.chop = axe;
                o.start = now;
            }
            if (o.on && loop != null) {
                // the clip begins where the hands enter the loop: its blows a loop's rest after, then every loop
                float t = now - o.start + loop.enter();
                int period = Math.round(loop.period());
                if (Math.round(t) % period == 0 && cuts(mc, p, o)) blow(mc, o.pos, o.state, o.face, o.point, o.chop);
            }
            if (!o.on && !swinging && now - o.lastSwing > 20) OTHERS.remove(p.getId());
        }
        if (!OTHERS.isEmpty()) OTHERS.keySet().removeIf(id -> mc.level.getEntity(id) == null);
    }

    /** Their look falls on a block their weapon cuts (into o's pos, state, face and point). */
    private static boolean cuts(Minecraft mc, Player p, Other o) {
        HitResult hit = p.pick(p.blockInteractionRange(), 1F, false);
        if (!(hit instanceof BlockHitResult b) || b.getType() != HitResult.Type.BLOCK) return false;
        BlockState state = mc.level.getBlockState(b.getBlockPos());
        if (state.isAir() || p.getMainHandItem().getDestroySpeed(state) <= 1F) return false;
        o.pos = b.getBlockPos();
        o.state = state;
        o.face = b.getDirection();
        o.point = b.getLocation();
        return true;
    }

    static void reset() {
        on = wanted = false;
        bodySynced = false;
        at = before = wantPos = null;
        cutState = null;
        phase = phaseO = 0F;
        rate = 1F;
        OTHERS.clear();
    }
}
