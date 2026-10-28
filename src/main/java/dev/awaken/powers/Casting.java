package dev.awaken.powers;

import dev.awaken.core.AwakenNet;
import dev.awaken.powers.content.PowerContent;
import dev.awaken.powers.fx.CastProfiles;
import dev.awaken.powers.net.PowerPayloads;
import dev.awaken.progression.Counters;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Stat;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The server cast pipeline: validates a key press (owned, slot, cooldown, energy, state), runs the power, spends
 * energy, starts cooldowns, counts uses; ticks running powers, energy regen, passives and dash chains.
 */
public final class Casting {
    private Casting() {}

    public static final String[] SLOT_LABELS = {"Z", "X", "C", "V", "R", "Alt"};

    public static String slotLabel(int slot) {
        return slot >= 0 && slot < SLOT_LABELS.length ? SLOT_LABELS[slot] : "?";
    }

    static long now(ServerPlayer p) {
        return p.level().getServer().overworld().getGameTime();
    }

    // ---- key presses ------------------------------------------------------------------------------------------------

    static void onPayload(ServerPlayer player, PowerPayloads.Cast msg) {
        if (msg.slot() < 0 || msg.slot() >= Loadout.SLOTS) return;
        String id = Powers.loadout(player).slot(msg.slot());
        PowerType type = PowerRegistry.get(id);
        if (msg.phase() == PowerPayloads.RELEASE) {
            if (type != null) release(player, type);
            return;
        }
        if (type == null) {
            feedback(player, msg.slot(), "", "empty", msg.predicted());
            return;
        }
        PowerType.Result r = cast(player, type, msg.slot(), msg.predicted(), msg.yRot(), msg.xRot(),
                PowerPayloads.strafeOf(msg.keys()), PowerPayloads.forwardOf(msg.keys()));
        if (!r.ok()) feedback(player, msg.slot(), type.id.toString(), r.reason(), msg.predicted());
    }

