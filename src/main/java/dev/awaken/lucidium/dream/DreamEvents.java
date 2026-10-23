package dev.awaken.lucidium.dream;

import dev.awaken.core.PlayDays;
import dev.awaken.lucidium.LucidiumContent;
import dev.awaken.lucidium.data.DreamState;
import dev.awaken.lucidium.data.LucidiumWorld;
import dev.awaken.lucidium.journal.DayJournal;
import dev.awaken.lucidium.plan.Nightmares;
import dev.awaken.lucidium.world.Cells;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Wires lucidium into the game: ticking, sleep, the journal's triggers, dream safety (no real death, no lost items, no
 * copied loot, nothing broken in a dream), logins mid-dream and the Lucidium cleanup.
 */
public final class DreamEvents {
    private DreamEvents() {}

    private static final Set<UUID> WAKE_ON_TICK = new HashSet<>();

    public static void init(IEventBus modBus) {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(DreamEvents::onServerTick);
        bus.addListener(DreamEvents::onPlayerTick);
        bus.addListener(Sleep::onWake);
        bus.addListener(Sleep::onSleepFinished);
        bus.addListener(EventPriority.LOWEST, DreamEvents::onDamagePre);
        bus.addListener(DreamEvents::onDamagePost);
        bus.addListener(DreamEvents::onIncomingDamage);
        bus.addListener(EventPriority.HIGHEST, DreamEvents::onDeathFirst);
        bus.addListener(EventPriority.LOW, DreamEvents::onDeath);
        bus.addListener(DreamEvents::onDrops);
        bus.addListener(DreamEvents::onBreak);
        bus.addListener(DreamEvents::onPlace);
        bus.addListener(DreamEvents::onToss);
        bus.addListener(DreamEvents::onExplosion);
        bus.addListener(DreamEvents::onTravel);
        bus.addListener(DreamEvents::onChangedDimension);
        bus.addListener(DreamEvents::onLogin);
        bus.addListener(DreamEvents::onLogout);
        bus.addListener(DreamEvents::onRightClickBlock);
        bus.addListener(DreamEvents::onServerStopping);
        bus.addListener(DreamEvents::onServerStopped);
        bus.addListener(DreamEvents::onServerAboutToStart);
        PlayDays.onMorning((server, day) -> Planner.morning());
    }

    private static boolean inLucidium(Level level) {
        return level.dimension() == LucidiumContent.LUCIDIUM;
    }

