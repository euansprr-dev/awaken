package dev.awaken.hearth.settle;

import dev.awaken.Awaken;
import dev.awaken.citizens.api.RosterBuilder;
import dev.awaken.citizens.api.Settlement;
import dev.awaken.citizens.api.SettlementSpec;
import dev.awaken.citizens.api.Settlements;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.room.Room;
import dev.awaken.hearth.room.RoomType;
import dev.awaken.hearth.room.Tally;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The people who live at your Hearth, on the people engine (SPEC §28.8, PLAN 4.7: "your Hearth registers as a
 * settlement through W0's API"). The Hearth is a settlement of its own ({@code hearth:<owner>}, its origin the Anchor,
 * its radius the Hearth's): its places are read from the rooms you built (each settler's bedside, Oren's anvil in the
 * Forge, the Greenhouse's soil for Ilse, the field's edge and the pen's gate for Hettie, the hatch over the shaft for the
 * miner, the Study's lectern, the Lookout's window, the hall's or kitchen's seats for meals, and the yard round the
 * fire of an evening), its street graph is walked over the real blocks ({@link HomeWalk}), and each person's day is the
 * village's rhythm at home: up at six, breakfast, work, the midday meal, work, supper, an evening by the fire, bed. Rebuilt
 * when the people or the rooms they use change (the walk is budgeted over ticks); bodies come and go with the world's
 * shared pool as anywhere else. Doors on their way open and close behind them.
 */
public final class HomePeople {
    private HomePeople() {}

    /** Block reads a tick a home's walk may spend (it runs only while a home's people changed). */
    private static final int WALK_BUDGET = 8_000;

    static final class Home {
        int settlement = -1;
        String built = "";
        boolean dirty = true;
        @Nullable Build job;
        /** Each record's settler id, in roster order. */
        String[] people = new String[0];
        long nextTry;
    }

    private static final Map<UUID, Home> HOMES = new HashMap<>();

    public static void clear() {
        HOMES.clear();
        OPENED.clear();
    }

    /** The people or rooms changed: rebuild at the next check. */
    public static void changed(HearthRecord h) {
        HOMES.computeIfAbsent(h.owner, k -> new Home()).dirty = true;
    }

    /** No one lives here (or the fire went back to a Hearthfire): the settlement stops. */
    public static void stop(HearthRecord h) {
        Home home = HOMES.remove(h.owner);
        if (home != null && home.settlement >= 0) Settlements.unregister(home.settlement);
    }

    /** The settlement key a Hearth runs under. */
    public static String key(UUID owner) {
        return "hearth:" + owner;
    }

    /** The running settlement of a Hearth, if its people are on the engine now. */
    public static @Nullable Settlement settlement(HearthRecord h) {
        Home home = HOMES.get(h.owner);
        return home == null || home.settlement < 0 ? null : Settlements.byId(home.settlement).orElse(null);
    }

    /** The settler a record of a home's settlement is (by the key it runs under), or null. */
    public static Folk.@Nullable Settler settlerOf(HearthRecord h, int rec) {
        Home home = HOMES.get(h.owner);
        if (home == null || rec < 0 || rec >= home.people.length) return null;
        return h.folk.settler(home.people[rec]);
    }

    /** A settler's record in the home's settlement, or -1. */
    public static int recordOf(HearthRecord h, String id) {
        Home home = HOMES.get(h.owner);
        if (home == null) return -1;
        for (int i = 0; i < home.people.length; i++) if (home.people[i].equals(id)) return i;
        return -1;
    }

    /** A settler's body near a player now, or null. */
    public static @Nullable Entity body(HearthRecord h, String id) {
        Settlement s = settlement(h);
        int rec = recordOf(h, id);
        return s == null || rec < 0 ? null : s.body(rec);
    }

    // ---- keeping the settlement in step ----------------------------------------------------------------------------

