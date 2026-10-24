package dev.awaken.story;

import net.minecraft.server.level.ServerPlayer;

/** Public story API for other modules (SPEC section 12). */
public final class Story {
    private Story() {}

    /**
     * Shows the Revelation {@code data/awaken/awaken/revelation/<key>.json} once for this player and records it in their
     * Codex. Returns false if it was already seen or doesn't exist.
     */
    public static boolean revelation(ServerPlayer player, String key) {
        if (StoryContent.revelation(key) == null || Prologue.progress(player).seen().contains("revelation:" + key)) return false;
        Beats.fire(player, key);
        return true;
    }

    // ---- Carillon's opening (SPEC §16.4): it drives the prologue's first beats and takes it back after the Void -----

    /** Where this player's prologue stands. */
    public static StoryProgress.Prologue prologue(ServerPlayer p) {
        return Prologue.progress(p).prologue;
    }

    /** The game time the current prologue beat began (the opening's timeline counts from it). */
    public static long prologueSince(ServerPlayer p) {
        return Prologue.progress(p).prologueSince;
    }

    /** The story's gate for this player ("" when none). */
    public static String prologueGate(ServerPlayer p) {
        return Prologue.progress(p).prologueGate;
    }

    /** Moves the prologue to one of the opening's beats (or DONE), its clock starting at {@code since}; synced. */
    public static void openingBeat(ServerPlayer p, StoryProgress.Prologue beat, long since) {
        StoryProgress s = Prologue.progress(p);
        s.prologue = beat;
        s.prologueSince = since;
        Prologue.sync(p);
    }

    /**
     * Hands the player to the story at its gate (GATE_OPEN): the Bell Crypt the opening opened in Choir Square. No
     * toast: the arrival's cinematic is the announcement.
     */
    public static void callToGate(ServerPlayer p, String gateId) {
        StoryProgress s = Prologue.progress(p);
        s.prologue = StoryProgress.Prologue.GATE_OPEN;
        s.prologueGate = gateId;
        s.prologueSince = p.level().getGameTime();
        Prologue.sync(p);
        dev.awaken.core.DirectorLog.log(p, "story", "prologue: the opening hands over at the Bell Crypt " + gateId);
    }

    /** Whether a seen-key (revelation, beat, flag) was newly recorded for this player. */
    public static boolean markSeen(ServerPlayer p, String key) {
        boolean added = Prologue.progress(p).seen.add(key);
        if (added) Prologue.sync(p);
        return added;
    }

    /** The prologue skipped (awakened, DONE), as {@code /awaken story skip}. */
    public static void skipPrologue(ServerPlayer p) {
        Prologue.skip(p);
    }

    /** The story is on in this world (server config {@code story}). */
    public static boolean on() {
        return Prologue.storyOn();
    }

    /** Lifts the Void's black (the opening takes the player from the question to the infirmary). */
    public static void liftTheBlack(ServerPlayer p) {
        Prologue.liftTheBlack(p);
    }

    /** Whether the ritual behind this gate has closed its doors (a later group needs a gate of its own). */
    public static boolean ritualSealed(net.minecraft.server.MinecraftServer server, String gateId) {
        int id;
        try {
            id = Integer.parseInt(gateId);
        } catch (NumberFormatException e) {
            return false;
        }
        RitualData.State st = RitualData.get(server).rituals.get(id);
        return st != null && st.beat.sealed();
    }

    /** No gate will come (gates off): the Void finds the player where they stand, as the old prologue's fallback. */
    public static void acceptInPlace(ServerPlayer p) {
        Prologue.beginAcceptance(p, Prologue.progress(p));
    }
}
