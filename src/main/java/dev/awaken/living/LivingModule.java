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

    public static void init(IEventBus modBus) {
        LivingContent.register(modBus);
        modBus.addListener(LivingNet::register);
        XpBudget.register(HUNT, 0.10);
        LivingHooks.install(new LivingHooks.Bridge() {
            @Override
            public boolean paysKills() {
                return true;
            }

            @Override
            public boolean levelMoment(ServerPlayer player, int level, int gained) {
                return LevelMoments.offer(player, level, gained);
            }
        });
        Progression.onXpGranted(XpStream::granted);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(EventPriority.LOW, FinalizeSpawnEvent.class, Creatures::onFinalizeSpawn);
        bus.addListener(EntityJoinLevelEvent.class, Creatures::onJoin);
        bus.addListener(LivingIncomingDamageEvent.class, Creatures::onIncomingDamage);
        bus.addListener(LivingDeathEvent.class, KillXp::onDeath);
        bus.addListener(EventPriority.LOWEST, CriticalHitEvent.class, Hits::onCrit);
        bus.addListener(LivingDamageEvent.Post.class, Hits::onDamage);
        bus.addListener(LivingDropsEvent.class, LootPillars::onDrops);
        bus.addListener(LivingDeathEvent.class, dev.awaken.living.quest.QuestEngine::onDeath);
        bus.addListener(dev.awaken.living.quest.QuestBook::addReload);
        bus.addListener(ServerTickEvent.Post.class, e -> {
            RegionTracker.tick(e.getServer());
            if (e.getServer().getTickCount() % 20 == 0) for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) dev.awaken.living.quest.QuestEngine.poll(p);
            XpStream.tick(e.getServer());
            shownTick(e.getServer());
        });
        bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) dev.awaken.core.AwakenNet.send(sp, new LivingNet.Shown(shown(sp.level().getServer())));
        });
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            var id = e.getEntity().getUUID();
            RegionTracker.forget(id);
            XpStream.forget(id);
            Hits.forget(id);
        });
        bus.addListener(ServerStoppedEvent.class, e -> {
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