    /** Every ten seconds: when the people or their rooms changed (or after a restart), plan the home again. */
    static void sync(ServerLevel level, HearthRecord h, long now) {
        List<Folk.Settler> living = new ArrayList<>();
        for (Folk.Settler s : h.folk.settlers) if (s.home()) living.add(s);
        if (living.isEmpty()) {
            stop(h);
            return;
        }
        Home home = HOMES.computeIfAbsent(h.owner, k -> new Home());
        if (home.job != null || now < home.nextTry) return;
        if (!level.isPositionEntityTicking(h.pos)) return;
        // rooms loaded from the save have no cells: read the home again first
        boolean cells = true;
        for (Room r : h.rooms) if (r.cells().isEmpty()) cells = false;
        if (!cells || h.lastScan < 0) {
            h.scheduleScan(now, 40);
            return;
        }
        String sig = signature(h, living);
        if (!home.dirty && sig.equals(home.built) && home.settlement >= 0) return;
        Plan plan = Plan.of(level, h, living);
        if (plan == null) {
            home.nextTry = now + 20 * 30;
            return;
        }
        home.job = new Build(plan, sig);
        home.dirty = false;
    }

    /** What the plan depends on: who lives here, their beds and roles, the rooms and the land's work. */
    static String signature(HearthRecord h, List<Folk.Settler> living) {
        StringBuilder b = new StringBuilder();
        for (Folk.Settler s : living) b.append(s.who.id()).append('/').append(s.who.role()).append('@').append(s.bed == null ? 0 : s.bed.asLong()).append(';');
        for (Room r : h.rooms) b.append(r.key()).append(r.types().hashCode()).append(',');
        b.append(h.yard.fields().size()).append(h.yard.gates().hashCode()).append(h.radius()).append(h.displayName());
        return Integer.toHexString(b.toString().hashCode()) + ":" + b.length();
    }

    /** Each server tick: a home being planned walks on; then the doors bodies pass. */
    public static void tick(ServerLevel level) {
        for (Map.Entry<UUID, Home> e : HOMES.entrySet()) {
            Home home = e.getValue();
            Build job = home.job;
            if (job == null) continue;
            // one home's walk a tick
            if (job.walk.step(WALK_BUDGET)) {
                home.job = null;
                HearthService.data(level.getServer()).of(e.getKey()).ifPresent(h -> register(level, h, home, job));
            }
            break;
        }
        doors(level);
    }

    private static void register(ServerLevel level, HearthRecord h, Home home, Build job) {
        Built built = job.build();
        if (home.settlement >= 0) Settlements.unregister(home.settlement);
        home.settlement = -1;
        if (built == null) {
            home.nextTry = level.getGameTime() + 20 * 60;
            return;
        }
        SettlementSpec spec = new SettlementSpec(key(h.owner), h.displayName(), h.pos, h.radius(), built.roster, false);
        var s = Settlements.register(level, spec);
        if (s.isEmpty()) {
            Awaken.LOG.warn("[hearth] {}'s people could not start on the people engine", h.ownerName);
            home.nextTry = level.getGameTime() + 20 * 60;
            return;
        }
        home.settlement = s.get().id();
        home.people = built.people;
        home.built = job.signature;
        Awaken.LOG.info("[hearth] {}'s home: {} people, {} places, {} cells walked, {} unreached", h.ownerName, built.people.length,
                built.places, job.walk.cells(), built.unreached);
    }

    // ---- doors ----------------------------------------------------------------------------------------------------

    /** Doors opened for someone going through, and when (closed again once nobody's in the doorway). */
    private static final Map<Long, Long> OPENED = new LinkedHashMap<>();