    // ---- ticks -------------------------------------------------------------------------------------------------------

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!WAKE_ON_TICK.isEmpty()) {
            for (UUID id : Set.copyOf(WAKE_ON_TICK)) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                WAKE_ON_TICK.remove(id);
                if (p != null && (Dreams.isDreaming(p) || inLucidium(p.level()))) Dreams.wake(p, Dreams.Wake.LOGIN);
            }
        }
        Dreams.tick(server);
        Sleep.tick(server);
        Visions.tick(server);
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        Sleep.onPlayerTick(sp);
        DayJournal.onPlayerTick(sp);
        Visions.checkFound(sp);
    }

    // ---- dream safety ------------------------------------------------------------------------------------------------

    /** A blow that would kill a dreamer wakes them instead (after every other module has adjusted the damage). */
    private static void onDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || !Dreams.isDreaming(sp)) return;
        if (event.getNewDamage() >= sp.getHealth()) {
            event.setNewDamage(Math.max(0, sp.getHealth() - 1));
            WAKE_DIED.add(sp.getUUID());
        }
    }

    private static final Set<UUID> WAKE_DIED = new HashSet<>();

    private static void onDamagePost(LivingDamageEvent.Post event) {
        LivingEntity e = event.getEntity();
        if (e instanceof ServerPlayer sp) {
            if (WAKE_DIED.remove(sp.getUUID()) && Dreams.isDreaming(sp)) {
                sp.level().getServer().execute(() -> Dreams.wake(sp, Dreams.Wake.DIED));
                return;
            }
            DayJournal.onHurt(sp);
            return;
        }
        // the mentor yields when worn down
        if (inLucidium(e.level()) && Dreams.isMentor(e) && e.getHealth() <= e.getMaxHealth() * 0.35f) {
            ActiveDream ad = Dreams.dreamOf(e);
            if (ad != null && event.getSource().getEntity() instanceof ServerPlayer sp) Mentors.end(ad, sp, true);
        }
    }

    /** The mentor's blows never finish you; when you'd fall, the duel ends instead. */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || !inLucidium(sp.level())) return;
        var src = event.getSource().getEntity();
        if (src == null || !Dreams.isMentor(src)) return;
        float left = sp.getHealth() - event.getAmount();
        if (left <= 3) {
            event.setAmount(Math.max(0, sp.getHealth() - 3));
            ActiveDream ad = Dreams.dreamOf(src);
            if (ad != null) sp.level().getServer().execute(() -> Mentors.end(ad, sp, false));
        }
    }

    /** Last line of defence: nothing dies "for real" in a dream (/kill, the void...). */
    private static void onDeathFirst(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && Dreams.isDreaming(sp)) {
            event.setCanceled(true);
            sp.setHealth(1f);
            sp.level().getServer().execute(() -> Dreams.wake(sp, Dreams.Wake.DIED));
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        LivingEntity dead = event.getEntity();
        if (inLucidium(dead.level())) return;
        if (dead instanceof ServerPlayer sp) {
            DayJournal.onPlayerDeath(sp, Nightmares.familyOf(event.getSource()));
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer killer) DayJournal.onKill(killer, dead);
    }

    /** Things that die in dreams leave Dream Residue, never their real loot. */
    private static void onDrops(LivingDropsEvent event) {
        LivingEntity e = event.getEntity();
        if (!inLucidium(e.level())) return;
        event.getDrops().clear();
        if (event.getSource().getEntity() instanceof Player && !(e instanceof Player)) {
            int n = 1 + e.getRandom().nextInt(2);
            event.getDrops().add(new ItemEntity(e.level(), e.getX(), e.getY() + 0.3, e.getZ(),
                    new ItemStack(LucidiumContent.DREAM_RESIDUE.get(), n)));
        }
    }

    private static void onBreak(BreakBlockEvent event) {
        if (inLucidium(event.getPlayer().level()) && !event.getPlayer().isCreative()) event.setCanceled(true);
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player p && inLucidium(p.level()) && !p.isCreative()) event.setCanceled(true);
    }

    /** Things dropped in a dream would be lost on waking: keep them in the pack. */
    private static void onToss(ItemTossEvent event) {
        Player p = event.getPlayer();
        if (!inLucidium(p.level()) || p.isCreative()) return;
        ItemStack stack = event.getEntity().getItem().copy();
        if (p.getInventory().add(stack)) {
            event.setCanceled(true);
            if (p instanceof ServerPlayer sp) {
                sp.inventoryMenu.broadcastChanges();
                sp.sendOverlayMessage(Component.translatable("hud.awaken.dream_toss"));
            }
        }
    }

    private static void onExplosion(ExplosionEvent.Detonate event) {
        if (inLucidium(event.getLevel())) event.getAffectedBlocks().clear();
    }

    // ---- moving between worlds ------------------------------------------------------------------------------------

    private static void onTravel(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && !Dreams.teleporting()) {
            DayJournal.onTravel(sp, event.getDimension().identifier().toString());
        }
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || Dreams.teleporting()) return;
        if (event.getFrom() == LucidiumContent.LUCIDIUM && Dreams.isDreaming(sp)) Dreams.wake(sp, Dreams.Wake.LEFT);
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        DreamState st = Dreams.state(sp);
        if (st.active || inLucidium(sp.level())) WAKE_ON_TICK.add(sp.getUUID());
        else Dreams.clearEffects(sp);
        var cfg = sp.getRespawnConfig();
        if (cfg != null) LucidiumWorld.get(sp.level().getServer()).setBed(sp.getUUID(), GlobalPos.of(cfg.respawnData().dimension(), cfg.respawnData().pos()));
        Visions.sync(sp);
        if (sp.level().isDarkOutside()) Planner.request(sp, "login at night");
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        Dreams.forget(sp);
        Sleep.forget(sp);
        DayJournal.forget(sp);
    }

    /** Right-click a bed with the journal before choosing an intention: choose one first. */
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!event.getItemStack().is(LucidiumContent.DREAM_JOURNAL.get())) return;
        if (!sp.level().getBlockState(event.getPos()).is(BlockTags.BEDS)) return;
        if (!Dreams.dreamer(sp).intention(PlayDays.current(sp.level().getServer())).isEmpty()) return;
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        JournalActions.open(sp);
    }

    // ---- server lifecycle ----------------------------------------------------------------------------------------------

    private static void onServerStopping(ServerStoppingEvent event) {
        Dreams.wakeAll(event.getServer());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        Cells.wipe(event.getServer());
        Planner.clearAll();
        Sleep.reset();
        Visions.reset();
        WAKE_ON_TICK.clear();
        WAKE_DIED.clear();
    }

    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        Cells.wipe(event.getServer());
        Planner.clearAll();
    }

    @SuppressWarnings("unused")
    private static final PlayerWakeUpEvent UNUSED = null;
    @SuppressWarnings("unused")
    private static final SleepFinishedTimeEvent UNUSED2 = null;
}
