package dev.awaken.gates.run;

import dev.awaken.gates.FloorState;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateService;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.net.RigPayloads;
import dev.awaken.gates.world.FloorLayout;
import dev.awaken.gates.world.Hash;
import dev.awaken.gates.world.Room;
import dev.awaken.gates.world.RoomGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The Collapse Timer's cave-in (SPEC §17 "Gate Rig"): once a floor's time is up, the ceiling really comes down. Every
 * wave picks three blocks of ceiling round each player still on the floor (one where they are heading), lets dust
 * trickle from them for a second, drops them, and leaves them where they land as rubble. Dodge them; what lands on you
 * hurts. Door axes and features stay clear, so the way on is never buried.
 */
final class RigCaveIn {
    private RigCaveIn() {}

    static final int WARN = 20, PER_WAVE = 3, SPREAD = 5;
    static final float DAMAGE = 2F, SHARE = 0.06F, MOB_DAMAGE = 4F;
    /** The client draws the drop falling at this rate (blocks per tick squared); the server lands it when it arrives. */
    static final double GRAVITY = 0.04;

    /** {@code keep}: it stays where it lands as rubble (never on a door axis, a doorway or a feature). */
    private record Drop(ServerLevel level, long at, boolean landing, BlockPos ceiling, BlockState state, BlockPos land, boolean keep, int theme) {}

    private static final List<Drop> DROPS = new ArrayList<>();

    static void resetAll() {
        DROPS.clear();
    }

    /** One wave over a player in a room of a floor whose time is up. */
    static void wave(ServerLevel level, GateRecord g, FloorLayout f, Room r, ServerPlayer p) {
        FloorState fs = g.floor(f.area, f.floor);
        int wave = fs.rig.caveWaves++;
        RigDirector.dirty(level);
        long now = GateService.now(level.getServer());
        BlockPos o = GateService.world(g, f, 0, r.dy, 0);
        // where they're heading in a second, then two more blocks round them
        double vx = (p.getX() - p.xo) * WARN, vz = (p.getZ() - p.zo) * WARN;
        for (int k = 0; k < PER_WAVE; k++) {
            int x = (int) Math.floor(p.getX() + (k == 0 ? vx : 0)) - o.getX(), z = (int) Math.floor(p.getZ() + (k == 0 ? vz : 0)) - o.getZ();
            if (k > 0) {
                x += Hash.range(g.plan.seed(), wave * 7 + k, f.floor, SPREAD * 2 + 1) - SPREAD;
                z += Hash.range(g.plan.seed(), wave * 7 + k, f.floor + 31, SPREAD * 2 + 1) - SPREAD;
            }
            if (!RoomGeometry.inside(r, x, z) || RoomGeometry.blocked(r, x, z)) continue;
            BlockPos ceil = o.offset(x, RoomGeometry.ceiling(r, x, z), z);
            BlockState s = level.getBlockState(ceil);
            if (s.isAir() || s.hasBlockEntity() || s.getLightEmission() > 0 || s.is(GatesRegistry.BARS.get())) continue;
            BlockPos land = o.offset(x, RoomGeometry.floorOffset(r, x, z), z);
            RigDirector.sendNear(level, Vec3.atCenterOf(ceil), new RigPayloads.Cue(RigPayloads.Cue.CAVE, f.theme.ordinal(),
                    List.of(ceil.asLong()), List.of(Block.getId(s)), WARN, 0));
            if (k == 0) RigDirector.sound(level, Vec3.atCenterOf(ceil), GatesRegistry.SND_RIG_CAVE.get(), 1F, 0.8F + 0.3F * level.getRandom().nextFloat());
            boolean keep = !RoomGeometry.onDoorAxis(r, x, z, 2) && RoomGeometry.tunnel(r, x, z) == null && !nearFeature(r, x, z);
            DROPS.add(new Drop(level, now + WARN, false, ceil, s, land, keep, f.theme.ordinal()));
        }
    }

    private static boolean nearFeature(Room r, int x, int z) {
        for (Room.Feature ft : r.features) if (Math.abs(ft.x() - x) <= 1 && Math.abs(ft.z() - z) <= 1) return true;
        return false;
    }

    static void tick(ServerLevel level, long now) {
        if (DROPS.isEmpty()) return;
        List<Drop> due = new ArrayList<>();
        DROPS.removeIf(d -> {
            if (d.at > now) return false;
            due.add(d);
            return true;
        });
        // each in the level it was made in (an open land's under the sky)
        for (Drop d : due) {
            if (!d.landing) fall(d.level(), d, now);
            else land(d.level(), d);
        }
    }

    /** The block lets go: the ceiling opens, the client draws it falling, and it lands when it gets there. */
    private static void fall(ServerLevel level, Drop d, long now) {
        if (level.getBlockState(d.ceiling) != d.state) return;
        level.setBlock(d.ceiling, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        double h = Math.max(1, d.ceiling.getY() - d.land.getY());
        int ticks = (int) Math.ceil(Math.sqrt(2 * h / GRAVITY));
        RigDirector.sendNear(level, Vec3.atCenterOf(d.ceiling), new RigPayloads.Cue(RigPayloads.Cue.DROP, d.theme, List.of(d.ceiling.asLong()),
                List.of(Block.getId(d.state)), ticks, (float) h));
        DROPS.add(new Drop(d.level(), now + ticks, true, d.ceiling, d.state, d.land, d.keep, d.theme));
    }

    /** It lands: whatever stands there is struck, and the block stays as rubble where the floor can take it. */
    private static void land(ServerLevel level, Drop d) {
        BlockPos at = d.land;
        AABB hit = new AABB(at.getX() - 0.3, at.getY(), at.getZ() - 0.3, at.getX() + 1.3, at.getY() + 2, at.getZ() + 1.3);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, hit, LivingEntity::isAlive)) {
            float dmg = e instanceof Player ? DAMAGE + e.getMaxHealth() * SHARE : MOB_DAMAGE;
            e.hurtServer(level, level.damageSources().source(RigCollapseLive.RUBBLE), dmg);
        }
        RigDirector.sound(level, Vec3.atCenterOf(at), GatesRegistry.SND_RIG_RUBBLE.get(), 1.1F, 0.8F + 0.3F * level.getRandom().nextFloat());
        level.sendParticles(GatesRegistry.P_GRIT.get(), at.getX() + 0.5, at.getY() + 0.2, at.getZ() + 0.5, 14, 0.5, 0.1, 0.5, 0.04);
        if (!d.keep) return;
        if (level.getBlockState(at).isAir() && !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty()
                && level.getEntitiesOfClass(LivingEntity.class, new AABB(at)).isEmpty()) {
            level.setBlock(at, d.state, Block.UPDATE_CLIENTS);
        }
    }

    /** Waves pending (tests). */
    static int pending() {
        return DROPS.size();
    }
}