    private static void doors(ServerLevel level) {
        long now = level.getGameTime();
        if (now % 2 != 0) return;
        for (Home home : HOMES.values()) {
            if (home.settlement < 0) continue;
            var s = Settlements.byId(home.settlement).orElse(null);
            if (s == null || !s.awake()) continue;
            for (int i = 0; i < home.people.length; i++) {
                Entity b = s.body(i);
                if (b == null) continue;
                BlockPos feet = b.blockPosition();
                // the door a step ahead (or the one they're in)
                double yaw = Math.toRadians(b.getYRot());
                BlockPos ahead = BlockPos.containing(b.getX() - Math.sin(yaw) * 0.9, b.getY() + 0.1, b.getZ() + Math.cos(yaw) * 0.9);
                for (BlockPos p : new BlockPos[]{feet, ahead}) open(level, p, now);
            }
        }
        if (OPENED.isEmpty()) return;
        var it = OPENED.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (now - e.getValue() < 30) continue;
            BlockPos p = BlockPos.of(e.getKey());
            if (!level.isLoaded(p)) {
                it.remove();
                continue;
            }
            if (!level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new AABB(p).inflate(1.2), x -> !(x instanceof Animal)).isEmpty()) {
                e.setValue(now - 20); // someone's in the doorway: try again shortly
                continue;
            }
            BlockState st = level.getBlockState(p);
            if (st.getBlock() instanceof DoorBlock door && st.getValue(DoorBlock.OPEN)) door.setOpen(null, level, st, p, false);
            it.remove();
        }
    }

    private static void open(ServerLevel level, BlockPos p, long now) {
        BlockState st = level.getBlockState(p);
        if (!(st.getBlock() instanceof DoorBlock door) || st.is(net.minecraft.world.level.block.Blocks.IRON_DOOR)) return;
        BlockPos lower = st.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? p.below() : p;
        BlockState ls = level.getBlockState(lower);
        if (!(ls.getBlock() instanceof DoorBlock)) return;
        if (!ls.getValue(DoorBlock.OPEN)) {
            door.setOpen(null, level, ls, lower, true);
            OPENED.put(lower.asLong(), now);
        } else if (OPENED.containsKey(lower.asLong())) OPENED.put(lower.asLong(), now);
    }

    // ---- the plan: places read from the rooms --------------------------------------------------------------------

    /** Where someone stands, which way they face. */
    record Spot(BlockPos feet, float yaw) {}

    /** Somewhere people go, of a kind, with its spots (the first is its node). */
    record Where(String kind, List<Spot> spots, @Nullable Room room) {
        BlockPos at() {
            return spots.getFirst().feet;
        }
    }

    /** Everything a home's day needs, read from its rooms and land; the walk's targets. */
    static final class Plan {
        final ServerLevel level;
        final HearthRecord h;
        final List<Folk.Settler> living;
        BlockPos hub;
        Where fire, meal, watch;
        final Map<String, Where> beds = new LinkedHashMap<>(), work = new LinkedHashMap<>(), work2 = new LinkedHashMap<>();
        final List<Where> errands = new ArrayList<>();

        private Plan(ServerLevel level, HearthRecord h, List<Folk.Settler> living) {
            this.level = level;
            this.h = h;
            this.living = living;
        }

        static @Nullable Plan of(ServerLevel level, HearthRecord h, List<Folk.Settler> living) {
            Plan p = new Plan(level, h, living);
            var hub = HearthService.safeSpotNear(level, h.pos);
            p.hub = BlockPos.containing(hub);
            if (!HomeWalk.stand(level, p.hub)) return null;
            p.fire = new Where("tavern_out", ring(level, h.pos, 2, 4, 8), null);
            if (p.fire.spots.isEmpty()) p.fire = new Where("stand", List.of(new Spot(p.hub, yaw(p.hub, h.pos))), null);
            p.meal = meal(level, h);
            p.watch = new Where("post", List.of(edge(level, h, p.hub)), null);
            for (Folk.Settler s : living) {
                if (s.bed != null) {
                    Settlers.Bedroom room = null;
                    for (Settlers.Bedroom b : Settlers.bedrooms(level, h)) if (b.bed().equals(s.bed)) room = b;
                    BlockPos side = room == null ? null : Arrival.bedside(level, room);
                    if (side != null) p.beds.put(s.who.id(), new Where("door", List.of(new Spot(side, yaw(side, s.bed))), room.room()));
                }
                Where w = workOf(level, h, s.who);
                if (w != null) p.work.put(s.who.id(), w);
                if ("hettie".equals(s.who.role()) || "farmer".equals(s.who.role())) {
                    Where pen = pen(level, h);
                    if (pen != null) p.work2.put(s.who.id(), pen);
                }
            }
            // errands round the place: outside each used room's door, the field's edge
            Set<Room> seen = new HashSet<>();
            for (Where w : p.beds.values()) if (w.room != null && seen.add(w.room)) outside(level, w.room).ifPresent(p.errands::add);
            for (Where w : p.work.values()) {
                if (w.room != null && seen.add(w.room)) outside(level, w.room).ifPresent(p.errands::add);
                else if (w.room == null) p.errands.add(new Where("stand", w.spots, null));
            }
            return p;
        }

        /** Every cell the walk must reach. */
        List<BlockPos> targets() {
            List<BlockPos> out = new ArrayList<>();
            out.add(hub);
            for (Where w : all()) for (Spot s : w.spots) out.add(s.feet);
            return out;
        }

        List<Where> all() {
            List<Where> out = new ArrayList<>();
            out.add(fire);
            if (meal != null) out.add(meal);
            out.add(watch);
            out.addAll(beds.values());
            out.addAll(work.values());
            out.addAll(work2.values());
            out.addAll(errands);
            return out;
        }

        /** The box the walk keeps to: round every target, within the land. */
        BlockPos[] box() {
            int x0 = hub.getX(), x1 = x0, z0 = hub.getZ(), z1 = z0, y0 = hub.getY(), y1 = y0;
            for (BlockPos t : targets()) {
                x0 = Math.min(x0, t.getX());
                x1 = Math.max(x1, t.getX());
                z0 = Math.min(z0, t.getZ());
                z1 = Math.max(z1, t.getZ());
                y0 = Math.min(y0, t.getY());
                y1 = Math.max(y1, t.getY());
            }
            int r = h.radius() + 2;
            return new BlockPos[]{new BlockPos(Math.max(x0 - 10, h.pos.getX() - r), Math.max(y0 - 8, h.pos.getY() - 32), Math.max(z0 - 10, h.pos.getZ() - r)),
                    new BlockPos(Math.min(x1 + 10, h.pos.getX() + r), Math.min(y1 + 8, h.pos.getY() + 64), Math.min(z1 + 10, h.pos.getZ() + r))};
        }
    }

    /** A settler's work place: their trade's room (at its furniture), or the land's. */
    static @Nullable Where workOf(ServerLevel level, HearthRecord h, Folk.Person p) {
        return switch (p.role()) {
            case "oren", "smith" -> at(level, h, RoomType.ARMORY, "anvil", s -> s.is(BlockTags.ANVIL));
            case "ilse" -> at(level, h, RoomType.GREENHOUSE, "wash",
                    s -> s.getBlock() instanceof FarmlandBlock || s.getBlock() instanceof FlowerPotBlock || s.is(BlockTags.SMALL_FLOWERS) || s.is(BlockTags.CROPS));
            case "teodor" -> at(level, h, RoomType.STUDY, "stand", s -> s.getBlock() instanceof LecternBlock);
            case "mira" -> at(level, h, RoomType.LOOKOUT, "stand",
                    s -> s.is(net.neoforged.neoforge.common.Tags.BlockItems.GLASS_PANES.block()) || s.is(net.neoforged.neoforge.common.Tags.BlockItems.GLASS_BLOCKS.block()));
            case "sun" -> at(level, h, RoomType.SANCTUARY, "stand", s -> s.getBlock() instanceof net.minecraft.world.level.block.BedBlock);
            case "miner" -> hatch(level, h);
            case "hettie", "farmer" -> field(level, h);
            default -> null;
        };
    }

    /** A standing spot beside the furniture of the first room of a kind, facing it. */
    static @Nullable Where at(ServerLevel level, HearthRecord h, RoomType type, String kind, Predicate<BlockState> furniture) {
        for (Room r : h.rooms) {
            if (!r.types().contains(type)) continue;
            Spot s = besideIn(level, r, furniture);
            if (s != null) return new Where(kind, List.of(s), r);
        }
        return null;
    }

    /** A cell of the room beside a block it holds (or a block in its walls, or the floor under it) that someone can stand in. */
    static @Nullable Spot besideIn(ServerLevel level, Room r, Predicate<BlockState> furniture) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockPos a = r.min().offset(-1, -1, -1), b = r.max().offset(1, 1, 1);
        long volume = (long) (b.getX() - a.getX() + 1) * (b.getY() - a.getY() + 1) * (b.getZ() - a.getZ() + 1);
        if (volume > 40_000) return null;
        Spot best = null;
        double bd = Double.MAX_VALUE;
        BlockPos c = r.centre();
        for (int y = a.getY(); y <= b.getY(); y++) for (int z = a.getZ(); z <= b.getZ(); z++) for (int x = a.getX(); x <= b.getX(); x++) {
            m.set(x, y, z);
            if (!furniture.test(level.getBlockState(m))) continue;
            BlockPos f = m.immutable();
            for (int dy = 0; dy <= 1; dy++) {
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos s = f.relative(d).above(dy);
                    if (!r.contains(s) || !HomeWalk.stand(level, s)) continue;
                    double dd = s.distSqr(c);
                    if (dd < bd) {
                        bd = dd;
                        best = new Spot(s, yaw(s, f));
                    }
                }
            }
        }
        return best;
    }

    /** The miner's place: beside the hatch over the shaft (going in there is going down). */
    static @Nullable Where hatch(ServerLevel level, HearthRecord h) {
        for (Room r : h.rooms) {
            if (!r.types().contains(RoomType.MINE_HEAD)) continue;
            BlockPos hatch = new BlockPos(r.fact(Tally.F_SHAFT_X), r.fact(Tally.F_SHAFT_Y), r.fact(Tally.F_SHAFT_Z));
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos s = hatch.relative(d).above();
                if (r.contains(s) && HomeWalk.stand(level, s)) return new Where("door", List.of(new Spot(s, yaw(s, hatch))), r);
            }
        }
        return null;
    }

    /** The edge of the field: a cell beside farmland (never on it), nearest the Anchor, facing the crop. */
    static @Nullable Where field(ServerLevel level, HearthRecord h) {
        List<Spot> spots = new ArrayList<>();
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos f : h.yard.fields()) {
            double d = f.distSqr(h.pos);
            if (d < bd) {
                bd = d;
                best = f;
            }
        }
        if (best == null) return null;
        // spots along the field's edge, near the first (she moves along it as she works)
        for (BlockPos f : h.yard.fields()) {
            if (f.distSqr(best) > 8 * 8 || spots.size() >= 4) continue;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos s = f.relative(d).above();
                if (!HomeWalk.stand(level, s)) continue;
                boolean dup = false;
                for (Spot o : spots) dup |= o.feet.distSqr(s) < 9;
                if (!dup) spots.add(new Spot(s, yaw(s, f.above())));
                break;
            }
        }
        return spots.isEmpty() ? null : new Where("stand", spots, null);
    }

    /** Outside the pen's gate, on the side away from the beasts. */
    static @Nullable Where pen(ServerLevel level, HearthRecord h) {
        if (h.yard.gates().isEmpty() || h.yard.beasts().isEmpty()) return null;
        double bx = 0, bz = 0;
        for (BlockPos b : h.yard.beasts()) {
            bx += b.getX();
            bz += b.getZ();
        }
        bx /= h.yard.beasts().size();
        bz /= h.yard.beasts().size();
        BlockPos gate = null;
        double gd = Double.MAX_VALUE;
        for (BlockPos g : h.yard.gates()) {
            double d = (g.getX() - bx) * (g.getX() - bx) + (g.getZ() - bz) * (g.getZ() - bz);
            if (d < gd) {
                gd = d;
                gate = g;
            }
        }
        if (gate == null || gd > 16 * 16) return null;
        Spot out = null;
        double far = -1;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos s = gate.relative(d);
            if (!HomeWalk.stand(level, s)) continue;
            double dd = (s.getX() - bx) * (s.getX() - bx) + (s.getZ() - bz) * (s.getZ() - bz);
            if (dd > far) {
                far = dd;
                out = new Spot(s, yaw(s, gate));
            }
        }
        return out == null ? null : new Where("stand", List.of(out), null);
    }

    /** Meals: the hall's seats (a stair on its floor), else the kitchen's (or room beside its stove), else by the fire. */
    static @Nullable Where meal(ServerLevel level, HearthRecord h) {
        for (RoomType t : new RoomType[]{RoomType.HALL, RoomType.KITCHEN}) {
            for (Room r : h.rooms) {
                if (!r.types().contains(t)) continue;
                List<Spot> seats = seats(level, r);
                if (!seats.isEmpty()) return new Where("sit", seats, r);
                Spot s = besideIn(level, r, st -> st.getBlock() instanceof net.minecraft.world.level.block.CraftingTableBlock
                        || st.getBlock() instanceof net.minecraft.world.level.block.AbstractFurnaceBlock);
                if (s != null) return new Where("stand", List.of(s), r);
            }
        }
        return null;
    }

    /** The cells over the room's bottom stairs (seats), facing out of the stair's back, at most eight. */
    static List<Spot> seats(ServerLevel level, Room r) {
        List<Spot> out = new ArrayList<>();
        if (r.cells().isEmpty()) return out;
        LongIterator it = r.cells().iterator();
        while (it.hasNext() && out.size() < 8) {
            BlockPos c = BlockPos.of(it.nextLong());
            BlockState below = level.getBlockState(c.below());
            if (!(below.getBlock() instanceof StairBlock) || below.getValue(StairBlock.HALF) != Half.BOTTOM) continue;
            if (!HomeWalk.stand(level, c)) continue;
            Direction back = below.getValue(StairBlock.FACING);
            out.add(new Spot(c, yaw(c, c.relative(back.getOpposite()))));
        }
        return out;
    }

    /** Spots round a point at a ring's distance, spread by angle, facing it. */
    static List<Spot> ring(ServerLevel level, BlockPos c, int r0, int r1, int n) {
        List<Spot> cells = new ArrayList<>();
        for (int dz = -r1; dz <= r1; dz++) for (int dx = -r1; dx <= r1; dx++) {
            int d = Math.max(Math.abs(dx), Math.abs(dz));
            if (d < r0 || d > r1) continue;
            for (int dy = 1; dy >= -2; dy--) {
                BlockPos s = c.offset(dx, dy, dz);
                if (HomeWalk.stand(level, s)) {
                    cells.add(new Spot(s, yaw(s, c)));
                    break;
                }
            }
        }
        cells.sort(java.util.Comparator.comparingDouble(s -> Math.atan2(s.feet.getZ() - c.getZ(), s.feet.getX() - c.getX())));
        List<Spot> out = new ArrayList<>();
        if (cells.isEmpty()) return out;
        int step = Math.max(1, cells.size() / n);
        for (int i = 0; i < cells.size() && out.size() < n; i += step) out.add(cells.get(i));
        return out;
    }

    /** A spot at the yard's edge looking out (Mira's watch at dusk). */
    static Spot edge(ServerLevel level, HearthRecord h, BlockPos hub) {
        for (int d = 10; d >= 3; d--) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos s = hub.relative(dir, d);
                for (int dy = 2; dy >= -2; dy--) if (HomeWalk.stand(level, s.above(dy))) return new Spot(s.above(dy), yaw(hub, s));
            }
        }
        return new Spot(hub, 0);
    }

    /** Outside a room's door: the cell beyond the doorway from inside. */
    static java.util.Optional<Where> outside(ServerLevel level, Room r) {
        if (r.cells().isEmpty()) return java.util.Optional.empty();
        LongIterator it = r.cells().iterator();
        while (it.hasNext()) {
            BlockPos c = BlockPos.of(it.nextLong());
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos door = c.relative(d);
                BlockState s = level.getBlockState(door);
                if (!(s.getBlock() instanceof DoorBlock) || s.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;
                BlockPos out = door.relative(d);
                if (r.contains(out)) continue;
                for (int dy = 0; dy >= -1; dy--) {
                    BlockPos o = out.above(dy);
                    if (HomeWalk.stand(level, o)) return java.util.Optional.of(new Where("stand", List.of(new Spot(o, yaw(o, door.relative(d, 3)))), null));
                }
            }
        }
        return java.util.Optional.empty();
    }

    static float yaw(BlockPos from, BlockPos to) {
        return (float) Math.toDegrees(Math.atan2(-(to.getX() - from.getX()), to.getZ() - from.getZ()));
    }

    // ---- building the roster ----------------------------------------------------------------------------------------

    record Built(RosterBuilder roster, String[] people, int places, int unreached) {}

    /** A plan being walked, then laid out as a roster. */
    static final class Build {
        final Plan plan;
        final String signature;
        final HomeWalk walk;

        Build(Plan plan, String signature) {
            this.plan = plan;
            this.signature = signature;
            BlockPos[] box = plan.box();
            this.walk = new HomeWalk(plan.level, plan.hub, box[0], box[1]);
        }

        /** A lot over a room's box (offsets from the Anchor), tagged "room": no city event takes a home's room. */
        private static int lot(RosterBuilder b, Room r, BlockPos o) {
            return b.lot(r.min().getX() - o.getX(), r.min().getZ() - o.getZ(), r.max().getX() - o.getX(), r.max().getZ() - o.getZ(),
                    r.max().getY() + 1 - o.getY(), Integer.MIN_VALUE, Integer.MIN_VALUE, "room");
        }

        @Nullable Built build() {
            Plan p = plan;
            HearthRecord h = p.h;
            BlockPos o = h.pos;
            RosterBuilder b = new RosterBuilder();
            Map<Long, Integer> nodes = new HashMap<>();
            Set<Long> edges = new HashSet<>();
            int[] unreached = {0};
            // a node for a cell (feet), a door's for the places people go in at
            java.util.function.BiFunction<BlockPos, Boolean, Integer> node = (cell, door) -> nodes.computeIfAbsent(cell.asLong(), k -> door
                    ? b.door(cell.getX() + 0.5, cell.getY() - 1 - o.getY(), cell.getZ() + 0.5, -1)
                    : b.junction(cell.getX() + 0.5, cell.getY() - 1 - o.getY(), cell.getZ() + 0.5));
            node.apply(p.hub, false);
            // the places people go in at are door nodes (made first: a node's kind is fixed when it's made)
            for (Where w : p.all()) if (w.kind.equals("door") && walk.reached(w.at())) node.apply(w.at(), true);
            // every target joined to the yard along its walked path's corners
            for (Where w : p.all()) {
                for (Spot s : w.spots) {
                    List<BlockPos> path = walk.path(s.feet);
                    if (path.isEmpty()) {
                        unreached[0]++;
                        continue;
                    }
                    List<BlockPos> corners = walk.corners(path);
                    for (int i = 1; i < corners.size(); i++) {
                        int na = node.apply(corners.get(i - 1), false), nb = node.apply(corners.get(i), false);
                        if (na == nb) continue;
                        long ek = Math.min(na, nb) * 1_000_003L + Math.max(na, nb);
                        if (edges.add(ek)) b.street(na, nb);
                    }
                }
            }
            b.district("the yard");
            // places, as offsets from the Anchor
            Map<Where, Integer> placeOf = new HashMap<>(), lotOf = new HashMap<>();
            int yard = b.lot(p.hub.getX() - 3 - o.getX(), p.hub.getZ() - 3 - o.getZ(), p.hub.getX() + 3 - o.getX(), p.hub.getZ() + 3 - o.getZ(),
                    p.hub.getY() + 2 - o.getY(), Integer.MIN_VALUE, Integer.MIN_VALUE, "yard");
            int places = 0;
            for (Where w : p.all()) {
                if (!walk.reached(w.at())) continue;
                Integer n = nodes.get(w.at().asLong());
                if (n == null) continue;
                BlockPos at = w.at();
                RosterBuilder.Place pl = b.place(w.kind, n, at.getX() + 0.5 - o.getX(), at.getY() - 1 - o.getY(), at.getZ() + 0.5 - o.getZ())
                        .yaw(w.spots.getFirst().yaw);
                if (w.kind.equals("door")) {
                    int lot = w.room == null ? yard : lot(b, w.room, o);
                    pl.ref(lot);
                    lotOf.put(w, lot);
                }
                if (!w.kind.equals("door")) {
                    for (Spot s : w.spots) if (walk.reached(s.feet)) pl.spot(s.feet.getX() + 0.5 - o.getX(), s.feet.getZ() + 0.5 - o.getZ(), s.yaw);
                    pl.cap(Math.max(1, w.spots.size()));
                }
                placeOf.put(w, pl.id());
                places++;
            }
            Integer fire = placeOf.get(p.fire);
            if (fire == null) return null;
            Integer meal = p.meal == null ? null : placeOf.get(p.meal);
            if (meal == null) meal = fire;
            Integer watch = placeOf.get(p.watch);
            List<Integer> errandIds = new ArrayList<>(List.of(fire));
            for (Where w : p.errands) if (placeOf.containsKey(w)) errandIds.add(placeOf.get(w));
            if (meal != fire.intValue()) errandIds.add(meal);
            int errands = b.route("wander", errandIds.stream().mapToInt(Integer::intValue).toArray());
            int group = b.group("home");
            // the people, one household each (their room is their home)
            List<String> ids = new ArrayList<>();
            int i = 0;
            for (Folk.Settler s : p.living) {
                Where bed = p.beds.get(s.who.id());
                Integer home = bed == null ? null : placeOf.get(bed);
                int lot = bed == null ? yard : lotOf.getOrDefault(bed, yard);
                if (home == null) {
                    // between rooms (or the bed can't be walked to): they sleep by the fire, going in at the yard
                    int n = node.apply(p.hub, false);
                    home = b.place("door", n, p.hub.getX() + 0.5 - o.getX(), p.hub.getY() - 1 - o.getY(), p.hub.getZ() + 0.5 - o.getZ()).ref(yard).id();
                    lot = yard;
                }
                int hh = b.household(s.who.family().isEmpty() ? s.who.given() : s.who.family(), home, lot);
                Folk.Person who = s.who;
                RosterBuilder.Person pr = b.person(who.given(), who.family(), who.trade()).household(hh).home(home).group(group)
                        .build(Faces.slim(who) ? 1 : 0).age(2).face(Faces.spec(who) + ";" + Work.title(who));
                Where w = p.work.get(who.id());
                Integer work = w == null ? null : placeOf.get(w);
                Where w2 = p.work2.get(who.id());
                Integer work2 = w2 == null ? null : placeOf.get(w2);
                Days.of(pr, i, who.role(), home, work, work2, meal, fire, watch, errands);
                ids.add(who.id());
                i++;
            }
            if (ids.isEmpty()) return null;
            return new Built(b, ids.toArray(String[]::new), places, unreached[0]);
        }
    }
}
