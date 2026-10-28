package dev.awaken.hearth.guide;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.block.HearthAnchorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.UUID;

/** GameTests for SPEC §28.11: Oren's cottage goes up over a day and catches up honestly; the Anchor is a real light. */
public final class CottageBuildTests {
    private CottageBuildTests() {}

    public static void register() {
        AwakenTests.registerIsolated("hearth_cottage_builds_over_a_day", 400, h -> {
            ServerLevel level = h.getLevel();
            BlockPos o = h.absolutePos(BlockPos.ZERO);
            int baseY = o.getY() + 120, cx = o.getX() & ~15, cz = o.getZ() & ~15;
            // a patch of grass high above the test floor; the whole cottage (overhangs, path, chimney) fits in the chunk
            clear(level, cx, baseY, cz);
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) level.setBlock(new BlockPos(cx + x, baseY, cz + z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            BlockPos origin = new BlockPos(cx + 2, baseY, cz + 4);
            UUID owner = UUID.randomUUID();
            HearthRecord rec = new HearthRecord(owner, "test", new BlockPos(cx + 14, baseY + 1, cz + 14), HearthService.OVERWORLD);
            int[] ground = new int[Cottage.WIDTH * Cottage.DEPTH];
            Cottage.Site site = new Cottage.Site(origin, Rotation.NONE, baseY, ground, List.of(), CottagePlan.MEADOW);
            CottageBuild b = Cottage.begin(level, rec, owner, site);
            // someone standing where the front wall's stone course goes
            BlockPos wallCell = CottagePlan.at(origin, Rotation.NONE, 2, 1, 0);
            ArmorStand stand = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
            stand.setNoGravity(true);
            stand.setPos(wallCell.getX() + 0.5, wallCell.getY(), wallCell.getZ() + 0.5);
            level.addFreshEntity(stand);
            Cottage.advance(level, rec);
            h.assertTrue(b.next <= 2 && rec.cottage == null, "nothing much stands at the start: " + b.next + " placed");

            // half a day away (a logout, the chunks unloaded): it catches up to there in a few steps, no further
            Cottage.hurry(rec, Cottage.DURATION / 2);
            for (int i = 0; i < 30; i++) Cottage.advance(level, rec);
            BlockState floor = level.getBlockState(CottagePlan.at(origin, Rotation.NONE, 4, 0, 4));
            BlockState post = level.getBlockState(CottagePlan.at(origin, Rotation.NONE, 0, 3, 0));
            BlockState ridge = level.getBlockState(CottagePlan.at(origin, Rotation.NONE, 4, CottagePlan.roofY(4), 4));
            BlockState bedHead = level.getBlockState(CottagePlan.at(origin, Rotation.NONE, 2, 1, 7));
            h.assertTrue(Math.abs(b.progress() - 0.5f) < 0.01f, "half the day's work is done: " + b.progress());
            h.assertTrue(floor.is(Blocks.SPRUCE_PLANKS) && post.is(BlockTags.LOGS), "foundation and frame stand by midday: " + floor + ", " + post);
            h.assertTrue(ridge.isAir() && bedHead.isAir(), "but no roof and no furniture yet: " + ridge + ", " + bedHead);
            h.assertTrue(!b.held.isEmpty() && level.getBlockState(wallCell).isAir(), "the blocks where someone stands wait: held " + b.held);

            // the save round-trips mid-build (a restart): the same plan comes back and goes on from where it was
            var saved = CottageBuild.CODEC.encodeStart(NbtOps.INSTANCE, b).getOrThrow();
            CottageBuild back = CottageBuild.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
            h.assertTrue(back.next == b.next && back.worked == b.worked && back.held.equals(b.held) && back.rot == b.rot
                    && back.palette.equals(b.palette) && back.top(4, -2) == b.top(4, -2), "the build saves and loads as it was");
            rec.cottageBuild = back;

            // they step away and the rest of the day passes: Oren finishes, the held block included
            stand.discard();
            Cottage.hurry(rec, Cottage.DURATION);
            h.succeedWhen(() -> {
                Cottage.advance(level, rec);
                h.assertTrue(rec.cottage != null && rec.cottageBuild == null, "the cottage is finished");
                h.assertTrue(level.getBlockState(wallCell).is(Blocks.COBBLESTONE) || level.getBlockState(wallCell).is(Blocks.MOSSY_COBBLESTONE),
                        "the held block went in once they moved: " + level.getBlockState(wallCell));
                h.assertTrue(level.getBlockState(CottagePlan.at(origin, Rotation.NONE, 2, 1, 7)).is(BlockTags.BEDS)
                        && level.getBlockState(CottagePlan.at(origin, Rotation.NONE, 4, 1, 0)).is(BlockTags.DOORS), "bed and door are in");
                clear(level, cx, baseY, cz);
            });
        });

        AwakenTests.registerIsolated("hearth_anchor_light", 200, h -> {
            ServerLevel level = h.getLevel();
            BlockPos o = h.absolutePos(BlockPos.ZERO);
            BlockPos at = new BlockPos((o.getX() & ~15) + 8, o.getY() + 100, (o.getZ() & ~15) + 8);
            try {
                for (int tier = 1; tier <= 5; tier++) {
                    BlockState s = HearthContent.HEARTH_ANCHOR.get().atTier(tier);
                    h.assertTrue(s.getLightEmission() == 15, "Tier " + tier + " burns at light 15, was " + s.getLightEmission());
                }
                level.setBlock(at, HearthContent.HEARTH_ANCHOR.get().atTier(1), 3);
                AwakenTests.awaitLight(h, at.offset(-8, -8, -8), at.offset(8, 8, 8));
                int beside = level.getBrightness(LightLayer.BLOCK, at.north()), near = level.getBrightness(LightLayer.BLOCK, at.offset(3, -1, 0));
                h.assertTrue(beside == 14 && near == 11, "the light reaches the ground round it: beside " + beside + ", 3 out and down " + near);
                h.assertFalse(level.getBlockState(at).isCollisionShapeFullBlock(level, at), "a shaped brazier, not a full cube");
                h.assertTrue(level.getBlockState(at).getValue(HearthAnchorBlock.TIER) == 1, "placed at Tier I");
            } finally {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
            }
            h.succeed();
        });
    }

    /** The test chunk back to air from under the grass to above the chimney. */
    private static void clear(ServerLevel level, int cx, int y, int cz) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int dy = -1; dy <= CottagePlan.TOP + 2; dy++) {
            if (!level.getBlockState(m.set(cx + x, y + dy, cz + z)).isAir()) level.setBlock(m, Blocks.AIR.defaultBlockState(), 2);
        }
    }
}
