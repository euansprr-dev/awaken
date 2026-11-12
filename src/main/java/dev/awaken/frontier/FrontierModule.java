package dev.awaken.frontier;

import dev.awaken.Awaken;
import dev.awaken.core.Throttle;
import dev.awaken.core.api.FarPost;
import dev.awaken.core.api.TheLull;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Far Landing (SPEC §67): in a world played far from Carillon, an old Precursor skiff on the ridge past Ilse's fire
 * flies you to the landing the seed chose, five to seven kilometres out, and back whenever you like; the city's errands
 * come to you there (letters, the wagon, the hold). Everything here only happens in a Far Landing world
 * ({@link TheLull#farLanding}); any other world plays exactly as before.
 */
public final class FrontierModule {
    private FrontierModule() {}

    static final DeferredRegister<TicketType> TICKETS = DeferredRegister.create(Registries.TICKET_TYPE, Awaken.MODID);
    /** Holds the far end's land loaded while a skiff flies to it (dropped when the travellers are moved). */
    static final DeferredHolder<TicketType, TicketType> FLIGHT_TICKET = TICKETS.register("frontier_flight",
            () -> new TicketType(20L * 240, TicketType.FLAG_LOADING)); // outlasts the longest wait for the far land (Flights.WAIT_MAX)

    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);
    static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Awaken.MODID);

    /** The skiff's own sounds, each named from the game's recordings (merge/frontier sounds.json, SPEC §67.5). */
    public static final DeferredHolder<SoundEvent, SoundEvent> CAST_OFF = sound("frontier.skiff.cast_off"), CORE_WAKE = sound("frontier.skiff.core_wake"),
            CORE_HUM = sound("frontier.skiff.core_hum"), CORE_REST = sound("frontier.skiff.core_rest"), SAIL = sound("frontier.skiff.sail"),
            BELL = sound("frontier.skiff.bell"), CREAK = sound("frontier.skiff.creak"), WIND = sound("frontier.skiff.wind"),
            SET_DOWN = sound("frontier.skiff.set_down");
    /** The flight's air: cloud, streaks of wind, motes of the core's light (client.FlightAir). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> P_WISP = PARTICLES.register("frontier_wisp", () -> new SimpleParticleType(false)),
            P_STREAK = PARTICLES.register("frontier_streak", () -> new SimpleParticleType(false)),
            P_MOTE = PARTICLES.register("frontier_mote", () -> new SimpleParticleType(false));

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(Awaken.id(path)));
    }

    public static void init(IEventBus modBus) {
        TICKETS.register(modBus);
        SOUNDS.register(modBus);
        PARTICLES.register(modBus);
        SkiffBlocks.register(modBus);
        modBus.addListener(FrontierNet::register);
        FrontierPost.install();
        FrontierGoals.register();
        FrontierCommands.register();
        FrontierTests.register();

        var bus = NeoForge.EVENT_BUS;
        bus.addListener((ServerTickEvent.Post e) -> {
            var server = e.getServer();
            Flights.tick(server);
            if (server.getTickCount() % 40 == 7) Moorings.tick(server);
            if (server.getTickCount() % 100 == 31) FrontierPost.tick(server);
        });
        bus.addListener((ServerStoppingEvent e) -> Flights.stopping(e.getServer()));
        bus.addListener((ServerStoppedEvent e) -> Flights.clear());
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) Flights.loggedIn(p);
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) Flights.loggedOut(p);
        });
        bus.addListener(EventPriority.HIGHEST, Flights::onDamage);
        bus.addListener(EventPriority.HIGHEST, FrontierModule::onUse);
        bus.addListener(EventPriority.HIGH, FrontierModule::onBreak);
        bus.addListener(EventPriority.HIGH, FrontierModule::onPlace);
        bus.addListener(FrontierModule::onExplode);
    }

    /** The helm flies you; the hold takes what the city asked for (or a keepsake, home) and keeps your letters; the board is Hale's. */
    private static void onUse(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (!(e.getEntity() instanceof ServerPlayer p) || !TheLull.farLanding(level.getServer())) return;
        BlockPos pos = e.getPos();
        Moorings.Which helm = Moorings.at(level.getServer(), pos, Skiff.HELM);
        Moorings.Which hold = helm == null ? Moorings.at(level.getServer(), pos, Skiff.HOLD) : null;
        Moorings.Which board = helm == null && hold == null ? Moorings.at(level.getServer(), pos, Skiff.BOARD) : null;
        if (helm == null && hold != Moorings.Which.LANDING && board != Moorings.Which.LANDING) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (e.getHand() != InteractionHand.MAIN_HAND || !Throttle.allow(p, "frontier_use", 10)) return;
        if (helm != null) Flights.helm(p, helm, p.isShiftKeyDown());
        else if (hold != null) FrontierPost.hold(p);
        else FrontierPost.board(p);
    }

    private static boolean denies(Player p, Level level, BlockPos pos) {
        if (!(p instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl) || sl.dimension() != Level.OVERWORLD) return false;
        if (sp.isCreative() || sp.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) return false;
        return Moorings.guarded(sl.getServer(), pos);
    }

    private static void tell(Player p) {
        if (p instanceof ServerPlayer sp && Throttle.allow(sp, "frontier_guard", 40))
            sp.sendOverlayMessage(Component.literal("The skiff's mooring is kept as it is."));
    }

    private static void onBreak(BreakBlockEvent e) {
        if (!(e.getLevel() instanceof Level level) || level.isClientSide() || !denies(e.getPlayer(), level, e.getPos())) return;
        e.setCanceled(true);
        e.setNotifyClient(true);
        tell(e.getPlayer());
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof Player p) || !(e.getLevel() instanceof Level level) || level.isClientSide() || !denies(p, level, e.getPos())) return;
        e.setCanceled(true);
        tell(p);
    }

    private static void onExplode(ExplosionEvent.Detonate e) {
        if (!(e.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        e.getAffectedBlocks().removeIf(pos -> Moorings.guarded(level.getServer(), pos));
    }
}
