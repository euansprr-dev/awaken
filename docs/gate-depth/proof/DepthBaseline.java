package dev.awaken.gates.world;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.worlds.GateWorld;
import dev.awaken.gates.worlds.GateWorlds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** DISPOSABLE planning harness (not committed): how flat, plumb and bare the world floors' places are today. */
final class DepthBaseline {
    private DepthBaseline() {}

    static void register() {
        AwakenTests.register("depthbase_all", 6000, h -> {
            StringBuilder out = new StringBuilder("\nDEPTHBASE world kind floors cols flat% levels partial% detail/100 roofSD roofRange plumb% carveMs\n");
            TreeMap<String, GateWorld> all = new TreeMap<>(GateWorlds.all());
            for (var e : all.entrySet()) {
                GateWorld w = e.getValue();
                if (w.tier() == GateWorld.Tier.SECRET) continue;
                String id = e.getKey();
                long cols = 0, flat = 0, surf = 0, partial = 0, detail = 0, edge = 0, plumb = 0;
                double carve = 0;
                int floors = 0, chunks = 0;
                List<Integer> roofs = new ArrayList<>();
                TreeMap<Integer, Integer> hist = new TreeMap<>();
                java.util.Set<Integer> levels = new java.util.HashSet<>();
                int placesN = 0;
                Theme gate = w.tier() == GateWorld.Tier.RARE ? Theme.values()[(w.theme().ordinal() + 5) % Theme.values().length] : w.theme();
                String mood = w.moods().isEmpty() ? "" : w.moods().get(0).id();
                GatePlan p = GateWorldBellTests.dressed(GateWorldBellTests.plan(0xD3E7L, 1, gate), id, mood);
                for (FloorLayout f : FloorBuilder.build(p).main) {
                    if (!f.isWorld() || floors >= 2) continue;
                    floors++;
                    WorldCheck wc = new WorldCheck(p, f);
                    carve += wc.carveNanosTotal / 1e6;
                    chunks += wc.chunks;
                    for (Room r : f.rooms) {
                        if (r.rig != null || r.type == Room.Type.BOSS) continue;
                        int base = f.baseY + r.dy;
                        java.util.Set<Integer> lv = new java.util.HashSet<>();
                        for (int x = r.gx * 16; x < (r.gx + r.gw) * 16; x++) for (int z = r.gz * 16; z < (r.gz + r.gh) * 16; z++) {
                            if (f.places != null && f.places.groundAt(x, z) != r.id) continue;
                            int feet = Integer.MIN_VALUE;
                            for (int y = base - 3; y <= base + 3 && feet == Integer.MIN_VALUE; y++) if (wc.node(x, y, z)) feet = y;
                            if (feet == Integer.MIN_VALUE) continue;
                            cols++;
                            if (feet == base) flat++;
                            hist.merge(feet - base, 1, Integer::sum);
                            lv.add(feet - base);
                            int roof = 0;
                            while (roof < 60 && wc.pass(x, feet + roof, z)) roof++;
                            if (roof < 60) roofs.add(roof);
                            for (int y = feet; y < feet + roof; y++) {
                                BlockState s = wc.at(x, y, z);
                                if (!s.isAir() && s.getFluidState().isEmpty()) detail++;
                            }
                            int[][] n4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                            for (int[] d : n4) {
                                int nx = x + d[0], nz = z + d[1];
                                if (!wc.pass(nx, feet + 1, nz)) {
                                    edge++;
                                    if (!wc.pass(nx, feet + 2, nz) && !wc.pass(nx, feet, nz)) plumb++;
                                }
                            }
                            // the surfaces this column shows: the floor under it, the roof over it, and its wall faces
                            for (BlockState s : new BlockState[]{wc.at(x, feet - 1, z), wc.at(x, feet + roof, z)}) {
                                if (s.isAir() || !s.getFluidState().isEmpty()) continue;
                                surf++;
                                if (!full(s)) partial++;
                            }
                            for (int[] d : n4) for (int y = feet; y < feet + Math.min(roof, 6); y++) {
                                BlockState s = wc.at(x + d[0], y, z + d[1]);
                                if (wc.pass(x + d[0], y, z + d[1]) || s.isAir()) continue;
                                surf++;
                                if (!full(s)) partial++;
                            }
                        }
                        levels.addAll(lv);
                        placesN++;
                    }
                }
                if (floors == 0) {
                    out.append("DEPTHBASE ").append(id).append(" ").append(w.kind()).append(" 0 (no world floor)\n");
                    continue;
                }
                double mean = roofs.stream().mapToInt(i -> i).average().orElse(0);
                double sd = Math.sqrt(roofs.stream().mapToDouble(i -> (i - mean) * (i - mean)).average().orElse(0));
                int rr = roofs.isEmpty() ? 0 : roofs.stream().mapToInt(i -> i).max().getAsInt() - roofs.stream().mapToInt(i -> i).min().getAsInt();
                out.append(String.format("DEPTHBASE %s %s %d %d %.1f %d %.2f %.1f %.1f %d %.1f %.2f%n", id, w.kind(), floors, cols,
                        100.0 * flat / Math.max(1, cols), levels.size(), 100.0 * partial / Math.max(1, surf), 100.0 * detail / Math.max(1, cols),
                        sd, rr, 100.0 * plumb / Math.max(1, edge), carve / Math.max(1, chunks)));
                out.append("DEPTHHIST ").append(id).append(' ').append(hist).append('\n');
            }
            dev.awaken.Awaken.LOG.info(out.toString());
            h.succeed();
        });
    }

    static boolean full(BlockState s) {
        return net.minecraft.world.level.block.Block.isShapeFullBlock(s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
    }
}
