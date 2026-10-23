package dev.awaken.lucidium.world;

import dev.awaken.lucidium.LucidiumContent;
import dev.awaken.lucidium.plan.DreamPalette;
import dev.awaken.lucidium.plan.DreamPlan;
import dev.awaken.lucidium.plan.DreamSign;
import dev.awaken.lucidium.plan.Fragment;
import dev.awaken.lucidium.plan.Imagined;
import dev.awaken.lucidium.plan.Nightmares;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Turns a {@link DreamPlan} into a {@link DreamBlueprint}: loads each remembered place, distorts it with the dream's
 * palette, floats it on an island and joins the islands with bridges, spiral stairs and doors. Deterministic and
 * free of world access, so it runs on a background thread.
 */
public final class BlueprintCompiler {
    private BlueprintCompiler() {}

    private static final int SPRINKLE = 45;

    public static DreamBlueprint compile(DreamPlan plan, int cell, Function<String, Optional<Fragment>> memories) {
        BlockPos center = Cells.center(cell, 0);
        DreamPalette palette = DreamPalette.of(plan.mood(), plan.hueShift());
        BlockState sprinkle = LucidiumContent.SHIFTING_STONE.get().defaultBlockState();
        BlockState cloud = LucidiumContent.DREAM_CLOUD.get().defaultBlockState();
        BlockState glass = LucidiumContent.DREAM_GLASS.get().defaultBlockState();

        List<DreamBlueprint.Piece> islands = new ArrayList<>();
        List<DreamBlueprint.Piece> fragments = new ArrayList<>();
        Map<Long, BlockState> props = new LinkedHashMap<>();
        List<DreamBlueprint.Node> nodes = new ArrayList<>();
        List<DreamBlueprint.Door> doors = new ArrayList<>();

        // ---- places ----------------------------------------------------------------------------------------------
        for (int i = 0; i < plan.nodes().size(); i++) {
            DreamPlan.Node n = plan.nodes().get(i);
            long seed = plan.seed() + i * 31L;
            Fragment f = fragmentFor(n, seed, memories);
            if (n.rot() != 0 && !DreamPlan.DOOR.equals(n.kind())) f = f.rotated(n.rot());
            if (n.flip()) f = f.flipped();
            if (!DreamPlan.VISION.equals(n.kind())) f = f.remapped(palette, seed, sprinkle, SPRINKLE);
            BlockPos feet = new BlockPos(center.getX() + n.x(), n.y(), center.getZ() + n.z());
            int minY = n.flip() ? feet.getY() - 1 - (f.sy - 1) : feet.getY() - 4;
            BlockPos min = new BlockPos(feet.getX() - f.sx / 2, minY, feet.getZ() - f.sz / 2);

            BlockState top = palette.apply(Blocks.GRASS_BLOCK.defaultBlockState());
            BlockState body = palette.apply(Blocks.DIRT.defaultBlockState());
            double radius = 12.0 + (Math.floorMod(seed, 5) * 0.5);
            int depth = 7 + (int) Math.floorMod(seed >> 3, 4);
            if (n.flip()) depth = 2;
            if (DreamPlan.NIGHTMARE.equals(n.kind()) && Nightmares.Family.FALLING.id.equals(n.ref())) {
                radius = 3.5;
                depth = 3;
            }
            if (DreamPlan.DOOR.equals(n.kind())) radius = 6.5;
            if (DreamPlan.VISION.equals(n.kind())) {
                top = Blocks.GRASS_BLOCK.defaultBlockState();
                body = Blocks.DIRT.defaultBlockState();
            }
            islands.add(new DreamBlueprint.IslandPiece(feet.getX(), feet.getZ(), feet.getY() - 1, radius, depth, seed, top, body, cloud));
            fragments.add(new DreamBlueprint.FragmentPiece(f, min));
            nodes.add(new DreamBlueprint.Node(i, n.kind(), n.ref(), n.label(), feet, min, f));

            if (DreamPlan.VISION.equals(n.kind())) visionFrame(props, feet, glass);
            if (DreamPlan.DOOR.equals(n.kind())) {
                // the lonely door is fragment-local (x 7..8, z 8); its opening sends you home, wide awake inside
                BlockPos o = new BlockPos(min.getX() + 7, feet.getY(), min.getZ() + 8);
                AABB trigger = new AABB(o.getX() + 0.1, o.getY(), o.getZ() + 0.2, o.getX() + 1.9, o.getY() + 2, o.getZ() + 0.8);
                doors.add(new DreamBlueprint.Door(trigger, Vec3.ZERO, 0, true));
            }
        }

        // ---- links ------------------------------------------------------------------------------------------------
        for (DreamPlan.Edge e : plan.edges()) {
            DreamBlueprint.Node a = nodes.get(e.a()), b = nodes.get(e.b());
            Materials m = materials(palette, e.style(), plan.hueShift());
            switch (e.kind()) {
                case DreamPlan.STAIR -> stair(props, a.feet(), b.feet(), m, glass);
                case DreamPlan.PORTAL -> portal(props, doors, a.feet(), b.feet(), m, palette);
                default -> bridge(props, a.feet(), b.feet(), m, e.style(), glass, 9.5, 9.5);
            }
        }

        // ---- dream signs ------------------------------------------------------------------------------------------
        Map<DreamSign, DreamBlueprint.Sign> signs = new EnumMap<>(DreamSign.class);
        DreamBlueprint.Node start = nodes.getFirst();
        for (String name : plan.signs()) {
            DreamSign sign = DreamSign.byName(name).orElse(null);
            if (sign == null) continue;
            switch (sign) {
                case DOOR_IN_SKY -> {
                    BlockPos o = start.feet().offset(6, 13, 4);
                    for (int dx = -2; dx <= 2; dx++)
                        for (int dz = -2; dz <= 2; dz++) if (Math.abs(dx) + Math.abs(dz) < 4) props.put(o.offset(dx, -1, dz).asLong(), cloud);
                    frame(props, o, Direction.SOUTH, glass, Blocks.DARK_OAK_DOOR);
                    signs.put(sign, new DreamBlueprint.Sign(sign, List.of(o.above()), 0, 0));
                }
                case SPINNING_CLOCK -> {
                    double ang = Math.toRadians(200 + Math.floorMod(plan.seed(), 90));
                    int ox = (int) Math.round(Math.cos(ang) * 9.5), oz = (int) Math.round(Math.sin(ang) * 9.5);
                    BlockPos face = start.feet().offset(ox, 3, oz);
                    // face plane runs along the tangent; the clock looks back at the island centre
                    boolean alongX = Math.abs(oz) >= Math.abs(ox);
                    int ax = alongX ? 1 : 0, az = alongX ? 0 : 1;
                    BlockState quartz = Blocks.QUARTZ_BLOCK.defaultBlockState();
                    BlockState rim = Blocks.GOLD_BLOCK.defaultBlockState();
                    for (int u = -3; u <= 3; u++)
                        for (int v = -3; v <= 3; v++) {
                            boolean edge = Math.abs(u) == 3 || Math.abs(v) == 3;
                            if (Math.abs(u) == 3 && Math.abs(v) == 3) continue;
                            props.put(face.offset(u * ax, v, u * az).asLong(), edge ? rim : quartz);
                        }
                    for (int y = start.feet().getY() - 1; y < face.getY() - 3; y++) props.put(new BlockPos(face.getX(), y, face.getZ()).asLong(), rim);
                    signs.put(sign, new DreamBlueprint.Sign(sign, List.of(face), ax, az));
                }
                case SCRAMBLED_SIGNS -> {
                    List<BlockPos> at = new ArrayList<>();
                    for (int k = 0; k < Math.min(3, nodes.size()); k++) {
                        DreamBlueprint.Node nd = nodes.get(k);
                        if (!nd.kind().equals(DreamPlan.MEMORY) && !nd.kind().equals(DreamPlan.IMAGINED)) continue;
                        at.add(nd.feet().offset(k % 2 == 0 ? 9 : -9, 0, k % 2 == 0 ? 2 : -3));
                    }
                    signs.put(sign, new DreamBlueprint.Sign(sign, at, 0, 0));
                }
                case NO_SHADOW -> {
                    DreamBlueprint.Node nd = nodes.size() > 1 ? nodes.get(1) : start;
                    signs.put(sign, new DreamBlueprint.Sign(sign, List.of(nd.feet().offset(-9, 0, 6)), 0, 0));
                }
                case RAIN_UP -> signs.put(sign, new DreamBlueprint.Sign(sign, List.of(start.feet()), 0, 0));
            }
        }

        // ---- arrival, materials for manifesting, spares -----------------------------------------------------------
        List<DreamBlueprint.Piece> all = new ArrayList<>(islands);
        all.addAll(fragments);
        all.add(new DreamBlueprint.BlockListPiece(props));
        DreamBlueprint probe = new DreamBlueprint(cell, center, plan, all, nodes, doors, Vec3.ZERO, 0, signs, List.of(), List.of());
        Vec3 spawn = findStanding(probe, start.feet());
        if (spawn == null) {
            BlockPos s = start.feet();
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) props.put(s.offset(dx, -1, dz).asLong(), cloud);
            for (int dy = 0; dy <= 1; dy++) props.put(s.above(dy).asLong(), Blocks.AIR.defaultBlockState());
            all.set(all.size() - 1, new DreamBlueprint.BlockListPiece(props));
            spawn = Vec3.atBottomCenterOf(s);
        }
        // door exits that were left for later (the door of intention leads back to the arrival point)
        List<DreamBlueprint.Door> finalDoors = new ArrayList<>();
        for (DreamBlueprint.Door d : doors) finalDoors.add(d.special() ? new DreamBlueprint.Door(d.trigger(), spawn, 0, true) : d);

