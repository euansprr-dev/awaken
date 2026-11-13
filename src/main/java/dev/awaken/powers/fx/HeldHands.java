package dev.awaken.powers.fx;

import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Who the hands' hold pose belongs to (SPEC §32, "A held pose has an owner"; Euan 9 Nov: two bare fists stuck up while he
 * swung a weapon). A held cast's first-person loop plays until something stops it, and it hides what you hold, so it must
 * belong to the cast that started it, not to whatever was cast last. The key and the server each hold it here, and it goes
 * the moment neither does. No client classes: a test runs it through every order of presses.
 */
public final class HeldHands {
    /** The first-person clip player (FirstPerson). */
    public interface Clips {
        boolean play(String clip);

        void stop(String clip);
    }

    private final Clips clips;
    /** The held cast each power key is down on, or null. */
    private final CastProfiles.@Nullable Profile[] keys;
    /** The cast whose hold loop is playing, or null. */
    private CastProfiles.@Nullable Profile loop;

    public HeldHands(int slots, Clips clips) {
        this.clips = clips;
        keys = new CastProfiles.Profile[slots];
    }

    private static boolean same(CastProfiles.@Nullable Profile a, CastProfiles.@Nullable Profile b) {
        return a != null && b != null && a.id() == b.id(); // the level rows share an id
    }

    /** A power key went down on this cast (null, or a cast with no hold: the key holds nothing). */
    public void press(int slot, CastProfiles.@Nullable Profile cast) {
        keys[slot] = cast != null && cast.channel() ? cast : null;
    }

    /** A key came up: the cast it was holding, if any, lets go. */
    public CastProfiles.@Nullable Profile release(int slot) {
        CastProfiles.Profile cast = keys[slot];
        keys[slot] = null;
        if (cast != null && !keyHolds(cast)) letGo(cast);
        return cast;
    }

    /** The server says this cast is over, whatever the keys are doing. */
    public void ended(CastProfiles.Profile cast) {
        for (int i = 0; i < keys.length; i++) if (same(keys[i], cast)) keys[i] = null;
        letGo(cast);
    }

    private void letGo(CastProfiles.Profile cast) {
        clips.stop(cast.begin1p());
        if (cast.hold1p() != null) clips.stop(cast.hold1p());
        if (same(loop, cast)) stopLoop();
    }

    private void stopLoop() {
        clips.stop(loop.hold1p());
        loop = null;
    }

    private boolean keyHolds(CastProfiles.Profile cast) {
        for (CastProfiles.Profile k : keys) if (same(k, cast)) return true;
        return false;
    }

    /** The cast a key is holding that this begin clip started, however many casts have come since; null if none. */
    public CastProfiles.@Nullable Profile byBegin(String beginClip) {
        for (CastProfiles.Profile k : keys) if (k != null && k.begin1p().equals(beginClip)) return k;
        return null;
    }

    /** A begin clip reached its hold: this cast's loop takes over (only ever one: a second hold replaces the first). */
    public void reached(CastProfiles.Profile cast) {
        if (cast.hold1p() == null) return;
        if (loop != null && !same(loop, cast)) stopLoop();
        loop = cast;
        clips.play(cast.hold1p());
    }

    /** Every tick: a loop no key holds and no running cast of the server's holds goes. {@code casting}: that cast is still running. */
    public void tick(Predicate<CastProfiles.Profile> casting) {
        if (loop != null && !keyHolds(loop) && !casting.test(loop)) stopLoop();
    }

    /** Logged out, respawned, keys forgotten: nothing is held any more. */
    public void reset() {
        java.util.Arrays.fill(keys, null);
        if (loop != null) stopLoop();
    }

    /** The cast whose loop is playing, or null. */
    public CastProfiles.@Nullable Profile loop() {
        return loop;
    }

    /**
     * Whether a cast's beat from the server starts its pose on this client (FxClient.onProfile; SPEC §32, "A reply never starts
     * a hold"). {@code beat}: BEGIN or RELEASE; {@code channel}: a held cast; {@code own}: this client is the caster's;
     * {@code keyed}: the caster's own key press made it (FxEvent.keyed); {@code shown}: ticks since this client's pose of that
     * cast began, or -1 when it isn't showing it.
     */
    public static boolean startsPose(int beat, boolean channel, boolean own, boolean keyed, float shown) {
        // one side decides: your own key press showed it from the press, so no reply starts it again, however late it comes
        // (Euan 12 Nov: the Haymaker's punch arrived after its pose had gone and wound the fist back up for half a minute)
        if (own && keyed) return false;
        // a cast the server began alone (a command, a second press) shows here
        if (beat == CastProfiles.BEGIN) return !(own && shown >= 0 && shown <= 8);
        // a power with no wind-up has only this event: it starts the pose too. Never a hold: a held cast's release comes
        // with or after its end, so nothing would be left to end it
        if (beat == CastProfiles.RELEASE) return !channel && shown < 0;
        return false;
    }

    /** Whether a key is down on a held cast, or its loop is still playing: the hands are a cast's, from the press to the let-go. */
    public boolean holding() {
        if (loop != null) return true;
        for (CastProfiles.Profile k : keys) if (k != null) return true;
        return false;
    }
}
