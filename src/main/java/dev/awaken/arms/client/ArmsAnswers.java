package dev.awaken.arms.client;

import dev.awaken.arms.net.ArmsPayloads;
import dev.awaken.core.api.Strikes;

/**
 * How the local player's guard answered a blow, told to whoever draws about it (SPEC §67.2: the ring bursts on a
 * deflect and says "Too early" or "Too late" after a miss). Read off the server's own word, so it is never a guess.
 */
final class ArmsAnswers {
    private ArmsAnswers() {}

    static void tell(ArmsPayloads.Result r) {
        switch (r.what()) {
            case ArmsPayloads.DEFLECT -> Strikes.answered(Strikes.Answer.DEFLECT, r.entity());
            case ArmsPayloads.BLOCK -> Strikes.answered(Strikes.Answer.BLOCK, r.entity());
            case ArmsPayloads.GUARD_BREAK -> Strikes.answered(Strikes.Answer.GUARD_BREAK, r.entity());
            case ArmsPayloads.HURT -> Strikes.answered(Strikes.Answer.STRUCK, r.entity());
            default -> { }
        }
    }
}