    static void feedback(ServerPlayer player, int slot, String power, String reason, boolean undo) {
        if (reason.isEmpty()) return;
        AwakenNet.send(player, new PowerPayloads.Feedback(slot, power, reason, undo));
        if (undo) {
            // Undo the client's predicted motion: stop it where the server thinks it is.
            player.setDeltaMovement(Vec3.ZERO);
            if (player.connection != null) player.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), Vec3.ZERO));
        }
        if (!reason.equals("busy") && player.connection != null) {
            // only the caster hears their own fizzle (others nearby would hear every cooldown press)
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(PowerSounds.FAIL, SoundSource.PLAYERS,
                    player.getX(), player.getY(), player.getZ(), 0.35f, 1f, player.getRandom().nextLong()));
        }
    }

    /** Cast a power the player owns. Used by the key payload, commands and tests. */
    public static PowerType.Result cast(ServerPlayer player, PowerType type, int slot, boolean predicted, float yRot, float xRot) {
        net.minecraft.world.entity.player.Input in = player.getLastClientInput();
        return cast(player, type, slot, predicted, yRot, xRot, Cast.strafeOf(in), Cast.forwardOf(in));
    }

    /** As above, with the movement keys held at the press (+1 left / -1 right, +1 forward / -1 back). */
    public static PowerType.Result cast(ServerPlayer player, PowerType type, int slot, boolean predicted, float yRot, float xRot,
                                        float strafe, float forward) {
        Loadout l = Powers.loadout(player);
        String id = type.id.toString();
        Loadout.Owned owned = l.get(id);
        if (owned == null) return PowerType.Result.fail("not_owned");
        if (type.isPassive() || type.behaviour == null) return PowerType.Result.fail("passive");
        if (player.isSpectator() || !player.isAlive()) return PowerType.Result.fail("cannot");
        dev.awaken.core.api.MovementHooks.castStarting(player); // a cast ends a tactical sprint; it is never held back
        dev.awaken.core.api.TipHooks.done(player, dev.awaken.Awaken.id("first/power"));
        PowerState st = PowerState.of(player);
        long now = now(player);
        PowerState.FollowUp follow = st.followUps.remove(id);
        if (follow != null && follow.until() >= now) {
            // a ladder step's second press: free, and the cooldown that waited for it starts now
            try {
                follow.run().run();
            } catch (RuntimeException e) {
                dev.awaken.Awaken.LOG.error("Power {} follow-up failed", id, e);
            }
            startCooldown(player, l, type, owned, st);
            Powers.markDirty(player);
            return PowerType.Result.OK;
        }
        if (type.mode == PowerType.Mode.TOGGLE || type.mode == PowerType.Mode.CHANNEL) {
            Running r = st.running(id);
            if (r != null) {
                if (type.mode == PowerType.Mode.TOGGLE) {
                    r.stop(Running.Why.TOGGLED_OFF);
                    return PowerType.Result.OK;
                }
                return PowerType.Result.fail("busy");
            }
        }
        if (l.cooldownLeft(id, now) > 0) return PowerType.Result.fail("cooldown");
        ensureEnergy(player, l);
        // a power cast in a Weave's beat (Arms, SPEC §25) fuses with the weapon and costs less
        float cost = costOf(player, type, owned.variant()) * dev.awaken.core.api.WeaveHooks.costScale(player);
        if (l.energy < cost) return PowerType.Result.fail("energy");
        Cast c = new Cast(player, type, owned.variant(), owned.level(), slot, predicted, yRot, xRot, strafe, forward);
        PowerType.Result r;
        PowerType prevCtx = Mastery.enter(type);
        boolean prevCast = Mastery.beginCast();
        try {
            CastProfiles.Profile prof = CastProfiles.byPower(type.path);
            // a heavy power with a profile waits a few ticks for the body (the foot lands, then the wave leaves): the press already
            // started the pose everywhere and spent the energy and the cooldown (docs/power-anims/PLAN.md D3)
            r = prof != null && prof.windup() > 0 ? windUp(c, prof) : type.behaviour.cast(c);
            // a cast that drew no effect of its own kind (no target) still begins its pose for everyone
            if (prof != null && r.ok() && !c.poseSent) Fx.pose(c);
        } catch (RuntimeException e) {
            errors++;
            dev.awaken.Awaken.LOG.error("Power {} failed to cast", id, e);
            Mastery.castFailed(player, type);
            return PowerType.Result.fail("error");
        } finally {
            Mastery.endCast(prevCast);
            Mastery.exit(prevCtx);
        }
        if (!r.ok()) {
            Mastery.castFailed(player, type);
            return r;
        }
        if (cost > 0) spend(player, l, cost, now);
        // a ladder step's guard (SPEC 36.3): damage taken is cut for a while after the cast
        if (c.kit.guard != null) st.buff(PowerState.Buffs.GUARD, now + c.kit.guard.ticks(), c.kit.guard.share());
        Mastery.cast(player, type, cost, now);
        Running channel = st.running(id);
        boolean deferred = channel != null && channel.channel && !channel.stopping();
        if (c.followUp != null) {
            // the HUD and the client's key see the window as an AGAIN entry where the cooldown would be
            st.followUps.put(id, new PowerState.FollowUp(type, now + c.followUpTicks, c.followUp));
            l.cooldowns.put(id, new Loadout.Cooldown(now + c.followUpTicks, Loadout.AGAIN));
        } else if (!deferred) startCooldown(player, l, type, owned, st);
        Progression.count(player, type.family.counter());
        if (type.dashLike) onDash(player, st);
        if (carries(type)) dev.awaken.core.api.ArrivalHooks.report(player, dev.awaken.core.api.ArrivalHooks.Way.POWER, MOVE_HOLD);
        MoveTwists.afterCast(c, cost, now); // the Awakened Body's chains and family twists
        dev.awaken.core.api.WeaveHooks.cast(player, type.family.name(), type.forces.isEmpty() ? null : type.forces.iterator().next().name(), owned.level());
        st.markCombatIfFoesNear(player);
        Powers.markDirty(player);
        return PowerType.Result.OK;
    }

    /** Begins a wind-up: the pose for everyone, then the power itself {@code windup} ticks later, aimed where the caster looks then. */
    private static PowerType.Result windUp(Cast c, CastProfiles.Profile prof) {
        Fx.pose(c);
        if (prof.beginSound() != null) {
            var sound = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(net.minecraft.resources.Identifier.parse(prof.beginSound()));
            sound.ifPresent(h -> c.sound(h, 0.8f, 1f));
        }
        int wait = Math.max(1, prof.windup());
        Running w = start(c, c.type.id + "_windup", wait);
        w.tick(rr -> {
            if (rr.age != wait - 1 || !c.player.isAlive() || c.player.isSpectator()) return;
            Cast at = new Cast(c.player, c.type, c.variant, c.powerLevel, c.slot, c.predicted, c.player.getYRot(), c.player.getXRot(),
                    c.strafe(), c.forward());
            PowerType prevCtx = Mastery.enter(c.type);
            try {
                c.type.behaviour.cast(at);
            } catch (RuntimeException e) {
                errors++;
                dev.awaken.Awaken.LOG.error("Power {} failed to cast after its wind-up", c.type.id, e);
            } finally {
                Mastery.exit(prevCtx);
            }
        });
        return PowerType.Result.OK;
    }

    /** Closes a cast's second press early (the leap landed before the plunge): its cooldown starts now. */
    public static void endFollowUp(Cast c) {
        PowerState st = PowerState.of(c.player);
        if (st.followUps.remove(c.type.id.toString()) == null) return;
        Loadout l = Powers.loadout(c.player);
        Loadout.Owned o = l.get(c.type.id.toString());
        if (o != null) startCooldown(c.player, l, c.type, o, st);
        Powers.markDirty(c.player);
    }

    /** A power's move counts as movement for this long after its cast (SPEC §17 "how you arrived"). */
    static final int MOVE_HOLD = 40;

    /** Whether a power carries its caster: the movement slot's powers, dashes, and Hover (the one active that flies you). */
    static boolean carries(PowerType type) {
        return type.slot == PowerType.Slot.MOVEMENT || type.dashLike || type.id.getPath().equals("hover");
    }

    static float costOf(ServerPlayer player, PowerType type, int variant) {
        float cost = PowerMath.cost(type.cost, type.variant(variant).cost());
        PowerState st = PowerState.of(player);
        long now = now(player);
        if (type.family == PowerType.Family.ENERGY && st.has(PowerState.Buffs.OVERLOAD_COST, now)) cost *= 0.5f;
        // Cloak's Shade Step (SPEC 36): the shadow moves are free while you're unseen
        if (st.has(PowerState.Buffs.CLOAK_FREE, now) && st.has(PowerState.Buffs.CLOAK, now)
                && (type.id.getPath().equals("shadow_step") || type.id.getPath().equals("echo_swap"))) cost = 0;
        return cost;
    }

    static void startCooldown(ServerPlayer player, Loadout l, PowerType type, Loadout.Owned owned, PowerState st) {
        st.refreshPerks(player, now(player));
        int cd = PowerMath.cooldown(type.cooldown, type.variant(owned.variant()).cooldown(), owned.level(), st.cdrPerk);
        cd = Math.max(1, Math.round(cd * dev.awaken.powers.ladder.PowerLadder.cdScale(type.path, owned.level())));
        l.cooldowns.put(type.id.toString(), new Loadout.Cooldown(now(player) + cd, cd));
    }

    static void spend(ServerPlayer player, Loadout l, float amount, long now) {
        l.energy = Math.max(0, l.energy - amount);
        l.pauseUntil = now + PowerMath.REGEN_PAUSE_TICKS;
    }

    /** Extra energy drawn by a running power (flight pressure). Returns false when the pool can't pay it. */
    public static boolean drainExtra(ServerPlayer player, float amount) {
        Loadout l = Powers.loadout(player);
        if (l.energy < amount) return false;
        l.energy -= amount;
        l.pauseUntil = now(player) + PowerMath.REGEN_PAUSE_TICKS;
        return true;
    }

    /** Gives energy back (Conduit, kills with Void Maw, a Hunter focus). Never above the pool; returns what was added. */
    public static float restore(ServerPlayer player, float amount) {
        if (amount <= 0) return 0;
        Loadout l = Powers.loadout(player);
        float before = Math.max(0, l.energy < 0 ? Powers.maxEnergy(player) : l.energy);
        l.energy = Math.min(Powers.maxEnergy(player), before + amount);
        Powers.markDirty(player);
        return l.energy - before;
    }

    static void ensureEnergy(ServerPlayer player, Loadout l) {
        if (l.energy < 0) l.energy = Powers.maxEnergy(player);
    }

    static void release(ServerPlayer player, PowerType type) {
        PowerState st = PowerState.peek(player);
        if (st == null) return;
        Running r = st.running(type.id.toString());
        if (r != null && r.channel && type.mode == PowerType.Mode.CHANNEL) r.stop(Running.Why.RELEASED);
    }

    static void cancelOnSlot(ServerPlayer player, int slot) {
        PowerState st = PowerState.peek(player);
        if (st == null) return;
        Running r = st.running(Powers.loadout(player).slot(slot));
        if (r != null && r.channel) r.stop(Running.Why.CANCELLED);
    }

    private static void onDash(ServerPlayer player, PowerState st) {
        Progression.count(player, Counters.DASHES);
        if (player.onGround()) {
            st.dashChain = 1;
            st.wasOnGround = true;
        } else {
            st.dashChain++;
        }
        Progression.record(player, Counters.DASH_CHAIN_AIRBORNE_BEST, st.dashChain);
        Progression.setCounter(player, Counters.DASH_CHAIN_AIRBORNE_BEST + "_session", st.dashChain);
    }

    // ---- running powers ---------------------------------------------------------------------------------------------

    /** Starts (or replaces) a running effect keyed to the cast's power. duration -1 = until stopped. */
    public static Running start(Cast c, int duration) {
        return start(c, c.type.id.toString(), duration);
    }

    public static Running start(Cast c, String key, int duration) {
        PowerState st = PowerState.of(c.player);
        Running old = st.running(key);
        if (old != null) finish(c.player, st, old, Running.Why.CANCELLED);
        Running r = new Running(c, key, duration);
        st.running.add(r);
        Powers.markDirty(c.player);
        return r;
    }

    /** A channel or toggle: drains the power's energy per second and starts its cooldown when it ends. */
    public static Running channel(Cast c, int maxTicks) {
        Running r = start(c, maxTicks <= 0 ? -1 : maxTicks);
        r.channel = true;
        return r;
    }

    static void finish(ServerPlayer player, PowerState st, Running r, Running.Why why) {
        if (r.ended) return;
        r.ended = true;
        if (r.why == null) r.why = why;
        PowerType prevCtx = Mastery.enter(r.c.type);
        try {
            if (r.onEnd != null) r.onEnd.end(r, r.why);
        } catch (RuntimeException e) {
            errors++;
            dev.awaken.Awaken.LOG.error("Power {} failed while ending", r.key, e);
        } finally {
            Mastery.exit(prevCtx);
        }
        if (r.channel) {
            Loadout l = Powers.loadout(player);
            Loadout.Owned o = l.get(r.c.type.id.toString());
            if (o != null) startCooldown(player, l, r.c.type, o, st);
        }
        Powers.markDirty(player);
    }

    /** Behaviour exceptions caught so far (GameTests assert this stays put). */
    static int errors;

    /** Ends every running power (death, logout, world change). Cleans up modifiers and post effects. */
    public static void endAll(ServerPlayer player, Running.Why why) {
        PowerState st = PowerState.peek(player);
        if (st == null) return;
        // an end callback may start a follow-up effect (a throw after a lift): end those too
        for (int pass = 0; pass < 4 && !st.running.isEmpty(); pass++) {
            List<Running> batch = List.copyOf(st.running);
            st.running.clear();
            for (Running r : batch) finish(player, st, r, why);
        }
        st.running.clear();
    }

    // ---- per-tick ---------------------------------------------------------------------------------------------------

    static void tickPlayer(ServerPlayer player) {
        PowerState st = PowerState.of(player);
        long now = now(player);
        st.refreshPerks(player, now);
        Loadout l = Powers.loadout(player);
        int intStat = Progression.stat(player, Stat.INT);
        float max = PowerMath.maxEnergy(intStat);
        float regen = PowerMath.regenPerTick(intStat, st.regenPerk) * (st.has(PowerState.Buffs.CONDUIT, now) ? 3f : 1f)
                * (float) dev.awaken.veil.Veil.energyRegenMultiplier(player);
        if (l.maxEnergy != max || Math.abs(l.regenPerTick - regen) > 1e-6) {
            l.maxEnergy = max;
            l.regenPerTick = regen;
            st.loadoutDirty = true;
        }
        if (l.energy < 0) l.energy = max;
        if (l.energy > max) l.energy = max;
        boolean regenerating = false;
        if (now >= l.pauseUntil && l.energy < max) {
            l.energy = Math.min(max, l.energy + regen);
            regenerating = true;
        }

        if (!st.running.isEmpty()) tickRunning(player, st, l, now);

        // passives
        if (++st.passiveTick >= PowerType.PASSIVE_PERIOD) {
            st.passiveTick = 0;
            for (Loadout.Owned o : l.owned.values()) {
                PowerType t = PowerRegistry.get(o.id());
                if (t != null && t.passive != null) {
                    PowerType prevCtx = Mastery.enter(t);
                    try {
                        t.passive.tick(new Cast(player, t, o.variant(), o.level(), -1, false, player.getYRot(), player.getXRot()));
                    } catch (RuntimeException e) {
                        errors++;
                        dev.awaken.Awaken.LOG.error("Passive {} failed", o.id(), e);
                    } finally {
                        Mastery.exit(prevCtx);
                    }
                }
            }
        }
        if (now % 200 == 0 && !l.cooldowns.isEmpty()) l.cooldowns.values().removeIf(cd -> cd.end() < now);
        if (!st.followUps.isEmpty()) {
            // a second press not taken: the cooldown it held back starts
            var it = st.followUps.entrySet().iterator();
            while (it.hasNext()) {
                var f = it.next();
                if (f.getValue().until() >= now) continue;
                it.remove();
                Loadout.Owned o = l.get(f.getKey());
                if (o != null) startCooldown(player, l, f.getValue().type(), o, st);
                st.loadoutDirty = true;
            }
        }
        dev.awaken.powers.ladder.Evolution.tick(player, l, now);

        // airborne dash chains end on landing
        boolean ground = player.onGround() || player.isInWater();
        if (ground && !st.wasOnGround && st.dashChain > 0) {
            st.dashChain = 0;
            Progression.setCounter(player, Counters.DASH_CHAIN_AIRBORNE_BEST + "_session", 0);
        }
        st.wasOnGround = ground;

        if (st.flowTickBank >= 20 || (st.flowTickBank > 0 && st.running.isEmpty())) {
            Progression.count(player, Counters.FLOW_TICKS, st.flowTickBank);
            st.flowTickBank = 0;
        }

        // mastery moves with nearly every hit: it rides the once-a-second sync instead of sending one per hit
        if (st.loadoutDirty || ((regenerating || st.masteryDirty) && now % 20 == 0)) sync(player, l, st, now);
    }

    private static void tickRunning(ServerPlayer player, PowerState st, Loadout l, long now) {
        List<Running> snapshot = new ArrayList<>(st.running);
        for (Running r : snapshot) {
            if (r.ended) continue;
            if (r.channel && !r.stopping()) {
                float drain = r.c.type.drain / 20f * r.c.v.cost() * r.drainScale;
                if (r.c.type.family == PowerType.Family.ENERGY && st.has(PowerState.Buffs.OVERLOAD_COST, now)) drain *= 0.5f;
                if (drain > 0) {
                    if (l.energy < drain) {
                        r.stop(Running.Why.NO_ENERGY);
                    } else {
                        l.energy -= drain;
                        l.pauseUntil = now + PowerMath.REGEN_PAUSE_TICKS;
                        Mastery.drained(player, st, r.c.type, drain, now);
                    }
                }
            }
            if (!r.stopping()) {
                // flight and hover keep counting as a power's move for as long as they run
                if (carries(r.c.type)) dev.awaken.core.api.ArrivalHooks.report(player, dev.awaken.core.api.ArrivalHooks.Way.POWER, 10);
                PowerType prevCtx = Mastery.enter(r.c.type);
                try {
                    if (r.onTick != null) r.onTick.tick(r);
                } catch (RuntimeException e) {
                    errors++;
                    dev.awaken.Awaken.LOG.error("Power {} failed while running", r.key, e);
                    r.stop(Running.Why.CANCELLED);
                } finally {
                    Mastery.exit(prevCtx);
                }
                r.age++;
                if (r.duration >= 0 && r.age >= r.duration) r.stop(Running.Why.EXPIRED);
            }
            if (r.stopping()) finish(player, st, r, r.why);
        }
        st.running.removeIf(r -> r.ended);
    }

    static void sync(ServerPlayer player, Loadout l, PowerState st, long now) {
        st.loadoutDirty = false;
        st.masteryDirty = false;
        l.syncTime = now;
        l.active.clear();
        for (Running r : st.running) {
            if (!r.ended && PowerRegistry.get(r.key) != null) {
                l.active.add(new Loadout.Active(r.key, r.duration < 0 ? -1 : now + r.left()));
            }
        }
        player.syncData(PowerContent.LOADOUT.get());
    }

    /** Ask a running power to stop (e.g. Flow pressed again). */
    public static boolean stop(ServerPlayer player, String key, Running.Why why) {
        PowerState st = PowerState.peek(player);
        if (st == null) return false;
        Running r = st.running(key);
        if (r == null) return false;
        r.stop(why);
        return true;
    }

    public static @Nullable Running running(ServerPlayer player, String key) {
        PowerState st = PowerState.peek(player);
        return st == null ? null : st.running(key);
    }
}
