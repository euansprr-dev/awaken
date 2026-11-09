package dev.awaken.hall.client;

import dev.awaken.arms.Archetype;
import dev.awaken.arms.Archetypes;
import dev.awaken.arms.LordForged;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** What makes a kind of weapon different, in plain words, from the numbers the blows use (SPEC §52). */
final class HallSpecial {
    private HallSpecial() {}

    static List<String> of(String style) {
        Archetypes.Loaded l = Archetypes.get(style);
        if (l == null) return List.of();
        Archetype a = l.def();
        Archetype.Traits t = a.traits();
        Archetype.Guard g = a.guard();
        Archetype.MoveDef heavy = l.move(Archetype.Move.HEAVY), counter = l.move(Archetype.Move.RIPOSTE);
        List<String> out = new ArrayList<>(3);
        if (t.backstab() > 1F) out.add("Get behind something and every hit lands " + n(t.backstab()) + " times as hard.");
        if (t.drawIdle() > 0) out.add("Leave it sheathed for " + n(t.drawIdle() / 20F) + " seconds and your next cut lands " + n(t.drawMotion())
                + " times as hard and shakes their stance " + n(t.drawPosture()) + " times as much.");
        if (t.spirit() > 1F) out.add("Hits spirits and other things without a body " + n(t.spirit()) + " times as hard.");
        if (t.pull() > 0F) out.add("Drags what it hits up to " + n(t.pull()) + " blocks towards you.");
        if (t.energy() > 0F) out.add("Every blow gives " + n(t.energy()) + " energy back to your powers.");
        if (t.reachPerStep() > 0F) out.add("Each strike in a combo reaches " + n(t.reachPerStep()) + " of a block further than the last.");
        if (t.riposteThrow() > 0F) out.add((style.equals("shieldblade") ? "Blocks " + pct(g.reduction()) + " of a blow across the widest guard of any weapon, and a" : "A")
                + " counter throws what you caught.");
        if (out.isEmpty()) plain(style, g, heavy, counter, out);
        Archetype.MoveDef art = l.move(Archetype.Move.ART);
        if (art != null) out.add(art(art));
        return out;
    }

    /** A kind with no trait of its own is told apart by what its numbers do best. */
    private static void plain(String style, Archetype.Guard g, Archetype.MoveDef heavy, Archetype.MoveDef counter, List<String> out) {
        if (heavy == null) return;
        switch (style) {
            case "longsword" -> out.add("Blocking takes " + pct(g.reduction()) + " off a blow, and every strike is as good as the last. It does nothing badly.");
            case "axe" -> out.add("A heavy swing breaks stances: it takes " + heavy.posture() + " off one in a blow.");
            case "maul" -> out.add("Its heavy slams the ground all round you and takes " + heavy.posture() + " off every stance it reaches.");
            case "spear" -> out.add("Its heavy reaches " + n(heavy.reach()) + " blocks, further than anything else you can hold.");
            case "greatblade" -> out.add("Blocks " + pct(g.reduction()) + " of a blow, and its heavy takes " + heavy.posture() + " off a stance.");
            case "rapier" -> out.add("A counter takes " + (counter == null ? heavy.posture() : counter.posture()) + " off a stance, and the moment to deflect lasts longer than with a longsword.");
            case "glaive" -> out.add("Its heavy sweeps a full circle " + n(heavy.reach()) + " blocks across.");
            case "twinblades" -> out.add("The last strike of a combo spins you through everything close.");
            default -> {}
        }
    }

    private static String art(Archetype.MoveDef art) {
        String shape = art.arc() >= 300F ? "a full circle" : art.arc() <= 40F ? "a straight thrust" : "a wide cut";
        String wave = art.art().filter(d -> d.wave() > 0F).map(d -> ", and sends a wave out to " + n(d.range()) + " blocks").orElse("");
        return "Its Art is " + shape + " " + n(art.reach()) + " blocks long" + wave + ".";
    }

    static List<String> lord(LordForged.Forged f) {
        LordForged.Signature s = f.art();
        return List.of("Its Art ends in " + s.name() + ", " + n(s.radius()) + " blocks across, at " + n(s.power()) + " times your attack.");
    }

    private static String pct(float v) {
        return Math.round(v * 100F) + "%";
    }

    static String n(float v) {
        return Math.abs(v - Math.round(v)) < .05F ? Integer.toString(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
    }
}
