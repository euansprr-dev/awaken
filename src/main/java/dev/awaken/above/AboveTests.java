package dev.awaken.above;

import dev.awaken.above.block.BrokenMachineBlock;
import dev.awaken.above.block.DoorCoreBlock;
import dev.awaken.above.frame.FrameForge;
import dev.awaken.above.frame.FrameModule;
import dev.awaken.above.halo.HaloLayout;
import dev.awaken.above.machine.CloneVats;
import dev.awaken.above.machine.MachineBlockEntity;
import dev.awaken.above.machine.Recalibrate;
import dev.awaken.above.power.EssenceCellItem;
import dev.awaken.above.power.Power;
import dev.awaken.above.ruin.RuinBuilder;
import dev.awaken.above.ruin.RuinKind;
import dev.awaken.above.scan.Scanning;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Stat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/** GameTests for the Above (run with tools/gradle.sh 1200 runGameTestServer, or /test run awaken:above_*). */
final class AboveTests {
    private AboveTests() {}

    static void register() {
        AwakenTests.register("above_blueprint_learning", 40, h -> {
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            BlockPos abs = h.absolutePos(BlockPos.ZERO);
            h.setBlock(BlockPos.ZERO, RuinBuilder.broken(MachineType.GATE_SCANNER, Direction.NORTH));
            long before = Progression.profile(sp).counter(Above.MACHINES_SCANNED);
            h.assertFalse(Above.knows(sp, MachineType.GATE_SCANNER), "starts unknown");
            boolean fresh = Scanning.scanMachine(sp, abs, h.getLevel().getBlockState(abs));
            h.assertTrue(fresh, "first scan reads the machine");
            h.assertTrue(Above.knows(sp, MachineType.GATE_SCANNER), "blueprint learned");
            h.assertTrue(Above.knows(sp, MachineType.REACTOR), "the first read also teaches the reactor");
            h.assertTrue(Progression.profile(sp).counter(Above.MACHINES_SCANNED) == before + 1, "counts awaken:machines_scanned");
            h.assertTrue(h.getLevel().getBlockState(abs).getValue(BrokenMachineBlock.SCANNED), "block marked scanned");
            boolean again = Scanning.scanMachine(sp, abs, h.getLevel().getBlockState(abs));
            h.assertFalse(again, "a scanned machine can't be read twice");
            h.assertTrue(Progression.profile(sp).counter(Above.MACHINES_SCANNED) == before + 1, "no double count");
            h.assertFalse(Above.learn(sp, MachineType.GATE_SCANNER), "learning a known blueprint is not new");
            h.succeed();
        });

        AwakenTests.register("above_fabricator_recipes_valid", 20, h -> {
            for (MachineType t : MachineType.values()) {
                int n = t.parts().size();
                h.assertTrue(n >= 3 && n <= 5, t.id + " needs 3-5 parts, has " + n);
                for (MachineType.Part p : t.parts()) {
                    h.assertTrue(p.item().get() != null && p.item().get() != Items.AIR, t.id + " has a missing part item");
                    h.assertTrue(p.count() > 0, t.id + " part count must be positive");
                }
                h.assertTrue(AboveItems.machine(t) != Items.AIR, t.id + " has no block item");
                BlockState s = AboveBlocks.machine(t).defaultBlockState();
                h.assertTrue(s.is(Above.MACHINES_TAG), t.id + " missing from #awaken:machines");
                h.assertTrue(MachineType.byId(t.id) == t, "id lookup " + t.id);
            }
            h.assertTrue(AboveBlocks.FABRICATOR.get().defaultBlockState().is(Above.MACHINES_TAG), "fabricator in #awaken:machines");
            for (FrameForge.Piece p : FrameForge.PIECES) {
                h.assertTrue(p.parts().size() >= 3 && p.parts().size() <= 5, p.name() + " parts");
                for (MachineType.Part part : p.parts()) h.assertTrue(part.item().get() != Items.AIR, p.name() + " missing part");
            }
            for (FrameModule m : FrameModule.values()) {
                h.assertTrue(m.parts().size() >= 3 && m.parts().size() <= 5, m.id + " parts");
                for (MachineType.Part part : m.parts()) h.assertTrue(part.item().get() != Items.AIR, m.id + " missing part");
                h.assertTrue(AboveItems.MODULES.get(m).get() != Items.AIR, m.id + " item");
            }
            h.succeed();
        });

        AwakenTests.register("above_reactor_range", 40, h -> {
            BlockPos o = BlockPos.ZERO;
            h.assertTrue(Power.inRange(o, o.offset(32, 0, 0)), "32 blocks is in range");
            h.assertFalse(Power.inRange(o, o.offset(33, 0, 0)), "33 blocks is out of range");
            h.assertTrue(Power.inRange(o, o.offset(18, 18, 18)), "diagonal 31.2 in range");
            h.assertFalse(Power.inRange(o, o.offset(19, 19, 19)), "diagonal 32.9 out of range");
            BlockPos reactorRel = new BlockPos(0, 1, 0);
            h.setBlock(reactorRel, AboveBlocks.machine(MachineType.REACTOR).defaultBlockState());
            BlockPos reactor = h.absolutePos(reactorRel);
            BlockPos machine = h.absolutePos(new BlockPos(1, 1, 0));
            var level = h.getLevel();
            h.assertTrue(level.getBlockEntity(reactor) instanceof MachineBlockEntity be && be.isReactor(), "reactor block entity");
            MachineBlockEntity be = (MachineBlockEntity) level.getBlockEntity(reactor);
            be.addEssence(500);
            Power.Cache cache = new Power.Cache();
            h.assertTrue(Power.stored(level, machine, cache) == 500, "machine sees the reactor's 500, saw " + Power.stored(level, machine, cache));
            h.assertTrue(Power.draw(level, machine, 200, cache), "draw 200");
            h.assertTrue(be.essence() == 300, "reactor left with 300");
            h.assertFalse(Power.draw(level, machine, 400, cache), "can't overdraw");
            h.assertTrue(be.essence() == 300, "failed draw takes nothing");
            h.setBlock(reactorRel, Blocks.AIR.defaultBlockState());
            h.assertTrue(Power.stored(level, machine, cache) == 0, "removing the reactor cuts the power (cache invalidated)");
            h.succeed();
        });

        AwakenTests.register("above_recalibrator_maths", 40, h -> {
            h.assertTrue(Recalibrate.refund(new int[]{10, 5, 7, 5, 5, 6}) == 8, "refund 5+2+1");
            h.assertTrue(Recalibrate.refund(new int[]{5, 5, 5, 5, 5, 5}) == 0, "nothing to refund");
            h.assertTrue(Recalibrate.cost(8) == 300 && Recalibrate.cost(0) == 0, "cost 100 + 25/pt");
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            Progression.awaken(sp);
            Progression.addStat(sp, Stat.STR, 4);
            Progression.addStat(sp, Stat.VIT, 2);
            Profile p = Progression.profile(sp);
            int unspent = p.unspent();
            int n = Recalibrate.apply(sp);
            h.assertTrue(n == 6, "refunded 6, was " + n);
            for (Stat s : Stat.values()) h.assertTrue(p.stat(s) == Stat.START, s + " back to start, was " + p.stat(s));
            h.assertTrue(p.unspent() == unspent + 6, "points returned: " + p.unspent() + " vs " + (unspent + 6));
            h.assertTrue(sp.getMaxHealth() == 20.0f, "VIT bonus removed, max health " + sp.getMaxHealth());
            h.succeed();
        });

        AwakenTests.register("above_clone_vat_once_per_day", 40, h -> {
            h.assertTrue(CloneVats.canUse(-1, 3) && !CloneVats.canUse(3, 3) && CloneVats.canUse(3, 4), "pure day rule");
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            h.setBlock(BlockPos.ZERO, AboveBlocks.machine(MachineType.CLONE_VAT).defaultBlockState());
            BlockPos abs = h.absolutePos(BlockPos.ZERO);
            MachineBlockEntity vat = (MachineBlockEntity) h.getLevel().getBlockEntity(abs);
            vat.setCloneReady(true);
            AboveData d = Above.data(sp);
            d.setCloneVat(Optional.of(GlobalPos.of(h.getLevel().dimension(), abs)));
            h.assertTrue(CloneVats.consume(sp, 7), "first death on day 7 is saved");
            h.assertTrue(d.cloneUsedDay() == 7 && d.clonePending(), "save recorded");
            h.assertFalse(vat.cloneReady(), "the clone was used");
            h.assertTrue(CloneVats.consume(sp, 7), "asking again during the same death still says yes");
            d.setClonePending(false); // respawned
            vat.setCloneReady(true);  // the vat grew a new clone
            h.assertFalse(CloneVats.consume(sp, 7), "second death on the same play-day is not saved");
            h.assertTrue(vat.cloneReady(), "a refused save keeps the clone");
            h.assertTrue(CloneVats.consume(sp, 8), "next play-day it works again");
            d.setClonePending(false);
            h.assertFalse(CloneVats.consume(sp, 9), "no grown clone, no save");
            h.succeed();
        });

        AwakenTests.register("above_essence_cell_charge", 40, h -> {
            ItemStack cell = new ItemStack(AboveItems.CELL.get());
            h.assertTrue(EssenceCellItem.charge(cell) == 0, "new cells are empty");
            h.assertTrue(EssenceCellItem.shardsThatFit(cell) == 10, "10 shards fill a cell");
            h.assertTrue(EssenceCellItem.fill(cell, 250) == 250, "fill 250");
            h.assertTrue(EssenceCellItem.fill(cell, 900) == 750, "capped at capacity");
            h.assertTrue(EssenceCellItem.charge(cell) == EssenceCellItem.CAPACITY, "full");
            h.assertTrue(EssenceCellItem.drain(cell, 300) == 300 && EssenceCellItem.charge(cell) == 700, "drain 300");
            h.assertTrue(EssenceCellItem.drain(cell, 900) == 700 && EssenceCellItem.charge(cell) == 0, "drain the rest only");
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            ItemStack a = new ItemStack(AboveItems.CELL.get());
            ItemStack b = new ItemStack(AboveItems.CELL.get());
            EssenceCellItem.setCharge(a, 300);
            EssenceCellItem.setCharge(b, 500);
            sp.getInventory().add(a);
            sp.getInventory().add(b);
            h.assertTrue(EssenceCellItem.carried(sp) == 800, "carried total, was " + EssenceCellItem.carried(sp));
            h.assertTrue(EssenceCellItem.drawCarried(sp, 600), "draw across cells");
            h.assertTrue(EssenceCellItem.carried(sp) == 200, "200 left");
            h.assertFalse(EssenceCellItem.drawCarried(sp, 300), "not enough");
            h.assertTrue(EssenceCellItem.carried(sp) == 200, "a failed draw takes nothing");
            h.succeed();
        });

        AwakenTests.register("above_ruin_layouts", 40, h -> {
            for (RuinKind kind : RuinKind.values()) {
                for (long seed = 1; seed <= 12; seed++) {
                    List<BlockState> placed = new ArrayList<>();
                    int[] chests = {0};
                    RuinBuilder.build(kind, seed * 7919L, 2, new RuinBuilder.Sink() {
                        @Override public void set(int x, int y, int z, BlockState state) {
                            RuinBuilder.Size s = RuinBuilder.size(kind);
                            h.assertTrue(x >= 0 && z >= 0 && x < s.x() && z < s.z() && y >= 0 && y < s.y(),
                                    kind.id + " block outside its box at " + x + "," + y + "," + z);
                            placed.add(state);
                        }
                        @Override public void fillDown(int x, int y, int z, BlockState state, int depth) {}
                        @Override public void chest(int x, int y, int z, Direction facing, ResourceKey<LootTable> table) { chests[0]++; }
                    });
                    EnumSet<MachineType> types = EnumSet.noneOf(MachineType.class);
                    int machines = 0, cores = 0, membranes = 0;
                    for (BlockState s : placed) {
                        if (s.is(AboveBlocks.BROKEN_MACHINE.get())) {
                            machines++;
                            types.add(s.getValue(BrokenMachineBlock.MACHINE));
                        }
                        if (s.is(AboveBlocks.DOOR_CORE.get())) {
                            cores++;
                            h.assertTrue(s.getValue(DoorCoreBlock.RANK) == (kind == RuinKind.SPAWN_VAULT ? 2 : 2), "door rank stored");
                        }
                        if (s.is(AboveBlocks.MEMBRANE.get())) membranes++;
                    }
                    h.assertTrue(machines >= 1 && machines <= 3, kind.id + " has " + machines + " broken machines");
                    h.assertTrue(types.size() == machines, kind.id + " repeats a machine type");
                    h.assertTrue(chests[0] >= 1, kind.id + " has loot");
                    if (kind.isVault()) h.assertTrue(cores == 1, kind.id + " has one door core");
                    if (kind == RuinKind.SPAWN_VAULT) h.assertTrue(types.contains(MachineType.GATE_SCANNER) && machines == 2, "spawn vault machines");
                    if (kind == RuinKind.OCEAN) h.assertTrue(membranes > 0, "ocean lab airlock");
                }
            }
            h.assertTrue(RuinBuilder.vaultRank(300) == 1 && RuinBuilder.vaultRank(2000) == 2 && RuinBuilder.vaultRank(9000) == 4, "vault rank by distance");
            h.succeed();
        });

        AwakenTests.register("above_ruins_generate_in_world", 200, h -> {
            // the same path as /place structure: generate a start, then place it chunk by chunk, far from other tests
            var level = h.getLevel();
            var structures = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
            var gen = level.getChunkSource().getGenerator();
            var rs = level.getChunkSource().randomState();
            BlockPos base = h.absolutePos(BlockPos.ZERO).offset(6000, 0, 0);
            int i = 0;
            for (RuinKind kind : RuinKind.values()) {
                var holder = structures.getOrThrow(kind.structure);
                BlockPos at = base.offset(i++ * 160, 0, 0);
                var start = holder.value().generate(holder, level.dimension(), level.registryAccess(), gen, gen.getBiomeSource(),
                        rs.createClimateSampler(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.EMPTY_UNCACHED), rs,
                        level.getStructureTemplateManager(), level.getSeed(), net.minecraft.world.level.ChunkPos.containing(at), 0, level, b -> true);
                h.assertTrue(start.isValid(), kind.id + " start is valid");
                var box = start.getBoundingBox();
                for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
                    for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                        level.getChunk(cx, cz);
                        var c = new net.minecraft.world.level.ChunkPos(cx, cz);
                        start.placeInChunk(level, level.structureManager(), gen, level.getRandom(),
                                new net.minecraft.world.level.levelgen.structure.BoundingBox(c.getMinBlockX(), level.getMinY(), c.getMinBlockZ(),
                                        c.getMaxBlockX(), level.getMaxY() + 1, c.getMaxBlockZ()), c);
                    }
                }
                int machines = 0, cores = 0, chests = 0;
                for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                    BlockState s = level.getBlockState(p);
                    if (s.is(AboveBlocks.BROKEN_MACHINE.get())) machines++;
                    else if (s.is(AboveBlocks.DOOR_CORE.get())) cores++;
                    else if (s.is(Blocks.CHEST) && level.getBlockEntity(p) instanceof net.minecraft.world.RandomizableContainer rc
                            && rc.getLootTable() != null) chests++;
                }
                h.assertTrue(machines >= 1 && machines <= 3, kind.id + " placed " + machines + " broken machines");
                h.assertTrue(chests >= 1, kind.id + " placed a loot chest");
                if (kind.isVault()) h.assertTrue(cores == 1, kind.id + " placed its door core");
                if (kind == RuinKind.SKY) h.assertTrue(box.minY() >= 262, "sky platforms float above Y 262, was " + box.minY());
            }
            h.succeed();
        });

        AwakenTests.register("above_data_codec_and_halo_layout", 20, h -> {
            AboveData d = new AboveData();
            d.learnAll();
            d.setCloneUsedDay(12);
            d.setHaloSeen(true);
            d.addScannedGate("g7");
            var tag = AboveData.CODEC.encodeStart(NbtOps.INSTANCE, d).getOrThrow();
            AboveData q = AboveData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
            h.assertTrue(q.blueprints().size() == MachineType.values().length, "blueprints round-trip");
            h.assertTrue(q.cloneUsedDay() == 12 && q.haloSeen() && q.scannedGates().contains("g7"), "timers round-trip");
            BlockPos a = HaloLayout.ARRIVAL;
            h.assertTrue(HaloLayout.at(a.getX(), a.getY(), a.getZ()) == null && HaloLayout.at(a.getX(), a.getY() + 1, a.getZ()) == null,
                    "arrival point is open");
            var floor = HaloLayout.at(a.getX(), a.getY() - 1, a.getZ());
            h.assertTrue(floor != null && floor.isSolid(), "arrival point has a floor");
            var pad = HaloLayout.at(HaloLayout.PAD.getX(), HaloLayout.PAD.getY(), HaloLayout.PAD.getZ());
            h.assertTrue(pad != null && pad.is(AboveBlocks.RETURN_PAD.get()), "return pad in the hub");
            h.assertTrue(HaloLayout.at(500, HaloLayout.FLOOR, 500) == null, "void outside the station");
            h.succeed();
        });
    }
}
