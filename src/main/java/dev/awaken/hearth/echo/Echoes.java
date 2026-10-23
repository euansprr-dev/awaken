package dev.awaken.hearth.echo;

import dev.awaken.Awaken;
import dev.awaken.core.Forces;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.HearthViews;
import dev.awaken.hearth.player.HearthPlayer;
import dev.awaken.hearth.town.TownNpc;
import dev.awaken.hearth.training.TrainingGhost;
import dev.awaken.progression.Counters;
import dev.awaken.progression.Perks;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Stat;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Echoes: the bound shadow army. A fallen elite, warden or boss lingers for ten seconds; press B near it and it may
 * answer. Bound Echoes march with you (tap B to call or dismiss them, hold B to choose who) or work at home on role
 * pads. They are faithful copies of what they were, shadow-dark, and never raise a hand to you or each other.
 */
public final class Echoes {
    private Echoes() {}

    public static final int LINGER_TICKS = 200;
    public static final double BIND_RANGE = 6.0;
    public static final int REFORM_TICKS = 20 * 60;
    private static final Identifier ECHO_HEALTH = Awaken.id("echo_level_health");
    private static final Identifier ECHO_DAMAGE = Awaken.id("echo_level_damage");

    /** A fallen mob's Echo waiting to be bound. */
    public record Lingering(UUID killer, ResourceKey<Level> dimension, Vec3 pos, String type, CompoundTag data, String name, String origin,
                            int contentLevel, long expires) {}

    private static final List<Lingering> LINGERING = new ArrayList<>();
    /** Roster echo id → live entity UUID. */
    private static final Map<UUID, UUID> LIVE = new HashMap<>();

