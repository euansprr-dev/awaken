package dev.awaken.hearth;

import dev.awaken.core.CoreModule;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.hearth.town.Town;
import dev.awaken.hearth.town.TownNpc;
import dev.awaken.hearth.town.Townsfolk;
import dev.awaken.hearth.training.Training;
import dev.awaken.hearth.training.TrainingGhost;
import dev.awaken.hearth.trial.Trials;
import dev.awaken.hearth.trophy.Trophies;
import dev.awaken.progression.DaysEnd;
import dev.awaken.progression.RankHooks;
import dev.awaken.progression.Regimen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * HEARTH: the home base that grows into a town, from the Ember's first fire to the Anchor; the reading of the rooms you
 * build, Comfort and Rested; trophies, the Training Grounds and the Reassessment trials (Echoes are gone: SPEC §43.4). See
 * docs/modules/hearth.md and SPEC sections 4.4, 10 and 28.
 */
public final class HearthModule {
    private HearthModule() {}

    public static void init(IEventBus modBus) {
        HearthTrails.init();
        HearthContent.register(modBus);
        dev.awaken.hearth.kit.HomeKit.init(modBus);
        modBus.addListener(HearthModule::attributes);
        modBus.addListener(HearthModule::creativeTab);
        modBus.addListener(dev.awaken.hearth.net.HomeNet::register);

        var bus = NeoForge.EVENT_BUS;
        bus.addListener(Townsfolk::onReloadListeners);
        bus.addListener(Town::onJoin);
        bus.addListener(HearthModule::onDeath);
        bus.addListener(HearthModule::onServerTick);

        HearthService.init();
        // (W6) the people who live with you answer on the people engine; the household is kin to them
        dev.awaken.hearth.settle.HomeTalk.init();
        bus.addListener((net.neoforged.neoforge.event.server.ServerAboutToStartEvent e) -> dev.awaken.hearth.settle.HomeTalk.installVoice());
        Embers.init();
        Rested.init();
        RoomEffects.init();
        HomeCrafting.init();
        HearthNow.register();
        HearthTask.register(); // the home's steps on the task card (SPEC §28.2)
        dev.awaken.hearth.guide.Guide.init(modBus);
        Offers.init();
        Later.init();
        dev.awaken.hearth.echo.OldEchoes.init();
        Training.init();
        Trials.init();
        dev.awaken.hearth.request.Requests.init(modBus);

        HearthHooks.install(new HearthHooks.Lookup() {
            @Override
            public net.minecraft.world.item.ItemStack keepsake(String id, String name, long day) {
                return dev.awaken.hearth.trophy.Trophies.make("awaken:keepsake/" + id, name, day);
            }

            @Override
            public Optional<BlockPos> nearest(ServerLevel level, BlockPos pos, int radius) {
                return HearthData.get(level.getServer()).nearest(pos, level.dimension().identifier().toString(), radius).map(h -> h.pos);
            }

            @Override
            public Optional<BlockPos> hearthOf(ServerLevel level, UUID player) {
                return HearthData.get(level.getServer()).home(player).map(h -> h.pos);
            }

            @Override
            public boolean inside(ServerLevel level, BlockPos pos) {
                return HearthData.get(level.getServer()).containing(pos, level.dimension().identifier().toString()).isPresent();
            }

            @Override
            public boolean belongs(ServerLevel level, UUID player, BlockPos anchor) {
                return HearthData.get(level.getServer()).at(anchor, HearthService.OVERWORLD).map(h -> h.belongs(player)).orElse(false);
            }

            @Override
            public java.util.List<BlockPos> hearthsOf(ServerLevel level, UUID player) {
                java.util.List<BlockPos> out = new java.util.ArrayList<>(2);
                for (HearthRecord h : HearthData.get(level.getServer()).hearthsOf(player)) out.add(h.pos);
                return out;
            }

            @Override
            public java.util.Set<UUID> household(ServerLevel level, UUID player) {
                java.util.Set<UUID> out = new java.util.LinkedHashSet<>();
                for (HearthRecord h : HearthData.get(level.getServer()).hearthsOf(player)) out.addAll(h.everyone());
                out.remove(player);
                return out;
            }

            @Override
            public boolean tideCalm(ServerLevel level, BlockPos pos) {
                return RoomEffects.tideCalm(level, pos);
            }

            @Override
            public int machineCost(ServerLevel level, BlockPos pos, int amount) {
                return RoomEffects.machineCost(level, pos, amount);
            }

            @Override
            public void systemRespawn(net.minecraft.server.level.ServerPlayer player,
                                      net.minecraft.server.level.ServerPlayer.@org.jspecify.annotations.Nullable RespawnConfig to, boolean lent) {
                HearthService.systemRespawn(player, to, lent);
            }

            @Override
            public Optional<HearthHooks.MetalNeed> metalNeed(net.minecraft.server.level.ServerPlayer player) {
                return Upgrades.metalNeed(player);
            }

            @Override
            public java.util.List<BlockPos> within(ServerLevel level, BlockPos pos, int radius) {
                java.util.List<BlockPos> out = new java.util.ArrayList<>(1);
                String dim = level.dimension().identifier().toString();
                long r2 = (long) radius * radius;
                for (HearthRecord h : HearthData.get(level.getServer()).all()) {
                    if (!h.dimension.equals(dim)) continue;
                    long dx = h.pos.getX() - pos.getX(), dz = h.pos.getZ() - pos.getZ();
                    if (dx * dx + dz * dz <= r2) out.add(h.pos);
                }
                return out;
            }

            @Override
            public boolean greenhouse(net.minecraft.world.level.Level level, BlockPos pos) {
                // the client knows only its own home's greenhouses (synced boxes); the server reads every Hearth's rooms
                if (level.isClientSide()) return dev.awaken.hearth.player.ClientHome.home().inGreenhouse(pos);
                if (!(level instanceof ServerLevel sl) || sl.dimension() != net.minecraft.world.level.Level.OVERWORLD) return false;
                return HearthData.get(sl.getServer()).containing(pos, HearthService.OVERWORLD)
                        .flatMap(h -> h.roomAt(pos, dev.awaken.hearth.room.RoomType.GREENHOUSE)).isPresent();
            }

            @Override
            public boolean study(net.minecraft.server.level.ServerPlayer player) {
                return HearthService.sharedAt(player, player.blockPosition()).map(h -> h.hasRoom(dev.awaken.hearth.room.RoomType.STUDY)).orElse(false);
            }
        });
        Regimen.penaltyHandler = Trials::penalty;
        RankHooks.onRankUp((sp, rank) -> {
            if (!HearthService.enabled()) return;
            Town.onRankUp(sp, rank);
            Auras.refresh(sp);
            HearthViews.refresh(sp);
        });
        DaysEnd.addWaiting(sp -> HearthService.homeOf(sp)
                .filter(h -> h.owner.equals(sp.getUUID()) ? h.pendingCelebration >= 0 : h.memberCelebrations.containsKey(sp.getUUID()))
                .map(h -> "The town is waiting to celebrate you")
                .orElse(null));
        HearthCommands.register();
        HearthTests.register();
        HomeTests.register();
        RespawnTests.register();
        HearthTrailTests.register();
        dev.awaken.hearth.settle.SettleTests.register();
        TogetherHearthTests.register();
        dev.awaken.hearth.guide.GuideTests.register();
        dev.awaken.hearth.guide.CottageBuildTests.register();
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(HearthContent.TOWNSFOLK.get(), TownNpc.createAttributes().build());
        e.put(HearthContent.TRAINING_GHOST.get(), TrainingGhost.createAttributes().build());
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent e) {
        if (!e.getTabKey().equals(CoreModule.TAB_KEY)) return;
        for (var item : HearthContent.tabItems()) e.accept(item.get());
    }

    private static void onServerTick(ServerTickEvent.Post e) {
        if (e.getServer().getTickCount() % 100 == 7 && HearthService.enabled()) Trials.checkPending(e.getServer());
    }

    /** One death listener for the module: trophies. */
    private static void onDeath(LivingDeathEvent e) {
        if (!HearthService.enabled()) return;
        LivingEntity dead = e.getEntity();
        if (!(dead.level() instanceof ServerLevel level) || dead instanceof ServerPlayer) return;
        Entity cause = e.getSource().getEntity();
        ServerPlayer killer = cause instanceof ServerPlayer p ? p : null;
        if (killer == null && dead.getKillCredit() instanceof ServerPlayer p) killer = p;
        if (killer == null) return;
        boolean trialMob = dead.getExistingData(CoreApi.MOB_ROLE.get()).map(MobRole::gateId).filter(g -> g.startsWith("trial:")).isPresent();
        if (trialMob) return;
        Trophies.onDeath(e, killer, level);
    }
}
