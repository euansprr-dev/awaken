package dev.awaken.body.ascent;

import dev.awaken.core.AwakenNet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The rappel's stage for tours ({@code /awaken ascent rappel stage [none|iron|deepglass]}; SPEC §28.5): a shaft carved
 * round the player, {@link #ACROSS} across and {@link #DEPTH} deep, open to the sky, its rock greying from stone to
 * tuff to deepslate going down; an anchor ring on the north wall's lip, so the rope runs down that wall's face; a lit
 * side passage in the same wall halfway down ({@link #PASSAGE} under the lip: 3 wide, 3 high, 9 deep). The player is
 * given the Tether and the line asked for, put in survival (a creative double jump would toggle flight), and hung on a
 * rope from the ring just under the lip, facing the wall. Built at once; plain blocks.
 */
final class RappelStage {
    private RappelStage() {}

    /** The shaft's inside (blocks across), its depth under the lip, and the passage's floor under the lip. */
    static final int ACROSS = 7, DEPTH = 44, PASSAGE = 22;
    private static final int R = ACROSS / 2, SHELL = R + 3;
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();

    /** Builds the stage round {@code p} and hangs them in it. Returns the anchor (x, y, z). */
    static double[] build(ServerPlayer p, int line) {
        ServerLevel level = p.level();
        BlockPos o = p.blockPosition();
        int ox = o.getX(), oz = o.getZ(), top = o.getY();
        int floor = top - DEPTH;
        BlockState air = Blocks.AIR.defaultBlockState();
        // the rock: a shell round the shaft, from under its floor to the lip
        for (int y = floor - 1; y < top; y++) {
            for (int dx = -SHELL; dx <= SHELL; dx++) for (int dz = -SHELL; dz <= SHELL; dz++) {
                boolean inside = Math.abs(dx) <= R && Math.abs(dz) <= R && y > floor;
                set(level, ox + dx, y, oz + dz, inside ? air : rock(ox + dx, y, oz + dz, top - y));
            }
        }
        // room over the lip for the camera and the swing
        for (int y = top; y <= top + 8; y++) {
            for (int dx = -SHELL; dx <= SHELL; dx++) for (int dz = -SHELL; dz <= SHELL; dz++) set(level, ox + dx, y, oz + dz, air);
        }
        // the side passage into the north wall: rock round it first (it runs past the shell), then carved and lit
        int py = top - PASSAGE;
        for (int y = py - 2; y <= py + 4; y++) {
            for (int dx = -R; dx <= R; dx++) for (int dz = -14; dz <= -SHELL - 1; dz++) set(level, ox + dx, y, oz + dz, rock(ox + dx, y, oz + dz, top - y));
        }
        for (int y = py; y <= py + 2; y++) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -12; dz <= -R - 1; dz++) set(level, ox + dx, y, oz + dz, air);
        }
        set(level, ox, py, oz - 11, Blocks.LANTERN.defaultBlockState());
        set(level, ox, py + 3, oz - 8, Blocks.SHROOMLIGHT.defaultBlockState());
        set(level, ox - 1, py, oz - 6, Blocks.LANTERN.defaultBlockState());
        // the floor: rubble and a lamp, so the bottom reads in a shot
        set(level, ox + 2, floor + 1, oz + 1, Blocks.LANTERN.defaultBlockState());
        set(level, ox - 2, floor + 1, oz + 2, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        set(level, ox + 1, floor + 1, oz - 2, Blocks.COBBLED_DEEPSLATE_SLAB.defaultBlockState());
        // the anchor: a ring on the north wall's top, its south face in line with the wall's face
        set(level, ox, top, oz - R - 1, AscentContent.ANCHOR_RING.get().defaultBlockState());
        double ax = ox + 0.5, ay = top + 0.5, az = oz - R + 0.04;
        Ascent.grant(p, Ascent.Tier.TETHER, "rappel stage");
        p.setData(AscentContent.ROPE_KIT, p.getData(AscentContent.ROPE_KIT).withLine(line));
        AscentServer.syncKit(p, false);
        p.setGameMode(GameType.SURVIVAL);
        // hung under the ring: the hand 3.5 under it, the body against the wall, facing it
        double bx = ax, bz = oz - R + 0.31, by = ay - 3.5 - AscentRules.HAND_HEIGHT;
        p.teleportTo(level, bx, by, bz, java.util.Set.of(), 180F, 25F, true);
        AwakenNet.send(p, new AscentPayloads.Pilot(String.format(java.util.Locale.ROOT, "rappel hang %.3f %.3f %.3f", ax, ay, az)));
        dev.awaken.Awaken.LOG.info("[ascent] rappel stage at {} {} {}: anchor {} {} {}, passage floor y {}, shaft floor y {}, line {}", ox, top, oz,
                ax, ay, az, py, floor + 1, line);
        return new double[]{ax, ay, az};
    }

    /** The shaft's rock by depth (blocks under the lip): stone flecked with andesite, then tuff, then deepslate. */
    private static BlockState rock(int x, int y, int z, int depth) {
        int h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
        int n = Math.floorMod(h, 7);
        if (depth < 14) return n == 0 ? Blocks.ANDESITE.defaultBlockState() : n == 1 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState();
        if (depth < 30) return n <= 1 ? Blocks.STONE.defaultBlockState() : n == 2 ? Blocks.CALCITE.defaultBlockState() : Blocks.TUFF.defaultBlockState();
        return n == 0 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState();
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState s) {
        level.setBlock(POS.set(x, y, z), s, Block.UPDATE_CLIENTS);
    }
}
