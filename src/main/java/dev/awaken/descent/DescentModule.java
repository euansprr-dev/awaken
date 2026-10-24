package dev.awaken.descent;

import dev.awaken.core.AwakenConfig;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.api.PowerCoreHooks;
import dev.awaken.descent.net.DescentPayloads;
import dev.awaken.descent.player.DeepPacks;
import dev.awaken.descent.player.DescentData;
import dev.awaken.descent.rules.Zones;
import dev.awaken.descent.spawn.DescentSpawner;
import dev.awaken.descent.well.Well;
import dev.awaken.descent.well.WellData;
import dev.awaken.progression.Curve;
import dev.awaken.progression.DaysEnd;
import dev.awaken.progression.Progression;
import dev.awaken.progression.RankHooks;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The Descent (SPEC section 8): the Well, ten strata and the Endless, their rules, the Waystones, the Deep Pack, Grave
 * Echoes and the Strain of climbing. Feature flag {@link AwakenConfig#DESCENT}.
 */
public final class DescentModule {
    private DescentModule() {}

    private static WellData well;
    private static final java.util.Set<java.util.UUID> WELL_NOTICED = new java.util.HashSet<>();

    public static void init(IEventBus modBus) {
        DescentContent.init(modBus);
        modBus.addListener(DescentModule::registerPayloads);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(DescentModule::onServerStarted);
        bus.addListener(DescentModule::onServerStopping);
        bus.addListener(DescentModule::onServerTick);
        bus.addListener(DescentModule::onPlayerTick);
        bus.addListener(DeepPacks::onPickup);
        bus.addListener(EventPriority.LOWEST, DescentModule::onDeath);
        bus.addListener(DescentModule::onRespawnPosition);
        bus.addListener(DescentModule::onRespawn);
        bus.addListener(DescentModule::onChangedDimension);
        bus.addListener(DescentModule::onLogin);
        bus.addListener(DescentModule::onLogout);
        bus.addListener(DescentModule::onIncomingDamage);
        bus.addListener(DescentModule::onBreak);
        bus.addListener(DescentModule::onPlace);
        bus.addListener(DescentModule::onJump);
        bus.addListener(DescentModule::onChangeTarget);
        bus.addListener(DescentModule::onJoinLevel);
        bus.addListener(DescentModule::onWake);
        Signal.onAction("descent", (player, action, accepted) -> {
            if (action.startsWith("descent:ascend:")) DescentTravel.onAscendAnswer(player, action, accepted);
        });
        DaysEnd.addWaiting(DescentModule::waitingLine);
        dev.awaken.core.api.DescentHooks.install(new dev.awaken.core.api.DescentHooks.Access() {
            @Override public java.util.List<Integer> litWaystones(net.minecraft.server.level.ServerPlayer p) { return java.util.List.copyOf(DescentSync.data(p).lit); }
            @Override public boolean ride(net.minecraft.server.level.ServerPlayer p, int stratum) { return Descent.ride(p, stratum); }
            @Override public boolean inDescent(net.minecraft.server.level.ServerPlayer p) { return Descent.isInDescent(p); }
        });
        RankHooks.onRankUp((player, rank) -> {
            int before = DescentSync.data(player).deepest;
            DescentSync.push(player);
            int open = DescentSync.openTo(player);
            if (open > Math.max(1, before) && open <= Strata.COUNT && Strata.def(open).opensAt() == rank) {
                Signal.toast(player, Component.literal("A new stratum opened below: " + Strata.label(open)));
            }
        });
        DescentCommands.register();
        DescentTests.register();
        dev.awaken.descent.well.WellTests.register();
    }

    /** Feature flag; tolerant of being asked before the world config is loaded. */
    public static boolean enabled() {
        try {
            return AwakenConfig.DESCENT.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static AwakenConfig.Difficulty difficulty() {
        try {
            return AwakenConfig.DIFFICULTY.get();
        } catch (IllegalStateException e) {
            return AwakenConfig.Difficulty.HUNTER;
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToClient(DescentPayloads.Sync.TYPE, DescentPayloads.Sync.STREAM_CODEC);
        r.playToClient(DescentPayloads.Hud.TYPE, DescentPayloads.Hud.STREAM_CODEC);
        r.playToClient(DescentPayloads.OpenTravel.TYPE, DescentPayloads.OpenTravel.STREAM_CODEC);
        r.playToServer(DescentPayloads.Travel.TYPE, DescentPayloads.Travel.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) DescentWaystones.travel(sp, msg.depth());
        });
    }

    // ---- server lifecycle -------------------------------------------------------------------------------------------

    private static void onServerStarted(ServerStartedEvent event) {
        well = WellData.get(event.getServer());
        Well.ensure(event.getServer());
    }

    public static void onWellBuilt(MinecraftServer server) {
        well = WellData.get(server);
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        WELL_NOTICED.clear();
        Well.reset();
        Zones.clear();
        DescentTravel.clearAll();
        well = null;
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        Well.tick(event.getServer());
        Zones.tick(event.getServer());
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        WellData w = well;
        if (w == null || !w.built() || sp.level().dimension() != Level.OVERWORLD) return;
        if (Well.inShaft(w, sp.getX(), sp.getY(), sp.getZ())) {
            sp.resetFallDistance();
            DescentTravel.enterWell(sp);
        } else if (sp.tickCount % 20 == 0 && sp.blockPosition().distSqr(w.center()) < 28 * 28) {
            if (sp.tickCount % 200 == 0) {
                sp.level().playSound(null, w.x, w.y - 8, w.z, DescentContent.SND_WELL.get(), SoundSource.AMBIENT, 0.8F, 0.7F);
            }
            if (DescentSync.data(sp).visited.isEmpty() && WELL_NOTICED.add(sp.getUUID())) {
                Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("The Well"))
                        .body(Component.literal("A Precursor ring around a shaft with no bottom you can see. Far below, faint lights."))
                        .row(Component.literal("Down"), Component.literal("Jump in, or use the lift on the rim"))
                        .footer(Component.literal("The Descent · stratum 1 is open to everyone"))
                        .ttl(20 * 10).build());
            }
        }
    }

    // ---- players ----------------------------------------------------------------------------------------------------

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) DescentSync.push(sp);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        Zones.left(sp);
        Zones.forget(sp.getUUID());
        DescentTravel.forget(sp.getUUID());
        DescentWaystones.forget(sp.getUUID());
        DeepPacks.forget(sp.getUUID());
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        boolean wasIn = Strata.isDescent(event.getFrom()), nowIn = Strata.isDescent(event.getTo());
        if (wasIn && !nowIn) {
            DeepPacks.bank(sp, "You climbed out.");
            DescentData d = DescentSync.data(sp);
            d.visitDepth = 0;
            Zones.left(sp);
            DescentSync.push(sp);
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) return;
        if (dead instanceof ServerPlayer sp) {
            if (Strata.isDescent(level.dimension()) && enabled()) {
                DeepPacks.onDeath(sp);
                Zones.left(sp);
            }
            return;
        }
        if (!Strata.isDescent(level.dimension())) return;
        Zones.onMobDeath(level, dead);
        MobRole role = dead.getData(CoreApi.MOB_ROLE.get());
        String boss = role.bossId().orElse("");
        if (Descent.FINALE_BOSS.equals(boss) || DescentSpawner.has(dead, DescentSpawner.FLAG_FINALE)) {
            for (ServerPlayer p : level.players()) if (p.distanceToSqr(dead) < 128 * 128) Descent.completeSeed(p);
            return;
        }
        int depth = Strata.depthAt(level.dimension(), dead.blockPosition());
        if (DescentSpawner.has(dead, DescentSpawner.FLAG_GUARDIAN) || (!boss.isEmpty() && boss.startsWith("awaken:guardian_"))) {
            for (ServerPlayer p : level.players()) if (p.distanceToSqr(dead) < 64 * 64) guardianDefeated(p, depth);
            return;
        }
        if (DescentSpawner.isOurs(dead) && event.getSource().getEntity() instanceof ServerPlayer killer) {
            double f = role.role() == MobRole.Role.ELITE ? 0.05 : 0.012;
            double xp = Curve.xpToNext(Strata.contentLevel(depth)) * f;
            Progression.grantXp(killer, xp, Progression.XpSource.DESCENT);
            // the party nearby shares the kill (SPEC "Together"): each their own share
            for (ServerPlayer mate : dev.awaken.core.api.Parties.sharing(killer, dead)) {
                Progression.grantXp(mate, xp, Progression.XpSource.DESCENT);
            }
        }
    }

    private static void guardianDefeated(ServerPlayer p, int depth) {
        DescentData d = DescentSync.data(p);
        Progression.grantLevels(p, 0.4, Progression.XpSource.DESCENT);
        if (!d.guardians.add(depth)) return;
        Progression.markNewThing(p, "guardian");
        Progression.bestMoment(p, "Defeated the Guardian of " + Strata.shortName(depth));
        Progression.count(p, "awaken:guardians_defeated");
        // first-kill reward: carried in the Deep Pack until you rest or climb out
        var pack = DescentSync.pack(p);
        ItemStack core = PowerCoreHooks.roll(Progression.rank(p), p.getRandom());
        if (!core.isEmpty()) pack.insert(core);
        pack.insert(new ItemStack(CoreApi.ESSENCE_SHARD.get(), Math.min(64, 2 + depth * 2)));
        DescentSync.packChanged(p);
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Guardian defeated"))
                .row(Component.literal("Stratum"), Component.literal(Strata.label(depth)))
                .row(Component.literal("Spoils"), Component.literal("In your Deep Pack · rest at the Waystone to keep them"))
                .priority(5).ttl(20 * 8).build());
        DescentSync.push(p);
    }

    private static void onRespawnPosition(PlayerRespawnPositionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || event.isFromEndFight() || !enabled()) return;
        DescentData d = DescentSync.data(sp);
        if (!d.diedInDescent || d.checkpoint < 1) return;
        ServerLevel level = sp.level().getServer().getLevel(Strata.dimensionOf(d.checkpoint));
        if (level == null) return;
        BlockPos pos = DescentTravel.arrivalPos(d.checkpoint, DescentTravel.Arrival.CAMP);
        event.setTeleportTransition(new TeleportTransition(level, Vec3.atBottomCenterOf(pos), Vec3.ZERO, 90, 0,
                TeleportTransition.DO_NOTHING));
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || event.isEndConquered()) return;
        DescentData d = DescentSync.data(sp);
        if (!d.diedInDescent) {
            DescentSync.push(sp);
            return;
        }
        d.diedInDescent = false;
        int depth = DescentTravel.depthOf(sp);
        if (depth > 0) DescentTravel.onArrive(sp, depth, DescentTravel.Arrival.CAMP, depth, false);
        DeepPacks.afterRespawn(sp);
        DescentSync.push(sp);
    }

    private static void onWake(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || event.wakeImmediately()) return;
        if (!Strata.isDescent(sp.level().dimension())) return;
        Progression.count(sp, "awaken:latent/slept_in_descent");
        Progression.count(sp, "awaken:slept_in_descent");
        int depth = DescentTravel.depthOf(sp);
        if (depth > 0 && Descent.nearCamp(sp, depth, 10)) {
            DescentData d = DescentSync.data(sp);
            if (d.lit.contains(depth)) {
                d.checkpoint = depth;
                DescentWaystones.rest(sp);
                DescentSync.push(sp);
            }
        }
    }

    // ---- combat, noise and arrival safety ---------------------------------------------------------------------------

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim instanceof ServerPlayer sp) {
            if (event.getSource().is(DamageTypeTags.IS_FALL) && DescentTravel.inGrace(sp)) {
                event.setCanceled(true);
                return;
            }
            Zones.noise(sp, 8);
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) Zones.noise(attacker, 16);
    }

    private static void onBreak(BreakBlockEvent event) {
        if (event.getPlayer() instanceof ServerPlayer sp) Zones.noise(sp, 15);
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) Zones.noise(sp, 5);
    }

    private static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && !sp.isShiftKeyDown()) Zones.noise(sp, 3);
    }

    private static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !(event.getNewAboutToBeSetTarget() instanceof ServerPlayer p)) return;
        if (!DescentSpawner.has(mob, DescentSpawner.FLAG_LISTENER)) return;
        if (!Zones.listenerMayTarget(mob, p)) event.setCanceled(true);
    }

    private static void onJoinLevel(EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel level)) return;
        if (event.getEntity() instanceof LivingEntity e && Strata.isDescent(level.dimension()) && DescentSpawner.isOurs(e)) {
            Zones.track(level, e);
        }
    }

    // ---- Day's End --------------------------------------------------------------------------------------------------

    private static String waitingLine(ServerPlayer player) {
        if (!enabled()) return null;
        DescentData d = DescentSync.data(player);
        if (d.visited.isEmpty()) {
            return well != null && well.built() ? "The Well. Faint lights, far below." : null;
        }
        int open = DescentSync.openTo(player);
        for (int n = 2; n <= Math.min(open, Strata.COUNT); n++) {
            if (!d.visited.contains(n)) return Strata.label(n) + " is open below you";
        }
        if (open > Strata.COUNT && !d.visited.contains(Strata.ENDLESS_BASE + 1)) return "The Endless is open below the Seed";
        return null;
    }
}
