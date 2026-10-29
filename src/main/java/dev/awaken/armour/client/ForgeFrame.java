package dev.awaken.armour.client;

import dev.awaken.armour.ForgeSong;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.phys.Vec3;

/**
 * Where a piece lies on the anvil (or the works table), shared by the stand's renderer and the song's screen so the marks
 * fall on the metal: its centre on the top, the way it runs (from the stand's side, away), and across. The piece lies
 * face up, crown away from the stand; a mark's (x, z) in -1..1 is across and along it.
 */
record ForgeFrame(Vec3 top, Vec3 along, Vec3 across) {
    /** Half the worked area: across, along (blocks). */
    static final double HALF_ACROSS = 0.26, HALF_ALONG = 0.32;
    /** The laid piece's scale, and how high the struck face stands over the anvil. */
    static final float SCALE = 0.7F, FACE = 0.11F;

    static ForgeFrame of(BlockPos stand, BlockPos work) {
        double dx = work.getX() - stand.getX(), dz = work.getZ() - stand.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-3) {
            dx = 0;
            dz = 1;
            len = 1;
        }
        Vec3 along = new Vec3(dx / len, 0, dz / len);
        Vec3 across = new Vec3(-along.z, 0, along.x);
        return new ForgeFrame(new Vec3(work.getX() + 0.5, work.getY() + 1.0, work.getZ() + 0.5), along, across);
    }

    /** A mark on the struck face, in the world. */
    Vec3 mark(float x, float z) {
        return top.add(across.scale(x * HALF_ACROSS)).add(along.scale(z * HALF_ALONG)).add(0, FACE, 0);
    }

    /** Where each piece's middle sits in its bones' model space (pixels down from the neck): to centre it on the anvil. */
    static float middle(ArmorType piece) {
        return switch (piece) {
            case HELMET -> -4F;
            case CHESTPLATE -> 6F;
            case LEGGINGS -> 15F;
            default -> 21F;
        };
    }

    static int strikes(ArmorType piece, boolean byHand) {
        return ForgeSong.strikes(piece, byHand);
    }
}
