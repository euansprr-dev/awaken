package dev.awaken.mobs.entity;

import dev.awaken.core.api.CreatureTag;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.SeepHearts;
import dev.awaken.mobs.def.FaunaRole;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;

/**
 * The seep heart (W5), one test for its life: placed by the API at a socket (idempotent, asleep, a beast's level by the
 * normal path on a warden's body, persistent, its light at its core), rooted (a shove puts it back), woken by a player
 * near (the wake's moment, then awake and on them), and its fall (the long death, the first kill paid, its light gone).
 */
public final class SeepTests {
    private SeepTests() {}

    @SuppressWarnings("removal")
    public static void register() {
        AwakenTests.registerIsolated("mobs_seep_heart", 600, h -> {
            ServerLevel level = h.getLevel();
            BlockPos at = h.absolutePos(new BlockPos(0, WildTalentTests.UP, 0));
            WildTalentTests.floor(level, at, 8, Blocks.DEEPSLATE);
            LivingEntity e = SeepHearts.spawn(level, at.below());
            h.assertTrue(e instanceof SeepHeart, "the API spawns a seep heart, got " + e);
            SeepHeart heart = (SeepHeart) e;
            h.assertTrue(heart.socket().equals(at), "given the floor block, it stands on it: " + heart.socket() + " for " + at);
            h.assertTrue(SeepHearts.spawn(level, at) == heart, "a second call at the same socket gives the same heart");
            h.assertTrue(heart.state() == SeepHeart.ASLEEP && heart.flag(AwakenMob.F_DORMANT), "it spawns asleep");
            h.assertTrue(heart.role().role() == MobRole.Role.WARDEN, "a warden's body: " + heart.role());
            int want = Math.max(0, FaunaHooks.server().contentLevel(level, at, FaunaRole.BEAST));
            h.assertTrue(heart.contentLevel() == want, "a beast's level by the normal path (" + want + "), has " + heart.contentLevel());
            h.assertTrue(CreatureTag.of(heart).kind() == CreatureTag.Kind.BEAST, "the band calls it a beast: " + CreatureTag.of(heart));
            h.assertTrue(heart.isPersistenceRequired() && !heart.removeWhenFarAway(10_000), "it never despawns");
            heart.setPos(heart.getX() + 1.5, heart.getY() + 0.5, heart.getZ());
            int[] step = {0}, mark = {0}, tick = {0};
            ServerPlayer[] p = {null};
            h.succeedWhen(() -> {
                tick[0]++;
                switch (step[0]) {
                    case 0 -> {
                        h.assertTrue(heart.blockPosition().equals(at) && Math.abs(heart.getX() - (at.getX() + 0.5)) < 0.01, "a shove puts it back in its socket");
                        var light = level.getBlockState(at.above());
                        h.assertTrue(light.is(Blocks.LIGHT) && light.getValue(LightBlock.LEVEL) == 5, "a dim light at its core while it sleeps: " + light);
                        ServerPlayer sp = h.makeMockServerPlayerInLevel();
                        sp.setGameMode(GameType.SURVIVAL);
                        sp.snapTo(at.getX() + 6.5, at.getY(), at.getZ() + 0.5);
                        p[0] = sp;
                        step[0] = 1;
                        h.assertTrue(false, "a player comes near");
                    }
                    case 1 -> {
                        h.assertTrue(heart.state() == SeepHeart.WAKING, "it wakes for a player within " + SeepHeart.WAKE_RANGE + " blocks");
                        mark[0] = tick[0];
                        step[0] = 2;
                        h.assertTrue(false, "waking");
                    }
                    case 2 -> {
                        h.assertTrue(heart.state() == SeepHeart.AWAKE && !heart.flag(AwakenMob.F_DORMANT), "awake after its moment");
                        h.assertTrue(tick[0] - mark[0] >= SeepHeart.WAKE_TICKS - 2, "the wake takes its moment (" + (tick[0] - mark[0]) + " ticks)");
                        h.assertTrue(heart.getTarget() == p[0], "and turns on the player who woke it");
                        var light = level.getBlockState(at.above());
                        h.assertTrue(light.is(Blocks.LIGHT) && light.getValue(LightBlock.LEVEL) >= 10, "the room lights up: " + light);
                        heart.hurtServer(level, level.damageSources().playerAttack(p[0]), 1.0E6F);
                        mark[0] = tick[0];
                        step[0] = 3;
                        h.assertTrue(false, "felled");
                    }
                    default -> {
                        h.assertTrue(heart.isDeadOrDying(), "it died");
                        h.assertTrue(heart.isRemoved(), "still falling at " + (tick[0] - mark[0]) + " ticks");
                        h.assertTrue(tick[0] - mark[0] >= SeepHeart.DEATH_HOLD - 2, "its fall lasts its moment (" + (tick[0] - mark[0]) + " ticks)");
                        h.assertTrue(!level.getBlockState(at.above()).is(Blocks.LIGHT), "its light is gone");
                        h.assertTrue(Progression.profile(p[0]).counter("awaken:discovered/beast_felled/seep_heart") == 1, "the first kill is paid");
                        WildTalentTests.wipe(level, at.offset(-9, -1, -9), at.offset(9, 4, 9));
                    }
                }
            });
        });
    }
}
