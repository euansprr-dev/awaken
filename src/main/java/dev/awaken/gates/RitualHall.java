package dev.awaken.gates;

import dev.awaken.gates.world.FloorLayout;
import dev.awaken.gates.world.GateLayout;
import dev.awaken.gates.world.PiecesRitual;
import dev.awaken.gates.world.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Ritual of the Bell's hall as the story's ritual director sees it: where everything stands in the gate world
 * (docs/proposals/the-ritual.md). Built from the same pure geometry the painter uses ({@link PiecesRitual}), so it
 * always matches the blocks. Public API (SPEC §12), read through {@link Gates#ritualHall}.
 *
 * @param hall       the hall's interior, floor to vault
 * @param antechamber the room you arrive in, where everyone gathers
 * @param throne     where the Seated One sits (its feet, on the dais top), facing the doors
 * @param doorway    the blocks that seal the great doors (the hall's side), each with its lateral 6..9 in {@code doorLat}
 * @param lights     every candle, fire and fill lamp, doors first, with its lit state
 * @param ring       the rune ring's eight segments: the floor blocks of each (segment 0 points at the doors)
 * @param plates     the three plates' centres (floor level)
 * @param words      where the rules appear (centre of the line), facing the doors
 */
public record RitualHall(int gateId, ServerLevel level, int floorY, AABB hall, AABB antechamber, Vec3 throne, float throneYaw, Vec3 altar,
                         Vec3 bell, Vec3 doorway, List<BlockPos> doorBlocks, List<Integer> doorLat, List<Statue> statues,
                         List<Light> lights, List<List<BlockPos>> ring, List<List<BlockState>> ringTop, BlockState ringBelow,
                         List<Vec3> plates, Vec3 words) {

    public record Statue(Vec3 at, float yaw) {}

    public record Light(BlockPos pos, BlockState lit, double order, boolean fill) {}

    /** Inside the hall proper (past the doors), with a little margin above the floor. */
    public boolean inHall(Entity e) {
        return e.level() == level && hall.contains(e.position());
    }

    /** In the antechamber (arrived, not yet through the doors). */
    public boolean inAntechamber(Entity e) {
        return e.level() == level && antechamber.contains(e.position());
    }

    /** The plate (0..2) this position stands on, or -1. */
    public int plateUnder(Vec3 p) {
        for (int k = 0; k < plates.size(); k++) {
            Vec3 c = plates.get(k);
            if (Math.abs(p.y - c.y) < 1.6 && Math.hypot(p.x - c.x, p.z - c.z) < PiecesRitual.PLATE_SIZE + 0.4) return k;
        }
        return -1;
    }

    static Optional<RitualHall> of(MinecraftServer server, GateRecord g) {
        if (g == null || !g.plan.ritual()) return Optional.empty();
        ServerLevel level = GateService.gateLevel(server);
        if (level == null) return Optional.empty();
        FloorLayout f = GateLayout.of(g.plan).floor(0, 1);
        if (f == null) return Optional.empty();
        Room hall = null, ante = f.room(f.start);
        for (Room r : f.rooms) if (r.type == Room.Type.RITUAL) hall = r;
        if (hall == null || ante == null) return Optional.empty();
        BlockPos o = GateService.world(g, f, 0, 0, 0);
        double ox = o.getX(), oy = o.getY(), oz = o.getZ();
        AABB hallBox = new AABB(ox + hall.x0(), oy - 3, oz + hall.z0() + 2, ox + hall.x1() + 1, oy + PiecesRitual.VAULT_H, oz + hall.z1() + 1);
        AABB anteBox = new AABB(ox + ante.x0(), oy - 1, oz + ante.z0(), ox + ante.x1() + 1, oy + ante.height, oz + ante.z1() + 3);
        PiecesRitual.Spot t = PiecesRitual.throne(hall);
        Vec3 throne = new Vec3(ox + t.x(), oy + t.dy(), oz + t.z());
        PiecesRitual.Spot a = PiecesRitual.altar(hall);
        Vec3 altar = new Vec3(ox + a.x(), oy, oz + a.z());
        Vec3 bell = new Vec3(ox + a.x(), oy + PiecesRitual.BELL_LIP + PiecesRitual.BELL_TALL / 2.0, oz + a.z());
        int[] d = PiecesRitual.doorway(hall);
        Vec3 doorway = new Vec3(ox + d[0], oy, oz + d[1] + 0.5);
        List<BlockPos> door = new ArrayList<>();
        List<Integer> lat = new ArrayList<>();
        for (int[] b : PiecesRitual.doorBlocks(hall)) {
            door.add(GateService.world(g, f, b[0], b[1], b[2]));
            lat.add(b[3]);
        }
        List<Statue> statues = new ArrayList<>();
        for (PiecesRitual.Spot s : PiecesRitual.statues(hall)) statues.add(new Statue(new Vec3(ox + s.x(), oy + s.dy(), oz + s.z()), s.yaw()));
        List<Light> lights = new ArrayList<>();
        for (PiecesRitual.Light l : PiecesRitual.lights(hall)) lights.add(new Light(GateService.world(g, f, l.x(), l.dy(), l.z()), l.lit(), l.order(), l.fill()));
        List<List<BlockPos>> ring = new ArrayList<>();
        List<List<BlockState>> ringTop = new ArrayList<>();
        for (int k = 0; k < 8; k++) {
            ring.add(new ArrayList<>());
            ringTop.add(new ArrayList<>());
        }
        for (int x = hall.x0(); x <= hall.x1(); x++) {
            for (int z = hall.z0(); z <= hall.z1(); z++) {
                int seg = PiecesRitual.ringSegment(hall, x, z);
                if (seg < 0 || PiecesRitual.plateAt(hall, x, z) >= 0) continue;
                ring.get(seg).add(GateService.world(g, f, x, -1, z));
                ringTop.get(seg).add(PiecesRitual.floorState(hall, x, z));
            }
        }
        for (int k = 0; k < 8; k++) {
            ring.set(k, List.copyOf(ring.get(k)));
            ringTop.set(k, List.copyOf(ringTop.get(k)));
        }
        List<Vec3> plates = new ArrayList<>();
        for (int k = 0; k < 3; k++) {
            double[] p = PiecesRitual.plate(hall, k);
            plates.add(new Vec3(ox + p[0], oy, oz + p[1]));
        }
        Vec3 words = new Vec3(ox + PiecesRitual.xAt(hall, 0), oy + PiecesRitual.DAIS_H + PiecesRitual.WORDS_H, oz + PiecesRitual.zAt(hall, PiecesRitual.WORDS_W));
        return Optional.of(new RitualHall(g.id, level, (int) oy, hallBox, anteBox, throne, t.yaw(), altar, bell, doorway, List.copyOf(door),
                List.copyOf(lat), List.copyOf(statues), List.copyOf(lights), List.copyOf(ring), List.copyOf(ringTop),
                g.plan.theme().palette().fill(), List.copyOf(plates), words));
    }
}