        List<Fragment> spares = new ArrayList<>();
        for (int k = 0; k < plan.spares().size(); k++) {
            String ref = plan.spares().get(k);
            long seed = plan.seed() ^ (0x5EED5L * (k + 1));
            Fragment f = ref.startsWith("imagined:") ? Imagined.place(ref.substring(9), seed)
                    : memories.apply(ref).orElseGet(() -> Imagined.place(Imagined.PLACES.get((int) Math.floorMod(seed, Imagined.PLACES.size())), seed));
            spares.add(f.rotated((int) Math.floorMod(seed, 4)).remapped(palette, seed, sprinkle, SPRINKLE));
        }
        return new DreamBlueprint(cell, center, plan, all, nodes, finalDoors, spawn, yawTowards(spawn, start.feet()), signs,
                manifestPalette(nodes, palette), spares);
    }

    private static Fragment fragmentFor(DreamPlan.Node n, long seed, Function<String, Optional<Fragment>> memories) {
        return switch (n.kind()) {
            case DreamPlan.MEMORY, DreamPlan.VISION -> memories.apply(n.ref())
                    .orElseGet(() -> Imagined.place(Imagined.PLACES.get((int) Math.floorMod(seed, Imagined.PLACES.size())), seed));
            case DreamPlan.NIGHTMARE -> Imagined.nightmare(Nightmares.Family.byId(n.ref()).orElse(Nightmares.Family.GENERIC), seed);
            case DreamPlan.MENTOR -> Imagined.mentorRing(seed);
            case DreamPlan.DOOR -> Imagined.lonelyDoor(seed);
            default -> Imagined.place(n.ref(), seed);
        };
    }

    // ---- materials -------------------------------------------------------------------------------------------------

    record Materials(BlockState deck, BlockState alt, BlockState slab) {}

    static Materials materials(DreamPalette p, int style, int hue) {
        return switch (Math.floorMod(style, 4)) {
            case 1 -> new Materials(p.apply(Blocks.OAK_PLANKS.defaultBlockState()), p.apply(Blocks.STRIPPED_OAK_LOG.defaultBlockState()),
                    p.apply(Blocks.OAK_SLAB.defaultBlockState()));
            case 2 -> new Materials(LucidiumContent.DREAM_CLOUD.get().defaultBlockState(), LucidiumContent.DREAM_CLOUD.get().defaultBlockState(), null);
            case 3 -> {
                DyeColor a = DyeColor.values()[Math.floorMod(hue, 16)], b = DyeColor.values()[Math.floorMod(hue + 5, 16)];
                yield new Materials(Blocks.CONCRETE.pick(a).defaultBlockState(), Blocks.CONCRETE.pick(b).defaultBlockState(),
                        Blocks.CONCRETE_SLAB.pick(a).defaultBlockState());
            }
            default -> new Materials(p.apply(Blocks.STONE_BRICKS.defaultBlockState()), p.apply(Blocks.CHISELED_STONE_BRICKS.defaultBlockState()),
                    p.apply(Blocks.STONE_BRICK_SLAB.defaultBlockState()));
        };
    }

    // ---- bridges ---------------------------------------------------------------------------------------------------

    /** A 3-wide walkway from rim to rim. Half steps use slabs so it can be walked without jumping. */
    static void bridge(Map<Long, BlockState> out, BlockPos a, BlockPos b, Materials m, int style, BlockState post, double rimA, double rimB) {
        double dx = b.getX() - a.getX(), dz = b.getZ() - a.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1) return;
        double ux = dx / len, uz = dz / len, px = -uz, pz = ux;
        double sx = a.getX() + 0.5 + ux * rimA, sz = a.getZ() + 0.5 + uz * rimA;
        double span = Math.max(1, len - rimA - rimB);
        double ya = a.getY() - 1, yb = b.getY() - 1;
        boolean stepping = Math.floorMod(style, 4) == 2;
        double arc = Math.floorMod(style, 4) == 1 ? Math.min(5, span / 6) : 0;
        int steps = (int) Math.ceil(span);
        for (int k = -1; k <= steps + 1; k++) {
            double t = Mth.clamp(k / span, 0, 1);
            double h = Mth.lerp(t, ya, yb) + arc * Math.sin(Math.PI * t);
            if (stepping && k > 1 && k < steps - 1 && Math.floorMod(k, 3) == 2) continue;
            double cx = sx + ux * k, cz = sz + uz * k;
            int y = Mth.floor(h);
            boolean half = m.slab() != null && h - y >= 0.5;
            for (int w = -1; w <= 1; w++) {
                int x = Mth.floor(cx + px * w), z = Mth.floor(cz + pz * w);
                BlockState deck = (Math.floorMod(k, 6) < 3 || w == 0) ? m.deck() : m.alt();
                out.put(BlockPos.asLong(x, y, z), deck);
                if (half) out.put(BlockPos.asLong(x, y + 1, z), m.slab());
            }
            if (!stepping && Math.floorMod(k, 5) == 0 && k > 0 && k < steps) {
                for (int side = -1; side <= 1; side += 2) {
                    int x = Mth.floor(cx + px * 2 * side), z = Mth.floor(cz + pz * 2 * side);
                    out.put(BlockPos.asLong(x, y + 1, z), post);
                    if (Math.floorMod(k, 10) == 0) out.put(BlockPos.asLong(x, y + 2, z), Blocks.END_ROD.defaultBlockState());
                }
            }
        }
    }

    /** A spiral stair hanging in the air between two islands at very different heights. */
    static void stair(Map<Long, BlockState> out, BlockPos a, BlockPos b, Materials m, BlockState core) {
        BlockPos low = a.getY() <= b.getY() ? a : b, high = low == a ? b : a;
        double mx = (a.getX() + b.getX()) / 2.0, mz = (a.getZ() + b.getZ()) / 2.0;
        double r = 4.5;
        double startAng = Math.atan2(low.getZ() - mz, low.getX() - mx);
        BlockPos entry = BlockPos.containing(mx + Math.cos(startAng) * r, low.getY(), mz + Math.sin(startAng) * r);
        bridge(out, low, entry, m, 0, core, 9.5, 0);
        int rise = high.getY() - low.getY();
        int n = Math.max(1, rise * 2);
        double dAng = 1.15 / r;
        double ang = startAng;
        for (int k = 0; k <= n; k++) {
            double h = low.getY() - 1 + k * 0.5;
            int y = Mth.floor(h);
            boolean half = m.slab() != null && h - y >= 0.5;
            for (double rr = r - 1; rr <= r + 0.01; rr += 1) {
                int x = Mth.floor(mx + Math.cos(ang) * rr), z = Mth.floor(mz + Math.sin(ang) * rr);
                out.put(BlockPos.asLong(x, y, z), m.deck());
                if (half) out.put(BlockPos.asLong(x, y + 1, z), m.slab());
            }
            ang += dAng;
        }
        for (int y = low.getY() - 3; y <= high.getY() + 1; y++) out.put(BlockPos.asLong(Mth.floor(mx), y, Mth.floor(mz)), core);
        BlockPos exit = BlockPos.containing(mx + Math.cos(ang) * r, high.getY(), mz + Math.sin(ang) * r);
        // short landing from the top of the spiral to the high island
        bridge(out, exit, high, m, 0, core, 0, 9.5);
    }

    /** An impossible door: walk through a frame on one island and come out of its twin on another. */
    static void portal(Map<Long, BlockState> out, List<DreamBlueprint.Door> doors, BlockPos a, BlockPos b, Materials m, DreamPalette p) {
        double toB = Math.atan2(b.getZ() - a.getZ(), b.getX() - a.getX());
        double toA = Math.atan2(a.getZ() - b.getZ(), a.getX() - b.getX());
        double angA = toB + Math.PI / 2, angB = toA - Math.PI / 2;
        BlockPos oa = BlockPos.containing(a.getX() + 0.5 + Math.cos(angA) * 9.5, a.getY(), a.getZ() + 0.5 + Math.sin(angA) * 9.5);
        BlockPos ob = BlockPos.containing(b.getX() + 0.5 + Math.cos(angB) * 9.5, b.getY(), b.getZ() + 0.5 + Math.sin(angB) * 9.5);
        Direction da = Direction.fromYRot(Math.toDegrees(angA) - 90), db = Direction.fromYRot(Math.toDegrees(angB) - 90);
        BlockState shifting = LucidiumContent.SHIFTING_STONE.get().defaultBlockState();
        frame(out, oa, da, shifting, Blocks.SPRUCE_DOOR);
        frame(out, ob, db, shifting, Blocks.SPRUCE_DOOR);
        for (BlockPos o : List.of(oa, ob)) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) out.put(o.offset(dx, -1, dz).asLong(), m.deck());
        doors.add(new DreamBlueprint.Door(cellBox(oa), exitFor(ob, b), yawTowards(exitFor(ob, b), b), false));
        doors.add(new DreamBlueprint.Door(cellBox(ob), exitFor(oa, a), yawTowards(exitFor(oa, a), a), false));
    }

    private static AABB cellBox(BlockPos o) {
        return new AABB(o.getX() + 0.15, o.getY(), o.getZ() + 0.15, o.getX() + 0.85, o.getY() + 1.8, o.getZ() + 0.85);
    }

    private static Vec3 exitFor(BlockPos frame, BlockPos islandCentre) {
        double dx = islandCentre.getX() - frame.getX(), dz = islandCentre.getZ() - frame.getZ();
        double len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
        return new Vec3(frame.getX() + 0.5 + dx / len * 2.2, frame.getY(), frame.getZ() + 0.5 + dz / len * 2.2);
    }

    /** A door frame: posts either side, a lintel, and an open door in the middle (opening at {@code o}). */
    static void frame(Map<Long, BlockState> out, BlockPos o, Direction facing, BlockState frame, Block door) {
        Direction side = facing.getClockWise();
        for (int y = 0; y <= 3; y++) {
            out.put(o.relative(side).above(y).asLong(), frame);
            out.put(o.relative(side.getOpposite()).above(y).asLong(), frame);
        }
        out.put(o.above(3).asLong(), frame);
        BlockState lower = door.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.OPEN, true)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        out.put(o.asLong(), lower);
        out.put(o.above().asLong(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static void visionFrame(Map<Long, BlockState> out, BlockPos feet, BlockState glass) {
        for (int k = 0; k < 12; k++) {
            double a = k * Math.PI / 6;
            int x = feet.getX() + (int) Math.round(Math.cos(a) * 11), z = feet.getZ() + (int) Math.round(Math.sin(a) * 11);
            for (int y = feet.getY(); y <= feet.getY() + 3; y++) out.put(BlockPos.asLong(x, y, z), glass);
            out.put(BlockPos.asLong(x, feet.getY() + 4, z), Blocks.END_ROD.defaultBlockState());
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    /** A spot near {@code feet} with solid ground and two free blocks above it. */
    static Vec3 findStanding(DreamBlueprint bp, BlockPos feet) {
        for (int r = 0; r <= 7; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy = 4; dy >= -3; dy--) {
                        int x = feet.getX() + dx, y = feet.getY() + dy, z = feet.getZ() + dz;
                        BlockState below = bp.blockAt(x, y - 1, z);
                        if (below == null || below.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) continue;
                        if (!free(bp.blockAt(x, y, z)) || !free(bp.blockAt(x, y + 1, z))) continue;
                        if (!below.getFluidState().isEmpty()) continue;
                        return new Vec3(x + 0.5, y, z + 0.5);
                    }
                }
            }
        }
        return null;
    }

    private static boolean free(BlockState s) {
        return s == null || (s.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty() && s.getFluidState().isEmpty());
    }

    static float yawTowards(Vec3 from, BlockPos to) {
        double dx = to.getX() + 0.5 - from.x, dz = to.getZ() + 0.5 - from.z;
        if (dx * dx + dz * dz < 0.01) return 0;
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** The three commonest solid blocks of the remembered places, for doors and bridges shaped by intent. */
    static List<BlockState> manifestPalette(List<DreamBlueprint.Node> nodes, DreamPalette palette) {
        Map<BlockState, Integer> counts = new java.util.HashMap<>();
        for (DreamBlueprint.Node n : nodes) {
            if (!DreamPlan.MEMORY.equals(n.kind())) continue;
            n.fragment().histogram().forEach((s, c) -> {
                if (s.isSolidRender() && !s.hasBlockEntity() && s.getFluidState().isEmpty()) counts.merge(s, c, Integer::sum);
            });
        }
        List<BlockState> out = new ArrayList<>(counts.entrySet().stream()
                .sorted((x, y) -> Integer.compare(y.getValue(), x.getValue())).limit(3).map(Map.Entry::getKey).toList());
        if (out.isEmpty()) out.add(palette.apply(Blocks.STONE_BRICKS.defaultBlockState()));
        out.add(LucidiumContent.DREAM_GLASS.get().defaultBlockState());
        return out;
    }
}
