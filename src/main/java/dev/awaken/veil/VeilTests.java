package dev.awaken.veil;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.veil.block.GhostDoorBlock;
import dev.awaken.veil.block.GhostLeverBlock;
import dev.awaken.veil.world.TempleBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** GameTests for the Veil: maths, the restore state machine, the Body, the touch rule, placement, the no-reveal rule. */
final class VeilTests {
    private VeilTests() {}

    static void register() {
        AwakenTests.register("veil_cord_maths", 40, VeilTests::cordMaths);
        AwakenTests.register("veil_restore_after_logout", 100, VeilTests::restoreAfterLogout);
        AwakenTests.register("veil_body_damage_warns", 100, VeilTests::bodyDamageWarns);
        AwakenTests.register("veil_interaction_rule", 100, VeilTests::interactionRule);
        AwakenTests.register("veil_spirit_placement", 40, VeilTests::spiritPlacement);
        AwakenTests.register("veil_no_reveal_near_hearth", 60, VeilTests::noReveal);
        AwakenTests.register("veil_ghost_door_and_solidity", 60, VeilTests::ghostDoor);
        AwakenTests.register("veil_gate_puzzles_solvable", 40, VeilTests::puzzlesSolvable);
        AwakenTests.register("veil_temple_layout", 40, VeilTests::templeLayout);
        AwakenTests.register("veil_stillpoint_dimension_decodes", 40, VeilTests::dimensionDecodes);
        AwakenTests.register("veil_meditation_flow", 100, VeilTests::meditationFlow);
    }

