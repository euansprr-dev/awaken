package dev.awaken.living.combat;

import java.util.Comparator;
import java.util.List;

/**
 * Which creatures wear a plate, and how visible each is (SPEC §21.1). Pure, so the client draws by it and a GameTest
 * holds it: every creature in a fight with the player first (nearest first), then the one under the crosshair, then
 * elites and named beasts the player has seen within {@link #NOTABLE_RANGE}; never more than {@link #MAX}.
 */
public final class PlateRules {
    private PlateRules() {}

    public static final int MAX = 4;
    /** A fight: the creature hurt the player, or the player hurt it, this recently. */
    public static final int FIGHT_TICKS = 120;
    /** After a fight the plate fades over this long; looking away from a calm creature, over {@link #LOOK_FADE}. */
    public static final int FIGHT_FADE = 60, LOOK_FADE = 12;
    public static final double NOTABLE_RANGE = 16;

    /**
     * How a creature measures against you, for the colour of its level chip (SPEC §21.10, Euan 2026-10-27): {@code TRIVIAL} grey
     * (ten or more levels below you), {@code EASY} blue (about your level or below: easy to kill), {@code FIGHT} orange (five or
     * more above: a good fight that takes skill), {@code HARD} red (fifteen or more above: you can win it, but only as an
     * insane fighter). The same for every creature, animals included.
     */
    public enum Heat { TRIVIAL, EASY, FIGHT, HARD }

    public static final int TRIVIAL_GAP = 10, FIGHT_GAP = 5, HARD_GAP = 15;

    /**
     * Whether a creature's plate shows its level chip: every creature that has one, animals and small wildlife included (Euan,
     * 2026-10-27: a cow in E land read as vanilla with no level). A meek animal's chip is its land's level and reads grey or blue.
     */
    public static boolean showsLevel(dev.awaken.core.api.CreatureTag tag) {
        return tag.kind() != dev.awaken.core.api.CreatureTag.Kind.NONE && tag.level() > 0;
    }

    public static Heat heat(int creatureLevel, int mine) {
        int gap = creatureLevel - mine;
        return gap <= -TRIVIAL_GAP ? Heat.TRIVIAL : gap < FIGHT_GAP ? Heat.EASY : gap < HARD_GAP ? Heat.FIGHT : Heat.HARD;
    }

    /** How far the band reads a creature under the crosshair: 24 blocks, 32 from PER 20, 40 from PER 40. */
    public static double lookRange(int per) {
        return per >= 40 ? 40 : per >= 20 ? 32 : 24;
    }

    /**
     * Through the binoculars (SPEC §39.8): a held look reads a creature out to {@link #GLASS_REACH} blocks, once the middle of
     * the view has rested on it for {@link #GLASS_DWELL} ticks when it's past the normal range. Only what the client already has.
     */
    public static final double GLASS_REACH = 160;
    public static final int GLASS_DWELL = 10;

    /** The look's reach now: the normal range, or the binoculars' while they magnify. */
    public static double lookRange(int per, double zoom) {
        return zoom > 1.05 ? Math.max(lookRange(per), GLASS_REACH) : lookRange(per);
    }

    /**
     * One creature near the player this tick. {@code lastFight} / {@code lastLook}: game ticks (Long.MIN_VALUE never);
     * {@code notable}: an elite or named beast the player has seen; {@code excluded}: a player, Echo, pet, citizen.
     */
    public record Candidate(int id, double distance, long lastFight, long lastLook, boolean notable, boolean excluded) {}

    /** Whether a creature is in a fight with the player now. */
    public static boolean fighting(Candidate c, long now) {
        return c.lastFight != Long.MIN_VALUE && now - c.lastFight <= FIGHT_TICKS;
    }

    /**
     * The clear screen (SPEC §40.6): the creature you mean (locked or marked) keeps the full plate, and so does one under
     * your look or a notable beast; the others fighting you show only a thin bar.
     */
    public static boolean thin(boolean clear, Candidate c, long now, int meant) {
        if (!clear || c.id == meant || c.notable) return false;
        boolean looked = c.lastLook != Long.MIN_VALUE && now - c.lastLook <= LOOK_FADE;
        return fighting(c, now) && !looked;
    }

    /** The plate's visibility (0..1) now: full while fighting, looked at or notable and near; then it fades. */
    public static float visibility(Candidate c, long now, double lookRange) {
        if (c.excluded) return 0;
        float v = 0;
        if (c.lastFight != Long.MIN_VALUE) {
            long since = now - c.lastFight;
            v = since <= FIGHT_TICKS ? 1 : Math.max(0, 1 - (since - FIGHT_TICKS) / (float) FIGHT_FADE);
        }
        if (c.lastLook != Long.MIN_VALUE && c.distance <= lookRange) {
            long since = now - c.lastLook;
            v = Math.max(v, since <= 0 ? 1 : Math.max(0, 1 - since / (float) LOOK_FADE));
        }
        if (c.notable && c.distance <= NOTABLE_RANGE) v = 1;
        return v;
    }

    /** Sorts {@code in} into plate order and keeps the first {@link #MAX} visible ones in {@code out}; allocation-free. */
    public static void pick(List<Candidate> in, List<Candidate> out, long now, double lookRange) {
        out.clear();
        in.sort(Comparator.comparingInt((Candidate c) -> rank(c, now)).thenComparingDouble(Candidate::distance));
        for (Candidate c : in) {
            if (out.size() >= MAX) break;
            if (visibility(c, now, lookRange) > 0.01F) out.add(c);
        }
    }

    /** 0 fighting, 1 looked at, 2 notable, 3 fading. */
    static int rank(Candidate c, long now) {
        if (fighting(c, now)) return 0;
        if (c.lastLook != Long.MIN_VALUE && now - c.lastLook <= 0) return 1;
        if (c.notable) return 2;
        return 3;
    }
}
