package dev.awaken.body.client;

import dev.awaken.body.ascent.AscentPayloads;
import dev.awaken.body.ascent.client.AscentMoves;
import dev.awaken.progression.Progression;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.KeyText;
import dev.awaken.signal.client.Ui;
import dev.awaken.signal.client.UiSection;
import net.minecraft.client.Minecraft;

/**
 * The Ascent page of the band (progression plan §5.5, SPEC §27): the rungs you hold, with how to use each; the next one as
 * a silhouette that names where it comes from and the rank it waits for; the rest "???". A ladder, never a manual: what
 * a rung does is only told once it's yours.
 */
final class AscentSection extends UiSection {
    @Override public String title() { return "THE ASCENT"; }
    @Override public int order() { return 28; } // with the day's goals: it's the long one

    /** One rung: its name, how to use it once held, and where it comes from before then. */
    private record Rung(AscentPayloads.Tier tier, String name, String how, String where) {}

    private static final Rung[] LADDER = {
            new Rung(AscentPayloads.Tier.TETHER, "The Tether",
                    "Hold {key.awaken.rope} while looking at a ring or a ledge above you to throw your rope. Press {key.forward} to swing, and let go to fly off. "
                            + "Over a drop, press {key.sneak} with {key.jump} to lower yourself on it.",
                    "You get it from a Precursor vault once your rank is high enough to open its door (D-rank at least)."),
            new Rung(AscentPayloads.Tier.LEAPER, "The Leaper",
                    "In the air: {key.jump} jumps again, {key.sprint} dashes, {key.sneak} high up dives to a slam.",
                    "Get good with your rope and reach D-rank, and a special gate opens for you: the Leaper's Gauntlet."),
            new Rung(AscentPayloads.Tier.WINGS, "Wings",
                    "While falling, press {key.jump} to open your wings. Dive for speed and pull up to climb. Press {key.sneak} to fold them.",
                    "Climb the rings of a sky platform at C-rank, and its core gives you wings."),
            new Rung(AscentPayloads.Tier.SKYBORN, "The open sky",
                    "Gliding, press {key.jump} to fly: {key.forward} thrusts, {key.sprint} boosts.",
                    "Reach A-rank with plenty of practice on your wings, and the sky calls you to a trial."),
            new Rung(AscentPayloads.Tier.SOVEREIGN, "The Sovereign",
                    "When your Aether is full, press {key.awaken.ascend} to take your sovereign form for thirty seconds.",
                    "Finish the story to unlock it."),
    };

    @Override
    public boolean visible() {
        var p = Minecraft.getInstance().player;
        return p != null && Progression.profile(p).awakened();
    }

    @Override
    protected void build(Ui ui) {
        ui.text("Runner moves: you've had these from the start. Run at walls and edges to use them.", Glass.a(Glass.LABEL, 0.85F));
        ui.gap(2);
        boolean next = true;
        for (Rung r : LADDER) {
            if (AscentMoves.has(r.tier())) {
                ui.text(r.name(), Glass.a(Glass.TITLE, 1F));
                ui.text(KeyText.resolve(r.how()), Glass.a(Glass.LABEL, 0.85F));
                // the rope's fittings, once any is on it (SPEC §28.5)
                String kit = r.tier() == AscentPayloads.Tier.TETHER ? AscentMoves.fittings() : null;
                if (kit != null) ui.text(kit, Glass.a(Glass.HORIZON, 0.9F));
            } else if (next) {
                // the next rung: named, with where it comes from, never how it plays
                ui.text("◇ " + r.name() + " · next", Glass.a(Glass.HORIZON, 1F));
                ui.text(r.where(), Glass.a(Glass.TEXT, 0.9F));
                next = false;
            } else {
                ui.text("???", Glass.a(Glass.LABEL, 0.5F));
            }
            ui.gap(2);
        }
    }
}
