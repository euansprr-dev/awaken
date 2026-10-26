package dev.awaken.hearth.settle;

import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.Household;
import dev.awaken.hearth.room.Room;
import dev.awaken.hearth.town.TownNpc;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Someone moving in (PLAN 4.7): "They walk up the road with a bundle on their back, take the room ("Ilse's room")". The
 * household hears they're coming; if one of them is near, the newcomer appears on the road at the edge of the land, on
 * the side they come from, and walks in with their things (a townsperson's body, wearing their own face, the bundle on
 * the back) through the door to the bed that will be theirs. There, the room takes their name, everyone in it sees the
 * name once, and from then on they live a day on the people engine ({@link HomePeople}). Nobody near: they simply move in.
 */
public final class Arrival {
    private Arrival() {}

    private record Walk(UUID entity, UUID hearth, long started) {}

    private static final Map<String, Walk> WALKS = new HashMap<>();

    static boolean walking(String id) {
        return WALKS.containsKey(id);
    }

    static long startedAt(String id) {
        Walk w = WALKS.get(id);
        return w == null ? Long.MIN_VALUE / 2 : w.started;
    }

    public static void clear() {
        WALKS.clear();
    }

    /** Someone on their way in now, walking (tours frame them), or null. */
    static @Nullable TownNpc walker(ServerLevel level) {
        for (Walk w : WALKS.values()) if (level.getEntity(w.entity) instanceof TownNpc npc && npc.isAlive()) return npc;
        return null;
    }

    /** They set out for this Hearth's spare bedroom. {@code from}: where they walk up from (null: the road on their side). */
    static void start(ServerLevel level, HearthRecord h, Folk.Person who, Settlers.Bedroom room, @Nullable BlockPos from) {
        start(level, h, who, room, from, null);
    }

