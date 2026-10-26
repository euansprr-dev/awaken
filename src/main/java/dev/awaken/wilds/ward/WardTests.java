package dev.awaken.wilds.ward;

import dev.awaken.above.AboveBlocks;
import dev.awaken.above.ruin.PrecursorRuinPiece;
import dev.awaken.above.ruin.RuinKind;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.api.WardHooks;
import dev.awaken.core.test.AwakenTests;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** The ward's GameTest (SPEC §17): one guard over every ward rule, through the real structure-reference path. */
public final class WardTests {
    private WardTests() {}

    public static void register() {
        AwakenTests.registerIsolated("wilds_ward_rules", 200, h -> {
            ServerLevel level = h.getLevel();
            BlockPos at = h.absolutePos(new BlockPos(1, 2, 1));
            AwakenTests.Live live = AwakenTests.survivalPlayer(h, "warded", new BlockPos(1, 2, 1));
            ServerPlayer p = live.player();
            Structure vault = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(RuinKind.VAULT.structure).value();
            ChunkPos cp = ChunkPos.containing(at);
            LevelChunk chunk = level.getChunk(cp.x(), cp.z());
            Map<Structure, StructureStart> starts = new HashMap<>(chunk.getAllStarts());
            Map<Structure, LongSet> refs = new HashMap<>(chunk.getAllReferences());
            HearthHooks.Lookup hearths = HearthHooks.installed();
            try {
                // a vault four blocks east and south: the player stands inside its ward (its box grown by MARGIN)
                PrecursorRuinPiece piece = new PrecursorRuinPiece(RuinKind.VAULT, at.offset(4, -1, 4), 0, 7L, 1);
                chunk.setStartForStructure(vault, new StructureStart(vault, cp, 0, new PiecesContainer(List.of(piece))));
                chunk.addReferenceForStructure(vault, cp.pack());
                Optional<WardHooks.Ward> ward = WardHooks.at(level, at);
                h.assertTrue(ward.isPresent() && ward.get().id().equals("ruin:" + cp.x() + "," + cp.z()), "a vault's ward reaches past its walls: " + ward);
                h.assertTrue(WardHooks.at(level, at.above(60)).isEmpty(), "and ends " + Wards.MARGIN + " blocks out");

                // a placed block crumbles after three seconds and its item comes home
                BlockPos spot = at.east();
                BlockState stone = Blocks.STONE.defaultBlockState();
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 5));
                level.setBlock(spot, stone, 3);
                h.assertTrue(WardEvents.placed(p, level, spot, stone), "a block placed in a ward is queued");
                p.getInventory().clearContent();
                h.assertTrue(Crumble.runDue(level, level.getGameTime() + Crumble.DELAY - 1) == 0, "not before three seconds");
                h.assertTrue(Crumble.runDue(level, level.getGameTime() + Crumble.DELAY) == 1 && level.getBlockState(spot).isAir(), "then it crumbles");
                h.assertTrue(stones(p) == 1, "and the stone is back in the placer's pack: " + stones(p));

                // a site's wall can't be broken by hand; natural ground still digs
                BlockPos wall = at.offset(4, 0, 4);
                BlockState plating = AboveBlocks.PLATING.get().defaultBlockState();
                level.setBlock(wall, plating, 3);
                h.assertTrue(WardEvents.refuses(p, level, wall, plating), "the vault's plating holds inside its ward");
                h.assertFalse(WardEvents.refuses(p, level, at.below(), Blocks.DIRT.defaultBlockState()), "dirt round it still digs");

                // creative players and the open world are untouched
                level.setBlock(at.above(60), stone, 3);
                h.assertFalse(WardEvents.placed(p, level, at.above(60), stone), "outside the ward nothing crumbles");
                p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                h.assertFalse(WardEvents.placed(p, level, spot, stone) || WardEvents.refuses(p, level, wall, plating), "creative builds freely");
                p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);

                // a Hearth's radius is home: its rules give way there
                HearthHooks.install(new HearthHooks.Lookup() {
                    @Override public Optional<BlockPos> nearest(ServerLevel l, BlockPos pos, int radius) { return Optional.of(at); }
                    @Override public Optional<BlockPos> hearthOf(ServerLevel l, UUID player) { return Optional.of(at); }
                    @Override public boolean inside(ServerLevel l, BlockPos pos) { return true; }
                });
                level.setBlock(spot, stone, 3);
                h.assertFalse(WardEvents.placed(p, level, spot, stone), "a block placed inside a Hearth stays");
                h.assertTrue(WardEvents.refuses(p, level, wall, plating), "but a vault's walls hold even there: its rank door can't be dug round");

                // a climb (a Tether Spire's ward, high over the test) refuses the first block outright, Hearth or not,
                // and keeps the lock in the band; its unopened cache stays sealed for someone who built their way in
                BlockPos high = at.above(120);
                WardHooks.Source spire = (l, pos, out) -> {
                    if (pos.getY() > at.getY() + 100) out.accept(new WardHooks.Ward("ruin:9,9", RuinKind.SPIRE.structure.identifier().getPath(),
                            new net.minecraft.world.level.levelgen.structure.BoundingBox(high).inflatedBy(8)));
                };
                WardHooks.register(spire);
                var real = dev.awaken.core.api.ArrivalHooks.installed();
                try {
                    dev.awaken.progression.Progression.awaken(p);
                    h.assertTrue(WardEvents.refusesClimb(p, level, high), "a spire refuses the first block, even inside a Hearth");
                    h.assertTrue(p.getData(dev.awaken.progression.ProgressionModule.PROMISES.get()).get("landmark:ruin:9,9") != null,
                            "and the band keeps its crown, waiting for the Tether");
                    h.assertFalse(WardEvents.refusesClimb(p, level, at.east()), "a vault isn't a climb: it crumbles instead");
                    level.setBlock(high, Blocks.CHEST.defaultBlockState(), 3);
                    net.minecraft.world.RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), high, dev.awaken.above.ruin.RuinBuilder.loot("precursor_spire"));
                    boolean[] moved = {false};
                    dev.awaken.core.api.ArrivalHooks.install(new dev.awaken.core.api.ArrivalHooks.Tracker() {
                        @Override public boolean byMovement(ServerPlayer q) { return moved[0]; }
                    });
                    h.assertTrue(WardEvents.sealsCache(p, level, high), "built your way up: the cache's inner seal holds");
                    moved[0] = true;
                    h.assertFalse(WardEvents.sealsCache(p, level, high), "came by rope: it opens");
                } finally {
                    dev.awaken.core.api.ArrivalHooks.install(real);
                    WardHooks.unregister(spire);
                    level.setBlock(high, Blocks.AIR.defaultBlockState(), 3);
                }
            } finally {
                HearthHooks.install(hearths);
                chunk.setAllStarts(starts);
                chunk.setAllReferences(refs);
                Crumble.clear();
                live.leave();
            }
            h.succeed();
        });
    }

    private static int stones(ServerPlayer p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(Items.STONE)) n += p.getInventory().getItem(i).getCount();
        return n;
    }
}
