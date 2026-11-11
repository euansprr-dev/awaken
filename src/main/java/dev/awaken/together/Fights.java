package dev.awaken.together;

import dev.awaken.core.AwakenConfig;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.FightsHooks;
import dev.awaken.core.api.GateHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.api.PartyHooks;
import dev.awaken.core.api.Strikes;
import dev.awaken.gates.Gates;
import dev.awaken.powers.Casting;
import dev.awaken.powers.Powers;
import dev.awaken.powers.Running;
import dev.awaken.signal.Signal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * My fights: Full or Gentle (SPEC §70.4), and standing back up (§70.3). Gentle is the same fight asked more simply of
 * one player: half the damage, second winds, falls caught, fewer small monsters at once. A blow aimed at a player on
 * Full is never touched here. What a boss chooses to throw at a Gentle player is the fight runner's part (it asks
 * {@link FightsHooks}). Kept in memory: a second wind is two seconds long and a fighter is down for twenty.
 */
final class Fights {
    private Fights() {}

    /** A second wind: this long on a knee, then this long before anything can hurt them again. */
    static final int KNEEL = 40, GRACE = 40;
    static final float WIND_HEALTH = 0.5F;
    /** Outside a gate second winds come back after this much calm. */
    static final int CALM = 20 * 60;
    /** A caught fall costs this much of the health they had on the last safe ground. */
    static final float FALL_COST = 4F;
    private static final double SWEEP_RANGE = 24, MIN_SIGHT = 16, MAX_SIGHT = 48;

    /** Where a Gentle player last stood safely in a gate, and how healthy. */
    private record Safe(ServerLevel level, Vec3 at, float health) {}

    /** A fighter waiting to be lifted. */
    static final class Down {
        int left;
        int lift;
        long lastLift;

        Down(int left) {
            this.left = left;
        }
    }

    private static final Map<UUID, Integer> KNEELING = new HashMap<>();
    private static final Map<UUID, Integer> GRACED = new HashMap<>();
    private static final Map<UUID, Integer> WINDS_USED = new HashMap<>();
    private static final Map<UUID, Long> FLOOR = new HashMap<>();
    private static final Map<UUID, Long> LAST_FIGHT = new HashMap<>();
    private static final Map<UUID, Safe> SAFE = new HashMap<>();
    private static final Map<UUID, Down> DOWN = new HashMap<>();
    private static final Map<UUID, Integer> LIFTS = new HashMap<>();
    /** Fighters whose down ran out: the death that follows is the real one. */
    private static final Set<UUID> DYING = new HashSet<>();
    /** Where each player's last gate stands in the overworld, noted as a blow that may kill them lands. */
    private record Door(BlockPos at, int gateId, long tick) {}

    private static final Map<UUID, Door> DOORS = new HashMap<>();
    /** Small monsters on each Gentle player right now (their entity ids), rebuilt once a second. */
    private static final Map<UUID, Set<Integer>> ON = new HashMap<>();
    /** What each player has done to each boss since it last chose whom to fight (boss entity id, then player). */
    private static final Map<Integer, Map<UUID, Float>> LEDGER = new HashMap<>();
    /** Phrases each boss has started since a player on Gentle last had one. */
    private static final Map<Integer, Integer> SINCE_GENTLE = new HashMap<>();

    static void forget() {
        KNEELING.clear();
        GRACED.clear();
        WINDS_USED.clear();
        FLOOR.clear();
        LAST_FIGHT.clear();
        SAFE.clear();
        DOWN.clear();
        LIFTS.clear();
        DYING.clear();
        ON.clear();
        LEDGER.clear();
        SINCE_GENTLE.clear();
        DOORS.clear();
    }

    static void forget(UUID id) {
        KNEELING.remove(id);
        GRACED.remove(id);
        SAFE.remove(id);
        ON.remove(id);
        DOWN.remove(id); // a logout while down is a death as before: the vanilla player is simply gone, at one health
    }

    // ---- the setting --------------------------------------------------------------------------------------------------

    static boolean gentle(Player p) {
        return TogetherConfig.gentle() && p.getData(TogetherContent.FIGHTS).gentle();
    }

