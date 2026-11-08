package dev.awaken.mobs.entity;

import dev.awaken.Awaken;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.boss.KitMoves;
import dev.awaken.mobs.boss.Kits;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.core.api.MobRole;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Set;

/** A blow aimed at you lands if you don't answer it (SPEC §47.15): a real fight against someone standing still. */
public final class FightReachTests {
    private FightReachTests() {}

    public static void register() {
        for (String id : new String[]{"hive_regent", "sky_leviathan", "gearwright_colossus", "ember_tyrant", "glass_serpent"}) {
            AwakenTests.registerIsolated("fights_reach_live_" + id, 1400, h -> live(h, id));
        }
        AwakenTests.register("fights_reach_table", 20, FightReachTests::table);
    }

    /** Every kit's every own swing against someone standing still: the share of its distances it can be aimed to reach. */
    private static void table(GameTestHelper h) {
        BlowAim.Result out = new BlowAim.Result();
        int moves = 0, points = 0, bare = 0, aimed = 0, never = 0;
        StringBuilder worst = new StringBuilder();
        for (Kits.Kit kit : Kits.all().values()) {
            MobDef def = Bestiary.get(kit.id());
            float half = def.width * def.scale * 0.5F, hip = def.height * def.scale * AwakenBoss.KIT_LEAN_HIP;
            for (KitMoves.Move m : kit.moves().moves()) {
                if (m.hits().isEmpty() || m.ability().isPresent()) continue;
                moves++;
                int here = 0, n = 0;
                float sign = behind(m) ? -1F : 1F;
                for (float d = m.minRange(); d <= m.maxRange() + 1.0E-3F; d += 0.5F) {
                    n++;
                    double z = sign * (d + half + 0.3);
                    if (BlowAim.bare(m, hip, 0, z, 0, 1.8, 0.3)) bare++;
                    if (BlowAim.solve(m, hip, 0, z, 0, 1.8, 0.3, def.flies(), 0F, true, Math.max(0, d - 0.1), out)) here++;
                }
                points += n;
                aimed += here;
                if (here == 0) {
                    never++;
                    worst.append(kit.id()).append('/').append(m.id()).append(' ');
                }
            }
        }
        Awaken.LOG.info("[reach] table: {} moves, {} places they begin from: {}% land as drawn, {}% once aimed; {} can't reach anyone: {}", moves, points,
                Math.round(100F * bare / points), Math.round(100F * aimed / points), never, worst);
        h.assertTrue(aimed >= points * 0.75F, "aimed blows reach " + aimed + " of " + points + " places");
        h.succeed();
    }

    /** A move swung behind the body (its sweep lies at its back). */
    private static boolean behind(KitMoves.Move m) {
        float sum = 0;
        int n = 0;
        for (var frame : m.hits()) for (var c : frame) {
            sum += c.az() + c.bz();
            n += 2;
        }
        return n > 0 && sum / n < -0.3F;
    }

    /** The share of blows that must land on someone standing still (a blow begun as a phase turns may not), and how high a flier may rise over them. */
    private static final float LANDS = 0.7F;
    private static final double HIGH = 3.5;

    private static void live(GameTestHelper h, String id) {
        ServerLevel level = h.getLevel();
        for (int x = -2; x <= 44; x++) for (int z = -2; z <= 44; z++) level.setBlockAndUpdate(h.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState());
        ServerPlayer p = FairTests.reference(h, Rank.E, new BlockPos(21, 1, 21));
        MobDef def = Bestiary.get(id);
        AwakenMob m = Mobs.spawn(level, def, h.absoluteVec(new Vec3(21.5, 1, 11.5)), 10,
                new MobRole(MobRole.Role.BOSS, 10, def.theme.id, Optional.of(def.bossId()), ""), Set.of());
        h.assertTrue(m instanceof AwakenBoss, id + " spawned as a boss");
        AwakenBoss boss = (AwakenBoss) m;
        boss.setTarget(p);
        FairTests.pool(p);
        int[] begun = {0}, landed = {0}, airborne = {0}, tick = {0};
        double[] high = {0}, aboveAtBegin = {0};
        String[] was = {null};
        boolean[] melee = {false};
        StringBuilder sb = new StringBuilder();
        h.onEachTick(() -> {
            tick[0]++;
            boss.setTarget(p);
            p.setHealth(p.getMaxHealth());
            double above = boss.getY() - p.getY();
            high[0] = Math.max(high[0], above);
            if (above > 3) airborne[0]++;
            if (tick[0] == 450) boss.setHealth(boss.getMaxHealth() * 0.6F);
            if (tick[0] == 900) boss.setHealth(boss.getMaxHealth() * 0.3F);
            String now = boss.kitPlaying();
            if (now == null ? was[0] != null : !now.equals(was[0])) {
                if (was[0] != null && melee[0]) {
                    boolean hit = FairTests.share(p) > 1.0E-3F;
                    if (hit) landed[0]++;
                    sb.append(was[0]).append(hit ? "+ " : "- ");
                }
                was[0] = now;
                melee[0] = false;
                if (now != null) {
                    Kits.Kit kit = boss.kit();
                    KitMoves.Move mv = kit.moves().get(kit.moves().index(now));
                    melee[0] = !mv.hits().isEmpty();
                    if (melee[0]) {
                        begun[0]++;
                        aboveAtBegin[0] = Math.max(aboveAtBegin[0], above);
                        FairTests.pool(p);
                    }
                }
            }
        });
        h.runAfterDelay(1350, () -> {
            Awaken.LOG.info("[reach] live {}: melee {} begun, {} landed on someone standing still; highest {} blocks over them ({} above at a blow), {} ticks more than 3 over; {}",
                    id, begun[0], landed[0], String.format("%.1f", high[0]), String.format("%.1f", aboveAtBegin[0]), airborne[0], sb);
            h.assertTrue(begun[0] >= 8 && landed[0] >= begun[0] * LANDS, id + " landed " + landed[0] + " of " + begun[0] + " blows on someone standing still: " + sb);
            h.assertTrue(!def.flies() || aboveAtBegin[0] < HIGH, id + " began a blow " + aboveAtBegin[0] + " blocks over them");
            boss.discard();
            h.succeed();
        });
    }
}
