package dev.awaken.anims;

import java.util.List;

/** The view clips the anims module plays itself (Hands P4 and P5 touches); a GameTest holds them to the file. */
public final class AnimsClips {
    private AnimsClips() {}

    public static final String PUSH = "fp_push", LEVER = "fp_lever", BUTTON = "fp_button", PLUCK = "fp_pluck", PET = "fp_pet", LID = "fp_lid",
            HAMMER = "fp_hammer", LAY = "fp_lay";

    /** Every touch; each but the lid has an "_off" twin for the off hand. */
    public static final List<String> TOUCHES = List.of(PUSH, LEVER, BUTTON, PLUCK, PET, LID, HAMMER, LAY);
}