    /** As above, by way of {@code via} (the end of the road they walk up) when there's one. */
    static void start(ServerLevel level, HearthRecord h, Folk.Person who, Settlers.Bedroom room, @Nullable BlockPos from, @Nullable BlockPos via) {
        Folk.Settler s = new Folk.Settler(who, room.bed(), Folk.Settler.COMING, Settlers.today(level.getServer()));
        h.folk.settlers.add(s);
        HearthService.dirty(level.getServer());
        String they = Settlers.they(who);
        Settlers.tell(level, h, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(who.given() + " is coming to live with you"))
                .body(Component.literal("On the road from " + (who.from().isEmpty() ? "afar" : who.from()) + " with " + (they.equals("they") ? "their" : they.equals("she") ? "her" : "his")
                        + " things. " + cap(they) + "'ll take " + room.room().reading().name() + "."))
                .topic("hearth_people").ttl(20 * 10).priority(4).build());
        // someone of the household near to see them come? then they walk; otherwise they're simply here when you are
        ServerPlayer near = null;
        for (ServerPlayer p : Household.online(level.getServer(), h)) {
            if (p.level() == level && p.distanceToSqr(Vec3.atCenterOf(h.pos)) < 128 * 128) near = p;
        }
        BlockPos at = near == null ? null : (from != null ? from : roadStart(level, h, near));
        BlockPos bedside = bedside(level, room);
        if (at == null || bedside == null || !level.isPositionEntityTicking(at) || !level.isPositionEntityTicking(bedside)) {
            movedIn(level, h, s, false);
            return;
        }
        TownNpc npc = HearthContent.TOWNSFOLK.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (npc == null) {
            movedIn(level, h, s, false);
            return;
        }
        Vec3 v = Vec3.atBottomCenterOf(at);
        npc.snapTo(v.x, v.y, v.z, yawTo(at, bedside), 0f);
        npc.setKind("arrival:" + who.id());
        npc.setHearthOwner(h.owner);
        npc.setCustomName(Component.literal(who.name()));
        npc.setCustomNameVisible(true);
        npc.arrive(who.id(), Faces.texture(who), Faces.slim(who), bedside, from != null ? via : null);
        level.addFreshEntity(npc);
        WALKS.put(who.id(), new Walk(npc.getUUID(), h.owner, level.getGameTime()));
    }

    /** The walker reached the bed that'll be theirs (or gave up trying): they're home. */
    public static void arrived(ServerLevel level, TownNpc npc, BlockPos at) {
        String id = npc.arrival();
        npc.discard();
        if (id == null) return;
        Walk w = WALKS.remove(id);
        if (w == null) return;
        HearthService.data(level.getServer()).of(w.hearth).ifPresent(h -> {
            Folk.Settler s = h.folk.settler(id);
            if (s != null && s.state == Folk.Settler.COMING) movedIn(level, h, s, true);
        });
    }

    /** They're in: their room takes their name, the household is told, and their day begins. */
    static void movedIn(ServerLevel level, HearthRecord h, Folk.Settler s, boolean seen) {
        Walk w = WALKS.remove(s.who.id());
        if (w != null && level.getEntity(w.entity) instanceof TownNpc npc) npc.discard();
        s.state = Folk.Settler.HOME;
        s.since = Settlers.today(level.getServer());
        HearthService.dirty(level.getServer());
        HomePeople.changed(h);
        Room room = null;
        if (s.bed != null) for (Room r : h.rooms) if (r.contains(s.bed)) room = r;
        String roomName = s.who.given() + "'s room";
        Settlers.tell(level, h, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(s.who.given() + " moved in"))
                .body(Component.literal(roomName + ". " + Work.welcome(s.who)))
                .topic("hearth_people").ttl(20 * 8).priority(4).build());
        if (s.bed != null) {
            level.playSound(null, s.bed, SoundEvents.WOOL_PLACE, SoundSource.NEUTRAL, 0.8f, 0.9f);
            level.sendParticles(HearthContent.HEARTH_EMBER.get(), s.bed.getX() + 0.5, s.bed.getY() + 1.0, s.bed.getZ() + 0.5, 14, 0.5, 0.3, 0.5, 0.01);
        }
        // whoever stands in the room sees its new name once, as the Hearth names any room
        if (room != null) {
            for (ServerPlayer p : Household.online(level.getServer(), h)) {
                if (room.contains(p.blockPosition()) || room.contains(p.blockPosition().above())) HearthService.tellRoom(p, room, roomName);
            }
        }
        if (h.spire == null) dev.awaken.hearth.town.Town.raiseSpire(level, h);
        for (ServerPlayer p : Household.online(level.getServer(), h)) if (dev.awaken.progression.Progression.profile(p).awakened()) {
            dev.awaken.progression.Progression.markNewThing(p, "settler");
        }
        Rescued.settled(level, h, s.who, h.displayName());
    }

    /**
     * Where the road into the land starts on the side the player isn't looking from: the edge of the land toward Carillon
     * (or toward the player's back), on open ground with room to stand.
     */
    static @Nullable BlockPos roadStart(ServerLevel level, HearthRecord h, ServerPlayer near) {
        BlockPos square = dev.awaken.core.api.CityHooks.square(level);
        double dx, dz;
        if (square != null && square.distSqr(h.pos) > 64 * 64) {
            dx = square.getX() - h.pos.getX();
            dz = square.getZ() - h.pos.getZ();
        } else {
            dx = near.getX() - h.pos.getX();
            dz = near.getZ() - h.pos.getZ();
            if (dx * dx + dz * dz < 1) dz = 1;
        }
        double len = Math.hypot(dx, dz);
        int reach = Math.min(h.radius() - 4, 40);
        for (int d = reach; d >= 12; d -= 4) {
            for (int side = 0; side < 5; side++) {
                double a = Math.atan2(dz, dx) + (side == 0 ? 0 : (side % 2 == 1 ? 1 : -1) * 0.25 * ((side + 1) / 2));
                int x = h.pos.getX() + (int) Math.round(Math.cos(a) * d), z = h.pos.getZ() + (int) Math.round(Math.sin(a) * d);
                if (!level.isLoaded(new BlockPos(x, h.pos.getY(), z))) continue;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos feet = new BlockPos(x, y, z);
                if (Math.abs(y - h.pos.getY()) > 12) continue;
                if (level.getBlockState(feet.below()).isSolid() && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                        && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                        && level.getFluidState(feet.below()).isEmpty()) return feet;
            }
        }
        return null;
    }

    /** A floor cell beside the bed inside its room, to walk to. */
    static @Nullable BlockPos bedside(ServerLevel level, Settlers.Bedroom room) {
        BlockPos bed = room.bed();
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = -1; dy <= 1; dy++) {
            BlockPos p = bed.offset(dx, dy, dz);
            if (!room.room().contains(p) || !HomeWalk.stand(level, p)) continue;
            double d = p.distSqr(bed);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    static float yawTo(BlockPos from, BlockPos to) {
        return (float) Math.toDegrees(Math.atan2(-(to.getX() - from.getX()), to.getZ() - from.getZ()));
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
