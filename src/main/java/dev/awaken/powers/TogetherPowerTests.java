package dev.awaken.powers;

import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;

/** GameTests for powers among friends (SPEC "Together"): a cast that fizzles is heard by its caster only. */
final class TogetherPowerTests {
    private TogetherPowerTests() {}

    static void register() {
        AwakenTests.registerIsolated("together_powers_fizzle_is_private", 60, h -> {
            AwakenTests.Live caster = AwakenTests.survivalPlayer(h, "caster", new BlockPos(1, 1, 1));
            AwakenTests.Live friend = AwakenTests.survivalPlayer(h, "beside", new BlockPos(2, 1, 1));
            try {
                caster.sent();
                friend.sent();
                Casting.feedback(caster.player(), 0, "awaken:shockwave", "cooldown", false);
                boolean casterHeard = caster.sent().stream().anyMatch(TogetherPowerTests::isFail);
                boolean friendHeard = friend.sent().stream().anyMatch(TogetherPowerTests::isFail);
                h.assertTrue(casterHeard, "the caster hears their cast fizzle");
                h.assertFalse(friendHeard, "the friend standing beside them doesn't");
            } finally {
                caster.leave();
                friend.leave();
            }
            h.succeed();
        });
    }

    private static boolean isFail(Object packet) {
        return packet instanceof ClientboundSoundPacket s && s.getSound().value() == PowerSounds.FAIL.get();
    }
}
