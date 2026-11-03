package dev.awaken.lucidium.trial;

import dev.awaken.lucidium.LucidiumContent;
import dev.awaken.lucidium.data.Snapshot;
import dev.awaken.lucidium.plan.DreamPalette;
import dev.awaken.lucidium.plan.DreamPlan;
import dev.awaken.lucidium.plan.DreamSign;
import dev.awaken.lucidium.plan.Fragment;
import dev.awaken.lucidium.world.BlueprintCompiler;
import dev.awaken.lucidium.world.Cells;
import dev.awaken.lucidium.world.DreamBlueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Builds a trial's dream (SPEC §9.3): you arrive in your own room (the Day Journal's latest home, rebuilt in the dream's
 * pearl, a hallway cut from your bed to the edge) or, with no home yet, a dream bedroom; a pane of dream glass closes
 * the doorway until you take control; beyond it the trial's course runs along +z over the cloud sea to the Waking Door
 * (or a bell before it). Pure and free of world access, so it runs off-thread like the island compiler.
 */
public final class TrialCompiler {
    private TrialCompiler() {}

    public static final int BASE_Y = 100;

    /** Gentler courses tried after the first fails its solver; the last is a plain road, which always passes. */
    static final int VARIANTS = 4, PLAIN = VARIANTS;

    /**
     * The compiled dream: the trial's course, a gentler variant when one fails its solver (logged; the
     * dreams_runs_solvable test keeps that from happening), a plain road at worst. A trial this version doesn't know
     * (a plan from a newer one) is dreamed as the first.
     */
    public static DreamBlueprint compile(DreamPlan plan, int cell, Function<String, Optional<Fragment>> memories) {
        Trial known = Trial.byId(plan.trial());
        Trial trial = known == null ? Trial.FIRST : known;
        BlockPos o = Cells.center(cell, BASE_Y);
        for (int variant = 0; variant < PLAIN; variant++) {
            DreamBlueprint bp = attempt(plan, trial, cell, o, memories, variant);
            if (bp != null) return bp;
        }
        DreamBlueprint bp = attempt(plan, trial, cell, o, memories, PLAIN);
        if (bp == null) throw new IllegalStateException("a plain dream road failed its solver");
        return bp;
    }

    private static @Nullable DreamBlueprint attempt(DreamPlan plan, Trial trial, int cell, BlockPos o,
                                                    Function<String, Optional<Fragment>> memories, int variant) {
        DreamPalette palette = DreamPalette.of("pearl", plan.hueShift());
        List<DreamBlueprint.Piece> pieces = new ArrayList<>();

        // ---- the room ---------------------------------------------------------------------------------------------
        CourseKit kit = new CourseKit(o.getX(), o.getY(), o.getZ() + 9);
        Fragment home = homeOf(plan, memories);
        List<BlockPos> doorway = new ArrayList<>();
        Vec3 start;
        if (home != null) {
            Fragment f = home.remapped(palette, plan.seed(), LucidiumContent.SHIFTING_STONE.get().defaultBlockState(), 90);
            BlockPos min = new BlockPos(o.getX() - f.sx / 2, o.getY() - Snapshot.FLOOR, o.getZ() - f.sz / 2);
            pieces.add(new DreamBlueprint.FragmentPiece(f, min));
            // a hallway cut from beside your bed out to the room's edge, floored with the road
            for (int dz = 1; dz <= 8; dz++)
                for (int dx = -1; dx <= 1; dx++) {
                    kit.block(o.getX() + dx, o.getY() - 1, o.getZ() + dz, kit.road);
                    for (int dy = 0; dy <= 2; dy++) kit.clear(o.getX() + dx, o.getY() + dy, o.getZ() + dz);
                }
            start = new Vec3(o.getX() + 0.5, o.getY(), o.getZ() + 1.5);
        } else {
            bedroom(kit, o);
            start = new Vec3(o.getX() + 0.5, o.getY(), o.getZ() + 0.5);
        }
        // the doorway: a pane of dream glass where the room meets the course, gone the moment you take control
        BlockState glass = LucidiumContent.DREAM_GLASS.get().defaultBlockState();
        int dzDoor = o.getZ() + (home != null ? 8 : 4);
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = 0; dy <= 2; dy++) {
                BlockPos p = new BlockPos(o.getX() + dx, o.getY() + dy, dzDoor);
                kit.block(p.getX(), p.getY(), p.getZ(), glass);
                doorway.add(p);
            }

