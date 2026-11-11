package dev.awaken.arms.client;

import dev.awaken.arms.net.ArmsPayloads;
import dev.awaken.core.api.Strikes;

/**
 * How the local player's guard answered a blow, told to whoever draws about it (SPEC §68.2: the ring bursts on a
 * deflect and says why after a miss, §68.6). Read off the server's own word, so it is never a guess.
 */
final class ArmsAnswers {
    private ArmsAnswers() {}

    static void tell(ArmsPayloads.Result r) {
        switch (r.what()) {
            case ArmsPayloads.DEFLECT -> Strikes.answered(Strikes.Answer.DEFLECT, r.entity());
            case ArmsPayloads.BLOCK -> Strikes.answered(Strikes.Answer.BLOCK, r.entity(), miss(r.aux()));
            case ArmsPayloads.GUARD_BREAK -> Strikes.answered(Strikes.Answer.GUARD_BREAK, r.entity(), miss(r.aux()));
            case ArmsPayloads.GUARD_PASS -> Strikes.answered(Strikes.Answer.PASSED, r.entity(),
                    r.aux() == ArmsPayloads.PASS_UNGUARDABLE ? Strikes.Why.UNGUARDABLE : Strikes.Why.SIDE);
            case ArmsPayloads.HURT -> Strikes.answered(Strikes.Answer.STRUCK, r.entity());
            default -> { }
        }
    }

    /** The server's reason a guard only blocked (SPEC §68.6). */
    private static Strikes.Why miss(int aux) {
        return switch (aux) {
            case dev.awaken.arms.ArmsRules.MISS_TOO_SOON -> Strikes.Why.TOO_SOON;
            case dev.awaken.arms.ArmsRules.MISS_LATE -> Strikes.Why.LATE;
            default -> Strikes.Why.EARLY;
        };
    }
}
