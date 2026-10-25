package dev.awaken.body;

import dev.awaken.body.net.BodyPayloads;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobRole;
import dev.awaken.progression.Perks;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import dev.awaken.veil.Veil;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The server half of the Awakened Body. The client predicts every move; here each start is checked (unlocked, not
 * riding / sleeping / projecting / meditating, cooldowns, and the geometry: the ledge is real and in reach, the space
 * is free), paid for (exhaustion), shown to everyone else, and refused with a correction when it doesn't hold. The one
 * fall-event path for the soft landing, the momentum hits and the slide's trip live here too.
 */
final class BodyServer {
    private BodyServer() {}


    /** The client's half of the one fall path (its own landing roll), installed by the client init. */
    interface ClientFall {
        void fell(Player player, double distance);
    }

    static @Nullable ClientFall clientFall;

    // the swing being resolved and a momentum hit's extra knockback waiting for that hit's own knockback event (the
    // server resolves one attack at a time on one thread)
    private static int swingAttacker = -1, swingTarget = -1, knockTarget = -1;
    private static long swingTick, knockTick;
    private static float knockExtra;

    static long now(Player p) {
        return p.level().getServer().overworld().getGameTime();
    }

    static BodyMotion motion(ServerPlayer p) {
        return p.getData(BodyContent.MOTION);
    }

    /** No move starts (and a running one ends) while riding, sleeping, out of the body, meditating, flying or gliding. */
    static boolean off(ServerPlayer p) {
        return p.isPassenger() || p.isSleeping() || p.isSpectator() || !p.isAlive() || p.getAbilities().flying || p.isFallFlying()
                || Veil.isSpirit(p) || Veil.isMeditating(p);
    }

    // ---- payloads ---------------------------------------------------------------------------------------------------

    static void onStart(ServerPlayer p, BodyPayloads.Start msg) {
        Move move = Move.byId(msg.move());
        if (move == null) return;
        boolean ok = switch (move) {
            case TACTICAL_SPRINT -> startTactical(p);
            case SLIDE -> startSlide(p, msg.yaw(), msg.flag());
            case MANTLE -> startMantle(p, msg.yaw(), msg.x(), msg.y(), msg.z());
            default -> false; // ROLL, and the Runner's Kit (its own Run payload)
        };
        if (!ok) refuse(p, move);
    }

    static void onEnd(ServerPlayer p, BodyPayloads.End msg) {
        BodyMotion m = p.getExistingDataOrNull(BodyContent.MOTION);
        Move move = Move.byId(msg.move());
        if (m == null || move == null) return;
        long now = now(p);
        if (move == Move.TACTICAL_SPRINT) endTactical(p, m, now, false);
        else if (move == Move.SLIDE) endSlide(p, m, now, false);
        else if (move.runner()) RunnerServer.onEnd(p, move);
    }

