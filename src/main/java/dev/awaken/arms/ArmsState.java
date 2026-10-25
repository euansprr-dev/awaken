package dev.awaken.arms;

import org.jspecify.annotations.Nullable;

/**
 * A player's fight in progress on the server (never saved: a relog, a death or a dimension change ends it). Plain
 * fields so the per-tick work allocates nothing. Times are overworld game time, the clock the body and powers use.
 */
final class ArmsState {
    /** The newest press the client numbered; older ones are stale. */
    int seq = -1;
    // the move running
    Archetype.@Nullable Move move;
    Archetypes.@Nullable Loaded style;
    long moveStart, moveEnd;
    /** The running strike was drawn from the sheath (the Katana's draw cut). */
    boolean drawn;
    // a Weave's beat (ArmsWeaves): the opening earned (0 none), when, and on whom (a Break's body)
    int opening, openingTarget = -1;
    long openingAt;
    /** Where the last dodge started (the echo of a perfect dodge strikes there). */
    double dodgeFromX, dodgeFromY, dodgeFromZ;
    // the echo waiting to strike, and the edge on the next blows
    long echoAt, edgeUntil;
    double echoX, echoY, echoZ;
    float echoPower;
    @org.jspecify.annotations.Nullable String echoForce, edgeForce;
    dev.awaken.powers.PowerType.@org.jspecify.annotations.Nullable Family echoFamily, edgeFamily;
    int edgeHits;
    int step = -1, cadence, moveSeq, recovery;
    boolean perfectHeavy;
    /** A buffered tap waiting for its window. */
    ArmsRules.@Nullable Decision buffered;
    int bufferedSeq;
    // who this move has hit (one hit each)
    final int[] hit = new int[16];
    int hitCount;
    long lastHitstopAt = Long.MIN_VALUE / 2;
    // the aimed creature and soft focus
    int aimed = -1, focus = -1;
    long focusSeenAt;
    // a held heavy
    long chargeStart = -1;
    int chargeSeq = -1;
    // a Plunge armed in a fall: its press, when, the furthest it has fallen; and the landing's power once it strikes
    int plungeSeq = -1;
    long plungeAt;
    double plungeFall;
    float plungePower = 1;
    // guard
    long raisedAt = Long.MIN_VALUE / 2, loweredAt = Long.MIN_VALUE / 2;
    boolean raiseDeflected, lockedOut, guardBreak;
    long deflectAt = Long.MIN_VALUE / 2;
    int deflected = -1;
    // dodge
    long dodgeAt = Long.MIN_VALUE / 2;
    float dodgeX, dodgeZ;
    int dodgeCharges = -1;
    long dodgeRechargeAt;
    // resonance
    float resonance;
    /** Tests stand in for the player's latency (ticks of grace; -1: read the connection). */
    int testGrace = -1;
    long fightAt = Long.MIN_VALUE / 2;

    boolean moving(long now) {
        return move != null && now < moveEnd;
    }

    boolean hasHit(int id) {
        for (int i = 0; i < hitCount; i++) if (hit[i] == id) return true;
        return false;
    }

    void markHit(int id) {
        if (hitCount < hit.length) hit[hitCount++] = id;
    }

    void clearMove() {
        move = null;
        step = -1;
        hitCount = 0;
        perfectHeavy = false;
    }
}
