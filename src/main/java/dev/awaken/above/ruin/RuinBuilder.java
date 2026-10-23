package dev.awaken.above.ruin;

import dev.awaken.Awaken;
import dev.awaken.above.AboveBlocks;
import dev.awaken.above.MachineType;
import dev.awaken.above.block.BrokenMachineBlock;
import dev.awaken.above.block.DoorCoreBlock;
import dev.awaken.above.block.LogTerminalBlock;
import dev.awaken.above.logs.PrecursorLogs;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The blueprints of every Precursor ruin, in local coordinates (0,0,0 = the piece's lower corner, before rotation).
 * Deterministic from the piece seed, so each chunk the ruin touches rebuilds exactly the same blocks and keeps only its
 * own; no choice ever reads the terrain. The Halo station reuses the machine and terminal helpers.
 *
 * <p>Art direction (docs/modules/structures.md): slate-blue plating with brass and signal-blue light lines, amber at
 * doors and warnings; one tall element that reads from far away; walls with a plinth, pilasters, recessed panels and a
 * cornice; weathering in patches (cracked and mossy plating, moss, vines, sand, coral), more at the base and the top.
 */
public final class RuinBuilder {
    private RuinBuilder() {}

    /** Where the blocks go. The world sink clips to one chunk and rotates; tests record. */
    public interface Sink {
        void set(int x, int y, int z, BlockState state);

        /** Fills the column under (x,y,z) downward with {@code state} until solid ground (at most {@code depth}). */
        void fillDown(int x, int y, int z, BlockState state, int depth);

        void chest(int x, int y, int z, Direction facing, ResourceKey<LootTable> table);
    }

    public record Size(int x, int y, int z) {}

    /** Footprints are square (the piece turns inside them). */
    public static Size size(RuinKind kind) {
        return switch (kind) {
            case VAULT, SPAWN_VAULT -> new Size(23, 18, 23);
            case SKY -> new Size(49, 26, 49);
            case OCEAN -> new Size(31, 22, 31);
            case MONOLITH -> new Size(17, 46, 17);
        };
    }

    public static ResourceKey<LootTable> loot(String name) {
        return ResourceKey.create(Registries.LOOT_TABLE, Awaken.id("chests/" + name));
    }

    // ---- shared pieces ----------------------------------------------------------------------------------------------

    static BlockState plating() { return AboveBlocks.PLATING.get().defaultBlockState(); }
    static BlockState panel() { return AboveBlocks.PANEL.get().defaultBlockState(); }
    static BlockState trim() { return AboveBlocks.TRIM.get().defaultBlockState(); }
    static BlockState glow() { return AboveBlocks.GLOW_PANEL.get().defaultBlockState(); }
    static BlockState warn() { return AboveBlocks.WARNING_LIGHT.get().defaultBlockState(); }
    static BlockState glass() { return AboveBlocks.VOID_GLASS.get().defaultBlockState(); }
    static BlockState air() { return Blocks.AIR.defaultBlockState(); }
    static BlockState pillar() { return RuinBlocks.PILLAR.get().defaultBlockState(); }
    static BlockState pillar(Direction.Axis axis) { return RuinBlocks.PILLAR.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis); }
    static BlockState glyph() { return RuinBlocks.GLYPH.get().defaultBlockState(); }
    static BlockState cracked() { return RuinBlocks.CRACKED.get().defaultBlockState(); }
    static BlockState mossy() { return RuinBlocks.MOSSY.get().defaultBlockState(); }
    static BlockState of(net.minecraft.world.level.block.Block b) { return b.defaultBlockState(); }

    public static BlockState broken(MachineType t, Direction facing) {
        return AboveBlocks.BROKEN_MACHINE.get().defaultBlockState().setValue(BrokenMachineBlock.MACHINE, t)
                .setValue(BrokenMachineBlock.FACING, facing);
    }

    public static BlockState terminal(PrecursorLogs.Pool pool, Direction facing) {
        return AboveBlocks.LOG_TERMINAL.get().defaultBlockState().setValue(LogTerminalBlock.POOL, pool).setValue(LogTerminalBlock.FACING, facing);
    }

    /** 1–3 machine types, weighted by {@link MachineType#scanWeight}, no repeats. */
    public static List<MachineType> pickMachines(RandomSource r, int count, Set<MachineType> exclude) {
        List<MachineType> out = new ArrayList<>();
        EnumSet<MachineType> used = EnumSet.noneOf(MachineType.class);
        used.addAll(exclude);
        for (int n = 0; n < count; n++) {
            int total = 0;
            for (MachineType t : MachineType.values()) if (!used.contains(t)) total += t.scanWeight;
            if (total <= 0) break;
            int roll = r.nextInt(total);
            for (MachineType t : MachineType.values()) {
                if (used.contains(t)) continue;
                roll -= t.scanWeight;
                if (roll < 0) {
                    out.add(t);
                    used.add(t);
                    break;
                }
            }
        }
        return out;
    }

    /** Machine slot positions for {@code n} machines along a row: centre, two ends, or all three. */
    private static int[] row(int n, int left, int mid, int right) {
        return switch (n) {
            case 1 -> new int[]{mid};
            case 2 -> new int[]{left, right};
            default -> new int[]{left, mid, right};
        };
    }

    /** Placement helpers bound to one ruin's sink and seed: boxes, weathered plating, vines, noise. */
    private static final class Brush {
        final Sink out;
        final long seed;

        Brush(Sink out, long seed) {
            this.out = out;
            this.seed = seed;
        }

        void set(int x, int y, int z, BlockState s) {
            out.set(x, y, z, s);
        }

        void box(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
            for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) out.set(x, y, z, s);
        }

        /** A stable 0..1 value per cell. */
        double n(int x, int y, int z) {
            long h = seed * 0x9E3779B97F4A7C15L + x * 0x632BE59BD9B4E019L + y * 0x9E3779B1L + z * 0x85157AF5L;
            h ^= h >>> 29;
            h *= 0xBF58476D1CE4E5B9L;
            h ^= h >>> 32;
            return (h & 0xFFFFFFL) / (double) 0x1000000L;
        }

        /** Smooth noise with cells of {@code cell} blocks: patches, not speckle. */
        double sn(int x, int y, int z, double cell) {
            double gx = x / cell, gy = y / cell, gz = z / cell;
            int x0 = (int) Math.floor(gx), y0 = (int) Math.floor(gy), z0 = (int) Math.floor(gz);
            double fx = s(gx - x0), fy = s(gy - y0), fz = s(gz - z0);
            double v = 0;
            for (int i = 0; i < 8; i++) {
                int dx = i & 1, dy = (i >> 1) & 1, dz = (i >> 2) & 1;
                double w = (dx == 1 ? fx : 1 - fx) * (dy == 1 ? fy : 1 - fy) * (dz == 1 ? fz : 1 - fz);
                v += w * n(x0 + dx + 7919, y0 + dy, z0 + dz);
            }
            return v;
        }

        private static double s(double t) {
            return t * t * (3 - 2 * t);
        }

        /** Plating as time left it: patches of moss and cracks, more where {@code wear} is high (0..1). */
        BlockState plate(int x, int y, int z, double wear) {
            double p = sn(x, y, z, 3.0), k = n(x, y, z);
            if (p < 0.12 + 0.28 * wear) return mossy();
            if (k < 0.05 + 0.16 * wear) return cracked();
            return plating();
        }

        /** Vine hanging on the face of the block at (x, y, z) + {@code towardWall}. */
        void vine(int x, int y, int z, Direction towardWall) {
            out.set(x, y, z, Blocks.VINE.defaultBlockState().setValue(VineBlock.PROPERTY_BY_DIRECTION.get(towardWall), true));
        }

        /** A vine curtain hanging from (x, top, z) down a few blocks. */
        void curtain(int x, int top, int z, Direction towardWall, int max) {
            int len = 1 + (int) (n(x, top, z) * max);
            for (int i = 0; i < len; i++) vine(x, top - i, z, towardWall);
        }
    }

    // ---- build ------------------------------------------------------------------------------------------------------

    public static void build(RuinKind kind, long seed, int doorRank, Sink out) {
        RandomSource r = new XoroshiroRandomSource(seed);
        Brush b = new Brush(out, seed);
        switch (kind) {
            case VAULT, SPAWN_VAULT -> vault(kind, r, doorRank, b);
            case SKY -> sky(r, b);
            case OCEAN -> ocean(r, b);
            case MONOLITH -> monolith(r, b);
        }
    }

    private static List<MachineType> machinesFor(RuinKind kind, RandomSource r) {
        if (kind == RuinKind.SPAWN_VAULT) {
            List<MachineType> m = new ArrayList<>();
            m.add(MachineType.GATE_SCANNER);
            m.addAll(pickMachines(r, 1, EnumSet.of(MachineType.GATE_SCANNER, MachineType.LAUNCH_RING)));
            return m;
        }
        int n = 1 + r.nextInt(3);
        return pickMachines(r, n, EnumSet.noneOf(MachineType.class));
    }

    private static boolean hasTerminal(RuinKind kind, RandomSource r) {
        return switch (kind) {
            case SPAWN_VAULT -> true;
            case MONOLITH -> r.nextFloat() < 0.7f;
            case SKY -> r.nextFloat() < 0.6f;
            default -> r.nextFloat() < 0.5f;
        };
    }

    /**
     * Landmarks (world module): every ruin keeps something worth taking for an E-rank finder outside its sealed parts.
     * Fixed spots, no random draws, so ruins already half-generated keep matching their other chunks.
     */
    private static void outerCache(Brush b, int x, int y, int z, Direction facing) {
        b.out.chest(x, y, z, facing, loot("landmark_outer"));
    }

    // ---- the Sealed Vault ----------------------------------------------------------------------------------------------

    /**
     * A monumental bunker half sunk in the earth: a forecourt between two tall pylons with amber crowns, a stepped mass
     * (pilasters, recessed glyph panels, an overhanging brass cornice) with a deep double-reveal portal, an antechamber
     * whose ceiling has fallen in at one corner, and behind a ring of light the sealed rank door; past it the machines,
     * the loot and a terminal, a glowing line on the floor leading to it. A signal beacon stands on the crown. Door faces
     * -Z; the outer cache waits in the forecourt (z &lt; 4).
     */
    private static void vault(RuinKind kind, RandomSource r, int doorRank, Brush b) {
        List<MachineType> machines = machinesFor(kind, r);
        boolean terminal = hasTerminal(kind, r);
        boolean secondChest = r.nextBoolean();
        final int X0 = 1, X1 = 21, Z0 = 4, Z1 = 21, ROOF = 9;
        // foundations and floors
        for (int x = 0; x <= 22; x++) {
            for (int z = 0; z <= 22; z++) {
                b.out.fillDown(x, -1, z, x >= X0 && x <= X1 && z >= Z0 ? plating() : of(Blocks.DEEPSLATE_TILES), 14);
                if (z < Z0) {
                    // the forecourt: deepslate paving, brass inlay along the walk, worn in patches
                    double p = b.sn(x, 0, z, 2.5);
                    BlockState f = (x == 9 || x == 13) ? trim() : p < 0.2 ? of(Blocks.MOSS_BLOCK) : b.n(x, 0, z) < 0.15
                            ? of(Blocks.CRACKED_DEEPSLATE_TILES) : of(Blocks.DEEPSLATE_TILES);
                    b.set(x, 0, z, f);
                    for (int y = 1; y <= 4; y++) b.set(x, y, z, air());
                } else if (x >= X0 && x <= X1 && z <= Z1) {
                    b.set(x, 0, z, (x + z) % 4 == 0 ? panel() : plating());
                } else {
                    b.set(x, 0, z, of(Blocks.DEEPSLATE_TILES));
                }
            }
        }
        // tier one: plinth, pilasters, recessed panels, cornice
        for (int x = X0; x <= X1; x++) {
            for (int z = Z0; z <= Z1; z++) {
                boolean outer = x == X0 || x == X1 || z == Z0 || z == Z1;
                boolean infill = !outer && (x == X0 + 1 || x == X1 - 1 || z == Z0 + 1 || z == Z1 - 1);
                for (int y = 1; y <= ROOF - 1; y++) {
                    if (outer) {
                        boolean pil = isPilaster(x, z, X0, X1, Z0, Z1);
                        BlockState s = y == 1 ? of(Blocks.POLISHED_DEEPSLATE) : pil ? pillar() : air();
                        b.set(x, y, z, s);
                    } else if (infill) {
                        double wear = y <= 2 ? 0.8 : y >= 7 ? 0.6 : 0.25;
                        boolean glyphRow = y == 5 && Math.floorMod(x + z, 4) == 2;
                        b.set(x, y, z, y == 1 ? of(Blocks.POLISHED_DEEPSLATE) : glyphRow ? glyph() : b.plate(x, y, z, wear));
                    } else {
                        b.set(x, y, z, air());
                    }
                }
                b.set(x, ROOF, z, outer ? trim() : b.plate(x, ROOF, z, 0.5));
            }
        }
        // the cornice overhangs the walls by a block
        for (int x = X0 - 1; x <= X1 + 1; x++) {
            b.set(x, ROOF, Z0 - 1, trim());
            b.set(x, ROOF, Z1 + 1, trim());
        }
        for (int z = Z0 - 1; z <= Z1 + 1; z++) {
            b.set(X0 - 1, ROOF, z, trim());
            b.set(X1 + 1, ROOF, z, trim());
        }
        // the portal: a deep double reveal framed in brass, a glowing emblem above
        for (int y = 1; y <= 7; y++) {
            for (int x = 8; x <= 14; x++) {
                boolean outerOpen = x >= 9 && x <= 13 && y <= 6;
                boolean innerOpen = x >= 10 && x <= 12 && y <= 5;
                b.set(x, y, Z0, outerOpen ? air() : (x == 8 || x == 14 || y == 7) ? trim() : b.plate(x, y, Z0, 0.3));
                b.set(x, y, Z0 + 1, innerOpen ? air() : (x == 9 || x == 13 || y == 6) && x >= 9 && x <= 13 ? trim() : b.plate(x, y, Z0 + 1, 0.3));
            }
        }
        b.set(11, 8, Z0, glow());
        b.set(10, 8, Z0, trim());
        b.set(12, 8, Z0, trim());
        b.set(8, 8, Z0, warn());
        b.set(14, 8, Z0, warn());
        // the antechamber (z 6..11) and the sealed chamber (z 13..19) behind the door wall at z 12
        final int WALL = 12;
        for (int x = 2; x <= 20; x++) {
            for (int y = 1; y <= 8; y++) {
                boolean door = x >= 10 && x <= 12 && y <= 3;
                double ring = Math.abs(Math.sqrt((x - 11) * (x - 11) + (y - 2.5) * (y - 2.5)) - 3.6);
                BlockState s;
                if (door) s = x == 11 && y == 2
                        ? AboveBlocks.DOOR_CORE.get().defaultBlockState().setValue(DoorCoreBlock.RANK, Math.max(0, Math.min(6, doorRank)))
                        : AboveBlocks.DOOR.get().defaultBlockState();
                else if (ring < 0.5) s = (x + y) % 2 == 0 ? glow() : trim();
                else if (y == 8) s = trim();
                else s = b.plate(x, y, WALL, 0.2);
                b.set(x, y, WALL, s);
            }
        }
        // floor line from the portal to the door, ceiling lights, cobwebs in the corners
        for (int z = 6; z <= 11; z++) b.set(11, 0, z, trim());
        for (int z = 7; z <= 10; z++) {
            b.set(5, ROOF, z, glow());
            if (z < 9) b.set(17, ROOF, z, glow());
        }
        b.set(2, 8, 6, of(Blocks.COBWEB));
        b.set(20, 8, 11, of(Blocks.COBWEB));
        b.set(2, 8, 11, of(Blocks.COBWEB));
        b.set(5, 6, WALL, warn());
        b.set(17, 6, WALL, warn());
        // the ceiling fell in over the antechamber's east corner: a hole to the sky, rubble below, vines down it
        for (int x = 15; x <= 18; x++) {
            for (int z = 7; z <= 9; z++) {
                if ((x == 15 || x == 18) && (z == 7 || z == 9) && b.n(x, 9, z) < 0.5) continue;
                b.set(x, ROOF, z, air());
            }
        }
        b.set(16, 1, 8, cracked());
        b.set(17, 1, 8, of(Blocks.COBBLED_DEEPSLATE));
        b.set(17, 2, 8, cracked());
        b.set(15, 1, 9, of(Blocks.MOSS_BLOCK));
        b.set(18, 1, 7, mossy());
        b.set(16, 1, 7, of(Blocks.MOSS_CARPET));
        for (int x = 14; x <= 19; x++) {
            for (int z = 6; z <= 10; z++) {
                boolean rimOfHole = (x == 14 || x == 19 || z == 6 || z == 10) && b.n(x, 8, z) < 0.45;
                if (rimOfHole) b.set(x, 8, z, of(Blocks.HANGING_ROOTS));
            }
        }
        // the sealed chamber: machines on brass daises along the back, a glowing line to the terminal
        int[] xs = row(machines.size(), 6, 11, 16);
        for (int i = 0; i < xs.length && i < machines.size(); i++) {
            b.set(xs[i], 0, 18, trim());
            b.set(xs[i], 1, 18, broken(machines.get(i), Direction.NORTH));
            b.set(xs[i], 3, 20, glow());
        }
        for (int z = 13; z <= 15; z++) b.set(11, 0, z, glow());
        for (int[] l : new int[][]{{5, 15}, {17, 15}, {5, 18}, {17, 18}}) b.set(l[0], ROOF, l[1], glow());
        b.set(2, 4, 14, warn());
        b.set(20, 4, 14, warn());
        b.out.chest(3, 1, 16, Direction.EAST, loot(kind == RuinKind.SPAWN_VAULT ? "precursor_spawn_vault" : "precursor_vault"));
        if (secondChest) b.out.chest(19, 1, 16, Direction.WEST, loot("precursor_vault"));
        if (terminal) b.set(11, 1, 15, terminal(kind.logs, Direction.NORTH));
        if (kind == RuinKind.SPAWN_VAULT) {
            // the antechamber leaves a Scanner for whoever finds it first
            b.out.chest(4, 1, 7, Direction.EAST, loot("precursor_spawn_antechamber"));
        }
        // tier two (its front east corner broke away with the ceiling below it), then the crown and its beacon
        for (int x = 4; x <= 18; x++) {
            for (int z = 7; z <= 18; z++) {
                boolean edge = x == 4 || x == 18 || z == 7 || z == 18;
                for (int y = ROOF + 1; y <= 13; y++) {
                    if (x >= 14 && z <= 10 && (x - 14) + (10 - z) + (y - ROOF) * 0.7 + b.n(x, y, z) * 1.5 > 3.2) continue;
                    if (!edge && y < 13) {
                        b.set(x, y, z, air());
                        continue;
                    }
                    boolean corner = (x == 4 || x == 18) && (z == 7 || z == 18);
                    BlockState s = y == 13 ? (edge ? trim() : b.plate(x, y, z, 0.6)) : corner ? pillar()
                            : (y == 11 && Math.floorMod(x + z, 3) == 0) ? glyph() : b.plate(x, y, z, 0.45);
                    b.set(x, y, z, s);
                }
            }
        }
        for (int x = 7; x <= 15; x++) {
            for (int z = 10; z <= 15; z++) {
                boolean edge = x == 7 || x == 15 || z == 10 || z == 15;
                b.set(x, 14, z, edge ? b.plate(x, 14, z, 0.5) : plating());
                if (x >= 9 && x <= 13 && z >= 11 && z <= 14) b.set(x, 15, z, (x == 9 || x == 13 || z == 11 || z == 14) ? trim() : plating());
            }
        }
        b.set(11, 16, 12, glow());
        b.set(11, 16, 13, glow());
        b.set(11, 17, 12, warn());
        // the pylons: tall, tapering, a light line on the face toward the walk, amber crowns
        for (int px : new int[]{1, 19}) {
            int inner = px == 1 ? 3 : 19;
            for (int y = 1; y <= 13; y++) {
                for (int x = px; x <= px + 2; x++) {
                    for (int z = 0; z <= 2; z++) {
                        boolean corner = (x == px || x == px + 2) && (z == 0 || z == 2);
                        BlockState s;
                        if (y == 1) s = of(Blocks.POLISHED_DEEPSLATE);
                        else if (y <= 10) s = corner ? pillar() : (x == inner && z == 1 && y >= 3 && y <= 9) ? glow() : b.plate(x, y, z, y > 7 ? 0.5 : 0.2);
                        else if (y == 11) s = trim();
                        else s = (x == px + 1 && z == 1) ? (y == 13 ? warn() : trim()) : air();
                        b.set(x, y, z, s);
                    }
                }
            }
        }
        outerCache(b, 4, 1, 1, Direction.SOUTH);
        // weathering: moss on the roofs, vines down the cornice
        for (int x = X0 - 1; x <= X1 + 1; x++) {
            for (int z = Z0 - 1; z <= Z1 + 1; z++) {
                boolean underTier2 = x >= 4 && x <= 18 && z >= 7 && z <= 18;
                if (!underTier2 && b.sn(x, 10, z, 2.5) < 0.42 && !(x >= 15 && x <= 18 && z >= 7 && z <= 9)) b.set(x, ROOF + 1, z, of(Blocks.MOSS_CARPET));
            }
        }
        // vines down the recessed panels (they cling to the infill behind the pilaster line)
        for (int x = X0 + 1; x < X1; x++) {
            if (!isPilaster(x, Z0, X0, X1, Z0, Z1) && (x < 8 || x > 14) && b.n(x, 2, 3) < 0.4) b.curtain(x, ROOF - 1, Z0, Direction.SOUTH, 6);
            if (!isPilaster(x, Z1, X0, X1, Z0, Z1) && b.n(x, 3, 22) < 0.4) b.curtain(x, ROOF - 1, Z1, Direction.NORTH, 6);
        }
        for (int z = Z0 + 1; z < Z1; z++) {
            if (!isPilaster(X0, z, X0, X1, Z0, Z1) && b.n(0, 4, z) < 0.35) b.curtain(X0, ROOF - 1, z, Direction.EAST, 6);
            if (!isPilaster(X1, z, X0, X1, Z0, Z1) && b.n(22, 5, z) < 0.35) b.curtain(X1, ROOF - 1, z, Direction.WEST, 6);
        }
    }

    private static boolean isPilaster(int x, int z, int x0, int x1, int z0, int z1) {
        if (x == x0 || x == x1) return z == z0 || z == z1 || Math.floorMod(z - z0, 4) == 0;
        return x == x0 || x == x1 || x == 7 || x == 15 || Math.floorMod(x - x0, 4) == 0;
    }

    // ---- the Monolith --------------------------------------------------------------------------------------------------

    /**
     * A black-and-brass obelisk rising 36 blocks over a sand-drowned plaza of broken standing stones, its glyph channels
     * glowing blue; above a gap of empty air its capstone hangs with an amber eye, and stones float round the gap. A hatch
     * in the plaza leads down a ladder to the sealed chamber under it (machines, loot, a terminal).
     */
    private static void monolith(RandomSource r, Brush b) {
        List<MachineType> machines = machinesFor(RuinKind.MONOLITH, r);
        boolean terminal = hasTerminal(RuinKind.MONOLITH, r);
        final int P = 6; // plaza level
        // the chamber under the plaza
        for (int x = 3; x <= 13; x++) {
            for (int z = 3; z <= 13; z++) {
                boolean edge = x == 3 || x == 13 || z == 3 || z == 13;
                for (int y = 0; y <= 5; y++) {
                    if (edge || y == 0 || y == 5) b.set(x, y, z, y == 0 ? panel() : b.plate(x, y, z, 0.3));
                    else b.set(x, y, z, air());
                }
            }
        }
        for (int[] l : new int[][]{{6, 6}, {10, 10}, {6, 10}, {10, 6}}) b.set(l[0], 5, l[1], glow());
        for (int x = 5; x <= 11; x++) b.set(x, 0, 8, x == 8 ? glow() : trim());
        int[] xs = row(machines.size(), 5, 8, 11);
        for (int i = 0; i < xs.length && i < machines.size(); i++) b.set(xs[i], 1, 12, broken(machines.get(i), Direction.NORTH));
        b.out.chest(12, 1, 5, Direction.WEST, loot("precursor_monolith"));
        if (terminal) b.set(8, 1, 4, terminal(RuinKind.MONOLITH.logs, Direction.SOUTH));
        // the plaza: a disc of sandstone with a Precursor ring inlaid, radial brass lines, drifting sand
        for (int x = 0; x <= 16; x++) {
            for (int z = 0; z <= 16; z++) {
                double d = Math.sqrt((x - 8) * (x - 8) + (z - 8) * (z - 8));
                if (d > 8.4) continue;
                boolean under = x >= 3 && x <= 13 && z >= 3 && z <= 13;
                if (!under) b.out.fillDown(x, P - 1, z, of(Blocks.SANDSTONE), 10);
                double drift = b.sn(x, P, z, 2.5);
                BlockState s;
                if (Math.abs(d - 6.5) < 0.5) s = panel();
                else if ((x == 8 || z == 8) && d > 3.5) s = trim();
                else if (drift < 0.3 && d > 4) s = of(Blocks.SAND);
                else s = b.n(x, P, z) < 0.35 ? of(Blocks.SMOOTH_SANDSTONE) : of(Blocks.CUT_SANDSTONE);
                b.set(x, P, z, s);
                for (int y = P + 1; y <= P + 4; y++) b.set(x, y, z, air());
                if (d > 6.9 && drift < 0.45) b.set(x, P + 1, z, of(Blocks.SAND));
            }
        }
        // the hatch over the ladder, framed in brass
        b.set(4, P, 8, air());
        b.set(4, P, 7, trim());
        b.set(4, P, 9, trim());
        b.set(5, P, 8, trim());
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
        for (int y = 1; y <= P; y++) b.set(4, y, 8, ladder);
        // broken standing stones round the ring
        for (int i = 0; i < 7; i++) {
            double a = i * Math.PI * 2 / 7 + 0.35;
            int x = 8 + (int) Math.round(Math.cos(a) * 7.4), z = 8 + (int) Math.round(Math.sin(a) * 7.4);
            if (x <= 5 && Math.abs(z - 8) <= 2) continue; // not over the hatch
            int h = 1 + (int) (b.n(i, 9, 3) * 4);
            for (int y = P + 1; y <= P + h; y++) b.set(x, y, z, y == P + h && h >= 3 ? of(Blocks.CHISELED_SANDSTONE) : of(Blocks.CUT_SANDSTONE));
        }
        // the pedestal, half buried
        for (int x = 4; x <= 12; x++) {
            for (int z = 4; z <= 12; z++) {
                boolean in7 = x >= 5 && x <= 11 && z >= 5 && z <= 11;
                if (in7) {
                    boolean edge = x == 5 || x == 11 || z == 5 || z == 11;
                    b.set(x, P + 1, z, edge ? trim() : plating());
                    if (x >= 6 && x <= 10 && z >= 6 && z <= 10) b.set(x, P + 2, z, (x == 6 || x == 10 || z == 6 || z == 10) ? b.plate(x, P + 2, z, 0.4) : plating());
                } else if (b.sn(x, P + 1, z, 2.0) < 0.55 && !(x == 4 && Math.abs(z - 8) <= 1)) b.set(x, P + 1, z, of(Blocks.SAND));
            }
        }
        // the shaft: 5x5 with pilaster corners, brass bands, glyph channels up the middle of each face
        for (int y = P + 3; y <= P + 20; y++) {
            boolean band = (y - P - 3) % 6 == 5;
            for (int x = 6; x <= 10; x++) {
                for (int z = 6; z <= 10; z++) {
                    boolean edge = x == 6 || x == 10 || z == 6 || z == 10;
                    if (!edge) {
                        b.set(x, y, z, plating());
                        continue;
                    }
                    boolean corner = (x == 6 || x == 10) && (z == 6 || z == 10);
                    boolean channel = (x == 8) != (z == 8);
                    int k = (y - P - 3) % 6;
                    BlockState s = band ? trim() : corner ? pillar() : channel ? (k == 1 || k == 2 ? glyph() : k == 3 ? glow() : plating())
                            : b.plate(x, y, z, y < P + 6 ? 0.45 : 0.15);
                    b.set(x, y, z, s);
                }
            }
        }
        // the upper shaft: 3x3, tapering to the gap
        for (int y = P + 21; y <= P + 28; y++) {
            for (int x = 7; x <= 9; x++) {
                for (int z = 7; z <= 9; z++) {
                    boolean centre = x == 8 && z == 8;
                    boolean face = (x == 8) != (z == 8);
                    b.set(x, y, z, y == P + 28 ? trim() : centre ? plating() : face && (y - P) % 3 == 0 ? glyph() : b.plate(x, y, z, 0.1));
                }
            }
        }
        // the gap, the stones it holds up, and the floating capstone with its amber eye
        for (int y = P + 29; y <= P + 30; y++) b.box(7, y, 7, 9, y, 9, air());
        b.set(5, P + 29, 8, b.plate(5, 0, 8, 0.3));
        b.set(11, P + 30, 8, plating());
        b.set(8, P + 30, 5, trim());
        b.set(8, P + 29, 11, b.plate(8, 0, 11, 0.3));
        for (int y = P + 31; y <= P + 33; y++) {
            for (int x = 7; x <= 9; x++) {
                for (int z = 7; z <= 9; z++) {
                    boolean face = (x == 8) != (z == 8);
                    b.set(x, y, z, y == P + 33 ? trim() : face && y == P + 32 ? glow() : plating());
                }
            }
        }
        b.set(8, P + 34, 8, plating());
        b.set(8, P + 35, 8, warn());
        b.set(8, P + 36, 8, trim());
        outerCache(b, 3, P + 1, 10, Direction.EAST); // on the plaza, beside the hatch
    }

    // ---- the Ocean Lab -------------------------------------------------------------------------------------------------

    /**
     * Three glass domes on the sea floor, ribbed in plating and lit from inside, joined by glass tubes: the main dome
     * (the machines and the way in, through a membrane airlock on its east side), a storage dome to the north and a
     * study dome to the west with the terminal. A mast with lights rises from the main dome toward the surface; the base
     * rings are crusted with prismarine and dead coral.
     */
    private static void ocean(RandomSource r, Brush b) {
        List<MachineType> machines = machinesFor(RuinKind.OCEAN, r);
        boolean terminal = hasTerminal(RuinKind.OCEAN, r);
        dome(b, 15, 17, 7);
        dome(b, 15, 4, 4);
        dome(b, 4, 17, 4);
        // tubes: 3 wide, 3 high inside, glass walls between plating frames
        tube(b, true, 15, 8, 10);  // north dome <-> main, along z
        tube(b, false, 17, 7, 9);  // west dome <-> main, along x
        // the airlock on the main dome's east side: membrane on the outer face
        for (int x = 22; x <= 26; x++) {
            for (int z = 16; z <= 18; z++) {
                for (int y = 0; y <= 3; y++) {
                    boolean inside = z == 17 && (y == 1 || y == 2);
                    if (inside) b.set(x, y, z, x == 26 ? AboveBlocks.MEMBRANE.get().defaultBlockState() : air());
                    else b.set(x, y, z, y == 3 && z == 17 ? (x == 26 ? warn() : trim()) : plating());
                }
            }
            b.out.fillDown(x, -1, 16, plating(), 6);
            b.out.fillDown(x, -1, 18, plating(), 6);
        }
        // the mast: a fluted column with brass crossbars, lights near the top
        for (int y = 8; y <= 19; y++) {
            b.set(15, y, 17, pillar());
            if (y % 4 == 0) {
                b.set(14, y, 17, trim());
                b.set(16, y, 17, trim());
            }
        }
        b.set(14, 17, 17, of(Blocks.SEA_LANTERN));
        b.set(16, 17, 17, of(Blocks.SEA_LANTERN));
        b.set(15, 20, 17, glow());
        b.set(15, 21, 17, warn());
        // inside: machines in the main dome, loot in the north dome, the terminal in the west dome
        int[] xs = row(machines.size(), 12, 15, 18);
        for (int i = 0; i < xs.length && i < machines.size(); i++) {
            b.set(xs[i], 0, 21, trim());
            b.set(xs[i], 1, 21, broken(machines.get(i), Direction.NORTH));
        }
        b.out.chest(15, 1, 2, Direction.SOUTH, loot("precursor_ocean"));
        b.set(13, 1, 4, of(Blocks.BARREL));
        b.set(17, 1, 4, of(Blocks.BARREL));
        if (terminal) b.set(2, 1, 17, terminal(RuinKind.OCEAN.logs, Direction.EAST));
        b.set(4, 1, 15, of(Blocks.LECTERN));
        outerCache(b, 20, 1, 19, Direction.WEST); // in the main dome, by the airlock
    }

    /** A glass dome of radius R on a plating ring at (cx, cz): ribs, a brass belt, a lit floor, a lamp hanging inside. */
    private static void dome(Brush b, int cx, int cz, int R) {
        for (int x = cx - R - 2; x <= cx + R + 2; x++) {
            for (int z = cz - R - 2; z <= cz + R + 2; z++) {
                if (x < 0 || z < 0 || x > 30 || z > 30) continue;
                double flat = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (flat <= R + 1.5) {
                    b.out.fillDown(x, -1, z, plating(), 8);
                    BlockState f;
                    if (flat < R - 0.6) f = Math.abs(flat - R * 0.62) < 0.5 ? (((x + z) & 1) == 0 ? glow() : panel()) : flat < 1 ? trim() : panel();
                    else {
                        double crust = b.sn(x, 0, z, 2.0);
                        f = crust < 0.3 ? of(Blocks.DARK_PRISMARINE) : crust < 0.5 ? of(Blocks.PRISMARINE) : plating();
                    }
                    b.set(x, 0, z, f);
                    if (flat >= R + 0.5 && b.n(x, 1, z) < 0.12) {
                        b.set(x, 1, z, b.n(x, 2, z) < 0.5 ? of(Blocks.DEAD_TUBE_CORAL_BLOCK) : of(Blocks.DEAD_HORN_CORAL_BLOCK));
                    }
                }
                for (int y = 1; y <= R + 1; y++) {
                    double d = Math.sqrt((x - cx) * (x - cx) + (y - 0.5) * (y - 0.5) + (z - cz) * (z - cz));
                    if (d < R - 0.5) b.set(x, y, z, air());
                    else if (d < R + 0.5) {
                        double ang = Math.atan2(z - cz, x - cx);
                        boolean rib = Math.abs(Math.sin(ang * 4)) * flat < 0.55;
                        boolean belt = y == Math.max(2, R / 2);
                        b.set(x, y, z, belt ? trim() : rib ? plating() : glass());
                    }
                }
            }
        }
        b.set(cx, R, cz, glow());
        if (R >= 6) {
            b.set(cx, R - 1, cz, of(Blocks.IRON_CHAIN));
            b.set(cx, R - 2, cz, of(Blocks.SEA_LANTERN));
        }
    }

    /** A glass tube between domes: along z at x = {@code at} (or along x at z = {@code at}), from {@code from} to {@code to}. */
    private static void tube(Brush b, boolean alongZ, int at, int from, int to) {
        for (int t = from; t <= to; t++) {
            for (int w = at - 2; w <= at + 2; w++) {
                for (int y = 0; y <= 4; y++) {
                    int x = alongZ ? w : t, z = alongZ ? t : w;
                    boolean inside = Math.abs(w - at) <= 1 && y >= 1 && y <= 3;
                    boolean frame = y == 0 || y == 4 || Math.abs(w - at) == 2 && (t % 2 == 0 || y == 1);
                    b.set(x, y, z, inside ? air() : frame ? (y == 0 && Math.abs(w - at) <= 1 ? panel() : plating()) : glass());
                }
                b.out.fillDown(alongZ ? w : t, -1, alongZ ? t : w, plating(), 6);
            }
        }
    }

    // ---- the Sky Platform ----------------------------------------------------------------------------------------------

    /**
     * A Precursor disc above the clouds: a keel of glowing rings beneath (it reads as a light in the sky from the ground),
     * a brass-railed deck, a glass cabin with a spire; round it four islands of earth it tore up with it, tethered by
     * chains, and long chains dangling lanterns below.
     */
    private static void sky(RandomSource r, Brush b) {
        List<MachineType> machines = machinesFor(RuinKind.SKY, r);
        boolean terminal = hasTerminal(RuinKind.SKY, r);
        final int C = 24, DECK = 12;
        for (int x = C - 12; x <= C + 12; x++) {
            for (int z = C - 12; z <= C + 12; z++) {
                double d = Math.sqrt((x - C) * (x - C) + (z - C) * (z - C));
                // the keel: rings narrowing downward, their outer edge alight
                for (int y = 4; y < DECK; y++) {
                    double rad = 1.0 + (y - 4) * 1.32;
                    if (d > rad) continue;
                    boolean rim = d > rad - 1.0;
                    b.set(x, y, z, rim ? ((y & 1) == 0 ? glow() : trim()) : y == 4 ? glow() : plating());
                }
                if (d <= 11.6) {
                    int ring = (int) Math.round(d);
                    boolean spoke = (x == C || z == C) && d > 5;
                    b.set(x, DECK, z, d > 10.6 ? trim() : spoke || ring == 4 || ring == 8 ? trim() : panel());
                    if (d > 10.6 && ((x + z) & 1) == 0) b.set(x, DECK + 1, z, trim());
                }
            }
        }
        for (int[] l : new int[][]{{C, C - 11}, {C, C + 11}, {C - 11, C}, {C + 11, C}}) b.set(l[0], DECK + 1, l[1], warn());
        // the cabin: pilaster corners, glass walls, a glowing roof, a door to the south
        for (int x = C - 4; x <= C + 4; x++) {
            for (int z = C - 4; z <= C + 4; z++) {
                boolean wall = x == C - 4 || x == C + 4 || z == C - 4 || z == C + 4;
                boolean corner = (x == C - 4 || x == C + 4) && (z == C - 4 || z == C + 4);
                for (int y = DECK + 1; y <= DECK + 5; y++) {
                    if (!wall) b.set(x, y, z, air());
                    else if (corner) b.set(x, y, z, pillar());
                    else if (y == DECK + 1 || y == DECK + 5) b.set(x, y, z, b.plate(x, y, z, 0.2));
                    else b.set(x, y, z, glass());
                }
                b.set(x, DECK + 6, z, wall ? trim() : (Math.abs(x - C) <= 1 && Math.abs(z - C) <= 1) ? glow() : plating());
            }
        }
        b.set(C, DECK + 1, C + 4, air());
        b.set(C, DECK + 2, C + 4, air());
        b.set(C, DECK + 3, C + 4, trim());
        // the spire
        for (int y = DECK + 7; y <= DECK + 12; y++) b.set(C, y, C, pillar());
        for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) b.set(C + o[0], DECK + 9, C + o[1], trim());
        b.set(C, DECK + 13, C, warn());
        int[] xs = row(machines.size(), C - 2, C, C + 2);
        for (int i = 0; i < xs.length && i < machines.size(); i++) b.set(xs[i], DECK + 1, C - 3, broken(machines.get(i), Direction.SOUTH));
        b.out.chest(C - 3, DECK + 1, C + 3, Direction.NORTH, loot("precursor_sky"));
        if (terminal) b.set(C + 3, DECK + 1, C + 2, terminal(RuinKind.SKY.logs, Direction.WEST));
        outerCache(b, C, DECK + 1, C + 7, Direction.NORTH); // on the deck, before the cabin
        // chains dangling under the keel, lanterns at their ends
        for (int[] c : new int[][]{{C - 4, C}, {C + 4, C}, {C, C - 4}, {C, C + 4}}) {
            int len = 4 + (int) (b.n(c[0], 0, c[1]) * 4);
            int top = 4 + (int) Math.ceil((Math.abs(c[0] - C) + Math.abs(c[1] - C) - 1.0) / 1.32);
            for (int y = top - 1; y > top - 1 - len && y > 0; y--) b.set(c[0], y, c[1], of(Blocks.IRON_CHAIN));
            b.set(c[0], Math.max(0, top - 1 - len), c[1], Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
        // four islands torn up with it, tethered by chains
        int[][] spots = {{C, 5}, {43, C}, {C, 43}, {5, C}};
        for (int i = 0; i < 4; i++) {
            int jitter = (int) ((b.n(i, 7, 7) - 0.5) * 6);
            int ix = spots[i][0] + (i % 2 == 0 ? jitter : 0), iz = spots[i][1] + (i % 2 == 1 ? jitter : 0);
            int top = 7 + (int) (b.n(i, 9, 9) * 9);
            double rad = 2.6 + b.n(i, 1, 1) * 1.2;
            island(b, ix, top, iz, rad, i);
            tether(b, i, ix, iz, top, rad, C, DECK);
        }
    }

    /** An island of earth: grass on top, a stone underside tapering down with ore in it, roots hanging below. */
    private static void island(Brush b, int cx, int top, int cz, double rad, int which) {
        int R = (int) Math.ceil(rad) + 1;
        for (int x = cx - R; x <= cx + R; x++) {
            for (int z = cz - R; z <= cz + R; z++) {
                if (x < 0 || z < 0 || x > 48 || z > 48) continue;
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz)) + b.n(x, top, z) * 0.6;
                if (d > rad) continue;
                int depth = (int) Math.round((rad - d) * 1.6 + 1 + b.n(x, 3, z) * 1.5);
                for (int k = 0; k <= depth; k++) {
                    int y = top - k;
                    if (y < 0) break;
                    BlockState s;
                    if (k == 0) s = d > rad - 0.8 && b.n(x, y, z) < 0.4 ? of(Blocks.COARSE_DIRT) : of(Blocks.GRASS_BLOCK);
                    else if (k <= 1) s = of(Blocks.DIRT);
                    else {
                        double o = b.n(x, y, z);
                        s = o < 0.06 ? of(Blocks.COAL_ORE) : o < 0.1 ? of(Blocks.IRON_ORE) : o < 0.13 ? of(Blocks.COPPER_ORE)
                                : o < 0.35 ? of(Blocks.ANDESITE) : of(Blocks.STONE);
                    }
                    b.set(x, y, z, s);
                }
                int bottom = top - depth - 1;
                if (bottom > 0 && b.n(x, bottom, z) < 0.3) b.set(x, bottom, z, of(Blocks.HANGING_ROOTS));
                if (d < rad - 1 && b.n(x, top + 1, z) < 0.3) b.set(x, top + 1, z, b.n(x, 5, z) < 0.8 ? of(Blocks.SHORT_GRASS)
                        : b.n(x, 6, z) < 0.5 ? of(Blocks.POPPY) : of(Blocks.DANDELION));
            }
        }
        if (which == 1) {
            // a small tree that came up with the ground
            for (int y = top + 1; y <= top + 4; y++) b.set(cx, y, cz, of(Blocks.OAK_LOG));
            for (int x = cx - 2; x <= cx + 2; x++) {
                for (int z = cz - 2; z <= cz + 2; z++) {
                    for (int y = top + 3; y <= top + 5; y++) {
                        if ((x == cx && z == cz && y < top + 5) || Math.abs(x - cx) + Math.abs(z - cz) + (y - top - 3) > 3) continue;
                        b.set(x, y, z, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
                    }
                }
            }
        } else if (which == 3) {
            // a lamp post someone planted on it
            b.set(cx, top + 1, cz, trim());
            b.set(cx, top + 2, cz, pillar());
            b.set(cx, top + 3, cz, warn());
        }
    }

    /** A chain from an island's anchor across to a post on the deck's rim (straight, along the axis between them). */
    private static void tether(Brush b, int i, int ix, int iz, int top, double rad, int c, int deck) {
        int h = Math.max(deck + 2, top + 2);
        boolean alongZ = i % 2 == 0;
        int dir = (alongZ ? iz : ix) < c ? 1 : -1; // from the island toward the platform
        int start = (alongZ ? iz : ix) + (int) Math.round(rad) * dir;
        int rim = c - 11 * dir;
        // the anchor post on the island edge
        int ax = alongZ ? ix : start, az = alongZ ? start : iz;
        for (int y = top + 1; y <= h; y++) b.set(ax, y, az, y == h ? trim() : of(Blocks.IRON_CHAIN));
        // the deck post
        int px = alongZ ? ix : rim, pz = alongZ ? rim : iz;
        for (int y = deck + 1; y <= h; y++) b.set(px, y, pz, y == h ? trim() : pillar());
        // the chain between them
        Direction.Axis axis = alongZ ? Direction.Axis.Z : Direction.Axis.X;
        int from = Math.min(start, rim) + 1, to = Math.max(start, rim) - 1;
        for (int t = from; t <= to; t++) {
            int x = alongZ ? ix : t, z = alongZ ? t : iz;
            b.set(x, h, z, Blocks.IRON_CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, axis));
        }
    }

    /** Door rank of a surface vault by distance from the world origin: the further out, the higher. */
    public static int vaultRank(double distanceFromOrigin) {
        if (distanceFromOrigin < 1200) return 1;  // D
        if (distanceFromOrigin < 2800) return 2;  // C
        if (distanceFromOrigin < 5000) return 3;  // B
        return 4;                                 // A
    }
}