        // ---- the course -------------------------------------------------------------------------------------------
        boolean bell = plan.trialRef().startsWith("bell:");
        Fragment place = node(plan, DreamPlan.VISION, memories), today = trial == Trial.WRONG ? todayOf(plan, memories) : null;
        if (trial.run()) lay(kit, trial, variant, bell);
        else RoomLayouts.lay(kit, trial, bell, place != null, plan.seed(), today != null);
        int par = kit.solve();
        if (par < 0) {
            dev.awaken.Awaken.LOG.warn("Lucidium: trial {} variant {} failed its solver: {}", trial.id, variant, kit.failure());
            return null;
        }
        int seconds = trial.seconds > 0 ? trial.seconds : Math.max(60, (int) Math.ceil(par * 1.8 / 20.0));

        // ---- dream signs beside the course -----------------------------------------------------------------------
        Map<Long, BlockState> props = new LinkedHashMap<>();
        Map<DreamSign, DreamBlueprint.Sign> signs = new EnumMap<>(DreamSign.class);
        // a Run's signs half way along; a Room's by the road in, where the room's walls don't hide them
        int mid = trial.run() ? (o.getZ() + 9 + kit.z) / 2 : o.getZ() + 12;
        for (String name : plan.signs()) DreamSign.byName(name).ifPresent(s -> sign(s, props, signs, kit.x, BASE_Y, mid, plan.seed()));
        Map<Long, BlockState> all = new LinkedHashMap<>(props);
        all.putAll(kit.blocks());
        pieces.add(new DreamBlueprint.BlockListPiece(all));