    /** Undo what the client predicted: stop a slide where the server has it, put a refused mantle back where it began. */
    static void refuse(ServerPlayer p, Move move) {
        AwakenNet.send(p, new BodyPayloads.Refuse(move.ordinal()));
        if (p.connection == null) return;
        if (move == Move.MANTLE) {
            p.connection.teleport(p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
        } else if (move == Move.SLIDE) {
            Vec3 v = new Vec3(0, p.getDeltaMovement().y, 0);
            p.setDeltaMovement(v);
            p.connection.send(new ClientboundSetEntityMotionPacket(p.getId(), v));
        }
    }

    // ---- tactical sprint --------------------------------------------------------------------------------------------

    static boolean startTactical(ServerPlayer p) {
        if (!Body.unlocked(p, Move.TACTICAL_SPRINT) || off(p) || p.isInLiquid()) return false;
        BodyMotion m = motion(p);
        long now = now(p);
        if (m.tacUntil != 0) return true;
        if (now < m.tacReadyAt || m.slideUntil != 0 || m.mantleUntil != 0) return false;
        if (!m.assisted(Move.TACTICAL_SPRINT, now) && !p.isSprinting()) return false;
        if (!p.isCreative() && p.getFoodData().getFoodLevel() <= 6) return false; // too hungry to sprint at all
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.addOrUpdateTransientModifier(BodyRules.TAC_MODIFIER);
        m.tacUntil = now + BodyRules.TAC_TICKS;
        m.tacNoSprint = 0;
        p.causeFoodExhaustion(BodyRules.TAC_EXHAUSTION);
        did(p, m, Move.TACTICAL_SPRINT, p.getYRot(), BodyContent.SPRINT, 0.7F, now);
        return true;
    }

    /** Ends a tactical sprint (time, a stop, a swing, a cast, a slide); tellClient when the client may not know yet. */
    static void endTactical(ServerPlayer p, BodyMotion m, long now, boolean tellClient) {
        if (m.tacUntil == 0) return;
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(BodyRules.TAC_ID);
        m.tacUntil = 0;
        m.tacReadyAt = now + BodyRules.tacRecharge(p);
        m.tacEndedAt = now;
        m.lastMove = now;
        if (tellClient) AwakenNet.send(p, new BodyPayloads.Refuse(Move.TACTICAL_SPRINT.ordinal()));
    }

    // ---- slide ------------------------------------------------------------------------------------------------------

    static boolean startSlide(ServerPlayer p, float yaw, boolean claimTactical) {
        if (!Body.unlocked(p, Move.SLIDE) || off(p) || p.isInLiquid()) return false;
        BodyMotion m = motion(p);
        long now = now(p);
        if (m.slideUntil != 0 || m.mantleUntil != 0 || now < m.slideReadyAt) return false;
        boolean assisted = m.assisted(Move.SLIDE, now);
        if (!assisted && !p.isSprinting() && m.tacUntil == 0) return false;
        if (!assisted && !p.onGround()) return false;
        if (!BodyRules.crawlFits(p.level(), CollisionContext.of(p), p.getX(), p.getY(), p.getZ())) return false;
        boolean fromTac = m.tacUntil != 0 || now - m.tacEndedAt <= 3;
        endTactical(p, m, now, false); // the slide takes over from the run
        m.slideUntil = now + BodyRules.SLIDE_TICKS + BodyRules.SLIDE_GRACE;
        m.slideStart = now;
        m.slideDirX = -Mth.sin(yaw * Mth.DEG_TO_RAD);
        m.slideDirZ = Mth.cos(yaw * Mth.DEG_TO_RAD);
        m.slideFromTac = fromTac && claimTactical;
        m.trippedCount = 0;
        // the crawl-height box on this side too, so gaps the client slides under are gaps here (and others see it)
        p.setForcedPose(Pose.SWIMMING);
        p.setPose(Pose.SWIMMING);
        p.causeFoodExhaustion(BodyRules.SLIDE_EXHAUSTION);
        if (assisted) m.assistMove = -1;
        did(p, m, Move.SLIDE, yaw, BodyContent.SLIDE, 0.85F, now);
        return true;
    }

    static void endSlide(ServerPlayer p, BodyMotion m, long now, boolean tellClient) {
        if (m.slideUntil == 0) return;
        m.slideUntil = 0;
        m.slideReadyAt = now + BodyRules.slideCooldown(Body.runnerRank(p));
        m.lastMove = now;
        // vanilla picks the pose again next tick: still crawling when the slide ended under a gap
        if (p.getForcedPose() == Pose.SWIMMING) p.setForcedPose(null);
        if (tellClient) AwakenNet.send(p, new BodyPayloads.Refuse(Move.SLIDE.ordinal()));
    }

    /** Small hostile creatures in the slide's way lose their footing: a stagger, never damage. */
    static int trip(ServerPlayer p, BodyMotion m) {
        AABB box = p.getBoundingBox().move(m.slideDirX * 0.4, 0, m.slideDirZ * 0.4).inflate(0.25, 0.2, 0.25);
        int n = 0;
        for (Mob mob : p.level().getEntitiesOfClass(Mob.class, box, e -> trippable(p, e))) {
            if (m.tripped(mob.getId())) continue;
            m.markTripped(mob.getId());
            mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, BodyRules.TRIP_TICKS, 5, false, false, false));
            mob.getNavigation().stop();
            // legs swept out along the slide: a short hop forward and the stagger above
            Vec3 v = mob.getDeltaMovement();
            mob.setDeltaMovement(v.x * 0.3 + m.slideDirX * 0.32, Math.max(v.y, 0.24), v.z * 0.3 + m.slideDirZ * 0.32);
            mob.needsSync = true;
            p.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), BodyContent.IMPACT, SoundSource.PLAYERS, 0.6F, 1.35F);
            show(mob, null, new BodyPayloads.Seen(mob.getId(), BodyPayloads.SEEN_TRIP, p.getYRot()));
            n++;
        }
        return n;
    }

    static boolean trippable(ServerPlayer p, Mob e) {
        if (!(e instanceof Enemy) || !e.isAlive() || e.getBbHeight() > BodyRules.TRIP_MAX_HEIGHT + 1e-6) return false;
        if (e.is(Tags.EntityTypes.BOSSES)) return false;
        MobRole role = e.getExistingDataOrNull(CoreApi.MOB_ROLE);
        return role == null || switch (role.role()) {
            case ECHO, BOSS, BREAK, WARDEN -> false;
            default -> true;
        };
    }

    // ---- mantle -----------------------------------------------------------------------------------------------------

    static boolean startMantle(ServerPlayer p, float yaw, double x, double y, double z) {
        if (!Body.unlocked(p, Move.MANTLE) || off(p) || p.isInLiquid() || p.isUsingItem() || p.isBlocking()) return false;
        BodyMotion m = motion(p);
        long now = now(p);
        if (m.mantleUntil != 0 || m.slideUntil != 0) return false;
        BodyRules.Ledge l = BodyRules.findLedge(p.level(), CollisionContext.of(p), p.getX(), p.getY(), p.getZ(), yaw, BodyRules.MANTLE_REACH_SERVER);
        if (l == null) return false;
        double ddx = l.x() - x, ddz = l.z() - z;
        if (Math.abs(l.y() - y) > 0.25 || ddx * ddx + ddz * ddz > 1.0) return false; // not the ledge the client climbed
        m.mantleUntil = now + BodyRules.MANTLE_TICKS + 4;
        p.causeFoodExhaustion(BodyRules.MANTLE_EXHAUSTION);
        p.resetFallDistance();
        did(p, m, Move.MANTLE, yaw, BodyContent.MANTLE, 0.8F, now);
        return true;
    }

    // ---- shared -----------------------------------------------------------------------------------------------------

    /** A move happened: count it, let everyone else see and hear it (the mover's own client already did). */
    static void did(ServerPlayer p, BodyMotion m, Move move, float yaw, Holder<SoundEvent> sound, float volume, long now) {
        m.lastMove = now;
        Progression.count(p, "awaken:body_" + move.key);
        // the Runner's moves are links in the Ascent's chains and earn the Runner's mastery
        switch (move) {
            case WALL_RUN, WALL_JUMP, LEDGE_JUMP, WALL_CLIMB -> dev.awaken.body.ascent.AscentServer.link(p, dev.awaken.body.ascent.AscentMemory.Track.RUNNER, 2);
            case LEDGE_HANG -> dev.awaken.body.ascent.AscentServer.link(p, dev.awaken.body.ascent.AscentMemory.Track.RUNNER, 1);
            default -> {
            }
        }
        p.level().playSound(p, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, 1.0F);
        show(p, p, new BodyPayloads.Seen(p.getId(), move.ordinal(), yaw));
    }

    /** A Runner move that others see as one of its clips (the variant: which side the wall is on, which way you go). */
    static void didClip(ServerPlayer p, BodyMotion m, Move move, float yaw, Holder<SoundEvent> sound, float volume, long now, String clip) {
        m.lastMove = now;
        Progression.count(p, "awaken:body_" + move.key);
        // the Runner's moves are links in the Ascent's chains and earn the Runner's mastery
        switch (move) {
            case WALL_RUN, WALL_JUMP, LEDGE_JUMP -> dev.awaken.body.ascent.AscentServer.link(p, dev.awaken.body.ascent.AscentMemory.Track.RUNNER, 2);
            case LEDGE_HANG -> dev.awaken.body.ascent.AscentServer.link(p, dev.awaken.body.ascent.AscentMemory.Track.RUNNER, 1);
            default -> {
            }
        }
        p.level().playSound(p, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, 1.0F);
        showClip(p, clip, yaw);
    }

    /** Others see this player's Runner clip change (no count, no sound: the hang settling after a catch or a shimmy). */
    static void showClip(ServerPlayer p, String clip, float yaw) {
        int i = BodyClips.RUNNER.indexOf(clip);
        if (i >= 0) show(p, p, new BodyPayloads.Seen(p.getId(), BodyPayloads.SEEN_CLIP + i, yaw));
    }

    /** Others see this player's Runner move end (its loop settles). */
    static void showRunnerEnd(ServerPlayer p) {
        show(p, p, new BodyPayloads.Seen(p.getId(), BodyPayloads.SEEN_RUNNER_END, p.getYRot()));
    }

    /** Sends to the players near enough to see it (through AwakenNet: test players and other mods' fakes can't take it). */
    static void show(Entity at, @Nullable ServerPlayer except, BodyPayloads.Seen msg) {
        for (ServerPlayer o : ((net.minecraft.server.level.ServerLevel) at.level()).players()) {
            if (o != except && o.distanceToSqr(at) < SEEN_RANGE * SEEN_RANGE) AwakenNet.send(o, msg);
        }
    }

    private static final double SEEN_RANGE = 96;

    // ---- ticking ----------------------------------------------------------------------------------------------------

    static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        // spike S3: every player keeps a short position history (the runner's claimed starts are checked against it)
        BodyMotion m = motion(p);
        RunnerServer.track(p, m);
        if (p.isFallFlying()) m.lastGlide = now(p);
        if (!m.active()) return;
        tick(p, m, now(p));
    }

    /** One tick of the moves in progress (also driven by GameTests, whose players the server doesn't tick). */
    static void tick(ServerPlayer p, BodyMotion m, long now) {
        if (m.tacUntil != 0) {
            if (now >= m.tacUntil || off(p) || p.isInLiquid() || p.isUsingItem()) {
                endTactical(p, m, now, true);
            } else {
                m.tacNoSprint = p.isSprinting() ? 0 : m.tacNoSprint + 1;
                if (m.tacNoSprint > 2) endTactical(p, m, now, true);
                else m.lastMove = now;
            }
        }
        if (m.slideUntil != 0) {
            if (now >= m.slideUntil || off(p) || p.isInLiquid()) {
                endSlide(p, m, now, true);
            } else {
                m.lastMove = now;
                if (((now - m.slideStart) & 1) == 0) trip(p, m);
            }
        }
        if (m.mantleUntil != 0) {
            p.resetFallDistance();
            if (now >= m.mantleUntil) m.mantleUntil = 0;
            else m.lastMove = now;
        }
        if (m.runner()) RunnerServer.tick(p, m, now);
    }

    // ---- soft landing: the one fall path ----------------------------------------------------------------------------

    /**
     * Runs after the other fall rules (LOW priority) and never sees a cancelled fall, so a power's own landing (Hover,
     * Glide, Flight, Dash: their no-fall window cancels it) is never a roll. Riding (mounts handle their passengers),
     * elytra and spectating are skipped; where another rule already gives at least the roll's safe fall (gate Low
     * Gravity, jet boots) or a perk already softens it more, the kinder rule wins and nothing adds up.
     */
    static void onFall(LivingFallEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) {
            if (clientFall != null) clientFall.fell(player, e.getDistance());
            return;
        }
        if (!(player instanceof ServerPlayer p)) return;
        double distance = e.getDistance();
        if (distance < BodyRules.ROLL_MIN_FALL || !Body.unlocked(p, Move.ROLL) || off(p)) return;
        long now = now(p);
        BodyMotion m = p.getExistingDataOrNull(BodyContent.MOTION);
        if (m != null && now - m.lastGlide < 10) return; // an elytra landing
        boolean assisted = m != null && m.assisted(Move.ROLL, now);
        if (!assisted && !p.isSprinting() && !p.isShiftKeyDown()) return;
        AttributeInstance safe = p.getAttribute(Attributes.SAFE_FALL_DISTANCE);
        double otherSafe = safe == null ? 0 : safe.getValue() - safe.getBaseValue();
        double perk = 1 - Mth.clamp(Progression.perk(p, Perks.FALL_RESIST), 0, 1);
        double[] r = BodyRules.roll(distance, otherSafe, perk);
        if (r == null) return;
        e.setDistance(r[0]);
        e.setDamageMultiplier((float) (e.getDamageMultiplier() * r[1]));
        BodyMotion mm = motion(p);
        if (assisted) mm.assistMove = -1;
        did(p, mm, Move.ROLL, p.getYRot(), BodyContent.ROLL, 0.9F, now);
    }

    // ---- momentum hits ----------------------------------------------------------------------------------------------

    /** A swing ends a tactical sprint and lands as normal; remember its target so sweeps get no momentum. */
    static void onAttack(AttackEntityEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        long now = now(p);
        BodyMotion m = p.getExistingDataOrNull(BodyContent.MOTION);
        if (m != null) endTactical(p, m, now, true);
        swingAttacker = p.getId();
        swingTarget = e.getTarget().getId();
        swingTick = now;
    }

    static void onIncomingDamage(LivingIncomingDamageEvent e) {
        DamageSource src = e.getSource();
        if (!(src.getEntity() instanceof ServerPlayer p) || src.getDirectEntity() != p || !src.is(DamageTypes.PLAYER_ATTACK)) return;
        LivingEntity target = e.getEntity();
        long now = now(p);
        if (swingAttacker != p.getId() || swingTarget != target.getId() || swingTick != now) return;
        swingTarget = -1;
        double bonus = momentumBonus(p, p.getAttackStrengthScale(0.5F));
        if (bonus <= 0) return;
        e.setAmount((float) (e.getAmount() * (1 + bonus)));
        knockTarget = target.getId();
        knockTick = now;
        knockExtra = (float) (bonus / BodyRules.MOMENTUM_MAX * BodyRules.MOMENTUM_KNOCK);
        BodyMotion m = motion(p);
        m.lastMove = now;
        p.level().playSound(null, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), BodyContent.IMPACT,
                SoundSource.PLAYERS, (float) (0.5 + bonus * 1.5), (float) (1.1 - bonus));
        show(target, null, new BodyPayloads.Seen(target.getId(), BodyPayloads.SEEN_MOMENTUM, p.getYRot()));
    }

    /** The share a full swing from this player gains right now: running or sliding, by their horizontal speed. */
    static double momentumBonus(ServerPlayer p, float strength) {
        if (!Body.unlocked(p, Move.ROLL) || strength < BodyRules.MOMENTUM_MIN_STRENGTH) return 0;
        if (!p.isSprinting() && !Body.isSliding(p)) return 0;
        return BodyRules.momentum(p.getKnownMovement().horizontalDistance());
    }

    static void onKnockBack(LivingKnockBackEvent e) {
        LivingEntity t = e.getEntity();
        if (t.getId() != knockTarget || t.level().isClientSide() || t.level().getServer().overworld().getGameTime() != knockTick) return;
        knockTarget = -1; // once: the hit's own knockback, not the sprint-hit extra right after it
        e.setStrength(e.getStrength() + knockExtra);
    }

    // ---- unlocks ----------------------------------------------------------------------------------------------------

    /**
     * Announces every move the player has and was never told about, as one toast. It goes out in the same tick as
     * the awakening or the rank-up, so it joins that moment's burst ("N new things · K") instead of standing alone.
     * Returns whether anything was announced.
     */
    static boolean announce(ServerPlayer p) {
        // the System speaks from the awakening on; a Runner's body just moves before it
        if (!dev.awaken.progression.Progression.profile(p).awakened()) return false;
        BodyMemory mem = p.getData(BodyContent.MEMORY);
        List<Move> fresh = new ArrayList<>(2);
        for (Move mv : Move.values()) {
            if (!mv.runner() && Body.unlocked(p, mv) && !mem.announced(mv)) { // the Runner's own moves are found, not told
                fresh.add(mv);
                mem = mem.with(mv);
            }
        }
        if (fresh.isEmpty()) return false;
        p.setData(BodyContent.MEMORY, mem);
        String title = fresh.size() == 1 ? fresh.get(0).title : fresh.contains(Move.ROLL) ? "Your body remembers" : "Your body learns";
        StringBuilder body = new StringBuilder();
        for (Move mv : fresh) body.append(body.isEmpty() ? "" : " · ").append(fresh.size() == 1 ? mv.how : mv.hint);
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(title)).body(Component.literal(body.toString()))
                .presentation(SignalWindow.Presentation.TOAST).ttl(20 * 6).build());
        dev.awaken.core.DirectorLog.log(p, "body", "announced " + fresh);
        return true;
    }

    /** The dev command: the next move of this kind skips its input checks (the client presses its keys). */
    static void assist(ServerPlayer p, @Nullable Move move, int ticks) {
        BodyMotion m = motion(p);
        m.assistMove = move == null ? -1 : move.ordinal();
        m.assistUntil = now(p) + ticks;
    }
}
