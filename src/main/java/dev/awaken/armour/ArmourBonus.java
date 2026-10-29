package dev.awaken.armour;

import dev.awaken.gear.SetBonus;

import java.util.ArrayList;
import java.util.List;

/**
 * What wearing a set does, in plain words (SPEC §44.5, §44.6): two pieces give the archetype's half bonus (a Hunter set its
 * own), four the archetype's ability and the set's own twist (a Lord-forged set the Lord's move). For tooltips and the
 * System's naming of a set.
 */
public final class ArmourBonus {
    private ArmourBonus() {}

    public record Line(int pieces, String text) {}

    public static String half(Archetype a) {
        return switch (a) {
            case BULWARK -> "+1 heart";
            case STRIDER -> "you land lighter: falls hurt a third less";
            case CHANNELER -> "your powers hit 10% harder";
            case DUELIST -> "you swing 6% quicker";
            case LORD -> "+1 heart, and blows push you back less";
        };
    }

    /** The archetype's ability, or null for the Lord-forged (each Lord's own move). */
    public static String ability(Archetype a) {
        return switch (a) {
            case BULWARK -> "Bastion: blows you guard or take fill it. Full, your next deflect, block or perfect heavy bursts a shockwave that throws back and staggers everyone near";
            case STRIDER -> "Flow: perfect dodges, wall-runs and slides fill it. Full, the next blow that would land strikes an afterimage instead, and you run on faster";
            case CHANNELER -> "Runes: each power you cast leaves a rune circling you, up to three. Cast with three and they fly out at the foes nearest you";
            case DUELIST -> "Edge: deflects, finishers and clean hits fill it. Full, your next strike sends a cutting wave along its swing";
            case LORD -> "";
        };
    }

    /** The set's own twist (the Lord-forged: the Lord's move), or null where the set has none of its own yet. */
    public static String twist(ArmourSet s) {
        return switch (s) {
            case BELLGUARD -> "Ring: deflect a blow and your armour rings like a bell, staggering everyone close to you";
            case ROOTWARDEN -> "Rooted: cast while standing still and nothing can knock you back for two seconds, and you mend a little";
            case AMBERWING -> "Tempo: a deflect or a perfect dodge quickens your feet and your blade for three seconds";
            case CLEFTRUNNER -> "Slipstream: come out of a wall-run or a slide running faster for a moment";
            case LANTERNLIGHT -> "Kindle: while your runes burn, anyone who strikes you up close catches fire";
            case BLADEWARD -> "Parry Ward: after a deflect, blows that land in the next two seconds do half";
            case HORNLORD -> "Gore: sprint for a moment and you lower your horns. The first creature you run into is gored and thrown";
            case VAULTBREAKER -> "Vault Wall: a blow you guard glances off your plate and throws whoever struck it back, reeling";
            case FROSTWALKER -> "Rime Path: still water freezes under your feet as you go, and a perfect dodge breathes frost that slows everyone close";
            case SUNWARD -> "Noon: when you overchannel, the sun at your back flares. Foes near catch fire, and you and your friends near are mended";
            case SHARDCROWN -> "Shatter: when a blow takes you under half health, crystal bursts out of your armour, cutting and throwing everyone close, and for three seconds blows do a third less";
            case DEEPCARAPACE -> "Shell: crouch and stand still and the shell closes. Blows do 40% less, and each one fills Bastion twice as much";
            case CLOUDRUNNER -> "Featherfall: falls never hurt you, and crouching in the air you drift down like a feather";
            case TIDEBREAKER -> "Undertow: your cutting wave drags those it cuts toward you, and in water or rain your Edge fills twice as fast";
            case HOLLOWHEART -> "Hollow Pulse: every fifth blow you land, the caged heart beats. A red wave hurts everyone close, and you mend for each one it strikes";
            default -> null;
        };
    }

    /** What the archetype's ability has grown into at the set's rank (SPEC §44.5, "By rank"), or null below C. */
    public static @org.jspecify.annotations.Nullable String growth(ArmourSet s) {
        if (s.archetype == Archetype.LORD || s.rank.ordinal() < dev.awaken.progression.Rank.C.ordinal()) return null;
        boolean b = s.rank.ordinal() >= dev.awaken.progression.Rank.B.ordinal();
        return switch (s.archetype) {
            case BULWARK -> "A hard landing bursts a full Bastion too" + (b ? ", and the burst carries the Force of your last power hit" : "");
            case STRIDER -> "Foes close to your afterimage swing at it and reel" + (b ? ", and an afterimage left in the air rushes you on" : "");
            case CHANNELER -> "Each rune stops one arrow or bolt" + (b ? ", and an overchannel gives back a quarter of your energy" : "");
            case DUELIST -> "A perfect dodge with Edge full cuts back at once" + (b ? ", and the wave leaves those it cuts bleeding" : "");
            case LORD -> null;
        };
    }

    public static List<Line> lines(ArmourSet s) {
        List<Line> out = new ArrayList<>();
        if (s.hunter != null) {
            for (SetBonus.Line l : SetBonus.lines(s.hunter)) out.add(new Line(l.pieces(), l.text()));
            if (s.archetype != Archetype.LORD) out.add(new Line(SetBonus.FULL, ability(s.archetype)));
            if (growth(s) != null) out.add(new Line(SetBonus.FULL, growth(s)));
            return out;
        }
        out.add(new Line(SetBonus.HALF, half(s.archetype)));
        if (s.archetype != Archetype.LORD) out.add(new Line(SetBonus.FULL, ability(s.archetype)));
        if (growth(s) != null) out.add(new Line(SetBonus.FULL, growth(s)));
        String t = twist(s);
        if (t != null) out.add(new Line(SetBonus.FULL, t));
        return out;
    }
}
