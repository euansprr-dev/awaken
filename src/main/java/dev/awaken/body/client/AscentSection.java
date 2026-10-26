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
                    "Hold {key.awaken.movement} at a ring or a ledge above: the rope bites. {key.forward} pumps the swing; let go to fly.",
                    "A Precursor vault keeps a line for a hunter it opens to: its door's rank, D at the least."),
            new Rung(AscentPayloads.Tier.LEAPER, "The Leaper",
                    "In the air: {key.jump} jumps again, {key.sprint} dashes, {key.sneak} high up dives to a slam.",
                    "Swing your rope until it knows you, at D-rank, and a gate will open for you: the Leaper's Gauntlet."),
            new Rung(AscentPayloads.Tier.WINGS, "Wings",
                    "Falling, press {key.jump} to open them. Dive for speed, pull up to climb. {key.sneak} folds them.",
                    "A sky platform's core gives them to a C-rank hunter who climbs its rings."),
            new Rung(AscentPayloads.Tier.SKYBORN, "The open sky",
                    "Gliding, press {key.jump} to fly: {key.forward} thrusts, {key.sprint} boosts.",
                    "At A-rank, with wings you know well, the sky will ask for you."),
            new Rung(AscentPayloads.Tier.SOVEREIGN, "The Sovereign",
                    "With your Aether full, press {key.awaken.ascend}: thirty seconds as the sky's own.",
                    "The story's end."),
    };

    @Override
    public boolean visible() {
        var p = Minecraft.getInstance().player;
        return p != null && Progression.profile(p).awakened();
    }

    @Override
    protected void build(Ui ui) {
        ui.text("Runner · yours from the start: run at walls and edges.", Glass.a(Glass.LABEL, 0.85F));
        ui.gap(2);
        boolean next = true;
        for (Rung r : LADDER) {
            if (AscentMoves.has(r.tier())) {
                ui.text(r.name(), Glass.a(Glass.TITLE, 1F));
                ui.text(KeyText.resolve(r.how()), Glass.a(Glass.LABEL, 0.85F));
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
