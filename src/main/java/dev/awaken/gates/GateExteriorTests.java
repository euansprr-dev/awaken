package dev.awaken.gates;

import dev.awaken.Awaken;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.block.GateBlocks;
import dev.awaken.gates.block.GateFissureBlock;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Layout;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.content.Twist;
import dev.awaken.gates.run.Interactions;
import dev.awaken.gates.run.Runs;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.TagValueInput;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The surface gate's tests: every rank's footprint placed and removed (the opening, the mouth, spurs, fissures, a
 * scar left behind), builds respected by the bigger footprint, walking into the top of a towering gate still enters,
 * the anchor's render data saved, synced and refreshed from the gate, old doorways rebuilt, and the vortex's frames
 * exported for the offline preview ({@code tools/art/specs/structures.py}).
 */
final class GateExteriorTests {
    private GateExteriorTests() {}

    /** In-world tests build this high above the test origin (isolated batches share the spot; others build lower). */
    static final int UP = 96;

    static void register() {
        AwakenTests.register("gates_doorway_one_surface", 40, h -> {
            // an exit (3x3 along x) and a rift (3x4 along z): one anchor each with its renderer, every other block
            // invisible and untargetable, and walking in still counts on every block
            ServerLevel level = h.getLevel();
            BlockPos base = h.absolutePos(new BlockPos(2, 1, 2));
            GateBlocks.Portal exit = (GateBlocks.Portal) GatesRegistry.EXIT.get();
            GateBlocks.Portal rift = (GateBlocks.Portal) GatesRegistry.RIFT.get();
            exit.place(level, base, net.minecraft.core.Direction.Axis.X, 3, 3, 3);
            rift.place(level, base.offset(0, 0, 4), net.minecraft.core.Direction.Axis.Z, 3, 4, 3);
            int[][] spans = {{-1, 1, 0, 2, 0, 0}, {0, 0, 0, 3, 3, 5}};
            for (int k = 0; k < 2; k++) {
                int anchors = 0, blocks = 0;
                for (BlockPos p : BlockPos.betweenClosed(base.offset(spans[k][0], spans[k][2], spans[k][4]), base.offset(spans[k][1], spans[k][3], spans[k][5]))) {
                    BlockState st = level.getBlockState(p);
                    h.assertTrue(st.getBlock() instanceof GateBlocks.Portal, "doorway block at " + p.toShortString() + ": " + st);
                    blocks++;
                    boolean anchor = st.getValue(GateBlocks.Portal.ROLE) == GateBlocks.Portal.Role.ANCHOR;
                    if (anchor) {
                        anchors++;
                        h.assertTrue(level.getBlockEntity(p) instanceof GatePortalBlockEntity, "the anchor carries the renderer");
                        h.assertTrue(p.getY() == base.getY(), "the anchor is the bottom of the middle column");
                    } else {
                        h.assertTrue(level.getBlockEntity(p) == null, "only the anchor has a block entity");
                    }
                    h.assertTrue(st.getRenderShape() == net.minecraft.world.level.block.RenderShape.INVISIBLE, "drawn as one surface, not per block");
                    h.assertTrue(st.getShape(level, p).isEmpty(), "no outline to aim at");
                    h.assertTrue(!st.getEntityInsideCollisionShape(level, p, null).isEmpty(), "walking in still counts");
                    h.assertTrue(st.getValue(GateBlocks.Portal.RANK) == 3, "carries the gate's rank colour");
                }
                h.assertTrue(blocks == (k == 0 ? 9 : 12) && anchors == 1, "one anchor in " + blocks + " blocks (" + anchors + ")");
            }
            h.assertTrue(GatesRegistry.EXIT.get().defaultBlockState().getRenderShape() == net.minecraft.world.level.block.RenderShape.MODEL,
                    "old doorways (default state) keep their per-block look");
            // an old exit (3x3 of default blocks) redraws itself as one opening, whichever block ticks
            for (BlockPos p : BlockPos.betweenClosed(base.offset(-1, 0, 0), base.offset(1, 3, 5))) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            for (BlockPos p : BlockPos.betweenClosed(base.offset(-1, 0, 0), base.offset(1, 2, 0))) level.setBlock(p, exit.defaultBlockState(), 3);
            exit.upgradeLegacy(level, base.offset(1, 2, 0));
            int oldAnchors = 0;
            for (BlockPos p : BlockPos.betweenClosed(base.offset(-1, 0, 0), base.offset(1, 2, 0))) {
                BlockState st = level.getBlockState(p);
                h.assertTrue(st.getValue(GateBlocks.Portal.ROLE) != GateBlocks.Portal.Role.LEGACY, "upgraded at " + p.toShortString());
                if (st.getValue(GateBlocks.Portal.ROLE) == GateBlocks.Portal.Role.ANCHOR) {
                    oldAnchors++;
                    h.assertTrue(p.equals(base) && level.getBlockEntity(p) instanceof GatePortalBlockEntity, "the old opening's anchor is its bottom middle");
                }
            }
            h.assertTrue(oldAnchors == 1, "one anchor after the upgrade");
            for (BlockPos p : BlockPos.betweenClosed(base.offset(-1, 0, 0), base.offset(1, 3, 5))) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            h.succeed();
        });
        AwakenTests.registerIsolated("gates_vortex_footprint_all_ranks", 200, h -> {
            ServerLevel level = h.getLevel();
            BlockPos ground = h.absolutePos(new BlockPos(0, UP, 0));
            int lastCores = 0;
            try {
                for (int rank = 0; rank <= 6; rank++) {
                    for (int axis = 0; axis <= 1; axis++) {
                        meadow(level, ground);
                        GateRecord g = GateExterior.display(ground, axis, rank, false);
                        h.assertTrue(Overworld.untouched(level, ground, axis, rank), "a meadow is untouched ground");
                        Overworld.place(level, g, false);
                        String what = Rank.values()[rank].label + "/" + axis;
                        // the anchor and its render data
                        h.assertTrue(level.getBlockEntity(Overworld.anchor(ground)) instanceof GateVortexBlockEntity v && v.rank() == rank && v.axis() == axis,
                                what + ": the anchor carries the vortex");
                        int cores = 0, spurs = 0;
                        for (Overworld.Placed p : Overworld.blocks(ground, axis, rank, false)) {
                            BlockState s = level.getBlockState(p.pos());
                            if (p.state().is(GatesRegistry.CORE.get())) {
                                cores++;
                                h.assertTrue(s.is(GatesRegistry.CORE.get()) && s.getValue(GateBlocks.Core.RANK) == rank, what + ": core at " + p.pos());
                            } else if (!s.isAir()) spurs++;
                        }
                        h.assertTrue(cores > lastCores || axis == 1, what + ": the opening grows with rank (" + cores + " cores)");
                        lastCores = Math.max(lastCores, cores);
                        h.assertTrue(level.getBlockState(ground.above(3)).is(GatesRegistry.CORE.get()), what + ": the middle of the opening is a core");
                        h.assertTrue(spurs >= 8, what + ": rock spurs stand beside the opening (" + spurs + ")");
                        BlockPos mouth = g.mouth();
                        h.assertTrue(level.getBlockState(mouth).isAir() && level.getBlockState(mouth.above()).isAir()
                                && !level.getBlockState(mouth.below()).isAir(), what + ": the mouth is standing room");
                        int fissures = count(level, ground, rank, GatesRegistry.FISSURE.get());
                        h.assertTrue(fissures >= 10, what + ": fissures run from the gate (" + fissures + ")");
                        h.assertTrue(GateVortex.size(rank).ringRadius() < 15, "the footprint stays inside the surface build's chunks");
                        if (axis == 0 && (rank == 0 || rank == 2 || rank == 5 || rank == 6)) dumpGate(level, ground, axis, rank, GateVortex.OPEN, "open");
                        // a break: red-black cores, cracked spurs, red fissures
                        Overworld.place(level, g, true);
                        h.assertTrue(level.getBlockState(ground.above(3)).getValue(GateBlocks.Core.BROKEN), what + ": the cores show the break");
                        h.assertTrue(redFissures(level, ground, rank) > 0, what + ": the fissures run red");
                        if (axis == 0 && (rank == 2 || rank == 5)) dumpGate(level, ground, axis, rank, GateVortex.BROKEN, "broken");
                        // and removal: nothing of the gate stays but its scar
                        Overworld.remove(level, g);
                        h.assertTrue(count(level, ground, rank, GatesRegistry.CORE.get()) == 0, what + ": the opening is gone");
                        h.assertTrue(count(level, ground, rank, GatesRegistry.FISSURE.get()) == 0, what + ": the fissures went dark");
                        h.assertTrue(count(level, ground, rank, GatesRegistry.FRAME_CRACKED.get()) == 0
                                && count(level, ground, rank, GatesRegistry.FRAME.get()) == 0, what + ": the spurs came down");
                        h.assertFalse(level.getBlockState(ground).is(Blocks.GRASS_BLOCK), what + ": a scorched scar stays where it stood");
                    }
                }
            } finally {
                wipe(level, ground);
            }
            h.succeed();
        });

        AwakenTests.registerIsolated("gates_vortex_respects_builds", 100, h -> {
            ServerLevel level = h.getLevel();
            BlockPos ground = h.absolutePos(new BlockPos(0, UP, 0));
            try {
                meadow(level, ground);
                // a chest just outside a small gate's crater but inside a big one's
                BlockPos chest = ground.offset(11, 1, 3);
                level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
                h.assertTrue(Overworld.untouched(level, ground, 0, 0), "an E-rank gate's footprint misses the chest");
                h.assertFalse(Overworld.untouched(level, ground, 0, 5), "an S-rank gate's crater would reach it: not here");
                h.assertFalse(Overworld.untouched(level, ground, 0), "the rank-blind check assumes the biggest gate");
                level.removeBlock(chest, false);
                // a plank floor on the pad of a gate placed anyway (a scripted gate) is never replaced
                BlockPos plank = ground.offset(4, 0, 2);
                level.setBlock(plank, Blocks.OAK_PLANKS.defaultBlockState(), 3);
                GateRecord g = GateExterior.display(ground, 0, 5, false);
                Overworld.place(level, g, false);
                h.assertTrue(level.getBlockState(plank).is(Blocks.OAK_PLANKS), "a build on the pad is left alone");
                Overworld.remove(level, g);
            } finally {
                wipe(level, ground);
            }
            h.succeed();
        });

        AwakenTests.registerIsolated("gates_vortex_enters_from_anywhere", 200, h -> {
            ServerLevel level = h.getLevel();
            BlockPos ground = h.absolutePos(new BlockPos(0, UP, 0));
            GatePlan plan = new GatePlan(424242, GatePlan.CONTENT_VERSION, Rank.S, Theme.BELL_CRYPT, Layout.LABYRINTH, List.of(), Twist.NONE, 3,
                    Theme.BELL_CRYPT.bossId(), Theme.HIVE, 0);
            meadow(level, ground);
            GateService.testLevel = level;
            GateRecord g = GateLiveTests.standUp(h, plan, 0, 1);
            ServerPlayer p = GateLiveTests.player(h);
            g.pos = ground;
            g.structurePlaced = true;
            Overworld.place(level, g, false);
            try {
                int top = GateVortex.coreTop(5), half = GateVortex.coreHalfWidth(5);
                BlockPos high = Overworld.at(ground, 0, 0, top, 0), side = Overworld.at(ground, 0, half, (int) GateVortex.size(5).centreY(), 0);
                h.assertTrue(level.getBlockState(high).is(GatesRegistry.CORE.get()), "the top of the opening is a core");
                h.assertTrue(Interactions.structureAt(level.getServer(), high) == g, "the top core belongs to its gate (" + top + " up)");
                h.assertTrue(Interactions.structureAt(level.getServer(), side) == g, "a side core belongs to its gate");
                h.assertTrue(Interactions.structureAt(level.getServer(), Overworld.anchor(ground)) == g, "the anchor belongs to its gate");
                h.assertTrue(Interactions.structureAt(level.getServer(), ground.offset(0, 1, 5)) == null, "the pad in front of it is not the gate");
                // walk into the top of a towering gate: you go in
                Interactions.touchCore(level, high, p);
            } catch (RuntimeException e) {
                GateLiveTests.teardown(level, p, g);
                wipe(level, ground);
                throw e;
            }
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(Runs.run(p).active(), "touching the top core entered the gate"))
                    .thenExecute(() -> {
                        GateLiveTests.teardown(level, p, g);
                        Overworld.remove(level, g);
                        g.structurePlaced = false;
                        wipe(level, ground);
                    })
                    .thenSucceed();
        });

