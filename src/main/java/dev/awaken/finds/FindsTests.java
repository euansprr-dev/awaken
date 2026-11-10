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

    /** Earth, living rock, sand, ice and water: the land itself, where a kind shapes a bank, a cliff face or a frozen fall. */
    private static boolean land(BlockState b) {
        return b.is(net.minecraft.tags.BlockTags.DIRT) || b.is(net.minecraft.tags.BlockTags.ICE) || b.is(net.minecraft.tags.BlockTags.SAND)
                || b.is(Blocks.GRASS_BLOCK) || b.is(Blocks.MUD) || b.is(Blocks.MOSS_BLOCK) || b.is(Blocks.PODZOL)
                || b.is(Blocks.STONE) || b.is(Blocks.TERRACOTTA) || b.is(Blocks.GRAVEL) || b.is(Blocks.SNOW_BLOCK) || !b.getFluidState().isEmpty();
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
        java.util.List<String> bad = new java.util.ArrayList<>();
        for (Kind k : Kinds.all())
            for (int i = 0; i < 6; i++) {
                Site s = Sites.forced(k, 0, 64, 0, i & 3, 77L * i + k.id().hashCode());
                Scene sc;
                try {
                    sc = Sites.build(s, Terrain.flat(64));
                } catch (RuntimeException e) {
                    bad.add(k.id() + " (seed " + i + ") doesn't build: " + e);
                    break;
                }
                String why = "";
                if (sc.blocks().isEmpty()) why = "builds nothing";
                int above = 0, shaped = 0;
                for (var e : sc.blocks().long2ObjectEntrySet()) {
                    long key = e.getLongKey();
                    int x = BlockPos.getX(key), y = BlockPos.getY(key), z = BlockPos.getZ(key);
                    if (Math.abs(x) > k.reach() || Math.abs(z) > k.reach()) why = "builds past its reach at " + x + "," + z;
                    BlockState b = e.getValue();
                    // what grew (leaves, trunks) and what the land is (earth, living rock, ice) is neither plain masonry nor
                    // shaped stone: it isn't counted either way
                    if (b.isAir() || y < 1 || b.is(net.minecraft.tags.BlockTags.LEAVES) || b.is(net.minecraft.tags.BlockTags.LOGS) || land(b)) continue;
                    above++;
                    if (!b.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) shaped++;
                }
                for (var e : sc.parts().entrySet())
                    for (BlockPos p : e.getValue()) if (Math.abs(p.getX()) > k.reach() || Math.abs(p.getZ()) > k.reach()) why = "part " + e.getKey() + " is past its reach";
                String f = floats(sc);
                if (!f.isEmpty()) why = "has something floating at " + f;
                else if (above > 0 && shaped * 3 < above) why = "only " + shaped + " of " + above + " blocks above the ground aren't plain cubes (the bar is a third)";
                if (sc.blocks().get(BlockPos.asLong(0, -2, 0)) != null) why = "builds where its heart goes";
                if (!why.isEmpty()) {
                    bad.add(k.id() + " (seed " + i + ") " + why);
                    break;
                }
            }
        h.assertTrue(bad.isEmpty(), bad.size() + " scenes fall short: " + String.join(" | ", bad.subList(0, Math.min(14, bad.size()))));
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
            Ctx.testWeather = 0;
        });
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        int n = 0;
        java.util.List<String> bad = new java.util.ArrayList<>();
        for (Kind k : Kinds.all()) {
            if (k.play() == null) continue;
            Ctx.testTicks = 0;
            Ctx.testHour = 6000;
            Ctx.testWeather = 1;
            Site s = FindsFeature.stamp(level, k, at, n & 3, 4242L + n);
            String why = "";
            if (!(level.getBlockEntity(s.heart()) instanceof FindHeartBlockEntity be)) why = "has no heart";
            else {
                p.setData(FindsContent.FINDS, FindsData.EMPTY);
                p.snapTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 0, 0);
                try {
                    k.play().run(new Trial(be, p));
                } catch (RuntimeException e) {
                    why = "its walkthrough broke: " + e;
                }
                Ctx c = be.ctx();
                if (!why.isEmpty()) {
                    // said already
                } else if (!be.done()) why = "its walkthrough doesn't finish it (stage " + be.stage() + ")";
                else if (!be.claimed(p.getUUID())) why = "doesn't pay who finished it";
                else if (p.getData(FindsContent.FINDS).count(k.id()) != 1) why = "counts " + p.getData(FindsContent.FINDS).count(k.id()) + " times";
                else if (!p.getData(FindsContent.FINDS).paidAt(s.key())) why = "doesn't remember the site";
                else if (c == null || Reward.late(c, p)) why = "pays the same player twice";
            }
            if (!why.isEmpty()) bad.add(k.id() + " " + why);
            // clear the pad for the next
            int r = k.reach() + 4;
            for (Entity e : level.getEntities((Entity) null, new AABB(at).inflate(r + 8, 40, r + 8), e -> !(e instanceof ServerPlayer))) e.discard();
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++)
                    for (int dy = -8; dy <= 24; dy++) level.setBlock(m.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            Sites.reset();
            n++;
        }
        h.assertTrue(bad.isEmpty(), bad.size() + " finds don't play through: " + String.join(" | ", bad.subList(0, Math.min(14, bad.size()))));
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