    /** Changes the setting. Out of a fight it changes at once; in one it waits and says so. */
    static boolean set(ServerPlayer p, boolean gentle) {
        if (!TogetherConfig.gentle()) {
            Signal.toast(p, Component.literal("Gentle fights are switched off in this world."));
            return false;
        }
        if (p.getData(TogetherContent.FIGHTS).gentle() == gentle) return true;
        if (Powers.inCombat(p)) {
            Signal.toast(p, Component.literal("Not in the middle of a fight. It'll change once you're clear of it: set it again then."));
            return false;
        }
        p.setData(TogetherContent.FIGHTS, new TogetherContent.FightsSetting(gentle, 1));
        WINDS_USED.remove(p.getUUID());
        Signal.toast(p, Component.literal(gentle
                ? "Your fights are Gentle now. Blows aimed at you are plainer and do half, and a blow that would kill you is a second wind instead."
                : "Your fights are Full now, the game as it is."));
        DirectorLog.log(p, "together", "fights set to " + (gentle ? "Gentle" : "Full"));
        TogetherNet.dirty();
        return true;
    }

    static int windsLeft(ServerPlayer p) {
        return gentle(p) ? Math.max(0, TogetherConfig.secondWinds() - WINDS_USED.getOrDefault(p.getUUID(), 0)) : 0;
    }

    static boolean kneeling(UUID id) {
        return KNEELING.containsKey(id) || DOWN.containsKey(id);
    }

    static boolean down(UUID id) {
        return DOWN.containsKey(id);
    }

    /** Ticks left before a downed fighter's death is counted (0 when not down). */
    static int downLeft(UUID id) {
        Down d = DOWN.get(id);
        return d == null ? 0 : d.left;
    }

    private static boolean shielded(UUID id) {
        return KNEELING.containsKey(id) || GRACED.containsKey(id) || DOWN.containsKey(id);
    }

    // ---- blows --------------------------------------------------------------------------------------------------------

    /** HIGHEST: on a knee or just up nothing lands; and nobody on a knee lands anything. */
    static void onIncoming(LivingIncomingDamageEvent e) {
        DamageSource src = e.getSource();
        if (src.is(DamageTypes.GENERIC_KILL)) return;
        if (e.getEntity() instanceof ServerPlayer p && shielded(p.getUUID())) {
            e.setCanceled(true);
            return;
        }
        if (src.getEntity() instanceof ServerPlayer by && kneeling(by.getUUID())) e.setCanceled(true);
    }

    /** LOWEST, so it scales what armour and every other rule have already made of the hit. */
    static void onDamage(LivingDamageEvent.Pre e) {
        LivingEntity hit = e.getEntity();
        if (hit instanceof ServerPlayer p) {
            LAST_FIGHT.put(p.getUUID(), p.level().getGameTime());
            if (gentle(p)) e.setNewDamage((float) (e.getNewDamage() * TogetherConfig.gentleDamage()));
            return;
        }
        Entity by = e.getSource().getEntity();
        if (by instanceof OwnableEntity pet) by = pet.getOwner();
        if (by instanceof ServerPlayer p) {
            LAST_FIGHT.put(p.getUUID(), p.level().getGameTime());
            if (backTurned(hit, p)) e.setNewDamage((float) (e.getNewDamage() * (1 + TogetherConfig.backTurned())));
            if (hit instanceof Mob mob && !small(mob)) LEDGER.computeIfAbsent(mob.getId(), k -> new HashMap<>()).merge(p.getUUID(), e.getNewDamage(), Float::sum);
        }
    }

    /**
     * Its back is turned (SPEC §70.3): the creature is in the middle of a blow at someone else of the striker's party.
     * Never alone, so a solo fight is what it was.
     */
    static boolean backTurned(LivingEntity target, ServerPlayer by) {
        if (!TogetherConfig.pair() || !(target instanceof Mob mob)) return false;
        Strikes.Tell tell = Strikes.tell(mob);
        if (tell == null || tell.ticks() <= 0 || tell.kind() == Strikes.Kind.NONE) return false;
        return mob.getTarget() instanceof ServerPlayer other && other != by && other.isAlive() && PartyHooks.sameParty(by, other);
    }