        AwakenTests.registerIsolated("gates_vortex_render_data_syncs", 100, h -> {
            ServerLevel level = h.getLevel();
            BlockPos ground = h.absolutePos(new BlockPos(0, UP, 0));
            GatePlan plan = new GatePlan(77, GatePlan.CONTENT_VERSION, Rank.B, Theme.HIVE, Layout.LABYRINTH, List.of(), Twist.RED_GATE, 3,
                    Theme.HIVE.bossId(), Theme.BELL_CRYPT, 0);
            GateRegistry reg = GateRegistry.get(level.getServer());
            GateRecord g = new GateRecord(reg.nextId(), plan, ground, 1, -1, 0, dev.awaken.core.PlayDays.current(level.getServer()) + 9,
                    GateState.READY, Optional.empty());
            reg.put(g);
            try {
                meadow(level, ground);
                Overworld.place(level, g, false);
                GateVortexBlockEntity be = (GateVortexBlockEntity) level.getBlockEntity(Overworld.anchor(ground));
                h.assertTrue(be != null && be.gateId() == g.id && be.rank() == 3 && be.axis() == 1 && be.red(), "bound to its gate: rank, axis, Red Gate");
                h.assertTrue(be.mode() == GateVortex.OPEN, "open while the break is far, was " + be.mode());
                // the update tag carries everything the renderer needs
                var tag = be.getUpdateTag(level.registryAccess());
                GateVortexBlockEntity copy = new GateVortexBlockEntity(be.getBlockPos(), be.getBlockState());
                copy.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
                h.assertTrue(copy.gateId() == g.id && copy.rank() == 3 && copy.axis() == 1 && copy.red() && copy.mode() == GateVortex.OPEN
                        && copy.seed() == be.seed() && copy.formedAt() == be.formedAt(), "the update tag round-trips");
                h.assertTrue(be.getUpdatePacket() != null, "it sends a block entity packet");
                // the break comes near: about to break
                g.breaksOnPlayDay = dev.awaken.core.PlayDays.current(level.getServer()) + 1;
                h.assertTrue(be.look(level.getServer(), g) && be.mode() == GateVortex.WARNING && be.warn() > 0.5F, "about to break, warn " + be.warn());
                g.breaksOnPlayDay = dev.awaken.core.PlayDays.current(level.getServer());
                h.assertTrue(be.look(level.getServer(), g) && be.warn() == 1F, "breaking today");
                h.assertFalse(be.look(level.getServer(), g), "no update when nothing changed");
                g.state = GateState.BROKEN;
                h.assertTrue(be.look(level.getServer(), g) && be.mode() == GateVortex.BROKEN, "broken");
                g.state = GateState.CLEARED;
                h.assertTrue(be.look(level.getServer(), g) && be.mode() == GateVortex.SPENT, "spent once cleared");
                g.state = GateState.BUILDING;
                h.assertTrue(be.look(level.getServer(), g) && be.mode() == GateVortex.FORMING, "forming while it builds");
                // a showcase keeps the look it was given
                be.showcase(6, 0, GateVortex.CALM, false, 0F, level.getGameTime());
                h.assertTrue(be.gateId() < 0 && be.mode() == GateVortex.CALM && be.rank() == 6, "showcase look");
                Overworld.remove(level, g);
            } finally {
                g.state = GateState.RELEASED;
                wipe(level, ground);
            }
            h.succeed();
        });

