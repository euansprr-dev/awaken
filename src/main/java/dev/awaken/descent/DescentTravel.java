package dev.awaken.descent;

import dev.awaken.core.DirectorLog;
import dev.awaken.descent.player.DeepPacks;
import dev.awaken.descent.player.DescentData;
import dev.awaken.descent.player.Strain;
import dev.awaken.descent.world.Layout;
import dev.awaken.descent.well.WellData;
import dev.awaken.progression.Counters;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Moving between the surface, the strata and the Endless: the Well, the pits (down), the ascent stones (up, with
 * Strain), Waystone travel and the Deep Lift. Every arrival is safe: a fixed platform, fall distance reset, and a
 * short grace during which fall damage is cancelled.
 */
public final class DescentTravel {
    private DescentTravel() {}

    public enum Arrival { ENTRANCE, CAMP }

    /** Special destination for the travel list: stratum 1's arrival platform (from the Well). */
    public static final int RIM_ENTRANCE = 1000;

    private static final Map<UUID, Long> GRACE = new HashMap<>();
    private static final Map<UUID, Long> PIT_COOLDOWN = new HashMap<>();
    private static final Map<UUID, Long> SEALED_COOLDOWN = new HashMap<>();
    private static final Map<UUID, Long> IN_TRANSIT = new HashMap<>();

    public static int depthOf(ServerPlayer player) {
        return Strata.depthAt(player.level().dimension(), player.blockPosition());
    }

    public static boolean inGrace(ServerPlayer player) {
        Long until = GRACE.get(player.getUUID());
        return until != null && player.level().getGameTime() < until;
    }

    /** World position of a depth's entrance or camp (feet). */
    public static BlockPos arrivalPos(int depth, Arrival arrival) {
        int form = Strata.formOf(depth);
        BlockPos local = arrival == Arrival.ENTRANCE ? Layout.entry(form) : Layout.campArrival(form);
        return Layout.world(depth, local);
    }

