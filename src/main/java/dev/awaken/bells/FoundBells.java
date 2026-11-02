package dev.awaken.bells;

import dev.awaken.Awaken;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.kin.KinWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Bells that lie where they were won (SPEC §43.16): set one down ({@link #place}), lift one ({@link #lift}), and the
 * Lampwing's old roost, the Nook Bell hanging under a branch near a new home's fire (§4.6 of the plan).
 */
final class FoundBells {
    private FoundBells() {}

    /** Where the first fire's Lampwing watches from, off the Anchor (kin.KinHome.lessonTick), and how far round it a tree is looked for. */
    static final int ROOST_DX = 12, ROOST_DZ = 9, ROOST_REACH = 24;

    /** Sets bell {@code no} down at {@code pos}. {@code claimants} empty: anyone may lift it. False if the spot isn't free. */
    static boolean place(ServerLevel level, BlockPos pos, int no, String where, List<UUID> claimants, long anchor, boolean hanging, boolean glow) {
        BlockState now = level.getBlockState(pos);
        if (!now.isAir() && !now.canBeReplaced()) return false;
        level.setBlock(pos, BellContent.FOUND_BELL.get().defaultBlockState().setValue(FoundBellBlock.HANGING, hanging).setValue(FoundBellBlock.GLOW, glow), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(pos) instanceof FoundBellEntity be)) return false;
        be.set(no, where, claimants, anchor);
        return true;
    }

    /** A found bell used: it's yours if it was left for you (and it's awake), and it rings out clear. */
    static void lift(ServerLevel level, BlockPos pos, ServerPlayer p) {
        if (!(level.getBlockEntity(pos) instanceof FoundBellEntity be) || be.no < 0) return;
        BellCatalog.Def d = BellCatalog.of(be.no);
        if (be.anchor != 0) {
            Optional<HearthRecord> h = HearthService.data(level.getServer()).home(p.getUUID());
            if (h.isEmpty() || h.get().pos.asLong() != be.anchor) {
                Bells.say(p, "[It's hanging by someone else's fire. It's theirs.]");
                return;
            }
            if (asleep(level, be.anchor)) {
                Bells.say(p, "[Something's asleep in it.]");
                return;
            }
        }
        if (!be.claimants.isEmpty() && !be.claimants.contains(p.getUUID())) {
            Bells.say(p, "[This one wasn't left for you. It's for the hunter who found it.]");
            return;
        }
        if (be.lifted.add(p.getUUID())) {
            if (Bells.has(p, d.id())) Bells.say(p, "[You've already got " + d.name().replaceFirst("^The ", "the ") + ". This one stays for whoever it's for.]");
            else {
                Bells.ring(p, d, be.where);
                level.playSound(null, pos, BellContent.note(d.no()), SoundSource.BLOCKS, 2F, 1F);
                level.sendParticles(ParticleTypes.WHITE_ASH, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 24, 0.35, 0.35, 0.35, 0.02);
            }
            be.setChanged();
        }
        if (be.done()) {
            if (be.anchor != 0) {
                BellWorld w = BellWorld.get(level.getServer());
                BellWorld.Home home = w.peek(BlockPos.of(be.anchor));
                if (home != null && pos.equals(home.roost)) {
                    home.roost = null;
                    w.setDirty();
                }
            }
            level.removeBlock(pos, false);
        }
    }

    /** Nothing lives at that home yet: the Lampwing is still asleep in its old bell. */
    static boolean asleep(ServerLevel level, long anchor) {
        return KinWorld.get(level.getServer()).at(BlockPos.of(anchor)).isEmpty();
    }

    // ---- the Lampwing's old roost (the Nook Bell) --------------------------------------------------------------------

    /**
     * Each 5 s for a home's owner nearby: the Lampwing's old bell is hung in the nearest tree once (not for a home whose
     * owner holds the Nook already), it glows while something sleeps in it, and the day a beast comes to live here it's
     * said to be the home's to take.
     */
    static void roost(ServerPlayer p, HearthRecord h) {
        if (!(p.level() instanceof ServerLevel level) || !HearthService.OVERWORLD.equals(h.dimension)) return;
        BellWorld w = BellWorld.get(level.getServer());
        BellWorld.Home home = w.home(h.pos);
        BellCatalog.Def nook = BellCatalog.of("nook");
        if (!home.roosted) {
            if (!h.owner.equals(p.getUUID()) || Bells.has(p, "nook") || home.hung.contains(nook.no())
                    || p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(h.pos)) > 32 * 32) return;
            BlockPos at = hangRoost(level, h.pos.offset(ROOST_DX, 0, ROOST_DZ), ROOST_REACH, h.pos.asLong());
            if (at == null) return;
            home.roost = at;
            home.roosted = true;
            w.setDirty();
            Awaken.LOG.info("[bells] the Lampwing's old bell hangs at {} for the home at {}", at.toShortString(), h.pos.toShortString());
            return;
        }
        if (home.roost == null || !level.isLoaded(home.roost)) return;
        BlockState s = level.getBlockState(home.roost);
        if (!s.is(BellContent.FOUND_BELL.get())) {
            home.roost = null;
            w.setDirty();
            return;
        }
        boolean glow = asleep(level, h.pos.asLong());
        if (s.getValue(FoundBellBlock.GLOW) != glow) level.setBlock(home.roost, s.setValue(FoundBellBlock.GLOW, glow), Block.UPDATE_ALL);
        if (!glow && h.belongs(p.getUUID()) && !Bells.has(p, "nook")) {
            BellBook b = Bells.book(p);
            if (b.once("roost_free")) {
                Bells.save(p, b);
                Bells.say(p, "[The Lampwing's left its old bell in the tree. It's yours.]");
            }
        }
    }

    /**
     * Hangs the Nook Bell under a branch of the tree nearest {@code centre} within {@code reach} (a leaves block with air
     * under it, three or more blocks up), or from a dead post at {@code centre} when there's no tree. Null if nothing
     * round it is loaded.
     */
    static @Nullable BlockPos hangRoost(ServerLevel level, BlockPos centre, int reach, long anchor) {
        BlockPos best = null;
        long bd = Long.MAX_VALUE;
        boolean any = false;
        for (int dx = -reach; dx <= reach; dx++)
            for (int dz = -reach; dz <= reach; dz++) {
                int x = centre.getX() + dx, z = centre.getZ() + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                any = true;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                for (int y = top; y >= ground + 3; y--) {
                    BlockPos leaf = new BlockPos(x, y, z);
                    if (!level.getBlockState(leaf).is(BlockTags.LEAVES) || !level.getBlockState(leaf.below()).isAir()) continue;
                    long d = (long) dx * dx + (long) dz * dz;
                    if (d < bd) {
                        bd = d;
                        best = leaf.below();
                    }
                    break;
                }
            }
        BellCatalog.Def nook = BellCatalog.of("nook");
        String where = "in the tree by your fire, where the Lampwing slept in it";
        if (best != null) return place(level, best, nook.no(), where, List.of(), anchor, true, true) ? best : null;
        if (!any || !level.hasChunkAt(centre)) return null;
        // no tree near: an old dead post at the edge of where the trees would be, an arm out, the bell under it
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre.getX(), centre.getZ());
        BlockPos foot = new BlockPos(centre.getX(), ground, centre.getZ());
        BlockState post = Blocks.STRIPPED_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        for (int y = 0; y < 4; y++) level.setBlock(foot.above(y), post, Block.UPDATE_ALL);
        level.setBlock(foot.above(3).east(), Blocks.STRIPPED_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X), Block.UPDATE_ALL);
        BlockPos at = foot.above(2).east();
        return place(level, at, nook.no(), where + ", on an old post", List.of(), anchor, true, true) ? at : null;
    }
}