    public static void init() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(Echoes::onIncomingDamage);
        bus.addListener(Echoes::onChangeTarget);
        bus.addListener(Echoes::onDrops);
        bus.addListener(Echoes::onXpDrop);
        bus.addListener(Echoes::onJoin);
        bus.addListener(Echoes::onLogout);
        bus.addListener(Echoes::onRespawn);
        bus.addListener(Echoes::onLogin);
        bus.addListener(Echoes::onDimension);
        bus.addListener(Echoes::onServerTick);
        bus.addListener((ServerStoppedEvent e) -> {
            LINGERING.clear();
            LIVE.clear();
        });
    }

    // ---- caps -------------------------------------------------------------------------------------------------------

    public static int fieldCap(Player p) {
        return BindMath.fieldCap(Progression.stat(p, Stat.SPI));
    }

    public static int totalCap(ServerPlayer sp) {
        int pads = HearthService.hearthOf(sp).map(h -> h.pads.size()).orElse(0);
        return BindMath.totalCap(Progression.stat(sp, Stat.SPI), pads);
    }

    public static EchoLink link(Entity e) {
        return e.getExistingData(HearthContent.ECHO_LINK.get()).orElse(EchoLink.NONE);
    }

    public static boolean isEcho(Entity e) {
        return e != null && link(e).isEcho();
    }

    // ---- ally rules -------------------------------------------------------------------------------------------------

    /** Whether an Echo must never harm {@code other}: its owner, the owner's other Echoes, townsfolk, training ghosts. */
    public static boolean isAlly(Entity echo, @Nullable Entity other) {
        if (other == null) return false;
        EchoLink l = link(echo);
        if (!l.isEcho()) return false;
        if (other.getUUID().equals(l.owner())) return true;
        EchoLink o = link(other);
        if (o.isEcho() && o.owner().equals(l.owner())) return true;
        return other instanceof TownNpc || other instanceof TrainingGhost;
    }

    /** Whether an Echo may pick {@code target}. Never its owner, never an ally, never a player in creative/spectator. */
    public static boolean canTarget(Entity echo, @Nullable LivingEntity target) {
        if (target == null || !target.isAlive() || target == echo || isAlly(echo, target)) return false;
        if (target instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        return true;
    }

    public static @Nullable ServerPlayer owner(Entity echo) {
        EchoLink l = link(echo);
        if (!l.isEcho() || !(echo.level() instanceof ServerLevel sl)) return null;
        return sl.getServer().getPlayerList().getPlayer(l.owner());
    }

    // ---- lingering and binding ----------------------------------------------------------------------------------------

    /** A bindable mob died to a player: its Echo lingers for ten seconds. */
    public static void onKill(LivingEntity dead, ServerPlayer killer, ServerLevel level) {
        if (isEcho(dead) || !(dead instanceof Mob)) return;
        Optional<MobRole> role = dead.getExistingData(CoreApi.MOB_ROLE.get());
        if (role.isEmpty() || !role.get().isBindable()) return;
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType()).toString();
        String name = dead.hasCustomName() ? dead.getCustomName().getString() : dead.getType().getDescription().getString();
        LINGERING.add(new Lingering(killer.getUUID(), level.dimension(), dead.position(), type, snapshot(dead), name, role.get().role().name(),
                Math.max(1, role.get().contentLevel()), level.getGameTime() + LINGER_TICKS));
        HearthPlayer hp = HearthService.player(killer);
        if (hp.tips.add("linger")) {
            Signal.send(killer, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Something stays behind"))
                    .body(Component.literal("The " + name + "'s Echo lingers where it fell. Stand near it and press B. It may answer."))
                    .footer(Component.literal("Ten seconds")).ttl(20 * 8).priority(3).build());
        } else {
            Signal.toast(killer, Component.literal(name + "'s Echo lingers · B"));
        }
    }

    private static final String[] STRIP = {"Pos", "Motion", "Rotation", "fall_distance", "Fire", "Air", "OnGround", "Invulnerable",
            "PortalCooldown", "invulnerable_time", "UUID", "Glowing", "TicksFrozen", "HasVisualFire", "Passengers", "Health", "HurtTime",
            "DeathTime", "AbsorptionAmount", "current_impulse_context_reset_grace_time", "current_explosion_impact_pos", "active_effects",
            "FallFlying", "sleeping_pos", "Brain", "last_hurt_by_player", "last_hurt_by_player_memory_time", "last_hurt_by_mob",
            "ticks_since_last_hurt_by_mob", "leash", "home_radius", "home_pos", "DeathLootTable", "DeathLootTableSeed", "NoAI", "CustomNameVisible"};

    /** The mob's saved data minus everything about where and how it died. */
    public static CompoundTag snapshot(LivingEntity e) {
        try (ProblemReporter.ScopedCollector r = new ProblemReporter.ScopedCollector(Awaken.LOG)) {
            TagValueOutput out = TagValueOutput.createWithContext(r, e.registryAccess());
            e.saveWithoutId(out);
            CompoundTag t = out.buildResult();
            for (String k : STRIP) t.remove(k);
            return t;
        }
    }

    /** B pressed: bind the nearest lingering Echo, otherwise call or dismiss the field army. */
    public static void pressBind(ServerPlayer sp) {
        if (!HearthService.enabled()) return;
        Lingering l = nearestLingering(sp);
        if (l != null) {
            bindAttempt(sp, l, sp.getRandom().nextDouble());
            return;
        }
        HearthPlayer hp = HearthService.player(sp);
        if (hp.echoes.isEmpty()) {
            sp.sendOverlayMessage(Component.literal("No Echoes yet. Defeat an elite and press B where it fell."));
            return;
        }
        if (hp.fieldOut) dismissField(sp, true);
        else summonField(sp);
    }

    static @Nullable Lingering nearestLingering(ServerPlayer sp) {
        long now = sp.level().getGameTime();
        Lingering best = null;
        double bestD = BIND_RANGE * BIND_RANGE;
        for (Lingering l : LINGERING) {
            if (!l.killer.equals(sp.getUUID()) || l.dimension != sp.level().dimension() || l.expires < now) continue;
            double d = sp.position().distanceToSqr(l.pos);
            if (d <= bestD) {
                bestD = d;
                best = l;
            }
        }
        return best;
    }

    public enum BindResult { BOUND, FAILED, FULL, TOO_STRONG }

    /** One attempt at binding {@code l} with a uniform roll in [0,1). Public for tests. */
    public static BindResult bindAttempt(ServerPlayer sp, Lingering l, double roll) {
        Profile p = Progression.profile(sp);
        HearthPlayer hp = HearthService.player(sp);
        ServerLevel level = sp.level();
        if ("BOSS".equals(l.origin) && !BindMath.bossAllowed(p.rank().ordinal(), p.path().orElse(""))) {
            Signal.toast(sp, Component.literal("This one is too strong to bind. (S-rank, or the Shadowbinder Path)"));
            LINGERING.remove(l);
            return BindResult.TOO_STRONG;
        }
        if (hp.echoes.size() >= totalCap(sp)) {
            Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("No room in your army"))
                    .body(Component.literal("You hold " + hp.echoes.size() + " Echoes. More Spirit lets more march with you, and every role pad at home gives one a job."))
                    .ttl(20 * 6).build());
            return BindResult.FULL;
        }
        LINGERING.remove(l);
        double chance = BindMath.chance(l.origin, p.stat(Stat.SPI), p.rank().ordinal(), BindMath.rankOfLevel(l.contentLevel),
                Progression.perk(sp, Perks.BIND_CHANCE));
        dev.awaken.core.DirectorLog.log(sp, "echo", String.format("bind %s (%s) chance %.0f%% roll %.0f%%", l.name, l.origin, chance * 100, roll * 100));
        if (roll >= chance) {
            level.playSound(null, l.pos.x, l.pos.y, l.pos.z, HearthContent.SND_BIND_FAIL.get(), SoundSource.PLAYERS, 1f, 1f);
            level.sendParticles(HearthContent.ECHO_WISP.get(), l.pos.x, l.pos.y + 1, l.pos.z, 30, 0.5, 0.8, 0.5, 0.08);
            Signal.toast(sp, Component.literal("The Echo slips away. (" + Math.round(chance * 100) + "%)"));
            return BindResult.FAILED;
        }
        boolean field = hp.fieldCount() < fieldCap(sp);
        EchoRecord r = new EchoRecord(UUID.randomUUID(), l.type, l.data, l.name, 1, 0, field, 0, l.origin, l.contentLevel,
                PlayDays.current(level.getServer()));
        boolean first = hp.echoes.isEmpty() && p.counter(Counters.ECHOES_BOUND) == 0;
        hp.echoes.add(r);
        Progression.count(sp, Counters.ECHOES_BOUND);
        level.playSound(null, sp.blockPosition(), HearthContent.SND_BIND.get(), SoundSource.PLAYERS, 1f, 1f);
        level.sendParticles(HearthContent.ECHO_WISP.get(), l.pos.x, l.pos.y + 1, l.pos.z, 40, 0.4, 0.9, 0.4, 0.02);
        var w = SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Echo bound: " + l.name))
                .row(Component.literal("Army"), Component.literal(hp.echoes.size() + " / " + totalCap(sp)))
                .row(Component.literal("Post"), Component.literal(field ? "marches with you" : "on duty at home"));
        if (first) {
            Progression.markNewThing(sp, "echo");
            w.body(Component.literal("It's yours now, and it remembers how to fight. Tap B to call your Echoes, tap again to send them away. Hold B to choose who marches."));
        }
        Signal.send(sp, w.priority(4).ttl(20 * 10).build());
        if (field && hp.fieldOut) summonOne(sp, r);
        HearthService.hearthOf(sp).ifPresent(h -> Duty.reassign(sp, HearthService.levelOf(level.getServer(), h), h));
        HearthViews.refresh(sp);
        return BindResult.BOUND;
    }

    /** For tests: make a lingering Echo directly. */
    public static Lingering linger(ServerPlayer killer, Mob dead, String origin, int contentLevel) {
        Lingering l = new Lingering(killer.getUUID(), killer.level().dimension(), dead.position(),
                BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType()).toString(), snapshot(dead), dead.getType().getDescription().getString(),
                origin, contentLevel, killer.level().getGameTime() + LINGER_TICKS);
        LINGERING.add(l);
        return l;
    }

    // ---- roster management ------------------------------------------------------------------------------------------

    /** Hold-B chooser: march with me / stay home. Respects the field cap. */
    public static void setField(ServerPlayer sp, UUID echoId, boolean field) {
        HearthPlayer hp = HearthService.player(sp);
        Optional<EchoRecord> r = hp.echo(echoId);
        if (r.isEmpty() || r.get().field() == field) return;
        if (field && hp.fieldCount() >= fieldCap(sp)) {
            sp.sendOverlayMessage(Component.literal("Your field army is full (" + fieldCap(sp) + "). Send one home first."));
            return;
        }
        hp.replace(r.get().withField(field));
        if (!field) unsummon(sp.level().getServer(), echoId);
        else if (hp.fieldOut) summonOne(sp, r.get().withField(true));
        HearthService.hearthOf(sp).ifPresent(h -> Duty.reassign(sp, HearthService.levelOf(sp.level().getServer(), h), h));
        HearthViews.refresh(sp);
    }

    /** Lets an Echo go for good. */
    public static void release(ServerPlayer sp, UUID echoId) {
        HearthPlayer hp = HearthService.player(sp);
        Optional<EchoRecord> r = hp.echo(echoId);
        if (r.isEmpty()) return;
        unsummon(sp.level().getServer(), echoId);
        hp.echoes.remove(r.get());
        Signal.toast(sp, Component.literal(r.get().name() + " is released. It doesn't look back."));
        HearthService.hearthOf(sp).ifPresent(h -> Duty.reassign(sp, HearthService.levelOf(sp.level().getServer(), h), h));
        HearthViews.refresh(sp);
    }

    // ---- summoning --------------------------------------------------------------------------------------------------

    public static void summonField(ServerPlayer sp) {
        HearthPlayer hp = HearthService.player(sp);
        hp.fieldOut = true;
        int n = 0;
        for (EchoRecord r : List.copyOf(hp.echoes)) {
            if (!r.field()) continue;
            if (n >= BindMath.MAX_FIELD_BODIES) break;
            if (summonOne(sp, r) != null) n++;
        }
        sp.level().playSound(null, sp.blockPosition(), HearthContent.SND_SUMMON.get(), SoundSource.PLAYERS, 1f, 1f);
        if (n == 0) sp.sendOverlayMessage(Component.literal("Your Echoes are still reforming."));
        recordPeak(sp);
        HearthViews.refresh(sp);
    }

    public static void dismissField(ServerPlayer sp, boolean byChoice) {
        HearthPlayer hp = HearthService.player(sp);
        if (byChoice) hp.fieldOut = false;
        for (EchoRecord r : hp.echoes) if (r.field()) unsummon(sp.level().getServer(), r.id());
        if (byChoice) {
            sp.level().playSound(null, sp.blockPosition(), HearthContent.SND_SUMMON.get(), SoundSource.PLAYERS, 0.7f, 0.6f);
            HearthViews.refresh(sp);
        }
    }

    /** Spawns one field Echo next to its owner, if it isn't already out and isn't still reforming. */
    static @Nullable Mob summonOne(ServerPlayer sp, EchoRecord r) {
        ServerLevel level = sp.level();
        if (r.downUntil() > level.getGameTime()) return null;
        Mob existing = live(level.getServer(), r.id());
        if (existing != null) return existing;
        double a = sp.getRandom().nextDouble() * Math.PI * 2;
        Vec3 at = sp.position().add(Math.cos(a) * 1.8, 0.1, Math.sin(a) * 1.8);
        Mob mob = spawn(level, r, sp.getUUID(), at, false);
        if (mob != null) level.sendParticles(HearthContent.ECHO_WISP.get(), at.x, at.y + 0.8, at.z, 20, 0.3, 0.6, 0.3, 0.02);
        return mob;
    }

    /** Makes a live Echo from a roster record (field or duty). */
    public static @Nullable Mob spawn(ServerLevel level, EchoRecord r, UUID owner, Vec3 at, boolean duty) {
        Identifier id = Identifier.tryParse(r.type());
        Optional<EntityType<?>> type = id == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        if (type.isEmpty()) return null;
        float yaw = level.getRandom().nextFloat() * 360f;
        Entity e = EntityType.loadEntityRecursive(type.get(), r.data().copy(), level, EntitySpawnReason.MOB_SUMMONED, ent -> {
            ent.snapTo(at.x, at.y, at.z, yaw, 0f);
            return ent;
        });
        if (!(e instanceof Mob mob)) {
            if (e != null) e.discard();
            return null;
        }
        makeEcho(mob, owner, r, duty);
        if (!level.addFreshEntity(mob)) return null;
        LIVE.put(r.id(), mob.getUUID());
        return mob;
    }

    /** Turns a freshly loaded mob into somebody's Echo: link, tags, scaling, and a new mind. */
    public static void makeEcho(Mob mob, UUID owner, EchoRecord r, boolean duty) {
        mob.setData(HearthContent.ECHO_LINK.get(), new EchoLink(owner, r.id(), duty));
        Forces.tag(mob, Forces.Tag.ECHO);
        MobHooks.scale(mob, r.contentLevel(), new MobRole(MobRole.Role.ECHO, r.contentLevel(), "", Optional.empty(), ""));
        double originHp = switch (r.origin()) { case "BOSS" -> 4.0; case "WARDEN" -> 2.5; default -> 1.5; };
        double originDmg = switch (r.origin()) { case "BOSS" -> 1.8; case "WARDEN" -> 1.4; default -> 1.2; };
        set(mob.getAttribute(Attributes.MAX_HEALTH), ECHO_HEALTH, originHp * (1 + 0.06 * (r.level() - 1)) - 1);
        set(mob.getAttribute(Attributes.ATTACK_DAMAGE), ECHO_DAMAGE, originDmg * (1 + 0.04 * (r.level() - 1)) - 1);
        mob.setHealth(mob.getMaxHealth());
        mob.setPersistenceRequired();
        mob.setCanPickUpLoot(false);
        mob.clearFire();
        mob.setCustomName(Component.literal(r.name()));
        mob.setCustomNameVisible(false);
        mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, true, false));
        for (EquipmentSlot s : EquipmentSlot.values()) mob.setDropChance(s, 0f);
        EchoGoals.install(mob, duty);
    }

    private static void set(@Nullable AttributeInstance inst, Identifier id, double amount) {
        if (inst == null) return;
        if (Math.abs(amount) < 1e-6) inst.removeModifier(id);
        else inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    public static @Nullable Mob live(net.minecraft.server.MinecraftServer server, UUID echoId) {
        UUID u = LIVE.get(echoId);
        if (u == null) return null;
        for (ServerLevel l : server.getAllLevels()) {
            if (l.getEntity(u) instanceof Mob m && m.isAlive()) return m;
        }
        return null;
    }

    static void unsummon(net.minecraft.server.MinecraftServer server, UUID echoId) {
        Mob m = live(server, echoId);
        LIVE.remove(echoId);
        if (m != null) {
            if (m.level() instanceof ServerLevel sl) sl.sendParticles(HearthContent.ECHO_WISP.get(), m.getX(), m.getY() + 0.8, m.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
            m.discard();
        }
    }

    /** Live field Echoes of this player in their current level. */
    public static List<LivingEntity> fieldEntities(ServerPlayer sp) {
        List<LivingEntity> out = new ArrayList<>();
        for (EchoRecord r : HearthService.player(sp).echoes) {
            if (!r.field()) continue;
            Mob m = live(sp.level().getServer(), r.id());
            if (m != null && m.level() == sp.level()) out.add(m);
        }
        return out;
    }

    static void recordPeak(ServerPlayer sp) {
        int n = fieldEntities(sp).size();
        if (n > 0) Progression.record(sp, "awaken:echo_army_peak", n);
    }

    // ---- echo deaths, kills and xp ----------------------------------------------------------------------------------

    /** An Echo fell: it reforms in a minute. Haulers and farmers drop what they were carrying. */
    public static void onEchoDeath(LivingEntity dead, ServerLevel level) {
        EchoLink l = link(dead);
        LIVE.remove(l.echoId(), dead.getUUID());
        if (l.duty() && Duty.isCarrier(dead.getUUID()) && !dead.getMainHandItem().isEmpty()) {
            dead.spawnAtLocation(level, dead.getMainHandItem().copy());
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(l.owner());
        if (owner == null) return;
        HearthPlayer hp = HearthService.player(owner);
        hp.echo(l.echoId()).ifPresent(r -> {
            hp.replace(r.withDownUntil(level.getGameTime() + REFORM_TICKS));
            if (!l.duty()) Signal.toast(owner, Component.literal(r.name() + " fades. It will reform in a minute."));
        });
        level.sendParticles(HearthContent.ECHO_WISP.get(), dead.getX(), dead.getY() + 0.8, dead.getZ(), 24, 0.4, 0.6, 0.4, 0.05);
        HearthViews.refresh(owner);
    }

    /** Something died to an Echo: the Echo learns a little. */
    public static void creditKill(Entity echo, LivingEntity victim) {
        ServerPlayer owner = owner(echo);
        if (owner == null) return;
        int xp = victim.getExistingData(CoreApi.MOB_ROLE.get()).map(r -> switch (r.role()) {
            case BOSS -> 25; case WARDEN -> 10; case ELITE -> 5; default -> 1;
        }).orElse(1);
        grantXp(owner, link(echo).echoId(), xp);
    }

    public static void grantXp(ServerPlayer owner, UUID echoId, int xp) {
        HearthPlayer hp = HearthService.player(owner);
        hp.echo(echoId).ifPresent(r -> {
            EchoRecord next = r.withXp(xp);
            hp.replace(next);
            if (next.level() > r.level()) {
                Mob m = live(owner.level().getServer(), echoId);
                if (m != null) makeLevelStats(m, next);
                Signal.toast(owner, Component.literal(next.name() + " grew stronger · Echo level " + next.level()));
                HearthViews.refresh(owner);
            }
        });
    }

    private static void makeLevelStats(Mob m, EchoRecord r) {
        double originHp = switch (r.origin()) { case "BOSS" -> 4.0; case "WARDEN" -> 2.5; default -> 1.5; };
        double originDmg = switch (r.origin()) { case "BOSS" -> 1.8; case "WARDEN" -> 1.4; default -> 1.2; };
        set(m.getAttribute(Attributes.MAX_HEALTH), ECHO_HEALTH, originHp * (1 + 0.06 * (r.level() - 1)) - 1);
        set(m.getAttribute(Attributes.ATTACK_DAMAGE), ECHO_DAMAGE, originDmg * (1 + 0.04 * (r.level() - 1)) - 1);
    }

    /** An Echo's strike, credited to its owner so gate kills still count for the hunter. */
    public static boolean strike(ServerLevel level, Mob echo, LivingEntity target) {
        ServerPlayer owner = owner(echo);
        double dmg = echo.getAttribute(Attributes.ATTACK_DAMAGE) != null ? echo.getAttributeValue(Attributes.ATTACK_DAMAGE) : 3.0;
        DamageSource src = level.damageSources().source(DamageTypes.MOB_ATTACK, echo, owner != null ? owner : echo);
        boolean hit = target.hurtServer(level, src, (float) Math.max(1.0, dmg));
        if (hit) {
            double dx = echo.getX() - target.getX(), dz = echo.getZ() - target.getZ();
            target.knockback(0.3, dx, dz, src, (float) dmg);
        }
        return hit;
    }

    // ---- events -----------------------------------------------------------------------------------------------------

    private static @Nullable Entity echoAttacker(DamageSource src) {
        if (src.getDirectEntity() != null && isEcho(src.getDirectEntity())) return src.getDirectEntity();
        if (src.getEntity() != null && isEcho(src.getEntity())) return src.getEntity();
        return null;
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent e) {
        LivingEntity victim = e.getEntity();
        DamageSource src = e.getSource();
        Entity echo = echoAttacker(src);
        if (echo != null && echo != victim && isAlly(echo, victim)) {
            e.setCanceled(true);
            return;
        }
        if (isEcho(victim)) {
            Entity by = src.getEntity();
            if (by != null && by != victim && isAlly(victim, by)) e.setCanceled(true);
        }
    }

    private static void onChangeTarget(LivingChangeTargetEvent e) {
        if (!isEcho(e.getEntity())) return;
        LivingEntity t = e.getNewAboutToBeSetTarget();
        if (t != null && !canTarget(e.getEntity(), t)) e.setCanceled(true);
    }

    private static void onDrops(LivingDropsEvent e) {
        if (isEcho(e.getEntity())) e.setCanceled(true);
    }

    private static void onXpDrop(LivingExperienceDropEvent e) {
        if (isEcho(e.getEntity())) e.setCanceled(true);
    }

    /** Echoes reloaded from disk: duty Echoes rejoin their pad, stray field Echoes (a crash) dissolve. */
    private static void onJoin(EntityJoinLevelEvent e) {
        if (!(e.getLevel() instanceof ServerLevel) || !e.loadedFromDisk() || !(e.getEntity() instanceof Mob mob)) return;
        EchoLink l = link(mob);
        if (!l.isEcho()) return;
        UUID known = LIVE.get(l.echoId());
        if (!l.duty() || (known != null && !known.equals(mob.getUUID()))) {
            e.setCanceled(true);
            return;
        }
        LIVE.put(l.echoId(), mob.getUUID());
        EchoGoals.install(mob, true);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) dismissField(sp, false);
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && HearthService.player(sp).fieldOut) {
            sp.level().getServer().execute(() -> {
                if (!sp.isRemoved()) summonField(sp);
            });
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && HearthService.player(sp).fieldOut) summonField(sp);
    }

    private static void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !HearthService.player(sp).fieldOut) return;
        dismissField(sp, false);
        summonField(sp);
    }

    /** The owner died: the field army fades with them and returns when they respawn. */
    public static void onOwnerDeath(ServerPlayer sp) {
        dismissField(sp, false);
    }

    private static void onServerTick(ServerTickEvent.Post e) {
        if (LINGERING.isEmpty()) return;
        var server = e.getServer();
        int tick = server.getTickCount();
        for (Iterator<Lingering> it = LINGERING.iterator(); it.hasNext(); ) {
            Lingering l = it.next();
            ServerLevel level = server.getLevel(l.dimension);
            if (level == null || level.getGameTime() > l.expires) {
                it.remove();
                continue;
            }
            if (tick % 4 == 0) {
                level.sendParticles(HearthContent.ECHO_WISP.get(), l.pos.x, l.pos.y + 0.6, l.pos.z, 3, 0.25, 0.5, 0.25, 0.01);
                if (tick % 20 == 0) level.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL, l.pos.x, l.pos.y + 0.5, l.pos.z, 1, 0.2, 0.2, 0.2, 0.01);
            }
        }
    }

    /** Duty upkeep and the chooser read this. */
    public static boolean isOut(net.minecraft.server.MinecraftServer server, UUID echoId) {
        return live(server, echoId) != null;
    }

    static void forget(UUID echoId) {
        LIVE.remove(echoId);
    }

    static void track(UUID echoId, Mob mob) {
        LIVE.put(echoId, mob.getUUID());
    }
}
