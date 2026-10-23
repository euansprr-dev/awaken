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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The blueprints of every Precursor ruin, in local coordinates (0,0,0 = the piece's lower corner, before rotation).
 * Deterministic from the piece seed, so each chunk the ruin touches rebuilds exactly the same blocks and keeps only its
 * own. The Halo station reuses the machine and terminal helpers.
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

    public static Size size(RuinKind kind) {
        return switch (kind) {
            case VAULT, SPAWN_VAULT -> new Size(13, 10, 13);
            case SKY -> new Size(25, 11, 25);
            case OCEAN -> new Size(17, 11, 17);
            case MONOLITH -> new Size(9, 24, 9);
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

    // ---- build ------------------------------------------------------------------------------------------------------

    public static void build(RuinKind kind, long seed, int doorRank, Sink out) {
        RandomSource r = new XoroshiroRandomSource(seed);
        switch (kind) {
            case VAULT, SPAWN_VAULT -> vault(kind, r, doorRank, out);
            case SKY -> sky(r, out);
            case OCEAN -> ocean(r, out);
            case MONOLITH -> monolith(r, out);
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

    /** A squat sealed bunker. Antechamber in front, sealed chamber behind a Precursor door. Door faces -Z. */
    private static void vault(RuinKind kind, RandomSource r, int doorRank, Sink out) {
        List<MachineType> machines = machinesFor(kind, r);
        boolean terminal = hasTerminal(kind, r);
        boolean secondChest = r.nextBoolean();
        for (int x = 0; x <= 12; x++) {
            for (int z = 0; z <= 12; z++) {
                boolean edge = x == 0 || x == 12 || z == 0 || z == 12;
                boolean corner = (x == 0 || x == 12) && (z == 0 || z == 12);
                out.fillDown(x, -1, z, plating(), 12);
                out.set(x, 0, z, edge ? plating() : panel());
                for (int y = 1; y <= 7; y++) {
                    if (corner) out.set(x, y, z, trim());
                    else if (edge) out.set(x, y, z, (y == 1 || y == 7) ? trim() : plating());
                    else out.set(x, y, z, air());
                }
                boolean sky = x >= 5 && x <= 7 && z >= 6 && z <= 8;
                out.set(x, 8, z, corner ? trim() : sky ? (x == 6 && z == 7 ? glow() : glass()) : plating());
                if (corner) out.set(x, 9, z, warn());
                else if (!sky && r.nextFloat() < 0.08f) out.set(x, 9, z, Blocks.MOSS_CARPET.defaultBlockState());
            }
        }
        // front opening with a brass lintel and amber lights
        for (int x = 5; x <= 7; x++) for (int y = 1; y <= 3; y++) out.set(x, y, 0, air());
        for (int x = 4; x <= 8; x++) out.set(x, 4, 0, trim());
        out.set(4, 3, 0, warn());
        out.set(8, 3, 0, warn());
        // inner wall with the sealed door
        for (int x = 1; x <= 11; x++) {
            for (int y = 1; y <= 7; y++) {
                boolean door = x >= 5 && x <= 7 && y <= 3;
                if (!door) out.set(x, y, 4, y == 4 && x >= 4 && x <= 8 ? trim() : plating());
            }
        }
        for (int x = 5; x <= 7; x++) {
            for (int y = 1; y <= 3; y++) {
                out.set(x, y, 4, x == 6 && y == 2
                        ? AboveBlocks.DOOR_CORE.get().defaultBlockState().setValue(DoorCoreBlock.RANK, Math.max(0, Math.min(6, doorRank)))
                        : AboveBlocks.DOOR.get().defaultBlockState());
            }
        }
        out.set(1, 6, 3, warn());
        out.set(11, 6, 3, warn());
        out.set(1, 6, 5, warn());
        out.set(11, 6, 5, warn());
        // sealed chamber
        out.set(3, 7, 6, glow());
        out.set(9, 7, 6, glow());
        out.set(3, 7, 10, glow());
        out.set(9, 7, 10, glow());
        int[] xs = row(machines.size(), 3, 6, 9);
        for (int i = 0; i < xs.length && i < machines.size(); i++) out.set(xs[i], 1, 11, broken(machines.get(i), Direction.NORTH));
        out.chest(1, 1, 8, Direction.EAST, loot(kind == RuinKind.SPAWN_VAULT ? "precursor_spawn_vault" : "precursor_vault"));
        if (secondChest) out.chest(11, 1, 8, Direction.WEST, loot("precursor_vault"));
        if (terminal) out.set(6, 1, 7, terminal(kind.logs, Direction.NORTH));
        if (kind == RuinKind.SPAWN_VAULT) {
            // the antechamber leaves a Scanner for whoever finds it first
            out.chest(2, 1, 2, Direction.EAST, loot("precursor_spawn_antechamber"));
        }
        outerCache(out, 10, 1, 2, Direction.WEST);
    }

    /**
     * Landmarks (world module): every ruin keeps something worth taking for an E-rank finder outside its sealed parts.
     * Fixed spots, no random draws, so ruins already half-generated keep matching their other chunks.
     */
    private static void outerCache(Sink out, int x, int y, int z, Direction facing) {
        out.chest(x, y, z, facing, loot("landmark_outer"));
    }

    /** A disc above the clouds with a glowing underside, a keel, and a glass cabin. */
    private static void sky(RandomSource r, Sink out) {
        List<MachineType> machines = machinesFor(RuinKind.SKY, r);
        boolean terminal = hasTerminal(RuinKind.SKY, r);
        for (int x = 0; x <= 24; x++) {
            for (int z = 0; z <= 24; z++) {
                double d = Math.sqrt((x - 12) * (x - 12) + (z - 12) * (z - 12));
                if (d <= 2.0) out.set(x, 0, z, glow());
                if (d <= 4.2) out.set(x, 1, z, d > 3.4 ? glow() : plating());
                if (d <= 7.2) out.set(x, 2, z, d > 6.4 ? glow() : plating());
                if (d <= 11.2) {
                    int ring = (int) Math.round(d);
                    out.set(x, 3, z, ring == 3 || ring == 6 || ring == 9 ? glow() : plating());
                }
                if (d <= 11.6) out.set(x, 4, z, d > 10.6 ? trim() : panel());
                if (d > 10.6 && d <= 11.6 && ((x + z) & 1) == 0) out.set(x, 5, z, trim());
            }
        }
        out.set(12, 5, 1, warn());
        out.set(12, 5, 23, warn());
        out.set(1, 5, 12, warn());
        out.set(23, 5, 12, warn());
        // cabin 7x7
        for (int x = 9; x <= 15; x++) {
            for (int z = 9; z <= 15; z++) {
                boolean wall = x == 9 || x == 15 || z == 9 || z == 15;
                boolean corner = (x == 9 || x == 15) && (z == 9 || z == 15);
                for (int y = 5; y <= 8; y++) {
                    if (!wall) out.set(x, y, z, air());
                    else if (corner || y == 5 || y == 8) out.set(x, y, z, plating());
                    else out.set(x, y, z, glass());
                }
                out.set(x, 9, z, wall ? trim() : (x == 12 && z == 12 ? glow() : plating()));
            }
        }
        out.set(12, 5, 15, air());
        out.set(12, 6, 15, air());
        out.set(12, 7, 15, trim());
        out.set(12, 10, 12, trim());
        int[] xs = row(machines.size(), 10, 12, 14);
        for (int i = 0; i < xs.length && i < machines.size(); i++) out.set(xs[i], 5, 10, broken(machines.get(i), Direction.SOUTH));
        out.chest(10, 5, 14, Direction.NORTH, loot("precursor_sky"));
        if (terminal) out.set(14, 5, 13, terminal(RuinKind.SKY.logs, Direction.WEST));
        outerCache(out, 12, 5, 18, Direction.NORTH);
    }

    /** A void-glass dome on the sea floor with a membrane airlock on +X. */
    private static void ocean(RandomSource r, Sink out) {
        List<MachineType> machines = machinesFor(RuinKind.OCEAN, r);
        boolean terminal = hasTerminal(RuinKind.OCEAN, r);
        for (int x = 0; x <= 16; x++) {
            for (int z = 0; z <= 16; z++) {
                double flat = Math.sqrt((x - 8) * (x - 8) + (z - 8) * (z - 8));
                if (flat <= 8.4) {
                    out.fillDown(x, -1, z, plating(), 8);
                    out.set(x, 0, z, flat < 7.0 ? panel() : plating());
                }
                for (int y = 1; y <= 9; y++) {
                    double d = Math.sqrt((x - 8) * (x - 8) + (y - 1) * (y - 1) + (z - 8) * (z - 8));
                    if (d < 7.0) out.set(x, y, z, air());
                    else if (d < 8.0) out.set(x, y, z, x == 8 || z == 8 || y == 4 ? plating() : glass());
                }
            }
        }
        out.set(8, 0, 8, glow());
        out.set(8, 8, 8, glow());
        out.set(3, 4, 8, glow());
        out.set(13, 4, 8, glow());
        // airlock tunnel on +X: z 7..9, y 0..3, x 13..16; membrane on the outside face
        for (int x = 13; x <= 16; x++) {
            for (int z = 7; z <= 9; z++) {
                for (int y = 0; y <= 3; y++) {
                    boolean inside = z == 8 && (y == 1 || y == 2);
                    if (inside) out.set(x, y, z, x == 16 ? AboveBlocks.MEMBRANE.get().defaultBlockState() : air());
                    else out.set(x, y, z, y == 3 && z == 8 ? warn() : plating());
                }
            }
        }
        int[] xs = row(machines.size(), 5, 8, 11);
        for (int i = 0; i < xs.length && i < machines.size(); i++) out.set(xs[i], 1, xs.length == 3 && i == 1 ? 3 : 4, broken(machines.get(i), Direction.SOUTH));
        out.chest(4, 1, 11, Direction.EAST, loot("precursor_ocean"));
        if (terminal) out.set(11, 1, 11, terminal(RuinKind.OCEAN.logs, Direction.WEST));
        outerCache(out, 12, 1, 6, Direction.WEST);
    }

    /** A brass-banded obelisk over the sand with a sealed chamber underneath (ladder down from the plaza). */
    private static void monolith(RandomSource r, Sink out) {
        List<MachineType> machines = machinesFor(RuinKind.MONOLITH, r);
        boolean terminal = hasTerminal(RuinKind.MONOLITH, r);
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                boolean edge = x == 0 || x == 8 || z == 0 || z == 8;
                for (int y = 0; y <= 5; y++) {
                    if (edge || y == 0 || y == 5) out.set(x, y, z, plating());
                    else out.set(x, y, z, air());
                }
                out.set(x, 6, z, edge ? trim() : panel());
            }
        }
        out.set(4, 5, 4, glow());
        out.set(2, 5, 2, glow());
        out.set(6, 5, 6, glow());
        // ladder down
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
        for (int y = 1; y <= 6; y++) out.set(1, y, 4, ladder);
        // corner posts
        for (int[] c : new int[][]{{0, 0}, {8, 0}, {0, 8}, {8, 8}}) {
            out.set(c[0], 7, c[1], trim());
            out.set(c[0], 8, c[1], warn());
        }
        // obelisk
        for (int y = 7; y <= 21; y++) {
            for (int x = 3; x <= 5; x++) {
                for (int z = 3; z <= 5; z++) {
                    boolean band = y == 11 || y == 16 || y == 21;
                    boolean faceCentre = (x == 4) != (z == 4);
                    boolean glyph = faceCentre && (y == 8 || y == 9 || y == 13 || y == 14 || y == 18 || y == 19);
                    out.set(x, y, z, band ? trim() : glyph ? glow() : plating());
                }
            }
        }
        out.set(4, 22, 4, warn());
        out.set(4, 23, 4, trim());
        int[] xs = row(machines.size(), 2, 4, 6);
        for (int i = 0; i < xs.length && i < machines.size(); i++) out.set(xs[i], 1, 7, broken(machines.get(i), Direction.NORTH));
        out.chest(7, 1, 2, Direction.WEST, loot("precursor_monolith"));
        if (terminal) out.set(4, 1, 2, terminal(RuinKind.MONOLITH.logs, Direction.SOUTH));
        outerCache(out, 1, 7, 7, Direction.EAST); // on the plaza, beside the ladder down
    }

    /** Door rank of a surface vault by distance from the world origin: the further out, the higher. */
    public static int vaultRank(double distanceFromOrigin) {
        if (distanceFromOrigin < 1200) return 1;  // D
        if (distanceFromOrigin < 2800) return 2;  // C
        if (distanceFromOrigin < 5000) return 3;  // B
        return 4;                                 // A
    }
}