    /** What a blow's stance damage counts for, by who struck and what it struck (asked by Arms). */
    static int stance(LivingEntity target, int amount, ServerPlayer by) {
        return amount > 0 && backTurned(target, by) ? Math.max(1, (int) Math.round(amount * (1 + TogetherConfig.backTurned()))) : amount;
    }

    // ---- a killing blow -----------------------------------------------------------------------------------------------

    /** HIGHEST: before a gate counts the death or Cheat Death is spent. A Gentle player's fall, or their second wind. */
    static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof Player)) { // a creature's gone: what was owed it goes too
            LEDGER.remove(e.getEntity().getId());
            SINCE_GENTLE.remove(e.getEntity().getId());
            return;
        }
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        // the gate's door, noted before the gate forgets this run: where they wake if the death is real (SPEC §70.3)
        var run = Gates.activeRun(p);
        if (run.isPresent() && TogetherConfig.pair()) {
            Gates.doorOf(p).ifPresent(at -> DOORS.put(p.getUUID(), new Door(at, run.get().gateId(), p.level().getGameTime())));
        }
        if (e.getSource().is(DamageTypes.GENERIC_KILL) || !gentle(p)) return;
        DamageSource src = e.getSource();
        if ((src.is(DamageTypes.FALL) || src.is(DamageTypes.FELL_OUT_OF_WORLD)) && caughtFall(p)) {
            e.setCanceled(true);
            return;
        }
        if (windsLeft(p) <= 0) return;
        e.setCanceled(true);
        WINDS_USED.merge(p.getUUID(), 1, Integer::sum);
        secondWind(p, src);
    }

    /** A fall that would kill, in a gate: back on the last safe ground, two hearts lighter. False with nowhere to put them. */
    private static boolean caughtFall(ServerPlayer p) {
        Safe safe = SAFE.get(p.getUUID());
        if (safe == null || safe.level() != p.level() || !GateHooks.inGate(p)) return false;
        steady(p, Math.max(2F, safe.health() - FALL_COST));
        p.teleport(new TeleportTransition(safe.level(), safe.at(), Vec3.ZERO, p.getYRot(), p.getXRot(), TeleportTransition.DO_NOTHING));
        p.resetFallDistance();
        GRACED.put(p.getUUID(), GRACE);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), TogetherContent.UP, SoundSource.PLAYERS, 0.8F, 1F);
        p.sendOverlayMessage(Component.literal("You're back where you last had your feet under you."));
        DirectorLog.log(p, "together", "a fall caught at " + BlockPos.containing(safe.at()).toShortString());
        return true;
    }

    /** Health back, fire and poisons gone, hands empty of casts: the state every catch starts from. */
    private static void steady(ServerPlayer p, float health) {
        p.setHealth(Mth.clamp(health, 1F, p.getMaxHealth()));
        p.clearFire();
        for (MobEffectInstance i : List.copyOf(p.getActiveEffects())) {
            if (i.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) p.removeEffect(i.getEffect());
        }
        p.stopUsingItem();
        Casting.endAll(p, Running.Why.CANCELLED);
        p.resetFallDistance();
    }

    private static void secondWind(ServerPlayer p, DamageSource src) {
        steady(p, p.getMaxHealth() * WIND_HEALTH);
        // thrown back from whatever struck
        Vec3 from = src.getSourcePosition();
        Vec3 away = from == null ? Vec3.ZERO : p.position().subtract(from).multiply(1, 0, 1);
        away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(0.9);
        p.setDeltaMovement(away.x, 0.35, away.z);
        p.needsSync = true;
        p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, KNEEL, 6, false, false, false));
        GRACED.remove(p.getUUID());
        KNEELING.put(p.getUUID(), KNEEL);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), TogetherContent.SECOND_WIND, SoundSource.PLAYERS, 0.9F, 1F);
        int left = windsLeft(p);
        p.sendOverlayMessage(Component.literal(left > 0 ? "A second wind. You have " + left + " more." : "A second wind. That was your last one for now."));
        shakeOff(p);
        DirectorLog.log(p, "together", "a second wind (" + left + " left)");
        TogetherNet.dirty();
    }

    // ---- standing back up (any pair, in a gate) --------------------------------------------------------------------------

    private static long floorKey(ServerPlayer p) {
        return Gates.activeRun(p).map(r -> ((long) r.gateId() << 16) ^ ((long) r.area() << 8) ^ r.floor()).orElse(-1L);
    }

    /** The rest of the player's party standing on their floor: alive, not down themselves. */
    private static @Nullable ServerPlayer standing(ServerPlayer p) {
        long floor = floorKey(p);
        if (floor < 0) return null;
        for (UUID id : PartyHooks.members(p)) {
            ServerPlayer m = id.equals(p.getUUID()) ? null : p.level().getServer().getPlayerList().getPlayer(id);
            if (m != null && m.isAlive() && !m.isSpectator() && !DOWN.containsKey(id) && floorKey(m) == floor) return m;
        }
        return null;
    }

    /** {@link FightsHooks.Access#goesDown}: asked by a gate once Cheat Death has had its chance. */
    static boolean goesDown(ServerPlayer p) {
        if (!TogetherConfig.pair() || DYING.contains(p.getUUID()) || DOWN.containsKey(p.getUUID())) return false;
        if (Gates.inScriptedGate(p)) return false;
        ServerPlayer mate = standing(p);
        if (mate == null) return false;
        long floor = floorKey(p);
        int allowed = FightsHooks.deathCost(p) == AwakenConfig.Difficulty.STORY ? 2 : 1;
        Long was = FLOOR.get(p.getUUID());
        if (was == null || was != floor) LIFTS.remove(p.getUUID());
        if (LIFTS.getOrDefault(p.getUUID(), 0) >= allowed) return false;
        steady(p, 1F);
        p.setDeltaMovement(Vec3.ZERO);
        p.needsSync = true;
        p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, TogetherConfig.downTicks() + 20, 9, false, false, false));
        DOWN.put(p.getUUID(), new Down(TogetherConfig.downTicks()));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), TogetherContent.DOWN, SoundSource.PLAYERS, 1F, 1F);
        String name = p.getGameProfile().name();
        Signal.toast(p, Component.literal("You're down. " + mate.getGameProfile().name() + " has twenty seconds to reach you and lift you."));
        for (ServerPlayer m : PartyService.online(p)) {
            if (m != p) Signal.toast(m, Component.literal(name + " is down. Hold Use on them to lift them."));
        }
        shakeOff(p);
        DirectorLog.log(p, "together", "down, waiting for " + mate.getGameProfile().name());
        TogetherNet.dirty();
        return true;
    }

    /** Use, held on a downed partner: a tick's worth of lifting each time it arrives (the client repeats it while held). */
    static void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getTarget() instanceof ServerPlayer fallen) || !(e.getEntity() instanceof ServerPlayer by)) return;
        Down d = DOWN.get(fallen.getUUID());
        if (d == null) return;
        e.setCanceled(true);
        if (kneeling(by.getUUID()) || !PartyHooks.sameParty(by, fallen)) return;
        long now = by.level().getGameTime();
        if (d.lastLift == now) return; // both hands arrive on one tick
        // a held Use arrives every fourth tick: each arrival is worth the ticks since the last, up to that much
        d.lift += (int) Mth.clamp(now - d.lastLift, 1, 5);
        d.lastLift = now;
        if (d.lift < TogetherConfig.liftTicks()) {
            by.sendOverlayMessage(Component.literal("Lifting " + fallen.getGameProfile().name() + "..."));
            return;
        }
        DOWN.remove(fallen.getUUID());
        LIFTS.merge(fallen.getUUID(), 1, Integer::sum);
        FLOOR.put(fallen.getUUID(), floorKey(fallen));
        fallen.removeEffect(MobEffects.SLOWNESS);
        fallen.setHealth((float) Math.max(1.0, fallen.getMaxHealth() * TogetherConfig.liftHealth()));
        GRACED.put(fallen.getUUID(), GRACE);
        fallen.level().playSound(null, fallen.getX(), fallen.getY(), fallen.getZ(), TogetherContent.LIFT, SoundSource.PLAYERS, 1F, 1F);
        String name = fallen.getGameProfile().name();
        Signal.toast(fallen, Component.literal(by.getGameProfile().name() + " got you back on your feet."));
        for (ServerPlayer m : PartyService.online(fallen)) if (m != fallen) Signal.toast(m, Component.literal(name + " is back up."));
        DirectorLog.log(fallen, "together", "lifted by " + by.getGameProfile().name());
        TogetherNet.dirty();
    }

    /** Nobody came, or nobody is left to come: the death as it would have been. */
    private static void die(ServerPlayer p) {
        DOWN.remove(p.getUUID());
        p.removeEffect(MobEffects.SLOWNESS);
        DYING.add(p.getUUID());
        try {
            p.hurtServer(p.level(), p.damageSources().genericKill(), Float.MAX_VALUE);
        } finally {
            DYING.remove(p.getUUID());
        }
        TogetherNet.dirty();
    }

    // ---- waking at the gate's door ---------------------------------------------------------------------------------------

    /** How long after a death its gate's door is still where you wake. */
    private static final int DOOR_TICKS = 20 * 120;

    /**
     * LOW, after the Hearth has chosen: a fighter who really died in a gate wakes at its door while someone of their
     * party is still inside (SPEC §70.3), not at home, so the walk back is a few steps. The Satchel and the Fracture
     * were the price as before.
     */
    static void onRespawnPosition(net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.isFromEndFight()) return;
        Door door = DOORS.remove(p.getUUID());
        MinecraftServer s = p.level().getServer();
        ServerLevel overworld = s.overworld();
        if (door == null || overworld.getGameTime() - door.tick() > DOOR_TICKS && p.level().getGameTime() - door.tick() > DOOR_TICKS) return;
        boolean inside = false;
        for (UUID id : PartyHooks.members(p)) {
            ServerPlayer m = id.equals(p.getUUID()) ? null : s.getPlayerList().getPlayer(id);
            if (m != null && m.isAlive() && Gates.activeRun(m).map(r -> r.gateId() == door.gateId()).orElse(false)) inside = true;
        }
        if (!inside) return;
        Vec3 spot = standingNear(overworld, door.at());
        if (spot == null) return;
        e.setTeleportTransition(new TeleportTransition(overworld, spot, Vec3.ZERO, p.getYRot(), 0F, TeleportTransition.DO_NOTHING));
        Signal.toast(p, Component.literal("You're at the gate's door. Your party is still inside: go back in and it'll take you to them."));
        DirectorLog.log(p, "together", "woke at the door of gate " + door.gateId());
    }

    /** Somewhere to stand within a few blocks of a gate's door, nearest first; null if there's nowhere. */
    private static @Nullable Vec3 standingNear(ServerLevel level, BlockPos door) {
        for (int r = 2; r <= 5; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy = 2; dy >= -3; dy--) {
                        BlockPos feet = door.offset(dx, dy, dz);
                        BlockPos below = feet.below();
                        if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && open(level, feet) && open(level, feet.above())) {
                            return new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean open(ServerLevel level, BlockPos at) {
        var state = level.getBlockState(at);
        return state.getCollisionShape(level, at).isEmpty() && state.getFluidState().isEmpty();
    }

    // ---- monsters -----------------------------------------------------------------------------------------------------

    private static boolean small(Mob mob) {
        MobRole.Role r = MobRole.of(mob).role();
        return r != MobRole.Role.BOSS && r != MobRole.Role.WARDEN && r != MobRole.Role.RITUAL;
    }

    /** Someone else of this player's party a monster could go for instead; with {@code fullOnly}, only one on Full. */
    private static @Nullable ServerPlayer other(Mob mob, ServerPlayer not, boolean fullOnly) {
        AttributeInstance sight = mob.getAttribute(Attributes.FOLLOW_RANGE);
        double range = Mth.clamp(sight == null ? MIN_SIGHT : sight.getValue(), MIN_SIGHT, MAX_SIGHT);
        ServerPlayer best = null;
        double bestD = range * range;
        for (ServerPlayer o : ((ServerLevel) mob.level()).players()) {
            if (o == not || !o.isAlive() || o.isSpectator() || o.isCreative() || kneeling(o.getUUID())) continue;
            if (fullOnly && gentle(o)) continue;
            double d = o.distanceToSqr(mob);
            if (d <= bestD && mob.canAttack(o) && GateHooks.mayTarget(mob, o)) {
                best = o;
                bestD = d;
            }
        }
        return best;
    }

    /**
     * Every target a monster picks comes through here. Someone on a knee is never chased. A Gentle player has at most a
     * few small monsters on them at a time: the rest go for someone on Full, or hold back.
     */
    static void onTarget(LivingChangeTargetEvent e) {
        if (!(e.getNewAboutToBeSetTarget() instanceof ServerPlayer p) || !(e.getEntity() instanceof Mob mob)) return;
        if (kneeling(p.getUUID())) {
            e.setNewAboutToBeSetTarget(other(mob, p, false));
            return;
        }
        if (!gentle(p) || !small(mob)) return;
        Set<Integer> on = ON.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
        if (on.contains(mob.getId())) return;
        if (on.size() < TogetherConfig.gentlePack()) {
            on.add(mob.getId());
            return;
        }
        e.setNewAboutToBeSetTarget(other(mob, p, true));
    }

    /** Monsters already after this player look again: the event above decides. */
    private static void shakeOff(ServerPlayer p) {
        AABB box = p.getBoundingBox().inflate(SWEEP_RANGE);
        for (Mob m : p.level().getEntitiesOfClass(Mob.class, box, x -> x.getTarget() == p)) m.setTarget(p);
    }

    // ---- whom a boss fights ---------------------------------------------------------------------------------------------

    /**
     * {@link FightsHooks.Access#nextTarget}: a boss is about to start a phrase. It keeps the one it is fighting unless
     * someone else of their party has done more to it since it last chose (SPEC §70.3). With players on Full and on
     * Gentle both in sight, a Gentle player gets one phrase in so many, whatever anyone has done (§70.4). Alone, it
     * answers what it was asked.
     */
    static @Nullable LivingEntity nextTarget(Mob boss, @Nullable LivingEntity current) {
        if (!(current instanceof ServerPlayer now) || !(TogetherConfig.pair() || TogetherConfig.gentle())) return current;
        AttributeInstance sight = boss.getAttribute(Attributes.FOLLOW_RANGE);
        double range = Mth.clamp(sight == null ? MIN_SIGHT : sight.getValue(), MIN_SIGHT, MAX_SIGHT);
        Map<UUID, Float> did = LEDGER.get(boss.getId());
        ServerPlayer bestFull = null, bestGentle = null;
        float bestFullDid = -1, bestGentleDid = -1;
        int others = 0;
        for (ServerPlayer o : ((ServerLevel) boss.level()).players()) {
            if (!o.isAlive() || o.isSpectator() || o.isCreative() || kneeling(o.getUUID())) continue;
            if (o != now && (!PartyHooks.sameParty(now, o) || o.distanceToSqr(boss) > range * range || !boss.canAttack(o) || !GateHooks.mayTarget(boss, o))) continue;
            if (o != now) others++;
            // what they have done to it; the one it is on now wins a tie, so nothing turns it for nothing
            float d = (did == null ? 0F : did.getOrDefault(o.getUUID(), 0F)) + (o == now ? 0.001F : 0F);
            if (gentle(o)) {
                if (d > bestGentleDid) {
                    bestGentle = o;
                    bestGentleDid = d;
                }
            } else if (d > bestFullDid) {
                bestFull = o;
                bestFullDid = d;
            }
        }
        if (others == 0) return current;
        ServerPlayer pick;
        if (TogetherConfig.gentle() && bestFull != null && bestGentle != null) {
            int since = SINCE_GENTLE.getOrDefault(boss.getId(), 0);
            if (since >= TogetherConfig.onePhraseIn() - 1) {
                pick = bestGentle;
                SINCE_GENTLE.put(boss.getId(), 0);
            } else {
                pick = TogetherConfig.pair() || bestFull == now ? bestFull : gentle(now) ? bestFull : now;
                SINCE_GENTLE.put(boss.getId(), since + 1);
            }
        } else if (TogetherConfig.pair()) {
            pick = bestFull != null && bestFullDid >= bestGentleDid ? bestFull : bestGentle;
        } else {
            return current;
        }
        if (pick == null) return current;
        if (pick != now) LEDGER.remove(boss.getId()); // it has chosen: the count starts again
        return pick;
    }

    // ---- the clock ----------------------------------------------------------------------------------------------------

    static void tick(MinecraftServer s) {
        if (!GRACED.isEmpty()) {
            GRACED.replaceAll((id, t) -> t - 1);
            GRACED.values().removeIf(t -> t <= 0);
        }
        if (!KNEELING.isEmpty()) {
            for (UUID id : List.copyOf(KNEELING.keySet())) {
                int left = KNEELING.get(id) - 1;
                if (left > 0) {
                    KNEELING.put(id, left);
                    continue;
                }
                KNEELING.remove(id);
                GRACED.put(id, GRACE);
                ServerPlayer p = s.getPlayerList().getPlayer(id);
                if (p != null) p.level().playSound(null, p.getX(), p.getY(), p.getZ(), TogetherContent.UP, SoundSource.PLAYERS, 0.9F, 1F);
                TogetherNet.dirty();
            }
        }
        if (!DOWN.isEmpty()) tickDown(s);
        if (s.getTickCount() % 20 == 0) second(s);
    }

    private static void tickDown(MinecraftServer s) {
        for (UUID id : List.copyOf(DOWN.keySet())) {
            Down d = DOWN.get(id);
            ServerPlayer p = s.getPlayerList().getPlayer(id);
            if (p == null) {
                DOWN.remove(id);
                continue;
            }
            // a hand taken away is a lift that drains
            if (d.lift > 0 && p.level().getGameTime() - d.lastLift > 8) d.lift = Math.max(0, d.lift - 2);
            if (--d.left <= 0 || standing(p) == null) die(p); // time's up, or everyone else is down or gone
        }
    }

    /** Once a second: second winds come back, safe ground is remembered, and each Gentle player's pack is counted again. */
    private static void second(MinecraftServer s) {
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            UUID id = p.getUUID();
            long floor = floorKey(p);
            Long was = FLOOR.put(id, floor);
            long now = p.level().getGameTime();
            boolean newFloor = was != null && was != floor && floor >= 0;
            boolean calm = floor < 0 && now - LAST_FIGHT.getOrDefault(id, 0L) >= CALM;
            if ((newFloor || calm) && WINDS_USED.remove(id) != null) TogetherNet.dirty();
            if (newFloor) LIFTS.remove(id);
            if (!gentle(p)) {
                ON.remove(id);
                SAFE.remove(id);
                continue;
            }
            if (GateHooks.inGate(p) && p.onGround() && !p.isInWater() && !p.isInLava() && !kneeling(id)) {
                BlockPos below = p.blockPosition().below();
                if (p.level().getBlockState(below).isFaceSturdy(p.level(), below, Direction.UP)) SAFE.put(id, new Safe(p.level(), p.position(), p.getHealth()));
            }
            Set<Integer> on = ON.get(id);
            if (on != null) {
                on.clear();
                AABB box = p.getBoundingBox().inflate(SWEEP_RANGE);
                for (Mob m : p.level().getEntitiesOfClass(Mob.class, box, x -> x.getTarget() == p && small(x))) {
                    if (on.size() < TogetherConfig.gentlePack()) on.add(m.getId());
                }
            }
        }
    }

    // ---- for tests ----------------------------------------------------------------------------------------------------

    static void elapseForTest(MinecraftServer s, UUID id) {
        if (KNEELING.containsKey(id)) KNEELING.put(id, 1);
        if (DOWN.containsKey(id)) DOWN.get(id).left = 1;
        tick(s);
    }

    static void refillForTest(UUID id) {
        WINDS_USED.remove(id);
        LIFTS.remove(id);
        GRACED.remove(id);
    }

    /** The moment's grace after getting up is over (tests strike again at once). */
    static void refillGraceForTest(UUID id) {
        GRACED.remove(id);
    }

    static @Nullable Down downForTest(UUID id) {
        return DOWN.get(id);
    }
}
