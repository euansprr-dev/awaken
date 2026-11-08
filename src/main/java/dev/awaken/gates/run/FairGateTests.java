package dev.awaken.gates.run;

import dev.awaken.Awaken;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.worlds.WorldLive;
import dev.awaken.mobs.entity.FairTests;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Fair damage in a gate (SPEC §47.14): what a gate does to you with no creature behind it, its traps and its falls,
 * landed on the reference player of every rank the gate comes at, alone and under Glass (everything doubled).
 */
public final class FairGateTests {
    private FairGateTests() {}

    /** One landing of a trap: 30% at most; under Glass, never half. */
    static final float TRAP = 0.30F, GLASS = 0.50F;

    private record Trap(String name, Function<ServerLevel, DamageSource> source, Function<ServerPlayer, Float> amount) {}

    public static void register() {
        AwakenTests.registerIsolated("fights_fair_gates", 200, h -> {
            ServerLevel level = h.getLevel();
            for (int x = -1; x <= 8; x++) for (int z = -1; z <= 3; z++) level.setBlockAndUpdate(h.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState());
            Function<ServerLevel, DamageSource> rubble = l -> l.damageSources().source(RigCollapseLive.RUBBLE);
            List<Trap> traps = List.of(
                    new Trap("the gauntlet's pit", rubble, p -> RigGauntletLive.PIT_DAMAGE + p.getMaxHealth() * RigGauntletLive.PIT_SHARE),
                    new Trap("the gauntlet's press", rubble, p -> RigGauntletLive.PRESS_DAMAGE + p.getMaxHealth() * RigGauntletLive.PRESS_SHARE),
                    new Trap("the collapse's pit", rubble, p -> RigCollapseLive.PIT_DAMAGE + p.getMaxHealth() * RigCollapseLive.PIT_SHARE),
                    new Trap("a cave-in's stone", rubble, p -> RigCaveIn.DAMAGE + p.getMaxHealth() * RigCaveIn.SHARE),
                    new Trap("a world's catch", l -> l.damageSources().fall(), p -> WorldLive.CATCH_DAMAGE));
            List<String> over = new ArrayList<>();
            Rank[] ranks = {Rank.E, Rank.D, Rank.C, Rank.B, Rank.A, Rank.S};
            for (int i = 0; i < ranks.length; i++) {
                ServerPlayer p = FairTests.reference(h, ranks[i], new BlockPos(1 + i, 1, 1));
                for (Trap t : traps) {
                    for (int glass = 1; glass <= 2; glass++) {
                        FairTests.pool(p);
                        p.hurtServer(level, t.source().apply(level), t.amount().apply(p) * glass);
                        float took = FairTests.share(p), budget = glass == 1 ? TRAP : GLASS;
                        if (glass == 1 || i == 0) Awaken.LOG.info("[fair] gate {} at {}{}: {}", t.name(), ranks[i].label, glass == 2 ? " under Glass" : "", FairTests.pct(took));
                        if (took > budget) over.add(t.name() + " at " + ranks[i].label + (glass == 2 ? " under Glass" : "") + ": " + FairTests.pct(took));
                    }
                }
            }
            h.assertTrue(over.isEmpty(), "over budget: " + over);
            h.succeed();
        });
    }
}