        AwakenTests.registerIsolated("gates_vortex_rebuilds_old_doorways", 100, h -> {
            ServerLevel level = h.getLevel();
            BlockPos ground = h.absolutePos(new BlockPos(0, UP, 0));
            GatePlan plan = new GatePlan(91, GatePlan.CONTENT_VERSION, Rank.C, Theme.HIVE, Layout.LABYRINTH, List.of(), Twist.NONE, 3,
                    Theme.HIVE.bossId(), Theme.BELL_CRYPT, 0);
            GateRegistry reg = GateRegistry.get(level.getServer());
            GateRecord g = new GateRecord(reg.nextId(), plan, ground, 0, -1, 0, 99, GateState.READY, Optional.empty());
            g.structurePlaced = true;
            reg.put(g);
            try {
                meadow(level, ground);
                // the doorway as build 1 placed it: platform, posts, lintel, a 3x5 core wall
                BlockState frame = GatesRegistry.FRAME.get().defaultBlockState();
                BlockState core = GatesRegistry.CORE.get().defaultBlockState().setValue(GateBlocks.Core.RANK, 2);
                for (int a = -3; a <= 3; a++) for (int b = -2; b <= 2; b++) if (!(Math.abs(a) == 3 && Math.abs(b) == 2)) level.setBlock(ground.offset(a, 0, b), frame, 3);
                for (int y = 1; y <= 5; y++) {
                    level.setBlock(ground.offset(-2, y, 0), frame, 3);
                    level.setBlock(ground.offset(2, y, 0), frame, 3);
                    for (int a = -1; a <= 1; a++) level.setBlock(ground.offset(a, y, 0), core, 3);
                }
                for (int a = -2; a <= 2; a++) level.setBlock(ground.offset(a, 6, 0), frame, 3);
                level.setBlock(ground.offset(0, 7, 0), frame, 3);
                h.assertTrue(Overworld.legacy(level, g), "an old doorway is recognised");
                h.assertTrue(GateExterior.upgradeLoaded(level.getServer()) >= 1, "it is rebuilt");
                h.assertTrue(level.getBlockEntity(Overworld.anchor(ground)) instanceof GateVortexBlockEntity, "with a vortex anchor");
                h.assertFalse(level.getBlockState(ground.offset(0, 7, 0)).is(GatesRegistry.FRAME.get()), "the old lintel's cap is gone");
                h.assertFalse(Overworld.legacy(level, g), "and it is not rebuilt twice");
                Overworld.remove(level, g);
            } finally {
                g.structurePlaced = false;
                g.state = GateState.RELEASED;
                wipe(level, ground);
            }
            h.succeed();
        });

