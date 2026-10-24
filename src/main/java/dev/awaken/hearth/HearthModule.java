package dev.awaken.hearth;

import dev.awaken.core.CoreModule;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.hearth.echo.Echoes;
import dev.awaken.hearth.net.HearthNet;
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
 * HEARTH: the home base that grows into a town, the Echo army, trophies, the Training Grounds and the Reassessment
 * trials. See docs/modules/hearth.md and SPEC sections 4.4 and 10.
 */
public final class HearthModule {
    private HearthModule() {}

    public static void init(IEventBus modBus) {
        HearthContent.register(modBus);
        modBus.addListener(HearthModule::attributes);
        modBus.addListener(HearthModule::creativeTab);
        modBus.addListener(HearthNet::register);

        var bus = NeoForge.EVENT_BUS;
        bus.addListener(Townsfolk::onReloadListeners);
        bus.addListener(Town::onJoin);
        bus.addListener(HearthModule::onDeath);
        bus.addListener(HearthModule::onServerTick);

        HearthService.init();
        HearthNow.register();
        dev.awaken.hearth.guide.Guide.init(modBus);
        Offers.init();
        Later.init();
        Echoes.init();
        Training.init();
        Trials.init();
        dev.awaken.hearth.request.Requests.init(modBus);

        HearthHooks.install(new HearthHooks.Lookup() {
            @Override
            public Optional<BlockPos> nearest(ServerLevel level, BlockPos pos, int radius) {
                return HearthData.get(level.getServer()).nearest(pos, level.dimension().identifier().toString(), radius).map(h -> h.pos);
            }

            @Override
            public Optional<BlockPos> hearthOf(ServerLevel level, UUID owner) {
                return HearthData.get(level.getServer()).of(owner).map(h -> h.pos);
            }

            @Override
            public boolean inside(ServerLevel level, BlockPos pos) {
                return HearthData.get(level.getServer()).containing(pos, level.dimension().identifier().toString()).isPresent();
            }
        });
        Regimen.penaltyHandler = Trials::penalty;
        RankHooks.onRankUp((sp, rank) -> {
            if (!HearthService.enabled()) return;
            Town.onRankUp(sp, rank);
            Auras.refresh(sp);
            HearthViews.refresh(sp);
        });
        DaysEnd.addWaiting(sp -> HearthService.hearthOf(sp)
                .filter(h -> h.pendingCelebration >= 0)
                .map(h -> "The town is waiting to celebrate you")
                .orElse(null));
        HearthCommands.register();
        HearthTests.register();
        dev.awaken.hearth.guide.GuideTests.register();
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

    /** One death listener for the module: Echo bookkeeping, trophies and lingering Echoes. */
    private static void onDeath(LivingDeathEvent e) {
        if (!HearthService.enabled()) return;
        LivingEntity dead = e.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) return;
        if (dead instanceof ServerPlayer sp) {
            Echoes.onOwnerDeath(sp);
            return;
        }
        if (Echoes.isEcho(dead)) {
            Echoes.onEchoDeath(dead, level);
            return;
        }
        Entity cause = e.getSource().getEntity();
        Entity direct = e.getSource().getDirectEntity();
        Entity echo = direct != null && Echoes.isEcho(direct) ? direct : cause != null && Echoes.isEcho(cause) ? cause : null;
        if (echo != null) Echoes.creditKill(echo, dead);
        ServerPlayer killer = cause instanceof ServerPlayer p ? p : echo != null ? Echoes.owner(echo) : null;
        if (killer == null && dead.getKillCredit() instanceof ServerPlayer p) killer = p;
        if (killer == null) return;
        boolean trialMob = dead.getExistingData(CoreApi.MOB_ROLE.get()).map(MobRole::gateId).filter(g -> g.startsWith("trial:")).isPresent();
        if (trialMob) return;
        Trophies.onDeath(e, killer, level);
        Echoes.onKill(dead, killer, level);
    }
}
