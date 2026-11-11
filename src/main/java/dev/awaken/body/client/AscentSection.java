package dev.awaken.body.client;

import dev.awaken.body.BodyConfig;
import dev.awaken.body.ascent.AscentMemory;
import dev.awaken.body.ascent.AscentPayloads;
import dev.awaken.body.ascent.WayUp;
import dev.awaken.body.ascent.client.AscentMoves;
import dev.awaken.body.ascent.client.WayClient;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.KeyText;
import dev.awaken.signal.client.Ui;
import dev.awaken.signal.client.UiSection;
import net.minecraft.client.Minecraft;

/**
 * The ladder on the band (K, Powers; SPEC §27.2, rewritten by The Way Up, §63.3): a row a rung you hold, its keys in your
 * own bindings and its mastery as a bar; the next rung as a dark diamond with one line of where it waits (the kind of
 * place, never what the move is, never a fight); the rest dark. What a rung does is only told once it's yours. The moves
 * Rope and Wings added to a rung (SPEC §62) are told the same way: a line each once its mastery is reached, and before
 * that only that another move waits, and at which mastery.
 */
final class AscentSection extends UiSection {
    @Override public String title() { return "THE ASCENT"; }
    @Override public int order() { return 28; } // with the day's goals: it's the long one

    /** One rung: its name, its keys once held, and the moves its mastery opens. */
    private record Row(AscentPayloads.Tier tier, AscentMemory.Track track, WayUp.Rung rung, String name, String keys, Extra... extras) {}

    /** A move a rung's mastery opens (0: it comes with the rung), and how it is made. */
    private record Extra(int level, String keys) {}

    private static final Row[] LADDER = {
            new Row(AscentPayloads.Tier.TETHER, AscentMemory.Track.TETHER, WayUp.Rung.ROPE, "The rope",
                    "Hold [{key.awaken.rope}] to swing. Let go to fly. Over a drop, [{key.sneak}] with [{key.jump}] lowers you.",
                    new Extra(0, "Where nothing is tall enough to swing from, hold [{key.awaken.rope}] at a top ahead. It throws you up and over."),
                    new Extra(dev.awaken.body.ascent.AirRules.SPRING_LEVEL, "As the rope pulls you up onto a top, hold [{key.forward}] to spring off it."),
                    new Extra(dev.awaken.body.ascent.AirRules.STRIKE_LEVEL, "From the rope or the air, [{key.attack}] at a creature out of reach. The rope pulls you into it.")),
            new Row(AscentPayloads.Tier.LEAPER, AscentMemory.Track.LEAPER, WayUp.Rung.LEAP, "The leap",
                    "In the air: [{key.jump}] again, [{key.sprint}] to dash, [{key.sneak}] to slam from high up."),
            new Row(AscentPayloads.Tier.WINGS, AscentMemory.Track.WINGS, WayUp.Rung.WINGS, "Wings",
                    "In a fall: [{key.jump}] twice to open them. Look to steer, dive for speed. [{key.sneak}] folds them.",
                    new Extra(0, "Come down slow and level, and they set you on your feet."),
                    new Extra(dev.awaken.body.ascent.AirRules.SKIM_LEVEL, "Fly low and fast, and the air under you carries you much further.")),
            new Row(AscentPayloads.Tier.SKYBORN, AscentMemory.Track.SKYBORN, WayUp.Rung.SKY, "The open sky",
                    "On open wings: [{key.jump}] again to fly, [{key.forward}] to push on, [{key.sprint}] to boost.",
                    new Extra(0, "From the ground: look up, hold [{key.sneak}], then [{key.jump}].")),
            new Row(AscentPayloads.Tier.SOVEREIGN, AscentMemory.Track.SOVEREIGN, WayUp.Rung.FORM, "The Sovereign's form",
                    "When the gold line is full: [{key.awaken.ascend}], for thirty seconds."),
    };

    @Override
    public boolean visible() {
        var p = Minecraft.getInstance().player;
        return p != null && Progression.profile(p).awakened();
    }

    /** Where the next rung waits, in a Lead's voice. Empty: nothing may be said of it yet (a rank fight is never named). */
    private static String lead(Row r) {
        var p = Minecraft.getInstance().player;
        int rank = p == null ? 0 : Progression.profile(p).rank().ordinal();
        boolean way = BodyConfig.wayUp();
        return switch (r.rung()) {
            case ROPE -> !way ? "A Precursor vault keeps it for a hunter of D-rank or more."
                    : rank < Rank.D.ordinal() ? "A vault keeps it. It waits for a hunter of a higher rank."
                    : "A vault has lit up for the chain you're carrying. Stand between its two pylons.";
            case LEAP -> !way ? "Get to know your rope, and a gate will open for you."
                    : WayClient.gateOpen() ? "A gate has opened where you climbed. It is a course, not a fight."
                    : "It waits for a hunter who climbs something tall by rope: a Tether Spire, a great tree.";
            case WINGS -> rank < Rank.C.ordinal() ? "A sky platform keeps it. It waits for a hunter of a higher rank."
                    : "A sky platform has lit up for you. Its rings climb to it by rope.";
            default -> "";
        };
    }

    @Override
    protected void build(Ui ui) {
        ui.text("Runner moves: you've had these from the start. Run at walls and edges to use them.", Glass.a(Glass.LABEL, 0.85F));
        ui.gap(2);
        boolean next = true;
        for (Row r : LADDER) {
            if (AscentMoves.has(r.tier())) {
                int level = AscentMoves.level(r.track());
                ui.pair(r.name(), "Mastery " + level + " of 10", Glass.a(Glass.TITLE, 1F), Glass.a(Glass.LABEL, 0.8F));
                ui.bar(level / 10.0, Glass.a(Glass.EDGE, 0.9F), 80);
                ui.text(KeyText.resolve(r.keys()), Glass.a(Glass.LABEL, 0.9F));
                int more = 0;
                for (Extra x : r.extras()) {
                    if (level >= x.level()) {
                        ui.text(KeyText.resolve(x.keys()), Glass.a(Glass.LABEL, 0.9F));
                    } else {
                        more = x.level();
                        break;
                    }
                }
                // the rope's fittings, once any is on it (SPEC §28.5)
                String kit = r.tier() == AscentPayloads.Tier.TETHER ? AscentMoves.fittings() : null;
                if (kit != null) ui.text(kit, Glass.a(Glass.HORIZON, 0.9F));
                if (BodyConfig.wayUp() && !WayClient.isDone(r.rung()) && r.rung() != WayUp.Rung.SKY && r.rung() != WayUp.Rung.FORM) {
                    ui.text("You haven't used it yet.", Glass.a(Glass.AMBER, 0.9F));
                }
                // the next move only: that it waits, never what it is
                if (more > 0) ui.text("◆ Another move at mastery " + more + ".", Glass.a(Glass.HORIZON, 0.85F));
            } else if (next) {
                // the next rung: a dark diamond and where it waits; never how it plays
                String lead = lead(r);
                ui.text("◆ Next", Glass.a(Glass.HORIZON, 1F));
                if (!lead.isEmpty()) ui.text(lead, Glass.a(Glass.TEXT, 0.9F));
                next = false;
            } else {
                ui.text("◆", Glass.a(Glass.LABEL, 0.35F));
            }
            ui.gap(2);
        }
    }
}