        Course course = kit.build(trial, start, 0f, doorway, par, seconds);
        Course.Room room = course.room();
        // today's place in the middle of what's wrong here, as it was (not in the dream's colours)
        if (room != null && today != null)
            pieces.add(new DreamBlueprint.FragmentPiece(today, new BlockPos(room.centre().getX() - today.sx / 2, room.centre().getY() - Snapshot.FLOOR,
                    room.centre().getZ() - today.sz / 2)));
        // the hidden place beyond the dream glass, on its own ground, undistorted
        if (room != null && room.place() != null && place != null) {
            BlockPos p = room.place();
            pieces.add(new DreamBlueprint.IslandPiece(p.getX(), p.getZ(), p.getY() - 1, 11.5, 6, plan.seed() ^ 0x51ACEL,
                    Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState(), LucidiumContent.DREAM_CLOUD.get().defaultBlockState()));
            pieces.add(new DreamBlueprint.FragmentPiece(place, new BlockPos(p.getX() - place.sx / 2, p.getY() - Snapshot.FLOOR, p.getZ() - place.sz / 2)));
        }
        return new DreamBlueprint(cell, o, plan, pieces, List.of(), List.of(), start, 0f, signs,
                List.of(LucidiumContent.DREAM_GLASS.get().defaultBlockState()), List.of(), course);
    }

    /** The trial's pieces, gentler with each variant (a failed solve tries the next). */
    public static void lay(CourseKit k, Trial trial, int variant, boolean bell) {
        int g = Math.max(1, 3 - variant);       // a jump's gap
        int run = Math.max(3, 6 - variant);     // a wall-run's span
        if (variant >= PLAIN) {
            k.road(12).ring().road(12);
            if (bell) k.dais();
            else k.door();
            return;
        }
        switch (trial) {
            case FIRST -> {
                // walk, a gap, a step up: the road lays itself as you look along it
                k.layRoad = true;
                k.road(10).gap(Math.min(2, g)).road(6).up().road(6).ring().road(4).gap(Math.min(2, g)).road(5).up().road(3).up().road(8)
                        .ring().road(6).gap(g).road(6);
            }
            case WALL -> {
                k.slideWalls = true;
                k.road(8).ring().road(2).lane(3, -1).wallRun(run - 1, -1).road(4).lane(3, 1).wallRun(run, 1).road(4).ring().road(2)
                        .lane(3, -1).wallRun(run, -1).road(2).lane(2, 1).wallRun(run, 1).road(5).ring().road(2)
                        .lane(3, -1).wallChain(3, 4, -1).road(6);
            }
            case LEDGE -> k.road(8).ring().road(2).hangUp(3, 3).ring().road(4).shimmy(6 - Math.min(1, variant), -1).road(4)
                    .hangUp(3, 2).ring().road(3).shimmy(5, 1).road(3).down(6).road(6);
            case VAULT -> k.road(5).ring().road(2).rail(false).road(4).rail(true).road(3).beam().road(4).ring().road(2)
                    .road(2).rail(false).road(2).beam().road(2).rail(true).road(4).ring().road(2).beam().road(1).beam().road(3)
                    .rail(false).road(4);
            case CLIMB -> {
                // a tower that grows as you come: faces to run up (Wall Stride) and ribbons to climb, the door at the top
                int high = variant == 0 ? 6 : 5, rib = Math.max(6, 10 - 2 * variant);
                k.growFaces = true;
                k.road(8).ring().road(2).climbUp(5, 3).road(3).ring().road(1).ribbon(rib - 2, 3).road(3).climbUp(high, 2).ring()
                        .road(2).hangUp(3, 2).road(2).ribbon(rib, 3).ring().road(3);
            }
            case ROPE -> {
                // gaps no jump crosses, each under a lantern of light: throw, swing, let go at the top
                k.road(8).ring().road(2).swing(22, 14, 6).road(9).ring().road(2).swing(24 - 2 * Math.min(1, variant), 14, 8).road(9).ring()
                        .road(2).swing(22, 16, 8).road(8);
            }
            case LEAP -> {
                // one jump in the air is enough, then not: the dash, a step up, and the long one down
                k.road(8).ring().road(2).leap(6, 0).road(5).leap(8, 0).road(4).ring().road(2).leap(6, 2).road(5)
                        .leap(10 - variant, 0).road(4).ring().road(2).leap(12, -4).road(6);
            }
            case WINGS -> {
                // off the cliff, wings open in the fall, through the rings to an island; then a longer one
                k.road(8).ring().road(3).glide(30, 20).road(CourseKit.ISLAND - 3).ring().road(3).glide(40 - 4 * variant, 24)
                        .road(6);
            }
            default -> k.road(12).ring().road(12);
        }
        if (bell) k.dais();
        else k.door();
    }

    // ---- the room -------------------------------------------------------------------------------------------------------

    /** Your latest home from the Day Journal (the plan names it as its first node), or null. */
    static @Nullable Fragment homeOf(DreamPlan plan, Function<String, Optional<Fragment>> memories) {
        for (DreamPlan.Node n : plan.nodes()) {
            if (!DreamPlan.MEMORY.equals(n.kind()) || n.x() != HOME) continue;
            Optional<Fragment> f = memories.apply(n.ref());
            if (f.isPresent() && f.get().sx == Snapshot.SX && f.get().sz == Snapshot.SZ) return f.get();
        }
        return null;
    }

    /** A plan's memory nodes say what they're for in x: your home (where you arrive), or today's place (what's wrong here). */
    public static final int HOME = 0, TODAY = 1;

    /** What's wrong here's place from today (a memory node marked {@link #TODAY}), a snapshot's size, or null. */
    static @Nullable Fragment todayOf(DreamPlan plan, Function<String, Optional<Fragment>> memories) {
        for (DreamPlan.Node n : plan.nodes()) {
            if (!DreamPlan.MEMORY.equals(n.kind()) || n.x() != TODAY) continue;
            Optional<Fragment> f = memories.apply(n.ref());
            if (f.isPresent() && f.get().sx == Snapshot.SX && f.get().sz == Snapshot.SZ) return f.get();
        }
        return null;
    }

    /** The first node of {@code kind} whose snapshot loads at a snapshot's size (the hidden place), or null. */
    static @Nullable Fragment node(DreamPlan plan, String kind, Function<String, Optional<Fragment>> memories) {
        for (DreamPlan.Node n : plan.nodes()) {
            if (!kind.equals(n.kind())) continue;
            Optional<Fragment> f = memories.apply(n.ref());
            if (f.isPresent() && f.get().sx == Snapshot.SX && f.get().sz == Snapshot.SZ) return f.get();
        }
        return null;
    }

    /** A dream bedroom (no home remembered yet): pale wood and calcite, a bed, a window on the cloud sea, a door +z. */
    static void bedroom(CourseKit k, BlockPos o) {
        int x0 = o.getX(), y0 = o.getY(), z0 = o.getZ();
        BlockState plank = Blocks.PALE_OAK_PLANKS.defaultBlockState(), wallB = Blocks.CALCITE.defaultBlockState();
        BlockState post = Blocks.PALE_OAK_LOG.defaultBlockState(), glass = LucidiumContent.DREAM_GLASS.get().defaultBlockState();
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -4; dz <= 4; dz++) {
                k.block(x0 + dx, y0 - 1, z0 + dz, plank);
                k.block(x0 + dx, y0 + 4, z0 + dz, plank);
                boolean edge = Math.abs(dx) == 4 || Math.abs(dz) == 4;
                for (int dy = 0; dy <= 3; dy++) {
                    if (!edge) continue;
                    boolean corner = Math.abs(dx) == 4 && Math.abs(dz) == 4;
                    boolean window = !corner && Math.abs(dx) == 4 && Math.abs(dz) <= 1 && dy >= 1 && dy <= 2;
                    k.block(x0 + dx, y0 + dy, z0 + dz, corner ? post : window ? glass : wallB);
                }
            }
        // the light: a pearlescent lamp in the ceiling, a rug, the bed you just lay down in
        k.block(x0, y0 + 4, z0, Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState());
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) k.block(x0 + dx, y0, z0 + dz, Blocks.CARPET.pick(DyeColor.PURPLE).defaultBlockState());
        BlockState foot = Blocks.BED.pick(DyeColor.LIGHT_BLUE).defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT);
        k.block(x0 - 2, y0, z0 - 2, foot);
        k.block(x0 - 2, y0, z0 - 3, foot.setValue(BedBlock.PART, BedPart.HEAD));
        // the doorway in the +z wall, and a short pier out to the course
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = 0; dy <= 2; dy++) k.clear(x0 + dx, y0 + dy, z0 + 4);
        for (int dz = 4; dz <= 8; dz++)
            for (int dx = -1; dx <= 1; dx++) k.block(x0 + dx, y0 - 1, z0 + dz, k.road);
    }

    // ---- dream signs ------------------------------------------------------------------------------------------------

    /** One sign beside the course, about half way along: notice it for more time. */
    static void sign(DreamSign sign, Map<Long, BlockState> props, Map<DreamSign, DreamBlueprint.Sign> signs, int x, int y, int z, long seed) {
        BlockState cloud = LucidiumContent.DREAM_CLOUD.get().defaultBlockState();
        int side = (seed & 1) == 0 ? 1 : -1;
        switch (sign) {
            case DOOR_IN_SKY -> {
                BlockPos at = new BlockPos(x + side * 9, y + 7, z);
                for (int dx = -2; dx <= 2; dx++)
                    for (int dz = -2; dz <= 2; dz++) if (Math.abs(dx) + Math.abs(dz) < 4) props.put(at.offset(dx, -1, dz).asLong(), cloud);
                BlueprintCompiler.frame(props, at, side > 0 ? Direction.WEST : Direction.EAST,
                        LucidiumContent.DREAM_GLASS.get().defaultBlockState(), Blocks.PALE_OAK_DOOR);
                signs.put(sign, new DreamBlueprint.Sign(sign, List.of(at.above()), 0, 0));
            }
            case SPINNING_CLOCK -> {
                BlockPos face = new BlockPos(x + side * 8, y + 4, z + 4);
                BlockState quartz = Blocks.QUARTZ_BLOCK.defaultBlockState(), rim = Blocks.GOLD_BLOCK.defaultBlockState();
                for (int u = -3; u <= 3; u++)
                    for (int v = -3; v <= 3; v++) {
                        if (Math.abs(u) == 3 && Math.abs(v) == 3) continue;
                        props.put(face.offset(0, v, u).asLong(), Math.abs(u) == 3 || Math.abs(v) == 3 ? rim : quartz);
                    }
                for (int yy = y - 6; yy < face.getY() - 3; yy++) props.put(new BlockPos(face.getX(), yy, face.getZ()).asLong(), rim);
                signs.put(sign, new DreamBlueprint.Sign(sign, List.of(face), 0, 1));
            }
            case SCRAMBLED_SIGNS -> {
                // a little pearl island each side for the signs to stand on
                List<BlockPos> at = new ArrayList<>();
                for (int s = -1; s <= 1; s += 2) {
                    BlockPos c = new BlockPos(x + s * 5, y, z - 4 + s * 3);
                    for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) props.put(c.offset(dx, -1, dz).asLong(), cloud);
                    at.add(c);
                }
                signs.put(sign, new DreamBlueprint.Sign(sign, at, 0, 0));
            }
            case NO_SHADOW -> {
                BlockPos c = new BlockPos(x - side * 6, y + 1, z + 2);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) props.put(c.offset(dx, -1, dz).asLong(), cloud);
                signs.put(sign, new DreamBlueprint.Sign(sign, List.of(c), 0, 0));
            }
            case RAIN_UP -> signs.put(sign, new DreamBlueprint.Sign(sign, List.of(new BlockPos(x, y, z)), 0, 0));
        }
    }
}
