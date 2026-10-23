package dev.awaken.progression;

import com.mojang.serialization.Codec;
import dev.awaken.Awaken;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.PlayDays;
import dev.awaken.progression.content.Content;
import dev.awaken.progression.net.ProgressionPayloads;
import dev.awaken.signal.Signal;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Levels, stats, ranks, Truths, Titles, Latents, Paths, Directives, Regimen (SPEC section 4). */
public final class ProgressionModule {
    private ProgressionModule() {}

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);

    public static final Supplier<AttachmentType<Profile>> PROFILE = ATTACHMENTS.register("profile", () -> AttachmentType
            .builder(Profile::new)
            .serialize(Profile.MAP_CODEC)
            .copyOnDeath()
            .sync((holder, to) -> holder == to, Profile.STREAM_CODEC)
            .build());

    /** Fraction of stats lost to the Strain of climbing (set by the Descent, decays over time). */
    public static final Supplier<AttachmentType<Double>> STRAIN = ATTACHMENTS.register("strain", () -> AttachmentType
            .builder(() -> 0.0)
            .serialize(Codec.DOUBLE.fieldOf("v"))
            .copyOnDeath()
            .build());

    public static void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        modBus.addListener(ProgressionModule::registerPayloads);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ProgressionModule::addReloadListeners);
        bus.addListener(ProgressionModule::onDatapackSync);
        bus.addListener(ProgressionModule::onLogin);
        bus.addListener(ProgressionModule::onRespawn);
        bus.addListener(ProgressionModule::onServerTick);
        bus.addListener(ProgressionModule::onPlayerTick);
        bus.addListener(ProgressionModule::onDeath);
        bus.addListener(ProgressionModule::onBreak);
        bus.addListener(ProgressionModule::onPlace);
        bus.addListener(ProgressionModule::onIncomingDamage);
        bus.addListener(ProgressionModule::onCrit);
        bus.addListener(ProgressionModule::onFall);
        bus.addListener(ProgressionModule::onWake);
        bus.addListener(ProgressionModule::onTame);
        Signal.onAction(Directives.ACTION, Directives::answer);
        PlayDays.onMorning((server, day) -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                startDay(p, day);
                Regimen.morning(p);
                Directives.tryOffer(p, "morning");
            }
        });
        ProgressionCommands.register();
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToClient(ProgressionPayloads.ContentSync.TYPE, ProgressionPayloads.ContentSync.STREAM_CODEC);
        r.playToServer(ProgressionPayloads.StatusAction.TYPE, ProgressionPayloads.StatusAction.STREAM_CODEC, (msg, ctx) -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            switch (msg.kind()) {
                case "spend" -> {
                    try { Progression.spendPoint(sp, Stat.valueOf(msg.arg())); } catch (IllegalArgumentException ignored) {}
                }
                case "declare" -> Progression.declareTruth(sp, msg.arg());
                case "undeclare" -> Progression.undeclareTruth(sp, msg.arg());
                case "title" -> Progression.equipTitle(sp, msg.arg());
                case "path" -> Progression.choosePath(sp, msg.arg());
                default -> {}
            }
            Progression.syncNow(sp);
        });
    }

    private static void addReloadListeners(AddServerReloadListenersEvent event) {
        for (Content.Store<?> s : Content.ALL) event.addListener(Awaken.id("progression_" + s.folder), s.loader());
    }

    private static void onDatapackSync(OnDatapackSyncEvent event) {
        var msg = ProgressionPayloads.ContentSync.build();
        event.getRelevantPlayers().forEach(p -> PacketDistributor.sendToPlayer(p, msg));
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        Progression.applyAttributes(sp);
        Progression.syncNow(sp);
        startDay(sp, PlayDays.current(sp.level().getServer()));
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            Progression.applyAttributes(sp);
            Progression.syncNow(sp);
        }
    }

    static void startDay(ServerPlayer player, long day) {
        Profile p = Progression.profile(player);
        if (p.day.day() == day) return;
        p.day = new Profile.DayLog(day, p.level, 0, 0, "");
        Progression.markDirty(player);
    }

    // ---- ticking ----------------------------------------------------------------------------------------------------

    private static final Map<UUID, Double> HOME_XP_THIS_MINUTE = new HashMap<>();

    private static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % 20 == 0) Progression.flushDirty();
        if (server.getTickCount() % 1200 == 0) {
            HOME_XP_THIS_MINUTE.clear();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                Profile prof = Progression.profile(p);
                if (!prof.awakened()) continue;
                Progression.count(p, "awaken:play_ticks", 1200);
                if (prof.fractureUntil > 0 && prof.fractureUntil <= p.level().getGameTime()) {
                    prof.fractureUntil = 0;
                    Progression.applyAttributes(p);
                    Signal.toast(p, Component.literal("Fracture healed"));
                }
                if (Progression.minutesSinceNewThing(p) > 20 && p.getRandom().nextInt(4) == 0) {
                    Directives.tryOffer(p, "gap since last new thing");
                }
            }
        }
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || sp.tickCount % 20 != 0) return;
        Profile p = Progression.profile(sp);
        if (!p.awakened()) return;
        if (sp.isSprinting()) {
            double dist = Math.hypot(sp.getX() - sp.xo, sp.getZ() - sp.zo) * 20; // approx blocks/second → per second
            Progression.count(sp, Counters.SPRINT_CM, Math.round(dist * 100));
        }
        double strain = sp.getData(STRAIN.get());
        if (strain > 0) {
            double next = Math.max(0, strain - 0.05 / 30.0); // full Strain (5%) fades over ~10 minutes
            sp.setData(STRAIN.get(), next);
            if (next == 0) Progression.applyAttributes(sp);
        }
    }

    /** Home activity XP, capped at 1.5% of a level per real minute (SPEC section 4.3). */
    static void homeXp(ServerPlayer player, double fractionOfLevel) {
        double used = HOME_XP_THIS_MINUTE.getOrDefault(player.getUUID(), 0.0);
        double allowed = Math.max(0, Math.min(fractionOfLevel, 0.015 - used));
        if (allowed <= 0) return;
        HOME_XP_THIS_MINUTE.put(player.getUUID(), used + allowed);
        Progression.grantLevels(player, allowed, Progression.XpSource.HOME);
    }

    private static boolean isHome(Level level) {
        return level.dimension() == Level.OVERWORLD;
    }

    // ---- activity counters ------------------------------------------------------------------------------------------

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead instanceof ServerPlayer sp) {
            Progression.count(sp, Counters.DEATHS);
            var killer = event.getSource().getEntity();
            if (killer != null) {
                String type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(killer.getType()).toString();
                Progression.count(sp, "awaken:killed_by/" + type);
                Progression.count(sp, "awaken:killed_by_week/" + type);
            }
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer sp)) return;
        Profile p = Progression.profile(sp);
        if (!p.awakened()) return;
        Progression.count(sp, Counters.KILLS);
        p.day = new Profile.DayLog(p.day.day(), p.day.levelStart(), p.day.gates(), p.day.kills() + 1, p.day.best());
        if (sp.getMainHandItem().isEmpty()) Progression.count(sp, Counters.KILLS_UNARMED);
        if (dead instanceof Enemy && isHome(sp.level())) {
            Progression.count(sp, Counters.KILLS_OVERWORLD);
            homeXp(sp, 0.04 * Math.min(2.0, dead.getMaxHealth() / 20.0));
        }
    }

    private static void onBreak(BreakBlockEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer sp) || !Progression.profile(sp).awakened()) return;
        var state = event.getState();
        boolean ore = state.is(BlockTags.ORES);
        if (ore) {
            Progression.count(sp, Counters.ORES_MINED);
            if (isHome(sp.level())) homeXp(sp, 0.01);
        }
        Progression.count(sp, "awaken:blocks_broken");
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || !Progression.profile(sp).awakened()) return;
        Progression.count(sp, Counters.BLOCKS_PLACED);
        if (isHome(sp.level())) homeXp(sp, 0.0005);
    }

    private static void onTame(net.neoforged.neoforge.event.entity.living.AnimalTameEvent event) {
        if (event.getTamer() instanceof ServerPlayer sp && event.getAnimal() instanceof Wolf) {
            Progression.count(sp, Counters.WOLVES_TAMED);
        }
    }

    private static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || !Progression.profile(sp).awakened()) return;
        double resist = Progression.perk(sp, Perks.FALL_RESIST);
        if (resist > 0) event.setDamageMultiplier((float) (event.getDamageMultiplier() * Math.max(0, 1 - resist)));
        double dist = event.getDistance();
        if (dist > 3) sp.level().getServer().execute(() -> {
            if (sp.isAlive()) Progression.record(sp, Counters.FALL_SURVIVED_MAX, Math.round(dist));
        });
    }

    // ---- combat stats -----------------------------------------------------------------------------------------------

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            Profile p = Progression.profile(sp);
            if (!p.awakened()) return;
            double vit = p.stat(Stat.VIT) - Stat.START;
            double resist = Math.min(0.35, vit * 0.005);
            if (event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) resist += Progression.perk(sp, Perks.EXPLOSION_RESIST);
            event.setAmount((float) (event.getAmount() * Math.max(0.2, 1 - resist)));
            Progression.count(sp, Counters.DAMAGE_TAKEN, Math.max(1, Math.round(event.getAmount())));
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            Profile p = Progression.profile(attacker);
            if (!p.awakened()) return;
            double bonus = Progression.perk(attacker, Perks.DAMAGE_ABOVE_HALF_HP);
            if (bonus > 0 && attacker.getHealth() > attacker.getMaxHealth() / 2) {
                event.setAmount((float) (event.getAmount() * (1 + bonus)));
            }
        }
    }

    private static void onCrit(CriticalHitEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        Profile p = Progression.profile(sp);
        if (!p.awakened() || event.isCriticalHit()) return;
        double chance = Math.min(0.35, (p.stat(Stat.PER) - Stat.START) * 0.004 + Progression.perk(sp, Perks.CRIT_CHANCE));
        if (sp.getRandom().nextDouble() < chance) {
            event.setCriticalHit(true);
            event.setDamageMultiplier(1.6f);
        }
    }

    // ---- day's end --------------------------------------------------------------------------------------------------

    private static void onWake(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || event.wakeImmediately()) return;
        Profile p = Progression.profile(sp);
        if (!p.awakened() || p.day.day() < 0) return;
        DaysEnd.send(sp, p);
    }

    static ServerLevel overworld(ServerPlayer p) {
        return p.level().getServer().overworld();
    }

    static void debug(ServerPlayer p, String what) {
        DirectorLog.log(p, "progression", what);
    }

}
