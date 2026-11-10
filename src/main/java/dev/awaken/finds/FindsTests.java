package dev.awaken.finds;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.finds.verb.Ctx;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The finds' checks (SPEC §58.9), each over every kind at once: the catalogue is whole; every scene stands (nothing
 * floats, nothing past its reach, enough of it isn't a plain cube); every kind's walkthrough finishes it and pays
 * once; and quiet land is quiet.
 */
final class FindsTests {
    private FindsTests() {}

    static void init() {
        AwakenTests.register("finds_catalogue_is_whole", 20, FindsTests::whole);
        AwakenTests.register("finds_every_scene_stands", 40, FindsTests::scenes);
        AwakenTests.registerIsolated("finds_every_kind_plays", 600, FindsTests::plays);
        AwakenTests.register("finds_quiet_land_is_quiet", 20, FindsTests::quiet);
    }

    /** Every set has its kinds numbered from 1 with no gaps, every kind has its words, a walkthrough and a loot table name. */
    private static void whole(GameTestHelper h) {
        Map<Kind.FSet, Set<Integer>> seen = new EnumMap<>(Kind.FSet.class);
        Set<String> names = new HashSet<>();
        for (Kind k : Kinds.all()) {
            h.assertTrue(seen.computeIfAbsent(k.set(), s -> new HashSet<>()).add(k.number()), k.id() + ": its number is taken in " + k.set());
            h.assertTrue(names.add(k.name()), "two finds are named " + k.name());
            h.assertTrue(!k.see().isBlank() && !k.learned().isBlank(), k.id() + " has its words");
            h.assertTrue(k.play() != null, k.id() + " has a walkthrough");
            h.assertTrue(k.rank() >= 0 && k.rank() <= 4 && k.reach() <= Sites.MAX_REACH, k.id() + ": rank and reach in range");
        }
        for (var e : seen.entrySet())
            for (int n = 1; n <= e.getValue().size(); n++) h.assertTrue(e.getValue().contains(n), e.getKey() + " has no number " + n);
        h.assertTrue(!Kinds.all().isEmpty(), "there are kinds");
        h.succeed();
    }

    /** What stands on its own: hanging things and leaves hang from what they touch, so one flood from the ground must reach everything. */
    private static String floats(Scene sc) {
        LongOpenHashSet all = new LongOpenHashSet(), reached = new LongOpenHashSet();
        LongArrayFIFOQueue q = new LongArrayFIFOQueue();
        for (var e : sc.blocks().long2ObjectEntrySet()) {
            if (e.getValue().isAir()) continue;
            long k = e.getLongKey();
            all.add(k);
            if (BlockPos.getY(k) <= sc.groundAt(BlockPos.getX(k), BlockPos.getZ(k)) + 1) {
                reached.add(k);
                q.enqueue(k);
            }
        }
        while (!q.isEmpty()) {
            long k = q.dequeueLong();
            int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
            for (int d = 0; d < 6; d++) {
                long n = BlockPos.asLong(x + (d == 0 ? 1 : d == 1 ? -1 : 0), y + (d == 2 ? 1 : d == 3 ? -1 : 0), z + (d == 4 ? 1 : d == 5 ? -1 : 0));
                if (all.contains(n) && reached.add(n)) q.enqueue(n);
            }
        }
        for (long k : all) if (!reached.contains(k)) return BlockPos.getX(k) + "," + BlockPos.getY(k) + "," + BlockPos.getZ(k) + " " + sc.blocks().get(k);
        return "";
    }

