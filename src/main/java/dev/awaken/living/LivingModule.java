package dev.awaken.living;

import dev.awaken.core.api.LivingHooks;
import dev.awaken.living.combat.Creatures;
import dev.awaken.living.combat.Hits;
import dev.awaken.living.combat.KillXp;
import dev.awaken.living.combat.LevelMoments;
import dev.awaken.living.combat.LootPillars;
import dev.awaken.living.combat.XpStream;
import dev.awaken.living.region.RegionService;
import dev.awaken.living.region.RegionTracker;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.progression.XpBudget;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The Living Wilds (SPEC §21): ordinary survival in a world that's always up to something. Phase B is here: the region
 * map ({@code region}, API {@code living.api.Regions}), creature levels and kill XP by level and role, and the events the
 * client's Holo combat layer is built from ({@code combat}; plates, glyphs, the XP stream, the level moment and loot
 * pillars draw in {@code living.client}). Later phases add Wayfarers and quests, the World Pulse, populations and camps,
 * crafts, wonders and villages, each in its own package.
 */
public final class LivingModule {
    private LivingModule() {}

    /** The budget source of kill XP in the open world (SPEC §21.3). */
    public static final String HUNT = "hunt";
    /** Share of neutral and hunter spawns that are elites (SPEC §21.0.6). */
    public static final float ELITE_CHANCE = 0.05F;

    /**
     * The share of neutral and hunter spawns that are elites on land of this level (SPEC §21.10): none roam the heartland
     * (an elite there is a den's guard), 2% in D land, 4% in C, 5% from B. An elite was 4.4 times a skeleton's health at the
     * Commons' wall, which killed a level-1 player; the land's rank, not a flat share, decides.
     */
    public static float eliteChance(int landLevel) {
        return switch (dev.awaken.living.region.RegionMap.rankOf(landLevel)) {
            case 0 -> 0F;
            case 1 -> 0.02F;
            case 2 -> 0.04F;
            default -> ELITE_CHANCE;
        };
    }

