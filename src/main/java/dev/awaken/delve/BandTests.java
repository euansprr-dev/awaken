package dev.awaken.delve;

import dev.awaken.Awaken;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.delve.api.Bands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The deep's bands and its noise (SPEC §34.5): the band by height and who lives in it (the Hollows' spawn rule asks
 * {@link Bands#allows}); a strike under the rock calls a hunter that hears within 24, a Hushed pick's doesn't.
 */
final class BandTests {
    private BandTests() {}

    static void register() {
        AwakenTests.register("delve_bands", 20, BandTests::bands);
        AwakenTests.registerIsolated("delve_strike_noise", 60, BandTests::noise);
    }

    private static void bands(GameTestHelper h) {
        h.assertTrue(Bands.of(64) == Bands.Band.SHALLOWS && Bands.of(40) == Bands.Band.SHALLOWS, "y 40 and up: the Shallows");
        h.assertTrue(Bands.of(39) == Bands.Band.MIDDLE && Bands.of(0) == Bands.Band.MIDDLE, "0 to 40: the Middle Dark");
        h.assertTrue(Bands.of(-1) == Bands.Band.DEEP && Bands.of(-60) == Bands.Band.DEEP, "below 0: the Deep");
        h.assertFalse(Bands.allows("hushling", 50), "no hushlings in the Shallows");
        h.assertTrue(Bands.allows("hushling", 20) && Bands.allows("hushling", -30), "hushlings from the Middle Dark down");
        h.assertTrue(Bands.allows("drip_lurker", 60) && Bands.allows("chime_cricket", 60), "lurkers and crickets in the Shallows");
        h.succeed();
    }

    private static void noise(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(0, 24, 0));
        for (int dx = -2; dx <= 18; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = -1; dy <= 4; dy++) {
            boolean air = dx >= 0 && dx <= 16 && Math.abs(dz) <= 1 && dy >= 0 && dy <= 2;
            level.setBlock(o.offset(dx, dy, dz), air ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        var type = BuiltInRegistries.ENTITY_TYPE.getValue(Awaken.id("hushling"));
        Mob m = (Mob) type.create(level, EntitySpawnReason.COMMAND);
        h.assertTrue(m != null, "a hushling");
        m.snapTo(o.getX() + 14.5, o.getY(), o.getZ() + 0.5, 0F, 0F);
        level.addFreshEntity(m);
        m.setOnGround(true);   // fresh, it hasn't ticked onto the floor yet (a mob in the air plans no path)
        ServerPlayer loud = h.makeMockServerPlayerInLevel(), quiet = h.makeMockServerPlayerInLevel();
        quiet.setItemInHand(InteractionHand.MAIN_HAND, hushed(level));
        BlockPos struck = o.offset(1, 1, 0);
        h.assertTrue(StrikeNoise.heard(level, struck, quiet).isEmpty(), "a Hushed pick's strike makes no noise");
        h.assertTrue(StrikeNoise.heard(level, struck, loud).contains(m), "a strike calls the hushling 13 off");
        h.assertTrue(m.getNavigation().isInProgress(), "and it comes");
        h.assertTrue(StrikeNoise.heard(level, struck, loud).isEmpty(), "the next strike, a moment later, is the same call");
        m.discard();
        h.succeed();
    }

    private static ItemStack hushed(ServerLevel level) {
        ItemStack pick = new ItemStack(DelveContent.WAKING_PICK.get());
        PickVerses.set(pick, PickVerses.Verse.HUSH.ordinal(), level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK));
        return pick;
    }
}
