package dev.awaken.body.ascent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The fittings on a player's Tether (SPEC §28.5; TotK's Zonaite: metal for the movement system): one line and one reel
 * at a time. Saved with the player and kept through death (the rope is), schema 1.
 * <ul>
 *   <li>line: none, iron (the rope reaches {@link AscentRules#LINE_REACH} further) or Deepglass (the iron line's reach,
 *   and it pays out smoothly and a little faster for the rappel);</li>
 *   <li>reel: none or gold (reeling in and the rope's fit as it bites wind {@link AscentRules#GOLD_REEL} times faster).</li>
 * </ul>
 */
public record RopeKit(int schemaVersion, int line, int reel) {
    public static final int SCHEMA = 1;
    public static final int LINE_NONE = 0, LINE_IRON = 1, LINE_DEEPGLASS = 2;
    public static final int REEL_NONE = 0, REEL_GOLD = 1;
    public static final RopeKit EMPTY = new RopeKit(SCHEMA, LINE_NONE, REEL_NONE);

    public static final MapCodec<RopeKit> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(RopeKit::schemaVersion),
            Codec.INT.optionalFieldOf("line", LINE_NONE).forGetter(RopeKit::line),
            Codec.INT.optionalFieldOf("reel", REEL_NONE).forGetter(RopeKit::reel)
    ).apply(i, RopeKit::new));

    /** The client's copy of its own kit (set from the server's Kit message; tooltips read it). */
    public static volatile RopeKit client = EMPTY;

    public RopeKit {
        line = line < LINE_NONE || line > LINE_DEEPGLASS ? LINE_NONE : line;
        reel = reel < REEL_NONE || reel > REEL_GOLD ? REEL_NONE : reel;
    }

    public boolean isEmpty() {
        return line == LINE_NONE && reel == REEL_NONE;
    }

    public RopeKit withLine(int l) {
        return new RopeKit(SCHEMA, l, reel);
    }

    public RopeKit withReel(int r) {
        return new RopeKit(SCHEMA, line, r);
    }

    /** Blocks the line adds to the rope's reach (the iron line's, which the Deepglass line keeps). */
    public double reachBonus() {
        return AscentRules.lineReach(line);
    }

    /** The Deepglass line: the rappel pays out in one smooth run instead of bites. */
    public boolean smooth() {
        return line == LINE_DEEPGLASS;
    }

    public boolean gold() {
        return reel == REEL_GOLD;
    }

    /** Line and reel in one int (Seen's kit for watchers, the client's copy): line in bits 0-1, reel in bit 2. */
    public int bits() {
        return line | (reel << 2);
    }

    public static RopeKit ofBits(int bits) {
        return new RopeKit(SCHEMA, bits & 3, (bits >> 2) & 1);
    }
}
