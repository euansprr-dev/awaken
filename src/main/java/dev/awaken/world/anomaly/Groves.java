package dev.awaken.world.anomaly;

import dev.awaken.Awaken;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.Playtest;
import dev.awaken.core.api.GateHooks;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import dev.awaken.world.landmark.Landmarks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Shimmer groves, mostly by day and always peaceful: a patch of air that shimmers 32–72 blocks from the player. Step
 * into it and the rules bend there for {@link #DURATION} ticks, one way per grove ({@link GroveVariant}): light steps
 * (low gravity, no fall damage), ore sight (ore glows through the stone, drawn by the client), a whispering spirit (a
 * hint toward a landmark worth finding) or healing flowers. Stepping in pays a discovery the first time and a little
 * world XP. Nobody comes within {@link #WAIT} ticks: it fades.
 */
final class Groves {
    private Groves() {}

    public static final double RADIUS = 7.0;
    static final int WAIT = 5 * Pacing.MINUTE;
    static final int DURATION = 150 * 20;
    static final int START_MAX = 72;
    static final int FLOWERS = 14;
    /** Only players this close (vertically) to the grove's heart count as standing in it. */
    static final double HEIGHT = 8.0;
    static final Identifier LIGHT_GRAVITY = Awaken.id("grove_light_gravity");
    static final Identifier LIGHT_FALL = Awaken.id("grove_light_fall");
    static final double GRAVITY_FACTOR = -0.65;

    private static final Block[] BLOOMS = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.ALLIUM, Blocks.AZURE_BLUET,
            Blocks.LILY_OF_THE_VALLEY, Blocks.PINK_TULIP};

    private static final String[] LORE = {
            "“The Signal chose you. It does not choose often.”",
            "“Something old sleeps under the world. It dreams of the surface.”",
            "“Light keeps them back. It always has.”",
            "“Walk where the stars fall. They remember the sky.”"};

    static GroveVariant pickVariant(RandomSource r, String last) {
        GroveVariant[] all = GroveVariant.values();
        GroveVariant v = all[r.nextInt(all.length)];
        if (v.id.equals(last)) v = all[(v.ordinal() + 1 + r.nextInt(all.length - 1)) % all.length];
        return v;
    }

    static Anomaly open(ServerLevel level, ServerPlayer owner, BlockPos ground, AnomalyData data, GroveVariant variant) {
        long now = level.getGameTime();
        Anomaly a = new Anomaly(data.allocateId(), AnomalyKind.SHIMMER_GROVE, owner.getUUID(), ground, now, now + WAIT);
        a.variant = variant.ordinal();
        data.add(a);
        owner.getData(AnomalyContent.MEMORY).lastGrove = variant.id;
        ensureShimmer(level, a);
        BlockPos c = a.focus();
        level.playSound(null, c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, AnomalyContent.GROVE.get(), SoundSource.AMBIENT, 2.0F, 1.0F);
        return a;
    }

    static GroveVariant variant(Anomaly a) {
        return GroveVariant.of(a.variant);
    }

    static boolean owns(ServerLevel level, int anomalyId, UUID entity) {
        Anomaly a = AnomalyData.get(level.getServer()).find(anomalyId);
        return a != null && a.kind == AnomalyKind.SHIMMER_GROVE && entity.equals(a.entity);
    }

    static @Nullable ShimmerEntity shimmer(ServerLevel level, Anomaly a) {
        if (a.entity == null) return null;
        return level.getEntity(a.entity) instanceof ShimmerEntity s && !s.isRemoved() ? s : null;
    }

    /** Puts the shimmer back if its chunk unloaded and came back (it is never saved). */
    static void ensureShimmer(ServerLevel level, Anomaly a) {
        BlockPos c = a.focus();
        if (!level.isPositionEntityTicking(c) || shimmer(level, a) != null) return;
        ShimmerEntity s = AnomalyContent.SHIMMER.get().create(level, EntitySpawnReason.TRIGGERED);
        if (s == null) return;
        s.snapTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 0.0F, 0.0F);
        s.setup(a.id, variant(a));
        s.setActive(a.stage == Anomaly.ACTIVE);
        level.addFreshEntity(s);
        a.entity = s.getUUID();
    }

    /** Whether {@code p} stands in the grove (horizontally within {@code r} of its heart, not far above or below). */
    static boolean inside(ServerPlayer p, Anomaly a, double r) {
        BlockPos c = a.focus();
        double dx = p.getX() - (c.getX() + 0.5), dz = p.getZ() - (c.getZ() + 0.5);
        return dx * dx + dz * dz <= r * r && Math.abs(p.getY() - c.getY()) <= HEIGHT;
    }

    /** One director pass (every 10 ticks). Returns why it ended, or null while it goes on. */
    static @Nullable String update(ServerLevel level, AnomalyData data, Anomaly a, long now) {
        ensureShimmer(level, a);
        if (a.stage != Anomaly.ACTIVE) {
            if (now >= a.endsAt) return "nobody stepped in";
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(a.owner);
            if (owner != null && !a.discovered && owner.level() == level && Spots.horizontal(owner.blockPosition(), a.focus()) <= Rifts.SEEN) {
                a.discovered = true;
                data.setDirty();
                AnomalyDirector.discovered(owner, a.kind);
            }
            for (ServerPlayer p : level.players()) {
                if (p.isAlive() && !p.isSpectator() && inside(p, a, RADIUS)) {
                    activate(level, data, a, p, now);
                    break;
                }
            }
            return null;
        }
        if (now >= a.endsAt) return "the shimmer faded";
        bend(level, a, now);
        return null;
    }

    /** Someone stepped in: the rules bend for {@link #DURATION}. */
    static void activate(ServerLevel level, AnomalyData data, Anomaly a, ServerPlayer by, long now) {
        a.stage = Anomaly.ACTIVE;
        a.endsAt = now + DURATION;
        data.setDirty();
        ShimmerEntity s = shimmer(level, a);
        if (s != null) s.setActive(true);
        GroveVariant v = variant(a);
        BlockPos c = a.focus();
        level.playSound(null, c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, AnomalyContent.GROVE.get(), SoundSource.AMBIENT, 2.0F, 1.4F);
        if (!a.discovered && by.getUUID().equals(a.owner)) a.discovered = true;
        AnomalyDirector.discovered(by, a.kind);
        Progression.worldXp(by, "anomaly", 0.04);
        Progression.count(by, "awaken:groves_visited");
        by.getData(AnomalyContent.MEMORY).groves++;
        Signal.send(by, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Shimmer grove · " + v.label))
                .body(Component.literal(v.blurb + " · " + clock(DURATION)))
                .presentation(SignalWindow.Presentation.TOAST).ttl(20 * 6).build());
        switch (v) {
            case BLOOM -> plantFlowers(level, a, level.getRandom());
            case WHISPER -> whisper(level, a, by);
            default -> { }
        }
        DirectorLog.log(by, "anomaly", "shimmer grove #" + a.id + " (" + v.id + ") woke: the rules bend for " + clock(DURATION));
        Playtest.log(by, "anomaly_closed", "kind", a.kind.id);
    }

    /** Applies the grove's rule to whoever stands in it right now. */
    static void bend(ServerLevel level, Anomaly a, long now) {
        GroveVariant v = variant(a);
        switch (v) {
            case LIGHTFOOT -> {
                for (ServerPlayer p : level.players()) {
                    boolean was = a.touched.contains(p.getUUID());
                    boolean in = p.isAlive() && !p.isSpectator() && inside(p, a, was ? RADIUS + 2 : RADIUS);
                    if (in) {
                        lighten(p);
                        a.touched.add(p.getUUID());
                    } else if (was) {
                        settle(p);
                        a.touched.remove(p.getUUID());
                    }
                }
                // anyone who left the level (logged out, changed dimension) keeps nothing: the modifiers were transient
                for (Iterator<UUID> it = a.touched.iterator(); it.hasNext(); ) {
                    UUID id = it.next();
                    ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
                    if (p == null || p.level() != level) {
                        if (p != null) settle(p);
                        it.remove();
                    }
                }
            }
            case BLOOM -> {
                if ((now / AnomalyDirector.UPDATE) % 4 != 0) return; // every 2 s
                for (ServerPlayer p : level.players()) {
                    if (!p.isAlive() || p.isSpectator() || !inside(p, a, RADIUS)) continue;
                    p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 1, true, true));
                    if (p.getHealth() < p.getMaxHealth()) {
                        level.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 2.0, p.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
                    }
                }
            }
            default -> { }
        }
    }

    /** Low gravity and a soft landing (transient: never saved, gone on relog). */
    static void lighten(ServerPlayer p) {
        AttributeInstance g = p.getAttribute(Attributes.GRAVITY);
        if (g != null && !g.hasModifier(LIGHT_GRAVITY)) {
            g.addTransientModifier(new AttributeModifier(LIGHT_GRAVITY, GRAVITY_FACTOR, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        AttributeInstance f = p.getAttribute(Attributes.SAFE_FALL_DISTANCE);
        if (f != null && !f.hasModifier(LIGHT_FALL)) f.addTransientModifier(new AttributeModifier(LIGHT_FALL, 16.0, AttributeModifier.Operation.ADD_VALUE));
    }

    /** Back to normal weight, gently: a few seconds of slow falling so leaving mid-jump never hurts. */
    static void settle(ServerPlayer p) {
        boolean had = false;
        AttributeInstance g = p.getAttribute(Attributes.GRAVITY);
        if (g != null && g.hasModifier(LIGHT_GRAVITY)) {
            g.removeModifier(LIGHT_GRAVITY);
            had = true;
        }
        AttributeInstance f = p.getAttribute(Attributes.SAFE_FALL_DISTANCE);
        if (f != null && f.hasModifier(LIGHT_FALL)) {
            f.removeModifier(LIGHT_FALL);
            had = true;
        }
        if (had) {
            p.resetFallDistance();
            if (!p.onGround()) p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, true, false));
        }
    }

    static boolean lightened(ServerPlayer p) {
        AttributeInstance g = p.getAttribute(Attributes.GRAVITY);
        return g != null && g.hasModifier(LIGHT_GRAVITY);
    }

    /** Wildflowers spring up around the heart (and stay when the grove fades: a mark it was here). Returns how many. */
    static int plantFlowers(ServerLevel level, Anomaly a, RandomSource r) {
        BlockPos c = a.pos;
        int placed = 0;
        for (int i = 0; i < 48 && placed < FLOWERS; i++) {
            double ang = r.nextDouble() * Math.PI * 2, d = 1.2 + r.nextDouble() * (RADIUS - 2.0);
            int x = c.getX() + (int) Math.round(Math.cos(ang) * d), z = c.getZ() + (int) Math.round(Math.sin(ang) * d);
            if (!level.hasChunkAt(x, z)) continue;
            BlockPos at = Spots.surface(level, x, z).above();
            if (Math.abs(at.getY() - c.getY() - 1) > 3 || !level.getBlockState(at).isAir()) continue;
            BlockState f = BLOOMS[r.nextInt(BLOOMS.length)].defaultBlockState();
            if (!f.canSurvive(level, at)) continue;
            level.setBlock(at, f, 3);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.getX() + 0.5, at.getY() + 0.4, at.getZ() + 0.5, 4, 0.25, 0.2, 0.25, 0.0);
            placed++;
        }
        return placed;
    }

    /** The spirit speaks: where something worth finding lies (a landmark you have a reason to visit, else a gate), else old words. */
    static String whisper(ServerLevel level, Anomaly a, ServerPlayer p) {
        a.hinted = true;
        String said = Landmarks.whisper(p).orElse(null);
        if (said == null) said = gateHint(level, p).orElse(null);
        if (said == null) said = LORE[level.getRandom().nextInt(LORE.length)];
        BlockPos c = a.focus();
        level.playSound(null, c.getX() + 0.5, c.getY() + 1.0, c.getZ() + 0.5, AnomalyContent.GROVE_WHISPER.get(), SoundSource.AMBIENT, 1.5F, 1.0F);
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("A spirit whispers"))
                .body(Component.literal(said)).presentation(SignalWindow.Presentation.TOAST).ttl(20 * 10).build());
        DirectorLog.log(p, "anomaly", "shimmer grove #" + a.id + ": the spirit said " + said);
        return said;
    }

    static Optional<String> gateHint(ServerLevel level, ServerPlayer p) {
        BlockPos me = p.blockPosition();
        List<GateHooks.GateView> gates = GateHooks.near(level, me, 1500).stream()
                .filter(g -> !"CLEARED".equals(g.state()) && !"RELEASED".equals(g.state()) && !"BROKEN".equals(g.state()))
                .sorted(Comparator.comparingDouble(g -> g.pos().distSqr(me))).toList();
        if (gates.isEmpty()) return Optional.empty();
        GateHooks.GateView g = gates.get(0);
        return Optional.of("“A " + Rank.of(g.rank()).label + "-rank gate, " + Spots.where(me, g.pos()) + ".”");
    }

    /** Ended: everything the grove changed goes back (the flowers stay), and the shimmer thins out. */
    static void cleanup(ServerLevel level, Anomaly a) {
        for (UUID id : a.touched) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) settle(p);
        }
        a.touched.clear();
        ShimmerEntity s = shimmer(level, a);
        if (s != null) {
            if (level.isPositionEntityTicking(s.blockPosition())) s.fade();
            else s.discard();
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(a.owner);
        if (owner != null && a.stage == Anomaly.ACTIVE && owner.level() == level && inside(owner, a, RADIUS + 4)) {
            Signal.send(owner, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("The shimmer fades"))
                    .body(Component.literal("The rules are the rules again")).presentation(SignalWindow.Presentation.TOAST).ttl(20 * 4).build());
        }
    }

    /** "2:30". */
    static String clock(long ticks) {
        long s = Math.max(0, ticks / 20);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }
}
