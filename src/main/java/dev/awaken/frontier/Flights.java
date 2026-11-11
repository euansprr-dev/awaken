package dev.awaken.frontier;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.api.FarPost;
import dev.awaken.core.api.TheLull;
import dev.awaken.signal.Signal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A skiff's flight, server side (SPEC §67). The helm starts one for everyone on the deck. The skiff wakes and lifts for
 * six seconds (the client films it), the land at the other end loads under a ticket meanwhile, and once the cloud has covered the
 * screen and the land is there the travellers are moved onto the other skiff's deck; their film then holds the cloud until
 * their own client has the land, and brings the skiff down. Nothing hurts a traveller from the helm until they're set down
 * (or half a minute after the move). A logout or a server stop mid-air puts them at the far deck; a crash does on their
 * next login. Overworld only.
 */
final class Flights {
    private Flights() {}

    /** The lift, in ticks (the client's film is the same length); a quick flight goes straight into the cloud. */
    static final int LIFT = 120, QUICK_LIFT = 14;
    /** The land at the far end loads in this radius (chunks) round its mooring. */
    static final int LOAD_RADIUS = 3;
    /** How long the move waits for the land before going anyway; how long the shield lasts after the move. */
    static final int WAIT_MAX = 20 * 40, SHIELD_AFTER = 20 * 30;
    /** A watcher this near the mooring sees it lift away. */
    static final int SEEN_RANGE = 160;
    /** Where you stand by a skiff that has come for you (south-facing, from its berth's middle): off its quarter, on the pier's side. */
    static final BlockPos BESIDE = new BlockPos(-5, 0, -4);

    private static final class Flight {
        final UUID who;
        final Moorings.Which to;
        final FrontierData.Mooring dest;
        final boolean quick;
        final ChunkPos chunk;
        @Nullable CompletableFuture<?> land;
        int age, movedAt = -1;
        boolean ticket;

        Flight(UUID who, Moorings.Which to, FrontierData.Mooring dest, boolean quick) {
            this.who = who;
            this.to = to;
            this.dest = dest;
            this.quick = quick;
            this.chunk = ChunkPos.containing(dest.origin());
        }

        int lift() {
            return quick ? QUICK_LIFT : LIFT;
        }
    }

    private static final Map<UUID, Flight> FLYING = new HashMap<>();

    static boolean flying(UUID who) {
        return FLYING.containsKey(who);
    }

    static boolean anyFlying() {
        return !FLYING.isEmpty();
    }

