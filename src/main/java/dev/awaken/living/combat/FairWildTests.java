package dev.awaken.living.combat;

import dev.awaken.Awaken;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.CreatureTag;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.entity.FairTests;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Fair damage in the land (SPEC §47.14): a creeper's blast is vanilla's, the same at every level, and a block off it took
 * more than an E player has. The land's creatures carry its level, so the fair guard holds it to half for a player who
 * belongs there; a formed creeper's seed (rung 3, Combos) the same.
 */
public final class FairWildTests {
    private FairWildTests() {}

    public static void register() {
        AwakenTests.registerIsolated("fights_fair_wilds", 200, h -> {
            ServerLevel level = h.getLevel();
            Rank[] ranks = {Rank.E, Rank.D, Rank.C, Rank.B, Rank.A, Rank.S};
            for (int x = -1; x <= ranks.length * 14; x++) for (int z = -1; z <= 4; z++) level.setBlockAndUpdate(h.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState());
            List<String> over = new ArrayList<>();
            for (int i = 0; i < ranks.length; i++) {
                ServerPlayer p = FairTests.reference(h, ranks[i], new BlockPos(2 + 14 * i, 1, 2));
                int lv = ranks[i].contentLevel;
                for (float power : new float[]{3F, 1.6F}) {
                    Creeper c = EntityTypes.CREEPER.create(level, EntitySpawnReason.MOB_SUMMONED);
                    h.assertTrue(c != null, "a creeper");
                    Vec3 at = p.position().add(1.5, 0, 0);
                    c.snapTo(at.x, at.y, at.z, 0, 0);
                    c.setNoAi(true);
                    c.setData(CoreApi.CREATURE_TAG.get(), new CreatureTag(lv, CreatureTag.Kind.MONSTER, false, ""));
                    Creatures.applyScaling(c, lv, false);
                    level.addFreshEntity(c);
                    FairTests.pool(p);
                    level.explode(c, at.x, at.y + 0.1, at.z, power, Level.ExplosionInteraction.NONE);
                    String what = power == 3F ? "a creeper's blast" : "a formed creeper's seed";
                    Awaken.LOG.info("[fair] wild {} at {}, a block and a half off: {} of its own, {} landed", what, ranks[i].label,
                            FairTests.pct(FairTests.raw(p)), FairTests.pct(FairTests.share(p)));
                    if (FairTests.share(p) > 0.5F + 1.0E-3F) over.add(what + " at " + ranks[i].label + ": " + FairTests.pct(FairTests.share(p)));
                    c.discard();
                }
            }
            h.assertTrue(over.isEmpty(), "more than half landed: " + over);
            h.succeed();
        });
    }
}
