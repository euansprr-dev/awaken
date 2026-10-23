package dev.awaken.above.halo;

import dev.awaken.Awaken;
import dev.awaken.above.Above;
import dev.awaken.above.AboveData;
import dev.awaken.above.AboveSounds;
import dev.awaken.above.logs.Revelations;
import dev.awaken.above.machine.Travel;
import dev.awaken.above.net.AboveNet;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.DirectorLog;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The Halo ({@code awaken:halo}): the Ascendants' orbital station. Low gravity while you're there, a safety net under
 * the station, Sky Leviathans passing outside, and a return pad home. Reaching it is a story beat.
 */
public final class Halo {
    private Halo() {}

    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, Awaken.id("halo"));
    private static final Identifier LOW_GRAVITY = Awaken.id("halo_low_gravity");
    private static final Identifier SOFT_LANDING = Awaken.id("halo_soft_landing");
    private static final Map<UUID, Long> NEXT_LEVIATHAN = new HashMap<>();

    public static void init() {
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                applyGravity(sp);
                if (e.getTo() == LEVEL) arrived(sp);
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) applyGravity(sp);
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerRespawnEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) applyGravity(sp);
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> NEXT_LEVIATHAN.remove(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, Halo::onTick);
    }

    public static boolean in(ServerPlayer player) {
        return player.level().dimension() == LEVEL;
    }

    /** Low gravity and soft landings inside the Halo; removed everywhere else. */
    public static void applyGravity(ServerPlayer player) {
        boolean on = in(player);
        AttributeInstance g = player.getAttribute(Attributes.GRAVITY);
        if (g != null) {
            if (on) g.addOrUpdateTransientModifier(new AttributeModifier(LOW_GRAVITY, -0.62, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            else g.removeModifier(LOW_GRAVITY);
        }
        AttributeInstance f = player.getAttribute(Attributes.SAFE_FALL_DISTANCE);
        if (f != null) {
            if (on) f.addOrUpdateTransientModifier(new AttributeModifier(SOFT_LANDING, 12.0, AttributeModifier.Operation.ADD_VALUE));
            else f.removeModifier(SOFT_LANDING);
        }
    }

    // ---- getting there and back -------------------------------------------------------------------------------------

    /** The Observatory shows the Halo for the first time. */
    public static void reveal(ServerPlayer player) {
        AboveData d = Above.data(player);
        if (d.haloSeen()) return;
        d.setHaloSeen(true);
        if (Progression.profile(player).counter(Above.HALO_SEEN) == 0) Progression.count(player, Above.HALO_SEEN, 1);
        Progression.markNewThing(player, "halo_seen");
        Signal.send(player, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("The Halo"))
                .body(Component.literal("A ring of light in orbit, too regular to be anything else. Theirs. Somebody built a door up there and closed it behind them."))
                .footer(Component.literal("A Launch Ring could reach it · Codex updated")).priority(6).ttl(20 * 12).build());
        Above.sync(player);
    }

    /** Fired by a formed, powered Launch Ring. */
    public static void launch(ServerPlayer player, ServerLevel from, BlockPos core) {
        Above.data(player).setLaunchOrigin(Optional.of(GlobalPos.of(from.dimension(), core.immutable())));
        from.sendParticles(ParticleTypes.END_ROD, core.getX() + 0.5, core.getY() + 1, core.getZ() + 0.5, 80, 1.5, 3, 1.5, 0.2);
        from.playSound(null, core, AboveSounds.LAUNCH.get(), SoundSource.BLOCKS, 1.5f, 1.0f);
        send(player);
    }

    /** Puts the player at the station's arrival point. */
    public static boolean send(ServerPlayer player) {
        ServerLevel halo = player.level().getServer().getLevel(LEVEL);
        if (halo == null) {
            player.sendOverlayMessage(Component.literal("The Halo is out of reach (dimension missing)."));
            return false;
        }
        halo.getChunk(HaloLayout.ARRIVAL.getX() >> 4, HaloLayout.ARRIVAL.getZ() >> 4);
        Travel.teleport(player, halo, HaloLayout.ARRIVAL);
        return true;
    }

    /** The return pad: back to the Launch Ring you came from, or to the world spawn. */
    public static void returnHome(ServerPlayer player) {
        var server = player.level().getServer();
        Optional<GlobalPos> origin = Above.data(player).launchOrigin();
        ServerLevel level = origin.map(o -> server.getLevel(o.dimension())).orElse(null);
        BlockPos target;
        if (level != null) {
            target = Travel.safeSpot(level, origin.get().pos().offset(0, 0, 3));
        } else {
            level = server.overworld();
            BlockPos spawn = level.getRespawnData().pos();
            target = Travel.safeSpot(level, level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn).below());
        }
        Travel.teleport(player, level, target);
    }

    private static void arrived(ServerPlayer player) {
        if (Progression.profile(player).counter(Above.HALO_REACHED) == 0) {
            Progression.count(player, Above.HALO_REACHED, 1);
            Progression.markNewThing(player, "halo_reached");
            Revelations.fire(player, "halo_found", "Up", "They went up to get away from it. It came with them. It always does.");
            DirectorLog.log(player, "halo", "first arrival");
        }
        Above.data(player).setHaloSeen(true);
        Above.sync(player);
        NEXT_LEVIATHAN.put(player.getUUID(), player.level().getGameTime() + 600 + player.getRandom().nextInt(600));
    }

    // ---- while there ------------------------------------------------------------------------------------------------

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || sp.tickCount % 20 != 0 || !in(sp)) return;
        if (sp.getY() < HaloLayout.FLOOR - 40 && !sp.isSpectator()) {
            // the station catches anyone who drifts off it
            Travel.teleport(sp, sp.level(), HaloLayout.ARRIVAL);
            sp.sendOverlayMessage(Component.literal("The station caught you."));
        }
        long now = sp.level().getGameTime();
        long next = NEXT_LEVIATHAN.computeIfAbsent(sp.getUUID(), k -> now + 1200);
        if (now >= next) {
            NEXT_LEVIATHAN.put(sp.getUUID(), now + 3600 + sp.getRandom().nextInt(3600));
            AwakenNet.send(sp, new AboveNet.Leviathan(sp.getRandom().nextLong(), 20 * 40));
            sp.level().playSound(null, sp.blockPosition(), AboveSounds.LEVIATHAN.get(), SoundSource.AMBIENT, 1.2f, 0.6f);
            boolean first = Progression.profile(sp).counter(Above.LEVIATHAN_SIGHTINGS) == 0;
            Progression.count(sp, Above.LEVIATHAN_SIGHTINGS, 1);
            if (first) Signal.toast(sp, Component.literal("Something vast moves past the glass."));
        }
    }
}