        AwakenTests.register("gates_vortex_frames", 40, h -> {
            int[] counts = new int[1];
            float[] bad = new float[1];
            for (int rank = 0; rank <= 6; rank++) {
                for (int mode = 0; mode < GateVortex.MODE_NAMES.length; mode++) {
                    GateVortex.Look l = new GateVortex.Look(rank, mode, false, 1234L + rank, mode == GateVortex.FORMING ? 0.6F : 1F,
                            mode == GateVortex.WARNING ? 1F : 0F, 1F);
                    counts[0] = 0;
                    GateVortex.emitSurface(l, 3.7F, 1, (x, y, z, u, v, argb, nx, ny, nz) -> {
                        counts[0]++;
                        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z) || v < 0F || v > 1F) bad[0]++;
                    });
                    h.assertTrue(counts[0] % 4 == 0, "whole quads");
                    h.assertTrue(counts[0] / 4 <= GateVortex.maxSurfaceQuads(rank), "within the quad budget: " + counts[0] / 4);
                    h.assertTrue(mode == GateVortex.SPENT || counts[0] / 4 > 1000, GateVortex.MODE_NAMES[mode] + " draws the surface: " + counts[0] / 4);
                }
            }
            h.assertTrue(bad[0] == 0, "every vertex finite, every v inside the atlas");
            exportFrames();
            h.succeed();
        });
    }

    // ---- helpers ------------------------------------------------------------------------------------------------------

    /** A flat meadow round the spot, clear above. */
    static void meadow(ServerLevel level, BlockPos ground) {
        wipe(level, ground);
        for (int dx = -16; dx <= 16; dx++) {
            for (int dz = -16; dz <= 16; dz++) {
                level.setBlock(ground.offset(dx, 0, dz), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(ground.offset(dx, -1, dz), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(ground.offset(dx, -2, dz), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    static void wipe(ServerLevel level, BlockPos ground) {
        for (BlockPos q : BlockPos.betweenClosed(ground.offset(-17, -6, -17), ground.offset(17, 26, 17))) {
            if (!level.getBlockState(q).isAir()) level.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static int count(ServerLevel level, BlockPos ground, int rank, Block block) {
        int n = 0;
        for (BlockPos q : BlockPos.betweenClosed(ground.offset(-16, -5, -16), ground.offset(16, 24, 16))) if (level.getBlockState(q).is(block)) n++;
        return n;
    }

    private static int redFissures(ServerLevel level, BlockPos ground, int rank) {
        int n = 0;
        for (BlockPos q : BlockPos.betweenClosed(ground.offset(-16, -5, -16), ground.offset(16, 5, 16))) {
            BlockState s = level.getBlockState(q);
            if (s.is(GatesRegistry.FISSURE.get()) && s.getValue(GateFissureBlock.RANK) == 7) n++;
        }
        return n;
    }

    /** Writes the built gate to run/structures/gate_&lt;rank&gt;_&lt;look&gt;.json for tools/art/specs/structures.py. */
    static void dumpGate(ServerLevel level, BlockPos ground, int axis, int rank, int mode, String look) {
        GateVortex.Size s = GateVortex.size(rank);
        StringBuilder extra = new StringBuilder();
        extra.append("\"vortex\":{\"rank\":").append(rank).append(",\"mode\":").append(mode).append(",\"axis\":").append(axis)
                .append(",\"centre\":[0.5,").append(s.centreY()).append(",0.5],\"rx\":").append(s.rx()).append(",\"ry\":").append(s.ry())
                .append(",\"seed\":").append(seedOf(level, ground)).append('}');
        dump(level, "gate_" + Rank.values()[rank].label.toLowerCase(Locale.ROOT) + "_" + look, "gate", ground,
                ground.offset(-16, -4, -16), ground.offset(16, 24, 16), extra.toString());
    }

    private static long seedOf(ServerLevel level, BlockPos ground) {
        return level.getBlockEntity(Overworld.anchor(ground)) instanceof GateVortexBlockEntity v ? v.seed() : 0L;
    }

    /**
     * A built structure as JSON: every non-air block relative to {@code origin} with its id and properties. The same
     * format for gates, ruins, the Well and lairs ({@code tools/art/specs/structures.py} reads them).
     */
    public static void dump(ServerLevel level, String name, String kind, BlockPos origin, BlockPos lo, BlockPos hi, String extra) {
        StringBuilder sb = new StringBuilder("{\"name\":\"").append(name).append("\",\"kind\":\"").append(kind).append("\",");
        if (extra != null && !extra.isEmpty()) sb.append(extra).append(',');
        sb.append("\"blocks\":[");
        boolean first = true;
        for (BlockPos q : BlockPos.betweenClosed(lo, hi)) {
            BlockState st = level.getBlockState(q);
            if (st.isAir()) continue;
            if (!first) sb.append(',');
            first = false;
            var id = BuiltInRegistries.BLOCK.getKey(st.getBlock());
            sb.append('[').append(q.getX() - origin.getX()).append(',').append(q.getY() - origin.getY()).append(',').append(q.getZ() - origin.getZ())
                    .append(",\"").append(id.getNamespace().equals("minecraft") ? id.getPath() : id.toString()).append("\",\"");
            boolean firstProp = true;
            for (Property<?> p : st.getProperties()) {
                if (!firstProp) sb.append(',');
                firstProp = false;
                sb.append(p.getName()).append('=').append(propValue(st, p));
            }
            sb.append("\"]");
        }
        sb.append("]}");
        try {
            Path dir = Path.of("structures");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(name + ".json"), sb.toString());
        } catch (IOException e) {
            Awaken.LOG.warn("[gates] could not write the structure dump: {}", e.toString());
        }
    }

    private static <T extends Comparable<T>> String propValue(BlockState s, Property<T> p) {
        return p.getName(s.getValue(p));
    }

    /** Frames of the vortex for the offline preview: each rank open, and the C and S ranks in every look. */
    static void exportFrames() {
        StringBuilder sb = new StringBuilder("{\"atlas\":512,\"frames\":[");
        boolean first = true;
        for (int rank = 0; rank <= 6; rank++) {
            for (int mode = 0; mode < GateVortex.MODE_NAMES.length; mode++) {
                if (mode != GateVortex.OPEN && rank != 2 && rank != 5) continue;
                for (float t : new float[]{0F, 0.6F, 1.2F}) {
                    GateVortex.Look l = new GateVortex.Look(rank, mode, false, 1234L + rank, mode == GateVortex.FORMING ? 0.55F : 1F,
                            mode == GateVortex.WARNING ? 1F : 0F, 1F);
                    if (!first) sb.append(',');
                    first = false;
                    sb.append("{\"rank\":").append(rank).append(",\"mode\":\"").append(GateVortex.MODE_NAMES[mode]).append("\",\"t\":").append(t)
                            .append(",\"rx\":").append(l.size().rx()).append(",\"ry\":").append(l.size().ry()).append(",\"cy\":").append(l.size().centreY())
                            .append(",\"quads\":[");
                    StringBuilder q = new StringBuilder();
                    GateVortex.emitSurface(l, t, 1, (x, y, z, u, v, argb, nx, ny, nz) -> {
                        if (q.length() > 0) q.append(',');
                        q.append(String.format(Locale.ROOT, "%.3f,%.3f,%.3f,%.4f,%.4f,%d", x, y, z, u, v, argb));
                    });
                    sb.append(q).append("],\"shards\":[");
                    StringBuilder r = new StringBuilder();
                    GateVortex.emitShards(l, t, (x, y, z, u, v, argb, nx, ny, nz) -> {
                        if (r.length() > 0) r.append(',');
                        r.append(String.format(Locale.ROOT, "%.3f,%.3f,%.3f,%.3f,%.3f,%.2f,%.2f,%.2f", x, y, z, u, v, nx, ny, nz));
                    });
                    sb.append(r).append("]}");
                }
            }
        }
        // doorways inside a gate: exits at three ranks (3 tall) and a red rift (4 tall), as GatePortalRenderer draws them
        for (int[] d : new int[][]{{0, 3, 0}, {2, 3, 0}, {4, 3, 0}, {3, 4, 1}}) {
            GateVortex.Look l = GateVortex.Look.forDoorway(d[0], d[2] == 1, 99L + d[0], 1.42F, d[1] * 0.5F - 0.04F, 1F);
            sb.append(",{\"rank\":").append(d[0]).append(",\"mode\":\"").append(d[2] == 1 ? "rift" : "doorway").append("\",\"t\":0.6")
                    .append(",\"rx\":").append(l.size().rx()).append(",\"ry\":").append(l.size().ry()).append(",\"cy\":").append(d[1] * 0.5F)
                    .append(",\"quads\":[");
            StringBuilder q = new StringBuilder();
            GateVortex.emitSurface(l, 0.6F, 1, (x, y, z, u, v, argb, nx, ny, nz) -> {
                if (q.length() > 0) q.append(',');
                q.append(String.format(Locale.ROOT, "%.3f,%.3f,%.3f,%.4f,%.4f,%d", x, y, z, u, v, argb));
            });
            sb.append(q).append("],\"shards\":[]}");
        }
        sb.append("]}");
        try {
            Path dir = Path.of("structures");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("vortex_frames.json"), sb.toString());
        } catch (IOException e) {
            Awaken.LOG.warn("[gates] could not write the vortex frames: {}", e.toString());
        }
    }
}