    private static ServerPlayer spiritAt(GameTestHelper h, BlockPos at) {
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        sp.connection.markClientLoaded(); // otherwise a fresh player cannot be hurt
        ServerLevel level = h.getLevel();
        sp.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), 0F, 0F, true);
        return sp;
    }

    private static void cordMaths(GameTestHelper h) {
        h.assertTrue(Cord.radius(0, 0) == 32, "cord at SPI 0 is 32");
        h.assertTrue(Cord.radius(5, 0) == 62, "cord at SPI 5 is 32 + 6x5 = 62");
        h.assertTrue(Cord.radius(5, 96) == 158, "the cord perk adds blocks");
        h.assertTrue(Cord.radius(500, 0) == 1024, "cord caps at 1024");
        h.assertTrue(Cord.timeTicks(5) == 45 * 20, "Veil time at SPI 5 is 45 s");
        h.assertTrue(Cord.timeTicks(10) == 60 * 20, "Veil time at SPI 10 is 60 s");
        h.assertTrue(Cord.awareness(5, 5, 0) == 31, "Awareness at PER 5 / SPI 5 is 31");
        h.assertTrue(Cord.awareness(30, 30, 0) == 106, "Awareness grows with PER and SPI");
        h.assertTrue(Cord.awareness(500, 500, 0) == Cord.MAX_AWARENESS, "Awareness caps");
        h.assertTrue(Cord.secretSense(8, 0) == 6, "secret sense is 4 + PER/4");
        h.assertTrue(Math.abs(Cord.tension(31, 62) - 0.5) < 1e-9, "tension is distance over radius");
        h.assertTrue(Cord.pullBackDistance(62) < 62 && Cord.pullBackDistance(62) > 59, "pull back lands just inside the edge");
        h.succeed();
    }

    private static void restoreAfterLogout(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos base = h.absolutePos(BlockPos.ZERO).above(2);
        ServerPlayer sp = spiritAt(h, base);
        GameType before = sp.gameMode.getGameModeForPlayer();
        h.assertTrue(Projection.start(sp, true, null), "projection should start");
        h.assertTrue(Veil.isSpirit(sp), "player should be a spirit");
        h.assertTrue(sp.gameMode.getGameModeForPlayer() == GameType.SPECTATOR, "spirit is spectator-backed");
        h.assertTrue(sp.getData(VeilContent.PROJECTION.get()).active(), "projection state is saved");
        Projection.Spirit s = Projection.live(sp);
        h.assertTrue(s != null && s.body() != null && s.body().isAlive(), "a Body stays behind");
        Mannequin body = s.body();
        Vec3 bodyPos = body.position();
        sp.teleportTo(level, base.getX() + 6.5, base.getY() + 3, base.getZ() + 0.5, Set.of(), 0F, 0F, true);

        // logout: back in the body before saving
        Projection.end(sp, Projection.EndReason.LOGOUT);
        h.assertFalse(Veil.isSpirit(sp), "no longer a spirit after logout");
        h.assertTrue(sp.gameMode.getGameModeForPlayer() == before, "old game mode restored");
        h.assertTrue(sp.position().distanceTo(bodyPos) < 0.5, "player stands where the Body was");
        h.assertTrue(body.isRemoved(), "the Body is gone");
        h.assertFalse(sp.getData(VeilContent.PROJECTION.get()).active(), "saved state cleared");

        // crash: the live half is lost, the saved half brings the player home on the next login
        h.assertTrue(Projection.start(sp, true, null), "second projection starts");
        Mannequin body2 = Projection.live(sp).body();
        Vec3 bodyPos2 = body2.position();
        sp.teleportTo(level, base.getX() + 3.5, base.getY() + 5, base.getZ() + 3.5, Set.of(), 0F, 0F, true);
        Projection.LIVE.remove(sp.getUUID());
        h.assertTrue(sp.getData(VeilContent.PROJECTION.get()).active(), "state survives the crash");
        Projection.restoreFromState(sp, false);
        h.assertTrue(sp.gameMode.getGameModeForPlayer() == before, "game mode restored after a crash");
        h.assertTrue(sp.position().distanceTo(bodyPos2) < 0.5, "back at the Body after a crash");
        h.assertTrue(body2.isRemoved(), "the orphaned Body is removed");
        h.assertFalse(sp.getData(VeilContent.PROJECTION.get()).active(), "state cleared after restore");
        level.getServer().getPlayerList().remove(sp);
        h.succeed();
    }

    private static void bodyDamageWarns(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos base = h.absolutePos(BlockPos.ZERO).above(2);
        ServerPlayer sp = spiritAt(h, base);
        h.assertTrue(Projection.start(sp, true, null), "projection should start");
        Projection.Spirit s = Projection.live(sp);
        Mannequin body = s.body();
        h.assertTrue(body != null, "Body exists");
        body.hurtServer(level, level.damageSources().magic(), 3F);
        h.assertTrue(s.warningsSent == 1, "a hit Body sends one warning");
        body.hurtServer(level, level.damageSources().magic(), 1000F);
        h.assertTrue(body.isDeadOrDying(), "the Body died");
        Projection.tick(sp, s); // mock players do not tick on their own
        h.assertFalse(Veil.isSpirit(sp), "the spirit is pulled home when the Body dies");
        h.assertTrue(sp.isDeadOrDying(), "and dies with it, as a normal death");
        level.getServer().getPlayerList().remove(sp);
        h.succeed();
    }

    private static void interactionRule(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos base = h.absolutePos(BlockPos.ZERO).above(2);
        BlockPos stone = base.offset(2, 0, 0);
        BlockPos leverPos = base.offset(2, 0, 2);
        BlockPos far = base.offset(20, 0, 0);
        level.setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(leverPos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(far.below(), Blocks.STONE.defaultBlockState(), 3);
        BlockState lever = VeilContent.GHOST_LEVER.get().defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR)
                .setValue(LeverBlock.FACING, Direction.NORTH);
        level.setBlock(leverPos, lever, 3);
        level.setBlock(far, lever, 3);
        ServerPlayer sp = spiritAt(h, base);
        h.assertTrue(VeilInteract.use(sp, leverPos) == VeilInteract.Result.NOT_SPIRIT, "the living cannot use the Veil touch");
        h.assertTrue(Projection.start(sp, true, null), "projection should start");
        h.assertTrue(VeilInteract.use(sp, stone) == VeilInteract.Result.NOT_BLUE, "a spirit's hand goes through stone");
        h.assertTrue(VeilInteract.use(sp, far) == VeilInteract.Result.TOO_FAR, "blue things still need to be in reach");
        h.assertTrue(VeilInteract.use(sp, leverPos) == VeilInteract.Result.ACCEPTED, "a spirit can pull a ghost lever");
        h.assertTrue(level.getBlockState(leverPos).getValue(GhostLeverBlock.POWERED), "the ghost lever is now on");
        Projection.end(sp, Projection.EndReason.COMMAND);
        level.getServer().getPlayerList().remove(sp);
        h.succeed();
    }

    private static void spiritPlacement(GameTestHelper h) {
        long seed = 0x5EEDL;
        int differs = 0;
        for (int cx = -5; cx <= 5; cx++) {
            for (int cz = -5; cz <= 5; cz++) {
                int[] a = SpiritPlacer.spot(seed, cx, cz), b = SpiritPlacer.spot(seed, cx, cz);
                h.assertTrue(a[0] == b[0] && a[1] == b[1], "same seed and cell, same spot");
                h.assertTrue(SpiritPlacer.haunted(seed, cx, cz) == SpiritPlacer.haunted(seed, cx, cz), "haunting is fixed");
                h.assertTrue(Math.floorDiv(a[0], SpiritPlacer.CELL) == cx && Math.floorDiv(a[1], SpiritPlacer.CELL) == cz, "spot stays in its cell");
                int[] other = SpiritPlacer.spot(seed + 1, cx, cz);
                if (other[0] != a[0] || other[1] != a[1]) differs++;
                List<String> cands = List.of("awaken:a", "awaken:b", "awaken:c");
                String pick = SpiritPlacer.choose(seed, cx, cz, cands);
                h.assertTrue(pick != null && pick.equals(SpiritPlacer.choose(seed, cx, cz, cands)), "the same spirit is chosen every time");
            }
        }
        h.assertTrue(differs > 60, "another world puts spirits elsewhere");
        if (!SignalLines.SPIRITS.isEmpty()) {
            h.assertTrue(Spirits.forBiome("minecraft:plains").contains("awaken:iri_the_cartographer"), "plains have Iri");
            h.assertTrue(Spirits.forBiome("minecraft:dark_forest").contains("awaken:kael_twice_bound"), "an exact biome wins");
            h.assertFalse(Spirits.forBiome("minecraft:birch_forest").isEmpty(), "a birch forest still finds the forest's spirit");
            h.assertTrue(Spirits.forBiome("minecraft:the_void").isEmpty(), "no spirit in the void");
        }
        h.succeed();
    }

    private static void noReveal(GameTestHelper h) {
        BlockPos hearth = new BlockPos(100, 64, 100);
        RevealFilter f = RevealFilter.of(List.of(hearth));
        h.assertFalse(f.allowed(new BlockPos(120, 64, 120)), "nothing near another player's Hearth is revealed");
        h.assertFalse(f.allowed(new BlockPos(100, 64, 147)), "47 blocks away is still hidden");
        h.assertTrue(f.allowed(new BlockPos(100, 64, 149)), "49 blocks away is fine");
        h.assertTrue(RevealFilter.OPEN.allowed(hearth), "no bases, nothing hidden");
        // a real other player with a bed: the filter built for a viewer hides it; built for its owner, it does not
        ServerLevel level = h.getLevel();
        BlockPos bed = h.absolutePos(BlockPos.ZERO).offset(10, 1, 10);
        ServerPlayer owner = h.makeMockServerPlayerInLevel();
        ServerPlayer viewer = h.makeMockServerPlayerInLevel();
        owner.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(level.dimension(), bed, 0F, 0F), true), false);
        RevealFilter forViewer = RevealFilter.build(level, viewer, Vec3.atCenterOf(bed), 32);
        h.assertTrue(forViewer.zones().contains(bed), "another player's bed is a no-reveal zone");
        h.assertFalse(forViewer.allowed(bed.offset(5, 0, 5)), "ores next to another player's bed stay hidden");
        RevealFilter forOwner = RevealFilter.build(level, owner, Vec3.atCenterOf(bed), 32);
        h.assertFalse(forOwner.zones().contains(bed), "your own bed is not hidden from you");
        level.getServer().getPlayerList().remove(owner);
        level.getServer().getPlayerList().remove(viewer);
        h.succeed();
    }

    private static void ghostDoor(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos base = h.absolutePos(BlockPos.ZERO).above(3);
        BlockState closed = VeilContent.GHOST_DOOR.get().defaultBlockState();
        for (int y = 0; y < 3; y++) level.setBlock(base.above(y), closed, 3);
        h.assertTrue(VeilSolid.isSolidForSpirit(level.getBlockState(base)), "a closed ghost door stops a spirit");
        List<BlockPos> pane = GhostDoorBlock.setPane(level, base.above(), true);
        h.assertTrue(pane.size() == 3, "the whole pane moves together");
        h.assertFalse(VeilSolid.isSolidForSpirit(level.getBlockState(base.above(2))), "an open ghost door lets a spirit through");
        h.assertTrue(VeilSolid.isSolidForSpirit(VeilContent.VEIL_STONE.get().defaultBlockState()), "Veil stone is solid to spirits");
        h.assertFalse(VeilSolid.isSolidForSpirit(Blocks.STONE.defaultBlockState()), "stone is not");
        GhostDoorBlock.setPane(level, base, false);
        Vec3 from = Vec3.atBottomCenterOf(base).add(-2, 0, 0), to = Vec3.atBottomCenterOf(base).add(2, 0, 0);
        h.assertTrue(VeilSolid.blocked(level, from, to), "a spirit cannot fly through a closed door");
        h.succeed();
    }

    private static void puzzlesSolvable(GameTestHelper h) {
        for (long seed = 1; seed < 200; seed++) {
            VeilGates.Layout l = VeilGates.Layout.of((int) (seed % 7), seed * 0x9E3779B97F4A7C15L);
            VeilGates.Layout again = VeilGates.Layout.of((int) (seed % 7), seed * 0x9E3779B97F4A7C15L);
            h.assertTrue(l.levers().equals(again.levers()) && java.util.Arrays.equals(l.target(), again.target()), "layouts are deterministic");
            int n = l.levers().size();
            boolean[] state = new boolean[n];
            boolean anyTarget = false;
            for (boolean t : l.target()) anyTarget |= t;
            h.assertTrue(anyTarget, "a puzzle is never already solved");
            // solve left to right: lever i flips itself and (if coupled) i+1
            for (int i = 0; i < n; i++) {
                if (state[i] != l.target()[i]) {
                    state[i] = !state[i];
                    if (l.coupled()[i] && i + 1 < n) state[i + 1] = !state[i + 1];
                }
            }
            h.assertTrue(java.util.Arrays.equals(state, l.target()), "every Veil Gate puzzle can be solved");
        }
        h.succeed();
    }

    private static void meditate(ServerPlayer sp, int ticks) {
        for (int i = 0; i < ticks; i++) {
            sp.setOnGround(true);
            Meditation.tick(sp);
        }
    }

    private static void meditationFlow(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos base = h.absolutePos(BlockPos.ZERO).above(2);
        ServerPlayer sp = spiritAt(h, base);
        dev.awaken.progression.Progression.awaken(sp);
        sp.setShiftKeyDown(true);
        long before = dev.awaken.progression.Progression.profile(sp).counter(dev.awaken.progression.Counters.MEDITATE_TICKS);
        Meditation.input(sp, true, false);
        meditate(sp, Meditation.STEP_OUT);
        h.assertTrue(Meditation.ticks(sp.getUUID()) == Meditation.STEP_OUT, "ten still seconds of meditation");
        h.assertTrue(Veil.isMeditating(sp), "meditating");
        h.assertTrue(Veil.energyRegenMultiplier(sp) >= 2.0, "meditation doubles energy regen");
        h.assertTrue(dev.awaken.progression.Progression.profile(sp).counter(dev.awaken.progression.Counters.MEDITATE_TICKS) > before,
                "meditate_ticks counts");
        Meditation.input(sp, false, true); // stood up
        h.assertFalse(Veil.isSpirit(sp), "standing up only ends the meditation");
        h.assertFalse(Veil.isMeditating(sp), "and it has ended");

        Meditation.input(sp, true, false);
        meditate(sp, 50);
        sp.teleportTo(level, base.getX() + 2.5, base.getY(), base.getZ() + 0.5, Set.of(), 0F, 0F, true);
        meditate(sp, 1);
        h.assertTrue(Meditation.ticks(sp.getUUID()) == 0, "moving breaks the stillness");
        meditate(sp, Meditation.STEP_OUT);
        Meditation.input(sp, false, false); // let go of Focus after ten seconds
        h.assertTrue(Veil.isSpirit(sp), "letting go of Focus after ten seconds steps out of the body");
        Projection.end(sp, Projection.EndReason.COMMAND);
        h.assertFalse(Veil.isSpirit(sp), "back in the body");
        level.getServer().getPlayerList().remove(sp);
        h.succeed();
    }

    /** The GameTest server never creates datapack dimensions, so decode ours against the live registries instead. */
    private static void dimensionDecodes(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var id = net.minecraft.resources.Identifier.fromNamespaceAndPath("awaken", "dimension/stillpoint.json");
        try (var reader = server.getResourceManager().openAsReader(id)) {
            var json = com.google.gson.JsonParser.parseReader(reader);
            var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, server.registryAccess());
            var stem = net.minecraft.world.level.dimension.LevelStem.CODEC.parse(ops, json).getOrThrow();
            h.assertTrue(stem.generator() instanceof dev.awaken.veil.world.StillpointGenerator, "the Stillpoint uses its own generator");
            h.assertTrue(stem.type().value().hasFixedTime(), "it is always dawn in the Stillpoint");
            h.assertTrue(stem.type().value().hasSkyLight(), "the Stillpoint has a sky");
        } catch (java.io.IOException e) {
            h.fail("missing dimension json: " + e.getMessage());
            return;
        }
        h.succeed();
    }

    private static void templeLayout(GameTestHelper h) {
        var t = TempleBuilder.blocks();
        h.assertTrue(t.get(TempleBuilder.DOOR_OUT.asLong()).is(VeilContent.DOOR_OUT.get()), "the Out door faces the sunrise");
        h.assertTrue(t.get(TempleBuilder.DOOR_IN.asLong()).is(VeilContent.DOOR_IN.get()), "the In door waits in the west");
        h.assertTrue(t.containsKey(TempleBuilder.SEAT.asLong()), "there is a seat at the centre");
        h.assertTrue(t.get(new BlockPos(0, TempleBuilder.FLOOR, 0).asLong()) != null, "the temple has a floor");
        List<BlockPos> lanterns = new ArrayList<>();
        for (var e : t.long2ObjectEntrySet()) if (e.getValue().is(Blocks.LANTERN)) lanterns.add(BlockPos.of(e.getLongKey()));
        h.assertTrue(lanterns.size() >= 8, "amber lanterns light the ring");
        h.succeed();
    }
}