    public static void init(IEventBus modBus) {
        LivingContent.register(modBus);
        modBus.addListener(net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent.class, dev.awaken.living.combat.WildKin::attributes);
        dev.awaken.living.quest.Relics.register(modBus);
        dev.awaken.living.wayfarer.Scenes.init();
        FirstThread.init(); // the first thread out of Carillon, and chains on the Now line (SPEC §27.6)
        FirstScenes.init(); // its High Gate shot and Ilse's scene by the fire
        IssueScene.init(); // Hale's issue: the first weapon, free (docs/arms-progression/PLAN.md §3.3)
        modBus.addListener(LivingNet::register);
        XpBudget.register(HUNT, 0.10);
        XpBudget.register("pulse", 0.15);
        XpBudget.register("camp", 0.10);
        // the crafts' budgets, as pacing_sim (SPEC §21.9)
        XpBudget.register("forage", 0.012);
        XpBudget.register("mine", 0.012);
        XpBudget.register("fish", 0.012);
        XpBudget.register("cook", 0.007);
        XpBudget.register("smith", 0.007);
        dev.awaken.living.craft.CraftItems.register(modBus);
        Progression.addPerkSource(dev.awaken.living.craft.CraftEvents::perk);
        dev.awaken.core.PlayDays.onNewPlayDay(dev.awaken.living.camp.Camps::onNewDay);
        Progression.addPerkSource(dev.awaken.living.pulse.Pulse::lucidity);
        LivingHooks.install(new LivingHooks.Bridge() {
            @Override
            public boolean paysKills() {
                return true;
            }

            @Override
            public boolean levelMoment(ServerPlayer player, int level, int gained) {
                return LevelMoments.offer(player, level, gained);
            }

            @Override
            public boolean ledgered(net.minecraft.server.level.ServerLevel level, String speciesId) {
                return dev.awaken.living.population.Ledger.ledgered(speciesId) && dev.awaken.living.population.Ledger.herdsOn(level);
            }
        });
        Progression.onXpGranted(XpStream::granted);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(EventPriority.LOW, FinalizeSpawnEvent.class, Creatures::onFinalizeSpawn);
        bus.addListener(EntityJoinLevelEvent.class, Creatures::onJoin);
        bus.addListener(EventPriority.HIGHEST, net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre.class, dev.awaken.living.combat.MarkEvents::onDamage);
        bus.addListener(net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck.class, dev.awaken.living.wonder.WonderWatch::onSpawnCheck);
        bus.addListener(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post.class, dev.awaken.living.wonder.WonderWatch::onPlayerTick);
        bus.addListener(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post.class, dev.awaken.living.combat.MarkEvents::onPlayerTick);
        bus.addListener(net.neoforged.neoforge.event.tick.ServerTickEvent.Post.class, dev.awaken.living.combat.MarkEvents::onTick);
        bus.addListener(net.neoforged.neoforge.event.PlayLevelSoundEvent.AtPosition.class, dev.awaken.living.combat.MarkEvents::onSound);
        bus.addListener(LivingIncomingDamageEvent.class, Creatures::onIncomingDamage);
        bus.addListener(LivingDeathEvent.class, KillXp::onDeath);
        bus.addListener(net.neoforged.neoforge.event.entity.living.LivingDropsEvent.class, dev.awaken.living.combat.GameDrops::onDrops);
        bus.addListener(EventPriority.LOWEST, CriticalHitEvent.class, Hits::onCrit);
        bus.addListener(LivingDamageEvent.Post.class, Hits::onDamage);
        bus.addListener(LivingDropsEvent.class, LootPillars::onDrops);
        bus.addListener(LivingDeathEvent.class, dev.awaken.living.quest.QuestEngine::onDeath);
        // the Wayfarers' camp keeps its ground: no monster spawns, none hunts there (SPEC §27.6)
        bus.addListener(net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck.class, dev.awaken.living.wayfarer.Camp::onSpawnCheck);
        bus.addListener(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent.class, dev.awaken.living.wayfarer.Camp::onTarget);
        bus.addListener(dev.awaken.living.quest.QuestBook::addReload);
        bus.addListener(dev.awaken.living.bounty.Bounties::addReload);
        bus.addListener(dev.awaken.living.pulse.HappeningBook::addReload);
        bus.addListener(LivingDropsEvent.class, dev.awaken.living.pulse.Pulse::onDrops);
        bus.addListener(LivingDeathEvent.class, e -> dev.awaken.living.population.Ledger.onDeath(e.getEntity()));
        bus.addListener(LivingDeathEvent.class, e -> dev.awaken.living.camp.Camps.onDeath(e.getEntity()));
        // crafts
        bus.addListener(LivingDeathEvent.class, dev.awaken.living.craft.CraftEvents::onDeath);
        bus.addListener(LivingDropsEvent.class, dev.awaken.living.craft.CraftEvents::onDrops);
        bus.addListener(EventPriority.LOW, CriticalHitEvent.class, dev.awaken.living.craft.CraftEvents::onCrit);
        bus.addListener(LivingIncomingDamageEvent.class, dev.awaken.living.craft.CraftEvents::onIncoming);
        bus.addListener(net.neoforged.neoforge.event.level.block.BreakBlockEvent.class, dev.awaken.living.craft.CraftEvents::onBreak);
        bus.addListener(net.neoforged.neoforge.event.level.BlockDropsEvent.class, dev.awaken.living.craft.CraftEvents::onBlockDrops);
        bus.addListener(PlayerEvent.BreakSpeed.class, dev.awaken.living.craft.CraftEvents::onBreakSpeed);
        bus.addListener(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock.class, dev.awaken.living.craft.CraftEvents::onUseBlock);
        bus.addListener(net.neoforged.neoforge.event.entity.player.ItemFishedEvent.class, dev.awaken.living.craft.CraftEvents::onFished);
        bus.addListener(net.neoforged.neoforge.event.AnvilUpdateEvent.class, dev.awaken.living.craft.CraftEvents::onAnvil);
        bus.addListener(net.neoforged.neoforge.event.entity.player.AnvilCraftEvent.Post.class, dev.awaken.living.craft.CraftEvents::onAnvilDone);
        bus.addListener(PlayerEvent.ItemCraftedEvent.class, dev.awaken.living.craft.CraftEvents::onCrafted);
        bus.addListener(PlayerEvent.ItemSmeltedEvent.class, dev.awaken.living.craft.CraftEvents::onSmelted);
        bus.addListener(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish.class, dev.awaken.living.craft.CraftEvents::onEat);
        bus.addListener(EntityJoinLevelEvent.class, e -> {
            if (!e.getLevel().isClientSide() && e.getLevel() instanceof net.minecraft.server.level.ServerLevel sl && sl.dimension() == net.minecraft.world.level.Level.OVERWORLD)
                dev.awaken.living.population.Ledger.onJoin(e.getEntity(), sl.getServer());
        });
        bus.addListener(LivingDeathEvent.class, dev.awaken.living.bounty.Bounties::onDeath);
        bus.addListener(EventPriority.HIGH, LivingDropsEvent.class, dev.awaken.living.quest.Relics::onDrops);
        bus.addListener(PlayerEvent.PlayerRespawnEvent.class, dev.awaken.living.quest.Relics::onRespawn);
        dev.awaken.signal.Signal.onAction(dev.awaken.living.wayfarer.Wayfarers.ACTION, dev.awaken.living.wayfarer.Wayfarers::answer);
        dev.awaken.signal.Signal.onAction(dev.awaken.living.bounty.Bounties.ACTION, dev.awaken.living.bounty.Bounties::answer);
        modBus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent e) ->
                e.put(LivingContent.WAYFARER.get(), dev.awaken.living.wayfarer.Wayfarer.createAttributes().build()));
        bus.addListener(ServerTickEvent.Post.class, e -> {
            RegionTracker.tick(e.getServer());
            if (e.getServer().getTickCount() % 20 == 0) for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) dev.awaken.living.quest.QuestEngine.poll(p);
            XpStream.tick(e.getServer());
            shownTick(e.getServer());
            // the GameTest server keeps the land quiet: no scene, ledger body or camp comes to a test's players on its own
            // (the tests drive them); happenings already under way still move on
            boolean quiet = e.getServer() instanceof net.minecraft.gametest.framework.GameTestServer;
            if (shown(e.getServer())) {
                if (!quiet) dev.awaken.living.wayfarer.Scenes.tick(e.getServer());
                dev.awaken.living.pulse.Pulse.tick(e.getServer());
                if (!quiet) dev.awaken.living.population.Ledger.tick(e.getServer());
                if (!quiet) dev.awaken.living.camp.Camps.tick(e.getServer());
                dev.awaken.living.wayfarer.Camp.ward(e.getServer());
            }
            dev.awaken.living.quest.Relics.wardTick(e.getServer());
            if (shown(e.getServer())) dev.awaken.living.craft.CraftEvents.tick(e.getServer());
        });
        bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                dev.awaken.core.AwakenNet.send(sp, new LivingNet.Shown(shown(sp.level().getServer())));
                dev.awaken.living.quest.QuestEngine.journal(sp);
            }
        });
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            var id = e.getEntity().getUUID();
            RegionTracker.forget(id);
            XpStream.forget(id);
            Hits.forget(id);
        });
        // the region map is made on the main thread at start, so worldgen threads find it ready (the signs ask)
        bus.addListener(net.neoforged.neoforge.event.server.ServerStartedEvent.class, e -> RegionService.map(e.getServer()));
        bus.addListener(net.neoforged.neoforge.event.server.ServerStartedEvent.class, e -> {
            // the GameTest server keeps the old natural herds for the fauna tests (living_ledger checks the rule itself)
            if (e.getServer() instanceof net.minecraft.gametest.framework.GameTestServer) {
                e.getServer().overworld().getGameRules().set(LivingContent.LIVING_HERDS.get(), false, e.getServer());
            }
        });
        bus.addListener(ServerStoppedEvent.class, e -> {
            dev.awaken.living.wonder.Wonders.reset();
            dev.awaken.living.wonder.Secrets.reset();
            RegionService.reset();
            RegionTracker.clear();
        });
        LivingCommands.register();
        LivingTests.register();
    }

    /** Whether this world shows the band's layer: plates, numbers, the stream, the moment (pure survival hides them). */
    public static boolean shown(MinecraftServer server) {
        try {
            return !server.overworld().getGameRules().get(LivingContent.PURE_SURVIVAL.get());
        } catch (RuntimeException notReady) {
            return true;
        }
    }

    private static boolean lastShown = true;

    private static void shownTick(MinecraftServer server) {
        if (server.getTickCount() % 100 != 0) return;
        boolean now = shown(server);
        if (now == lastShown) return;
        lastShown = now;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) dev.awaken.core.AwakenNet.send(p, new LivingNet.Shown(now));
    }

    /**
     * The open world pays by rank the way the gates do (SPEC §21.3): C is the long climb, so hunting, crafts and camps pay
     * 0.55 there. Mirrors {@code WILDS_RANK} in tools/sim/pacing_sim.py.
     */
    public static double wildsRank(Rank rank) {
        return rank == Rank.C ? 0.55 : 1.0;
    }
}