    /**
     * Sends a player to a depth (0 = the Well). The destination chunk is loaded first, off the tick, then the player is
     * moved. {@code strain}: climbing upward on foot costs Strain.
     */
    public static void send(ServerPlayer player, int depth, Arrival arrival, boolean strain) {
        MinecraftServer server = player.level().getServer();
        int from = depthOf(player);
        ServerLevel dest;
        BlockPos pos;
        float yaw;
        if (depth <= 0) {
            dest = server.overworld();
            WellData well = WellData.get(server);
            pos = well.built() ? well.rim() : dest.getRespawnData().pos();
            yaw = 90;
        } else {
            dest = server.getLevel(Strata.dimensionOf(depth));
            if (dest == null) {
                player.sendSystemMessage(Component.literal("That stratum does not exist in this world.").withStyle(ChatFormatting.RED));
                return;
            }
            pos = arrivalPos(depth, arrival);
            yaw = arrival == Arrival.ENTRANCE ? 0 : 90;
        }
        long now = player.level().getGameTime();
        Long transit = IN_TRANSIT.get(player.getUUID());
        if (transit != null && now - transit < 100) return;
        IN_TRANSIT.put(player.getUUID(), now);
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false));
        player.level().playSound(null, player.blockPosition(), DescentContent.SND_DROP.get(), SoundSource.PLAYERS, 1.0F, depth > from ? 0.8F : 1.2F);
        final ServerLevel target = dest;
        final BlockPos at = pos;
        target.getChunkSource().addTicketAndLoadWithRadius(TicketType.PORTAL, ChunkPos.containing(at), 1)
                .thenRunAsync(() -> arrive(player, target, at, yaw, depth, arrival, from, strain), server);
    }

    private static void arrive(ServerPlayer player, ServerLevel target, BlockPos at, float yaw, int depth, Arrival arrival,
                               int from, boolean strain) {
        IN_TRANSIT.remove(player.getUUID());
        if (player.isRemoved() || !player.isAlive()) return;
        GRACE.put(player.getUUID(), target.getGameTime() + 120);
        player.resetFallDistance();
        ServerPlayer moved = player.teleport(new TeleportTransition(target, Vec3.atBottomCenterOf(at), Vec3.ZERO, yaw, 0,
                TeleportTransition.PLACE_PORTAL_TICKET));
        ServerPlayer p = moved != null ? moved : player;
        p.resetFallDistance();
        GRACE.put(p.getUUID(), target.getGameTime() + 120);
        onArrive(p, depth, arrival, from, strain);
    }

    /** Bookkeeping after any arrival (also used on respawn at a checkpoint). */
    public static void onArrive(ServerPlayer player, int depth, Arrival arrival, int from, boolean strain) {
        DescentData d = DescentSync.data(player);
        d.visitDepth = depth;
        d.visitFromEntrance = arrival == Arrival.ENTRANCE && depth > from;
        d.visitHeard = false;
        player.level().playSound(null, player.blockPosition(), DescentContent.SND_ARRIVE.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
        if (strain && from > depth) Strain.climb(player, from, depth);
        if (depth <= 0) {
            DeepPacks.bank(player, "You climbed out.");
            DescentSync.push(player);
            return;
        }
        boolean first = d.visited.add(depth);
        d.deepest = Math.max(d.deepest, depth);
        if (first) {
            Progression.grantLevels(player, depth > Strata.COUNT ? 0.2 : 0.3, Progression.XpSource.DESCENT);
            reveal(player, depth);
            DirectorLog.log(player, "descent", "first arrival at " + Strata.label(depth));
        }
        if (Progression.record(player, Counters.DEPTH_RECORD, Strata.recordValue(depth))) {
            Progression.markNewThing(player, "depth record");
            Progression.bestMoment(player, "Reached " + Strata.label(depth));
        }
        DescentSync.push(player);
    }

    /** The REVELATION window shown once per stratum: what this place is and its one rule. */
    public static void reveal(ServerPlayer player, int depth) {
        SignalWindow.Builder w;
        if (depth <= Strata.COUNT) {
            Strata.Def def = Strata.def(depth);
            w = SignalWindow.builder(SignalWindow.Kind.REVELATION, Component.literal(Strata.label(depth)))
                    .body(Component.literal(def.flavour()))
                    .row(Component.literal("Rule"), Component.literal(def.rule()));
            if (depth < Strata.COUNT) {
                Strata.Def next = Strata.def(depth + 1);
                w.row(Component.literal("Below"), Component.literal("Stratum " + (depth + 1) + " opens at " + next.opensAt().label + "-rank"));
            } else {
                w.row(Component.literal("Below"), Component.literal("The Endless, after the Seed"));
            }
            w.footer(Component.literal(depth == Strata.COUNT ? "The arena waits under the Seed"
                    : "Light the Waystone at the bottom camp"));
        } else {
            int rules = Strata.rulesOf(depth);
            StringBuilder sb = new StringBuilder();
            for (int r = 2; r <= 9; r++) if (Strata.has(rules, r)) sb.append(sb.isEmpty() ? "" : " · ").append(Strata.ruleName(r));
            w = SignalWindow.builder(SignalWindow.Kind.REVELATION, Component.literal(Strata.label(depth)))
                    .body(Component.literal("Generated strata, stacking rules. Every depth here is a new record."))
                    .row(Component.literal("Shaped like"), Component.literal(Strata.def(Strata.formOf(depth)).name()))
                    .row(Component.literal("Rules"), Component.literal(sb.isEmpty() ? "None" : sb.toString()))
                    .footer(Component.literal("Depth record · " + Strata.recordValue(depth)));
        }
        Signal.send(player, w.ttl(20 * 14).priority(6).build());
    }

    // ---- entry points -----------------------------------------------------------------------------------------------

    /** The player fell into the Well's shaft, or used the lift. */
    public static void enterWell(ServerPlayer player) {
        if (!DescentModule.enabled()) return;
        send(player, 1, Arrival.ENTRANCE, false);
    }

    public static void useWellLift(ServerPlayer player, BlockPos pos) {
        if (!DescentModule.enabled()) {
            player.sendOverlayMessage(Component.literal("The Well is quiet (the Descent is turned off)."));
            return;
        }
        DescentData d = DescentSync.data(player);
        if (!d.lit.isEmpty() && !player.isSecondaryUseActive()) {
            DescentWaystones.openTravel(player, 0, pos);
        } else {
            enterWell(player);
        }
    }

    /** Stepped into a pit: drop to the next depth if the player's rank opens it. */
    public static void onPit(ServerPlayer player, BlockPos pos) {
        long now = player.level().getGameTime();
        Long cd = PIT_COOLDOWN.get(player.getUUID());
        if (cd != null && now < cd) return;
        PIT_COOLDOWN.put(player.getUUID(), now + 40);
        if (!DescentModule.enabled()) return;
        int here = Strata.depthAt(player.level().dimension(), pos);
        if (here <= 0) return;
        int target = here + 1;
        DescentData d = DescentSync.data(player);
        var prof = Progression.profile(player);
        Strata.Access access = Strata.access(prof.rank(), prof.level(), d.seedCleared, target);
        if (access != Strata.Access.OPEN) {
            sealed(player, access, target);
            return;
        }
        if (target == Strata.COUNT) {
            var storySeal = dev.awaken.core.api.StoryHooks.seedSealed(player);
            if (storySeal.isPresent()) {
                player.sendOverlayMessage(Component.literal(storySeal.get()));
                return;
            }
        }
        if (Strata.has(Strata.rulesOf(here), Strata.RULE_LISTENERS) && d.visitFromEntrance && !d.visitHeard) {
            Progression.count(player, "awaken:latent/silent_halls_unheard");
            Signal.toast(player, Component.literal("You crossed " + Strata.shortName(here) + " without a sound."));
        }
        send(player, target, Arrival.ENTRANCE, false);
    }

    private static void sealed(ServerPlayer player, Strata.Access access, int target) {
        long now = player.level().getGameTime();
        // throw the player back out over the rim, away from the pit's centre
        int depth = Strata.depthAt(player.level().dimension(), player.blockPosition());
        BlockPos pit = Layout.world(Math.max(1, depth), Layout.pit(Strata.formOf(Math.max(1, depth))));
        double dx = player.getX() - (pit.getX() + 0.5), dz = player.getZ() - (pit.getZ() + 0.5);
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.3) { dx = 1; dz = 0; len = 1; }
        player.setDeltaMovement(dx / len * 0.55, 0.6, dz / len * 0.55);
        player.syncVelocity = true;
        Long cd = SEALED_COOLDOWN.get(player.getUUID());
        if (cd != null && now < cd) return;
        SEALED_COOLDOWN.put(player.getUUID(), now + 200);
        player.level().playSound(null, player.blockPosition(), DescentContent.SND_SEALED.get(), SoundSource.PLAYERS, 1.0F, 0.7F);
        Signal.send(player, SignalWindow.builder(SignalWindow.Kind.WARNING, Component.literal("The way down is sealed"))
                .body(Component.literal(Strata.sealedMessage(access, target)))
                .footer(Component.literal(access == Strata.Access.SEALED_SEED ? "The story ends at the Seed" : "Grow, and come back"))
                .ttl(20 * 8).build());
    }

    /** Ascent stone on an arrival platform: climb back up one stratum (asks first: it costs Strain). */
    public static void useAscent(ServerPlayer player, BlockPos pos) {
        int here = Strata.depthAt(player.level().dimension(), pos);
        if (here <= 0) return;
        int target = here - 1;
        double strain = player.getData(dev.awaken.progression.ProgressionModule.STRAIN.get());
        double after = Strain.next(strain, 1);
        Signal.send(player, SignalWindow.builder(SignalWindow.Kind.WARNING, Component.literal("Climb back up?"))
                .row(Component.literal("To"), Component.literal(target == 0 ? "The Well (the surface)" : Strata.label(target) + " · bottom camp"))
                .row(Component.literal("Strain"), Component.literal("−" + Math.round(after * 100) + "% stats for a while"))
                .footer(Component.literal(target == 0 ? "Your Deep Pack becomes yours at the top" : "The Deep Lift at home climbs without Strain"))
                .action("descent:ascend:" + here).build());
    }

    static void onAscendAnswer(ServerPlayer player, String action, boolean accepted) {
        if (!accepted) return;
        int here;
        try {
            here = Integer.parseInt(action.substring(action.lastIndexOf(':') + 1));
        } catch (NumberFormatException e) {
            return;
        }
        if (depthOf(player) != here) return;
        BlockPos ascent = Layout.world(here, Layout.ascent(Strata.formOf(here)));
        if (player.blockPosition().distSqr(ascent) > 12 * 12) {
            player.sendOverlayMessage(Component.literal("Stand by the ascent stone to climb."));
            return;
        }
        send(player, here - 1, Arrival.CAMP, true);
    }

    public static void forget(UUID id) {
        GRACE.remove(id);
        PIT_COOLDOWN.remove(id);
        SEALED_COOLDOWN.remove(id);
        IN_TRANSIT.remove(id);
    }

    public static void clearAll() {
        GRACE.clear();
        PIT_COOLDOWN.clear();
        SEALED_COOLDOWN.clear();
        IN_TRANSIT.clear();
    }
}