    private static void scenes(GameTestHelper h) {
        for (Kind k : Kinds.all())
            for (int i = 0; i < 6; i++) {
                Site s = Sites.forced(k, 0, 64, 0, i & 3, 77L * i + k.id().hashCode());
                Scene sc = Sites.build(s, Terrain.flat(64));
                h.assertTrue(!sc.blocks().isEmpty(), k.id() + " builds something");
                int above = 0, shaped = 0;
                for (var e : sc.blocks().long2ObjectEntrySet()) {
                    long key = e.getLongKey();
                    int x = BlockPos.getX(key), y = BlockPos.getY(key), z = BlockPos.getZ(key);
                    h.assertTrue(Math.abs(x) <= k.reach() && Math.abs(z) <= k.reach(), k.id() + " builds past its reach at " + x + "," + z);
                    BlockState b = e.getValue();
                    if (b.isAir() || y < 1) continue;
                    above++;
                    if (!b.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) shaped++;
                }
                for (var e : sc.parts().entrySet())
                    for (BlockPos p : e.getValue()) h.assertTrue(Math.abs(p.getX()) <= k.reach() && Math.abs(p.getZ()) <= k.reach(), k.id() + ": part " + e.getKey() + " past its reach");
                String f = floats(sc);
                h.assertTrue(f.isEmpty(), k.id() + " (seed " + i + ") has something floating at " + f);
                h.assertTrue(above == 0 || shaped * 3 >= above, k.id() + ": only " + shaped + " of " + above + " blocks above the ground aren't plain cubes (the bar is a third)");
                h.assertTrue(sc.blocks().get(BlockPos.asLong(0, -2, 0)) == null, k.id() + " builds where its heart goes");
            }
        h.succeed();
    }

    /** Every kind stood up on a pad and walked through by a scripted player: it finishes, pays that player once, and counts. */
    private static void plays(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos at = h.absolutePos(new BlockPos(0, 1, 0));
        AwakenTests.wideArea(h, at.offset(-56, 0, -56), at.offset(56, 0, 56));
        AwakenTests.onEnd(h, () -> {
            Ctx.testTicks = 0;
            Ctx.testHour = -1;
        });
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        int n = 0;
        for (Kind k : Kinds.all()) {
            if (k.play() == null) continue;
            Ctx.testTicks = 0;
            Ctx.testHour = 6000;
            Site s = FindsFeature.stamp(level, k, at, n & 3, 4242L + n);
            h.assertTrue(level.getBlockEntity(s.heart()) instanceof FindHeartBlockEntity, k.id() + " has its heart");
            FindHeartBlockEntity be = (FindHeartBlockEntity) level.getBlockEntity(s.heart());
            p.setData(FindsContent.FINDS, FindsData.EMPTY);
            p.snapTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 0, 0);
            try {
                k.play().run(new Trial(be, p));
            } catch (RuntimeException e) {
                h.fail(k.id() + ": the walkthrough broke: " + e);
            }
            h.assertTrue(be.done(), k.id() + ": the walkthrough doesn't finish it (stage " + be.stage() + ")");
            h.assertTrue(be.claimed(p.getUUID()), k.id() + " pays who finished it");
            h.assertTrue(p.getData(FindsContent.FINDS).count(k.id()) == 1, k.id() + " counts once: " + p.getData(FindsContent.FINDS).count(k.id()));
            h.assertTrue(p.getData(FindsContent.FINDS).paidAt(s.key()), k.id() + " remembers the site");
            Ctx c = be.ctx();
            h.assertTrue(c != null && !Reward.late(c, p), k.id() + " never pays the same player twice");
            // clear the pad for the next
            int r = k.reach() + 4;
            for (Entity e : level.getEntities((Entity) null, new AABB(at).inflate(r + 8, 40, r + 8), e -> !(e instanceof ServerPlayer))) e.discard();
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++)
                    for (int dy = -4; dy <= 24; dy++) level.setBlock(m.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            Sites.reset();
            n++;
        }
        h.assertTrue(n > 0, "some kind was played");
        h.succeed();
    }

    /** About three parts in ten of the land holds no curios at all, the home ring is never quiet, and lively land really is livelier. */
    private static void quiet(GameTestHelper h) {
        long seed = 90210L;
        int quiet = 0, total = 0;
        double sum = 0;
        for (int x = -20000; x <= 20000; x += 400)
            for (int z = -20000; z <= 20000; z += 400) {
                double m = Sites.richness(seed, x, z, 0, 0, 0);
                total++;
                if (m == 0) quiet++;
                sum += m;
                if ((long) x * x + (long) z * z < (long) Sites.HOME * Sites.HOME) h.assertTrue(m >= 0.9, "the home ring is never quiet (" + x + "," + z + ": " + m + ")");
            }
        double share = quiet / (double) total;
        h.assertTrue(share > 0.18 && share < 0.45, "quiet land is between a fifth and under a half of the world: " + share);
        h.assertTrue(sum / total > 0.35 && sum / total < 1.1, "the mean liveliness is sane: " + sum / total);
        h.succeed();
    }
}