    /** The helm: everyone on this mooring's deck flies to the other end. */
    static void helm(ServerPlayer pilot, Moorings.Which from, boolean quickAsked) {
        MinecraftServer server = pilot.level().getServer();
        ServerLevel level = server.overworld();
        if (pilot.level() != level || !TheLull.farLanding(server)) return;
        if (flying(pilot.getUUID())) return;
        if (!FarPost.ready(pilot) && !FrontierData.get(server).marked(pilot.getUUID(), "ready")) {
            Signal.toast(pilot, Component.literal("The skiff isn't going anywhere yet. Ilse at the Last Fire will tell you about it."));
            return;
        }
        FrontierData.Mooring here = Moorings.get(server, from);
        Moorings.Which to = from == Moorings.Which.RIDGE ? Moorings.Which.LANDING : Moorings.Which.RIDGE;
        if (here == null) return;
        FrontierData.Mooring there = Moorings.get(server, to);
        if (there == null || !there.built()) {
            // the far mooring stands where the land is: the seed's spot, its exact ground found once it loads
            BlockPos spot = to == Moorings.Which.RIDGE ? Moorings.ridgeSpot(server) : Moorings.landingSpot(server);
            if (spot == null) return;
            there = new FrontierData.Mooring(new BlockPos(spot.getX(), level.getSeaLevel() + 8, spot.getZ()), Moorings.facing(server, to), false, Moorings.LOOK);
        }
        List<ServerPlayer> party = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || flying(p.getUUID())) continue;
            if (p == pilot || Skiff.onDeck(here.origin(), here.facing(), p.getX(), p.getY(), p.getZ())) party.add(p);
        }
        String title = to == Moorings.Which.LANDING ? "The Far Landing" : "Carillon";
        launch(level, party, here.origin(), here.facing(), to, there, quickAsked, title, line(server, to), false);
        Awaken.LOG.info("[frontier] {} lifts from the {} with {} aboard{}", pilot.getName().getString(), from == Moorings.Which.RIDGE ? "ridge" : "landing",
                party.size(), quickAsked ? " (straight into the cloud)" : "");
    }

    /**
     * The homecoming (SPEC §67): the skiff has come for this player where they stand, and takes them (and anyone right
     * beside them) home to the ridge. Never quick. False when it can't: not in the overworld, under a roof, already flying.
     */
    static boolean fetch(ServerPlayer pilot, String line) {
        MinecraftServer server = pilot.level().getServer();
        ServerLevel level = server.overworld();
        if (pilot.level() != level || !TheLull.farLanding(server) || flying(pilot.getUUID())) return false;
        BlockPos at = pilot.blockPosition();
        if (!level.canSeeSky(at.above(2))) return false;
        FrontierData.Mooring there = Moorings.get(server, Moorings.Which.RIDGE);
        if (there == null || !there.built()) {
            BlockPos spot = Moorings.ridgeSpot(server);
            if (spot == null) return false;
            there = new FrontierData.Mooring(new BlockPos(spot.getX(), level.getSeaLevel() + 8, spot.getZ()), Moorings.facing(server, Moorings.Which.RIDGE), false, Moorings.LOOK);
        }
        // it has come down beside you, its bow toward the city and its core just off the ground; its helm is a few steps away
        Direction facing = Direction.fromYRot(bearing(at, there.origin()));
        BlockPos origin = at.subtract(rotated(facing, BESIDE)).above(-Skiff.KEEL);
        List<ServerPlayer> party = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || flying(p.getUUID())) continue;
            if (p == pilot || p.distanceToSqr(pilot) < 5 * 5) party.add(p);
        }
        launch(level, party, origin, facing, Moorings.Which.RIDGE, there, false, "Carillon", line, true);
        Awaken.LOG.info("[frontier] the skiff came for {} at {} and takes {} home", pilot.getName().getString(), at.toShortString(), party.size());
        return true;
    }

    /** A part's place relative to the berth's middle, turned to this facing. */
    private static BlockPos rotated(Direction facing, BlockPos local) {
        return Skiff.at(BlockPos.ZERO, facing, local.getX(), 0, local.getZ());
    }

    private static void launch(ServerLevel level, List<ServerPlayer> party, BlockPos from, Direction fromFacing, Moorings.Which to,
                               FrontierData.Mooring there, boolean quickAsked, String title, String line, boolean drawn) {
        FrontierData data = FrontierData.get(level.getServer());
        List<UUID> ids = party.stream().map(ServerPlayer::getUUID).toList();
        for (ServerPlayer p : party) {
            boolean quick = quickAsked && data.marked(p.getUUID(), "flown");
            Flight f = new Flight(p.getUUID(), to, there, quick);
            f.land = level.getChunkSource().addTicketAndLoadWithRadius(FrontierModule.FLIGHT_TICKET.get(), f.chunk, LOAD_RADIUS);
            f.ticket = true;
            FLYING.put(p.getUUID(), f);
            double[] s = Moorings.stand(there);
            data.pending(p.getUUID(), new FrontierData.Dest(s[0], s[1], s[2], (float) s[3]));
            AwakenNet.send(p, new FrontierNet.Fly(FrontierNet.Fly.START, from, fromFacing.get2DDataValue(), there.origin(),
                    there.facing().get2DDataValue(), quick, title, line, ids, drawn));
        }
        if (drawn) return; // nobody else sees a skiff that was never built there
        float bearing = bearing(from, there.origin());
        for (ServerPlayer w : level.players()) {
            if (ids.contains(w.getUUID())) continue;
            double dx = w.getX() - from.getX(), dz = w.getZ() - from.getZ();
            if (dx * dx + dz * dz < SEEN_RANGE * SEEN_RANGE)
                AwakenNet.send(w, new FrontierNet.Seen(from, fromFacing.get2DDataValue(), bearing, quickAsked, ids));
        }
    }

    /** The small line under the title: how far, and which way from where you set out. */
    static String line(MinecraftServer server, Moorings.Which to) {
        BlockPos c = TheLull.centre(server), l = TheLull.landing(server);
        if (l == null) return "";
        int dx = l.getX() - c.getX(), dz = l.getZ() - c.getZ();
        double km = Math.hypot(dx, dz) / 1000.0;
        String dist = String.format(java.util.Locale.ROOT, "%.1f km", km);
        return to == Moorings.Which.LANDING ? dist + " " + compass(dx, dz) + " of Carillon" : dist + " " + compass(-dx, -dz) + " of the landing";
    }

    static String compass(double dx, double dz) {
        String[] names = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        return names[Math.floorMod((int) Math.round(yaw / 45.0), 8)];
    }

    /** Minecraft's yaw from a to b. */
    static float bearing(BlockPos a, BlockPos b) {
        return (float) Math.toDegrees(Math.atan2(-(b.getX() - a.getX()), b.getZ() - a.getZ()));
    }

    static void tick(MinecraftServer server) {
        if (FLYING.isEmpty()) return;
        ServerLevel level = server.overworld();
        for (Iterator<Flight> it = FLYING.values().iterator(); it.hasNext(); ) {
            Flight f = it.next();
            f.age++;
            ServerPlayer p = server.getPlayerList().getPlayer(f.who);
            if (p == null) {
                release(level, f);
                it.remove();
                continue;
            }
            if (f.movedAt < 0) {
                if (p.level() != level) {
                    // a death or a portal took them out of the overworld: the flight is off
                    release(level, f);
                    FrontierData.get(server).pending(f.who, null);
                    AwakenNet.send(p, end(f));
                    it.remove();
                    continue;
                }
                boolean landReady = f.land == null || f.land.isDone();
                if (f.age >= f.lift() && (landReady || f.age >= f.lift() + WAIT_MAX)) move(p, f);
            } else if (f.age - f.movedAt >= SHIELD_AFTER) {
                finish(p, f);
                it.remove();
            }
        }
    }

    /** Onto the far deck: its mooring built now if it wasn't, the traveller put at its helm. */
    private static void move(ServerPlayer p, Flight f) {
        ServerLevel level = p.level().getServer().overworld();
        FrontierData.Mooring dest = f.dest;
        if (!dest.built() && Moorings.ensure(level, f.to)) {
            FrontierData.Mooring built = Moorings.get(level.getServer(), f.to);
            if (built != null) dest = built;
        }
        double[] s = Moorings.stand(dest);
        p.teleportTo(level, s[0], s[1], s[2], Set.of(), (float) s[3], 0F, false);
        p.fallDistance = 0;
        f.movedAt = f.age;
        FrontierData data = FrontierData.get(level.getServer());
        data.pending(f.who, null);
        boolean first = data.mark(f.who, "flown");
        if (f.to == Moorings.Which.LANDING) data.mark(f.who, "landed");
        release(level, f);
        AwakenNet.send(p, new FrontierNet.Fly(FrontierNet.Fly.ARRIVED, BlockPos.ZERO, 0, dest.origin(), dest.facing().get2DDataValue(), f.quick, "", "", List.of(), false));
        Awaken.LOG.info("[frontier] {} set down at the {} ({}){}", p.getName().getString(), f.to == Moorings.Which.RIDGE ? "ridge" : "landing",
                dest.origin().toShortString(), first ? ", the first flight" : "");
    }

    private static void finish(ServerPlayer p, Flight f) {
        AwakenNet.send(p, end(f));
    }

    private static FrontierNet.Fly end(Flight f) {
        return new FrontierNet.Fly(FrontierNet.Fly.END, BlockPos.ZERO, 0, BlockPos.ZERO, 0, f.quick, "", "", List.of(), false);
    }

    private static void release(ServerLevel level, Flight f) {
        if (f.ticket) {
            level.getChunkSource().removeTicketWithRadius(FrontierModule.FLIGHT_TICKET.get(), f.chunk, LOAD_RADIUS);
            f.ticket = false;
        }
    }

    /** The traveller's film has set them down: the shield ends. */
    static void landed(ServerPlayer p) {
        Flight f = FLYING.get(p.getUUID());
        if (f == null || f.movedAt < 0) return;
        FLYING.remove(p.getUUID());
        release(p.level().getServer().overworld(), f);
    }

    /** Leaving mid-air: put them at the far deck before they're saved. */
    static void loggedOut(ServerPlayer p) {
        Flight f = FLYING.remove(p.getUUID());
        if (f == null) return;
        ServerLevel level = p.level().getServer().overworld();
        if (f.movedAt < 0 && p.level() == level) {
            // its mooring stands now if its land got loaded in time; else the seed's spot, where it will stand
            FrontierData.Mooring dest = Moorings.ensure(level, f.to) ? Moorings.get(level.getServer(), f.to) : null;
            double[] s = Moorings.stand(dest != null ? dest : f.dest);
            FrontierData.get(level.getServer()).mark(f.who, "flown");
            if (f.to == Moorings.Which.LANDING) FrontierData.get(level.getServer()).mark(f.who, "landed");
            p.teleportTo(level, s[0], s[1], s[2], Set.of(), (float) s[3], 0F, false);
            FrontierData.get(level.getServer()).pending(f.who, null);
            Awaken.LOG.info("[frontier] {} left mid-flight; set down at the far deck", p.getName().getString());
        }
        release(level, f);
    }

    /** The server stopping: everyone in the air is put down first. */
    static void stopping(MinecraftServer server) {
        for (Flight f : List.copyOf(FLYING.values())) {
            ServerPlayer p = server.getPlayerList().getPlayer(f.who);
            if (p != null) loggedOut(p);
        }
        FLYING.clear();
    }

    /** A login after a crash mid-air: the far deck, where they were going. */
    static void loggedIn(ServerPlayer p) {
        MinecraftServer server = p.level().getServer();
        FrontierData data = FrontierData.get(server);
        FrontierData.Dest d = data.pending(p.getUUID());
        if (d == null || flying(p.getUUID())) return;
        data.pending(p.getUUID(), null);
        if (p.level().dimension() != Level.OVERWORLD) return;
        p.teleportTo(server.overworld(), d.x(), d.y(), d.z(), Set.of(), d.yaw(), 0F, true);
        Awaken.LOG.info("[frontier] {} was mid-flight when the world stopped; set down where they were going", p.getName().getString());
    }

    static void onDamage(LivingIncomingDamageEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && flying(p.getUUID()) && !e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) e.setCanceled(true);
    }

    static void clear() {
        FLYING.clear();
    }

    /** Dev and tests: how far into its flight a player is (-1 when not flying), and whether they've been moved. */
    static int age(UUID who) {
        Flight f = FLYING.get(who);
        return f == null ? -1 : f.age;
    }

    static boolean moved(UUID who) {
        Flight f = FLYING.get(who);
        return f != null && f.movedAt >= 0;
    }
}
