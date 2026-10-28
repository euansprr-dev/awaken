package dev.awaken.powers.client.fx;

import dev.awaken.lens.client.LensClient;
import dev.awaken.powers.fx.CastProfiles;

/**
 * A power's Lens look (docs/power-anims/PLAN.md D8): the frame's grade for the cast's length, through the one moment channel Lens
 * has ({@link LensClient#override}), eased in and out. Yours only; never over a story's or Arms' moment (whoever holds the look
 * keeps it), and only while Lens draws ({@link LensClient#active()}): with Lens off the screen flash of the camera moment carries
 * the release. A channel's look holds until it lets go.
 */
final class CastLens {
    private CastLens() {}

    /** The look and how long it holds (ticks; a channel's is cut by its let-go). */
    private static String playing;
    private static int left;

    private static int holdFor(String look) {
        return switch (look) {
            case "awaken:cast_flow" -> 80;
            case "awaken:cast_foresight" -> 120;
            case "awaken:cast_overload" -> 36;
            case "awaken:cast_legion" -> 32;
            case "awaken:cast_citadel" -> 30;
            case "awaken:cast_void" -> 70;
            // the big level-5 forms (SPEC 34.5)
            case "awaken:awake_flow" -> 80;
            case "awaken:awake_gravity_well", "awaken:awake_overload" -> 40;
            case "awaken:awake_legion" -> 36;
            case "awaken:awake_nova" -> 30;
            default -> 24;
        };
    }

    /** The profile's release on your own screen. */
    static void release(CastProfiles.Profile prof) {
        if (prof.lens() == null || !LensClient.active()) return;
        String now = LensClient.overriding();
        if (now != null && !now.equals(playing)) return; // a story's or Arms' moment keeps the frame
        playing = prof.lens();
        left = prof.channel() ? 20 * 30 : holdFor(prof.lens());
        LensClient.override(playing, 4);
    }

    /** A channel let go. */
    static void end(CastProfiles.Profile prof) {
        if (playing != null && playing.equals(prof.lens())) left = Math.min(left, 1);
    }

    static void tick() {
        if (playing == null) return;
        if (!playing.equals(LensClient.overriding())) {
            playing = null; // someone else took the frame
            return;
        }
        if (--left <= 0) {
            LensClient.clearOverride(16);
            playing = null;
        }
    }

    static void reset() {
        if (playing != null && playing.equals(LensClient.overriding())) LensClient.clearOverride(4);
        playing = null;
        left = 0;
    }
}
